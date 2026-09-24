#!/usr/bin/env python3
"""0.5 stabilization: verify A-B-A consent, single-use and no silent rearming."""
from pathlib import Path
base=Path("app/src/main/java/com/edwinkarolczyk/edhome")
camera=(base/"PantryTakeCaptureActivity.java").read_text(encoding="utf-8")
state=(base/"PantryTakeCountdown.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
assert 'if (barcode.equals(lastCommitted)) {' in state
assert 'pending = null;' in state and 'awaitingApproval = barcode;' in state
assert 'boolean needsApproval()' in state
assert 'void markCommitted(String barcode)' in state
assert 'countdown.needsApproval()' in camera
assert 'PANTRY_TAKE_REPEAT_REQUIRES_CONFIRMATION' in camera
assert 'countdown.markCommitted(scannedBarcode);' in camera
assert camera.index('PantryBarcodeStore.commit(db, barcode, null, "TAKE",') < camera.index(
    'countdown.markCommitted(scannedBarcode);')
assert 'if (another != null) another.setEnabled(false);' in camera
assert "versionCode 84" in gradle
assert "versionNameSuffix ''" in gradle
print("A-B-A consent and cancellation after unsuccessful scan: PASS")
