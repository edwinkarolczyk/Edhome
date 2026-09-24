#!/usr/bin/env python3
"""EDHOME Magazyn displays one nested place/box/thing tree with persistent collapse."""
from pathlib import Path
s=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
start=s.index("    private void storage() {")
end=s.index("    private void storageEditor(",start)
ui=s[start:end]
assert 'title("Drzewko magazynu")' in ui
assert 'storageTreePlace(place,places,items,drawnPlaces,drawnItems,0)' in ui
assert 'storageTreeItem(item,items,drawnItems,depth+1)' in ui
assert 'String key="storage_tree_place_"+place.id;' in ui
assert 'String key="storage_tree_box_"+item.id;' in ui
assert 'prefs.getBoolean(key,false)' in ui
assert 'prefs.edit().putBoolean(prefKey,!collapsed).apply();' in ui
assert 'row.setOnClickListener(v->' in ui
assert 'item.boxId==null && item.placeId!=null' in ui
assert 'child.parent!=null&&child.parent==place.id' in ui
assert 'child.boxId!=null && child.boxId==item.id' in ui
assert 'if(collapsed)return;' in ui
assert 'if(depth>64||!drawnItems.add(item.id))return;' in ui
assert 'if(depth>64||!drawnPlaces.add(place.id))return;' in ui
assert 'StorageStore.location(db.getReadableDatabase(),item)' in ui
assert 'StorageStore.remove(db.getWritableDatabase(),item.id)' in ui
assert 'StorageStore.returned(db.getWritableDatabase(),item.id)' in ui
assert 'showStorageQr(item)' in ui
assert 'storageEditor(item.kind,item.id)' in ui
assert 'SELECT id FROM storage_items ORDER BY kind,name COLLATE NOCASE,id' in ui
assert 'box.addView(text(("box".equals(item.kind)?' not in ui
print("Storage tree: persistent branch state, hierarchy, CRUD/QR actions and no flat duplicate list PASS")
