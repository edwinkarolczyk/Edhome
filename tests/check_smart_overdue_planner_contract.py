#!/usr/bin/env python3
"""P0 kontrakt bezpiecznego przeplanowania zaległych czynności w Beta."""
from pathlib import Path

base=Path("app/src/main/java/com/edwinkarolczyk/edhome")
planner=(base/"SmartTaskPlanner.java").read_text(encoding="utf-8")
slots=(base/"TimeSlotRules.java").read_text(encoding="utf-8")
main=(base/"MainActivity.java").read_text(encoding="utf-8")
receiver=(base/"ReminderReceiver.java").read_text(encoding="utf-8")
backup=(base/"DataBackup.java").read_text(encoding="utf-8")
for token in (
    "LOOKAHEAD_DAYS = 28",
    "options.size()<3",
    "ProjectPlanningStore.windows(db,memberId,shift)",
    "member_shift_exceptions",
    "member_weekly_shifts",
    "ProjectStore.remainingMinutes(db,task.id,task.duration)",
    "ProjectPlanningStore.hasUndatedHardBlocker",
    "project_task_dependencies",
    "project_task_work_sessions",
    "TimeSlotRules.overlaps(a,b,start,end)",
    "TimeSlotRules.ceilQuarter(now.plusSeconds(1))",
    "SELECT remind_time,duration_minutes FROM tasks",
    "SELECT start_at,end_at FROM device_timers",
    "remind_time",
    "reminder_lead_days",
    "WHERE t.done=0 AND t.due_date IS NOT NULL AND t.due_date<?",
    "AND t.task_kind<>'waste'",
    "if(!valid)throw new IllegalStateException(",
    "db.beginTransaction()",
    "db.setTransactionSuccessful()",
    "id=? AND done=0 AND due_date=? AND repeat_rule='once'",
    "TASK_REPLANNED",
):
    assert token in planner, "Brak kontraktu planera: "+token

assert 'remind_time TEXT' in main
assert '"remind_time", "reminder_lead_days"' in backup
assert 'super(context, "edhome-beta-preview.db", null, 50' in main
for token in (
    "SmartTaskPlanner.proposeNew(",
    "SmartTaskPlanner.proposeForProject(",
    "plannedStarts.put(taskId,chosen.start.toString())",
    'values.put("remind_time",plannedStarts.get(item.getKey()))',
    "private boolean openOverduePlanIntent(Intent intent)",
    'intent.removeExtra("open_overdue_plan")',
    "private void showOverduePlanList()",
    "private void showOverduePlanOptions(",
    "SmartTaskPlanner.approve(",
    "ReminderReceiver.schedule(this);",
    'button("↻ Zaplanuj bez terminu i zaległe"',
):
    assert token in main, "Brak interfejsu zatwierdzania: "+token
for token in (
    "SmartTaskPlanner.planningOverview(planner)",
    "TASK_OVERDUE_SUGGESTIONS_READY",
    "open_overdue_plan",
    "Znajdź nowe terminy",
    "ReminderRules.nextAllowed",
    'pref.getBoolean("reminders_enabled", false)',
    "QuietHoursRules.isQuiet",
):
    assert token in receiver, "Brak bezpiecznego powiadomienia: "+token
assert "aStart.isBefore(bEnd)&&aEnd.isAfter(bStart)" in slots
assert "TimeSuggestions.proposeAvailability(" not in main
assert "rounded.plusMinutes(15)" in slots

# Zbiorcze przeplanowanie nie opuszcza okna po pierwszej z kilku zmian.
flow=main.split("private void showOverduePlanOptions(",1)[1].split(
    "private boolean backupLegacyExportNext;",1)[0]
list_flow=main.split("private void showOverduePlanList()",1)[1].split(
    "private void showOverduePlanOptions(",1)[0]
assert "pendingPlanningTasks()" in list_flow
assert '"once".equals(task.repeatRule)' in main
assert "SmartTaskPlanner.undated(" in main
assert "SmartTaskPlanner.approveUndated(" in flow
assert "showTaskPlannerAssignee(task);" in flow
assert 'pozostało: "+items.size()' in list_flow
assert "if(pendingPlanningTasks().isEmpty())" in flow
assert "showOverduePlanList();" in flow
assert "render();" in flow
assert 'go("tasks");' not in flow
assert '"Wróć do listy"' in flow
assert '"Wróć do terminów"' in flow

# Nazwy dni to tylko etykiety; przechowywane daty ISO nie są konwertowane.
labels=(base/"TaskDateLabels.java").read_text(encoding="utf-8")
shortcut=(base/"QuickTaskShortcut.java").read_text(encoding="utf-8")
assert 'Locale.forLanguageTag("pl-PL")' in labels
assert 'DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy"' in labels
assert "TaskDateLabels.format(task.due)" in main
assert "TaskDateLabels.format(option.date)" in main
assert "TaskDateLabels.format(o.date)" in shortcut
print("Smart planner: wiele terminów w jednym przebiegu, polskie dni tygodnia i backup v50 PASS")
