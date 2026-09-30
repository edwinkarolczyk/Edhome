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
            + "stage TEXT NOT NULL CHECK(stage IN ('sow','plant','harvest')), "
            + "task_id INTEGER NOT NULL, "
            + "created_at INTEGER NOT NULL, "
            + "UNIQUE(planting_id,stage), UNIQUE(planting_id,task_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS garden_task_links_planting_idx "
            + "ON garden_task_links(planting_id,stage,task_id)");
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

    /**
     * Mirror planned garden dates into the existing Tasks engine. This gives
     * the garden one calendar/notification source instead of a second scheduler.
     * Re-running is idempotent: linked tasks are updated, not duplicated.
     */
    static int syncPlanTasks(SQLiteDatabase db,long plantingId,String remindTime,int leadDays) {
        if(remindTime!=null && (!ReminderRules.validTime(remindTime)
                || !ReminderRules.allowedLead(leadDays)))
            throw new IllegalArgumentException("Nieprawidłowe ustawienie powiadomienia.");

        String plantName,areaName,plannedSow,plannedPlant,plannedHarvest;
        try(Cursor c=db.rawQuery(
                "SELECT COALESCE(NULLIF(gc.name,''),NULLIF(cp.name,''),'Roślina'),"
                + "a.name,p.planned_sow,p.planned_plant,p.planned_harvest "
                + "FROM garden_plantings p JOIN garden_areas a ON a.id=p.area_id "
                + "LEFT JOIN garden_catalog gc ON gc.id=p.catalog_id "
                + "LEFT JOIN garden_custom_plants cp ON cp.id=p.custom_plant_id "
                + "WHERE p.id=?",
                new String[]{Long.toString(plantingId)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Nasadzenie już nie istnieje.");
            plantName=c.getString(0);
            areaName=c.getString(1);
            plannedSow=c.getString(2);
            plannedPlant=c.getString(3);
            plannedHarvest=c.getString(4);
        }

        String[][] stages={
            {"sow","Siew",plannedSow},
            {"plant","Sadzenie",plannedPlant},
            {"harvest","Zbiór",plannedHarvest}
        };
        int linked=0;
        db.beginTransaction();
        try {
            for(String[] stage:stages) {
                String due=stage[2];
                if(due==null||due.isEmpty()) continue;
                validateDate(due);
                String title="Ogród • "+stage[1]+": "+plantName+" • "+areaName;

                Long taskId=null;
                try(Cursor existing=db.rawQuery(
                        "SELECT l.task_id FROM garden_task_links l "
                        + "JOIN tasks t ON t.id=l.task_id "
                        + "WHERE l.planting_id=? AND l.stage=? LIMIT 1",
                        new String[]{Long.toString(plantingId),stage[0]})) {
                    if(existing.moveToFirst()) taskId=existing.getLong(0);
                }

                ContentValues task=new ContentValues();
                task.put("title",title);
                task.put("done",0);
                task.put("due_date",due);
                task.put("repeat_rule","once");
                task.put("repeat_every",1);
                task.put("priority","normal");
                task.put("duration_minutes","harvest".equals(stage[0])?45:30);
                task.put("task_kind","general");
                task.putNull("waste_fraction");
                if(remindTime==null) {
                    task.putNull("remind_time");
                    task.put("reminder_lead_days",0);
                } else {
                    task.put("remind_time",remindTime);
                    task.put("reminder_lead_days",leadDays);
                }

                if(taskId==null) {
                    taskId=db.insertOrThrow("tasks",null,task);
                    ContentValues link=new ContentValues();
                    link.put("planting_id",plantingId);
                    link.put("stage",stage[0]);
                    link.put("task_id",taskId);
                    link.put("created_at",System.currentTimeMillis());
                    db.insertOrThrow("garden_task_links",null,link);
                } else {
                    db.update("tasks",task,"id=?",new String[]{Long.toString(taskId)});
                }
                linked++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return linked;
    }

    static int linkedTaskCount(SQLiteDatabase db,long plantingId) {
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM garden_task_links l "
                + "JOIN tasks t ON t.id=l.task_id WHERE l.planting_id=?",
                new String[]{Long.toString(plantingId)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static int importCatalogCsv(SQLiteDatabase db,String csv) {
        java.util.List<GardenCatalogCsv.Entry> entries=GardenCatalogCsv.parse(csv);
        db.beginTransaction();
        try {
            for(GardenCatalogCsv.Entry e:entries) {
                ContentValues v=new ContentValues();
                v.put("catalog_key",e.key);
                v.put("name",e.name);
                v.put("latin_name",e.latin);
                v.put("variety",e.variety);
                v.put("source_label",e.source);
                v.put("source_url",e.url);
                v.put("source_license",e.license);
                v.put("sow_from_month",e.sowFrom);
                v.put("sow_to_month",e.sowTo);
                v.put("plant_from_month",e.plantFrom);
                v.put("plant_to_month",e.plantTo);
                v.put("harvest_from_month",e.harvestFrom);
                v.put("harvest_to_month",e.harvestTo);
                v.put("spacing_cm",e.spacing);
                v.put("depth_mm",e.depth);
                v.put("sunlight",e.sunlight);
                v.put("watering",e.watering);
                v.put("notes",e.notes);
                v.put("updated_at",System.currentTimeMillis());
                db.insertWithOnConflict("garden_catalog",null,v,
                    SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return entries.size();
    }

    static String exportCatalogCsv(SQLiteDatabase db) {
        StringBuilder out=new StringBuilder(GardenCatalogCsv.HEADER).append('\n');
        try(Cursor c=db.rawQuery(
                "SELECT catalog_key,name,latin_name,variety,source_label,source_url,"
                +"source_license,sow_from_month,sow_to_month,plant_from_month,plant_to_month,"
                +"harvest_from_month,harvest_to_month,spacing_cm,depth_mm,sunlight,watering,notes "
                +"FROM garden_catalog ORDER BY name COLLATE NOCASE,variety COLLATE NOCASE",null)) {
            while(c.moveToNext()) {
                out.append(GardenCatalogCsv.row(
                    c.getString(0),c.getString(1),c.getString(2),c.getString(3),
                    c.getString(4),c.getString(5),c.getString(6),
                    Integer.toString(c.getInt(7)),Integer.toString(c.getInt(8)),
                    Integer.toString(c.getInt(9)),Integer.toString(c.getInt(10)),
                    Integer.toString(c.getInt(11)),Integer.toString(c.getInt(12)),
                    Integer.toString(c.getInt(13)),Integer.toString(c.getInt(14)),
                    c.getString(15),c.getString(16),c.getString(17))).append('\n');
            }
        }
        return out.toString();
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
