"""Stage 4: powiadomienia, faktury per miesiąc, zakres korekty i rozdzielne wskaźniki."""
from pathlib import Path
base=Path(__file__).resolve().parents[1]/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(base/"MainActivity.java").read_text(encoding="utf-8")
budget=(base/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
reminder=(base/"PaycheckBudgetReminderReceiver.java").read_text(encoding="utf-8")
attachments=(base/"PaycheckBudgetAttachmentStore.java").read_text(encoding="utf-8")
rules=(base/"PaycheckBudgetExecutionRules.java").read_text(encoding="utf-8")

def section(text,start,end):
    return text.split(start,1)[1].split(end,1)[0]

assert "static void refreshAfterSettlement(Context context)" in reminder
refresh=section(reminder,"static void refreshAfterSettlement(", "    @Override public void onReceive(")
assert "manager.cancel(NOTIFICATION_ID)" in refresh
assert "showDueReminders(context,true)" in refresh
assert "onlyPreviouslyFired && !previouslyFired" in reminder
assert "PaycheckMonthlyBudget.remainingDue(" in reminder
assert "PaycheckBudgetReminderReceiver.refreshAfterSettlement(this);" in main
assert main.count("PaycheckBudgetReminderReceiver.refreshAfterSettlement(this);") >= 7

assign=section(attachments,"static boolean assignMonth(", "    static List<Attachment> load(")
assert "target.month=month.toString();" in assign
assert "count>=MAX_PER_ITEM" in assign
assert "if (!target.month.isBlank())" in assign
assert "json.optString(\"month\",\"\")" in attachments
assert "month.equals(attachment.month)" in attachments

attach=section(main,"private void showBudgetAttachmentActions(", "private void openBudgetAttachment(")
assert "PaycheckBudgetAttachmentStore.assignMonth(" in attach
assert "ATTACHMENT_MONTH_ASSIGNED" in attach
assert "legacy && which==1" in attach
assert "budgetMonthLabel(viewedMonth)" in attach

delete=section(main,"private void showBudgetDeleteScopeDialog(", "private void confirmBudgetItemDelete(")
assert "selectedMonth" in delete
assert "PaycheckMonthlyBudget.closeOccurrence(" not in delete
assert "showBudgetCloseOccurrenceReasonDialog(item,selectedMonth)" in delete
assert "PaycheckMonthlyBudget.deleteFromMonth(" in delete
assert "PaycheckMonthlyBudget.endCycleAt(" in delete
assert "selectedMonth.plusMonths(1)" in delete
assert "PaycheckBudgetReminderReceiver.refreshAfterSettlement(this)" in delete
assert "YearMonth.now()" not in delete
assert 'showBudgetDeleteScopeDialog(item,month)' in main

end=section(budget,"static boolean deleteFromMonth(", "    /** Dane oryginalnej operacji")
assert "from.toString()" in end
assert '"ITEM_DEACTIVATED"' in end
assert "YearMonth.now()" not in end

execution=section(main,"private void sharedMonthlyBudgetBlock(", "private int budgetSortDay(")
assert "PaycheckMonthlyBudget.sharedAssignedActual(" in execution
assert "PaycheckBudgetExecutionRules.outsideBudget(" in execution
assert "PaycheckBudgetExecutionRules.coveredPlan(" in execution
assert "Wykonanie pozycji Budżetu" in execution
assert "Pozostałe / nieprzypisane wydatki PayCheck" in execution
assert "Różnica plan–fakt" not in execution
assert "confirmed.expense-assigned.expense" not in execution
assigned=section(budget,"static Totals sharedAssignedActual(", "static Totals privateActual(")
assert "sharedMatchedActual(db,item,month)" in assigned
assert "sharedSplitSurplus(db,item,month)" in assigned
assert "for (Item item : all)" in assigned
assert "assignedGrosz>confirmedGrosz" in rules
print("EDHOME Budżet etap 4: integracja UI/finansów/powiadomień/załączników PASS")
