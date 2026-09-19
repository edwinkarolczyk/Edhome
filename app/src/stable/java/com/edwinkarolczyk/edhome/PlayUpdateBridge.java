package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Stable channel: Play only. Check once per app launch after user unlocks.
 * The store owns downloading, signing, installation and distribution.
 */
public final class PlayUpdateBridge {
    private PlayUpdateBridge() { }
    public static void checkOnce(Activity activity) {
        AppUpdateManager manager = AppUpdateManagerFactory.create(activity);
        manager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                new AlertDialog.Builder(activity)
                    .setTitle("EDHOME — aktualizacja pobrana")
                    .setMessage("Google Play zakończyło pobieranie. Dokończyć aktualizację?")
                    .setPositiveButton("Aktualizuj", (d, w) -> manager.completeUpdate())
                    .setNegativeButton("Później", null).show();
                return;
            }
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE
                || !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return;
            new AlertDialog.Builder(activity)
                .setTitle("EDHOME — nowa wersja")
                .setMessage("W Google Play jest aktualizacja. Szczegóły zmian znajdziesz w opisie wydania. Możesz zainstalować ją teraz lub później.")
                .setPositiveButton("Aktualizuj", (d, w) -> {
                    try {
                        manager.startUpdateFlowForResult(info,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            activity, 3131);
                    } catch (Exception e) {
                        Log.w("EDHOME_PLAY", "Unable to start Play update");
                    }
                })
                .setNeutralButton("Szczegóły w Play", (d, w) -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception ignored) { }
                })
                .setNegativeButton("Później", null).show();
        }).addOnFailureListener(error ->
            Log.i("EDHOME_PLAY", "Play update check unavailable for this installation"));
    }
}
