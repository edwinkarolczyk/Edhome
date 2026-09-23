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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * Opt-in reminders. Legacy tasks use a daily inexact 09:00 alarm; tasks with
 * custom time get one inexact alarm per task/occurrence. No exact-alarm permission
 * or background network is required. A completed/edited task cannot send a
 * stale old alarm because both the ID and occurrence fingerprint are checked.
 */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION_REMIND =
        "com.edwinkarolczyk.edhome.REMIND_TASKS";
    private static final String ACTION_TASK =
        "com.edwinkarolczyk.edhome.REMIND_SINGLE_TASK";
    private static final String CHANNEL_ID = "edhome-task-due";
    private static final int ALARM_ID = 7125;
    private static final String DATABASE = "edhome-beta-preview.db";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
            "edhome_beta_prefs", Context.MODE_PRIVATE);
    }

    private static int requestCode(long id) {
        return 100000 + (int) (id % 900000);
    }

    private static PendingIntent taskIntent(Context context, long id, int flags,
            String fingerprint) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_TASK).putExtra("task_id", id);
        if (fingerprint != null)
            intent.putExtra("occurrence", fingerprint);
        return PendingIntent.getBroadcast(context, requestCode(id), intent,
            flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancelTask(Context context, long taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        PendingIntent pending = taskIntent(context, taskId,
            PendingIntent.FLAG_NO_CREATE, null);
        if (pending != null) {
            if (manager != null) manager.cancel(pending);
            pending.cancel();
        }
    }

    private static String fingerprint(String due, String hhmm, int lead) {
        return due + "|" + hhmm + "|" + lead;
    }

    private static long epoch(LocalDateTime when) {
        return when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static void scheduleCustom(Context context, AlarmManager manager,
            SharedPreferences pref, long id, String due, String hhmm,
            int lead, LocalDateTime now) {
        if (due == null || hhmm == null || !ReminderRules.validTime(hhmm)
                || !ReminderRules.allowedLead(lead)) return;
        LocalDate dueDay;
        LocalDateTime requested;
        try {
            dueDay = LocalDate.parse(due);
            requested = ReminderRules.target(due, hhmm, lead,
                pref.getString("quiet_hours_start", QuietHoursRules.DEFAULT_START),
                pref.getString("quiet_hours_end", QuietHoursRules.DEFAULT_END));
        } catch (Exception invalid) {
            DiagnosticLog.event("REMINDER_INVALID_TASK");
            return;
        }
        // Very old unfinished tasks stay in the legacy overdue daily digest,
        // not an immediate storm of backlogged personalized notifications.
        if (dueDay.isBefore(now.toLocalDate().minusDays(1))) return;
        String occurrence = fingerprint(due, hhmm, lead);
        if (occurrence.equals(pref.getString("reminder_fired_" + id, "")))
            return;
        LocalDateTime when = requested.isAfter(now)
            ? requested : now.plusMinutes(1);
        when = ReminderRules.nextAllowed(when,
            pref.getString("quiet_hours_start", QuietHoursRules.DEFAULT_START),
            pref.getString("quiet_hours_end", QuietHoursRules.DEFAULT_END));
        PendingIntent pending = taskIntent(context, id,
            PendingIntent.FLAG_UPDATE_CURRENT, occurrence);
        // Inexact delivery: Android may delay it, e.g. Doze or battery saving.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            epoch(when), pending);
    }

    static void schedule(Context context) {
        // Vehicle opt-in is independent of the global task reminder switch.
        VehicleReminderReceiver.schedule(context);
        AlarmManager manager = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_REMIND);
        PendingIntent daily = PendingIntent.getBroadcast(context, ALARM_ID,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);
        manager.cancel(daily);
        SharedPreferences pref = prefs(context);
        java.io.File file = context.getDatabasePath(DATABASE);
        if (file.isFile()) {
            try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                 Cursor tasks = database.rawQuery(
                    "SELECT id,due_date,remind_time,reminder_lead_days,done "
                    + "FROM tasks WHERE remind_time IS NOT NULL", null)) {
                LocalDateTime now = LocalDateTime.now();
                while (tasks.moveToNext()) {
                    long id = tasks.getLong(0);
                    cancelTask(context, id);
                    if (pref.getBoolean("reminders_enabled", false)
                            && tasks.getInt(4) == 0) {
                        scheduleCustom(context, manager, pref, id,
                            tasks.isNull(1) ? null : tasks.getString(1),
                            tasks.getString(2), tasks.getInt(3), now);
                    }
                }
            } catch (Exception problem) {
                DiagnosticLog.error("REMINDER_RESCHEDULE", problem);
            }
        }
        if (!pref.getBoolean("reminders_enabled", false)) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis())
            next.add(Calendar.DAY_OF_MONTH, 1);
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
            next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, daily);
        DiagnosticLog.event("REMINDER_SCHEDULED");
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION_REMIND.equals(action) && !ACTION_TASK.equals(action)) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action))
                schedule(context);
            return;
        }
        SharedPreferences pref = prefs(context);
        if (!pref.getBoolean("reminders_enabled", false)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        java.io.File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        // Never announce within quiet hours even when Android delays an alarm.
        String quietStart = pref.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String quietEnd = pref.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        if (!QuietHoursRules.validWindow(quietStart, quietEnd)) {
            quietStart = QuietHoursRules.DEFAULT_START;
            quietEnd = QuietHoursRules.DEFAULT_END;
        }
        if (QuietHoursRules.isQuiet(now, quietStart, quietEnd)) {
            schedule(context);
            return;
        }
        int due = 0;
        int waste = 0;
        String firedKey = null;
        String firedValue = null;
        boolean legacy = ACTION_REMIND.equals(action);
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            if (legacy) {
                // Daily digest is independent of customized on-time alerts.
                // Overdue personalized tasks still appear on later days.
                if (now.getHour() < 9 || today.toString().equals(
                        pref.getString("reminder_legacy_day", ""))) return;
                try (Cursor tasks = database.rawQuery(
                        "SELECT id,task_kind,remind_time,due_date FROM tasks "
                        + "WHERE done=0 AND due_date IS NOT NULL AND due_date<=?",
                        new String[]{today.toString()})) {
                    while (tasks.moveToNext()) {
                        if (!tasks.isNull(2) && !tasks.getString(3).equals(
                                today.toString())) {
                            if (today.toString().equals(pref.getString(
                                    "reminder_custom_day_" + tasks.getLong(0),
                                    ""))) continue;
                        } else if (!tasks.isNull(2)) continue;
                        due++;
                        if ("waste".equals(tasks.getString(1))) waste++;
                    }
                }
            } else {
                long id = intent.getLongExtra("task_id", -1L);
                String expected = intent.getStringExtra("occurrence");
                if (id < 0 || expected == null) return;
                try (Cursor task = database.rawQuery(
                        "SELECT due_date,remind_time,reminder_lead_days,"
                        + "task_kind FROM tasks WHERE id=? AND done=0",
                        new String[]{Long.toString(id)})) {
                    if (!task.moveToFirst() || task.isNull(0)
                            || task.isNull(1)) return;
                    String taskDue = task.getString(0);
                    String time = task.getString(1);
                    int lead = task.getInt(2);
                    String actual = fingerprint(taskDue, time, lead);
                    if (!expected.equals(actual)
                            || expected.equals(pref.getString(
                                "reminder_fired_" + id, ""))) return;
                    if (ReminderRules.target(taskDue, time, lead,
                            quietStart, quietEnd).isAfter(now)) {
                        schedule(context);
                        return;
                    }
                    due = 1;
                    waste = "waste".equals(task.getString(3)) ? 1 : 0;
                    firedKey = "reminder_fired_" + id;
                    firedValue = actual;
                }
            }
        } catch (Exception problem) {
            DiagnosticLog.error("REMINDER_DATABASE", problem);
            return;
        }
        if (due == 0) {
            if (legacy) pref.edit().putString("reminder_legacy_day",
                today.toString()).apply();
            return;
        }
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null || !notifications.areNotificationsEnabled())
            return;
        notifications.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, "Przypomnienia o czynnościach",
            NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 0,
            new Intent(context, MainActivity.class).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • czynności")
            .setContentText(waste > 0
                ? "Odpady do wystawienia: " + waste
                    + " • pozostałe czynności: " + (due - waste)
                : "Czynności do wykonania: " + due)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).build();
        int notificationId = legacy ? ALARM_ID
            : requestCode(intent.getLongExtra("task_id", -1L));
        notifications.notify(notificationId, notification);
        SharedPreferences.Editor editor = pref.edit();
        if (legacy) editor.putString("reminder_legacy_day", today.toString());
        else editor.putString(firedKey, firedValue)
            .putString("reminder_custom_day_"
                + intent.getLongExtra("task_id", -1L), today.toString());
        editor.apply();
        DiagnosticLog.event("REMINDER_DELIVERED");
        if (!legacy) schedule(context);
    }
}
