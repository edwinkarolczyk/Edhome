#!/usr/bin/env python3
"""Vehicle opt-in, migration, backup, dedup, deep-link and stale alarms."""
from pathlib import Path
import runpy
src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"VehicleStore.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
receiver=(src/"VehicleReminderReceiver.java").read_text(encoding="utf-8")
tasks=(src/"ReminderReceiver.java").read_text(encoding="utf-8")
for token in (
    "VehicleReminderReceiver.schedule(context);",
    "ACTION_BOOT_COMPLETED", "ACTION_MY_PACKAGE_REPLACED",
    "ACTION_TIMEZONE_CHANGED", "ACTION_TIME_CHANGED",
):
    assert token in tasks,token
for token in (
    "setAndAllowWhileIdle", "FLAG_NO_CREATE", "manager.cancel(existing)",
    "VehicleReminderRules.allowed(lead)", "occurrence.equals(",
    "expected.equals(fingerprint(deadline, lead))",
    "LocalDate.parse(deadline).isBefore(LocalDate.now())",
    "QuietHoursRules.isQuiet", "QuietHoursRules.nextAllowed",
    "POST_NOTIFICATIONS", "getDatabasePath(DB)", "open_vehicles",
    "setContentIntent(content)", "VEHICLE_REMINDER_DELIVERED",
    "vehicle_reminder_fired_", "Uri.parse(",
):
    assert token in receiver,token
for token in (
    "private void editVehicleReminders(", "saveReminderLeads(",
    "VehicleReminderReceiver.schedule(this);", "open_vehicles",
    "DATABASE_MIGRATED_27_TO_28_VEHICLE_REMINDERS",
):
    assert token in main,token
for token in (
    "oc_reminder_lead", "inspection_reminder_lead",
    "static void saveReminderLeads(", "db.beginTransaction();",
):
    assert token in store,token
for token in (
    'private static final int DB_VERSION = 35;',
    '"oc_reminder_lead", "inspection_reminder_lead"',
    'inputVersion < 28',
    'VehicleReminderRules.allowed(ocLead)',
):
    assert token in backup,token
assert "PaycheckStore" not in receiver and "PaycheckStore" not in store
db=runpy.run_path("tests/check_db_contract.py")["fresh"]
db.execute("INSERT INTO vehicles(id,name,oc_until,inspection_until) "
           "VALUES(88,'Audi','2027-09-08','2027-04-08')")
assert db.execute("SELECT oc_reminder_lead,inspection_reminder_lead FROM vehicles "
                  "WHERE id=88").fetchone()==(None,None)
db.execute("UPDATE vehicles SET oc_reminder_lead=30,inspection_reminder_lead=7 "
           "WHERE id=88")
assert db.execute("SELECT oc_reminder_lead,inspection_reminder_lead FROM vehicles "
                  "WHERE id=88").fetchone()==(30,7)
import sqlite3
try:
    db.execute("UPDATE vehicles SET oc_reminder_lead=2 WHERE id=88")
    raise AssertionError("Unsupported reminder accepted")
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT oc_reminder_lead FROM vehicles WHERE id=88"
                  ).fetchone()==(30,)
print("Vehicle reminders migration, independent opt-in, stale alarm and UI contract: PASS")
