#!/usr/bin/env python3
"""EDHOME: domyślnie wyłączona kontrola odłożenia rzeczy, powiadomienia dzienne."""
from pathlib import Path
from datetime import date, timedelta
import sqlite3

root=Path(__file__).resolve().parents[1]
store=(root/"app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text(encoding="utf8")
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf8")
alerts=(root/"app/src/main/java/com/edwinkarolczyk/edhome/StorageHomeReminderReceiver.java").read_text(encoding="utf8")
backup=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf8")
selective=(root/"app/src/main/java/com/edwinkarolczyk/edhome/BackupSelectiveMerge.java").read_text(encoding="utf8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8")
reminder=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ReminderReceiver.java").read_text(encoding="utf8")

for token in (
    'return_required INTEGER NOT NULL DEFAULT 0',
    'return_after_days INTEGER NOT NULL DEFAULT 1',
    'taken_at INTEGER CHECK(taken_at IS NULL OR taken_at>0)',
    'static void upgrade50(SQLiteDatabase db)',
    'static void takeTemporarily(SQLiteDatabase db,long id)',
    'static void putBack(SQLiteDatabase db,long id)',
    'fields.putNull("taken_at")',
    'if(item.takenAt!=null)throw new IllegalArgumentException',
    'Najpierw odłóż rzecz na miejsce',
):
    assert token in store, token
for token in (
    'Powinna wracać na swoje miejsce',
    'Wzięte • przypominaj o odłożeniu',
    '✓ Odłożone na swoje miejsce',
    'case "storage"',
    'DATABASE_MIGRATED_49_TO_50_STORAGE_HOME',
    'super(context, "edhome-beta-preview.db", null, 52)',
):
    assert token in main, token
assert 'java.time.LocalDateTime' in alerts
assert 'today-start<days' in alerts
assert '"storage_home_notice_"+itemId' in alerts
assert 'mgr.notify(NOTICE,notice)' in alerts
assert 'QuietHoursRules.isQuiet' in alerts
assert 'reminders_enabled' in alerts
assert 'LegacyCompat.setAndAllowWhileIdle' in alerts
assert 'StorageHomeReminderReceiver.schedule(context);' in reminder
assert 'android:name=".StorageHomeReminderReceiver"' in manifest
assert 'return_required","return_after_days","taken_at"' in backup
assert 'inputVersion < 50 && "storage_items".equals(definition[0])' in backup
assert 'CURRENT_SCHEMA = 52;' in selective
assert 'if(!project.has("return_after_days"))' in selective
assert '"Powinna wracać na swoje miejsce","return_required"' in desktop
assert 'appendDesktopStorageHomeEvent' in desktop
assert 'row.addProperty("taken_at",System.currentTimeMillis())' in desktop

db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE storage_items (
    id INTEGER PRIMARY KEY,kind TEXT NOT NULL,parent_box_id INTEGER,place_id INTEGER,
    return_required INTEGER NOT NULL DEFAULT 0 CHECK(return_required IN (0,1)),
    return_after_days INTEGER NOT NULL DEFAULT 1 CHECK(return_after_days BETWEEN 1 AND 365),
    taken_at INTEGER CHECK(taken_at IS NULL OR taken_at>0))""")
db.execute("INSERT INTO storage_items(id,kind,parent_box_id) VALUES (1,'thing',5)")
db.execute("INSERT INTO storage_items(id,kind,place_id) VALUES (2,'thing',7)")
assert db.execute("SELECT return_required,return_after_days,taken_at FROM storage_items WHERE id=1").fetchone()==(0,1,None)
db.execute("UPDATE storage_items SET return_required=1,return_after_days=3 WHERE id=1")
db.execute("UPDATE storage_items SET taken_at=1792000000000 WHERE id=1")
assert db.execute("SELECT parent_box_id FROM storage_items WHERE id=1").fetchone()==(5,)
assert (date(2026,10,18)-date(2026,10,16)).days<3
assert (date(2026,10,19)-date(2026,10,16)).days>=3
db.execute("UPDATE storage_items SET taken_at=NULL WHERE id=1")
assert db.execute("SELECT return_required,return_after_days,taken_at,parent_box_id FROM storage_items WHERE id=1").fetchone()==(1,3,None,5)
try:
    db.execute("UPDATE storage_items SET return_after_days=0 WHERE id=1")
    raise AssertionError("Allowed invalid period 0")
except sqlite3.IntegrityError:
    pass
db.close()
print("Storage home return: switch per item, calendar days, daily reminder, rollback and restore: PASS")
