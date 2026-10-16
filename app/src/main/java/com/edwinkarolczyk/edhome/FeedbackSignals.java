package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Krótkie sygnały UI. Nie odtwarza dźwięku w trybie cichym. */
final class FeedbackSignals {
    private static final String PREFS = "edhome_beta_prefs";
    static final String SOUNDS_ENABLED = "feedback_sounds_enabled";
    static final String VIBRATIONS_ENABLED = "feedback_vibrations_enabled";

    private FeedbackSignals() { }

    static void success(Context context) {
        signal(context, ToneGenerator.TONE_PROP_ACK, 65, 35);
    }

    static void warning(Context context) {
        signal(context, ToneGenerator.TONE_PROP_NACK, 130, 90);
    }

    private static void signal(Context context, int tone, int durationMs,
            int vibrationMs) {
        SharedPreferences prefs = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE);
        AudioManager audio = (AudioManager) context.getSystemService(
            Context.AUDIO_SERVICE);
        if (audio == null || audio.getRingerMode()
                == AudioManager.RINGER_MODE_SILENT) return;
        if (prefs.getBoolean(SOUNDS_ENABLED, true)
                && audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            ToneGenerator generator = null;
            try {
                generator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35);
                generator.startTone(tone, durationMs);
                // ToneGenerator.stopTone() and release() are intentionally
                // deferred to avoid cutting off the tone immediately.
                final ToneGenerator pending = generator;
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        pending.stopTone();
                        pending.release();
                    }, durationMs + 40L);
                generator = null;
            } catch (RuntimeException ignored) {
                if (generator != null) generator.release();
            }
        }
        if (prefs.getBoolean(VIBRATIONS_ENABLED, true)) {
            Vibrator vibrator = (Vibrator) context.getSystemService(
                Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(vibrationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(vibrationMs);
                }
            }
        }
    }
}
