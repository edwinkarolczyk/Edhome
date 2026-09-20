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
history = statements(section(main, "private static void addTaskHistory",
                             "private static void addAuditTables"))
places = statements(section(main, "private static void addPlaces",
                            "private static void addTaskHistory"))
upgrade = section(main, "@Override public void onUpgrade", "private static void addPlaces")
step2 = statements(section(upgrade, "if (oldVersion < 2)", "if (oldVersion < 3)"))
step3 = statements(section(upgrade, "if (oldVersion < 3)", "if (oldVersion < 4)"))
step4 = statements(section(upgrade, "if (oldVersion < 4)", "private static void addPlaces")) if "private static void addPlaces" in upgrade else statements(upgrade.split("if (oldVersion < 4)",1)[1])

def execute(database, sql):
    for statement in sql:
        database.execute(statement)

def schema(database):
    return {name: [row[1:3] for row in database.execute(
        'PRAGMA table_info("' + name + '")')]
        for (name,) in database.execute(
            "SELECT name FROM sqlite_master WHERE type='table' "
            "AND name NOT LIKE 'sqlite_%' ORDER BY name")}

assert len(create) == 2 and len(audit) == 3 and len(history) == 2 and len(places) == 1
version = int(re.search(r'super\(context, "edhome-beta-preview.db", null, (\d+)\)', main).group(1))
backup_version = int(re.search(r'private static final int DB_VERSION = (\d+);', backup).group(1))
assert version == backup_version == 4, "Database version and backup format differ"

fresh = sqlite3.connect(":memory:")
execute(fresh, create + audit + history + places)
expected = schema(fresh)
assert len(expected) == 7, "Unexpected number of tables"
for old in (1, 2, 3):
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
    if old < 2:
        execute(db, step2 + audit)
    if old < 3:
        execute(db, step3 + history)
    execute(db, step4 + places)
    assert schema(db) == expected, f"Upgrade from SQLite v{old} differs from fresh schema"
    assert db.execute("SELECT id,title,done FROM tasks").fetchone() == (7, "Test", 0)
    assert db.execute("SELECT id,name,qty FROM pantry").fetchone() == (3, "Ryż", 4)
    assert db.execute("SELECT repeat_rule,repeat_every,place_id FROM tasks").fetchone() == (
        "once", 1, None)
    db.close()

definitions = section(backup, "private static final String[][] TABLES = {", "};")
table_defs = re.findall(r'\{\s*"([^"]+)"\s*,([^{}]+)\}', definitions, re.S)
assert len(table_defs) == len(expected), "A table is missing from backup definition"
for table, fields in table_defs:
    columns = ["id"] + re.findall(r'"([^"]+)"', fields)
    assert columns == [col[0] for col in expected[table]], (
        "Backup columns do not match SQL schema: " + table)
assert "database.beginTransaction();" in backup and "database.setTransactionSuccessful();" in backup
assert 'inputVersion != 2 && inputVersion != 3 && inputVersion != DB_VERSION' in backup
print("SQLite migrations 1→4, 2→4, 3→4: PASS; 7 backup tables/columns: PASS")
