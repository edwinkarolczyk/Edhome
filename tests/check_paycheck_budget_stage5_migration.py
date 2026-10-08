"""Etap 5A: migracja bezstratna, backup i synchronizacja danych budżetu."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
java=root/"app/src/main/java/com/edwinkarolczyk/edhome"
store=(java/"PaycheckBudgetSqliteStore.java").read_text(encoding="utf-8")
history=(java/"PaycheckBudgetHistoryStore.java").read_text(encoding="utf-8")
backup=(java/"DataBackup.java").read_text(encoding="utf-8")
main=(java/"MainActivity.java").read_text(encoding="utf-8")
sync=(java/"SyncRecordStore.java").read_text(encoding="utf-8")

for table in ("budget_items","budget_occurrences","budget_recipients",
              "budget_credits","budget_history"):
    assert f'"{table}"' in store, table
    assert f'{{"{table}", "id", "entity_key", "payload"}}' in backup, table
assert "CREATE TABLE IF NOT EXISTS " in store
assert "PaycheckBudgetStableIds.of(table,key)" in store
assert 'SQLiteDatabase.CONFLICT_REPLACE' in store
assert "entity_key TEXT NOT NULL UNIQUE" in store

assert "static void reconcileRaw(" in store
assert "new JSONArray(itemsJson)" in store
assert "new JSONArray(recipientsJson)" in store
assert "new JSONArray(historyJson)" in store
assert "static void appendHistory(" in store
assert "if(!key.equals(current.getString(0))" in store
assert 'upsert(db,"budget_history",key,json)' in store
assert "archiveBeforeTrim(events)" in history
assert history.index("archiveBeforeTrim(events)") < history.index(
    "while (events.size() > MAX_EVENTS) events.remove(0)")
assert "static List<Event> loadAll(SQLiteDatabase db" in history
assert "unique.putIfAbsent(event.id,event)" in history
assert "PaycheckBudgetHistoryStore.loadAll(" in main

assert 'DB_VERSION = 46' in backup
assert 'null, 46);' in main
assert 'newVersion > 46' in main
assert 'if(oldVersion < 46)' in main
assert "PaycheckBudgetSqliteStore.create(database);" in main
assert "PaycheckBudgetSqliteStore.configure(this);" in main
assert "PaycheckBudgetSqliteStore.reconcile(db.getWritableDatabase(),prefs);" in main
assert "PaycheckBudgetSqliteStore.reconcile(database,prefs);" in backup
assert 'inputVersion < 46 && definition[0].startsWith("budget_")' in backup
restore=backup.split("database.beginTransaction();",1)[1]
assert "PaycheckBudgetSqliteStore.reconcileRaw(database," in restore
assert restore.index("PaycheckBudgetSqliteStore.reconcileRaw(database,") < (
    restore.index("if (!restored.commit())"))
assert 'SyncRecordStore.exportMetadata(database)' in backup
assert 'DataBackup.syncDefinitions()' in sync
assert 'DataBackup.syncColumns(table)' in sync

# Nie pomylić fazy przygotowawczej z pełnym wdrożeniem Desktop.
desktop=(root/"desktop/src/main/java/com/edhome/desktop/DesktopBudgetPlanner.java").read_text(encoding="utf-8")
assert 'paycheck-budget-plan.json' in desktop
print("EDHOME Budżet etap 5A: 5 tabel, backup, migracja i archiwum SQLite PASS")
