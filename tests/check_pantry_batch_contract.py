#!/usr/bin/env python3
"""EDHOME batch scanning: one explicit stock action per scan, no silent relaunch."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
state = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBatchSession.java").read_text(encoding="utf-8")
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
checks = [
    'button("📷 Skanuj serię — dodawaj +1"',
    'button("📷 Skanuj serię — wyciągaj −1"',
    'this::finishPantryBatch',
    'pantryBatch.start(mode)',
    'pantryBatch.launchCamera()',
    'pantryBatch.receiveScan(scan.getContents())',
    'pantryBatch.repeated(barcode)',
    'if (pantryBatch.skip()) continuePantryBatch();',
    'onPantryBarcode(barcode, mode, true)',
    'pantryBatch.commit(barcode)',
    'DiagnosticLog.event("PANTRY_BATCH_ITEM_COMMITTED")',
    'DiagnosticLog.event("PANTRY_BATCH_FINISHED")',
    'DiagnosticLog.event("PANTRY_BATCH_DUPLICATE_RESULT_IGNORED")',
    'DiagnosticLog.event("PANTRY_UNEXPECTED_CAMERA_RESULT_IGNORED")',
    'if (scan.getContents() == null) finishPantryBatch();',
    'if ("COMMITTED".equals(result) && pantryBatch.commit(barcode))',
    'continuePantryBatch();',
]
for check in checks:
    assert check in main, "Missing batch UI/safety hook: " + check
assert 'private final PantryBatchSession pantryBatch' in main
assert 'private boolean pantrySingleCameraPending;' in main
assert main.index('pantryBatch.receiveScan(scan.getContents())') < main.index(
    'onPantryBarcode(scan.getContents(), pantryBatch.mode())'), "Result must be claimed once"
assert main.index('if ("COMMITTED".equals(result) && pantryBatch.commit(barcode))') < main.index(
    'continuePantryBatch();', main.index('if ("COMMITTED".equals(result) && pantryBatch.commit(barcode))')
), "Camera must resume only after a committed operation"
assert 'int saved = pantryBatch.stop();' in main
assert 'one explicit confirmation' in state
assert 'if (!active || cameraPending || awaitingConfirmation) return false;' in state
assert 'if (!active || !cameraPending) return false;' in state
assert 'if (!active || !awaitingConfirmation || barcode == null) return false;' in state
assert 'operationExists(db, operationId)' in store
assert 'db.beginTransaction();' in store and 'movement.put("qty", 1);' in store
assert "versionCode 83" in gradle and "versionNameSuffix ''" in gradle
print("Batch scan UI, double-result guard, explicit confirmation, idempotence: PASS")
