#!/usr/bin/env python3
"""Shopping destination: per-row optional place, confirmed receipt snapshot, v23 backup."""
from pathlib import Path
import sqlite3
import re, json
root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
receipt=(root/"ShoppingReceiptStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
cost=(root/"ShoppingCostRules.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
for phrase in (
    'checked INTEGER NOT NULL DEFAULT 0, place_id INTEGER',
    'ALTER TABLE shopping_items ADD COLUMN place_id INTEGER',
    'ALTER TABLE shopping_receipts ADD COLUMN place_id INTEGER',
    'ADD COLUMN place_name_snapshot TEXT NOT NULL DEFAULT',
    "DATABASE_MIGRATED_22_TO_23_SHOPPING_PLACES",
    'SELECT id,name,qty_milli,unit,checked,place_id FROM shopping_items',
    'destinationNames.add("Wybiorę miejsce przy przyjęciu")',
    'placeIds.get(placeChoice.getSelectedItemPosition())',
    'shoppingId, pantryId, packages,',
    'UPDATE shopping_items SET place_id=NULL WHERE place_id=?',
    'UPDATE shopping_receipts SET place_id=NULL WHERE place_id=?',
    'SELECT place_name_snapshot FROM shopping_receipts',
    'ShoppingCostRules.totalGrosz(',
    'Cena 1 ', 'łącznie: ', 'getDatabasePath("edhome-beta-preview.db")',
):
    if phrase == 'getDatabasePath("edhome-beta-preview.db")':
        continue
    assert phrase in main, phrase
for phrase in (
    'place_id INTEGER', "place_name_snapshot TEXT NOT NULL DEFAULT",
    'if (placeId != null && placeId <= 0)',
    'if (!place.moveToFirst()) return "MISSING_PLACE";',
    'record.put("place_name_snapshot", placeName);',
    'PantryPriceHistoryStore.linkReceived(db, shoppingId, pantryId);',
    'db.setTransactionSuccessful();',
):
    assert phrase in receipt, phrase
assert 'pricePerUnitGrosz' in cost and 'quantityMilli == null) return null;' in cost
assert 'BigDecimal.valueOf(quantityMilli, 3)' in cost
assert 'RoundingMode.HALF_UP' in cost
assert 'private static final int DB_VERSION = 30;' in backup
assert '"shopping_items", "id", "name", "qty_milli", "unit", "checked",' in backup
assert '"packages", "before_qty", "after_qty", "happened_at",' in backup
assert 'if (inputVersion < 23' in backup
assert 'inputVersion != 22 && inputVersion != 23 && inputVersion != 24 && inputVersion != 25 && inputVersion != 26 && inputVersion != 27 && inputVersion != 28 && inputVersion != DB_VERSION' in backup
assert 'Zakup ma nieistniejące miejsce.' in backup
assert 'Przyjęcie ma nieistniejące miejsce.' in backup
assert "versionCode 72" in gradle and "versionNameSuffix ''" in gradle
print("Per-row shopping destination, receipt-only stock, v23 backup: PASS")
