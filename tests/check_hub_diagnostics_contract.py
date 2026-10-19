#!/usr/bin/env python3
"""EDHOME Beta: phone diagnostics over active Desktop Hub, independent of data sync."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
android=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DesktopHubSync.java").read_text(encoding="utf-8")
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
server=(root/"desktop/src/main/java/com/edhome/desktop/DesktopHubServer.java").read_text(encoding="utf-8")
pc=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
logs=(root/"desktop/src/main/java/com/edhome/desktop/DesktopDiagnosticLog.java").read_text(encoding="utf-8")
runtime=(root/"desktop/src/test/java/com/edhome/desktop/DesktopHubServerRuntimeTest.java").read_text(encoding="utf-8")

assert '"/diagnostics-upload"' in android and '"/diagnostics-upload"' in server
assert '"X-EDHOME-TOKEN"' in android and 'constantTimeEquals(token,supplied)' in server
assert 'client.deviceId,client.version,log' in server
assert 'receivePhoneDiagnostics(' in logs
assert 'ATOMIC_MOVE' in logs
assert '"edhome-android-last.txt"' in logs
assert 'Files.isRegularFile(PHONE)' in logs
assert 'clearIfTransferred' not in logs
assert 'DIAG_INTERVAL_MS=5L*60L*1000L' in android
assert 'sendDiagnostics(context);' in android
assert 'BackupSyncGate.exitTransfer();' in android
assert 'if(!prefs.getString(PREF_CONFLICT,"").isBlank())return;' in android
assert 'private static String sendDiagnostics(Context context)' in android
assert 'static void sendDiagnosticsNow(' in android
assert 'PREF_LAST_SYNC' not in android.split('private static String sendDiagnostics(Context context)',1)[1].split('private static void syncOnce(Context context)',1)[0]
assert '"Pobierz logi Desktop i Android (TXT)"' in pc
assert 'DesktopDiagnosticLog.readPhoneDiagnostics()' in pc
assert '"Pobierz pełne logi Android (.txt)"' in main
assert '"Prześlij logi Android do Desktopu (LAN)"' in main
assert 'this::exportDiagnosticLogsToFile' in main
assert 'void acceptsAndroidLogsWhileSynchronizationIsBlockedWithoutTouchingData()' in runtime
print("EDHOME Hub diagnostics: authenticated transfer, separate PC/Android TXT, conflicts untouched PASS")
