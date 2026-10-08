#!/usr/bin/env python3
"""Regresja modułu Projekty: hierarchia, zależności, koszty, zasoby i planer."""
import sqlite3
from pathlib import Path

root = Path(".")
project = (root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = (root / "app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
sync = (root / "app/src/main/java/com/edwinkarolczyk/edhome/SyncRecordStore.java").read_text(encoding="utf-8")
time = (root / "app/src/main/java/com/edwinkarolczyk/edhome/TimeSuggestions.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
work_notification = (root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectWorkNotification.java").read_text(encoding="utf-8")
lan_service = (root / "app/src/main/java/com/edwinkarolczyk/edhome/LanSyncService.java").read_text(encoding="utf-8")

# Projekty są warstwą nad wspólnymi Czynnościami i Magazynem.
for marker in (
    "CREATE TABLE IF NOT EXISTS projects",
    "CREATE TABLE IF NOT EXISTS project_resources",
    "CREATE TABLE IF NOT EXISTS project_costs",
    "CREATE TABLE IF NOT EXISTS project_task_dependencies",
    "descendantIds",
    "openTaskIdsForPlanning",
    'Trudność "+score+"/5',
    "Zależność musi dotyczyć czynności z tego samego projektu głównego.",
    "Ta zależność utworzyłaby pętlę.",
):
    assert marker in project, marker

for marker in (
    'case "projects": projects();',
    "showProjectDependencyDialog",
    "showProjectBlockers",
    "Zaproponuj terminy projektu",
    "Plan używa pozostałego czasu po Start/Stop",
    "+ Rzecz / Pudełko",
    "+ Koszt / zakup",
):
    assert marker in main, marker

assert "MAX_TASK_MINUTES = 600" in main
assert "durationMinutes > 600" in time
assert 'private static final int DB_VERSION = 45;' in backup
assert '{"project_task_dependencies", "task_id", "depends_on_task_id", "created_at"}' in backup
assert '"project_task_dependencies".equals(table)' in sync
assert 'return new String[]{"task_id","depends_on_task_id"};' in sync
assert '"project_task_dependencies".equals(table)' in desktop
assert 'canonicalId(row.get("depends_on_task_id"))' in desktop
assert 'database.delete("project_task_dependencies"' in main
assert 'project_task_work_sessions' in project
assert 'PROJECT_MIN_TASK_MINUTES = 20' in main
assert 'TaskRules.minutesFromParts' in main
assert 'hoursBox.addView(text("Godziny",12,false))' in main
assert 'minutesBox.addView(text("Minuty",12,false))' in main
assert 'automatycznie ustawi na 20 min' in main
assert '0,5 • 1 • 1,5' not in main
assert 'PROJECT_WORK_STARTED' in main and 'PROJECT_WORK_STOPPED' in main
assert 'bindProjectTimeStatus(projectTime,taskId,minutes,activeStarted)' in main
assert 'view.postDelayed(refresh[0],1000L);' in main
assert 'projectTimeRed()' in main and 'projectTimeYellow()' in main and 'projectTimeGreen()' in main
assert 'addProjectTimeProgress(summary,stats);' in main
assert 'addProjectTimeProgress(box,stats);' in main
assert 'stats.activeWork?projectTimeRed():projectTimeYellow()' in main
assert 'boolean timeOverrun()' in project and 'int timePct()' in project
assert 'Czas przekroczony, praca nadal trwa.' in main
assert 'Czas przekroczony, pomiar zatrzymany.' in main
assert '?projectTimeRed():projectTimeGreen()' in main
assert 'overrun>0?projectTimeYellow():projectTimeGreen()' in main
assert 'ProjectStore.remainingMinutes' in main
assert 'TimeSuggestions.proposeAvailability' in main
assert 'ProjectPlanningStore.windows' in main
assert 'ProjectPlanningStore.startBlockReason' in project
assert 'project_task_blockers' in main
assert 'database.delete("project_task_work_sessions"' in main
project_tasks=main.split("private void renderProjectTasks",1)[1].split(
    "private int projectTimeGreen",1)[0]
assert 'final boolean completionBlocked=blockers>0||hardRequirements>0;' in project_tasks
assert 'check.setEnabled(done||!completionBlocked);' in project_tasks
assert 'showProjectBlockedDialog(taskId,taskName);' in project_tasks
assert 'ProjectStore.openDependencyCount' in project_tasks
assert 'ProjectPlanningStore.openHardCount' in project_tasks
assert 'confirmDeleteProjectTask(taskId,taskName)' in project_tasks
assert 'private void confirmDeleteProjectTask' in main
assert 'ProjectWorkNotification.cancel(this,taskId);' in main
assert 'PROJECT_TASK_DELETED' in main
assert 'ProjectStore.displayTasks' in main
assert 'showProjectBlockedDialog' in main
assert '🔗 Czeka na: ' in main
assert '▣ Brakuje: ' in main
assert '↳ Od tej czynności zależą ' in main
assert 'ProjectStore.openDependents(db.getReadableDatabase(),taskId)' in project_tasks
assert 'showProjectDependentsDialog(taskId,taskName)' in project_tasks
assert 'openProjectTask(dependent.id)' in project_tasks
assert 'openProjectTask(dependency.id)' in main
assert 'pendingProjectTaskFocusId' in main
assert 'applyProjectTaskIntent(getIntent(),false)' in main
assert 'open_project_task_id' in main
assert 'openDependencies' in project
assert 'openDependentCount' in project
assert 'static List<TaskItem> displayTasks' in project
assert 'projectWorkBlockReason' in project
assert 'Projekt „"+item.name+"” jest wstrzymany.' in project
assert 'Nie można zakończyć projektu. Pozostało ' in project
assert 'open_project_task_id' in work_notification
assert 'open_project_id' in work_notification
assert 'EDHOME • Projekt: ' in work_notification
assert '"Otwórz czynność"' in work_notification
assert '.setGroup(GROUP_ID)' in work_notification
assert 'GROUP_ID = "edhome-lan-status"' in lan_service
assert '"project_sort_order"' in backup
assert 'project_sort_order INTEGER NOT NULL DEFAULT 0' in main
assert 'DATABASE_MIGRATED_44_TO_45_PROJECT_TASK_ORDER' in main
assert 't.project_sort_order,t.id' in project
assert 'if(hours==0)return rest+" min";' in main
assert 'return hours+" h "+rest+" min";' in main
assert '"20 min","30 min","45 min","1 h","1 h 30 min","2 h"' in main
quick_tasks=main.split("private void showProjectQuickTasks",1)[1].split(
    "private void renderProjectTasks",1)[0]
assert 'due.setFocusable(false);' in quick_tasks
assert 'new DatePickerDialog' in quick_tasks
assert 'due.setHint("Termin • dotknij, aby wybrać datę")' in quick_tasks
assert 'reminder.setText("08:00")' in quick_tasks
assert 'dueValue.isEmpty()||remind.isEmpty()?null:remind' in quick_tasks

dependency_dialog=main.split("private void showProjectDependencyDialog",1)[1].split(
    "private void showProjectBlockers",1)[0]
assert '.setMultiChoiceItems(labels.toArray(new String[0]),chosen' in dependency_dialog
assert 'database.beginTransaction();' in dependency_dialog
assert 'if(current.equals(desired))return;' in dependency_dialog
assert 'if(!desired.contains(old))' in dependency_dialog
assert 'if(!current.contains(added))' in dependency_dialog
assert 'ProjectStore.removeDependency(database,taskId,old);' in dependency_dialog
assert 'ProjectStore.addDependency(database,taskId,added);' in dependency_dialog
assert 'database.endTransaction();' in dependency_dialog
assert '.setMessage(' not in dependency_dialog

# Minimalny kontrakt SQL zależności: brak self-loop i brak duplikatów.
db = sqlite3.connect(":memory:")
db.execute("""CREATE TABLE project_task_dependencies (
    task_id INTEGER NOT NULL,
    depends_on_task_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    CHECK(task_id!=depends_on_task_id),
    UNIQUE(task_id,depends_on_task_id)
)""")
db.execute("INSERT INTO project_task_dependencies VALUES (2,1,1)")
try:
    db.execute("INSERT INTO project_task_dependencies VALUES (2,1,2)")
    raise AssertionError("Duplicate dependency allowed")
except sqlite3.IntegrityError:
    pass
try:
    db.execute("INSERT INTO project_task_dependencies VALUES (3,3,3)")
    raise AssertionError("Self dependency allowed")
except sqlite3.IntegrityError:
    pass

# Koszty Projektów pozostają osobnym rejestrem, a nie automatycznym księgowaniem PayCheck.
assert "Koszty projektu nie są księgowane w PayCheck." in main
print("Projects v1 contract: PASS")
# Stabilizacja Android/Desktop: identyczne blokady, kolejność i bezpieczne usuwanie.
assert 'openDependents' in project
assert 'Ta czynność jest wymagana przez:' in main
assert 'Usuń mimo to' in main
for marker in (
    'desktopProjectWorkBlockReason',
    'desktopProjectTaskRank',
    'deleteDesktopProjectTask',
    'desktopOpenDependencyTitles',
    'Najpierw zatrzymaj pomiar czasu tej czynności.',
):
    assert marker in desktop, "Missing Desktop project stabilization: "+marker
assert 'desktopProjectDescendsFrom' in desktop
assert 'Nie można zakończyć projektu. Pozostało ' in desktop
assert 'hubEnsureSingleActiveProjectSession' in desktop
assert 'Ta czynność ma już aktywny timer na innym urządzeniu.' in desktop
assert 'JsonObject working=snapshot.deepCopy();' in desktop
print("projects Android/Desktop blocker/order/delete stabilization contract OK")

# P0 Projekty 08.10.2026 — pełny CRUD wymagań oraz czytelne checkboxy.
blocker_ui=main.split("private void showProjectBlockers",1)[1].split(
    "private void showProjectResourcePicker",1)[0]
for marker in (
    'checked.setChecked(item.resolved);',
    'ProjectPlanningStore.setResolved',
    'showProjectBlockerEditorDialog',
    'ProjectPlanningStore.updateBlocker',
    'ProjectPlanningStore.deleteBlocker',
    'Wybierz datę',
    'PROJECT_BLOCKER_EDITED',
):
    assert marker in blocker_ui, "Missing Android requirement CRUD: "+marker
assert 'if(done)' in project_tasks and '↶ Cofnij' in project_tasks
assert 'quickDuration.setSelection(0);' in main
assert 'static void updateBlocker(' in (root /
    "app/src/main/java/com/edwinkarolczyk/edhome/ProjectPlanningStore.java"
    ).read_text(encoding="utf-8")
for marker in (
    'showDesktopTaskRequirements',
    'renderDesktopTaskRequirements',
    'editDesktopTaskRequirement',
    'toggleDesktopProjectCompletion',
    'Najpierw zatrzymaj pomiar czasu tej czynności.',
    'PROJECT_TASK_COMPLETED_DESKTOP',
):
    assert marker in desktop, "Missing Desktop requirement or completion UX: "+marker
print("Projects P0 requirement CRUD and dependency editor contract OK")

# P0: okno Wymagania nie może dołączać paska już osadzonego w body.
# compactActionRow() automatycznie dołącza element do głównego ekranu.
requirements_dialog=main.split("private void showProjectBlockers(",1)[1].split(
    "private void showProjectBlockerActions(",1)[0]
assert "LinearLayout actions=compactActionRow();" not in requirements_dialog
assert "LinearLayout actions=new LinearLayout(this);" in requirements_dialog
assert "actions.setOrientation(LinearLayout.HORIZONTAL);" in requirements_dialog
assert "row.addView(actions);" in requirements_dialog
print("Projects Requirements dialog has only one view parent OK")




# Dopracowanie 08.10: stan blokad, nazwy zależności i widoczne akcje Desktop.
desktop_card=desktop.split("private JPanel desktopProjectTaskCard",1)[1].split(
    "private JsonObject desktopTaskById",1)[0]
for marker in (
    'desktopOpenDependencyTitles(taskId)',
    'desktopHardBlockReason(taskId)',
    'desktopOpenDependentTitles(taskId)',
    'Zależą od tej czynności',
    'Gotowa do wykonania',
    '⛔ ',
    'actions,BorderLayout.SOUTH',
):
    assert marker in desktop_card, "Brak czytelnego stanu w Desktop: "+marker
assert 'private java.util.List<String> desktopOpenDependentTitles(' in desktop
assert 'requirementsTotal=ProjectPlanningStore.blockers(' in project_tasks
assert 'openRequirements>0?' in project_tasks
print("Projects named dependents and responsive Desktop actions contract OK")

# Projekty P0: zachowanie pozycji listy i nieutraconego formularza.
assert 'pendingProjectTaskFocusView=box;' in main
assert 'final View focusedProjectTask=pendingProjectTaskFocusView;' in main
assert 'scroll.scrollTo(0,destination);' in main
assert 'if(pageScroll==scroll && restoreScreen.equals(screen))' in main
assert 'DiagnosticLog.error("PROJECT_TASK_SAVE",saveError);' in main
assert 'Dane formularza zachowano.' in main
assert 'if(id!=null)ReminderReceiver.cancelTask(this,id);' in main
assert 'scroll.setName("edhome-project-tasks");' in desktop
assert 'sectionScrollPane(content, current)' in desktop
assert 'namedScrollPane(node,"edhome-project-tasks")' in desktop
print("Projects Android/Desktop scroll and safe task-edit contract OK")

# Bezpieczeństwo przenoszenia: nie wolno osierocić istniejących zależności.
task_editor=main.split("private void editTask(",1)[1].split(
    "private void showTaskHistory(",1)[0]
assert 'boolean movingAcrossRoots=false;' in task_editor
assert 'ProjectStore.rootProjectId(' in task_editor
assert 'WHERE task_id=? OR depends_on_task_id=? LIMIT 1' in task_editor
assert 'Najpierw usuń te powiązania w Projektach.' in task_editor
assert 'if(movingAcrossRoots)' in task_editor
assert 'scroll.putClientProperty("edhome.projectId",Long.valueOf(projectId))' in desktop
assert 'sectionScrollKey("Projekty",desktopProjectId)' in desktop
print("Projects dependency move guard and per-project scroll contract OK")

