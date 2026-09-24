#!/usr/bin/env python3
"""Vehicle document register is local, idempotent and included in backup."""
from pathlib import Path
src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
store=(src/"VehicleDocumentStore.java").read_text(encoding="utf-8")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
for word in ("CREATE TABLE vehicle_documents","operation_id TEXT NOT NULL UNIQUE",
             "vehicle_id INTEGER NOT NULL","valid_until TEXT NOT NULL DEFAULT",
             "DUPLICATE_IGNORED"):
    assert word in store, word
for word in ("VehicleDocumentStore.create(database)",
             "DATABASE_MIGRATED_32_TO_33_VEHICLE_DOCUMENTS",
             "+ Dodaj dokument","editVehicleDocument("):
    assert word in main, word
assert '"vehicle_documents", "id", "operation_id", "vehicle_id"' in backup
assert 'inputVersion < 33 && "vehicle_documents".equals(definition[0])' in backup
assert "PaycheckStore" not in store
print("Vehicle documents: local register, backup and idempotency contract PASS")
