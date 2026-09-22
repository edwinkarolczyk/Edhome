#!/usr/bin/env python3
"""Package units remain separate from inventory packages and scan operations."""
from pathlib import Path
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryPackageStore.java").read_text()
scan = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text()
gradle = Path("app/build.gradle").read_text()
for value in (
    '"DATABASE_MIGRATED_16_TO_17_PANTRY_PACKAGES"',
    'super(context, "edhome-beta-preview.db", null, 22)',
    'PantryPackageStore.fillLegacy(database)',
    'PantryPackageStore.create(database)',
    'PantryPackageRules.summary(',
    'new PantryPackageStore.Pack("szt.", 1000)',
    'PantryPackageRules.parse(packSize.getText().toString(), unit)',
    'db.addStock(name, categoryId, unit, milli)',
    'db.editStock(id, name, categoryId, unit, milli)',
    'PantryPackageStore.Pack pack = PantryPackageStore.find(',
    'db.getReadableDatabase(), item.id)',
    'PantryPackageStore.requireSame(database, existing, unit, milli)',
    'database.delete("pantry_packages", "pantry_id=?"',
):
    assert value in main, "Missing Android unit or migration contract: " + value
for value in (
    'pantry.qty counts PACKAGES',
    "CREATE TABLE pantry_packages",
    "CHECK(unit!='szt.' OR size_milli%1000=0)",
    "SELECT id,'szt.',1000 FROM pantry",
    'size_milli BETWEEN 1 AND 1000000000',
):
    assert value in store, "Missing package-store contract: " + value
for value in (
    'PantryPackageStore.set(db, pantryId, unit, sizeMilli)',
    'PantryPackageStore.requireSame(db, pantryId, unit, sizeMilli)',
    'db.beginTransaction();',
    'movement.put("qty", 1);',
):
    assert value in scan, "Missing atomic barcode/package contract: " + value
for value in (
    'DB_VERSION = 22;',
    '{"pantry_packages", "pantry_id", "unit", "size_milli"}',
    'inputVersion < 17 && "pantry_packages".equals(definition[0])',
    '"size_milli".equals(column)',
    'PantryPackageRules.valid(',
    'PantryPackageStore.fillLegacy(database)',
    'packagedProducts.size() != pantryIds.size()',
):
    assert value in backup, "Missing backup contract: " + value
assert 'versionCode 59' in gradle and "versionNameSuffix '-beta.12'" in gradle
print("Pantry packages UI, stock isolation, backup v17 and scanner: PASS")
