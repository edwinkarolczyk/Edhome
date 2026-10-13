#!/usr/bin/env python3
"""Regresja tworzenia nowej pozycji Budżetu z historii bankowej (bez podwójnego salda)."""
from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
rules=(root/"app/src/main/java/com/edwinkarolczyk/edhome/BankToBudgetRules.java").read_text(encoding="utf-8")
bank_view=main.split("private void showBankEvidenceList(",1)[1].split(
    "private void showCreateBudgetFromBank(",1)[0]
selector=main.split("private void openBankEvidenceRow(",1)[1].split(
    "private void confirmBankEvidenceMatch(",1)[0]
proposals=main.split("private void showBankBudgetProposals(int offset) {",1)[1].split(
    "private void showBankEvidenceQueue(int filter)",1)[0]
create=main.split("private void showCreateBudgetFromBank(",1)[1].split(
    "private void openBankEvidenceRow(",1)[0]
for part in (bank_view,selector,proposals):
    assert 'showCreateBudgetFromBank(row)' in part,"Brak przejścia bank -> budżet"
for required in (
    'PaycheckMonthlyBudget.newItem(', 'PaycheckMonthlyBudget.add(prefs,item)',
    'ensureBudgetPaycheckPending(bankMonth)',
    'confirmBankEvidenceMatch(row,item,bankMonth)', 
    'BankToBudgetRules.matchingMonths(', 
    'BankToBudgetRules.suggestedName(row.description)',
    'new String[]{row.kind,row.evidenceKey}', 
    'if(!current.moveToFirst()', 
    'existing.name.equalsIgnoreCase(itemName)', 
    'MoneyRules.format(row.amount).replace(" zł","")', 'MoneyRules.parse(',
    'repetition.getSelectedItemPosition()==1?1:0',
    'setPositiveButton("Sprawdź przelew"',
    'setNegativeButton("Później"',
    '"open".equals(current.getString(0))',
    '"income".equals(row.kind)',
):
    assert required in create,required
assert create.index('PaycheckMonthlyBudget.add(prefs,item)') < create.index(
    'confirmBankEvidenceMatch(row,item,bankMonth)')
assert 'PaycheckStore.confirm(' not in create
assert 'BankEvidenceStore.matchBudgetActual(' not in create
assert 'PaycheckStore.add(' not in create
assert "static int matchingMonths(" in rules

# Nowa definicja planu nie może sama zmieniać potwierdzonej księgi.
db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE tx (id TEXT PRIMARY KEY, kind TEXT, amount INTEGER,
    status TEXT, bank_key TEXT UNIQUE)""")
db.execute("INSERT INTO tx VALUES('old','expense',250000,'confirmed','old-key')")
def balance():
    return db.execute("""SELECT COALESCE(SUM(CASE WHEN kind='income' THEN amount
    ELSE -amount END),0) FROM tx WHERE status='confirmed'""").fetchone()[0]
assert balance()==-250000
# Budżet -> oczekująca transakcja
db.execute("INSERT INTO tx VALUES('budget-new','expense',3381,'pending',NULL)")
assert balance()==-250000
# Potwierdzenie dopiero po osobnej decyzji.
assert db.execute("""UPDATE tx SET status='confirmed',bank_key='new-key'
    WHERE id='budget-new' AND status='pending'""").rowcount==1
assert balance()==-253381
assert db.execute("""UPDATE tx SET status='confirmed'
    WHERE id='budget-new' AND status='pending'""").rowcount==0
assert balance()==-253381
print("PASS bank -> budzet: jednorazowy/cykliczny, nowa pozycja, bez dubletu salda")
