#!/usr/bin/env python3
"""0.6.0.6: one voluntary shared payment per durable vehicle cost."""
from pathlib import Path
import runpy
import sqlite3

src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
store=(src/"VehicleCostStore.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
for token in ("db.beginTransaction();","db.insertOrThrow(\"paycheck_transactions\"",
              "db.insertOrThrow(\"vehicle_costs\"",
              "db.setTransactionSuccessful();","db.endTransaction();",
              '"DUPLICATE_IGNORED"', 'cost.putNull("paycheck_operation_id")',
              'transaction.put("scope","shared");',
              'transaction.put("kind","expense");',
              'transaction.put("category","vehicle");'):
    assert token in store, token
for token in ("private void editVehicleCost(","VehicleCostStore.record(",
              "paycheck.setChecked(false);","Zapisz koszt pojazdu",
              "VehicleCostStore.create(database);",
              "DATABASE_MIGRATED_28_TO_29_VEHICLE_COSTS"):
    assert token in main,token
for token in ('"vehicle_costs", "id", "operation_id", "vehicle_id"',
              'inputVersion < 29 && "vehicle_costs".equals(definition[0])',
              'parsed.get("vehicle_costs")',
              'costFinanceOperations.add(linked)',
              'sharedFinance.get(linked)',
              '"paycheck_operation_id".equals(key)'):
    assert token in backup,token
db=runpy.run_path("tests/check_db_contract.py")["fresh"]
db.execute("INSERT INTO vehicles(id,name) VALUES(9,'Audi')")
op="11111111-1111-4111-8111-111111111111"
db.commit()
db.execute("BEGIN")
db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,"
           "amount_grosz,note,created_at) VALUES(?,'shared','expense','vehicle',"
           "65000,'OC Audi',1)",(op,))
db.execute("INSERT INTO vehicle_costs(operation_id,vehicle_id,kind,paid_on,"
           "amount_grosz,note,paycheck_operation_id) VALUES(?,9,'oc',"
           "'2026-09-23',65000,'',?)",(op,op))
db.execute("COMMIT")
assert db.execute("SELECT SUM(amount_grosz) FROM vehicle_costs").fetchone()==(65000,)
assert db.execute("SELECT SUM(amount_grosz) FROM paycheck_transactions").fetchone()==(65000,)
try:
    db.execute("INSERT INTO vehicle_costs(operation_id,vehicle_id,kind,paid_on,"
               "amount_grosz) VALUES(?,9,'oc','2026-09-23',65000)",(op,))
    raise AssertionError("duplicate vehicle cost accepted")
except sqlite3.IntegrityError: pass
try:
    db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,"
               "amount_grosz,created_at) VALUES(?,'shared','expense','vehicle',65000,1)",
               (op,))
    raise AssertionError("duplicate PayCheck operation accepted")
except sqlite3.IntegrityError: pass
db.execute("INSERT INTO vehicle_costs(operation_id,vehicle_id,kind,paid_on,"
           "amount_grosz) VALUES('22222222-2222-4222-8222-222222222222',"
           "9,'tyres','2026-09-23',15000)")
assert db.execute("SELECT COUNT(*) FROM paycheck_transactions").fetchone()==(1,)
assert db.execute("SELECT COUNT(*) FROM vehicle_costs").fetchone()==(2,)
print("Linked vehicle costs, one shared PayCheck expense, unlinked costs and v29 migration: PASS")
