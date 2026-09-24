package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Optional local acknowledgement after a NEW bank hint has been saved.
 * Not a statement of bank settlement and never changes PayCheck balances.
 */
final class BankReceiptNotifier {
    private static final String CHANNEL="edhome_bank_hints_v1";

    private BankReceiptNotifier() {}

    static boolean allowed(Context context) {
        return BankNotificationHints.enabled(context)
            && BankNotificationHints.receiptEnabled(context)
            && (Build.VERSION.SDK_INT<33
                ||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    ==PackageManager.PERMISSION_GRANTED);
    }

    static void show(Context context,BankNotificationHints.Entry entry) {
        if(!BetaUpdater.isBeta()||!allowed(context)||entry==null)return;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        try {
            NotificationChannel channel=new NotificationChannel(CHANNEL,
                "PayCheck • odebrane powiadomienia bankowe",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Potwierdza zapis sugestii z wybranych banków; nie potwierdza księgowania.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(channel);
            if(!manager.areNotificationsEnabled())return;
            NotificationChannel configured=manager.getNotificationChannel(CHANNEL);
            if(configured!=null
                    &&configured.getImportance()==NotificationManager.IMPORTANCE_NONE)
                return;

            String bank="Wybrany bank";
            try {
                bank=context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(entry.source,0))
                    .toString();
            }catch(PackageManager.NameNotFoundException unavailable) {
                // Do not leak the source package or the original notification text.
            }
            String amount=MoneyRules.format(entry.amount);
            String title="EDHOME • zapisano "
                +("income".equals(entry.kind)?"wpływ ":"wydatek ")+amount;
            Intent open=new Intent(context,MainActivity.class)
                .putExtra("open_paycheck",true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    |Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap=PendingIntent.getActivity(context,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification publicView=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle("EDHOME • nowy sygnał bankowy")
                .setContentText("Otwórz PayCheck, aby sprawdzić sugestię.")
                .build();
            Notification notice=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle(title)
                .setContentText(bank+" • do potwierdzenia w PayCheck")
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicView)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
            // Distinct saved hints get distinct notifications. Duplicates never
            // reach this method; no merchant, card or full bank text is retained.
            manager.notify("edhome_bank_hint_"+entry.key,1,notice);
        }catch(RuntimeException unavailable) {
            // Notification permission/channel can be revoked at any time.
            // A failed acknowledgement must not lose a saved PayCheck hint.
        }
    }
}
