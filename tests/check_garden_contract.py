#!/usr/bin/env python3
"""EDHOME 0.7 Garden foundation + calendar/notification contract."""
from pathlib import Path

src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"GardenStore.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
tiles=(src/"HomeTileCatalog.java").read_text(encoding="utf-8")

for table in (
    "garden_areas","garden_catalog","garden_catalog_overrides",
    "garden_custom_plants","garden_plantings","garden_task_links",
    "garden_events","garden_harvests"
):
    assert ("CREATE TABLE IF NOT EXISTS "+table) in store, table
    assert ('{"'+table+'",' in backup), "backup missing "+table

for token in (
    'case "garden": garden();',
    'ensureGardenTileSeeded();',
    'GardenStore.create(database);',
    'DATABASE_MIGRATED_36_TO_37_GARDEN',
    'DATABASE_MIGRATED_37_TO_38_GARDEN_CYCLE',
    'compactAction(row,"Powiadomienia"',
    'GardenStore.syncPlanTasks(db.getWritableDatabase(),plantingId,null,0)',
    'ReminderReceiver.schedule(this);',
    'IMPORT_GARDEN_CATALOG',
    'EXPORT_GARDEN_CATALOG',
    'GardenStore.importCatalogCsv',
    'GardenStore.exportCatalogCsv',
    'GardenStore.markStage',
    'GardenStore.addHarvest',
    'GardenStore.finishSeason',
    'GardenStore.cloneNextSeason',
    'gardenCycleDialog(',
    'gardenHarvestDialog(',
    'gardenHistoryDialog(',
    'gardenActivityDialog(',
    'GardenStore.createActivityTask(',
    'gardenSeasonReport(',
    'GardenStore.seasonReport(',
):
    assert token in main, token

for token in (
    '"garden"', 'case "garden": return "Ogród";',
    'case "garden": return "garden";'
):
    assert token in tiles, token

assert "source_license TEXT NOT NULL" in store
assert "season_year" in store and "finished_at" in store
assert "CREATE TABLE IF NOT EXISTS garden_events" in store
assert "CREATE TABLE IF NOT EXISTS garden_harvests" in store
assert "quantity_milli INTEGER NOT NULL CHECK(quantity_milli>0)" in store
assert "static long parseQuantityMilli(" in store
assert "static String harvestSummary(" in store
assert "static long createActivityTask(" in store
assert "static int activeActivityTaskCount(" in store
assert "static String seasonReport(" in store
assert '"garden:"+plantingId+":"+cleanKind' in store
assert "static int importCatalogCsv(" in store
assert "static String exportCatalogCsv(" in store
assert "garden_catalog_overrides" in store
assert "planned_sow TEXT NOT NULL" in store
assert "actual_sow TEXT NOT NULL" in store
assert "stage TEXT NOT NULL" in store
assert "UNIQUE(planting_id,stage)" in store
assert "static int syncPlanTasks(" in store
assert '"Ogród • "+stage[1]+": "+plantName+" • "+areaName' in store
assert 'task.put("repeat_rule","once")' in store
assert 'task.put("reminder_lead_days",leadDays)' in store
assert '{"garden_task_links", "id", "planting_id", "stage", "task_id", "created_at"}' in backup
assert '{"garden_events", "id", "planting_id", "event_kind", "event_date", "note",' in backup
assert '{"garden_harvests", "id", "planting_id", "harvested_on", "quantity_milli",' in backup
assert "private static final int DB_VERSION = 43;" in backup
print("Garden 0.7: catalog + crop cycle + recurring care tasks + season yield report PASS")