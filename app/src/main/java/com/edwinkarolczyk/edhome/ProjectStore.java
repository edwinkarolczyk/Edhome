package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Lokalna hierarchia projektów oparta na istniejących Czynnościach i Magazynie. */
final class ProjectStore {
    static final String[] STATUSES={"active","paused","done"};

    static final class Project {
        final long id;
        final String name;
        final Long parentId;
        final Long placeId;
        final Long assigneeId;
        final String status;
        final String dueDate;
        final Long budgetGrosz;
        Project(long id,String name,Long parentId,Long placeId,Long assigneeId,
                String status,String dueDate,Long budgetGrosz) {
            this.id=id; this.name=name; this.parentId=parentId;
            this.placeId=placeId; this.assigneeId=assigneeId;
            this.status=status; this.dueDate=dueDate; this.budgetGrosz=budgetGrosz;
        }
    }

    static final class TaskItem {
        final long id;
        final String title;
        final boolean done;
        final String dueDate;
        final int durationMinutes;
        TaskItem(long id,String title,boolean done,String dueDate,int durationMinutes) {
            this.id=id;this.title=title;this.done=done;
            this.dueDate=dueDate;this.durationMinutes=durationMinutes;
        }
    }

    static final class TaskRef {
        final long id;
        final long projectId;
        final String title;
        TaskRef(long id,long projectId,String title) {
            this.id=id;this.projectId=projectId;this.title=title;
        }
    }

    static final class Stats {
        final int tasks;
        final int doneTasks;
        final int overdueTasks;
        final int totalMinutes;
        final int doneMinutes;
        final long plannedCostGrosz;
        final long spentCostGrosz;
        final int workedMinutes;
        final boolean activeWork;
        Stats(int tasks,int doneTasks,int overdueTasks,int totalMinutes,
                int doneMinutes,long plannedCostGrosz,long spentCostGrosz,
                int workedMinutes,boolean activeWork) {
            this.tasks=tasks; this.doneTasks=doneTasks;
            this.overdueTasks=overdueTasks; this.totalMinutes=totalMinutes;
            this.doneMinutes=doneMinutes;
            this.plannedCostGrosz=plannedCostGrosz;
            this.spentCostGrosz=spentCostGrosz;
            this.workedMinutes=workedMinutes;
            this.activeWork=activeWork;
        }
        int progressPct() {
            if(totalMinutes<=0)return tasks<=0?0:(doneTasks*100/tasks);
            return Math.max(0,Math.min(100,
                (int)Math.round(doneMinutes*100.0/totalMinutes)));
        }
        int remainingMinutes(){return Math.max(0,totalMinutes-doneMinutes);}
        int timePct() {
            if(totalMinutes<=0)return 0;
            return Math.max(0,(int)Math.round(workedMinutes*100.0/totalMinutes));
        }
        boolean timeOverrun(){return totalMinutes>0&&workedMinutes>totalMinutes;}
    }

    private ProjectStore(){}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS projects ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"name TEXT NOT NULL COLLATE NOCASE,"
            +"parent_id INTEGER,"
            +"place_id INTEGER,"
            +"assignee_id INTEGER,"
            +"status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','paused','done')),"
            +"due_date TEXT,"
            +"budget_grosz INTEGER,"
            +"created_at INTEGER NOT NULL,"
            +"CHECK(parent_id IS NULL OR parent_id!=id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS projects_parent_idx "
            +"ON projects(parent_id,name COLLATE NOCASE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS project_resources ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"project_id INTEGER NOT NULL,"
            +"target_kind TEXT NOT NULL CHECK(target_kind IN ('thing','box')),"
            +"target_id INTEGER NOT NULL,"
            +"created_at INTEGER NOT NULL,"
            +"UNIQUE(project_id,target_kind,target_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS project_resources_project_idx "
            +"ON project_resources(project_id,id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS project_costs ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"project_id INTEGER NOT NULL,"
            +"name TEXT NOT NULL,"
            +"qty_milli INTEGER NOT NULL DEFAULT 1000,"
            +"unit TEXT NOT NULL DEFAULT 'szt.',"
            +"unit_price_grosz INTEGER NOT NULL DEFAULT 0,"
            +"status TEXT NOT NULL DEFAULT 'planned' "
            +"CHECK(status IN ('planned','bought','paid')),"
            +"note TEXT NOT NULL DEFAULT '',"
            +"created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS project_costs_project_idx "
            +"ON project_costs(project_id,id)");
        upgrade41(db);
        upgrade43(db);
    }

    static void upgrade41(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_task_dependencies ("
            +"task_id INTEGER NOT NULL,"
            +"depends_on_task_id INTEGER NOT NULL,"
            +"created_at INTEGER NOT NULL,"
            +"CHECK(task_id!=depends_on_task_id),"
            +"UNIQUE(task_id,depends_on_task_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS project_task_dependencies_task_idx "
            +"ON project_task_dependencies(task_id,depends_on_task_id)");
    }

    static void upgrade43(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_task_work_sessions ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT,"
            +"task_id INTEGER NOT NULL,"
            +"started_at INTEGER NOT NULL,"
            +"ended_at INTEGER,"
            +"worked_minutes INTEGER,"
            +"CHECK(ended_at IS NULL OR ended_at>=started_at),"
            +"CHECK((ended_at IS NULL AND worked_minutes IS NULL) OR "
                +"(ended_at IS NOT NULL AND worked_minutes IS NOT NULL "
                +"AND worked_minutes>=1)))");
        db.execSQL("CREATE INDEX IF NOT EXISTS project_task_work_sessions_task_idx "
            +"ON project_task_work_sessions(task_id,id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS project_task_work_sessions_active_idx "
            +"ON project_task_work_sessions(task_id) WHERE ended_at IS NULL");
    }

    static Project find(SQLiteDatabase db,long id) {
        try(Cursor c=db.rawQuery(
                "SELECT id,name,parent_id,place_id,assignee_id,status,due_date,budget_grosz "
                +"FROM projects WHERE id=?",new String[]{Long.toString(id)})) {
            return c.moveToFirst()?new Project(c.getLong(0),c.getString(1),
                c.isNull(2)?null:c.getLong(2),c.isNull(3)?null:c.getLong(3),
                c.isNull(4)?null:c.getLong(4),c.getString(5),
                c.isNull(6)?"":c.getString(6),c.isNull(7)?null:c.getLong(7)):null;
        }
    }

    static List<Project> children(SQLiteDatabase db,Long parentId) {
        ArrayList<Project> out=new ArrayList<>();
        String where=parentId==null?"parent_id IS NULL":"parent_id=?";
        String[] args=parentId==null?null:new String[]{Long.toString(parentId)};
        try(Cursor c=db.rawQuery(
                "SELECT id,name,parent_id,place_id,assignee_id,status,due_date,budget_grosz "
                +"FROM projects WHERE "+where+" ORDER BY name COLLATE NOCASE,id",args)) {
            while(c.moveToNext())out.add(new Project(c.getLong(0),c.getString(1),
                c.isNull(2)?null:c.getLong(2),c.isNull(3)?null:c.getLong(3),
                c.isNull(4)?null:c.getLong(4),c.getString(5),
                c.isNull(6)?"":c.getString(6),c.isNull(7)?null:c.getLong(7)));
        }
        return Collections.unmodifiableList(out);
    }

    static long addProject(SQLiteDatabase db,String rawName,Long parentId,
            Long placeId,Long assigneeId,String dueDate,Long budgetGrosz) {
        String name=TextEntryRules.capitalizeLabel(rawName);
        if(name.isEmpty()||name.length()>160)
            throw new IllegalArgumentException("Podaj nazwę projektu 1–160 znaków.");
        if(parentId!=null&&find(db,parentId)==null)
            throw new IllegalArgumentException("Projekt nadrzędny już nie istnieje.");
        if(placeId!=null)try(Cursor c=db.rawQuery(
                "SELECT 1 FROM places WHERE id=?",
                new String[]{Long.toString(placeId)})) {
            if(!c.moveToFirst())throw new IllegalArgumentException("Miejsce już nie istnieje.");
        }
        if(assigneeId!=null)try(Cursor c=db.rawQuery(
                "SELECT 1 FROM household_members WHERE id=?",
                new String[]{Long.toString(assigneeId)})) {
            if(!c.moveToFirst())throw new IllegalArgumentException("Wykonawca już nie istnieje.");
        }
        if(dueDate==null)dueDate="";
        dueDate=dueDate.trim();
        if(!dueDate.isEmpty())try{java.time.LocalDate.parse(dueDate);}
        catch(Exception invalid){throw new IllegalArgumentException("Nieprawidłowy termin projektu.");}
        if(budgetGrosz!=null&&budgetGrosz<0)
            throw new IllegalArgumentException("Budżet nie może być ujemny.");
        ContentValues v=new ContentValues();
        v.put("name",name);
        if(parentId==null)v.putNull("parent_id");else v.put("parent_id",parentId);
        if(placeId==null)v.putNull("place_id");else v.put("place_id",placeId);
        if(assigneeId==null)v.putNull("assignee_id");else v.put("assignee_id",assigneeId);
        v.put("status","active");
        if(dueDate.isEmpty())v.putNull("due_date");else v.put("due_date",dueDate);
        if(budgetGrosz==null)v.putNull("budget_grosz");else v.put("budget_grosz",budgetGrosz);
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("projects",null,v);
    }

    static void setStatus(SQLiteDatabase db,long id,String status) {
        boolean valid=false;
        for(String value:STATUSES)if(value.equals(status))valid=true;
        if(!valid)throw new IllegalArgumentException("Nieprawidłowy status projektu.");
        if(find(db,id)==null)
            throw new IllegalArgumentException("Projekt już nie istnieje.");
        if("done".equals(status)) {
            Set<Long> ids=descendantIds(db,id);
            if(!ids.isEmpty()) {
                try(Cursor c=db.rawQuery(
                        "SELECT COUNT(*) FROM tasks WHERE done=0 AND project_id IN ("
                            +inClause(ids)+")",args(ids))) {
                    int open=c.moveToFirst()?c.getInt(0):0;
                    if(open>0)
                        throw new IllegalArgumentException(
                            "Nie można zakończyć projektu. Pozostało "
                                +open+" niewykonanych czynności.");
                }
            }
        }
        ContentValues v=new ContentValues();v.put("status",status);
        if(db.update("projects",v,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalArgumentException("Projekt już nie istnieje.");
    }

    static Set<Long> descendantIds(SQLiteDatabase db,long root) {
        if(find(db,root)==null)return Collections.emptySet();
        HashSet<Long> result=new HashSet<>();
        result.add(root);
        for(int pass=0;pass<256;pass++) {
            int before=result.size();
            try(Cursor c=db.rawQuery("SELECT id,parent_id FROM projects WHERE parent_id IS NOT NULL",null)) {
                while(c.moveToNext())if(result.contains(c.getLong(1)))result.add(c.getLong(0));
            }
            if(result.size()==before)return result;
        }
        throw new IllegalStateException("Zbyt głęboka hierarchia projektów.");
    }

    private static String inClause(Set<Long> ids) {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<ids.size();i++) {
            if(i>0)b.append(',');
            b.append('?');
        }
        return b.toString();
    }
    private static String[] args(Set<Long> ids) {
        String[] out=new String[ids.size()];int i=0;
        for(Long id:ids)out[i++]=Long.toString(id);
        return out;
    }

    static Stats stats(SQLiteDatabase db,long projectId) {
        Set<Long> ids=descendantIds(db,projectId);
        if(ids.isEmpty())return new Stats(0,0,0,0,0,0,0,0,false);
        String in=inClause(ids);String[] a=args(ids);
        HashMap<Long,Integer> worked=new HashMap<>();
        boolean activeWork=false;
        long now=System.currentTimeMillis();
        try(Cursor c=db.rawQuery(
                "SELECT s.task_id,s.started_at,s.ended_at,s.worked_minutes "
                    +"FROM project_task_work_sessions s JOIN tasks t ON t.id=s.task_id "
                    +"WHERE t.project_id IN ("+in+")",a)) {
            while(c.moveToNext()) {
                int minutes;
                if(c.isNull(2)) {
                    activeWork=true;
                    long elapsed=Math.max(0L,now-c.getLong(1));
                    minutes=(int)Math.max(0L,elapsed/60000L);
                } else minutes=Math.max(0,c.getInt(3));
                worked.put(c.getLong(0),
                    worked.getOrDefault(c.getLong(0),0)+minutes);
            }
        }
        int tasks=0,done=0,overdue=0,totalMinutes=0,doneMinutes=0;
        int workedMinutes=0;
        try(Cursor c=db.rawQuery(
                "SELECT id,done,duration_minutes,due_date FROM tasks "
                    +"WHERE project_id IN ("+in+")",a)) {
            String today=java.time.LocalDate.now().toString();
            while(c.moveToNext()) {
                tasks++;
                long taskId=c.getLong(0);
                int minutes=Math.max(1,c.getInt(2));
                int taskWorked=worked.getOrDefault(taskId,0);
                totalMinutes+=minutes;
                if(c.getInt(1)!=0) {
                    done++;
                    doneMinutes+=minutes;
                    workedMinutes+=Math.max(minutes,taskWorked);
                } else {
                    doneMinutes+=Math.min(minutes,taskWorked);
                    workedMinutes+=taskWorked;
                    if(!c.isNull(3)&&c.getString(3).compareTo(today)<0)overdue++;
                }
            }
        }
        long planned=0,spent=0;
        try(Cursor c=db.rawQuery(
                "SELECT qty_milli,unit_price_grosz,status FROM project_costs "
                    +"WHERE project_id IN ("+in+")",a)) {
            while(c.moveToNext()) {
                long value=Math.max(0,Math.round(c.getLong(0)*c.getLong(1)/1000.0));
                planned+=value;
                if("bought".equals(c.getString(2))||"paid".equals(c.getString(2)))
                    spent+=value;
            }
        }
        return new Stats(tasks,done,overdue,totalMinutes,doneMinutes,planned,spent,
            workedMinutes,activeWork);
    }

    static List<TaskItem> displayTasks(SQLiteDatabase db,long projectId) {
        ArrayList<TaskItem> out=new ArrayList<>();
        String ready=
            "NOT EXISTS(SELECT 1 FROM project_task_dependencies d "
                +"JOIN tasks req ON req.id=d.depends_on_task_id "
                +"WHERE d.task_id=t.id AND req.done=0) "
            +"AND NOT EXISTS(SELECT 1 FROM project_task_blockers b "
                +"WHERE b.task_id=t.id AND b.hard=1 AND b.resolved=0)";
        String sql=
            "SELECT t.id,t.title,t.done,t.due_date,t.duration_minutes FROM tasks t "
                +"WHERE t.project_id=? ORDER BY CASE "
                +"WHEN t.done!=0 THEN 4 "
                +"WHEN EXISTS(SELECT 1 FROM project_task_work_sessions s "
                    +"WHERE s.task_id=t.id AND s.ended_at IS NULL) THEN 0 "
                +"WHEN "+ready+" AND EXISTS("
                    +"SELECT 1 FROM project_task_dependencies next "
                    +"JOIN tasks waiter ON waiter.id=next.task_id "
                    +"WHERE next.depends_on_task_id=t.id AND waiter.done=0) THEN 1 "
                +"WHEN "+ready+" THEN 2 ELSE 3 END,"
                +"CASE WHEN t.due_date IS NULL OR TRIM(t.due_date)='' "
                    +"THEN 1 ELSE 0 END,"
                +"t.due_date,t.project_sort_order,t.id";
        try(Cursor c=db.rawQuery(sql,new String[]{Long.toString(projectId)})) {
            while(c.moveToNext())
                out.add(new TaskItem(c.getLong(0),c.getString(1),
                    c.getInt(2)!=0,c.isNull(3)?"":c.getString(3),c.getInt(4)));
        }
        return Collections.unmodifiableList(out);
    }

    static Long activeWorkStartedAt(SQLiteDatabase db,long taskId) {
        try(Cursor c=db.rawQuery(
                "SELECT started_at FROM project_task_work_sessions "
                    +"WHERE task_id=? AND ended_at IS NULL ORDER BY id DESC LIMIT 1",
                new String[]{Long.toString(taskId)})) {
            return c.moveToFirst()?c.getLong(0):null;
        }
    }

    static int workedMinutes(SQLiteDatabase db,long taskId,boolean includeActive) {
        long total=0L;
        try(Cursor c=db.rawQuery(
                "SELECT started_at,ended_at,worked_minutes "
                    +"FROM project_task_work_sessions WHERE task_id=?",
                new String[]{Long.toString(taskId)})) {
            long now=System.currentTimeMillis();
            while(c.moveToNext()) {
                if(c.isNull(1)) {
                    if(includeActive)
                        total+=Math.max(0L,now-c.getLong(0))/60000L;
                } else total+=Math.max(0,c.getInt(2));
            }
        }
        return (int)Math.min(Integer.MAX_VALUE,total);
    }

    static int remainingMinutes(SQLiteDatabase db,long taskId,int plannedMinutes) {
        return Math.max(0,plannedMinutes-workedMinutes(db,taskId,true));
    }

    static int overrunMinutes(SQLiteDatabase db,long taskId,int plannedMinutes) {
        return Math.max(0,workedMinutes(db,taskId,true)-plannedMinutes);
    }

    static void startWork(SQLiteDatabase db,long taskId) {
        Long project=taskProjectId(db,taskId);
        if(project==null)
            throw new IllegalArgumentException("Start pracy dotyczy czynności projektu.");
        try(Cursor c=db.rawQuery("SELECT done FROM tasks WHERE id=?",
                new String[]{Long.toString(taskId)})) {
            if(!c.moveToFirst())
                throw new IllegalArgumentException("Czynność już nie istnieje.");
            if(c.getInt(0)!=0)
                throw new IllegalArgumentException("Czynność jest już oznaczona jako wykonana.");
        }
        String projectBlock=projectWorkBlockReason(db,taskId);
        if(!projectBlock.isEmpty())
            throw new IllegalArgumentException(projectBlock);
        if(openDependencyCount(db,taskId)>0)
            throw new IllegalArgumentException(
                "Najpierw zakończ wcześniejsze czynności zależne.");
        String blocker=ProjectPlanningStore.startBlockReason(db,taskId);
        if(!blocker.isEmpty())
            throw new IllegalArgumentException(blocker);
        if(activeWorkStartedAt(db,taskId)!=null)
            throw new IllegalArgumentException("Ta czynność jest już uruchomiona.");
        ContentValues v=new ContentValues();
        v.put("task_id",taskId);
        v.put("started_at",System.currentTimeMillis());
        v.putNull("ended_at");
        v.putNull("worked_minutes");
        db.insertOrThrow("project_task_work_sessions",null,v);
    }

    static int stopWork(SQLiteDatabase db,long taskId) {
        long sessionId;
        long started;
        try(Cursor c=db.rawQuery(
                "SELECT id,started_at FROM project_task_work_sessions "
                    +"WHERE task_id=? AND ended_at IS NULL ORDER BY id DESC LIMIT 1",
                new String[]{Long.toString(taskId)})) {
            if(!c.moveToFirst())
                throw new IllegalArgumentException("Ta czynność nie jest uruchomiona.");
            sessionId=c.getLong(0);
            started=c.getLong(1);
        }
        long ended=System.currentTimeMillis();
        int minutes=(int)Math.max(1L,Math.round(Math.max(0L,ended-started)/60000.0));
        ContentValues v=new ContentValues();
        v.put("ended_at",ended);
        v.put("worked_minutes",minutes);
        if(db.update("project_task_work_sessions",v,"id=? AND ended_at IS NULL",
                new String[]{Long.toString(sessionId)})!=1)
            throw new IllegalStateException("Nie udało się zakończyć sesji pracy.");
        return minutes;
    }

    static String timeClass(SQLiteDatabase db,long projectId,long taskId) {
        Set<Long> ids=descendantIds(db,projectId);
        if(ids.isEmpty())return "";
        int min=Integer.MAX_VALUE,max=0,value=-1;
        try(Cursor c=db.rawQuery(
                "SELECT id,duration_minutes FROM tasks WHERE project_id IN ("
                    +inClause(ids)+")",args(ids))) {
            while(c.moveToNext()) {
                int minutes=Math.max(1,c.getInt(1));
                min=Math.min(min,minutes);
                max=Math.max(max,minutes);
                if(c.getLong(0)==taskId)value=minutes;
            }
        }
        if(value<0)return "";
        int score=min==max?3:1+(int)Math.round((value-min)*4.0/(max-min));
        score=Math.max(1,Math.min(5,score));
        String[] labels={"Bardzo łatwa","Łatwa","Średnia","Trudna","Bardzo trudna"};
        return "Trudność "+score+"/5 • "+labels[score-1];
    }

    private static Long taskProjectId(SQLiteDatabase db,long taskId) {
        try(Cursor c=db.rawQuery("SELECT project_id FROM tasks WHERE id=?",
                new String[]{Long.toString(taskId)})) {
            return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):null;
        }
    }

    static TaskRef taskRef(SQLiteDatabase db,long taskId) {
        try(Cursor c=db.rawQuery(
                "SELECT id,project_id,title FROM tasks "
                    +"WHERE id=? AND project_id IS NOT NULL",
                new String[]{Long.toString(taskId)})) {
            return c.moveToFirst()
                ?new TaskRef(c.getLong(0),c.getLong(1),c.getString(2)):null;
        }
    }

    static String projectWorkBlockReason(SQLiteDatabase db,long taskId) {
        Long projectId=taskProjectId(db,taskId);
        HashSet<Long> seen=new HashSet<>();
        while(projectId!=null) {
            if(!seen.add(projectId))
                throw new IllegalStateException("Wykryto pętlę w hierarchii projektów.");
            Project item=find(db,projectId);
            if(item==null)return "Projekt tej czynności już nie istnieje.";
            if("paused".equals(item.status))
                return "Projekt „"+item.name+"” jest wstrzymany.";
            if("done".equals(item.status))
                return "Projekt „"+item.name+"” jest zakończony.";
            projectId=item.parentId;
        }
        return "";
    }

    static long rootProjectId(SQLiteDatabase db,long projectId) {
        Project current=find(db,projectId);
        if(current==null)throw new IllegalArgumentException("Projekt nie istnieje.");
        HashSet<Long> seen=new HashSet<>();
        while(current.parentId!=null) {
            if(!seen.add(current.id))
                throw new IllegalStateException("Wykryto pętlę w hierarchii projektów.");
            Project parent=find(db,current.parentId);
            if(parent==null)break;
            current=parent;
        }
        return current.id;
    }

    static List<Long> dependencyIds(SQLiteDatabase db,long taskId) {
        ArrayList<Long> out=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT depends_on_task_id FROM project_task_dependencies "
                    +"WHERE task_id=? ORDER BY created_at,depends_on_task_id",
                new String[]{Long.toString(taskId)})) {
            while(c.moveToNext())out.add(c.getLong(0));
        }
        return Collections.unmodifiableList(out);
    }

    static int openDependencyCount(SQLiteDatabase db,long taskId) {
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM project_task_dependencies d "
                    +"JOIN tasks t ON t.id=d.depends_on_task_id "
                    +"WHERE d.task_id=? AND t.done=0",
                new String[]{Long.toString(taskId)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static List<TaskRef> openDependencies(SQLiteDatabase db,long taskId) {
        ArrayList<TaskRef> out=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT t.id,t.project_id,t.title "
                    +"FROM project_task_dependencies d "
                    +"JOIN tasks t ON t.id=d.depends_on_task_id "
                    +"WHERE d.task_id=? AND t.done=0 "
                    +"ORDER BY t.project_sort_order,t.id",
                new String[]{Long.toString(taskId)})) {
            while(c.moveToNext())
                out.add(new TaskRef(c.getLong(0),c.getLong(1),c.getString(2)));
        }
        return Collections.unmodifiableList(out);
    }

    static int openDependentCount(SQLiteDatabase db,long taskId) {
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM project_task_dependencies d "
                    +"JOIN tasks t ON t.id=d.task_id "
                    +"WHERE d.depends_on_task_id=? AND t.done=0",
                new String[]{Long.toString(taskId)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    static List<TaskRef> openDependents(SQLiteDatabase db,long taskId) {
        ArrayList<TaskRef> out=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT t.id,t.project_id,t.title "
                    +"FROM project_task_dependencies d "
                    +"JOIN tasks t ON t.id=d.task_id "
                    +"WHERE d.depends_on_task_id=? AND t.done=0 "
                    +"ORDER BY t.project_sort_order,t.id",
                new String[]{Long.toString(taskId)})) {
            while(c.moveToNext())
                out.add(new TaskRef(c.getLong(0),c.getLong(1),c.getString(2)));
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean reaches(SQLiteDatabase db,long start,long target) {
        ArrayList<Long> pending=new ArrayList<>();
        HashSet<Long> seen=new HashSet<>();
        pending.add(start);
        while(!pending.isEmpty()) {
            long current=pending.remove(pending.size()-1);
            if(current==target)return true;
            if(!seen.add(current))continue;
            try(Cursor c=db.rawQuery(
                    "SELECT depends_on_task_id FROM project_task_dependencies "
                        +"WHERE task_id=?",
                    new String[]{Long.toString(current)})) {
                while(c.moveToNext())pending.add(c.getLong(0));
            }
            if(seen.size()>20000)
                throw new IllegalStateException("Zbyt wiele zależności projektu.");
        }
        return false;
    }

    static void addDependency(SQLiteDatabase db,long taskId,long dependsOnTaskId) {
        if(taskId==dependsOnTaskId)
            throw new IllegalArgumentException("Czynność nie może zależeć od samej siebie.");
        Long project=taskProjectId(db,taskId);
        Long prerequisiteProject=taskProjectId(db,dependsOnTaskId);
        if(project==null||prerequisiteProject==null)
            throw new IllegalArgumentException(
                "Zależności można ustawiać tylko między czynnościami projektów.");
        if(rootProjectId(db,project)!=rootProjectId(db,prerequisiteProject))
            throw new IllegalArgumentException(
                "Zależność musi dotyczyć czynności z tego samego projektu głównego.");
        if(reaches(db,dependsOnTaskId,taskId))
            throw new IllegalArgumentException("Ta zależność utworzyłaby pętlę.");
        ContentValues v=new ContentValues();
        v.put("task_id",taskId);
        v.put("depends_on_task_id",dependsOnTaskId);
        v.put("created_at",System.currentTimeMillis());
        db.insertWithOnConflict("project_task_dependencies",null,v,
            SQLiteDatabase.CONFLICT_IGNORE);
    }

    static void removeDependency(SQLiteDatabase db,long taskId,long dependsOnTaskId) {
        db.delete("project_task_dependencies",
            "task_id=? AND depends_on_task_id=?",
            new String[]{Long.toString(taskId),Long.toString(dependsOnTaskId)});
    }

    static List<Long> openTaskIdsForPlanning(SQLiteDatabase db,long projectId) {
        Set<Long> projects=descendantIds(db,projectId);
        if(projects.isEmpty())return Collections.emptyList();
        java.util.LinkedHashSet<Long> remaining=new java.util.LinkedHashSet<>();
        try(Cursor c=db.rawQuery(
                "SELECT id FROM tasks WHERE done=0 AND project_id IN ("
                    +inClause(projects)+") ORDER BY duration_minutes DESC,id",
                args(projects))) {
            while(c.moveToNext()) {
                long taskId=c.getLong(0);
                if(projectWorkBlockReason(db,taskId).isEmpty())
                    remaining.add(taskId);
            }
        }
        ArrayList<Long> ordered=new ArrayList<>();
        while(!remaining.isEmpty()) {
            boolean progress=false;
            ArrayList<Long> snapshot=new ArrayList<>(remaining);
            for(Long task:snapshot) {
                boolean waits=false;
                for(Long dependency:dependencyIds(db,task))
                    if(remaining.contains(dependency)) {waits=true;break;}
                if(waits)continue;
                ordered.add(task);
                remaining.remove(task);
                progress=true;
            }
            if(!progress) {
                ordered.addAll(remaining);
                break;
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    static void addResource(SQLiteDatabase db,long projectId,String kind,long targetId) {
        if(find(db,projectId)==null)throw new IllegalArgumentException("Projekt nie istnieje.");
        if(!"thing".equals(kind)&&!"box".equals(kind))
            throw new IllegalArgumentException("Do projektu można przypisać Rzecz albo Pudełko.");
        StorageStore.Item item=StorageStore.find(db,targetId);
        if(item==null||!kind.equals(item.kind))
            throw new IllegalArgumentException("Zasób już nie istnieje.");
        ContentValues v=new ContentValues();
        v.put("project_id",projectId);v.put("target_kind",kind);
        v.put("target_id",targetId);v.put("created_at",System.currentTimeMillis());
        db.insertWithOnConflict("project_resources",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    static long addCost(SQLiteDatabase db,long projectId,String rawName,
            long qtyMilli,String unit,long unitPriceGrosz,String status,String note) {
        if(find(db,projectId)==null)throw new IllegalArgumentException("Projekt nie istnieje.");
        String name=TextEntryRules.capitalizeLabel(rawName);
        if(name.isEmpty()||name.length()>160)
            throw new IllegalArgumentException("Podaj nazwę kosztu/zakupu.");
        if(qtyMilli<=0||unitPriceGrosz<0)
            throw new IllegalArgumentException("Ilość i cena muszą być poprawne.");
        if(!"planned".equals(status)&&!"bought".equals(status)&&!"paid".equals(status))
            throw new IllegalArgumentException("Nieprawidłowy status kosztu.");
        ContentValues v=new ContentValues();
        v.put("project_id",projectId);v.put("name",name);v.put("qty_milli",qtyMilli);
        v.put("unit",unit==null?"szt.":unit);v.put("unit_price_grosz",unitPriceGrosz);
        v.put("status",status);v.put("note",note==null?"":note.trim());
        v.put("created_at",System.currentTimeMillis());
        return db.insertOrThrow("project_costs",null,v);
    }

    static void setCostStatus(SQLiteDatabase db,long id,String status) {
        if(!"planned".equals(status)&&!"bought".equals(status)&&!"paid".equals(status))
            throw new IllegalArgumentException("Nieprawidłowy status kosztu.");
        ContentValues v=new ContentValues();v.put("status",status);
        db.update("project_costs",v,"id=?",new String[]{Long.toString(id)});
    }
}
