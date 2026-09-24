package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * PayCheck first increment: SHARED ledger ONLY.
 * Personal accounts/entries must never be shown in this unauthenticated Beta.
 */
final class PaycheckStore {
    private PaycheckStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE paycheck_transactions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, "
            + "scope TEXT NOT NULL CHECK(scope='shared'), "
            + "kind TEXT NOT NULL CHECK(kind IN ('income','expense')), "
            + "category TEXT NOT NULL, amount_grosz INTEGER NOT NULL "
            + "CHECK(amount_grosz BETWEEN 1 AND 99999999999), "
            + "note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, "
            + "status TEXT NOT NULL DEFAULT 'confirmed' "
            + "CHECK(status IN ('pending','confirmed')), "
            + "confirmation_source TEXT NOT NULL DEFAULT 'legacy' "
            + "CHECK(confirmation_source IN ('none','legacy','manual')), "
            + "confirmed_at INTEGER)");
    }

    static String add(SQLiteDatabase db,String operationId,String kind,
            String category,long grosz,String note) {
        if(operationId==null || !operationId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Nieprawidłowa operacja.");
        if(!"income".equals(kind) && !"expense".equals(kind))
            throw new IllegalArgumentException("Nieznany rodzaj operacji.");
        if(!MoneyRules.category(category) || grosz<1
                || grosz>MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowa kwota lub kategoria.");
        if(note==null || note.length()>160)
            throw new IllegalArgumentException("Opis może mieć maks. 160 znaków.");
        db.beginTransaction();
        try {
            try(Cursor previous=db.rawQuery(
                    "SELECT 1 FROM paycheck_transactions WHERE operation_id=?",
                    new String[]{operationId})) {
                if(previous.moveToFirst())return "DUPLICATE";
            }
            ContentValues values=new ContentValues();
            values.put("operation_id",operationId);
            values.put("scope","shared");
            values.put("kind",kind);
            values.put("category",category);
            values.put("amount_grosz",grosz);
            values.put("note",note.trim());
            values.put("created_at",System.currentTimeMillis());
            values.put("status","pending");
            values.put("confirmation_source","none");
            db.insertOrThrow("paycheck_transactions",null,values);
            db.setTransactionSuccessful();
            return "COMMITTED";
        }finally{db.endTransaction();}
    }

    /** Manual attestation only; future bank notification/statement matching is separate.
     * Only the first pending -> confirmed transition changes the reported balance.
     */
    static String confirm(SQLiteDatabase db, String operationId) {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Nieprawidłowa transakcja.");
        db.beginTransaction();
        try {
            ContentValues state = new ContentValues();
            state.put("status", "confirmed");
            state.put("confirmation_source", "manual");
            state.put("confirmed_at", System.currentTimeMillis());
            int changed = db.update("paycheck_transactions", state,
                "operation_id=? AND scope='shared' AND status='pending'",
                new String[]{operationId});
            db.setTransactionSuccessful();
            return changed == 1 ? "CONFIRMED" : "ALREADY_OR_MISSING";
        } finally { db.endTransaction(); }
    }

    static long sharedBalance(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN kind='income' "
                + "THEN amount_grosz ELSE -amount_grosz END),0) "
                + "FROM paycheck_transactions WHERE scope='shared' AND status='confirmed'",null)) {
            return c.moveToFirst()?c.getLong(0):0L;
        }
    }
}
