#!/usr/bin/env python3
"""0.5 take stability: local UPC/EAN aliases never silently change the stock key."""
from pathlib import Path
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
store=(root/"PantryBarcodeStore.java").read_text(encoding="utf-8")
camera=(root/"PantryTakeCaptureActivity.java").read_text(encoding="utf-8")
lookup=(root/"PantryLookupCodes.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text()
method=store.split("static TakeCode findTakeCode(SQLiteDatabase db, String scanned) {",1)[1].split(
    "private static boolean operationExists",1)[0]
for token in (
    "Item exact = find(db, scanned);",
    "if (exact != null) return new TakeCode(scanned, exact);",
    "PantryLookupCodes.candidates(scanned)",
    "if (alias.equals(scanned)) continue;",
    "Item linked = find(db, alias);",
    "resolved.item.id != linked.id",
    'throw new IllegalStateException("Sprzeczne powiązania kodów UPC/EAN.")',
    "new TakeCode(alias, linked)",
):
    assert token in method, "Missing local-safe candidate rule: "+token
assert method.index("Item exact = find(db, scanned);") < method.index(
    "PantryLookupCodes.candidates(scanned)")
for token in (
    "PantryBarcodeStore.findTakeCode(db, barcode)",
    "pendingStockBarcode = known.linkedBarcode;",
    "pendingPantryId = item.id;",
    "item.id == lastCommittedPantryId && !userApprovedRepeat",
    "cancelPending(\"PANTRY_TAKE_ALIAS_REPEAT_BLOCKED\")",
    "PANTRY_TAKE_LOCAL_ALIAS_MATCH",
    "boolean userApprovedRepeat = approvedRepeat;",
    "approvedRepeat = false;",
    "approvedRepeat = true;",
    "lastCommittedPantryId = pantryId;",
    "countdown.markCommitted(scannedBarcode);",
    'PantryBarcodeStore.commit(db, barcode, null, "TAKE",',
):
    assert token in camera, "Missing safe camera alias/consent path: "+token
assert camera.index("pendingStockBarcode = known.linkedBarcode;") < camera.index(
    'PantryBarcodeStore.commit(db, barcode, null, "TAKE",')
assert camera.index("countdown.markCommitted(scannedBarcode);") < camera.index(
    "lastCommittedPantryId = pantryId;")
assert 'if (resolved != null && resolved.item.id != linked.id)' in method
assert "PantryScanRules.validBarcode(code)" in lookup
assert "versionCode 74" in gradle and "versionNameSuffix ''" in gradle
print("TAKE local UPC/EAN aliases, conflict rejection, repeat consent by product: PASS")
