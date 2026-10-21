package com.edhome.desktop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Privacy-safe rolling log for EDHOME Desktop Beta. */
final class DesktopDiagnosticLog {
    private static final Object LOCK = new Object();
    private static boolean initialized;
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final int MAX_COPY_CHARS = 20000;
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".edhome", "logs");
    private static final Path CURRENT = DIR.resolve("edhome-desktop.log");
    private static final Path PREVIOUS = DIR.resolve("edhome-desktop.previous.log");
    private static final Path PHONE = DIR.resolve("edhome-android-last.txt");
    private static final Path PHONE_ARCHIVE = DIR.resolve("android-archive");
    private static final int MAX_PHONE_BYTES = 3 * 1024 * 1024;

    private DesktopDiagnosticLog() { }

    static void init(String version) {
        synchronized (LOCK) {
            if (initialized) return;
            try {
                Files.createDirectories(DIR);
                // Rejestruje również awarie w wątkach roboczych i EDT Swing.
                Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, problem) -> {
                    error("UNCAUGHT_" + safe(thread.getName()), problem);
                    if (previous != null) previous.uncaughtException(thread, problem);
                });
                initialized = true;
                event("APP_START", "version=" + safe(version));
            } catch (Exception ignored) {
                System.err.println("EDHOME_DIAGNOSTYKA_START_FAILED");
            }
        }
    }

    static void event(String event) { event(event, ""); }

    static void event(String event, String safeMetadata) {
        append(safe(event), safeMetadata == null ? "" : safeMetadata);
    }

    static void error(String event, Throwable problem) {
        Throwable root = rootCause(problem);
        StringBuilder info = new StringBuilder("type=")
            .append(problem == null ? "unknown" : problem.getClass().getSimpleName());
        if (root != null && root != problem) {
            info.append(" root=").append(root.getClass().getSimpleName());
        }
        // Komunikat wyjątku może zawierać nazwy, kwoty lub ścieżki użytkownika.
        // Zostawiamy klasę błędu i ramki stosu, ale nie zapisujemy jego treści.
        if (root != null) {
            StackTraceElement[] frames = root.getStackTrace();
            for (int i = 0; i < Math.min(10, frames.length); i++) {
                info.append(" | ").append(frames[i].getClassName())
                    .append("#").append(frames[i].getMethodName())
                    .append(":").append(frames[i].getLineNumber());
            }
        }
        append("ERROR_" + safe(event), info.toString());
    }

    private static Throwable rootCause(Throwable problem) {
        Throwable current = problem;
        for (int i = 0; current != null && current.getCause() != null
                && current.getCause() != current && i < 12; i++) {
            current = current.getCause();
        }
        return current;
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

    /** Ostatnie logi telefonu, przesłane tokenizowanym API Huba po LAN. */
    static boolean hasPhoneDiagnostics() {
        synchronized (LOCK) { return Files.isRegularFile(PHONE); }
    }

    static String readPhoneDiagnostics() throws IOException {
        synchronized (LOCK) {
            if (!Files.isRegularFile(PHONE))
                throw new IOException("Telefon nie przekazał jeszcze logów do Desktopu.");
            return Files.readString(PHONE, StandardCharsets.UTF_8);
        }
    }

    /** Archive on PC first; return a receipt only when both files are durable. */
    static String receivePhoneDiagnostics(String deviceId,String androidVersion,
            String body) throws IOException {
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,96}"))
            throw new IOException("Nieprawidłowy identyfikator telefonu.");
        if (body == null || body.isBlank()
                || body.getBytes(StandardCharsets.UTF_8).length > MAX_PHONE_BYTES)
            throw new IOException("Pusty lub zbyt duży raport telefonu.");
        String receipt = sha256(body);
        String header = "EDHOME Android Beta "
            + safe(androidVersion) + " • odebrano "
            + Instant.now() + "\n"
            + "Urządzenie: " + safe(deviceId) + "\n"
            + "Identyfikator transferu SHA-256: " + receipt + "\n\n";
        synchronized (LOCK) {
            Files.createDirectories(PHONE_ARCHIVE);
            Path archived = PHONE_ARCHIVE.resolve(deviceId+"-"+receipt+".txt");
            // Unique content hash makes retry after a lost response idempotent.
            if (!Files.isRegularFile(archived)) {
                writeAtomically(archived,header+body);
            }
            // Exports from Settings always show the latest successful transfer.
            writeAtomically(PHONE,header+body);
        }
        event("HUB_PHONE_DIAGNOSTICS_ARCHIVED",
            "bytes="+body.getBytes(StandardCharsets.UTF_8).length);
        return receipt;
    }

    /**
     * Export all acknowledged phone log transfers, not only edhome-android-last.txt.
     * The archive is append-only and each transfer is named by its SHA-256 receipt.
     * This does not communicate with the phone and never deletes archived data.
     * Streaming avoids loading potentially years of diagnostic history into memory.
     */
    static int exportPhoneHistory(Path destination) throws IOException {
        java.util.ArrayList<Path> files=new java.util.ArrayList<>();
        synchronized (LOCK) {
            if (Files.isDirectory(PHONE_ARCHIVE)) {
                try (java.util.stream.Stream<Path> stream=Files.list(PHONE_ARCHIVE)) {
                    stream.filter(p->Files.isRegularFile(p,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .filter(p->p.getFileName().toString().matches(
                            "[A-Za-z0-9_-]{1,96}-[a-f0-9]{64}\\.txt"))
                        .forEach(files::add);
                }
            }
            if (files.isEmpty() && Files.isRegularFile(PHONE))
                files.add(PHONE); // Support old installations without an archive.
            files.sort((a,b)->{
                try {
                    int byTime=Files.getLastModifiedTime(a)
                        .compareTo(Files.getLastModifiedTime(b));
                    return byTime!=0?byTime:a.getFileName().toString()
                        .compareTo(b.getFileName().toString());
                } catch(IOException error) {
                    return a.getFileName().toString()
                        .compareTo(b.getFileName().toString());
                }
            });
        }
        if(files.isEmpty())return 0;
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(destination.toAbsolutePath().getParent(),
            "edhome-android-export-", ".tmp");
        try {
            try(java.io.BufferedWriter out=Files.newBufferedWriter(temp,
                    StandardCharsets.UTF_8)) {
                out.write("EDHOME • pełna historia logów Android z archiwum Desktop\n");
                out.write("Liczba potwierdzonych transferów: "+files.size()+"\n");
                for(int i=0;i<files.size();i++) {
                    out.write("\n========== TRANSFER "+(i+1)+" / "+files.size()
                        +" ==========\n");
                    try(java.io.BufferedReader input=Files.newBufferedReader(
                            files.get(i),StandardCharsets.UTF_8)) {
                        input.transferTo(out);
                    }
                    out.write("\n");
                }
            }
            try {
                Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch(java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return files.size();
    }

    static Path phoneArchiveDirectory() { return PHONE_ARCHIVE; }

    private static void writeAtomically(Path path,String text) throws IOException {
        Path temp = Files.createTempFile(path.getParent(),"edhome-diag-", ".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp,StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String sha256(String text) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IOException("Brak SHA-256, nie potwierdzono transferu.",unavailable);
        }
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
