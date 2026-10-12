#!/usr/bin/env python3
"""P0: ZIP restore nie omija ochrony LAN Android ↔ Desktop."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
app = root / "app/src/main/java/com/edwinkarolczyk/edhome"
gate = (app / "BackupSyncGate.java").read_text(encoding="utf-8")
hub = (app / "DesktopHubSync.java").read_text(encoding="utf-8")
lan = (app / "LanSyncServer.java").read_text(encoding="utf-8")
service = (app / "LanSyncService.java").read_text(encoding="utf-8")
main = (app / "MainActivity.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

def section(text, begin, end):
    a = text.index(begin)
    b = text.index(end, a + len(begin))
    return text[a:b]

for token in (
    'private static final ReentrantLock LOCK = new ReentrantLock(true);',
    'private static final String HOLD = "desktop_sync_restore_review_required";',
    'static void beginRestore(Context context)',
    'putBoolean(HOLD, true).commit()',
    'static void finishRestore(Context context, boolean success)',
    'DesktopHubSync.invalidateAfterLocalRestore(context)',
    'static boolean isHeld(Context context)',
    'return restoring || prefs(context).getBoolean(HOLD,false);',
    'static void allowAfterReview(Context context)',
    'putBoolean(HOLD,false).commit()',
    'LOCK.lock();', 'LOCK.unlock();'
):
    assert token in gate, "Brak fail-closed lock: " + token

restore = section(main,
    'private void restoreDataBackupAsync(java.io.File backupFile,',
    'private void showDataBackupBusy(')
undo = section(main,
    'private void confirmDataBackupUndo(java.io.File selected,',
    'private void backup() {')
for text in (restore, undo):
    assert 'BackupSyncGate.beginRestore(this);' in text
    assert 'BackupSyncGate.finishRestore(this,' in text
    assert text.index('BackupSyncGate.beginRestore(this);') < text.index(
        'DataBackupArchive.recoveryCheckpoint(')
    assert 'finally {' in text
assert 'restoredData=true;' in restore
assert 'boolean restoreGateStarted=false;' in restore
assert 'restoreCompleted&&!recovered' in undo

for token in (
    'server.setTransferBlocked(()->BackupSyncGate.isHeld(this))',
    'this::changesSince',
    'this::applyRecordPatch'
):
    assert token in service, "Serwer LAN nie sprawdza blokady importu: " + token

handle = section(lan, 'private void handle(Socket socket) {',
                 'private static String queryValue(')
assert 'BackupSyncGate.enterTransfer();' in handle
assert 'if (transferBlocked.isBlocked())' in handle
assert '"RESTORE_RECONCILIATION_REQUIRED"' in handle or (
    r'\"RESTORE_RECONCILIATION_REQUIRED\"' in handle)
assert 'reply(peer,409,' in handle
assert 'BackupSyncGate.exitTransfer();' in handle
assert handle.index('if (transferBlocked.isBlocked())') < handle.index(
    'if ("GET".equals(method) && "/snapshot".equals(path))')
assert handle.index('if (transferBlocked.isBlocked())') < handle.index(
    'if ("GET".equals(method) && "/changes".equals(path))')
assert handle.index('if (transferBlocked.isBlocked())') < handle.index(
    'if ("POST".equals(method) && "/patch".equals(path))')
assert handle.index('if (transferBlocked.isBlocked())') < handle.index(
    'if ("POST".equals(method) && "/snapshot".equals(path))')

for token in (
    'static void invalidateAfterLocalRestore(Context context)',
    'baselineFile(context)',
    'if(baseline.isFile()&&!baseline.delete())',
    '.remove(PREF_BASE_SHA)',
    'HUB_BASELINE_INVALIDATED_AFTER_RESTORE',
    'HUB_RESTORE_HOLD_NO_AUTO_PUSH_PULL',
    'if(BackupSyncGate.isHeld(context))',
    'BackupSyncGate.allowAfterReview(context)',
    'BackupSyncGate.enterTransfer();',
    'BackupSyncGate.exitTransfer();',
    '"Po przywróceniu ZIP Telefon i Desktop mogą zawierać "',
    'saveConflictBackup(app,prefs);',
    'X-EDHOME-BASE-SHA256'
):
    assert token in hub, "Hub nie jest odporny na przywrócenie ZIP: " + token

sync_once = section(hub, 'private static void syncOnce(Context context)',
                    'private static void register(Context context')
assert sync_once.index('if(BackupSyncGate.isHeld(context))') < sync_once.index(
    'String host=resolveHost(context,prefs);')
assert sync_once.index('if(BackupSyncGate.isHeld(context))') < sync_once.index(
    'HttpResult boot=request(context,prefs,host,"POST",')
assert 'if(!baseline.isFile()) {' in sync_once
assert 'HUB_NO_BASELINE_CONFLICT' in sync_once

for token in (
    'Zastąpić dane Desktopu stanem telefonu?',
    'Zastąpić dane telefonu stanem Desktopu?',
    'Synchronizacja z PC jest WSTRZYMANA',
    'Wznów starsze połączenie LAN (po sprawdzeniu PC)',
    'BackupSyncGate.allowAfterReview(this)',
    'DesktopHubSync.resolvePhone(this)',
    'DesktopHubSync.resolveDesktop(this)'
):
    assert token in main, "Brak decyzji użytkownika: " + token

assert 'SYNC_PHONE_REVISION_REGRESSED' in desktop
assert 'pullFromPhone(host, secret, null, true);' in desktop

sources = root / "tests/sync_restore_gate_java"
with tempfile.TemporaryDirectory(prefix="edhome-sync-restore-") as target:
    subprocess.run([
        "javac", "-encoding", "UTF-8", "-d", target,
        str(sources / "android/content/Context.java"),
        str(sources / "android/content/SharedPreferences.java"),
        str(sources / "com/edwinkarolczyk/edhome/BackupSyncGateSmoke.java"),
        str(app / "BackupSyncGate.java")
    ], check=True, cwd=root)
    subprocess.run([
        "java", "-cp", target,
        "com.edwinkarolczyk.edhome.BackupSyncGateSmoke"
    ], check=True, cwd=root)

print("EDHOME P0: ZIP↔LAN restore gate, both network paths, "
      "fail-closed, persistent pause, user approval PASS")
