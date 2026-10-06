package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Oryginalne zdjęcia Magazynu zapisane prywatnie, aby pełna kopia mogła je odtworzyć. */
final class StorageOriginals {
    static final String DIRECTORY = "edhome-storage-originals";
    static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_DIMENSION = 20000;

    private StorageOriginals() { }

    static File folder(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    static File file(Context context, long itemId) {
        if (itemId <= 0) throw new IllegalArgumentException("Nieprawidłowy identyfikator rzeczy.");
        return new File(folder(context), itemId + ".img");
    }

    static void save(Context context, long itemId, Uri source) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalArgumentException("Nie można odczytać zdjęcia.");
            save(context, itemId, input);
        }
    }

    static void save(Context context, long itemId, File source) throws Exception {
        if (source == null || !source.isFile())
            throw new IllegalArgumentException("Brak oryginalnego zdjęcia.");
        try (InputStream input = new FileInputStream(source)) {
            save(context, itemId, input);
        }
    }

    private static void save(Context context, long itemId, InputStream input)
            throws Exception {
        File destination = file(context, itemId);
        File directory = destination.getParentFile();
        if (!directory.exists() && !directory.mkdirs())
            throw new IllegalStateException("Nie utworzono katalogu zdjęć.");
        File temporary = new File(directory, itemId + ".tmp");
        long size = 0;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_FILE_BYTES)
                        throw new IllegalArgumentException(
                            "Oryginalne zdjęcie przekracza 32 MB.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (size < 4) throw new IllegalArgumentException("Zdjęcie jest puste.");
            validateImage(temporary);
            if (destination.exists() && !destination.delete())
                throw new IllegalStateException("Nie można zastąpić starego zdjęcia.");
            if (!temporary.renameTo(destination))
                throw new IllegalStateException("Nie zapisano oryginalnego zdjęcia.");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void validateImage(File image) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1
                || bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION)
            throw new IllegalArgumentException("Nieobsługiwany format zdjęcia.");
    }

    static void delete(Context context, long itemId) {
        if (itemId <= 0) return;
        File image = file(context, itemId);
        if (image.exists()) image.delete();
    }

    static int prune(Context context, SQLiteDatabase database) {
        Set<Long> live = new HashSet<>();
        try (Cursor cursor = database.rawQuery("SELECT id FROM storage_items", null)) {
            while (cursor.moveToNext()) live.add(cursor.getLong(0));
        }
        File directory = folder(context);
        File[] files = directory.listFiles();
        if (files == null) return 0;
        int removed = 0;
        for (File image : files) {
            String name = image.getName();
            long id = -1;
            if (name.matches("[1-9][0-9]*\\.img")) {
                try { id = Long.parseLong(name.substring(0, name.length() - 4)); }
                catch (NumberFormatException ignored) { id = -1; }
            }
            if (id > 0 && live.contains(id)) continue;
            if (image.delete()) removed++;
        }
        return removed;
    }

    static List<File> liveFiles(Context context, SQLiteDatabase database) {
        List<File> result = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM storage_items ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                File image = file(context, cursor.getLong(0));
                if (image.isFile() && image.length() >= 4
                        && image.length() <= MAX_FILE_BYTES) result.add(image);
            }
        }
        return result;
    }

    private static String thumbnail(File image) throws Exception {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), probe);
        if (probe.outWidth < 1 || probe.outHeight < 1
                || probe.outWidth > MAX_DIMENSION || probe.outHeight > MAX_DIMENSION)
            throw new IllegalArgumentException("Nieprawidłowe zdjęcie w kopii.");
        BitmapFactory.Options sample = new BitmapFactory.Options();
        sample.inSampleSize = 1;
        while (probe.outWidth / sample.inSampleSize > StorageThumbs.MAX_EDGE_PX * 2
                || probe.outHeight / sample.inSampleSize > StorageThumbs.MAX_EDGE_PX * 2)
            sample.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), sample);
        if (bitmap == null) throw new IllegalArgumentException("Nie odczytano zdjęcia.");
        try { return StorageThumbs.compress(bitmap); }
        finally { bitmap.recycle(); }
    }

    /** Dla starszej rzeczy bez oryginału pozostaje miniatura zapisana w JSON. */
    static int regenerateThumbnails(Context context, SharedPreferences prefs,
            SQLiteDatabase database) {
        SharedPreferences.Editor editor = prefs.edit();
        int regenerated = 0;
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM storage_items ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                File image = file(context, id);
                if (!image.isFile()) continue;
                try {
                    editor.putString(StorageThumbs.key(id), thumbnail(image));
                    regenerated++;
                } catch (Exception ignored) {
                    // data.json zawiera zweryfikowane miniatury jako zgodność dla starszych danych.
                }
            }
        }
        if (regenerated > 0 && !editor.commit())
            throw new IllegalStateException("Nie zapisano odtworzonych miniaturek.");
        return regenerated;
    }
}
