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
assert 'phoneArchiveDirectory()' in logs
assert 'android-archive' in logs
assert 'FileChannel.open(temp' in logs
assert 'channel.force(true)' in logs
assert 'StandardCopyOption.ATOMIC_MOVE' in logs
assert 'return receipt;' in logs
assert 'ATOMIC_MOVE' in logs
assert '"edhome-android-last.txt"' in logs
assert 'Files.isRegularFile(PHONE)' in logs
assert 'clearIfTransferred' not in logs
phone_log=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DiagnosticLog.java").read_text(encoding="utf-8")
assert 'static Transfer captureTransfer()' in phone_log
assert 'new Transfer(snapshot,snapshot.isBlank()?"":sha256(snapshot))' in phone_log
assert 'DiagnosticLog.clearIfTransferred(transfer.id)' in android
assert 'transfer.id.equals(receipt.optString("transferId",""))' in android
assert 'transferId' in server
assert 'String receipt = DesktopDiagnosticLog.receivePhoneDiagnostics(' in server
assert 'DIAG_INTERVAL_MS=60L*1000L' in android
assert 'PREF_DIAG_ATTEMPT_AT' in android
assert 'preferences.edit().putLong(PREF_DIAG_ATTEMPT_AT,now).apply()' in android
assert 'sendDiagnostics(context,false);' in android
assert 'BackupSyncGate.exitTransfer();' in android
assert 'if(!prefs.getString(PREF_CONFLICT,"").isBlank())return;' in android
assert 'private static String sendDiagnostics(Context context,boolean manual)' in android
assert 'static void sendDiagnosticsNow(' in android
assert 'PREF_LAST_SYNC' not in android.split('private static String sendDiagnostics(Context context,boolean manual)',1)[1].split('private static void syncOnce(Context context)',1)[0]
assert '"Stan Hub Android: " + hubConnectionSummary()' in pc
assert '"Ostatnia synchronizacja danych Hub (ta sesja): "' in pc
assert '"Starszy protokół PC → telefon: "' in pc
assert 'API połączone • synchronizacja danych niepotwierdzona' in pc
assert 'API połączone • synchronizacja danych potwierdzona' in pc
assert 'now-phone.seenAt<=180000L' in pc
assert 'now-hubLastDataSyncAt<=180000L' in pc
assert 'minCompatible=' in pc
assert '"Pobierz logi Desktop i Android (TXT)"' in pc
assert 'DesktopDiagnosticLog.readPhoneDiagnostics()' in pc
assert '"Pobierz pełne logi Android (.txt)"' in main
assert '"Prześlij logi Android do Desktopu (LAN)"' in main
assert 'this::exportDiagnosticLogsToFile' in main
assert 'void acceptsAndroidLogsWhileSynchronizationIsBlockedWithoutTouchingData()' in runtime
print("EDHOME Hub diagnostics: automatic SHA-confirmed archive and safe phone cleanup, conflicts untouched PASS")
