#!/usr/bin/env python3
"""EDHOME Beta: profil pierwszej instalacji i zachowanie ustawień po ZIP."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/com/edwinkarolczyk/edhome"
app=(source/"EdhomeApplication.java").read_text(encoding="utf-8")
backup=(source/"DataBackup.java").read_text(encoding="utf-8")
main=(source/"MainActivity.java").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# Absolutnie nie inicjalizuj presetu w Stable / na aktualizowanym telefonie.
for token in (
    'if (!BuildConfig.DIAGNOSTICS_ENABLED) return;',
    '"edhome_beta_prefs"',
    'if (!prefs.getAll().isEmpty()',
    'getDatabasePath("edhome-beta-preview.db").exists()) return;',
    '.putString("theme",UiSkin.PASTEL)',
    '.putString("home_interface_mode","current")',
    '.putInt(HomeTileLayout.PAGE_SLOTS_KEY,24)',
    '.putBoolean("home_tile_full_art",true)',
    '.putInt(HomeTileLayout.SHORT_KEY,HomeTileLayout.DEFAULT_SHORT_MS)',
    '.putInt(HomeTileLayout.DRAG_KEY,HomeTileLayout.DEFAULT_DRAG_MS)',
    '.putBoolean("reminders_enabled",true)',
    '.putString("quiet_hours_start",QuietHoursRules.DEFAULT_START)',
    '.putString("quiet_hours_end",QuietHoursRules.DEFAULT_END)',
    '.putBoolean("nfc_global_listen",false)',
    '.putBoolean("storage_gallery_enabled",true)',
    '.putBoolean("storage_show_things",true)',
    '.putBoolean("storage_show_boxes",true)',
    '.putBoolean("storage_show_places",true)',
    '.putInt(PantryTakeCountdown.DELAY_PREF,PantryTakeCountdown.DEFAULT_SECONDS)',
):
    assert token in app, "Preset nowej instalacji: " + token
assert 'android:name=".EdhomeApplication"' in manifest

# Reguły QR/NFC: bez wymuszania "otwórz" i bez ustawiania klucza
# świadomego wyboru stylu ikon.
assert 'SCAN_DEFAULT_NFC = "scan_default_nfc_global"' in main
assert 'SCAN_DEFAULT_GLOBAL' in main
assert 'icon_style_explicit' not in app

# Preset nie może tworzyć użytkownika Edwin ani zapisywać PIN-u.
for forbidden in ('"Edwin"', 'pin_hash', 'household_members', 'user_profiles'):
    assert forbidden not in app, "Konfiguracja profilu wymaga zgody: " + forbidden

# Wsteczna kompatybilność: stare ZIP nie muszą mieć nowych pól.
fields={
    "remindersEnabled":"reminders_enabled",
    "nfcGlobalListen":"nfc_global_listen",
    "storageGalleryEnabled":"storage_gallery_enabled",
    "storageShowThings":"storage_show_things",
    "storageShowBoxes":"storage_show_boxes",
    "storageShowPlaces":"storage_show_places",
}
for field,pref in fields.items():
    assert 'settings.put("'+field+'"' in backup
    assert 'if (settings.has("'+field+'"))' in backup
    assert 'restored.putBoolean("'+pref+'"' in backup
    assert 'settings.getBoolean("'+field+'")' in backup
    assert '"'+field+'"' in backup
assert 'if (settings.has(key) && !(settings.get(key) instanceof Boolean))' in backup
assert 'if (!restored.commit())' in backup
print("EDHOME Beta: preset świeżej instalacji, upgrade/ZIP i reguły skanera OK")
