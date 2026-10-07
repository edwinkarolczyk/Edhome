#!/usr/bin/env python3
"""Monthly PayCheck plan stays separate from real confirmed ledger."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
budget = (root / "PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
vault = (root / "PrivatePaycheckVault.java").read_text(encoding="utf-8")
backup = (root / "DataBackup.java").read_text(encoding="utf-8")
portable = (root / "PrivatePaycheckPortable.java").read_text(encoding="utf-8")

for token in (
    'PREF_KEY = "paycheck_monthly_budget_v1"',
    '"fixed".equals(item.amountMode)',
    '"estimate".equals(item.amountMode)',
    'ALLOWED_CYCLES = {0, 1, 2, 3, 6, 12}',
    '"FROM paycheck_transactions WHERE scope=\'shared\' AND status=?"',
    'static Totals sharedActual(',
    'static Totals privateActual(',
    'static List<Item> activeFor(',
    'static Totals planned(',
    'static Item suggest(',
    'static boolean match(SharedPreferences prefs',
    'static long sharedMatchedActual(',
    'static long privateMatchedActual(',
    'matchedOperationIds',
    'matchedAllocationsGrosz',
    'static boolean allocateMatch(',
    'static boolean moveOptionalToNextMonth(',
    'static boolean closeOptionalForMonth(',
    'static void changeAmountForMonth(',
    'static void changeAmountFromMonth(',
    'static long plannedAmount(',
    'static int installmentPosition(',
    'static int installmentTotal(',
    'static long sharedCarryBefore(',
    'static void closeArrears(',
    'static void endCycleAt(',
    'balanceAdjustmentsGrosz',
    'adjustmentReasons',
    'boolean optional;',
    'boolean installment;',
    'int installmentCount;',
    'int dueDay;',
    'String creditAgreementNumber;',
    'json.put("creditAgreementNumber", item.creditAgreementNumber);',
    'json.optString("creditAgreementNumber", "")',
    '"loans".equals(item.category)',
):
    assert token in budget, "Missing monthly budget contract: " + token

for token in (
    'sharedMonthlyBudgetBlock();',
    'sharedMonthlyBudgetEntry();',
    'case "paycheck_budget": paycheckMonthlyBudget(); break;',
    'Wejdź do budżetu miesiąca',
    'Budżet miesiąca • ',
    'planowane saldo',
    'Różnica plan–fakt',
    'Pozycja planowana nie jest transakcją.',
    '◀ Poprzedni',
    'Następny ▶',
    'Bieżący miesiąc',
    'NIEZAPŁACONE',
    'ZAPŁACONE',
    'OPCJONALNY',
    'NIEDOPŁATA',
    'NADPŁATA',
    'ZALEGŁOŚĆ',
    'Z poprzednich miesięcy: zaległości',
    'Zamknij zaległość',
    'Zakończ cykl',
    '! OSTATNIA',
    'showCloseBudgetArrearsDialog(item,month,carry)',
    'PaycheckMonthlyBudget.sharedCarryBefore(',
    'budgetItemCard(item, month)',
    'showBudgetAmountChangeDialog(item, month)',
    'moveBudgetOptional(item, month)',
    'closeBudgetOptional(item, month)',
    'Jednorazowy',
    'Cykliczny',
    'Rata',
    'Termin płatności • dzień 1–31',
    'Liczba rat, np. 12',
    'Jedno pole automatycznie blokuje drugie.',
    'Nr umowy kredytowej (opcjonalnie)',
    'refreshCreditAgreementVisibility',
    '"loans".equals(itemCategory)',
    'Nr umowy kredytowej: ',
    'Tylko " + label',
    'Od " + label + " na stałe',
    'Podziel → ',
    'PAYCHECK_SHARED_BUDGET_SPLIT_MATCHED',
    'PaycheckMonthlyBudget.allocateMatch(',
    'privateMonthlyBudgetBlock(entries);',
    'Prywatny plan jest szyfrowany w sejfie.',
    'transakcje potwierdzone po sprawdzeniu banku / wyciągu',
    'Pasuje do Budżetu miesiąca',
):
    assert token in main, "Missing Android monthly budget UI: " + token

assert "PaycheckStore.add" not in budget
assert "INSERT INTO paycheck_transactions" not in budget
assert "UPDATE paycheck_transactions" not in budget
assert "DELETE FROM paycheck_transactions" not in budget

for token in (
    "private_paycheck_budget_items",
    "PrivatePaycheckCrypto.seal(",
    "PaycheckMonthlyBudget.fromJson(",
    "static String addBudgetItem(",
    "static boolean deleteBudgetItem(",
    "static boolean matchBudgetOperation(",
    "unlinkBudgetOperationsInside(",
):
    assert token in vault, "Private budget must stay encrypted: " + token

for token in (
    'plain.put("budget", budget);',
    'decoded.optJSONArray("budget")',
    'private_paycheck_budget_items',
):
    assert token in portable, "Private encrypted backup missing: " + token

for token in (
    'settings.put("paycheckMonthlyBudget"',
    'PaycheckMonthlyBudget.validateSerialized(paycheckMonthlyBudget);',
    '.putString(PaycheckMonthlyBudget.PREF_KEY, paycheckMonthlyBudget)',
):
    assert token in backup, "Shared budget backup missing: " + token

# Existing transaction confirmation remains the source of truth for real saldo.
for token in (
    'status=\'confirmed\'',
    'Potwierdź po sprawdzeniu banku / wyciągu',
    'Do potwierdzenia • bez wpływu na saldo',
    'Sprawdziłem w banku • potwierdź',
):
    assert token in main, "Confirmed-ledger gate missing: " + token

assert 'PaycheckMonthlyBudget.match(' in main
assert 'PrivatePaycheckVault.matchBudgetOperation(' in main
assert 'PaycheckMonthlyBudget.unmatch(prefs,operationId)' in main
print("PayCheck monthly plan, simple matching, private encryption and confirmed-transaction gate: PASS")
