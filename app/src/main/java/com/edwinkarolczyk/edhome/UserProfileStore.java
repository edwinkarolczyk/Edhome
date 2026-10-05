package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Rozszerzenie istniejących domowników o profile użytkowników EDHOME.
 * Id profilu jest zawsze równe id domownika, dzięki czemu stare przypisania
 * zadań i grafików pozostają bez zmian.
 *
 * PIN profilu celowo NIE znajduje się w tej tabeli. Jest przechowywany
 * lokalnie w SharedPreferences urządzenia i nie jest synchronizowany.
 */
final class UserProfileStore {
    static final String ROLE_ADMIN = "admin";
    static final String ROLE_MEMBER = "member";
    static final String DEFAULT_AVATAR = "👤";
    static final String DEFAULT_COLOR = "#607D8B";

    static final String[] ROLES = {ROLE_ADMIN, ROLE_MEMBER};
    static final String[] ROLE_LABELS = {"Administrator", "Domownik"};
    static final String[] AVATARS = {"👤", "🧑", "👩", "👨", "🧒", "🏠"};
    static final String[] COLORS = {
        "#607D8B", "#1976D2", "#388E3C", "#7B1FA2", "#F57C00"
    };
    static final String[] COLOR_LABELS = {
        "Grafitowy", "Niebieski", "Zielony", "Fioletowy", "Pomarańczowy"
    };

    private UserProfileStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS user_profiles ("
            + "id INTEGER PRIMARY KEY, "
            + "member_id INTEGER NOT NULL UNIQUE, "
            + "role TEXT NOT NULL DEFAULT 'member' "
            + "CHECK(role IN ('admin','member')), "
            + "avatar TEXT NOT NULL DEFAULT '👤' "
            + "CHECK(length(avatar) BETWEEN 1 AND 8), "
            + "color TEXT NOT NULL DEFAULT '#607D8B' "
            + "CHECK(color IN ('#607D8B','#1976D2','#388E3C','#7B1FA2','#F57C00')), "
            + "created_at INTEGER NOT NULL, "
            + "CHECK(id=member_id))");
    }

    static void ensureAll(SQLiteDatabase db) {
        create(db);
        db.execSQL("INSERT OR IGNORE INTO user_profiles "
            + "(id,member_id,role,avatar,color,created_at) "
            + "SELECT id,id,'member','👤','#607D8B',? FROM household_members",
            new Object[]{System.currentTimeMillis()});
        ensureAdministrator(db);
    }

    private static void ensureAdministrator(SQLiteDatabase db) {
        db.execSQL("UPDATE user_profiles SET role='admin' "
            + "WHERE id=(SELECT MIN(id) FROM user_profiles) "
            + "AND NOT EXISTS (SELECT 1 FROM user_profiles WHERE role='admin')");
    }

    static int count(SQLiteDatabase db) {
        try (Cursor c=db.rawQuery("SELECT COUNT(*) FROM household_members",null)) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static long add(SQLiteDatabase db,String name,String role,
            String avatar,String color) {
        String clean=validateName(name);
        validateProfile(role,avatar,color);
        boolean ownTransaction=!db.inTransaction();
        if(ownTransaction)db.beginTransaction();
        try {
            ContentValues member=new ContentValues();
            member.put("name",clean);
            long memberId=db.insertWithOnConflict("household_members",null,
                member,SQLiteDatabase.CONFLICT_IGNORE);
            if(memberId==-1)return -1L;

            ContentValues profile=new ContentValues();
            profile.put("id",memberId);
            profile.put("member_id",memberId);
            profile.put("role",role);
            profile.put("avatar",avatar);
            profile.put("color",color);
            profile.put("created_at",System.currentTimeMillis());
            db.insertOrThrow("user_profiles",null,profile);
            ensureAdministrator(db);
            if(ownTransaction)db.setTransactionSuccessful();
            return memberId;
        } finally {
            if(ownTransaction)db.endTransaction();
        }
    }

    static boolean update(SQLiteDatabase db,long memberId,String name,
            String role,String avatar,String color) {
        String clean=validateName(name);
        validateProfile(role,avatar,color);
        try(Cursor duplicate=db.rawQuery(
                "SELECT 1 FROM household_members WHERE name=? COLLATE NOCASE "
                    + "AND id<>? LIMIT 1",
                new String[]{clean,Long.toString(memberId)})) {
            if(duplicate.moveToFirst())return false;
        }
        boolean ownTransaction=!db.inTransaction();
        if(ownTransaction)db.beginTransaction();
        try {
            ContentValues member=new ContentValues();
            member.put("name",clean);
            if(db.update("household_members",member,"id=?",
                    new String[]{Long.toString(memberId)})!=1)
                throw new IllegalArgumentException("Użytkownik nie istnieje.");

            ensureAll(db);
            if(ROLE_MEMBER.equals(role)) {
                try(Cursor current=db.rawQuery(
                        "SELECT role FROM user_profiles WHERE member_id=?",
                        new String[]{Long.toString(memberId)})) {
                    boolean demoting=current.moveToFirst()
                        &&ROLE_ADMIN.equals(current.getString(0));
                    if(demoting) {
                        try(Cursor others=db.rawQuery(
                                "SELECT COUNT(*) FROM user_profiles "
                                    +"WHERE role='admin' AND member_id<>?",
                                new String[]{Long.toString(memberId)})) {
                            if(!others.moveToFirst()||others.getInt(0)==0)
                                throw new IllegalArgumentException(
                                    "Najpierw ustaw innego użytkownika jako Administratora.");
                        }
                    }
                }
            }
            ContentValues profile=new ContentValues();
            profile.put("role",role);
            profile.put("avatar",avatar);
            profile.put("color",color);
            if(db.update("user_profiles",profile,"member_id=?",
                    new String[]{Long.toString(memberId)})!=1)
                throw new IllegalStateException("Nie udało się zapisać profilu.");
            if(ownTransaction)db.setTransactionSuccessful();
            return true;
        } finally {
            if(ownTransaction)db.endTransaction();
        }
    }

    static Cursor list(SQLiteDatabase db) {
        return db.rawQuery(
            "SELECT m.id,m.name,COALESCE(p.role,'member'),"
                + "COALESCE(p.avatar,'👤'),COALESCE(p.color,'#607D8B'),"
                + "COALESCE(p.created_at,0) "
                + "FROM household_members m LEFT JOIN user_profiles p "
                + "ON p.member_id=m.id ORDER BY m.name COLLATE NOCASE",null);
    }

    static Cursor one(SQLiteDatabase db,long memberId) {
        return db.rawQuery(
            "SELECT m.id,m.name,COALESCE(p.role,'member'),"
                + "COALESCE(p.avatar,'👤'),COALESCE(p.color,'#607D8B'),"
                + "COALESCE(p.created_at,0) "
                + "FROM household_members m LEFT JOIN user_profiles p "
                + "ON p.member_id=m.id WHERE m.id=?",
            new String[]{Long.toString(memberId)});
    }

    static String roleLabel(String role) {
        return ROLE_ADMIN.equals(role)?"Administrator":"Domownik";
    }

    static void assertIntegrity(SQLiteDatabase db) {
        try(Cursor broken=db.rawQuery(
                "SELECT COUNT(*) FROM user_profiles p "
                    + "LEFT JOIN household_members m ON m.id=p.member_id "
                    + "WHERE m.id IS NULL OR p.id<>p.member_id",null)) {
            if(!broken.moveToFirst()||broken.getInt(0)!=0)
                throw new IllegalStateException("Uszkodzone powiązanie profilu użytkownika.");
        }
    }

    private static String validateName(String name) {
        String clean=TextEntryRules.capitalizeLabel(name);
        if(clean.isEmpty()||clean.length()>80)
            throw new IllegalArgumentException("Imię użytkownika: 1–80 znaków.");
        return clean;
    }

    private static void validateProfile(String role,String avatar,String color) {
        if(!contains(ROLES,role))
            throw new IllegalArgumentException("Nieprawidłowa rola użytkownika.");
        if(avatar==null||avatar.isEmpty()||avatar.length()>8
                ||!contains(AVATARS,avatar))
            throw new IllegalArgumentException("Nieprawidłowy avatar użytkownika.");
        if(!contains(COLORS,color))
            throw new IllegalArgumentException("Nieprawidłowy kolor użytkownika.");
    }

    private static boolean contains(String[] values,String wanted) {
        if(wanted==null)return false;
        for(String value:values)if(value.equals(wanted))return true;
        return false;
    }
}
