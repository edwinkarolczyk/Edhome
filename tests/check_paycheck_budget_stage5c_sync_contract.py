#!/usr/bin/env python3
"""5C: kontrakt zmian budżetu po UUID i bezpieczeństwo obu kierunków LAN."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
android=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DesktopHubSync.java").read_text(encoding="utf-8")
sync=(root/"app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
delta=(root/"desktop/src/main/java/com/edhome/desktop/DesktopBudgetDelta.java").read_text(encoding="utf-8")
integrity=(root/"desktop/src/main/java/com/edhome/desktop/DesktopBudgetIntegrity.java").read_text(encoding="utf-8")
history=(root/"desktop/src/main/java/com/edhome/desktop/DesktopBudgetHistoryArchive.java").read_text(encoding="utf-8")

assert "private static JSONObject budgetSettingsDelta(" in android
for key in ("paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"):
    assert key in android and key in delta, key
assert 'payload.put("budgetDelta",budgetDelta)' in android
assert 'operations.length()+budgetChanges>500' in android
assert 'if(!canonical(oldOther).equals(canonical(newOther)))return null;' in android
assert 'if("paycheckBudgetHistory".equals(field)&&next==null)continue;' in android
assert 'applyServerSnapshot(context,prefs,patched.body,patched.sha256,current)' in android
assert 'canonical(expected.opt(section))' in android
assert 'applyServerSnapshot(app,prefs,result.body,result.sha256,expectedLocal)' in android
assert 'saveConflictBackup(app,prefs)' in android

assert "DesktopBudgetDelta.apply(working,budgetDelta)" in desktop
assert "DesktopBudgetIntegrity.assertAllocations(working)" in desktop
assert "DesktopBudgetIntegrity.assertSidecars(working)" in desktop
assert 'long currentShare=Math.addExact(allocated,surplusGrosz);' in integrity
assert '||"paycheck_transactions".equals(table)' in desktop
assert 'table.startsWith("budget_")' in desktop
assert 'rows.get(rowIndex).equals(op.get("row"))' in desktop
assert "DesktopBudgetHistoryArchive.retain(snapshot,incomingRoot)" in desktop
assert "static int apply(JsonObject snapshot,JsonObject delta)" in delta
assert "throw conflict(name,id," in delta
assert 'while(merged.size()>6000' in delta
assert "long total=Math.addExact(" in integrity
assert "if(total>payment.amount)" in integrity
assert "newRows.add(oldRow.deepCopy());" in history
assert 'if("budget_history".equals(table)' in sync
print("EDHOME Budżet 5C: delta UUID, retry, limity alokacji, konflikt, backup i lokalny CAS — kontrakt PASS")
