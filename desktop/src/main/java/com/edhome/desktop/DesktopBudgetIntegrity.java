package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sprawdza połączone alokacje po scaleniu zmian offline.
 * Dwa różne wiersze budżetu nie mogą razem użyć więcej pieniędzy,
 * niż wynosi jedna potwierdzona transakcja PayCheck.
 */
final class DesktopBudgetIntegrity {
    private DesktopBudgetIntegrity() { }

    static void assertAllocations(JsonObject state)
            throws DesktopHubServer.Conflict {
        if(state==null||!state.has("settings")||!state.has("tables"))
            throw conflict("Brak danych PayCheck.");
        JsonObject settings=state.getAsJsonObject("settings");
        if(!settings.has("paycheckMonthlyBudget"))return;
        JsonObject tables=state.getAsJsonObject("tables");
        if(!tables.has("paycheck_transactions"))return;
        JsonArray payments=tables.getAsJsonArray("paycheck_transactions");
        Map<String,Payment> confirmed=new HashMap<>();
        for(JsonElement entry:payments) {
            if(!entry.isJsonObject())continue;
            JsonObject row=entry.getAsJsonObject();
            if(!"shared".equals(string(row,"scope"))
                    ||!"confirmed".equals(string(row,"status")))continue;
            String id=string(row,"operation_id");
            long amount=number(row,"amount_grosz");
            if(id.isBlank()||amount<=0L)
                throw conflict("Niepoprawna potwierdzona transakcja PayCheck.");
            if(confirmed.put(id,new Payment(amount,string(row,"kind")))!=null)
                throw conflict("Powtórzony identyfikator operacji PayCheck.");
        }

        final JsonArray items;
        try {
            items=JsonParser.parseString(settings.get(
                "paycheckMonthlyBudget").getAsString()).getAsJsonArray();
        } catch(Exception invalid) {
            throw conflict("Nieprawidłowe dane Budżetu.");
        }
        Map<String,Long> used=new HashMap<>();
        for(JsonElement entry:items) {
            if(!entry.isJsonObject())
                throw conflict("Nieprawidłowa pozycja Budżetu.");
            JsonObject item=entry.getAsJsonObject();
            JsonArray matches=item.has("matches")&&item.get("matches").isJsonArray()
                ?item.getAsJsonArray("matches"):new JsonArray();
            JsonObject allocation=item.has("allocations")&&item.get("allocations").isJsonObject()
                ?item.getAsJsonObject("allocations"):new JsonObject();
            Set<String> seen=new HashSet<>();
            for(JsonElement match:matches) {
                if(!match.isJsonPrimitive())
                    throw conflict("Niepoprawny identyfikator płatności w budżecie.");
                String id=match.getAsString();
                if(!seen.add(id))
                    throw conflict("Płatność jest przypisana dwa razy w jednej pozycji.");
                Payment payment=confirmed.get(id);
                if(payment==null)continue; // starsza, lokalna lub prywatna transakcja
                if(!payment.kind.equals(string(item,"kind")))
                    throw conflict("Typ płatności nie zgadza się z pozycją Budżetu.");
                long allocated;
                try {
                    allocated=allocation.has(id)?allocation.get(id).getAsLong()
                        :payment.amount;
                    if(allocated<=0L||allocated>payment.amount)
                        throw new ArithmeticException("Kwota poza zakresem.");
                    long total=Math.addExact(used.getOrDefault(id,0L),allocated);
                    if(total>payment.amount)
                        throw new ArithmeticException("Podwójne rozliczenie.");
                    used.put(id,total);
                } catch(Exception invalid) {
                    throw new DesktopHubServer.Conflict(
                        "paycheck_transactions",id,
                        "Podwójna płatność lub nadmierna alokacja — "
                        +"wstrzymano synchronizację bez zmiany danych.");
                }
            }
        }
    }

    private static String string(JsonObject obj,String key) {
        if(!obj.has(key)||obj.get(key).isJsonNull())return "";
        return obj.get(key).getAsString();
    }
    private static long number(JsonObject obj,String key) {
        if(!obj.has(key)||obj.get(key).isJsonNull())return 0L;
        try{return obj.get(key).getAsLong();}
        catch(Exception invalid){return 0L;}
    }
    private static DesktopHubServer.Conflict conflict(String message) {
        return new DesktopHubServer.Conflict("paycheckMonthlyBudget","",message);
    }
    private static final class Payment {
        final long amount;
        final String kind;
        Payment(long amount,String kind){this.amount=amount;this.kind=kind;}
    }
}
