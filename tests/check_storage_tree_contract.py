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
assert 'storageTreePlaceHeading(place,depth,key,collapsed,target)' in ui
assert 'storagePlaceHeaderAction(row,"QR / etykieta"' in ui
assert 'storagePlaceHeaderAction(row,"NFC"' in ui
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
assert 'StorageStore.listAll(db.getReadableDatabase())' in ui
assert 'SELECT id FROM storage_items ORDER BY kind,name COLLATE NOCASE,id' not in ui
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
assert 'button("⚡ Szybko dodaj pudełko", this::quickAddStorageBox);' in ui
assert 'button("+ Dodaj pudełko", () -> storageEditor("box", null));' in ui
assert 'button("⚡ Szybko dodaj miejsce", this::quickAddPlace);' in ui
assert 'if(showPlaces)button("← Cofnij"' in ui
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
assert 'thumbView.setOnClickListener(v->showStorageThumbnailActions(item.id));' in ui
assert 'private void showStorageThumbnailActions(long itemId)' in ui
assert 'labels.add("Usuń zdjęcie");' in ui
assert 'labels.add("Pokaż QR");' in ui
assert 'labels.add("Drukuj etykietę / PDF / Udostępnij");' in ui
assert 'labels.add("Przenieś • NFC / QR / ręcznie");' in ui
item_start=ui.index('private void storageTreeItem(')
item_end=ui.index('private void storageItemHeaderAction(',item_start)
item_ui=ui[item_start:item_end]
assert 'smallButton(details,"Pokaż QR"' not in item_ui
assert 'smallButton(details,"Drukuj etykietę / PDF / Udostępnij"' not in item_ui
assert 'smallButton(details,"Przenieś • NFC / QR / ręcznie"' not in item_ui
assert 'smallButton(details,"Wypożycz"' in item_ui
assert 'smallButton(details,"Usuń"' in item_ui
assert 'settingsStorageAction(thingActions,"thing","photo","Zdjęcie / miniatura")' in s
assert 'settingsStorageAction(boxActions,"box","nfc","NFC")' in s
assert 'settingsStorageAction(placeActions,"place","qr","QR / etykieta miejsca")' in s
assert 'STORAGE_GALLERY_PREF = "storage_gallery_enabled"' in s
assert 'private void storageThingsGallery(java.util.List<StorageStore.Item> items)' in s
assert 'if("thing".equals(storageTemporaryKind)) {' in ui
assert 'storageThingsGallery(items);\n            return;' in ui
assert 'title("Rzeczy")' in ui
assert 'missing.setContentDescription("Brak zdjęcia");' in ui
assert 'StorageThumbs.read(prefs,item.id)' in ui
assert 'tile.setOnClickListener(v->showStorageThingDetails(item.id));' in ui
assert 'private void showStorageThingDetails(long itemId)' in ui
assert 'storageEditor(item.kind,item.id);' in ui
assert 'settingsYesNo(storageSettings,"Galeria miniaturek rzeczy"' in s
print("Storage tree: persistent branch state, hierarchy, CRUD/QR actions and no flat duplicate list PASS")

# Stała semantyka kolorów + szybki podgląd miniatury.
for marker in (
    'private int storageKindTextColor(String kind)',
    '"#1565C0":"#64B5F6"',
    '"#A65300":"#FFB74D"',
    '"#2E7D32":"#81C784"',
    'storageKindLegend();',
    'heading.setTextColor(storageKindTextColor("place"));',
    'heading.setTextColor(storageKindTextColor("box"));',
    'storageKindText(\n                "thing","◉ "+item.name,16,true)',
    'private void bindStorageThumbnailPeek(View target,Bitmap thumbnail)',
    'v.postDelayed(showPeek[0],400L);',
    'shown.setTouchable(false);',
    'popup[0].dismiss();',
    'if(action==MotionEvent.ACTION_UP&&consumed)return true;',
    'if(thumbnail!=null)bindStorageThumbnailPeek(thumbView,thumbnail);',
):
    assert marker in s, f"Missing semantic storage color / thumbnail peek: {marker}"

assert 'TextView heading = storageKindText("place", entry.name, 19, true);' in s
assert 'actions.addView(storageKindText(kind,' in s
assert 'actions.addView(storageKindText(kind,name,18,true));' in s
print("Storage semantic colors + hold-to-peek thumbnail: PASS")

# Globalny system semantycznych kolorów EDHOME: nie tylko Magazyn.
for marker in (
    'private int semanticTextColor(String kind)',
    'private CharSequence semanticText(String value)',
    'private void applySemanticTextTree(View view)',
    'applySemanticTextTree(root);',
    'semanticTextColor("place")',
    'semanticTextColor("box")',
    'semanticTextColor("thing")',
    'semanticTextColor("qr")',
    'semanticTextColor("nfc")',
    'view.setText(semanticText(value));',
    'b.setText(semanticText(value));',
    'chip.setText(semanticText(filter[1]));',
):
    assert marker in s, f"Missing global semantic UI marker: {marker}"

# Narzędzia QR są spięte obok siebie, skaner zostaje osobno.
storage_start=s.index('    private void storage() {')
storage_end=s.index('    private void storageEditor(',storage_start)
storage_ui=s[storage_start:storage_end]
assert 'LinearLayout qrTools=compactActionRow();' in storage_ui
assert 'compactAction(qrTools,"▣ Drukuj wybrane etykiety QR / PDF"' in storage_ui
assert 'compactAction(qrTools,"◷ Historia skanowania i drukowania QR"' in storage_ui

# Historia magazynu po każdym wejściu startuje zwinięta i rozwija się lokalnie.
for marker in (
    'private LinearLayout collapsedStorageHistory()',
    'entries.setVisibility(View.GONE);',
    'TextView label=text("▸ Ostatnie ruchy magazynu",18,true);',
    'entries.setVisibility(expand?View.VISIBLE:View.GONE);',
):
    assert marker in s, f"Missing collapsed storage history marker: {marker}"

print("Global semantic colors + compact QR actions + collapsed history: PASS")