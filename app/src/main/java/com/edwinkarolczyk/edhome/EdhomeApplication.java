package com.edwinkarolczyk.edhome;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * Ustawienia pierwszego uruchomienia: Styl 1 wyłącznie dla NOWEJ instalacji.
 * Aktualizacja istniejącej wersji nie może zmieniać wyglądu użytkownika,
 * również gdy poprzedni motyw był tylko domyślny i nie zapisano klucza theme.
 */
public final class EdhomeApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences prefs=getSharedPreferences(
            "edhome_beta_prefs",MODE_PRIVATE);
        if(!prefs.contains("theme") && prefs.getAll().isEmpty()) {
            prefs.edit().putString("theme",UiSkin.MODERN_LIGHT).commit();
        }
    }
}
