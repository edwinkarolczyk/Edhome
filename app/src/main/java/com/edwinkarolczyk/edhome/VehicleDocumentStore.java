package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;

/** Local vehicle-document register. No PayCheck posting and no external upload. */
final class VehicleDocumentStore {
    static final String[] KINDS = {
        "registration","insurance","inspection","invoice","service","other"
    };
    static final String[] LABELS = {
        "Dowód rejestracyjny","Polisa / ubezpieczenie","Przegląd",
        "Faktura / rachunek","Dokument serwisowy","Inne"
    };

    private VehicleDocumentStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle_documents ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, vehicle_id INTEGER NOT NULL, "
            + "kind TEXT NOT NULL CHECK(kind IN "
            + "('registration','insurance','inspection','invoice','service','other')), "
            + "title TEXT NOT NULL, document_number TEXT NOT NULL DEFAULT '', "
            + "issued_on TEXT NOT NULL DEFAULT '', valid_until TEXT NOT NULL DEFAULT '', "
            + "note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX vehicle_documents_vehicle_idx "
            + "ON vehicle_documents(vehicle_id,id)");
    }

    static String label(String kind) {
        for (int i=0;i<KINDS.length;i++) if (KINDS[i].equals(kind)) return LABELS[i];
        return "Inne";
    }

    static boolean validKind(String kind) {
        for (String candidate : KINDS) if (candidate.equals(kind)) return true;
        return false;
    }

    static String add(SQLiteDatabase db,long vehicleId,String operationId,
            String kind,String title,String number,String issuedOn,String validUntil,
            String note) {
        if (operationId==null || !operationId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Nieprawidłowy identyfikator dokumentu.");
        if (!validKind(kind)) throw new IllegalArgumentException("Wybierz rodzaj dokumentu.");
        title=clean(title,1,120,"Nazwa dokumentu");
        number=clean(number,0,120,"Numer dokumentu");
        note=clean(note,0,500,"Notatka");
        issuedOn=optionalDate(issuedOn);
        validUntil=optionalDate(validUntil);
        if (!issuedOn.isEmpty() && !validUntil.isEmpty()
                && LocalDate.parse(validUntil).isBefore(LocalDate.parse(issuedOn)))
            throw new IllegalArgumentException("Termin ważności nie może być przed datą dokumentu.");
        db.beginTransaction();
        try {
            try (Cursor existing=db.rawQuery(
                    "SELECT 1 FROM vehicle_documents WHERE operation_id=?",
                    new String[]{operationId})) {
                if (existing.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return "DUPLICATE_IGNORED";
                }
            }
            if (VehicleStore.find(db,vehicleId)==null) return "MISSING_VEHICLE";
            ContentValues values=new ContentValues();
            values.put("operation_id",operationId);
            values.put("vehicle_id",vehicleId);
            values.put("kind",kind);
            values.put("title",title);
            values.put("document_number",number);
            values.put("issued_on",issuedOn);
            values.put("valid_until",validUntil);
            values.put("note",note);
            values.put("created_at",System.currentTimeMillis());
            db.insertOrThrow("vehicle_documents",null,values);
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }

    private static String clean(String raw,int min,int max,String label) {
        String value=raw==null?"":raw.trim();
        if (value.length()<min || value.length()>max)
            throw new IllegalArgumentException(label+": "+min+"–"+max+" znaków.");
        return value;
    }

    private static String optionalDate(String raw) {
        String value=raw==null?"":raw.trim();
        if (value.isEmpty()) return "";
        try { return LocalDate.parse(value).toString(); }
        catch (Exception e) { throw new IllegalArgumentException("Nieprawidłowa data dokumentu."); }
    }
}
