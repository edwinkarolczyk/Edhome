package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Odrzucenie tylko wybranych, w pełni nierozliczonych dowodów bankowych. */
final class DesktopBankEvidenceBulk {
    private DesktopBankEvidenceBulk() { }

    static int dismissSelected(JsonArray evidence,JsonArray transactions,
            Set<String> selected) {
        if(evidence==null||transactions==null||selected==null
                ||selected.isEmpty()||selected.size()>100)return 0;
        for(String key:selected)
            if(key==null||!key.matches("[0-9a-f]{64}"))return 0;

        // Weryfikacja całej paczki przed zmianą pierwszego wiersza.
        for(JsonElement element:transactions) {
            if(!element.isJsonObject())continue;
            JsonObject tx=element.getAsJsonObject();
            if(selected.contains(text(tx,"statement_key")))return 0;
        }
        Map<String,JsonObject> eligible=new LinkedHashMap<>();
        for(JsonElement element:evidence) {
            if(!element.isJsonObject())continue;
            JsonObject row=element.getAsJsonObject();
            String key=text(row,"evidence_key");
            if(!selected.contains(key))continue;
            if(eligible.containsKey(key)||!"open".equals(text(row,"state"))
                    ||hasValue(row,"matched_operation_id")
                    ||hasValue(row,"matched_at"))return 0;
            eligible.put(key,row);
        }
        if(eligible.size()!=selected.size())return 0;
        for(JsonObject row:eligible.values()) {
            row.addProperty("state","dismissed");
            row.add("matched_operation_id",JsonNull.INSTANCE);
            row.add("matched_at",JsonNull.INSTANCE);
        }
        return eligible.size();
    }

    private static String text(JsonObject row,String key) {
        JsonElement v=row.get(key);
        return v==null||v.isJsonNull()?"":v.getAsString();
    }

    private static boolean hasValue(JsonObject row,String key) {
        JsonElement v=row.get(key);
        return v!=null&&!v.isJsonNull()&&!v.getAsString().isEmpty();
    }
}
