#!/usr/bin/env python3
"""Static safety contract for individual alarms, migration and backup."""
from pathlib import Path
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
receiver = Path("app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text(encoding="utf-8")
project_work = Path("app/src/main/java/com/edwinkarolczyk/edhome/ProjectWorkNotification.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
for expected in (
    'if (!pref.getBoolean("reminders_enabled", false)) return;',
    'Manifest.permission.POST_NOTIFICATIONS',
    'ACTION_TASK', 'ACTION_REMIND',
    '"reminder_fired_" + id',
    'if (!expected.equals(actual)',
    'ReminderRules.target(taskDue, time, lead,',
    'quietStart, quietEnd).isAfter(now)',
    'LegacyCompat.setExactOrInexactWhileIdle(context, manager,',
    'ReminderRules.nextAllowed(when,',
    'Intent.ACTION_BOOT_COMPLETED',
    'Intent.ACTION_MY_PACKAGE_REPLACED',
    'Intent.ACTION_TIMEZONE_CHANGED',
):
    assert expected in receiver, "Unsafe/missing receiver contract: " + expected
for expected in (
    'ReminderReceiver.cancelTask(this, id);',
    'ReminderReceiver.schedule(this);',
    'super(context, "edhome-beta-preview.db", null, 52',
    'ALTER TABLE tasks ADD COLUMN remind_time TEXT',
    'ALTER TABLE tasks ADD COLUMN reminder_lead_days',
):
    assert expected in main, "Missing reminder persistence contract: " + expected
assert '"remind_time", "reminder_lead_days"' in backup
# Regresja 2026-10-18: nie wolno planować alarmu na 08:00
# i odrzucać go w odbiorniku przed 09:00.
assert 'next.set(Calendar.HOUR_OF_DAY, 8);' in receiver
assert 'now.getHour() < 9' not in receiver
assert 'pref.getString("reminder_legacy_day", "")' in receiver
assert 'next.setTimeInMillis(epoch(ReminderRules.nextAllowed(' in receiver
assert 'QuietHoursRules.validWindow(quietStart, quietEnd)' in receiver
assert 'int overdue = 0;' in receiver
assert 'tasks.getString(3).compareTo(today.toString()) < 0' in receiver
assert '"Zaległe: " + overdue' in receiver
assert 'daily inexact 08:00 alarm' in receiver
assert 'Standardowo ok. 08:00' in main
assert '? "08:00" : savedReminder[0]' in main
assert main.count('reminder.setText("08:00");') >= 2
assert 'inputVersion < 10 && "tasks".equals(definition[0])' in backup
assert 'ReminderRules.validTime(remindAt)' in backup

# Project reminders can start work, then become a persistent stopwatch with Stop.
for expected in (
    'ProjectWorkNotification.ACTION_START.equals(action)',
    'ProjectWorkNotification.ACTION_STOP.equals(action)',
    '"▶ Start", ProjectWorkNotification.startAction(',
    'ProjectWorkNotification.restoreActive(context)',
):
    assert expected in receiver, "Missing project reminder action: " + expected

for expected in (
    'setUsesChronometer(true)',
    'setOngoing(true)',
    'setAutoCancel(false)',
    'Notification.FLAG_ONGOING_EVENT',
    'Notification.FLAG_NO_CLEAR',
    '"■ Stop", stopAction(context, taskId)',
    'ProjectStore.startWork(database, taskId)',
    'ProjectStore.stopWork(database, taskId)',
    'PROJECT_WORK_STARTED_FROM_NOTIFICATION',
    'PROJECT_WORK_STOPPED_FROM_NOTIFICATION',
    'WHERE s.ended_at IS NULL AND t.done=0',
):
    assert expected in project_work, "Missing persistent project timer notification: " + expected

assert 'ProjectWorkNotification.showActive(this,taskId);' in main
assert 'ProjectWorkNotification.cancel(this,taskId);' in main
# P0: kliknięcie w przypomnienie prowadzi do jednoznacznego zadania.
# Każde przypomnienie ma własny PendingIntent (brak nadpisywania extras).
for expected in (
    '"open_task_id",singleTaskId',
    '"open_task_id",firstDailyTaskId',
    '"open_task_reminder_list",true',
    'destination.setAction("com.edwinkarolczyk.edhome.OPEN_REMINDER."+openCode)',
    'PendingIntent.getActivity(context,openCode,destination,',
    'StringBuilder dailyTaskNames',
    'dailyTaskNames.append("• ")',
    '"Znajdź nowe terminy", openPlanning',
):
    assert expected in receiver, "Missing task reminder route/title: " + expected

for expected in (
    'private boolean openTaskReminderIntent(Intent intent,boolean renderNow)',
    'if (openTaskReminderIntent(intent,true)) return;',
    'root.post(() -> openTaskReminderIntent(getIntent(),true));',
    'root.post(()->openTaskReminderIntent(getIntent(),true));',
    'private void showRemindedTaskList()',
    'private void openRemindedTask(long taskId,boolean renderNow)',
    'if(renderNow)openProjectTask(taskId);',
    'tasksPage=0;',
    'pendingTaskFocusId=taskId;',
    'pendingTaskFocusView=box;',
    'actions.setVisibility(fromReminder?View.VISIBLE:View.GONE);',
    'scroll.scrollTo(0,destination);',
):
    assert expected in main, "Missing in-app task deep link: " + expected

for expected in (
    'private static NotificationTask reminderTask(Context context,long taskId)',
    'target==null ? "EDHOME • pomiar wstrzymany"',
    'openAction(context,target.projectId,taskId)',
    '"Czynność: "+target.title',
):
    assert expected in project_work, "Missing named 60-minute project notice: " + expected

# Regresja Android 0.8.1.153: ekran Czynności nie może wrócić do trzech kart.
# Kliknięcie w przypomnienie nadal przewija do właściwej karty bez stronicowania.
tasks_screen = main.split("    private void tasks() {", 1)[1].split(
    "    private void smallButton(", 1
)[0]
assert 'tasksPage = 0; // Lista przewijana zamiast stron po trzy pozycje.' in tasks_screen
assert 'ORDER BY done ASC,' in tasks_screen
assert 'CASE WHEN due_date IS NULL THEN 1 ELSE 0 END,' in tasks_screen
assert 'due_date ASC,id DESC' in tasks_screen
assert 'while (cursor.moveToNext())' in tasks_screen
assert 'LIMIT 3' not in tasks_screen
assert 'OFFSET' not in tasks_screen
assert 'position/3' not in main
assert 'private String tasksFilter = "all";' in main
print("Czynności: pełna lista bez limitu 3 i od najwcześniejszego terminu: PASS")

# Regresja Start/Stop: przyciski aplikacji muszą uruchamiać te same
# sygnały co działania z powiadomienia (Android 5.1+).
feedback = Path("app/src/main/java/com/edwinkarolczyk/edhome/FeedbackSignals.java").read_text(
    encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
start_card = main.split("ProjectStore.startWork(db.getWritableDatabase(),taskId);", 1)[1].split(
    'DiagnosticLog.event("PROJECT_WORK_STARTED"', 1)[0]
stop_card = main.split("ProjectStore.stopWork(\n", 1)[1].split(
    'DiagnosticLog.event("PROJECT_WORK_STOPPED"', 1)[0]
assert "FeedbackSignals.success(this);" in start_card
assert "FeedbackSignals.stopped(this);" in stop_card
assert "FeedbackSignals.success(context);" in project_work
assert "FeedbackSignals.stopped(context);" in project_work
assert 'android.permission.VIBRATE' in manifest
assert 'android.permission.POST_NOTIFICATIONS' in manifest
assert "Vibrator vibrator" in feedback and "vibrator.vibrate(" in feedback
assert "ToneGenerator generator" in feedback and "startTone(tone, durationMs)" in feedback
assert 'DiagnosticLog.error("FEEDBACK_AUDIO", problem)' in feedback
assert 'DiagnosticLog.error("FEEDBACK_VIBRATION", problem)' in feedback
assert "static String diagnostics(Context context)" in feedback
assert 'FeedbackSignals.diagnostics(this)' in main
assert "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS" in main
assert '"edhome-task-due-alert-v3"' in main
print("PASS: dźwięk i wibracje Start/Stop, diagnostyka i ustawienia kanału")

print("Project reminder Start + persistent chronometer + Stop contract: PASS")
print("Reminder opt-in, dedup, re-arm, SQLite/backup and quiet-hours contracts: PASS")