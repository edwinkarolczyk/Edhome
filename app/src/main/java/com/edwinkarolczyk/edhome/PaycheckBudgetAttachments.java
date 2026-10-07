package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Jeden lokalny załącznik faktury na zobowiązanie i miesiąc. */
final class PaycheckBudgetAttachments {
    static final String DIRECTORY = "edhome-budget-attachments";
    static final long MAX_FILE_BYTES = 24L * 1024 * 1024;

    private PaycheckBudgetAttachments() { }

    static File folder(Context context) {
        File dir = new File(context.getFilesDir(),DIRECTORY);
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new IllegalStateException("Nie utworzono katalogu załączników.");
        return dir;
    }

    static File find(Context context, String itemId, YearMonth month) {
        if (!validItemId(itemId) || month == null) return null;
        String prefix = prefix(itemId,month);
        File[] files = folder(context).listFiles();
        if (files == null) return null;
        for (File file : files)
            if (file.isFile() && file.getName().startsWith(prefix)
                    && validName(file.getName()))
                return file;
        return null;
    }

    static File save(Context context, String itemId, YearMonth month, Uri source)
            throws Exception {
        if (!validItemId(itemId) || month == null || source == null)
            throw new IllegalArgumentException("Nieprawidłowy załącznik.");
        File dir = folder(context);
        File temporary = new File(dir,prefix(itemId,month) + ".tmp");
        if (temporary.exists()) temporary.delete();
        long total = 0L;
        try {
            try (InputStream input =
                    context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null)
                    throw new IllegalArgumentException("Nie można odczytać pliku.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count=input.read(buffer))!=-1) {
                    total += count;
                    if (total > MAX_FILE_BYTES)
                        throw new IllegalArgumentException(
                            "Załącznik przekracza 24 MB.");
                    output.write(buffer,0,count);
                }
            }
            if (total < 4)
                throw new IllegalArgumentException("Załącznik jest pusty.");
            String extension = detectExtension(temporary);
            File destination =
                new File(dir,prefix(itemId,month) + "." + extension);
            delete(context,itemId,month);
            if (!temporary.renameTo(destination))
                throw new IllegalStateException("Nie zapisano załącznika.");
            return destination;
        } catch(Exception error) {
            temporary.delete();
            throw error;
        }
    }

    static boolean delete(Context context, String itemId, YearMonth month) {
        if (!validItemId(itemId) || month == null) return false;
        File[] files = folder(context).listFiles();
        boolean removed = false;
        if (files != null) {
            String prefix = prefix(itemId,month);
            for (File file : files)
                if (file.isFile() && file.getName().startsWith(prefix)
                        && (validName(file.getName())
                            || file.getName().equals(prefix + ".tmp")))
                    removed |= file.delete();
        }
        return removed;
    }

    static Uri shareUri(Context context, File file) {
        if (file == null || !file.isFile() || !validName(file.getName()))
            throw new IllegalArgumentException("Brak załącznika.");
        return FileProvider.getUriForFile(context,
            BuildConfig.APPLICATION_ID + ".storage.files",file);
    }

    static String mime(File file) {
        if (file == null) return "application/octet-stream";
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".jpg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    static List<File> backupFiles(Context context) {
        File[] files = folder(context).listFiles();
        List<File> result = new ArrayList<>();
        if (files != null)
            for (File file : files)
                if (file.isFile() && validName(file.getName())
                        && file.length() > 0 && file.length() <= MAX_FILE_BYTES)
                    result.add(file);
        Collections.sort(result,(a,b)->a.getName().compareTo(b.getName()));
        return result;
    }

    static boolean validName(String name) {
        if (name == null) return false;
        return name.matches(
            "[0-9a-fA-F-]{36}__[0-9]{4}-[0-9]{2}\\.(?:pdf|jpg|png|webp)");
    }

    private static String prefix(String itemId, YearMonth month) {
        return itemId + "__" + month;
    }

    private static boolean validItemId(String itemId) {
        return itemId != null && itemId.matches("[0-9a-fA-F-]{36}");
    }

    private static String detectExtension(File file) throws Exception {
        byte[] head = new byte[12];
        int read;
        try (FileInputStream input = new FileInputStream(file)) {
            read = input.read(head);
        }
        if (read >= 5 && head[0]=='%' && head[1]=='P' && head[2]=='D'
                && head[3]=='F' && head[4]=='-') return "pdf";
        if (read >= 3 && (head[0]&255)==255 && (head[1]&255)==216
                && (head[2]&255)==255) return "jpg";
        if (read >= 8 && (head[0]&255)==137 && head[1]=='P'
                && head[2]=='N' && head[3]=='G'
                && (head[4]&255)==13 && (head[5]&255)==10
                && (head[6]&255)==26 && (head[7]&255)==10) return "png";
        if (read >= 12 && head[0]=='R' && head[1]=='I' && head[2]=='F'
                && head[3]=='F' && head[8]=='W' && head[9]=='E'
                && head[10]=='B' && head[11]=='P') return "webp";
        throw new IllegalArgumentException(
            "Obsługiwane są PDF, JPG, PNG i WEBP.");
    }
}
