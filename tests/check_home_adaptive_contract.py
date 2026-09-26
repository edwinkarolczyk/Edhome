#!/usr/bin/env python3
"""Adaptive EDHOME tile layout and threshold settings are preserved across backup."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
backup = (root / "DataBackup.java").read_text(encoding="utf-8")
layout = (root / "HomeTileLayout.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")

for token in (
    'int columns = HomeTileLayout.columns(Math.round(viewport / density));',
    'used + span > columns',
    'int contentWidth = Math.max(dp(1), viewport - dp(32));',
    '(contentWidth - dp(9) * (columns - 1)) / columns',
    'tile.setOnTouchListener(new View.OnTouchListener()',
    'tile.postDelayed(startDrag, prefs.getInt(',
    'tile.removeCallbacks(startDrag);',
    'HomeTileLayout.openMenuOnRelease(',
    'else tile.performClick();',
    'showTileActions(tile, tileId);',
    'beginHomeDrag(tile, tileId);',
    'HOME_TILE_GESTURE_TIMING_SAVED',
    'HomeTileLayout.validPair(menuMs, moveMs)',
    'prefs.edit().putInt(HomeTileLayout.SHORT_KEY, menuMs)',
    '.putInt(HomeTileLayout.DRAG_KEY, moveMs).commit()',
    'Kafelki • czas przytrzymania',
):
    assert token in main, token

assert 'HomeTileCatalog.moved(homeDragOrder, homeDragSource, slot)' in main
assert 'HomeTileOrder.moved(' not in main
assert 'HOME_TILE_DRAG_STALE' in main
assert 'HOME_TILE_DRAG_INVALID' in main
assert 'used + span > 3' not in main
assert 'Math.min(widthSide, heightSide)' not in main
for token in (
    'homeTileShortHoldMs',
    'homeTileDragHoldMs',
    'HomeTileLayout.validPair(shortHoldMs, dragHoldMs)',
    '.putInt(HomeTileLayout.SHORT_KEY, shortHoldMs)',
    '.putInt(HomeTileLayout.DRAG_KEY, dragHoldMs)',
):
    assert token in backup, token
# Big native/AI 3D icons are sized from actual tile geometry, not a 46dp
# thumbnail, and the optional ZIP picker never gates ordinary APK updates.
assert 'int tileHeight = isHome ? side + dp(76) : side;' in main
assert 'int iconWidth = side * span + dp(9) * (span - 1) - dp(10);' in main
assert 'int iconHeight = tileHeight - dp(19) - dp(31) - dp(14);' in main
assert 'Math.min(iconWidth, iconHeight)' in main
assert 'trim3dTransparentMargins(image)' in main
assert 'Opcjonalnie: importuj paczkę ikon ZIP' in main
assert '.putBoolean("icon_style_explicit", true).commit()' in main
assert 'DEFAULT_SHORT_MS = 450' in layout
assert 'DEFAULT_DRAG_MS = 1100' in layout
assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84
assert "versionNameSuffix ''" in gradle
assert 'DB_VERSION = 35;' in backup
print("Adaptive home tiles, configurable drag/menu and backup settings: PASS")
