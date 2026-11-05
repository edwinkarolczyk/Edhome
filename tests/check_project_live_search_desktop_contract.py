#!/usr/bin/env python3
"""Desktop regression contract for project live search and last active task."""
from pathlib import Path

source = Path("desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
detail = source.split("private JComponent desktopProjectDetail", 1)[1].split("private void showDesktopProjectPdfReport", 1)[0]
assert "Szukaj w projekcie: czynności, zakupy, materiały" in detail
assert "DocumentListener" in detail
assert "desktopProjectTaskCard(task)" in detail
assert 'table("project_costs")' in detail
assert 'table("project_resources")' in detail
assert "desktopProjectDescendsFrom(owner,projectId)" in detail
assert 'PREFS.getLong("project_last_active_task_"+projectId,0L)' in detail
assert "Ostatnio aktywna czynność:" in detail
start = source.split("private void startDesktopProjectWork", 1)[1].split("private void stopDesktopProjectWork", 1)[0]
assert 'PREFS.putLong("project_last_active_task_"+activeProjectId,taskId)' in start
assert "activeProjectId=longValue(activeProject,\"parent_id\")" in start
print("Desktop project live search and last active task: PASS")
