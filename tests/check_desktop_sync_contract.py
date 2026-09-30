from pathlib import Path

root = Path(__file__).resolve().parents[1]
server = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncServer.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
service = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncService.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
sync_store = (root / "app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")
desktop_log = (root / "desktop/src/main/java/com/edhome/desktop/DesktopDiagnosticLog.java").read_text(encoding="utf-8")
phone_log = (root / "app/src/main/java/com/edwinkarolczyk/edhome/DiagnosticLog.java").read_text(encoding="utf-8")

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
assert 'WATCHDOG_MS = 5000L' in service
assert 'DESKTOP_SYNC_WATCHDOG_RESTART' in service
assert 'endpointRunning()' in service
assert 'Napraw / uruchom połączenie PC' in main
assert 'Serwer LAN:' in main
assert 'LAST_CONNECTION_ATTEMPT_AT' in server
assert 'lastConnectionResult()' in server
assert 'odrzucono: nieprawidłowy kod parowania' in server
assert 'Ostatnia próba PC:' in main
assert 'Serwer LAN działa • czeka na PC' in service
assert 'InetAddress.getByName("0.0.0.0")' in server
assert 'bind=0.0.0.0 port=' in server
assert 'Diagnostyka połączenia z PC' in main
assert 'lanTcpSelfTest("127.0.0.1")' in main
assert 'Problem jest po stronie interfejsu/bindu Androida.' in main
assert 'Kopiuj diagnostykę telefonu' in main
assert 'Kopiuj logi diagnostyczne' in main
assert 'copyDiagnosticLogsFromSettings' in main
assert 'PHONE_LAN_DIAGNOSTICS' in main
assert 'Ostatni adres PC:' in main
assert 'Ostatni wynik:' in main
assert 'Połączono z EDHOME Desktop' in service

assert 'hasRecentClient()' in server
assert 'LAST_CLIENT_SEEN_AT' in server
assert 'isSyncing()' in server
assert 'LAST_SYNC_ACTIVITY_AT' in server
assert 'ACTIVE_SYNC_COUNT' in server
assert 'beginSyncActivity()' in server
assert 'endSyncActivity()' in server
assert '●  ⇄ PC' in main
assert '●  →   PC' in main
assert '●    ← PC' in main
assert 'desktopConnectionGreen()' in main
assert 'desktopConnectionRed()' in main
assert 'desktopLastSyncTime()' in main
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
assert 'SyncRecordStore.ensureAll(database)' in service
assert 'SELECT COALESCE(SUM(revision),0) FROM sync_records' in service
assert 'rawQuery("PRAGMA data_version"' not in service
assert 'buildRecordPatch' in desktop
assert 'baseRowSha256' in desktop
assert 'PatchUnsupportedException' in desktop
assert 'new javax.swing.Timer(1500' in desktop
assert '180000L' in desktop
assert 'ZAPISANO LOKALNIE' in desktop
assert 'localAddresses()' in desktop
assert 'LinkedHashSet<String> prefixes' in desktop
assert 'newFixedThreadPool(96)' in desktop
assert 'ExecutorCompletionService<String>' in desktop
assert 'Diagnostyka połączenia PC ↔ telefon' in desktop
assert 'diagnosePhoneConnection' in desktop
assert 'TCP ' in desktop and 'BRAK POŁĄCZENIA' in desktop
assert 'Automatyczne szukanie telefonu' in desktop
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


# DHCP/IP change recovery: failed heartbeat must drop ONLINE state and trigger rediscovery.
assert 'OFFLINE • szukam telefonu po zmianie IP' in desktop
assert 'connected = false;' in desktop
assert 'SwingUtilities.invokeLater(() -> autoConnectSaved(true))' in desktop
print("desktop DHCP reconnect contract OK")

# Phone LAN self-tests must never impersonate a real Desktop client.
assert 'isThisDeviceAddress(InetAddress remote)' in server
assert 'remote.isLoopbackAddress()' in server
assert 'if (!selfClient)' in server
assert 'LAST_CLIENT_SEEN_AT = System.currentTimeMillis();' in server
assert 'syncTransfer = !selfClient' in server
assert 'Self-test telefonu: nie jest liczony jako połączenie PC' in main
print("desktop self-test isolation contract OK")

# Android UI must refresh the currently visible screen after Desktop writes data.
assert 'DESKTOP_DATA_CHANGED_UI_REFRESH' in main
assert 'desktopDataChangedReceiver' in main
assert 'new IntentFilter(' in main
assert 'DESKTOP_DATA_CHANGED' in main
assert 'registerReceiver(desktopDataChangedReceiver' in main
assert 'unregisterReceiver(desktopDataChangedReceiver)' in main
assert 'if (root != null && !isFinishing()) render();' in main
assert 'screenScrollY' in main
print("desktop-to-android live view refresh contract OK")


# Phone diagnostics transfer: download first, delete only after Desktop ACK.
for marker in ('"/diagnostics"', '"/diagnostics/ack"', '"x-edhome-diagnostics-id"',
               'replyNoContent(peer)', 'DiagnosticLog.clearIfTransferred'):
    assert marker in server, "Missing phone diagnostics transfer contract: " + marker
for marker in ('transferId()', 'clearIfTransferred(String expectedId)',
               'MessageDigest.getInstance("SHA-256")'):
    assert marker in phone_log, "Missing safe diagnostics cleanup: " + marker
for marker in ('Pobierz nowe logi z telefonu', 'downloadPhoneDiagnostics',
               'ackDiagnostics', 'X-EDHOME-DIAGNOSTICS-ID',
               'Brak nowych logów diagnostycznych na telefonie',
               'Kopiuj diagnostykę EDHOME Desktop',
               'Zapisz diagnostykę Desktop TXT'):
    assert marker in desktop, "Missing Desktop diagnostics UX: " + marker
for marker in ('edhome-desktop.log', 'edhome-desktop.previous.log',
               'MAX_BYTES', 'readFullText()', 'readForClipboard()'):
    assert marker in desktop_log, "Missing Desktop rolling diagnostics log: " + marker
assert 'Files.writeString(target, result.text, StandardCharsets.UTF_8)' in desktop
assert '.ackDiagnostics(result.id)' in desktop
print("desktop diagnostics transfer + local log contract OK")

# Desktop diagnostics must preserve the real async failure behind SwingWorker/ExecutionException.
assert 'rootCause(problem)' in desktop_log
assert 'root=' in desktop_log and 'message=' in desktop_log
print("desktop diagnostics root-cause contract OK")


# Local Desktop cache must tolerate Windows file locks and concurrent cache writes.
for marker in (
    'private static synchronized void saveCache(JsonObject data)',
    'Files.createTempFile(CACHE.getParent()',
    'Thread.sleep(75L * attempt)',
    'DesktopDiagnosticLog.error("LOCAL_CACHE_SAVE", error)'
):
    assert marker in desktop, "Missing resilient local cache save contract: " + marker
print("desktop resilient local cache save contract OK")


# New Desktop-created records must be cached only after they are inserted
# into the backing table. Otherwise a failed phone sync can lose the record
# on Desktop restart even though the UI claimed a local save.
add_idx = desktop.index('table(tableName).add(row);')
persist_idx = desktop.index('markDirty();', add_idx)
assert persist_idx > add_idx, "New records are not persisted after table insertion"
assert persist_idx - add_idx < 500, "Persistence must happen immediately after insertion"
print("desktop new-record local persistence contract OK")


# Desktop settings: one click must export Desktop + Android diagnostics directly to Windows Desktop.
for marker in (
    'Pobierz logi telefonu + Desktop na Pulpit',
    'saveAllDiagnosticsToDesktop(',
    'diagnosticsDesktopFolder()',
    'EDHOME-Desktop-diagnostyka-',
    'EDHOME-Android-diagnostyka-',
    'PHONE_DIAGNOSTICS_CLEARED'
):
    assert marker in desktop, "Missing one-click diagnostics export contract: " + marker
assert 'downloadAllLogs.addActionListener' in desktop
assert 'Files.writeString(desktopTarget, desktopDiagnosticsText()' in desktop
assert 'Files.writeString(phoneTarget, phone.text' in desktop
print("desktop one-click diagnostics export contract OK")
