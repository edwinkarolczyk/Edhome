package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBankEvidenceBulkTest {
    private static String key(char c) {
        return String.valueOf(c).repeat(64);
    }
    private static JsonObject evidence(String id,String state) {
        JsonObject row=new JsonObject();
        row.addProperty("evidence_key",id);
        row.addProperty("state",state);
        return row;
    }
    @Test void dismissesOnlySelectedOpenAndLeavesOtherRowsUntouched() {
        JsonArray bank=new JsonArray(),transactions=new JsonArray();
        bank.add(evidence(key('a'),"open"));
        bank.add(evidence(key('b'),"open"));
        bank.add(evidence(key('c'),"matched"));
        assertEquals(1,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of(key('b'))));
        assertEquals("open",bank.get(0).getAsJsonObject().get("state").getAsString());
        assertEquals("dismissed",bank.get(1).getAsJsonObject().get("state").getAsString());
        assertEquals("matched",bank.get(2).getAsJsonObject().get("state").getAsString());
        assertTrue(bank.get(1).getAsJsonObject().get("matched_operation_id").isJsonNull());
        assertEquals(0,transactions.size());
    }
    @Test void anyMatchedOrLinkedRowAbortsWholeSelection() {
        JsonArray bank=new JsonArray(),transactions=new JsonArray();
        bank.add(evidence(key('a'),"open"));
        bank.add(evidence(key('b'),"matched"));
        assertEquals(0,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of(key('a'),key('b'))));
        assertEquals("open",bank.get(0).getAsJsonObject().get("state").getAsString());
        JsonObject tx=new JsonObject();
        tx.addProperty("statement_key",key('a'));
        tx.addProperty("status","confirmed");
        transactions.add(tx);
        assertEquals(0,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of(key('a'))));
        assertEquals("open",bank.get(0).getAsJsonObject().get("state").getAsString());
    }
    @Test void rejectMissingInvalidOrAlreadyDismissedWithoutChanges() {
        JsonArray bank=new JsonArray(),transactions=new JsonArray();
        bank.add(evidence(key('a'),"open"));
        assertEquals(0,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of(key('a'),key('d'))));
        assertEquals(0,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of("nieprawidlowy")));
        assertEquals(0,DesktopBankEvidenceBulk.dismissSelected(
            bank,transactions,Set.of()));
        assertEquals("open",bank.get(0).getAsJsonObject().get("state").getAsString());
    }
}
