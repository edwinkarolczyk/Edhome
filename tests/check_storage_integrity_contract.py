#!/usr/bin/env python3
"""No dangling storage location and full inherited place path."""
from pathlib import Path
import sqlite3
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text()
store=Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text()
gradle=Path("app/build.gradle").read_text()
assert 'versionCode 49' in gradle and "versionNameSuffix '-beta.6'" in gradle
method=main.split('boolean deletePlace(long id) {',1)[1].split('private static void addDeviceTimers(',1)[0]
assert 'SELECT 1 FROM storage_items WHERE place_id=? LIMIT 1' in method
assert method.index('SELECT 1 FROM storage_items WHERE place_id=? LIMIT 1') < method.index('UPDATE tasks SET place_id=NULL')
assert method.index('UPDATE tasks SET place_id=NULL') < method.index('database.delete("places"')
assert 'Miejsce ma podmiejsca lub rzeczy/pudełka' in main
for value in (
    'private static String placePath(SQLiteDatabase db,long placeId)',
    'SELECT name,parent_id FROM places WHERE id=?',
    'path.insert(0,placePath(db,current.placeId)+" / ")',
    'if(!seen.add(id))return "Błąd: cykl miejsc";',
):
    assert value in store, value

db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE places(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER);
CREATE TABLE storage_items(id INTEGER PRIMARY KEY, name TEXT,
 parent_box_id INTEGER, place_id INTEGER);
CREATE TABLE tasks(id INTEGER PRIMARY KEY, place_id INTEGER);
INSERT INTO places VALUES(1,'Dom',NULL);
INSERT INTO storage_items VALUES(1,'Pudełko',NULL,1);
INSERT INTO tasks VALUES(1,1);
""")
def may_delete(place_id):
    has_children=db.execute("SELECT 1 FROM places WHERE parent_id=?", (place_id,)).fetchone()
    has_storage=db.execute("SELECT 1 FROM storage_items WHERE place_id=?", (place_id,)).fetchone()
    if has_children or has_storage:return False
    db.execute("UPDATE tasks SET place_id=NULL WHERE place_id=?", (place_id,))
    db.execute("DELETE FROM places WHERE id=?", (place_id,))
    return True
assert not may_delete(1)
assert db.execute("SELECT place_id FROM tasks").fetchone()==(1,)
db.execute("UPDATE storage_items SET place_id=NULL WHERE id=1")
assert may_delete(1)
assert db.execute("SELECT place_id FROM tasks").fetchone()==(None,)
print("Storage place deletion guard and inherited hierarchy: PASS")
