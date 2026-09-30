package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** One tyre set belongs to one vehicle; only one set may be fitted at a time.
 * A wheel change, mileage advance and vehicle history entry are one transaction.
 */
final class VehicleTyreStore {
    private VehicleTyreStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE vehicle_tyre_sets ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, "
            + "label TEXT NOT NULL, season TEXT NOT NULL "
            + "CHECK(season IN ('summer','winter','allseason')), "
            + "dot TEXT NOT NULL DEFAULT '', tread_tenths INTEGER "
            + "CHECK(tread_tenths IS NULL OR tread_tenths BETWEEN 0 AND 200), "
            + "mounted INTEGER NOT NULL DEFAULT 0 CHECK(mounted IN (0,1)), "
            + "place_id INTEGER, CHECK(mounted=0 OR place_id IS NULL))");
        db.execSQL("CREATE UNIQUE INDEX vehicle_tyre_one_mounted "
            + "ON vehicle_tyre_sets(vehicle_id) WHERE mounted=1");
        db.execSQL("CREATE INDEX vehicle_tyre_sets_vehicle_idx "
            + "ON vehicle_tyre_sets(vehicle_id,id)");
    }

    static final class SetInfo {
        final long id, vehicleId;
        final String label, season, dot;
        final Integer tread;
        final boolean mounted;
        final Long placeId;
        SetInfo(Cursor c) {
            id=c.getLong(0); vehicleId=c.getLong(1);
            label=c.getString(2); season=c.getString(3); dot=c.getString(4);
            tread=c.isNull(5)?null:c.getInt(5);
            mounted=c.getInt(6)==1;
            placeId=c.isNull(7)?null:c.getLong(7);
        }
    }

    static SetInfo find(SQLiteDatabase db, long id) {
        try(Cursor c=db.rawQuery("SELECT id,vehicle_id,label,season,dot,"
                + "tread_tenths,mounted,place_id FROM vehicle_tyre_sets WHERE id=?",
                new String[]{Long.toString(id)})) {
            return c.moveToFirst()?new SetInfo(c):null;
        }
    }

    static String label(String text) {
        String trimmed=text==null?"":text.trim();
        if(trimmed.isEmpty() || trimmed.length()>120)
            throw new IllegalArgumentException("Nazwa kompletu: 1–120 znaków.");
        return trimmed;
    }

    static String season(String value) {
        if(!"summer".equals(value) && !"winter".equals(value)
                && !"allseason".equals(value))
            throw new IllegalArgumentException("Wybierz sezon opon.");
        return value;
    }

    static String dot(String input) {
        String clean=input==null?"":input.trim();
        if(clean.isEmpty()) return "";
        if(!clean.matches("[0-9]{4}")
                || Integer.parseInt(clean.substring(0,2))<1
                || Integer.parseInt(clean.substring(0,2))>53)
            throw new IllegalArgumentException("DOT: cztery cyfry, tydzień 01–53.");
        return clean;
    }

    static Integer tread(String input) {
        String clean=input==null?"":input.trim().replace(',','.');
        if(clean.isEmpty())return null;
        if(!clean.matches("[0-9]{1,2}(\\.[0-9])?"))
            throw new IllegalArgumentException("Bieżnik w mm, np. 6,5.");
        int dot=clean.indexOf('.');
        int value=dot<0?Integer.parseInt(clean)*10
            :Integer.parseInt(clean.substring(0,dot))*10
                +Integer.parseInt(clean.substring(dot+1));
        if(value>200)throw new IllegalArgumentException("Bieżnik: maks. 20 mm.");
        return value;
    }

    static String seasonLabel(String value) {
        switch(season(value)){
            case "summer": return "Letnie";
            case "winter": return "Zimowe";
            default: return "Całoroczne";
        }
    }

    private static void validPlace(SQLiteDatabase db, Long place) {
        if(place==null)return;
        if(place<1)throw new IllegalArgumentException("Nieprawidłowe miejsce.");
        try(Cursor c=db.rawQuery("SELECT 1 FROM places WHERE id=?",
                new String[]{Long.toString(place)})){
            if(!c.moveToFirst())
                throw new IllegalArgumentException("Miejsce już nie istnieje.");
        }
    }

    static long save(SQLiteDatabase db,long id,long vehicleId,String name,
            String tyreSeason,String tyreDot,Integer tread,Long placeId) {
        name=label(name); tyreSeason=season(tyreSeason); tyreDot=dot(tyreDot);
        if(tread!=null && (tread<0 || tread>200))
            throw new IllegalArgumentException("Nieprawidłowy bieżnik.");
        db.beginTransaction();
        try {
            if(VehicleStore.find(db,vehicleId)==null)
                throw new IllegalArgumentException("Pojazd już nie istnieje.");
            SetInfo current=id==0?null:find(db,id);
            if(id<0 || id>0 && (current==null || current.vehicleId!=vehicleId))
                throw new IllegalArgumentException("Komplet nie należy do pojazdu.");
            if(current!=null && current.mounted)placeId=null;
            validPlace(db,placeId);
            ContentValues values=new ContentValues();
            values.put("vehicle_id",vehicleId);
            values.put("label",name);values.put("season",tyreSeason);
            values.put("dot",tyreDot);
            if(tread==null)values.putNull("tread_tenths");
            else values.put("tread_tenths",tread);
            if(placeId==null)values.putNull("place_id");
            else values.put("place_id",placeId);
            if(id==0)id=db.insertOrThrow("vehicle_tyre_sets",null,values);
            else if(db.update("vehicle_tyre_sets",values,"id=? AND vehicle_id=?",
                    new String[]{Long.toString(id),Long.toString(vehicleId)})!=1)
                throw new IllegalStateException("Komplet zmienił się podczas zapisu.");
            db.setTransactionSuccessful();
            return id;
        } finally {db.endTransaction();}
    }

    /** targetSetId=0 means remove fitted set without installing another one. */
    static String change(SQLiteDatabase db,long vehicleId,long targetSetId,
            Long storagePlace,String operationId,String date,Long mileage) {
        date=VehicleRules.optionalDate(date);
        if(date.isEmpty())throw new IllegalArgumentException("Podaj datę zmiany.");
        if(operationId==null || !operationId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Nieprawidłowa operacja.");
        if(mileage!=null && (mileage<0 || mileage>999999999L))
            throw new IllegalArgumentException("Nieprawidłowy przebieg.");
        db.beginTransaction();
        try {
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM vehicle_events WHERE operation_id=?",
                    new String[]{operationId})){
                if(c.moveToFirst()){
                    db.setTransactionSuccessful();return "DUPLICATE_IGNORED";
                }
            }
            VehicleStore.Vehicle vehicle=VehicleStore.find(db,vehicleId);
            if(vehicle==null)return "MISSING_VEHICLE";
            SetInfo incoming=targetSetId==0?null:find(db,targetSetId);
            if(targetSetId<0 || targetSetId>0
                    && (incoming==null || incoming.vehicleId!=vehicleId
                        || incoming.mounted))
                throw new IllegalArgumentException("Wybierz niezamontowany komplet.");
            SetInfo outgoing=null;
            try(Cursor c=db.rawQuery(
                    "SELECT id FROM vehicle_tyre_sets WHERE vehicle_id=? AND mounted=1",
                    new String[]{Long.toString(vehicleId)})){
                if(c.moveToFirst())outgoing=find(db,c.getLong(0));
            }
            if(outgoing==null && incoming==null)
                throw new IllegalArgumentException("Nie ma kompletu do zdjęcia.");
            validPlace(db,storagePlace);
            if(mileage!=null && mileage<vehicle.mileage)
                throw new IllegalArgumentException("Przebieg nie może się zmniejszyć.");
            if(outgoing!=null) {
                ContentValues off=new ContentValues();
                off.put("mounted",0);
                if(storagePlace==null)off.putNull("place_id");
                else off.put("place_id",storagePlace);
                if(db.update("vehicle_tyre_sets",off,"id=? AND mounted=1",
                        new String[]{Long.toString(outgoing.id)})!=1)
                    throw new IllegalStateException("Zmieniono stan kół.");
            }
            if(incoming!=null){
                ContentValues on=new ContentValues();
                on.put("mounted",1);on.putNull("place_id");
                if(db.update("vehicle_tyre_sets",on,"id=? AND mounted=0",
                        new String[]{Long.toString(incoming.id)})!=1)
                    throw new IllegalStateException("Zmieniono stan kół.");
            }
            String note="Zmiana kół: "
                +(outgoing==null?"bez zdjęcia":("zdjęto "+outgoing.label))
                +" → "+(incoming==null?"bez montażu":("zamontowano "+incoming.label));
            String result=VehicleStore.addEvent(db,vehicleId,operationId,
                "tyres",date,mileage,note);
            if(!"COMMITTED".equals(result))
                throw new IllegalStateException("Nie zapisano historii zmiany kół.");
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {db.endTransaction();}
    }
}
