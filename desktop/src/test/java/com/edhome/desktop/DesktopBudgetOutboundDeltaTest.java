package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBudgetOutboundDeltaTest {
    private static final String ID="11111111-1111-4111-8111-111111111111";

    private JsonObject snapshot(long amount) {
        JsonObject item=new JsonObject();
        item.addProperty("id",ID);
        item.addProperty("name","Energia");
        item.addProperty("amountGrosz",amount);
        JsonArray items=new JsonArray();
        items.add(item);
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        settings.addProperty("paycheckRecipients","[]");
        settings.addProperty("paycheckBudgetHistory","[]");
        settings.addProperty("household","Dom");
        JsonObject tables=new JsonObject();
        tables.add("budget_items",new JsonArray());
        tables.add("budget_occurrences",new JsonArray());
        tables.add("budget_recipients",new JsonArray());
        tables.add("budget_credits",new JsonArray());
        tables.add("budget_history",new JsonArray());
        tables.add("paycheck_transactions",new JsonArray());
        JsonObject snapshot=new JsonObject();
        snapshot.add("settings",settings);
        snapshot.add("tables",tables);
        return snapshot;
    }

    @Test void amountEditUsesOnlyOneBudgetDeltaNotFullSnapshot()
            throws Exception {
        JsonObject before=snapshot(10000);
        JsonObject after=snapshot(12500);
        DesktopBudgetOutboundDelta.Result result=
            DesktopBudgetOutboundDelta.build(before,after);
        assertNotNull(result);
        assertEquals(1,result.changes);
        assertEquals(0,result.payload.getAsJsonArray("operations").size());
        JsonObject changes=result.payload.getAsJsonObject("budgetDelta");
        assertEquals(1,changes.getAsJsonArray("paycheckMonthlyBudget").size());
        JsonObject changed=changes.getAsJsonArray("paycheckMonthlyBudget")
            .get(0).getAsJsonObject();
        assertEquals(10000,changed.getAsJsonObject("before")
            .get("amountGrosz").getAsLong());
        assertEquals(12500,changed.getAsJsonObject("after")
            .get("amountGrosz").getAsLong());
        assertEquals(10000,before.getAsJsonObject("settings")
            .get("paycheckMonthlyBudget").getAsString()
            .contains("10000") ? 10000 : -1);
    }

    @Test void unrelatedSettingsDoNotUseBudgetOnlyEndpoint() throws Exception {
        JsonObject before=snapshot(10000);
        JsonObject after=snapshot(12500);
        after.getAsJsonObject("settings").addProperty("household","Inny dom");
        assertNull(DesktopBudgetOutboundDelta.build(before,after));
    }

    @Test void differentFinancialTableRequiresIndependentSync() throws Exception {
        JsonObject before=snapshot(10000);
        JsonObject after=snapshot(12500);
        JsonObject operation=new JsonObject();
        operation.addProperty("id",1);
        after.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions").add(operation);
        assertNull(DesktopBudgetOutboundDelta.build(before,after));
    }

    @Test void historyCacheTrimNeverGeneratesDeletion() throws Exception {
        JsonObject before=snapshot(10000);
        JsonObject after=snapshot(10000);
        String eventId="22222222-2222-4222-8222-222222222222";
        JsonObject event=new JsonObject();
        event.addProperty("id",eventId);
        JsonArray archived=new JsonArray();
        archived.add(event);
        before.getAsJsonObject("settings")
            .addProperty("paycheckBudgetHistory",archived.toString());
        assertNull(DesktopBudgetOutboundDelta.build(before,after));
    }

    @Test void historyAppendProducesSingleUuidOperation() throws Exception {
        JsonObject before=snapshot(10000);
        JsonObject after=snapshot(10000);
        JsonObject event=new JsonObject();
        event.addProperty("id","22222222-2222-4222-8222-222222222222");
        JsonArray added=new JsonArray();
        added.add(event);
        after.getAsJsonObject("settings")
            .addProperty("paycheckBudgetHistory",added.toString());
        DesktopBudgetOutboundDelta.Result result=
            DesktopBudgetOutboundDelta.build(before,after);
        assertNotNull(result);
        assertEquals(1,result.changes);
        assertTrue(result.payload.getAsJsonObject("budgetDelta")
            .has("paycheckBudgetHistory"));
    }
}
