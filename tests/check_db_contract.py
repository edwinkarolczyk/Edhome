#!/usr/bin/env python3
"""SQLite schema upgrade/backup contract checks without an Android emulator."""
import json
import re
import sqlite3
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
pantry_store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text(encoding="utf-8")

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
pantry14 = statements(section(pantry_store, "static void createTables(SQLiteDatabase db)", "static Item find(").replace("db.execSQL(", "database.execSQL("))

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
assert version == backup_version == 14, "Database version and backup format differ"

fresh = sqlite3.connect(":memory:")
execute(fresh, create + audit + history + rotations + places + sibling_index + members + shifts + shopping + timers + pantry14)
expected = schema(fresh)
assert len(expected) == 15 and len(pantry14) == 4, "Unexpected number of tables"
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
        execute(db, step8 + shopping)
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
        execute(db, step8 + shopping)
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
    assert db.execute("SELECT id,name,qty FROM pantry").fetchone() == (3, "Ryż", 4)
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
assert len(table_defs) == len(expected), "A table is missing from backup definition"
for table, fields in table_defs:
    columns = re.findall(r'"([^"]+)"', fields)
    assert columns == [col[0] for col in expected[table]], (
        "Backup columns do not match SQL schema: " + table)
assert "database.beginTransaction();" in backup and "database.setTransactionSuccessful();" in backup
assert 'inputVersion != 2 && inputVersion != 3 && inputVersion != 4 && inputVersion != 5 && inputVersion != 6 && inputVersion != 7 && inputVersion != 8 && inputVersion != 9 && inputVersion != 10 && inputVersion != 11 && inputVersion != 12 && inputVersion != 13 && inputVersion != DB_VERSION' in backup
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
print("SQLite migrations v1–v12→v14: PASS; barcodes, rotations, timers, places and backup: PASS")
