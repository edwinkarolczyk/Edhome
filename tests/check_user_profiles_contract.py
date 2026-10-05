#!/usr/bin/env python3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
store=(root/"app/src/main/java/com/edwinkarolczyk/edhome/UserProfileStore.java").read_text(encoding="utf-8")
backup=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")

assert "CREATE TABLE IF NOT EXISTS user_profiles" in store
assert "CHECK(id=member_id)" in store
assert "ROLE_ADMIN" in store and "ROLE_MEMBER" in store
assert "ensureAdministrator" in store
assert "Najpierw ustaw innego użytkownika jako Administratora." in store
assert "INSERT OR IGNORE INTO user_profiles" in store
assert "DATABASE_MIGRATED_41_TO_42_USER_PROFILES" in main
assert 'case "member_profile": memberProfile(); break;' in main
assert "showAddUserDialog" in main
assert "Użytkownicy • profile domowników" in main
assert "USER_PROFILE_ADDED" in main and "USER_PROFILE_UPDATED" in main
assert "MEMBER_PIN_HASH_PREFIX" in main
assert "PBKDF2WithHmacSHA256" in main
assert 'DB_VERSION = 45' in backup
assert '{"user_profiles"' in backup
assert "MEMBER_PIN_HASH_PREFIX" not in backup
assert "member_pin_hash_" not in backup
print("user profiles v42 contract OK")