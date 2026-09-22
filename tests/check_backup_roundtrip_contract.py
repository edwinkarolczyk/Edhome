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
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
definitions=ctx["table_defs"]
manifest={table:re.findall(r'"([^"]+)"', columns) for table,columns in definitions}
assert len(manifest)==24
numbers=set(re.findall(r'"([^"]+)"\.equals\(column\)',
    source.split("private static boolean isNumberColumn(String column)",1)[1]))
nulls=source.split("if (value == JSONObject.NULL) {",1)[1].split("values.putNull(key);",1)[0]
global_null=set(re.findall(r'"([^"]+)"\.equals\(key\)',
    nulls.split('|| ("storage_items".equals(definition[0])',1)[0]))
scoped={"storage_items":{"lent_to","parent_box_id"},
        "pantry_purchase_prices":{"shopping_id","pantry_id","quantity_milli"}}
for table,columns in manifest.items():
    info=db.execute('PRAGMA table_info("'+table+'")').fetchall()
    assert [row[1] for row in info]==columns,table
    for _,name,kind,required,default,pk in info:
        if "INT" in kind.upper():
            assert name in numbers,table+"."+name+" integer rejected as wrong type"
        if not required and not pk:
            assert name in global_null|scoped.get(table,set()), (
                table+"."+name+" SQL allows null but import rejects it")

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
content={}
for table,columns in manifest.items():
    order=("task_id,position" if table=="task_rotation_members"
           else "pantry_id" if table=="pantry_packages" else "id")
    found=db.execute('SELECT '+",".join(columns)+' FROM "'+table+'" ORDER BY '+order).fetchall()
    content[table]=[dict(zip(columns,row)) for row in found]
payload=json.loads(json.dumps({"databaseVersion":22,"tables":content},ensure_ascii=False))
price=payload["tables"]["pantry_purchase_prices"][0]
assert (price["unit_price_grosz"],price["pantry_id"],price["quantity_milli"])==(649,None,None)
assert payload["tables"]["storage_items"][0]["lent_to"] is None

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
assert 'if (!verifyDataBackupDocument(data.getData(), bytes))' in main
assert 'MessageDigest.isEqual(expectedHash, actualHash.digest())' in main
assert '"wt"' in main and 'DATA_BACKUP_VERIFY_FAILED' in main
print("Backup: 24 tables and all numeric/nullable fields, sample JSON roundtrip, readback and rollback contract PASS")
