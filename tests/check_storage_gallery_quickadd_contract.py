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
assert 'NFC • Przypisz tag rzeczy' in main
assert '📍 Ustaw położenie • NFC / QR / lista' in main
assert 'pendingStorageDestinationItemId' in main
assert 'handleStorageDestinationNfc(current,uid)' in main
assert 'STORAGE_LOCATION_SET_BY_NFC' in main
assert 'STORAGE_LOCATION_SET_BY_QR' in main
assert 'private void showStorageMoveOptions(StorageStore.Item item)' in main
assert 'NFC • Dotknij tagu celu' in main
assert 'QR • Zeskanuj etykietę celu' in main
assert 'private void beginStorageDestinationQr(long itemId)' in main
assert 'Pudełko możesz przenieść tylko do miejsca.' in main
assert 'Rzecz możesz przenieść do pudełka albo miejsca.' in main
assert 'Galeria rzeczy' in main
assert 'Szukaj rzeczy lub miejsca…' in main
assert 'renderStorageGalleryGrid' in main
assert '📍 Gdzie to jest?' in main
assert 'showStorageLocator' in main

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
