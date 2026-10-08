package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DesktopBudgetHistoryArchiveTest {
    private JsonObject state(String entries) {
        return JsonParser.parseString(
            "{\"tables\":{\"budget_history\":["+entries+"]},\"syncRecords\":[]}").getAsJsonObject();
    }

    private String row(long id,String key,long amount) {
        JsonObject payload=new JsonObject();
        payload.addProperty("id",key);
        payload.addProperty("amountGrosz",amount);
        JsonObject row=new JsonObject();
        row.addProperty("id",id);
        row.addProperty("entity_key",key);
        row.addProperty("payload",payload.toString());
        return row.toString();
    }

    @Test void combinesTwoOfflineHistoriesWithoutDeletingEvents() throws Exception {
        JsonObject old=state(row(1,"old-event",100));
        JsonObject incoming=state(row(2,"new-event",200));
        DesktopBudgetHistoryArchive.retain(old,incoming);
        JsonArray merged=incoming.getAsJsonObject("tables").getAsJsonArray("budget_history");
        assertEquals(2,merged.size());
        assertEquals("new-event",merged.get(0).getAsJsonObject()
            .get("entity_key").getAsString());
        assertEquals("old-event",merged.get(1).getAsJsonObject()
            .get("entity_key").getAsString());
        assertEquals(1,old.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
    }

    @Test void duplicateSameEventDoesNotDoubleCount() throws Exception {
        JsonObject old=state(row(1,"shared-event",500));
        JsonObject incoming=state(row(1,"shared-event",500));
        DesktopBudgetHistoryArchive.retain(old,incoming);
        assertEquals(1,incoming.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
    }

    @Test void conflictingSameIdStopsFullSnapshot() {
        JsonObject old=state(row(1,"shared-event",500));
        JsonObject incoming=state(row(1,"shared-event",900));
        DesktopHubServer.Conflict ex=assertThrows(
            DesktopHubServer.Conflict.class,
            ()->DesktopBudgetHistoryArchive.retain(old,incoming));
        assertEquals("budget_history",ex.table);
        assertEquals("shared-event",ex.rowKey);
    }

    @Test void absentHistoryTableIsNotAReasonToDeleteOldEvents() {
        JsonObject old=state(row(1,"old-event",100));
        JsonObject incoming=JsonParser.parseString(
            "{\"tables\":{},\"syncRecords\":[]}").getAsJsonObject();
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetHistoryArchive.retain(old,incoming));
    }
}
