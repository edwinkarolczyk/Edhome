#!/usr/bin/env python3
"""Kontrakt P0: dluga historia bankowa, filtr po SQLite przed stronicowaniem."""
from pathlib import Path
import sqlite3
import re

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
read=lambda name:(src/name).read_text(encoding="utf-8")
csv=read("BankStatementCsv.java")
mbank=read("BankStatementMbank.java")
xls=read("BankStatementWorkbook.java")
pdf=read("BankStatementVeloPdf.java")
store=read("BankEvidenceStore.java")
ui=read("MainActivity.java")
for token in (
    "MAX_BYTES = 8 * 1024 * 1024", "MAX_ROWS = 25000",
    "if(entries.size()>=MAX_ROWS)"
): assert token in csv, token
assert "Maksymalnie 250 transakcji" not in csv+mbank+pdf
assert "if(rows.getLength()>12000)" in xls
assert "statement.size()>1" in pdf and        pdf.index("statement.size()>1") < pdf.index("parseLabeledConfirmation(normalized)")
assert "MAX_IMPORT_BATCH=50000" in store
assert "MAX_ROWS=200000" in store
assert "LIMIT ? OFFSET ?" in store
assert 'filterPredicate(filter)' in store
assert 'BankEvidenceStore.listPage(read,"open",filter,pageSize,first)' in ui
assert 'BankEvidenceStore.count(read,"open",filter)' in ui
assert "BankEvidenceStore.listPage(read,\"dismissed\",0,pageSize,first)" in ui
assert 'new Thread(()->importStatementFilesWorker' in ui
assert 'db.getWritableDatabase(),incoming' in ui
assert 'if(entries.size()>=BankEvidenceStore.MAX_IMPORT_BATCH)' in ui
assert 'if(output.size()+n>BankStatementCsv.MAX_BYTES)' in ui
assert 'bankImportDiag("OK",files.size(),queue.inserted)' in ui
assert "BankEvidenceStore.ingest(" in ui
assert "PaycheckStore.confirm(" not in ui.split("private void importStatementFilesWorker(",1)[1].split("private void showBankEvidenceQueue(",1)[0]
assert "PaycheckStore.add(" not in ui.split("private void importStatementFilesWorker(",1)[1].split("private void showBankEvidenceQueue(",1)[0]
# SQL jest odpowiednikiem zapytan listPage/count i liczy konkretne strony
# historii takze gdy pierwsze 500 ma zupelnie inne daty/rodzaje.
db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE bank_evidence_queue (
    id INTEGER PRIMARY KEY, evidence_key TEXT UNIQUE NOT NULL,
    state TEXT NOT NULL,kind TEXT NOT NULL,booking_date TEXT NOT NULL,
    amount_grosz INTEGER NOT NULL)""")
db.execute("""CREATE TABLE paycheck_transactions (
    operation_id TEXT, scope TEXT, status TEXT,kind TEXT,
    amount_grosz INTEGER)""")
db.executemany("""INSERT INTO bank_evidence_queue
    (id,evidence_key,state,kind,booking_date,amount_grosz)
    VALUES (?,?,?,?,?,?)""",
    ((i,f"bank-key-{i}","open","income" if i%3==0 else "expense",
      f"2026-09-{(i%28)+1:02}",100+i) for i in range(1,3002)))
db.execute("""INSERT INTO paycheck_transactions VALUES
    ('a','shared','pending','income',400)""")
assert db.execute("SELECT COUNT(*) FROM bank_evidence_queue WHERE state='open'").fetchone()[0]==3001
read_ids=[]
for offset in range(0,3001,100):
    page=db.execute("""SELECT id FROM bank_evidence_queue
        WHERE state='open' ORDER BY booking_date DESC,id DESC LIMIT ? OFFSET ?""",
        (100,offset)).fetchall()
    read_ids.extend([x[0] for x in page])
assert len(read_ids)==3001 and len(set(read_ids))==3001
assert max(read_ids)==3001 and min(read_ids)==1
income=db.execute("SELECT count(*) FROM bank_evidence_queue WHERE kind='income'").fetchone()[0]
seen=[]
for offset in range(0,income,100):
    seen.extend(x[0] for x in db.execute("""SELECT id FROM bank_evidence_queue
        WHERE state='open' AND kind='income'
        ORDER BY booking_date DESC,id DESC LIMIT ? OFFSET ?""",(100,offset)))
assert len(seen)==income and all(i%3==0 for i in seen)
assert 300 in seen and 3000 in seen
print("PASS bank history: no 250/500 cap; 3001 items and filtered pages all accessible")
