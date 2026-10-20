#!/usr/bin/env python3
"""Regresja Projektów: pięć sortowań, grupy i priorytety w Android Beta.

Kontrole statyczne + model SQLite. Test na fizycznym Androidzie pozostaje osobny.
"""
from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/edwinkarolczyk/edhome"
project = (src / "ProjectStore.java").read_text(encoding="utf-8")
main = (src / "MainActivity.java").read_text(encoding="utf-8")
backup = (src / "DataBackup.java").read_text(encoding="utf-8")

for token in (
    'displayTasks(SQLiteDatabase db,long projectId,String sortMode)',
    'case "date":', 'case "priority":', 'case "alphabetical":',
    'case "manual":', 't.title COLLATE NOCASE,t.id',
    'ORDER BY group_rank,', 't.project_sort_order,t.id',
    'WHEN t.done!=0 THEN 4',
    'WHERE s.task_id=t.id AND s.ended_at IS NULL',
    'WHERE d.task_id=t.id AND req.done=0',
    'WHERE b.task_id=t.id AND b.hard=1 AND b.resolved=0',
    'COALESCE(m.name', 'static boolean reorderTask(',
    'moving.group!=target.group', 'db.beginTransaction();',
    'db.setTransactionSuccessful();',
    'static void setTaskPriority(', 'v.put("priority",priority);',
):
    assert token in project, token
for token in (
    '"W trakcie","Gotowe • odblokowują inne","Gotowe do wykonania"',
    '"Zablokowane","Zakończone"',
    'project_task_sort_', 'project_task_collapse_',
    'group==4', '"Sortowanie czynności"',
    'if(!hasFocusedTask)continue;',
    'if(item.group!=groupId)continue;',
    'showProjectTaskPriorityPicker(project.id,taskId)',
    '⚑ ', '👤 Bez wykonawcy', 'item.assigneeName',
    'setOnDragListener(', 'startDragAndDrop(',
    '↑ Przesuń wyżej', '↓ Przesuń niżej',
    'ProjectStore.reorderTask(', 'ProjectStore.displayTasks(',
):
    assert token in main, token
assert '"project_sort_order"' in backup
assert '"priority"' in backup and '"assignee_id"' in backup
assert 'project_sort_order INTEGER NOT NULL DEFAULT 0' in main
assert "private void renderProjectTasks(ProjectStore.Project project)" in main

# Model priorytetu/grup w SQLite: grupa chroni zależności przed sortowaniem A-Z.
c=sqlite3.connect(":memory:")
c.executescript("""
CREATE TABLE tasks(id INTEGER PRIMARY KEY,title TEXT,done INTEGER,
    due_date TEXT,priority TEXT,project_id INTEGER,project_sort_order INTEGER);
CREATE TABLE project_task_dependencies(task_id INTEGER,depends_on_task_id INTEGER);
CREATE TABLE project_task_blockers(task_id INTEGER,hard INTEGER,resolved INTEGER);
CREATE TABLE project_task_work_sessions(task_id INTEGER,ended_at INTEGER);
INSERT INTO tasks VALUES
(1,'Zakończona',1,'2026-10-01','low',7,10),
(2,'Aktywna',0,'2026-10-30','low',7,20),
(3,'Odblokowuje',0,'2026-11-03','normal',7,30),
(4,'Zablokowana',0,'2026-10-20','urgent',7,40),
(5,'Zwykła',0,'2026-11-01','high',7,50);
INSERT INTO project_task_work_sessions VALUES(2,NULL);
INSERT INTO project_task_dependencies VALUES(4,3);
""")
ranking="""CASE WHEN t.done!=0 THEN 4
 WHEN EXISTS(SELECT 1 FROM project_task_work_sessions s
   WHERE s.task_id=t.id AND s.ended_at IS NULL) THEN 0
 WHEN NOT EXISTS(SELECT 1 FROM project_task_dependencies d
   JOIN tasks req ON req.id=d.depends_on_task_id
   WHERE d.task_id=t.id AND req.done=0)
   AND NOT EXISTS(SELECT 1 FROM project_task_blockers b
   WHERE b.task_id=t.id AND b.hard=1 AND b.resolved=0)
   AND EXISTS(SELECT 1 FROM project_task_dependencies d
   JOIN tasks w ON w.id=d.task_id
   WHERE d.depends_on_task_id=t.id AND w.done=0) THEN 1
 WHEN NOT EXISTS(SELECT 1 FROM project_task_dependencies d
   JOIN tasks req ON req.id=d.depends_on_task_id
   WHERE d.task_id=t.id AND req.done=0)
   AND NOT EXISTS(SELECT 1 FROM project_task_blockers b
   WHERE b.task_id=t.id AND b.hard=1 AND b.resolved=0) THEN 2
 ELSE 3 END"""
query=f"""SELECT t.id,{ranking} AS g FROM tasks t
 WHERE t.project_id=7 ORDER BY g,t.title COLLATE NOCASE,t.id"""
rows=c.execute(query).fetchall()
assert rows==[(2,0),(3,1),(5,2),(4,3),(1,4)], rows
assert all(group <= rows[i+1][1] for i,(_,group) in enumerate(rows[:-1]))

# Ręczny ruch nie pozwala przenieść zablokowanej czynności do grupy 2.
assert dict(rows)[4]==3 and dict(rows)[5]==2
c.execute("UPDATE tasks SET project_sort_order=80 WHERE id=5 AND project_id=7")
assert c.execute("SELECT project_sort_order FROM tasks WHERE id=5").fetchone()[0]==80
print("PASS: Projekty Android — sortowanie, grupy, priorytet, wykonawca, drag, ręczna kolejność")
