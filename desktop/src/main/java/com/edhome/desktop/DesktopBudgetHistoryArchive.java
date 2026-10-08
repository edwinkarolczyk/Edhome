package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

/**
 * Historia Budżetu miesiąca jest append-only również przy pełnym snapshot.
 * Desktop zachowuje wcześniejsze zdarzenia i nigdy nie zastępuje po cichu
 * istniejącego UUID inną treścią.
 */
final class DesktopBudgetHistoryArchive {
    private DesktopBudgetHistoryArchive() { }

    static void retain(JsonObject previous, JsonObject incoming)
            throws DesktopHubServer.Conflict {
        if (previous == null || !previous.has("tables")
                || !previous.get("tables").isJsonObject()) return;
        JsonObject previousTables = previous.getAsJsonObject("tables");
        if (!previousTables.has("budget_history")
                || !previousTables.get("budget_history").isJsonArray()) return;
        JsonArray oldRows = previousTables.getAsJsonArray("budget_history");
        if (oldRows.size() == 0) return;
        if (incoming == null || !incoming.has("tables")
                || !incoming.get("tables").isJsonObject()
                || !incoming.getAsJsonObject("tables").has("budget_history")
                || !incoming.getAsJsonObject("tables")
                    .get("budget_history").isJsonArray())
            throw new DesktopHubServer.Conflict("budget_history", "",
                "Pełna kopia nie zawiera archiwum Budżetu miesiąca.");

        JsonArray newRows = incoming.getAsJsonObject("tables")
            .getAsJsonArray("budget_history");
        Map<String,JsonObject> known = new HashMap<>();
        for (JsonElement element : newRows) {
            JsonObject row = checked(element);
            String key = row.get("entity_key").getAsString();
            if (known.put(key, row) != null)
                throw new DesktopHubServer.Conflict("budget_history", key,
                    "Powtórzone ID zdarzenia w danych z telefonu.");
        }
        for (JsonElement element : oldRows) {
            JsonObject oldRow = checked(element);
            String key = oldRow.get("entity_key").getAsString();
            JsonObject current = known.get(key);
            if (current == null) {
                newRows.add(oldRow.deepCopy());
                known.put(key, oldRow);
            } else if (!current.get("id").equals(oldRow.get("id"))
                    || !payload(current).equals(payload(oldRow))) {
                throw new DesktopHubServer.Conflict("budget_history", key,
                    "Dwie różne wersje jednego zdarzenia Budżetu. "
                    + "Synchronizacja wstrzymana bez utraty historii.");
            }
        }
    }

    private static JsonObject checked(JsonElement element)
            throws DesktopHubServer.Conflict {
        if (element == null || !element.isJsonObject())
            throw new DesktopHubServer.Conflict("budget_history", "",
                "Nieprawidłowy rekord historii Budżetu.");
        JsonObject row = element.getAsJsonObject();
        if (!row.has("id") || !row.has("entity_key")
                || !row.has("payload") || !row.get("payload").isJsonPrimitive())
            throw new DesktopHubServer.Conflict("budget_history", "",
                "Niepełny rekord historii Budżetu.");
        return row;
    }

    private static JsonElement payload(JsonObject row)
            throws DesktopHubServer.Conflict {
        try {
            return JsonParser.parseString(row.get("payload").getAsString());
        } catch (Exception invalid) {
            throw new DesktopHubServer.Conflict("budget_history",
                row.get("entity_key").getAsString(),
                "Niepoprawne dane zdarzenia Budżetu.");
        }
    }
}
