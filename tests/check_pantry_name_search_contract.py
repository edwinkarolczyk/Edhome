#!/usr/bin/env python3
"""Name search must stay opt-in, informational and stock-neutral."""
from pathlib import Path
api=Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryProductLookup.java").read_text(encoding="utf-8")
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
for token in (
    'static NameSearchReport searchByName(String rawQuery, Progress progress)',
    'query.length() < 3 || query.length() > 80',
    'URLEncoder.encode(query, StandardCharsets.UTF_8.name())',
    '"/api/v2/search?search_terms=" + encoded',
    '"&page_size=6&fields=code,product_name_pl,product_name,"',
    'for (String[] catalogue : CATALOGUES)',
    'new Product(name, label, brand, imageUrl, null)',
    'return new NameSearchReport(results, joinResults(statuses), partialFailure);',
):
    assert token in api, "missing name search: "+token
for token in (
    'setNeutralButton("Szukaj po nazwie"',
    'promptPantryNameSearch(barcode, operationId)',
    'PantryProductLookup.searchByName(query,',
    'new AlertDialog.Builder(this).setTitle("Wybierz zgodny produkt")',
    'checked.products.get(which), checked.details',
    'showNewPantryProductDialog(scannedBarcode, operationId,',
    'Dopasowano po nazwie;',
    'showNewPantryProductDialog(scannedBarcode, operationId, null)',
    'Nie znaleziono propozycji.',
    'PantryProductLookup.fetchImage(candidate.imageUrl)',
    'PANTRY_NAME_SEARCH_PHOTO_UNAVAILABLE',
):
    assert token in main, "missing UI flow: "+token
# The remote-search helpers must never mutate pantry or stock.
section=api.split("static NameSearchReport searchByName(",1)[1].split("private static String readProductName",1)[0]
for forbidden in ("SQLiteDatabase", "insertOrThrow(", "PantryBarcodeStore.commit(", "database.update("):
    assert forbidden not in section, "remote search modifies stock: "+forbidden
assert "versionCode 46" in gradle and "versionNameSuffix '-beta.3'" in gradle
print("Name search: opt-in, four sources, selection and stock-neutral confirmation: PASS")
