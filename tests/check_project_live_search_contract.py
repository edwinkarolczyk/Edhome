#!/usr/bin/env python3
"""Regression contract for project-scoped live search and last-started task shortcut."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
detail = main.split("private void projects()", 1)[1].split("private void bindProjectLaborSummary", 1)[0]
search = main.split("private void addProjectSearch", 1)[1].split("private ProjectStore.TaskItem findProjectTask", 1)[0]
results = main.split("private void renderProjectSearchResults", 1)[1].split("/** Raport jest generowany", 1)[0]

assert "addProjectSearch(project.id);" in detail
assert 'setHint("Szukaj w projekcie:' in search
assert "TextWatcher" in search and "renderProjectSearchResults(projectId,value.toString(),results)" in search
assert "project_last_active_task_" in search
assert "openProjectTask(lastTask)" in search
assert "collectProjectTreeIds" in search
assert "ProjectStore.displayTasks" in results
assert "FROM project_costs WHERE project_id=?" in results
assert "FROM project_resources WHERE project_id=?" in results
assert "openProjectTask(task.id)" in results
assert "PROJECT_WORK_STARTED" in main
start = main.split("ProjectStore.startWork(db.getWritableDatabase(),taskId);", 1)[1][:450]
assert 'putLong("project_last_active_task_"+project.id,taskId)' in start
assert "putLong(\"project_last_active_at_\"+project.id,System.currentTimeMillis())" in start
assert "body.addView(searchCard)" not in search  # card() attaches itself
print("Project live search and last active task shortcut: PASS")
