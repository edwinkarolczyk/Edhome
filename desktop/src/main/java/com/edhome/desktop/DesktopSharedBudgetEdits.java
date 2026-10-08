package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Edycja pojedynczego wspólnego budżetu. Bez drugiej księgi na PC:
 * kopia ustawień i rekordy sidecar SQLite są zmieniane wspólnie,
 * a historia jest zawsze dopisywana. Zwraca nowy snapshot — błąd nie
 * zmienia oryginału, co pozwala zachować poprzedni stan w razie konfliktu.
 */
final class DesktopSharedBudgetEdits {
    private static final long MAX_GROSZ=99_999_999_999L;

    private DesktopSharedBudgetEdits() { }

    static JsonObject changeMonth(JsonObject original,String itemId,
            String requestedName,YearMonth month,long amountGrosz)
            throws Exception {
        if(original==null||itemId==null
                ||!itemId.matches("[0-9a-fA-F-]{36}")||month==null
                ||requestedName==null||requestedName.trim().isEmpty()
                ||requestedName.trim().length()>80
                ||amountGrosz<1L||amountGrosz>MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowe dane edycji Budżetu.");
        JsonObject root=original.deepCopy();
        if(!root.has("settings")||!root.get("settings").isJsonObject()
                ||!root.has("tables")||!root.get("tables").isJsonObject())
            throw new IllegalArgumentException("Brak wspólnego Budżetu z telefonu.");
        JsonObject settings=root.getAsJsonObject("settings");
        JsonObject tables=root.getAsJsonObject("tables");
        JsonArray items=JsonParser.parseString(
            settings.get("paycheckMonthlyBudget").getAsString()).getAsJsonArray();
        JsonArray dbItems=tables.getAsJsonArray("budget_items");
        JsonArray dbMonths=tables.getAsJsonArray("budget_occurrences");
        JsonArray dbHistory=tables.getAsJsonArray("budget_history");
        if(dbItems==null||dbMonths==null||dbHistory==null)
            throw new IllegalStateException("Brak tabel budżetu 5A w synchronizacji.");
        JsonObject item=null,row=null;
        for(JsonElement entry:items) {
            JsonObject candidate=entry.getAsJsonObject();
            if(itemId.equals(candidate.get("id").getAsString())) {
                if(item!=null)throw new IllegalStateException("Powtórzone ID pozycji.");
                item=candidate;
            }
        }
        for(JsonElement entry:dbItems) {
            JsonObject candidate=entry.getAsJsonObject();
            if(itemId.equals(candidate.get("entity_key").getAsString())) {
                if(row!=null)throw new IllegalStateException("Powtórzony wiersz budżetu.");
                row=candidate;
            }
        }
        if(item==null||row==null)
            throw new IllegalArgumentException("Pozycja nie istnieje w obu magazynach danych.");

        String oldName=item.get("name").getAsString();
        String newName=requestedName.trim();
        JsonObject overrides=item.has("monthAmountOverrides")
            && item.get("monthAmountOverrides").isJsonObject()
            ?item.getAsJsonObject("monthAmountOverrides"):new JsonObject();
        long previous=overrides.has(month.toString())
            ?overrides.get(month.toString()).getAsLong()
            :DesktopBudgetMirror.amountFor(item,month);
        if(oldName.equals(newName)&&previous==amountGrosz)
            return original;

        if(!oldName.equals(newName))item.addProperty("name",newName);
        if(previous!=amountGrosz) {
            overrides.addProperty(month.toString(),amountGrosz);
            item.add("monthAmountOverrides",overrides);
        }
        row.addProperty("payload",item.toString());
        settings.addProperty("paycheckMonthlyBudget",items.toString());

        if(previous!=amountGrosz) {
            String monthKey=itemId+":"+month;
            JsonObject occurrence=null;
            for(JsonElement e:dbMonths) {
                JsonObject candidate=e.getAsJsonObject();
                if(monthKey.equals(candidate.get("entity_key").getAsString())) {
                    if(occurrence!=null)
                        throw new IllegalStateException("Duplikat wystąpienia miesiąca.");
                    occurrence=candidate;
                }
            }
            if(occurrence==null) {
                occurrence=new JsonObject();
                occurrence.addProperty("id",stableId("budget_occurrences",monthKey));
                occurrence.addProperty("entity_key",monthKey);
                occurrence.addProperty("payload","{}");
                dbMonths.add(occurrence);
            }
            JsonObject payload=JsonParser.parseString(
                occurrence.get("payload").getAsString()).getAsJsonObject();
            payload.addProperty("monthAmountOverrides",amountGrosz);
            occurrence.addProperty("payload",payload.toString());
        }

        JsonArray cache=JsonParser.parseString(
            settings.get("paycheckBudgetHistory").getAsString()).getAsJsonArray();
        if(!oldName.equals(newName))
            appendEvent(cache,dbHistory,item,month,"ITEM_RENAMED",amountGrosz,
                "Zmieniono nazwę z: "+oldName);
        if(previous!=amountGrosz)
            appendEvent(cache,dbHistory,item,month,"AMOUNT_MONTH_CHANGED",
                amountGrosz,"Zmiana kwoty miesiąca na Desktopie.");
        while(cache.size()>6000||cache.toString().length()>1_999_000)
            cache.remove(0); // pełne zdarzenia już zapisano do budget_history
        settings.addProperty("paycheckBudgetHistory",cache.toString());
        return root;
    }

    private static void appendEvent(JsonArray cache,JsonArray archive,
            JsonObject item,YearMonth month,String type,long amount,String note)
            throws Exception {
        String uuid=UUID.randomUUID().toString();
        JsonObject event=new JsonObject();
        event.addProperty("id",uuid);
        event.addProperty("itemId",item.get("id").getAsString());
        event.addProperty("recipientId",item.has("recipientId")
            ?item.get("recipientId").getAsString():"");
        event.addProperty("month",month.toString());
        event.addProperty("type",type);
        event.addProperty("amountGrosz",amount);
        event.addProperty("note",note.length()>240?note.substring(0,240):note);
        event.addProperty("operationId","");
        event.addProperty("transactionDate","");
        event.addProperty("createdAt",System.currentTimeMillis());
        cache.add(event.deepCopy());

        JsonObject row=new JsonObject();
        row.addProperty("id",stableId("budget_history",uuid));
        row.addProperty("entity_key",uuid);
        row.addProperty("payload",event.toString());
        archive.add(row);
    }

    private static long stableId(String table,String key) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(
            (table+"\u0000"+key).getBytes(StandardCharsets.UTF_8));
        long result=0L;
        for(int i=0;i<8;i++)result=(result<<8)|(hash[i]&255L);
        result &= Long.MAX_VALUE;
        return result==0L?1L:result;
    }
}
