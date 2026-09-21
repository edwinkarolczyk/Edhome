#!/usr/bin/env python3
"""Static safety contract for individual alarms, migration and backup."""
from pathlib import Path
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
receiver = Path("app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
for expected in (
    'if (!pref.getBoolean("reminders_enabled", false)) return;',
    'Manifest.permission.POST_NOTIFICATIONS',
    'ACTION_TASK', 'ACTION_REMIND',
    '"reminder_fired_" + id',
    'if (!expected.equals(actual)',
    'ReminderRules.target(taskDue, time, lead,',
    'quietStart, quietEnd).isAfter(now)',
    'manager.setAndAllowWhileIdle(',
    'ReminderRules.nextAllowed(when,',
    'Intent.ACTION_BOOT_COMPLETED',
    'Intent.ACTION_MY_PACKAGE_REPLACED',
    'Intent.ACTION_TIMEZONE_CHANGED',
):
    assert expected in receiver, "Unsafe/missing receiver contract: " + expected
for expected in (
    'ReminderReceiver.cancelTask(this, id);',
    'ReminderReceiver.schedule(this);',
    'super(context, "edhome-beta-preview.db", null, 18',
    'ALTER TABLE tasks ADD COLUMN remind_time TEXT',
    'ALTER TABLE tasks ADD COLUMN reminder_lead_days',
):
    assert expected in main, "Missing reminder persistence contract: " + expected
assert '"remind_time", "reminder_lead_days"' in backup
assert 'inputVersion < 10 && "tasks".equals(definition[0])' in backup
assert 'ReminderRules.validTime(remindAt)' in backup
print("Reminder opt-in, dedup, re-arm, SQLite/backup and quiet-hours contracts: PASS")
