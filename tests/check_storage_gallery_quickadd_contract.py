from pathlib import Path
import re

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(
    encoding="utf-8"
)
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text(
    encoding="utf-8"
)
gradle = Path("app/build.gradle").read_text(encoding="utf-8")

assert int(re.search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 143
assert '⚡ Szybko dodaj rzecz' in main
assert 'private void quickAddStorageThing()' in main
assert 'NFC • Przypisz / zmień tag' in main
assert '📍 Ustaw położenie • NFC / QR / lista' in main
assert 'pendingStorageDestinationItemId' in main
assert 'handleStorageDestinationNfc(current,uid)' in main
assert 'STORAGE_LOCATION_SET_BY_NFC' in main
for marker in (
    'private void showCreateStorageTargetFromUnknownNfc(long itemId,String uid)',
    '＋ Nowe pudełko',
    '＋ Nowe miejsce',
    'private void showCreateStorageTargetNameDialog(long itemId,String uid,String kind)',
    'Nowe pudełko z tego taga',
    'Nowe miejsce z tego taga',
    'NfcLinkStore.bind(db.getWritableDatabase(),uid,kind,targetId)',
    'applyStorageDestination(\n                            itemId,kind,targetId,"nfc")',
    'STORAGE_UNKNOWN_NFC_TARGET_CREATED',
):
    assert marker in main, f"Missing unknown NFC create-target flow: {marker}"
assert 'STORAGE_LOCATION_SET_BY_QR' in main
assert 'private void showStorageMoveOptions(StorageStore.Item item)' in main
assert 'NFC • Dotknij tagu celu' in main
assert 'QR • Zeskanuj etykietę celu' in main
assert 'private void beginStorageDestinationQr(long itemId)' in main
assert 'Pudełko możesz przenieść tylko do miejsca.' in main
assert 'Rzecz możesz przenieść do pudełka albo miejsca.' in main
assert 'title("Rzeczy")' in main
assert 'Szukaj rzeczy lub miejsca…' in main
assert 'renderStorageGalleryGrid' in main
assert 'showStorageThingDetails(item.id)' in main
assert 'private void showStorageThingDetails(long itemId)' in main
assert 'Przytrzymaj ' in main
assert 'miniaturę około 0,4 s' in main
assert 'bindStorageThumbnailPeek(tile,thumbnail)' in main
assert 'private int storageGalleryPageSize()' in main
assert 'StorageThumbs.isLowRamDevice()?30:60' in main
assert 'for(int i=0;i<visible;i+=3)' in main
assert 'Pokaż kolejne ' in main
assert 'STORAGE_GALLERY_BATCH' in main
assert 'StorageStore.locations(db.getReadableDatabase(),things)' in main
assert 'renderStorageGalleryGrid(grid,things,\"\")' not in main
assert 'static Map<Long,String> locations(SQLiteDatabase db,List<Item> targets)' in store
assert 'SELECT id,name,kind,parent_box_id,place_id,lent_to FROM storage_items' in store
assert 'SELECT id,name,parent_id FROM places' in store
assert 'for(int slot=0;slot<3;slot++)' in main
assert 'if("thing".equals(storageTemporaryKind)) {' in main
assert 'storageThingsGallery(items);\n            return;' in main
assert '📍 Gdzie to jest?' in main
assert 'showStorageLocator' in main

# Skaner działa w obie strony: rzecz -> cel oraz cel -> rzecz/pudełko.
assert 'private String pendingStorageDropKind;' in main
assert 'beginStorageDropTarget("box", id, name)' in main
assert 'beginStorageDropTarget("place", id, name)' in main
assert 'private void handleStorageDropSource(String sourceKind,long sourceId,String source)' in main
assert 'handleStorageDropSourceNfc(current,uid)' in main
assert 'handleStorageDropSource(storageTarget.kind, storageTarget.id, "qr")' in main
assert '📥 Włóż rzecz tutaj • skanuj QR / NFC' in main
assert '📍 Zostaw tutaj rzecz lub pudełko • skanuj QR / NFC' in main
assert 'Do pudełka możesz włożyć rzecz.' in main
assert 'W miejscu możesz zostawić rzecz albo pudełko.' in main
assert 'STORAGE_DROP_COMPLETED' in main

# Storage hub and direct home shortcuts must stay in sync.
catalog = Path("app/src/main/java/com/edwinkarolczyk/edhome/HomeTileCatalog.java").read_text(
    encoding="utf-8"
)
assert '"storage_things", "storage_boxes"' in catalog
assert 'case "storage_things": return "Rzeczy";' in catalog
assert 'case "storage_boxes": return "Pudełka";' in catalog
assert 'ensureStorageShortcutTilesSeeded();' in main
assert 'private void ensureStorageShortcutTilesSeeded()' in main
assert 'storageTemporaryKind = "storage_things".equals(target)' in main
assert 'private void storageViewSwitcher()' in main
assert '{"all", "Wszystko"}, {"thing", "Rzeczy"}' in main
assert '{"box", "Pudełka"}, {"place", "Miejsca"}' in main
assert 'if (storageTemporaryKind != null)' in main
assert 'STORAGE_VIEW_CHANGED' in main

# The quick NFC flow must rely on the existing inherited-location model:
# a thing points at a box, and StorageStore.location walks the box chain until place_id.
assert 'if(current.placeId!=null)' in store
assert 'current=find(db,current.boxId);' in store
assert 'CHECK(parent_box_id IS NULL OR place_id IS NULL)' in store

print("OK: storage gallery + quick add + NFC/QR move contract")

# Stabilizacja skanera magazynu: jawna operacja + QR/NFC/ręcznie + brak ruchu do tego samego celu.
for marker in (
    'Magazyn • szybkie operacje',
    'Przenieś rzecz', 'Wyjmij rzecz', 'Przenieś pudełko',
    'beginStorageScannerOperation("move", "thing")',
    'beginStorageScannerOperation("take_out", "thing")',
    'beginStorageScannerOperation("move", "box")',
    'QR • zeskanuj ', 'NFC • przyłóż tag ', 'Ręcznie • wybierz z listy',
    'handleStorageScannerSourceNfc', 'takeStorageThingOut',
    'StorageStore.sameDestination', 'Już znajduje się tutaj.',
    'STORAGE_LOCATION_NOOP', 'STORAGE_THING_TAKEN_OUT',
):
    assert marker in main, f"Missing stabilized storage scanner flow: {marker}"

# Skróty Rzeczy/Pudełka/Miejsca są dostępne bez wychodzenia ze skanera.
scanner_start = main.index('private void scannerHub()')
scanner_end = main.index('private String scanSpecificKey', scanner_start)
scanner_ui = main[scanner_start:scanner_end]
for marker in ('"Rzeczy"', '"Pudełka"', '"Miejsca"'):
    assert marker in scanner_ui

# Krótkie akcje w całym APK łączą się po dwie, długie pozostają pełnej szerokości.
for marker in (
    'AUTO_ACTION_ROW_TAG', 'AUTO_SMALL_ACTION_ROW_TAG',
    'value.length()<=28', 'label.length()<=28',
):
    assert marker in main, f"Missing compact global action layout: {marker}"

print("OK: storage scanner operations + idempotent move + compact actions")

# Szybkie dodawanie jest sesją wieloetapową: po zdjęciu/NFC/lokalizacji nie wyrzuca użytkownika.
for marker in (
    '⚡ Szybko dodaj pudełko',
    '⚡ Szybko dodaj miejsce',
    'private void quickAddStorageBox()',
    'private void quickAddPlace()',
    'private void showQuickStorageSetup(String kind,long id)',
    'quickStorageSetupDialog',
    'quickStorageSetupStatusText',
    'Każda czynność zapisuje się od razu; kończysz dopiero przyciskiem Gotowe.',
    '📷 Dodaj / zmień zdjęcie',
    'NFC • Przypisz / zmień tag',
    'QR • Pokaż / etykieta',
    '📍 Ustaw położenie • NFC / QR / lista',
    '📍 Ustaw nadrzędne ręcznie',
    'refreshQuickStorageSetupStatus(target.kind,target.id)',
    'refreshQuickStorageSetupStatus(moving.kind,itemId)',
):
    assert marker in main, f"Missing persistent quick setup: {marker}"

quick_start = main.index('private void showQuickStorageSetup(String kind,long id)')
quick_end = main.index('private void beginStorageScannerOperation', quick_start)
quick = main[quick_start:quick_end]
assert 'dialog.dismiss();\n            selectStorageThumbnail' not in quick
assert 'dialog.dismiss();\n            beginNfcAssignment' not in quick
assert 'setNegativeButton("Gotowe",null)' in quick

print("OK: quick add stays open for Thing/Box/Place until explicit Gotowe")

# Ten sam tag pozostający przy telefonie po przypisaniu nie może od razu otworzyć obiektu.
for marker in (
    'suppressedNfcUid',
    'suppressedNfcUntilElapsed',
    'consumeSuppressedNfcRepeat(uid)',
    'NFC_REPEAT_IGNORED',
    'suppressNfcRepeat(uid)',
    'isQuickStorageSetupTarget',
):
    assert marker in main, f"Missing NFC quick-flow debounce: {marker}"

# W szybkim kreatorze sukces NFC/lokalizacji aktualizuje status bez modalnego OK.
assert 'if(!isQuickStorageSetupTarget(target.kind,target.id))' in main
assert 'isQuickStorageSetupTarget(moving.kind,itemId)' in main
assert 'isQuickThingBatchTarget(itemId)' in main
assert 'moved!=null&&!isQuickStorageSetupTarget(moving.kind,itemId)' in main
assert '&&!isQuickThingBatchTarget(itemId)' in main
print("OK: quick setup NFC debounce + non-modal progress")


# Szybkie dodawanie rzeczy: najpierw jeden cel dla serii, potem zdjęcia.
# Każde poprawne zdjęcie tworzy od razu Rzecz N w wybranym pudełku/miejscu.
for marker in (
    'private String quickThingBatchTargetKind;',
    'private Long quickThingBatchTargetId;',
    'private boolean quickThingBatchWaitingForTarget;',
    'private void showQuickThingBatchTargetChooser()',
    'Przyłóż tag NFC pudełka albo miejsca.',
    'private void handleQuickThingBatchTargetNfc(',
    'private void launchQuickThingBatchTargetQr()',
    'private void showQuickThingBatchTargetPicker()',
    'private void quickThingBatchSelectTarget(',
    'private void showQuickThingBatchUnknownTargetNfc(String uid)',
    '＋ Nowe pudełko',
    '＋ Nowe miejsce',
    'Utwórz i zacznij zdjęcia',
    'private void quickThingBatchCommitPhoto(java.io.File originalFile)',
    'String name="Rzecz "+quickThingBatchCounter;',
    'StorageStore.create(',
    'Dodać jeszcze?',
    '📷 Dodaj następną',
    '📍 Zmień pudełko / miejsce',
    '✓ Zakończ serię',
    'STORAGE_QUICK_BATCH_TARGET_SELECTED',
    'STORAGE_QUICK_BATCH_ITEM_CREATED',
):
    assert marker in main, f"Missing target-first quick Thing flow: {marker}"

quick_thing = main[
    main.index('private void quickAddStorageThing()'):
    main.index('private void quickAddStorageBox()')
]
start_batch = quick_thing[
    quick_thing.index('private void startQuickThingBatch()'):
    quick_thing.index('private void showQuickThingBatchTargetChooser()')
]
assert 'showQuickThingBatchTargetChooser();' in start_batch
assert 'takeQuickThingBatchDraftPhoto();' not in start_batch

commit_photo = quick_thing[
    quick_thing.index('private void quickThingBatchCommitPhoto(java.io.File originalFile)'):
    quick_thing.index('private void showQuickThingBatchAfterPhoto')
]
assert 'String name="Rzecz "+quickThingBatchCounter;' in commit_photo
assert 'StorageStore.create' in commit_photo
assert 'StorageOriginals.save(this,id,originalFile);' in commit_photo
assert 'quickThingBatchCounter++;' in commit_photo
assert 'StorageThumbs.key(id)' in commit_photo

# Old per-item sequence must be gone: no name dialog and no NFC after every photo.
assert 'quickThingBatchAskDraftName' not in quick_thing
assert 'quickThingBatchCreateFromDraft' not in quick_thing
assert 'quickThingBatchStartDestinationNfc' not in quick_thing
assert 'Dalej • od razu skanuj NFC' not in quick_thing
assert 'STORAGE_QUICK_BATCH_NFC_AUTO_WAITING' not in quick_thing
print("OK: quick Thing selects target once, then each photo creates Rzecz N")
