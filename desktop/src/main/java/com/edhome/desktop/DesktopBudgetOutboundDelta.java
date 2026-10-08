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
            if(table.startsWith("budget_"))continue;
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
            LinkedHashMap<String,JsonObject> before=rows(
                JsonParser.parseString(oldSettings.get(field).getAsString()).getAsJsonArray());
            LinkedHashMap<String,JsonObject> after=rows(
                JsonParser.parseString(newSettings.get(field).getAsString()).getAsJsonArray());
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
        for(JsonElement element:data) {
            if(!element.isJsonObject())return null;
            JsonObject item=element.getAsJsonObject();
            if(!item.has("id")||!item.get("id").isJsonPrimitive())return null;
            String id=item.get("id").getAsString();
            if(!id.matches("[0-9a-fA-F-]{36}")||result.put(id,item)!=null)
                return null;
        }
        return result;
    }
}
