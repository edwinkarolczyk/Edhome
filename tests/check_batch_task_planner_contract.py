#!/usr/bin/env python3
"""Wsadowe propozycje czynności, polskie daty i atomowe zatwierdzenie."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(base/"MainActivity.java").read_text(encoding="utf-8")
planner=(base/"SmartTaskPlanner.java").read_text(encoding="utf-8")
batch=(base/"BatchTaskPlanner.java").read_text(encoding="utf-8")
labels=(base/"TaskDateLabels.java").read_text(encoding="utf-8")
workflow=(root/".github/workflows/android-beta.yml").read_text(encoding="utf-8")

for token in (
    '.setPositiveButton("Dopasuj terminy automatycznie",',
    'private void showBatchPlanPreview()',
    'BatchTaskPlanner.create(',
    'BatchTaskPlanner.approve(',
    'new AlertDialog.Builder(this)',
    'showTaskPlannerAssignee(selected.task,true);',
    'setTitle("Propozycje (niezapisane)',
    'Zapisz dopasowane (',
    'Zatwierdź i zapisz',
    'Przelicz ponownie',
    'ReminderReceiver.schedule(this);',
    'if(returnToBatch)showBatchPlanPreview();',
):
    assert token in main, token

preview=main.split('private void showBatchPlanPreview()',1)[1].split(
    'private void showOverduePlanOptions(',1)[0]
assert 'setItems(labels,' in preview
assert '.setMessage("Sprawdź daty' not in preview, (
    "AlertDialog setMessage+setItems może ukryć całą listę"
)
assert 'draft.rows.get(i).label()' in preview

for token in (
    'static final class DraftReservation',
    'static List<TimeSuggestions.Option> proposeForBatch(',
    'calculate(db,task.memberId,task.id,duration,now,notBefore,held)',
    'hold.overlaps(memberId,date,candidate,end)',
    'TimeSlotRules.overlaps(a,b,date.atTime(start),date.atTime(end))',
    'ProjectPlanningStore.planningNotBefore',
    'ProjectStore.remainingMinutes',
):
    assert token in planner, token

for token in (
    'final SmartTaskPlanner.Task task;',
    'final TimeSuggestions.Option chosen;',
    'TaskDateLabels.format(chosen.date)',
    'if(task.memberId<=0)',
    'SmartTaskPlanner.reason(db,task,0L)',
    'new SmartTaskPlanner.DraftReservation(task.memberId,first)',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
    'db.endTransaction();',
    'SmartTaskPlanner.readAny(db,row.task.id)',
    'SmartTaskPlanner.proposeForBatch(',
    'current.memberId!=row.task.memberId',
    'current.duration!=row.task.duration',
    'current.due.equals(row.task.due)',
    'values.put("due_date",row.chosen.date.toString())',
    'values.put("remind_time",row.chosen.start.toString())',
    'values.put("reminder_lead_days",0)',
):
    assert token in batch, token
assert 'DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy"' in labels
assert 'Locale.forLanguageTag("pl-PL")' in labels
assert 'check_batch_task_planner_contract.py' in workflow
print("Wsadowe dopasowanie, podgląd 9+ czynności, polskie dni i transakcja: PASS")
