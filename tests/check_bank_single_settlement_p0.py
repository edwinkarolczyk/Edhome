#!/usr/bin/env python3
"""P0: potwierdzenie 33,81 zł / recovery częściowego SQLite + Budżet.
Testuje kontrakt Android + księgę SQLite. Fizyczny telefon nadal do odbioru.
"""
from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
single=(src/"BankBudgetSingleSettlement.java").read_text(encoding="utf-8")
budget=(src/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
bank=(src/"BankEvidenceStore.java").read_text(encoding="utf-8")
action=main.split("private void confirmBankEvidenceMatch(",1)[1].split(
    "private void showDismissedBankEvidence()",1)[0]
screen=main.split("private void paycheck() {",1)[1].split(
    "private void showPaycheckBankMenu()",1)[0]
for required in (
    '"Zatwierdź parę"', "BankBudgetSingleSettlement.confirm(",
    "BankBudgetSingleSettlement.Result", "beforeGrosz", "afterGrosz",
    "BankBudgetSingleSettlement.PartialSettlementException",
    "PAYCHECK_BANK_PARTIAL_SETTLEMENT", "BankBudgetSingleSettlement.repairUnlinked(",
    "ZATWIERDZONO PRZELEW BANKOWY", "Status: uzgodnione z bankiem (✓).",
):
    assert required in action, required
assert "BankBudgetSingleSettlement.repairUnlinked(" in screen
assert screen.index("BankBudgetSingleSettlement.repairUnlinked(") < screen.index(
    "PaycheckStore.sharedBalance("), "Napraw przed wyświetleniem salda"
preflight=single.split("static Result confirm(",1)[1].split(
    "static int repairUnlinked(",1)[0]
for required in (
    "PaycheckMonthlyBudget.preflightBankMatch(", "SELECT amount_grosz,kind,booking_date,state",
    "BankEvidenceStore.matchBudgetActual(", "PaycheckMonthlyBudget.match(",
    "SELECT status,amount_grosz,statement_key", "item.matchedOperationIds.contains(operationId)",
    "PaycheckStore.sharedBalance(db)", "Math.addExact(before",
):
    assert required in preflight,required
assert preflight.index("PaycheckMonthlyBudget.preflightBankMatch(") < preflight.index(
    "BankEvidenceStore.matchBudgetActual(") < preflight.index(
    "PaycheckMonthlyBudget.match(")
assert 'paycheck_transactions t JOIN bank_evidence_queue e' in single
recovery=single.split("static int repairUnlinked(",1)[1]
for required in ("e.matched_operation_id=t.operation_id",
                 "e.state='matched'", "t.status='confirmed'",
                 "budgetOperationId(item.id,month)",
                 "item.matchedOperationIds.contains(opId)",
                 "PaycheckMonthlyBudget.match(prefs,db,item.id,opId)"):
    assert required in recovery,required
assert "BankEvidenceStore.matchBudgetActual(" not in recovery
assert "PaycheckStore.add(" not in recovery
assert "PaycheckStore.confirm(" not in recovery

# Odpowiada stanom SQLite aplikacji; 2500 zł wcześniej rozliczone,
# 33,81 zł czeka. Po potwierdzeniu saldo to -2533,81 zł dokładnie raz.
connection=sqlite3.connect(":memory:")
connection.execute("""CREATE TABLE paycheck_transactions(
  operation_id TEXT PRIMARY KEY,kind TEXT,amount_grosz INTEGER,
  status TEXT,statement_key TEXT UNIQUE, note TEXT)""")
connection.execute("""CREATE TABLE bank_evidence_queue(
  evidence_key TEXT PRIMARY KEY,kind TEXT,amount_grosz INTEGER,
  booking_date TEXT,state TEXT,matched_operation_id TEXT)""")
connection.executemany("INSERT INTO paycheck_transactions VALUES (?,?,?,?,?,?)",[
  ("2500","expense",250000,"confirmed","bank-2500","Budżet 2026-10 • przelew"),
  ("33_81","expense",3381,"pending",None,"Budżet 2026-10 • Rejestrator kamer"),
])
connection.executemany("INSERT INTO bank_evidence_queue VALUES (?,?,?,?,?,?)",[
  ("bank-2500","expense",250000,"2026-10-09","matched","2500"),
  ("bank-33_81","expense",3381,"2026-10-12","open",None),
])
def balance():
  return connection.execute("""SELECT COALESCE(SUM(CASE WHEN kind='income'
    THEN amount_grosz ELSE -amount_grosz END),0)
    FROM paycheck_transactions WHERE status='confirmed'""").fetchone()[0]
assert balance()==-250000
with connection:
  assert connection.execute("""UPDATE paycheck_transactions SET status='confirmed',
      amount_grosz=?, statement_key=? WHERE operation_id=? AND status='pending'
      AND statement_key IS NULL""",(3381,"bank-33_81","33_81")).rowcount==1
  assert connection.execute("""UPDATE bank_evidence_queue
      SET state='matched',matched_operation_id=? WHERE evidence_key=?
      AND state='open'""",("33_81","bank-33_81")).rowcount==1
assert balance()==-253381
assert connection.execute("""UPDATE paycheck_transactions SET status='confirmed'
      WHERE operation_id=? AND status='pending'""",("33_81",)).rowcount==0
assert balance()==-253381

# Awaria pomiędzy SQL i zapisem planu: naprawa przypisuje istniejący UUID
# bez zmiany statusu, kwoty, bankowego dowodu ani salda.
assigned=set()
assert "33_81" not in assigned
before_recovery=balance()
assert connection.execute("""SELECT matched_operation_id FROM bank_evidence_queue
    WHERE evidence_key='bank-33_81'""").fetchone()[0]=="33_81"
assigned.add("33_81")
assert balance()==before_recovery
assert len(assigned)==1
print("PASS P0: 33,81 -> saldo raz, potwierdzenie stanu, recovery bez podwojnego ksiegowania")
