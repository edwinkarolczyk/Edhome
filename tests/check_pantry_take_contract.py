#!/usr/bin/env python3
"""Scanner BETA.11 contract. Java smoke verifies countdown state; device camera needs device QA."""
from pathlib import Path
import xml.etree.ElementTree as ET

src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
camera=(src/"PantryTakeCaptureActivity.java").read_text(encoding="utf-8")
rules=(src/"PantryTakeCountdown.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text()
manifest=Path("app/src/main/AndroidManifest.xml").read_text()
root=ET.fromstring(manifest)
android="{http://schemas.android.com/apk/res/android}"
assert any(el.attrib.get(android+"name")=="android.permission.CAMERA"
    for el in root.findall("uses-permission"))
assert any(el.attrib.get(android+"name")==".PantryTakeCaptureActivity"
    and el.attrib.get(android+"exported")=="false"
    for el in root.findall("./application/activity"))
for token in (
    "camera.decodeContinuous(new BarcodeCallback()",
    "BarcodeFormat.EAN_8", "BarcodeFormat.UPC_A", "BarcodeFormat.EAN_13",
    "BarcodeFormat.ITF", "BarcodeFormat.CODE_128",
    "countdown.observe(barcode, SystemClock.elapsedRealtime())",
    "PANTRY_TAKE_REPLACED", "countdown.due(SystemClock.elapsedRealtime())",
    "handler.removeCallbacks(tick);",
    "countdown.consume(); // single use before touching SQLite",
    'PantryBarcodeStore.commit(db, barcode, null, "TAKE",',
    'if (!"COMMITTED".equals(result))',
    "cancelPending(\"PANTRY_TAKE_BACKGROUND_CANCELLED\")",
    "getDatabasePath(\"edhome-beta-preview.db\")",
    "PANTRY_TAKE_SCANNER_FINISHED", "requestPermissions(",
    "CAMERA_PERMISSION", "PantryPackageStore.find(db, item.id)",
    'setResult(RESULT_OK, new Intent().putExtra(EXTRA_COMMITTED, committed))',
    "To kolejne opakowanie tego samego kodu", "Anuluj bieżące wyjęcie",
    'if (!batch) finishScanner();',
):
    assert token in camera, "Missing continuous camera guard: "+token
assert camera.index("countdown.consume();") < camera.index(
    'PantryBarcodeStore.commit(db, barcode, null, "TAKE",')
assert camera.index("resumed = false;") < camera.index(
    'cancelPending("PANTRY_TAKE_BACKGROUND_CANCELLED")')
assert "DEFAULT_SECONDS = 5;" in rules and "DELAY_OPTIONS = {3, 5, 8, 10}" in rules
assert "private void launchTakeScanner(boolean series)" in main
assert "launchTakeScanner(false);" in main and "launchTakeScanner(true);" in main
assert '"TAKE".equals(mode)' in main and "TAKE_SCANNER_RESULT" in main
assert "PantryTakeCountdown.DELAY_PREF" in main
assert "pantryTakeDelaySeconds" in backup and "validSeconds(takeDelaySeconds)" in backup
assert '.putInt(PantryTakeCountdown.DELAY_PREF, takeDelaySeconds)' in backup
assert "versionCode 70" in gradle and "versionNameSuffix ''" in gradle
print("Continuous camera wiring, interruption, lifecycle cancellation, backup/version: PASS")
