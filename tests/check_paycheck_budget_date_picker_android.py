#!/usr/bin/env python3
"""Regresja: wszystkie pełne daty i miesiące Budżetu otwierają picker Androida."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
src=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")

date_helper=src.split(
    "private void attachPaycheckBudgetDateCalendar(EditText input,",1)[1].split(
    "private void attachPaycheckBudgetMonthCalendar(",1)[0]
month_helper=src.split(
    "private void attachPaycheckBudgetMonthCalendar(EditText input,",1)[1].split(
    "private void showBudgetInvoiceDialog(",1)[0]
invoice=src.split(
    "private void showBudgetInvoiceDialog(PaycheckMonthlyBudget.Item item,",1)[1].split(
    "private void pickBudgetAttachment(",1)[0]
new_item=src.split(
    "private void showBudgetItemDialog(boolean privateScope)",1)[1].split(
    "private void showBudgetItemSummary(",1)[0] if (
        "private void showBudgetItemSummary(" in src.split(
            "private void showBudgetItemDialog(boolean privateScope)",1)[1]
    ) else src.split(
        "private void showBudgetItemDialog(boolean privateScope)",1)[1].split(
        "private void ",1)[0]

for name,func in [("data",date_helper),("miesiąc",month_helper)]:
    for contract in (
        "input.setFocusable(false);",
        "input.setClickable(true);",
        "input.setOnClickListener(",
        "new DatePickerDialog(this,",
        "dialog.show();",
        'dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"Wyczyść",',
    ):
        assert contract in func,(name,contract)
assert "java.time.LocalDate.parse(" in date_helper
assert "java.time.LocalDate.of(year,month+1,day).toString()" in date_helper
assert "YearMonth.parse(" in month_helper
assert "YearMonth.of(year,month+1).toString()" in month_helper
assert 'Wybierz miesiąc • dzień jest pomijany' in month_helper

for marker in (
    "attachPaycheckBudgetDateCalendar(due,",
    "attachPaycheckBudgetDateCalendar(plan,",
):
    assert marker in invoice,marker
assert "String savedPlan=item.plannedPaymentDates.get(month.toString());" in invoice
assert "due.addTextChangedListener(watcher);" in invoice
assert "plan.addTextChangedListener(watcher);" in invoice
assert 'attachPaycheckBudgetDateCalendar(invoiceDueDate,' in new_item
for marker in (
    "attachPaycheckBudgetMonthCalendar(startMonth,null,",
    "attachPaycheckBudgetMonthCalendar(endMonth,startMonth,",
    "endMonth.addTextChangedListener(endWatcher);",
    "startMonth.addTextChangedListener(startWatcher);",
):
    assert marker in new_item,marker
# Dzień miesiąca pozostaje celowo liczbą 1–31, nie pełną datą.
assert 'dueDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);' in new_item

# Każde pole kalendarza musi pozostać opcjonalne tylko tam, gdzie
# walidacja pozwala na pusty tekst; wymagany termin faktury nie może zniknąć.
assert "oldDue==null?month.atDay(1):oldDue,false" in invoice
assert "PaycheckBudgetInvoiceDates.planned(month,item.dueDay,oldDue),true" in invoice
assert "defaultInvoiceMonth.atDay(1),true" in new_item
assert "paycheckBudgetMonth==null?YearMonth.now():paycheckBudgetMonth,false" in new_item
assert "paycheckBudgetMonth==null?YearMonth.now():paycheckBudgetMonth,true" in new_item
print("PASS: 5 pól dat Budżetu/PayCheck otwiera Android DatePicker, czyszczenie i raty zachowane")
