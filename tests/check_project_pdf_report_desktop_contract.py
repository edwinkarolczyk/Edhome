#!/usr/bin/env python3
"""Desktop project PDF report action, saved data and embedded photos contract."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
desktop = (ROOT / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
pdf = (ROOT / "desktop/src/main/java/com/edhome/desktop/DesktopReportPdf.java").read_text(encoding="utf-8")
gradle = (ROOT / "desktop/build.gradle").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/desktop-beta.yml").read_text(encoding="utf-8")

assert 'DESKTOP_VERSION = "0.7.0.146"' in desktop
assert "version = '0.7.0.146'" in gradle
assert 'DESKTOP_VERSION: "0.7.0.146"' in workflow
assert 'actionButton("▣ PDF")' in desktop
assert "showDesktopProjectPdfReport(project)" in desktop
for marker in (
    'table("projects")',
    'table("tasks")',
    'table("project_task_work_sessions")',
    'table("project_costs")',
    'table("task_history")',
    'task_thumbnail_1_base64',
    'task_thumbnail_2_base64',
    'thumbnail_base64',
    'printTableReport("Raport projektu',
):
    assert marker in desktop, marker
for marker in ("jpegBase64", "ImageIO.read", "LosslessFactory.createFromImage"):
    assert marker in pdf, marker
assert "Desktop project PDF report contract" in workflow
print("PASS: Desktop project PDF report, saved photos, history and version gate")
