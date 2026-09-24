#!/usr/bin/env python3
"""Optional shopping price is a history event, never an inventory or bank transaction."""
import json
import re
import sqlite3
from pathlib import Path

base = Path("app/src/main/java/com/edwinkarolczyk/edhome")
source = (base / "PantryPriceHistoryStore.java").read_text(encoding="utf-8")
main = (base / "MainActivity.java").read_text(encoding="utf-8")
receipt = (base / "ShoppingReceiptStore.java").read_text(encoding="utf-8")
backup = (base / "DataBackup.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
part = source.split("static void create(SQLiteDatabase db)", 1)[1].split("static String markBought(", 1)[0]
sql = []
for expr in re.findall(r"db\.execSQL\((.*?)\);", part, flags=re.S):
    sql.append("".join(json.loads(v) for v in re.findall(r'"(?:\\.|[^"\\])*"', expr)))
assert len(sql) == 2 and "CREATE TABLE pantry_purchase_prices" in sql[0]
database = sqlite3.connect(":memory:")
for statement in sql:
    database.execute(statement)
database.execute(
    "INSERT INTO pantry_purchase_prices "
    "(operation_id,name_snapshot,unit,unit_price_grosz,shop,happened_at) "
    "VALUES('uuid-1','Ryż','szt.',649,'Sklep',1789980000000)")
assert database.execute(
    "SELECT unit_price_grosz,shop FROM pantry_purchase_prices").fetchone() == (649,"Sklep")
try:
    database.execute("INSERT INTO pantry_purchase_prices "
        "(operation_id,name_snapshot,unit,unit_price_grosz,happened_at) "
        "VALUES('uuid-2','Ryż','szt.',0,1789980000000)")
    raise AssertionError("zero is not unknown")
except sqlite3.IntegrityError:
    pass
try:
    database.execute("INSERT INTO pantry_purchase_prices "
        "(operation_id,name_snapshot,unit,unit_price_grosz,happened_at) "
        "VALUES('uuid-1','Ryż','szt.',700,1789980000001)")
    raise AssertionError("duplicate operation")
except sqlite3.IntegrityError:
    pass
assert 'db.beginTransaction();' in source and 'db.setTransactionSuccessful();' in source
assert 'priceGrosz != null' in source and 'row.put("unit_price_grosz", priceGrosz)' in source
assert 'PantryPriceHistoryStore.linkReceived(db, shoppingId, pantryId);' in receipt
assert 'PantryPriceHistoryStore.markBought(' in main
assert 'private void shoppingBoughtDialog(' in main
assert 'private void showPantryPriceHistory(' in main
assert 'PantryPriceHistoryStore.create(database);' in main
assert 'DATABASE_MIGRATED_21_TO_22_PANTRY_PRICES' in main
assert '"pantry_purchase_prices", "id", "operation_id", "shopping_id", "pantry_id"' in backup
assert 'inputVersion < 22 && "pantry_purchase_prices".equals(definition[0])' in backup
assert 'private static final int DB_VERSION = 33;' in backup
assert 'versionCode 79' in gradle and "versionNameSuffix ''" in gradle
assert '"pantry_purchase_prices"' not in (base / "PaycheckStore.java").read_text(encoding="utf-8")
print("Optional historical prices, v22 migration, backup, no automatic PayCheck: PASS")
