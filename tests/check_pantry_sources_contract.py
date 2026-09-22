#!/usr/bin/env python3
"""Guard against silently skipping non-food catalogues or reporting failures as misses."""
from pathlib import Path
api = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryProductLookup.java").read_text()
codes = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryLookupCodes.java").read_text()
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
for token in (
    'world.openfoodfacts.org', 'world.openproductsfacts.org',
    'world.openbeautyfacts.org', 'world.openpetfoodfacts.org',
    'for (String[] catalogue : CATALOGUES)',
    'for (String candidate : alternatives)',
    'lookupOne(candidate, catalogue[0], label)',
    'if (found != null) return new Report(found,',
    'return new Report(null, joinResults(results), partialFailure)',
    'new URL(current, location)',
    'allowedHttps(current, image)',
    'Przekierowanie poza zaufane serwery Open Facts.',
    'code == 301 || code == 302 || code == 303',
    'code == 307 || code == 308',
    'if (!image && (code == 404 || code == 410)) return null;',
    'status = "rekord bez nazwy"',
    'status = "brak rekordu"',
    'status = "problem (" + lastError',
    'PantryLookupCodes.candidates(barcode)',
    'PANTRY_CATALOGUE_',
):
    assert token in api, "Missing source fallback or diagnostics: " + token
for token in (
    'static List<String> candidates(String raw)',
    'codes.add("0" + raw);',
    'codes.add("00" + raw);',
    'codes.add(raw.substring(1));',
    'codes.add(raw.substring(2));',
    'PantryScanRules.validBarcode(code)',
):
    assert token in codes, "Missing UPC/EAN/GTIN form: " + token
for token in (
    'PantryProductLookup.lookupDetailed(barcode,',
    'checked.partialFailure && checked.product == null',
    'checked.details',
    'Sprawdzone katalogi:',
    'checked.product, checked.details',
    'showNewPantryProductDialog(barcode, operationId, null)',
):
    assert token in main, "Missing on-screen source report: " + token
assert api.count('new URL(current, location)') == 1
assert api.index('if (!allowedHttps(current, image))') < api.index('current.openConnection()')
assert 'if (found != null) return found;' not in api
print("Source-by-source barcode search, safe HTTPS redirects and outcome reports: PASS")
