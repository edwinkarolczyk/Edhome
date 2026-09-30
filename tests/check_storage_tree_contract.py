#!/usr/bin/env python3
"""EDHOME Magazyn displays one nested place/box/thing tree with persistent collapse."""
from pathlib import Path
s=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
start=s.index("    private void storage() {")
end=s.index("    private void storageEditor(",start)
ui=s[start:end]
assert 'title("Podgląd magazynu")' in ui
assert 'storageTreePlace(place,places,items,drawnPlaces,drawnItems,0,body)' in ui
assert 'storageTreeItem(child,items,drawnItems,childDepth,inner)' in ui
assert 'String key="storage_tree_place_"+place.id;' in ui
assert 'String key="storage_tree_box_"+item.id;' in ui
assert 'prefs.getBoolean(key,false)' in ui
assert 'prefs.edit().putBoolean(prefKey,nowCollapsed).apply();' in ui
assert 'row.setOnClickListener(v->' in ui
assert 'item.boxId==null && item.placeId!=null' in ui
assert 'child.parent!=null&&child.parent==place.id' in ui
assert 'child.boxId!=null && child.boxId==item.id' in ui
assert 'children.setVisibility(nowCollapsed?View.GONE:View.VISIBLE);' in ui
assert 'storageTreeAttach(details,inner);' in ui
assert 'render();' not in ui[ui.index('private LinearLayout storageTreeHeading('):ui.index('private void storageTreePlace(')]
assert 'if(depth>64||!drawnItems.add(item.id))return;' in ui
assert 'if(depth>64||!drawnPlaces.add(place.id))return;' in ui
assert 'StorageStore.location(db.getReadableDatabase(),item)' in ui
assert 'StorageStore.remove(db.getWritableDatabase(),item.id)' in ui
assert 'StorageStore.returned(db.getWritableDatabase(),item.id)' in ui
assert 'showStorageQr(item)' in ui
assert 'storageEditor(item.kind,item.id)' in ui
assert 'SELECT id FROM storage_items ORDER BY kind,name COLLATE NOCASE,id' in ui
assert 'box.addView(text(("box".equals(item.kind)?' not in ui
assert 'STORAGE_SHOW_THINGS_PREF = "storage_show_things"' in s
assert 'STORAGE_SHOW_BOXES_PREF = "storage_show_boxes"' in s
assert 'STORAGE_SHOW_PLACES_PREF = "storage_show_places"' in s
assert 'boolean showThings=storageThingsVisible();' in ui
assert 'boolean showBoxes=storageBoxesVisible();' in ui
assert 'boolean showPlaces=storagePlacesVisible();' in ui
assert 'if(showThings) {' in ui
assert 'button("⚡ Szybko dodaj rzecz", this::quickAddStorageThing);' in ui
assert 'button("+ Dodaj rzecz", () -> storageEditor("thing", null));' in ui
assert 'if(showBoxes)button("+ Dodaj pudełko"' in ui
assert 'if(showPlaces)button("← Miejsca"' in ui
assert 'boolean visible=storagePlacesVisible();' in ui
assert 'boolean visible=isBox?storageBoxesVisible():storageThingsVisible();' in ui
assert 'childDepth=depth+1;' in ui
assert 'ukrywanie niczego nie usuwa' in ui
assert 'storageTemporaryKind=kind;' in s
assert 'Rzeczy / narzędzia' in s
assert '"Pudełka",STORAGE_SHOW_BOXES_PREF' in s
assert '"Miejsca",STORAGE_SHOW_PLACES_PREF' in s
assert 'STORAGE_ACTION_PREFIX = "storage_action_"' in s
assert 'private boolean storageActionVisible(String kind,String action)' in s
assert 'storageActionVisible(item.kind,"photo")' in ui
assert 'storageActionVisible(item.kind,"qr")' in ui
assert 'storageActionVisible(item.kind,"nfc")' in ui
assert 'storageActionVisible(item.kind,"print")' in ui
assert 'storageActionVisible(item.kind,"move")' in ui
assert 'storageActionVisible(item.kind,"lend")' in ui
assert 'storageActionVisible(item.kind,"delete")' in ui
assert 'settingsStorageAction(thingActions,"thing","photo","Zdjęcie / miniatura")' in s
assert 'settingsStorageAction(boxActions,"box","nfc","NFC")' in s
assert 'settingsStorageAction(placeActions,"place","qr","QR / etykieta miejsca")' in s
assert 'STORAGE_GALLERY_PREF = "storage_gallery_enabled"' in s
assert 'private void storageThingsGallery(java.util.List<StorageStore.Item> items)' in s
assert 'if(showThings && prefs.getBoolean(STORAGE_GALLERY_PREF,true))' in ui
assert 'title("Galeria rzeczy")' in ui
assert 'Brak zdjęcia' in ui
assert 'StorageThumbs.read(prefs,item.id)' in ui
assert 'tile.setOnClickListener(v->showStorageLocator(item));' in ui
assert 'tile.setOnLongClickListener(v->{' in ui
assert 'storageEditor(item.kind,item.id);' in ui
assert 'settingsYesNo(storageSettings,"Galeria miniaturek rzeczy"' in s
print("Storage tree: persistent branch state, hierarchy, CRUD/QR actions and no flat duplicate list PASS")
