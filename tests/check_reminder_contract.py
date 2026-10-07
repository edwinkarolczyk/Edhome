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
    'LegacyCompat.setAndAllowWhileIdle(manager,',
    'ReminderRules.nextAllowed(when,',
    'Intent.ACTION_BOOT_COMPLETED',
    'Intent.ACTION_MY_PACKAGE_REPLACED',
    'Intent.ACTION_TIMEZONE_CHANGED',
):
    assert expected in receiver, "Unsafe/missing receiver contract: " + expected
for expected in (
    'ReminderReceiver.cancelTask(this, id);',
    'ReminderReceiver.schedule(this);',
    'super(context, "edhome-beta-preview.db", null, 45',
    'ALTER TABLE tasks ADD COLUMN remind_time TEXT',
    'ALTER TABLE tasks ADD COLUMN reminder_lead_days',
):
    assert expected in main, "Missing reminder persistence contract: " + expected
assert '"remind_time", "reminder_lead_days"' in backup
assert 'next.set(Calendar.HOUR_OF_DAY, 8);' in receiver
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
print("Project reminder Start + persistent chronometer + Stop contract: PASS")
print("Reminder opt-in, dedup, re-arm, SQLite/backup and quiet-hours contracts: PASS")