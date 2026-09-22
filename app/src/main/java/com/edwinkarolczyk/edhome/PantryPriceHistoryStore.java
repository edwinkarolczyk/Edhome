package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.UUID;

/**
 * Purchase-price events, separate from inventory receipts and PayCheck ledger.
 * Unknown purchase price is NOT zero, and "bought" does not add stock or expense.
 */
final class PantryPriceHistoryStore {
    private PantryPriceHistoryStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pantry_purchase_prices ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, "
            + "shopping_id INTEGER, pantry_id INTEGER, "
            + "name_snapshot TEXT NOT NULL, "
            + "unit TEXT NOT NULL, quantity_milli INTEGER, "
            + "unit_price_grosz INTEGER NOT NULL "
            + "CHECK(unit_price_grosz BETWEEN 1 AND 100000000000), "
            + "shop TEXT NOT NULL DEFAULT '', "
            + "happened_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX pantry_price_product_date_idx "
            + "ON pantry_purchase_prices (pantry_id,happened_at DESC,id DESC)");
    }

    /**
     * Check a shopping item once, optionally preserving a real price for ONE
     * recorded unit. No pantry quantity or PayCheck balance changes here.
     */
    static String markBought(SQLiteDatabase db, long shoppingId,
            Long priceGrosz, String shop) {
        if (priceGrosz != null && (priceGrosz < 1
                || priceGrosz > MoneyRules.MAX_GROSZ))
            throw new IllegalArgumentException("Nieprawidłowa cena.");
        if (shop == null || shop.trim().length() > 80)
            throw new IllegalArgumentException("Sklep ma maks. 80 znaków.");
        db.beginTransaction();
        try {
            String name, unit;
            Long quantity;
            try (Cursor c = db.rawQuery(
                    "SELECT name,unit,qty_milli,checked FROM shopping_items WHERE id=?",
                    new String[]{Long.toString(shoppingId)})) {
                if (!c.moveToFirst()) return "MISSING";
                if (c.getInt(3) != 0) return "ALREADY_BOUGHT";
                name = c.getString(0);
                unit = c.getString(1);
                quantity = c.isNull(2) ? null : c.getLong(2);
            }
            ContentValues purchase = new ContentValues();
            purchase.put("checked", 1);
            if (db.update("shopping_items", purchase, "id=? AND checked=0",
                    new String[]{Long.toString(shoppingId)}) != 1)
                return "ALREADY_BOUGHT";
            if (priceGrosz != null) {
                ContentValues row = new ContentValues();
                row.put("operation_id", UUID.randomUUID().toString());
                row.put("shopping_id", shoppingId);
                row.put("name_snapshot", name);
                row.put("unit", unit);
                if (quantity == null) row.putNull("quantity_milli");
                else row.put("quantity_milli", quantity);
                row.put("unit_price_grosz", priceGrosz);
                row.put("shop", shop.trim());
                row.put("happened_at", System.currentTimeMillis());
                db.insertOrThrow("pantry_purchase_prices", null, row);
            }
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }

    /** Link known historical prices to the actual product on confirmed receipt. */
    static void linkReceived(SQLiteDatabase db, long shoppingId, long pantryId) {
        ContentValues values = new ContentValues();
        values.put("pantry_id", pantryId);
        db.update("pantry_purchase_prices", values,
            "shopping_id=? AND pantry_id IS NULL",
            new String[]{Long.toString(shoppingId)});
    }

    static Cursor forProduct(SQLiteDatabase db, long pantryId) {
        return db.rawQuery("SELECT unit_price_grosz,unit,shop,happened_at,"
                + "name_snapshot,quantity_milli FROM pantry_purchase_prices "
                + "WHERE pantry_id=? ORDER BY happened_at DESC,id DESC LIMIT 100",
            new String[]{Long.toString(pantryId)});
    }

    static Cursor forShoppingItem(SQLiteDatabase db, long shoppingId) {
        return db.rawQuery("SELECT unit_price_grosz,unit,shop,happened_at "
                + "FROM pantry_purchase_prices WHERE shopping_id=? "
                + "ORDER BY happened_at DESC,id DESC LIMIT 1",
            new String[]{Long.toString(shoppingId)});
    }
}
