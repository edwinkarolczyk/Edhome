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
    'super(context, "edhome-beta-preview.db", null, 39)',
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
    'DB_VERSION = 39;',
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
assert 'id INTEGER PRIMARY KEY AUTOINCREMENT' in store
assert 'CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT' in main
assert 'QR zostanie unieważniony' in main
assert 'przydzielony nowemu obiektowi' in main
assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84 and "versionNameSuffix ''" in gradle

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
# AUTOINCREMENT is part of the QR identity guarantee: a deleted local ID
# must never be reassigned to a different thing/box.
old_max=db.execute("SELECT max(id) FROM storage_items").fetchone()[0]
db.execute("DELETE FROM storage_items WHERE id=2")
db.execute("DELETE FROM storage_items WHERE id=1")
new_id=db.execute("INSERT INTO storage_items(name,kind,created_at) VALUES ('Next','box',3)").lastrowid
assert new_id>old_max, (old_max,new_id)
print("Storage QR, place inheritance, cycle prevention, history, backup v19: PASS")

# QR label printing contract: place IDs remain distinct from things/boxes.
labels=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageQrLabels.java").read_text()
provider=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageQrPdfProvider.java").read_text()
beta=Path("app/src/beta/AndroidManifest.xml").read_text()
for token in (
    '"place".equals(kind)', 'StorageQr.encode("place",place.id)',
    'selectBulkQrLabels()', 'selectQrLabelFormat(',
    'StorageQrLabels.pdf(selected,format)', 'StorageQrLabels.print(this,pdf,',
    'Intent.ACTION_CREATE_DOCUMENT', '"application/pdf"',
    'StorageQrLabels.log(this, "Skan miejsca", qrLabel(place))',
    'StorageQrLabels.log(this,\n                        "Skan " +',
    'showQrHistory()', 'StorageStore.returned(',
):
    assert token in (qr+"\n"+main), "Missing QR integration: "+token
for token in ('"40 × 30 mm"', '"50 × 30 mm"', '"70 × 50 mm"',
    '"A4 — zbiorczo"', 'new PdfDocument()', 'BarcodeFormat.QR_CODE',
    'PrintDocumentAdapter', 'PrintManager', 'HISTORY_LIMIT',
    'int perPage = rows * columns;', 'StorageQr.encode(kind,id)'):
    assert token in labels, "Missing PDF/print: "+token
assert 'settings.put("storageQrHistory"' in backup
assert 'String qrHistory = settings.optString("storageQrHistory","")' in backup
assert '.putString(StorageQrLabels.HISTORY, qrHistory)' in backup
assert 'android:authorities="${applicationId}.qrpdf"' in beta
assert 'android:exported="false"' in beta
assert '"r".equals(mode)' in provider
assert 'getCacheDir()' in main and 'FLAG_GRANT_READ_URI_PERMISSION' in main
print("QR place/thing/box, printable formats, PDF export/share and history: PASS")


# Stabilizacja 0.7.4.5: QR identity is never recycled by sync or backup restore.
sync=Path("app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text()
assert 'private static boolean qrIdentityTable(String table)' in sync
assert '"storage_items".equals(table) || "places".equals(table)' in sync
assert 'retiredRowExists(db, table, rowKey)' in sync
assert 'deleted_at IS NOT NULL' in sync
assert 'reserveQrIdentitySequences(database);' in backup
assert 'new String[]{"storage_items","places"}' in backup
assert 'MAX(CAST(row_key AS INTEGER)) FROM sync_records' in backup
version_name=next(line.split("'")[1] for line in gradle.splitlines()
    if line.strip().startswith("versionName "))
version_parts=version_name.split(".")
assert version_parts[:3]==["0","7","4"] and int(version_parts[3]) >= 5
print("Storage QR tombstone identity + restore sequence reservation: PASS")

# Idempotencja lokalizacji: ponowne wskazanie tego samego celu nie tworzy historii moved.
assert 'static boolean sameDestination(Item item,Long box,Long place)' in store
assert 'if(sameDestination(item,box,place))' in store
assert 'static Long effectivePlaceId(SQLiteDatabase db,Item item)' in store
print("Storage same-destination no-op + inherited place helper: PASS")
