package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plan zależności dla selektywnego importu. Działa wyłącznie na JSON,
 * bez zapisu do bazy. Żadna dodatkowa kategoria nie jest importowana
 * bez wyraźnego potwierdzenia przez użytkownika.
 */
final class BackupRestoreDependencies {
    private BackupRestoreDependencies() { }

    static Set<String> resolve(String installed, String archived,
            Set<String> requested) throws Exception {
        if (requested == null || requested.isEmpty())
            throw new IllegalArgumentException("Wybierz moduł do przywrócenia.");
        JSONObject current = new JSONObject(installed).getJSONObject("tables");
        JSONObject source = new JSONObject(archived).getJSONObject("tables");
        LinkedHashSet<String> selected = new LinkedHashSet<>(requested);
        Set<String> available = BackupSelectiveMerge.available(archived);
        if (!available.containsAll(selected))
            throw new IllegalArgumentException("W kopii brakuje wybranych modułów.");

        for (int pass = 0; pass < 20; pass++) {
            int before = selected.size();

            // Powiązania Projektów przechowują ID czynności z osobnej
            // tabeli tasks. Same projekty nie wystarczają do ich odtworzenia.
            if (selected.contains("projects")
                    && (hasRows(source, "project_task_dependencies")
                        || hasRows(source, "project_task_work_sessions")
                        || hasRows(source, "project_task_blockers")
                        || containsProjectTasks(source)))
                selected.add("tasks");

            // Czynności mają przypisania do Projektów. Gdy bieżące Projekty
            // nie odpowiadają źródłu, potrzebne są oba moduły.
            if (selected.contains("tasks"))
                includeIfDifferent(selected, source, current,
                    "tasks", "project_id", "projects", "projects");

            // Miejsca w różnych modułach są współdzielonymi rekordami.
            // Nie wolno przypisać rzeczy do obcej lokalizacji o tym samym ID.
            if (selected.contains("storage")) {
                includeIfDifferent(selected, source, current,
                    "storage_items", "place_id", "places", "places");
                includeIfDifferentByKind(selected, source, current,
                    "nfc_links", "target_kind", "place", "target_id",
                    "places", "places");
            }
            if (selected.contains("projects"))
                includeIfDifferent(selected, source, current,
                    "projects", "place_id", "places", "places");
            if (selected.contains("tasks"))
                includeIfDifferent(selected, source, current,
                    "tasks", "place_id", "places", "places");
            if (selected.contains("vehicles"))
                includeIfDifferent(selected, source, current,
                    "vehicle_tyre_sets", "place_id", "places", "places");
            if (selected.contains("pantry")) {
                includeIfDifferent(selected, source, current,
                    "shopping_items", "place_id", "places", "places");
                includeIfDifferent(selected, source, current,
                    "shopping_receipts", "place_id", "places", "places");
            }

            // Dane domowników również muszą odpowiadać przypisaniom.
            if (selected.contains("projects"))
                includeIfDifferent(selected, source, current,
                    "projects", "assignee_id", "household_members", "profiles");
            if (selected.contains("tasks")) {
                includeIfDifferent(selected, source, current,
                    "tasks", "assignee_id", "household_members", "profiles");
                includeIfDifferent(selected, source, current,
                    "task_rotation_members", "member_id",
                    "household_members", "profiles");
            }
            if (selected.contains("calendar")) {
                for (String table : new String[]{"member_weekly_shifts",
                        "member_shift_exceptions", "member_shift_hours",
                        "member_project_windows"})
                    includeIfDifferent(selected, source, current,
                        table, "member_id", "household_members", "profiles");
            }

            if (!available.containsAll(selected))
                throw new IllegalArgumentException(
                    "Kopia nie zawiera modułu wymaganego przez powiązania.");
            if (before == selected.size()) {
                // Zmiana Miejsc nie może przeadresować pozycji zachowywanych
                // w innych modułach (nawet gdy ich numer ID się nie zmieni).
                if (selected.contains("places"))
                    guardUnselectedPlaces(current, source, selected);
                return selected;
            }
        }
        throw new IllegalStateException("Nie można ustalić zależności kopii.");
    }

    private static boolean hasRows(JSONObject tables, String name) {
        JSONArray rows = tables.optJSONArray(name);
        return rows != null && rows.length() > 0;
    }

    private static boolean containsProjectTasks(JSONObject tables)
            throws Exception {
        JSONArray tasks = tables.optJSONArray("tasks");
        if (tasks == null) return false;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject row = tasks.getJSONObject(i);
            if (!row.isNull("project_id") && row.optLong("project_id") > 0)
                return true;
        }
        return false;
    }

    private static Map<Long,JSONObject> byId(JSONObject tables, String table)
            throws Exception {
        Map<Long,JSONObject> rows = new HashMap<>();
        JSONArray values = tables.optJSONArray(table);
        if (values == null) return rows;
        for (int i = 0; i < values.length(); i++) {
            JSONObject row = values.getJSONObject(i);
            rows.put(row.getLong("id"), row);
        }
        return rows;
    }

    private static void includeIfDifferent(Set<String> selection,
            JSONObject source, JSONObject current, String fromTable,
            String foreignColumn, String toTable, String category)
            throws Exception {
        JSONArray rows = source.optJSONArray(fromTable);
        if (rows == null) return;
        compareReferenced(selection, source, current, rows, foreignColumn,
            toTable, category, null, null);
    }

    private static void includeIfDifferentByKind(Set<String> selection,
            JSONObject source, JSONObject current, String fromTable,
            String kindColumn, String kindValue, String foreignColumn,
            String toTable, String category) throws Exception {
        JSONArray rows = source.optJSONArray(fromTable);
        if (rows == null) return;
        compareReferenced(selection, source, current, rows, foreignColumn,
            toTable, category, kindColumn, kindValue);
    }

    private static void compareReferenced(Set<String> selection,
            JSONObject source, JSONObject current, JSONArray records,
            String foreignColumn, String toTable, String category,
            String kindColumn, String kindValue) throws Exception {
        if (selection.contains(category)) return;
        Map<Long,JSONObject> archivedTargets = byId(source, toTable);
        Map<Long,JSONObject> installedTargets = byId(current, toTable);
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.getJSONObject(i);
            if (kindColumn != null
                    && !kindValue.equals(row.optString(kindColumn))) continue;
            if (!row.has(foreignColumn) || row.isNull(foreignColumn)) continue;
            long id = row.getLong(foreignColumn);
            if (id <= 0) continue;
            JSONObject archivedTarget = archivedTargets.get(id);
            if (archivedTarget == null)
                throw new IllegalArgumentException(
                    "W kopii brakuje rekordu powiązanego z modułem: "
                    + category + " (ID " + id + ").");
            JSONObject installedTarget = installedTargets.get(id);
            if (installedTarget == null
                    || !PaycheckBudgetSqliteStore.sameJson(
                        archivedTarget, installedTarget)) {
                selection.add(category);
                return;
            }
        }
    }

    private static void guardUnselectedPlaces(JSONObject current,
            JSONObject archived, Set<String> selected) throws Exception {
        Map<Long,JSONObject> old = byId(current, "places");
        Map<Long,JSONObject> incoming = byId(archived, "places");
        String[][] references = {
            {"storage_items","storage","place_id"},
            {"tasks","tasks","place_id"},
            {"projects","projects","place_id"},
            {"vehicle_tyre_sets","vehicles","place_id"},
            {"shopping_items","pantry","place_id"},
            {"shopping_receipts","pantry","place_id"}
        };
        for (String[] reference : references) {
            if (selected.contains(reference[1])) continue;
            JSONArray rows = current.optJSONArray(reference[0]);
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!row.has(reference[2]) || row.isNull(reference[2])) continue;
                long placeId = row.getLong(reference[2]);
                JSONObject prior = old.get(placeId);
                JSONObject next = incoming.get(placeId);
                if (prior != null && (next == null
                        || !PaycheckBudgetSqliteStore.sameJson(prior, next)))
                    throw new IllegalArgumentException(
                        "Nie można zmienić Miejsc: zachowany moduł "
                        + reference[1] + " korzysta z innej lokalizacji. "
                        + "Zaznacz zgodne dane lub użyj pełnej kopii.");
            }
        }
    }
}
