#!/usr/bin/env python3
"""EDHOME Beta: two planning scans, undated tasks, assignee safety, voice input."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/com/edwinkarolczyk/edhome"
planner=(base/"SmartTaskPlanner.java").read_text(encoding="utf-8")
receiver=(base/"ReminderReceiver.java").read_text(encoding="utf-8")
main=(base/"MainActivity.java").read_text(encoding="utf-8")
quick=(base/"QuickTaskShortcut.java").read_text(encoding="utf-8")

assert 'ACTION_PLAN =' in receiver
assert 'CHECK_TASK_PLANNING' in receiver
assert 'night.set(Calendar.HOUR_OF_DAY,19)' in receiver
assert 'next.set(Calendar.HOUR_OF_DAY, 8)' in receiver
assert 'manager.setInexactRepeating(AlarmManager.RTC_WAKEUP' in receiver
assert 'planningOverview(planner)' in receiver
assert '"task_planner_last_notice",""' in receiver
assert 'if (planningFresh && planning!=null)' in receiver
assert 'if (!legacy && !plannerOnly)' in receiver
assert 'if (!pref.getBoolean("reminders_enabled", false)) return;' in receiver
assert 'QuietHoursRules.isQuiet' in receiver
assert 'open_overdue_plan' in receiver
assert 'due==0&&!planningFresh' in receiver
assert 'plannerOnly&&!planningFresh' in receiver

assert 'static List<Task> undated(SQLiteDatabase db,int limit)' in planner
assert "(t.due_date IS NULL OR t.due_date='')" in planner
assert 't.repeat_rule=\'once\'' in planner
assert 'static List<TimeSuggestions.Option> proposeUndated(' in planner
assert 'static void approveUndated(' in planner
assert "AND (due_date IS NULL OR due_date='')" in planner
assert 'static void assignIfMissing(' in planner
assert 'task.memberId<=0' in planner
assert 'db.beginTransaction()' in planner
assert 'if(!valid)throw new IllegalStateException(' in planner
assert 'public' not in planner.split('class PlanningOverview',1)[0][-100:]

assert '"Kto ma wykonać? (wymagane)"' in quick
assert 'names.add("Wybierz domownika…")' in quick
assert 'person.getSelectedItemPosition()' in quick
assert 'members.get(memberIndex-1).id' in quick
assert 'speech.start(title)' in quick
assert 'duration.setSelection(0);' in quick
assert 'date.setSelection(2);' in quick
assert 'SmartTaskPlanner.proposeNew(' in main
assert 'SmartTaskPlanner.proposeUndated(' in main
assert 'this::launchQuickTaskSpeech' in main
assert 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' in main
assert '"pl-PL"' in main
assert 'RecognizerIntent.EXTRA_RESULTS' in main
assert 'launcherQuickTaskDialog.isShowing()' in main
assert 'quickTaskSpeechTarget = null;' in main
assert 'SmartTaskPlanner.assignIfMissing(' in main
assert 'if(task.due.isEmpty())SmartTaskPlanner.approveUndated(' in main
assert 'ReminderReceiver.schedule(this);' in main
print("EDHOME: 8/19, overdue + undated, selected user, no auto-commit, speech pl-PL PASS")
