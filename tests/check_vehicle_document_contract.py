#!/usr/bin/env python3
"""Vehicle document register is local, idempotent and included in backup."""
from pathlib import Path
import runpy
import sqlite3
src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
store=(src/"VehicleDocumentStore.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
for word in ("CREATE TABLE vehicle_documents","operation_id TEXT NOT NULL UNIQUE",
             "vehicle_id INTEGER NOT NULL","valid_until TEXT NOT NULL DEFAULT",
             "DUPLICATE_IGNORED"):
    assert word in store, word
for word in ("VehicleDocumentStore.create(database)",
             "DATABASE_MIGRATED_32_TO_33_VEHICLE_DOCUMENTS",
             "+ Dodaj dokument","editVehicleDocument("):
    assert word in main, word
assert '"vehicle_documents", "id", "operation_id", "vehicle_id"' in backup
assert 'inputVersion < 33 && "vehicle_documents".equals(definition[0])' in backup
assert "PaycheckStore" not in store
for word in ('documentOperations.add(document.getAsString("operation_id"))',
             '"Nieprawidłowy dokument pojazdu w kopii."',
             'vehicleIds.contains(document.getAsLong("vehicle_id"))'):
    assert word in backup, word

db=runpy.run_path("tests/check_db_contract.py")["fresh"]
db.execute("INSERT INTO vehicles(id,name,registration,mileage) VALUES(1,'Test','','0')")
doc=("11111111-1111-4111-8111-111111111111",1,"insurance",
     "Polisa OC","ABC-123","2026-09-01","2027-08-31","Próbka",123)
sql=("INSERT INTO vehicle_documents (operation_id,vehicle_id,kind,title,"
     "document_number,issued_on,valid_until,note,created_at) "
     "VALUES (?,?,?,?,?,?,?,?,?)")
db.execute(sql,doc)
assert db.execute("SELECT COUNT(*) FROM vehicle_documents").fetchone()==(1,)
try:
    db.execute(sql,doc)
    raise AssertionError("Duplicate vehicle document accepted")
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT COUNT(*) FROM vehicle_documents").fetchone()==(1,)
assert db.execute("SELECT COUNT(*) FROM paycheck_transactions").fetchone()==(0,)
print("Vehicle documents: SQLite v34, one-time ID, ledger isolation and backup contract PASS")
