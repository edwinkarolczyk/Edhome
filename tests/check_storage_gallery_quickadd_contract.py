from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(
    encoding="utf-8"
)
store = Path("app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text(
    encoding="utf-8"
)
gradle = Path("app/build.gradle").read_text(encoding="utf-8")

assert 'versionName \'0.7.4.2\'' in gradle
assert '⚡ Szybko dodaj rzecz' in main
assert 'private void quickAddStorageThing()' in main
assert 'NFC • Przypisz tag rzeczy' in main
assert 'NFC • Wskaż pudełko lub miejsce' in main
assert 'pendingStorageDestinationThingId' in main
assert 'handleStorageDestinationNfc(current,uid)' in main
assert 'STORAGE_LOCATION_SET_BY_NFC' in main
assert 'Galeria rzeczy' in main
assert 'Szukaj rzeczy lub miejsca…' in main
assert 'renderStorageGalleryGrid' in main
assert '📍 Gdzie to jest?' in main
assert 'showStorageLocator' in main

# The quick NFC flow must rely on the existing inherited-location model:
# a thing points at a box, and StorageStore.location walks the box chain until place_id.
assert 'if(current.placeId!=null)' in store
assert 'current=find(db,current.boxId);' in store
assert 'CHECK(parent_box_id IS NULL OR place_id IS NULL)' in store

print("OK: storage gallery + quick add + NFC location contract")
