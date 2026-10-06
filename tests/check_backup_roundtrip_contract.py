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
assert len(manifest)==49
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
print("Backup: 49 domain tables + verified ZIP manifest/SHA-256, original storage media, thumbnail regeneration, legacy JSON and rollback contract PASS")
