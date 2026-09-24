#!/usr/bin/env python3
"""Legacy bank evidence is unknown; manual transitions are idempotent."""
from pathlib import Path
import runpy
src = Path("app/src/main/java/com/edwinkarolczyk/edhome")
pay = (src / "PaycheckStore.java").read_text(encoding="utf-8")
cost = (src / "VehicleCostStore.java").read_text(encoding="utf-8")
ui = (src / "MainActivity.java").read_text(encoding="utf-8")
backup = (src / "DataBackup.java").read_text(encoding="utf-8")
assert 'values.put("confirmation_source","none");' in pay
assert 'transaction.put("confirmation_source","none");' in cost
assert 'state.put("confirmation_source", "manual");' in pay
assert 'state.put("confirmed_at", System.currentTimeMillis());' in pay
assert "AND status='pending'" in pay and "status='confirmed'" in pay
assert "DATABASE_MIGRATED_30_TO_31_CONFIRMATION_PROVENANCE" in ui
assert "WHERE status='pending'" in ui
assert "WPIS HISTORYCZNY" in ui and "POTWIERDZONE RĘCZNIE" in ui
assert "inputVersion != 29 && inputVersion != 30" in backup
assert '"confirmation_source", "confirmed_at"' in backup

db = runpy.run_path("tests/check_db_contract.py")["fresh"]
db.execute("""INSERT INTO paycheck_transactions
(operation_id,scope,kind,category,amount_grosz,note,created_at,status,confirmation_source)
VALUES ('11111111-1111-4111-8111-111111111111','shared','expense','vehicle',1000,'',1,'pending','none')""")
db.execute("""INSERT INTO paycheck_transactions
(operation_id,scope,kind,category,amount_grosz,note,created_at)
VALUES ('22222222-2222-4222-8222-222222222222','shared','income','salary',2000,'',2)""")
assert db.execute("SELECT confirmation_source FROM paycheck_transactions WHERE kind='income'").fetchone() == ('legacy',)
assert db.execute("SELECT confirmation_source,confirmed_at FROM paycheck_transactions WHERE kind='expense'").fetchone() == ('none', None)
assert db.execute("""UPDATE paycheck_transactions SET status='confirmed',
confirmation_source='manual',confirmed_at=123
WHERE kind='expense' AND status='pending'""").rowcount == 1
assert db.execute("""UPDATE paycheck_transactions SET status='confirmed',
confirmation_source='manual',confirmed_at=456
WHERE kind='expense' AND status='pending'""").rowcount == 0
assert db.execute("SELECT confirmed_at FROM paycheck_transactions WHERE kind='expense'").fetchone() == (123,)
print("PayCheck provenance, historic entries and one-time confirmation: PASS")
