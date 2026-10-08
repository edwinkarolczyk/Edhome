package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etap 5C: scalenie wyłącznie zmienionych pozycji wspólnego Budżetu.
 * Zdarzenia historii są append-only, a istniejące pozycje wymagają zgodności
 * z wersją bazową. Nie ma automatycznego nadpisywania wpłat ani nadpłat.
 */
final class DesktopBudgetDelta {
    private static final String ITEMS="paycheckMonthlyBudget";
    private static final String RECIPIENTS="paycheckRecipients";
    private static final String HISTORY="paycheckBudgetHistory";
    private DesktopBudgetDelta() { }

    static int apply(JsonObject snapshot,JsonObject delta)
            throws DesktopHubServer.Conflict {
        if(delta==null||delta.size()==0)
            throw conflict("budget","", "Pusty pakiet zmian budżetu.");
        if(snapshot==null||!snapshot.has("settings")
                ||!snapshot.get("settings").isJsonObject())
            throw conflict("budget","", "Brak ustawień budżetu.");
        JsonObject settings=snapshot.getAsJsonObject("settings");
        int total=0;
        for(Map.Entry<String,JsonElement> field:delta.entrySet()) {
            String name=field.getKey();
            if(!ITEMS.equals(name)&&!RECIPIENTS.equals(name)
                    &&!HISTORY.equals(name))
                throw conflict("budget","", "Niedozwolone pole budżetu.");
            if(!settings.has(name)||!settings.get(name).isJsonPrimitive()
                    ||!field.getValue().isJsonArray())
                throw conflict(name,"", "Nieprawidłowa lista zmian budżetu.");

            final JsonArray current;
            try {
                current=JsonParser.parseString(
                    settings.get(name).getAsString()).getAsJsonArray();
            } catch(Exception error) {
                throw conflict(name,"", "Nieprawidłowy format Budżetu na PC.");
            }
            LinkedHashMap<String,JsonObject> rows=rows(current,name);
            for(JsonElement element:field.getValue().getAsJsonArray()) {
                if(!element.isJsonObject())
                    throw conflict(name,"", "Nieprawidłowa zmiana pozycji.");
                JsonObject change=element.getAsJsonObject();
                if(!change.has("id")||!change.get("id").isJsonPrimitive()
                        ||!change.has("before")||!change.has("after"))
                    throw conflict(name,"", "Niekompletna zmiana budżetu.");
                String id=change.get("id").getAsString();
                if(!id.matches("[0-9a-fA-F-]{36}"))
                    throw conflict(name,id,"Błędny identyfikator budżetu.");
                JsonObject expected=objectOrNull(change.get("before"),name,id);
                JsonObject incoming=objectOrNull(change.get("after"),name,id);
                if(expected!=null&&!id.equals(identity(expected))
                        ||incoming!=null&&!id.equals(identity(incoming)))
                    throw conflict(name,id,"Niespójny identyfikator pozycji.");

                JsonObject actual=rows.get(id);
                if(HISTORY.equals(name)) {
                    if(expected!=null||incoming==null)
                        throw conflict(name,id,
                            "Historia jest tylko do dopisywania.");
                    if(actual!=null) {
                        if(!actual.equals(incoming))
                            throw conflict(name,id,
                                "Zdarzenie historii ma dwie różne treści.");
                        // Powtórne otrzymanie identycznego zdarzenia nie księguje go drugi raz.
                    } else rows.put(id,incoming.deepCopy());
                } else {
                    if(!same(actual,expected))
                        throw conflict(name,id,
                            "Ta pozycja została zmieniona na innym urządzeniu.");
                    if(incoming==null)rows.remove(id);
                    else rows.put(id,incoming.deepCopy());
                }
                if(++total>500)
                    throw conflict(name,id,"Za dużo zmian w jednej paczce.");
            }
            JsonArray merged=new JsonArray();
            for(JsonObject item:rows.values())merged.add(item);
            if(HISTORY.equals(name)) {
                // Stare zdarzenia pozostają w budget_history (SQLite).
                while(merged.size()>6000||merged.toString().length()>1_900_000)
                    merged.remove(0);
            }
            settings.addProperty(name,merged.toString());
        }
        return total;
    }

    private static LinkedHashMap<String,JsonObject> rows(JsonArray source,
            String name) throws DesktopHubServer.Conflict {
        LinkedHashMap<String,JsonObject> out=new LinkedHashMap<>();
        for(JsonElement entry:source) {
            JsonObject item=objectOrNull(entry,name,"");
            String id=identity(item);
            if(!id.matches("[0-9a-fA-F-]{36}")||out.put(id,item)!=null)
                throw conflict(name,id,"Powtórzony albo nieprawidłowy identyfikator.");
        }
        return out;
    }

    private static JsonObject objectOrNull(JsonElement element,
            String field,String key) throws DesktopHubServer.Conflict {
        if(element==null||element instanceof JsonNull)return null;
        if(!element.isJsonObject())throw conflict(field,key,
            "Nieprawidłowa struktura pozycji.");
        return element.getAsJsonObject();
    }

    private static String identity(JsonObject obj) {
        if(obj==null||!obj.has("id")||!obj.get("id").isJsonPrimitive())
            return "";
        return obj.get("id").getAsString();
    }

    private static boolean same(JsonObject a,JsonObject b) {
        return a==b || a!=null&&b!=null&&a.equals(b);
    }

    private static DesktopHubServer.Conflict conflict(
            String field,String key,String reason) {
        return new DesktopHubServer.Conflict(field,key,reason);
    }
}
