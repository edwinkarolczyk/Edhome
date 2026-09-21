#!/usr/bin/env python3
"""Verify waste tasks reuse the task/calendar/history pipeline."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
reminder = Path("app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
checks = (
    "case \"waste\": waste(); break;",
    '"FROM tasks WHERE task_kind=\'waste\' "',
    'values.put("task_kind", "waste");',
    'values.put("waste_fraction", fraction);',
    'if (existing.moveToFirst()) return false;',
    'db.completeTask(id);',
    'database.insertOrThrow("task_history", null, history);',
    "ALTER TABLE tasks ADD COLUMN task_kind TEXT NOT NULL DEFAULT 'general'",
    "ALTER TABLE tasks ADD COLUMN waste_fraction TEXT",
)
for expected in checks:
    assert expected in main, "Missing shared waste-task contract: " + expected
assert 'CREATE TABLE waste' not in main
assert 'task_kind=\'waste\'' in reminder
assert 'prefs.getBoolean("reminders_enabled", false)' in reminder
assert 'WHERE done=0 AND due_date IS NOT NULL' in reminder
assert '"task_kind", "waste_fraction"' in backup
print("Waste tasks share task IDs, calendar, history and opt-in reminders: PASS")
