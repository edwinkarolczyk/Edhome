#!/usr/bin/env python3
"""EDHOME 0.6.0-beta.4: OC renewal is one historical record and one calendar date."""
from pathlib import Path
import sqlite3, runpy
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
policy=(root/"VehiclePolicyStore.java").read_text(encoding="utf-8")
vehicle=(root/"VehicleStore.java").read_text(encoding="utf-8")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
db=runpy.run_path("tests/check_db_contract.py")["fresh"]
for token in (
    "VehiclePolicyStore.create(database);",
    "DATABASE_MIGRATED_25_TO_26_VEHICLE_POLICIES",
    "Polisy OC — bieżąca i historia",
    "editVehiclePolicy(item)",
    "private void editVehiclePolicy(",
    "VehiclePolicyStore.add(",
    "lightDialogForm(form);",
):
    assert token in main,token
for token in (
    "CREATE TABLE vehicle_policies (",
    "operation_id TEXT NOT NULL UNIQUE",
    "CREATE UNIQUE INDEX vehicle_policies_one_current",
    "WHERE current=1",
    '"oc_until"',
    "db.beginTransaction();",
    "db.setTransactionSuccessful();",
    '"DUPLICATE_IGNORED"',
):
    assert token in policy,token
assert "PaycheckStore" not in policy and "PaycheckStore" not in main.split("private void editVehiclePolicy(",1)[1].split("private String vehicleDeadline",1)[0]
assert "Termin bieżącej polisy OC zmieniaj przez zapis nowej polisy." in vehicle
for token in (
    '"vehicle_policies", "id", "operation_id", "vehicle_id", "provider"',
    'inputVersion < 26 && "vehicle_policies".equals(definition[0])',
    "VehiclePolicyStore.dates(from,until)",
    'policyCurrentVehicles.add(vehicle)',
    'policyOperations.add(operation)',
):
    assert token in backup,token

db.execute("INSERT INTO vehicles(id,name,oc_until) VALUES(11,'Samochód','2027-09-01')")
db.execute("INSERT INTO vehicle_policies(operation_id,vehicle_id,provider,"
           "policy_number,valid_from,valid_until,current,notes) "
           "VALUES('10000000-0000-4000-8000-000000000001',11,'Firma','A/001',"
           "'2026-09-02','2027-09-01',1,'')")
db.commit()
db.execute("BEGIN")
db.execute("UPDATE vehicle_policies SET current=0 WHERE vehicle_id=11")
db.execute("INSERT INTO vehicle_policies(operation_id,vehicle_id,provider,"
           "policy_number,valid_from,valid_until,current,notes) "
           "VALUES('10000000-0000-4000-8000-000000000002',11,'Firma','B/002',"
           "'2027-09-02','2028-09-01',1,'')")
db.execute("UPDATE vehicles SET oc_until='2028-09-01' WHERE id=11")
db.execute("COMMIT")
assert db.execute("SELECT policy_number,current FROM vehicle_policies "
                  "WHERE vehicle_id=11 ORDER BY id").fetchall()==[
                  ('A/001',0),('B/002',1)]
assert db.execute("SELECT oc_until FROM vehicles WHERE id=11").fetchone()==('2028-09-01',)
try:
    db.execute("UPDATE vehicle_policies SET current=1 WHERE policy_number='A/001'")
    raise AssertionError("Two current policies accepted")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("INSERT INTO vehicle_policies(operation_id,vehicle_id,provider,"
               "policy_number,valid_from,valid_until,current) "
               "VALUES('10000000-0000-4000-8000-000000000002',11,'Firma','C',"
               "'2028-09-02','2029-09-01',0)")
    raise AssertionError("Duplicate operation accepted")
except sqlite3.IntegrityError:
    pass
print("Policies v26 migration, history, one current OC, calendar and backup contract: PASS")
