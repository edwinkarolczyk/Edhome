package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBudgetIntegrityTest {
    private static final String OP="11111111-1111-4111-8111-111111111111";

    private JsonObject item(String id,long allocated) {
        JsonObject item=new JsonObject();
        item.addProperty("id",id);
        item.addProperty("kind","expense");
        JsonArray matches=new JsonArray();
        matches.add(OP);
        item.add("matches",matches);
        JsonObject alloc=new JsonObject();
        if(allocated>0)alloc.addProperty(OP,allocated);
        item.add("allocations",alloc);
        return item;
    }

    private JsonObject state(long allocationA,long allocationB) {
        JsonArray items=new JsonArray();
        items.add(item("22222222-2222-4222-8222-222222222222",allocationA));
        items.add(item("33333333-3333-4333-8333-333333333333",allocationB));
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        JsonObject transaction=new JsonObject();
        transaction.addProperty("operation_id",OP);
        transaction.addProperty("scope","shared");
        transaction.addProperty("kind","expense");
        transaction.addProperty("status","confirmed");
        transaction.addProperty("amount_grosz",10000);
        JsonArray transactions=new JsonArray();
        transactions.add(transaction);
        JsonObject tables=new JsonObject();
        tables.add("paycheck_transactions",transactions);
        JsonObject state=new JsonObject();
        state.add("settings",settings);
        state.add("tables",tables);
        return state;
    }

    @Test void partialAllocationsCanUseOneConfirmedPaymentExactlyOnce()
            throws Exception {
        assertDoesNotThrow(()->DesktopBudgetIntegrity.assertAllocations(
            state(4000,6000)));
    }

    @Test void disconnectedDevicesMustNotDoubleSpendSamePayment() {
        DesktopHubServer.Conflict error=assertThrows(
            DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertAllocations(state(7000,5000)));
        assertEquals("paycheck_transactions",error.table);
        assertEquals(OP,error.rowKey);
    }

    @Test void offlineSurplusCannotBeSpentTwiceAlongsideAnotherAllocation() {
        JsonObject state=state(4000,2000);
        JsonObject first=JsonParser.parseString(
            state.getAsJsonObject("settings")
                .get("paycheckMonthlyBudget").getAsString())
            .getAsJsonArray().get(0).getAsJsonObject();
        JsonObject surplus=new JsonObject();
        surplus.addProperty(OP,6000);
        first.add("splitSurplusesGrosz",surplus);
        JsonArray updated=new JsonArray();
        updated.add(first);
        updated.add(item("33333333-3333-4333-8333-333333333333",2000));
        state.getAsJsonObject("settings").addProperty(
            "paycheckMonthlyBudget",updated.toString());
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertAllocations(state));
    }

    @Test void oneAllocationAndItsSurplusMayEqualPaymentExactly()
            throws Exception {
        JsonObject state=state(4000,0);
        JsonObject single=item("22222222-2222-4222-8222-222222222222",4000);
        JsonObject surplus=new JsonObject();
        surplus.addProperty(OP,6000);
        single.add("splitSurplusesGrosz",surplus);
        JsonArray rows=new JsonArray();
        rows.add(single);
        state.getAsJsonObject("settings").addProperty(
            "paycheckMonthlyBudget",rows.toString());
        assertDoesNotThrow(()->DesktopBudgetIntegrity.assertAllocations(state));
    }

    @Test void twoFullAssignmentsCannotCountSamePaymentTwice() {
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertAllocations(state(0,0)));
    }

    @Test void unconfirmedPaymentsDoNotCountAsSettled() {
        JsonObject state=state(0,0);
        state.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions").get(0).getAsJsonObject()
            .addProperty("status","pending");
        assertDoesNotThrow(()->DesktopBudgetIntegrity.assertAllocations(state));
    }

    @Test void oldArchivedEventsMayOutliveTrimmedSettingsCache() {
        JsonObject state=state(4000,6000);
        JsonObject tables=state.getAsJsonObject("tables");
        JsonObject settings=state.getAsJsonObject("settings");
        JsonObject oldEvent=new JsonObject();
        oldEvent.addProperty("id","44444444-4444-4444-8444-444444444444");
        oldEvent.addProperty("amountGrosz",500);
        JsonObject historyRow=new JsonObject();
        historyRow.addProperty("id",123L);
        historyRow.addProperty("entity_key",oldEvent.get("id").getAsString());
        historyRow.addProperty("payload",oldEvent.toString());
        JsonArray archive=new JsonArray();
        archive.add(historyRow);
        tables.add("budget_history",archive);
        settings.addProperty("paycheckBudgetHistory","[]");
        assertDoesNotThrow(()->DesktopBudgetIntegrity.assertSidecars(state));
    }

    @Test void inconsistentSidecarNeverAcknowledgesFinancialPatch() {
        JsonObject state=state(4000,6000);
        JsonObject item=item("22222222-2222-4222-8222-222222222222",4000);
        JsonArray settingsItems=new JsonArray();
        settingsItems.add(item);
        state.getAsJsonObject("settings").addProperty(
            "paycheckMonthlyBudget",settingsItems.toString());
        JsonObject changed=item.deepCopy();
        changed.getAsJsonObject("allocations").addProperty(OP,9500);
        JsonObject row=new JsonObject();
        row.addProperty("id",123L);
        row.addProperty("entity_key",item.get("id").getAsString());
        row.addProperty("payload",changed.toString());
        JsonArray stored=new JsonArray();
        stored.add(row);
        state.getAsJsonObject("tables").add("budget_items",stored);
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertSidecars(state));
    }

    @Test void wrongPaymentKindIsNotSilentlyAccepted() {
        JsonObject state=state(4000,6000);
        state.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions").get(0).getAsJsonObject()
            .addProperty("kind","income");
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertAllocations(state));
    }
}
