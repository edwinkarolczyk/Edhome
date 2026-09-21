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
assert 'super(context, "edhome-beta-preview.db", null, 17' in main
assert 'private static final int DB_VERSION = 17;' in backup
assert "newVersion > 17" in main
home_ids = main.split("private static final String[] HOME_TILE_IDS = {", 1)[1].split("};", 1)[0]
assert len(re.findall(r'"(tasks|calendar|places|pantry|audit|updates|backup|settings|today)"', home_ids)) == 9
assert "showTileActions(tile, tileId);" in main
assert 'text("✎  Edytuj kafelek"' in main
assert 'text("✥  Przesuń kafelek"' in main
assert "beginHomeDrag(tile, (String) id)" in main
assert "MotionEvent.ACTION_MOVE" in main
assert "ACTION_DRAG_LOCATION" in main
assert "scrollHomeDuringDrag(" in main
assert "previewHomeTilePlacement(nearest)" in main
assert "translationX(target[0] - old[0])" in main
assert "translationY(target[1] - old[1])" in main
assert "homeDragHint.setText(" in main
assert "HomeTileOrder.moved(" in main and "homeDragOrder, homeDragSource, slot" in main
assert "HomeTileOrder.moved(before, source, slot)" in main
assert "scheduleHomeDragFinish()" in main
assert "tile.setAlpha(1f)" in main
assert "tile.setVisibility(View.VISIBLE)" in main
assert "homeDragDropped = homeDragTargetIndex >= 0;" in main
assert "moveHomeTile(sourceId, tileId)" not in main
assert 'putString("home_tile_order"' in main
assert 'prefs.getString("tile_label_" + id' in main
assert 'prefs.getString("tile_tint_" + id' in main
assert '"homeTileAppearance"' in backup
assert 'restored.remove("tile_label_" + id)' in backup
assert '.remove("tile_tint_" + id).remove("tile_icon_" + id)' in backup
assert 'prefs.getString("tile_icon_" + moduleId, moduleId)' in main
assert 'settings.put("homeTileAppearance", appearance)' in backup
assert 'tile.put("icon", prefs.getString("tile_icon_" + id, id))' in backup
icons = Path("app/src/main/java/com/edwinkarolczyk/edhome/TileIcon.java").read_text(encoding="utf-8")
assert all('"' + item + '"' in icons for item in ("washer", "dryer", "dishwasher"))
assert 'TileIcon.ICON_NAMES' in main and 'previewFrame' in main
assert 'prefs.contains("tile_label_" + id)' in backup
assert '"pin_hash"' in main and "unlocked = BetaUpdater.isBeta();" in main
assert "versionCode 39" in gradle
assert "versionName '0.4.0'" in gradle
print("6 themes, 9 tiles, exact preview/drop, restored visibility, icon library and DB v17: PASS")
