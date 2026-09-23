package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Vehicle data owns its deadlines and log; PayCheck is never posted automatically. */
final class VehicleStore {
    private VehicleStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicles ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "name TEXT NOT NULL, registration TEXT NOT NULL DEFAULT '', "
            + "mileage INTEGER NOT NULL DEFAULT 0 CHECK(mileage BETWEEN 0 AND 999999999), "
            + "oc_until TEXT NOT NULL DEFAULT '', "
            + "inspection_until TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE vehicle_events ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, vehicle_id INTEGER NOT NULL, "
            + "kind TEXT NOT NULL CHECK(kind IN ('service','tyres','other')), "
            + "event_date TEXT NOT NULL, mileage INTEGER, "
            + "note TEXT NOT NULL, "
            + "CHECK(mileage IS NULL OR mileage BETWEEN 0 AND 999999999))");
        db.execSQL("CREATE INDEX vehicle_events_vehicle_idx "
            + "ON vehicle_events(vehicle_id,id)");
    }

    static final class Vehicle {
        final long id, mileage;
        final String name, registration, ocUntil, inspectionUntil, notes;
        Vehicle(Cursor c) {
            id = c.getLong(0);
            name = c.getString(1);
            registration = c.getString(2);
            mileage = c.getLong(3);
            ocUntil = c.getString(4);
            inspectionUntil = c.getString(5);
            notes = c.getString(6);
        }
    }

    static Vehicle find(SQLiteDatabase db, long id) {
        try (Cursor c = db.rawQuery(
                "SELECT id,name,registration,mileage,oc_until,inspection_until,notes "
                + "FROM vehicles WHERE id=?", new String[]{Long.toString(id)})) {
            return c.moveToFirst() ? new Vehicle(c) : null;
        }
    }

    static long save(SQLiteDatabase db, long id, String name, String registration,
            long mileage, String ocUntil, String inspectionUntil, String notes) {
        name = VehicleRules.name(name);
        registration = VehicleRules.registration(registration);
        ocUntil = VehicleRules.optionalDate(ocUntil);
        inspectionUntil = VehicleRules.optionalDate(inspectionUntil);
        if (mileage < 0 || mileage > 999999999L
                || notes == null || notes.length() > 500)
            throw new IllegalArgumentException("Nieprawidłowy przebieg lub opis.");
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("registration", registration);
        values.put("mileage", mileage);
        values.put("oc_until", ocUntil);
        values.put("inspection_until", inspectionUntil);
        values.put("notes", notes);
        db.beginTransaction();
        try {
            if (id > 0) {
                Vehicle previous = find(db, id);
                if (previous == null)
                    throw new IllegalArgumentException("Pojazd już nie istnieje.");
                try (Cursor policy = db.rawQuery(
                        "SELECT valid_until FROM vehicle_policies "
                        + "WHERE vehicle_id=? AND current=1",
                        new String[]{Long.toString(id)})) {
                    if (policy.moveToFirst() && !ocUntil.equals(policy.getString(0)))
                        throw new IllegalArgumentException(
                            "Termin bieżącej polisy OC zmieniaj przez zapis nowej polisy.");
                }
                if (mileage < previous.mileage)
                    throw new IllegalArgumentException("Przebieg nie może się zmniejszyć.");
                if (db.update("vehicles", values, "id=? AND mileage=?",
                        new String[]{Long.toString(id),
                            Long.toString(previous.mileage)}) != 1)
                    throw new IllegalStateException("Przebieg zmienił się podczas zapisu.");
            } else if (id == 0) {
                id = db.insertOrThrow("vehicles", null, values);
            } else throw new IllegalArgumentException("Nieprawidłowy pojazd.");
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    /**
     * Records one intentional service operation and advances mileage atomically.
     * Retried operation_id never writes a second event or changes mileage.
     */
    static String addEvent(SQLiteDatabase db, long vehicleId, String operationId,
            String kind, String date, Long mileage, String description) {
        VehicleRules.eventType(kind);
        date = VehicleRules.optionalDate(date);
        if (date.isEmpty()) throw new IllegalArgumentException("Podaj datę wykonania.");
        description = VehicleRules.note(description);
        if (operationId == null || operationId.trim().isEmpty())
            throw new IllegalArgumentException("Brak identyfikatora wpisu.");
        if (mileage != null && (mileage < 0 || mileage > 999999999L))
            throw new IllegalArgumentException("Nieprawidłowy przebieg.");
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery(
                    "SELECT 1 FROM vehicle_events WHERE operation_id=?",
                    new String[]{operationId})) {
                if (c.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return "DUPLICATE_IGNORED";
                }
            }
            Vehicle vehicle = find(db, vehicleId);
            if (vehicle == null) return "MISSING_VEHICLE";
            if (mileage != null && mileage < vehicle.mileage)
                throw new IllegalArgumentException(
                    "Wpis ma przebieg mniejszy od zapisanego. Nie obniżamy licznika.");
            ContentValues event = new ContentValues();
            event.put("operation_id", operationId);
            event.put("vehicle_id", vehicleId);
            event.put("kind", kind);
            event.put("event_date", date);
            if (mileage == null) event.putNull("mileage");
            else event.put("mileage", mileage);
            event.put("note", description);
            db.insertOrThrow("vehicle_events", null, event);
            if (mileage != null) {
                ContentValues updated = new ContentValues();
                updated.put("mileage", mileage);
                if (db.update("vehicles", updated, "id=? AND mileage=?",
                        new String[]{Long.toString(vehicleId),
                            Long.toString(vehicle.mileage)}) != 1)
                    throw new IllegalStateException("Przebieg zmienił się podczas zapisu.");
            }
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally { db.endTransaction(); }
    }
}
