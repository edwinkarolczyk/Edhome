package com.edwinkarolczyk.edhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

/**
 * Keeps the local EDHOME Desktop endpoint alive independently from MainActivity.
 * Beta only in the manifest. No cloud, no Internet exposure: LanSyncServer still
 * accepts only site-local/loopback peers and requires the pairing token.
 */
public final class LanSyncService extends Service {
    private static final String CHANNEL = "edhome-desktop-lan";
    private static final int NOTIFICATION_ID = 1700042;

    private MainActivity.LocalDb db;
    private LanSyncServer server;
    private SharedPreferences prefs;

    static void ensureStarted(Context context) {
        if (!BetaUpdater.isBeta()) return;
        try {
            Intent intent = new Intent(context, LanSyncService.class);
            context.startForegroundService(intent);
        } catch (RuntimeException error) {
            DiagnosticLog.error("DESKTOP_SYNC_SERVICE_START", error);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        DiagnosticLog.init(this);
        prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        db = new MainActivity.LocalDb(this);
        db.getWritableDatabase();

        NotificationManager manager = (NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "EDHOME Desktop • sieć lokalna",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                "Utrzymuje lokalne połączenie EDHOME Android ↔ Desktop.");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME Desktop")
            .setContentText("Połączenie lokalne Wi‑Fi gotowe")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build();
        startForeground(NOTIFICATION_ID, notification);

        String token = LanSyncServer.ensureToken(prefs);
        server = new LanSyncServer(token,
            () -> DataBackup.exportJson(db.getReadableDatabase(), prefs),
            json -> {
                DataBackup.restoreJson(db.getWritableDatabase(), prefs, json);
                afterDataChange();
            },
            this::databaseRevision,
            this::applyRecordPatch);
        server.start();
        DiagnosticLog.event("DESKTOP_SYNC_SERVICE_STARTED");
    }

    private String applyRecordPatch(String incoming) throws Exception {
        String result = SyncRecordStore.applyPatch(
            db.getWritableDatabase(), incoming);
        afterDataChange();
        return result;
    }

    private void afterDataChange() {
        ReminderReceiver.schedule(this);
        DeviceTimerReceiver.scheduleAll(this);
        sendBroadcast(new Intent(
            "com.edwinkarolczyk.edhome.DESKTOP_DATA_CHANGED")
            .setPackage(getPackageName()));
    }

    private long databaseRevision() {
        if (db == null) return -1L;
        try (android.database.Cursor cursor = db.getReadableDatabase()
                .rawQuery("PRAGMA data_version", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        } catch (Exception error) {
            DiagnosticLog.error("DESKTOP_SYNC_DATA_VERSION", error);
            return -1L;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (server != null) server.start();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (db != null) {
            try { db.close(); } catch (Exception ignored) { }
            db = null;
        }
        DiagnosticLog.event("DESKTOP_SYNC_SERVICE_STOPPED");
        super.onDestroy();
    }
}
