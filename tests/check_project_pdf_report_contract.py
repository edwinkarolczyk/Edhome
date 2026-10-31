#!/usr/bin/env python3
"""Projects: offline PDF report content, placement and safe sharing contract."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/edwinkarolczyk/edhome"
main = (SRC / "MainActivity.java").read_text(encoding="utf-8")
report = (SRC / "ProjectReportPdf.java").read_text(encoding="utf-8")
backup = (SRC / "DataBackup.java").read_text(encoding="utf-8")
paths = (ROOT / "app/src/main/res/xml/storage_file_paths.xml").read_text(encoding="utf-8")

project_screen = main.split("private void projects()", 1)[1].split(
    "private void bindProjectLaborSummary", 1
)[0]
assert 'smallButton(summary,"▣ PDF",()->shareProjectReport(project.id));' in project_screen
assert project_screen.index('smallButton(summary,"▣ PDF"') < project_screen.index('summary.addView(text("Status: "')
assert "private void shareProjectReport(long projectId)" in main
assert "new Thread(()->" in main
assert "ProjectReportPdf.create(db.getReadableDatabase(),projectId)" in main
assert 'getPackageName()+".storage.files",out' in main
assert 'name="project_reports" path="project-reports/"' in paths

for marker in (
    "ProjectStore.descendantIds",
    "ProjectStore.stats",
    "FROM tasks WHERE project_id IN",
    "project_task_work_sessions",
    "FROM project_costs",
    "FROM task_history",
    "task_thumbnail_1_base64",
    "task_thumbnail_2_base64",
    "ProjectStore.thumbnail",
    "EDHOME • Idea by Edwin",
    "Wszystkie koszty:",
    "Wykonane czynności:",
    "nie przechowuje pełnego dziennika",
    "wersje skompresowane",
    "PdfDocument",
):
    assert marker in report, marker

assert "insert(" not in report.lower()
assert "update(" not in report.lower()
assert "DB_VERSION = 52" in backup
print("PASS: project PDF report placement, content, read-only export and scoped sharing")
