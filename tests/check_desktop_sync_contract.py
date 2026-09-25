from pathlib import Path

root = Path(__file__).resolve().parents[1]
server = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncServer.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

assert '"/snapshot"' in server
assert '"X-EDHOME-TOKEN"' in desktop
assert '"x-edhome-token"' in server
assert 'READ_ONLY' in server
assert 'BetaUpdater.isBeta()' in main
assert 'DataBackup.exportJson' in main
assert 'PrivatePaycheck' not in server
assert 'desktop_sync_token' in server
assert 'isSiteLocalAddress()' in server
assert 'tylko odczyt' in desktop.lower()

print("desktop sync contract OK")
