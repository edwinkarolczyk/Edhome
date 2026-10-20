package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Podgląd grupowego planowania bez skutków ubocznych. Zapis dopiero po
 * zatwierdzeniu, w jednej transakcji z ponowną kontrolą terminów i grafiku.
 */
final class BatchTaskPlanner {
    static final class Row {
        final SmartTaskPlanner.Task task;
        final TimeSuggestions.Option chosen;
        final String unavailable;
        Row(SmartTaskPlanner.Task task, TimeSuggestions.Option chosen, String reason) {
            this.task=task;
            this.chosen=chosen;
            this.unavailable=reason;
        }
        String label() {
            return task.title+"\n"+(chosen==null
                ? "Nie dopasowano: "+unavailable
                : TaskDateLabels.format(chosen.date)+" • "+chosen.start+"–"+chosen.end);
        }
    }

    static final class Draft {
        final List<Row> rows;
        final int count;
        Draft(List<Row> rows,int count) {
            this.rows=Collections.unmodifiableList(rows);
            this.count=count;
        }
    }

    private BatchTaskPlanner() { }

    static Draft create(SQLiteDatabase db,List<SmartTaskPlanner.Task> tasks,
            LocalDateTime now) {
        if(tasks==null || now==null)
            throw new IllegalArgumentException("Brak danych do planowania.");
        ArrayList<Row> rows=new ArrayList<>();
        ArrayList<SmartTaskPlanner.DraftReservation> held=new ArrayList<>();
        int matched=0;
        for(SmartTaskPlanner.Task task:tasks) {
            if(task.memberId<=0) {
                rows.add(new Row(task,null,"Wybierz wykonawcę."));
                continue;
            }
            String reason=SmartTaskPlanner.reason(db,task,0L);
            if(!reason.isEmpty()) {
                rows.add(new Row(task,null,reason));
                continue;
            }
            List<TimeSuggestions.Option> options=SmartTaskPlanner.proposeForBatch(
                db,task,now,held);
            if(options.isEmpty()) {
                rows.add(new Row(task,null,
                    "Brak wolnego przedziału w ciągu 28 dni. Sprawdź grafik lub blokady."));
                continue;
            }
            TimeSuggestions.Option first=options.get(0);
            rows.add(new Row(task,first,""));
            held.add(new SmartTaskPlanner.DraftReservation(task.memberId,first));
            matched++;
        }
        return new Draft(rows,matched);
    }

    /** Nie zapisuj części zmian po błędzie synchronizacji lub nowej kolizji. */
    static int approve(SQLiteDatabase db,Draft draft) {
        if(draft==null||draft.count<=0)
            throw new IllegalArgumentException("Nie ma terminów do zatwierdzenia.");
        int saved=0;
        db.beginTransaction();
        try {
            for(Row row:draft.rows) {
                if(row.chosen==null)continue;
                SmartTaskPlanner.Task current=SmartTaskPlanner.readAny(db,row.task.id);
                if(current==null
                        || current.memberId!=row.task.memberId
                        || current.duration!=row.task.duration
                        || !current.due.equals(row.task.due)
                        || !current.repeatRule.equals(row.task.repeatRule)
                        || current.project!=row.task.project
                        || !"once".equals(current.repeatRule)) {
                    throw new IllegalStateException("Zmieniono czynność „"
                        +row.task.title+"”. Odśwież propozycje.");
                }
                // W obrębie transakcji wcześniej zapisane czynności są już
                // widoczne dla silnika kolizji, bez udawania wolnych godzin.
                List<TimeSuggestions.Option> options=SmartTaskPlanner.proposeForBatch(
                    db,current,LocalDateTime.now(),Collections.emptyList());
                boolean available=false;
                for(TimeSuggestions.Option option:options) {
                    if(option.date.equals(row.chosen.date)
                            &&option.start.equals(row.chosen.start)
                            &&option.end.equals(row.chosen.end)) {
                        available=true;
                        break;
                    }
                }
                if(!available)
                    throw new IllegalStateException("Termin „"+row.task.title
                        +"” nie jest już wolny. Wyszukaj ponownie.");
                ContentValues values=new ContentValues();
                values.put("due_date",row.chosen.date.toString());
                values.put("remind_time",row.chosen.start.toString());
                values.put("reminder_lead_days",0);
                String where=current.due.isEmpty()
                    ? "id=? AND done=0 AND repeat_rule='once' "
                        +"AND (due_date IS NULL OR due_date='')"
                    : "id=? AND done=0 AND repeat_rule='once' AND due_date=?";
                String[] args=current.due.isEmpty()
                    ?new String[]{String.valueOf(current.id)}
                    :new String[]{String.valueOf(current.id),current.due};
                if(db.update("tasks",values,where,args)!=1)
                    throw new IllegalStateException("Nie zapisano terminu „"
                        +row.task.title+"”. Nie zmieniono żadnej czynności.");
                saved++;
            }
            if(saved!=draft.count)
                throw new IllegalStateException("Niekompletny podgląd planowania.");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        DiagnosticLog.event("TASK_BATCH_PLANNED","count="+saved);
        return saved;
    }
}
