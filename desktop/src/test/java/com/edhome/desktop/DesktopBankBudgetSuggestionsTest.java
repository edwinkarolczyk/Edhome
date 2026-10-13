package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBankBudgetSuggestionsTest {
    private static JsonObject item(String id,String name,String kind,
            long grosz,String start,int due) {
        JsonObject item=new JsonObject();
        item.addProperty("id",id);
        item.addProperty("name",name);
        item.addProperty("kind",kind);
        item.addProperty("category","other");
        item.addProperty("amountGrosz",grosz);
        item.addProperty("amountMode","fixed");
        item.addProperty("startMonth",start);
        item.addProperty("endMonth","");
        item.addProperty("cycleMonths",0);
        item.addProperty("dueDay",due);
        item.addProperty("active",true);
        return item;
    }

    private static JsonObject evidence(String key,String date,String note,
            String kind,long grosz,String state) {
        JsonObject row=new JsonObject();
        row.addProperty("evidence_key",key);
        row.addProperty("booking_date",date);
        row.addProperty("description",note);
        row.addProperty("kind",kind);
        row.addProperty("amount_grosz",grosz);
        row.addProperty("state",state);
        return row;
    }

    private static JsonObject snapshot(JsonArray items,JsonArray evidence,
            JsonArray transactions) {
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        settings.addProperty("paycheckRecipients","[]");
        JsonObject tables=new JsonObject();
        tables.add("bank_evidence_queue",evidence);
        tables.add("paycheck_transactions",transactions);
        JsonObject root=new JsonObject();
        root.add("settings",settings);
        root.add("tables",tables);
        return root;
    }

    @Test void exactUniqueAmountDescriptionAndDateIsGreenButNeverChangesData() {
        JsonArray items=new JsonArray();
        items.add(item("11111111-1111-4111-8111-111111111111",
            "Internet Orange","expense",7999L,"2026-10",12));
        JsonArray bank=new JsonArray();
        bank.add(evidence("ev1","2026-10-12","Orange internet",
            "expense",7999L,"open"));
        JsonObject root=snapshot(items,bank,new JsonArray());
        JsonObject before=root.deepCopy();
        List<DesktopBankBudgetSuggestions.Proposal> p=
            DesktopBankBudgetSuggestions.find(root,null);
        assertEquals(1,p.size());
        assertTrue(p.get(0).confident);
        assertEquals(7999L,p.get(0).actualGrosz);
        assertEquals(before,root); // czysty podgląd bez księgowania
    }

    @Test void differentBankAmountIsYellowAndKeepsRealBankAmount() {
        JsonArray items=new JsonArray();
        items.add(item("11111111-1111-4111-8111-111111111111",
            "Wypłata Metalbox","income",450000L,"2026-10",10));
        JsonArray bank=new JsonArray();
        bank.add(evidence("ev2","2026-10-09","Metalbox wynagrodzenie",
            "income",430000L,"open"));
        List<DesktopBankBudgetSuggestions.Proposal> p=
            DesktopBankBudgetSuggestions.find(snapshot(items,bank,new JsonArray()),null);
        assertEquals(1,p.size());
        assertFalse(p.get(0).confident);
        assertEquals(450000L,p.get(0).plannedGrosz);
        assertEquals(430000L,p.get(0).actualGrosz);
    }

    @Test void SameAmountDifferentRecipientOrAmbiguousCandidatesRemainYellow() {
        JsonArray items=new JsonArray();
        items.add(item("11111111-1111-4111-8111-111111111111",
            "Internet Orange","expense",7999L,"2026-10",12));
        items.add(item("22222222-2222-4222-8222-222222222222",
            "Internet Orange","expense",7999L,"2026-10",12));
        JsonArray bank=new JsonArray();
        bank.add(evidence("ev3","2026-10-12","Internet Orange",
            "expense",7999L,"open"));
        List<DesktopBankBudgetSuggestions.Proposal> p=
            DesktopBankBudgetSuggestions.find(snapshot(items,bank,new JsonArray()),null);
        assertEquals(2,p.size());
        assertTrue(p.stream().noneMatch(x->x.confident));
    }

    @Test void noCrossDirectionReusingConfirmedEvidenceOrIgnoringOldMonths() {
        JsonArray items=new JsonArray();
        items.add(item("11111111-1111-4111-8111-111111111111",
            "Zakupy Market","expense",20000L,"2025-12",12));
        JsonArray bank=new JsonArray();
        bank.add(evidence("historic","2025-12-12","Zakupy Market",
            "expense",20000L,"open"));
        bank.add(evidence("other","2025-12-12","Zakupy Market",
            "income",20000L,"open"));
        bank.add(evidence("closed","2025-12-12","Zakupy Market",
            "expense",20000L,"matched"));
        JsonArray transactions=new JsonArray();
        JsonObject confirmed=new JsonObject();
        confirmed.addProperty("statement_key","historic");
        confirmed.addProperty("status","confirmed");
        transactions.add(confirmed);
        JsonObject root=snapshot(items,bank,transactions);
        assertTrue(DesktopBankBudgetSuggestions.find(root,null).isEmpty());
        assertTrue(DesktopBankBudgetSuggestions.find(root,Set.of("closed")).isEmpty());

        // Zachowaj możliwość znalezienia przelewu z dawnego roku.
        transactions.remove(0);
        List<DesktopBankBudgetSuggestions.Proposal> old=
            DesktopBankBudgetSuggestions.find(root,Set.of("historic"));
        assertEquals(1,old.size());
        assertEquals("2025-12-12",old.get(0).date);
    }

    @Test void exactAmountWithoutRecipientAgreementDoesNotTurnGreen() {
        JsonArray items=new JsonArray();
        items.add(item("11111111-1111-4111-8111-111111111111",
            "Prąd Tauron","expense",12000L,"2026-10",13));
        JsonArray bank=new JsonArray();
        bank.add(evidence("ev5","2026-10-13","Inny odbiorca",
            "expense",12000L,"open"));
        List<DesktopBankBudgetSuggestions.Proposal> p=
            DesktopBankBudgetSuggestions.find(snapshot(items,bank,new JsonArray()),null);
        assertEquals(1,p.size());
        assertFalse(p.get(0).confident);
    }
}
