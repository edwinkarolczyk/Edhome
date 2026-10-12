package com.edwinkarolczyk.edhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Wykonywalny smoke: cofanie wskazuje ostatni SUKCES, nie najnowszy ZIP. */
public final class BackupRecoveryLedgerSmoke {
    private static void ok(boolean state, String why) {
        if (!state) throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("edhome-backup-ledger-");
        File folder = tmp.toFile();
        File initial = new File(folder, "EDHOME-przed-importem-100-1.zip");
        File failed = new File(folder, "EDHOME-przed-importem-200-2.zip");
        File next = new File(folder, "EDHOME-przed-importem-300-3.zip");
        Files.write(initial.toPath(), "pierwsza kopia".getBytes(StandardCharsets.UTF_8));
        Files.write(failed.toPath(), "nieudana proba".getBytes(StandardCharsets.UTF_8));
        Files.write(next.toPath(), "udane cofniecie".getBytes(StandardCharsets.UTF_8));

        ok(BackupRecoveryLedger.lastCompleted(folder) == null,
            "Brak punktu po instalacji");
        BackupRecoveryLedger.recordCompleted(folder, initial);
        ok(initial.equals(BackupRecoveryLedger.lastCompleted(folder)),
            "Pierwszy udany import musi utworzyc punkt cofania");

        // Drugi ZIP istnieje, lecz drugi import się nie udał.
        // Sam fakt utworzenia pliku nie może zmienić celu przycisku Cofnij.
        ok(initial.equals(BackupRecoveryLedger.lastCompleted(folder)),
            "Nieudany import zaslonil ostatnie powodzenie");

        BackupRecoveryLedger.recordCompleted(folder, next);
        ok(next.equals(BackupRecoveryLedger.lastCompleted(folder)),
            "Udane cofniecie nie ustawia nowego punktu ponowienia");
        ok(next.equals(BackupRecoveryLedger.lastCompleted(folder)),
            "Zapis musi pozostac po ponownym odczycie (restart aplikacji)");

        File outside = new File(tmp.getParent().toFile(),
            "EDHOME-przed-importem-zewnetrzny.zip");
        Files.write(outside.toPath(), "obcy plik".getBytes(StandardCharsets.UTF_8));
        try {
            BackupRecoveryLedger.recordCompleted(folder, outside);
            throw new AssertionError("Nie odrzucono kopii spoza katalogu aplikacji");
        } catch (java.io.IOException expected) { }
        ok(next.equals(BackupRecoveryLedger.lastCompleted(folder)),
            "Odrzucony zewnetrzny ZIP zmienil zapisany punkt");

        File marker = new File(folder, "last-successful-restore.txt");
        Files.write(marker.toPath(), "../EDHOME-przed-importem-zewnetrzny.zip\n"
            .getBytes(StandardCharsets.UTF_8));
        ok(BackupRecoveryLedger.lastCompleted(folder) == null,
            "Path traversal w znaczniku powinien byc odrzucony");
        Files.write(marker.toPath(), "EDHOME-przed-importem-300-3.zip\nextra\n"
            .getBytes(StandardCharsets.UTF_8));
        ok(BackupRecoveryLedger.lastCompleted(folder) == null,
            "Wielo-liniowy wskaznik nie jest poprawny");

        BackupRecoveryLedger.recordCompleted(folder, initial);
        Files.delete(initial.toPath());
        ok(BackupRecoveryLedger.lastCompleted(folder) == null,
            "Wskazanie usunietej kopii powinno byc niewazne");

        Files.deleteIfExists(outside.toPath());
        Files.deleteIfExists(marker.toPath());
        Files.deleteIfExists(failed.toPath());
        Files.deleteIfExists(next.toPath());
        Files.delete(folder.toPath());
        System.out.println("EDHOME backup: completed-restore marker, failed import, "
            + "undo redo, and path traversal PASS");
    }
}
