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
    "private boolean openOverduePlanIntent(Intent intent)",
    'intent.removeExtra("open_overdue_plan")',
    "private void showOverduePlanList()",
    "private void showOverduePlanOptions(",
    "SmartTaskPlanner.approve(",
    "ReminderReceiver.schedule(this);",
    'button("↻ Znajdź nowe terminy zaległych"',
):
    assert token in main, "Brak interfejsu zatwierdzania: "+token
for token in (
    "SmartTaskPlanner.hasProposals(planning,",
    "TASK_OVERDUE_SUGGESTIONS_READY",
    "open_overdue_plan",
    "Znajdź nowe terminy",
    "ReminderRules.nextAllowed",
    'pref.getBoolean("reminders_enabled", false)',
    "QuietHoursRules.isQuiet",
):
    assert token in receiver, "Brak bezpiecznego powiadomienia: "+token
assert "aStart.isBefore(bEnd)&&aEnd.isAfter(bStart)" in slots
assert "rounded.plusMinutes(15)" in slots
print("Smart planner: 28 dni, 3 propozycje, kolizje, zatwierdzenie, alarm i backup v50 PASS")
