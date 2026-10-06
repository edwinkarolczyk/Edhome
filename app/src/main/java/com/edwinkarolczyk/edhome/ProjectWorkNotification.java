package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Persistent notification + actions for active Project work sessions. */
final class ProjectWorkNotification {
    static final String ACTION_START =
        "com.edwinkarolczyk.edhome.PROJECT_WORK_START";
    static final String ACTION_STOP =
        "com.edwinkarolczyk.edhome.PROJECT_WORK_STOP";
    private static final String CHANNEL_ID = "edhome-project-work";
    private static final String GROUP_ID = "edhome-project-work";
    private static final String DATABASE = "edhome-beta-preview.db";

    private ProjectWorkNotification() { }

    private static int notificationId(long taskId) {
        // Deliberately matches ReminderReceiver's single-task notification id,
        // so START replaces the reminder instead of leaving a duplicate behind.
        return 100000 + (int) (taskId % 900000);
    }

    private static int actionRequestCode(long taskId, boolean stop) {
        return (stop ? 2100000 : 1200000) + (int) (taskId % 800000);
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    static PendingIntent startAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_START)
            .putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            actionRequestCode(taskId, false), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent stopAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_STOP)
            .putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            actionRequestCode(taskId, true), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAction(Context context,long projectId,
            long taskId) {
        Intent intent=new Intent(context,MainActivity.class)
            .setAction("com.edwinkarolczyk.edhome.OPEN_PROJECT_TASK."+taskId)
            .putExtra("open_project_id",projectId)
            .putExtra("open_project_task_id",taskId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                |Intent.FLAG_ACTIVITY_CLEAR_TOP
                |Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context,
            3100000+(int)(taskId%800000),intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean isActive(Context context, long taskId) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return false;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = database.rawQuery(
                "SELECT 1 FROM project_task_work_sessions "
                    + "WHERE task_id=? AND ended_at IS NULL LIMIT 1",
                new String[]{Long.toString(taskId)})) {
            return cursor.moveToFirst();
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_NOTIFICATION_ACTIVE", problem);
            return false;
        }
    }

    static void handleStartAction(Context context, long taskId) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            ProjectStore.startWork(database, taskId);
            DiagnosticLog.event("PROJECT_WORK_STARTED_FROM_NOTIFICATION",
                "task=" + taskId);
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_START_NOTIFICATION", problem);
            showActionError(context, taskId,
                problem.getMessage() == null
                    ? "Nie udało się rozpocząć pomiaru."
                    : problem.getMessage());
            return;
        }
        showActive(context, taskId);
    }

    static void handleStopAction(Context context, long taskId) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        int minutes;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            minutes = ProjectStore.stopWork(database, taskId);
            DiagnosticLog.event("PROJECT_WORK_STOPPED_FROM_NOTIFICATION",
                "task=" + taskId + " minutes=" + minutes);
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_STOP_NOTIFICATION", problem);
            showActionError(context, taskId,
                problem.getMessage() == null
                    ? "Nie udało się zatrzymać pomiaru."
                    : problem.getMessage());
            return;
        }
        cancel(context, taskId);
        showStopped(context, taskId, minutes);
    }

    static void showActive(Context context, long taskId) {
        if (!canNotify(context)) return;
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;

        String title;
        String projectName;
        long projectId;
        int plannedMinutes;
        long activeStarted;
        int closedMinutes = 0;
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor task = database.rawQuery(
                    "SELECT t.title,t.duration_minutes,t.done,t.project_id,p.name "
                        +"FROM tasks t JOIN projects p ON p.id=t.project_id "
                        +"WHERE t.id=?",
                    new String[]{Long.toString(taskId)})) {
                if (!task.moveToFirst() || task.getInt(2) != 0) {
                    cancel(context, taskId);
                    return;
                }
                title = task.getString(0);
                plannedMinutes = Math.max(1, task.getInt(1));
                projectId = task.getLong(3);
                projectName = task.getString(4);
            }
            try (Cursor active = database.rawQuery(
                    "SELECT started_at FROM project_task_work_sessions "
                        + "WHERE task_id=? AND ended_at IS NULL "
                        + "ORDER BY id DESC LIMIT 1",
                    new String[]{Long.toString(taskId)})) {
                if (!active.moveToFirst()) {
                    cancel(context, taskId);
                    return;
                }
                activeStarted = active.getLong(0);
            }
            try (Cursor closed = database.rawQuery(
                    "SELECT COALESCE(SUM(worked_minutes),0) "
                        + "FROM project_task_work_sessions "
                        + "WHERE task_id=? AND ended_at IS NOT NULL",
                    new String[]{Long.toString(taskId)})) {
                if (closed.moveToFirst()) closedMinutes = Math.max(0,
                    closed.getInt(0));
            }
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_NOTIFICATION_BUILD", problem);
            return;
        }

        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, "Aktywna praca nad projektem",
            NotificationManager.IMPORTANCE_LOW));

        PendingIntent open=openAction(context,projectId,taskId);

        // Backdate the chronometer by already closed sessions so the system
        // chronometer shows total worked time, not only the newest Start.
        long chronometerBase = activeStarted - closedMinutes * 60000L;

        String detail="● Praca trwa • "+title
            +"\nPlan: "+formatMinutes(plannedMinutes)
            +"\nDotknij, aby otworzyć tę czynność.";
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • Projekt: " + projectName)
            .setContentText("● Praca trwa • " + title)
            .setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setWhen(chronometerBase)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setGroup(GROUP_ID)
            .addAction(android.R.drawable.ic_menu_view,
                "Otwórz czynność", open)
            .addAction(android.R.drawable.ic_media_pause,
                "■ Stop", stopAction(context, taskId))
            .build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_NO_CLEAR;
        manager.notify(notificationId(taskId), notification);
    }

    static void cancel(Context context, long taskId) {
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(taskId));
    }

    static void restoreActive(Context context) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        List<Long> active = new ArrayList<>();
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = database.rawQuery(
                "SELECT DISTINCT s.task_id "
                    + "FROM project_task_work_sessions s "
                    + "JOIN tasks t ON t.id=s.task_id "
                    + "WHERE s.ended_at IS NULL AND t.done=0 "
                    + "AND t.project_id IS NOT NULL", null)) {
            while (cursor.moveToNext()) active.add(cursor.getLong(0));
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_NOTIFICATION_RESTORE", problem);
            return;
        }
        for (Long taskId : active) showActive(context, taskId);
    }

    private static void showStopped(Context context, long taskId, int minutes) {
        if (!canNotify(context)) return;
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, "Aktywna praca nad projektem",
            NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • pomiar zatrzymany")
            .setContentText("Ostatnia sesja: " + formatMinutes(minutes))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build();
        manager.notify(notificationId(taskId), notification);
    }

    private static void showActionError(Context context, long taskId,
            String message) {
        if (!canNotify(context)) return;
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, "Aktywna praca nad projektem",
            NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • nie rozpoczęto pomiaru")
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build();
        manager.notify(notificationId(taskId), notification);
    }

    private static String formatMinutes(int total) {
        int value = Math.max(0, total);
        int hours = value / 60;
        int minutes = value % 60;
        if (hours == 0) return minutes + " min";
        if (minutes == 0) return hours + " h";
        return hours + " h " + minutes + " min";
    }
}
