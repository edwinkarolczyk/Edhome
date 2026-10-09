#!/usr/bin/env python3
"""Regresja Android: planowane kwoty pojawiaja sie w PayCheck, lecz nie zasilaja ksiegi automatycznie."""
from pathlib import Path
import sqlite3
from uuid import uuid3, NAMESPACE_DNS

root=Path(__file__).resolve().parents[1]
base=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(base/"MainActivity.java").read_text(encoding="utf-8")
paycheck=(base/"PaycheckStore.java").read_text(encoding="utf-8")
budget=(base/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")

def between(source,a,b):
    return source.split(a,1)[1].split(b,1)[0]

pay_ui=between(main,"private void paycheck() {","/** Android notification access")
assert "sharedBudgetAwaitingPaycheckConfirmation();" in pay_ui
assert "private void sharedBudgetAwaitingPaycheckConfirmation()" in pay_ui
queue=between(main,"private void sharedBudgetAwaitingPaycheckConfirmation()",
              "private String budgetPaycheckOperationId(")
for token in (
    "PaycheckMonthlyBudget.activeFor(",
    "PaycheckMonthlyBudget.remainingDue(",
    "WHERE scope='shared' AND operation_id=?",
    '"Z Budżetu miesiąca • do obsłużenia ("',
    '" • PLAN"', '" • DO POTWIERDZENIA"',
    "smallButton(entry,\"Wybierz istniejący przelew\"",
    "showBudgetToPaycheckDialog(item,month)",
    "confirmBudgetPaymentAssignment(item,month,",
    "if(due<=0)continue;",
):
    assert token in queue, token
assert "PaycheckStore.add(" not in queue, "Samo wejscie do PayCheck nie tworzy operacji"
bridge=between(main,"private void showBudgetToPaycheckDialog(",
               "private void sharedMonthlyBudgetEntry()")
for token in (
    "if(!month.equals(YearMonth.now()))",
    "WHERE scope='shared' AND operation_id=?",
    "showBudgetPaymentPicker(item,month,0)",
    "PaycheckStore.add(db.getWritableDatabase()",
    '"COMMITTED".equals(result)',
    "render();",
    "Saldo bez zmian.",
    "Nie utworzono duplikatu.",
):
    assert token in bridge, token
assert "PaycheckStore.confirm(" not in bridge, "Zgloszenie nie jest potwierdzeniem"
assert "PaycheckMonthlyBudget.match(" not in bridge, "Zgloszenie nie jest realizacja budzetu"
assert "nameUUIDFromBytes(" in main and '"edhome-budget-pending:"' in main
assert 'button("← Wróć do PayCheck", ()->go("paycheck"));' in main
assert '"→ PayCheck • do potwierdzenia"' in main
assert 'status=\"pending\";' not in budget
assert 'values.put("status","pending");' in paycheck
assert '"operation_id=? AND scope=\'shared\' AND status=\'pending\'"' in paycheck

# Fikcyjny test 3 etapow: plan nie zmienia salda, utworzenie pending nie
# zmienia salda, potwierdzenie zmienia saldo dokladnie 1 raz.
db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE ledger (op TEXT PRIMARY KEY,kind TEXT,amount INTEGER,status TEXT)")
def balance():
    return db.execute("""SELECT COALESCE(SUM(CASE WHEN kind='income'
        THEN amount ELSE -amount END),0) FROM ledger WHERE status='confirmed'""").fetchone()[0]
assert balance()==0
op="EDHOME-BUDGET-TEST-2026-10"
db.execute("INSERT INTO ledger VALUES (?,?,?,?)",(op,"expense",25000,"pending"))
assert balance()==0
try:
    db.execute("INSERT INTO ledger VALUES (?,?,?,?)",(op,"expense",25000,"pending"))
    raise AssertionError("Duplikat operacji")
except sqlite3.IntegrityError: pass
assert db.execute("UPDATE ledger SET status='confirmed' WHERE op=? AND status='pending'",(op,)).rowcount==1
assert balance()==-25000
assert db.execute("UPDATE ledger SET status='confirmed' WHERE op=? AND status='pending'",(op,)).rowcount==0
assert balance()==-25000
print("PASS Android Budzet -> PayCheck: plan widoczny, jedno zgloszenie, jedno potwierdzenie, brak podwojnego salda")
