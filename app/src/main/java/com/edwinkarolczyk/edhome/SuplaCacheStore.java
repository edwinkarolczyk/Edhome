package com.edwinkarolczyk.edhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
        Files.writeString(temp.toPath(), root.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static JSONObject load(Context context) {
        try {
            File target = new File(context.getFilesDir(), FILE_NAME);
            if (!target.isFile()) return empty();
            String text = Files.readString(target.toPath(), StandardCharsets.UTF_8);
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
        if (target.exists()) target.delete();
        if (temp.exists()) temp.delete();
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
