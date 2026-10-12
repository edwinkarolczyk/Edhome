package com.edwinkarolczyk.edhome;
import android.content.Context;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class DesktopHubSync {
    static int invalidations;
    static boolean crashOnInvalidate;
    static void invalidateAfterLocalRestore(Context context) {
        invalidations++;
        if(crashOnInvalidate)
            throw new IllegalStateException("Uszkodzony cache porównawczy");
    }
}
class DiagnosticLog {
    static void event(String code) { }
    static void error(String code,Exception error) { }
}

public final class BackupSyncGateSmoke {
    private static void ok(boolean check,String message) {
        if(!check)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Context context=new Context();
        ok(!BackupSyncGate.isHeld(context),"Pusta instalacja blokuje LAN");
        BackupSyncGate.beginRestore(context);
        ok(BackupSyncGate.isHeld(context),"ZIP nie zablokowal obu protokolow");

        AtomicBoolean transferred=new AtomicBoolean();
        CountDownLatch started=new CountDownLatch(1);
        Thread worker=new Thread(()->{
            started.countDown();
            BackupSyncGate.enterTransfer();
            try { transferred.set(true); }
            finally { BackupSyncGate.exitTransfer(); }
        });
        worker.start();
        ok(started.await(1,TimeUnit.SECONDS),"Brak watku klienta LAN");
        Thread.sleep(80L);
        ok(!transferred.get(),"Transfer LAN ominal trwajacy restore");
        BackupSyncGate.finishRestore(context,true);
        worker.join(2000L);
        ok(transferred.get(),"Transfer nie zwolnil mutexa po imporcie");
        ok(DesktopHubSync.invalidations==1,
            "Nie uniewazniono baseline Desktop po udanym ZIP");
        ok(BackupSyncGate.isHeld(new Context()),
            "Restart aplikacji usunal blokade po przywroceniu ZIP");

        BackupSyncGate.allowAfterReview(context);
        ok(!BackupSyncGate.isHeld(context),
            "Swiadome rozstrzygniecie nie wznowilo LAN");

        BackupSyncGate.beginRestore(context);
        BackupSyncGate.finishRestore(context,false);
        ok(BackupSyncGate.isHeld(context),
            "Nieudany restore automatycznie wznowil LAN");
        ok(DesktopHubSync.invalidations==1,
            "Nieudany restore nie powinien oznaczac ukonczonego resetu baseline");

        BackupSyncGate.allowAfterReview(context);
        DesktopHubSync.crashOnInvalidate=true;
        BackupSyncGate.beginRestore(context);
        BackupSyncGate.finishRestore(context,true);
        ok(BackupSyncGate.isHeld(context),
            "Blad cache synchronizacji nie moze zdjac blokady");
        DesktopHubSync.crashOnInvalidate=false;
        BackupSyncGate.allowAfterReview(context);
        System.out.println("EDHOME RESTORE_SYNC_GATE_SMOKE PASS: lock, "
            +"persistent hold, invalidation, fail-closed and explicit resume");
    }
}