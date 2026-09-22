#!/usr/bin/env python3
"""Private finance archive: encrypted-only export, authenticated import, no shared backup."""
from pathlib import Path
root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
archive = (root / "PrivatePaycheckPortable.java").read_text(encoding="utf-8")
ui = (root / "MainActivity.java").read_text(encoding="utf-8")
shared = (root / "DataBackup.java").read_text(encoding="utf-8")
crypto = (root / "PrivatePaycheckCrypto.java").read_text(encoding="utf-8")
for clause in (
    'EDHOME_PRIVATE_PAYCHECK_ENCRYPTED_V1',
    'PrivatePaycheckCrypto.newSalt()',
    'PrivatePaycheckCrypto.key(backupPassword, salt)',
    'PrivatePaycheckCrypto.seal(key, plain.toString())',
    'PrivatePaycheckCrypto.unseal(key, archive.getString("encrypted"))',
    'PrivatePaycheckCrypto.seal(\n                        session.secret(), entry.toString())',
    'database.beginTransaction()',
    'database.setTransactionSuccessful()',
    'database.endTransaction()',
    'Conflicting private operation ID',
    'new HashSet<>()',
    'MAX_BYTES = 8 * 1024 * 1024',
    'Arrays.fill(salt, (byte) 0)',
    'Arrays.fill(key, (byte) 0)',
    'session.secret()',
):
    assert clause in archive, f"Missing private archive invariant: {clause}"
for clause in (
    'EXPORT_PRIVATE_BACKUP = 1214',
    'IMPORT_PRIVATE_BACKUP = 1215',
    'privateBackupForSave',
    'PrivatePaycheckPortable.exportEncrypted(',
    'PrivatePaycheckPortable.importEncrypted(',
    'ACTION_CREATE_DOCUMENT',
    'ACTION_OPEN_DOCUMENT',
    'privateBackupImportDialog(',
    'PAYCHECK_PRIVATE_BACKUP_EXPORTED',
    'PAYCHECK_PRIVATE_BACKUP_IMPORTED',
    'FLAG_SECURE',
):
    assert clause in ui, f"Missing private backup UI guard: {clause}"
assert 'private_paycheck_entries' not in shared
assert 'edhome-paycheck-private.db' not in shared
assert 'edhome_private_paycheck' not in shared
assert 'AES/GCM/NoPadding' in crypto
print("Encrypted private PayCheck archive, atomic deduplicated import and isolation: PASS")
