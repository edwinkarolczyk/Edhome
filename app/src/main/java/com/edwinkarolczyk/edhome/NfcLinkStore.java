package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** One physical NFC UID points to exactly one EDHOME object. */
final class NfcLinkStore {
    private NfcLinkStore() { }

    static final class Link {
        final long id;
        final String uid;
        final String kind;
        final long targetId;
        Link(long id,String uid,String kind,long targetId) {
            this.id=id;
            this.uid=uid;
            this.kind=kind;
            this.targetId=targetId;
        }
    }

    static String uid(byte[] raw) {
        if(raw==null || raw.length==0)
            throw new IllegalArgumentException("Tag NFC nie udostępnił UID.");
        StringBuilder out=new StringBuilder(raw.length*2);
        for(byte value:raw) out.append(String.format(
            Locale.ROOT,"%02X",value & 0xFF));
        return out.toString();
    }

    static String normalize(String raw) {
        String uid=raw==null?"":raw.replaceAll("[^0-9A-Fa-f]","")
            .toUpperCase(Locale.ROOT);
        if(uid.length()<4 || uid.length()>64 || (uid.length()&1)!=0)
            throw new IllegalArgumentException("Nieprawidłowy UID NFC.");
        return uid;
    }

    static Link findByUid(SQLiteDatabase db,String rawUid) {
        String uid=normalize(rawUid);
        try(Cursor c=db.rawQuery(
                "SELECT id,uid,target_kind,target_id FROM nfc_links "
                +"WHERE uid=? COLLATE NOCASE LIMIT 1",
                new String[]{uid})) {
            return c.moveToFirst()
                ? new Link(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3))
                : null;
        }
    }

    static Link findByTarget(SQLiteDatabase db,String kind,long targetId) {
        validKind(kind);
        try(Cursor c=db.rawQuery(
                "SELECT id,uid,target_kind,target_id FROM nfc_links "
                +"WHERE target_kind=? AND target_id=? ORDER BY id DESC LIMIT 1",
                new String[]{kind,Long.toString(targetId)})) {
            return c.moveToFirst()
                ? new Link(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3))
                : null;
        }
    }

    static boolean targetExists(SQLiteDatabase db,String kind,long id) {
        String table;
        String extra="";
        java.util.List<String> args=new java.util.ArrayList<>();
        args.add(Long.toString(id));
        switch(validKind(kind)) {
            case "thing":
            case "box":
                table="storage_items";
                extra=" AND kind=?";
                args.add(kind);
                break;
            case "place": table="places"; break;
            case "pantry": table="pantry"; break;
            case "vehicle": table="vehicles"; break;
            default: return false;
        }
        try(Cursor c=db.rawQuery("SELECT 1 FROM "+table+" WHERE id=?"+extra+" LIMIT 1",
                args.toArray(new String[0]))) {
            return c.moveToFirst();
        }
    }

    static String targetName(SQLiteDatabase db,String kind,long id) {
        if(!targetExists(db,kind,id)) return "";
        String table=("thing".equals(kind)||"box".equals(kind))
            ? "storage_items"
            : "place".equals(kind) ? "places"
            : "pantry".equals(kind) ? "pantry" : "vehicles";
        try(Cursor c=db.rawQuery("SELECT name FROM "+table+" WHERE id=?",
                new String[]{Long.toString(id)})) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    static void bind(SQLiteDatabase db,String rawUid,String kind,long targetId) {
        String uid=normalize(rawUid);
        kind=validKind(kind);
        if(!targetExists(db,kind,targetId))
            throw new IllegalArgumentException("Obiekt już nie istnieje.");
        db.beginTransaction();
        try {
            // One tag per object in the normal UI and one object per physical UID.
            db.delete("nfc_links","target_kind=? AND target_id=?",
                new String[]{kind,Long.toString(targetId)});
            db.delete("nfc_links","uid=? COLLATE NOCASE",new String[]{uid});
            ContentValues row=new ContentValues();
            row.put("uid",uid);
            row.put("target_kind",kind);
            row.put("target_id",targetId);
            row.put("created_at",System.currentTimeMillis());
            db.insertOrThrow("nfc_links",null,row);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static boolean clearTarget(SQLiteDatabase db,String kind,long targetId) {
        validKind(kind);
        return db.delete("nfc_links","target_kind=? AND target_id=?",
            new String[]{kind,Long.toString(targetId)})>0;
    }

    static boolean clearUid(SQLiteDatabase db,String rawUid) {
        return db.delete("nfc_links","uid=? COLLATE NOCASE",
            new String[]{normalize(rawUid)})>0;
    }

    static void assertIntegrity(SQLiteDatabase db) {
        Set<String> targets=new HashSet<>();
        try(Cursor c=db.rawQuery(
                "SELECT uid,target_kind,target_id FROM nfc_links ORDER BY id",null)) {
            while(c.moveToNext()) {
                String uid=normalize(c.getString(0));
                String kind=validKind(c.getString(1));
                long targetId=c.getLong(2);
                if(uid.isEmpty()||!targetExists(db,kind,targetId))
                    throw new IllegalArgumentException(
                        "NFC wskazuje nieistniejący obiekt.");
                if(!targets.add(kind+":"+targetId))
                    throw new IllegalArgumentException(
                        "Jeden obiekt ma więcej niż jeden tag NFC.");
            }
        }
    }

    static String shortUid(String uid) {
        String clean=normalize(uid);
        return clean.length()<=8?clean:
            clean.substring(0,4)+"…"+clean.substring(clean.length()-4);
    }

    static String kindLabel(String kind) {
        switch(kind) {
            case "thing": return "Rzecz";
            case "box": return "Pudełko";
            case "place": return "Miejsce";
            case "pantry": return "Produkt";
            case "vehicle": return "Pojazd";
            default: return kind;
        }
    }

    private static String validKind(String kind) {
        if(!"thing".equals(kind) && !"box".equals(kind)
                && !"place".equals(kind) && !"pantry".equals(kind)
                && !"vehicle".equals(kind))
            throw new IllegalArgumentException("Nieobsługiwany typ NFC.");
        return kind;
    }
}
