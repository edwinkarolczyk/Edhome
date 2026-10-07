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
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Lokalne przypomnienia o planowanych płatnościach Budżetu miesiąca. */
public final class BudgetReminderReceiver extends BroadcastReceiver {
    private static final String ACTION =
        "com.edwinkarolczyk.edhome.REMIND_BUDGET_PAYMENT";
    private static final String CHANNEL = "edhome-budget-due";
    private static final String DATABASE = "edhome-beta-preview.db";
    private static final int[] LEADS = {3, 0};

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
            "edhome_beta_prefs", Context.MODE_PRIVATE);
    }

    private static int requestCode(String itemId, YearMonth month, int lead) {
        int hash = (itemId + "|" + month + "|" + lead).hashCode();
        return 220000000 + (hash & 0x0fffffff);
    }

    private static PendingIntent alarmIntent(Context context, String itemId,
            YearMonth month, int lead, int flags) {
        Intent intent = new Intent(context,BudgetReminderReceiver.class)
            .setAction(ACTION)
            .putExtra("item_id",itemId)
            .putExtra("month",month.toString())
            .putExtra("lead",lead);
        return PendingIntent.getBroadcast(context,
            requestCode(itemId,month,lead),intent,
            flags | LegacyCompat.immutableFlag());
    }

    private static int notificationId(String itemId, YearMonth month) {
        return 320000000 + ((itemId + "|" + month).hashCode() & 0x0fffffff);
    }

    static void schedule(Context context) {
        SharedPreferences pref = prefs(context);
        if (!pref.getBoolean("budget_reminders_enabled",true)) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(
            Context.ALARM_SERVICE);
        if (manager == null) return;

        final List<PaycheckMonthlyBudget.Item> items;
        try {
            items = PaycheckMonthlyBudget.load(pref);
        } catch(Exception damaged) {
            DiagnosticLog.error("BUDGET_REMINDER_PLAN",damaged);
            return;
        }

        java.io.File databaseFile = context.getDatabasePath(DATABASE);
        SQLiteDatabase database = null;
        try {
            if (databaseFile.isFile())
                database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),null,
                    SQLiteDatabase.OPEN_READONLY);
            LocalDateTime now = LocalDateTime.now();
            YearMonth current = YearMonth.from(now);
            for (PaycheckMonthlyBudget.Item item : items) {
                if (!"expense".equals(item.kind) || !item.active) continue;
                for (int offset=0;offset<4;offset++) {
                    YearMonth month = current.plusMonths(offset);
                    if (PaycheckMonthlyBudget.activeFor(
                            java.util.Collections.singletonList(item),month)
                            .isEmpty()) continue;
                    LocalDate planned =
                        PaycheckMonthlyBudget.plannedPaymentDate(item,month);
                    if (planned == null) continue;

                    long plannedAmount =
                        PaycheckMonthlyBudget.plannedAmount(item,month);
                    long appliedCredit =
                        PaycheckMonthlyBudget.creditAppliedTo(item,month);
                    long actual = database == null ? 0L
                        : PaycheckMonthlyBudget.sharedMatchedActual(
                            database,item,month);
                    boolean paid = actual + appliedCredit >= plannedAmount;
                    NotificationManager notifications =
                        (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
                    if (paid && notifications != null)
                        notifications.cancel(notificationId(item.id,month));

                    for (int lead : LEADS) {
                        PendingIntent pending = alarmIntent(context,item.id,month,
                            lead,PendingIntent.FLAG_UPDATE_CURRENT);
                        manager.cancel(pending);
                        if (paid) continue;
                        LocalDate targetDay = planned.minusDays(lead);
                        if (targetDay.isBefore(now.toLocalDate())) continue;
                        LocalDateTime target = LocalDateTime.of(
                            targetDay,LocalTime.of(8,0));
                        if (!target.isAfter(now))
                            target = now.plusMinutes(1);
                        target = ReminderRules.nextAllowed(target,
                            pref.getString("quiet_hours_start",
                                QuietHoursRules.DEFAULT_START),
                            pref.getString("quiet_hours_end",
                                QuietHoursRules.DEFAULT_END));
                        LegacyCompat.setAndAllowWhileIdle(
                            manager,AlarmManager.RTC_WAKEUP,
                            target.atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli(),
                            pending);
                    }
                }
            }
            DiagnosticLog.event("BUDGET_REMINDERS_SCHEDULED");
        } catch(Exception error) {
            DiagnosticLog.error("BUDGET_REMINDER_SCHEDULE",error);
        } finally {
            if (database != null) database.close();
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        SharedPreferences pref = prefs(context);
        if (!pref.getBoolean("budget_reminders_enabled",true)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        String itemId = intent.getStringExtra("item_id");
        String monthRaw = intent.getStringExtra("month");
        int lead = intent.getIntExtra("lead",-1);
        if (itemId == null || monthRaw == null || (lead != 0 && lead != 3))
            return;

        final YearMonth month;
        try { month = YearMonth.parse(monthRaw); }
        catch(Exception invalid) { return; }

        PaycheckMonthlyBudget.Item item = null;
        try {
            for (PaycheckMonthlyBudget.Item candidate
                    : PaycheckMonthlyBudget.load(pref))
                if (itemId.equals(candidate.id)) {
                    item = candidate;
                    break;
                }
        } catch(Exception damaged) {
            return;
        }
        if (item == null || !"expense".equals(item.kind)
                || PaycheckMonthlyBudget.activeFor(
                    java.util.Collections.singletonList(item),month).isEmpty())
            return;

        LocalDate plannedDate =
            PaycheckMonthlyBudget.plannedPaymentDate(item,month);
        if (plannedDate == null
                || !plannedDate.minusDays(lead).equals(LocalDate.now()))
            return;

        long plannedAmount = PaycheckMonthlyBudget.plannedAmount(item,month);
        long appliedCredit = PaycheckMonthlyBudget.creditAppliedTo(item,month);
        long actual = 0L;
        java.io.File databaseFile = context.getDatabasePath(DATABASE);
        if (databaseFile.isFile()) {
            try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),null,
                    SQLiteDatabase.OPEN_READONLY)) {
                actual = PaycheckMonthlyBudget.sharedMatchedActual(
                    database,item,month);
            } catch(Exception problem) {
                DiagnosticLog.error("BUDGET_REMINDER_DATABASE",problem);
                return;
            }
        }
        if (actual + appliedCredit >= plannedAmount) {
            NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notificationId(item.id,month));
            return;
        }

        NotificationManager notifications = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null
                || !LegacyCompat.notificationsEnabled(notifications)) return;
        LegacyCompat.ensureChannel(notifications,CHANNEL,
            "Budżet • płatności",NotificationManager.IMPORTANCE_DEFAULT,
            null,true,true,false);

        String recipient = "";
        try {
            recipient = PaycheckRecipientStore.name(
                PaycheckRecipientStore.load(pref),item.recipientId);
        } catch(Exception ignored) { }
        String title = recipient.isBlank() ? item.name
            : recipient + " • " + item.name;
        String message = lead == 3
            ? "Za 3 dni planowana płatność • "
                + MoneyRules.format(Math.max(0L,plannedAmount-appliedCredit))
            : "Planowana płatność dzisiaj • "
                + MoneyRules.format(Math.max(0L,plannedAmount-appliedCredit));

        Intent openIntent = new Intent(context,MainActivity.class)
            .putExtra("open_paycheck_budget",true)
            .putExtra("budget_month",month.toString())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context,
            notificationId(item.id,month),openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());

        Notification notification =
            LegacyCompat.notificationBuilder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle("EDHOME • " + title)
                .setContentText(message)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build();
        notifications.notify(notificationId(item.id,month),notification);
        DiagnosticLog.event("BUDGET_REMINDER_DELIVERED",
            "lead="+lead);
    }
}
