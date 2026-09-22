#!/usr/bin/env python3
"""Beta installer must be single-flight, retryable and poll modestly."""
from pathlib import Path
src=Path("app/src/main/java/com/edwinkarolczyk/edhome/BetaUpdater.java").read_text(encoding="utf-8")
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
):
 assert token in src, token
assert src.count("check(false);")==1
assert src.count("installerStartedFor = apk;")==1
print("Updater single-flight, explicit retry and throttle: PASS")
