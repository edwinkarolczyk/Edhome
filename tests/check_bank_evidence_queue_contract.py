#!/usr/bin/env python3
"""Persistent multi-bank evidence queue: schema, state machine, dedup and UI contract."""
from pathlib import Path
import json
import re
import sqlite3

src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
store=(src/"BankEvidenceStore.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")

def statements(source):
    out=[]
    body=source.split("static void create(SQLiteDatabase db)",1)[1].split(
        "static IngestResult ingest(",1)[0]
    for expr in re.findall(r'db\.execSQL\((.*?)\);',body,re.S):
        parts=re.findall(r'"(?:\\.|[^"\\])*"',expr)
        if parts:
            out.append("".join(json.loads(p) for p in parts))
    return out

sql=statements(store)
assert len(sql)==2, sql
db=sqlite3.connect(":memory:")
for statement in sql:
    db.execute(statement)

key="a"*64
db.execute("""INSERT INTO bank_evidence_queue
(evidence_key,source_kind,source_label,kind,amount_grosz,booking_date,
 description,imported_at,state)
VALUES (?,'csv','EDHOME TEST','expense',1250,'2026-09-25',
 'Test',1,'open')""",(key,))
try:
    db.execute("""INSERT INTO bank_evidence_queue
    (evidence_key,source_kind,source_label,kind,amount_grosz,booking_date,
     description,imported_at,state)
    VALUES (?,'mbank','mBank','expense',1250,'2026-09-25','D',2,'open')""",(key,))
    raise AssertionError("duplicate evidence key accepted")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("UPDATE bank_evidence_queue SET state='matched' WHERE evidence_key=?",(key,))
    raise AssertionError("matched row without operation/timestamp accepted")
except sqlite3.IntegrityError:
    pass
db.execute("""UPDATE bank_evidence_queue SET state='matched',
 matched_operation_id='11111111-1111-4111-8111-111111111111',matched_at=3
 WHERE evidence_key=?""",(key,))
assert db.execute("SELECT state,matched_at FROM bank_evidence_queue").fetchone()==(
    "matched",3)
try:
    db.execute("UPDATE bank_evidence_queue SET source_kind='unknown'")
    raise AssertionError("unknown source kind accepted")
except sqlite3.IntegrityError:
    pass

for token in (
    'MAX_ROWS=5000',
    'UNION SELECT 1 FROM paycheck_transactions WHERE statement_key=?',
    'static IngestResult ingest(',
    'static List<Row> list(',
    'static int pendingMatches(',
    'static String match(',
    'db.beginTransaction();',
    "operation_id=? AND scope='shared' AND status='pending'",
    'evidence_key=? AND state=\'open\'',
    'static boolean dismiss(',
    'static boolean reopen(',
):
    assert token in store,token

for token in (
    'BankEvidenceStore.ingest(',
    'showBankEvidenceQueue(0)',
    'BankEvidenceStore.pendingMatches(',
    'BankEvidenceStore.match(',
    'BankEvidenceStore.dismiss(',
    'BankEvidenceStore.reopen(',
    'Banki i potwierdzenia • kolejka (',
    'Kolejka bankowa:',
    'diagStage="QUEUE_SAVE"',
):
    assert token in main,token

for token in (
    'super(context, "edhome-beta-preview.db", null, 35)',
    'BankEvidenceStore.create(database);',
    'DATABASE_MIGRATED_33_TO_34_BANK_EVIDENCE_QUEUE',
):
    assert token in main,token

for token in (
    'private static final int DB_VERSION = 35;',
    '{"bank_evidence_queue", "id", "evidence_key"',
    'inputVersion < 34 && "bank_evidence_queue".equals(definition[0])',
    '"bank_evidence_queue".equals(definition[0])',
):
    assert token in backup,token

print("Bank evidence queue: persistent v35 schema, state constraints, dedup, UI and backup PASS")
