"""Etap 2: wymagane połączenia logiki faktur z ekranem i trwałymi danymi."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / "app/src/main/java/com/edwinkarolczyk/edhome"
budget = (java / "PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
main = (java / "MainActivity.java").read_text(encoding="utf-8")
attachments = (java / "PaycheckBudgetAttachmentStore.java").read_text(encoding="utf-8")
dates = (java / "PaycheckBudgetInvoiceDates.java").read_text(encoding="utf-8")

assert "item.dueDay = PaycheckBudgetInvoiceDates.templateDay(dueDay);" in budget
assert 'json.put("templateDueDay", item.dueDay);' in budget
assert 'json.optInt("templateDueDay",' in budget
assert "migrateLegacyDay(" in budget
assert "PaycheckBudgetInvoiceDates.planned(" in budget
assert "item.dueDay <= 0) return null" not in budget

upsert = budget.split("static void updateInvoiceForMonth(", 1)[1].split(
    "static LocalDate invoiceDueDate(", 1
)[0]
assert 'YearMonth.from(dueDate)' in upsert
assert 'target.monthAmountOverrides.put(month.toString(),amountGrosz);' in upsert
assert "setInvoiceForMonth(target,dueDate,plannedDate);" in upsert
assert "save(prefs,items);" in upsert
assert 'INVOICE_ADDED' in upsert and 'INVOICE_UPDATED' in upsert

dialog = main.split("private void showBudgetInvoiceDialog(", 1)[1].split(
    "private void pickBudgetAttachment(", 1
)[0]
assert "PaycheckMonthlyBudget.updateInvoiceForMonth(" in dialog
assert "PaycheckBudgetReminderReceiver.schedule(this);" in dialog
assert "pickBudgetAttachment(item,month)" in dialog
assert "Zapisz + dokument" in dialog

creation = main.split("private void showBudgetItemDialog(", 1)[1].split(
    "private void showBudgetItemsDialog(", 1
)[0]
assert "int day = 10;" in creation
assert "day = defaultPlan.getDayOfMonth();" not in creation
assert "PaycheckMonthlyBudget.setInvoiceForMonth(" in creation

assert "PaycheckBudgetAttachmentStore.list(prefs,item.id,month)" in main
assert "YearMonth invoiceMonth=pendingBudgetAttachmentMonth" in main
assert "PaycheckBudgetAttachmentStore.add(" in main
assert 'json.optString("month","")' in attachments
assert 'json.put("month",attachment.month);' in attachments
assert 'month.equals(attachment.month)' in attachments
assert "Math.min(templateDay(selectedDay),month.lengthOfMonth())" in dates
print("Budżet etap 2: integracja terminu, faktury miesięcznej i załącznika PASS")
