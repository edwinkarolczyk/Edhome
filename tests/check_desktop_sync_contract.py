from pathlib import Path

root = Path(__file__).resolve().parents[1]
server = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncServer.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
service = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncService.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

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
