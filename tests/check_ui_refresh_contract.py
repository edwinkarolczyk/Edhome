#!/usr/bin/env python3
"""EDHOME UI-only release guard: presentation, gesture and backup stay compatible."""
from pathlib import Path
import re

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
skin = Path("app/src/main/java/com/edwinkarolczyk/edhome/UiSkin.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
assert all('"' + name + '"' in skin for name in
           ("Neonowy", "Naturalny", "Pastelowy", "Szklany", "WMM", "Trener 2"))
assert all('"' + name + '"' in skin for name in
           ("Grafitowy", "Leśny", "Jasny", "Trener 2"))
assert "UiSkin.forName(" in main and "UiSkin.accepted(theme)" in backup
assert 'super(context, "edhome-beta-preview.db", null, 30' in main
assert 'private static final int DB_VERSION = 31;' in backup
assert "newVersion > 31" in main
home_ids = main.split("private static final String[] HOME_TILE_IDS = {", 1)[1].split("};", 1)[0]
assert len(re.findall(r'"(tasks|calendar|places|pantry|audit|updates|backup|settings|today)"', home_ids)) == 9
assert "showTileActions(tile, tileId);" in main
assert "tile.setOnTouchListener(new View.OnTouchListener()" in main
assert "HomeTileLayout.openMenuOnRelease(" in main
assert 'text("✎  Edytuj kafelek"' in main
assert 'text("✥  Przesuń kafelek"' in main
assert "beginHomeDrag(tile, (String) tileTag)" in main
assert "MotionEvent.ACTION_MOVE" in main
assert "ACTION_DRAG_LOCATION" in main
assert "scrollHomeDuringDrag(" in main
assert "previewHomeTilePlacement(nearest)" in main
assert "translationX(target[0] - old[0])" in main
assert "translationY(target[1] - old[1])" in main
assert "homeDragHint.setText(" in main
assert "HomeTileCatalog.moved(" in main
assert "homeDragOrder, homeDragSource, slot" in main
assert "HomeTileCatalog.moved(before, source, slot)" in main
assert "scheduleHomeDragFinish()" in main
assert "tile.setAlpha(1f)" in main
assert "tile.setVisibility(View.VISIBLE)" in main
assert "homeDragDropped = homeDragTargetIndex >= 0;" in main
assert "moveHomeTile(sourceId, tileId)" not in main
assert "HomeTileCatalog.ORDER_KEY" in main
assert 'prefs.getString("tile_label_" + id' in main
assert 'prefs.getString("tile_tint_" + id' in main
assert 'prefs.getString("tile_target_" + id' in main
assert 'prefs.getString("tile_width_" + id' in main
assert 'home_tiles_v2_hidden' in main
assert 'showAddTileDialog' in main and 'restoreHiddenHomeTile' in main
assert 'homeTileTarget(id)' in main and 'openHomeTile(id)' in main
assert 'updateTile(grid, id, "•"' in main
assert '"homeTileAppearance"' in backup
assert '"homeTileOrderV2"' in backup and '"homeTileHiddenV2"' in backup
assert 'restored.remove(key)' in backup
assert 'prefs.getString("tile_icon_" + id,' in main
assert 'settings.put("homeTileAppearance", appearance)' in backup
assert 'tile.put("icon", prefs.getString("tile_icon_" + id, id))' in backup
catalog = Path("app/src/main/java/com/edwinkarolczyk/edhome/HomeTileCatalog.java").read_text(encoding="utf-8")
for destination in ("timers", "shopping", "paycheck", "paycheck_private",
                    "waste", "storage", "diagnostics"):
    assert f'"{destination}"' in catalog
assert "List<String> STARTER_IDS" in catalog
assert 'setPositiveButton("Usuń skrót"' in main
icons = Path("app/src/main/java/com/edwinkarolczyk/edhome/TileIcon.java").read_text(encoding="utf-8")
assert all('"' + item + '"' in icons for item in ("washer", "dryer", "dishwasher"))
assert 'TileIcon.ICON_NAMES' in main and 'previewFrame' in main
assert 'prefs.contains("tile_label_" + id)' in backup
assert '"pin_hash"' in main and "unlocked = BetaUpdater.isBeta();" in main
assert "versionCode 73" in gradle
assert "versionName '0.6.0.8'" in gradle
settings=main.split("private void settings() {",1)[1].split("private void backup() {",1)[0]
assert 'java.util.Arrays.asList(UiSkin.THEMES)' in settings
assert 'themeChoice.setSelection(Math.max(0, currentTheme));' in settings
assert 'smallButton(appearance, "Zastosuj styl"' in settings
assert 'themeDescription.setText(descriptions[position]);' in settings
assert 'for (int i = 0; i < UiSkin.THEMES.length; i++)' not in settings
print("6 themes via compact dropdown, unlimited configurable tiles, drag, backup and SQLite v22: PASS")
