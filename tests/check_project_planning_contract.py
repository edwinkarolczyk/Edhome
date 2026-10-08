#!/usr/bin/env python3
"""Kontrakt planera Projektów: dostępność użytkownika, blokery i pozostały czas."""
from pathlib import Path
import re

root=Path(".")
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
planning=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ProjectPlanningStore.java").read_text(encoding="utf-8")
project=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")
time=(root/"app/src/main/java/com/edwinkarolczyk/edhome/TimeSuggestions.java").read_text(encoding="utf-8")
backup=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")

for marker in (
    "CREATE TABLE IF NOT EXISTS member_shift_hours",
    "CREATE TABLE IF NOT EXISTS member_project_windows",
    "CREATE TABLE IF NOT EXISTS project_task_blockers",
    "I zmiana","II zmiana","III zmiana","Dzień wolny",
    "LocalTime.of(16,0)","LocalTime.of(21,0)",
    "LocalTime.of(8,0)","LocalTime.of(12,0)",
    "LocalTime.of(9,0)","LocalTime.of(18,0)",
    "Zakup / materiał","Dostawa / oczekiwanie","Dorób / przygotuj",
    "Akceptacja / decyzja",
    "hasUndatedHardBlocker","planningNotBefore","startBlockReason",
):
    assert marker in planning, marker

for marker in (
    "showMemberShiftHoursEditor",
    "showProjectAvailabilityEditor",
    "showProjectBlockers",
    "showAddProjectBlockerDialog",
    "Termin minął • zostało",
    "TimeSuggestions.proposeAvailability",
    "ProjectPlanningStore.windows",
    "PROJECT_BLOCKER_ADDED",
    "MEMBER_PROJECT_WINDOWS_SAVED",
    "DATABASE_MIGRATED_43_TO_44_PROJECT_PLANNING",
):
    assert marker in main, marker

assert "ProjectPlanningStore.startBlockReason" in project
assert "Czynność jest zablokowana. Czeka na:" in planning
assert "static List<Option> proposeAvailability" in time
assert "interface WindowSource" in time
assert "final class Window" in time

# Stary administrator nie może zostać po cichu ostatnim domownikiem.
profile=(root/"app/src/main/java/com/edwinkarolczyk/edhome/UserProfileStore.java").read_text(encoding="utf-8")
assert "Najpierw ustaw innego użytkownika jako Administratora." in profile

# Backup i LAN sync dostają wszystkie nowe tabele przez DataBackup.syncDefinitions().
for table in ("member_shift_hours","member_project_windows","project_task_blockers"):
    assert '{"'+table+'"' in backup, table
assert "private static final int DB_VERSION = 46;" in backup
assert "inputVersion < 44" in backup
assert '"slot".equals(column)' in backup
assert '"enabled".equals(column)' in backup
assert '"hard".equals(column)' in backup
assert '"resolved".equals(column)' in backup

# Start nie może ominąć twardego blokera.
start=project.split("static void startWork",1)[1].split("static int stopWork",1)[0]
assert "ProjectPlanningStore.startBlockReason" in start

# Planer nie wraca do pełnego szacunku: bierze remainingMinutes.
planner=main.split("private void showProjectPlanSuggestions",1)[1].split(
    "private void tasks()",1)[0]
assert "ProjectStore.remainingMinutes" in planner
assert "hasUndatedHardBlocker" in planner
assert "planningNotBefore" in planner
assert "proposeAvailability" in planner
assert "chosen.date.atTime(chosen.end)" in planner

print("Project planning availability/blockers/admin-role contract: PASS")