package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.File;

/**
 * Kreator profilu wyłącznie przy pierwszym uruchomieniu Beta.
 * Z istniejącą bazą / po aktualizacji kreator nie jest uruchamiany.
 * Tworzy użytkownika przez istniejący UserProfileStore, bez danych demo.
 */
final class FirstRunProfileSetup {
    static final String PROFILE_PENDING = "first_run_profile_pending";
    static final String NOTIFICATION_PENDING = "first_run_notification_permission_pending";
    private static boolean showing;

    private FirstRunProfileSetup() { }

    static void register(Application application, SharedPreferences preferences) {
        application.registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle saved) { }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) {
                    if (!(activity instanceof MainActivity) || activity.isFinishing())
                        return;
                    activity.getWindow().getDecorView().post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (preferences.getBoolean(PROFILE_PENDING,false)) {
                            show(activity,preferences);
                        } else if (preferences.getBoolean(NOTIFICATION_PENDING,false)) {
                            askNotifications(activity,preferences);
                        }
                    });
                }
                @Override public void onActivityPaused(Activity activity) { }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(
                        Activity activity, Bundle state) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            });
    }

    private static void show(Activity activity, SharedPreferences preferences) {
        if (showing || !preferences.getBoolean(PROFILE_PENDING,false)) return;
        File file=activity.getDatabasePath("edhome-beta-preview.db");
        if (!file.exists()) return; // Poczekaj na inicjalizację bazy.
        try (SQLiteDatabase db=SQLiteDatabase.openDatabase(
                file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)) {
            // Przy odtworzeniu danych przed wyświetleniem kreatora
            // nie twórz dodatkowego administratora.
            if (UserProfileStore.count(db)>0) {
                preferences.edit().putBoolean(PROFILE_PENDING,false).apply();
                return;
            }
        } catch (Exception notReady) {
            DiagnosticLog.error("FIRST_RUN_PROFILE_DB_NOT_READY",notReady);
            return;
        }

        showing=true;
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int inset=(int)(22*activity.getResources().getDisplayMetrics().density);
        form.setPadding(inset,inset/2,inset,inset/2);
        EditText name=new EditText(activity);
        name.setHint("Twoje imię");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Pierwsze uruchomienie EDHOME")
            .setMessage("Utwórz pierwszy profil administratora. "
                +"Imię możesz później zmienić w Ustawieniach → Użytkownicy.")
            .setView(form)
            .setCancelable(false)
            .setPositiveButton("Utwórz profil",null)
            .create();
        dialog.setOnDismissListener(ignored -> showing=false);
        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value=name.getText().toString().trim();
                    if (value.isEmpty() || value.length()>80) {
                        name.setError("Wpisz imię (1–80 znaków).");
                        return;
                    }
                    try (SQLiteDatabase db=SQLiteDatabase.openDatabase(
                            file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                        if (UserProfileStore.count(db)==0) {
                            long id=UserProfileStore.add(db,value,
                                UserProfileStore.ROLE_ADMIN,
                                UserProfileStore.DEFAULT_AVATAR,
                                UserProfileStore.DEFAULT_COLOR);
                            if (id<=0) {
                                name.setError("Ten profil już istnieje.");
                                return;
                            }
                        }
                    } catch (Exception failure) {
                        DiagnosticLog.error("FIRST_RUN_PROFILE_SAVE_FAILED",failure);
                        name.setError("Nie udało się zapisać profilu. Spróbuj ponownie.");
                        return;
                    }
                    preferences.edit().putBoolean(PROFILE_PENDING,false).commit();
                    dialog.dismiss();
                    activity.recreate();
                }));
        dialog.show();
    }

    private static void askNotifications(Activity activity,
            SharedPreferences preferences) {
        // Pytanie systemowe tylko raz; nigdy nie deklaruj nadania uprawnienia.
        preferences.edit().putBoolean(NOTIFICATION_PENDING,false).apply();
        if (Build.VERSION.SDK_INT>=33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},4921);
        } else {
            ReminderReceiver.schedule(activity);
        }
    }
}
