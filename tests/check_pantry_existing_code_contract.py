#!/usr/bin/env python3
"""Selected existing pantry item is linked by ID, never matched through remote name."""
from pathlib import Path
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
store=Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
for token in (
    '.setNeutralButton("Moje produkty"',
    'choosePantryProductForCode(barcode, operationId)',
    'SELECT id,name,qty FROM pantry ORDER BY name COLLATE NOCASE LIMIT 250',
    'confirmExistingPantryCode(barcode, operationId, ids.get(which))',
    'SELECT name,qty FROM pantry WHERE id=?',
    'Powiąż i dodaj +1',
    'null, pack.unit, pack.sizeMilli, pantryId)',
    'commitPantryBarcode(barcode, name, mode, operationId, found, newCategory,',
    'unit, sizeMilli, 0L);',
    'barcode, name, mode, operationId, unit, sizeMilli, selectedPantryId)',
):
    assert token in main, "Missing existing product UI or stock guard: "+token
for token in (
    'long selectedPantryId)',
    'if (selectedPantryId < 0)',
    'if (item != null && selectedPantryId > 0 && item.id != selectedPantryId)',
    '"Kod jest przypisany do innego produktu."',
    'if (selectedPantryId > 0) {',
    '"SELECT id FROM pantry WHERE id=? LIMIT 1"',
    'if (pantryId == 0)',
    '"Wybrany produkt już nie istnieje."',
    'PantryPackageStore.requireSame(db, pantryId, unit, sizeMilli);',
    'db.beginTransaction();',
    'if (operationExists(db, operationId))',
    'movement.put("operation_id", operationId);',
    'db.setTransactionSuccessful();',
):
    assert token in store, "Missing atomic ID-based assignment: "+token
assert 'versionCode 72' in gradle and "versionNameSuffix ''" in gradle
print("Existing product barcode assignment: explicit ID, atomic +1, safe failure: PASS")
