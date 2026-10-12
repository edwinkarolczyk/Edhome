package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Podział jednej zweryfikowanej migawki EDHOME na samodzielne sekcje ZIP.
 * Nie przywraca danych: zależności między modułami muszą być sprawdzone
 * przed uruchomieniem selektywnego importu.
 */
final class BackupModuleSnapshot {
    static final String PREFIX = "modules/";
    static final String FORMAT = "edhome-data-module";
    static final int VERSION = 1;

    private BackupModuleSnapshot() { }

    static String categoryForTable(String table) {
        if (table.startsWith("budget_")) return "budget";
        if (table.startsWith("paycheck_") || table.startsWith("bank_"))
            return "paycheck";
        if (table.startsWith("project_") || "projects".equals(table))
            return "projects";
        if (table.startsWith("storage_") || "nfc_links".equals(table))
            return "storage";
        if (table.startsWith("vehicle_") || "vehicles".equals(table))
            return "vehicles";
        if (table.startsWith("pantry_") || "pantry".equals(table)
                || table.startsWith("shopping_")) return "pantry";
        if (table.startsWith("garden_")) return "garden";
        if (table.startsWith("task_") || "tasks".equals(table))
            return "tasks";
        if (table.startsWith("member_")) return "calendar";
        if ("places".equals(table)) return "places";
        if ("household_members".equals(table) || "user_profiles".equals(table))
            return "profiles";
        if (table.startsWith("audit_")) return "audit";
        if ("device_timers".equals(table)) return "timers";
        // Dodatkowe przyszłe tabele nie mogą zniknąć z kopii.
        return "other";
    }

    private static TreeMap<String, JSONObject> groups(JSONObject snapshot)
            throws Exception {
        JSONObject tables = snapshot.getJSONObject("tables");
        TreeMap<String, JSONObject> grouped = new TreeMap<>();
        for (Iterator<String> names = tables.keys(); names.hasNext();) {
            String name = names.next();
            String category = categoryForTable(name);
            JSONObject group = grouped.get(category);
            if (group == null) {
                group = new JSONObject();
                grouped.put(category, group);
            }
            group.put(name, tables.getJSONArray(name));
        }
        // Ustawienia są sekcją niezależną od tabel SQL.
        grouped.put("settings", new JSONObject());
        return grouped;
    }

    static JSONObject index(String json) throws Exception {
        JSONObject index = new JSONObject();
        for (Map.Entry<String,JSONObject> group
                : groups(new JSONObject(json)).entrySet()) {
            JSONObject counts = new JSONObject();
            JSONObject tables = group.getValue();
            for (Iterator<String> names = tables.keys(); names.hasNext();) {
                String name = names.next();
                counts.put(name, tables.getJSONArray(name).length());
            }
            index.put(group.getKey(), counts);
        }
        return index;
    }

    static Map<String,byte[]> split(String json) throws Exception {
        JSONObject source = new JSONObject(json);
        LinkedHashMap<String,byte[]> parts = new LinkedHashMap<>();
        for (Map.Entry<String,JSONObject> group : groups(source).entrySet()) {
            JSONObject section = new JSONObject();
            section.put("format", FORMAT);
            section.put("formatVersion", VERSION);
            section.put("category", group.getKey());
            section.put("databaseVersion", source.getInt("databaseVersion"));
            section.put("createdAt", source.optLong("createdAt"));
            section.put("sourceVersion", source.optString("sourceVersion"));
            section.put("tables", group.getValue());
            if ("settings".equals(group.getKey()))
                section.put("settings", source.getJSONObject("settings"));
            parts.put(PREFIX + group.getKey() + ".json",
                section.toString().getBytes(StandardCharsets.UTF_8));
        }
        return parts;
    }

    static boolean allowedPath(String path) {
        if (path == null || !path.startsWith(PREFIX)
                || !path.endsWith(".json")) return false;
        String category = path.substring(PREFIX.length(), path.length() - 5);
        switch (category) {
            case "budget":
            case "paycheck":
            case "projects":
            case "storage":
            case "vehicles":
            case "pantry":
            case "garden":
            case "tasks":
            case "calendar":
            case "places":
            case "profiles":
            case "audit":
            case "timers":
            case "other":
            case "settings":
                return true;
            default:
                return false;
        }
    }
}
