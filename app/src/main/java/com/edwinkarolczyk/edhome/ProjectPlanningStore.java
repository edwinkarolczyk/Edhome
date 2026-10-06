package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dane wejściowe planera Projektów: rzeczywiste godziny zmian użytkownika,
 * dobrowolne okna dostępności na projekty oraz wymagania blokujące czynność.
 */
final class ProjectPlanningStore {
    static final String[] SHIFT_CODES={"morning","afternoon","night","off"};
    static final String[] SHIFT_NAMES={"I zmiana","II zmiana","III zmiana","Dzień wolny"};
    static final String[] BLOCKER_KINDS={
        "purchase","delivery","prepare","resource","approval","manual"
    };
    static final String[] BLOCKER_LABELS={
        "Zakup / materiał","Dostawa / oczekiwanie","Dorób / przygotuj",
        "Rzecz / zasób","Akceptacja / decyzja","Inne wymaganie"
    };

    static final class ShiftHours {
        final String start,end;
        ShiftHours(String start,String end){this.start=start;this.end=end;}
        String label(){return start+"–"+end;}
    }

    static final class Blocker {
        final long id,taskId;
        final String kind,label,availableOn;
        final boolean hard,resolved;
        Blocker(long id,long taskId,String kind,String label,boolean hard,
                boolean resolved,String availableOn){
            this.id=id;this.taskId=taskId;this.kind=kind;this.label=label;
            this.hard=hard;this.resolved=resolved;this.availableOn=availableOn;
        }
    }

    private ProjectPlanningStore(){}

    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS member_shift_hours ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"member_id INTEGER NOT NULL,"
            +"shift TEXT NOT NULL CHECK(shift IN ('morning','afternoon','night')),"
            +"start_time TEXT NOT NULL,"
            +"end_time TEXT NOT NULL,"
            +"UNIQUE(member_id,shift))");
        db.execSQL("CREATE INDEX IF NOT EXISTS member_shift_hours_member_idx "
            +"ON member_shift_hours(member_id,shift)");
        db.execSQL("CREATE TABLE IF NOT EXISTS member_project_windows ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"member_id INTEGER NOT NULL,"
            +"shift TEXT NOT NULL CHECK(shift IN ('morning','afternoon','night','off')),"
            +"slot INTEGER NOT NULL CHECK(slot BETWEEN 1 AND 2),"
            +"enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),"
            +"start_time TEXT NOT NULL DEFAULT '',"
            +"end_time TEXT NOT NULL DEFAULT '',"
            +"UNIQUE(member_id,shift,slot))");
        db.execSQL("CREATE INDEX IF NOT EXISTS member_project_windows_member_idx "
            +"ON member_project_windows(member_id,shift,slot)");
        db.execSQL("CREATE TABLE IF NOT EXISTS project_task_blockers ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"task_id INTEGER NOT NULL,"
            +"kind TEXT NOT NULL CHECK(kind IN "
                +"('purchase','delivery','prepare','resource','approval','manual')),"
            +"label TEXT NOT NULL,"
            +"hard INTEGER NOT NULL DEFAULT 1 CHECK(hard IN (0,1)),"
            +"resolved INTEGER NOT NULL DEFAULT 0 CHECK(resolved IN (0,1)),"
            +"available_on TEXT,"
            +"created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS project_task_blockers_task_idx "
            +"ON project_task_blockers(task_id,resolved,hard,id)");
    }

    private static void requireMember(SQLiteDatabase db,long memberId){
        try(Cursor c=db.rawQuery("SELECT 1 FROM household_members WHERE id=?",
                new String[]{Long.toString(memberId)})){
            if(!c.moveToFirst())
                throw new IllegalArgumentException("Użytkownik nie istnieje.");
        }
    }

    private static void requireProjectTask(SQLiteDatabase db,long taskId){
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM tasks WHERE id=? AND project_id IS NOT NULL",
                new String[]{Long.toString(taskId)})){
            if(!c.moveToFirst())
                throw new IllegalArgumentException(
                    "Wymagania można dodać tylko do czynności projektu.");
        }
    }

    private static String[] defaultHours(String shift){
        if("morning".equals(shift))return new String[]{"06:00","14:00"};
        if("afternoon".equals(shift))return new String[]{"14:00","22:00"};
        if("night".equals(shift))return new String[]{"22:00","06:00"};
        throw new IllegalArgumentException("Nieprawidłowa zmiana.");
    }

    static ShiftHours shiftHours(SQLiteDatabase db,long memberId,String shift){
        if(!"morning".equals(shift)&&!"afternoon".equals(shift)
                &&!"night".equals(shift))
            throw new IllegalArgumentException("Nieprawidłowa zmiana.");
        try(Cursor c=db.rawQuery(
                "SELECT start_time,end_time FROM member_shift_hours "
                    +"WHERE member_id=? AND shift=?",
                new String[]{Long.toString(memberId),shift})){
            if(c.moveToFirst())return new ShiftHours(c.getString(0),c.getString(1));
        }
        String[] value=defaultHours(shift);
        return new ShiftHours(value[0],value[1]);
    }

    static void setShiftHours(SQLiteDatabase db,long memberId,String shift,
            String start,String end){
        requireMember(db,memberId);
        if(!"morning".equals(shift)&&!"afternoon".equals(shift)
                &&!"night".equals(shift))
            throw new IllegalArgumentException("Nieprawidłowa zmiana.");
        LocalTime a=parseTime(start,"Początek zmiany");
        LocalTime b=parseTime(end,"Koniec zmiany");
        if(a.equals(b))
            throw new IllegalArgumentException("Zmiana nie może trwać 24 godziny.");
        ContentValues v=new ContentValues();
        v.put("member_id",memberId);v.put("shift",shift);
        v.put("start_time",a.toString());v.put("end_time",b.toString());
        db.insertWithOnConflict("member_shift_hours",null,v,
            SQLiteDatabase.CONFLICT_REPLACE);
    }

    static boolean hasWindowConfig(SQLiteDatabase db,long memberId,String shift){
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM member_project_windows WHERE member_id=? AND shift=? LIMIT 1",
                new String[]{Long.toString(memberId),shift})){
            return c.moveToFirst();
        }
    }

    static List<TimeSuggestions.Window> windows(SQLiteDatabase db,long memberId,
            String shift){
        ArrayList<TimeSuggestions.Window> out=new ArrayList<>();
        if(!knownProjectShift(shift))return Collections.emptyList();
        boolean configured=hasWindowConfig(db,memberId,shift);
        if(configured){
            try(Cursor c=db.rawQuery(
                    "SELECT enabled,start_time,end_time FROM member_project_windows "
                        +"WHERE member_id=? AND shift=? ORDER BY slot",
                    new String[]{Long.toString(memberId),shift})){
                while(c.moveToNext()){
                    if(c.getInt(0)==0)continue;
                    String start=c.getString(1),end=c.getString(2);
                    if(start.isEmpty()||end.isEmpty())continue;
                    out.add(new TimeSuggestions.Window(
                        LocalTime.parse(start),LocalTime.parse(end)));
                }
            }
            return Collections.unmodifiableList(out);
        }
        if("morning".equals(shift))
            out.add(new TimeSuggestions.Window(LocalTime.of(16,0),LocalTime.of(21,0)));
        else if("afternoon".equals(shift))
            out.add(new TimeSuggestions.Window(LocalTime.of(8,0),LocalTime.of(12,0)));
        else if("off".equals(shift))
            out.add(new TimeSuggestions.Window(LocalTime.of(9,0),LocalTime.of(18,0)));
        // III zmiana bez domyślnego okna: użytkownik ustala je świadomie.
        return Collections.unmodifiableList(out);
    }

    static String windowsSummary(SQLiteDatabase db,long memberId,String shift){
        List<TimeSuggestions.Window> windows=windows(db,memberId,shift);
        if(windows.isEmpty())return "nie planuj";
        ArrayList<String> pieces=new ArrayList<>();
        for(TimeSuggestions.Window w:windows)
            pieces.add(w.start+"–"+w.end);
        return android.text.TextUtils.join(" + ",pieces)
            +(hasWindowConfig(db,memberId,shift)?"":" • domyślne");
    }

    static void setWindows(SQLiteDatabase db,long memberId,String shift,
            String start1,String end1,String start2,String end2){
        requireMember(db,memberId);
        if(!knownProjectShift(shift))
            throw new IllegalArgumentException("Nieprawidłowy rodzaj dnia.");
        String[][] raw={{clean(start1),clean(end1)},{clean(start2),clean(end2)}};
        SQLiteDatabase database=db;
        database.beginTransaction();
        try{
            database.delete("member_project_windows","member_id=? AND shift=?",
                new String[]{Long.toString(memberId),shift});
            for(int i=0;i<2;i++){
                boolean empty=raw[i][0].isEmpty()&&raw[i][1].isEmpty();
                if(!empty&&(raw[i][0].isEmpty()||raw[i][1].isEmpty()))
                    throw new IllegalArgumentException(
                        "W oknie "+(i+1)+" podaj początek i koniec albo zostaw oba puste.");
                ContentValues v=new ContentValues();
                v.put("member_id",memberId);v.put("shift",shift);v.put("slot",i+1);
                if(empty){
                    v.put("enabled",0);v.put("start_time","");v.put("end_time","");
                }else{
                    LocalTime a=parseTime(raw[i][0],"Początek okna");
                    LocalTime b=parseTime(raw[i][1],"Koniec okna");
                    if(!a.isBefore(b))
                        throw new IllegalArgumentException(
                            "Okno projektów musi zaczynać się przed końcem tego samego dnia.");
                    v.put("enabled",1);v.put("start_time",a.toString());
                    v.put("end_time",b.toString());
                }
                database.insertOrThrow("member_project_windows",null,v);
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }

    static void resetWindows(SQLiteDatabase db,long memberId,String shift){
        db.delete("member_project_windows","member_id=? AND shift=?",
            new String[]{Long.toString(memberId),shift});
    }

    static void deleteMemberConfig(SQLiteDatabase db,long memberId){
        db.delete("member_shift_hours","member_id=?",
            new String[]{Long.toString(memberId)});
        db.delete("member_project_windows","member_id=?",
            new String[]{Long.toString(memberId)});
    }

    static List<Blocker> blockers(SQLiteDatabase db,long taskId){
        ArrayList<Blocker> out=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT id,task_id,kind,label,hard,resolved,available_on "
                    +"FROM project_task_blockers WHERE task_id=? ORDER BY resolved,id",
                new String[]{Long.toString(taskId)})){
            while(c.moveToNext())out.add(new Blocker(
                c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),
                c.getInt(4)!=0,c.getInt(5)!=0,c.isNull(6)?"":c.getString(6)));
        }
        return Collections.unmodifiableList(out);
    }

    static List<Blocker> openHardBlockers(SQLiteDatabase db,long taskId){
        ArrayList<Blocker> out=new ArrayList<>();
        for(Blocker item:blockers(db,taskId))
            if(item.hard&&!item.resolved)out.add(item);
        return Collections.unmodifiableList(out);
    }

    static long addBlocker(SQLiteDatabase db,long taskId,String kind,String rawLabel,
            boolean hard,String availableOn){
        requireProjectTask(db,taskId);
        if(indexOf(BLOCKER_KINDS,kind)<0)
            throw new IllegalArgumentException("Nieprawidłowy rodzaj wymagania.");
        String label=clean(rawLabel);
        if(label.isEmpty()||label.length()>180)
            throw new IllegalArgumentException("Podaj wymaganie 1–180 znaków.");
        String date=clean(availableOn);
        if(!date.isEmpty())try{LocalDate.parse(date);}
        catch(Exception invalid){
            throw new IllegalArgumentException("Nieprawidłowa data dostępności.");
        }
        ContentValues v=new ContentValues();
        v.put("task_id",taskId);v.put("kind",kind);v.put("label",label);
        v.put("hard",hard?1:0);v.put("resolved",0);
        if(date.isEmpty())v.putNull("available_on");else v.put("available_on",date);
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("project_task_blockers",null,v);
    }

    static void setResolved(SQLiteDatabase db,long id,boolean resolved){
        ContentValues v=new ContentValues();v.put("resolved",resolved?1:0);
        if(db.update("project_task_blockers",v,"id=?",
                new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Wymaganie już nie istnieje.");
    }

    static void deleteBlocker(SQLiteDatabase db,long id){
        db.delete("project_task_blockers","id=?",
            new String[]{Long.toString(id)});
    }

    static int openHardCount(SQLiteDatabase db,long taskId){
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM project_task_blockers "
                    +"WHERE task_id=? AND hard=1 AND resolved=0",
                new String[]{Long.toString(taskId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static int openSoftCount(SQLiteDatabase db,long taskId){
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM project_task_blockers "
                    +"WHERE task_id=? AND hard=0 AND resolved=0",
                new String[]{Long.toString(taskId)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static boolean hasUndatedHardBlocker(SQLiteDatabase db,long taskId){
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM project_task_blockers WHERE task_id=? "
                    +"AND hard=1 AND resolved=0 AND "
                    +"(available_on IS NULL OR TRIM(available_on)='') LIMIT 1",
                new String[]{Long.toString(taskId)})){
            return c.moveToFirst();
        }
    }

    static LocalDate planningNotBefore(SQLiteDatabase db,long taskId){
        LocalDate latest=null;
        try(Cursor c=db.rawQuery(
                "SELECT available_on FROM project_task_blockers WHERE task_id=? "
                    +"AND hard=1 AND resolved=0 AND available_on IS NOT NULL "
                    +"AND TRIM(available_on)!='' ORDER BY available_on DESC LIMIT 1",
                new String[]{Long.toString(taskId)})){
            if(c.moveToFirst())latest=LocalDate.parse(c.getString(0));
        }
        return latest;
    }

    static String startBlockReason(SQLiteDatabase db,long taskId){
        ArrayList<String> labels=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT label FROM project_task_blockers WHERE task_id=? "
                    +"AND hard=1 AND resolved=0 ORDER BY id LIMIT 3",
                new String[]{Long.toString(taskId)})){
            while(c.moveToNext())labels.add(c.getString(0));
        }
        if(labels.isEmpty())return "";
        int total=openHardCount(db,taskId);
        return "Czynność jest zablokowana. Czeka na: "
            +android.text.TextUtils.join(", ",labels)
            +(total>labels.size()?" (+"+(total-labels.size())+")":"")+".";
    }

    static void assertIntegrity(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM member_shift_hours h "
                    +"LEFT JOIN household_members m ON m.id=h.member_id "
                    +"WHERE m.id IS NULL",null)) {
            if(!c.moveToFirst()||c.getInt(0)!=0)
                throw new IllegalStateException("Grafik zawiera nieistniejącego użytkownika.");
        }
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM member_project_windows w "
                    +"LEFT JOIN household_members m ON m.id=w.member_id "
                    +"WHERE m.id IS NULL",null)) {
            if(!c.moveToFirst()||c.getInt(0)!=0)
                throw new IllegalStateException("Dostępność zawiera nieistniejącego użytkownika.");
        }
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM project_task_blockers b "
                    +"LEFT JOIN tasks t ON t.id=b.task_id "
                    +"WHERE t.id IS NULL OR t.project_id IS NULL",null)) {
            if(!c.moveToFirst()||c.getInt(0)!=0)
                throw new IllegalStateException("Wymaganie wskazuje nieistniejącą czynność projektu.");
        }
        try(Cursor c=db.rawQuery(
                "SELECT shift,start_time,end_time FROM member_shift_hours",null)) {
            while(c.moveToNext()) {
                if(!"morning".equals(c.getString(0))
                        &&!"afternoon".equals(c.getString(0))
                        &&!"night".equals(c.getString(0)))
                    throw new IllegalStateException("Nieprawidłowa zmiana użytkownika.");
                LocalTime a=LocalTime.parse(c.getString(1));
                LocalTime b=LocalTime.parse(c.getString(2));
                if(a.equals(b))
                    throw new IllegalStateException("Nieprawidłowe godziny zmiany.");
            }
        } catch(java.time.format.DateTimeParseException invalid) {
            throw new IllegalStateException("Nieprawidłowe godziny zmiany.",invalid);
        }
        try(Cursor c=db.rawQuery(
                "SELECT enabled,start_time,end_time FROM member_project_windows",null)) {
            while(c.moveToNext()) {
                boolean enabled=c.getInt(0)!=0;
                String start=c.getString(1),end=c.getString(2);
                if(!enabled) {
                    if(!start.isEmpty()||!end.isEmpty())
                        throw new IllegalStateException("Wyłączone okno ma zapisane godziny.");
                    continue;
                }
                LocalTime a=LocalTime.parse(start),b=LocalTime.parse(end);
                if(!a.isBefore(b))
                    throw new IllegalStateException("Nieprawidłowe okno projektów.");
            }
        } catch(java.time.format.DateTimeParseException invalid) {
            throw new IllegalStateException("Nieprawidłowe okno projektów.",invalid);
        }
        try(Cursor c=db.rawQuery(
                "SELECT available_on FROM project_task_blockers "
                    +"WHERE available_on IS NOT NULL AND TRIM(available_on)!=''",null)) {
            while(c.moveToNext())LocalDate.parse(c.getString(0));
        } catch(java.time.format.DateTimeParseException invalid) {
            throw new IllegalStateException("Nieprawidłowa data wymagania.",invalid);
        }
    }

    static String kindLabel(String kind){
        int i=indexOf(BLOCKER_KINDS,kind);
        return i<0?"Wymaganie":BLOCKER_LABELS[i];
    }

    private static boolean knownProjectShift(String shift){
        return "morning".equals(shift)||"afternoon".equals(shift)
            ||"night".equals(shift)||"off".equals(shift);
    }

    private static LocalTime parseTime(String raw,String label){
        try{return LocalTime.parse(clean(raw));}
        catch(Exception invalid){
            throw new IllegalArgumentException(label+": użyj HH:mm.");
        }
    }

    private static String clean(String value){return value==null?"":value.trim();}

    private static int indexOf(String[] values,String value){
        for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;
        return -1;
    }
}
