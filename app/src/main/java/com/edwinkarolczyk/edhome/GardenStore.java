package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;

/**
 * Offline-first Ogród. Katalog referencyjny jest oddzielony od korekt użytkownika
 * i faktycznych nasadzeń, więc aktualizacja katalogu nie nadpisuje historii.
 */
final class GardenStore {
    private GardenStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS garden_areas ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "name TEXT NOT NULL COLLATE NOCASE UNIQUE, "
            + "kind TEXT NOT NULL DEFAULT 'Grządka', "
            + "notes TEXT NOT NULL DEFAULT '', "
            + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS garden_catalog ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "catalog_key TEXT NOT NULL UNIQUE, "
            + "name TEXT NOT NULL COLLATE NOCASE, "
            + "latin_name TEXT NOT NULL DEFAULT '', "
            + "variety TEXT NOT NULL DEFAULT '', "
            + "source_label TEXT NOT NULL DEFAULT '', "
            + "source_url TEXT NOT NULL DEFAULT '', "
            + "source_license TEXT NOT NULL DEFAULT '', "
            + "sow_from_month INTEGER NOT NULL DEFAULT 0 CHECK(sow_from_month BETWEEN 0 AND 12), "
            + "sow_to_month INTEGER NOT NULL DEFAULT 0 CHECK(sow_to_month BETWEEN 0 AND 12), "
            + "plant_from_month INTEGER NOT NULL DEFAULT 0 CHECK(plant_from_month BETWEEN 0 AND 12), "
            + "plant_to_month INTEGER NOT NULL DEFAULT 0 CHECK(plant_to_month BETWEEN 0 AND 12), "
            + "harvest_from_month INTEGER NOT NULL DEFAULT 0 CHECK(harvest_from_month BETWEEN 0 AND 12), "
            + "harvest_to_month INTEGER NOT NULL DEFAULT 0 CHECK(harvest_to_month BETWEEN 0 AND 12), "
            + "spacing_cm INTEGER NOT NULL DEFAULT 0 CHECK(spacing_cm BETWEEN 0 AND 10000), "
            + "depth_mm INTEGER NOT NULL DEFAULT 0 CHECK(depth_mm BETWEEN 0 AND 10000), "
            + "sunlight TEXT NOT NULL DEFAULT '', "
            + "watering TEXT NOT NULL DEFAULT '', "
            + "notes TEXT NOT NULL DEFAULT '', "
            + "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_catalog_name_idx "
            + "ON garden_catalog(name COLLATE NOCASE,variety COLLATE NOCASE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS garden_catalog_overrides ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "catalog_id INTEGER NOT NULL, "
            + "field_name TEXT NOT NULL, "
            + "value_text TEXT NOT NULL, "
            + "updated_at INTEGER NOT NULL, "
            + "UNIQUE(catalog_id,field_name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_override_catalog_idx "
            + "ON garden_catalog_overrides(catalog_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS garden_custom_plants ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "name TEXT NOT NULL COLLATE NOCASE, "
            + "latin_name TEXT NOT NULL DEFAULT '', "
            + "variety TEXT NOT NULL DEFAULT '', "
            + "notes TEXT NOT NULL DEFAULT '', "
            + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_custom_name_idx "
            + "ON garden_custom_plants(name COLLATE NOCASE,variety COLLATE NOCASE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS garden_plantings ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "area_id INTEGER NOT NULL, "
            + "catalog_id INTEGER NOT NULL DEFAULT 0, "
            + "custom_plant_id INTEGER NOT NULL DEFAULT 0, "
            + "label TEXT NOT NULL DEFAULT '', "
            + "status TEXT NOT NULL DEFAULT 'planned' "
            + "CHECK(status IN ('planned','sown','seedling','planted','active','harvesting','finished','cancelled')), "
            + "planned_sow TEXT NOT NULL DEFAULT '', "
            + "actual_sow TEXT NOT NULL DEFAULT '', "
            + "planned_plant TEXT NOT NULL DEFAULT '', "
            + "actual_plant TEXT NOT NULL DEFAULT '', "
            + "planned_harvest TEXT NOT NULL DEFAULT '', "
            + "actual_harvest TEXT NOT NULL DEFAULT '', "
            + "notes TEXT NOT NULL DEFAULT '', "
            + "created_at INTEGER NOT NULL, "
            + "CHECK((catalog_id>0 AND custom_plant_id=0) "
            + "OR (catalog_id=0 AND custom_plant_id>0)))");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_plantings_area_idx "
            + "ON garden_plantings(area_id,status,id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS garden_task_links ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "planting_id INTEGER NOT NULL, "
            + "task_id INTEGER NOT NULL, "
            + "created_at INTEGER NOT NULL, "
            + "UNIQUE(planting_id,task_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_task_links_planting_idx "
            + "ON garden_task_links(planting_id,task_id)");
    }

    static long addArea(SQLiteDatabase db, String name, String kind, String notes) {
        ContentValues v=new ContentValues();
        v.put("name", required(name,80,"Podaj nazwę obszaru."));
        String cleanKind=optional(kind,40);
        v.put("kind", cleanKind.isEmpty() ? "Grządka" : cleanKind);
        v.put("notes", optional(notes,1200));
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("garden_areas",null,v);
    }

    static long addCustomPlant(SQLiteDatabase db,String name,String latin,
            String variety,String notes) {
        ContentValues v=new ContentValues();
        v.put("name",required(name,100,"Podaj nazwę rośliny."));
        v.put("latin_name",optional(latin,140));
        v.put("variety",optional(variety,100));
        v.put("notes",optional(notes,2000));
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("garden_custom_plants",null,v);
    }

    static long addPlanting(SQLiteDatabase db,long areaId,long catalogId,
            long customPlantId,String label,String plannedSow,
            String plannedPlant,String plannedHarvest,String notes) {
        if(!exists(db,"garden_areas",areaId))
            throw new IllegalArgumentException("Wybrany obszar już nie istnieje.");
        if((catalogId>0)==(customPlantId>0))
            throw new IllegalArgumentException("Wybierz dokładnie jedną roślinę.");
        if(catalogId>0&&!exists(db,"garden_catalog",catalogId))
            throw new IllegalArgumentException("Pozycja katalogu już nie istnieje.");
        if(customPlantId>0&&!exists(db,"garden_custom_plants",customPlantId))
            throw new IllegalArgumentException("Roślina już nie istnieje.");
        validateDate(plannedSow); validateDate(plannedPlant); validateDate(plannedHarvest);
        ContentValues v=new ContentValues();
        v.put("area_id",areaId);
        v.put("catalog_id",Math.max(0,catalogId));
        v.put("custom_plant_id",Math.max(0,customPlantId));
        v.put("label",optional(label,100));
        v.put("status","planned");
        v.put("planned_sow",date(plannedSow));
        v.put("actual_sow","");
        v.put("planned_plant",date(plannedPlant));
        v.put("actual_plant","");
        v.put("planned_harvest",date(plannedHarvest));
        v.put("actual_harvest","");
        v.put("notes",optional(notes,2000));
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("garden_plantings",null,v);
    }

    static void putCatalogOverride(SQLiteDatabase db,long catalogId,String field,String value) {
        if(!exists(db,"garden_catalog",catalogId))
            throw new IllegalArgumentException("Pozycja katalogu już nie istnieje.");
        String clean=required(field,60,"Brak pola korekty.");
        if(!clean.matches("[a-z_]{2,60}"))
            throw new IllegalArgumentException("Nieprawidłowe pole korekty.");
        ContentValues v=new ContentValues();
        v.put("catalog_id",catalogId);
        v.put("field_name",clean);
        v.put("value_text",optional(value,4000));
        v.put("updated_at",System.currentTimeMillis());
        db.insertWithOnConflict("garden_catalog_overrides",null,v,
            SQLiteDatabase.CONFLICT_REPLACE);
    }

    static boolean validIsoDate(String value) {
        if(value==null||value.trim().isEmpty()) return true;
        try { String x=value.trim(); return LocalDate.parse(x).toString().equals(x); }
        catch(Exception invalid){ return false; }
    }

    private static void validateDate(String value) {
        if(!validIsoDate(value))
            throw new IllegalArgumentException("Data musi mieć format RRRR-MM-DD.");
    }
    private static String date(String value){ return value==null?"":value.trim(); }

    private static boolean exists(SQLiteDatabase db,String table,long id) {
        if(id<=0) return false;
        try(Cursor c=db.rawQuery("SELECT 1 FROM "+table+" WHERE id=? LIMIT 1",
                new String[]{Long.toString(id)})){ return c.moveToFirst(); }
    }

    private static String required(String value,int max,String message) {
        String x=value==null?"":value.trim();
        if(x.isEmpty()||x.length()>max||x.indexOf('\0')>=0)
            throw new IllegalArgumentException(message);
        return x;
    }
    private static String optional(String value,int max) {
        String x=value==null?"":value.trim();
        if(x.length()>max||x.indexOf('\0')>=0)
            throw new IllegalArgumentException("Tekst jest zbyt długi.");
        return x;
    }
}
