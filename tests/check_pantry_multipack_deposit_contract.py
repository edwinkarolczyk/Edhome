#!/usr/bin/env python3
"""Pantry v39: multipack scans, catalogue quantity inference and deposits."""
from pathlib import Path
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
lookup=(root/"PantryProductLookup.java").read_text(encoding="utf-8")
scan=(root/"PantryBarcodeStore.java").read_text(encoding="utf-8")
pack=(root/"PantryPackageStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
take=(root/"PantryTakeCaptureActivity.java").read_text(encoding="utf-8")

for token in (
    "quantity,",
    "product_quantity,product_quantity_unit",
    "readPackSuggestion(row)",
    "unitsPerScan",
    "baseSizeMilli",
):
    assert token in lookup, token

for token in (
    "units_per_scan INTEGER NOT NULL DEFAULT 1",
    'movement.put("qty", quantity);',
    'after = item.qty + ("ADD".equals(mode) ? quantity : -quantity)',
    "PantryPackageStore.addPendingDeposit(db, item.id, quantity)",
):
    assert token in scan, token

for token in (
    "deposit_grosz INTEGER NOT NULL DEFAULT 0",
    "deposit_pending INTEGER NOT NULL DEFAULT 0",
    "totalDepositGrosz",
    "returnDeposit",
    "stockDepositGrosz",
    "allDepositGrosz",
):
    assert token in pack, token

for token in (
    "DATABASE_MIGRATED_38_TO_39_PANTRY_PACKAGING",
    "Skanuj serię — dodawaj wg opakowania",
    "Ile sztuk / opakowań podstawowych",
    "Kaucja / opakowania zwrotne",
    "showPantryDepositSummary",
):
    assert token in main, token

assert "pendingUnits = PantryBarcodeStore.unitsPerScan" in take
assert "long selectedPantryId, int quantity, int unitsPerScan" in scan
assert "DB_VERSION = 43;" in backup
assert '"units_per_scan"' in backup
assert '"deposit_grosz", "deposit_pending"' in backup
print("Pantry multipack + deposit v39 contract: PASS")