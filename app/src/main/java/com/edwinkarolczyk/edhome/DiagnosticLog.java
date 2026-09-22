package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Beta-only, privacy-safe diagnostic log.
 *
 * Do not log PINs, product names, finance or notification contents, auth tokens
 * or unfiltered exception messages. Android system logs are not collected.
 */
public final class DiagnosticLog {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_EXPORT_CHARS = 160000;
    private static final int MAX_CHAT_CHARS = 20000;
    private static File current;
    private static File previous;

    private DiagnosticLog() { }

    public static boolean enabled() {
        return BuildConfig.DIAGNOSTICS_ENABLED;
    }

    public static void init(Context context) {
        if (!enabled()) return;
        synchronized (LOCK) {
            File dir = new File(context.getApplicationContext().getFilesDir(), "diagnostics");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e("EDHOME_DIAG", "Unable to create beta diagnostic folder");
                return;
            }
            current = new File(dir, "edhome-beta.log");
            previous = new File(dir, "edhome-beta.previous.log");
            append("APP_START", "version=" + BuildConfig.VERSION_NAME
                + " sdk=" + Build.VERSION.SDK_INT);
        }
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            error("UNCAUGHT_" + safe(thread.getName()), exception);
            if (previousHandler != null) previousHandler.uncaughtException(thread, exception);
        });
    }

    private static String safe(String event) {
        if (event == null) return "unknown";
        return event.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    public static void event(String event) {
        if (!enabled()) return;
        append(safe(event), "");
    }

    public static void event(String event, String safeMetadata) {
        if (!enabled()) return;
        // Only pass code-owned metadata such as counts, screen IDs or booleans.
        append(safe(event), safeMetadata == null ? "" : safeMetadata);
    }

    public static void error(String event, Throwable problem) {
        if (!enabled()) return;
        // The exception message is deliberately excluded; it may contain user data.
        StringBuilder trace = new StringBuilder();
        trace.append("type=").append(problem == null ? "unknown" :
            problem.getClass().getSimpleName());
        if (problem != null) {
            StackTraceElement[] frames = problem.getStackTrace();
            for (int i = 0; i < Math.min(12, frames.length); i++) {
                trace.append(" | ").append(frames[i].getClassName())
                    .append("#").append(frames[i].getMethodName())
                    .append(":").append(frames[i].getLineNumber());
            }
        }
        append("ERROR_" + safe(event), trace.toString());
    }

    private static void append(String event, String info) {
        if (!enabled()) return;
        synchronized (LOCK) {
            if (current == null) return;
            try {
                long now = System.currentTimeMillis();
                String when = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    Locale.ROOT).format(new Date(now));
                String line = when + " | " + event + (info.isEmpty() ? "" : " | " + info) + "\n";
                byte[] data = line.getBytes(StandardCharsets.UTF_8);
                if (current.length() + data.length > MAX_BYTES) {
                    if (previous.exists() && !previous.delete()) {
                        Log.w("EDHOME_DIAG", "Unable to rotate previous log");
                    }
                    if (!current.renameTo(previous)) {
                        try (FileOutputStream reset = new FileOutputStream(current, false)) {
                            reset.write(("Log rotation at " + when + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                try (FileOutputStream out = new FileOutputStream(current, true)) {
                    out.write(data);
                }
            } catch (Exception ignored) {
                Log.e("EDHOME_DIAG", "Unable to write beta diagnostic file");
            }
        }
    }

    /** Legacy bounded preview; the full .txt export uses readFullText(). */
    public static String readText() {
        String content = readFullText();
        if (content.length() > MAX_EXPORT_CHARS)
            return "[Older diagnostic lines omitted]\n"
                + content.substring(content.length() - MAX_EXPORT_CHARS);
        return content;
    }

    /** Full retained history: current and previous 1 MB segments, not a truncated preview. */
    public static String readFullText() {
        if (!enabled()) return "";
        synchronized (LOCK) {
            if (current == null) return "";
            StringBuilder out = new StringBuilder();
            readInto(previous, out);
            readInto(current, out);
            return out.toString();
        }
    }

    /** Paste-safe newest complete lines, limited to 5k / 12k / 20k characters. */
    public static String readForChat(int requestedChars) {
        int limit = Math.max(1000, Math.min(MAX_CHAT_CHARS, requestedChars));
        String content = readText();
        if (content.length() <= limit) return content;
        String header = "[EDHOME: starsze wpisy pominięte w kopii do czatu; "
            + "pełna historia w eksporcie .txt]\n";
        String tail = content.substring(content.length() - (limit - header.length()));
        int firstNewline = tail.indexOf('\n');
        if (firstNewline >= 0) tail = tail.substring(firstNewline + 1);
        return header + tail;
    }

    private static void readInto(File file, StringBuilder out) {
        if (file == null || !file.exists()) return;
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            int count;
            while ((count = input.read(buffer)) != -1)
                bytes.write(buffer, 0, count);
            out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            out.append("\n[Unable to read one diagnostic segment]\n");
        }
    }

    public static String exportName() {
        return "EDHOME-beta-diagnostyka.txt";
    }
}
