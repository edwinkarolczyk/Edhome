#!/usr/bin/env python3
"""Regression for settings view parent crash and bounded diagnostics clipboard."""
import re
from pathlib import Path

base=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(base/"MainActivity.java").read_text(encoding="utf-8")
log=(base/"DiagnosticLog.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
settings=main.split("private void settings() {",1)[1].split("private void backup() {",1)[0]
card=main.split("private LinearLayout card() {",1)[1].split("private Button button(",1)[0]
assert "body.addView(box, params);" in card, "card() must attach its own view"
assert "LinearLayout gestures = card();" in settings
assert "body.addView(gestures);" not in settings, "Settings attaches same card twice"
assert "gestures.addView(shortHold);" in settings
assert "gestures.addView(dragHold);" in settings
assert "smallButton(gestures" in settings
assert "LinearLayout appearance = card();" in settings
assert "body.addView(appearance);" not in settings
assert 'themeChoice.setSelection(Math.max(0, currentTheme));' in settings
assert 'prefs.edit().putString("theme", selectedTheme).commit()' in settings
assert "int[] chatLimits = {5000, 12000, 20000};" in main
assert "chatSize.setSelection(1);" in main
assert "DiagnosticLog.readForChat(limit)" in main
assert "report.length()" in main and "limit " in main
assert "DiagnosticLog.readFullText().getBytes(StandardCharsets.UTF_8)" in main
assert "MAX_CHAT_CHARS = 20000" in log
assert "Math.max(1000, Math.min(MAX_CHAT_CHARS, requestedChars))" in log
assert "content.length() <= limit" in log and "firstNewline + 1" in log
assert "readInto(previous, out);" in log and "readInto(current, out);" in log
assert "new String(bytes.toByteArray(), StandardCharsets.UTF_8)" in log
assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84 and "versionNameSuffix ''" in gradle
print("Settings single-parent view, clipboard 5k/12k/20k chars, complete .txt retention: PASS")
