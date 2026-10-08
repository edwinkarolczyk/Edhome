package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

    @Test void wrongPaymentKindIsNotSilentlyAccepted() {
        JsonObject state=state(4000,6000);
        state.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions").get(0).getAsJsonObject()
            .addProperty("kind","income");
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetIntegrity.assertAllocations(state));
    }
}
