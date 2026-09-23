#!/usr/bin/env python3
"""Private money must not exist in unauthenticated Beta; shared ledger is explicit."""
from pathlib import Path
import sqlite3
import json
import re

main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
store=Path("app/src/main/java/com/edwinkarolczyk/edhome/PaycheckStore.java").read_text()
rules=Path("app/src/main/java/com/edwinkarolczyk/edhome/MoneyRules.java").read_text()
backup=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
gradle=Path("app/build.gradle").read_text()
for token in (
    'case "paycheck": paycheck(); break;',
    'PayCheck • wspólny budżet',
    'PaycheckStore.sharedBalance(db.getReadableDatabase())',
    'PaycheckStore.add(',
    'new AlertDialog.Builder(this)',
    'Potwierdź transakcję wspólną',
    'PAYCHECK_SHARED_COMMITTED',
    "WHERE scope='shared'",
    'DATABASE_MIGRATED_19_TO_20_PAYCHECK_SHARED',
    'super(context, "edhome-beta-preview.db", null, 25)',
):
    assert token in main, token
for token in (
    "CHECK(scope='shared')",
    "kind IN ('income','expense')",
    "amount_grosz BETWEEN 1 AND 99999999999",
    'values.put("scope","shared");',
    'SELECT 1 FROM paycheck_transactions WHERE operation_id=?',
    'if(previous.moveToFirst())return "DUPLICATE";',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
):
    assert token in store,token
for token in (
    'DB_VERSION = 25;',
    '{"paycheck_transactions", "id", "operation_id", "scope", "kind",',
    'inputVersion < 20 && "paycheck_transactions".equals(definition[0])',
    '!"shared".equals(scope)',
    'MoneyRules.category(category)',
    'Zduplikowana transakcja PayCheck.',
    '"amount_grosz".equals(column)',
):
    assert token in backup,token
assert 'BigDecimal' in rules and 'RoundingMode.UNNECESSARY' in rules
assert "versionCode 62" in gradle and "versionNameSuffix '-beta.2'" in gradle
assert "versionName '0.6.0'" in gradle
# Receipt and barcode commit must never automatically post to PayCheck.
receipt=Path("app/src/main/java/com/edwinkarolczyk/edhome/ShoppingReceiptStore.java").read_text()
barcode=Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text()
assert 'PaycheckStore' not in receipt and 'PaycheckStore' not in barcode

expr=store.split('static void create(SQLiteDatabase db)',1)[1].split('static String add(',1)[0]
sql=''.join(json.loads(part) for part in re.findall(r'"(?:\\.|[^"\\])*"',expr))
db=sqlite3.connect(":memory:")
db.execute(sql)
db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,amount_grosz,note,created_at) VALUES(?,?,?,?,?,?,?)",("id1","shared","income","salary",20000,"Wkład",1))
db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,amount_grosz,note,created_at) VALUES(?,?,?,?,?,?,?)",("id2","shared","expense","shopping",1250,"Zakupy",2))
balance=db.execute("SELECT COALESCE(SUM(CASE WHEN kind='income' THEN amount_grosz ELSE -amount_grosz END),0) FROM paycheck_transactions WHERE scope='shared'").fetchone()[0]
assert balance==18750, balance
try:
    db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,amount_grosz,note,created_at) VALUES(?,?,?,?,?,?,?)",("id1","shared","expense","shopping",100,"Double",3))
    raise AssertionError("duplicate operation accepted")
except sqlite3.IntegrityError:pass
try:
    db.execute("INSERT INTO paycheck_transactions(operation_id,scope,kind,category,amount_grosz,note,created_at) VALUES(?,?,?,?,?,?,?)",("id3","private","expense","other",100,"Private",3))
    raise AssertionError("private entry accepted")
except sqlite3.IntegrityError:pass
print("PayCheck shared-only ledger, money precision, idempotence, backup v20: PASS")
