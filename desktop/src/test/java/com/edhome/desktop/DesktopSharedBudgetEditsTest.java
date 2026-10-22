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
        settings.addProperty("paycheckRecipients","[]");
        JsonObject tables=new JsonObject();
        tables.add("budget_items",dbItems);
        tables.add("budget_occurrences",new JsonArray());
        tables.add("budget_history",new JsonArray());
        tables.add("budget_recipients",new JsonArray());
        JsonArray transactions=new JsonArray();
        JsonObject bank=new JsonObject();
        bank.addProperty("operation_id","22222222-2222-4222-8222-222222222222");
        bank.addProperty("amount_grosz",9000L);
        bank.addProperty("status","confirmed");
        bank.addProperty("scope","shared");
        bank.addProperty("kind","income");
        transactions.add(bank);
        tables.add("paycheck_transactions",transactions);
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

    @Test void desktopCanCreateRecurringBudgetWithoutChangingBankBalance()
            throws Exception {
        JsonObject before=initial();
        JsonArray oldTransactions=before.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions").deepCopy();
        JsonObject after=DesktopSharedBudgetEdits.createItem(
            before,"Internet","expense","subscriptions",7999L,"estimate",
            YearMonth.of(2026,10),null,1,12,false,false,0,"");

        assertEquals(1,DesktopBudgetMirror.month(
            DesktopBudgetMirror.loadShared(before.getAsJsonObject("settings")),
            new JsonArray(),YearMonth.of(2026,10)).rows.getRowCount());
        JsonArray newItems=DesktopBudgetMirror.loadShared(
            after.getAsJsonObject("settings"));
        assertEquals(2,newItems.size());
        JsonObject newItem=newItems.get(1).getAsJsonObject();
        assertEquals(7999L,newItem.get("amountGrosz").getAsLong());
        assertEquals("estimate",newItem.get("amountMode").getAsString());
        assertEquals(12,newItem.get("templateDueDay").getAsInt());
        assertTrue(newItem.getAsJsonArray("matches").isEmpty());
        assertEquals("ITEM_CREATED",JsonParser.parseString(
            after.getAsJsonObject("tables").getAsJsonArray("budget_history")
                .get(0).getAsJsonObject().get("payload").getAsString())
            .getAsJsonObject().get("type").getAsString());
        assertEquals(2,after.getAsJsonObject("tables")
            .getAsJsonArray("budget_items").size());
        assertEquals(1,after.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
        assertEquals(oldTransactions,after.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions"));
        assertEquals(oldTransactions,before.getAsJsonObject("tables")
            .getAsJsonArray("paycheck_transactions"));
        assertEquals(2,DesktopBudgetMirror.month(newItems,new JsonArray(),
            YearMonth.of(2026,11)).rows.getRowCount());
        DesktopBudgetIntegrity.assertSidecars(after);
        // Dodanie pozycji zmienia również tabele SQLite budget_*.
        // Delta obejmująca wyłącznie settings nie może pomijać tych zmian.
        // Pełna synchronizacja rekordów musi obsłużyć spójny zestaw.
        DesktopBudgetOutboundDelta.Result delta=
            DesktopBudgetOutboundDelta.build(before,after);
        assertNull(delta);
    }

    @Test void desktopCreatesSingleIncomeOnlyInSelectedMonth()
            throws Exception {
        JsonObject after=DesktopSharedBudgetEdits.createItem(
            initial(),"Premia","income","salary",40000L,"fixed",
            YearMonth.of(2026,11),null,0,18,false,false,0,"");
        JsonArray items=DesktopBudgetMirror.loadShared(
            after.getAsJsonObject("settings"));
        assertEquals(1,DesktopBudgetMirror.month(items,new JsonArray(),
            YearMonth.of(2026,10)).rows.getRowCount());
        assertEquals(2,DesktopBudgetMirror.month(items,new JsonArray(),
            YearMonth.of(2026,11)).rows.getRowCount());
        assertEquals(1,DesktopBudgetMirror.month(items,new JsonArray(),
            YearMonth.of(2026,12)).rows.getRowCount());
        DesktopBudgetIntegrity.assertSidecars(after);
    }

    @Test void desktopRejectsDuplicatesAndInvalidInstallmentsWithoutSideEffects()
            throws Exception {
        JsonObject before=initial();
        JsonObject created=DesktopSharedBudgetEdits.createItem(
            before,"Woda","expense","utilities",4500L,"fixed",
            YearMonth.of(2026,10),null,1,5,false,false,0,"");
        assertThrows(IllegalArgumentException.class,
            ()->DesktopSharedBudgetEdits.createItem(
                created,"WODA","expense","utilities",4500L,"fixed",
                YearMonth.of(2026,10),null,1,5,false,false,0,""));
        assertThrows(IllegalArgumentException.class,
            ()->DesktopSharedBudgetEdits.createItem(
                before,"Rata","income","loans",30000L,"fixed",
                YearMonth.of(2026,10),null,1,5,false,true,12,""));
        assertThrows(IllegalArgumentException.class,
            ()->DesktopSharedBudgetEdits.createItem(
                before,"Rachunek","expense","bills",2000L,"fixed",
                YearMonth.of(2026,10),null,1,5,false,false,0,
                "33333333-3333-4333-8333-333333333333"));
        assertEquals(1,before.getAsJsonObject("tables")
            .getAsJsonArray("budget_items").size());
        assertEquals(0,before.getAsJsonObject("tables")
            .getAsJsonArray("budget_history").size());
    }

    @Test void desktopCreatesInstallmentWithAndroidCompatibleFields()
            throws Exception {
        JsonObject after=DesktopSharedBudgetEdits.createItem(
            initial(),"Rata lodówki","expense","loans",15000L,"fixed",
            YearMonth.of(2026,10),null,1,21,false,true,10,"");
        JsonObject item=DesktopBudgetMirror.loadShared(
            after.getAsJsonObject("settings")).get(1).getAsJsonObject();
        assertTrue(item.get("installment").getAsBoolean());
        assertEquals(10,item.get("installmentCount").getAsInt());
        assertEquals(2,DesktopBudgetMirror.month(
            DesktopBudgetMirror.loadShared(after.getAsJsonObject("settings")),
            new JsonArray(),YearMonth.of(2027,7)).rows.getRowCount());
        assertEquals(1,DesktopBudgetMirror.month(
            DesktopBudgetMirror.loadShared(after.getAsJsonObject("settings")),
            new JsonArray(),YearMonth.of(2027,8)).rows.getRowCount());
    }

}
