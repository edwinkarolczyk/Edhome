#!/usr/bin/env python3
"""Adaptive EDHOME tile layout and threshold settings are preserved across backup."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
backup = (root / "DataBackup.java").read_text(encoding="utf-8")
layout = (root / "HomeTileLayout.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")

for token in (
    'HomeTileLayout.homeColumns(Math.round(viewport / density))',
    'HomeTileLayout.columns(Math.round(viewport / density))',
    'used + span > columns',
    'int contentWidth = Math.max(dp(1), viewport - dp(32));',
    '(contentWidth - dp(tileGapDp) * (columns - 1)) / columns',
    'tile.setOnTouchListener(new View.OnTouchListener()',
    'tile.postDelayed(startDrag, prefs.getInt(',
    'tile.removeCallbacks(startDrag);',
    'HomeTileLayout.openMenuOnRelease(',
    'else tile.performClick();',
    'showTileActions(tile, tileId);',
    'beginHomeDrag(tile, tileId);',
    'HOME_TILE_GESTURE_TIMING_SAVED',
    'HomeTileLayout.validPair(menuMs,moveMs)',
    'prefs.edit().putInt(HomeTileLayout.SHORT_KEY,menuMs)',
    '.putInt(HomeTileLayout.DRAG_KEY,moveMs).apply()',
    'Czas przytrzymania kafelków',
    'Miejsca na jednej stronie Start',
    'HomeTileLayout.PAGE_SLOTS_KEY',
    'HomeTileLayout.PAGE_SLOT_OPTIONS',
    'homePageCapacity()',
    'used + span > capacity',
    'previewHomeDragPageEdge(-1)',
    'previewHomeDragPageEdge(1)',
    'HOME_TILE_MOVED_BETWEEN_PAGES',
    'homePageInsertionSlot(',
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
    'homeTilePageSlots',
    '.putInt(HomeTileLayout.PAGE_SLOTS_KEY, pageSlots)',
):
    assert token in backup, token
# Big native/AI 3D icons are sized from actual tile geometry, not a 46dp
# thumbnail, and the optional ZIP picker never gates ordinary APK updates.
assert 'HomeTileLayout.HOME_EXTRA_HEIGHT_DP' in main
assert 'HOME_MIN_TILE_DP = 73' in layout
assert 'HOME_GAP_DP = 6' in layout
assert 'HOME_EXTRA_HEIGHT_DP = 53' in layout
assert 'side * span + dp(tileGapDp) * (span - 1)' in main
assert 'int iconHeight = tileHeight - dp(19) - dp(31) - dp(14);' in main
assert 'Math.min(iconWidth, iconHeight)' in main
assert 'trim3dTransparentMargins(image)' in main
assert 'Opcjonalnie: importuj paczkę ikon ZIP' in main
assert '.putBoolean("icon_style_explicit",true).apply()' in main
# Optional complete home interfaces: the classic UI remains available.
for token in (
    'HOME_INTERFACE_PREF = "home_interface_mode"',
    'HOME_INTERFACE_CONCEPT5 = "concept5"',
    'HOME_INTERFACE_CONCEPT8 = "concept8"',
    '"Obecny interfejs", "Koncepcja 5", "Koncepcja 8"',
    'homeConcept5();',
    'homeConcept8();',
    'addConceptBottomNavigation();',
    'conceptActionRow();',
    'conceptModuleList(true);',
):
    assert token in main, token
for token in (
    'settings.put("homeInterface"',
    'String homeInterface=settings.optString("homeInterface","current")',
    '.putString("home_interface_mode", homeInterface)',
):
    assert token in backup, token
assert 'DEFAULT_SHORT_MS = 450' in layout
assert 'DEFAULT_DRAG_MS = 1100' in layout
assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84
assert "versionNameSuffix ''" in gradle
assert 'DB_VERSION = 39;' in backup
print("Adaptive home tiles, configurable drag/menu and backup settings: PASS")


# Reordering is impossible during ordinary Start-page swipes.
assert 'if (!homeEditMode) return false;' in main
assert 'Zwykłe przewijanie nigdy nie może rozpocząć przenoszenia kafelka.' in main
print("Home swipe cannot move tiles outside explicit layout mode: PASS")

# Pełnoekranowe tło kafelka: tylko dedykowana grafika PNG/WebP.
for token in (
    'private boolean homeFullTileArtEnabled()',
    'prefs.getBoolean("home_tile_full_art", true)',
    'private Bitmap homeFullTileBitmap(String iconId)',
    'private Drawable homeFullTileBackground(Drawable base, Bitmap image)',
    'bounds.width()/(float)image.getWidth()',
    'bounds.height()/(float)image.getHeight()',
    'canvas.clipPath(clip);',
    'canvas.drawBitmap(image,null,destination,artPaint);',
    'fullTileArt=homeFullArtBitmap!=null;',
    '?homeFullTileBackground(tileBase,homeFullArtBitmap):tileBase',
    'tile.addView(spacer,new LinearLayout.LayoutParams(-1,0,1f));',
    'if(!fullTileArt) {',
    'Pełne grafiki kafelków jako tło: WŁ. → wyłącz',
):
    assert token in main, token

# Nigdy nie rozciągamy zwykłej paczki 192x192 AI 3D jako tła.
fullart=main.split('private Bitmap homeFullTileBitmap(String iconId)',1)[1]
fullart=fullart.split('private String defaultTileIcon',1)[0]
assert 'IconPack3D.bitmap' not in fullart
assert 'Gravity.FILL' not in fullart

custom=(root / "TileCustomImage.java").read_text(encoding="utf-8")
assert 'private static final int MAX_EDGE = 1600;' in custom
assert 'Bitmap.createBitmap(192, 192' not in custom
assert 'MAX_EDGE/(float)Math.max(sourceWidth,sourceHeight)' in custom
assert 'if(scaled!=source)scaled.recycle();' in custom

for token in (
    'settings.put("homeTileFullArt"',
    'prefs.getBoolean("home_tile_full_art", true)',
    'boolean fullTileArt=settings.optBoolean("homeTileFullArt",true);',
    '.putBoolean("home_tile_full_art",fullTileArt)',
):
    assert token in backup, token
print("Full-tile AI/custom artwork background and high-resolution import: PASS")

# Pełne tło nie ma osobnego podpisu, więc home() nie może twardo rzutować
# ostatniego dziecka kafelka na TextView.
assert 'private TextView homeTileCaption(LinearLayout tile)' in main
assert 'child instanceof TextView' in main
assert '"home_caption".equals(child.getTag())' in main
assert 'TextView caption = homeTileCaption(tile);' in main
assert 'if(caption!=null)caption.setText(visibleLabel);' in main
assert 'captionView.setTag("home_caption");' in main
assert '(TextView) tile.getChildAt(tile.getChildCount() - 1)' not in main
print("Home full-art caption lookup is crash-safe: PASS")

# Biblioteka pełnych grafik: osobna od ikon, trzy style i ścisłe 1P/2P.
tileart=(root / "TileArtLibrary.java").read_text(encoding="utf-8")
for token in (
    'static final int SINGLE_WIDTH=840;',
    'static final int DOUBLE_WIDTH=1728;',
    'static final int HEIGHT=656;',
    'static final String STYLE_1="style1";',
    'static final String STYLE_2="style2";',
    'static final String STYLE_3="style3";',
    'target+suffix',
    'span==2?"_2p.webp":"_1p.webp"',
    'Grafika 2P musi mieć proporcję 1728 × 656.',
    'Grafika 1P musi mieć proporcję 840 × 656.',
    'migrateExistingCustomArt',
):
    assert token in tileart, token
for token in (
    'Grafika kafelka — pełne tło',
    'TileArtLibrary.prefKey(id)',
    'TileArtLibrary.bitmap(this,tileTarget,artStyle,span)',
    'if(homeFullArtBitmap==null)',
    'HOME_TILE_ART_IMPORTED',
    'IMPORT_TILE_ART',
    'EDHOME nigdy nie rozciąga 1P do 2P ani odwrotnie.',
):
    assert token in main, token
assert 'TileArtLibrary.bitmap(this,tileTarget,artStyle,1)' not in main
assert 'TileArtLibrary.bitmap(this,tileTarget,artStyle,2)' not in main
for token in (
    'tile.put("art"',
    'Map<String, String> arts = new HashMap<>();',
    'TileArtLibrary.knownStyle(art)',
    'key.startsWith(TileArtLibrary.PREF_PREFIX)',
    'restored.putString(TileArtLibrary.prefKey(id),arts.get(id));',
):
    assert token in backup, token
print("Tile artwork library with exact 1P/2P variants and backup preferences: PASS")
