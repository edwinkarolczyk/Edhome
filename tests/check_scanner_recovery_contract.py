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

# Central EDHOME scanner must unify QR/NFC/product handling and keep destructive
# actions behind explicit confirmation.
for marker in (
    'case "scanner": scannerHub(); break;',
    'button("▣ Skanuj QR / kod kreskowy"',
    'NFC • Przyłóż tag do telefonu',
    'handleStorageTargetScan(storageTarget.kind, storageTarget.id, "qr")',
    'handleKnownNfcScan(current);',
    'showProductBarcodeActions(code);',
    'resolveScanAction(String source, String kind, long id)',
    'scanSpecificKey(source, kind, id)',
    'scanTypeKey(source, kind)',
    'SCAN_DEFAULT_GLOBAL',
    'Po zeskanowaniu domyślnie…',
    '.setTitle("Wyciągnąć −1?")',
    '.setPositiveButton("Wyciągnij"',
):
    assert marker in main, "Central scanner contract missing: " + marker
assert main.index('case "scanner": scannerHub(); break;') < main.index('case "audit": audit(); break;')
assert 'placeholder("Skaner"' not in main
for marker in (
    'showScannerUnknownNfc(uid);',
    'showStorageThingDetails(link.targetId);',
    'showScannerStorageContents(link.kind,link.targetId,name);',
    'Miejsce • zawartość',
    'Pudełko • zawartość',
    '📥 Włóż rzecz tutaj • QR / NFC',
    '📍 Zostaw tutaj rzecz lub pudełko • QR / NFC',
):
    assert marker in main, "Scanner gallery routing missing: " + marker

# Zły kod podczas oczekującej operacji Magazynu musi skasować stan
# operacji przed routingiem do produktu, aby kolejny QR nie dokończył starej akcji.
central=main.split("private void handleCentralScan(String raw)",1)[1].split(
    "private void showProductBarcodeActions(",1)[0]
assert 'if(pendingStorageDropKind!=null && pendingStorageDropId!=null)' in central
assert 'clearStorageDropTarget();' in central
assert 'if(pendingStorageDestinationItemId!=null)' in central
assert 'pendingStorageDestinationItemId=null;' in central
assert 'storageDestinationNfcDialog.dismiss();' in central
assert central.index('if(pendingStorageDropKind!=null && pendingStorageDropId!=null)') < central.index('PantryScanRules.validBarcode(code)')
assert central.index('if(pendingStorageDestinationItemId!=null)') < central.index('PantryScanRules.validBarcode(code)')

assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84 and "versionNameSuffix ''" in gradle
# Nieznany NFC -> nowy obiekt: jeśli przypisanie taga zawiedzie po utworzeniu,
# świeży obiekt musi zostać cofnięty, aby nie zostawić sieroty w Magazynie/Miejscach.
create_nfc=main.split("private void showScannerCreateNfcTarget(String uid,String kind)",1)[1].split(
    "private java.util.List<StorageStore.Item> scannerStorageThings(",1)[0]
assert 'if(id>0)' in create_nfc
assert 'NfcLinkStore.clearTarget(' in create_nfc
assert 'db.deletePlace(id);' in create_nfc
assert 'StorageStore.remove(db.getWritableDatabase(),id);' in create_nfc
assert 'NFC_SCANNER_TARGET_CREATE_ROLLED_BACK' in create_nfc
assert create_nfc.index('NfcLinkStore.bind(') < create_nfc.index('NFC_SCANNER_TARGET_CREATE_ROLLED_BACK')

print("Scanner recovery + central QR/NFC/product action routing: PASS")
