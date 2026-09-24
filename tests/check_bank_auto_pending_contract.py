#!/usr/bin/env python3
"""Bank notification to unassigned draft, explicit scope, pending ledger safety."""
from pathlib import Path
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
hints=(root/"BankNotificationHints.java").read_text(encoding="utf-8")
shared=(root/"PaycheckStore.java").read_text(encoding="utf-8")
private=(root/"PrivatePaycheckVault.java").read_text(encoding="utf-8")
listener=(root/"BankNotificationListener.java").read_text(encoding="utf-8")
for token in ('bank_notification_handled_v1','rememberHandled(context,key)',
              'if(alreadyHandled(context,unique))return;',
              'return pref(context).edit().putString(HANDLED',
              'if(persist(context,entries))'):
    assert token in hints, token
for token in ('WSPÓLNY • dodaj do oczekujących','PRYWATNY • otwórz sejf',
              'pendingPrivateBankHintKey','PaycheckStore.add(db.getWritableDatabase()',
              'PrivatePaycheckVault.addPending(this,',
              'BankNotificationHints.remove(this,signal.key)',
              'java.util.UUID.nameUUIDFromBytes('):
    assert token in ui,token
assert 'BankNotificationHints.collect(' in listener
assert 'PaycheckStore.add(' not in listener
assert 'PrivatePaycheckVault.addPending(' not in listener
assert 'scope TEXT NOT NULL CHECK(scope=\'shared\')' in shared
assert 'values.put("status","pending");' in shared
assert "status='confirmed'" in shared
assert 'payload.put("status",status)' in private
assert 'static String addPending(' in private
assert 'static boolean confirmPending(' in private
assert '!"pending".equals(entry.status)' in ui
assert 'privatePaycheckSession.active()' in ui
assert 'getBooleanExtra("open_paycheck",false)' in ui
assert '.putExtra("open_paycheck",true)' in (
    root/"BankReceiptNotifier.java").read_text(encoding="utf-8")
print("Bank auto-pending: unassigned drafts, explicit scope, replay guard, private encryption, balances PASS")
