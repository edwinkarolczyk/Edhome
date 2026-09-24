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
assert 'DEFAULT_SHORT_MS = 450' in layout
assert 'DEFAULT_DRAG_MS = 1100' in layout
assert 'versionCode 83' in gradle
assert "versionNameSuffix ''" in gradle
assert 'DB_VERSION = 33;' in backup
print("Adaptive home tiles, configurable drag/menu and backup settings: PASS")
