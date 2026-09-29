package com.edhome.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Privacy-safe rolling log for EDHOME Desktop Beta. */
final class DesktopDiagnosticLog {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final int MAX_COPY_CHARS = 20000;
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".edhome", "logs");
    private static final Path CURRENT = DIR.resolve("edhome-desktop.log");
    private static final Path PREVIOUS = DIR.resolve("edhome-desktop.previous.log");

    private DesktopDiagnosticLog() { }

    static void init(String version) {
        try {
            Files.createDirectories(DIR);
            event("APP_START", "version=" + safe(version));
        } catch (Exception ignored) { }
    }

    static void event(String event) { event(event, ""); }

    static void event(String event, String safeMetadata) {
        append(safe(event), safeMetadata == null ? "" : safeMetadata);
    }

    static void error(String event, Throwable problem) {
        StringBuilder info = new StringBuilder("type=")
            .append(problem == null ? "unknown" : problem.getClass().getSimpleName());
        if (problem != null) {
            StackTraceElement[] frames = problem.getStackTrace();
            for (int i = 0; i < Math.min(10, frames.length); i++) {
                info.append(" | ").append(frames[i].getClassName())
                    .append("#").append(frames[i].getMethodName())
                    .append(":").append(frames[i].getLineNumber());
            }
        }
        append("ERROR_" + safe(event), info.toString());
    }

    static String readFullText() {
        synchronized (LOCK) {
            StringBuilder out = new StringBuilder();
            read(PREVIOUS, out);
            read(CURRENT, out);
            return out.toString();
        }
    }

    static String readForClipboard() {
        String text = readFullText();
        if (text.length() <= MAX_COPY_CHARS) return text;
        return "[EDHOME Desktop: starsze wpisy pominięte; pełna historia w TXT]\n"
            + text.substring(text.length() - MAX_COPY_CHARS);
    }

    static Path logDirectory() { return DIR; }

    private static void append(String event, String info) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(DIR);
                String line = Instant.now() + " | " + event
                    + (info.isBlank() ? "" : " | " + info) + "\n";
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                long size = Files.exists(CURRENT) ? Files.size(CURRENT) : 0L;
                if (size + bytes.length > MAX_BYTES) {
                    if (Files.exists(PREVIOUS)) Files.delete(PREVIOUS);
                    if (Files.exists(CURRENT))
                        Files.move(CURRENT, PREVIOUS, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.write(CURRENT, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) { }
        }
    }

    private static void read(Path path, StringBuilder out) {
        try {
            if (Files.exists(path))
                out.append(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            out.append("[Nie udało się odczytać jednego segmentu logu Desktop]\n");
        }
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.replaceAll("[^A-Za-z0-9_.=,:;\\- ]", "_");
    }
}
