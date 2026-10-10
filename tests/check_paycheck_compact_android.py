#!/usr/bin/env python3
"""P0: kompaktowy PayCheck, pełne akcje nadal dostępne bez naruszania finansów."""
from pathlib import Path

source=(Path(__file__).resolve().parents[1] /
        "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")

def section(start,end):
    assert start in source and end in source,(start,end)
    return source.split(start,1)[1].split(end,1)[0]

main=section("private void paycheck() {",
             "/** Pełna historia księgi")
due=section("private void sharedBudgetAwaitingPaycheckConfirmation() {",
            "/** To samo ID dla tej samej pozycji")
menu=section("private void showPaycheckBankMenu() {",
             "private void showPaycheckToolsMenu() {")
goals=section("private void paycheckGoals() {",
              "private void paycheck() {")

for text in ('sharedMonthlyBudgetEntry();',
             'sharedBudgetAwaitingPaycheckConfirmation();',
             'this::showPaycheckBankMenu',
             'this::showPaycheckToolsMenu',
             'this::showSharedPaycheckPendingQueue',
             'ORDER BY id DESC LIMIT 12',
             'expandedPaycheckDetails[0].setVisibility(View.GONE)'):
    assert text in main,text

assert 'showBankNotificationHints();' not in main
assert 'note("Diagnostyka importu:' not in main
assert 'sharedPaycheckGoals();' not in main

for text in ('new LinearLayout(this)', 'setOrientation(LinearLayout.HORIZONTAL)',
             'setVisibility(View.GONE)', 'expanded[0].setVisibility(View.GONE)',
             'details.setVisibility(open?View.VISIBLE:View.GONE)',
             'smallButton(details,"Rozlicz z historii bankowej"',
             'smallButton(details,"Wybierz istniejący przelew"',
             'confirmBudgetPaymentAssignment(item,month,',
             'budgetPaycheckOperationId(item,month)',
             'PaycheckMonthlyBudget.remainingDue('):
    assert text in due,text
assert "PaycheckStore.confirm(" not in due
assert "PaycheckStore.add(" not in due

for text in ('"Importuj PDF / CSV / XLSX"',
             'selectStatementCsv();', 'showBankEvidenceQueue(0)',
             'showBankEvidenceArchive(0)', 'BankPdfConverterActivity.class',
             'configureBankNotifications();', 'go("paycheck_bank_diagnostics")'):
    assert text in menu,text

assert 'bankImportDiagLine()' in source
assert 'showBankNotificationHints();' in source
assert 'go("paycheck_goals")' in source
assert 'case "paycheck_goals": paycheckGoals(); break;' in source
assert 'sharedPaycheckGoals();' in goals
assert 'this::deleteSharedPaycheckEntriesBulk' not in main
assert 'deleteSharedPaycheckEntriesBulk();' in source
print("EDHOME PayCheck: zwięzłe wiersze, jedno rozwinięcie, bankowe menu, cele i historia PASS")
