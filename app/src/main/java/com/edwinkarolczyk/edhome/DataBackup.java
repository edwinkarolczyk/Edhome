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
    private static final int DB_VERSION = 27;
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
        {"pantry", "id", "name", "qty", "category"},
        {"shopping_items", "id", "name", "qty_milli", "unit", "checked",
            "place_id"},
        {"shopping_receipts", "id", "shopping_id", "pantry_id", "name_snapshot",
            "packages", "before_qty", "after_qty", "happened_at",
            "place_id", "place_name_snapshot"},
        {"pantry_purchase_prices", "id", "operation_id", "shopping_id", "pantry_id",
            "name_snapshot", "unit", "quantity_milli", "unit_price_grosz",
            "shop", "happened_at"},
        {"storage_items", "id", "name", "kind", "parent_box_id", "place_id",
            "lent_to", "lent_at", "created_at"},
        {"storage_events", "id", "item_id", "name_snapshot", "action",
            "details", "happened_at"},
        {"paycheck_transactions", "id", "operation_id", "scope", "kind",
            "category", "amount_grosz", "note", "created_at"},
        {"paycheck_goals", "id", "scope", "name", "target_grosz", "created_at"},
        {"paycheck_goal_allocations", "id", "operation_id", "goal_id",
            "amount_grosz", "created_at"},
        {"device_timers", "id", "device_type", "title", "start_at", "end_at",
            "status", "acknowledged_at"},
        {"audit_sessions", "id", "started_at", "completed_at", "status"},
        {"audit_rows", "id", "session_id", "pantry_id", "name_snapshot",
            "expected_qty", "counted_qty", "status"},
        {"audit_corrections", "id", "session_id", "pantry_id", "old_qty",
            "new_qty", "changed_at"},
        {"task_history", "id", "task_id", "title_snapshot", "completed_at",
            "due_date", "next_due_date", "assignee_id", "assignee_name_snapshot"},
        {"pantry_barcodes", "id", "pantry_id", "barcode"},
        {"pantry_movements", "id", "operation_id", "pantry_id", "barcode",
            "name_snapshot", "kind", "qty", "before_qty", "after_qty", "happened_at"},
        {"pantry_product_details", "id", "pantry_id", "brand", "image_url"},
        {"pantry_packages", "pantry_id", "unit", "size_milli"},
        {"vehicles", "id", "name", "registration", "mileage",
            "oc_until", "inspection_until", "notes"},
        {"vehicle_events", "id", "operation_id", "vehicle_id",
            "kind", "event_date", "mileage", "note"},
        {"vehicle_tyre_sets", "id", "vehicle_id", "label", "season",
            "dot", "tread_tenths", "mounted", "place_id"},
        {"vehicle_policies", "id", "operation_id", "vehicle_id", "provider",
            "policy_number", "valid_from", "valid_until", "current", "notes",
            "goal_id"}
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
        settings.put("homeTileOrderV2", HomeTileCatalog.encode(
            HomeTileCatalog.canonical(
                prefs.getString(HomeTileCatalog.ORDER_KEY, null),
                prefs.getString("home_tile_order", ""),
                BuildConfig.DIAGNOSTICS_ENABLED)));
        settings.put("homeTileHiddenV2",
            prefs.getString("home_tiles_v2_hidden", ""));
        settings.put("timerNotificationsEnabled",
            prefs.getBoolean("timer_notifications_enabled", false));
        settings.put("quietHoursStart", prefs.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START));
        settings.put("quietHoursEnd", prefs.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END));
        JSONObject appearance = new JSONObject();
        java.util.Set<String> tileIds = new java.util.LinkedHashSet<>(
            java.util.Arrays.asList(HOME_TILE_IDS));
        tileIds.addAll(HomeTileCatalog.canonical(
            prefs.getString(HomeTileCatalog.ORDER_KEY, null),
            prefs.getString("home_tile_order", ""),
            BuildConfig.DIAGNOSTICS_ENABLED));
        for (String id : tileIds) {
            JSONObject tile = new JSONObject();
            if (prefs.contains("tile_label_" + id))
                tile.put("label", prefs.getString("tile_label_" + id, ""));
            if (prefs.contains("tile_tint_" + id))
                tile.put("tint", prefs.getString("tile_tint_" + id, "default"));
            if (prefs.contains("tile_icon_" + id))
                tile.put("icon", prefs.getString("tile_icon_" + id, id));
            if (prefs.contains("tile_target_" + id))
                tile.put("target", prefs.getString("tile_target_" + id, ""));
            if (prefs.contains("tile_width_" + id))
                tile.put("width", prefs.getString("tile_width_" + id, "small"));
            if (tile.length() > 0) appearance.put(id, tile);
        }
        settings.put("homeTileAppearance", appearance);
        settings.put("homeTileShortHoldMs", prefs.getInt(
            HomeTileLayout.SHORT_KEY, HomeTileLayout.DEFAULT_SHORT_MS));
        settings.put("homeTileDragHoldMs", prefs.getInt(
            HomeTileLayout.DRAG_KEY, HomeTileLayout.DEFAULT_DRAG_MS));
        settings.put("pantryTakeDelaySeconds", prefs.getInt(
            PantryTakeCountdown.DELAY_PREF, PantryTakeCountdown.DEFAULT_SECONDS));
        result.put("settings", settings);

        JSONObject tables = new JSONObject();
        database.beginTransactionNonExclusive();
        try {
            for (String[] definition : TABLES) {
                String[] columns = columns(definition);
                JSONArray rows = new JSONArray();
                String orderBy = "task_rotation_members".equals(definition[0])
                    ? "task_id ASC, position ASC"
                    : "pantry_packages".equals(definition[0])
                        ? "pantry_id ASC" : "id ASC";
                try (Cursor cursor = database.query(definition[0], columns,
                        null, null, null, null, orderBy)) {
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
                || (inputVersion != 2 && inputVersion != 3 && inputVersion != 4 && inputVersion != 5 && inputVersion != 6 && inputVersion != 7 && inputVersion != 8 && inputVersion != 9 && inputVersion != 10 && inputVersion != 11 && inputVersion != 12 && inputVersion != 13 && inputVersion != 14 && inputVersion != 15 && inputVersion != 16 && inputVersion != 17 && inputVersion != 18 && inputVersion != 19 && inputVersion != 20 && inputVersion != 21 && inputVersion != 22 && inputVersion != 23 && inputVersion != 24 && inputVersion != 25 && inputVersion != DB_VERSION))
            throw new IllegalArgumentException("Nieobsługiwany format lub wersja kopii.");

        JSONObject settings = root.getJSONObject("settings");
        String household = settings.getString("household");
        String theme = settings.getString("theme");
        String tileOrder = settings.optString("homeTileOrder", "");
        String tileOrderV2 = settings.has("homeTileOrderV2")
            ? settings.getString("homeTileOrderV2") : null;
        String hiddenTilesV2 = settings.optString("homeTileHiddenV2", "");
        java.util.List<String> hiddenIds = new java.util.ArrayList<>();
        java.util.List<String> restoredTiles = null;
        if (tileOrderV2 != null) {
            restoredTiles = new java.util.ArrayList<>();
            for (String tileId : tileOrderV2.split(",", -1)) {
                if (tileId.isEmpty() && tileOrderV2.isEmpty()) continue;
                if (!HomeTileCatalog.validTileId(tileId)
                        || restoredTiles.contains(tileId))
                    throw new IllegalArgumentException(
                        "Nieprawidłowy skrót lub duplikat w kopii.");
                restoredTiles.add(tileId);
            }
        }
        for (String id : hiddenTilesV2.split(",", -1)) {
            if (id.isEmpty() && hiddenTilesV2.isEmpty()) continue;
            if (restoredTiles == null || !restoredTiles.contains(id)
                    || hiddenIds.contains(id))
                throw new IllegalArgumentException(
                    "Nieprawidłowa lista ukrytych kafelków.");
            hiddenIds.add(id);
        }
        int shortHoldMs = settings.optInt("homeTileShortHoldMs",
            HomeTileLayout.DEFAULT_SHORT_MS);
        int dragHoldMs = settings.optInt("homeTileDragHoldMs",
            HomeTileLayout.DEFAULT_DRAG_MS);
        if ((settings.has("homeTileShortHoldMs")
                && (!(settings.get("homeTileShortHoldMs") instanceof Number)
                    || ((Number) settings.get("homeTileShortHoldMs"))
                        .doubleValue() != shortHoldMs))
                || (settings.has("homeTileDragHoldMs")
                    && (!(settings.get("homeTileDragHoldMs") instanceof Number)
                        || ((Number) settings.get("homeTileDragHoldMs"))
                            .doubleValue() != dragHoldMs))
                || !HomeTileLayout.validPair(shortHoldMs, dragHoldMs))
            throw new IllegalArgumentException("Nieprawidłowe czasy przytrzymania.");
        int takeDelaySeconds = settings.optInt("pantryTakeDelaySeconds",
            PantryTakeCountdown.DEFAULT_SECONDS);
        if (!PantryTakeCountdown.validSeconds(takeDelaySeconds)
                || (settings.has("pantryTakeDelaySeconds")
                    && (!(settings.get("pantryTakeDelaySeconds") instanceof Number)
                        || ((Number) settings.get("pantryTakeDelaySeconds"))
                            .doubleValue() != takeDelaySeconds)))
            throw new IllegalArgumentException("Nieprawidłowy czas wyjmowania.");
        boolean timerNotifications = settings.optBoolean(
            "timerNotificationsEnabled", false);
        if (settings.has("timerNotificationsEnabled")
                && !(settings.get("timerNotificationsEnabled") instanceof Boolean))
            throw new IllegalArgumentException("Nieprawidłowe ustawienia minutników.");
        String quietStart = settings.optString("quietHoursStart",
            QuietHoursRules.DEFAULT_START);
        String quietEnd = settings.optString("quietHoursEnd",
            QuietHoursRules.DEFAULT_END);
        if (!QuietHoursRules.validWindow(quietStart, quietEnd))
            throw new IllegalArgumentException(
                "Nieprawidłowe godziny ciszy w kopii.");
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
        Map<String, String> targets = new HashMap<>();
        Map<String, String> widths = new HashMap<>();
        if (appearance != null) {
            java.util.Iterator<String> keys = appearance.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                if (!java.util.Arrays.asList(HOME_TILE_IDS).contains(id)
                        && (restoredTiles == null
                            || !restoredTiles.contains(id)))
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
                if (tile.has("target")) {
                    String destination = tile.getString("target");
                    if (!HomeTileCatalog.validTarget(destination, true))
                        throw new IllegalArgumentException("Nieznany cel kafelka.");
                    targets.put(id, destination);
                }
                if (tile.has("width")) {
                    String width = tile.getString("width");
                    if (!"small".equals(width) && !"double".equals(width))
                        throw new IllegalArgumentException("Nieznany rozmiar kafelka.");
                    widths.put(id, width);
                }
            }
        }
        if (restoredTiles != null) for (String id : restoredTiles) {
            if (id.startsWith("tile_") && !targets.containsKey(id))
                throw new IllegalArgumentException(
                    "Własny kafelek nie ma celu w kopii.");
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
                || (inputVersion < 14 && ("pantry_barcodes".equals(definition[0])
                    || "pantry_movements".equals(definition[0])))
                || (inputVersion < 15 && "pantry_product_details".equals(definition[0]))
                || (inputVersion < 17 && "pantry_packages".equals(definition[0]))
                || (inputVersion < 18 && "shopping_receipts".equals(definition[0]))
                || (inputVersion < 19 && ("storage_items".equals(definition[0])
                    || "storage_events".equals(definition[0])))
                || (inputVersion < 20 && "paycheck_transactions".equals(definition[0]))
                || (inputVersion < 21 && ("paycheck_goals".equals(definition[0])
                    || "paycheck_goal_allocations".equals(definition[0])))
                || (inputVersion < 22 && "pantry_purchase_prices".equals(definition[0]))
                || (inputVersion < 24 && ("vehicles".equals(definition[0])
                    || "vehicle_events".equals(definition[0])))
                || (inputVersion < 25 && "vehicle_tyre_sets".equals(definition[0]))
                || (inputVersion < 26 && "vehicle_policies".equals(definition[0]))
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
                        if (inputVersion < 16 && "pantry".equals(definition[0])
                                && "category".equals(key)) {
                            values.put(key, "other");
                            continue;
                        }
                        if (inputVersion < 27
                                && "vehicle_policies".equals(definition[0])
                                && "goal_id".equals(key)) {
                            values.putNull(key);
                            continue;
                        }
                        if (inputVersion < 23
                                && ("shopping_items".equals(definition[0])
                                    || "shopping_receipts".equals(definition[0]))) {
                            if ("place_id".equals(key)) {
                                values.putNull(key); continue;
                            }
                            if ("place_name_snapshot".equals(key)) {
                                values.put(key, ""); continue;
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
                             || "acknowledged_at".equals(key)
                            || ("storage_items".equals(definition[0])
                                && ("lent_to".equals(key)
                                    || "parent_box_id".equals(key)
                                    || "lent_at".equals(key)))
                            || ("pantry_purchase_prices".equals(definition[0])
                                && ("shopping_id".equals(key)
                                    || "pantry_id".equals(key)
                                    || "quantity_milli".equals(key)))
                            || ("vehicle_events".equals(definition[0])
                                && "mileage".equals(key))
                            || ("vehicle_tyre_sets".equals(definition[0])
                                && "tread_tenths".equals(key))
                            || ("vehicle_policies".equals(definition[0])
                                && "goal_id".equals(key))))
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
                if (!"task_rotation_members".equals(definition[0])
                        && !"pantry_packages".equals(definition[0])) {
                    Long id = values.getAsLong("id");
                    if (id == null || id <= 0 || !ids.add(id))
                        throw new IllegalArgumentException(
                            "Nieprawidłowe lub powielone ID.");
                }
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
                if ("vehicles".equals(definition[0])) {
                    String title = values.getAsString("name");
                    String plate = values.getAsString("registration");
                    String oc = values.getAsString("oc_until");
                    String inspection = values.getAsString("inspection_until");
                    String notes = values.getAsString("notes");
                    Long mileage = values.getAsLong("mileage");
                    if (mileage == null || mileage > 999999999L || notes == null
                            || notes.length() > 500
                            || !VehicleRules.name(title).equals(title)
                            || !VehicleRules.registration(plate).equals(plate)
                            || !VehicleRules.optionalDate(oc).equals(oc)
                            || !VehicleRules.optionalDate(inspection).equals(inspection))
                        throw new IllegalArgumentException("Nieprawidłowy pojazd w kopii.");
                }
                if ("vehicle_tyre_sets".equals(definition[0])) {
                    Long vehicle = values.getAsLong("vehicle_id");
                    String name = values.getAsString("label");
                    String season = values.getAsString("season");
                    String dot = values.getAsString("dot");
                    Long tread = values.getAsLong("tread_tenths");
                    Long mounted = values.getAsLong("mounted");
                    Long place = values.getAsLong("place_id");
                    if (vehicle == null || vehicle < 1
                            || tread != null && (tread < 0 || tread > 200)
                            || mounted == null || mounted < 0 || mounted > 1
                            || mounted == 1 && place != null
                            || place != null && place < 1
                            || !VehicleTyreStore.label(name).equals(name)
                            || !VehicleTyreStore.season(season).equals(season)
                            || !VehicleTyreStore.dot(dot).equals(dot))
                        throw new IllegalArgumentException(
                            "Nieprawidłowy komplet opon w kopii.");
                }
                if ("vehicle_policies".equals(definition[0])) {
                    String operation = values.getAsString("operation_id");
                    Long vehicle = values.getAsLong("vehicle_id");
                    String company = values.getAsString("provider");
                    String number = values.getAsString("policy_number");
                    String from = values.getAsString("valid_from");
                    String until = values.getAsString("valid_until");
                    Long current = values.getAsLong("current");
                    Long goalId = values.getAsLong("goal_id");
                    String description = values.getAsString("notes");
                    if (operation == null || !operation.matches(
                                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                            || vehicle == null || vehicle < 1
                            || current == null || current < 0 || current > 1
                            || goalId != null && goalId < 1
                            || !VehiclePolicyStore.provider(company).equals(company)
                            || !VehiclePolicyStore.number(number).equals(number)
                            || !VehiclePolicyStore.dates(from,until).equals(until)
                            || !VehiclePolicyStore.notes(description).equals(description))
                        throw new IllegalArgumentException(
                            "Nieprawidłowa polisa OC w kopii.");
                }
                if ("vehicle_events".equals(definition[0])) {
                    String operation = values.getAsString("operation_id");
                    Long vehicle = values.getAsLong("vehicle_id");
                    Long mileage = values.getAsLong("mileage");
                    String date = values.getAsString("event_date");
                    String description = values.getAsString("note");
                    if (operation == null || !operation.matches(
                                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                            || vehicle == null || vehicle < 1
                            || mileage != null && mileage > 999999999L
                            || !VehicleRules.optionalDate(date).equals(date)
                            || date.isEmpty()
                            || !VehicleRules.note(description).equals(description))
                        throw new IllegalArgumentException(
                            "Nieprawidłowa historia pojazdu w kopii.");
                    VehicleRules.eventType(values.getAsString("kind"));
                }
                if ("pantry_purchase_prices".equals(definition[0])) {
                    String operation = values.getAsString("operation_id");
                    String name = values.getAsString("name_snapshot");
                    String unit = values.getAsString("unit");
                    Long amount = values.getAsLong("unit_price_grosz");
                    Long qty = values.getAsLong("quantity_milli");
                    Long shopping = values.getAsLong("shopping_id");
                    Long product = values.getAsLong("pantry_id");
                    String shop = values.getAsString("shop");
                    Long date = values.getAsLong("happened_at");
                    if (operation == null
                            || !operation.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                            || name == null || name.trim().isEmpty()
                            || name.length() > 160 || !ShoppingRules.knownUnit(unit)
                            || amount == null || amount < 1
                            || amount > MoneyRules.MAX_GROSZ
                            || qty != null && (qty < 1 || qty > ShoppingRules.MAX_MILLI)
                            || shopping != null && shopping < 1
                            || product != null && product < 1
                            || shop == null || shop.length() > 80
                            || date == null || date <= 0)
                        throw new IllegalArgumentException("Nieprawidłowa historia ceny.");
                }
                if ("paycheck_goals".equals(definition[0])) {
                    String scope = values.getAsString("scope");
                    String name = values.getAsString("name");
                    Long target = values.getAsLong("target_grosz");
                    Long date = values.getAsLong("created_at");
                    if (!"shared".equals(scope) || name == null
                            || name.trim().isEmpty() || name.length() > 80
                            || target == null || target < 1
                            || target > MoneyRules.MAX_GROSZ
                            || date == null || date <= 0)
                        throw new IllegalArgumentException("Nieprawidłowy wspólny cel.");
                }
                if ("paycheck_goal_allocations".equals(definition[0])) {
                    String operation = values.getAsString("operation_id");
                    Long goal = values.getAsLong("goal_id");
                    Long amount = values.getAsLong("amount_grosz");
                    Long date = values.getAsLong("created_at");
                    if (operation == null || !operation.matches("[0-9a-fA-F-]{36}")
                            || goal == null || goal <= 0
                            || amount == null || amount < 1
                            || amount > MoneyRules.MAX_GROSZ
                            || date == null || date <= 0)
                        throw new IllegalArgumentException("Nieprawidłowe odłożenie na cel.");
                }
                if ("paycheck_transactions".equals(definition[0])) {
                    String operation = values.getAsString("operation_id");
                    String scope = values.getAsString("scope");
                    String kind = values.getAsString("kind");
                    String category = values.getAsString("category");
                    String note = values.getAsString("note");
                    Long amount = values.getAsLong("amount_grosz");
                    Long date = values.getAsLong("created_at");
                    if (operation == null || !operation.matches("[0-9a-fA-F-]{36}")
                            || !"shared".equals(scope)
                            || !("income".equals(kind) || "expense".equals(kind))
                            || !MoneyRules.category(category)
                            || note == null || note.length() > 160
                            || amount == null || amount < 1
                            || amount > MoneyRules.MAX_GROSZ
                            || date == null || date <= 0)
                        throw new IllegalArgumentException("Nieprawidłowa transakcja wspólna.");
                }
                if ("storage_items".equals(definition[0])) {
                    String name = values.getAsString("name");
                    String kind = values.getAsString("kind");
                    String person = values.getAsString("lent_to");
                    Long lentAt = values.getAsLong("lent_at");
                    Long created = values.getAsLong("created_at");
                    Long box = values.getAsLong("parent_box_id");
                    Long place = values.getAsLong("place_id");
                    if (name == null || name.trim().isEmpty() || name.length() > 160
                            || !("box".equals(kind) || "thing".equals(kind))
                            || box != null && place != null
                            || person != null && (person.trim().isEmpty()
                                || person.length() > 80 || !"thing".equals(kind))
                            || (person == null) != (lentAt == null)
                            || lentAt != null && lentAt <= 0
                            || created == null || created <= 0)
                        throw new IllegalArgumentException("Nieprawidłowa rzecz lub pudełko.");
                }
                if ("storage_events".equals(definition[0])) {
                    String action = values.getAsString("action");
                    String name = values.getAsString("name_snapshot");
                    String details = values.getAsString("details");
                    Long itemId = values.getAsLong("item_id");
                    Long stamp = values.getAsLong("happened_at");
                    if (itemId == null || itemId < 1 || name == null
                            || name.trim().isEmpty() || name.length() > 160
                            || details == null || details.length() > 300
                            || !("created".equals(action) || "moved".equals(action)
                                || "lent".equals(action) || "returned".equals(action)
                                || "removed".equals(action))
                            || stamp == null || stamp <= 0)
                        throw new IllegalArgumentException("Nieprawidłowa historia rzeczy.");
                }
                if ("shopping_receipts".equals(definition[0])) {
                    Long shopping = values.getAsLong("shopping_id");
                    Long pantry = values.getAsLong("pantry_id");
                    Long packages = values.getAsLong("packages");
                    Long before = values.getAsLong("before_qty");
                    Long after = values.getAsLong("after_qty");
                    Long stamp = values.getAsLong("happened_at");
                    String name = values.getAsString("name_snapshot");
                    String placeName = values.getAsString("place_name_snapshot");
                    if (shopping == null || shopping < 1 || pantry == null || pantry < 1
                            || packages == null || packages < 1 || packages > 100000000
                            || before == null || before < 0 || before > 100000000
                            || after == null || after != before + packages
                            || after > 100000000 || stamp == null || stamp <= 0
                            || name == null || name.trim().isEmpty() || name.length() > 160
                            || placeName == null || placeName.length() > 160)
                        throw new IllegalArgumentException("Nieprawidłowe przyjęcie zakupów.");
                }
                if ("pantry".equals(definition[0])) {
                    Long qty = values.getAsLong("qty");
                    if (qty == null || qty > 100000000L ||
                        values.getAsString("name") == null
                        || values.getAsString("name").trim().isEmpty()
                        || values.getAsString("name").length() > 160
                        || !PantryCategories.known(values.getAsString("category")))
                        throw new IllegalArgumentException("Nieprawidłowy produkt w kopii.");
                }
                if ("pantry_packages".equals(definition[0])) {
                    Long amount = values.getAsLong("size_milli");
                    if (amount == null || !PantryPackageRules.valid(
                            values.getAsString("unit"), amount))
                        throw new IllegalArgumentException("Nieprawidłowe opakowanie w kopii.");
                }
                if ("pantry_barcodes".equals(definition[0])
                        && !PantryScanRules.validBarcode(values.getAsString("barcode")))
                    throw new IllegalArgumentException("Nieprawidłowy kod w kopii.");
                if ("pantry_product_details".equals(definition[0])) {
                    String brand = values.getAsString("brand");
                    String imageUrl = values.getAsString("image_url");
                    if (brand == null || brand.length() > 100 || imageUrl == null
                            || !imageUrl.isEmpty()
                                && !PantryProductLookup.safeImageUrl(imageUrl))
                        throw new IllegalArgumentException("Nieprawidłowe dane zdjęcia w kopii.");
                }
                if ("pantry_movements".equals(definition[0])) {
                    Long before = values.getAsLong("before_qty");
                    Long after = values.getAsLong("after_qty");
                    Long amount = values.getAsLong("qty");
                    String kind = values.getAsString("kind");
                    String operation = values.getAsString("operation_id");
                    if (!PantryScanRules.validBarcode(values.getAsString("barcode"))
                            || operation == null || operation.isEmpty()
                            || amount == null || amount != 1 || before == null
                            || after == null || before > 100000000L
                            || after > 100000000L
                            || !("ADD".equals(kind) ? after == before + 1
                                : "TAKE".equals(kind) && after + 1 == before))
                        throw new IllegalArgumentException("Nieprawidłowy ruch w kopii.");
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

        Set<Long> vehicleIds = new HashSet<>();
        for (ContentValues vehicle : parsed.get("vehicles"))
            vehicleIds.add(vehicle.getAsLong("id"));
        Set<String> vehicleOperations = new HashSet<>();
        for (ContentValues event : parsed.get("vehicle_events")) {
            if (!vehicleIds.contains(event.getAsLong("vehicle_id"))
                    || !vehicleOperations.add(event.getAsString("operation_id")))
                throw new IllegalArgumentException(
                    "Historia pojazdu bez pojazdu lub zduplikowany wpis.");
        }
        Set<Long> mountedVehicles = new HashSet<>();
        for (ContentValues tyres : parsed.get("vehicle_tyre_sets")) {
            Long vehicle = tyres.getAsLong("vehicle_id");
            Long mounted = tyres.getAsLong("mounted");
            if (!vehicleIds.contains(vehicle)
                    || mounted == 1 && !mountedVehicles.add(vehicle))
                throw new IllegalArgumentException(
                    "Komplet opon bez pojazdu lub dwa zamontowane komplety.");
        }
        Set<Long> policyCurrentVehicles = new HashSet<>();
        Set<String> policyOperations = new HashSet<>();
        for (ContentValues policy : parsed.get("vehicle_policies")) {
            Long vehicle = policy.getAsLong("vehicle_id");
            Long current = policy.getAsLong("current");
            String operation = policy.getAsString("operation_id");
            if (!vehicleIds.contains(vehicle)
                    || !policyOperations.add(operation)
                    || current == 1 && !policyCurrentVehicles.add(vehicle))
                throw new IllegalArgumentException(
                    "Polisa OC bez pojazdu, duplikat lub dwie bieżące polisy.");
        }
        Map<Long, ContentValues> objects = new HashMap<>();
        Set<Long> validPlaces = new HashSet<>();
        for (ContentValues place : parsed.get("places"))
            validPlaces.add(place.getAsLong("id"));
        for (ContentValues tyres : parsed.get("vehicle_tyre_sets")) {
            Long place = tyres.getAsLong("place_id");
            if (place != null && !validPlaces.contains(place))
                throw new IllegalArgumentException("Komplet opon ma nieistniejące miejsce.");
        }
        for (ContentValues item : parsed.get("shopping_items")) {
            Long place = item.getAsLong("place_id");
            if (place != null && !validPlaces.contains(place))
                throw new IllegalArgumentException("Zakup ma nieistniejące miejsce.");
        }
        for (ContentValues receipt : parsed.get("shopping_receipts")) {
            Long place = receipt.getAsLong("place_id");
            if (place != null && !validPlaces.contains(place))
                throw new IllegalArgumentException("Przyjęcie ma nieistniejące miejsce.");
            if (place != null && receipt.getAsString("place_name_snapshot").isEmpty())
                throw new IllegalArgumentException("Przyjęcie nie ma nazwy miejsca.");
        }
        for (ContentValues object : parsed.get("storage_items"))
            objects.put(object.getAsLong("id"), object);
        for (ContentValues object : parsed.get("storage_items")) {
            Long box = object.getAsLong("parent_box_id");
            Long place = object.getAsLong("place_id");
            if (place != null && !validPlaces.contains(place))
                throw new IllegalArgumentException("Rzecz ma nieistniejące miejsce.");
            Set<Long> seen = new HashSet<>();
            while (box != null) {
                ContentValues parent = objects.get(box);
                if (!seen.add(box) || parent == null
                        || !"box".equals(parent.getAsString("kind")))
                    throw new IllegalArgumentException("Błąd powiązania pudełek.");
                box = parent.getAsLong("parent_box_id");
            }
        }
        Map<Long, Long> goalLimits = new HashMap<>();
        for (ContentValues goal : parsed.get("paycheck_goals"))
            goalLimits.put(goal.getAsLong("id"),
                goal.getAsLong("target_grosz"));
        for (ContentValues policy : parsed.get("vehicle_policies")) {
            Long goalId = policy.getAsLong("goal_id");
            if (goalId != null && !goalLimits.containsKey(goalId))
                throw new IllegalArgumentException(
                    "Polisa OC wskazuje nieistniejący wspólny cel PayCheck.");
        }
        Map<Long, Long> goalTotals = new HashMap<>();
        Set<String> goalOperations = new HashSet<>();
        for (ContentValues row : parsed.get("paycheck_goal_allocations")) {
            Long goalId = row.getAsLong("goal_id");
            Long maximum = goalLimits.get(goalId);
            long amount = row.getAsLong("amount_grosz");
            long previous = goalTotals.containsKey(goalId)
                ? goalTotals.get(goalId) : 0;
            if (maximum == null || !goalOperations.add(
                    row.getAsString("operation_id"))
                    || previous > maximum || amount > maximum - previous)
                throw new IllegalArgumentException(
                    "Powielona wpłata albo przekroczony lub nieistniejący cel.");
            goalTotals.put(goalId, previous + amount);
        }
        Set<String> sharedFinanceOperations = new HashSet<>();
        for (ContentValues entry : parsed.get("paycheck_transactions")) {
            if (!sharedFinanceOperations.add(entry.getAsString("operation_id")))
                throw new IllegalArgumentException("Zduplikowana transakcja PayCheck.");
        }
        Set<Long> pantryIds = new HashSet<>();
        for (ContentValues p : parsed.get("pantry"))
            pantryIds.add(p.getAsLong("id"));
        Set<Long> packagedProducts = new HashSet<>();
        for (ContentValues packageRow : parsed.get("pantry_packages")) {
            Long pid = packageRow.getAsLong("pantry_id");
            if (!pantryIds.contains(pid) || !packagedProducts.add(pid))
                throw new IllegalArgumentException("Nieprawidłowe przypisanie opakowania.");
        }
        if (inputVersion >= 17 && packagedProducts.size() != pantryIds.size())
            throw new IllegalArgumentException("Brakuje wielkości opakowania w kopii.");
        Set<String> codes = new HashSet<>();
        for (ContentValues b : parsed.get("pantry_barcodes")) {
            if (!pantryIds.contains(b.getAsLong("pantry_id"))
                    || !codes.add(b.getAsString("barcode")))
                throw new IllegalArgumentException("Nieprawidłowe powiązanie kodu.");
        }
        Set<Long> detailedProducts = new HashSet<>();
        for (ContentValues detail : parsed.get("pantry_product_details")) {
            Long pid = detail.getAsLong("pantry_id");
            if (!pantryIds.contains(pid) || !detailedProducts.add(pid))
                throw new IllegalArgumentException("Nieprawidłowe powiązanie zdjęcia.");
        }
        Set<String> movements = new HashSet<>();
        for (ContentValues m : parsed.get("pantry_movements"))
            if (!movements.add(m.getAsString("operation_id")))
                throw new IllegalArgumentException("Powielona operacja skanu.");
        Set<String> priceOperationIds = new HashSet<>();
        for (ContentValues price : parsed.get("pantry_purchase_prices")) {
            if (!priceOperationIds.add(price.getAsString("operation_id")))
                throw new IllegalArgumentException("Powielony zapis ceny w kopii.");
        }
        Set<Long> receivedShoppingIds = new HashSet<>();
        for (ContentValues receipt : parsed.get("shopping_receipts")) {
            Long shopping = receipt.getAsLong("shopping_id");
            if (!receivedShoppingIds.add(shopping))
                throw new IllegalArgumentException("Podwójne przyjęcie zakupów w kopii.");
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
            if (inputVersion < 17) PantryPackageStore.fillLegacy(database);
            // Deleted shopping rows intentionally leave receipt/price history.
            // After importing into a fresh database, AUTOINCREMENT would only
            // know IDs still present in shopping_items and could reuse an ID
            // referenced by an old receipt (falsely "ALREADY_RECEIVED").
            // Reserve every historical shopping ID inside this same transaction.
            database.execSQL("INSERT INTO sqlite_sequence(name,seq) "
                + "SELECT 'shopping_items',0 WHERE NOT EXISTS "
                + "(SELECT 1 FROM sqlite_sequence WHERE name='shopping_items')");
            database.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,"
                + "COALESCE((SELECT MAX(shopping_id) FROM shopping_receipts),0),"
                + "COALESCE((SELECT MAX(shopping_id) FROM pantry_purchase_prices),0)) "
                + "WHERE name='shopping_items'");

            // Preferences and SQL are separate stores. Save preferences BEFORE
            // committing SQL, so a failed preference write rolls SQL back.
            // Never clear the installed PIN, private vault or update channel.
            // Keep the new installation's PIN and update-source configuration untouched.
            SharedPreferences.Editor restored = prefs.edit()
                .putString("household", household)
                .putString("theme", theme)
                .putString("home_tile_order", tileOrder)
                .putInt(HomeTileLayout.SHORT_KEY, shortHoldMs)
                .putInt(HomeTileLayout.DRAG_KEY, dragHoldMs)
                .putInt(PantryTakeCountdown.DELAY_PREF, takeDelaySeconds)
                .putBoolean("timer_notifications_enabled", timerNotifications)
                .putString("quiet_hours_start", quietStart)
                .putString("quiet_hours_end", quietEnd);
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("tile_label_") || key.startsWith("tile_tint_")
                        || key.startsWith("tile_icon_") || key.startsWith("tile_target_")
                        || key.startsWith("tile_width_"))
                    restored.remove(key);
            }
            if (tileOrderV2 == null) restored.remove(HomeTileCatalog.ORDER_KEY);
            else restored.putString(HomeTileCatalog.ORDER_KEY, tileOrderV2);
            restored.putString("home_tiles_v2_hidden", hiddenTilesV2);
            for (String id : labels.keySet())
                restored.putString("tile_label_" + id, labels.get(id));
            for (String id : tints.keySet())
                restored.putString("tile_tint_" + id, tints.get(id));
            for (String id : icons.keySet())
                restored.putString("tile_icon_" + id, icons.get(id));
            for (String id : targets.keySet())
                restored.putString("tile_target_" + id, targets.get(id));
            for (String id : widths.keySet())
                restored.putString("tile_width_" + id, widths.get(id));
            if (!restored.commit())
                throw new IllegalStateException("Nie zapisano ustawień; baza danych została cofnięta.");
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
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
            || "changed_at".equals(column) || "size_milli".equals(column)
            || "shopping_id".equals(column) || "packages".equals(column)
            || "amount_grosz".equals(column)
            || "quantity_milli".equals(column) || "unit_price_grosz".equals(column)
            || "target_grosz".equals(column) || "goal_id".equals(column)
            || "parent_box_id".equals(column) || "lent_at".equals(column)
            || "created_at".equals(column) || "item_id".equals(column)
            || "before_qty".equals(column)
            || "after_qty".equals(column) || "happened_at".equals(column)
            || "mileage".equals(column) || "vehicle_id".equals(column)
            || "tread_tenths".equals(column) || "mounted".equals(column)
            || "current".equals(column);
    }
}
