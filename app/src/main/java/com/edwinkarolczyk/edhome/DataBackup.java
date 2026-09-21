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
    private static final int DB_VERSION = 13;
    private static final String[] HOME_TILE_IDS = {
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today"
    };
    // Keep all existing tables, including pending and completed remanents.
    private static final String[][] TABLES = {
        {"places", "id", "name", "kind", "parent_id", "icon"},
        {"household_members", "id", "name"},
        {"member_weekly_shifts", "id", "member_id", "weekday", "shift"},
        {"member_shift_exceptions", "id", "member_id", "date", "shift"},
        {"tasks", "id", "title", "done", "due_date", "repeat_rule", "repeat_every",
            "place_id", "priority", "duration_minutes", "assignee_id",
            "task_kind", "waste_fraction", "remind_time", "reminder_lead_days"},
        {"task_rotation_members", "task_id", "member_id", "position"},
        {"pantry", "id", "name", "qty"},
        {"shopping_items", "id", "name", "qty_milli", "unit", "checked"},
        {"device_timers", "id", "device_type", "title", "start_at", "end_at",
            "status", "acknowledged_at"},
        {"audit_sessions", "id", "started_at", "completed_at", "status"},
        {"audit_rows", "id", "session_id", "pantry_id", "name_snapshot",
            "expected_qty", "counted_qty", "status"},
        {"audit_corrections", "id", "session_id", "pantry_id", "old_qty",
            "new_qty", "changed_at"},
        {"task_history", "id", "task_id", "title_snapshot", "completed_at",
            "due_date", "next_due_date", "assignee_id", "assignee_name_snapshot"}
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
        settings.put("timerNotificationsEnabled",
            prefs.getBoolean("timer_notifications_enabled", false));
        JSONObject appearance = new JSONObject();
        for (String id : HOME_TILE_IDS) {
            JSONObject tile = new JSONObject();
            if (prefs.contains("tile_label_" + id))
                tile.put("label", prefs.getString("tile_label_" + id, ""));
            if (prefs.contains("tile_tint_" + id))
                tile.put("tint", prefs.getString("tile_tint_" + id, "default"));
            if (prefs.contains("tile_icon_" + id))
                tile.put("icon", prefs.getString("tile_icon_" + id, id));
            if (tile.length() > 0) appearance.put(id, tile);
        }
        settings.put("homeTileAppearance", appearance);
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
                || (inputVersion != 2 && inputVersion != 3 && inputVersion != 4 && inputVersion != 5 && inputVersion != 6 && inputVersion != 7 && inputVersion != 8 && inputVersion != 9 && inputVersion != 10 && inputVersion != 11 && inputVersion != 12 && inputVersion != DB_VERSION))
            throw new IllegalArgumentException("Nieobsługiwany format lub wersja kopii.");

        JSONObject settings = root.getJSONObject("settings");
        String household = settings.getString("household");
        String theme = settings.getString("theme");
        String tileOrder = settings.optString("homeTileOrder", "");
        boolean timerNotifications = settings.optBoolean(
            "timerNotificationsEnabled", false);
        if (settings.has("timerNotificationsEnabled")
                && !(settings.get("timerNotificationsEnabled") instanceof Boolean))
            throw new IllegalArgumentException("Nieprawidłowe ustawienia minutników.");
        if (household.trim().isEmpty() || household.length() > 200
                || !UiSkin.accepted(theme))
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

        JSONObject appearance = settings.optJSONObject("homeTileAppearance");
        Map<String, String> labels = new HashMap<>();
        Map<String, String> tints = new HashMap<>();
        Map<String, String> icons = new HashMap<>();
        if (appearance != null) {
            java.util.Iterator<String> keys = appearance.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                if (!java.util.Arrays.asList(HOME_TILE_IDS).contains(id))
                    throw new IllegalArgumentException("Nieznany kafelek w kopii.");
                JSONObject tile = appearance.getJSONObject(id);
                if (tile.has("label")) {
                    String label = tile.getString("label").trim();
                    if (label.isEmpty() || label.length() > 24
                            || label.contains("\n"))
                        throw new IllegalArgumentException("Nieprawidłowa nazwa kafelka.");
                    labels.put(id, label);
                }
                if (tile.has("tint")) {
                    String tint = tile.getString("tint");
                    if (!java.util.Arrays.asList("default", "mint",
                            "blue", "amber", "violet").contains(tint))
                        throw new IllegalArgumentException("Nieznany kolor kafelka.");
                    tints.put(id, tint);
                }
                if (tile.has("icon")) {
                    String iconId = tile.getString("icon");
                    if (!TileIcon.known(iconId))
                        throw new IllegalArgumentException("Nieznana ikona kafelka.");
                    icons.put(id, iconId);
                }
            }
        }

        JSONObject tables = root.getJSONObject("tables");
        Map<String, List<ContentValues>> parsed = new HashMap<>();
        for (String[] definition : TABLES) {
            JSONArray items = (inputVersion == 2 && "task_history".equals(definition[0]))
                || (inputVersion < 4 && "places".equals(definition[0]))
                || (inputVersion < 6 && "household_members".equals(definition[0]))
                || (inputVersion < 7 && ("member_weekly_shifts".equals(definition[0])
                    || "member_shift_exceptions".equals(definition[0])))
                || (inputVersion < 8 && "shopping_items".equals(definition[0]))
                || (inputVersion < 12 && "device_timers".equals(definition[0]))
                || (inputVersion < 13 && "task_rotation_members".equals(definition[0]))
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
                        if (inputVersion < 6 && "tasks".equals(definition[0])
                                && "assignee_id".equals(key)) {
                            values.putNull(key);
                            continue;
                        }
                        if (inputVersion < 9 && "tasks".equals(definition[0])) {
                            if ("task_kind".equals(key)) {
                                values.put(key, "general");
                                continue;
                            }
                            if ("waste_fraction".equals(key)) {
                                values.putNull(key);
                                continue;
                            }
                        }
                        if (inputVersion < 11 && "places".equals(definition[0])) {
                            if ("parent_id".equals(key)) {
                                values.putNull(key);
                                continue;
                            }
                            if ("icon".equals(key)) {
                                values.put(key, "places");
                                continue;
                            }
                        }
                        if (inputVersion < 13 && "task_history".equals(definition[0])) {
                            if ("assignee_id".equals(key)
                                    || "assignee_name_snapshot".equals(key)) {
                                values.putNull(key);
                                continue;
                            }
                        }
                        if (inputVersion < 10 && "tasks".equals(definition[0])) {
                            if ("remind_time".equals(key)) {
                                values.putNull(key);
                                continue;
                            }
                            if ("reminder_lead_days".equals(key)) {
                                values.put(key, 0);
                                continue;
                            }
                        }
                        throw new IllegalArgumentException("Niekompletny rekord: " + definition[0]);
                    }
                    Object value = item.get(key);
                    if (value == JSONObject.NULL) {
                        if (!("completed_at".equals(key) || "counted_qty".equals(key)
                            || "due_date".equals(key) || "next_due_date".equals(key)
                            || "place_id".equals(key) || "parent_id".equals(key) || "assignee_id".equals(key)
                            || "qty_milli".equals(key) || "waste_fraction".equals(key)
                            || "remind_time".equals(key)
                             || "assignee_name_snapshot".equals(key)
                             || "acknowledged_at".equals(key)))
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
                    if (PlaceRules.validateFields(name, kind,
                            values.getAsString("icon")) != null)
                        throw new IllegalArgumentException("Nieprawidłowe miejsce w kopii.");
                }
                if ("household_members".equals(definition[0])) {
                    String memberName = values.getAsString("name");
                    if (memberName == null || memberName.trim().isEmpty()
                            || memberName.length() > 80)
                        throw new IllegalArgumentException("Nieprawidłowy domownik.");
                }
                if ("member_weekly_shifts".equals(definition[0])
                        || "member_shift_exceptions".equals(definition[0])) {
                    String shift = values.getAsString("shift");
                    if (!java.util.Arrays.asList("off", "morning", "afternoon",
                            "night").contains(shift))
                        throw new IllegalArgumentException("Nieprawidłowa zmiana.");
                    if ("member_weekly_shifts".equals(definition[0])) {
                        Long day = values.getAsLong("weekday");
                        if (day == null || day < 1 || day > 7)
                            throw new IllegalArgumentException("Nieprawidłowy dzień tygodnia.");
                    } else {
                        String date = values.getAsString("date");
                        try {
                            if (date == null
                                    || !java.time.LocalDate.parse(date).toString().equals(date))
                                throw new IllegalArgumentException("Nieprawidłowa data wyjątku.");
                        } catch (java.time.format.DateTimeParseException problem) {
                            throw new IllegalArgumentException("Nieprawidłowa data wyjątku.");
                        }
                    }
                }
                if ("device_timers".equals(definition[0])) {
                    Long started = values.getAsLong("start_at");
                    Long ends = values.getAsLong("end_at");
                    if (started == null || ends == null
                            || !DeviceTimerRules.validRecord(
                                values.getAsString("device_type"),
                                values.getAsString("title"), started, ends,
                                values.getAsString("status"),
                                values.getAsLong("acknowledged_at")))
                        throw new IllegalArgumentException(
                            "Nieprawidłowy minutnik w kopii.");
                }
                if ("shopping_items".equals(definition[0])) {
                    String itemName = values.getAsString("name");
                    String unit = values.getAsString("unit");
                    Long amount = values.getAsLong("qty_milli");
                    Long checked = values.getAsLong("checked");
                    if (itemName == null || itemName.trim().isEmpty()
                            || itemName.length() > 160
                            || !ShoppingRules.knownUnit(unit)
                            || amount != null && (amount < 1
                                || amount > ShoppingRules.MAX_MILLI)
                            || checked == null || checked > 1)
                        throw new IllegalArgumentException(
                            "Nieprawidłowa pozycja listy zakupów.");
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
                    String kind = values.getAsString("task_kind");
                    String fraction = values.getAsString("waste_fraction");
                    if ("general".equals(kind)) {
                        if (fraction != null)
                            throw new IllegalArgumentException("Zwykła czynność ma frakcję odpadów.");
                    } else if ("waste".equals(kind)) {
                        if (WasteRules.validate(fraction, due, rule,
                                every == null ? 0 : every) != null)
                            throw new IllegalArgumentException("Nieprawidłowy termin odpadów.");
                    } else throw new IllegalArgumentException("Nieznany typ czynności.");
                    String remindAt = values.getAsString("remind_time");
                    Long lead = values.getAsLong("reminder_lead_days");
                    if (lead == null || lead < 0 || lead > 7
                            || !ReminderRules.allowedLead(lead.intValue())
                            || (remindAt == null && lead != 0)
                            || (remindAt != null
                                && (!ReminderRules.validTime(remindAt) || due == null)))
                        throw new IllegalArgumentException(
                            "Nieprawidłowa godzina lub wyprzedzenie przypomnienia.");
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

        Set<String> shoppingNames = new HashSet<>();
        for (ContentValues item : parsed.get("shopping_items")) {
            if (!shoppingNames.add(item.getAsString("name").toLowerCase(
                    java.util.Locale.ROOT)))
                throw new IllegalArgumentException(
                    "Powielone produkty na liście zakupów.");
        }
        Set<Long> members = new HashSet<>();
        Set<String> memberNames = new HashSet<>();
        for (ContentValues member : parsed.get("household_members")) {
            members.add(member.getAsLong("id"));
            if (!memberNames.add(member.getAsString("name").toLowerCase(
                    java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Powielone imiona domowników.");
        }
        Set<String> weeklyKeys = new HashSet<>();
        for (ContentValues shift : parsed.get("member_weekly_shifts")) {
            Long memberId = shift.getAsLong("member_id");
            if (!members.contains(memberId))
                throw new IllegalArgumentException("Grafik wskazuje nieistniejącego domownika.");
            String key = memberId + ":" + shift.getAsLong("weekday");
            if (!weeklyKeys.add(key))
                throw new IllegalArgumentException("Powielony dzień tygodnia w grafiku.");
        }
        Set<String> exceptionKeys = new HashSet<>();
        for (ContentValues shift : parsed.get("member_shift_exceptions")) {
            Long memberId = shift.getAsLong("member_id");
            if (!members.contains(memberId))
                throw new IllegalArgumentException("Wyjątek wskazuje nieistniejącego domownika.");
            String key = memberId + ":" + shift.getAsString("date");
            if (!exceptionKeys.add(key))
                throw new IllegalArgumentException("Powielony wyjątek w grafiku.");
        }
        Set<Long> places = new HashSet<>();
        Set<String> placeNames = new HashSet<>();
        Map<Long, Long> hierarchy = new HashMap<>();
        for (ContentValues place : parsed.get("places")) {
            Long id = place.getAsLong("id");
            Long parentId = place.getAsLong("parent_id");
            places.add(id);
            hierarchy.put(id, parentId);
            String siblingKey = (parentId == null ? 0 : parentId)
                + ":" + place.getAsString("name").toLowerCase(
                    java.util.Locale.ROOT);
            if (!placeNames.add(siblingKey))
                throw new IllegalArgumentException(
                    "Powielone nazwy miejsc w jednej lokalizacji.");
        }
        if (!PlaceRules.validForest(hierarchy))
            throw new IllegalArgumentException(
                "Kopia zawiera nieistniejące miejsce nadrzędne lub zapętlenie.");
        Set<Long> tasks = new HashSet<>();
        Map<Long, String> taskRules = new HashMap<>();
        Map<Long, Long> taskAssignees = new HashMap<>();
        for (ContentValues task : parsed.get("tasks")) {
            Long taskId = task.getAsLong("id");
            tasks.add(taskId);
            taskRules.put(taskId, task.getAsString("repeat_rule"));
            taskAssignees.put(taskId, task.getAsLong("assignee_id"));
            Long placeId = task.getAsLong("place_id");
            if (placeId != null && !places.contains(placeId))
                throw new IllegalArgumentException("Czynność wskazuje nieistniejące miejsce.");
            Long assigneeId = task.getAsLong("assignee_id");
            if (assigneeId != null && !members.contains(assigneeId))
                throw new IllegalArgumentException(
                    "Czynność wskazuje nieistniejącego domownika.");
        }
        Map<Long, Set<Long>> rotationMembers = new HashMap<>();
        Map<Long, Set<Long>> rotationPositions = new HashMap<>();
        for (ContentValues row : parsed.get("task_rotation_members")) {
            Long taskId = row.getAsLong("task_id");
            Long memberId = row.getAsLong("member_id");
            Long position = row.getAsLong("position");
            if (!tasks.contains(taskId) || !members.contains(memberId)
                    || position == null || position < 0
                    || !TaskRules.recurring(taskRules.get(taskId)))
                throw new IllegalArgumentException(
                    "Nieprawidłowa rotacja wykonawców w kopii.");
            if (!rotationMembers.computeIfAbsent(taskId,
                    ignored -> new HashSet<>()).add(memberId)
                    || !rotationPositions.computeIfAbsent(taskId,
                    ignored -> new HashSet<>()).add(position))
                throw new IllegalArgumentException(
                    "Powielona osoba lub pozycja w rotacji.");
        }
        for (Map.Entry<Long, Set<Long>> entry : rotationMembers.entrySet()) {
            if (entry.getValue().size() < 2)
                throw new IllegalArgumentException(
                    "Rotacja musi zawierać co najmniej dwie osoby.");
            Long assigned = taskAssignees.get(entry.getKey());
            if (assigned == null || !entry.getValue().contains(assigned))
                throw new IllegalArgumentException(
                    "Aktualny wykonawca nie należy do rotacji.");
            Set<Long> positions = rotationPositions.get(entry.getKey());
            for (long i = 0; i < positions.size(); i++)
                if (!positions.contains(i))
                    throw new IllegalArgumentException(
                        "Rotacja ma nieciągłą kolejność.");
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
        SharedPreferences.Editor restored = prefs.edit()
            .putString("household", household)
            .putString("theme", theme)
            .putString("home_tile_order", tileOrder)
            .putBoolean("timer_notifications_enabled", timerNotifications);
        for (String id : HOME_TILE_IDS) {
            restored.remove("tile_label_" + id)
                .remove("tile_tint_" + id).remove("tile_icon_" + id);
            if (labels.containsKey(id))
                restored.putString("tile_label_" + id, labels.get(id));
            if (tints.containsKey(id))
                restored.putString("tile_tint_" + id, tints.get(id));
            if (icons.containsKey(id))
                restored.putString("tile_icon_" + id, icons.get(id));
        }
        if (!restored.commit())
            throw new IllegalStateException("Dane przywrócono, ale zapis ustawień nie powiódł się.");
    }

    private static String[] columns(String[] table) {
        String[] result = new String[table.length - 1];
        System.arraycopy(table, 1, result, 0, result.length);
        return result;
    }

    private static boolean isNumberColumn(String column) {
        return "id".equals(column) || "done".equals(column)
            || "checked".equals(column) || "qty_milli".equals(column)
            || "qty".equals(column)
            || "repeat_every".equals(column) || "duration_minutes".equals(column)
            || "reminder_lead_days".equals(column)
            || "start_at".equals(column) || "end_at".equals(column)
            || "acknowledged_at".equals(column)
            || "task_id".equals(column)
            || "place_id".equals(column) || "parent_id".equals(column) || "assignee_id".equals(column)
            || "member_id".equals(column) || "position".equals(column) || "weekday".equals(column)
            || "started_at".equals(column) || "completed_at".equals(column)
            || "session_id".equals(column) || "pantry_id".equals(column)
            || "expected_qty".equals(column) || "counted_qty".equals(column)
            || "old_qty".equals(column) || "new_qty".equals(column)
            || "changed_at".equals(column);
    }
}
