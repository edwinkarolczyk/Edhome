#!/usr/bin/env python3
"""SQLite schema upgrade/backup contract checks without an Android emulator."""
import json
import re
import sqlite3
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
pantry_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text(encoding="utf-8")
package_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryPackageStore.java").read_text(encoding="utf-8")
receipt_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/ShoppingReceiptStore.java").read_text(encoding="utf-8")
storage_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text(encoding="utf-8")
paycheck_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PaycheckStore.java").read_text(encoding="utf-8")
bank_evidence_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/BankEvidenceStore.java").read_text(encoding="utf-8")
goals_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PaycheckGoalsStore.java").read_text(encoding="utf-8")
price_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryPriceHistoryStore.java").read_text(encoding="utf-8")
policy_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/VehiclePolicyStore.java").read_text(encoding="utf-8")
cost_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/VehicleCostStore.java").read_text(encoding="utf-8")
document_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/VehicleDocumentStore.java").read_text(encoding="utf-8")
tyre_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/VehicleTyreStore.java").read_text(encoding="utf-8")
vehicle_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/VehicleStore.java").read_text(encoding="utf-8")
sync_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")

def section(source, after, before):
    return source.split(after, 1)[1].split(before, 1)[0]

def statements(source):
    result = []
    for expression in re.findall(r"database\.execSQL\((.*?)\);", source, re.S):
        chunks = re.findall(r'"(?:\\.|[^"\\])*"', expression)
        if chunks:
            result.append("".join(json.loads(chunk) for chunk in chunks))
    return result

create = statements(section(main, "@Override public void onCreate(SQLiteDatabase database)",
                            "@Override public void onUpgrade"))
audit = statements(section(main, "private static void addAuditTables", "long openAuditId"))
members = statements(section(main, "private static void addMembers", "private static void addAuditTables"))
shifts = statements(section(main, "private static void addMemberSchedules", "private static void addMembers"))
shopping = statements(section(main, "private static void addShopping", "private static void addMemberSchedules"))
timers = statements(section(main, "private static void addDeviceTimers", "long startDeviceTimer"))
rotations = statements(section(main, "private static void addTaskRotations",
                                "private static void addShopping"))
history = statements(section(main, "private static void addTaskHistory",
                             "private static void addTaskRotations"))
legacy_history = [
    "CREATE TABLE task_history (id INTEGER PRIMARY KEY AUTOINCREMENT, "
    "task_id INTEGER NOT NULL, title_snapshot TEXT NOT NULL, "
    "completed_at INTEGER NOT NULL, due_date TEXT, next_due_date TEXT)",
    "CREATE INDEX task_history_task_idx ON task_history(task_id,id)"
]
places = statements(section(main, "private static void addPlaces",
                            "private static void addTaskHistory"))
sibling_index = statements(section(main, "private static void addPlaceSiblingIndex",
                                    "private static void addPlaces"))
legacy_places = [
    "CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, "
    "name TEXT NOT NULL COLLATE NOCASE UNIQUE, "
    "kind TEXT NOT NULL DEFAULT 'Inne')"
]
upgrade = section(main, "@Override public void onUpgrade", "private static void addPlaceSiblingIndex")
step2 = statements(section(upgrade, "if (oldVersion < 2)", "if (oldVersion < 3)"))
step3 = statements(section(upgrade, "if (oldVersion < 3)", "if (oldVersion < 4)"))
step4 = statements(section(upgrade, "if (oldVersion < 4)", "if (oldVersion < 5)"))
step5 = statements(section(upgrade, "if (oldVersion < 5)", "if (oldVersion < 6)"))
step6 = statements(section(upgrade, "if (oldVersion < 6)", "if (oldVersion < 7)"))
step7 = statements(section(upgrade, "if (oldVersion < 7)", "if (oldVersion < 8)"))
step8 = statements(section(upgrade, "if (oldVersion < 8)", "if (oldVersion < 9)"))
step9 = statements(section(upgrade, "if (oldVersion < 9)", "if (oldVersion < 10)"))
step10 = statements(section(upgrade, "if (oldVersion < 10)", "if (oldVersion < 11 && oldVersion >= 4)"))
step11 = statements(section(upgrade, "if (oldVersion < 11 && oldVersion >= 4)", "if (oldVersion < 12)"))
step12 = statements(section(upgrade, "if (oldVersion < 12)", "if (oldVersion < 13)"))
step13 = statements(section(upgrade, "if (oldVersion < 13)", "if (oldVersion < 14)"))
pantry14 = statements(section(pantry_store, "static void createTables(SQLiteDatabase db)", "static void createDetails(").replace("db.execSQL(", "database.execSQL("))
pantry15 = statements(section(pantry_store, "static void createDetails(SQLiteDatabase db)", "static final class Details").replace("db.execSQL(", "database.execSQL("))
pantry17 = statements(section(package_store, "static void create(SQLiteDatabase db)", "static void fillLegacy(").replace("db.execSQL(", "database.execSQL("))
receipts18 = statements(section(receipt_store, "static void create(SQLiteDatabase db)", "static boolean received(").replace("db.execSQL(", "database.execSQL("))
storage19 = statements(section(storage_store, "static void createTables(SQLiteDatabase db)", "static final class Item").replace("db.execSQL(", "database.execSQL("))
paycheck32 = statements(section(paycheck_store, "static void create(SQLiteDatabase db)", "static String add(").replace("db.execSQL(", "database.execSQL("))
paycheck20 = [sql.replace(", status TEXT NOT NULL DEFAULT 'confirmed' CHECK(status IN ('pending','confirmed'))", "").replace(", confirmation_source TEXT NOT NULL DEFAULT 'legacy' CHECK(confirmation_source IN ('none','legacy','manual'))", "").replace(", confirmed_at INTEGER", "").replace(", statement_key TEXT, statement_date TEXT", "") for sql in paycheck32 if sql.startswith("CREATE TABLE")]
bank34 = statements(section(bank_evidence_store,
    "static void create(SQLiteDatabase db)", "static IngestResult ingest("
    ).replace("db.execSQL(", "database.execSQL("))
step34 = bank34.copy()
nfc35 = statements(section(main, "private static void addNfcLinks",
    "private static void addPlaceSiblingIndex"))
step35 = nfc35.copy()
sync36 = [
    "CREATE TABLE sync_records (sync_uuid TEXT PRIMARY KEY, "
    "table_name TEXT NOT NULL, row_key TEXT NOT NULL, "
    "revision INTEGER NOT NULL CHECK(revision>=1), updated_at INTEGER NOT NULL, "
    "deleted_at INTEGER, row_hash TEXT NOT NULL)",
    "CREATE UNIQUE INDEX sync_records_live_row_idx "
    "ON sync_records(table_name,row_key) WHERE deleted_at IS NULL",
    "CREATE INDEX sync_records_table_idx ON sync_records(table_name,row_key)",
    "CREATE INDEX sync_records_updated_idx ON sync_records(updated_at)",
]
step36 = sync36.copy()
step30 = statements(section(upgrade, "if (oldVersion >= 20 && oldVersion < 30)", "if (oldVersion < 31)"))
step31 = statements(section(upgrade, "if (oldVersion < 31)", "if(oldVersion < 32)"))
step32 = statements(section(upgrade, "if(oldVersion < 32)", "if(oldVersion < 33)"))
documents33 = statements(section(document_store, "static void create(SQLiteDatabase db)", "static String label(").replace("db.execSQL(", "database.execSQL("))
step33 = documents33.copy()
goals21 = statements(section(goals_store, "static void create(SQLiteDatabase db)", "static long addGoal(").replace("db.execSQL(", "database.execSQL("))
prices22 = statements(section(price_store, "static void create(SQLiteDatabase db)", "static String markBought(").replace("db.execSQL(", "database.execSQL("))
vehicles28 = statements(section(vehicle_store, "static void create(SQLiteDatabase db)", "static final class Vehicle").replace("db.execSQL(", "database.execSQL("))
vehicles24 = [sql.replace(
    ", oc_reminder_lead INTEGER CHECK(oc_reminder_lead IN (0,1,7,14,30)), "
    "inspection_reminder_lead INTEGER CHECK(inspection_reminder_lead IN (0,1,7,14,30)))",
    ")") for sql in vehicles28]
tyres25 = statements(section(tyre_store, "static void create(SQLiteDatabase db)", "static final class SetInfo").replace("db.execSQL(", "database.execSQL("))
step25 = tyres25.copy()
policies27 = statements(section(policy_store, "static void create(SQLiteDatabase db)", "static String provider(").replace("db.execSQL(", "database.execSQL("))
step26 = [statement.replace(", goal_id INTEGER)", ")")
          for statement in policies27]
step27 = statements(section(upgrade,
    "if (oldVersion >= 26 && oldVersion < 27)", "if (oldVersion < 28)"))
step28 = statements(section(upgrade, "if (oldVersion < 28)", "if (oldVersion < 29)"))
costs29 = statements(section(cost_store, "static void create(SQLiteDatabase db)", "static boolean validKind(").replace("db.execSQL(", "database.execSQL("))
step29 = costs29.copy()
# Historic installs: columns arrive only with the v23 migration.
shopping22 = [sql.replace(", place_id INTEGER)", ")") for sql in shopping]
receipts22 = [sql.replace(", place_id INTEGER, place_name_snapshot TEXT NOT NULL DEFAULT '')", ")")
              for sql in receipts18]
step23 = statements(section(upgrade, "if (oldVersion < 23)",
                            'DiagnosticLog.event("DATABASE_MIGRATED_22_TO_23_SHOPPING_PLACES")'))
step24 = vehicles24.copy()  # LocalDb delegates v23->24 to VehicleStore.create.
legacy17 = statements(section(package_store, "static void fillLegacy(SQLiteDatabase db)", "static final class Pack").replace("db.execSQL(", "database.execSQL("))
step16 = statements(section(upgrade, "if (oldVersion < 16)", 'DiagnosticLog.event("DATABASE_MIGRATED_15_TO_16_PANTRY_CATEGORIES")'))
legacy_create = [sql.split(", category TEXT NOT NULL DEFAULT")[0] + ")"
                 if sql.startswith("CREATE TABLE pantry (") else sql for sql in create]

def execute(database, sql):
    for statement in sql:
        database.execute(statement)

def schema(database):
    return {name: [row[1:3] for row in database.execute(
        'PRAGMA table_info("' + name + '")')]
        for (name,) in database.execute(
            "SELECT name FROM sqlite_master WHERE type='table' "
            "AND name NOT LIKE 'sqlite_%' ORDER BY name")}

assert len(create) == 2 and len(audit) == 3 and len(history) == 2 and len(rotations) == 2 and len(places) == 1 and len(sibling_index) == 1 and len(step11) == 4 and len(timers) == 2 and len(members) == 1 and len(shifts) == 2 and len(shopping) == 1
version = int(re.search(r'super\(context, "edhome-beta-preview.db", null, (\d+)\)', main).group(1))
backup_version = int(re.search(r'private static final int DB_VERSION = (\d+);', backup).group(1))
assert version == backup_version == 36, "Database version and backup format differ"

fresh = sqlite3.connect(":memory:")
execute(fresh, create + audit + history + rotations + places + sibling_index + members + shifts + shopping + timers + pantry14 + pantry15 + pantry17 + receipts18 + storage19 + paycheck32 + bank34 + nfc35 + sync36 + goals21 + prices22 + vehicles28 + tyres25 + policies27 + costs29 + documents33)
expected = schema(fresh)
assert len(expected) == 33 and len(sync36) == 4 and len(bank34) == 2 and len(nfc35) == 2 and len(documents33) == 2 and len(costs29) == 2 and len(step30) == 1 and len(step31) == 3 and len(step32) == 3 and len(vehicles24) == 3 and len(tyres25) == 3 and len(policies27) == 3 and len(step27) == 1 and len(step28) == 2 and len(step23) == 3 and len(prices22) == 2 and len(goals21) == 3 and len(paycheck20) == 1 and len(storage19) == 3 and len(receipts18) == 1 and len(pantry14) == 4 and len(pantry15) == 1 and len(step16) == 1 and len(pantry17) == 1 and len(legacy17) == 1, "Unexpected number of tables"
for old in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12):
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, "
               "title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0)")
    db.execute("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, "
               "name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0)")
    db.execute("INSERT INTO tasks (id,title,done) VALUES (7,'Test',0)")
    db.execute("INSERT INTO pantry (id,name,qty) VALUES (3,'Ryż',4)")
    if old >= 2:
        execute(db, audit)
    if old >= 3:
        execute(db, step3 + legacy_history)
    if old >= 4:
        execute(db, step4 + (places + sibling_index if old >= 11 else legacy_places))
    if old >= 5:
        execute(db, step5)
    if old >= 6:
        execute(db, step6 + members)
    if old >= 7:
        execute(db, step7 + shifts)
    if old >= 8:
        execute(db, step8 + shopping22)
    if old >= 9:
        execute(db, step9)
    if old >= 10:
        execute(db, step10)
    if old < 2:
        execute(db, step2 + audit)
    if old < 3:
        execute(db, step3 + legacy_history)
    if old < 4:
        execute(db, step4 + places + sibling_index)
    if old < 5:
        execute(db, step5)
    if old < 6:
        execute(db, step6 + members)
    if old < 7:
        execute(db, step7 + shifts)
    if old < 8:
        execute(db, step8 + shopping22)
    if old < 9:
        execute(db, step9)
    if old < 10:
        execute(db, step10)
    if 4 <= old < 11:
        execute(db, step11 + sibling_index)
    if old >= 12:
        execute(db, timers)
    elif old < 12:
        execute(db, timers)
    if old < 13:
        execute(db, rotations)
        for statement in step13:
            if statement.startswith("ALTER TABLE task_history"):
                db.execute(statement)
    execute(db, pantry14)  # v13 to v14, also after older migrations
    execute(db, pantry15)  # v14 to v15, preserves existing pantry rows
    execute(db, step16)  # v15 to v16, default unknown products to other
    execute(db, pantry17 + legacy17)  # v16 to v17, one szt. per legacy pack
    execute(db, receipts22)  # v17 to v18, receipt ledger without changing stock
    execute(db, storage19)  # v18 to v19, object and box QR, history
    execute(db, paycheck20)  # v19 to v20, shared-only money ledger
    execute(db, goals21)  # v20 to v21, shared goals and allocations
    execute(db, prices22)  # v21 to v22, optional purchase price history
    execute(db, step23)  # v22 to v23, per-shopping destination
    execute(db, step24)  # v23 to v24, vehicles
    execute(db, step25)  # v24 to v25, tyre sets
    execute(db, step26)  # v25 to v26, policies
    execute(db, step27)  # v26 to v27, optional goal link
    execute(db, step28)  # v27 to v28, per-vehicle reminder choices
    execute(db, step29)  # v28 to v29, linked vehicle costs
    execute(db, step30)  # historical PayCheck entries become confirmed
    execute(db, step31)  # pending stays pending; legacy source remains unknown
    execute(db, step32)  # bank CSV evidence reference and unique index
    execute(db, step33)  # vehicle document register
    execute(db, step34)  # v33 to v34, persistent bank evidence queue
    execute(db, step35)  # v34 to v35, NFC links
    execute(db, step36)  # v35 to v36, sync UUID/revision/tombstone sidecar
    assert schema(db) == expected, f"Upgrade from SQLite v{old} differs from fresh schema"
    assert db.execute("SELECT id,title,done FROM tasks").fetchone() == (7, "Test", 0)
    db.execute("INSERT INTO device_timers (id,device_type,title,start_at,"
               "end_at,status) VALUES (3,'washer','Bawełna',1789980000000,"
               "1789985400000,'running')")
    assert db.execute("SELECT title,status,acknowledged_at FROM device_timers "
                      "WHERE id=3").fetchone() == ('Bawełna','running',None)
    db.execute("UPDATE device_timers SET status='acknowledged',"
               "acknowledged_at=1789985500000 WHERE id=3")
    assert db.execute("SELECT status FROM device_timers WHERE id=3"
                      ).fetchone() == ('acknowledged',)
    assert db.execute("SELECT id,name,qty,category FROM pantry").fetchone() == (3, "Ryż", 4, "other")
    assert db.execute("SELECT pantry_id,unit,size_milli FROM pantry_packages").fetchone() == (3, "szt.", 1000)
    assert db.execute("SELECT repeat_rule,repeat_every,place_id FROM tasks").fetchone() == (
        "once", 1, None)
    assert db.execute("SELECT priority,duration_minutes FROM tasks").fetchone() == (
        "normal", 30), "Migration must preserve task with planning defaults"
    assert db.execute(
        "SELECT task_kind,waste_fraction FROM tasks WHERE id=7"
    ).fetchone() == ("general", None)
    assert db.execute("SELECT remind_time,reminder_lead_days FROM tasks "
                      "WHERE id=7").fetchone() == (None, 0)
    db.execute("INSERT INTO places (id,name,kind) VALUES (12,'Dom','Dom')")
    db.execute("INSERT INTO places (id,name,parent_id,kind,icon) "
               "VALUES (13,'Kuchnia',12,'Pomieszczenie','room')")
    db.execute("INSERT INTO places (id,name,parent_id,kind,icon) "
               "VALUES (14,'Półka 1',13,'Półka','shelf')")
    db.execute("INSERT INTO places (id,name,parent_id,kind,icon) "
               "VALUES (15,'Garaż',NULL,'Garaż','garage')")
    db.execute("INSERT INTO places (id,name,parent_id,kind,icon) "
               "VALUES (16,'Półka 1',15,'Półka','shelf')")
    assert db.execute("SELECT name,kind,parent_id,icon FROM places WHERE id=12"
                      ).fetchone() == ('Dom', 'Dom', None, 'places')
    db.execute("UPDATE tasks SET place_id=14 WHERE id=7")
    assert db.execute("SELECT place_id FROM tasks WHERE id=7"
                      ).fetchone() == (14,)
    try:
        db.execute("INSERT INTO places (name,parent_id,icon) "
                   "VALUES ('półka 1',13,'shelf')")
        raise AssertionError("Duplicate sibling name allowed")
    except sqlite3.IntegrityError:
        pass
    assert db.execute("SELECT assignee_id FROM tasks").fetchone() == (None,)
    db.execute("INSERT INTO household_members (id,name) VALUES (4,'Edwin')")
    db.execute("UPDATE tasks SET assignee_id=4 WHERE id=7")
    assert db.execute("SELECT t.title,m.name FROM tasks t "
                      "JOIN household_members m ON m.id=t.assignee_id").fetchone() == (
        "Test", "Edwin")
    db.execute("INSERT INTO member_weekly_shifts (member_id,weekday,shift) "
               "VALUES (4,1,'morning')")
    db.execute("INSERT INTO member_shift_exceptions (member_id,date,shift) "
               "VALUES (4,'2026-09-21','off')")
    assert db.execute("SELECT shift FROM member_weekly_shifts "
                      "WHERE member_id=4 AND weekday=1").fetchone() == ("morning",)
    assert db.execute("SELECT shift FROM member_shift_exceptions "
                      "WHERE member_id=4 AND date='2026-09-21'").fetchone() == ("off",)
    db.execute("INSERT INTO shopping_items (id,name,qty_milli,unit,checked) "
               "VALUES (10,'Mleko',1500,'l',0)")
    db.execute("INSERT INTO shopping_items (id,name,qty_milli,unit,checked) "
               "VALUES (11,'Ryż',NULL,'szt.',1)")
    assert db.execute("SELECT qty_milli,unit FROM shopping_items "
                      "WHERE id=10").fetchone() == (1500, "l")
    assert db.execute("SELECT qty_milli,checked FROM shopping_items "
                      "WHERE id=11").fetchone() == (None, 1)
    db.execute("INSERT INTO tasks (title,due_date,repeat_rule,repeat_every,"
               "task_kind,waste_fraction) VALUES "
               "('Wystaw: Papier','2026-09-23','weekly',1,'waste','paper')")
    assert db.execute("SELECT waste_fraction FROM tasks WHERE task_kind='waste'"
                      ).fetchone() == ("paper",)
    db.execute("UPDATE tasks SET remind_time='19:30',reminder_lead_days=1 "
               "WHERE id=7")
    assert db.execute("SELECT remind_time,reminder_lead_days FROM tasks "
                      "WHERE id=7").fetchone() == ("19:30", 1)
    db.close()

definitions = section(backup, "private static final String[][] TABLES = {", "};")
table_defs = re.findall(r'\{\s*"([^"]+)"\s*,([^{}]+)\}', definitions, re.S)
assert len(table_defs) == len(expected) - 1, "A domain table is missing from backup definition"
assert "sync_records" in expected, "Synchronization sidecar table missing"
for table, fields in table_defs:
    columns = re.findall(r'"([^"]+)"', fields)
    assert columns == [col[0] for col in expected[table]], (
        "Backup columns do not match SQL schema: " + table)
assert "database.beginTransaction();" in backup and "database.setTransactionSuccessful();" in backup
for accepted in range(2,36):
    assert ("inputVersion != "+str(accepted)) in backup
assert 'inputVersion != DB_VERSION' in backup
assert 'inputVersion < 34 && "bank_evidence_queue".equals(definition[0])' in backup
assert 'inputVersion < 35 && "nfc_links".equals(definition[0])' in backup
assert 'inputVersion < 16 && "pantry".equals(definition[0])' in backup
assert 'PantryCategories.known(values.getAsString("category"))' in backup
assert 'inputVersion < 5 && "tasks".equals(definition[0])' in backup
assert '"priority".equals(key)' in backup and '"duration_minutes".equals(key)' in backup
assert 'inputVersion < 6 && "household_members".equals(definition[0])' in backup
assert '"assignee_id".equals(key)' in backup
assert 'inputVersion < 7 && ("member_weekly_shifts".equals(definition[0])' in backup
assert '"member_id".equals(column)' in backup and '"weekday".equals(column)' in backup
assert 'inputVersion < 8 && "shopping_items".equals(definition[0])' in backup
assert '"qty_milli".equals(key)' in backup
assert 'inputVersion < 9 && "tasks".equals(definition[0])' in backup
assert '"task_kind".equals(key)' in backup
assert '"waste_fraction".equals(key)' in backup
assert 'WasteRules.validate(fraction, due, rule,' in backup
assert 'inputVersion < 10 && "tasks".equals(definition[0])' in backup
assert '"remind_time".equals(key)' in backup
assert '"reminder_lead_days".equals(key)' in backup
assert 'inputVersion < 11 && "places".equals(definition[0])' in backup
assert '"parent_id".equals(key)' in backup and '"icon".equals(key)' in backup
assert 'PlaceRules.validForest(hierarchy)' in backup
assert 'PlaceRules.validateFields(name, kind,' in backup
assert '"task_rotation_members", "task_id", "member_id", "position"' in backup
assert '"assignee_id", "assignee_name_snapshot"' in backup
assert '"task_rotation_members".equals(definition[0])' in backup
assert 'task_id ASC, position ASC' in backup
# An already-upgraded v14 installation must preserve barcode links and inventory.
existing14 = sqlite3.connect(":memory:")
execute(existing14, legacy_create + audit + history + rotations + places + sibling_index
        + members + shifts + shopping22 + timers + pantry14)
existing14.execute("INSERT INTO pantry(id,name,qty) VALUES (2,'Mleko',7)")
existing14.execute("INSERT INTO pantry_barcodes(pantry_id,barcode) "
                   "VALUES(2,'5901234123457')")
execute(existing14, pantry15)
execute(existing14, step16)
execute(existing14, pantry17 + legacy17)
execute(existing14, receipts22)
execute(existing14, storage19)
execute(existing14, paycheck20)
execute(existing14, goals21)
execute(existing14, prices22)
execute(existing14, step23)
execute(existing14, step24)
execute(existing14, step25)
execute(existing14, step26)
execute(existing14, step27)  # v26 to v27, optional goal link
execute(existing14, step28)  # v27 to v28, per-vehicle reminder choices
execute(existing14, step29)  # v28 to v29, vehicle costs
execute(existing14, step30)
execute(existing14, step31)
execute(existing14, step32)
execute(existing14, step33)
execute(existing14, step34)
execute(existing14, step35)
execute(existing14, step36)
assert schema(existing14) == expected
assert existing14.execute("SELECT id,name,qty,category FROM pantry").fetchone() == (2,'Mleko',7,'other')
assert existing14.execute("SELECT pantry_id,barcode FROM pantry_barcodes").fetchone() == (2,'5901234123457')
assert existing14.execute("SELECT pantry_id,unit,size_milli FROM pantry_packages").fetchone() == (2,"szt.",1000)
existing14.close()
# Existing SQLite v23 with populated objects and shopping history stays intact.
existing23 = sqlite3.connect(":memory:")
execute(existing23, create + audit + history + rotations + places
        + sibling_index + members + shifts + shopping + timers + pantry14
        + pantry15 + pantry17 + receipts18 + storage19 + paycheck20 + goals21
        + prices22)
existing23.execute("INSERT INTO pantry(id,name,qty) VALUES(9,'Ryż',6)")
existing23.execute("INSERT INTO shopping_items(id,name) VALUES(42,'Ryż')")
execute(existing23, step24)
execute(existing23, step25)
execute(existing23, step26)
execute(existing23, step27)  # v26 to v27, optional goal link
execute(existing23, step28)  # v27 to v28, per-vehicle reminder choices
execute(existing23, step29)  # v28 to v29, vehicle costs
execute(existing23, step30)
execute(existing23, step31)
execute(existing23, step32)
execute(existing23, step33)
execute(existing23, step34)
execute(existing23, step35)
execute(existing23, step36)
assert schema(existing23) == expected
assert existing23.execute("SELECT id,name,qty FROM pantry").fetchone() == (9,'Ryż',6)
assert existing23.execute("SELECT id,name FROM shopping_items").fetchone() == (42,'Ryż')
existing23.close()
print("SQLite migrations v1–v35→v36: PASS; sync UUID/revision/tombstones, NFC links, bank queue, reminders, policies, tyres, vehicles, shopping, pantry, backup: PASS")
