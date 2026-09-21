package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Barcode links and durable scan operations; pantry remains the only stock ledger. */
final class PantryBarcodeStore {
    static final int MAX_QTY = 100_000_000;
    private PantryBarcodeStore() { }

    static final class Item {
        final long id;
        final String name;
        final int qty;
        Item(long id, String name, int qty) {
            this.id = id;
            this.name = name;
            this.qty = qty;
        }
    }

    static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pantry_barcodes ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "pantry_id INTEGER NOT NULL, barcode TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE INDEX pantry_barcodes_pantry_idx "
            + "ON pantry_barcodes(pantry_id)");
        db.execSQL("CREATE TABLE pantry_movements ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, pantry_id INTEGER NOT NULL, "
            + "barcode TEXT NOT NULL, name_snapshot TEXT NOT NULL, "
            + "kind TEXT NOT NULL CHECK(kind IN ('ADD','TAKE')), "
            + "qty INTEGER NOT NULL CHECK(qty>0), "
            + "before_qty INTEGER NOT NULL, after_qty INTEGER NOT NULL, "
            + "happened_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX pantry_movements_pantry_idx "
            + "ON pantry_movements(pantry_id,id)");
    }

    /** Photo metadata only; cached files are optional and can be downloaded again. */
    static void createDetails(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pantry_product_details ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "pantry_id INTEGER NOT NULL UNIQUE, "
            + "brand TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '')");
    }

    static final class Details {
        final String brand;
        final String imageUrl;
        Details(String brand, String imageUrl) {
            this.brand = brand;
            this.imageUrl = imageUrl;
        }
    }

    static Details details(SQLiteDatabase db, long pantryId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT brand,image_url FROM pantry_product_details WHERE pantry_id=?",
                new String[]{Long.toString(pantryId)})) {
            return cursor.moveToFirst() ? new Details(
                cursor.getString(0), cursor.getString(1)) : null;
        }
    }

    static void saveDetails(SQLiteDatabase db, long pantryId, String brand, String imageUrl) {
        if (brand == null || brand.length() > 100
                || imageUrl == null || !imageUrl.isEmpty()
                    && !PantryProductLookup.safeImageUrl(imageUrl))
            throw new IllegalArgumentException("Nieprawidłowe dane produktu.");
        ContentValues values = new ContentValues();
        values.put("brand", brand);
        values.put("image_url", imageUrl);
        if (db.update("pantry_product_details", values, "pantry_id=?",
                new String[]{Long.toString(pantryId)}) != 0) return;
        values.put("pantry_id", pantryId);
        db.insertOrThrow("pantry_product_details", null, values);
    }

    static Item find(SQLiteDatabase db, String barcode) {
        try (Cursor cursor = db.rawQuery(
                "SELECT p.id,p.name,p.qty FROM pantry_barcodes b "
                + "JOIN pantry p ON p.id=b.pantry_id "
                + "WHERE b.barcode=? LIMIT 1", new String[]{barcode})) {
            return cursor.moveToFirst() ?
                new Item(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)) : null;
        }
    }

    private static boolean operationExists(SQLiteDatabase db, String id) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM pantry_movements WHERE operation_id=? LIMIT 1",
                new String[]{id})) {
            return cursor.moveToFirst();
        }
    }

    /**
     * A scan never changes stock until user confirmation. In one transaction,
     * create/link a product if needed, adjust pantry.qty and record movement.
     * Replaying an operation ID (also after restart) does not change stock twice.
     */
    static String commit(SQLiteDatabase db, String barcode, String nameIfNew,
                         String mode, String operationId) {
        return commit(db, barcode, nameIfNew, mode, operationId, "szt.", 1000);
    }

    static String commit(SQLiteDatabase db, String barcode, String nameIfNew,
                         String mode, String operationId, String unit, long sizeMilli) {
        return commit(db, barcode, nameIfNew, mode, operationId, unit, sizeMilli, 0L);
    }

    /** Attach a new barcode to the explicitly selected pantry ID, never a name guess. */
    static String commit(SQLiteDatabase db, String barcode, String nameIfNew,
                         String mode, String operationId, String unit, long sizeMilli,
                         long selectedPantryId) {
        if (selectedPantryId < 0)
            throw new IllegalArgumentException("Nieprawidłowy produkt.");
        if (!PantryPackageRules.valid(unit, sizeMilli))
            throw new IllegalArgumentException("Nieprawidłowe opakowanie.");
        if (!PantryScanRules.validBarcode(barcode)
                || !("ADD".equals(mode) || "TAKE".equals(mode))
                || operationId == null || operationId.trim().isEmpty())
            throw new IllegalArgumentException("Nieprawidłowy kod lub operacja.");
        db.beginTransaction();
        try {
            if (operationExists(db, operationId)) {
                db.setTransactionSuccessful();
                return "DUPLICATE_IGNORED";
            }
            Item item = find(db, barcode);
            if (item != null && selectedPantryId > 0 && item.id != selectedPantryId)
                throw new IllegalArgumentException("Kod jest przypisany do innego produktu.");
            if (item == null) {
                if (!"ADD".equals(mode))
                    throw new IllegalArgumentException("Nieznany kod. Najpierw dodaj produkt.");
                String name = nameIfNew == null ? "" : nameIfNew.trim();
                if (selectedPantryId == 0 && (name.isEmpty() || name.length() > 160))
                    throw new IllegalArgumentException("Nazwa produktu: 1–160 znaków.");
                long pantryId = 0;
                if (selectedPantryId > 0) {
                    try (Cursor cursor = db.rawQuery(
                            "SELECT id FROM pantry WHERE id=? LIMIT 1",
                            new String[]{Long.toString(selectedPantryId)})) {
                        if (cursor.moveToFirst()) pantryId = cursor.getLong(0);
                    }
                    if (pantryId == 0)
                        throw new IllegalArgumentException("Wybrany produkt już nie istnieje.");
                } else {
                    try (Cursor cursor = db.rawQuery(
                            "SELECT id FROM pantry WHERE name=? COLLATE NOCASE LIMIT 1",
                            new String[]{name})) {
                        if (cursor.moveToFirst()) pantryId = cursor.getLong(0);
                    }
                }
                if (pantryId == 0) {
                    ContentValues product = new ContentValues();
                    product.put("name", name);
                    product.put("qty", 0);
                    pantryId = db.insertOrThrow("pantry", null, product);
                    PantryPackageStore.set(db, pantryId, unit, sizeMilli);
                } else {
                    PantryPackageStore.requireSame(db, pantryId, unit, sizeMilli);
                }
                ContentValues barcodeLink = new ContentValues();
                barcodeLink.put("pantry_id", pantryId);
                barcodeLink.put("barcode", barcode);
                db.insertOrThrow("pantry_barcodes", null, barcodeLink);
                item = find(db, barcode);
            }
            if (item == null) throw new IllegalStateException("Nie można znaleźć produktu.");
            if ("TAKE".equals(mode) && item.qty < 1)
                throw new IllegalArgumentException("Nie można zejść poniżej zera.");
            if ("ADD".equals(mode) && item.qty >= MAX_QTY)
                throw new IllegalArgumentException("Osiągnięto maksymalny stan.");
            int after = item.qty + ("ADD".equals(mode) ? 1 : -1);
            ContentValues quantity = new ContentValues();
            quantity.put("qty", after);
            int updated = db.update("pantry", quantity, "id=? AND qty=?",
                new String[]{Long.toString(item.id), Integer.toString(item.qty)});
            if (updated != 1)
                throw new IllegalStateException("Stan zmienił się podczas operacji.");
            ContentValues movement = new ContentValues();
            movement.put("operation_id", operationId);
            movement.put("pantry_id", item.id);
            movement.put("barcode", barcode);
            movement.put("name_snapshot", item.name);
            movement.put("kind", mode);
            movement.put("qty", 1);
            movement.put("before_qty", item.qty);
            movement.put("after_qty", after);
            movement.put("happened_at", System.currentTimeMillis());
            db.insertOrThrow("pantry_movements", null, movement);
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }
}
