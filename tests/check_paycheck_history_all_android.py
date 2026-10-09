#!/usr/bin/env python3
"""Historia PayCheck na Androidzie ma dostep do wszystkich wplywow i wydatkow."""
from pathlib import Path
import sqlite3
root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(java/"MainActivity.java").read_text(encoding="utf-8")
bank=(java/"BankStatementCsv.java").read_text(encoding="utf-8")
mbank=(java/"BankStatementMbank.java").read_text(encoding="utf-8")
history=(java/"PaycheckBudgetHistoryStore.java").read_text(encoding="utf-8")

assert "private void showSharedPaycheckHistory()" in main
assert "private void showSharedPaycheckHistoryPage(String kind,int offset)" in main
assert "private void showSharedPaycheckHistoryEntry(String operationId)" in main
assert 'button("Pełna historia • wpływy + / wydatki −",' in main
assert 'button("Historia PayCheck • wszystkie wpływy + / wydatki −",' in main
assert 'new String[]{' in main and '"Tylko wpływy (+)"' in main
assert '"Tylko wydatki (−)"' in main
body=main.split("private void showSharedPaycheckHistoryPage(",1)[1].split(
    "private void showSharedPaycheckHistoryEntry(",1)[0]
for required in (
    "WHERE scope='shared'", '" AND kind=?"',
    "ORDER BY id DESC LIMIT ? OFFSET ?", "Integer.toString(offset)",
    'ids.remove(pageSize);', 'labels.remove(pageSize);',
    '"→ Następne 60 wpisów"', '"← Poprzednie"',
    '.setView(scroll)',
    'details.setVisibility(View.GONE)',
    'expanded[0].setVisibility(View.GONE)',
    '"income".equals(c.getString(1))?"+ ":"− "',
):
    assert required in body, required
assert ".setMessage(" not in body, "Komunikat Android moze schowac liste"
assert 'if (++count >= 40)' not in body
assert 'ORDER BY id DESC LIMIT 40' in main, "Zostawiono podglad 40 wpisow"
# Ostatnie 40 w glownym PayCheck i pełna historia bez kart;
# klik w inna kwotę zwija poprzednią pozycję.
summary=main.split('title("PayCheck • ostatnie operacje");',1)[1].split(
    'if(count==0)note("Brak transakcji.");',1)[0]
assert 'LinearLayout entry=new LinearLayout(this);' in summary
assert 'LinearLayout entry=card();' not in summary
assert 'expandedPaycheckDetails[0].setVisibility(View.GONE)' in summary
assert 'details.setVisibility(open?View.VISIBLE:View.GONE)' in summary
assert 'smallButton(details,"Usuń wpis"' in summary


events=main.split("private void showBudgetItemHistory(PaycheckMonthlyBudget.Item item,int offset)",1)[1].split(
    "/** Jedno zobowiązanie",1)[0]
assert "final int pageSize=60;" in events
assert "showBudgetItemHistory(item,first+pageSize)" in events
assert '"→ Następne wpisy historii"' in events
assert "events.get(first+i)" in events
assert "Math.min(events.size(),100)" not in events
assert "PaycheckBudgetSqliteStore.archivedHistory(db,null)" in history

assert 'Plan: wpływy + ' in main and 'wydatki − ' in main
assert 'Do potwierdzenia: wpływy + ' in main
assert '("income".equals(item.kind)?"+ ":"− ")' in main
assert 'replace(\'−\',\'-\')' in bank
assert 'replace(\'−\',\'-\')' in mbank

# Wykonywalny test prawdziwej paginacji SQLite po ponad 100 wpisach.
db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE paycheck_transactions (id INTEGER PRIMARY KEY,scope TEXT,kind TEXT,amount_grosz INTEGER)")
for i in range(1,182):
    db.execute("INSERT INTO paycheck_transactions VALUES (?,?,?,?)",
        (i,"shared","income" if i%3==0 else "expense",i*100))
all_ids={x[0] for x in db.execute("SELECT id FROM paycheck_transactions WHERE scope='shared'")}
for kind in (None,"income","expense"):
    loaded=[]
    offset=0
    while True:
        where="WHERE scope='shared'"+("" if kind is None else " AND kind=?")
        values=(61,offset) if kind is None else (kind,61,offset)
        raw=db.execute("SELECT id FROM paycheck_transactions "+where+
            " ORDER BY id DESC LIMIT ? OFFSET ?",values).fetchall()
        loaded.extend(x[0] for x in raw[:60])
        if len(raw)<=60: break
        offset+=60
    expected=all_ids if kind is None else {
        x[0] for x in db.execute("SELECT id FROM paycheck_transactions WHERE kind=?",(kind,))}
    assert set(loaded)==expected,(kind,len(loaded),len(expected))
    assert len(loaded)==len(set(loaded)),(kind,"duplikaty")
print("PayCheck Android: 181 wpisow, pelna historia, filtry +/-, archiwum i paginacja PASS")
