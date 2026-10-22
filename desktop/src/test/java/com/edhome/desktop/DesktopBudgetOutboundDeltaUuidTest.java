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

    @Test void acceptsCanonicalUuidAndCreatesOneOperation() throws Exception {
        JsonObject before = snapshot(null);
        JsonObject edited = snapshot("12345678-1234-1234-1234-1234567890ab");
        DesktopBudgetOutboundDelta.Result result = DesktopBudgetOutboundDelta.build(before, edited);
        assertNotNull(result);
        assertEquals(1, result.changes);
    }
}
