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
    'PaycheckBudgetSqliteStore.reconcileRaw(db,',
    'PaycheckBudgetSqliteStore.validateSerialized' if False else
        'PaycheckMonthlyBudget.validateSerialized(',
    'PaycheckRecipientStore.validateSerialized(',
    'PaycheckBudgetHistoryStore.validateSerialized(',
    'SyncRecordStore.ensureAll(db)',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
    'db.endTransaction();',
    'if(!editor.commit())',
    'verifySharedAllocations(db,updatedItems)',
    'Math.addExact(allocated,extra)',
    'if(total>available)',
    'SELECT operation_id,amount_grosz,kind',
):
    assert marker in merge, marker
assert 'DataBackup.restoreJson(db,prefs,merged.toString())' not in merge
assert 'SQLiteDatabase.create(null)' not in merge
assert 'original.getJSONObject("settings")' in merge
assert 'if(delta.has(field)&&!(delta.opt(field) instanceof JSONArray))' in merge
assert 'new SyncRecordStore.SyncConflict(' in merge
assert 'new JsonArray()' in out and 'output.add("budgetDelta",budget);' in out
assert 'return null' in out
assert 'DesktopBudgetOutboundDelta.build(baseline,current)' in client
assert 'SnapshotResult confirmed=client.snapshot();' in client
assert 'return new SyncWriteResult(confirmed.data,' in client
print("Etap 5C PC→Android: przyrostowy Budżet, transakcja tylko tabel finansowych, CAS UUID i bezpieczny ACK — kontrakt PASS")
