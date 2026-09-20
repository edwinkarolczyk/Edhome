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
import java.util.Calendar;

/** Opt-in local reminders, with an inexact daily alarm restored after reboot/update. */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION_REMIND =
        "com.edwinkarolczyk.edhome.REMIND_TASKS";
    private static final String CHANNEL_ID = "edhome-task-due";
    private static final int ALARM_ID = 7125;

    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION_REMIND);
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.cancel(pending);
        SharedPreferences prefs = context.getSharedPreferences(
            "edhome_beta_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis())
            next.add(Calendar.DAY_OF_MONTH, 1);
        // Android can defer inexact alarms under battery restrictions.
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
            next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pending);
        DiagnosticLog.event("REMINDER_SCHEDULED");
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_REMIND.equals(intent.getAction())) {
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)) schedule(context);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(
            "edhome_beta_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("reminders_enabled", false)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;
        java.io.File file = context.getDatabasePath("edhome-beta-preview.db");
        if (!file.isFile()) return;
        int due = 0;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor rows = database.rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE done=0 AND due_date IS NOT NULL "
                    + "AND due_date<=?", new String[]{LocalDate.now().toString()})) {
            if (rows.moveToFirst()) due = rows.getInt(0);
        } catch (Exception problem) {
            DiagnosticLog.error("REMINDER_DATABASE", problem);
            return;
        }
        if (due == 0) return;
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null || !notifications.areNotificationsEnabled()) return;
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
            .setContentText("Czynności do wykonania: " + due)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build();
        notifications.notify(ALARM_ID, notification);
        DiagnosticLog.event("REMINDER_DELIVERED");
    }
}
