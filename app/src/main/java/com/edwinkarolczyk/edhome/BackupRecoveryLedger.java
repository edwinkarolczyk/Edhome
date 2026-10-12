package com.edwinkarolczyk.edhome;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Trwały znacznik ostatniego zakończonego sukcesem przywracania.
 * Nie zależy od Androida, więc można testować go wykonywalnym Java smoke.
 *
 * Samo wykonanie checkpointu NIE aktualizuje wskaźnika. Nieudany import
 * pozostawia ostatnią potwierdzoną wersję do cofnięcia.
 */
final class BackupRecoveryLedger {
    private static final String MARKER = "last-successful-restore.txt";
    private static final String PREFIX = "EDHOME-przed-importem-";

    private BackupRecoveryLedger() { }

    static File lastCompleted(File folder) {
        File marker = new File(folder, MARKER);
        if (!marker.isFile() || marker.length() <= 0 || marker.length() > 256)
            return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(marker), StandardCharsets.UTF_8))) {
            String filename = reader.readLine();
            if (!validName(filename) || reader.readLine() != null)
                return null;
            File checkpoint = new File(folder, filename);
            if (!checkpoint.isFile() || checkpoint.length() <= 0
                    || !checkpoint.getCanonicalFile().getParentFile().equals(
                        folder.getCanonicalFile()))
                return null;
            return checkpoint;
        } catch (Exception invalid) {
            return null;
        }
    }

    static void recordCompleted(File folder, File checkpoint) throws IOException {
        if (!folder.isDirectory() || checkpoint == null
                || !checkpoint.isFile() || checkpoint.length() <= 0
                || !validName(checkpoint.getName())
                || !checkpoint.getCanonicalFile().getParentFile().equals(
                    folder.getCanonicalFile()))
            throw new IOException("Nieprawidłowy punkt cofnięcia importu.");

        File marker = new File(folder, MARKER);
        File temporary = new File(folder, ".last-successful-restore-"
            + Long.toString(System.nanoTime()) + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write((checkpoint.getName() + "\n").getBytes(
                    StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temporary.renameTo(marker))
                throw new IOException("Nie utrwalono punktu cofnięcia importu.");
        } finally {
            temporary.delete();
        }
    }

    private static boolean validName(String value) {
        return value != null && value.startsWith(PREFIX)
            && value.endsWith(".zip") && value.length() <= 180
            && value.indexOf('/') < 0 && value.indexOf('\\') < 0
            && !value.contains("..");
    }
}
