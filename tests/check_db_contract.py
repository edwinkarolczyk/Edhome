#!/usr/bin/env python3
"""SQLite schema upgrade/backup contract checks without an Android emulator."""
import json
import re
import sqlite3
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")

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
history = statements(section(main, "private static void addTaskHistory",
                             "private static void addMemberSchedules"))
places = statements(section(main, "private static void addPlaces",
                            "private static void addTaskHistory"))
upgrade = section(main, "@Override public void onUpgrade", "private static void addPlaces")
step2 = statements(section(upgrade, "if (oldVersion < 2)", "if (oldVersion < 3)"))
step3 = statements(section(upgrade, "if (oldVersion < 3)", "if (oldVersion < 4)"))
step4 = statements(section(upgrade, "if (oldVersion < 4)", "if (oldVersion < 5)"))
step5 = statements(section(upgrade, "if (oldVersion < 5)", "if (oldVersion < 6)"))
step6 = statements(section(upgrade, "if (oldVersion < 6)", "if (oldVersion < 7)"))
step7 = statements(upgrade.split("if (oldVersion < 7)", 1)[1])

def execute(database, sql):
    for statement in sql:
        database.execute(statement)

def schema(database):
    return {name: [row[1:3] for row in database.execute(
        'PRAGMA table_info("' + name + '")')]
        for (name,) in database.execute(
            "SELECT name FROM sqlite_master WHERE type='table' "
            "AND name NOT LIKE 'sqlite_%' ORDER BY name")}

assert len(create) == 2 and len(audit) == 3 and len(history) == 2 and len(places) == 1 and len(members) == 1 and len(shifts) == 2
version = int(re.search(r'super\(context, "edhome-beta-preview.db", null, (\d+)\)', main).group(1))
backup_version = int(re.search(r'private static final int DB_VERSION = (\d+);', backup).group(1))
assert version == backup_version == 7, "Database version and backup format differ"

fresh = sqlite3.connect(":memory:")
execute(fresh, create + audit + history + places + members + shifts)
expected = schema(fresh)
assert len(expected) == 10, "Unexpected number of tables"
for old in (1, 2, 3, 4, 5, 6):
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
        execute(db, step3 + history)
    if old >= 4:
        execute(db, step4 + places)
    if old >= 5:
        execute(db, step5)
    if old >= 6:
        execute(db, step6 + members)
    if old < 2:
        execute(db, step2 + audit)
    if old < 3:
        execute(db, step3 + history)
    if old < 4:
        execute(db, step4 + places)
    if old < 5:
        execute(db, step5)
    if old < 6:
        execute(db, step6 + members)
    execute(db, step7 + shifts)
    assert schema(db) == expected, f"Upgrade from SQLite v{old} differs from fresh schema"
    assert db.execute("SELECT id,title,done FROM tasks").fetchone() == (7, "Test", 0)
    assert db.execute("SELECT id,name,qty FROM pantry").fetchone() == (3, "Ryż", 4)
    assert db.execute("SELECT repeat_rule,repeat_every,place_id FROM tasks").fetchone() == (
        "once", 1, None)
    assert db.execute("SELECT priority,duration_minutes FROM tasks").fetchone() == (
        "normal", 30), "Migration must preserve task with planning defaults"
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
    db.close()

definitions = section(backup, "private static final String[][] TABLES = {", "};")
table_defs = re.findall(r'\{\s*"([^"]+)"\s*,([^{}]+)\}', definitions, re.S)
assert len(table_defs) == len(expected), "A table is missing from backup definition"
for table, fields in table_defs:
    columns = re.findall(r'"([^"]+)"', fields)
    assert columns == [col[0] for col in expected[table]], (
        "Backup columns do not match SQL schema: " + table)
assert "database.beginTransaction();" in backup and "database.setTransactionSuccessful();" in backup
assert 'inputVersion != 2 && inputVersion != 3 && inputVersion != 4 && inputVersion != 5 && inputVersion != 6 && inputVersion != DB_VERSION' in backup
assert 'inputVersion < 5 && "tasks".equals(definition[0])' in backup
assert '"priority".equals(key)' in backup and '"duration_minutes".equals(key)' in backup
assert 'inputVersion < 6 && "household_members".equals(definition[0])' in backup
assert '"assignee_id".equals(key)' in backup
assert 'inputVersion < 7 && ("member_weekly_shifts".equals(definition[0])' in backup
assert '"member_id".equals(column) || "weekday".equals(column)' in backup
print("SQLite migrations v1–v6→v7: PASS; household members, weekly shifts, exceptions and backup: PASS")
