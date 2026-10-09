#!/usr/bin/env python3
"""Etap 6 P0: widoczne listy wyboru i ostrzeżenia finansowe w tytule."""
from pathlib import Path

src=(Path(__file__).resolve().parents[1]/
     "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(
    encoding="utf-8")
assert "private android.view.View paycheckChoiceDialogTitle(" in src
assert "hint.setMaxLines(3);" in src
assert "hint.setText(explanation);" in src

sections=[
    "Wybierz właściwy wydatek / wpływ",
    "Potwierdzenia • ",
    "Wybierz właściwą transakcję",
    "Pasuje do Budżetu miesiąca",
    "Odliczyć nadpłatę?",
    "Co chcesz zamknąć?",
    "Odbiorcy / szablony",
    "Korekta / zakończenie zobowiązania",
]
for title in sections:
    token='"'+title
    start=src.find(token)
    assert start>=0,title
    prefix=src[max(0,start-130):start]
    assert ".setCustomTitle(paycheckChoiceDialogTitle(" in prefix,title
    after=src[start:start+1100]
    assert ".setItems(" in after,title
    assert ".setMessage(" not in after.split(".setItems(",1)[0],title

# Kolejka i ręczny wybór z Budżetu też nie łączą message i listy.
queue=src.split(
    "private void showSharedPaycheckPendingQueue(int offset)",1)[1].split(
    "private void confirmSharedPaycheckEntry(",1)[0]
picker=src.split(
    "private void showBudgetPaymentPicker(PaycheckMonthlyBudget.Item item,",1)[1].split(
    "private void confirmBudgetPaymentAssignment(",1)[0]
for body in (queue,picker):
    assert ".setItems(" in body
    assert ".setMessage(" not in body
# W następnej fazie musi nadal istnieć świadome potwierdzenie.
assert '.setPositiveButton("Sprawdziłem — potwierdź"' in src
assert 'PaycheckStore.confirm(db.getWritableDatabase()' in src
assert 'boolean changed=PaycheckMonthlyBudget.match(' in src
print("PASS: 10 list PayCheck jest klikalnych, ostrzeżenia i świadoma zgoda zachowane")
