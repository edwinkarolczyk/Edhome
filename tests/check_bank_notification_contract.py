#!/usr/bin/env python3
"""Bank notification hints require explicit user opt-in and never post to ledger."""
from pathlib import Path
s=Path("app/src/main/java/com/edwinkarolczyk/edhome")
ui=(s/"MainActivity.java").read_text(encoding="utf-8")
listener=(s/"BankNotificationListener.java").read_text(encoding="utf-8")
store=(s/"BankNotificationHints.java").read_text(encoding="utf-8")
rules=(s/"BankNotificationRules.java").read_text(encoding="utf-8")
beta=Path("app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
stable=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for phrase in ('selected_banking_packages','bank_notification_opt_in',
               'static void collect(', 'MAX=40', 'TTL=14L',
               'putString(ROWS,result.toString())',
               'getStringSet(PACKAGES', 'selected(context).contains(source)'):
    assert phrase in store, phrase
assert 'PaycheckStore' not in store
assert 'PaycheckStore' not in listener
assert 'getString("text"' not in store
for phrase in ('!BankNotificationHints.enabled(this)',
               'BankNotificationHints.selected(this)',
               'Notification.EXTRA_BIG_TEXT',
               'Notification.EXTRA_TEXT'):
    assert phrase in listener, phrase
for phrase in ('android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
               '.BankNotificationListener',
               'android.service.notification.NotificationListenerService'):
    assert phrase in beta, phrase
assert 'BankNotificationListener' not in stable
for phrase in ('bankNotificationPermissionGranted()',
               'Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS',
               'choices.addView(choice)',
               'showBankNotificationHints()',
               'confirmSharedPaycheckEntry(ids.get(0),signal.key)',
               'BankNotificationHints.remove(this,hintKey)'):
    assert phrase in ui, phrase
dialog=ui[ui.index("    private void configureBankNotifications()"):ui.index("    private void showBankNotificationHints()")]
assert '.setView(form)' in dialog
assert 'choices.addView(choice)' in dialog
assert 'Intent.ACTION_PICK_ACTIVITY' in dialog
assert '.setMultiChoiceItems(names,selected' not in dialog

assert 'PaycheckStore.confirm(' in ui
assert 'PaycheckStore.confirm(' not in listener
assert 'PaycheckStore.matchStatement(' not in listener
assert 'MoneyRules.parse(' in rules
print("Bank notifications: Beta opt-in/selected packages, metadata-only hints and manual ledger PASS")
