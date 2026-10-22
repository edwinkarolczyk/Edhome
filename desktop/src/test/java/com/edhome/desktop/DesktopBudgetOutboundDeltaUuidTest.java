package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopBudgetOutboundDeltaUuidTest {
    private JsonObject snapshot(String id) {
        JsonObject root = new JsonObject();
        JsonObject settings = new JsonObject();
        for (String field : new String[]{"paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"}) {
            JsonArray rows = new JsonArray();
            if ("paycheckMonthlyBudget".equals(field) && id != null) {
                JsonObject item = new JsonObject();
                item.addProperty("id", id);
                rows.add(item);
            }
            settings.addProperty(field, rows.toString());
        }
        root.add("settings", settings);
        root.add("tables", new JsonObject());
        return root;
    }

    @Test void rejectsMalformedUuidWithoutMutatingSnapshots() throws Exception {
        JsonObject before = snapshot(null);
        JsonObject edited = snapshot("12345678-1234-1234-1234-12345678901Z");
        String original = edited.toString();
        assertNull(DesktopBudgetOutboundDelta.build(before, edited));
        assertEquals(original, edited.toString());
    }

    @Test void rejectsChangedBudgetTableInsteadOfSilentlyDroppingRows() throws Exception {
        JsonObject before = snapshot(null);
        JsonObject edited = snapshot("12345678-1234-1234-1234-1234567890ab");
        JsonArray oldRows = new JsonArray();
        JsonArray newRows = new JsonArray();
        JsonObject row = new JsonObject();
        row.addProperty("id", 1);
        newRows.add(row);
        before.getAsJsonObject("tables").add("budget_entries", oldRows);
        edited.getAsJsonObject("tables").add("budget_entries", newRows);
        assertNull(DesktopBudgetOutboundDelta.build(before, edited));
        assertEquals(1, edited.getAsJsonObject("tables").getAsJsonArray("budget_entries").size());
    }

    @Test void rejectsCaseVariantDuplicateUuid() throws Exception {
        JsonObject before = snapshot(null);
        JsonObject edited = snapshot("12345678-1234-1234-1234-abcdefabcdef");
        JsonArray rows = new JsonArray();
        JsonObject first = new JsonObject();
        first.addProperty("id", "12345678-1234-1234-1234-abcdefabcdef");
        JsonObject second = new JsonObject();
        second.addProperty("id", "12345678-1234-1234-1234-ABCDEFABCDEF");
        rows.add(first);
        rows.add(second);
        edited.getAsJsonObject("settings").addProperty("paycheckMonthlyBudget", rows.toString());
        assertNull(DesktopBudgetOutboundDelta.build(before, edited));
    }

    @Test void caseChangeOfUuidDoesNotProduceDeleteAndInsert() throws Exception {
        JsonObject before = snapshot("12345678-1234-1234-1234-abcdefabcdef");
        JsonObject edited = snapshot("12345678-1234-1234-1234-ABCDEFABCDEF");
        DesktopBudgetOutboundDelta.Result result = DesktopBudgetOutboundDelta.build(before, edited);
        assertNotNull(result);
        assertEquals(1, result.changes);
        JsonArray changes = result.payload.getAsJsonObject("budgetDelta")
                .getAsJsonArray("paycheckMonthlyBudget");
        assertEquals(1, changes.size());
        JsonObject operation = changes.get(0).getAsJsonObject();
        assertTrue(operation.get("before").isJsonObject());
        assertTrue(operation.get("after").isJsonObject());
    }

    @Test void acceptsCanonicalUuidAndCreatesOneOperation() throws Exception {
        JsonObject before = snapshot(null);
        JsonObject edited = snapshot("12345678-1234-1234-1234-1234567890ab");
        DesktopBudgetOutboundDelta.Result result = DesktopBudgetOutboundDelta.build(before, edited);
        assertNotNull(result);
        assertEquals(1, result.changes);
    }
}
