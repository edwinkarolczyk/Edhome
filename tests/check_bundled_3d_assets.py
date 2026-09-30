#!/usr/bin/env python3
"""Fail closed: never publish a 3D-promising APK missing its 100 real image assets."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

archive = Path("app/src/main/assets/EDHOME_AI_3D_100.zip")
assert archive.is_file(), (
    "EDHOME AI 3D: missing bundled 100-icon ZIP; do not publish an import-only APK."
)
assert 100_000 < archive.stat().st_size < 12_000_000, "Unexpected 3D assets size"
with zipfile.ZipFile(archive) as bundle:
    assert bundle.testzip() is None, "Corrupt 3D asset"
    names = bundle.namelist()
    assert len(names) == 101 and len(names) == len(set(names))
    manifest = json.loads(bundle.read("manifest.json"))
    assert manifest["pack"] == "EDHOME AI 3D"
    assert manifest["schema"] == 1 and manifest["iconCount"] == 100
    assert len(manifest["icons"]) == 100
    ids = set()
    for icon in manifest["icons"]:
        ident = icon["id"]
        assert re.fullmatch(r"[a-z][a-z0-9_]{0,39}", ident) and ident not in ids
        ids.add(ident)
        path = "icons/" + ident + ".webp"
        assert icon["file"] == path and path in names
        data = bundle.read(path)
        assert 500 < len(data) < 512 * 1024 and data[:4] == b"RIFF"
        assert data[8:12] == b"WEBP"
        assert hashlib.sha256(data).hexdigest() == icon["sha256"]
    assert len(ids) == 100
print("EDHOME AI 3D 100 actual image assets bundled in APK: PASS")
