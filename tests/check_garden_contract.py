#!/usr/bin/env python3
"""EDHOME 0.7 Garden foundation contract."""
from pathlib import Path

src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"GardenStore.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
tiles=(src/"HomeTileCatalog.java").read_text(encoding="utf-8")

for table in (
    "garden_areas","garden_catalog","garden_catalog_overrides",
    "garden_custom_plants","garden_plantings","garden_task_links"
):
    assert ("CREATE TABLE IF NOT EXISTS "+table) in store, table
    assert ('{"'+table+'",' in backup, "backup missing "+table

for token in (
    'case "garden": garden();',
    'ensureGardenTileSeeded();',
    'GardenStore.create(database);',
    'DATABASE_MIGRATED_36_TO_37_GARDEN',
    'Następny krok 0.7 spina nasadzenia z istniejącymi Czynnościami',
):
    assert token in main, token

for token in (
    '"garden"', 'case "garden": return "Ogród";',
    'case "garden": return "garden";'
):
    assert token in tiles, token

assert "source_license TEXT NOT NULL" in store
assert "garden_catalog_overrides" in store
assert "planned_sow TEXT NOT NULL" in store
assert "actual_sow TEXT NOT NULL" in store
assert "CHECK((catalog_id>0 AND custom_plant_id=0)" in store
assert "private static final int DB_VERSION = 37;" in backup
print("Garden 0.7 foundation: offline catalog/overrides/custom plants/plantings, backup and sync-ready schema PASS")
