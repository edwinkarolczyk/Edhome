#!/usr/bin/env python3
"""Opt-in OFF integration, offline fallback and inventory/backup contract."""
from pathlib import Path
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
api = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryProductLookup.java").read_text()
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryBarcodeStore.java").read_text()
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
gradle = Path("app/build.gradle").read_text()
required = {
    "offline existing": "PantryBarcodeStore.find(",
    "opt-in database button": '.setPositiveButton("Open Food Facts"',
    "offline manual button": '.setNeutralButton("Wpisz ręcznie"',
    "no inventory mutation on lookup": "showNewPantryProductDialog(barcode, operationId, found);",
    "confirmation before inventory update": '.setPositiveButton("Dodaj +1", (d,w) -> {',
    "transactional scanner reuse": 'PantryBarcodeStore.commit(db.getWritableDatabase()',
    "metadata insert": "PantryBarcodeStore.saveDetails(",
    "migration 14 to 15": "DATABASE_MIGRATED_14_TO_15_PANTRY_DETAILS",
    "manual fallback on connection failure": "Brak połączenia z bazą",
    "photo user refresh": "refreshPantryPhoto(details.imageUrl)",
}
for label, token in required.items():
    assert token in main, f"Missing {label}"
assert main.index("PantryBarcodeStore.find(") < main.index('setPositiveButton("Open Food Facts"')
for token in (
    "https://world.openfoodfacts.org/api/v2/product/",
    "PantryScanRules.validBarcode(barcode)",
    'IMAGE_HOST = "images.openfoodfacts.org"',
    "setInstanceFollowRedirects(false)",
    "setConnectTimeout(5000)",
    "setReadTimeout(6000)",
    "MAX_JSON_BYTES = 96 * 1024",
    "MAX_IMAGE_BYTES = 640 * 1024",
    "getFilesDir()",
    "MessageDigest.getInstance(\"SHA-256\")",
):
    assert token in api, "Unsafe or missing network/cache condition: " + token
for token in ("CREATE TABLE pantry_product_details", "pantry_id INTEGER NOT NULL UNIQUE",
              "safeImageUrl(imageUrl)", "saveDetails(SQLiteDatabase"):
    assert token in store, "Missing metadata contract: " + token
for token in ('{"pantry_product_details", "id", "pantry_id", "brand", "image_url"}',
              'inputVersion < 15 && "pantry_product_details".equals(definition[0])',
              "Nieprawidłowe powiązanie zdjęcia.",
              "Nieprawidłowe dane zdjęcia w kopii.",
              "DB_VERSION = 15;"):
    assert token in backup, "Missing backup contract: " + token
assert "versionCode 33" in gradle and "versionNameSuffix '-beta.3'" in gradle
print("Opt-in Open Food Facts, offline fallback, v15 metadata and backup: PASS")
