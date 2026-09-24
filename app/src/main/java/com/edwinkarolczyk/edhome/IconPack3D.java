package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Optional, user-imported EDHOME AI 3D asset pack. Nothing touches the ledger or vault. */
final class IconPack3D {
    static final String PREFIX = "ai3d_";
    private static final String PACK = "EDHOME AI 3D";
    private static final int EXPECTED = 100;
    private static final long MAX_ARCHIVE = 12L * 1024 * 1024;
    private static final long MAX_ENTRY = 512L * 1024;
    private static final long MAX_EXTRACTED = 24L * 1024 * 1024;

    static final class Option {
        final String id;
        final String title;
        Option(String id, String title) { this.id = id; this.title = title; }
    }

    private IconPack3D() { }

    private static File folder(Context context) {
        return new File(context.getFilesDir(), "edhome-ai-3d-icons");
    }

    static boolean installed(Context context) {
        return new File(folder(context), "manifest.json").isFile();
    }

    static List<Option> options(Context context) {
        List<Option> out = new ArrayList<>();
        if (!installed(context)) return out;
        try {
            JSONObject manifest = readManifest(folder(context));
            JSONArray rows = manifest.getJSONArray("icons");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.getJSONObject(i);
                String id = item.getString("id");
                if (safeId(id))
                    out.add(new Option(PREFIX + id, "3D • " + item.getString("name")));
            }
        } catch (Exception invalid) {
            return new ArrayList<>();
        }
        return out;
    }

    static boolean known(Context context, String key) {
        if (key == null || !key.startsWith(PREFIX)) return false;
        for (Option option : options(context))
            if (key.equals(option.id)) return true;
        return false;
    }

    static Bitmap bitmap(Context context, String key) {
        if (!known(context, key)) return null;
        String id = key.substring(PREFIX.length());
        return BitmapFactory.decodeFile(
            new File(folder(context), id + ".webp").getAbsolutePath());
    }

    static String defaultFor(String target) {
        switch (target) {
            case "audit": return PREFIX + "checklist";
            case "timers": return PREFIX + "stopwatch";
            case "shopping": return PREFIX + "shopping";
            case "paycheck": return PREFIX + "paycheck";
            case "paycheck_private": return PREFIX + "paycheck_private";
            case "waste": return PREFIX + "waste";
            case "storage": return PREFIX + "storage";
            case "vehicles": return PREFIX + "vehicles";
            case "diagnostics": return PREFIX + "diagnostics";
            default: return PREFIX + target;
        }
    }

    private static boolean safeId(String id) {
        return id != null && id.matches("[a-z][a-z0-9_]{0,39}");
    }

    /** Bounded ZIP extraction, filename and digest validation, atomic pack replacement. */
    static int importArchive(Context context, Uri source) throws Exception {
        File parent = context.getFilesDir();
        File stage = new File(parent, "edhome-ai-3d-stage");
        File active = folder(context);
        File previous = new File(parent, "edhome-ai-3d-previous");
        delete(stage);
        if (!stage.mkdirs()) throw new IllegalStateException("Brak miejsca na paczkę ikon.");
        Set<String> files = new HashSet<>();
        long total = 0;
        try {
            try (InputStream raw = context.getContentResolver().openInputStream(source)) {
                if (raw == null) throw new IllegalArgumentException("Nie można otworzyć ZIP.");
                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory()) throw new IllegalArgumentException("Nieprawidłowy ZIP.");
                        String name = entry.getName();
                        String destination;
                        if ("manifest.json".equals(name)) destination = name;
                        else if (name.matches("icons/[a-z][a-z0-9_]{0,39}\\.webp"))
                            destination = name.substring(6);
                        else throw new IllegalArgumentException("Nieznany plik w paczce.");
                        if (!files.add(name) || files.size() > EXPECTED + 1)
                            throw new IllegalArgumentException("Powtórzony plik w ZIP.");
                        File output = new File(stage, destination);
                        long entrySize = 0;
                        try (FileOutputStream sink = new FileOutputStream(output)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = zip.read(buffer)) != -1) {
                                entrySize += length;
                                total += length;
                                if (entrySize > MAX_ENTRY || total > MAX_EXTRACTED)
                                    throw new IllegalArgumentException("Zbyt duża paczka ikon.");
                                sink.write(buffer, 0, length);
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }
            if (files.size() != EXPECTED + 1)
                throw new IllegalArgumentException("Paczka musi zawierać dokładnie 100 ikon.");
            JSONObject manifest = readManifest(stage);
            if (!PACK.equals(manifest.getString("pack"))
                    || manifest.getInt("schema") != 1
                    || manifest.getInt("iconCount") != EXPECTED)
                throw new IllegalArgumentException("Nieobsługiwany zestaw ikon.");
            JSONArray icons = manifest.getJSONArray("icons");
            if (icons.length() != EXPECTED)
                throw new IllegalArgumentException("Brakuje ikon.");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < icons.length(); i++) {
                JSONObject entry = icons.getJSONObject(i);
                String id = entry.getString("id");
                String name = entry.getString("name");
                String file = entry.getString("file");
                if (!safeId(id) || !ids.add(id) || name.isEmpty()
                        || name.length() > 80 || !file.equals("icons/" + id + ".webp")
                        || !files.contains(file))
                    throw new IllegalArgumentException("Nieprawidłowa ikona w paczce.");
                File icon = new File(stage, id + ".webp");
                String hash = sha256(icon);
                if (!hash.equalsIgnoreCase(entry.getString("sha256")))
                    throw new IllegalArgumentException("Niezgodna suma kontrolna ikony.");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(icon.getAbsolutePath(), options);
                if (options.outWidth != 192 || options.outHeight != 192)
                    throw new IllegalArgumentException("Nieprawidłowy obraz ikony.");
            }
            delete(previous);
            if (active.exists() && !active.renameTo(previous))
                throw new IllegalStateException("Nie można zabezpieczyć starej paczki.");
            if (!stage.renameTo(active)) {
                if (previous.exists()) previous.renameTo(active);
                throw new IllegalStateException("Nie można zapisać nowej paczki.");
            }
            delete(previous);
            return EXPECTED;
        } finally {
            delete(stage);
        }
    }

    private static JSONObject readManifest(File directory) throws Exception {
        File file = new File(directory, "manifest.json");
        if (!file.isFile() || file.length() > MAX_ENTRY)
            throw new IllegalArgumentException("Brak manifestu ikon.");
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        StringBuilder text = new StringBuilder();
        for (byte b : digest.digest())
            text.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return text.toString();
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        if (file.exists()) file.delete();
    }
}
