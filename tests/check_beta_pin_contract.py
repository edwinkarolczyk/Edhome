#!/usr/bin/env python3
"""Static regression check: Beta must not request a PIN on any app lifecycle path.

This complements Android compilation; actual device behaviour still needs validation.
"""
from pathlib import Path
source = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
assert "beta {" in gradle and "buildConfigField 'boolean', 'DIAGNOSTICS_ENABLED', 'true'" in gradle
assert "stable {" in gradle and "buildConfigField 'boolean', 'DIAGNOSTICS_ENABLED', 'false'" in gradle
checks = (
    'unlocked = BetaUpdater.isBeta();',
    'if (!BetaUpdater.isBeta()) unlocked = false;',
    'if (BetaUpdater.isBeta()) unlocked = true;',
    'if (!BetaUpdater.isBeta() && !prefs.contains("pin_hash")) setupPin();',
    'else if (!BetaUpdater.isBeta() && !unlocked) unlockPin();',
    'unlocked = BetaUpdater.isBeta();\n                            render();',
)
for check in checks:
    assert check in source, "PIN-free Beta contract missing: " + check
assert 'prefs.edit().remove("pin_hash")' not in source, "Never destroy legacy PIN data."
print("Beta PIN-free startup/background/restore contract: PASS; Stable PIN retained.")
