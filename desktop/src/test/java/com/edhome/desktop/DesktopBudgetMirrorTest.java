package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBudgetMirrorTest {
    @Test void syncedBudgetIsRecognizedAndLegacyIsNotOverwritten() {
        JsonObject settings=new JsonObject();
        assertFalse(DesktopBudgetMirror.hasSharedBudget(settings));
        settings.addProperty("paycheckMonthlyBudget","[]");
        assertTrue(DesktopBudgetMirror.hasSharedBudget(settings));
        assertEquals(0,DesktopBudgetMirror.loadShared(settings).size());
    }

    @Test void recurringInvoiceAndOneTimeItemUseSameMonthsAsAndroid() {
        JsonArray items=JsonParser.parseString("""
            [
              {"id":"bill","name":"Prąd","kind":"expense","amountGrosz":10000,
               "startMonth":"2026-10","endMonth":"","active":true,"cycleMonths":1,
               "dueDay":10,"monthAmountOverrides":{"2026-10":12345},
               "invoiceDueDates":{"2026-10":"2026-10-07"}},
              {"id":"salary","name":"Pensja","kind":"income","amountGrosz":600000,
               "startMonth":"2026-10","endMonth":"","active":true,"cycleMonths":1,
               "dueDay":10},
              {"id":"once","name":"Jednorazowy zakup","kind":"expense",
               "amountGrosz":5000,"startMonth":"2026-10","endMonth":"",
               "active":true,"cycleMonths":0,"dueDay":14}
            ]
            """).getAsJsonArray();
        DesktopBudgetMirror.Summary october=DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,10));
        assertEquals(600000L,october.income);
        assertEquals(17345L,october.expenses);
        assertEquals(3,october.rows.getRowCount());
        assertEquals("2026-10-07",october.rows.getValueAt(0,0));
        DesktopBudgetMirror.Summary november=DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,11));
        assertEquals(600000L,november.income);
        assertEquals(10000L,november.expenses);
        assertEquals(2,november.rows.getRowCount());
        assertEquals("2026-11-10",november.rows.getValueAt(0,0));
    }

    @Test void calendarCorrectionsAndTerminatedDefinitionRemainHistorical() {
        JsonArray items=JsonParser.parseString("""
            [{"id":"x","name":"Rata","kind":"expense","amountGrosz":35000,
            "startMonth":"2026-01","endMonth":"","cycleMonths":1,
            "active":false,"inactiveFromMonth":"2026-12",
            "monthAmountOverrides":{"2026-09":37000},
            "amountChanges":{"2026-10":40000},
            "closedMonths":["2026-08"]}]
            """).getAsJsonArray();
        assertEquals(37000L,DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,9)).expenses);
        assertEquals(40000L,DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,10)).expenses);
        assertEquals(0L,DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,8)).expenses);
        assertEquals(0L,DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,12)).expenses);
    }

    @Test void malformedSyncedPayloadNeverBecomesASecondBudget() {
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget","{not-an-array}");
        assertThrows(RuntimeException.class,
            ()->DesktopBudgetMirror.loadShared(settings));
    }
}
