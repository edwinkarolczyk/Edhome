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
dialog_bodies=src.split(".setCustomTitle(paycheckChoiceDialogTitle(")[1:]
assert len(dialog_bodies)==8, len(dialog_bodies)
for title in sections:
    matches=[chunk for chunk in dialog_bodies
             if '"'+title in chunk.split(".setItems(",1)[0]]
    assert len(matches)==1,(title,len(matches))
    before_list=matches[0].split(".setItems(",1)[0]
    assert ".setItems(" in matches[0],title
    assert ".setMessage(" not in before_list,title

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
