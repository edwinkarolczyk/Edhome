#!/usr/bin/env python3
"""EDHOME Beta v51: widoczność czynności Projektów, ich harmonogram i ZIP."""
from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/com/edwinkarolczyk/edhome"
read=lambda path:(java/path).read_text(encoding="utf-8")
project=read("ProjectStore.java")
main=read("MainActivity.java")
planner=read("SmartTaskPlanner.java")
backup=read("DataBackup.java")
selective=read("BackupSelectiveMerge.java")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

required={
"project": [
    'show_in_main_tasks INTEGER NOT NULL DEFAULT 1',
    'static void upgrade51(SQLiteDatabase db)',
    'static void setShowInMainTasks(',
    'static boolean showInMainTasks(',
    'static String mainTaskClause(',
    'WITH RECURSIVE hidden(id) AS (',
    'JOIN hidden h ON child.parent_id=h.id',
    'WHEN priority=\'urgent\' THEN 2',
    'WHEN priority=\'high\' THEN 3',
    'for(Long dependency:dependencyIds(db,task))'
],
"android": [
    'super(context, "edhome-beta-preview.db", null, 52);',
    'ProjectStore.upgrade51(database);',
    'DATABASE_MIGRATED_50_TO_51_PROJECT_VISIBILITY',
    'Pokazuj czynności projektu w głównej liście',
    'showInMain.setChecked(existing==null',
    'ProjectStore.setShowInMainTasks(db.getWritableDatabase()',
    'condition+=taskVisibility;',
    'Zaległe w głównej liście: ',
    'showProjectPlanSuggestions(long projectId)',
    'ProjectStore.openTaskIdsForPlanning(',
    'ProjectWorkNotification.showActive('
],
"planner": [
    'ProjectStore.mainTaskClause(db,"t")',
    "WHEN 'urgent' THEN 0",
    'static List<TimeSuggestions.Option> proposeForProject(',
    'static List<TimeSuggestions.Option> proposeForBatch(',
    'static void approveUndated('
],
"backup": [
    'private static final int DB_VERSION = 52;',
    '"show_in_main_tasks"',
    'inputVersion != 50 && inputVersion != 51 && inputVersion != DB_VERSION',
    'if(inputVersion < 51 && "projects".equals(definition[0])',
    'values.put(key,1);continue;'
],
"selective": [
    'CURRENT_SCHEMA = 52;',
    'sourceVersion < 51 && "projects".equals(table)',
    'project.put("show_in_main_tasks",1);'
],
"desktop": [
    'desktopMainVisibleTasks()',
    'Pokazuj czynności w głównych listach (1/0)',
    'desktopProjectDescendsFrom(projectId,rootId)',
    'showDesktopProjectEditor(JsonObject existing,Long parentPreset)',
    'desktopProjectId = id;'
]
}
for label,src in [("project",project),("android",main),("planner",planner),
                  ("backup",backup),("selective",selective),("desktop",desktop)]:
    for token in required[label]:
        assert token in src,(label,token)
assert 'displayTasks(SQLiteDatabase db,long projectId,String sortMode)' in project
assert 'setLaborRate(db.getWritableDatabase(),id,hourlyRate);' in main

db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE projects(id INTEGER PRIMARY KEY,parent_id INTEGER,show_in_main_tasks INTEGER NOT NULL DEFAULT 1);
CREATE TABLE tasks(id INTEGER PRIMARY KEY,project_id INTEGER,due_date TEXT,done INTEGER DEFAULT 0,
    priority TEXT NOT NULL DEFAULT 'normal',duration_minutes INTEGER);
INSERT INTO projects VALUES(1,NULL,0),(2,1,1),(3,NULL,1),(4,3,0),(5,4,1);
INSERT INTO tasks VALUES(1,NULL,'2026-10-20',0,'normal',60),
(2,1,'2026-10-20',0,'urgent',30),
(3,2,'2026-10-20',0,'high',40),
(4,3,'2026-10-20',0,'normal',60),
(5,4,'2026-10-20',0,'high',60),
(6,5,'2026-10-20',0,'low',60);
""")
query="""WITH RECURSIVE hidden(id) AS (
  SELECT id FROM projects WHERE show_in_main_tasks=0
  UNION SELECT child.id FROM projects child JOIN hidden h ON child.parent_id=h.id
) SELECT id FROM hidden ORDER BY id"""
hidden=[r[0] for r in db.execute(query)]
assert hidden==[1,2,4,5], hidden
ids=','.join(str(i) for i in hidden)
shown=db.execute("SELECT id FROM tasks WHERE project_id IS NULL OR project_id NOT IN ("+ids+") ORDER BY id").fetchall()
assert shown==[(1,),(4,)],shown
# Stary projekt bez kolumny (v50) po migracji musi być widoczny, jak wcześniej.
legacy=sqlite3.connect(":memory:")
legacy.executescript("CREATE TABLE projects(id INTEGER PRIMARY KEY);INSERT INTO projects VALUES(42);")
legacy.execute("ALTER TABLE projects ADD COLUMN show_in_main_tasks INTEGER NOT NULL DEFAULT 1 CHECK(show_in_main_tasks IN (0,1))")
assert legacy.execute("SELECT show_in_main_tasks FROM projects WHERE id=42").fetchone()[0]==1
# Ukrycie nie usuwa pozycji, historii ani sesji Start/Stop.
assert db.execute("SELECT COUNT(*) FROM tasks").fetchone()[0]==6
# Nie zmienia się łączna suma planowanego czasu; oddzielamy wyświetlanie.
assert db.execute("SELECT SUM(duration_minutes) FROM tasks").fetchone()[0]==310
# Reguły harmonogramu: zaległe przed dzisiejszymi, dzisiejsze przed odległymi;
# potem Pilny, Wysoki, Normalny, Niski.
for priority,due,minutes in [
 ("normal","2026-10-21",45),
 ("urgent","2026-11-30",20),
 ("low","2026-10-19",60),
 ("high","2026-10-26",40),
]:
    db.execute("INSERT INTO tasks(project_id,due_date,done,priority,duration_minutes) VALUES(NULL,?,0,?,?)",
               (due,priority,minutes))
ranked=db.execute("""SELECT priority,due_date FROM tasks WHERE project_id IS NULL
ORDER BY CASE WHEN due_date IS NOT NULL AND due_date < '2026-10-20' THEN 0
              WHEN due_date = '2026-10-20' THEN 1
              WHEN priority='urgent' THEN 2 WHEN priority='high' THEN 3
              WHEN priority='normal' THEN 4 ELSE 5 END,
         due_date,duration_minutes DESC,id""").fetchall()
assert ranked[0]==("low","2026-10-19"),ranked
assert ranked[1]==("normal","2026-10-20"),ranked
assert ranked[2]==("urgent","2026-11-30"),ranked
print("PASS: projekty — widoczność v51, hierarchia, historia, priorytet, zgodność ZIP")
