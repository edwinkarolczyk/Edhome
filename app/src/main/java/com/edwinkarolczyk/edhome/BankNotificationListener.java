package com.edwinkarolczyk.edhome;

import android.app.Notification;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.lang.ref.WeakReference;

/** Beta-only manifest entry. System permission + explicit app allowlist
 * are both required. All other notifications are ignored without reading extras.
 */
public final class BankNotificationListener extends NotificationListenerService {
    // Android binds this service independently of an open EDHOME activity.
    // A weak reference lets the opt-in screen recheck notifications already visible
    // when the user selects a bank after granting notification access.
    private static volatile WeakReference<BankNotificationListener> connected;

    /** Connection to Android is not the same as just having permission. */
    static boolean isConnected() {
        WeakReference<BankNotificationListener> ref=connected;
        return ref!=null && ref.get()!=null;
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected=new WeakReference<>(this);
        collectActiveNotifications();
    }

    @Override public void onListenerDisconnected() {
        clearConnected();
        super.onListenerDisconnected();
        // Android may disconnect a listener under memory pressure or after an
        // app update. Ask the framework to bind us again; no foreground
        // service or permanent notification is required.
        try {
            requestRebind(new ComponentName(this,
                BankNotificationListener.class));
        } catch (RuntimeException ignored) {
            // The system may be shutting down or the permission may be gone.
        }
    }

    @Override public void onDestroy() {
        clearConnected();
        super.onDestroy();
    }

    private void clearConnected() {
        WeakReference<BankNotificationListener> ref=connected;
        if(ref!=null && ref.get()==this)connected=null;
    }

    /** Called after an explicit bank selection; does not request new access. */
    static void recheckActiveNotifications() {
        WeakReference<BankNotificationListener> ref=connected;
        BankNotificationListener service=ref==null?null:ref.get();
        if(service!=null)service.collectActiveNotifications();
    }

    /** Catch up only on notifications STILL active when Android reconnects.
     * Dismissed notifications cannot be recovered by this service.
     */
    private void collectActiveNotifications() {
        if(!BetaUpdater.isBeta()||!BankNotificationHints.enabled(this))return;
        try {
            StatusBarNotification[] active=getActiveNotifications();
            if(active==null)return;
            for(StatusBarNotification sbn:active)onNotificationPosted(sbn);
        }catch(RuntimeException disconnected) {
            // Android can revoke permission or disconnect during the query.
            // Do not log bank notification contents or alter ledger entries.
            DiagnosticLog.error("BANK_NOTIFICATION_RECHECK",disconnected);
        }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null||!BetaUpdater.isBeta()
                ||!BankNotificationHints.enabled(this)
                ||!BankNotificationHints.selected(this)
                    .contains(sbn.getPackageName()))return;
        // Diagnostic metadata for selected banks only: no content or card data.
        BankNotificationHints.recordSeen(this,sbn.getPackageName());
        Notification notification=sbn.getNotification();
        if(notification==null||notification.extras==null)return;
        CharSequence title=notification.extras.getCharSequence(
            Notification.EXTRA_TITLE);
        CharSequence body=notification.extras.getCharSequence(
            Notification.EXTRA_BIG_TEXT);
        if(body==null)body=notification.extras.getCharSequence(
            Notification.EXTRA_TEXT);
        String text=(title==null?"":title.toString())+"\n"
            +(body==null?"":body.toString());
        BankNotificationHints.collect(this,sbn,text);
    }
}
