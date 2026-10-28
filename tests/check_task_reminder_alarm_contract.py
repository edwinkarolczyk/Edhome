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
alarm_service=(root/"app/src/main/java/com/edwinkarolczyk/edhome/TaskAlarmService.java").read_text(encoding="utf-8")
beta_manifest=(root/"app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'TaskAlarmService.ACTION_START' in receiver
assert 'startForegroundService(ringing)' in receiver
assert 'context.stopService(new Intent(context, TaskAlarmService.class))' in receiver
assert 'player.setLooping(true)' in alarm_service
assert 'vibrator.vibrate(pattern, 0)' in alarm_service
assert 'Wyłącz alarm' in alarm_service
assert 'android:foregroundServiceType="mediaPlayback"' in beta_manifest
assert 'FOREGROUND_SERVICE_MEDIA_PLAYBACK' in beta_manifest
daily_method=receiver.split("private static void notifyDailyTask",1)[1].split(
    "private static void notifyPlanning",1)[0]
assert "singleTaskId" not in daily_method and "plannerOnly" not in daily_method
receive_method=receiver.split("@Override public void onReceive",1)[1]
assert ("if (!legacy && !plannerOnly && singleTaskId > 0L)\n"
        "            addTaskActions(context, builder, singleTaskId, firedValue);") in receive_method
assert 'smallButton(form, "Wybierz godzinę", () -> reminderHour.performClick())' in main
assert 'ACTION_REQUEST_SCHEDULE_EXACT_ALARM' in main
assert 'versionCode 354' in gradle and "versionName '0.8.1.161'" in gradle

print("task reminder exact-alarm contract OK")

# Główna lista Czynności: zielone wykonane, czerwone zaległe, niebieskie pozostałe.
assert 'taskStatusSurface(Color.rgb(220, 55, 55))' in main
assert 'taskStatusSurface(Color.rgb(45, 130, 230))' in main
assert 'final boolean overdue = !done && !due.isEmpty()' in main
