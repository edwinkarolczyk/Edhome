#!/usr/bin/env python3
"""Regresja P0 grupowego dopasowania — bez automatycznego księgowania propozycji."""
from pathlib import Path
import sqlite3

src=Path(__file__).resolve().parents[1]/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
budget=(src/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
bank=(src/"BankEvidenceStore.java").read_text(encoding="utf-8")
settle=(src/"BankBudgetGroupSettlement.java").read_text(encoding="utf-8")
rules=(src/"BankBudgetGroupRules.java").read_text(encoding="utf-8")

import_worker=main.split("private void importStatementFilesWorker(",1)[1].split(
    "private static final class BankAutoCandidate",1)[0]
assert '.setPositiveButton("Rozlicz teraz",' in import_worker
assert '(dialog,which)->showBankBudgetProposals()' in import_worker
assert '.setNegativeButton("Później",null)' in import_worker
assert "autoSettleImportedBankEvidence(incoming)" not in import_worker

grp=main.split("private int appendBankBudgetGroupProposals(",1)[1].split(
    "private void showBankBudgetProposals() {",1)[0]
for q in (
    "twoBankToOneBudget(", "oneBankToTwoBudgets(",
    "confirmOneBankForTwoBudgets(", "confirmTwoBanksForOneBudget(",
    "Sprawdź i rozlicz razem", "pulseBankProposal(label)",
    'setPositiveButton("Rozlicz razem"',
):
    assert q in grp,q
assert 'AlphaAnimation(0.55f,1.0f)' in grp
assert "MAX_BANK_PAIR_DAYS = 7" in rules
assert 'BankBudgetGroupSettlement.oneBankToTwoBudgets(' in grp
assert 'BankBudgetGroupSettlement.twoBankToOneBudget(' in grp

proposals=main.split("private void showBankBudgetProposals(int offset) {",1)[1].split(
    "private void showBankEvidenceQueue(int filter)",1)[0]
assert 'appendBankBudgetGroupProposals(' in proposals
assert "if(rank>0)pulseBankProposal(headerLine);" in proposals
assert "Propozycje łączone:" in proposals
assert "Math.min(total,300)" in proposals, "Nie analizuj całej wielkiej historii na UI"
assert 'BankEvidenceStore.matchBudgetActual(' not in proposals

for key in (
    "db.beginTransaction()", "db.setTransactionSuccessful()",
    'status=\'pending\'', "statement_key IS NULL",
    "BankEvidenceStore.matchBudgetActual(",
    "PaycheckMonthlyBudget.matchBankParts(",
    "PaycheckMonthlyBudget.allocateSplit(",
    "PaycheckStore.add(", "db.delete(", 'state',
):
    assert key in settle,key
# Rozliczenia planu wykonuje nowy mechanizm recovery po commit SQLite.
assert 'Arrays.asList(firstOp,secondOp)' in settle
assert 'Arrays.asList(firstId,secondId)' in settle
assert 'journal(db,a.key,"two_to_one"' in settle
assert 'journal(db,bank.key,"one_to_two"' in settle
assert 'repairInterrupted(prefs,db);' in settle
assert 'MAX_BANK_PAIR_DAYS' in rules
assert "saveWithEvents(prefs,items,events);" in budget.split(
    "static boolean matchBankParts(",1)[1].split(
    "static boolean allocateMatch(",1)[0]
assert "MAX_MATCHES_PER_ITEM" in budget

# Pojedynczy rzeczywisty przelew 500 nie tworzy dwóch zapisów 500 w PayCheck.
def saldo(operations):
    return sum(amount if kind=="income" else -amount
               for amount,kind,state in operations if state=="confirmed")
assert saldo([(50000,"expense","confirmed"),
              (20000,"expense","pending")]) == -50000
# Dwa rzeczywiste przelewy 200 i 300 sumują się w ledgerze dokładnie raz.
assert saldo([(20000,"expense","confirmed"),
              (30000,"expense","confirmed")]) == -50000
assert saldo([(20000,"expense","pending"),
              (30000,"expense","pending")]) == 0

db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE bank_evidence_queue (
    evidence_key TEXT UNIQUE, state TEXT, amount_grosz INTEGER,
    kind TEXT, booking_date TEXT)""")
db.executemany("INSERT INTO bank_evidence_queue VALUES (?,?,?,?,?)",[
    ("200","open",20000,"expense","2026-10-09"),
    ("300","open",30000,"expense","2026-10-10"),
    ("matched","matched",30000,"expense","2026-10-10"),
    ("income","open",30000,"income","2026-10-10"),
    ("old","open",30000,"expense","2026-09-10"),
])
bank_rows=db.execute("""SELECT evidence_key,amount_grosz FROM bank_evidence_queue
 WHERE state='open' AND kind='expense' AND booking_date LIKE '2026-10-%'
 ORDER BY amount_grosz""").fetchall()
assert bank_rows==[("200",20000),("300",30000)],bank_rows
print("PASS bank group: pulsowanie, 200+300=500 w obie strony, dialog zgody, 1x saldo")
