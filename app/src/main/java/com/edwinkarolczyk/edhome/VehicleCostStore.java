package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** An optional SHARED PayCheck expense and its vehicle link commit atomically.
 * Manual costs never change PayCheck. No private ledger is read or modified.
 */
final class VehicleCostStore {
    static final String[] KINDS = {"oc", "inspection", "service", "tyres", "other"};
    static final String[] LABELS = {"OC", "Przegląd", "Serwis", "Opony", "Inne"};
    private VehicleCostStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle_costs ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, vehicle_id INTEGER NOT NULL, "
            + "kind TEXT NOT NULL CHECK(kind IN ('oc','inspection','service','tyres','other')), "
            + "paid_on TEXT NOT NULL, amount_grosz INTEGER NOT NULL "
            + "CHECK(amount_grosz BETWEEN 1 AND 99999999999), "
            + "note TEXT NOT NULL DEFAULT '', paycheck_operation_id TEXT UNIQUE)");
        db.execSQL("CREATE INDEX vehicle_costs_vehicle_idx "
            + "ON vehicle_costs(vehicle_id,id)");
    }

    static boolean validKind(String value) {
        for (String kind : KINDS) if (kind.equals(value)) return true;
        return false;
    }

    static String label(String value) {
        for (int i=0;i<KINDS.length;i++) if (KINDS[i].equals(value)) return LABELS[i];
        return "Inne";
    }

    static String note(String value) {
        String clean=value==null?"":value.trim();
        if (clean.length()>100)
            throw new IllegalArgumentException("Opis kosztu: maksymalnie 100 znaków.");
        return clean;
    }

    /** One explicit tap; retries cannot duplicate a cost or a bank-ledger entry. */
    static String record(SQLiteDatabase db, long vehicleId, String operationId,
            String kind, String paidOn, long amountGrosz, String details,
            boolean sharedExpense) {
        if (operationId==null || !operationId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Nieprawidłowy identyfikator kosztu.");
        if (!validKind(kind)) throw new IllegalArgumentException("Wybierz rodzaj kosztu.");
        String date=VehicleRules.optionalDate(paidOn);
        if (date.isEmpty()) throw new IllegalArgumentException("Wybierz datę zapłaty.");
        if (amountGrosz<1 || amountGrosz>MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowa kwota.");
        details=note(details);
        db.beginTransaction();
        try {
            try (Cursor prior=db.rawQuery(
                    "SELECT 1 FROM vehicle_costs WHERE operation_id=?",
                    new String[]{operationId})) {
                if (prior.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return "DUPLICATE_IGNORED";
                }
            }
            VehicleStore.Vehicle vehicle=VehicleStore.find(db,vehicleId);
            if (vehicle==null) return "MISSING_VEHICLE";
            if (sharedExpense) {
                ContentValues transaction=new ContentValues();
                transaction.put("operation_id",operationId);
                transaction.put("scope","shared");
                transaction.put("kind","expense");
                transaction.put("category","vehicle");
                transaction.put("amount_grosz",amountGrosz);
                String summary=vehicle.name+" • "+label(kind)
                    +(details.isEmpty()?"":" • "+details);
                transaction.put("note",summary.length()>160
                    ?summary.substring(0,160):summary);
                transaction.put("created_at",System.currentTimeMillis());
                db.insertOrThrow("paycheck_transactions",null,transaction);
            }
            ContentValues cost=new ContentValues();
            cost.put("operation_id",operationId);
            cost.put("vehicle_id",vehicleId);
            cost.put("kind",kind);
            cost.put("paid_on",date);
            cost.put("amount_grosz",amountGrosz);
            cost.put("note",details);
            if (sharedExpense) cost.put("paycheck_operation_id",operationId);
            else cost.putNull("paycheck_operation_id");
            db.insertOrThrow("vehicle_costs",null,cost);
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }
}
