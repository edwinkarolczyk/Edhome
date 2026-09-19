#!/usr/bin/env python3
"""Generate an optional EDHOME beta manifest for a separately hosted APK.

This script never uploads artifacts. A trusted public HTTPS URL must be provided
by the repository owner; private GitHub Actions artifacts are not a feed.
"""
import hashlib
import json
import os
import pathlib
import re
from urllib.parse import urlparse

apk = pathlib.Path("app/build/outputs/apk/beta/release/app-beta-release.apk")
gradle = pathlib.Path("app/build.gradle").read_text(encoding="utf-8")
url = os.environ.get("EDHOME_BETA_APK_URL", "").strip()
if not url:
    print("No EDHOME_BETA_APK_URL: no remote update manifest generated.")
    raise SystemExit(0)
parsed = urlparse(url)
if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
    raise SystemExit("EDHOME_BETA_APK_URL must be HTTPS without credentials, query or fragment")
code = int(re.search(r"\bversionCode\s+(\d+)", gradle).group(1))
version = re.search(r"\bversionName\s+'([^']+)'", gradle).group(1)
beta = re.search(r"beta\s*\{[\s\S]*?versionNameSuffix\s+'([^']+)'", gradle).group(1)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
manifest = {
    "channel": "beta",
    "versionCode": code,
    "versionName": version + beta,
    "changelog": os.environ.get("EDHOME_BETA_NOTES", "Aktualizacja EDHOME Beta DEV."),
    "apkUrl": url,
    "sha256": digest,
}
path = pathlib.Path("beta-manifest.json")
path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Generated manifest for", manifest["versionName"], "SHA-256", digest)
print("Publish APK at the configured URL and manifest on trusted HTTPS before enabling in-app update checks.")
