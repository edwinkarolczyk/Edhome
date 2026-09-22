package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Stock in pantry.qty counts PACKAGES, not kg or litres. */
final class PantryPackageStore {
    private PantryPackageStore() { }
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pantry_packages ("
            + "pantry_id INTEGER PRIMARY KEY, "
            + "unit TEXT NOT NULL CHECK(unit IN ('szt.','kg','l')), "
            + "size_milli INTEGER NOT NULL CHECK(size_milli BETWEEN 1 AND 1000000000), "
            + "CHECK(unit!='szt.' OR size_milli%1000=0))");
    }
    static void fillLegacy(SQLiteDatabase db) {
        db.execSQL("INSERT INTO pantry_packages(pantry_id,unit,size_milli) "
            + "SELECT id,'szt.',1000 FROM pantry WHERE id NOT IN "
            + "(SELECT pantry_id FROM pantry_packages)");
    }
    static final class Pack {
        final String unit;
        final long sizeMilli;
        Pack(String unit, long sizeMilli) {
            this.unit = unit;
            this.sizeMilli = sizeMilli;
        }
    }
    static Pack find(SQLiteDatabase db, long pantryId) {
        try (Cursor c = db.rawQuery(
                "SELECT unit,size_milli FROM pantry_packages WHERE pantry_id=?",
                new String[]{Long.toString(pantryId)})) {
            return c.moveToFirst()
                ? new Pack(c.getString(0), c.getLong(1))
                : new Pack("szt.", 1000);
        }
    }
    static void set(SQLiteDatabase db, long pantryId, String unit, long milli) {
        if (!PantryPackageRules.valid(unit, milli))
            throw new IllegalArgumentException("Nieprawidłowa wielkość opakowania.");
        ContentValues values = new ContentValues();
        values.put("unit", unit);
        values.put("size_milli", milli);
        if (db.update("pantry_packages", values, "pantry_id=?",
                new String[]{Long.toString(pantryId)}) != 0) return;
        values.put("pantry_id", pantryId);
        db.insertOrThrow("pantry_packages", null, values);
    }
    static void requireSame(SQLiteDatabase db, long pantryId, String unit, long milli) {
        Pack existing = find(db, pantryId);
        if (!existing.unit.equals(unit) || existing.sizeMilli != milli)
            throw new IllegalArgumentException("Produkt o tej nazwie ma inne opakowanie. "
                + "Edytuj go lub użyj odrębnej nazwy.");
    }
}
