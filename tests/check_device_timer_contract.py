#!/usr/bin/env python3
"""Appliance timers: opt-in, backup and inexact alarm lifecycle contract."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
receiver = Path("app/src/main/java/com/edwinkarolczyk/edhome/DeviceTimerReceiver.java").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
rules = Path("app/src/main/java/com/edwinkarolczyk/edhome/DeviceTimerRules.java").read_text(encoding="utf-8")

for token in (
    'addDeviceTimers(database);',
    'DATABASE_MIGRATED_11_TO_12_DEVICE_TIMERS',
    'CREATE TABLE device_timers',
    '"device_timers", null, values',
    'AND status=\'running\'',
    '"timers": timers();',
    'button("↻ Odśwież pozostały czas"',
    'DeviceTimerReceiver.scheduleAll(this);',
    'DeviceTimerReceiver.cancel(this, id);',
    'device_timers WHERE status=\'running\'',
):
    assert token in main + receiver, "Missing timer code: " + token

assert "timer_notifications_enabled" in main and "timer_notifications_enabled" in receiver
assert "timer_notifications_enabled" in backup
assert '"device_timers", "id", "device_type", "title", "start_at", "end_at"' in backup
assert 'inputVersion < 12 && "device_timers".equals(definition[0])' in backup
assert "DeviceTimerRules.validRecord(" in backup
assert '"acknowledged_at".equals(key)' in backup
assert "NotificationManager.IMPORTANCE_DEFAULT" in receiver
assert "AlarmManager.RTC_WAKEUP" in receiver
assert "setAndAllowWhileIdle(" in receiver
assert "ReminderRules.nextAllowed(" in receiver
assert 'context.checkSelfPermission(' in receiver
assert 'context.getDatabasePath(DB)' in receiver
assert '"WHERE id=? AND status=\'running\'"' in receiver
assert "c.getLong(2) != expected" in receiver
assert 'timer_notified_" + id' in receiver
assert "newVersion > 16" in main and "DB_VERSION = 16;" in backup
assert '.DeviceTimerReceiver"' in manifest and "RECEIVE_BOOT_COMPLETED" in manifest
assert "MIN_MINUTES = 1" in rules and "MAX_MINUTES = 1440" in rules
assert "DeviceTimerReceiver.scheduleAll(this);" in main
assert 'prefs.edit().putBoolean("timer_notifications_enabled"' in main
print("Device timer alarms, backup, opt-in, UI, reboot, stale event guard: PASS")
