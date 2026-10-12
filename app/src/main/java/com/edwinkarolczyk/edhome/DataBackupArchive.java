package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Zweryfikowana kopia ZIP EDHOME. Prywatny PayCheck pozostaje osobnym eksportem. */
final class DataBackupArchive {
    static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 768L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 1024L * 1024;
    private static final String FORMAT = "edhome-backup-archive";
    private static final int FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final String DATA = "data.json";
    private static final String MANIFEST = "manifest.json";
    private static final String STORAGE_PREFIX = "media/storage-originals/";
    private static final String LEGACY_THUMB_PREFIX = "media/storage-thumbnails/";
    private static final String TILE_PREFIX = "media/tile-icons/";
    private static final String BUDGET_ATTACHMENT_PREFIX =
        "media/paycheck-budget-attachments/";

    static final class Created {
        final File file;
        final int storageOriginals;
        final int legacyThumbnails;
        final int tileImages;
        final int paycheckAttachments;
        Created(File file, int storageOriginals, int legacyThumbnails,
                int tileImages, int paycheckAttachments) {
            this.file = file;
            this.storageOriginals = storageOriginals;
            this.legacyThumbnails = legacyThumbnails;
            this.tileImages = tileImages;
            this.paycheckAttachments = paycheckAttachments;
        }
    }

    static final class Restored {
        final int storageOriginals;
        final int legacyThumbnails;
        final int regeneratedThumbnails;
        final int tileImages;
        final int paycheckAttachments;
        Restored(int storageOriginals, int legacyThumbnails,
                int regeneratedThumbnails, int tileImages,
                int paycheckAttachments) {
            this.storageOriginals = storageOriginals;
            this.legacyThumbnails = legacyThumbnails;
            this.regeneratedThumbnails = regeneratedThumbnails;
            this.tileImages = tileImages;
            this.paycheckAttachments = paycheckAttachments;
        }
    }

    private static final class Source {
        final String path;
        final byte[] bytes;
        final File file;
        final String role;
        Source(String path, byte[] bytes, File file, String role) {
            this.path = path;
            this.bytes = bytes;
            this.file = file;
            this.role = role;
        }
    }

    private static final class DigestInfo {
        final long size;
        final String sha256;
        DigestInfo(long size, String sha256) { this.size = size; this.sha256 = sha256; }
    }

    private static final class Inspection {
        final String json;
        final List<String> paths;
        final int storageCount;
        final int legacyThumbCount;
        final int tileCount;
        final int paycheckAttachmentCount;
        Inspection(String json, List<String> paths, int storageCount,
                int legacyThumbCount, int tileCount,
                int paycheckAttachmentCount) {
            this.json = json;
            this.paths = paths;
            this.storageCount = storageCount;
            this.legacyThumbCount = legacyThumbCount;
            this.tileCount = tileCount;
            this.paycheckAttachmentCount = paycheckAttachmentCount;
        }
    }

    private static final class DirectorySwap {
        final File active;
        final File previous;
        final boolean hadActive;
        DirectorySwap(File active, File previous, boolean hadActive) {
            this.active = active;
            this.previous = previous;
            this.hadActive = hadActive;
        }
    }

    private DataBackupArchive() { }

    static Created create(Context context, SQLiteDatabase database,
            SharedPreferences prefs) throws Exception {
        return createInternal(context, database, prefs, true);
    }

    // Eksport v1 do starszych instalacji, gdy potrzebny jest format zgodny z nimi.
    static Created createLegacy(Context context, SQLiteDatabase database,
            SharedPreferences prefs) throws Exception {
        return createInternal(context, database, prefs, false);
    }

    private static Created createInternal(Context context, SQLiteDatabase database,
            SharedPreferences prefs, boolean modular) throws Exception {
        StorageOriginals.prune(context, database);
        List<File> originalFiles = StorageOriginals.liveFiles(context, database);
        Set<Long> originalIds = new HashSet<>();
        for (File image : originalFiles) {
            String name = image.getName();
            originalIds.add(Long.parseLong(name.substring(0, name.length() - 4)));
        }

        Set<Long> validStorageIds = new HashSet<>();
        try (Cursor cursor = database.rawQuery("SELECT id FROM storage_items", null)) {
            while (cursor.moveToNext()) validStorageIds.add(cursor.getLong(0));
        }

        List<Source> sources = new ArrayList<>();
        Set<Long> omittedThumbnailIds = new HashSet<>();
        int legacyThumbnails = 0;
        for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(StorageThumbs.PREFIX)
                    || !(entry.getValue() instanceof String)) continue;
            long id;
            try {
                id = Long.parseLong(key.substring(StorageThumbs.PREFIX.length()));
            } catch (NumberFormatException invalid) {
                continue;
            }
            if (!validStorageIds.contains(id)) continue;
            String encoded = (String) entry.getValue();
            if (encoded.length() > StorageThumbs.MAX_BASE64_CHARS)
                throw new IllegalStateException("Nieprawidłowa miniatura magazynu.");
            byte[] jpeg;
            try {
                jpeg = Base64.decode(encoded, Base64.NO_WRAP);
            } catch (Exception invalid) {
                throw new IllegalStateException("Nieprawidłowe kodowanie miniatury.", invalid);
            }
            if (jpeg.length < 4 || jpeg.length > StorageThumbs.MAX_JPEG_BYTES
                    || (jpeg[0] & 255) != 255 || (jpeg[1] & 255) != 216)
                throw new IllegalStateException("Nieprawidłowa miniatura magazynu.");
            omittedThumbnailIds.add(id);
            if (!originalIds.contains(id)) {
                sources.add(new Source(LEGACY_THUMB_PREFIX + id + ".jpg",
                    jpeg, null, "storage-thumbnail"));
                legacyThumbnails++;
            }
        }
        if (legacyThumbnails > 200)
            throw new IllegalStateException("Za dużo miniaturek magazynu.");

        byte[] data = DataBackup.exportJson(database, prefs, omittedThumbnailIds)
            .getBytes(StandardCharsets.UTF_8);
        sources.add(0, new Source(DATA, data, null, "database"));
        if (modular) {
            for (Map.Entry<String,byte[]> part :
                    BackupModuleSnapshot.split(new String(data, StandardCharsets.UTF_8))
                    .entrySet()) {
                sources.add(new Source(part.getKey(), part.getValue(), null, "module"));
            }
        }

        int originals = 0;
        for (File image : originalFiles) {
            sources.add(new Source(STORAGE_PREFIX + image.getName(),
                null, image, "storage-original"));
            originals++;
        }

        int tileImages = 0;
        File tileFolder = new File(context.getFilesDir(), "edhome-custom-tile-icons");
        File[] icons = tileFolder.listFiles();
        if (icons != null) {
            List<File> ordered = new ArrayList<>();
            Collections.addAll(ordered, icons);
            Collections.sort(ordered, (a, b) -> a.getName().compareTo(b.getName()));
            for (File icon : ordered) {
                String name = icon.getName();
                if (!icon.isFile() || !name.endsWith(".png")) continue;
                String id = name.substring(0, name.length() - 4);
                if (!HomeTileCatalog.validTileId(id) || icon.length() <= 0
                        || icon.length() > 8L * 1024 * 1024) continue;
                sources.add(new Source(TILE_PREFIX + name, null, icon, "tile-image"));
                tileImages++;
            }
        }

        int paycheckAttachments = 0;
        for (File attachment :
                PaycheckBudgetAttachmentStore.liveFiles(context,prefs)) {
            sources.add(new Source(
                BUDGET_ATTACHMENT_PREFIX + attachment.getName(),
                null,attachment,"paycheck-budget-attachment"));
            paycheckAttachments++;
        }

        JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("formatVersion", modular ? FORMAT_VERSION : LEGACY_FORMAT_VERSION);
        manifest.put("createdAt", System.currentTimeMillis());
        manifest.put("sourceVersion", BuildConfig.VERSION_NAME);
        JSONArray files = new JSONArray();
        for (Source source : sources) {
            DigestInfo digest = digest(source);
            JSONObject entry = new JSONObject();
            entry.put("path", source.path);
            entry.put("role", source.role);
            entry.put("category", sourceCategory(source.path));
            entry.put("bytes", digest.size);
            entry.put("sha256", digest.sha256);
            files.put(entry);
        }
        manifest.put("files", files);
        // Indeks jest również dostępny w starym ZIP v1 bez dodatkowych plików.
        manifest.put("categoryIndexVersion", 1);
        manifest.put("categories", BackupModuleSnapshot.index(
            new String(data, StandardCharsets.UTF_8)));
        manifest.put("databaseVersion", new JSONObject(
            new String(data, StandardCharsets.UTF_8)).getInt("databaseVersion"));
        byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);

        File archive = new File(context.getCacheDir(),
            "EDHOME-backup-" + System.currentTimeMillis() + ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                byte[] buffer = new byte[8192];
                for (Source source : sources) {
                    zip.putNextEntry(new ZipEntry(source.path));
                    if (source.bytes != null) {
                        zip.write(source.bytes);
                    } else {
                        try (InputStream input = new FileInputStream(source.file)) {
                            int count;
                            while ((count = input.read(buffer)) != -1)
                                zip.write(buffer, 0, count);
                        }
                    }
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry(MANIFEST));
                zip.write(manifestBytes);
                zip.closeEntry();
            }
            if (archive.length() <= 0 || archive.length() > MAX_ARCHIVE_BYTES)
                throw new IllegalStateException("Kopia ZIP ma nieprawidłowy rozmiar.");
            inspect(archive);
            return new Created(archive, originals, legacyThumbnails,
                tileImages, paycheckAttachments);
        } catch (Exception error) {
            archive.delete();
            throw error;
        }
    }

    static void writeToDocument(Context context, Uri destination, File archive)
            throws Exception {
        try (InputStream input = new FileInputStream(archive);
             OutputStream output = context.getContentResolver()
                 .openOutputStream(destination, "wt")) {
            if (output == null) throw new IllegalStateException("Brak dostępu do pliku.");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
    }

    static boolean verifyDocument(Context context, Uri destination, File archive)
            throws Exception {
        long expectedLength = archive.length();
        String expectedHash = digestFile(archive).sha256;
        MessageDigest actual = MessageDigest.getInstance("SHA-256");
        long length = 0;
        try (InputStream input = context.getContentResolver().openInputStream(destination)) {
            if (input == null) return false;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                length += count;
                if (length > expectedLength) return false;
                actual.update(buffer, 0, count);
            }
        }
        return length == expectedLength && expectedHash.equals(hex(actual.digest()));
    }

    static File copyFromDocument(Context context, Uri source) throws Exception {
        File local = new File(context.getCacheDir(),
            "edhome-backup-import-" + System.nanoTime() + ".bin");
        long total = 0;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(local)) {
                if (input == null) throw new IllegalArgumentException("Nie można odczytać kopii.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ARCHIVE_BYTES)
                        throw new IllegalArgumentException("Kopia przekracza 512 MB.");
                    output.write(buffer, 0, count);
                }
            }
            if (total < 2) throw new IllegalArgumentException("Kopia jest pusta.");
            return local;
        } catch (Exception error) {
            local.delete();
            throw error;
        }
    }

    static boolean isArchive(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            int a = input.read(), b = input.read(), c = input.read(), d = input.read();
            return a == 'P' && b == 'K' && c == 3 && d == 4;
        }
    }

    static String readLegacyJson(File file) throws Exception {
        if (file.length() <= 0 || file.length() > DataBackup.MAX_BYTES)
            throw new IllegalArgumentException("Stara kopia JSON przekracza limit 8 MB.");
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static Restored restore(Context context, SQLiteDatabase database,
            SharedPreferences prefs, File archive) throws Exception {
        Inspection inspection = inspect(archive);
        String suffix = Long.toString(System.nanoTime());
        File stageRoot = new File(context.getFilesDir(), ".edhome-restore-" + suffix);
        File stageStorage = new File(stageRoot, StorageOriginals.DIRECTORY);
        File stageTiles = new File(stageRoot, "edhome-custom-tile-icons");
        File stageBudget = new File(stageRoot,
            PaycheckBudgetAttachmentStore.DIRECTORY);
        deleteTree(stageRoot);
        if (!stageStorage.mkdirs() || !stageTiles.mkdirs()
                || !stageBudget.mkdirs())
            throw new IllegalStateException("Nie przygotowano przywracania plików.");

        DirectorySwap storageSwap = null;
        DirectorySwap tileSwap = null;
        DirectorySwap budgetSwap = null;
        try {
            extractMedia(archive, inspection.paths,
                stageStorage, stageTiles, stageBudget);
            storageSwap = installDirectory(
                StorageOriginals.folder(context), stageStorage, suffix);
            tileSwap = installDirectory(
                new File(context.getFilesDir(), "edhome-custom-tile-icons"),
                stageTiles, suffix);
            budgetSwap = installDirectory(
                PaycheckBudgetAttachmentStore.folder(context),
                stageBudget,suffix);
            try {
                DataBackup.restoreJson(database, prefs, inspection.json,
                    readArchivedThumbnails(archive, inspection.paths));
            } catch (Exception dataError) {
                rollback(budgetSwap);
                budgetSwap = null;
                rollback(tileSwap);
                tileSwap = null;
                rollback(storageSwap);
                storageSwap = null;
                throw dataError;
            }
            forgetPrevious(storageSwap);
            storageSwap = null;
            forgetPrevious(tileSwap);
            tileSwap = null;
            forgetPrevious(budgetSwap);
            budgetSwap = null;
            StorageOriginals.prune(context, database);
            int regenerated = 0;
            try {
                regenerated = StorageOriginals.regenerateThumbnails(
                    context, prefs, database);
            } catch (Exception ignored) {
                // data.json zawiera zweryfikowane miniatury jako zgodność dla starszych danych.
            }
            PaycheckBudgetAttachmentStore.prune(context,prefs);
            return new Restored(
                inspection.storageCount, inspection.legacyThumbCount,
                regenerated, inspection.tileCount,
                inspection.paycheckAttachmentCount);
        } finally {
            if (budgetSwap != null) rollback(budgetSwap);
            if (tileSwap != null) rollback(tileSwap);
            if (storageSwap != null) rollback(storageSwap);
            deleteTree(stageRoot);
        }
    }

    /**
     * P0: import wybranych modułów, z trwałą kopią bezpieczeństwa przed zmianą.
     * Wywoływać tylko w pojedynczym wątku obsługi kopii (nie na wątku UI).
     * Przeciążenie bez checkpointa istnieje wyłącznie dla pełnego importu.
     */
    static Restored restoreSelected(Context context, SQLiteDatabase database,
            SharedPreferences prefs, File archive, Set<String> selected) throws Exception {
        Inspection inspection = inspect(archive);
        if (selected == null || selected.isEmpty())
            throw new IllegalArgumentException("Nie wybrano kategorii przywracania.");

        String before = DataBackup.exportJson(database, prefs);
        String merged = BackupSelectiveMerge.merge(before, inspection.json, selected);
        File checkpoint = recoveryCheckpoint(context, database, prefs);

        String suffix = Long.toString(System.nanoTime());
        File stageRoot = new File(context.getFilesDir(), ".edhome-selected-" + suffix);
        File stageStorage = new File(stageRoot, StorageOriginals.DIRECTORY);
        File stageTiles = new File(stageRoot, "edhome-custom-tile-icons");
        File stageBudget = new File(stageRoot, PaycheckBudgetAttachmentStore.DIRECTORY);
        DirectorySwap storageSwap = null;
        DirectorySwap tileSwap = null;
        DirectorySwap budgetSwap = null;
        boolean restoreAttempted = false;
        try {
            if (!stageStorage.mkdirs() || !stageTiles.mkdirs() || !stageBudget.mkdirs())
                throw new IllegalStateException("Nie utworzono katalogów przywracania.");
            extractMedia(archive, inspection.paths, stageStorage, stageTiles, stageBudget);
            if (selected.contains("storage"))
                storageSwap = installDirectory(StorageOriginals.folder(context),
                    stageStorage, suffix);
            if (selected.contains("settings"))
                tileSwap = installDirectory(
                    new File(context.getFilesDir(), "edhome-custom-tile-icons"),
                    stageTiles, suffix);
            if (selected.contains("budget"))
                budgetSwap = installDirectory(
                    PaycheckBudgetAttachmentStore.folder(context),
                    stageBudget, suffix);

            Map<Long,String> photos = selected.contains("storage")
                ? readArchivedThumbnails(archive, inspection.paths)
                : Collections.emptyMap();
            restoreAttempted = true;
            DataBackup.restoreJson(database, prefs, merged, photos, false);
            // Nie wolno zmienić ani jednego wpisu w niewybranych modułach.
            verifyUnselected(before, DataBackup.exportJson(database, prefs), selected);

            // Generowanie miniatur jest odzyskiwalne z oryginałów. Ewentualny
            // błąd nie może uruchamiać cofania PO usunięciu starych katalogów.
            if (selected.contains("storage")) {
                try {
                    StorageOriginals.regenerateThumbnails(context, prefs, database);
                } catch (Exception thumbnailError) {
                    // Oryginały pozostają dostępne; miniatury można odtworzyć później.
                }
            }
            forgetPrevious(storageSwap);
            storageSwap = null;
            forgetPrevious(tileSwap);
            tileSwap = null;
            forgetPrevious(budgetSwap);
            budgetSwap = null;
            return new Restored(
                selected.contains("storage") ? inspection.storageCount : 0,
                selected.contains("storage") ? inspection.legacyThumbCount : 0,
                0, selected.contains("settings") ? inspection.tileCount : 0,
                selected.contains("budget") ? inspection.paycheckAttachmentCount : 0);
        } catch (Exception failure) {
            rollback(budgetSwap);
            budgetSwap = null;
            rollback(tileSwap);
            tileSwap = null;
            rollback(storageSwap);
            storageSwap = null;
            if (restoreAttempted) {
                try {
                    DataBackup.restoreJson(database, prefs, before,
                        Collections.emptyMap(), false);
                } catch (Exception undoError) {
                    try {
                        restoreExact(context, database, prefs, checkpoint);
                    } catch (Exception recoveryError) {
                        failure.addSuppressed(undoError);
                        failure.addSuppressed(recoveryError);
                        throw new IllegalStateException(
                            "Przywracanie nie powiodło się i automatyczne cofnięcie "
                            + "również. Kopia bezpieczeństwa: "
                            + checkpoint.getAbsolutePath(), failure);
                    }
                }
            }
            throw failure;
        } finally {
            if (budgetSwap != null) rollback(budgetSwap);
            if (tileSwap != null) rollback(tileSwap);
            if (storageSwap != null) rollback(storageSwap);
            deleteTree(stageRoot);
            // Kopii checkpoint NIGDY nie kasujemy po przywróceniu.
        }
    }

    /** Cofnięcie do pełnej migawki bez dopisywania historii z nowszego stanu. */
    static Restored restoreExact(Context context, SQLiteDatabase database,
            SharedPreferences prefs, File checkpoint) throws Exception {
        Inspection inspection = inspect(checkpoint);
        String suffix = Long.toString(System.nanoTime());
        File stageRoot = new File(context.getFilesDir(), ".edhome-undo-" + suffix);
        File storage = new File(stageRoot, StorageOriginals.DIRECTORY);
        File tiles = new File(stageRoot, "edhome-custom-tile-icons");
        File budget = new File(stageRoot, PaycheckBudgetAttachmentStore.DIRECTORY);
        DirectorySwap a = null, b = null, c = null;
        try {
            if (!storage.mkdirs() || !tiles.mkdirs() || !budget.mkdirs())
                throw new IllegalStateException("Nie przygotowano cofnięcia danych.");
            extractMedia(checkpoint, inspection.paths, storage, tiles, budget);
            a = installDirectory(StorageOriginals.folder(context), storage, suffix);
            b = installDirectory(new File(context.getFilesDir(),
                "edhome-custom-tile-icons"), tiles, suffix);
            c = installDirectory(PaycheckBudgetAttachmentStore.folder(context),
                budget, suffix);
            DataBackup.restoreJson(database, prefs, inspection.json,
                readArchivedThumbnails(checkpoint, inspection.paths), false);
            forgetPrevious(a);
            a = null;
            forgetPrevious(b);
            b = null;
            forgetPrevious(c);
            c = null;
            return new Restored(inspection.storageCount, inspection.legacyThumbCount,
                0, inspection.tileCount, inspection.paycheckAttachmentCount);
        } finally {
            if (c != null) rollback(c);
            if (b != null) rollback(b);
            if (a != null) rollback(a);
            deleteTree(stageRoot);
        }
    }

    /**
     * Kopia zostaje w prywatnym katalogu aplikacji po zamknięciu programu.
     * W razie awarii pełną paczkę można odzyskać przed kolejnym importem.
     */
    static File recoveryCheckpoint(Context context, SQLiteDatabase database,
            SharedPreferences prefs) throws Exception {
        File folder = new File(context.getFilesDir(), "edhome-recovery");
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IllegalStateException("Nie utworzono folderu kopii ratunkowych.");
        Created snapshot = create(context, database, prefs);
        String name = "EDHOME-przed-importem-" + System.currentTimeMillis()
            + "-" + Long.toString(System.nanoTime()) + ".zip";
        File completed = new File(folder, name);
        File temporary = new File(folder, name + ".tmp");
        try {
            try (InputStream input = new FileInputStream(snapshot.file);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0)
                    if (count > 0) output.write(buffer, 0, count);
                output.getFD().sync();
            }
            if (!temporary.renameTo(completed))
                throw new IllegalStateException("Nie utrwalono kopii ratunkowej.");
            // Akceptuj checkpoint dopiero po powtórnej walidacji sum SHA-256.
            inspect(completed);
            return completed;
        } catch (Exception error) {
            temporary.delete();
            completed.delete();
            throw error;
        } finally {
            snapshot.file.delete();
        }
    }

    /** Kontrola niezmienności kategorii, których użytkownik nie wybrał. */
    private static void verifyUnselected(String before, String after,
            Set<String> selected) throws Exception {
        JSONObject left = new JSONObject(before).getJSONObject("tables");
        JSONObject right = new JSONObject(after).getJSONObject("tables");
        for (java.util.Iterator<String> names = left.keys(); names.hasNext();) {
            String table = names.next();
            if (selected.contains(BackupModuleSnapshot.categoryForTable(table)))
                continue;
            JSONArray prior = left.getJSONArray(table);
            JSONArray next = right.getJSONArray(table);
            if (prior.length() != next.length())
                throw new IllegalStateException(
                    "Zmieniła się niewybrana kategoria: " + table);
            for (int i = 0; i < prior.length(); i++) {
                JSONObject a = prior.getJSONObject(i);
                JSONObject b = next.getJSONObject(i);
                if (a.length() != b.length())
                    throw new IllegalStateException(
                        "Niezgodność niewybranego modułu: " + table);
                for (java.util.Iterator<String> columns = a.keys();
                        columns.hasNext();) {
                    String column = columns.next();
                    if (!b.has(column) || !String.valueOf(a.get(column))
                            .equals(String.valueOf(b.get(column))))
                        throw new IllegalStateException(
                            "Zmieniono niewybraną pozycję: " + table + "/" + column);
                }
            }
        }
    }

    private static Inspection inspect(File archive) throws Exception {
        if (!archive.isFile() || archive.length() <= 0
                || archive.length() > MAX_ARCHIVE_BYTES)
            throw new IllegalArgumentException("Nieprawidłowa kopia ZIP.");
        try (ZipFile zip = new ZipFile(archive)) {
            Set<String> actual = new HashSet<>();
            long extractedTotal = 0;
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !safeEntryName(name) || !actual.add(name))
                    throw new IllegalArgumentException("Nieprawidłowa struktura kopii ZIP.");
                long size = entry.getSize();
                if (size > 0) {
                    extractedTotal += size;
                    if (extractedTotal > MAX_EXTRACTED_BYTES)
                        throw new IllegalArgumentException("Kopia po rozpakowaniu jest za duża.");
                }
            }
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null)
                throw new IllegalArgumentException("Brak manifestu kopii.");
            String manifestText = new String(
                readEntry(zip, manifestEntry, MAX_MANIFEST_BYTES),
                StandardCharsets.UTF_8);
            JSONObject manifest = new JSONObject(manifestText);
            int archiveVersion = manifest.optInt("formatVersion", -1);
            if (!FORMAT.equals(manifest.optString("format"))
                    || (archiveVersion != LEGACY_FORMAT_VERSION
                        && archiveVersion != FORMAT_VERSION))
                throw new IllegalArgumentException("Nieobsługiwany format kopii ZIP.");
            JSONArray files = manifest.getJSONArray("files");
            Map<String,String> moduleHashes = new HashMap<>();
            Set<String> expected = new HashSet<>();
            expected.add(MANIFEST);
            String json = null;
            int storage = 0, legacyThumbs = 0, tiles = 0,
                paycheckAttachments = 0;
            long verifiedPayloadBytes = 0;
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                String path = item.getString("path");
                long bytes = item.getLong("bytes");
                String hash = item.getString("sha256");
                if (!allowedPayload(path) || !expected.add(path)
                        || bytes < 0 || hash.length() != 64
                        || (archiveVersion == FORMAT_VERSION
                            && !sourceCategory(path).equals(item.optString("category"))))
                    throw new IllegalArgumentException("Nieprawidłowy manifest kopii.");
                ZipEntry entry = zip.getEntry(path);
                if (entry == null)
                    throw new IllegalArgumentException("Brakuje pliku w kopii: " + path);
                DigestInfo digest = digestEntry(zip, entry);
                verifiedPayloadBytes += digest.size;
                if (verifiedPayloadBytes > MAX_EXTRACTED_BYTES)
                    throw new IllegalArgumentException("Kopia po rozpakowaniu jest za duża.");
                if (digest.size != bytes || !digest.sha256.equalsIgnoreCase(hash))
                    throw new IllegalArgumentException("Niezgodna suma kontrolna: " + path);
                if (DATA.equals(path)) {
                    if (bytes > DataBackup.MAX_BYTES)
                        throw new IllegalArgumentException("data.json przekracza limit.");
                    json = new String(readEntry(zip, entry, DataBackup.MAX_BYTES),
                        StandardCharsets.UTF_8);
                } else if (BackupModuleSnapshot.allowedPath(path)) {
                    if (archiveVersion != FORMAT_VERSION)
                        throw new IllegalArgumentException(
                            "Stary format kopii nie obsługuje modułów.");
                    moduleHashes.put(path, digest.sha256);
                } else if (path.startsWith(STORAGE_PREFIX)) storage++;
                else if (path.startsWith(LEGACY_THUMB_PREFIX)) legacyThumbs++;
                else if (path.startsWith(TILE_PREFIX)) tiles++;
                else if (path.startsWith(BUDGET_ATTACHMENT_PREFIX))
                    paycheckAttachments++;
            }
            if (!actual.equals(expected) || json == null)
                throw new IllegalArgumentException("Kopia ZIP zawiera brakujące lub obce pliki.");
            validatePayloadNamesAgainstJson(json, expected);
            if (archiveVersion == FORMAT_VERSION) {
                if (manifest.optInt("databaseVersion", -1) !=
                        new JSONObject(json).getInt("databaseVersion"))
                    throw new IllegalArgumentException(
                        "Niezgodna wersja bazy w manifeście.");
                validateCategoryIndex(manifest.getJSONObject("categories"), json);
                // Każdy moduł musi odtwarzać dokładnie ten sam stan co data.json.
                // Sam hash w manifeście nie wystarcza: wykryj podmieniony moduł
                // nawet wtedy, gdy ktoś podmienił też hash w manifeście.
                Map<String,byte[]> canonical = BackupModuleSnapshot.split(json);
                if (!moduleHashes.keySet().equals(canonical.keySet()))
                    throw new IllegalArgumentException(
                        "Brakujące lub nadmiarowe moduły kopii.");
                for (Map.Entry<String,byte[]> section : canonical.entrySet()) {
                    DigestInfo calculated = digest(new Source(section.getKey(),
                        section.getValue(), null, "module"));
                    if (!calculated.sha256.equalsIgnoreCase(
                            moduleHashes.get(section.getKey())))
                        throw new IllegalArgumentException(
                            "Niezgodność danych modułu: " + section.getKey());
                }
            }
            return new Inspection(json,new ArrayList<>(expected),
                storage,legacyThumbs,tiles,paycheckAttachments);
        }
    }

    /** Nie ufamy samemu indeksowi z manifestu bez porównania z migawką. */
    private static void validateCategoryIndex(JSONObject provided, String json)
            throws Exception {
        JSONObject calculated = BackupModuleSnapshot.index(json);
        if (provided.length() != calculated.length())
            throw new IllegalArgumentException("Indeks modułów nie zgadza się z kopią.");
        for (java.util.Iterator<String> categories = calculated.keys();
                categories.hasNext();) {
            String category = categories.next();
            JSONObject expected = calculated.getJSONObject(category);
            JSONObject actual = provided.optJSONObject(category);
            if (actual == null || actual.length() != expected.length())
                throw new IllegalArgumentException("Nieprawidłowy indeks: " + category);
            for (java.util.Iterator<String> tables = expected.keys();
                    tables.hasNext();) {
                String table = tables.next();
                if (actual.optInt(table, -1) != expected.getInt(table))
                    throw new IllegalArgumentException(
                        "Niezgodna liczba pozycji: " + category + "/" + table);
            }
        }
    }

    /** Podgląd kategorii dla przyszłego wyboru przywracania; niczego nie zmienia. */
    static JSONObject inspectCategories(File archive) throws Exception {
        Inspection inspection = inspect(archive);
        return BackupModuleSnapshot.index(inspection.json);
    }

    private static void validatePayloadNamesAgainstJson(String json, Set<String> paths)
            throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"edhome-data-backup".equals(root.optString("format"))
                || root.optInt("formatVersion", -1) != 1)
            throw new IllegalArgumentException("Nieprawidłowy data.json.");
        Set<Long> ids = new HashSet<>();
        JSONArray rows = root.getJSONObject("tables").getJSONArray("storage_items");
        for (int i = 0; i < rows.length(); i++) ids.add(rows.getJSONObject(i).getLong("id"));
        for (String path : paths) {
            if (path.startsWith(STORAGE_PREFIX)) {
                String name = path.substring(STORAGE_PREFIX.length());
                long id = Long.parseLong(name.substring(0, name.length() - 4));
                if (!ids.contains(id))
                    throw new IllegalArgumentException("Zdjęcie wskazuje brakującą rzecz.");
            } else if (path.startsWith(LEGACY_THUMB_PREFIX)) {
                String name = path.substring(LEGACY_THUMB_PREFIX.length());
                long id = Long.parseLong(name.substring(0, name.length() - 4));
                if (!ids.contains(id))
                    throw new IllegalArgumentException(
                        "Miniatura wskazuje brakującą rzecz.");
            } else if (path.startsWith(TILE_PREFIX)) {
                String name = path.substring(TILE_PREFIX.length());
                String id = name.substring(0, name.length() - 4);
                if (!HomeTileCatalog.validTileId(id))
                    throw new IllegalArgumentException("Nieprawidłowa ikona kafelka.");
            } else if (path.startsWith(BUDGET_ATTACHMENT_PREFIX)) {
                String name=path.substring(BUDGET_ATTACHMENT_PREFIX.length());
                String raw=root.getJSONObject("settings").optString(
                    "paycheckBudgetAttachments","[]");
                JSONArray attachments=new JSONArray(raw);
                boolean found=false;
                for (int i=0;i<attachments.length();i++)
                    if (name.equals(attachments.getJSONObject(i)
                            .optString("fileName"))) {
                        found=true;break;
                    }
                if (!found)
                    throw new IllegalArgumentException(
                        "Załącznik wskazuje brakującą pozycję metadanych.");
            }
        }
        String rawAttachments=root.getJSONObject("settings").optString(
            "paycheckBudgetAttachments","[]");
        JSONArray attachmentMeta=new JSONArray(rawAttachments);
        for (int i=0;i<attachmentMeta.length();i++) {
            String name=attachmentMeta.getJSONObject(i).getString("fileName");
            if (!paths.contains(BUDGET_ATTACHMENT_PREFIX+name))
                throw new IllegalArgumentException(
                    "Brakuje pliku załącznika budżetu.");
        }
    }

    private static boolean safeEntryName(String name) {
        return name != null && !name.isEmpty() && !name.startsWith("/")
            && name.indexOf('\\') < 0 && !name.contains("../") && !name.contains("/..")
            && name.length() <= 180;
    }

    private static String sourceCategory(String path) {
        if (DATA.equals(path)) return "full";
        if (BackupModuleSnapshot.allowedPath(path))
            return path.substring(BackupModuleSnapshot.PREFIX.length(),
                path.length() - 5);
        if (path.startsWith(STORAGE_PREFIX)
                || path.startsWith(LEGACY_THUMB_PREFIX)) return "storage";
        if (path.startsWith(BUDGET_ATTACHMENT_PREFIX)) return "budget";
        if (path.startsWith(TILE_PREFIX)) return "settings";
        return "other";
    }

    private static boolean allowedPayload(String path) {
        if (DATA.equals(path) || BackupModuleSnapshot.allowedPath(path)) return true;
        if (path.matches("media/storage-originals/[1-9][0-9]*\\.img")) return true;
        if (path.matches("media/storage-thumbnails/[1-9][0-9]*\\.jpg")) return true;
        if (path.startsWith(TILE_PREFIX) && path.endsWith(".png")) {
            String id = path.substring(TILE_PREFIX.length(), path.length() - 4);
            return HomeTileCatalog.validTileId(id);
        }
        if (path.matches(
                "media/paycheck-budget-attachments/[0-9a-fA-F-]{36}\\.(?:pdf|img)"))
            return true;
        return false;
    }

    private static Map<Long,String> readArchivedThumbnails(
            File archive, List<String> paths) throws Exception {
        Map<Long,String> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(archive)) {
            for (String path : paths) {
                if (!path.startsWith(LEGACY_THUMB_PREFIX)) continue;
                String name = path.substring(LEGACY_THUMB_PREFIX.length());
                long id = Long.parseLong(name.substring(0, name.length() - 4));
                if (result.containsKey(id))
                    throw new IllegalArgumentException("Duplikat miniatury w kopii.");
                ZipEntry entry = zip.getEntry(path);
                byte[] jpeg = readEntry(zip, entry, StorageThumbs.MAX_JPEG_BYTES);
                if (jpeg.length < 4
                        || (jpeg[0] & 255) != 255 || (jpeg[1] & 255) != 216)
                    throw new IllegalArgumentException(
                        "Nieprawidłowa miniatura archiwalna.");
                result.put(id, Base64.encodeToString(jpeg, Base64.NO_WRAP));
            }
        }
        return result;
    }

    private static void extractMedia(File archive, List<String> paths,
            File stageStorage, File stageTiles, File stageBudget)
            throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            byte[] buffer = new byte[8192];
            for (String path : paths) {
                if (!(path.startsWith(STORAGE_PREFIX)
                        || path.startsWith(TILE_PREFIX)
                        || path.startsWith(BUDGET_ATTACHMENT_PREFIX)))
                    continue;
                ZipEntry entry = zip.getEntry(path);
                File destination;
                if (path.startsWith(STORAGE_PREFIX))
                    destination = new File(stageStorage,
                        path.substring(STORAGE_PREFIX.length()));
                else if (path.startsWith(TILE_PREFIX))
                    destination = new File(stageTiles,
                        path.substring(TILE_PREFIX.length()));
                else
                    destination = new File(stageBudget,
                        path.substring(BUDGET_ATTACHMENT_PREFIX.length()));
                try (InputStream input = zip.getInputStream(entry);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    int count;
                    long written = 0;
                    while ((count = input.read(buffer)) != -1) {
                        written += count;
                        if (written > StorageOriginals.MAX_FILE_BYTES
                                && path.startsWith(STORAGE_PREFIX))
                            throw new IllegalArgumentException(
                                "Zdjęcie w kopii jest za duże.");
                        if (written > 8L * 1024 * 1024
                                && path.startsWith(TILE_PREFIX))
                            throw new IllegalArgumentException(
                                "Ikona w kopii jest za duża.");
                        if (written > PaycheckBudgetAttachmentStore.MAX_FILE_BYTES
                                && path.startsWith(BUDGET_ATTACHMENT_PREFIX))
                            throw new IllegalArgumentException(
                                "Załącznik budżetu w kopii jest za duży.");
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private static DirectorySwap installDirectory(
            File active, File stage, String suffix) throws Exception {
        File previous = new File(active.getParentFile(),
            "." + active.getName() + "-before-" + suffix);
        deleteTree(previous);
        boolean hadActive = active.exists();
        if (hadActive && !active.renameTo(previous))
            throw new IllegalStateException("Nie zabezpieczono bieżących plików.");
        if (!stage.renameTo(active)) {
            if (hadActive) previous.renameTo(active);
            throw new IllegalStateException("Nie zainstalowano plików z kopii.");
        }
        return new DirectorySwap(active, previous, hadActive);
    }

    private static void rollback(DirectorySwap swap) {
        if (swap == null) return;
        deleteTree(swap.active);
        if (swap.hadActive && swap.previous.exists())
            swap.previous.renameTo(swap.active);
        else deleteTree(swap.previous);
    }

    private static void forgetPrevious(DirectorySwap swap) {
        if (swap != null) deleteTree(swap.previous);
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry, long limit)
            throws Exception {
        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IllegalArgumentException("Plik w kopii jest za duży.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static DigestInfo digest(Source source) throws Exception {
        if (source.bytes != null) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(source.bytes);
            return new DigestInfo(source.bytes.length, hex(md.digest()));
        }
        return digestFile(source.file);
    }

    private static DigestInfo digestFile(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                md.update(buffer, 0, count);
            }
        }
        return new DigestInfo(size, hex(md.digest()));
    }

    private static DigestInfo digestEntry(ZipFile zip, ZipEntry entry) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > MAX_EXTRACTED_BYTES)
                    throw new IllegalArgumentException("Plik w kopii jest za duży.");
                md.update(buffer, 0, count);
            }
        }
        return new DigestInfo(size, hex(md.digest()));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
