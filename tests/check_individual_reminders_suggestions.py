#!/usr/bin/env python3
"""Regresja osobnych przypomnień i bezpiecznych zgłoszeń EDHOME Beta."""
from pathlib import Path
import importlib.util
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/edwinkarolczyk/edhome"
receiver = (src / "ReminderReceiver.java").read_text(encoding="utf-8")
main = (src / "MainActivity.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(
    encoding="utf-8"
)
workflow = (root / ".github/workflows/suggestions-beta.yml").read_text(
    encoding="utf-8"
)

# Zwykłe przypomnienie 08:00: każde niezależnie, bez zbiorczego notificationId.
legacy = receiver.split("if(legacy) {\n            // Bez podsumowania", 1)[1].split(
    "boolean proposalsReady=", 1
)[0]
assert "for(DailyTask task:dailyTasks)notifyDailyTask" in legacy
assert "pref.edit()" in legacy and '"reminder_legacy_day"' in legacy
assert "notifyPlanning(context,notifications,planning.suggested," in legacy
assert 'notifications.notify("edhome-task-"+task.id,ALARM_ID,builder.build())' in receiver
assert 'notifications.notify("edhome-task-"+singleTaskId,ALARM_ID,notification)' in receiver
assert '"edhome://reminder/task/"+task.id' in receiver
assert '.putExtra("open_task_id",task.id)' in receiver
assert 'boolean overdue;' in receiver and "task.overdue" in receiver
assert 'new DailyTask(tasks.getLong(0),' in receiver
assert 'ProjectWorkNotification.startAction(context,task.id)' in receiver
assert 'ReminderRules.target(taskDue, time, lead,' in receiver
assert "if (QuietHoursRules.isQuiet(now, quietStart, quietEnd))" in receiver
assert "int notificationId = plannerOnly ? PLAN_ALARM_ID" in receiver

# Formularze chronią sekrety i nie umieszczają tokenów w programach.
for code in (main, desktop):
    assert '"https://github.com/edwinkarolczyk/Edhome/issues/new"' in code
    assert "[EDHOME Sugestia] " in code and "[EDHOME Błąd] " in code
    assert "github_suggestion_draft_body" in code
assert 'this::showSuggestionDialog' in main
assert "sendSuggestion.addActionListener(e -> showDesktopSuggestionDialog())" in desktop
assert "URI.create(url)" in desktop
assert "Uri.encode(bodyText)" in main
assert 'issues: read' in workflow and 'contents: write' in workflow
assert 'branches: [beta]' in workflow

# Zgłoszenia innych osób i PR-y nie trafiają do pliku.
module_path = root / ".github/scripts/sync_edhome_suggestions.py"
spec = importlib.util.spec_from_file_location("edhome_suggestions_sync", module_path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
sample = {
    "number": 321, "title": "[EDHOME Sugestia] Większe przyciski",
    "body": "Proszę o wygodniejsze klikanie przycisków Start/Stop.",
    "user": {"login": "edwinkarolczyk"},
    "state": "open", "created_at": "2026-10-23T14:00:00Z"
}
assert "GitHub Issue #321" in module.entry(sample)
assert "Większe przyciski" in module.entry(sample)
assert module.entry({**sample,"user":{"login":"nieznajomy"}}) == ""
assert module.entry({**sample,"pull_request":{"url":"https://..."}}) == ""
assert module.entry({**sample,"title":"Dowolny temat"}) == ""
original_fetch = module.fetch_issues
original_target = module.TARGET
try:
    with tempfile.TemporaryDirectory() as tmp:
        module.TARGET=Path(tmp)/"SUGESTIE.md"
        module.fetch_issues=lambda:[sample, {**sample,"user":{"login":"ktoś"}}]
        module.main()
        text = module.TARGET.read_text(encoding="utf-8")
        assert text.count("<!-- edhome-issue:321 -->") == 1
        assert "Zgłoszenie" not in text or "GitHub Issues" in text
finally:
    module.TARGET=original_target
    module.fetch_issues=original_fetch

print("PASS: indywidualne powiadomienia i bezpieczne sugestie Android/Desktop/GitHub")
