package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Bariera przywracanie ZIP ↔ LAN, współdzielona przez Huba i serwer telefonu.
 *
 * Blokada jest sprawiedliwa: import czeka na już rozpoczętą operację LAN,
 * a następne transfery nie mogą minąć importu. Flaga przetrwa restart APK
 * także wtedy, gdy system zabije proces w trakcie odtwarzania.
 */
final class BackupSyncGate {
    private static final String PREFS = "edhome_beta_prefs";
    private static final String HOLD = "desktop_sync_restore_review_required";
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static volatile boolean restoring;

    private BackupSyncGate() { }

    static void beginRestore(Context context) {
        LOCK.lock();
        boolean started = false;
        try {
            if (!prefs(context).edit().putBoolean(HOLD, true).commit())
                throw new IllegalStateException(
                    "Nie zapisano blokady synchronizacji przed importem.");
            restoring = true;
            started = true;
            DiagnosticLog.event("BACKUP_SYNC_GATE_STARTED");
        } finally {
            if (!started) LOCK.unlock();
        }
    }

    /**
     * Zawsze wywołuj w finally wątku, który uruchomił beginRestore().
     * Po błędzie pozostaw wstrzymanie (fail closed): nie zakładaj, że
     * częściowo przywrócona baza jest zgodna z PC.
     */
    static void finishRestore(Context context, boolean success) {
        try {
            if (success) {
                try {
                    DesktopHubSync.invalidateAfterLocalRestore(context);
                } catch (Exception failure) {
                    DiagnosticLog.error("BACKUP_SYNC_BASELINE_INVALIDATE",failure);
                    // Flaga HOLD została utrwalona w beginRestore().
                }
                DiagnosticLog.event("BACKUP_SYNC_RESTORE_COMPLETED");
            } else {
                DiagnosticLog.event("BACKUP_SYNC_RESTORE_UNCERTAIN");
            }
        } finally {
            restoring = false;
            LOCK.unlock();
        }
    }

    static boolean isHeld(Context context) {
        return restoring || prefs(context).getBoolean(HOLD,false);
    }

    /** Do wywołania dopiero po wybraniu strony konfliktu lub świadomym
     * wznowieniu starszego interfejsu LAN. */
    static void allowAfterReview(Context context) {
        if (!prefs(context).edit().putBoolean(HOLD,false).commit())
            throw new IllegalStateException(
                "Nie zapisano zgody na wznowienie synchronizacji.");
        DiagnosticLog.event("BACKUP_SYNC_GATE_RELEASED");
    }

    /** Wspólny mutex chroni transakcje LAN przed przywróceniem ZIP. */
    static void enterTransfer() {
        LOCK.lock();
    }

    static void exitTransfer() {
        LOCK.unlock();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
            PREFS, Context.MODE_PRIVATE);
    }
}
