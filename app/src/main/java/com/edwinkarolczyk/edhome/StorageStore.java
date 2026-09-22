package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Set;

/** Local object/box inventory. Children inherit place from their box, never copy it. */
final class StorageStore {
    private StorageStore() { }

    static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE storage_items ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "name TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('thing','box')), "
            + "parent_box_id INTEGER, place_id INTEGER, "
            + "lent_to TEXT, lent_at INTEGER, created_at INTEGER NOT NULL, "
            + "CHECK(parent_box_id IS NULL OR place_id IS NULL))");
        db.execSQL("CREATE TABLE storage_events ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, "
            + "name_snapshot TEXT NOT NULL, action TEXT NOT NULL "
            + "CHECK(action IN ('created','moved','lent','returned','removed')), "
            + "details TEXT NOT NULL, happened_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX storage_items_box_idx "
            + "ON storage_items(parent_box_id)");
    }

    static final class Item {
        final long id;
        final String name;
        final String kind;
        final Long boxId;
        final Long placeId;
        final String lentTo;
        Item(long id, String name, String kind, Long boxId, Long placeId,
                String lentTo) {
            this.id=id; this.name=name; this.kind=kind;
            this.boxId=boxId; this.placeId=placeId; this.lentTo=lentTo;
        }
    }

    static Item find(SQLiteDatabase db, long id) {
        try (Cursor c=db.rawQuery("SELECT id,name,kind,parent_box_id,place_id,lent_to "
                + "FROM storage_items WHERE id=?", new String[]{Long.toString(id)})) {
            return c.moveToFirst()
                ? new Item(c.getLong(0),c.getString(1),c.getString(2),
                    c.isNull(3)?null:c.getLong(3),
                    c.isNull(4)?null:c.getLong(4),c.getString(5)) : null;
        }
    }

    private static String validName(String name) {
        if (name==null || name.trim().isEmpty() || name.trim().length()>160)
            throw new IllegalArgumentException("Podaj nazwę 1–160 znaków.");
        return name.trim();
    }

    private static void validDestination(SQLiteDatabase db, Long box, Long place,
            Long movedId) {
        if (box!=null && place!=null)
            throw new IllegalArgumentException("Wybierz pudełko albo miejsce.");
        if (place!=null) try (Cursor c=db.rawQuery(
                "SELECT 1 FROM places WHERE id=?", new String[]{Long.toString(place)})) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("Miejsce nie istnieje.");
        }
        if (box==null) return;
        Set<Long> ancestors=new HashSet<>();
        Long cursorId=box;
        for (int i=0;cursorId!=null && i<128;i++) {
            if (!ancestors.add(cursorId) || cursorId.equals(movedId))
                throw new IllegalArgumentException("Nie można umieścić pudełka w sobie.");
            Item parent=find(db,cursorId);
            if (parent==null || !"box".equals(parent.kind) || parent.lentTo!=null)
                throw new IllegalArgumentException("Pudełko docelowe jest niedostępne.");
            cursorId=parent.boxId;
        }
        if (cursorId!=null)
            throw new IllegalArgumentException("Zbyt głęboka hierarchia pudełek.");
    }

    private static void log(SQLiteDatabase db, Item item,String action,String details) {
        ContentValues row=new ContentValues();
        row.put("item_id",item.id);
        row.put("name_snapshot",item.name);
        row.put("action",action);
        row.put("details",details);
        row.put("happened_at",System.currentTimeMillis());
        db.insertOrThrow("storage_events",null,row);
    }

    static long create(SQLiteDatabase db,String name,String kind,Long box,Long place) {
        name=validName(name);
        if (!"thing".equals(kind) && !"box".equals(kind))
            throw new IllegalArgumentException("Wybierz rzecz lub pudełko.");
        db.beginTransaction();
        try {
            validDestination(db,box,place,null);
            ContentValues row=new ContentValues();
            row.put("name",name); row.put("kind",kind);
            if(box!=null)row.put("parent_box_id",box);
            if(place!=null)row.put("place_id",place);
            row.put("created_at",System.currentTimeMillis());
            long id=db.insertOrThrow("storage_items",null,row);
            log(db,find(db,id),"created","Dodano do magazynu");
            db.setTransactionSuccessful();
            return id;
        }finally{db.endTransaction();}
    }

    static void move(SQLiteDatabase db,long id,Long box,Long place) {
        db.beginTransaction();
        try {
            Item item=find(db,id);
            if(item==null)throw new IllegalArgumentException("Rzecz nie istnieje.");
            if(item.lentTo!=null)throw new IllegalArgumentException(
                "Najpierw odnotuj zwrot wypożyczonej rzeczy.");
            validDestination(db,box,place,id);
            ContentValues row=new ContentValues();
            if(box==null)row.putNull("parent_box_id");else row.put("parent_box_id",box);
            if(place==null)row.putNull("place_id");else row.put("place_id",place);
            if(db.update("storage_items",row,"id=?",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Nie zapisano przeniesienia.");
            log(db,item,"moved","Zmieniono położenie");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    static void lend(SQLiteDatabase db,long id,String recipient) {
        recipient=validName(recipient);
        if(recipient.length()>80)
            throw new IllegalArgumentException("Wpisz maksymalnie 80 znaków.");
        db.beginTransaction();
        try{
            Item item=find(db,id);
            if(item==null || !"thing".equals(item.kind))
                throw new IllegalArgumentException("Wypożyczamy tylko istniejące rzeczy.");
            if(item.lentTo!=null)
                throw new IllegalArgumentException("Rzecz jest już wypożyczona.");
            ContentValues values=new ContentValues();
            values.put("lent_to",recipient);
            values.put("lent_at",System.currentTimeMillis());
            if(db.update("storage_items",values,"id=? AND lent_to IS NULL",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Stan wypożyczenia zmienił się.");
            log(db,item,"lent","Wypożyczono: "+recipient);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    static void returned(SQLiteDatabase db,long id) {
        db.beginTransaction();
        try{
            Item item=find(db,id);
            if(item==null || item.lentTo==null)
                throw new IllegalArgumentException("Rzecz nie jest wypożyczona.");
            ContentValues values=new ContentValues();
            values.putNull("lent_to");values.putNull("lent_at");
            if(db.update("storage_items",values,"id=? AND lent_to IS NOT NULL",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Stan wypożyczenia zmienił się.");
            log(db,item,"returned","Zwrot od: "+item.lentTo);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    static void remove(SQLiteDatabase db,long id) {
        db.beginTransaction();
        try{
            Item item=find(db,id);
            if(item==null)throw new IllegalArgumentException("Rzecz nie istnieje.");
            if(item.lentTo!=null)
                throw new IllegalArgumentException("Najpierw odnotuj zwrot.");
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM storage_items WHERE parent_box_id=? LIMIT 1",
                    new String[]{Long.toString(id)})) {
                if(c.moveToFirst())
                    throw new IllegalArgumentException("Pudełko nie jest puste.");
            }
            log(db,item,"removed","Usunięto z magazynu");
            if(db.delete("storage_items","id=?",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Nie usunięto.");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private static String placePath(SQLiteDatabase db,long placeId) {
        StringBuilder result=new StringBuilder();
        Set<Long> seen=new HashSet<>();
        Long id=placeId;
        for(int depth=0;id!=null && depth<128;depth++){
            if(!seen.add(id))return "Błąd: cykl miejsc";
            try(Cursor c=db.rawQuery(
                    "SELECT name,parent_id FROM places WHERE id=?",
                    new String[]{Long.toString(id)})){
                if(!c.moveToFirst())return "Nieznane miejsce";
                if(result.length()>0)result.insert(0," / ");
                result.insert(0,c.getString(0));
                id=c.isNull(1)?null:c.getLong(1);
            }
        }
        return id==null?result.toString():"Zbyt głęboka hierarchia miejsc";
    }

    static String location(SQLiteDatabase db,Item item) {
        if(item==null)return "Nie znaleziono";
        StringBuilder path=new StringBuilder();
        Set<Long> visited=new HashSet<>();
        Item current=item;
        for(int depth=0;current!=null && depth<128;depth++){
            if(!visited.add(current.id))return "Błąd: cykl pudełek";
            if(path.length()>0)path.insert(0," / ");
            path.insert(0,current.name);
            if(current.placeId!=null){
                path.insert(0,placePath(db,current.placeId)+" / ");
                return path.toString();
            }
            if(current.boxId==null)return "Bez miejsca / "+path;
            current=find(db,current.boxId);
        }
        return "Nieznana lokalizacja";
    }
}
