package com.edwinkarolczyk.edhome;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Beta-only manifest entry. System permission + explicit app allowlist
 * are both required. All other notifications are ignored without reading extras.
 */
public final class BankNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null||!BetaUpdater.isBeta()
                ||!BankNotificationHints.enabled(this)
                ||!BankNotificationHints.selected(this)
                    .contains(sbn.getPackageName()))return;
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
