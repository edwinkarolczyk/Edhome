#!/usr/bin/env python3
"""Barcode scanner failure recovery: no silent retry or duplicate stock mutation."""
from pathlib import Path

base=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(base/"MainActivity.java").read_text(encoding="utf-8")
store=(base/"PantryBarcodeStore.java").read_text(encoding="utf-8")
batch=(base/"PantryBatchSession.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
camera=main.split("private void openPantryCamera(boolean take) {",1)[1].split(
    "private void manualPantryBarcode()",1)[0]
scan=main.split("private void onPantryBarcode(String barcode, String mode, boolean repeatedApproved) {",1)[1].split(
    "private void choosePantryProductForCode(",1)[0]
commit=main.split("private void commitPantryBarcode(String barcode, String name,\n            String mode, String operationId, PantryProductLookup.Product found,\n            String newCategory, String unit, long sizeMilli, long selectedPantryId) {",1)[1].split(
    "private void showPantryScanHistory()",1)[0]
for marker in ("scanner.initiateScan();", 'abortPantryScan("PANTRY_CAMERA_LAUNCH"',
               "pantrySingleCameraPending = false;", "pantryBatch.stop();",
               "DiagnosticLog.error(event, problem);"):
    assert marker in camera, "Camera launch not recoverable: "+marker
assert camera.index("scanner.initiateScan();") < camera.index(
    'abortPantryScan("PANTRY_CAMERA_LAUNCH"')
for marker in ("PANTRY_SCAN_READ", "PANTRY_PACKAGE_READ", "Nie udało się odczytać produktu",
               "Nie udało się odczytać opakowania"):
    assert marker in scan, "Read failure not guarded: "+marker
assert scan.index("PantryBarcodeStore.find(db.getReadableDatabase(), barcode)") < scan.index(
    'abortPantryScan("PANTRY_SCAN_READ"')
assert 'if ("DUPLICATE_IGNORED".equals(result))' in commit
assert 'abortPantryScan("PANTRY_SCAN_DUPLICATE_IGNORED", null,' in commit
assert commit.index('if ("DUPLICATE_IGNORED".equals(result))') < commit.index(
    'DiagnosticLog.event("PANTRY_SCAN_COMMITTED");')
assert 'abortPantryScan("PANTRY_SCAN_COMMIT", problem, explanation);' in commit
assert "problem instanceof IllegalArgumentException" in commit
assert "finishPantryBatch();\n            DiagnosticLog.error(\"PANTRY_SCAN_COMMIT\"" not in commit
assert "operationExists(db, operationId)" in store
assert "db.beginTransaction();" in store and "db.setTransactionSuccessful();" in store
assert "db.endTransaction();" in store and "movement.put(\"operation_id\", operationId);" in store
assert "if (!active || cameraPending || awaitingConfirmation) return false;" in batch
assert "versionCode 64" in gradle and "versionNameSuffix '-beta.4'" in gradle
print("Scanner camera/read/commit failure recovery, duplicate and SQLite guards: PASS")
