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
import android.os.Build;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Local inexact alarms for appliances, independent of task reminders. */
public final class DeviceTimerReceiver extends BroadcastReceiver {
    private static final String ACTION_END =
        "com.edwinkarolczyk.edhome.DEVICE_TIMER_FINISHED";
    private static final String DB = "edhome-beta-preview.db";
    private static final String CHANNEL = "edhome-appliances";
    private static final int NOTIFICATION_BASE = 1500000;

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("edhome_beta_prefs",
            Context.MODE_PRIVATE);
    }

    private static int notificationId(long id) {
        return NOTIFICATION_BASE + (int) (id % 1000000L);
    }

    private static PendingIntent alarm(Context context, long id, long end,
            int flags) {
        Intent intent = new Intent(context, DeviceTimerReceiver.class)
            .setAction(ACTION_END)
            .putExtra("timer_id", id)
            .putExtra("end_at", end);
        return PendingIntent.getBroadcast(context, notificationId(id),
            intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAlarm(Context context, long id) {
        AlarmManager manager = (AlarmManager)
            context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = alarm(context, id, 0L,
            PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            if (manager != null) manager.cancel(pending);
            pending.cancel();
        }
    }

    static void cancel(Context context, long id) {
        cancelAlarm(context, id);
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancel(notificationId(id));
    }

    static void scheduleAll(Context context) {
        File file = context.getDatabasePath(DB);
        if (!file.isFile()) return;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor c = database.rawQuery(
                    "SELECT id,end_at FROM device_timers WHERE status='running'",
                    null)) {
            while (c.moveToNext())
                scheduleOne(context, c.getLong(0), c.getLong(1));
        } catch (Exception error) {
            // Boot/upgrade can precede migration: the UI initializes the DB first.
            DiagnosticLog.error("DEVICE_TIMERS_RESCHEDULE", error);
        }
    }

    static void scheduleOne(Context context, long id, long end) {
        cancelAlarm(context, id);
        if (!preferences(context).getBoolean("timer_notifications_enabled",
                false) || end <= System.currentTimeMillis()) return;
        AlarmManager manager = (AlarmManager)
            context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        LocalDateTime local = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(end), ZoneId.systemDefault());
        SharedPreferences prefs = preferences(context);
        String quietStart = prefs.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String quietEnd = prefs.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        if (!QuietHoursRules.validWindow(quietStart, quietEnd)) {
            quietStart = QuietHoursRules.DEFAULT_START;
            quietEnd = QuietHoursRules.DEFAULT_END;
        }
        long allowed = ReminderRules.nextAllowed(local, quietStart, quietEnd)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            allowed, alarm(context, id, end, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_END.equals(intent.getAction())) {
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)) {
                scheduleAll(context);
                if (BetaUpdater.isBeta()
                        && (Intent.ACTION_BOOT_COMPLETED.equals(action)
                            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)))
                    LanSyncService.ensureStarted(context);
            }
            return;
        }
        if (!preferences(context).getBoolean("timer_notifications_enabled",
                false)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        long id = intent.getLongExtra("timer_id", -1L);
        long expected = intent.getLongExtra("end_at", -1L);
        if (id <= 0 || expected <= 0 || System.currentTimeMillis() < expected)
            return;
        File file = context.getDatabasePath(DB);
        if (!file.isFile()) return;
        String type, title;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor c = database.rawQuery(
                    "SELECT device_type,title,end_at FROM device_timers "
                    + "WHERE id=? AND status='running'",
                    new String[]{Long.toString(id)})) {
            if (!c.moveToFirst() || c.getLong(2) != expected) return;
            type = c.getString(0);
            title = c.getString(1);
        } catch (Exception error) {
            DiagnosticLog.error("DEVICE_TIMER_ALARM", error);
            return;
        }
        SharedPreferences prefs = preferences(context);
        String quietStart = prefs.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String quietEnd = prefs.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        if (!QuietHoursRules.validWindow(quietStart, quietEnd)) {
            quietStart = QuietHoursRules.DEFAULT_START;
            quietEnd = QuietHoursRules.DEFAULT_END;
        }
        LocalDateTime now = LocalDateTime.now();
        if (QuietHoursRules.isQuiet(now, quietStart, quietEnd)) {
            // If Doze delivers in quiet hours, retry after the configured window.
            long next = ReminderRules.nextAllowed(now, quietStart, quietEnd)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            AlarmManager manager = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
            if (manager != null)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    next, alarm(context, id, expected,
                        PendingIntent.FLAG_UPDATE_CURRENT));
            return;
        }
        String fingerprint = Long.toString(expected);
        if (fingerprint.equals(prefs.getString("timer_notified_" + id, "")))
            return;
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null || !notifications.areNotificationsEnabled())
            return;
        notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Minutniki urządzeń", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_timers", true);
        PendingIntent pending = PendingIntent.getActivity(context,
            notificationId(id), open, PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • " + label(type))
            .setContentText("Zakończono: " + title
                + ". Potwierdź w Minutnikach.")
            .setContentIntent(pending)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).build();
        notifications.notify(notificationId(id), notification);
        prefs.edit().putString("timer_notified_" + id, fingerprint).apply();
        DiagnosticLog.event("DEVICE_TIMER_NOTIFIED");
    }

    private static String label(String type) {
        for (int i = 0; i < DeviceTimerRules.TYPES.length; i++)
            if (DeviceTimerRules.TYPES[i].equals(type))
                return DeviceTimerRules.LABELS[i];
        return "Urządzenie";
    }
}
