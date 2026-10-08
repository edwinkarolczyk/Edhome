#!/usr/bin/env python3
"""Etap 6: kontrakt jednego wspólnego Budżetu i ergonomii widoku PC."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"desktop/src/main/java/com/edhome/desktop/DesktopBudgetMirror.java").read_text(encoding="utf-8")
tests=(root/"desktop/src/test/java/com/edhome/desktop/DesktopBudgetMirrorTest.java").read_text(encoding="utf-8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

for piece in (
    'String monthLabel=current.format(',
    '"LLLL yyyy",Locale.forLanguageTag("pl-PL")',
    '"Bieżący miesiąc","Odbiorcy"',
    'recipientOverview(items,recipients,current)',
    'boolean editable=sharedSnapshot!=null&&onSave!=null;',
    'DesktopSharedBudgetEdits.changeMonth(',
    'List<Object[]> incoming=new ArrayList<>();',
    'List<Object[]> outgoing=new ArrayList<>();',
    'incoming.sort(byDate);',
    'outgoing.sort(byDate);',
    'for(Object[] entry:incoming)table.addRow(entry);',
    'for(Object[] entry:outgoing)table.addRow(entry);',
    'static DefaultTableModel recipientOverview(',
    'directory.sort(Comparator.comparing(',
    'obligations++;',
    '"expense".equals(str(item,"kind","expense"))',
):
    assert piece in ui,piece
for piece in (
    'recipientsShowOnlyRealDirectoryWithTheirActiveBills',
    'incomeFirstAndExpensesSortedRegardlessOfInputOrder',
    'duplicateRecipientsAreNotSilentlyMerged',
    'recurringInvoiceAndOneTimeItemUseSameMonthsAsAndroid',
    'calendarCorrectionsAndTerminatedDefinitionRemainHistorical',
):
    assert piece in tests,piece
assert 'DesktopBudgetMirror.showEditable(' in desktop
assert 'paycheck-budget-plan.json' in desktop
print("Etap 6 Desktop: jeden nagłówek, wpłaty nad wydatkami, terminy, odbiorcy i edycja bez drugiej księgi — kontrakt PASS")
