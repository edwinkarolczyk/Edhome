#!/usr/bin/env python3
"""Miejsca: hierarchy is real, old IDs survive, task selector uses full paths."""
from pathlib import Path
main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
checks = (
    "parent_id INTEGER, icon TEXT NOT NULL DEFAULT 'places'",
    "CREATE UNIQUE INDEX places_siblings_idx",
    "INSERT INTO places_v11 (id,name,kind)",
    "SELECT id,name,kind FROM places",
    "return PlaceRules.canMove(parents, movingId, parentId);",
    "if (!canPlaceWithin(id, parentId))",
    "SELECT COUNT(*) FROM places WHERE parent_id=?",
    "placeNames.add(db.placePath(places.getLong(0)));",
    "return placeId == null ? \"\" : placePath(placeId);",
    "placeEditor(null, \"\", \"\", entry.id, \"places\")",
    'setTitle(id == null ? "Nowe miejsce" : "Edytuj / przenieś miejsce")',
    "Nie można przenieść miejsca",
)
for text in checks:
    assert text in main, "Missing place contract: " + text

# Miejsca są trwałe dla QR/NFC i synchronizacji. UI nie może ich usuwać.
branch=main.split("private void renderPlaceBranch(",1)[1].split(
    "private void placeEditor(",1)[0]
assert '"Usuń"' not in branch
blocked=main.split("private void confirmDeletePlace(",1)[1].split(
    "private void placeEditor(",1)[0]
assert 'Miejsc nie usuwamy.' in blocked
assert 'db.deletePlace(' not in blocked
assert 'PLACE_DELETE_BLOCKED' in blocked
# Niskopoziomowy rollback pozostaje dostępny tylko dla niedokończonego kreatora.
assert "boolean deletePlace(long id)" in main
assert "PLACE_TYPES" not in main, "Fixed place-kind list must not return"
assert 'inputVersion < 11 && "places".equals(definition[0])' in backup
assert '"parent_id", "icon"' in backup
assert 'PlaceRules.validForest(hierarchy)' in backup
assert 'if (!placeNames.add(siblingKey))' in backup
assert 'database.beginTransaction();' in backup
print("Hierarchical places and backward-compatible backup: PASS")
