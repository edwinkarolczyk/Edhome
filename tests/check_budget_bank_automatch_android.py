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
print("PASS Budget Bank auto: plan pending 1x, wyciag confirm 1x, konflikty bez podwojnego salda")
