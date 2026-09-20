#!/usr/bin/env python3
"""Opt-in binary-only EDHOME Beta publishing to a separate PUBLIC GitHub repo.

Private source repo stays private. Nothing is published unless the owner creates
a separate public distribution repo and configures BOTH a variable and secret.
Upload signed APK first, then update the tiny public manifest as the last step.
A tag is immutable: re-run requires a NEW versionCode/versionName.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

repo = os.environ.get("EDHOME_PUBLIC_CHANNEL_REPO", "").strip()
token = os.environ.get("GH_TOKEN", "").strip()
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
    raise SystemExit("EDHOME_PUBLIC_CHANNEL_REPO must be owner/public-repo")
if not token:
    raise SystemExit("EDHOME_PUBLIC_CHANNEL_TOKEN is missing; refusing publication.")

apk = Path("app/build/outputs/apk/beta/release/app-beta-release.apk")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
if not apk.is_file() or apk.stat().st_size < 1000:
    raise SystemExit("Signed APK is missing or too small.")
name = re.search(r"\bversionName\s+'([^']+)'", gradle).group(1)
suffix = re.search(r"beta\s*\{[\s\S]*?versionNameSuffix\s+'([^']+)'", gradle).group(1)
code = int(re.search(r"\bversionCode\s+(\d+)", gradle).group(1))
version = name + suffix
tag = "beta-v" + version
asset_name = "edhome-beta.apk"
asset_url = ("https://github.com/" + repo + "/releases/download/"
             + urllib.parse.quote(tag) + "/" + asset_name)
digest = hashlib.file_digest(apk.open("rb"), "sha256").hexdigest()

headers = {
    "Authorization": "Bearer " + token,
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "EDHOME-Beta-CI",
}

def api(method, url, payload=None, content_type=None):
    data = payload
    request_headers = dict(headers)
    if content_type:
        request_headers["Content-Type"] = content_type
    elif isinstance(payload, (dict, list)):
        data = json.dumps(payload).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method,
                                 headers=request_headers)
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.read(), response.status

base = "https://api.github.com/repos/" + repo
metadata, _ = api("GET", base)
if json.loads(metadata).get("private") is not False:
    raise SystemExit("Distribution repository is not PUBLIC; refusing to publish.")
try:
    api("GET", base + "/releases/tags/" + urllib.parse.quote(tag))
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
else:
    raise SystemExit("Version tag already exists; bump versionCode/versionName first.")

body, _ = api("POST", base + "/releases", {
    "tag_name": tag,
    "target_commitish": "main",
    "name": "EDHOME " + version,
    "body": "Binary-only EDHOME Beta distribution. Source repository remains private.",
    "draft": False,
    "prerelease": True,
})
release = json.loads(body)
upload_url = release["upload_url"].split("{", 1)[0]
upload_url += "?name=" + urllib.parse.quote(asset_name)
api("POST", upload_url, apk.read_bytes(),
    "application/vnd.android.package-archive")
manifest = {
    "channel": "beta",
    "versionCode": code,
    "versionName": version,
    "changelog": os.environ.get("EDHOME_BETA_NOTES",
                                 "Usprawnienia EDHOME Beta DEV."),
    "apkUrl": asset_url,
    "sha256": digest,
}
manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2)
                  + "\n").encode("utf-8")
# Only advance 'latest' AFTER the new release asset is available.
latest_url = base + "/contents/beta-manifest.json"
try:
    existing, _ = api("GET", latest_url)
    existing_sha = json.loads(existing)["sha"]
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    existing_sha = None
request = {
    "message": "release(beta): " + version,
    "content": base64.b64encode(manifest_bytes).decode("ascii"),
    "branch": "main",
}
if existing_sha:
    request["sha"] = existing_sha
api("PUT", latest_url, request)
Path("beta-manifest.json").write_bytes(manifest_bytes)
print("EDHOME public channel updated:", repo, version, "versionCode=", code)
print("Feed (public): https://raw.githubusercontent.com/"
      + repo + "/main/beta-manifest.json")
