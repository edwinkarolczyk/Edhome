#!/usr/bin/env python3
"""EDHOME 0.7 code-side acceptance gate. Physical phone/PC acceptance stays manual."""
from pathlib import Path
import re

root=Path(".")
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"GardenStore.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")

version=re.search(r"versionName '([^']+)'",gradle).group(1)
assert version.startswith(("0.7.4.","0.8.")), version

# Foundation + migration from last 0.6 DB.
assert 'super(context, "edhome-beta-preview.db", null, 45)' in main
assert 'DATABASE_MIGRATED_36_TO_37_GARDEN' in main
assert 'DATABASE_MIGRATED_37_TO_38_GARDEN_CYCLE' in main
assert 'private static final int DB_VERSION = 45;' in backup

# Sync backfill must run only after the Garden v38 columns/tables exist.
# Otherwise an existing v36/v37 install crashes on startup while ensureAll()
# queries season_year/finished_at before GardenStore.upgrade38() adds them.
upgrade=main.split("@Override public void onUpgrade",1)[1].split(
    "private static void addNfcLinks",1)[0]
assert upgrade.count("SyncRecordStore.ensureAll(database);") == 1
assert upgrade.index("GardenStore.upgrade38(database);") < upgrade.index(
    "SyncRecordStore.ensureAll(database);")

# Offline data + portable backup/restore.
for table in (
    "garden_areas","garden_catalog","garden_catalog_overrides",
    "garden_custom_plants","garden_plantings","garden_task_links",
    "garden_events","garden_harvests"
):
    assert ("CREATE TABLE IF NOT EXISTS "+table) in store, table
    assert ('{"'+table+'",' in backup), "backup missing "+table

# Planned vs actual, season history and idempotence guards.
for token in (
    "planned_sow TEXT NOT NULL",
    "actual_sow TEXT NOT NULL",
    "season_year",
    "finished_at",
    "static void markStage(",
    "static long addHarvest(",
    "static void finishSeason(",
    "static long cloneNextSeason(",
    "static String seasonReport(",
    "SELECT id FROM garden_harvests WHERE planting_id=? AND harvested_on=?",
):
    assert token in store, token

# Shared tasks/calendar/reminders, no parallel scheduler.
for token in (
    "static int syncPlanTasks(",
    "static long createActivityTask(",
    'task.put("task_kind","garden:"+plantingId',
    'task.put("repeat_rule",cleanRule)',
    'task.put("reminder_lead_days",leadDays)',
):
    assert token in store, token
for token in (
    "gardenActivityDialog(",
    "gardenScheduleDialog(",
    "ReminderReceiver.schedule(this);",
    'compactAction(links,"▦ Kalendarz"',
    'compactAction(links,"✓ Czynności"',
):
    assert token in main, token

# User-owned catalog import/export.
assert "GardenStore.importCatalogCsv" in main
assert "GardenStore.exportCatalogCsv" in main
assert "garden_catalog_overrides" in store

# Desktop reads the same LAN snapshot instead of a separate garden database.
for token in (
    '"Ogród"', 'return garden();', '"garden_plantings"',
    '"garden_harvests"', 'gardenHarvestSummary'
):
    assert token in desktop, token

print("EDHOME 0.7 code-side acceptance gate PASS; physical phone/PC acceptance still required")