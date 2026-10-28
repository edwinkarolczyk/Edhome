import pathlib

service = pathlib.Path("app/src/main/java/com/edwinkarolczyk/edhome/TaskAlarmService.java").read_text()
receiver = pathlib.Path("app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text()
layout = pathlib.Path("app/src/main/res/layout/notification_task_alarm_snooze.xml").read_text()
assert ".setCustomBigContentView(expanded)" in service
assert "SnoozePickerActivity" not in service
for minutes in (5, 10, 15, 30, 60):
    assert f'R.id.task_snooze_{minutes}' in service
    assert f'android:id="@+id/task_snooze_{minutes}"' in layout
assert 'R.id.task_alarm_mute' in service and 'android:id="@+id/task_alarm_mute"' in layout
assert 'Math.max(1, Math.min(120,' in receiver
assert 'snooze_minutes' in receiver
assert '.putExtra("occurrence", firedValue)' in receiver
assert 'REMINDER_ALERT_DISMISSED' in receiver
assert 'manager.cancel("edhome-task-" + id, ALARM_ID)' not in receiver
print("PASS: expanded notification has direct 5/10/15/30/60-minute snooze buttons and mute; no picker activity; occurrence binding and notification preservation")
