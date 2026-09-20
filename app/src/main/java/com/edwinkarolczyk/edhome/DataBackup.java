package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Portable, user-initiated backup of EDHOME preview data.
 * JSON intentionally excludes the PIN verifier, update feed and diagnostic log.
 * Import is a full replacement, not a merge; validate before touching the database.
 */
final class DataBackup {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String FORMAT = "edhome-data-backup";
    private static final int FORMAT_VERSION = 1;
    private static final int DB_VERSION = 5;
    // Keep all existing tables, including pending and completed remanents.
    private static final String[][] TABLES = {
        {"places", "id", "name", "kind"},
        {"tasks", "id", "title", "done", "due_date", "repeat_rule", "repeat_every",
            "place_id", "priority", "duration_minutes"},
        {"pantry", "id", "name", "qty"},
        {"audit_sessions", "id", "started_at", "completed_at", "status"},
        {"audit_rows", "id", "session_id", "pantry_id", "name_snapshot",
            "expected_qty", "counted_qty", "status"},
        {"audit_corrections", "id", "session_id", "pantry_id", "old_qty",
            "new_qty", "changed_at"},
        {"task_history", "id", "task_id", "title_snapshot", "completed_at",
            "due_date", "next_due_date"}
    };

    private DataBackup() { }

    static String exportJson(SQLiteDatabase database, SharedPreferences prefs) throws Exception {
        JSONObject result = new JSONObject();
        result.put("format", FORMAT);
        result.put("formatVersion", FORMAT_VERSION);
        result.put("databaseVersion", DB_VERSION);
        result.put("createdAt", System.currentTimeMillis());
        result.put("sourceVersion", BuildConfig.VERSION_NAME);

        JSONObject settings = new JSONObject();
        settings.put("household", prefs.getString("household", "Moje gospodarstwo"));
        settings.put("theme", prefs.getString("theme", "Grafitowy"));
        settings.put("homeTileOrder", prefs.getString("home_tile_order", ""));
        result.put("settings", settings);

        JSONObject tables = new JSONObject();
        database.beginTransactionNonExclusive();
        try {
            for (String[] definition : TABLES) {
                String[] columns = columns(definition);
                JSONArray rows = new JSONArray();
                try (Cursor cursor = database.query(definition[0], columns,
                        null, null, null, null, "id ASC")) {
                    while (cursor.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < columns.length; i++) {
                            switch (cursor.getType(i)) {
                                case Cursor.FIELD_TYPE_NULL:
                                    row.put(columns[i], JSONObject.NULL);
                                    break;
                                case Cursor.FIELD_TYPE_INTEGER:
                                    row.put(columns[i], cursor.getLong(i));
                                    break;
                                case Cursor.FIELD_TYPE_STRING:
                                    row.put(columns[i], cursor.getString(i));
                                    break;
                                default:
                                    throw new IllegalStateException("Unsupported backup data type");
                            }
                        }
                        rows.put(row);
                    }
                }
                tables.put(definition[0], rows);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        result.put("tables", tables);
        String json = result.toString(2);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalStateException("Kopia przekracza limit 8 MB.");
        return json;
    }

    static void restoreJson(SQLiteDatabase database, SharedPreferences prefs, String json)
            throws Exception {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Plik jest za duży (maks. 8 MB).");
        JSONObject root = new JSONObject(json);
        int inputVersion = root.optInt("databaseVersion", -1);
        if (!FORMAT.equals(root.optString("format"))
                || root.optInt("formatVersion", -1) != FORMAT_VERSION
                || (inputVersion != 2 && inputVersion != 3 && inputVersion != 4 && inputVersion != DB_VERSION))
            throw new IllegalArgumentException("Nieobsługiwany format lub wersja kopii.");

        JSONObject settings = root.getJSONObject("settings");
        String household = settings.getString("household");
        String theme = settings.getString("theme");
        String tileOrder = settings.optString("homeTileOrder", "");
        if (household.trim().isEmpty() || household.length() > 200
                || !("Grafitowy".equals(theme) || "Leśny".equals(theme)
                    || "Jasny".equals(theme) || "Trener 2".equals(theme)))
            throw new IllegalArgumentException("Nieprawidłowe ustawienia kopii.");
        if (!tileOrder.isEmpty()) {
            java.util.Set<String> allowed = new HashSet<>(java.util.Arrays.asList(
                "tasks", "calendar", "places", "pantry", "audit", "updates",
                "backup", "settings", "today"));
            java.util.Set<String> selected = new HashSet<>();
            String[] ids = tileOrder.split(",", -1);
            if (ids.length != 9)
                throw new IllegalArgumentException("Nieprawidłowa kolejność kafelków.");
            for (String id : ids) if (!allowed.contains(id) || !selected.add(id))
                throw new IllegalArgumentException("Nieprawidłowa kolejność kafelków.");
        }

        JSONObject tables = root.getJSONObject("tables");
        Map<String, List<ContentValues>> parsed = new HashMap<>();
        for (String[] definition : TABLES) {
            JSONArray items = (inputVersion == 2 && "task_history".equals(definition[0]))
                || (inputVersion < 4 && "places".equals(definition[0]))
                ? new JSONArray() : tables.getJSONArray(definition[0]);
            if (items.length() > 20000)
                throw new IllegalArgumentException("Zbyt wiele rekordów w kopii.");
            List<ContentValues> rows = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                ContentValues values = new ContentValues();
                for (int j = 1; j < definition.length; j++) {
                    String key = definition[j];
                    if (!item.has(key)) {
                        if (inputVersion == 2 && "tasks".equals(definition[0])) {
                            if ("due_date".equals(key)) { values.putNull(key); continue; }
                            if ("repeat_rule".equals(key)) { values.put(key, "once"); continue; }
                            if ("repeat_every".equals(key)) { values.put(key, 1); continue; }
                        }
                        if (inputVersion < 4 && "tasks".equals(definition[0])
                                && "place_id".equals(key)) {
                            values.putNull(key);
                            continue;
                        }
                        if (inputVersion < 5 && "tasks".equals(definition[0])) {
                            if ("priority".equals(key)) {
                                values.put(key, "normal"); continue;
                            }
                            if ("duration_minutes".equals(key)) {
                                values.put(key, 30); continue;
                            }
                        }
                        throw new IllegalArgumentException("Niekompletny rekord: " + definition[0]);
                    }
                    Object value = item.get(key);
                    if (value == JSONObject.NULL) {
                        if (!("completed_at".equals(key) || "counted_qty".equals(key)
                            || "due_date".equals(key) || "next_due_date".equals(key)
                            || "place_id".equals(key)))
                            throw new IllegalArgumentException("Brak wymaganej wartości: " + key);
                        values.putNull(key);
                    } else if (value instanceof String) {
                        if ("id".equals(key) || isNumberColumn(key))
                            throw new IllegalArgumentException("Nieprawidłowa liczba: " + key);
                        values.put(key, (String) value);
                    } else if (value instanceof Number && isNumberColumn(key)) {
                        long number = ((Number) value).longValue();
                        if (((Number) value).doubleValue() != (double) number || number < 0)
                            throw new IllegalArgumentException("Nieprawidłowa liczba: " + key);
                        values.put(key, number);
                    } else {
                        throw new IllegalArgumentException("Nieprawidłowy typ pola: " + key);
                    }
                }
                Long id = values.getAsLong("id");
                if (id == null || id <= 0 || !ids.add(id))
                    throw new IllegalArgumentException("Nieprawidłowe lub powielone ID.");
                if ("places".equals(definition[0])) {
                    String name = values.getAsString("name");
                    String kind = values.getAsString("kind");
                    if (name == null || name.trim().isEmpty() || name.length() > 160
                            || !java.util.Arrays.asList("Dom", "Ogród", "Garaż",
                                "Warsztat", "Pomieszczenie", "Inne").contains(kind))
                        throw new IllegalArgumentException("Nieprawidłowe miejsce w kopii.");
                }
                if ("pantry".equals(definition[0])) {
                    Long qty = values.getAsLong("qty");
                    if (qty == null || qty > 100000000L ||
                        values.getAsString("name") == null
                        || values.getAsString("name").trim().isEmpty()
                        || values.getAsString("name").length() > 160)
                        throw new IllegalArgumentException("Nieprawidłowy produkt w kopii.");
                }
                if ("audit_sessions".equals(definition[0])) {
                    String status = values.getAsString("status");
                    if (!("open".equals(status) || "completed".equals(status)
                            || "cancelled".equals(status)))
                        throw new IllegalArgumentException("Nieznany stan remanentu.");
                }
                if ("audit_rows".equals(definition[0])) {
                    String status = values.getAsString("status");
                    Long expected = values.getAsLong("expected_qty");
                    Long counted = values.getAsLong("counted_qty");
                    if (expected == null || expected > 100000000L
                            || counted != null && counted > 100000000L
                            || !("pending".equals(status) || "skip".equals(status)
                                 || "match".equals(status) || "count".equals(status)))
                        throw new IllegalArgumentException("Nieprawidłowy wpis remanentu.");
                }
                if ("audit_corrections".equals(definition[0])) {
                    if (values.getAsLong("old_qty") > 100000000L ||
                        values.getAsLong("new_qty") > 100000000L)
                        throw new IllegalArgumentException("Nieprawidłowa korekta remanentu.");
                }
                if ("tasks".equals(definition[0])) {
                    Long done = values.getAsLong("done");
                    if (done == null || done > 1)
                        throw new IllegalArgumentException("Nieprawidłowy status czynności.");
                    String due = values.getAsString("due_date");
                    String rule = values.getAsString("repeat_rule");
                    Integer every = values.getAsInteger("repeat_every");
                    String error = TaskRules.validate(values.getAsString("title"),
                        due == null ? "" : due, rule, every == null ? 0 : every);
                    if (error != null) throw new IllegalArgumentException(error);
                    String priority = values.getAsString("priority");
                    Long minutes = values.getAsLong("duration_minutes");
                    if (priority == null
                            || !java.util.Arrays.asList(
                                "low", "normal", "high", "urgent").contains(priority)
                            || minutes == null || minutes < 1 || minutes > 480)
                        throw new IllegalArgumentException(
                            "Nieprawidłowy priorytet lub czas czynności.");
                }
                rows.add(values);
            }
            parsed.put(definition[0], rows);
        }

        Set<Long> places = new HashSet<>();
        Set<String> placeNames = new HashSet<>();
        for (ContentValues place : parsed.get("places")) {
            places.add(place.getAsLong("id"));
            if (!placeNames.add(place.getAsString("name").toLowerCase(
                    java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Powielone nazwy miejsc w kopii.");
        }
        for (ContentValues task : parsed.get("tasks")) {
            Long placeId = task.getAsLong("place_id");
            if (placeId != null && !places.contains(placeId))
                throw new IllegalArgumentException("Czynność wskazuje nieistniejące miejsce.");
        }
        Set<Long> sessions = new HashSet<>();
        for (ContentValues session : parsed.get("audit_sessions"))
            sessions.add(session.getAsLong("id"));
        for (String name : new String[]{"audit_rows", "audit_corrections"}) {
            for (ContentValues item : parsed.get(name)) {
                if (!sessions.contains(item.getAsLong("session_id")))
                    throw new IllegalArgumentException("Kopia ma wpisy bez remanentu.");
            }
        }

        database.beginTransaction();
        try {
            for (int i = TABLES.length - 1; i >= 0; i--)
                database.delete(TABLES[i][0], null, null);
            for (String[] definition : TABLES) {
                for (ContentValues values : parsed.get(definition[0]))
                    database.insertOrThrow(definition[0], null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        // Keep the new installation's PIN and update-source configuration untouched.
        if (!prefs.edit().putString("household", household)
                .putString("theme", theme)
                .putString("home_tile_order", tileOrder).commit())
            throw new IllegalStateException("Dane przywrócono, ale zapis ustawień nie powiódł się.");
    }

    private static String[] columns(String[] table) {
        String[] result = new String[table.length - 1];
        System.arraycopy(table, 1, result, 0, result.length);
        return result;
    }

    private static boolean isNumberColumn(String column) {
        return "id".equals(column) || "done".equals(column) || "qty".equals(column)
            || "repeat_every".equals(column) || "duration_minutes".equals(column)
            || "task_id".equals(column)
            || "place_id".equals(column)
            || "started_at".equals(column) || "completed_at".equals(column)
            || "session_id".equals(column) || "pantry_id".equals(column)
            || "expected_qty".equals(column) || "counted_qty".equals(column)
            || "old_qty".equals(column) || "new_qty".equals(column)
            || "changed_at".equals(column);
    }
}
