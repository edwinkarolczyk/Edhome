package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            +"protect_frost INTEGER NOT NULL DEFAULT 0, "
            +"return_required INTEGER NOT NULL DEFAULT 0 CHECK(return_required IN (0,1)), "
            +"return_after_days INTEGER NOT NULL DEFAULT 1 CHECK(return_after_days BETWEEN 1 AND 365), "
            +"taken_at INTEGER CHECK(taken_at IS NULL OR taken_at>0), "
            + "CHECK(parent_box_id IS NULL OR place_id IS NULL))");
        db.execSQL("CREATE TABLE storage_events ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, "
            + "name_snapshot TEXT NOT NULL, action TEXT NOT NULL "
            + "CHECK(action IN ('created','moved','lent','returned','removed')), "
            + "details TEXT NOT NULL, happened_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX storage_items_box_idx "
            + "ON storage_items(parent_box_id)");
        ensurePerformanceIndexes(db);
    }

    static void ensurePerformanceIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS storage_items_kind_name_idx "
            + "ON storage_items(kind,name COLLATE NOCASE,id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS storage_items_place_idx "
            + "ON storage_items(place_id)");
    }

    static final class Item {
        final long id;
        final String name;
        final String kind;
        final Long boxId;
        final Long placeId;
        final String lentTo;
        final boolean protectFrost;
        final boolean returnRequired;
        final int returnAfterDays;
        final Long takenAt;
        Item(long id, String name, String kind, Long boxId, Long placeId,
                String lentTo,boolean protectFrost,boolean returnRequired,
                int returnAfterDays,Long takenAt) {
            this.id=id; this.name=name; this.kind=kind;
            this.boxId=boxId; this.placeId=placeId; this.lentTo=lentTo;
            this.protectFrost=protectFrost;
            this.returnRequired=returnRequired;
            this.returnAfterDays=returnAfterDays;
            this.takenAt=takenAt;
        }
    }

    static Item find(SQLiteDatabase db, long id) {
        try (Cursor c=db.rawQuery("SELECT id,name,kind,parent_box_id,place_id,lent_to,protect_frost,return_required,return_after_days,taken_at "
                + "FROM storage_items WHERE id=?", new String[]{Long.toString(id)})) {
            return c.moveToFirst()
                ? new Item(c.getLong(0),c.getString(1),c.getString(2),
                    c.isNull(3)?null:c.getLong(3),
                    c.isNull(4)?null:c.getLong(4),c.getString(5),c.getInt(6)==1,c.getInt(7)==1,c.getInt(8),
                    c.isNull(9)?null:c.getLong(9)) : null;
        }
    }

    /**
     * Zbiorczy odczyt do ekranów list/galerii. Nie wykonuj StorageStore.find()
     * w pętli renderującej — przy setkach rzeczy tworzyłoby to N+1 zapytań SQLite.
     */
    static List<Item> listAll(SQLiteDatabase db) {
        List<Item> items=new ArrayList<>();
        try (Cursor c=db.rawQuery(
                "SELECT id,name,kind,parent_box_id,place_id,lent_to,protect_frost,return_required,return_after_days,taken_at "
                    + "FROM storage_items ORDER BY kind,name COLLATE NOCASE,id",
                null)) {
            while(c.moveToNext())
                items.add(new Item(c.getLong(0),c.getString(1),c.getString(2),
                    c.isNull(3)?null:c.getLong(3),
                    c.isNull(4)?null:c.getLong(4),c.getString(5),c.getInt(6)==1,c.getInt(7)==1,c.getInt(8),
                    c.isNull(9)?null:c.getLong(9)));
        }
        return items;
    }

    private static String validName(String name) {
        String clean=TextEntryRules.capitalizeLabel(name);
        if (clean.isEmpty() || clean.length()>160)
            throw new IllegalArgumentException("Podaj nazwę 1–160 znaków.");
        return clean;
    }

    private static void validKindDestination(String kind, Long box, Long place) {
        if ("box".equals(kind) && box!=null)
            throw new IllegalArgumentException(
                "Pudełko można przypisać tylko do miejsca, nie do innego pudełka.");
        if ("thing".equals(kind) || "box".equals(kind)) return;
        throw new IllegalArgumentException("Nieznany typ magazynu.");
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
        validKindDestination(kind,box,place);
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

    static void rename(SQLiteDatabase db,long id,String name) {
        String clean=validName(name);
        ContentValues row=new ContentValues();
        row.put("name",clean);
        if(db.update("storage_items",row,"id=?",
                new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Rzecz nie istnieje.");
    }

    static boolean sameDestination(Item item,Long box,Long place) {
        return item!=null
            &&java.util.Objects.equals(item.boxId,box)
            &&java.util.Objects.equals(item.placeId,place);
    }

    static Long effectivePlaceId(SQLiteDatabase db,Item item) {
        if(item==null)return null;
        Set<Long> visited=new HashSet<>();
        Item current=item;
        for(int depth=0;current!=null&&depth<128;depth++) {
            if(!visited.add(current.id))return null;
            if(current.placeId!=null)return current.placeId;
            if(current.boxId==null)return null;
            current=find(db,current.boxId);
        }
        return null;
    }

    static void move(SQLiteDatabase db,long id,Long box,Long place) {
        db.beginTransaction();
        try {
            Item item=find(db,id);
            if(item==null)throw new IllegalArgumentException("Rzecz nie istnieje.");
            if(item.lentTo!=null)throw new IllegalArgumentException(
                "Najpierw odnotuj zwrot wypożyczonej rzeczy.");
            if(item.takenAt!=null)throw new IllegalArgumentException(
                "Najpierw oznacz odłożenie rzeczy. Potem przenieś ją na stałe.");
            validKindDestination(item.kind,box,place);
            validDestination(db,box,place,id);
            if(sameDestination(item,box,place)) {
                db.setTransactionSuccessful();
                return;
            }
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
            if(item.takenAt!=null)
                throw new IllegalArgumentException("Najpierw odłóż rzecz na miejsce.");
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
            if(item.takenAt!=null)
                throw new IllegalArgumentException("Najpierw oznacz odłożenie rzeczy.");
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM storage_items WHERE parent_box_id=? LIMIT 1",
                    new String[]{Long.toString(id)})) {
                if(c.moveToFirst())
                    throw new IllegalArgumentException("Pudełko nie jest puste.");
            }
            log(db,item,"removed","Usunięto z magazynu");
            NfcLinkStore.clearTarget(db,item.kind,item.id);
            if(db.delete("storage_items","id=?",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Nie usunięto.");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Domyślnie wyłączone; przypomnienie po pełnych dniach kalendarzowych. */
    static void setReturnRule(SQLiteDatabase db,long id,boolean required,int days) {
        if(days<1||days>365)throw new IllegalArgumentException(
            "Termin odłożenia musi wynosić 1–365 dni.");
        Item item=find(db,id);
        if(item==null||!"thing".equals(item.kind))
            throw new IllegalArgumentException("Nie znaleziono rzeczy.");
        if(!required&&item.takenAt!=null)
            throw new IllegalArgumentException(
                "Najpierw odłóż rzecz na miejsce, potem wyłącz przypomnienia.");
        ContentValues fields=new ContentValues();
        fields.put("return_required",required?1:0);
        fields.put("return_after_days",days);
        if(db.update("storage_items",fields,"id=?",
                new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Nie zapisano ustawień rzeczy.");
    }

    /** Zachowujemy oryginalne powiązanie z miejscem/pudełkiem. */
    static void takeTemporarily(SQLiteDatabase db,long id) {
        db.beginTransaction();
        try {
            Item item=find(db,id);
            if(item==null||!"thing".equals(item.kind))
                throw new IllegalArgumentException("Rzecz nie istnieje.");
            if(!item.returnRequired)
                throw new IllegalArgumentException("Włącz ustawienie powrotu w edycji rzeczy.");
            if(item.takenAt!=null)
                throw new IllegalArgumentException("Ta rzecz jest już poza miejscem.");
            if(item.boxId==null&&item.placeId==null)
                throw new IllegalArgumentException("Najpierw przypisz rzecz do miejsca.");
            if(item.lentTo!=null)
                throw new IllegalArgumentException("Najpierw odnotuj zwrot wypożyczenia.");
            ContentValues fields=new ContentValues();
            fields.put("taken_at",System.currentTimeMillis());
            if(db.update("storage_items",fields,"id=? AND taken_at IS NULL",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Nie zapisano wyjęcia rzeczy.");
            log(db,item,"moved","Wzięte czasowo; ma wrócić na swoje miejsce");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Zwrot kończy przypomnienia, ale nie zmienia stałej lokalizacji. */
    static void putBack(SQLiteDatabase db,long id) {
        db.beginTransaction();
        try {
            Item item=find(db,id);
            if(item==null||item.takenAt==null)
                throw new IllegalArgumentException("Rzecz nie jest oznaczona jako wzięta.");
            if(item.boxId!=null) {
                Item box=find(db,item.boxId);
                if(box==null||!"box".equals(box.kind))
                    throw new IllegalArgumentException("Pudełko nie istnieje. Wybierz nowe miejsce.");
            }else if(item.placeId!=null) {
                try(Cursor c=db.rawQuery("SELECT 1 FROM places WHERE id=?",
                        new String[]{Long.toString(item.placeId)})) {
                    if(!c.moveToFirst())
                        throw new IllegalArgumentException("Miejsce nie istnieje. Wybierz nowe.");
                }
            }
            ContentValues fields=new ContentValues();
            fields.putNull("taken_at");
            if(db.update("storage_items",fields,"id=? AND taken_at IS NOT NULL",
                    new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Nie zapisano odłożenia.");
            log(db,item,"returned","Odłożono na swoje miejsce");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Idempotentna migracja bazy 49→50. */
    static void upgrade50(SQLiteDatabase db) {
        ensureColumn(db,"return_required",
            "INTEGER NOT NULL DEFAULT 0 CHECK(return_required IN (0,1))");
        ensureColumn(db,"return_after_days",
            "INTEGER NOT NULL DEFAULT 1 CHECK(return_after_days BETWEEN 1 AND 365)");
        ensureColumn(db,"taken_at","INTEGER CHECK(taken_at IS NULL OR taken_at>0)");
    }

    private static void ensureColumn(SQLiteDatabase db,String name,String definition) {
        try(Cursor c=db.rawQuery("PRAGMA table_info(storage_items)",null)) {
            while(c.moveToNext())if(name.equals(c.getString(1)))return;
        }
        db.execSQL("ALTER TABLE storage_items ADD COLUMN "+name+" "+definition);
    }

    static void assertIntegrity(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery(
                "SELECT id,kind,parent_box_id,place_id,lent_to,lent_at "
                    +"FROM storage_items ORDER BY id",null)) {
            while(c.moveToNext()) {
                long id=c.getLong(0);
                String kind=c.getString(1);
                Long box=c.isNull(2)?null:c.getLong(2);
                Long place=c.isNull(3)?null:c.getLong(3);
                String lent=c.getString(4);
                Long lentAt=c.isNull(5)?null:c.getLong(5);
                if(!"thing".equals(kind)&&!"box".equals(kind))
                    throw new IllegalArgumentException(
                        "Magazyn zawiera nieznany typ obiektu.");
                if(box!=null&&place!=null)
                    throw new IllegalArgumentException(
                        "Obiekt magazynu ma dwa położenia jednocześnie.");
                if("box".equals(kind)&&box!=null)
                    throw new IllegalArgumentException(
                        "Pudełko nie może znajdować się w innym pudełku.");
                if((lent==null)!=(lentAt==null)||lent!=null&&!"thing".equals(kind))
                    throw new IllegalArgumentException(
                        "Niespójny stan wypożyczenia w magazynie.");
                if(box!=null) {
                    Item parent=find(db,box);
                    if(parent==null||!"box".equals(parent.kind)||parent.id==id)
                        throw new IllegalArgumentException(
                            "Rzecz wskazuje nieistniejące pudełko.");
                }
                if(place!=null) try(Cursor p=db.rawQuery(
                        "SELECT 1 FROM places WHERE id=? LIMIT 1",
                        new String[]{Long.toString(place)})) {
                    if(!p.moveToFirst())
                        throw new IllegalArgumentException(
                            "Rzecz lub pudełko wskazuje nieistniejące miejsce.");
                }
            }
        }
    }


    /**
     * Zbiorczo wylicza lokalizacje bez zapytania SQL dla każdego kafelka.
     * Galeria może dzięki temu przeszukać setki rzeczy po jednym odczycie
     * storage_items i jednym odczycie places.
     */
    static Map<Long,String> locations(SQLiteDatabase db,List<Item> targets) {
        Map<Long,Item> items=new HashMap<>();
        try(Cursor cursor=db.rawQuery(
                "SELECT id,name,kind,parent_box_id,place_id,lent_to,protect_frost,return_required,return_after_days,taken_at FROM storage_items",
                null)) {
            while(cursor.moveToNext()) {
                Item item=new Item(cursor.getLong(0),cursor.getString(1),
                    cursor.getString(2),
                    cursor.isNull(3)?null:cursor.getLong(3),
                    cursor.isNull(4)?null:cursor.getLong(4),
                    cursor.getString(5),cursor.getInt(6)==1,cursor.getInt(7)==1,
                    cursor.getInt(8),cursor.isNull(9)?null:cursor.getLong(9));
                items.put(item.id,item);
            }
        }

        Map<Long,PlaceNode> places=new HashMap<>();
        try(Cursor cursor=db.rawQuery("SELECT id,name,parent_id FROM places",null)) {
            while(cursor.moveToNext())
                places.put(cursor.getLong(0),new PlaceNode(
                    cursor.getString(1),
                    cursor.isNull(2)?null:cursor.getLong(2)));
        }

        Map<Long,String> result=new HashMap<>();
        if(targets==null)return result;
        for(Item target:targets)
            if(target!=null)
                result.put(target.id,locationFromMaps(target,items,places));
        return result;
    }

    private static String locationFromMaps(Item item,Map<Long,Item> items,
            Map<Long,PlaceNode> places) {
        if(item==null)return "Nie znaleziono";
        StringBuilder path=new StringBuilder();
        Set<Long> visited=new HashSet<>();
        Item current=item;
        for(int depth=0;current!=null&&depth<128;depth++) {
            if(!visited.add(current.id))return "Błąd: cykl pudełek";
            if(path.length()>0)path.insert(0," / ");
            path.insert(0,current.name);
            if(current.placeId!=null) {
                String place=placePathFromMaps(current.placeId,places);
                path.insert(0,place+" / ");
                return path.toString();
            }
            if(current.boxId==null)return "Bez miejsca / "+path;
            current=items.get(current.boxId);
            if(current==null)return "Nieznana lokalizacja";
        }
        return "Nieznana lokalizacja";
    }

    private static String placePathFromMaps(long placeId,
            Map<Long,PlaceNode> places) {
        StringBuilder result=new StringBuilder();
        Set<Long> seen=new HashSet<>();
        Long id=placeId;
        for(int depth=0;id!=null&&depth<128;depth++) {
            if(!seen.add(id))return "Błąd: cykl miejsc";
            PlaceNode node=places.get(id);
            if(node==null)return "Nieznane miejsce";
            if(result.length()>0)result.insert(0," / ");
            result.insert(0,node.name);
            id=node.parentId;
        }
        return id==null?result.toString():"Zbyt głęboka hierarchia miejsc";
    }

    private static final class PlaceNode {
        final String name;
        final Long parentId;
        PlaceNode(String name,Long parentId) {
            this.name=name;
            this.parentId=parentId;
        }
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
