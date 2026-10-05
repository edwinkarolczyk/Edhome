#!/usr/bin/env python3
"""Regresja modułu Projekty: hierarchia, zależności, koszty, zasoby i planer."""
import sqlite3
from pathlib import Path

root = Path(".")
project = (root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = (root / "app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
sync = (root / "app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")
time = (root / "app/src/main/java/com/edwinkarolczyk/edhome/TimeSuggestions.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

# Projekty są warstwą nad wspólnymi Czynnościami i Magazynem.
for marker in (
    "CREATE TABLE IF NOT EXISTS projects",
    "CREATE TABLE IF NOT EXISTS project_resources",
    "CREATE TABLE IF NOT EXISTS project_costs",
    "CREATE TABLE IF NOT EXISTS project_task_dependencies",
    "descendantIds",
    "openTaskIdsForPlanning",
    'Trudność "+score+"/5',
    "Zależność musi dotyczyć czynności z tego samego projektu głównego.",
    "Ta zależność utworzyłaby pętlę.",
):
    assert marker in project, marker

for marker in (
    'case "projects": projects();',
    "showProjectDependencyDialog",
    "showProjectBlockers",
    "Zaproponuj terminy projektu",
    "Plan obejmuje również podprojekty i respektuje",
    "+ Rzecz / Pudełko",
    "+ Koszt / zakup",
):
    assert marker in main, marker

assert "MAX_TASK_MINUTES = 600" in main
assert "durationMinutes > 600" in time
assert 'private static final int DB_VERSION = 44;' in backup
assert '{"project_task_dependencies", "task_id", "depends_on_task_id", "created_at"}' in backup
assert '"project_task_dependencies".equals(table)' in sync
assert 'return new String[]{"task_id","depends_on_task_id"};' in sync
assert '"project_task_dependencies".equals(table)' in desktop
assert 'canonicalId(row.get("depends_on_task_id"))' in desktop
assert 'database.delete("project_task_dependencies"' in main
assert 'project_task_work_sessions' in project
assert 'PROJECT_MIN_TASK_MINUTES = 20' in main
assert 'PROJECT_WORK_STARTED' in main and 'PROJECT_WORK_STOPPED' in main
assert 'ProjectStore.remainingMinutes' in main
assert 'TimeSuggestions.proposeAvailability' in main
assert 'ProjectPlanningStore.windows' in main
assert 'ProjectPlanningStore.startBlockReason' in project
assert 'project_task_blockers' in main
assert 'database.delete("project_task_work_sessions"' in main

# Minimalny kontrakt SQL zależności: brak self-loop i brak duplikatów.
db = sqlite3.connect(":memory:")
db.execute("""CREATE TABLE project_task_dependencies (
    task_id INTEGER NOT NULL,
    depends_on_task_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    CHECK(task_id!=depends_on_task_id),
    UNIQUE(task_id,depends_on_task_id)
)""")
db.execute("INSERT INTO project_task_dependencies VALUES (2,1,1)")
try:
    db.execute("INSERT INTO project_task_dependencies VALUES (2,1,2)")
    raise AssertionError("Duplicate dependency allowed")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("INSERT INTO project_task_dependencies VALUES (3,3,3)")
    raise AssertionError("Self dependency allowed")
except sqlite3.IntegrityError:
    pass

# Koszty Projektów pozostają osobnym rejestrem, a nie automatycznym księgowaniem PayCheck.
assert "Koszty projektu nie są księgowane w PayCheck." in main
print("Projects v1 contract: PASS")