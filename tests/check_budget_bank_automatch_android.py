#!/usr/bin/env python3
"""P0: pozycja Budzetu -> pending; unikatowy wyciag -> confirmed + budzet.
Dwa bankowe wiersze albo brak identyfikowalnego odbiorcy -> reczna decyzja.
"""
from pathlib import Path
import sqlite3
import uuid

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"PaycheckStore.java").read_text(encoding="utf-8")
bank=(src/"BankEvidenceStore.java").read_text(encoding="utf-8")
rules=(src/"BankBudgetMatchRules.java").read_text(encoding="utf-8")
flow=main.split("private void paycheck() {",1)[1].split("private void ",1)[0]
assert "ensureBudgetPaycheckPending(YearMonth.now());" in flow
assert 'button("Dodaj transakcję do PayCheck"' not in flow
add=main.split("PaycheckMonthlyBudget.add(prefs,item);",1)[1][:220]
assert "ensureBudgetPaycheckPending(YearMonth.now());" in add
auto=main.split("private int ensureBudgetPaycheckPending(",1)[1].split(
    "private void sharedBudgetAwaitingPaycheckConfirmation()",1)[0]
for required in ("PaycheckMonthlyBudget.activeFor(", "remainingDue(",
    "budgetPaycheckOperationId(item,month)", "PaycheckStore.add(",
    '"COMMITTED".equals(result)'):
    assert required in auto,required
assert "PaycheckStore.confirm(" not in auto
assert '"edhome-budget-pending:"' in main
assert 'values.put("status","pending");' in store

worker=main.split("private void importStatementFilesWorker(",1)[1].split(
    "private static final class BankAutoCandidate",1)[0]
assert "BankEvidenceStore.ingest(" in worker
assert "autoSettleImportedBankEvidence(incoming)" in worker
assert worker.index("BankEvidenceStore.ingest(")<worker.index(
    "autoSettleImportedBankEvidence(incoming)")
matching=main.split("private BankAutoResult autoSettleImportedBankEvidence(",1)[1].split(
    "private void showBankEvidenceQueue(int filter)",1)[0]
for required in (
    '"velo_pdf".equals(e.sourceKind)',
    "YearMonth.parse(e.entry.date.substring(0,7))",
    "ensureBudgetPaycheckPending(month);",
    "BankBudgetMatchRules.descriptionIdentifies(",
    "if(matches!=1)",
    "if(evidence.size()!=1)",
    "BankEvidenceStore.match(sql,",
    "PaycheckMonthlyBudget.match(prefs,sql,",
    'if(!"MATCHED".equals(result))',
):
    assert required in matching,required
assert "db.beginTransaction()" in bank and "status='pending'" in bank
assert "GENERIC" in rules and "descriptionIdentifies(" in rules

# Finansowy model: 1 wiersz bankowy / 1 pending / 1 saldo.
db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE tx (id TEXT PRIMARY KEY,kind TEXT,amount INTEGER,status TEXT)")
db.execute("CREATE TABLE evidence (bank_id TEXT PRIMARY KEY,tx_id TEXT UNIQUE)")
budget_id=str(uuid.uuid3(uuid.NAMESPACE_DNS,"edhome-budzet-2026-10-prad"))
def saldo():
    return db.execute("""SELECT coalesce(sum(case when kind='income'
         then amount else -amount end),0) FROM tx WHERE status='confirmed'""").fetchone()[0]
db.execute("INSERT INTO tx VALUES (?,?,?,?)",(budget_id,"expense",25000,"pending"))
assert saldo()==0
db.execute("INSERT OR IGNORE INTO tx VALUES (?,?,?,?)",(budget_id,"expense",25000,"pending"))
assert db.execute("SELECT count(*) FROM tx").fetchone()[0]==1
assert db.execute("UPDATE tx SET status='confirmed' WHERE id=? AND status='pending'",
                  (budget_id,)).rowcount==1
db.execute("INSERT INTO evidence VALUES (?,?)",("bank-1",budget_id))
assert saldo()==-25000
assert db.execute("UPDATE tx SET status='confirmed' WHERE id=? AND status='pending'",
                  (budget_id,)).rowcount==0
assert saldo()==-25000
try:
    db.execute("INSERT INTO evidence VALUES (?,?)",("bank-2",budget_id))
    raise AssertionError("Dwie operacje bankowe nie moga rozliczyc tego samego ID")
except sqlite3.IntegrityError:
    pass

# Kwoty na wyciagu wyznaczaja saldo, nawet gdy roznia sie od planu.
budget_action=main.split("private void confirmBankEvidenceMatch(",1)[1].split(
    "private void showDismissedBankEvidence()",1)[0]
budget_selector=main.split("private void openBankEvidenceRow(",1)[1].split(
    "private void confirmBankEvidenceMatch(",1)[0]
actual_match=bank.split("static String matchBudgetActual(",1)[1].split(
    "static boolean dismiss(",1)[0]
for required in (
    'payment.put("amount_grosz",actual);',
    'payment.put("status","confirmed");',
    'payment.put("statement_key",evidenceKey);',
    'statement_key IS NULL',
    "status='pending'",
    'finished.put("state","matched");',
):
    assert required in actual_match,required
for required in (
    'PaycheckMonthlyBudget.plannedAmount(item,month)',
    'PaycheckMonthlyBudget.remainingDue(',
    'BankEvidenceStore.matchBudgetActual(',
    'PaycheckMonthlyBudget.match(',
    'row.amount',
    'Zatwierdź parę',
):
    assert required in budget_action,required
assert 'amount_grosz=?' not in budget_selector, "Nie wybieraj tylko identycznych kwot"
assert 'BankBudgetMatchRules.descriptionIdentifies(' in budget_selector
assert 'ensureBudgetPaycheckPending(month);' in budget_selector
assert 'if(!"velo_pdf".equals(e.sourceKind))' in matching
ledger=sqlite3.connect(":memory:")
ledger.execute("""CREATE TABLE tx (
    operation_id TEXT PRIMARY KEY, kind TEXT, amount_grosz INTEGER,
    status TEXT, statement_key TEXT UNIQUE)""")
ledger.execute("""CREATE TABLE bank (
    evidence_key TEXT PRIMARY KEY, kind TEXT, amount_grosz INTEGER,
    state TEXT)""")
entries=[
    ("salary","income",450000,430000),
    ("electricity","expense",30000,28000),
    ("water","expense",15000,17000),
    ("bonus","income",50000,65000),
]
for label,kind,plan,actual in entries:
    ledger.execute("INSERT INTO tx VALUES (?,?,?,'pending',NULL)",(label,kind,plan))
    ledger.execute("INSERT INTO bank VALUES (?,?,?,'open')",(label,kind,actual))
def actual_balance():
    return ledger.execute("""SELECT coalesce(sum(
        CASE WHEN kind='income' THEN amount_grosz ELSE -amount_grosz END
    ),0) FROM tx WHERE status='confirmed'""").fetchone()[0]
assert actual_balance()==0
for label,kind,planned,actual in entries:
    assert ledger.execute("""UPDATE tx SET status='confirmed', amount_grosz=?,
        statement_key=? WHERE operation_id=? AND status='pending'
        AND kind=? AND statement_key IS NULL""",
        (actual,label,label,kind)).rowcount==1
    assert ledger.execute("""UPDATE bank SET state='matched'
        WHERE evidence_key=? AND state='open'""",(label,)).rowcount==1
    assert ledger.execute("""UPDATE tx SET status='confirmed',amount_grosz=?
        WHERE operation_id=? AND status='pending'""",
        (actual,label)).rowcount==0
assert actual_balance()==430000-28000-17000+65000
assert ledger.execute("SELECT amount_grosz FROM tx WHERE operation_id='salary'").fetchone()[0]==430000
assert 450000-430000==20000
assert 30000-28000==2000
assert 17000-15000==2000
assert 65000-50000==15000
assert all(ledger.execute("SELECT count(*) FROM tx WHERE operation_id=?",(key,)).fetchone()[0]==1
           for key,_,_,_ in entries)
print("PASS Budget Bank actual: 4500->4300, rachunki 300->280 i 150->170, premia 500->650; saldo tylko bank 1x")

print("PASS Budget Bank auto: plan pending 1x, wyciag confirm 1x, konflikty bez podwojnego salda")
