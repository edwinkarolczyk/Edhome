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
import java.time.ZoneId;

/** Lokalne przypomnienia początku przygotowań i dnia wcześniej. */
public final class FrostReminderReceiver extends BroadcastReceiver {
    private static final String ACTION="com.edwinkarolczyk.edhome.FROST";
    private static final int DUE=71721,BEFORE=71722;
    static void schedule(Context context) {
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null)return;
        for(int code:new int[]{DUE,BEFORE}) {
            Intent intent=new Intent(context,FrostReminderReceiver.class)
                .setAction(ACTION).putExtra("code",code);
            PendingIntent pending=PendingIntent.getBroadcast(context,code,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());
            alarm.cancel(pending);
            SharedPreferences pref=context.getSharedPreferences("edhome_beta_prefs",0);
            if(!pref.getBoolean("reminders_enabled",false))continue;
            java.io.File file=context.getDatabasePath("edhome-beta-preview.db");
            if(!file.exists())continue;
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
                FrostStore.Settings cfg=FrostStore.settings(db);
                if(code==DUE&&!cfg.remindStart||code==BEFORE&&!cfg.remindBefore)continue;
                LocalDate today=LocalDate.now();
                for(int year=today.getYear();year<=today.getYear()+1;year++){
                    LocalDateTime time=FrostStore.due(cfg,year)
                        .minusDays(code==BEFORE?1:0).atTime(9,0);
                    if(!time.isAfter(LocalDateTime.now()))continue;
                    LegacyCompat.setAndAllowWhileIdle(alarm,AlarmManager.RTC_WAKEUP,
                        time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        pending);
                    break;
                }
            }catch(Exception e){DiagnosticLog.error("FROST_REMINDER_SCHEDULE",e);}
        }
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        SharedPreferences pref=context.getSharedPreferences("edhome_beta_prefs",0);
        if(!pref.getBoolean("reminders_enabled",false))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        LocalDateTime now=LocalDateTime.now();
        if(QuietHoursRules.isQuiet(now,
            pref.getString("quiet_hours_start",QuietHoursRules.DEFAULT_START),
            pref.getString("quiet_hours_end",QuietHoursRules.DEFAULT_END)))return;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(
                context.getDatabasePath("edhome-beta-preview.db").getAbsolutePath(),
                null,SQLiteDatabase.OPEN_READONLY)){
            FrostStore.Settings cfg=FrostStore.settings(db);
            int code=intent.getIntExtra("code",DUE);
            if(code==DUE&&!cfg.remindStart||code==BEFORE&&!cfg.remindBefore)return;
            if(!FrostStore.due(cfg,now.getYear())
                .minusDays(code==BEFORE?1:0).equals(now.toLocalDate()))return;
            int count=FrostStore.pending(db,now.getYear());
            if(count==0)return;
            NotificationManager manager=(NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager==null||!LegacyCompat.notificationsEnabled(manager))return;
            LegacyCompat.ensureChannel(manager,"edhome-frost","Ochrona przed mrozem",
                NotificationManager.IMPORTANCE_DEFAULT,null,true,true,false);
            PendingIntent open=PendingIntent.getActivity(context,0,
                new Intent(context,MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());
            Notification notice=LegacyCompat.notificationBuilder(context,"edhome-frost")
                .setSmallIcon(R.drawable.ic_edhome)
                .setContentTitle("EDHOME • Przed zimą")
                .setContentText("Zabezpiecz przed mrozem: "+count+" obiektów")
                .setContentIntent(open).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build();
            manager.notify(code,notice);
        }catch(Exception e){DiagnosticLog.error("FROST_REMINDER_NOTIFY",e);}
        finally{schedule(context);}
    }
}
