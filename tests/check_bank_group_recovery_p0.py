#!/usr/bin/env python3
"""P0: dwuetapowe grupowe rozliczenie SQLite->Budżet i odtwarzanie po awarii.

Kontrakt produkcyjnych wywołań Java + symulacja przejść i awarii SQLite.
Nie zastępuje testów Android na rzeczywistej bazie.
"""
from pathlib import Path
import sqlite3

base = Path(__file__).resolve().parents[1]
src = base / "app/src/main/java/com/edwinkarolczyk/edhome"
settle = (src / "BankBudgetGroupSettlement.java").read_text(encoding="utf-8")
main = (src / "MainActivity.java").read_text(encoding="utf-8")
budget = (src / "PaycheckMonthlyBudget.java").read_text(encoding="utf-8")

for token in (
    "bank_group_settlement_recovery", "CREATE TABLE IF NOT EXISTS",
    "requireNoUnfinished(db)", "verifiedPayment(db,",
    'journal(db,a.key,"two_to_one"', 'journal(db,bank.key,"one_to_two"',
    "static int repairInterrupted(", 'db.delete(JOURNAL,"evidence_key=?"',
    "PaycheckMonthlyBudget.matchBankParts(", "PaycheckMonthlyBudget.allocateSplit(",
):
    assert token in settle, token

two = settle.split("static void twoBankToOneBudget(", 1)[1].split(
    "static void oneBankToTwoBudgets(", 1)[0]
one = settle.split("static void oneBankToTwoBudgets(", 1)[1]
for segment in (two, one):
    assert segment.index("db.setTransactionSuccessful();") < segment.index(
        "}finally{db.endTransaction();}") < segment.index("repairInterrupted(prefs,db);")
    assert "PaycheckMonthlyBudget.matchBankParts(prefs" not in segment
    assert "PaycheckMonthlyBudget.allocateSplit(prefs" not in segment

screen = main.split("private void paycheck() {", 1)[1].split(
    "ensureBudgetPaycheckPending(YearMonth.now())", 1)[0]
assert screen.index("BankBudgetGroupSettlement.repairInterrupted(") < screen.index(
    "BankBudgetSingleSettlement.repairUnlinked(")
assert 'note("UWAGA: wykryto nieukończone rozliczenie bankowe.' in screen
assert "return;" in screen
repair = settle.split("static int repairInterrupted(", 1)[1].split(
    "static void twoBankToOneBudget(", 1)[0]
for forbidden in ("PaycheckStore.add(", "PaycheckStore.confirm(", "matchBudgetActual("):
    assert forbidden not in repair, forbidden

assert 'editor.putString(PREF_KEY,serialize(items))' in budget
assert 'PaycheckBudgetHistoryStore.stage(prefs,editor,events)' in budget

# Symulacja księgi: 500 zł wydatku, jednego dowodu albo 200+300.
def new_db():
    con = sqlite3.connect(":memory:")
    con.executescript("""
      CREATE TABLE payments (id TEXT PRIMARY KEY, amount INTEGER, state TEXT);
      CREATE TABLE journal (id TEXT PRIMARY KEY, mode TEXT NOT NULL);
    """)
    return con

def balance(con):
    return -sum(row[0] for row in con.execute(
        "SELECT amount FROM payments WHERE state='confirmed'"))

for mode, amounts in (("two_to_one", (20000,30000)),
                      ("one_to_two", (50000,))):
    # Crash PRZED SQLite commit: ani saldo, ani dziennik nie utrwalają się.
    con = new_db()
    con.execute("BEGIN")
    for n, amount in enumerate(amounts):
        con.execute("INSERT INTO payments VALUES (?,?,?)",
                    (f"p{n}",amount,"confirmed"))
    con.execute("INSERT INTO journal VALUES (?,?)", ("group",mode))
    con.rollback()
    assert balance(con) == 0
    assert con.execute("SELECT count(*) FROM journal").fetchone()[0] == 0

    # Crash PO SQLite commit, PRZED prefs: bez ponownego księgowania.
    with con:
        for n, amount in enumerate(amounts):
            con.execute("INSERT INTO payments VALUES (?,?,?)",
                        (f"p{n}",amount,"confirmed"))
        con.execute("INSERT INTO journal VALUES (?,?)", ("group",mode))
    assert balance(con) == -50000
    planned_links = set()
    for _ in range(2):  # powtórne wykonanie recovery jest idempotentne
        rows = con.execute("SELECT id,mode FROM journal").fetchall()
        for key, _ in rows:
            if key not in planned_links:
                planned_links.add(key)
            con.execute("DELETE FROM journal WHERE id=?", (key,))
        assert balance(con) == -50000
    assert planned_links == {"group"}
    assert con.execute("SELECT count(*) FROM journal").fetchone()[0] == 0
    con.close()

print("PASS P0 bank group: journal trwale po SQL, prefs po commit, saldo bez ponownego ksiegowania")
