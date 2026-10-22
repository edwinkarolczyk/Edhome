#!/usr/bin/env python3
"""Regresja P0 LAN: wznawianie klienta oraz heartbeat bez modyfikowania baz."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
svc=(root/"app/src/main/java/com/edwinkarolczyk/edhome/LanSyncService.java").read_text(encoding="utf-8")
sync=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DesktopHubSync.java").read_text(encoding="utf-8")
pc=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
junit=(root/"desktop/src/test/java/com/edhome/desktop/DesktopHubServerRuntimeTest.java").read_text(encoding="utf-8")
service_create=svc.split("@Override public void onCreate()",1)[1].split(
    "private void updateNotificationStatus()",1)[0]
assert "DesktopHubSync.ensureScheduled(this);" in service_create
assert "START_STICKY" in svc
sync_once=sync.split("private static void syncOnce(Context context)",1)[1].split(
    "private static void register(Context context",1)[0]
assert sync_once.count("heartbeatWithoutDataTransfer(context,prefs);")==2
heartbeat=sync.split("private static void heartbeatWithoutDataTransfer(",1)[1].split(
    "private static void register(",1)[0]
assert "resolveHost(context,prefs)" in heartbeat
assert "register(context,prefs,host)" in heartbeat
assert '"API POŁĄCZONE • dane czekają na uzgodnienie"' in heartbeat
for forbidden in ("applyServerSnapshot(", '"/patch"', '"/snapshot"',
                  ".putLong(PREF_LAST_SYNC", ".remove(PREF_LAST_SYNC",
                  "clearConflict(", "restoreJson(", "applyPatch("):
    assert forbidden not in heartbeat, forbidden
assert "ensureHubApiRunning();" in pc.split("private void startReconnectLoop()",1)[1]
watchdog=pc.split("private void ensureHubApiRunning()",1)[1].split(
    "private void startReconnectLoop()",1)[0]
assert "30000L" in watchdog and "hubServer.close()" in watchdog
assert "startHubApi();" in watchdog and "HUB_API_WATCHDOG_RESTART" in watchdog
assert "repeatedApiHeartbeatDoesNotWriteDataAndHubCanRestart()" in junit
print("EDHOME LAN: serwis telefonu, heartbeat konfliktu, odtwarzanie Huba OK")
