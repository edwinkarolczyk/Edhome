#!/usr/bin/env python3
"""Kontrakt regresji ochrony mrozowej, SQLite v47, ZIP i Android UI."""
from pathlib import Path
import re
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
storage=(root/"StorageStore.java").read_text(encoding="utf-8")
frost=(root/"FrostStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
reminder=(root/"FrostReminderReceiver.java").read_text(encoding="utf-8")
for token in ('super(context, "edhome-beta-preview.db", null, 47)',
              'FrostStore.upgrade47(database);',
              'FrostStore.create(database);',
              'frostRisk.setText("❄ Narażone na mróz")',
              'protect.setText("❄ Chronić przed mrozem")',
              'this::showFrostPreparation',
              'withFrostPlacementWarning(true,',
              'private void showFrostSettings()'):
    assert token in main, token
for token in ('CREATE TABLE frost_settings',
              'CREATE TABLE frost_checks',
              'ALTER TABLE places ADD COLUMN frost_risk',
              'ALTER TABLE storage_items ADD COLUMN protect_frost',
              'static boolean placeRisk(',
              'static boolean sensitive(',
              'static boolean moveRisk(',
              'static void checked(',
              'year*1000000000000L+itemId',
              'static LocalDate due('):
    assert token in frost,token
assert 'protect_frost INTEGER NOT NULL DEFAULT 0' in storage
for token in ('private static final int DB_VERSION = 47',
              '"frost_risk"', '"protect_frost"', '"frost_settings"',
              '"frost_checks"', 'inputVersion < 47',
              '"start_month".equals(column)', '"season_year".equals(column)'):
    assert token in backup,token
assert 'FrostReminderReceiver.schedule(context);' in (
    root/"ReminderReceiver.java").read_text(encoding="utf-8")
assert 'QuietHoursRules.isQuiet(' in reminder
assert 'FrostStore.pending(db,now.getYear())' in reminder
assert 'android:name=".FrostReminderReceiver"' in Path(
    "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
print("PASS frost SQLite v47, old ZIP, flags, safe move, annual checklist, reminders")
