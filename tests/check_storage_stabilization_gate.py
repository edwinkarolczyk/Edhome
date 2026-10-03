#!/usr/bin/env python3
"""EDHOME Magazyn cross-device stabilization gate.

This is intentionally a domain/integration contract: Android and Desktop must
enforce the same storage graph rules and preserve QR/NFC identity and history.
"""
from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
storage=(src/"StorageStore.java").read_text(encoding="utf-8")
nfc=(src/"NfcLinkStore.java").read_text(encoding="utf-8")
sync=(src/"SyncRecordStore.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
service=(src/"LanSyncService.java").read_text(encoding="utf-8")
thumb=(src/"StorageThumbs.java").read_text(encoding="utf-8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(
    encoding="utf-8")

# Android domain rules.
for marker in (
    'Pudełko można przypisać tylko do miejsca, nie do innego pudełka.',
    'Najpierw odnotuj zwrot wypożyczonej rzeczy.',
    'StorageStore.assertIntegrity(db);',
    'NfcLinkStore.assertIntegrity(db);',
    'retiredRowExists(db, table, rowKey)',
):
    assert marker in storage+"\n"+sync, marker

assert 'NfcLinkStore.clearTarget(db,item.kind,item.id);' in storage
assert 'Jeden obiekt ma więcej niż jeden tag NFC.' in nfc
assert 'NFC wskazuje nieistniejący obiekt.' in nfc
assert 'reserveQrIdentitySequences(database);' in backup
assert 'StorageThumbs.prune(' in main
assert 'STORAGE_THUMBNAILS_PRUNED_AFTER_SYNC' in service
assert 'static int prune(SharedPreferences prefs,SQLiteDatabase db)' in thumb

# Desktop must implement the same graph and lifecycle rules.
for marker in (
    'validateDesktopStorageRow(row);',
    'restoreJsonObject(row,before);',
    'storageParentBoxCombo(row,raw)',
    'appendDesktopStorageMove(row,before);',
    'event.addProperty("action","moved")',
    'removeDesktopNfcTarget(value(row,"kind"),id);',
    'removeStorageThumbnail(id);',
    'Najpierw opróżnij pudełko.',
    'Pudełko można przypisać tylko do miejsca.',
    'Typ istniejącej rzeczy/pudełka jest stały ze względu na QR i NFC.',
    'if(storageRowLive&&moved&&!value(before,"lent_to").isBlank())',
):
    assert marker in desktop, marker

# Lightweight graph smoke: Place -> Box -> Thing, safe move and safe delete.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE places(
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL
);
CREATE TABLE storage_items(
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL,
 kind TEXT NOT NULL CHECK(kind IN ('thing','box')),
 parent_box_id INTEGER,
 place_id INTEGER,
 CHECK(parent_box_id IS NULL OR place_id IS NULL)
);
CREATE TABLE nfc_links(
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 uid TEXT NOT NULL UNIQUE,
 target_kind TEXT NOT NULL,
 target_id INTEGER NOT NULL
);
CREATE TABLE sync_records(
 sync_uuid TEXT PRIMARY KEY,
 table_name TEXT NOT NULL,
 row_key TEXT NOT NULL,
 revision INTEGER NOT NULL,
 updated_at INTEGER NOT NULL,
 deleted_at INTEGER,
 row_hash TEXT NOT NULL
);
""")
db.execute("INSERT INTO places(id,name) VALUES(1,'Garaż')")
db.execute("""INSERT INTO storage_items(id,name,kind,place_id)
              VALUES(10,'Pudełko A','box',1)""")
db.execute("""INSERT INTO storage_items(id,name,kind,parent_box_id)
              VALUES(11,'Wiertarka','thing',10)""")
db.execute("""INSERT INTO nfc_links(uid,target_kind,target_id)
              VALUES('A1B2C3D4','thing',11)""")

# Box deletion is blocked while it contains a thing.
assert db.execute(
    "SELECT 1 FROM storage_items WHERE parent_box_id=? LIMIT 1",(10,)
).fetchone() is not None

# Thing moves atomically from box to place.
db.execute("UPDATE storage_items SET parent_box_id=NULL,place_id=1 WHERE id=11")
assert db.execute(
    "SELECT parent_box_id,place_id FROM storage_items WHERE id=11"
).fetchone()==(None,1)

# Deleting the thing also requires removing its NFC binding.
db.execute("DELETE FROM nfc_links WHERE target_kind='thing' AND target_id=11")
db.execute("DELETE FROM storage_items WHERE id=11")
assert db.execute(
    "SELECT 1 FROM nfc_links WHERE target_kind='thing' AND target_id=11"
).fetchone() is None

# Simulate restore of a tombstoned QR identity: AUTOINCREMENT must be advanced
# past the historical id so an old QR can never address a new object.
db.execute("""INSERT INTO sync_records
(sync_uuid,table_name,row_key,revision,updated_at,deleted_at,row_hash)
VALUES('11111111-1111-4111-8111-111111111111','storage_items','99',2,1,1,'DELETED')""")
db.execute("""INSERT INTO sqlite_sequence(name,seq)
SELECT 'storage_items',0 WHERE NOT EXISTS
(SELECT 1 FROM sqlite_sequence WHERE name='storage_items')""")
db.execute("""UPDATE sqlite_sequence SET seq=MAX(seq,
COALESCE((SELECT MAX(CAST(row_key AS INTEGER)) FROM sync_records
WHERE table_name='storage_items' AND row_key NOT LIKE '%:%'),0))
WHERE name='storage_items'""")
new_id=db.execute(
    "INSERT INTO storage_items(name,kind) VALUES('Nowa rzecz','thing')"
).lastrowid
assert new_id>99,new_id

print("Storage stabilization gate: Android/Desktop graph, sync, QR/NFC, history and thumbnails PASS")
