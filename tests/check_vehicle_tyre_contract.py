#!/usr/bin/env python3
"""EDHOME 0.6.0: tyres, one fitted set, migration, place and backup contract."""
from pathlib import Path
import runpy
import sqlite3
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
store=(root/"VehicleTyreStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
db=runpy.run_path("tests/check_db_contract.py")["fresh"]
for marker in (
    "VehicleTyreStore.create(database);",
    "DATABASE_MIGRATED_24_TO_25_TYRE_SETS",
    "editVehicleTyres(", "changeVehicleTyres(",
    "VehicleTyreStore.save(", "VehicleTyreStore.change(",
):
    assert marker in main, marker
for marker in (
    "CREATE TABLE vehicle_tyre_sets (",
    "CREATE UNIQUE INDEX vehicle_tyre_one_mounted ",
    "WHERE mounted=1",
    "db.beginTransaction();", "db.setTransactionSuccessful();",
    "VehicleStore.addEvent(", "DUPLICATE_IGNORED",
    "validPlace(db,storagePlace);",
):
    assert marker in store, marker
assert "PaycheckStore" not in store and "PantryBarcodeStore" not in store
for marker in (
    '"vehicle_tyre_sets", "id", "vehicle_id", "label", "season"',
    'inputVersion < 25 && "vehicle_tyre_sets".equals(definition[0])',
    'VehicleTyreStore.label(name)',
    'VehicleTyreStore.season(season)',
    'VehicleTyreStore.dot(dot)',
    'mountedVehicles.add(vehicle)',
    'parsed.get("vehicle_tyre_sets")',
):
    assert marker in backup, marker
db.execute("INSERT INTO vehicles(id,name) VALUES(10,'Auto')")
db.execute("INSERT INTO places(id,name) VALUES(4,'Garaż')")
db.execute("INSERT INTO vehicle_tyre_sets(id,vehicle_id,label,season,dot,"
           "tread_tenths,mounted,place_id) VALUES"
           "(1,10,'Letnie','summer','3424',65,1,NULL)")
db.execute("INSERT INTO vehicle_tyre_sets(id,vehicle_id,label,season,dot,"
           "tread_tenths,mounted,place_id) VALUES"
           "(2,10,'Zimowe','winter','4823',72,0,4)")
try:
    db.execute("UPDATE vehicle_tyre_sets SET mounted=1,place_id=NULL WHERE id=2")
    raise AssertionError("Two sets mounted at once")
except sqlite3.IntegrityError:
    pass
db.commit()
db.execute("BEGIN")
db.execute("UPDATE vehicle_tyre_sets SET mounted=0,place_id=4 WHERE id=1")
db.execute("UPDATE vehicle_tyre_sets SET mounted=1,place_id=NULL WHERE id=2")
db.execute("INSERT INTO vehicle_events(operation_id,vehicle_id,kind,event_date,note)"
           "VALUES('a1e563a2-1234-4123-8123-000000000001',10,'tyres','2026-09-23',"
           "'Zdjęto letnie → zamontowano zimowe')")
db.execute("COMMIT")
assert db.execute("SELECT id FROM vehicle_tyre_sets WHERE mounted=1").fetchall()==[(2,)]
assert db.execute("SELECT place_id FROM vehicle_tyre_sets WHERE id=1").fetchone()==(4,)
assert db.execute("SELECT count(*) FROM vehicle_events WHERE kind='tyres'").fetchone()==(1,)
print("Tyre-set schema, one fitted set, atomic swap, backup and UI contract: PASS")
