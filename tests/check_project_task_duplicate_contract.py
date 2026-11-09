#!/usr/bin/env python3
"""Regression contract for project-local similar-name warnings."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
rules = Path("app/src/main/java/com/edwinkarolczyk/edhome/ProjectTaskDuplicateRules.java").read_text(encoding="utf-8")
quick = main.split("private void showProjectQuickTasks", 1)[1].split("private static final String[] PROJECT_TASK_SORT_VALUES", 1)[0]
scope = main.split("private void confirmProjectTaskDuplicate", 1)[1].split("private void showProjectQuickTasks", 1)[0]
assert "ProjectStore.projectTaskIds" in scope
assert 'SELECT title,done FROM tasks WHERE id=?' in scope
assert "ProjectTaskDuplicateRules.isSimilar(title,existingTitle)" in scope
assert "Dodaj mimo to" in scope and "openProjectTask(duplicateId)" in scope
assert 'text("Otwórz tę czynność  →",13,true)' in scope
assert "duplicateName=text(duplicateTitle,16,true)" in scope
assert "setItems(labels.toArray" not in scope
assert "duplicateConfirmed[0]" in quick and "confirmProjectTaskDuplicate(project.id" in quick
assert "projectDuplicateConfirmed[0]" in main
assert "confirmProjectTaskDuplicate(selectedProjectId,title,null" in main
assert "done" in scope
assert "Normalizer.Form.NFD" in rules and "distance(a,b)" in rules
assert "common.size()/(double)all.size()>=0.60" in rules
print("Project task duplicate warning contract: PASS")
