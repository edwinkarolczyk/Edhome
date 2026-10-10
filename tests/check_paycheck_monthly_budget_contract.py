#!/usr/bin/env python3
"""Monthly PayCheck plan stays separate from real confirmed ledger."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
budget = (root / "PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
vault = (root / "PrivatePaycheckVault.java").read_text(encoding="utf-8")
backup = (root / "DataBackup.java").read_text(encoding="utf-8")
portable = (root / "PrivatePaycheckPortable.java").read_text(encoding="utf-8")
recipients = (root / "PaycheckRecipientStore.java").read_text(encoding="utf-8")
history = (root / "PaycheckBudgetHistoryStore.java").read_text(encoding="utf-8")
attachments = (root / "PaycheckBudgetAttachmentStore.java").read_text(encoding="utf-8")
budget_reminder = (root / "PaycheckBudgetReminderReceiver.java").read_text(encoding="utf-8")
archive = (root / "DataBackupArchive.java").read_text(encoding="utf-8")

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
    'static List<Arrear> sharedArrearsBefore(',
    'static List<Credit> sharedCreditsBefore(',
    'static long sharedCreditBefore(',
    'static long creditAppliedTo(',
    'static void applyCredit(',
    'static void closeArrears(',
    'static void closeOccurrence(',
    'static void endCycleAt(',
    'creditApplicationsGrosz',
    'creditApplicationCreatedAt',
    'closedMonths',
    'closedMonthReasons',
    'closedMonthCreatedAt',
    'balanceAdjustmentsGrosz',
    'adjustmentReasons',
    'boolean optional;',
    'boolean installment;',
    'int installmentCount;',
    'int dueDay;',
    'String creditAgreementNumber;',
    'String recipientId;',
    'String inactiveFromMonth;',
    'activeInMonth(item,month)',
    'invoiceDueDates',
    'plannedPaymentDates',
    'static YearMonth installmentEndMonth(',
    'static long installmentTotalPlanned(',
    'static long sharedMatchedActualAll(',
    'static LocalDate defaultPlannedPaymentDate(',
    'static void setInvoiceForMonth(',
    'static LocalDate invoiceDueDate(',
    'static LocalDate plannedPaymentDate(',
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
    'Pozostałe / nieprzypisane wydatki PayCheck',
    'Wykonanie pozycji Budżetu',
    'Pozycja planowana nie jest transakcją.',
    'budgetInlineButton("‹"',
    'budgetInlineButton("›"',
    'NIEZAPŁACONE',
    'ZAPŁACONE',
    'OPCJONALNE',
    'budgetItemDisplayName(item) + " z ',
    'NADPŁATA',
    'ZALEGŁE',
    'Z poprzednich miesięcy: zaległości',
    'Zamknij ręcznie z powodem',
    'Zamknij / cykl',
    'Co chcesz zamknąć?',
    'Pokryj tę pozycję nadpłatą',
    '⚠ ostatnia',
    'budgetArrearRow(',
    'showApplyBudgetCreditDialog(',
    'PaycheckMonthlyBudget.sharedCarryBefore(',
    'budgetItemCard(item, month)',
    'showBudgetAmountChangeDialog(item,month)',
    'moveBudgetOptional(item,month)',
    'closeBudgetOptional(item,month)',
    'Jednorazowy',
    'Cykliczny',
    'Rata',
    'Dzień zapłaty 1–31 • domyślnie 10',
    'Termin faktury YYYY-MM-DD (opcjonalnie)',
    'Planowana zapłata faktury: —',
    'Odbiorca, np. TAURON / bank (opcjonalnie)',
    'refreshInstallmentCalculation',
    'installmentSource',
    'Zobowiązanie łącznie: ',
    'Odbiorca: ',
    'Termin faktury: ',
    'Liczba rat, np. 12',
    'Jedno pole automatycznie blokuje drugie.',
    'Nr umowy kredytowej (opcjonalnie)',
    'refreshCreditAgreementVisibility',
    '"loans".equals(itemCategory)',
    'Nr umowy kredytowej: ',
    'Tylko " + label',
    'Od " + label + " na stałe',
    'Podziel na ',
    'PAYCHECK_SHARED_BUDGET_SPLIT_MATCHED',
    'PaycheckMonthlyBudget.allocateSplit(',
    'budgetSplitItems(',
    'allocateBudgetSplit(',
    'Odbiorcy / szablony',
    'showBudgetRecipientTemplateDialog(',
    '＋ Dodaj zdjęcie / PDF',
    'showBudgetAttachmentActions(',
    'open_paycheck_budget',

    'privateMonthlyBudgetBlock(entries);',
    'Prywatny plan jest szyfrowany w sejfie.',
    'transakcje potwierdzone po sprawdzeniu banku / wyciągu',
    'Pasuje do Budżetu miesiąca',
    'budgetItemDisplayName(item) + " z ',
    'Nadpłata nie jest używana automatycznie.',
    'expandedBudgetItemId',
    'expandedBudgetDetailsView',
    'showBudgetItemHistory(item)',
    'line.setTextColor(statusColor);',
    'Color.rgb(251,192,45)',
):
    assert token in main, "Missing Android monthly budget UI: " + token

# Etap 6 Android: zwięzły bilans i szczegóły na żądanie, bez zmiany księgi.
android_budget = main.split('private void sharedMonthlyBudgetBlock()',1)[1].split(
    'private int budgetSortDay(',1)[0]
for token in (
    'private boolean budgetFinanceDetailsExpanded;',
    '▾ Szczegóły rozliczenia',
    '▴ Ukryj szczegóły rozliczenia',
    'budgetFinanceDetailsExpanded = !budgetFinanceDetailsExpanded;',
    'financeDetails.setVisibility(budgetFinanceDetailsExpanded',
    'summary.addView(financeDetails);',
    'Różnica ',
    'do zapłaty ',
):
    assert token in main, "Brak ergonomii Android etapu 6: " + token
assert android_budget.index('summary.addView(text("Plan:') < android_budget.index(
    'financeDetails.addView(text("Wykonanie pozycji Budżetu:')
assert android_budget.index('summary.addView(text("Z poprzednich miesięcy:') < android_budget.index(
    'financeDetails.addView(text("Wykonanie pozycji Budżetu:')
assert 'if (!pending.empty())' in android_budget
assert 'summary.addView(text("Do potwierdzenia:' in android_budget
assert 'PaycheckMonthlyBudget.sharedArrearsBefore(' in android_budget
assert 'PaycheckMonthlyBudget.sharedCreditBefore(' in android_budget
assert 'private void paycheckMonthlyBudget() {' in main
assert 'sharedMonthlyBudgetBlock();' in main
assert 'private void sharedMonthlyBudgetBlock() {' in main
assert 'Plan: wpływy ' in main.split('private void paycheckMonthlyBudget()',1)[0]


budget_method = main.split('private void budgetItemCard(',1)[1].split(
    'private void budgetArrearRow(',1)[0]
assert 'LinearLayout box = card();' not in budget_method
assert 'LinearLayout box = new LinearLayout(this);' in budget_method
assert 'if (expandedBudgetDetailsView != null' in budget_method
assert 'wspólny czy prywatny' not in main
assert 'Saldo potwierdzone wspólne' not in main
assert 'if ("paycheck_private".equals(destination)) destination="paycheck";' in main

assert "PaycheckStore.add" not in budget
assert "INSERT INTO paycheck_transactions" not in budget
assert "UPDATE paycheck_transactions" not in budget
assert "DELETE FROM paycheck_transactions" not in budget
assert 'sharedCreditBefore(' in budget
assert 'applyCredit(' in budget

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
    'Potwierdź / dopasuj',
    'Do potwierdzenia z Budżetu (',
    'Sprawdziłem w banku • potwierdź',
):
    assert token in main, "Confirmed-ledger gate missing: " + token

assert 'PaycheckMonthlyBudget.match(' in main
assert 'PrivatePaycheckVault.matchBudgetOperation(' in main
assert 'PaycheckMonthlyBudget.unmatch(prefs,operationId,' in main

for token in (
    'PREF_KEY = "paycheck_recipients_v1"',
    'static Recipient getOrCreate(',
    'static String name(',
    'MAX_RECIPIENTS = 200',
):
    assert token in recipients, "Recipient contract missing: " + token

for token in (
    'settings.put("paycheckRecipients"',
    'PaycheckRecipientStore.validateSerialized(paycheckRecipients);',
    '.putString(PaycheckRecipientStore.PREF_KEY, paycheckRecipients)',
):
    assert token in backup, "Recipient backup missing: " + token

for token in (
    'PREF_KEY = "paycheck_budget_history_v1"',
    'ITEM_DEACTIVATED',
    'TRANSACTION_ALLOCATED',
    'ARREAR_CLOSED',
    'OCCURRENCE_CLOSED',
    'CYCLE_ENDED',
):
    assert token in history or token in budget, "Budget history contract missing: " + token

assert 'target.active = false;' in budget
assert 'target.inactiveFromMonth = from.toString();' in budget
assert 'PaycheckBudgetHistoryStore.append(' in budget

for token in (
    'static Recipient updateTemplate(',
    'static boolean deactivate(',
    'static java.util.List<String> suggestions(',
    '"TAURON","Wodociągi","Podatek","Internet"',
    'defaultCategory',
    'defaultDueDay',
    'defaultAmountGrosz',
):
    assert token in recipients, "Recipient template contract missing: " + token

for token in (
    'planned.equals(today.plusDays(3))',
    'reminderDateToday(context,now.toLocalDate())',
    'schedule(context,false);',
    'PAYCHECK_BUDGET_REMINDER_CATCH_UP_TODAY',
    'open_paycheck_budget',
    'PaycheckMonthlyBudget.remainingDue(',
    'PAYCHECK_BUDGET_REMINDER_DELIVERED',
):
    assert token in budget_reminder, "Budget reminder contract missing: " + token
assert '!planned.isAfter(today.plusDays(3))' not in budget_reminder

for token in (
    'PREF_KEY = "paycheck_budget_attachments_v1"',
    'MAX_FILE_BYTES = 20L * 1024 * 1024',
    'application/pdf',
    'image/',
    'FileProvider.getUriForFile',
):
    assert token in attachments, "Budget attachment contract missing: " + token

for token in (
    'paycheckBudgetHistory',
    'PaycheckBudgetHistoryStore.validateSerialized',
    'paycheckBudgetAttachments',
    'PaycheckBudgetAttachmentStore.validateSerialized',
):
    assert token in backup, "Budget extended backup missing: " + token

for token in (
    'BUDGET_ATTACHMENT_PREFIX',
    'PaycheckBudgetAttachmentStore.liveFiles',
    'PaycheckBudgetAttachmentStore.prune',
    'paycheck-budget-attachment',
):
    assert token in archive, "Budget attachment ZIP backup missing: " + token

print("PayCheck monthly plan, simple matching, private encryption and confirmed-transaction gate: PASS")
