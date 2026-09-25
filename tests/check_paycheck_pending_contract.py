#!/usr/bin/env python3
"""Pending shared entries do not affect confirmed balance; legacy stays confirmed."""
from pathlib import Path
import runpy
import sqlite3
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
pay=Path("app/src/main/java/com/edwinkarolczyk/edhome/PaycheckStore.java").read_text(encoding="utf-8")
cost=Path("app/src/main/java/com/edwinkarolczyk/edhome/VehicleCostStore.java").read_text(encoding="utf-8")
backup=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
for token in ('values.put("status","pending");','status=\'confirmed\'',
              'status=\'pending\'','static String confirm(','state.put("status", "confirmed");',
              "AND status='pending'"):
    assert token in pay,token
assert 'transaction.put("status","pending");' in cost
for token in ('Do potwierdzenia • bez wpływu na saldo',
              'Potwierdź po sprawdzeniu banku / wyciągu',
              'PaycheckStore.confirm(db.getWritableDatabase()',
              'Saldo potwierdzone wspólne:',
              'DATABASE_MIGRATED_29_TO_30_PAYCHECK_PENDING'):
    assert token in main,token
for token in ('DB_VERSION = 34;','"created_at", "status"',
              'inputVersion < 30','values.put(key, "confirmed");',
              '|| !("pending".equals(status) || "confirmed".equals(status))'):
    assert token in backup,token
fresh=runpy.run_path("tests/check_db_contract.py")["fresh"]
fresh.execute("INSERT INTO paycheck_transactions (operation_id,scope,kind,category,"
              "amount_grosz,note,created_at,status) VALUES "
              "('11111111-1111-4111-8111-111111111111','shared','expense','vehicle',"
              "65000,'OC',1,'pending')")
def balance():
    return fresh.execute("SELECT COALESCE(SUM(CASE WHEN kind='income' THEN "
        "amount_grosz ELSE -amount_grosz END),0) "
        "FROM paycheck_transactions WHERE scope='shared' AND status='confirmed'").fetchone()[0]
assert balance()==0,"pending vehicle cost unexpectedly deducted"
fresh.execute("UPDATE paycheck_transactions SET status='confirmed' "
              "WHERE operation_id='11111111-1111-4111-8111-111111111111' "
              "AND status='pending'")
assert balance()==-65000
assert fresh.execute("UPDATE paycheck_transactions SET status='confirmed' "
              "WHERE operation_id='11111111-1111-4111-8111-111111111111' "
              "AND status='pending'").rowcount==0,"duplicate confirmation"
fresh.execute("INSERT INTO paycheck_transactions (operation_id,scope,kind,category,"
              "amount_grosz,note,created_at) VALUES "
              "('22222222-2222-4222-8222-222222222222','shared','income','salary',"
              "100000,'historical',2)")
assert fresh.execute("SELECT status FROM paycheck_transactions WHERE note='historical'"
              ).fetchone()==('confirmed',),"old ledger value must remain confirmed"
assert balance()==35000
try:
    fresh.execute("UPDATE paycheck_transactions SET status='approved'")
    raise AssertionError("bad transaction status accepted")
except sqlite3.IntegrityError: pass
print("PayCheck pending, legacy-confirmed migration, one-time manual confirmation and balance: PASS")
