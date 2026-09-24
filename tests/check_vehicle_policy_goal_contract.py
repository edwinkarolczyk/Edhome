#!/usr/bin/env python3
"""0.6.0.6: optional OC-to-shared-goal link, v27 migrations, backup and ledger isolation."""
from pathlib import Path
import sqlite3
import runpy

root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
store=(root/"VehiclePolicyStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
context=runpy.run_path("tests/check_db_contract.py")
db=context["fresh"]

for token in (
    "goal_id INTEGER)",
    "Long goalId)",
    "SELECT 1 FROM paycheck_goals WHERE id=? AND scope='shared'",
    'policy.putNull("goal_id");',
    'policy.put("goal_id",goalId);',
    "db.beginTransaction();",
    'return "DUPLICATE_IGNORED";',
):
    assert token in store,token

for token in (
    "DATABASE_MIGRATED_26_TO_27_POLICY_GOAL_LINK",
    "ALTER TABLE vehicle_policies ADD COLUMN goal_id INTEGER",
    '"SELECT id,name,target_grosz FROM paycheck_goals "',
    "goal.setAdapter(lightDialogSpinnerAdapter(goalNames))",
    "goalIds.get(goal.getSelectedItemPosition())",
    'private String vehiclePolicyGoalLabel(long id)',
    "PaycheckGoalsStore.allocated(",
    "WHERE p.goal_id=? AND p.current=1",
    "showVehicleDateInCalendar(item.ocUntil)",
):
    assert token in main,token
for token in (
    "private static final int DB_VERSION = 33;",
    '"notes",\n            "goal_id"',
    'inputVersion < 27',
    '"vehicle_policies".equals(definition[0])',
    '"goal_id".equals(key)',
    'goalId != null && !goalLimits.containsKey(goalId)',
):
    assert token in backup,token
assert "PaycheckStore.add(" not in store
assert "PaycheckGoalsStore.allocate(" not in store
form=main.split("private void editVehiclePolicy(",1)[1].split(
    "private String vehicleDeadline(",1)[0]
assert "PaycheckStore.add(" not in form
assert "PaycheckGoalsStore.allocate(" not in form
assert "versionCode 82" in gradle and "versionName '0.6.0.17'" in gradle

db.execute("INSERT INTO vehicles(id,name,registration,mileage,oc_until,inspection_until,notes)"
           " VALUES(77,'Audi A4','',0,'','','')")
db.execute("INSERT INTO paycheck_goals(id,scope,name,target_grosz,created_at)"
           " VALUES(9,'shared','OC Audi',75000,1)")
db.execute("INSERT INTO vehicle_policies(operation_id,vehicle_id,provider,policy_number,"
           "valid_from,valid_until,current,notes,goal_id) VALUES("
           "'a0000000-0000-4000-8000-000000000001',77,'Ubezpieczyciel','OC/1',"
           "'2026-09-09','2027-09-08',1,'',9)")
assert db.execute("SELECT p.goal_id,g.name FROM vehicle_policies p "
                  "JOIN paycheck_goals g ON g.id=p.goal_id").fetchone()==(
                   9,"OC Audi")
assert db.execute("SELECT COUNT(*) FROM paycheck_goal_allocations").fetchone()==(0,)
assert db.execute("SELECT COUNT(*) FROM paycheck_transactions").fetchone()==(0,)
old=sqlite3.connect(":memory:")
old.execute("CREATE TABLE vehicle_policies(id INTEGER PRIMARY KEY,vehicle_id INTEGER,notes TEXT)")
old.execute("INSERT INTO vehicle_policies VALUES(1,77,'history')")
old.execute("ALTER TABLE vehicle_policies ADD COLUMN goal_id INTEGER")
assert old.execute("SELECT notes,goal_id FROM vehicle_policies").fetchone()==(
    "history",None)
print("OC goal links, v26→v27 migration, v26 backup compatibility, no duplicate ledger: PASS")
