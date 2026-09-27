package com.edwinkarolczyk.edhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps the local EDHOME Desktop endpoint alive independently from MainActivity.
 * Beta only in the manifest. No cloud, no Internet exposure: LanSyncServer still
 * accepts only site-local/loopback peers and requires the pairing token.
 */
public final class LanSyncService extends Service {
    private static final String CHANNEL = "edhome-desktop-lan";
    private static final int NOTIFICATION_ID = 1700042;
    private static final long WATCHDOG_MS = 5000L;
    private static volatile boolean ENDPOINT_RUNNING;

    private MainActivity.LocalDb db;
    private LanSyncServer server;
    private SharedPreferences prefs;
    private Handler watchdogHandler;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            LanSyncServer current = server;
            if (current == null) {
                ENDPOINT_RUNNING = false;
            } else {
                if (!current.isRunning()) {
                    DiagnosticLog.event("DESKTOP_SYNC_WATCHDOG_RESTART");
                    current.start();
                }
                ENDPOINT_RUNNING = current.isRunning();
            }
            updateNotificationStatus();
            if (watchdogHandler != null)
                watchdogHandler.postDelayed(this, WATCHDOG_MS);
        }
    };

    static boolean endpointRunning() {
        return ENDPOINT_RUNNING;
    }

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
            .setContentText("Serwer LAN uruchamia się • czeka na PC")
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
        ENDPOINT_RUNNING = server.isRunning();
        watchdogHandler = new Handler(Looper.getMainLooper());
        watchdogHandler.postDelayed(watchdog, 1200L);
        DiagnosticLog.event("DESKTOP_SYNC_SERVICE_STARTED");
    }

    private void updateNotificationStatus() {
        NotificationManager manager = (NotificationManager)
            getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        String state;
        if (!ENDPOINT_RUNNING)
            state = "Serwer LAN uruchamia się • czeka na PC";
        else if (LanSyncServer.isSyncing())
            state = "Synchronizacja z PC…";
        else if (LanSyncServer.hasRecentClient())
            state = "Połączono z EDHOME Desktop";
        else
            state = "Serwer LAN działa • czeka na PC";

        Intent open = new Intent(this, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME Desktop")
            .setContentText(state)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build();
        manager.notify(NOTIFICATION_ID, notification);
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
        try {
            android.database.sqlite.SQLiteDatabase database =
                db.getWritableDatabase();
            // Refresh the sidecar first. Unlike PRAGMA data_version this also
            // observes writes performed through this service's own connection.
            SyncRecordStore.ensureAll(database);
            try (android.database.Cursor cursor = database.rawQuery(
                    "SELECT COALESCE(SUM(revision),0) FROM sync_records", null)) {
                return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
            }
        } catch (Exception error) {
            DiagnosticLog.error("DESKTOP_SYNC_REVISION", error);
            return -1L;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (server != null) {
            server.start();
            ENDPOINT_RUNNING = server.isRunning();
        }
        if (watchdogHandler != null) {
            watchdogHandler.removeCallbacks(watchdog);
            watchdogHandler.postDelayed(watchdog, 500L);
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        ENDPOINT_RUNNING = false;
        if (watchdogHandler != null) {
            watchdogHandler.removeCallbacks(watchdog);
            watchdogHandler = null;
        }
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
