#!/usr/bin/env python3
"""Shared goals stay shared-only, amounts precise, allocations do not post expenses."""
from pathlib import Path
import json, re, sqlite3

root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
source=(root/"PaycheckGoalsStore.java").read_text()
main=(root/"MainActivity.java").read_text()
backup=(root/"DataBackup.java").read_text()
gradle=Path("app/build.gradle").read_text()
for token in (
    "CHECK(scope='shared')",
    "operation_id TEXT NOT NULL UNIQUE",
    "paycheck_goal_allocations_goal_idx",
    "if (prior.moveToFirst()) return \"DUPLICATE\";",
    'WHERE id=? AND scope=\'shared\'',
    'if (already > target || grosz > target - already) return "OVER_TARGET";',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
):
    assert token in source, "Goal accounting guard absent: "+token
for token in (
    'sharedPaycheckGoals();',
    'button("+ Nowy cel wspólny", this::createSharedPaycheckGoal);',
    'PaycheckGoalsStore.addGoal(',
    'PaycheckGoalsStore.allocate(',
    'PAYCHECK_SHARED_GOAL_ALLOCATED',
    'Nie zmienia salda wspólnego PayCheck.',
    'DATABASE_MIGRATED_20_TO_21_PAYCHECK_GOALS',
    'super(context, "edhome-beta-preview.db", null, 21)',
):
    assert token in main, "Goal UI/schema missing: "+token
for token in (
    'DB_VERSION = 21;',
    '"paycheck_goals", "id", "scope", "name", "target_grosz", "created_at"',
    '"paycheck_goal_allocations", "id", "operation_id", "goal_id"',
    'inputVersion < 21 && ("paycheck_goals".equals(definition[0])',
    'goalLimits.get(goalId)',
    'amount > maximum - previous',
    '"target_grosz".equals(column) || "goal_id".equals(column)',
):
    assert token in backup, "Backup protection absent: "+token
assert "versionCode 46" in gradle and "versionNameSuffix '-beta.3'" in gradle
assert "PaycheckGoalsStore" not in (root/"ShoppingReceiptStore.java").read_text()
assert "PaycheckGoalsStore" not in (root/"PantryBarcodeStore.java").read_text()
body=source.split("static void create(SQLiteDatabase db)",1)[1].split("static long addGoal(",1)[0]
sql=[]
for match in re.findall(r'db[.]execSQL[(](.*?)[)];',body,re.S):
    pieces=re.findall(r'"[^"]*"',match)
    sql.append(''.join(json.loads(part) for part in pieces))
assert len(sql)==3,sql
db=sqlite3.connect(":memory:")
for statement in sql: db.execute(statement)
db.execute("INSERT INTO paycheck_goals(scope,name,target_grosz,created_at) VALUES('shared','OC',70000,1)")
db.execute("INSERT INTO paycheck_goal_allocations(operation_id,goal_id,amount_grosz,created_at) VALUES(?,?,?,?)",("id1",1,50000,2))
db.execute("INSERT INTO paycheck_goal_allocations(operation_id,goal_id,amount_grosz,created_at) VALUES(?,?,?,?)",("id2",1,20000,3))
assert db.execute("SELECT SUM(amount_grosz) FROM paycheck_goal_allocations").fetchone()[0]==70000
try:
    db.execute("INSERT INTO paycheck_goal_allocations(operation_id,goal_id,amount_grosz,created_at) VALUES(?,?,?,?)",("id2",1,100,4))
    raise AssertionError("duplicate allocation accepted")
except sqlite3.IntegrityError: pass
try:
    db.execute("INSERT INTO paycheck_goals(scope,name,target_grosz,created_at) VALUES('private','leak',100,1)")
    raise AssertionError("private goal accepted")
except sqlite3.IntegrityError: pass
print("PayCheck goals: shared-only, exact grosze, duplicate guard, backup v21: PASS")
