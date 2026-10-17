#!/usr/bin/env python3
"""Realny test kalkulatora robocizny, niezalezny od Android SDK."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
source = root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectLaborCalculator.java"
smoke = root / "tests/project_labor_cost/ProjectLaborCalculatorSmoke.java"
store = (root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")

assert "static ProjectLaborCalculator.Summary laborPreview(" in store
assert "FROM project_task_work_sessions s JOIN tasks t" in store
assert "t.project_id IN (" in store
assert "s.worker_id" in store
assert "ProjectLaborCalculator.calculate(sessions,now)" in store
assert "worked_minutes" not in store.split("static ProjectLaborCalculator.Summary laborPreview(", 1)[1].split("static int remainingMinutes", 1)[0]
assert "db.insert" not in store.split("static ProjectLaborCalculator.Summary laborPreview(", 1)[1].split("static int remainingMinutes", 1)[0]
assert "db.update" not in store.split("static ProjectLaborCalculator.Summary laborPreview(", 1)[1].split("static int remainingMinutes", 1)[0]

if not shutil.which("javac") or not shutil.which("java"):
    raise SystemExit("Wymagane JDK do uruchomienia testu robocizny.")
with tempfile.TemporaryDirectory(prefix="edhome-labor-") as temp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temp, str(source), str(smoke)], check=True)
    subprocess.run(["java", "-cp", temp,
                    "com.edwinkarolczyk.edhome.ProjectLaborCalculatorSmoke"], check=True)
print("Kontrakt robocizny Projektow: OK")
