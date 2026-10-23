package com.edwinkarolczyk.edhome;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Small API-level bridge so EDHOME can run on Android 5.1 / Fire OS 5 (API 22). */
final class LegacyCompat {
    private LegacyCompat() { }

    static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    static boolean notificationsEnabled(NotificationManager manager) {
        if (manager == null) return false;
        return Build.VERSION.SDK_INT < 24 || Api24.notificationsEnabled(manager);
    }

    static void ensureChannel(NotificationManager manager, String id, String name,
            int importance, String description, boolean privateVisibility,
            boolean showBadge, boolean silent) {
        if (manager == null || Build.VERSION.SDK_INT < 26) return;
        Api26.ensureChannel(manager, id, name, importance, description,
            privateVisibility, showBadge, silent);
    }

    static void ensureAudibleChannel(NotificationManager manager,String id,
            String name) {
        if(manager!=null&&Build.VERSION.SDK_INT>=26)
            Api26.ensureAudibleChannel(manager,id,name);
    }

    static boolean channelEnabled(NotificationManager manager, String id) {
        return manager != null
            && (Build.VERSION.SDK_INT < 26 || Api26.channelEnabled(manager, id));
    }

    static Notification.Builder notificationBuilder(Context context, String channelId) {
        return Build.VERSION.SDK_INT >= 26
            ? Api26.notificationBuilder(context, channelId)
            : new Notification.Builder(context);
    }

    static void setAndAllowWhileIdle(AlarmManager manager, int type,
            long triggerAtMillis, PendingIntent operation) {
        if (Build.VERSION.SDK_INT >= 23)
            Api23.setAndAllowWhileIdle(manager, type, triggerAtMillis, operation);
        else
            manager.set(type, triggerAtMillis, operation);
    }

    static void startForegroundService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) Api26.startForegroundService(context, intent);
        else context.startService(intent);
    }

    @android.annotation.TargetApi(23)
    private static final class Api23 {
        static void setAndAllowWhileIdle(AlarmManager manager, int type,
                long triggerAtMillis, PendingIntent operation) {
            manager.setAndAllowWhileIdle(type, triggerAtMillis, operation);
        }
    }

    @android.annotation.TargetApi(24)
    private static final class Api24 {
        static boolean notificationsEnabled(NotificationManager manager) {
            return manager.areNotificationsEnabled();
        }
    }

    @android.annotation.TargetApi(26)
    private static final class Api26 {
        static void ensureChannel(NotificationManager manager, String id, String name,
                int importance, String description, boolean privateVisibility,
                boolean showBadge, boolean silent) {
            NotificationChannel channel = new NotificationChannel(id, name, importance);
            if (description != null && !description.isEmpty())
                channel.setDescription(description);
            if (privateVisibility)
                channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.setShowBadge(showBadge);
            if (silent) {
                channel.setSound(null, null);
                channel.enableVibration(false);
            }
            manager.createNotificationChannel(channel);
        }

        static void ensureAudibleChannel(NotificationManager manager,String id,
                String name) {
            NotificationChannel channel=new NotificationChannel(id,name,
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0,180,120,180});
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(channel);
        }

        static boolean channelEnabled(NotificationManager manager, String id) {
            NotificationChannel channel = manager.getNotificationChannel(id);
            return channel == null
                || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }

        static Notification.Builder notificationBuilder(Context context,
                String channelId) {
            return new Notification.Builder(context, channelId);
        }

        static void startForegroundService(Context context, Intent intent) {
            context.startForegroundService(intent);
        }
    }
}
