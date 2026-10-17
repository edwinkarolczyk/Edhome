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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Raz dziennie przypomina o rzeczach oznaczonych "powinna wracać na miejsce".
 * Nie używa timera godzinowego, internetu ani nie zmienia stałego położenia.
 */
public final class StorageHomeReminderReceiver extends BroadcastReceiver {
    private static final String ACTION="com.edwinkarolczyk.edhome.STORAGE_HOME_DAILY";
    private static final String CHANNEL="edhome-storage-home";
    private static final int REQUEST=73810;
    private static final int NOTICE=73811;
    private static final String DB="edhome-beta-preview.db";
    private static final String PREFS="edhome_beta_prefs";

    static void schedule(Context context) {
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarms==null)return;
        Intent intent=new Intent(context,StorageHomeReminderReceiver.class).setAction(ACTION);
        PendingIntent pending=PendingIntent.getBroadcast(context,REQUEST,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());
        alarms.cancel(pending);
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!prefs.getBoolean("reminders_enabled",false)) {
            clearNotification(context);
            return;
        }
        File file=context.getDatabasePath(DB);
        if(!file.isFile())return;
        boolean active=false;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),
                null,SQLiteDatabase.OPEN_READONLY);
            Cursor c=db.rawQuery("SELECT 1 FROM storage_items WHERE "
                +"kind='thing' AND return_required=1 AND taken_at IS NOT NULL LIMIT 1",null)) {
            active=c.moveToFirst();
        }catch(Exception error) {
            DiagnosticLog.error("STORAGE_HOME_SCHEDULE",error);
            return;
        }
        if(!active) {
            clearNotification(context);
            return;
        }
        // Alarm jest dzienny; wyznaczamy następny dzień po każdym uruchomieniu.
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime next=now.toLocalDate().atTime(9,0);
        if(!next.isAfter(now))next=next.plusDays(1);
        LegacyCompat.setAndAllowWhileIdle(alarms,AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),pending);
    }

    static void clearNotification(Context context) {
        NotificationManager mgr=(NotificationManager)context.getSystemService(
            Context.NOTIFICATION_SERVICE);
        if(mgr!=null)mgr.cancel(NOTICE);
    }

    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        try {
            deliver(context);
        }catch(Exception error){
            DiagnosticLog.error("STORAGE_HOME_REMINDER",error);
        }finally {
            schedule(context);
        }
    }

    private static void deliver(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!prefs.getBoolean("reminders_enabled",false))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return;
        LocalDateTime now=LocalDateTime.now();
        if(QuietHoursRules.isQuiet(now,
                prefs.getString("quiet_hours_start",QuietHoursRules.DEFAULT_START),
                prefs.getString("quiet_hours_end",QuietHoursRules.DEFAULT_END)))
            return;

        NotificationManager mgr=(NotificationManager)context.getSystemService(
            Context.NOTIFICATION_SERVICE);
        if(mgr==null||!LegacyCompat.notificationsEnabled(mgr))return;
        long today=now.toLocalDate().toEpochDay();
        List<String> waiting=new ArrayList<>();
        List<Long> waitingIds=new ArrayList<>();
        File file=context.getDatabasePath(DB);
        if(!file.isFile())return;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),
                null,SQLiteDatabase.OPEN_READONLY);
            Cursor cursor=db.rawQuery(
                "SELECT id,name,taken_at,return_after_days FROM storage_items "
                    +"WHERE kind='thing' AND return_required=1 AND taken_at IS NOT NULL",
                null)) {
            while(cursor.moveToNext()) {
                long itemId=cursor.getLong(0);
                String name=cursor.getString(1);
                long taken=cursor.getLong(2);
                int days=cursor.getInt(3);
                if(taken<=0||days<1||days>365)continue;
                long start=Instant.ofEpochMilli(taken).atZone(
                    ZoneId.systemDefault()).toLocalDate().toEpochDay();
                if(today-start<days)continue;
                if(prefs.getLong("storage_home_notice_"+itemId,-1L)==today)continue;
                waiting.add(name);
                waitingIds.add(itemId);
            }
        }
        if(waiting.isEmpty())return;
        LegacyCompat.ensureChannel(mgr,CHANNEL,"Rzeczy do odłożenia",
            NotificationManager.IMPORTANCE_DEFAULT,null,true,true,false);
        Intent open=new Intent(context,MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,REQUEST,open,
            PendingIntent.FLAG_UPDATE_CURRENT|LegacyCompat.immutableFlag());
        String description=waiting.size()==1
            ?waiting.get(0)+" nadal nie wróciła na swoje miejsce."
            :waiting.size()+" rzeczy czekają na odłożenie, m.in. "
                +waiting.get(0)+(waiting.size()>1?", "+waiting.get(1):"");
        Notification notice=LegacyCompat.notificationBuilder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • Odłóż rzeczy na miejsce")
            .setContentText(description)
            .setContentIntent(content)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        mgr.notify(NOTICE,notice);
        SharedPreferences.Editor remembered=prefs.edit();
        for(Long id:waitingIds)remembered.putLong("storage_home_notice_"+id,today);
        remembered.apply();
        DiagnosticLog.event("STORAGE_HOME_REMINDER",
            "overdue="+waiting.size());
    }
}
