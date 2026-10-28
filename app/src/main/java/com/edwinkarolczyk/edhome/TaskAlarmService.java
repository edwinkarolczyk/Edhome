package com.edwinkarolczyk.edhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Beta-only persistent ringing for a task with a selected reminder time. */
public final class TaskAlarmService extends Service {
    static final String ACTION_START = "com.edwinkarolczyk.edhome.START_TASK_ALARM";
    private static final String CHANNEL = "edhome-task-alarm-ringing-v1";
    private static final int NOTIFICATION_ID = 7138;
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopRinging();
            stopSelf();
            return START_NOT_STICKY;
        }
        long taskId = intent.getLongExtra("task_id", -1L);
        String title = intent.getStringExtra("task_title");
        if (title == null || title.trim().isEmpty()) title = "Czynność";
        createSilentChannel();
        startForeground(NOTIFICATION_ID, buildNotification(title, taskId));
        startRinging();
        DiagnosticLog.event("TASK_ALARM_RINGING_STARTED", "task=" + taskId);
        return START_NOT_STICKY;
    }

    private void createSilentChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
            "Aktywny alarm czynności", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String title, long id) {
        Intent stop = new Intent(this, ReminderReceiver.class)
            .setAction("com.edwinkarolczyk.edhome.DISMISS_TASK_ALERT")
            .putExtra("task_id", id);
        PendingIntent stopPending = PendingIntent.getBroadcast(this, 7139,
            stop, PendingIntent.FLAG_UPDATE_CURRENT | LegacyCompat.immutableFlag());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_edhome)
            .setContentTitle("EDHOME • ALARM")
            .setContentText(title + " — alarm dzwoni")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true).setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Wyłącz alarm", stopPending)
            .build();
    }

    private void startRinging() {
        stopRinging();
        try {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (sound != null) {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this, sound);
                player.setLooping(true);
                player.prepare();
                player.start();
            }
        } catch (Exception error) {
            DiagnosticLog.error("TASK_ALARM_AUDIO", error);
            if (player != null) {
                try { player.release(); } catch (Exception ignored) {}
                player = null;
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0, 700, 500, 700, 900};
            try {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else vibrator.vibrate(pattern, 0);
            } catch (Exception error) { DiagnosticLog.error("TASK_ALARM_VIBRATION", error); }
        }
    }

    private void stopRinging() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
            vibrator = null;
        }
        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    @Override public void onDestroy() { stopRinging(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}