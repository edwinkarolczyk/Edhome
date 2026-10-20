package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
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
 * Opt-in reminders. Legacy tasks use a daily inexact 08:00 alarm; tasks with
 * custom time get one inexact alarm per task/occurrence. No exact-alarm permission
 * or background network is required. A completed/edited task cannot send a
 * stale old alarm because both the ID and occurrence fingerprint are checked.
 */
public final class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION_REMIND =
        "com.edwinkarolczyk.edhome.REMIND_TASKS";
    private static final String ACTION_TASK =
        "com.edwinkarolczyk.edhome.REMIND_SINGLE_TASK";
    private static final String ACTION_PLAN =
        "com.edwinkarolczyk.edhome.CHECK_TASK_PLANNING";
    private static final String CHANNEL_ID = "edhome-task-due";
    private static final int ALARM_ID = 7125;
    private static final int PLAN_ALARM_ID = 7126;
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
            flags | LegacyCompat.immutableFlag());
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
        LegacyCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP,
            epoch(when), pending);
    }

    static void schedule(Context context) {
        // Active Project timers are independent from the reminder toggle.
        ProjectWorkNotification.restoreActive(context);
        // Vehicle and PayCheck reminders have their own scheduling rules.
        VehicleReminderReceiver.schedule(context);
        PaycheckBudgetReminderReceiver.schedule(context);
        FrostReminderReceiver.schedule(context);
        StorageHomeReminderReceiver.schedule(context);
        AlarmManager manager = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class)
            .setAction(ACTION_REMIND);
        PendingIntent daily = PendingIntent.getBroadcast(context, ALARM_ID,
            intent, PendingIntent.FLAG_UPDATE_CURRENT
                | LegacyCompat.immutableFlag());
        manager.cancel(daily);
        Intent planningIntent=new Intent(context,ReminderReceiver.class)
            .setAction(ACTION_PLAN);
        PendingIntent evening=PendingIntent.getBroadcast(context,PLAN_ALARM_ID,
            planningIntent,PendingIntent.FLAG_UPDATE_CURRENT
                |LegacyCompat.immutableFlag());
        manager.cancel(evening);
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
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis())
            next.add(Calendar.DAY_OF_MONTH, 1);
        // Jeśli 08:00 wypada w ciszy nocnej, zaczekaj do jej końca.
        // Codzienne przypomnienie nie może zostać pominięte bez śladu.
        String quietStart = pref.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String quietEnd = pref.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        if (!QuietHoursRules.validWindow(quietStart, quietEnd)) {
            quietStart = QuietHoursRules.DEFAULT_START;
            quietEnd = QuietHoursRules.DEFAULT_END;
        }
        LocalDateTime planned = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(next.getTimeInMillis()),
            ZoneId.systemDefault());
        next.setTimeInMillis(epoch(ReminderRules.nextAllowed(
            planned, quietStart, quietEnd)));
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
            next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, daily);
        if(BetaUpdater.isBeta()) {
            Calendar night=Calendar.getInstance();
            night.set(Calendar.HOUR_OF_DAY,19);
            night.set(Calendar.MINUTE,0);
            night.set(Calendar.SECOND,0);
            night.set(Calendar.MILLISECOND,0);
            if(night.getTimeInMillis()<=System.currentTimeMillis())
                night.add(Calendar.DAY_OF_MONTH,1);
            LocalDateTime eveningTime=LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(night.getTimeInMillis()),
                ZoneId.systemDefault());
            night.setTimeInMillis(epoch(ReminderRules.nextAllowed(
                eveningTime,quietStart,quietEnd)));
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                night.getTimeInMillis(),AlarmManager.INTERVAL_DAY,evening);
        }
        DiagnosticLog.event("REMINDER_SCHEDULED");
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ProjectWorkNotification.ACTION_DISMISS.equals(action)) {
            ProjectWorkNotification.cancel(context, intent.getLongExtra("task_id", -1L));
            return;
        }
        if (ProjectWorkNotification.ACTION_CHECK.equals(action)) {
            ProjectWorkNotification.handleCheckAction(
                context, intent.getLongExtra("task_id", -1L));
            return;
        }
        if (ProjectWorkNotification.ACTION_DISMISS.equals(action)) {
            ProjectWorkNotification.dismissPaused(context,
                intent.getLongExtra("task_id", -1L));
            return;
        }
        if (ProjectWorkNotification.ACTION_TIMEOUT.equals(action)) {
            ProjectWorkNotification.handleTimeout(context,
                intent.getLongExtra("task_id", -1L),
                intent.getLongExtra("started_at", -1L));
            return;
        }
        if (ProjectWorkNotification.ACTION_START.equals(action)) {
            ProjectWorkNotification.handleStartAction(
                context, intent.getLongExtra("task_id", -1L));
            return;
        }
        if (ProjectWorkNotification.ACTION_STOP.equals(action)) {
            ProjectWorkNotification.handleStopAction(
                context, intent.getLongExtra("task_id", -1L));
            return;
        }
        if (!ACTION_REMIND.equals(action)
                && !ACTION_TASK.equals(action) && !ACTION_PLAN.equals(action)) {
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
        int overdue = 0;
        int waste = 0;
        String firedKey = null;
        String firedValue = null;
        long singleTaskId = -1L;
        String singleTaskTitle = "";
        boolean singleProjectTask = false;
        boolean legacy = ACTION_REMIND.equals(action);
        boolean plannerOnly=ACTION_PLAN.equals(action);
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            if (legacy) {
                // Daily digest is independent of customized on-time alerts.
                // Overdue personalized tasks still appear on later days.
                // Alarm uruchamia się około 08:00. Warunek godziny 09:00
                // blokował wszystkie powiadomienia, które dotarły punktualnie.
                if (today.toString().equals(
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
                        if (tasks.getString(3).compareTo(today.toString()) < 0)
                            overdue++;
                        if ("waste".equals(tasks.getString(1))) waste++;
                    }
                }
            } else if(!plannerOnly) {
                long id = intent.getLongExtra("task_id", -1L);
                String expected = intent.getStringExtra("occurrence");
                if (id < 0 || expected == null) return;
                try (Cursor task = database.rawQuery(
                        "SELECT due_date,remind_time,reminder_lead_days,"
                        + "task_kind,title,project_id FROM tasks "
                        + "WHERE id=? AND done=0",
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
                    singleTaskId = id;
                    singleTaskTitle = task.isNull(4) ? "" : task.getString(4);
                    singleProjectTask = !task.isNull(5);
                    firedKey = "reminder_fired_" + id;
                    firedValue = actual;
                }
            }
        } catch (Exception problem) {
            DiagnosticLog.error("REMINDER_DATABASE", problem);
            return;
        }
        // Każdego ranka i wieczoru nowe terminy szukane są lokalnie.
        // Nie zapisujemy dat bez wyboru przez domownika.
        SmartTaskPlanner.PlanningOverview planning=null;
        boolean planningTick=(legacy||plannerOnly)&&BetaUpdater.isBeta();
        boolean planningFresh=false;
        if(planningTick) {
            try(SQLiteDatabase planner=SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
                planning=SmartTaskPlanner.planningOverview(planner);
                planningFresh=planning.actionable()
                    &&!planning.digest.equals(pref.getString(
                        "task_planner_last_notice",""));
                DiagnosticLog.event("TASK_PLANNING_SCAN",
                    "morning="+legacy+" suggested="+planning.suggested
                        +" missing="+planning.needsAssignee);
            } catch(Exception problem) {
                DiagnosticLog.error("TASK_PLANNING_SCAN",problem);
            }
        }
        if((plannerOnly&&!planningFresh)||(due==0&&!planningFresh)) {
            if(legacy)pref.edit().putString("reminder_legacy_day",
                today.toString()).apply();
            return;
        }
        if (!legacy && singleProjectTask
                && ProjectWorkNotification.isActive(context, singleTaskId)) {
            ProjectWorkNotification.showActive(context, singleTaskId);
            pref.edit().putString(firedKey, firedValue)
                .putString("reminder_custom_day_" + singleTaskId,
                    today.toString()).apply();
            DiagnosticLog.event("REMINDER_PROJECT_ALREADY_ACTIVE",
                "task=" + singleTaskId);
            schedule(context);
            return;
        }
        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null || !LegacyCompat.notificationsEnabled(notifications))
            return;
        LegacyCompat.ensureChannel(notifications, CHANNEL_ID,
            "Przypomnienia o czynnościach", NotificationManager.IMPORTANCE_DEFAULT,
            null, true, true, false);
        boolean proposalsReady=planning!=null&&planning.suggested>0;
        int missingAssignee=planning==null?0:planning.needsAssignee;
        boolean openOverdue=planningTick&&(
            planningFresh||(legacy&&overdue>0));
        if(proposalsReady)DiagnosticLog.event("TASK_OVERDUE_SUGGESTIONS_READY");
        Intent destination=new Intent(context, MainActivity.class).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (openOverdue) destination.putExtra("open_overdue_plan",true);
        PendingIntent open = PendingIntent.getActivity(context, 0,destination,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
        final boolean foundTerms=proposalsReady;
        Notification.Builder builder = LegacyCompat.notificationBuilder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle(!legacy && singleProjectTask
                && !singleTaskTitle.isEmpty()
                    ? "EDHOME • " + singleTaskTitle
                    : "EDHOME • czynności")
            .setContentText(!legacy && singleProjectTask
                ? "Czynność projektu • możesz uruchomić pomiar czasu"
                : plannerOnly
                    ? "Nowe terminy: "+(planning==null?0:planning.suggested)
                        +" • wskaż wykonawcę: "+missingAssignee
                    : (overdue > 0
                    ? "Zaległe: " + overdue + " • na dziś: " + (due - overdue)
                        + (foundTerms ? " • znaleziono nowe terminy"
                            : " • sprawdź terminy")
                        + (waste > 0 ? " • odpady: " + waste : "")
                    : (waste > 0
                        ? "Odpady do wystawienia: " + waste
                            + " • pozostałe czynności: " + (due - waste)
                        : "Czynności do wykonania: " + due
                            +(planningFresh?" • sprawdź propozycje":""))))
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true);
        if (openOverdue)
            builder.addAction(android.R.drawable.ic_menu_today,
                "Znajdź nowe terminy", open);
        if (!legacy && singleProjectTask)
            builder.addAction(android.R.drawable.ic_media_play,
                "▶ Start", ProjectWorkNotification.startAction(
                    context, singleTaskId));
        Notification notification = builder.build();
        int notificationId = plannerOnly ? PLAN_ALARM_ID
            : legacy ? ALARM_ID
            : requestCode(intent.getLongExtra("task_id", -1L));
        notifications.notify(notificationId, notification);
        SharedPreferences.Editor editor = pref.edit();
        if (legacy) editor.putString("reminder_legacy_day", today.toString());
        if (planningFresh && planning!=null)
            editor.putString("task_planner_last_notice",planning.digest);
        if (!legacy && !plannerOnly)
            editor.putString(firedKey, firedValue)
                .putString("reminder_custom_day_"
                    + intent.getLongExtra("task_id", -1L), today.toString());
        editor.apply();
        DiagnosticLog.event("REMINDER_DELIVERED");
        if (!legacy) schedule(context);
    }
}
