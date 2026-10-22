package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

/** Bezpieczny przyrostowy zapis wspólnego Budżetu Desktop -> Android. */
final class DesktopBudgetOutboundDelta {
    static final class Result {
        final JsonObject payload;
        final int changes;
        Result(JsonObject payload,int changes) {
            this.payload=payload;
            this.changes=changes;
        }
    }
    private DesktopBudgetOutboundDelta() { }

    static Result build(JsonObject base,JsonObject edited) throws Exception {
        if(base==null||edited==null
                ||!base.has("settings")||!edited.has("settings")
                ||!base.has("tables")||!edited.has("tables"))return null;
        JsonObject oldSettings=base.getAsJsonObject("settings");
        JsonObject newSettings=edited.getAsJsonObject("settings");
        final String[] fields={
            "paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"
        };
        JsonObject left=oldSettings.deepCopy(),right=newSettings.deepCopy();
        for(String field:fields) {
            left.remove(field);
            right.remove(field);
        }
        if(!left.equals(right))return null;

        // Ta paczka zawiera tylko edycję Budżetu i odpowiadające jej
        // tabele pochodne. Inne modyfikacje wymagają osobnego patcha.
        JsonObject oldTables=base.getAsJsonObject("tables");
        JsonObject newTables=edited.getAsJsonObject("tables");
        for(Map.Entry<String,JsonElement> e:oldTables.entrySet()) {
            String table=e.getKey();
            // Nie wolno pomijać zmian tabel budget_: payload budgetDelta
            // zawiera wyłącznie ustawienia, więc pominięte wiersze zginęłyby.
            // Wymagaj osobnego patcha rekordów zamiast cichej utraty zmian.
            if(!e.getValue().equals(newTables.get(table)))return null;
        }
        for(String table:newTables.keySet())
            if(!oldTables.has(table))return null;

        JsonObject budget=new JsonObject();
        int count=0;
        for(String field:fields) {
            if(!oldSettings.has(field)||!newSettings.has(field)
                    ||!oldSettings.get(field).isJsonPrimitive()
                    ||!newSettings.get(field).isJsonPrimitive())return null;
            // Nieprawidłowy lub niepełny snapshot nie może być podstawą delty.
            // Zatrzymaj wysyłkę zamiast tworzyć operacje z niepewnych danych.
            JsonElement oldData, newData;
            try {
                oldData=JsonParser.parseString(oldSettings.get(field).getAsString());
                newData=JsonParser.parseString(newSettings.get(field).getAsString());
            } catch (com.google.gson.JsonParseException error) {
                return null;
            }
            if(!oldData.isJsonArray()||!newData.isJsonArray())return null;
            LinkedHashMap<String,JsonObject> before=rows(oldData.getAsJsonArray());
            LinkedHashMap<String,JsonObject> after=rows(newData.getAsJsonArray());
            if(before==null||after==null)return null;
            Set<String> ids=new TreeSet<>();
            ids.addAll(before.keySet());
            ids.addAll(after.keySet());
            JsonArray changes=new JsonArray();
            for(String id:ids) {
                JsonObject old=before.get(id),now=after.get(id);
                if(old!=null&&old.equals(now))continue;
                // Usunięcie z ograniczonego cache historii nie oznacza
                // zniszczenia append-only eventu z SQLite.
                if("paycheckBudgetHistory".equals(field)&&now==null)continue;
                if("paycheckBudgetHistory".equals(field)&&old!=null)
                    return null;
                JsonObject operation=new JsonObject();
                operation.addProperty("id",id);
                operation.add("before",old==null?JsonNull.INSTANCE:old);
                operation.add("after",now==null?JsonNull.INSTANCE:now);
                changes.add(operation);
                if(++count>500)return null;
            }
            if(changes.size()>0)budget.add(field,changes);
        }
        if(count==0)return null;
        JsonObject output=new JsonObject();
        output.addProperty("format","edhome-record-patch");
        output.addProperty("version",2);
        output.add("operations",new JsonArray());
        output.add("budgetDelta",budget);
        return new Result(output,count);
    }

    private static LinkedHashMap<String,JsonObject> rows(JsonArray data) {
        LinkedHashMap<String,JsonObject> result=new LinkedHashMap<>();
        Set<String> canonicalIds=new java.util.HashSet<>();
        for(JsonElement element:data) {
            if(!element.isJsonObject())return null;
            JsonObject item=element.getAsJsonObject();
            if(!item.has("id")||!item.get("id").isJsonPrimitive())return null;
            String id=item.get("id").getAsString();
            if(!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || !canonicalIds.add(id.toLowerCase(java.util.Locale.ROOT)))
                return null;
            // Ten sam rekord musi mieć ten sam klucz także pomiędzy snapshotami,
            // niezależnie od zapisu liter UUID. Nie generuj usunięcia i dodania.
            JsonObject normalized=item.deepCopy();
            normalized.addProperty("id",id.toLowerCase(java.util.Locale.ROOT));
            result.put(id.toLowerCase(java.util.Locale.ROOT),normalized);
        }
        return result;
    }
}
