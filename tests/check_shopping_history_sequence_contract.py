#!/usr/bin/env python3
"""No recycled shopping_id after JSON restore with deleted shopping items."""
from pathlib import Path
import sqlite3, re, json
src=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
text=src.split("database.beginTransaction();",1)[1]
parts=re.findall(r'database\.execSQL\((.*?sqlite_sequence.*?)\);',text,re.S)
assert len(parts)==2
sql=["".join(json.loads(c) for c in re.findall(r'"(?:\\.|[^"\\])*"',part))
     for part in parts]
assert "shopping_receipts" in sql[1] and "pantry_purchase_prices" in sql[1]
assert text.index("database.insertOrThrow(definition[0], null, values);") < text.index(
    'database.execSQL("INSERT INTO sqlite_sequence')
assert text.index('database.execSQL("UPDATE sqlite_sequence') < text.index(
    "if (!restored.commit())")
for active in (False,True):
    db=sqlite3.connect(":memory:")
    db.executescript("""
    CREATE TABLE shopping_items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT);
    CREATE TABLE shopping_receipts(id INTEGER PRIMARY KEY AUTOINCREMENT,
        shopping_id INTEGER NOT NULL UNIQUE);
    CREATE TABLE pantry_purchase_prices(id INTEGER PRIMARY KEY AUTOINCREMENT,
        shopping_id INTEGER);
    """)
    if active: db.execute("INSERT INTO shopping_items(id,name) VALUES(3,'existing')")
    db.execute("INSERT INTO shopping_receipts(shopping_id) VALUES(18)")
    db.execute("INSERT INTO pantry_purchase_prices(shopping_id) VALUES(30)")
    for statement in sql: db.execute(statement)
    db.execute("INSERT INTO shopping_items(name) VALUES('new')")
    assert db.execute("SELECT MAX(id) FROM shopping_items").fetchone()[0]==31
    assert db.execute("SELECT shopping_id FROM shopping_receipts").fetchone()[0]==18
    db.close()
print("Deleted shopping IDs reserved after restore: empty and live lists PASS")
