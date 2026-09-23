#!/usr/bin/env python3
"""Rzeczy/pudełka: QR, cycle guard, local history, backup and export ordering."""
import json
import re
import sqlite3
from pathlib import Path

main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
store=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text()
qr=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageQr.java").read_text()
backup=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
gradle=Path("app/build.gradle").read_text()

for token in (
    'StorageStore.createTables(database);',
    'DATABASE_MIGRATED_18_TO_19_STORAGE_QR',
    'super(context, "edhome-beta-preview.db", null, 26)',
    'case "storage": storage(); break;',
    'private boolean storageQrCameraPending;',
    'if (storageQrCameraPending) {',
    'storageQrCameraPending = false;',
    'openStorageQr(scan.getContents())',
    'IntentIntegrator.QR_CODE',
    'StorageQr.decode(value)',
    'StorageQr.encode(item.kind,item.id)',
    'StorageStore.location(db.getReadableDatabase(),item)',
    'StorageStore.move(db.getWritableDatabase(),existing.id,',
    'StorageStore.returned(db.getWritableDatabase(),item.id)',
    'StorageStore.lend(db.getWritableDatabase(),item.id,',
):
    assert token in main, "Missing storage Android integration: "+token

for token in (
    "storage_items", "storage_events",
    "CHECK(parent_box_id IS NULL OR place_id IS NULL)",
    "CHECK(kind IN ('thing','box'))",
    "Set<Long> ancestors=new HashSet<>();",
    "if (!ancestors.add(cursorId) || cursorId.equals(movedId))",
    "if(item.lentTo!=null)",
    'action IN (\'created\',\'moved\',\'lent\',\'returned\',\'removed\')',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
):
    assert token in store, "Missing storage safety: "+token

for token in (
    'DB_VERSION = 26;',
    '{"storage_items", "id", "name", "kind", "parent_box_id", "place_id",',
    '{"storage_events", "id", "item_id", "name_snapshot", "action",',
    'inputVersion < 19 && ("storage_items".equals(definition[0])',
    '"pantry_packages".equals(definition[0])',
    '? "pantry_id ASC" : "id ASC"',
    'Nieprawidłowa rzecz lub pudełko.',
    'Błąd powiązania pudełek.',
):
    assert token in backup, "Missing storage backup: "+token
assert 'EDHOME:STORAGE:1:' in qr
assert "versionCode 65" in gradle and "versionNameSuffix '-beta.5'" in gradle

expression=store.split('static void createTables(SQLiteDatabase db)',1)[1].split('static final class Item',1)[0]
statements=[]
for match in re.findall(r'db.execSQL\((.*?)\);',expression,re.S):
    statements.append(''.join(json.loads(x) for x in re.findall(r'"(?:\\.|[^"\\])*"',match)))
assert len(statements)==3, "two tables and index"
db=sqlite3.connect(":memory:")
for sql in statements: db.execute(sql)
db.execute("INSERT INTO storage_items(name,kind,created_at) VALUES ('Box','box',1)")
db.execute("INSERT INTO storage_items(name,kind,parent_box_id,created_at) VALUES ('Tool','thing',1,2)")
try:
    db.execute("INSERT INTO storage_items(name,kind,parent_box_id,place_id,created_at) VALUES ('Invalid','thing',1,1,2)")
    raise AssertionError("simultaneous box/place accepted")
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT count(*) FROM storage_items").fetchone()[0]==2
print("Storage QR, place inheritance, cycle prevention, history, backup v19: PASS")
