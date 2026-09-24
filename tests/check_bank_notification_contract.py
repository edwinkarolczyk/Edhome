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
receipt=(s/"BankReceiptNotifier.java").read_text(encoding="utf-8")

for phrase in ('selected_banking_packages','bank_notification_opt_in',
               'static void collect(', 'Never truncate' ,
               'putString(ROWS,result.toString())',
               'getStringSet(PACKAGES', 'selected(context).contains(source)'):
    assert phrase in store, phrase
assert 'PaycheckStore' not in store
assert 'PaycheckStore' not in listener
assert 'BankNotificationHints.alreadyHandled(context,unique)' not in listener
assert 'static boolean alreadyHandled(' in store
assert 'private static boolean rememberHandled(' in store
assert 'if(alreadyHandled(context,unique))return;' in store
assert 'PaycheckStore.add(db.getWritableDatabase(),' in ui
assert 'PrivatePaycheckVault.addPending(this,' in ui
assert 'pendingPrivateBankHintKey' in ui
assert 'private static String addWithStatus(' in Path(
    "app/src/main/java/com/edwinkarolczyk/edhome/PrivatePaycheckVault.java"
).read_text(encoding="utf-8")
assert 'PaycheckStore' not in receipt
for phrase in ('bank_receipt_notifications_enabled', 'receiptEnabled(',
               'setReceiptEnabled(', 'if(persist(context,entries))',
               'BankReceiptNotifier.show(context,saved)',
               '.putString(ROWS,result.toString()).commit()'):
    assert phrase in store, phrase
for phrase in ('Manifest.permission.POST_NOTIFICATIONS',
               'NotificationChannel', 'Notification.VISIBILITY_PRIVATE',
               'setPublicVersion(publicView)', 'manager.notify(',
               'BankNotificationHints.receiptEnabled(context)',
               'NotificationManager.IMPORTANCE_DEFAULT',
               'manager.areNotificationsEnabled()'):
    assert phrase in receipt, phrase
for secret in ('notificationText', 'EXTRA_TEXT', 'EXTRA_BIG_TEXT',
               'PaycheckStore.confirm('):
    assert secret not in receipt, secret
assert 'Powiadomienie EDHOME po odebraniu:' in ui
assert 'requestBankReceiptNotificationPermission()' in ui

# The listener must remain independent of a foreground activity and recover
# only still-visible notifications on Android service reconnection / opt-in.
for phrase in ('onListenerConnected()', 'getActiveNotifications()',
               'recheckActiveNotifications()', 'onNotificationPosted(sbn)'):
    assert phrase in listener, phrase
assert 'BankNotificationListener.recheckActiveNotifications()' in ui
assert 'transakcj[aeęąi]*\\\\s+kart' in rules

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
