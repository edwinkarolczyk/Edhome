#!/usr/bin/env python3
"""Etap 5C: kontrakt ochrony zdarzeń; test źródłowy (nie test na telefonie)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
android = root / "app/src/main/java/com/edwinkarolczyk/edhome"
backup = (android / "DataBackup.java").read_text(encoding="utf-8")
sync = (android / "SyncRecordStore.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
archive = (root / "desktop/src/main/java/com/edhome/desktop/DesktopBudgetHistoryArchive.java").read_text(encoding="utf-8")

restore = backup[backup.index("database.beginTransaction();"):]
pos = lambda phrase: restore.index(phrase)
assert pos("PaycheckBudgetSqliteStore.archivedHistory(database, null)") < pos(
    "database.delete(TABLES[i][0], null, null)")
assert pos("retainedBudgetCache = new JSONArray(") < pos(
    "database.delete(TABLES[i][0], null, null)")
assert pos("PaycheckBudgetSqliteStore.reconcileRaw(database,") < pos(
    "PaycheckBudgetSqliteStore.appendHistory(database, retainedBudgetHistory)")
assert pos("PaycheckBudgetSqliteStore.appendHistory(database, retainedBudgetHistory)") < pos(
    "PaycheckBudgetSqliteStore.appendHistory(database, retainedBudgetCache)")
assert pos("PaycheckBudgetSqliteStore.appendHistory(database, retainedBudgetCache)") < (
    restore.index("SyncRecordStore.ensureAll(database)", pos(
        "PaycheckBudgetSqliteStore.appendHistory(database, retainedBudgetCache)")))
assert pos("SyncRecordStore.ensureAll(database)") < pos("if (!restored.commit())")
assert "Historia Budżetu jest tylko do dopisywania." in sync
assert 'if("budget_history".equals(table)' in sync
assert 'DesktopBudgetHistoryArchive.retain(snapshot,incomingRoot);' in desktop
assert 'if("budget_history".equals(table)) {' in desktop
assert "throw new DesktopHubServer.Conflict" in archive
assert 'newRows.add(oldRow.deepCopy());' in archive
print("Etap 5C P0: append-only history, transaction rollback and conflict guards — contract PASS")
