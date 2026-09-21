package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

final class PantryCategoriesStore {
    private PantryCategoriesStore() { }

    static String find(SQLiteDatabase db, long id) {
        try (Cursor c = db.rawQuery("SELECT category FROM pantry WHERE id=?",
                new String[]{Long.toString(id)})) {
            return c.moveToFirst() ? c.getString(0) : "other";
        }
    }

    static void set(SQLiteDatabase db, long id, String category) {
        if (!PantryCategories.known(category))
            throw new IllegalArgumentException("Nieznana kategoria.");
        ContentValues value = new ContentValues();
        value.put("category", category);
        if (db.update("pantry", value, "id=?",
                new String[]{Long.toString(id)}) != 1)
            throw new IllegalArgumentException("Nie znaleziono produktu.");
    }

    /** Never replace an existing classification with a guess from a catalogue. */
    static void setIfOther(SQLiteDatabase db, long id, String category) {
        if (!PantryCategories.known(category))
            throw new IllegalArgumentException("Nieznana kategoria.");
        ContentValues value = new ContentValues();
        value.put("category", category);
        db.update("pantry", value, "id=? AND category='other'",
            new String[]{Long.toString(id)});
    }
}
