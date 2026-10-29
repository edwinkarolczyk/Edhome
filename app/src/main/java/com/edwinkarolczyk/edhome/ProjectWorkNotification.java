package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.Notification;
import android.app.AlarmManager;
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
    static final String ACTION_DISMISS =
        "com.edwinkarolczyk.edhome.PROJECT_WORK_DISMISS";
    static final String ACTION_CHECK =
        "com.edwinkarolczyk.edhome.PROJECT_WORK_CHECK";
    static final String ACTION_TIMEOUT =
        "com.edwinkarolczyk.edhome.PROJECT_WORK_TIMEOUT";
    private static final long CHECK_MS = 60L * 60L * 1000L;

    private static long reminderIntervalMs(Context context) {
        int minutes = context.getSharedPreferences("edhome_beta_prefs", Context.MODE_PRIVATE)
            .getInt("project_work_reminder_interval_minutes", 60);
        if (minutes != 15 && minutes != 30 && minutes != 45
                && minutes != 60 && minutes != 90 && minutes != 120) minutes = 60;
        return minutes * 60L * 1000L;
    }
    private static int reminderNotificationId(long taskId) {
        return 1000000 + (int) (taskId % 800000);
    }
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
        return manager != null && LegacyCompat.notificationsEnabled(manager);
    }

    static PendingIntent startAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_START)
            .putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            actionRequestCode(taskId, false), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
    }

    static PendingIntent stopAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_STOP)
            .putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            actionRequestCode(taskId, true), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
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
            PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());
    }

    private static final class NotificationTask {
        final String title;
        final long projectId;
        NotificationTask(String title,long projectId) {
            this.title=title;
            this.projectId=projectId;
        }
    }

    private static NotificationTask reminderTask(Context context,long taskId) {
        File file=context.getDatabasePath(DATABASE);
        if(!file.isFile())return null;
        try(SQLiteDatabase database=SQLiteDatabase.openDatabase(
                file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            Cursor row=database.rawQuery(
                "SELECT title,project_id FROM tasks WHERE id=? AND project_id IS NOT NULL",
                new String[]{Long.toString(taskId)})) {
            return row.moveToFirst()
                ? new NotificationTask(row.getString(0),row.getLong(1)) : null;
        }catch(Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_REMINDER_TARGET",problem);
            return null;
        }
    }

    private static PendingIntent checkAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_CHECK).putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            4100000 + (int)(taskId % 800000), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
    }

    private static void scheduleCheck(Context context, long taskId, long started) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        LegacyCompat.setAndAllowWhileIdle(alarm, AlarmManager.RTC_WAKEUP,
            Math.max(System.currentTimeMillis() + 1000L, started + CHECK_MS),
            checkAction(context, taskId));
    }

    private static void cancelCheck(Context context, long taskId) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = checkAction(context, taskId);
        if (alarm != null) alarm.cancel(pending);
        pending.cancel();
    }

    /** Po godzinie przypomina, ale nigdy nie zamyka ani nie pauzuje sesji. */
    static void handleCheckAction(Context context, long taskId) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        long started;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT started_at FROM project_task_work_sessions "
                + "WHERE task_id=? AND ended_at IS NULL ORDER BY id DESC LIMIT 1",
                new String[]{Long.toString(taskId)})) {
            if (!cursor.moveToFirst()) { cancelReminder(context, taskId); return; }
            started = cursor.getLong(0);
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_REMINDER_CHECK", problem);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < started + reminderIntervalMs(context)) { scheduleCheck(context, taskId, started); return; }
        // Keep the persistent chronometer; show this reminder under a separate ID.
        scheduleNextCheck(context, taskId);
        if (!canNotify(context)) {
            DiagnosticLog.event("PROJECT_WORK_REMINDER_SKIPPED", "task=" + taskId + " reason=notifications_disabled");
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        LegacyCompat.ensureChannel(manager, CHANNEL_ID, "Aktywna praca nad projektem",
            NotificationManager.IMPORTANCE_DEFAULT, null, true, true, false);
        NotificationTask target = reminderTask(context, taskId);
        Notification notification = LegacyCompat.notificationBuilder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle(target == null ? "EDHOME • licznik nadal działa" : "EDHOME • " + target.title)
            .setContentText("Minęła godzina. Licznik nadal działa — wybierz, kiedy zakończyć.")
            .setStyle(new Notification.BigTextStyle().bigText(
                (target == null ? "" : "Czynność: " + target.title + "\\n")
                + "Minęła godzina, ale pomiar nie został zatrzymany. Czas nadal się nalicza."))
            .setContentIntent(target == null ? null : openAction(context, target.projectId, taskId))
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopAction(context, taskId))
            .build();
        manager.notify(reminderNotificationId(taskId), notification);
        FeedbackSignals.warning(context);
        DiagnosticLog.event("PROJECT_WORK_REMINDER_SHOWN", "task=" + taskId);
    }

    static void rescheduleActiveSessions(Context context) {
        File file = context.getDatabasePath(DATABASE);
        if (!file.isFile()) return;
        List<long[]> active = new ArrayList<>();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery(
                "SELECT task_id, started_at FROM project_task_work_sessions WHERE ended_at IS NULL",
                null)) {
            while (cursor.moveToNext()) active.add(new long[]{cursor.getLong(0), cursor.getLong(1)});
        } catch (Exception problem) {
            DiagnosticLog.error("PROJECT_WORK_REMINDER_RESCHEDULE", problem);
            return;
        }
        for (long[] session : active) {
            if (session[0] > 0L && session[1] > 0L)
                scheduleCheck(context, session[0], session[1]);
        }
        DiagnosticLog.event("PROJECT_WORK_REMINDER_INTERVAL_APPLIED", "active=" + active.size());
    }

    private static void scheduleNextCheck(Context context, long taskId) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        LegacyCompat.setAndAllowWhileIdle(alarm, AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + reminderIntervalMs(context), checkAction(context, taskId));
    }

    private static PendingIntent stopNoticeAction(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_DISMISS).putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(context,
            5100000 + (int)(taskId % 800000), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
    }

    private static PendingIntent timeoutIntent(Context context, long taskId,
            long started) {
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_TIMEOUT).putExtra("task_id", taskId)
            .putExtra("started_at", started);
        return PendingIntent.getBroadcast(context,
            4100000 + (int) (taskId % 800000), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
    }

    private static void scheduleTimeout(Context context, long taskId,
            long started) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        if (alarm != null) LegacyCompat.setAndAllowWhileIdle(alarm,
            AlarmManager.RTC_WAKEUP, started + reminderIntervalMs(context),
            timeoutIntent(context, taskId, started));
    }

    private static void cancelTimeout(Context context, long taskId) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(context,
            4100000 + (int) (taskId % 800000),
            new Intent(context, ReminderReceiver.class).setAction(ACTION_TIMEOUT),
            PendingIntent.FLAG_NO_CREATE | LegacyCompat.immutableFlag());
        if (pending != null) {
            if (alarm != null) alarm.cancel(pending);
            pending.cancel();
        }
    }

    static void dismissPaused(Context context, long taskId) {
        cancel(context, taskId);
    }

    /** Legacy alarm from older builds: turn it into a reminder, never a pause. */
    static void handleTimeout(Context context, long taskId, long started) {
        if (taskId < 0 || started <= 0) return;
        DiagnosticLog.event("PROJECT_WORK_LEGACY_TIMEOUT_REMAPPED", "task=" + taskId);
        handleCheckAction(context, taskId);
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
            FeedbackSignals.warning(context);
            showActionError(context, taskId,
                problem.getMessage() == null
                    ? "Nie udało się rozpocząć pomiaru."
                    : problem.getMessage());
            return;
        }
        showActive(context, taskId);
        FeedbackSignals.success(context);
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
            FeedbackSignals.warning(context);
            showActionError(context, taskId,
                problem.getMessage() == null
                    ? "Nie udało się zatrzymać pomiaru."
                    : problem.getMessage());
            return;
        }
        cancelCheck(context, taskId);
        cancelTimeout(context, taskId);
        cancel(context, taskId);
        showStopped(context, taskId, minutes);
        FeedbackSignals.stopped(context);
    }

    static void showActive(Context context, long taskId) {
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

        scheduleCheck(context, taskId, activeStarted);
        if (!canNotify(context)) return;
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        LegacyCompat.ensureChannel(manager, CHANNEL_ID,
            "Aktywna praca nad projektem", NotificationManager.IMPORTANCE_LOW,
            null, true, true, false);

        cancelReminder(context, taskId);
        PendingIntent open=openAction(context,projectId,taskId);

        // Backdate the chronometer by already closed sessions so the system
        // chronometer shows total worked time, not only the newest Start.
        long chronometerBase = activeStarted - closedMinutes * 60000L;

        String detail="● Praca trwa • "+title
            +"\nPlan: "+formatMinutes(plannedMinutes)
            +"\nDotknij, aby otworzyć tę czynność.";
        Notification notification = LegacyCompat.notificationBuilder(context, CHANNEL_ID)
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

    private static void cancelReminder(Context context, long taskId) {
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(reminderNotificationId(taskId));
    }

    static void cancel(Context context, long taskId) {
        cancelTimeout(context, taskId);
        cancelReminder(context, taskId);
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
        LegacyCompat.ensureChannel(manager, CHANNEL_ID,
            "Aktywna praca nad projektem", NotificationManager.IMPORTANCE_LOW,
            null, true, true, false);
        NotificationTask target=reminderTask(context,taskId);
        Notification notification = LegacyCompat.notificationBuilder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle(target==null?"EDHOME • pomiar zatrzymany"
                :"EDHOME • "+target.title)
            .setContentText("Pomiar zatrzymany • ostatnia sesja: " + formatMinutes(minutes))
            .setContentIntent(target==null?null
                :openAction(context,target.projectId,taskId))
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
        LegacyCompat.ensureChannel(manager, CHANNEL_ID,
            "Aktywna praca nad projektem", NotificationManager.IMPORTANCE_LOW,
            null, true, true, false);
        Notification notification = LegacyCompat.notificationBuilder(context, CHANNEL_ID)
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
