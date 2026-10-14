#!/usr/bin/env python3
"""Schema-backed test: backup types/nullability, SQLite-to-JSON-to-SQLite; not Android UI."""
import json
import re
import runpy
import sqlite3
from pathlib import Path

ctx=runpy.run_path("tests/check_db_contract.py")
db=ctx["fresh"]
source=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
archive=Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackupArchive.java").read_text(encoding="utf-8")
originals=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageOriginals.java").read_text(encoding="utf-8")
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
definitions=ctx["table_defs"]
manifest={table:re.findall(r'"([^"]+)"', columns) for table,columns in definitions}
assert len(manifest)==56, "Nieoczekiwana liczba tabel backupu: "+str(len(manifest))
assert {"frost_settings", "frost_checks"}.issubset(manifest), "Brak tabel ochrony przed mrozem w kopii"
assert {"budget_items","budget_occurrences","budget_recipients",
        "budget_credits","budget_history"}.issubset(manifest), (
    "Brak tabel budżetu w kopii zapasowej")
numbers=set(re.findall(r'"([^"]+)"\.equals\(column\)',
    source.split("private static boolean isNumberColumn(String column)",1)[1]))
nulls=source.split("if (value == JSONObject.NULL) {",1)[1].split("values.putNull(key);",1)[0]
global_null=set(re.findall(r'"([^"]+)"\.equals\(key\)',
    nulls.split('|| ("storage_items".equals(definition[0])',1)[0]))
scoped={"vehicles":{"oc_reminder_lead","inspection_reminder_lead"},
        "storage_items":{"lent_to","parent_box_id","lent_at"},
        "pantry_purchase_prices":{"shopping_id","pantry_id","quantity_milli"},
        "vehicle_events":{"mileage"},
        "vehicle_tyre_sets":{"tread_tenths"},
        "vehicle_policies":{"goal_id"},
        "vehicle_costs":{"paycheck_operation_id"},
        "bank_evidence_queue":{"matched_operation_id","matched_at"}}
missing_types=[]
missing_nullable=[]
for table,columns in manifest.items():
    info=db.execute('PRAGMA table_info("'+table+'")').fetchall()
    assert [row[1] for row in info]==columns,table
    for _,name,kind,required,default,pk in info:
        if "INT" in kind.upper():
            if name not in numbers: missing_types.append(table+"."+name)
        if not required and not pk:
            if name not in global_null|scoped.get(table,set()):
                missing_nullable.append(table+"."+name)

assert not missing_types, "Numeric types missing: "+", ".join(missing_types)
assert not missing_nullable, "Nullable fields missing: "+", ".join(missing_nullable)

db.execute("INSERT INTO places(id,name,kind,parent_id,icon) VALUES(1,'Dom','Dom',NULL,'places')")
db.execute("INSERT INTO tasks(id,title,done) VALUES(1,'Kontrola',0)")
# Historyczna czynność projektu ma 10 minut: obecne UI wymaga minimum 20
# TYLKO przy tworzeniu/edycji. Backup musi zachować stary plan bez zmian.
db.execute("""INSERT INTO projects(id,name,status,created_at)
VALUES(1,'Projekt archiwalny','active',1790000000000)""")
db.execute("""INSERT INTO tasks(id,title,done,project_id,duration_minutes,project_sort_order)
VALUES(2,'Historyczna czynność',0,1,10,0)""")
db.execute("""INSERT INTO tasks(id,title,done,project_id,duration_minutes,project_sort_order)
VALUES(3,'Druga czynność',0,1,30,1)""")
db.execute("""INSERT INTO project_task_dependencies(task_id,depends_on_task_id,created_at)
VALUES(3,2,1790000000001)""")
db.execute("""INSERT INTO project_task_work_sessions(
id,task_id,started_at,ended_at,worked_minutes)
VALUES(1,2,1790000000000,1790000300000,5)""")
db.execute("INSERT INTO pantry(id,name,qty,category) VALUES(1,'Ryż',2,'other')")
db.execute("INSERT INTO pantry_packages(pantry_id,unit,size_milli) VALUES(1,'szt.',1000)")
db.execute("INSERT INTO shopping_items(id,name,qty_milli,unit,checked) VALUES(1,'Ryż',NULL,'szt.',1)")
db.execute("""INSERT INTO pantry_purchase_prices
(id,operation_id,shopping_id,pantry_id,name_snapshot,unit,quantity_milli,unit_price_grosz,shop,happened_at)
VALUES(1,'11111111-1111-4111-8111-111111111111',1,NULL,'Ryż','szt.',NULL,649,'Sklep',1790000000000)""")
db.execute("""INSERT INTO storage_items
(id,name,kind,parent_box_id,place_id,lent_to,lent_at,created_at)
VALUES(1,'Pudełko','box',NULL,1,NULL,NULL,1790000000000)""")
db.execute("""INSERT INTO paycheck_transactions
(id,operation_id,scope,kind,category,amount_grosz,note,created_at)
VALUES(1,'22222222-2222-4222-8222-222222222222','shared','expense','shopping',649,'',1790000000000)""")
db.execute("""INSERT INTO bank_evidence_queue
(id,evidence_key,source_kind,source_label,kind,amount_grosz,booking_date,
 description,imported_at,state,matched_operation_id,matched_at)
VALUES(1,?,'csv','EDHOME TEST','expense',649,'2026-09-25',
 'Test kolejki',1790000000001,'open',NULL,NULL)""",('a'*64,))
content={}
for table,columns in manifest.items():
    order=("task_id,position" if table=="task_rotation_members"
           else "task_id,depends_on_task_id" if table=="project_task_dependencies"
           else "pantry_id" if table=="pantry_packages" else "id")
    found=db.execute('SELECT '+",".join(columns)+' FROM "'+table+'" ORDER BY '+order).fetchall()
    content[table]=[dict(zip(columns,row)) for row in found]
payload=json.loads(json.dumps({"databaseVersion":35,"tables":content},ensure_ascii=False))
archived_tasks={row["id"]:row for row in payload["tables"]["tasks"]}
assert archived_tasks[2]["duration_minutes"]==10
assert archived_tasks[2]["project_id"]==1
assert payload["tables"]["project_task_dependencies"][0]["depends_on_task_id"]==2
assert payload["tables"]["project_task_work_sessions"][0]["worked_minutes"]==5
# Nie wolno przeliczać historycznych 10 min na 20 podczas importu.
assert 'minutes == null || minutes < 1 || minutes > 600' in source
assert 'taskMinutes<20' not in source
assert 'Czynność projektowa musi mieć co najmniej 20 minut.' not in source
price=payload["tables"]["pantry_purchase_prices"][0]
assert (price["unit_price_grosz"],price["pantry_id"],price["quantity_milli"])==(649,None,None)
assert payload["tables"]["storage_items"][0]["lent_to"] is None
queue=payload["tables"]["bank_evidence_queue"][0]
assert queue["state"]=="open" and queue["matched_operation_id"] is None
assert queue["matched_at"] is None

clone=sqlite3.connect(":memory:")
for (sql,) in db.execute("""SELECT sql FROM sqlite_master WHERE type='table'
AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY name"""):
    clone.execute(sql)
for (sql,) in db.execute("""SELECT sql FROM sqlite_master WHERE type='index'
AND sql IS NOT NULL ORDER BY name"""):
    clone.execute(sql)
for table,columns in manifest.items():
    for row in payload["tables"][table]:
        clone.execute('INSERT INTO "'+table+'" ('+",".join(columns)+') VALUES ('+
                      ",".join("?" for _ in columns)+')',[row[col] for col in columns])
for table in manifest:
    assert clone.execute('SELECT * FROM "'+table+'" ORDER BY rowid').fetchall()==(
        db.execute('SELECT * FROM "'+table+'" ORDER BY rowid').fetchall()),table

restore=source.split("database.beginTransaction();",1)[1]
assert restore.index("if (!restored.commit())")<restore.index(
    "database.setTransactionSuccessful()")<restore.index("database.endTransaction()")
for token in (
    'ZipOutputStream',
    '"edhome-backup-archive"',
    '"manifest.json"',
    '"media/storage-originals/"',
    '"media/storage-thumbnails/"',
    'digestEntry(zip, entry)',
    'verifiedPayloadBytes += digest.size',
    'verifiedPayloadBytes > MAX_EXTRACTED_BYTES',
    'readArchivedThumbnails(archive, inspection.paths)',
    'DataBackup.restoreJson(database, prefs, inspection.json,',
):
    assert token in archive, token
# Archiwum v2 ma niezależne pliki modules/*.json oraz kompatybilność odczytu v1.
modules=Path("app/src/main/java/com/edwinkarolczyk/edhome/BackupModuleSnapshot.java").read_text(
    encoding="utf-8")
for token in (
    'private static final int FORMAT_VERSION = 2;',
    'private static final int LEGACY_FORMAT_VERSION = 1;',
    'manifest.put("categoryIndexVersion", 1)',
    'manifest.put("categories", BackupModuleSnapshot.index(',
    'manifest.put("files", files)',
    'BackupModuleSnapshot.split(new String(data, StandardCharsets.UTF_8))',
    'static Created createLegacy(',
    'archiveVersion != LEGACY_FORMAT_VERSION',
    'archiveVersion != FORMAT_VERSION',
    'moduleHashes.keySet().equals(canonical.keySet())',
    'calculated.sha256.equalsIgnoreCase(',
    'validateCategoryIndex(manifest.getJSONObject("categories"), json)',
    'static JSONObject inspectCategories(File archive)',
    'provided.length() != calculated.length()',
    'actual.optInt(table, -1) != expected.getInt(table)',
    'entry.put("category", sourceCategory(source.path))',
    '!sourceCategory(path).equals(item.optString("category"))',
    'manifest.put("databaseVersion", new JSONObject(',
    'new JSONObject(json).getInt("databaseVersion")',
    'BackupModuleSnapshot.allowedPath(path)',
    'sources.add(0, new Source(DATA, data, null, "database"))',
):
    assert token in archive, "Brak kontraktu ZIP v1/v2: " + token
for token in (
    'static final String PREFIX = "modules/";',
    'section.put("databaseVersion", source.getInt("databaseVersion"))',
    'section.put("tables", group.getValue())',
    'section.put("settings", source.getJSONObject("settings"))',
    'category = categoryForTable(name)',
    'return "budget";',
    'return "paycheck";',
    'return "projects";',
    'return "storage";',
    'return "vehicles";',
    'return "pantry";',
    'return "garden";',
    'return "tasks";',
    'return "calendar";',
    'return "places";',
    'return "profiles";',
    'return "audit";',
    'return "timers";',
    'return "other";',
):
    assert token in modules, "Brak sekcji danych modułu: " + token
assert 'DataBackup.restoreJson(database, prefs, inspection.json,' in archive, (
    "Przywracanie pełnej kopii musi pozostać dostępne")
assert 'if (archiveVersion == FORMAT_VERSION)' in archive, (
    "Odrębne moduły muszą być sprawdzane wyłącznie w ZIP v2")
assert 'if (archiveVersion != FORMAT_VERSION)' in archive, (
    "Nie wolno dopuścić modules/* do starszego ZIP v1")
assert 'private static final int FORMAT_VERSION = 2;' in archive

for token in (
    'static void save(Context context, long itemId, Uri source)',
    'MAX_FILE_BYTES = 32L * 1024 * 1024',
    'regenerateThumbnails(',
    'auxiliaryOrImageId',
    'id + ".bak"',
    'id + ".tmp"',
    'validateImage(temporary)',
    '!destination.exists() && backup.isFile()',
):
    assert token in originals, token
for token in (
    'DataBackupArchive.create(',
    'DataBackupArchive.verifyDocument(',
    'DataBackupArchive.restore(',
    'StorageOriginals.save(this,id,file)',
    'StorageOriginals.save(this,id,selected)',
    'setType("application/zip")',
    'DATA_BACKUP_ZIP_EXPORTED',
):
    assert token in main, token
assert 'DataBackup.restoreJson(' in main, "legacy JSON import must remain supported"
# Starsze kopie z EDHOME 0.8.0.25 mają bazę v45.
# Kopie v45 i v46 muszą być obsługiwane w SQLite v47.
# Brakujące tabele budżetu/frost powstają podczas importu, bez czyszczenia historii.
assert 'inputVersion != 44 && inputVersion != 45 && inputVersion != 46 && inputVersion != DB_VERSION' in source, (
    "EDHOME backup: schemas v45/v46 must remain restorable")
assert 'if (inputVersion < 47)' in source
assert 'INSERT OR IGNORE INTO frost_settings(id) VALUES(1)' in source
assert 'inputVersion < 46 && definition[0].startsWith("budget_")' in source, (
    "EDHOME backup: v45 must migrate without requiring v46 budget tables")
assert 'Map<Long,String> archivedStorageThumbs' in source
assert 'restoredStorageThumbs.size() + archivedStorageThumbs.size() > 200' in source
assert 'legacy_thumbs=' in main
assert '"Etap: "+backupStage' in main
for token in (
    'exportDataBackupAsync(data.getData());',
    'prepareDataBackupImportAsync(data.getData());',
    '"edhome-backup-export").start();',
    '"edhome-backup-prepare").start();',
    '"edhome-backup-restore").start();',
    'showDataBackupRestoreConfirmation(',
    'restoreDataBackupAsync(backupFile,archive,legacyJson)',
    'DATA_BACKUP_BACKGROUND_STARTED',
):
    assert token in main, "Backup heavy I/O must stay off UI thread: "+token
for token in (
    'private AlertDialog dataBackupBusyDialog;',
    'showDataBackupBusy("Tworzenie kopii"',
    'showDataBackupBusy("Sprawdzanie kopii"',
    'showDataBackupBusy("Przywracanie kopii"',
    '.setCancelable(false)',
    'hideDataBackupBusy();',
):
    assert token in main, "Background restore must stay serialized behind modal: "+token
# P0: selektywne przywracanie musi mieć checkpoint, walidację i cofanie.
selective=Path("app/src/main/java/com/edwinkarolczyk/edhome/BackupSelectiveMerge.java").read_text(
    encoding="utf-8")
for token in (
    'static String merge(String installed, String archived, Set<String> requested)',
    'OLDEST_SELECTIVE_SCHEMA = 45;',
    'if (!archivedTables.has(table))',
    'CURRENT_SCHEMA = 47;',
    'sourceVersion < 47 && table.startsWith("frost_")',
    'String flag = "places".equals(table) ? "frost_risk" : "protect_frost";',
    'if (!row.has(flag)) row.put(flag, 0);',
    'installedTables.put(table, archivedTables.getJSONArray(table))',
    'copyKeys(incomingSettings, settings,',
    'copyKeys(settings, replacement, storageKeys)',
    'copyKeys(settings, replacement, budgetKeys)',
    'JSONArray currentSync = current.getJSONArray("syncRecords")',
    'JSONArray backupSync = old.getJSONArray("syncRecords")',
    'current.put("syncRecords", joinedSync)',
    'mergeBudgetHistory(',
    'PaycheckBudgetSqliteStore.sameJson(',
    'meta.put("rowHash", "DELETED")',
    'restoredUuids.contains(existing.getKey())',
    'retainedHistoryRows.containsKey(from.getString("rowKey"))',
):
    assert token in selective, "Brak bezpiecznego scalania modułów: " + token
for token in (
    'static Restored restoreSelected(',
    'BackupSelectiveMerge.merge(before, inspection.json, selected)',
    'File checkpoint = recoveryCheckpoint(context, database, prefs)',
    'extractMedia(archive, inspection.paths, stageStorage, stageTiles, stageBudget)',
    'if (selected.contains("storage"))',
    'if (selected.contains("settings"))',
    'if (selected.contains("budget"))',
    'DataBackup.restoreJson(database, prefs, merged, photos, false)',
    'verifyUnselected(before, DataBackup.exportJson(database, prefs), selected)',
    'DataBackup.restoreJson(database, prefs, before,',
    'restoreExact(context, database, prefs, checkpoint)',
    'output.getFD().sync()',
    'static File recoveryCheckpoint(',
    'static Restored restoreExact(',
    'Nie wolno zmienić ani jednego wpisu w niewybranych modułach.',
):
    assert token in archive, "Brak zabezpieczenia selektywnego przywracania: " + token
for token in (
    'boolean preserveLocalBudgetHistory',
    'JSONArray retainedBudgetHistory = preserveLocalBudgetHistory',
    'JSONArray retainedBudgetCache = new JSONArray(',
    'preserveLocalBudgetHistory',
    '? prefs.getString(PaycheckBudgetHistoryStore.PREF_KEY, "[]")',
):
    assert token in source, "Brak ścisłego rollbacku historii: " + token
for token in (
    'setMultiChoiceItems(labels,checked,',
    'DataBackupArchive.inspectCategories(backupFile)',
    'DataBackupArchive.restoreSelected(',
    'DataBackupArchive.recoveryCheckpoint(',
    'DataBackupArchive.restoreExact(',
    'button("Cofnij ostatnie przywrócenie"',
    'button("Eksportuj ZIP w starszym formacie v1"',
    'created=legacyFormat',
):
    assert token in main, "Brak obsługi modułów w Android UI: " + token

# Android AlertDialog pokazuje listę ALBO setMessage; użycie obu w tym
# samym kreatorze ukrywa checkboxy (regresja wykryta na filmie z telefonu).
picker=main.split('private void showDataBackupRestoreConfirmation(',1)[1].split(
    'private void confirmFullDataBackupRestore(',1)[0]
list_dialog=picker.split('new AlertDialog.Builder(this)',1)[1].split(
    '.setMultiChoiceItems(labels,checked,',1)[0]
assert '.setTitle("Co przywrócić z kopii?' in list_dialog
assert '.setMessage(' not in list_dialog, (
    "P0: setMessage zasłania listę wyboru modułów w Android AlertDialog")
assert '.setMultiChoiceItems(labels,checked,' in picker
assert 'labels[i]=backupCategoryName(category)+" ("+total+" wpisów)"' in picker

# P0 — film Android 0.8.0.96: Projekty bez Zadań i Magazyn bez Miejsc.
# Dodatkowe moduły wymagają WYRAŹNEJ zgody; żadnych cichych nadpisań.
dep=Path("app/src/main/java/com/edwinkarolczyk/edhome/BackupRestoreDependencies.java").read_text(
    encoding="utf-8")
for token in (
    'static Set<String> resolve(String installed, String archived,',
    'selected.contains("projects")',
    'hasRows(source, "project_task_dependencies")',
    'hasRows(source, "project_task_work_sessions")',
    'containsProjectTasks(source)',
    'selected.add("tasks")',
    'selected.contains("storage")',
    '"storage_items", "place_id", "places", "places"',
    '"tasks", "project_id", "projects", "projects"',
    'selected.contains("places")',
    'guardUnselectedPlaces(current, source, selected)',
    'PaycheckBudgetSqliteStore.sameJson(',
    'selection.add(category)',
    'Kopia nie zawiera modułu wymaganego przez powiązania.',
):
    assert token in dep, "Brak kontroli zależności modułów: " + token
for token in (
    'BackupRestoreDependencies.resolve(',
    'if (!names.containsAll(required))',
    'Wybór nie obejmuje wymaganych powiązań:',
):
    assert token in selective, "Nieodpowiednio zabezpieczony import częściowy: " + token
assert 'static String verifiedSnapshot(File archive)' in archive
for token in (
    'previewSelectedBackupDependencies(backupFile,categories,chosen)',
    'BackupRestoreDependencies.resolve(',
    'DataBackupArchive.verifiedSnapshot(archiveFile)',
    'BackupSelectiveMerge.merge(installed,incoming,planned)',
    'confirmSelectedBackupDependencies(',
    'WYMAGANE DODATKOWO:',
    'Przywróć razem',
    'restoreDataBackupAsync(archiveFile,true,null,planned)',
):
    assert token in main, "Brak potwierdzenia powiązanych kategorii: " + token
assert main.index('BackupRestoreDependencies.resolve(') < main.index(
    'restoreDataBackupAsync(archiveFile,true,null,planned)'), (
    "Relacje należy obliczyć przed importem")

print("Backup: 54 tables, ZIP v1/v2, selective restore + rescue checkpoint static contracts PASS")
