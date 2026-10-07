package com.edwinkarolczyk.edhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lokalny cache odczytu SUPLA. Nie zawiera tokenu i nie jest częścią backupu EDHOME.
 */
final class SuplaCacheStore {
    private static final String FILE_NAME = "supla-cloud-cache.json";
    private static final int FORMAT = 1;

    private SuplaCacheStore() {}

    static void save(Context context, SuplaCloudClient.Snapshot snapshot) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("baseUrl", snapshot.baseUrl);
        root.put("fetchedAt", snapshot.fetchedAt);
        root.put("channels", snapshot.channels);

        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        replaceFile(temp, target);
    }

    static JSONObject load(Context context) {
        try {
            File target = new File(context.getFilesDir(), FILE_NAME);
            File backup = new File(context.getFilesDir(), FILE_NAME + ".bak");
            if (!target.isFile() && backup.isFile()) backup.renameTo(target);
            if (!target.isFile()) return empty();
            if (target.length() > 2L * 1024L * 1024L) return empty();
            byte[] bytes = new byte[(int) target.length()];
            int offset = 0;
            try (FileInputStream input = new FileInputStream(target)) {
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
            }
            if (offset != bytes.length) return empty();
            String text = new String(bytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(text);
            if (root.optInt("format", 0) != FORMAT
                    || root.optJSONArray("channels") == null) return empty();
            return root;
        } catch (Exception ignored) {
            return empty();
        }
    }

    static int channelCount(Context context) {
        JSONArray channels = load(context).optJSONArray("channels");
        return channels == null ? 0 : channels.length();
    }

    static long fetchedAt(Context context) {
        return load(context).optLong("fetchedAt", 0L);
    }

    static void clear(Context context) {
        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        File backup = new File(context.getFilesDir(), FILE_NAME + ".bak");
        if (target.exists()) target.delete();
        if (temp.exists()) temp.delete();
        if (backup.exists()) backup.delete();
    }

    private static void replaceFile(File temp, File target) throws Exception {
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        if (backup.exists() && !backup.delete())
            throw new java.io.IOException("Nie udało się usunąć starej kopii cache SUPLA.");
        if (target.exists() && !target.renameTo(backup))
            throw new java.io.IOException("Nie udało się zabezpieczyć starego cache SUPLA.");
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target);
            throw new java.io.IOException("Nie udało się zapisać cache SUPLA.");
        }
        if (backup.exists()) backup.delete();
    }

    private static JSONObject empty() {
        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT);
            root.put("baseUrl", "");
            root.put("fetchedAt", 0L);
            root.put("channels", new JSONArray());
        } catch (Exception ignored) {}
        return root;
    }
}
