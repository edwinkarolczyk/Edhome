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
            + "deposit_grosz INTEGER NOT NULL DEFAULT 0 CHECK(deposit_grosz BETWEEN 0 AND 100000), "
            + "deposit_pending INTEGER NOT NULL DEFAULT 0 CHECK(deposit_pending BETWEEN 0 AND 100000000), "
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
        final long depositGrosz;
        final int depositPending;
        Pack(String unit, long sizeMilli) {
            this(unit, sizeMilli, 0L, 0);
        }
        Pack(String unit, long sizeMilli, long depositGrosz, int depositPending) {
            this.unit = unit;
            this.sizeMilli = sizeMilli;
            this.depositGrosz = depositGrosz;
            this.depositPending = depositPending;
        }
    }
    static Pack find(SQLiteDatabase db, long pantryId) {
        try (Cursor c = db.rawQuery(
                "SELECT unit,size_milli,deposit_grosz,deposit_pending "
                    + "FROM pantry_packages WHERE pantry_id=?",
                new String[]{Long.toString(pantryId)})) {
            return c.moveToFirst()
                ? new Pack(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3))
                : new Pack("szt.", 1000, 0L, 0);
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
    static void setDeposit(SQLiteDatabase db, long pantryId, long grosz) {
        if (grosz < 0 || grosz > 100000)
            throw new IllegalArgumentException("Kaucja musi wynosić 0–1000 zł za sztukę.");
        ContentValues values = new ContentValues();
        values.put("deposit_grosz", grosz);
        if (db.update("pantry_packages", values, "pantry_id=?",
                new String[]{Long.toString(pantryId)}) != 1)
            throw new IllegalArgumentException("Produkt nie istnieje.");
    }

    static void addPendingDeposit(SQLiteDatabase db, long pantryId, int units) {
        if (units <= 0) return;
        Pack pack = find(db, pantryId);
        if (pack.depositGrosz <= 0) return;
        if ((long) pack.depositPending + units > 100000000L)
            throw new IllegalArgumentException("Za dużo opakowań kaucyjnych.");
        ContentValues values = new ContentValues();
        values.put("deposit_pending", pack.depositPending + units);
        if (db.update("pantry_packages", values,
                "pantry_id=? AND deposit_pending=?",
                new String[]{Long.toString(pantryId),
                    Integer.toString(pack.depositPending)}) != 1)
            throw new IllegalStateException("Stan kaucji zmienił się podczas zapisu.");
    }

    static void returnDeposit(SQLiteDatabase db, long pantryId, int units) {
        if (units <= 0)
            throw new IllegalArgumentException("Podaj liczbę oddanych opakowań.");
        Pack pack = find(db, pantryId);
        if (units > pack.depositPending)
            throw new IllegalArgumentException("Nie masz tylu opakowań do zwrotu.");
        ContentValues values = new ContentValues();
        values.put("deposit_pending", pack.depositPending - units);
        if (db.update("pantry_packages", values,
                "pantry_id=? AND deposit_pending=?",
                new String[]{Long.toString(pantryId),
                    Integer.toString(pack.depositPending)}) != 1)
            throw new IllegalStateException("Stan kaucji zmienił się podczas zapisu.");
    }

    static long totalDepositGrosz(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(deposit_grosz*deposit_pending),0) "
                    + "FROM pantry_packages", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    static long stockDepositGrosz(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(pp.deposit_grosz*p.qty),0) "
                    + "FROM pantry_packages pp JOIN pantry p ON p.id=pp.pantry_id "
                    + "WHERE pp.deposit_grosz>0", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    static int stockDepositUnits(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(p.qty),0) "
                    + "FROM pantry_packages pp JOIN pantry p ON p.id=pp.pantry_id "
                    + "WHERE pp.deposit_grosz>0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    static long allDepositGrosz(SQLiteDatabase db) {
        return Math.addExact(stockDepositGrosz(db), totalDepositGrosz(db));
    }

    static int totalPendingDepositUnits(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(deposit_pending),0) FROM pantry_packages", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    static void requireSame(SQLiteDatabase db, long pantryId, String unit, long milli) {
        Pack existing = find(db, pantryId);
        if (!existing.unit.equals(unit) || existing.sizeMilli != milli)
            throw new IllegalArgumentException("Produkt o tej nazwie ma inne opakowanie. "
                + "Edytuj go lub użyj odrębnej nazwy.");
    }
}
