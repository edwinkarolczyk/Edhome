#!/usr/bin/env python3
"""Beta installer must be single-flight, retryable and poll modestly."""
from pathlib import Path
src=Path("app/src/main/java/com/edwinkarolczyk/edhome/BetaUpdater.java").read_text(encoding="utf-8")
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
for token in (
 "AUTO_CHECK_MS = 15L * 60L * 1000L;",
 "now - lastAutomaticCheckMs >= AUTO_CHECK_MS",
 "lastAutomaticCheckMs = now;",
 "targetFile.equals(installerStartedFor)",
 "if (candidate.equals(installerStartedFor)) return;",
 "if (apk.equals(installerStartedFor))",
 "installerStartedFor = apk;",
 "UPDATE_INSTALL_ALREADY_OPEN",
 "installerStartedFor = null;",
 "public void installReady()",
 "DiagnosticLog.event(\"UPDATE_INSTALLER_OPENED\")",
 "edhome_ts=",
 "conn.setUseCaches(false);",
 "conn.setDefaultUseCaches(false);",
 "Cache-Control",
 "no-cache, no-store",
 "Pragma",
 "public void destroy()",
 "handler.removeCallbacksAndMessages(null);",
 "background.shutdownNow();",
 "if (destroyed || activity.isFinishing() || activity.isDestroyed()) return;",
 "UPDATE_SUPERSEDED_DOWNLOAD_REMOVED",
 "manager.remove(activeDownload)",
 "UPDATE_DOWNLOAD_RECORD_MISSING",
 "check(manual);",
):
 assert token in src, token
assert src.count("check(false);")==1
assert src.count("installerStartedFor = apk;")==1
assert src.count("handler.post(() ->")==1
assert src.count("postUi(() ->")>=3
assert "if (updater != null) updater.destroy();" in main
for token in (
    'button("QR do pobrania Beta / Stable", this::showUpdateDownloadQrCodes);',
    'showUpdateDownloadQrCodes',
    'releases/download/beta-v',
    'edhome-beta.apk',
    'https://play.google.com/store/apps/details',
    'id=com.edwinkarolczyk.edhome',
    'QR pobierania EDHOME Beta',
    'QR pobierania EDHOME Stable',
):
    assert token in main, token
assert 'updater.showLatestChanges()' in main
assert 'Układ Start 3 × 3. Gdy jest więcej niż 9 kafelków' not in main
assert 'QR Beta /\\nStable' in main
assert main.count('updateTile(tiles,') == 6  # pięć aktywnych kafelków Beta (QR na głównym ekranie)
for token in (
    'public void showLatestChanges()',
    'private void cacheLatestRelease(JSONObject manifest)',
    'cacheLatestRelease(json);',
    'validReleaseManifest(read)',
    'Ostatnio zapisany opis (bez potwierdzenia aktualności)',
):
    assert token in src, token
print("Updater single-flight, explicit retry, throttle and Beta/Stable QR: PASS")
