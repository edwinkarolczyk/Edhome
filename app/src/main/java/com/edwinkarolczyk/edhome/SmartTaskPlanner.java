package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lokalny planer zaległych czynności. Nie przesuwa terminów automatycznie.
 * remind_time jest w obecnym modelu również godziną przypomnienia; zapis
 * zatwierdzonego terminu wykorzystuje ten sam czas jako początek pracy.
 * Czynność bez godziny jest traktowana konserwatywnie jako zajęty dzień.
 */
final class SmartTaskPlanner {
    static final int LOOKAHEAD_DAYS = 28;
    static final int MAX_DURATION = 600;

    static final class Task {
        final long id, memberId;
        final String title, due, repeatRule;
        final int duration;
        final boolean project;
        Task(long id, long memberId, String title, String due, String repeatRule,
                int duration, boolean project) {
            this.id=id;this.memberId=memberId;this.title=title;this.due=due;
            this.repeatRule=repeatRule;this.duration=duration;this.project=project;
        }
    }
    private static final class Busy {
        final LocalDateTime start, end;
        Busy(LocalDateTime start,LocalDateTime end) {
            this.start=start;this.end=end;
        }
        boolean overlaps(LocalDateTime a,LocalDateTime b) {
            return TimeSlotRules.overlaps(a,b,start,end);
        }
    }

    private SmartTaskPlanner() { }

    static List<Task> overdue(SQLiteDatabase db,int limit) {
        ArrayList<Task> items=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT t.id,t.title,t.due_date,t.repeat_rule,t.duration_minutes,"
                    +"COALESCE(t.assignee_id,p.assignee_id,0),t.project_id "
                    +"FROM tasks t LEFT JOIN projects p ON p.id=t.project_id "
                    +"WHERE t.done=0 AND t.due_date IS NOT NULL AND t.due_date<? "
                    +"AND t.task_kind<>'waste' ORDER BY t.due_date,t.id LIMIT ?",
                new String[]{LocalDate.now().toString(),String.valueOf(limit)})) {
            while(c.moveToNext())items.add(new Task(
                c.getLong(0),c.getLong(5),c.getString(1),c.getString(2),
                c.getString(3),c.getInt(4),!c.isNull(6)));
        }
        return Collections.unmodifiableList(items);
    }

    private static Task read(SQLiteDatabase db,long id) {
        try(Cursor c=db.rawQuery(
                "SELECT t.id,t.title,t.due_date,t.repeat_rule,t.duration_minutes,"
                    +"COALESCE(t.assignee_id,p.assignee_id,0),t.project_id "
                    +"FROM tasks t LEFT JOIN projects p ON p.id=t.project_id "
                    +"WHERE t.id=? AND t.done=0 AND t.due_date IS NOT NULL",
                new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst())return null;
            return new Task(c.getLong(0),c.getLong(5),c.getString(1),
                c.getString(2),c.getString(3),c.getInt(4),!c.isNull(6));
        }
    }

    static String reason(SQLiteDatabase db,Task task,long fallbackMember) {
        if(task==null)return "Czynność nie istnieje lub została wykonana.";
        if(!"once".equals(task.repeatRule))
            return "Czynność cykliczna zachowuje swoją regułę. Nie przesuwaj jej automatycznie.";
        if(task.project) {
            if(ProjectPlanningStore.hasUndatedHardBlocker(db,task.id))
                return ProjectPlanningStore.startBlockReason(db,task.id);
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM project_task_dependencies d "
                        +"JOIN tasks earlier ON earlier.id=d.depends_on_task_id "
                        +"WHERE d.task_id=? AND earlier.done=0 LIMIT 1",
                    new String[]{String.valueOf(task.id)})) {
                if(c.moveToFirst())return "Najpierw wykonaj czynności wymagane przez zależności.";
            }
            try(Cursor c=db.rawQuery(
                    "SELECT 1 FROM project_task_work_sessions "
                        +"WHERE task_id=? AND ended_at IS NULL LIMIT 1",
                    new String[]{String.valueOf(task.id)})) {
                if(c.moveToFirst())return "Trwa pomiar pracy. Najpierw zatrzymaj licznik.";
            }
            if(ProjectStore.remainingMinutes(db,task.id,task.duration)<=0)
                return "Zaplanowany czas już wykorzystano. Sprawdź postęp czynności.";
        }
        if(task.memberId<=0 && fallbackMember<=0)
            return "Wybierz domownika, aby określić jego dostępność.";
        return "";
    }

    static List<TimeSuggestions.Option> propose(SQLiteDatabase db,long taskId,
            long fallbackMember,LocalDateTime now) {
        Task task=read(db,taskId);
        if(task==null || !LocalDate.parse(task.due).isBefore(now.toLocalDate())
                || !"once".equals(task.repeatRule))return Collections.emptyList();
        if(!reason(db,task,fallbackMember).isEmpty())
            return Collections.emptyList();
        int duration=task.project
            ?ProjectStore.remainingMinutes(db,task.id,task.duration)
            :task.duration;
        LocalDate notBefore=task.project
            ?ProjectPlanningStore.planningNotBefore(db,task.id):null;
        return calculate(db,task.memberId>0?task.memberId:fallbackMember,
            taskId,duration,now,notBefore);
    }

    static List<TimeSuggestions.Option> proposeNew(SQLiteDatabase db,
            long memberId,int duration,LocalDateTime now) {
        return calculate(db,memberId,-1,duration,now,null);
    }

    /** Projektowe propozycje weryfikują też istniejące rezerwacje godzinowe. */
    static List<TimeSuggestions.Option> proposeForProject(SQLiteDatabase db,
            long memberId,long taskId,int remainingMinutes,LocalDateTime earliest) {
        return calculate(db,memberId,taskId,remainingMinutes,
            earliest.minusSeconds(1),null);
    }

    private static List<TimeSuggestions.Option> calculate(SQLiteDatabase db,
            long memberId,long excludedTask,int duration,LocalDateTime now,
            LocalDate notBefore) {
        if(memberId<=0 || duration<1 || duration>MAX_DURATION)
            return Collections.emptyList();
        ArrayList<TimeSuggestions.Option> options=new ArrayList<>();
        for(int d=0;d<LOOKAHEAD_DAYS && options.size()<3;d++) {
            LocalDate date=now.toLocalDate().plusDays(d);
            if(notBefore!=null && date.isBefore(notBefore))continue;
            String shift=effectiveShift(db,memberId,date);
            List<TimeSuggestions.Window> windows=
                ProjectPlanningStore.windows(db,memberId,shift);
            if(windows.isEmpty())continue;
            ArrayList<Busy> busy=new ArrayList<>();
            if(!loadBusy(db,memberId,excludedTask,date,busy))continue;
            for(TimeSuggestions.Window window:windows) {
                LocalDateTime candidate=date.atTime(window.start);
                if(!candidate.isAfter(now))candidate=TimeSlotRules.ceilQuarter(now.plusSeconds(1));
                if(candidate.isBefore(date.atTime(window.start)))
                    candidate=date.atTime(window.start);
                for(;!candidate.plusMinutes(duration).isAfter(date.atTime(window.end));
                        candidate=candidate.plusMinutes(15)) {
                    LocalDateTime end=candidate.plusMinutes(duration);
                    boolean blocked=false;
                    for(Busy item:busy)if(item.overlaps(candidate,end)) {
                        blocked=true;break;
                    }
                    if(!blocked) {
                        options.add(new TimeSuggestions.Option(date,
                            candidate.toLocalTime(),end.toLocalTime()));
                        break;
                    }
                }
                if(!options.isEmpty()
                        &&options.get(options.size()-1).date.equals(date))break;
            }
        }
        return Collections.unmodifiableList(options);
    }

    /** Nieznany harmonogram nie oznacza dnia wolnego. */
    private static String effectiveShift(SQLiteDatabase db,long member,LocalDate date) {
        String[] args={String.valueOf(member),date.toString()};
        try(Cursor c=db.rawQuery(
                "SELECT shift FROM member_shift_exceptions "
                    +"WHERE member_id=? AND date=?",args)) {
            if(c.moveToFirst())return c.getString(0);
        }
        try(Cursor c=db.rawQuery(
                "SELECT shift FROM member_weekly_shifts "
                    +"WHERE member_id=? AND weekday=?",
                new String[]{String.valueOf(member),
                    String.valueOf(date.getDayOfWeek().getValue())})) {
            return c.moveToFirst()?c.getString(0):"unset";
        }
    }

    /**
     * Null reminder => brak godziny, więc nie obiecujemy wolnej godziny.
     * Zajętość wynika z czynności EDHOME i lokalnych minutników; zewnętrzne
     * kalendarze nie są źródłem danych tego planera.
     */
    private static boolean loadBusy(SQLiteDatabase db,long member,long exclude,
            LocalDate date,List<Busy> busy) {
        try(Cursor c=db.rawQuery(
                "SELECT remind_time,duration_minutes FROM tasks "
                    +"WHERE done=0 AND due_date=? AND id<>? "
                    +"AND (assignee_id IS NULL OR assignee_id=?)",
                new String[]{date.toString(),String.valueOf(exclude),
                    String.valueOf(member)})) {
            while(c.moveToNext()) {
                if(c.isNull(0)||!ReminderRules.validTime(c.getString(0)))
                    return false;
                LocalDateTime start=date.atTime(LocalTime.parse(c.getString(0)));
                busy.add(new Busy(start,start.plusMinutes(Math.max(1,c.getInt(1)))));
            }
        }
        long dayStart=date.atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();
        long dayEnd=date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();
        try(Cursor c=db.rawQuery(
                "SELECT start_at,end_at FROM device_timers "
                    +"WHERE start_at<? AND end_at>?",
                new String[]{String.valueOf(dayEnd),String.valueOf(dayStart)})) {
            while(c.moveToNext()) {
                busy.add(new Busy(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(0)),
                        ZoneId.systemDefault()),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)),
                        ZoneId.systemDefault())));
            }
        }
        return true;
    }

    /** Nie wpisuj daty, której inna synchronizacja lub użytkownik już zmienił. */
    static void approve(SQLiteDatabase db,long id,String expectedDue,
            TimeSuggestions.Option selected,long fallbackMember) {
        db.beginTransaction();
        try {
            Task item=read(db,id);
            if(item==null || !item.due.equals(expectedDue))
                throw new IllegalStateException("Termin zmienił się na innym urządzeniu.");
            List<TimeSuggestions.Option> fresh=propose(db,id,
                fallbackMember,LocalDateTime.now());
            boolean valid=false;
            for(TimeSuggestions.Option candidate:fresh)
                if(candidate.date.equals(selected.date)
                        &&candidate.start.equals(selected.start)
                        &&candidate.end.equals(selected.end))valid=true;
            if(!valid)throw new IllegalStateException(
                "Wybrany przedział jest już niedostępny. Wyszukaj ponownie.");
            ContentValues value=new ContentValues();
            value.put("due_date",selected.date.toString());
            value.put("remind_time",selected.start.toString());
            value.put("reminder_lead_days",0);
            int updated=db.update("tasks",value,
                "id=? AND done=0 AND due_date=? AND repeat_rule='once'",
                new String[]{String.valueOf(id),expectedDue});
            if(updated!=1)throw new IllegalStateException(
                "Czynność została zmieniona. Odśwież propozycje.");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        DiagnosticLog.event("TASK_REPLANNED",
            "id="+id+" from="+expectedDue+" to="+selected.date
                +" at="+selected.start);
    }

    /** Szybki sygnał dla dziennego powiadomienia; bez trwałej zmiany danych. */
    static boolean hasProposals(SQLiteDatabase db,long fallbackMember) {
        for(Task item:overdue(db,12))
            if(!propose(db,item.id,fallbackMember,LocalDateTime.now()).isEmpty())
                return true;
        return false;
    }
}
