package com.edwinkarolczyk.edhome;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * Zatwierdzone ustawienia wyłącznie dla nowej instalacji EDHOME Beta.
 * Aktualizacja, istniejąca baza oraz odtwarzanie ZIP zachowują preferencje.
 * Uprawnienie POST_NOTIFICATIONS nadal zatwierdza użytkownik w Androidzie.
 */
public final class EdhomeApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Diagnostyka działa przed ekranami, odbiornikami i usługą LAN.
        DiagnosticLog.init(this);
        if (!BuildConfig.DIAGNOSTICS_ENABLED) return;

        SharedPreferences prefs=getSharedPreferences(
            "edhome_beta_prefs",MODE_PRIVATE);
        // Baza może już istnieć mimo braku kluczy preferences (stara wersja).
        // Nigdy nie traktuj takiej aktualizacji jako świeżej instalacji.
        if (prefs.getAll().isEmpty()
                && !getDatabasePath("edhome-beta-preview.db").exists()) {
            initializeFreshInstall(prefs);
        }
        FirstRunProfileSetup.register(this,prefs);
    }

    private void initializeFreshInstall(SharedPreferences prefs) {
        prefs.edit()
            .putBoolean(FirstRunProfileSetup.PROFILE_PENDING,true)
            .putBoolean(FirstRunProfileSetup.NOTIFICATION_PENDING,true)
            .putString("theme",UiSkin.PASTEL)
            .putString("home_interface_mode","current")
            .putInt(HomeTileLayout.PAGE_SLOTS_KEY,24)
            .putBoolean("home_tile_full_art",true)
            .putInt(HomeTileLayout.SHORT_KEY,HomeTileLayout.DEFAULT_SHORT_MS)
            .putInt(HomeTileLayout.DRAG_KEY,HomeTileLayout.DEFAULT_DRAG_MS)
            .putBoolean("reminders_enabled",true)
            .putString("quiet_hours_start",QuietHoursRules.DEFAULT_START)
            .putString("quiet_hours_end",QuietHoursRules.DEFAULT_END)
            .putBoolean("nfc_global_listen",false)
            .putBoolean("storage_gallery_enabled",true)
            .putBoolean("storage_show_things",true)
            .putBoolean("storage_show_boxes",true)
            .putBoolean("storage_show_places",true)
            .putInt(PantryTakeCountdown.DELAY_PREF,PantryTakeCountdown.DEFAULT_SECONDS)
            .commit();
        // Domyślne AI 3D i "Pokaż działania" po QR/NFC wynikają z istniejących
        // reguł aplikacji. Nie ustawiaj "icon_style_explicit" ani reguły skanu:
        // te znaczniki są przeznaczone dla świadomych wyborów użytkownika.
    }
}
