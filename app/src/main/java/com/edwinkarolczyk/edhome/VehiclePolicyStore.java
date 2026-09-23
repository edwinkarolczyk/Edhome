package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;

/** OC policy records are historical facts; renewal only changes the selected current
 * policy and the existing vehicle OC calendar deadline. No PayCheck postings or auto-renewal.
 */
final class VehiclePolicyStore {
    private VehiclePolicyStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle_policies ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, vehicle_id INTEGER NOT NULL, "
            + "provider TEXT NOT NULL, policy_number TEXT NOT NULL, "
            + "valid_from TEXT NOT NULL, valid_until TEXT NOT NULL, "
            + "current INTEGER NOT NULL DEFAULT 0 CHECK(current IN (0,1)), "
            + "notes TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE UNIQUE INDEX vehicle_policies_one_current "
            + "ON vehicle_policies(vehicle_id) WHERE current=1");
        db.execSQL("CREATE INDEX vehicle_policies_vehicle_idx "
            + "ON vehicle_policies(vehicle_id,id)");
    }

    static String provider(String value) {
        String clean=value==null?"":value.trim();
        if(clean.isEmpty() || clean.length()>120)
            throw new IllegalArgumentException("Ubezpieczyciel: 1–120 znaków.");
        return clean;
    }

    static String number(String value) {
        String clean=value==null?"":value.trim();
        if(clean.isEmpty() || clean.length()>80)
            throw new IllegalArgumentException("Numer polisy: 1–80 znaków.");
        return clean;
    }

    static String notes(String value) {
        String clean=value==null?"":value.trim();
        if(clean.length()>500)
            throw new IllegalArgumentException("Notatka: maks. 500 znaków.");
        return clean;
    }

    static String dates(String start, String end) {
        String from=VehicleRules.optionalDate(start);
        String until=VehicleRules.optionalDate(end);
        if(from.isEmpty() || until.isEmpty())
            throw new IllegalArgumentException("Podaj obie daty polisy.");
        if(LocalDate.parse(until).isBefore(LocalDate.parse(from)))
            throw new IllegalArgumentException("Koniec polisy nie może być przed początkiem.");
        return until;
    }

    /** New policy is appended, never overwrites historic policy.
     * A retried operationId cannot switch current policy back or create another row.
     */
    static String add(SQLiteDatabase db, long vehicleId, String operationId,
            String company, String policyNumber, String from, String until,
            boolean makeCurrent, String description) {
        company=provider(company);
        policyNumber=number(policyNumber);
        until=dates(from,until);
        from=VehicleRules.optionalDate(from);
        description=notes(description);
        if(operationId==null || !operationId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Nieprawidłowy identyfikator zapisu.");
        db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery("SELECT 1 FROM vehicle_policies WHERE operation_id=?",
                    new String[]{operationId})){
                if(c.moveToFirst()){
                    db.setTransactionSuccessful();
                    return "DUPLICATE_IGNORED";
                }
            }
            if(VehicleStore.find(db,vehicleId)==null)return "MISSING_VEHICLE";
            if(makeCurrent) {
                ContentValues previous=new ContentValues();
                previous.put("current",0);
                db.update("vehicle_policies",previous,
                    "vehicle_id=? AND current=1",new String[]{Long.toString(vehicleId)});
            }
            ContentValues policy=new ContentValues();
            policy.put("operation_id",operationId);
            policy.put("vehicle_id",vehicleId);
            policy.put("provider",company);
            policy.put("policy_number",policyNumber);
            policy.put("valid_from",from);
            policy.put("valid_until",until);
            policy.put("current",makeCurrent?1:0);
            policy.put("notes",description);
            db.insertOrThrow("vehicle_policies",null,policy);
            if(makeCurrent){
                ContentValues vehicle=new ContentValues();
                vehicle.put("oc_until",until);
                if(db.update("vehicles",vehicle,"id=?",
                        new String[]{Long.toString(vehicleId)})!=1)
                    throw new IllegalStateException("Nie zapisano terminu OC.");
            }
            db.setTransactionSuccessful();
            return "COMMITTED";
        }finally{db.endTransaction();}
    }
}
