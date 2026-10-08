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

    /**
     * Ustawienia (stary JSON) i tabele 5A muszą opisywać te same pozycje.
     * Inaczej patch może wyglądać na udany, a kolejny eksport skasuje zmianę.
     * Archiwum historii zawiera też zdarzenia starsze niż ostatnie 6000.
     */
    static void assertSidecars(JsonObject snapshot)
            throws DesktopHubServer.Conflict {
        if(snapshot==null||!snapshot.has("settings")||!snapshot.has("tables"))
            throw conflict("Brak struktury kopii Budżetu.");
        JsonObject settings=snapshot.getAsJsonObject("settings");
        JsonObject tables=snapshot.getAsJsonObject("tables");
        String[][] fields={
            {"paycheckMonthlyBudget","budget_items"},
            {"paycheckRecipients","budget_recipients"},
            {"paycheckBudgetHistory","budget_history"}
        };
        for(String[] pair:fields) {
            String setting=pair[0],table=pair[1];
            if(!tables.has(table))continue; // starsza kopia sprzed SQLite 5A
            if(!settings.has(setting)||!settings.get(setting).isJsonPrimitive()
                    ||!tables.get(table).isJsonArray())
                throw conflict("Niespójna struktura "+table+".");
            final JsonArray cache;
            try {
                cache=JsonParser.parseString(
                    settings.get(setting).getAsString()).getAsJsonArray();
            } catch(Exception invalid) {
                throw conflict("Niespójna zawartość "+setting+".");
            }
            Map<String,JsonObject> persisted=new HashMap<>();
            for(JsonElement element:tables.getAsJsonArray(table)) {
                if(!element.isJsonObject())
                    throw conflict("Uszkodzony rekord "+table+".");
                JsonObject row=element.getAsJsonObject();
                try {
                    String key=string(row,"entity_key");
                    JsonObject payload=JsonParser.parseString(
                        string(row,"payload")).getAsJsonObject();
                    if(key.isBlank()||!key.equals(string(payload,"id"))
                            ||persisted.put(key,payload)!=null)
                        throw new IllegalArgumentException();
                } catch(Exception invalid) {
                    throw conflict("Niespójny identyfikator lub duplikat w "+table+".");
                }
            }
            Set<String> seen=new HashSet<>();
            for(JsonElement element:cache) {
                if(!element.isJsonObject())
                    throw conflict("Uszkodzony element "+setting+".");
                JsonObject data=element.getAsJsonObject();
                String id=string(data,"id");
                if(id.isBlank()||!seen.add(id)
                        ||!data.equals(persisted.get(id)))
                    throw conflict("Ustawienia i SQLite są niespójne dla "+setting
                        +" (pozycja "+id+").");
            }
            if(!"budget_history".equals(table)&&persisted.size()!=seen.size())
                throw conflict("W SQLite pozostały niezgodne rekordy "+table+".");
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
