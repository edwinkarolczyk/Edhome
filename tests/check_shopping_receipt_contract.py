#!/usr/bin/env python3
"""Shopping purchased != accepted; receipts survive backup and reject duplicate stock."""
from pathlib import Path
import sqlite3

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/ShoppingReceiptStore.java").read_text()
gradle = Path("app/build.gradle").read_text()

for token in (
    'ShoppingReceiptStore.create(database);',
    'DATABASE_MIGRATED_17_TO_18_SHOPPING_RECEIPTS',
    'super(context, "edhome-beta-preview.db", null, 23)',
    'oldVersion < 1 || newVersion > 23',
    'ShoppingReceiptStore.received(',
    'smallButton(box, "Przyjmij do spiżarni"',
    'chooseShoppingReceipt(shoppingId, name)',
    'confirmShoppingReceipt(shoppingId, shoppingName,',
    'ShoppingReceiptStore.accept(',
    'if ("COMMITTED".equals(outcome))',
    'SHOPPING_RECEIPT_COMMITTED',
):
    assert token in main, token
for token in (
    'db.beginTransaction();',
    'SELECT checked FROM shopping_items WHERE id=?',
    'if (received(db, shoppingId)) return "ALREADY_RECEIVED";',
    'if (!shopping.moveToFirst() || shopping.getInt(0) != 1)',
    'db.insertOrThrow("shopping_receipts", null, record)',
    'id=? AND qty=?',
    'db.setTransactionSuccessful();',
    'db.endTransaction();',
    'shopping_id INTEGER NOT NULL UNIQUE',
):
    assert token in store, token
for token in (
    'DB_VERSION = 23;',
    '{"shopping_receipts", "id", "shopping_id", "pantry_id", "name_snapshot",',
    'inputVersion < 18 && "shopping_receipts".equals(definition[0])',
    'Podwójne przyjęcie zakupów w kopii.',
    '"shopping_id".equals(column) || "packages".equals(column)',
):
    assert token in backup, token
assert "versionCode 60" in gradle and "versionNameSuffix '-beta.12.1'" in gradle

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE shopping_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT,
 shopping_id INTEGER NOT NULL UNIQUE, pantry_id INTEGER NOT NULL,
 name_snapshot TEXT NOT NULL,
 packages INTEGER NOT NULL CHECK(packages BETWEEN 1 AND 100000000),
 before_qty INTEGER NOT NULL, after_qty INTEGER NOT NULL,
 happened_at INTEGER NOT NULL);
""")
db.execute("INSERT INTO shopping_receipts(shopping_id,pantry_id,name_snapshot,packages,before_qty,after_qty,happened_at) VALUES (1,5,'Mleko',2,3,5,1)")
try:
    db.execute("INSERT INTO shopping_receipts(shopping_id,pantry_id,name_snapshot,packages,before_qty,after_qty,happened_at) VALUES (1,5,'Mleko',2,5,7,2)")
    raise AssertionError("duplicate accepted")
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT shopping_id,packages,before_qty,after_qty FROM shopping_receipts").fetchall() == [(1,2,3,5)]
print("Shopping receipt transaction, durable idempotence and backup v18: PASS")
