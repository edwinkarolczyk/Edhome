#!/usr/bin/env python3
"""SUPLA Cloud 0.8.0.1 read-only foundation and secret-safety contract."""
from pathlib import Path
import re

root=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
client=(root/"SuplaCloudClient.java").read_text(encoding="utf-8")
secret=(root/"SuplaSecretStore.java").read_text(encoding="utf-8")
cache=(root/"SuplaCacheStore.java").read_text(encoding="utf-8")
backup=(root/"DataBackup.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
beta_manifest=Path("app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")

# Official SUPLA Cloud API pattern used by EDHOME: HTTPS + Bearer PAT + API v3.
for marker in (
    '"/api/channels?include=state"',
    'setRequestMethod("GET")',
    'setRequestProperty("Accept", "application/json")',
    'setRequestProperty("X-Accept-Version", API_VERSION)',
    'setRequestProperty("Authorization", "Bearer " + cleanToken)',
    'static final String API_VERSION = "3"',
    '"https".equalsIgnoreCase(scheme)',
):
    assert marker in client, "Missing SUPLA read-only client contract: "+marker

# Stage 1 must not contain channel-control HTTP verbs.
assert 'setRequestMethod("POST")' not in client
assert 'setRequestMethod("PUT")' not in client
assert 'setRequestMethod("PATCH")' not in client
assert 'setRequestMethod("DELETE")' not in client

# Personal access token is encrypted with a non-exportable Android Keystore key.
for marker in (
    '"AndroidKeyStore"',
    '"AES/GCM/NoPadding"',
    'KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT',
    'setRandomizedEncryptionRequired(true)',
    'GCMParameterSpec(128, iv)',
):
    assert marker in secret, "Missing SUPLA secret-store contract: "+marker

# Token must never enter EDHOME data backup or local SUPLA cache.
assert "token" not in cache.lower()
assert "supla_cloud_token" not in main
assert "supla_cloud_token" not in backup
assert "Authorization" not in cache
assert "Bearer" not in cache

# UI exposes read-only setup and explicitly keeps control disabled.
for marker in (
    'case "supla": supla(); break;',
    'header("SUPLA Cloud")',
    '"Sprawdź i pobierz kanały"',
    'SuplaSecretStore.saveToken',
    'SuplaCloudClient.fetchChannels',
    'SuplaCacheStore.save',
    'Sterowanie i automatyczne podlewanie pozostają wyłączone.',
):
    assert marker in main, "Missing SUPLA UI contract: "+marker

assert 'android.permission.INTERNET' in beta_manifest
assert re.search(r"\bversionCode\s+193\b", gradle)
assert "versionName '0.8.0.1'" in gradle
print("SUPLA Cloud 0.8.0.1 read-only, HTTPS, PAT/Keystore and cache contract: PASS")
