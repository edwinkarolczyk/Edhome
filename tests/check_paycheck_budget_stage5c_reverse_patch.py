#!/usr/bin/env python3
"""5C: kodowy kontrakt Desktop -> Android. Testy JUnit badają semantyk delt PC."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
android=(root/"app/src/main/java/com/edwinkarolczyk/edhome")
desktop=(root/"desktop/src/main/java/com/edhome/desktop")
service=(android/"LanSyncService.java").read_text(encoding="utf-8")
merge=(android/"PaycheckBudgetSyncPatch.java").read_text(encoding="utf-8")
out=(desktop/"DesktopBudgetOutboundDelta.java").read_text(encoding="utf-8")
client=(desktop/"EdhomeDesktop.java").read_text(encoding="utf-8")

for marker in (
    'PaycheckBudgetSyncPatch.apply(database,prefs,incoming)',
    'SyncRecordStore.applyPatch(database,incoming)',
):
    assert marker in service, marker

for marker in (
    '"edhome-record-patch"',
    'patch.getJSONArray("operations").length()!=0',
    'if(!same(current,desired))',
    'new SyncRecordStore.SyncConflict(',
    '"paycheckBudgetHistory".equals(field)',
    'PaycheckBudgetSqliteStore.reconcileRaw(temp,',
    'PaycheckBudgetSqliteStore.validateSerialized' if False else
        'PaycheckMonthlyBudget.validateSerialized(',
    'PaycheckRecipientStore.validateSerialized(',
    'PaycheckBudgetHistoryStore.validateSerialized(',
    'DataBackup.restoreJson(db,prefs,merged.toString())',
    'm.put("deletedAt",now)',
):
    assert marker in merge, marker
assert 'new JsonArray()' in out and 'output.add("budgetDelta",budget);' in out
assert 'return null' in out
assert 'DesktopBudgetOutboundDelta.build(baseline,current)' in client
assert 'SnapshotResult confirmed=client.snapshot();' in client
assert 'return new SyncWriteResult(confirmed.data,' in client
print("Etap 5C PC→Android: przyrostowy Budżet, CAS UUID, cache i archiwum, bezpieczny ACK — kontrakt PASS")
