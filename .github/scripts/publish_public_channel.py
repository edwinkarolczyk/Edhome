#!/usr/bin/env python3
"""Publish signed EDHOME Beta like Trener 2/WMM: public GitHub Releases.

The source repo is already public. This script must only run for the trusted
beta branch with the built-in GitHub Actions GITHUB_TOKEN. It never publishes a
signing key, authentication token, or user data. Upload APK before advancing
the beta branch manifest; existing release tags remain immutable.
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

repo = "edwinkarolczyk/Edhome"
branch = "beta"
token = os.environ.get("GH_TOKEN", "").strip()
if os.environ.get("GITHUB_REPOSITORY") != repo:
    raise SystemExit("Refusing publication outside the EDHOME repository.")
if os.environ.get("GITHUB_REF") != "refs/heads/beta":
    raise SystemExit("Refusing publication outside the beta branch.")
if not token:
    raise SystemExit("Built-in GitHub Actions token is missing.")

apk = Path("app/build/outputs/apk/beta/release/app-beta-release.apk")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
if not apk.is_file() or apk.stat().st_size < 1000:
    raise SystemExit("Signed APK is missing or too small.")
name = re.search(r"\bversionName\s+'([^']+)'", gradle).group(1)
suffix_match = re.search(r"beta\s*\{[\s\S]*?versionNameSuffix\s+'([^']*)'", gradle)
suffix = suffix_match.group(1) if suffix_match else ""
code = int(re.search(r"\bversionCode\s+(\d+)", gradle).group(1))
version = name + suffix
tag = "beta-v" + version
asset_name = "edhome-beta.apk"
asset_url = ("https://github.com/" + repo + "/releases/download/"
             + urllib.parse.quote(tag) + "/" + asset_name)
with apk.open("rb") as source:
    digest = hashlib.file_digest(source, "sha256").hexdigest()

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
    "target_commitish": branch,
    "name": "EDHOME " + version,
    "body": "Signed EDHOME Beta APK. Install over your existing Beta; keep app data. Android must confirm installation.",
    "draft": False,
    "prerelease": True,
})
release = json.loads(body)
upload_url = release["upload_url"].split("{", 1)[0]
upload_url += "?name=" + urllib.parse.quote(asset_name)
api("POST", upload_url, apk.read_bytes(),
    "application/vnd.android.package-archive")
# Check the release really exposes its APK before advertising it to installed phones.
asset_response, _ = api("GET", base + "/releases/tags/"
                        + urllib.parse.quote(tag))
assets = json.loads(asset_response).get("assets", [])
if not any(item.get("name") == asset_name and item.get("size", 0) > 1000
           for item in assets):
    raise SystemExit("Release APK is not visible; refusing manifest advancement.")
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
latest_url = base + "/contents/beta-manifest.json?ref=" + branch
try:
    existing, _ = api("GET", latest_url)
    existing_info = json.loads(existing)
    existing_sha = existing_info["sha"]
    previous = json.loads(base64.b64decode(existing_info["content"]))
    if int(previous.get("versionCode", 0)) >= code:
        raise SystemExit("Refusing to replace a newer or equal Beta manifest.")
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    existing_sha = None
request = {
    "message": "release(beta): " + version,
    "content": base64.b64encode(manifest_bytes).decode("ascii"),
    "branch": branch,
}
if existing_sha:
    request["sha"] = existing_sha
api("PUT", latest_url, request)
Path("beta-manifest.json").write_bytes(manifest_bytes)
print("EDHOME public channel updated:", repo, version, "versionCode=", code)
print("Feed (public): https://raw.githubusercontent.com/"
      + repo + "/" + branch + "/beta-manifest.json")
