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
        assertEquals("Wpływ",october.rows.getValueAt(0,3));
        assertTrue(october.rows.getValueAt(0,2).toString().startsWith("+ "));
        assertTrue(october.rows.getValueAt(1,2).toString().startsWith("− "));
        assertEquals("2026-10-10",october.rows.getValueAt(0,0));
        assertEquals("2026-10-07",october.rows.getValueAt(1,0));
        assertEquals("2026-10-14",october.rows.getValueAt(2,0));
        DesktopBudgetMirror.Summary november=DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,11));
        assertEquals(600000L,november.income);
        assertEquals(10000L,november.expenses);
        assertEquals(2,november.rows.getRowCount());
        assertEquals("Wpływ",november.rows.getValueAt(0,3));
        assertEquals("2026-11-10",november.rows.getValueAt(1,0));
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

    @Test void recipientsShowOnlyRealDirectoryWithTheirActiveBills() {
        JsonArray recipients=JsonParser.parseString("""
            [
              {"id":"r1","name":"TAURON"},
              {"id":"r2","name":"Wodociągi"},
              {"id":"r3","name":"Dostawca niewykorzystywany"}
            ]
            """).getAsJsonArray();
        JsonArray items=JsonParser.parseString("""
            [
              {"id":"a","recipientId":"r1","name":"Prąd","kind":"expense",
                "amountGrosz":12500,"startMonth":"2026-10","cycleMonths":1},
              {"id":"b","recipientId":"r1","name":"Rata","kind":"expense",
                "amountGrosz":2500,"startMonth":"2026-10","cycleMonths":1},
              {"id":"c","recipientId":"r2","name":"Woda","kind":"expense",
                "amountGrosz":8100,"startMonth":"2026-11","cycleMonths":1},
              {"id":"d","recipientId":"r2","name":"Zwrot","kind":"income",
                "amountGrosz":800,"startMonth":"2026-10","cycleMonths":1}
            ]
            """).getAsJsonArray();
        javax.swing.table.DefaultTableModel rows=
            DesktopBudgetMirror.recipientOverview(
                items,recipients,YearMonth.of(2026,10));
        assertEquals(3,rows.getRowCount());
        assertEquals("Dostawca niewykorzystywany",rows.getValueAt(0,0));
        assertEquals(0,rows.getValueAt(0,1));
        assertEquals("TAURON",rows.getValueAt(1,0));
        assertEquals(2,rows.getValueAt(1,1));
        assertEquals("− 150,00 zł",rows.getValueAt(1,2).toString().replace("\u00a0"," "));
        assertEquals("Wodociągi",rows.getValueAt(2,0));
        assertEquals(0,rows.getValueAt(2,1));
        assertEquals(1,DesktopBudgetMirror.recipientOverview(
            items,recipients,YearMonth.of(2026,11))
            .getValueAt(2,1));
    }

    @Test void incomeFirstAndExpensesSortedRegardlessOfInputOrder() {
        JsonArray items=JsonParser.parseString("""
            [
              {"id":"late","name":"Opłata B","kind":"expense","amountGrosz":10000,
               "startMonth":"2026-10","cycleMonths":1,"dueDay":29},
              {"id":"early","name":"Opłata A","kind":"expense","amountGrosz":1000,
               "startMonth":"2026-10","cycleMonths":1,"dueDay":3},
              {"id":"income","name":"Pensja","kind":"income","amountGrosz":300000,
               "startMonth":"2026-10","cycleMonths":1,"dueDay":20}
            ]
            """).getAsJsonArray();
        DesktopBudgetMirror.Summary summary=DesktopBudgetMirror.month(
            items,new JsonArray(),YearMonth.of(2026,10));
        assertEquals("Pensja",summary.rows.getValueAt(0,1));
        assertEquals("Opłata A",summary.rows.getValueAt(1,1));
        assertEquals("Opłata B",summary.rows.getValueAt(2,1));
        assertEquals(300000L,summary.income);
        assertEquals(11000L,summary.expenses);
    }

    @Test void duplicateRecipientsAreNotSilentlyMerged() {
        JsonArray directory=JsonParser.parseString("""
            [{"id":"x","name":"Pierwszy"},{"id":"x","name":"Drugi"}]
            """).getAsJsonArray();
        assertThrows(IllegalArgumentException.class,()->
            DesktopBudgetMirror.recipientOverview(
                new JsonArray(),directory,YearMonth.of(2026,10)));
    }

    @Test void malformedSyncedPayloadNeverBecomesASecondBudget() {
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget","{not-an-array}");
        assertThrows(RuntimeException.class,
            ()->DesktopBudgetMirror.loadShared(settings));
    }
}
