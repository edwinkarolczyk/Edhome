from pathlib import Path

root=Path(__file__).resolve().parents[1]
receiver=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text(encoding="utf-8")
compat=(root/"app/src/main/java/com/edwinkarolczyk/edhome/LegacyCompat.java").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")

assert 'android.permission.SCHEDULE_EXACT_ALARM' in manifest
assert 'manager.setExactAndAllowWhileIdle(type, triggerAtMillis, operation)' in compat
assert 'canScheduleExactAlarms()' in compat
assert 'LegacyCompat.setExactOrInexactWhileIdle(context, manager' in receiver
assert 'edhome-task-due-alert-v3' in receiver
assert 'Notification.CATEGORY_ALARM' in receiver
assert 'Włącz dokładne alarmy o godzinie' in main
assert 'Godzina przypomnienia' in main
assert 'COMPLETE_TASK_FROM_REMINDER' in main
assert 'completeTaskFromReminderIntent(getIntent())' in main
assert '✓ Wykonane' in receiver
assert 'Wyłącz alarm' in receiver
assert 'Drzemka 5 min' in receiver
assert 'ACTION_SNOOZE_ALERT' in receiver
assert 'smallButton(form, "Wybierz godzinę", () -> reminderHour.performClick())' in main
assert 'ACTION_REQUEST_SCHEDULE_EXACT_ALARM' in main
assert 'versionCode 352' in gradle and "versionName '0.8.1.159'" in gradle

print("task reminder exact-alarm contract OK")
