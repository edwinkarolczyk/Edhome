#!/usr/bin/env python3
"""Pantry categories: filtering, manual choice, sourced choice and backup contract."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text()
categories = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryCategories.java").read_text()
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/PantryCategoriesStore.java").read_text()
for token in (
    'private String pantryCategoryFilter = "";',
    '"SELECT id,name,qty,category FROM pantry ORDER BY name COLLATE NOCASE"',
    'pantryCategoryFilter.equals(category)',
    'PantryCategories.label(category)',
    'pantryCategorySpinner(oldCategory)',
    'pantryCategorySpinner(',
    'found == null ? "other" : PantryCategories.fromSource(found.source)',
    'PantryCategoriesStore.setIfOther(',
    'db.addStock(name, categoryId)',
    'PantryCategoriesStore.set(db.getWritableDatabase(), id, categoryId)',
    '"DATABASE_MIGRATED_15_TO_16_PANTRY_CATEGORIES"',
    'ALTER TABLE pantry ADD COLUMN category TEXT',
    'super(context, "edhome-beta-preview.db", null, 17)',
):
    assert token in main, "Missing main contract: " + token
for token in (
    'DB_VERSION = 17;',
    '{"pantry", "id", "name", "qty", "category"}',
    'inputVersion < 16 && "pantry".equals(definition[0])',
    'values.put(key, "other");',
    'PantryCategories.known(values.getAsString("category"))',
):
    assert token in backup, "Missing backup contract: " + token
for token in (
    'if ("Open Food Facts".equals(source)) return "food";',
    'if ("Open Beauty Facts".equals(source)) return "beauty";',
    'if ("Open Pet Food Facts".equals(source)) return "pet";',
    'return "other";',
):
    assert token in categories, "Missing source mapping: " + token
assert 'category=\'other\'' in store
print("Pantry categories UI, backup v17, and classification: PASS")
