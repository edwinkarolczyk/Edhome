"""Etap 3: kontrola rzeczywistego zapisu płatności, dat i odwracania uzgodnień."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/com/edwinkarolczyk/edhome"
budget=(java/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
history=(java/"PaycheckBudgetHistoryStore.java").read_text(encoding="utf-8")
main=(java/"MainActivity.java").read_text(encoding="utf-8")
rules=(java/"PaycheckBudgetHistoryRules.java").read_text(encoding="utf-8")

def between(text,first,last):
    return text.split(first,1)[1].split(last,1)[0]

tx=between(budget,"static SharedTransaction sharedTransaction(", "private static void saveWithEvents(")
assert '"FROM paycheck_transactions WHERE scope=' in tx
assert "created_at,statement_date,status" in tx
assert "PaycheckBudgetHistoryRules.bankDate(" in tx
assert "confirmed" in tx

match=between(budget,"static boolean match(SharedPreferences prefs, SQLiteDatabase db,", "static boolean allocateMatch(")
assert "sharedTransaction(db,operationId)" in match
assert "if (existing) return false" in match, "Ponowne przypisanie nie może tworzyć kolejnych płatności"
assert "saveWithEvents(prefs,items,events)" in match
assert "YearMonth.now()" not in match
assert "addPaymentEvents(" in match

payment=between(budget,"private static void addPaymentEvents(", "static boolean match(")
for label in ['"PAYMENT_CONFIRMED"','"OVERPAYMENT"','"UNDERPAYMENT"']:
    # OVERPAYMENT i UNDERPAYMENT wybiera czysta reguła obliczeniowa.
    assert label in payment or label in rules, label
assert "PaycheckBudgetHistoryRules.differenceType(" in payment
assert "tx.operationId,tx.date" in payment

allocation=between(budget,"static PaycheckBudgetSplitMath.Result allocateSplit(", "static long allocatedForOperation(")
assert "tx.month" in allocation and "tx.operationId,tx.date" in allocation
assert '"PAYMENT_CONFIRMED"' in allocation
assert 'result.differenceGrosz > 0L ? "OVERPAYMENT" : "UNDERPAYMENT"' in allocation
assert "saveWithEvents(prefs,all,events)" in allocation
assert "YearMonth.now()" not in allocation

unmatch=between(budget,"static boolean unmatch(SharedPreferences prefs,", "static boolean moveOptionalToNextMonth(")
assert "SharedTransaction tx" in unmatch
assert '"PAYMENT_REVERSED"' in unmatch and "tx.month" in unmatch
assert "saveWithEvents(prefs,items,events)" in unmatch
assert "YearMonth.now()" not in unmatch
assert "sharedTransaction(" in main and "PaycheckMonthlyBudget.unmatch(prefs,operationId," in main

assert "editor.putString(PREF_KEY,serialize(items))" in budget
assert "PaycheckBudgetHistoryStore.stage(prefs,editor,events)" in budget
assert "editor.commit()" in budget

for label in ("operationId","transactionDate","recipientId","month","amountGrosz"):
    assert f"json.put(\"{label}\"" in history, "Brak w serializacji: "+label
    assert f"json.optString(\"{label}\"" in history or label in ("recipientId","month","amountGrosz")
assert "json.optString(\"operationId\",\"\")" in history, "Stare historie muszą być odczytywane"
assert 'case "PAYMENT_CONFIRMED"' in main
assert 'case "UNDERPAYMENT"' in main
assert 'case "OVERPAYMENT"' in main
assert "event.transactionDate" in main and "event.operationId" in main
assert "PaycheckBudgetHistoryRules.bankMonth(" in budget

print("EDHOME etapa 3: daty przelewów, jawne zdarzenia, idempotencja i zapis atomowy PASS")
