#!/usr/bin/env python3
"""EDHOME UI-only release guard: presentation, gesture and backup stay compatible."""
from pathlib import Path
import re

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
skin = Path("app/src/main/java/com/edwinkarolczyk/edhome/UiSkin.java").read_text(encoding="utf-8")
backup = Path("app/src/main/java/com/edwinkarolczyk/edhome/DataBackup.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
assert all('"' + name + '"' in skin for name in
           ("Neonowy", "Naturalny", "Pastelowy", "Szklany"))
assert all('"' + name + '"' in skin for name in
           ("Grafitowy", "Leśny", "Jasny", "Trener 2"))
assert "UiSkin.forName(" in main and "UiSkin.accepted(theme)" in backup
assert 'super(context, "edhome-beta-preview.db", null, 10)' in main
assert 'private static final int DB_VERSION = 10;' in backup
assert "newVersion > 10" in main
home_ids = main.split("private static final String[] HOME_TILE_IDS = {", 1)[1].split("};", 1)[0]
assert len(re.findall(r'"(tasks|calendar|places|pantry|audit|updates|backup|settings|today)"', home_ids)) == 9
assert "showTileActions(tile, tileId);" in main
assert 'text("✎  Edytuj kafelek"' in main
assert 'text("✥  Przesuń kafelek"' in main
assert "beginHomeDrag(tile, (String) id)" in main
assert "MotionEvent.ACTION_MOVE" in main
assert "ACTION_DRAG_LOCATION" in main
assert "scrollHomeDuringDrag(" in main
assert 'putString("home_tile_order"' in main
assert 'prefs.getString("tile_label_" + id' in main
assert 'prefs.getString("tile_tint_" + id' in main
assert '"homeTileAppearance"' in backup
assert 'restored.remove("tile_label_" + id).remove("tile_tint_" + id)' in backup
assert 'prefs.contains("tile_label_" + id)' in backup
assert '"pin_hash"' in main and "unlocked = BetaUpdater.isBeta();" in main
assert "versionCode 24" in gradle
assert "versionName '0.3.3'" in gradle
print("4 themes, 9 tiles, long-press/edit/drag, visual backup, DB v10 and Beta PIN-free: PASS")
