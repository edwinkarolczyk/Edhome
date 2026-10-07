package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local reminders for unpaid monthly-budget expenses. */
public final class PaycheckBudgetReminderReceiver extends BroadcastReceiver {
    private static final String ACTION =
        "com.edwinkarolczyk.edhome.REMIND_PAYCHECK_BUDGET";
    private static final String CHANNEL_ID = "edhome-paycheck-budget";
    private static final int ALARM_ID = 82341;
    private static final int NOTIFICATION_ID = 82342;
    static final String ENABLED_PREF = "paycheck_budget_reminders_enabled";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
            "edhome_beta_prefs",Context.MODE_PRIVATE);
    }

    static void schedule(Context context) {
        AlarmManager manager=(AlarmManager)context.getSystemService(
            Context.ALARM_SERVICE);
        if (manager==null) return;
        Intent intent=new Intent(context,PaycheckBudgetReminderReceiver.class)
            .setAction(ACTION);
        PendingIntent pending=PendingIntent.getBroadcast(
            context,ALARM_ID,intent,PendingIntent.FLAG_UPDATE_CURRENT
                |LegacyCompat.immutableFlag());
        manager.cancel(pending);
        if (!prefs(context).getBoolean(ENABLED_PREF,true)) return;

        LocalDateTime now=LocalDateTime.now();
        LocalDateTime next=now.toLocalDate().atTime(8,0);
        if (!next.isAfter(now)) next=next.plusDays(1);
        LegacyCompat.setAndAllowWhileIdle(manager,AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),pending);
        DiagnosticLog.event("PAYCHECK_BUDGET_REMINDER_SCHEDULED");
    }

    @Override public void onReceive(Context context,Intent intent) {
        if (intent==null) return;
        String action=intent.getAction();
        if (!ACTION.equals(action)) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action))
                schedule(context);
            return;
        }
        schedule(context);
        SharedPreferences pref=prefs(context);
        if (!pref.getBoolean(ENABLED_PREF,true)) return;
        if (Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED) return;

        LocalDate today=LocalDate.now();
        Set<YearMonth> months=new LinkedHashSet<>();
        months.add(YearMonth.from(today));
        months.add(YearMonth.from(today.plusDays(3)));
        int dueToday=0;
        int dueSoon=0;
        long todayAmount=0L;
        long soonAmount=0L;
        String firstName="";

        LocalDb helper=null;
        try {
            helper=new LocalDb(context);
            java.util.List<PaycheckMonthlyBudget.Item> items=
                PaycheckMonthlyBudget.load(pref);
            android.database.sqlite.SQLiteDatabase database=
                helper.getReadableDatabase();
            for (YearMonth month:months) {
                List<PaycheckMonthlyBudget.Item> active=
                    PaycheckMonthlyBudget.activeFor(items,month);
                for (PaycheckMonthlyBudget.Item item:active) {
                    if (!"expense".equals(item.kind)) continue;
                    LocalDate planned=
                        PaycheckMonthlyBudget.plannedPaymentDate(item,month);
                    if (planned==null) continue;
                    boolean todayHit=planned.equals(today);
                    boolean soonHit=planned.minusDays(3).equals(today);
                    if (!todayHit && !soonHit) continue;

                    long plannedAmount=PaycheckMonthlyBudget.plannedAmount(
                        item,month);
                    long credit=PaycheckMonthlyBudget.creditAppliedTo(item,month);
                    long due=Math.max(0L,plannedAmount-credit);
                    long actual=PaycheckMonthlyBudget.sharedMatchedActual(
                        database,item,month);
                    long remaining=Math.max(0L,due-actual);
                    if (remaining<=0L) continue;

                    String firedKey="paycheck_budget_reminder_fired_"
                        +item.id+"_"+month+"_"+(todayHit?"0":"3");
                    if (today.toString().equals(pref.getString(firedKey,"")))
                        continue;
                    pref.edit().putString(firedKey,today.toString()).apply();
                    if (firstName.isEmpty()) firstName=item.name;
                    if (todayHit) {
                        dueToday++;
                        todayAmount=Math.addExact(todayAmount,remaining);
                    } else {
                        dueSoon++;
                        soonAmount=Math.addExact(soonAmount,remaining);
                    }
                }
            }
        } catch(Exception error) {
            DiagnosticLog.error("PAYCHECK_BUDGET_REMINDER_READ",error);
            return;
        } finally {
            if (helper!=null) helper.close();
        }

        if (dueToday==0 && dueSoon==0) return;
        NotificationManager notifications=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications==null
                ||!LegacyCompat.notificationsEnabled(notifications)) return;
        LegacyCompat.ensureChannel(notifications,CHANNEL_ID,
            "Budżet miesiąca",NotificationManager.IMPORTANCE_DEFAULT,
            null,true,true,false);

        Intent openIntent=new Intent(context,MainActivity.class)
            .putExtra("open_paycheck_budget",true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                |Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(
            context,NOTIFICATION_ID,openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());

        String text;
        if (dueToday>0 && dueSoon>0)
            text="Dzisiaj "+dueToday+" • "+MoneyRules.format(todayAmount)
                +" | za 3 dni "+dueSoon+" • "+MoneyRules.format(soonAmount);
        else if (dueToday>0)
            text="Dzisiaj do zapłaty: "+dueToday+" • "
                +MoneyRules.format(todayAmount);
        else
            text="Za 3 dni do zapłaty: "+dueSoon+" • "
                +MoneyRules.format(soonAmount);

        Notification notification=LegacyCompat.notificationBuilder(
                context,CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle(firstName.isEmpty()
                ?"EDHOME • Budżet miesiąca"
                :"EDHOME • "+firstName)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build();
        notifications.notify(NOTIFICATION_ID,notification);
        DiagnosticLog.event("PAYCHECK_BUDGET_REMINDER_DELIVERED",
            "today="+dueToday+" soon="+dueSoon);
    }
}
