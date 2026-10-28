import pathlib
service = pathlib.Path("app/src/main/java/com/edwinkarolczyk/edhome/TaskAlarmService.java").read_text()
receiver = pathlib.Path("app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text()
manifest = pathlib.Path("app/src/beta/AndroidManifest.xml").read_text()
picker = pathlib.Path("app/src/main/java/com/edwinkarolczyk/edhome/SnoozePickerActivity.java").read_text()
assert '"Odłóż…", snoozePending' in service
assert 'Math.max(1, Math.min(120,' in receiver
assert 'snooze_minutes' in receiver and 'SnoozePickerActivity' in manifest
assert 'slider.setMax(119)' in picker and 'setPositiveButton("Odłóż alarm"' in picker
assert '.putExtra("occurrence", firedValue)' in receiver
assert 'REMINDER_ALERT_DISMISSED' in receiver
assert 'manager.cancel("edhome-task-" + id, ALARM_ID)' not in receiver
print("PASS: snooze picker, bounded minutes, occurrence binding, manifest registration, and mute without task notification loss")
