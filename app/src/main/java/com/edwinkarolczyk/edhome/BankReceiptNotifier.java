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

/** Optional local acknowledgement after a bank notification has been handled.
 * It never proves settlement and never changes PayCheck balances.
 */
final class BankReceiptNotifier {
    private static final String CHANNEL="edhome_bank_hints_v1";

    private BankReceiptNotifier() {}

    private static boolean systemAllowed(Context context) {
        return Build.VERSION.SDK_INT<33
            ||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                ==PackageManager.PERMISSION_GRANTED;
    }

    static boolean allowed(Context context) {
        return BankNotificationHints.enabled(context)
            &&BankNotificationHints.receiptEnabled(context)
            &&systemAllowed(context);
    }

    private static NotificationManager manager(Context context) {
        if(!systemAllowed(context))return null;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return null;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "PayCheck • odebrane powiadomienia bankowe",
            NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(
            "Potwierdza odbiór sygnału z wybranego banku; nie potwierdza księgowania.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
        if(!manager.areNotificationsEnabled())return null;
        NotificationChannel configured=manager.getNotificationChannel(CHANNEL);
        return configured!=null
            &&configured.getImportance()==NotificationManager.IMPORTANCE_NONE
            ?null:manager;
    }

    private static String bankLabel(Context context,String source) {
        try {
            return context.getPackageManager().getApplicationLabel(
                context.getPackageManager().getApplicationInfo(source,0))
                .toString();
        }catch(PackageManager.NameNotFoundException unavailable) {
            return "Wybrany bank";
        }
    }

    private static PendingIntent openPaycheck(Context context,int requestCode) {
        Intent open=new Intent(context,MainActivity.class)
            .putExtra("open_paycheck",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                |Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context,requestCode,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    static void show(Context context,BankNotificationHints.Entry entry) {
        if(!BetaUpdater.isBeta()||!allowed(context)||entry==null)return;
        NotificationManager manager=manager(context);
        if(manager==null)return;
        try {
            String bank=bankLabel(context,entry.source);
            String amount=MoneyRules.format(entry.amount);
            String title="EDHOME • zapisano "
                +("income".equals(entry.kind)?"wpływ ":"wydatek ")+amount;
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
                .setContentIntent(openPaycheck(context,0))
                .setAutoCancel(true)
                .build();
            manager.notify("edhome_bank_hint_"+entry.key,1,notice);
        }catch(RuntimeException unavailable) {
            // A failed acknowledgement must not lose a saved PayCheck hint.
        }
    }

    /** Explicit two-minute diagnostic. No amount, merchant or bank text is copied. */
    static void showListenerTest(Context context,String source,boolean recognized) {
        if(!BetaUpdater.isBeta()||!BankNotificationHints.enabled(context))return;
        NotificationManager manager=manager(context);
        if(manager==null)return;
        try {
            String bank=bankLabel(context,source);
            String title=recognized
                ?"EDHOME • nasłuch działa"
                :"EDHOME • nasłuch działa, parser nie rozpoznał";
            String body=recognized
                ?bank+" • powiadomienie odebrane i zapisane do PayCheck"
                :bank+" • powiadomienie odebrane, ale bez jednoznacznej transakcji";
            Notification publicView=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle("EDHOME • test nasłuchu banku")
                .setContentText("Otwórz PayCheck, aby zobaczyć wynik testu.")
                .build();
            Notification notice=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicView)
                .setContentIntent(openPaycheck(context,7011))
                .setAutoCancel(true)
                .build();
            manager.notify("edhome_bank_listener_test",2,notice);
        }catch(RuntimeException unavailable) {
            // Test result remains visible in PayCheck even if Android blocks alerts.
        }
    }
}
