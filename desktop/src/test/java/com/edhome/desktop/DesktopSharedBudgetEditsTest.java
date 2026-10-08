package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopSharedBudgetEditsTest {
    private static final String ID="11111111-1111-4111-8111-111111111111";

    private static JsonObject initial() {
        JsonObject item=JsonParser.parseString("""
            {"id":"11111111-1111-4111-8111-111111111111",
             "name":"Energia","kind":"expense","category":"utilities",
             "amountGrosz":12000,"amountMode":"fixed",
             "startMonth":"2026-10","endMonth":"","cycleMonths":1,
             "dueDay":5,"active":true,"recipientId":"",
             "monthAmountOverrides":{}}
            """).getAsJsonObject();
        JsonArray items=new JsonArray();
        items.add(item);
        JsonObject dbRow=new JsonObject();
        dbRow.addProperty("id",1234L);
        dbRow.addProperty("entity_key",ID);
        dbRow.addProperty("payload",item.toString());
        JsonArray dbItems=new JsonArray();
        dbItems.add(dbRow);

        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        settings.addProperty("paycheckBudgetHistory","[]");
        JsonObject tables=new JsonObject();
        tables.add("budget_items",dbItems);
        tables.add("budget_occurrences",new JsonArray());
        tables.add("budget_history",new JsonArray());
        JsonObject root=new JsonObject();
        root.add("settings",settings);
        root.add("tables",tables);
        root.add("syncRecords",new JsonArray());
        return root;
    }

    @Test void monthUpdateChangesBothStoresAndRecordsAudit() throws Exception {
        JsonObject before=initial();
        JsonObject after=DesktopSharedBudgetEdits.changeMonth(
            before,ID,"Energia i prąd",YearMonth.of(2026,10),13999);
        assertNotSame(before,after);
        assertEquals("Energia",JsonParser.parseString(
            before.getAsJsonObject("settings").get("paycheckMonthlyBudget")
                .getAsString()).getAsJsonArray().get(0)
                .getAsJsonObject().get("name").getAsString());

        JsonObject stored=JsonParser.parseString(after.getAsJsonObject("tables")
            .getAsJsonArray("budget_items").get(0).getAsJsonObject()
            .get("payload").getAsString()).getAsJsonObject();
        assertEquals("Energia i prąd",stored.get("name").getAsString());
        assertEquals(13999L,stored.getAsJsonObject("monthAmountOverrides")
            .get("2026-10").getAsLong());
        assertEquals(1,after.getAsJsonObject("tables")
            .getAsJsonArray("budget_occurrences").size());
        assertEquals(2,after.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
        assertEquals(2,JsonParser.parseString(after.getAsJsonObject("settings")
            .get("paycheckBudgetHistory").getAsString()).getAsJsonArray().size());
        assertEquals(0,before.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
    }

    @Test void unchangedItemDoesNotCreateDuplicateEvents() throws Exception {
        JsonObject before=initial();
        assertSame(before,DesktopSharedBudgetEdits.changeMonth(before,ID,
            "Energia",YearMonth.of(2026,10),12000));
    }

    @Test void invalidAmountCannotMutateExistingSnapshot() {
        JsonObject before=initial();
        assertThrows(IllegalArgumentException.class,
            ()->DesktopSharedBudgetEdits.changeMonth(before,ID,
                "Prąd",YearMonth.of(2026,10),-10));
        assertEquals(0,before.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
    }
}
