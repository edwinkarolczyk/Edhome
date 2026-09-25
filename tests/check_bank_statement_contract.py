#!/usr/bin/env python3
"""User-supplied statement data can only match one existing pending shared entry."""
from pathlib import Path
import runpy
import sqlite3
src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
pay=(src/"PaycheckStore.java").read_text(encoding="utf-8")
ui=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
parser=(src/"BankStatementCsv.java").read_text(encoding="utf-8")
mbank=(src/"BankStatementMbank.java").read_text(encoding="utf-8")
workbook=(src/"BankStatementWorkbook.java").read_text(encoding="utf-8")
velo=(src/"BankStatementVeloPdf.java").read_text(encoding="utf-8")
pdfbridge=(src/"BankPdfText.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

for word in ('statement_key TEXT','CREATE UNIQUE INDEX paycheck_statement_key_unique',
             'static String matchStatement(', "AND status='pending'",
             "AND kind=? AND amount_grosz=?", 'statement_key IS NULL',
             'statement_date'):
    assert word in pay,word
for word in ('IMPORT_STATEMENT_CSV','selectStatementCsv','importStatementCsv(',
             'showStatementEntries','matchStatementEntry','PaycheckStore.matchStatement(',
             'DATABASE_MIGRATED_31_TO_32_STATEMENT_MATCH'):
    assert word in ui,word
for word in ('bankImportDiag(', 'bankImportDiagLine()',
             '"bank_import_diag_state"', '"bank_import_diag_files"',
             '"bank_import_diag_rows"', '"bank_import_diag_at"',
             'diagStage="READ"', 'diagStage="PDF_TEXT"',
             'diagStage="XLSX_TEXT"', 'diagStage="MBANK_PARSE"',
             'diagStage="CSV_PARSE"', 'bankImportDiag("OK"',
             'bankImportDiag("BŁĄD • "+diagStage',
             'Diagnostyka importu:'):
    assert word in ui,word
assert 'inputVersion != 31 && inputVersion != DB_VERSION' in backup
assert '"statement_key", "statement_date"' in backup
assert 'bankStatementOperations.add(bankKey)' in backup
assert 'MAX_ROWS = 250' in parser and 'MAX_BYTES = 256 * 1024' in parser
fixture=Path("tests/fixtures/paycheck_statement_acceptance.csv").read_text(encoding="utf-8").splitlines()
assert fixture[0]=="Data;Kwota;Id transakcji;Opis"
assert len(fixture)==2
date,amount,reference,description=fixture[1].split(";")
assert date=="2026-09-24" and amount=="-12,50"
assert reference=="EDHOME-TEST-CSV-1250-001"
assert description=="Test uzgodnienia PayCheck"
for word in ('BankStatementMbank.recognizes(text)',
             'BankStatementMbank.parse(text)',
             'BankStatementWorkbook.textRows(bytes)',
             'BankPdfText.isPdf(bytes)',
             'BankPdfText.extract(this,bytes)',
             'BankStatementVeloPdf.parse(pdfText)',
             'Intent.EXTRA_ALLOW_MULTIPLE',
             'Zatwierdź parę'):
    assert word in ui, word
assert '"#Saldo końcowe"' in mbank
assert '"#Data księgowania;"' in mbank
assert 'balanceGrosz' in mbank and '"mbank\\n"' in mbank
assert "ZipInputStream" in workbook and 'disallow-doctype-decl' in workbook
assert 'velobank-pdf-confirmation' in velo and 'velobank-pdf-row' in velo
assert 'PDF skanowany jako obraz' in velo
assert 'Class.forName(' in pdfbridge and 'PDFTextStripper' in pdfbridge
assert 'MAX_PDF_BYTES=8*1024*1024' in pdfbridge
assert "betaImplementation 'com.tom-roush:pdfbox-android:2.0.27.0'" in gradle
assert 'implementation \'com.tom-roush:pdfbox-android' not in gradle
db=runpy.run_path("tests/check_db_contract.py")["fresh"]
for op,amount in [('11111111-1111-4111-8111-111111111111',65000),
                  ('22222222-2222-4222-8222-222222222222',65000)]:
    db.execute("""INSERT INTO paycheck_transactions
    (operation_id,scope,kind,category,amount_grosz,note,created_at,status,confirmation_source)
    VALUES (?,'shared','expense','vehicle',?,'',1,'pending','none')""",(op,amount))
key='a'*64
assert db.execute("""UPDATE paycheck_transactions SET status='confirmed',
    confirmation_source='manual',confirmed_at=123,statement_key=?,
    statement_date='2026-09-24'
    WHERE operation_id=? AND status='pending' AND kind='expense'
    AND amount_grosz=65000""",(key,'11111111-1111-4111-8111-111111111111')).rowcount==1
assert db.execute("""UPDATE paycheck_transactions SET status='confirmed'
    WHERE operation_id=? AND status='pending'""",
    ('11111111-1111-4111-8111-111111111111',)).rowcount==0
try:
    db.execute("""UPDATE paycheck_transactions SET status='confirmed',
    statement_key=? WHERE operation_id=?""",
    (key,'22222222-2222-4222-8222-222222222222'))
    raise AssertionError("bank evidence reused")
except sqlite3.IntegrityError: pass
assert db.execute("""UPDATE paycheck_transactions SET status='confirmed'
    WHERE operation_id=? AND status='pending' AND amount_grosz=100""",
    ('22222222-2222-4222-8222-222222222222',)).rowcount==0
assert db.execute("SELECT count(*) FROM paycheck_transactions").fetchone()==(2,)
print("Bank CSV reconciliation: existing-only, unique key, exact amount and once-only PASS")
