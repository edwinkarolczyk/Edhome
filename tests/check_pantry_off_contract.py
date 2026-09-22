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
    "opt-in database button": '.setPositiveButton("Szukaj produktu"',
    "offline manual fallback": '.setPositiveButton("Wpisz ręcznie"',
    "no inventory mutation on lookup": "checked.product, checked.details);",
    "confirmation before inventory update": '.setPositiveButton("Dodaj +1", (d,w) -> {',
    "transactional scanner reuse": 'PantryBarcodeStore.commit(db.getWritableDatabase()',
    "metadata insert": "PantryBarcodeStore.saveDetails(",
    "migration 14 to 15": "DATABASE_MIGRATED_14_TO_15_PANTRY_DETAILS",
    "manual fallback on connection failure": "Nie wszystkie bazy odpowiedziały",
    "photo user refresh": "refreshPantryPhoto(details.imageUrl)",
}
for label, token in required.items():
    assert token in main, f"Missing {label}"
assert main.index("PantryBarcodeStore.find(") < main.index('setPositiveButton("Szukaj produktu"')
for token in (
    '"world.openfoodfacts.org"',
    '"world.openproductsfacts.org"',
    '"world.openbeautyfacts.org"',
    '"world.openpetfoodfacts.org"',
    '"Open Products Facts"',
    '"Open Beauty Facts"',
    '"Open Pet Food Facts"',
    '"https://" + host + "/api/v2/product/"',
    "PantryLookupCodes.candidates(barcode)",
    '"images.openfoodfacts.org"',
    '"images.openproductsfacts.org"',
    '"images.openbeautyfacts.org"',
    '"images.openpetfoodfacts.org"',
    "setInstanceFollowRedirects(false)",
    "setConnectTimeout(4000)",
    "setReadTimeout(5000)",
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
              "DB_VERSION = 22;"):
    assert token in backup, "Missing backup contract: " + token
assert "versionCode 53" in gradle and "versionNameSuffix '-beta.9.1'" in gradle
assert "if (found == null) {\n            name = new EditText(this);" in main
assert "String entered = found != null ? found.name" in main
assert "Źródło: \" + found.source" in main
assert "lookupOne(candidate, catalogue[0], label)" in api
assert "if (!image && (code == 404 || code == 410)) return null;" in api
assert "PantryLookupCodes.candidates(barcode)" in api
assert "lookupDetailed(barcode," in main
assert "Sprawdzone katalogi:" in main
assert "Przekierowanie poza zaufane serwery Open Facts." in api
print("Multi-catalogue Open Facts, UPC aliases, redirects, diagnostic report: PASS")
