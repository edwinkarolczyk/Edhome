package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** One separately confirmed receipt per shopping list row; no automatic stock from checkbox. */
final class ShoppingReceiptStore {
    static final int MAX_PACKAGES = 100000000;
    private ShoppingReceiptStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shopping_receipts ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "shopping_id INTEGER NOT NULL UNIQUE, "
            + "pantry_id INTEGER NOT NULL, "
            + "name_snapshot TEXT NOT NULL, "
            + "packages INTEGER NOT NULL CHECK(packages BETWEEN 1 AND 100000000), "
            + "before_qty INTEGER NOT NULL, after_qty INTEGER NOT NULL, "
            + "happened_at INTEGER NOT NULL, place_id INTEGER, "
            + "place_name_snapshot TEXT NOT NULL DEFAULT '')");
    }

    static boolean received(SQLiteDatabase db, long shoppingId) {
        try (Cursor c = db.rawQuery(
                "SELECT 1 FROM shopping_receipts WHERE shopping_id=?",
                new String[]{Long.toString(shoppingId)})) {
            return c.moveToFirst();
        }
    }

    static String accept(SQLiteDatabase db, long shoppingId, long pantryId,
            int packages, Long placeId) {
        if (placeId != null && placeId <= 0)
            throw new IllegalArgumentException("Nieprawidłowe miejsce.");
        if (packages < 1 || packages > MAX_PACKAGES)
            throw new IllegalArgumentException("Podaj 1–100000000 całych opakowań.");
        db.beginTransaction();
        try {
            if (received(db, shoppingId)) return "ALREADY_RECEIVED";
            try (Cursor shopping = db.rawQuery(
                    "SELECT checked FROM shopping_items WHERE id=?",
                    new String[]{Long.toString(shoppingId)})) {
                if (!shopping.moveToFirst() || shopping.getInt(0) != 1)
                    return "NOT_PURCHASED";
            }
            String name;
            int before;
            try (Cursor pantry = db.rawQuery(
                    "SELECT name,qty FROM pantry WHERE id=?",
                    new String[]{Long.toString(pantryId)})) {
                if (!pantry.moveToFirst()) return "MISSING_PRODUCT";
                name = pantry.getString(0);
                before = pantry.getInt(1);
            }
            if (before > MAX_PACKAGES - packages) return "LIMIT";
            String placeName = "";
            if (placeId != null) {
                try (Cursor place = db.rawQuery(
                        "SELECT name FROM places WHERE id=? LIMIT 1",
                        new String[]{Long.toString(placeId)})) {
                    if (!place.moveToFirst()) return "MISSING_PLACE";
                    placeName = place.getString(0);
                }
            }
            ContentValues record = new ContentValues();
            record.put("shopping_id", shoppingId);
            record.put("pantry_id", pantryId);
            record.put("name_snapshot", name);
            record.put("packages", packages);
            record.put("before_qty", before);
            record.put("after_qty", before + packages);
            record.put("happened_at", System.currentTimeMillis());
            if (placeId == null) record.putNull("place_id");
            else record.put("place_id", placeId);
            record.put("place_name_snapshot", placeName);
            db.insertOrThrow("shopping_receipts", null, record);
            // Price history follows the real product only on confirmed receipt.
            PantryPriceHistoryStore.linkReceived(db, shoppingId, pantryId);
            ContentValues updated = new ContentValues();
            updated.put("qty", before + packages);
            if (db.update("pantry", updated, "id=? AND qty=?",
                    new String[]{Long.toString(pantryId), Integer.toString(before)}) != 1)
                throw new IllegalStateException("Stan zmienił się — spróbuj ponownie.");
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }
}
