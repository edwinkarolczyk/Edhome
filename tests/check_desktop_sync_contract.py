from pathlib import Path

root = Path(__file__).resolve().parents[1]
server = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncServer.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
service = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncService.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
sync_store = (root / "app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")

assert '"/snapshot"' in server
assert '"X-EDHOME-TOKEN"' in desktop
assert '"x-edhome-token"' in server
assert 'POST' in server
assert 'BetaUpdater.isBeta()' in main
assert 'DataBackup.exportJson' in service
assert 'PrivatePaycheck' not in server
assert 'desktop_sync_token' in server
assert 'isSiteLocalAddress()' in server
assert 'android ↔ pc' in desktop.lower()

print("desktop sync contract OK")

assert 'PAIR_PORT = 45824' in desktop
assert 'MultiFormatWriter' in desktop
assert 'edhome://desktop-pair' in desktop
assert 'x-edhome-nonce' in desktop.lower()
assert 'desktopPairQrCameraPending' in main
assert 'Skanuj QR z ekranu PC' in main
assert 'DESKTOP_QR_PAIRED' in main

assert 'X-EDHOME-NONCE' in main

manifest = (root / "app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android:usesCleartextTraffic="true"' in manifest
assert 'X-EDHOME-BASE-SHA256' in desktop
assert 'x-edhome-base-sha256' in server
assert 'DESKTOP_SYNC_SNAPSHOT_WRITTEN' in server
assert 'DataBackup.restoreJson' in service
assert 'Zapisz zmiany do telefonu' in desktop
assert '409' in server

assert 'LanSyncService.ensureStarted(this)' in main
assert 'startForeground' in service
assert 'START_STICKY' in service
assert 'android:name=".LanSyncService"' in manifest
assert 'FOREGROUND_SERVICE_CONNECTED_DEVICE' in manifest

assert 'hasRecentClient()' in server
assert 'LAST_CLIENT_SEEN_AT' in server
assert '●  ⇄ PC' in main
assert '○  ⇄ PC' in main
assert 'Stan PC:' in main
assert '"Skaner"' in desktop
assert 'DesktopHardwareScanner' in desktop
assert '"nfc_links"' in desktop
assert 'DATABASE_MIGRATED_34_TO_35_NFC_LINKS' in main
backup = (root / "app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
assert 'DB_VERSION = 36' in backup
assert '{"nfc_links"' in backup

# Incremental record sync v2 + backward-compatible v1.
assert '"/patch"' in server
assert '"edhome-record-patch"' in sync_store
assert 'RevisionProvider' in server
assert 'DESKTOP_SYNC_PATCH_WRITTEN' in server
assert 'DESKTOP_SYNC_PATCH_CONFLICT' in server
assert 'PRAGMA data_version' in service
assert 'buildRecordPatch' in desktop
assert 'baseRowSha256' in desktop
assert 'PatchUnsupportedException' in desktop
assert 'new javax.swing.Timer(1500' in desktop
assert '180000L' in desktop
assert 'ZAPISANO LOKALNIE' in desktop
print("desktop incremental sync contract OK")

# Hardening: direct SQLite patching, UUID identity, revisions and tombstones.
patch_handler = server.split('if ("POST".equals(method) && "/patch".equals(path))',1)[1].split(
    'if ("POST".equals(method) && "/snapshot".equals(path))',1)[0]
assert 'patchProvider.patch(incoming)' in patch_handler
assert 'restoreProvider.restore' not in patch_handler
assert 'applyRecordPatch(incoming)' not in patch_handler
assert 'SyncRecordStore.applyPatch' in service

for token in (
    'sync_records', 'sync_uuid', 'revision', 'updated_at', 'deleted_at',
    'row_hash', 'UUID.randomUUID()', 'applyPatch(SQLiteDatabase db',
    'db.update(', 'db.delete(', 'insertOrThrow', 'baseRevision',
    'SyncConflict'
):
    assert token in sync_store, "Missing hardened sync contract: "+token

assert 'DATABASE_MIGRATED_35_TO_36_SYNC_RECORDS' in main
assert 'SyncRecordStore.create(database)' in main
assert 'syncRecords' in backup
assert 'SyncRecordStore.restoreMetadata' in backup
assert 'payload.addProperty("version", 2)' in desktop
assert 'syncUuid' in desktop and 'baseRevision' in desktop and 'rowKey' in desktop
assert 'ensureDesktopSyncMetadata' in desktop
assert 'applyPatchAck' in desktop
assert '1_000_000_000_000L' in desktop
print("desktop sync v2 hardening contract OK")
