#!/usr/bin/env python3
"""No private money in unauthenticated/shared ledger, backup, or diagnostic payloads."""
from pathlib import Path
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
crypto=(root/"PrivatePaycheckCrypto.java").read_text(encoding="utf-8")
vault=(root/"PrivatePaycheckVault.java").read_text(encoding="utf-8")
shared=(root/"PaycheckStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
for token in (
    '"PBKDF2WithHmacSHA256"',
    "ITERATIONS = 310000",
    '"AES/GCM/NoPadding"',
    'new GCMParameterSpec(128, nonce)',
    'cipher.updateAAD(AAD)',
    "password.length >= 12",
    "newSalt()",
):
    assert token in crypto, "Crypto guard missing: "+token
for token in (
    '"edhome_private_paycheck"',
    '"edhome-paycheck-private.db"',
    'sealed TEXT NOT NULL',
    'operation_id TEXT NOT NULL UNIQUE',
    'session.secret()',
    "Arrays.fill(key, (byte) 0)",
    'static long cooldownMillis(Context context)',
    'static void recordFailure(Context context)',
    'if (next >= 5)',
    'System.currentTimeMillis() + 300000L',
    'db.beginTransaction();',
    'db.setTransactionSuccessful();',
    'return "DUPLICATE";',
):
    assert token in vault, "Vault guard missing: "+token
for token in (
    'private PrivatePaycheckVault.Session privatePaycheckSession;',
    'private void openPrivatePaycheck()',
    'private void privatePaycheck()',
    'case "paycheck_private": privatePaycheck(); break;',
    'button("🔒 Prywatny sejf PayCheck", this::openPrivatePaycheck);',
    'if ("paycheck_private".equals(screen)) {',
    'root.removeAllViews();',
    'privatePaycheckSession.lock();',
    'getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)',
    'getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)',
    'privateNeedsRender = true;',
    'dialog.getWindow().addFlags(',
    'confirmationDialog.getWindow().addFlags(',
    'password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)',
    'confirmation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)',
    'privateEntryDialog = confirmationDialog;',
    'if (privateNeedsRender && root != null)',
    'PrivatePaycheckVault.configure(this, secret)',
    'PrivatePaycheckVault.unlock(this, secret)',
    'PrivatePaycheckVault.recordFailure(this)',
    'PayCheck • prywatny sejf',
    'PrivatePaycheckVault.entries(this, privatePaycheckSession)',
    'PrivatePaycheckVault.add(',
):
    assert token in ui, "Private UI guard missing: "+token
assert ui.index('if ("paycheck_private".equals(screen)) {') < ui.index('super.onPause();')
assert 'PaycheckStore.add(' not in ui.split('private void privatePaycheck()',1)[1].split('private void sharedPaycheckGoals()',1)[0]
assert '"edhome-paycheck-private.db"' not in backup
assert 'private_paycheck_entries' not in backup
assert 'edhome_private_paycheck' not in backup
assert "private_paycheck_entries" not in shared
assert "CHECK(scope='shared')" in shared
assert 'android:allowBackup="false"' in manifest
assert 'versionCode 68' in gradle and "versionNameSuffix ''" in gradle
assert 'super(context, "edhome-beta-preview.db", null, 26)' in ui
assert 'DB_VERSION = 26;' in backup
print("Private vault isolated, encrypted, locked on background and excluded from JSON: PASS")
