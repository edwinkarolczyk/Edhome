#!/usr/bin/env python3
"""0.6 vehicle schema, calendar, backup and stock/paycheck isolation contracts."""
from pathlib import Path
import runpy
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
store=(root/"VehicleStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
catalog=(root/"HomeTileCatalog.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
for token in (
    "VehicleStore.create(database);", "VehicleTyreStore.create(database);",
    "DATABASE_MIGRATED_24_TO_25_TYRE_SETS",
    "DATABASE_MIGRATED_23_TO_24_VEHICLES",
    'super(context, "edhome-beta-preview.db", null, 36)',
    'case "vehicles": vehicles(); break;',
    'header("Kalendarz • czynności i pojazdy")',
    "oc_until AS deadline", "inspection_until",
    'private void vehicles()', "private void editVehicle(",
    "private void editVehicleEvent(", "VehicleStore.save(",
    "VehicleStore.addEvent(", "VEHICLE_EVENT_COMMITTED",
    'go("vehicles")',
):
    assert token in main, token
for token in (
    "CREATE TABLE vehicles (", "CREATE TABLE vehicle_events (",
    "operation_id TEXT NOT NULL UNIQUE", "vehicle_id INTEGER NOT NULL",
    "CHECK(mileage BETWEEN 0 AND 999999999)",
    "db.beginTransaction();", "db.endTransaction();",
    '"DUPLICATE_IGNORED"', "db.setTransactionSuccessful();",
    'id=? AND mileage=?',
):
    assert token in store, token
assert "PaycheckStore" not in store and "PantryBarcodeStore" not in store
assert "VehicleRules.optionalDate" in store
assert '"vehicles"' in catalog and 'case "vehicles": return "Pojazdy";' in catalog
assert 'private static final int DB_VERSION = 36;' in backup
assert '{"vehicles", "id", "name", "registration", "mileage",' in backup
assert '{"vehicle_events", "id", "operation_id", "vehicle_id",' in backup
assert 'inputVersion < 24 && ("vehicles".equals(definition[0])' in backup
assert 'vehicleIds.contains(event.getAsLong("vehicle_id"))' in backup
assert 'vehicleOperations.add(event.getAsString("operation_id"))' in backup
assert '"mileage".equals(column) || "vehicle_id".equals(column)' in backup
assert '"tread_tenths".equals(column) || "mounted".equals(column)' in backup
# Release numbers advance independently of the vehicle schema; avoid pinning CI
# to an already published APK when only the build number changes.
import re
version_code = re.search(r"\bversionCode\s+(\d+)", gradle)
version_name = re.search(r"\bversionName\s+'([^']+)'", gradle)
assert version_code and int(version_code.group(1)) >= 84
assert version_name and re.fullmatch(r"0\.6\.0\.\d+", version_name.group(1))
assert "versionNameSuffix ''" in gradle
context=runpy.run_path("tests/check_db_contract.py")
db=context["fresh"]
assert len(context["schema"](db))==33
db.execute("INSERT INTO vehicles(id,name,registration,mileage,oc_until,inspection_until,notes) "
           "VALUES(1,'Audi A4','WD 123',150000,'2027-09-01','2027-08-05','')")
db.execute("INSERT INTO vehicle_events(operation_id,vehicle_id,kind,event_date,mileage,note) "
           "VALUES('11111111-1111-4111-8111-111111111111',1,'service','2026-09-22',150000,'Olej')")
try:
    db.execute("INSERT INTO vehicle_events(operation_id,vehicle_id,kind,event_date,note) "
               "VALUES('11111111-1111-4111-8111-111111111111',1,'service','2026-09-23','Olej')")
    raise AssertionError("Duplicate event accepted")
except __import__("sqlite3").IntegrityError:
    pass
assert db.execute("SELECT name,oc_until,inspection_until FROM vehicles").fetchone()==(
    'Audi A4','2027-09-01','2027-08-05')
print("Vehicle v24 migration, idempotent history and calendar/backup contract: PASS")
