package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Independent, opt-in OC/inspection alerts. No duplicate tasks or PayCheck writes. */
public final class VehicleReminderReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.edwinkarolczyk.edhome.VEHICLE_DUE";
    private static final String CHANNEL = "edhome-vehicle-due";
    private static final String DB = "edhome-beta-preview.db";
    private static final String PREFS = "edhome_beta_prefs";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String fingerprint(String deadline, int lead) {
        return deadline + "|" + lead;
    }

    private static String receipt(long id, String kind) {
        return "vehicle_reminder_fired_" + id + "_" + kind;
    }

    private static int requestCode(long id, String kind) {
        return (int) (10000000L + id % 1000000L * 2L + ("oc".equals(kind) ? 0 : 1));
    }

    private static PendingIntent pending(Context context, long id, String kind,
            String occurrence, int flags) {
        Intent intent = new Intent(context, VehicleReminderReceiver.class)
            .setAction(ACTION)
            .setData(Uri.parse("edhome://vehicle-reminder/" + id + "/" + kind))
            .putExtra("vehicle_id", id).putExtra("kind", kind);
        if (occurrence != null) intent.putExtra("occurrence", occurrence);
        return PendingIntent.getBroadcast(context, requestCode(id, kind),
            intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancel(Context context, AlarmManager manager,
            long id, String kind) {
        PendingIntent existing = pending(context, id, kind, null,
            PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            manager.cancel(existing);
            existing.cancel();
        }
    }

    private static LocalDateTime target(LocalDateTime now, String deadline, int lead,
            SharedPreferences pref) {
        LocalDateTime proposed = LocalDate.parse(deadline).minusDays(lead).atTime(9, 0);
        if (!proposed.isAfter(now)) proposed = now.plusMinutes(1);
        return QuietHoursRules.nextAllowed(proposed,
            pref.getString("quiet_hours_start", QuietHoursRules.DEFAULT_START),
            pref.getString("quiet_hours_end", QuietHoursRules.DEFAULT_END));
    }

    private static void arm(Context context, AlarmManager manager,
            SharedPreferences pref, LocalDateTime now, long id,
            String kind, String deadline, Integer lead) {
        cancel(context, manager, id, kind);
        if (lead == null || deadline == null || deadline.isEmpty()
                || !VehicleReminderRules.allowed(lead)) return;
        LocalDate end;
        try { end = LocalDate.parse(deadline); }
        catch (Exception invalid) {
            DiagnosticLog.event("VEHICLE_REMINDER_INVALID_DATE");
            return;
        }
        if (end.isBefore(now.toLocalDate())) return;
        String occurrence = fingerprint(deadline, lead);
        if (occurrence.equals(pref.getString(receipt(id, kind), ""))) return;
        try {
            LocalDateTime when = target(now, deadline, lead, pref);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pending(context, id, kind, occurrence,
                    PendingIntent.FLAG_UPDATE_CURRENT));
        } catch (Exception error) {
            DiagnosticLog.error("VEHICLE_REMINDER_ARM", error);
        }
    }

    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        if (manager == null) return;
        java.io.File file = context.getDatabasePath(DB);
        if (!file.isFile()) return;
        SharedPreferences pref = prefs(context);
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor vehicles = db.rawQuery(
                "SELECT id,oc_until,inspection_until,"
                + "oc_reminder_lead,inspection_reminder_lead FROM vehicles", null)) {
            LocalDateTime now = LocalDateTime.now();
            while (vehicles.moveToNext()) {
                long id = vehicles.getLong(0);
                arm(context, manager, pref, now, id, "oc", vehicles.getString(1),
                    vehicles.isNull(3) ? null : vehicles.getInt(3));
                arm(context, manager, pref, now, id, "inspection", vehicles.getString(2),
                    vehicles.isNull(4) ? null : vehicles.getInt(4));
            }
        } catch (Exception error) {
            DiagnosticLog.error("VEHICLE_REMINDER_SCHEDULE", error);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        long id = intent.getLongExtra("vehicle_id", -1L);
        String kind = intent.getStringExtra("kind");
        String expected = intent.getStringExtra("occurrence");
        if (id < 1 || expected == null
                || !("oc".equals(kind) || "inspection".equals(kind))) return;
        SharedPreferences pref = prefs(context);
        if (expected.equals(pref.getString(receipt(id, kind), ""))) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null || !notifications.areNotificationsEnabled()) return;
        java.io.File file = context.getDatabasePath(DB);
        if (!file.isFile()) return;
        String deadline, vehicleName;
        int lead;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery(
                "SELECT name," + ("oc".equals(kind) ? "oc_until,oc_reminder_lead"
                    : "inspection_until,inspection_reminder_lead")
                + " FROM vehicles WHERE id=?",
                new String[]{Long.toString(id)})) {
            if (!c.moveToFirst() || c.isNull(2)) return;
            vehicleName = c.getString(0);
            deadline = c.getString(1);
            lead = c.getInt(2);
        } catch (Exception error) {
            DiagnosticLog.error("VEHICLE_REMINDER_READ", error);
            return;
        }
        if (!VehicleReminderRules.allowed(lead) || deadline.isEmpty()
                || !expected.equals(fingerprint(deadline, lead))
                || LocalDate.parse(deadline).isBefore(LocalDate.now())) return;
        LocalDateTime now = LocalDateTime.now();
        if (LocalDate.parse(deadline).minusDays(lead).atTime(9, 0).isAfter(now)
                || QuietHoursRules.isQuiet(now,
                    pref.getString("quiet_hours_start", QuietHoursRules.DEFAULT_START),
                    pref.getString("quiet_hours_end", QuietHoursRules.DEFAULT_END))) {
            schedule(context);
            return;
        }
        notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Pojazdy • OC i przeglądy", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class)
            .putExtra("open_vehicles", true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context,
            requestCode(id, kind), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • " + ("oc".equals(kind) ? "OC" : "Przegląd"))
            .setContentText(vehicleName + " • termin: " + deadline)
            .setContentIntent(content)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).build();
        notifications.notify(requestCode(id, kind), n);
        pref.edit().putString(receipt(id, kind), expected).apply();
        DiagnosticLog.event("VEHICLE_REMINDER_DELIVERED");
    }
}
