#!/usr/bin/env python3
"""Bank notification to the single current PayCheck, pending-ledger safety."""
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
for token in ('Dodaj do oczekujących PayCheck',
              'PaycheckStore.add(db.getWritableDatabase()',
              'BankNotificationHints.remove(this,signal.key)',
              'java.util.UUID.nameUUIDFromBytes('):
    assert token in ui,token
assert 'WSPÓLNY • dodaj do oczekujących' not in ui
assert 'PRYWATNY • otwórz sejf' not in ui
assert 'entries.subList(0,MAX)' not in hints
assert 'received>now-TTL' not in hints
assert 'visibleBankDrafts++>=40' in ui
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
for token in ('countRecentStatementMatches(signal)',
              'Możliwy duplikat',
              'To ta sama • zamknij sygnał',
              'To inna • utwórz wpis',
              'statement_key IS NOT NULL',
              'ChronoUnit.DAYS'):
    assert token in ui,token
assert 'getBooleanExtra("open_paycheck",false)' in ui
assert '.putExtra("open_paycheck",true)' in (
    root/"BankReceiptNotifier.java").read_text(encoding="utf-8")
print("Bank auto-pending: single PayCheck pending flow, replay guard and balances PASS")
