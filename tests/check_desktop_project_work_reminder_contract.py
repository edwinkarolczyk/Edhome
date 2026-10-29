#!/usr/bin/env python3
"""Kontrakt przypomnień Desktop: przypomnienie nie kończy sesji pomiaru."""
from pathlib import Path

source = Path("desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
build = Path("desktop/build.gradle").read_text(encoding="utf-8")
workflow = Path(".github/workflows/desktop-beta.yml").read_text(encoding="utf-8")

assert 'DESKTOP_VERSION = "0.7.0.144"' in source
assert "version = '0.7.0.144'" in build
assert 'DESKTOP_VERSION: "0.7.0.144"' in workflow
assert "startProjectWorkReminderLoop();" in source
assert "new javax.swing.Timer(30_000, e -> checkProjectWorkReminders())" in source
assert 'projectWorkReminderIntervalMinutes()' in source
assert 'PREFS.getLong(key, startedAt + projectWorkReminderIntervalMs())' in source
assert 'PREFS.putLong(key, now + projectWorkReminderIntervalMs())' in source
assert 'projectWorkReminderIntervalMinutes", 60' in source
assert 'PROJECT_WORK_REMINDER_INTERVAL_SAVED' in source
assert 'new Integer[] {15, 30, 45, 60, 90, 120}' in source or 'Integer[] reminderIntervals = {15, 30, 45, 60, 90, 120}' in source

block = source.split('private void checkProjectWorkReminders()', 1)[1].split('private void startDesktopProjectWork', 1)[0]
assert 'if (!value(session, "ended_at").isBlank()) continue;' in block
assert 'session.addProperty("ended_at"' not in block
assert 'session.addProperty("worked_minutes"' not in block
assert 'PROJECT_WORK_REMINDER_DESKTOP' in block
assert 'displayMessage("EDHOME • Czynność"' in block

start = source.split('private void startDesktopProjectWork(JsonObject task)', 1)[1].split('private void stopDesktopProjectWork', 1)[0]
assert 'PREFS.putLong(projectWorkReminderKey(longValue(session, "id"))' in start
stop = source.split('private void stopDesktopProjectWork(JsonObject task)', 1)[1].split('private void addDesktopProjectTask', 1)[0]
assert 'session.addProperty("ended_at",ended);' in stop
assert 'PREFS.remove(projectWorkReminderKey(longValue(session, "id")));' in stop
print("Desktop project timer reminder contract: PASS")
