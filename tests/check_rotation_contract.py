#!/usr/bin/env python3
"""Recurring household rotation: DB, UI, history and backup safety contract."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")

for token in (
    "addTaskRotations(database);",
    "DATABASE_MIGRATED_12_TO_13_TASK_ROTATION",
    "CREATE TABLE task_rotation_members",
    "PRIMARY KEY(task_id,member_id), UNIQUE(task_id,position)",
    "ALTER TABLE task_history ADD COLUMN assignee_id INTEGER",
    "assignee_name_snapshot TEXT",
    "RotationRules.validate(rotationMembers",
    "Aktualny wykonawca musi należeć do rotacji.",
    "RotationRules.next(rotation, completedBy)",
    'history.put("assignee_id", completedBy)',
    'history.put("assignee_name_snapshot", completedByName)',
    'database.delete("task_rotation_members", "task_id=?"',
    'database.delete("task_rotation_members", "member_id=?"',
    "Re-number positions after removing a person.",
    'description += " • Rotacja: " + rotation;',
    'form.addView(text("Rotacja wykonawców (opcjonalnie)"',
):
    assert token in main, "Missing rotation contract: " + token

for token in (
    '{"task_rotation_members", "task_id", "member_id", "position"}',
    '"assignee_id", "assignee_name_snapshot"',
    'inputVersion < 13 && "task_rotation_members".equals(definition[0])',
    'inputVersion < 13 && "task_history".equals(definition[0])',
    'rotationMembers.computeIfAbsent(taskId',
    '"Rotacja musi zawierać co najmniej dwie osoby."',
    '"Aktualny wykonawca nie należy do rotacji."',
    '"task_rotation_members".equals(definition[0])',
    '"task_id ASC, position ASC"',
):
    assert token in backup, "Missing rotation backup contract: " + token

# Rotation rows have a composite key and deliberately no synthetic "id".
assert 'if (!"task_rotation_members".equals(definition[0])' in backup
assert '&& !"pantry_packages".equals(definition[0]))' in backup
assert 'private static final int DB_VERSION = 20;' in backup
assert 'super(context, "edhome-beta-preview.db", null, 20' in main
print("Rotation DB v20, next-assignee history, deletion and backup contract: PASS")
