package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.*;

/** Wspólny model zimowy synchronizowany tak samo jak Magazyn i Miejsca. */
final class FrostStore {
    private FrostStore(){}
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE frost_settings (id INTEGER PRIMARY KEY, "
            +"start_month INTEGER NOT NULL DEFAULT 12, start_day INTEGER NOT NULL DEFAULT 1, "
            +"lead_days INTEGER NOT NULL DEFAULT 30, remind_start INTEGER NOT NULL DEFAULT 1, "
            +"remind_before INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("INSERT INTO frost_settings(id) VALUES(1)");
        db.execSQL("CREATE TABLE frost_checks (id INTEGER PRIMARY KEY, "
            +"season_year INTEGER NOT NULL, item_id INTEGER NOT NULL, "
            +"done INTEGER NOT NULL DEFAULT 0 CHECK(done IN (0,1)), "
            +"changed_at INTEGER NOT NULL, UNIQUE(season_year,item_id))");
        db.execSQL("CREATE INDEX frost_checks_season ON frost_checks(season_year,item_id)");
    }
    static void upgrade47(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE places ADD COLUMN frost_risk INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE storage_items ADD COLUMN protect_frost INTEGER NOT NULL DEFAULT 0");
        create(db);
    }
    static final class Settings {
        final int month,day,lead;final boolean remindStart,remindBefore;
        Settings(int month,int day,int lead,boolean first,boolean before) {
            this.month=month;this.day=day;this.lead=lead;
            remindStart=first;remindBefore=before;
        }
    }
    static Settings settings(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT start_month,start_day,lead_days,"
                +"remind_start,remind_before FROM frost_settings WHERE id=1",null)){
            if(c.moveToFirst())return new Settings(c.getInt(0),c.getInt(1),c.getInt(2),
                c.getInt(3)==1,c.getInt(4)==1);
        }
        return new Settings(12,1,30,true,true);
    }
    static void saveSettings(SQLiteDatabase db,int month,int day,int lead,
            boolean remindStart,boolean remindBefore){
        if(month<1||month>12||day<1||day>28||lead<1||lead>90)
            throw new IllegalArgumentException("Dzień 1–28, przygotowanie 1–90 dni.");
        ContentValues row=new ContentValues();
        row.put("id",1);row.put("start_month",month);row.put("start_day",day);
        row.put("lead_days",lead);row.put("remind_start",remindStart?1:0);
        row.put("remind_before",remindBefore?1:0);
        db.insertWithOnConflict("frost_settings",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    static LocalDate due(Settings settings,int year){
        return LocalDate.of(year,settings.month,settings.day).minusDays(settings.lead);
    }
    static boolean explicitRisk(SQLiteDatabase db,long id){
        try(Cursor c=db.rawQuery("SELECT frost_risk FROM places WHERE id=?",
                new String[]{Long.toString(id)})){
            return c.moveToFirst()&&c.getInt(0)==1;
        }
    }
    static boolean placeRisk(SQLiteDatabase db,Long placeId){
        Set<Long>seen=new HashSet<>();
        while(placeId!=null&&seen.add(placeId)&&seen.size()<128){
            try(Cursor c=db.rawQuery("SELECT parent_id,frost_risk FROM places WHERE id=?",
                    new String[]{Long.toString(placeId)})){
                if(!c.moveToFirst())return false;
                if(c.getInt(1)==1)return true;
                placeId=c.isNull(0)?null:c.getLong(0);
            }
        }
        return false;
    }
    static void setProtection(SQLiteDatabase db,long id,boolean yes){
        ContentValues v=new ContentValues();v.put("protect_frost",yes?1:0);
        if(db.update("storage_items",v,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Rzecz nie istnieje.");
    }
    static boolean sensitive(SQLiteDatabase db,long id){
        StorageStore.Item item=StorageStore.find(db,id);
        if(item==null)return false;
        if(item.protectFrost)return true;
        if(!"box".equals(item.kind))return false;
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM storage_items WHERE "
                +"parent_box_id=? AND protect_frost=1",new String[]{Long.toString(id)})){
            return c.moveToFirst()&&c.getInt(0)>0;
        }
    }
    static boolean riskAtDestination(SQLiteDatabase db,Long box,Long place){
        if(box!=null)place=StorageStore.effectivePlaceId(db,StorageStore.find(db,box));
        return placeRisk(db,place);
    }
    static boolean moveRisk(SQLiteDatabase db,long itemId,Long box,Long place){
        return sensitive(db,itemId)&&riskAtDestination(db,box,place);
    }
    static final class Entry{
        final long id;final String name,location;final boolean box,done;
        Entry(long id,String name,String location,boolean box,boolean done){
            this.id=id;this.name=name;this.location=location;this.box=box;this.done=done;
        }
    }
    static List<Entry> entries(SQLiteDatabase db,int year){
        Map<Long,Boolean>done=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT item_id,done FROM frost_checks WHERE season_year=?",
                new String[]{Integer.toString(year)})){
            while(c.moveToNext())done.put(c.getLong(0),c.getInt(1)==1);
        }
        List<StorageStore.Item>items=StorageStore.listAll(db);
        Set<Long>boxes=new HashSet<>();
        for(StorageStore.Item item:items)
            if(item.protectFrost&&item.boxId!=null)boxes.add(item.boxId);
        List<Entry>out=new ArrayList<>();
        for(StorageStore.Item item:items)
            if(item.protectFrost||boxes.contains(item.id))
                out.add(new Entry(item.id,item.name,StorageStore.location(db,item),
                    "box".equals(item.kind),Boolean.TRUE.equals(done.get(item.id))));
        return out;
    }
    static int pending(SQLiteDatabase db,int year){
        int n=0;for(Entry e:entries(db,year))if(!e.done)n++;return n;
    }
    static void checked(SQLiteDatabase db,int year,long itemId,boolean done){
        if(year<2000||year>2100||!sensitive(db,itemId))
            throw new IllegalArgumentException("Nieprawidłowa pozycja zabezpieczenia.");
        ContentValues v=new ContentValues();
        v.put("id",year*1000000000000L+itemId);
        v.put("season_year",year);v.put("item_id",itemId);
        v.put("done",done?1:0);v.put("changed_at",System.currentTimeMillis());
        db.insertWithOnConflict("frost_checks",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
}
