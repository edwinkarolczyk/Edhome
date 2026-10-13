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


    /**
     * Tworzy definicję we WSPÓLNYM budżecie Android/PC. Nie księguje
     * transakcji PayCheck i nie modyfikuje salda. Rekord i historia 5A
     * powstają razem na kopii snapshotu przed wysłaniem delty po UUID.
     */
    static JsonObject createItem(JsonObject original,String requestedName,
            String kind,String category,long amountGrosz,String amountMode,
            YearMonth start,YearMonth end,int cycleMonths,int dueDay,
            boolean optional,boolean installment,int installmentCount,
            String recipientId) throws Exception {
        String name=requestedName==null?"":requestedName.trim();
        String recipient=recipientId==null?"":recipientId.trim();
        if(original==null||name.isEmpty()||name.length()>80
                ||!("expense".equals(kind)||"income".equals(kind))
                ||!java.util.Set.of("shopping","food","subscriptions","utilities",
                    "bills","home","household","vehicle","fuel","transport",
                    "health","beauty","clothing","restaurants","entertainment",
                    "education","children","pet","insurance","loans","salary",
                    "benefits","savings","transfers","other").contains(category)
                ||amountGrosz<1||amountGrosz>MAX_GROSZ
                ||!("fixed".equals(amountMode)||"estimate".equals(amountMode))
                ||start==null||!java.util.Set.of(0,1,2,3,6,12).contains(cycleMonths)
                ||dueDay<1||dueDay>31
                ||end!=null&&(end.isBefore(start)||cycleMonths==0)
                ||(!recipient.isBlank()&&!recipient.matches("[0-9a-fA-F-]{36}"))
                ||installmentCount<0||installmentCount>600
                ||(installment&&(!"expense".equals(kind)||cycleMonths!=1
                    ||(installmentCount>0)==(end!=null)))
                ||(!installment&&installmentCount!=0))
            throw new IllegalArgumentException("Nieprawidłowe dane nowej pozycji Budżetu.");
        JsonObject root=original.deepCopy();
        if(!root.has("settings")||!root.get("settings").isJsonObject()
                ||!root.has("tables")||!root.get("tables").isJsonObject())
            throw new IllegalArgumentException("Najpierw zsynchronizuj wspólny Budżet.");
        JsonObject settings=root.getAsJsonObject("settings");
        JsonObject tables=root.getAsJsonObject("tables");
        if(!settings.has("paycheckMonthlyBudget")
                ||!settings.has("paycheckBudgetHistory")
                ||!settings.has("paycheckRecipients"))
            throw new IllegalStateException("Brak wspólnych danych Budżetu z telefonu.");
        JsonArray items=JsonParser.parseString(
            settings.get("paycheckMonthlyBudget").getAsString()).getAsJsonArray();
        JsonArray recipients=JsonParser.parseString(
            settings.get("paycheckRecipients").getAsString()).getAsJsonArray();
        JsonArray history=JsonParser.parseString(
            settings.get("paycheckBudgetHistory").getAsString()).getAsJsonArray();
        JsonArray dbItems=tables.getAsJsonArray("budget_items");
        JsonArray dbHistory=tables.getAsJsonArray("budget_history");
        if(dbItems==null||dbHistory==null
                ||tables.getAsJsonArray("budget_occurrences")==null
                ||tables.getAsJsonArray("budget_recipients")==null)
            throw new IllegalStateException("Brak kompletu tabel Budżetu 5A.");
        DesktopBudgetIntegrity.assertSidecars(root);
        if(items.size()>=200)throw new IllegalArgumentException(
            "Budżet ma już 200 pozycji (limit Androida).");
        if(!recipient.isBlank()) {
            boolean found=false;
            for(JsonElement e:recipients)
                if(e.isJsonObject()&&recipient.equals(
                        e.getAsJsonObject().get("id").getAsString()))
                    found=true;
            if(!found)throw new IllegalArgumentException(
                "Wybrany odbiorca nie istnieje we wspólnej kartotece.");
        }
        for(JsonElement e:items) {
            JsonObject old=e.getAsJsonObject();
            if(name.equalsIgnoreCase(old.get("name").getAsString())
                    &&kind.equals(old.get("kind").getAsString())
                    &&start.toString().equals(old.get("startMonth").getAsString())
                    &&cycleMonths==old.get("cycleMonths").getAsInt()
                    &&amountGrosz==old.get("amountGrosz").getAsLong())
                throw new IllegalArgumentException(
                    "Taka pozycja już istnieje. Sprawdź Budżet, aby nie dublować planu.");
        }

        JsonObject item=new JsonObject();
        String id=UUID.randomUUID().toString();
        item.addProperty("id",id);
        item.addProperty("name",name);
        item.addProperty("kind",kind);
        item.addProperty("category",category);
        item.addProperty("amountGrosz",amountGrosz);
        item.addProperty("amountMode",amountMode);
        item.addProperty("startMonth",start.toString());
        item.addProperty("endMonth",end==null?"":end.toString());
        item.addProperty("cycleMonths",cycleMonths);
        item.addProperty("dueDay",dueDay);
        item.addProperty("templateDueDay",dueDay);
        item.addProperty("optional",optional);
        item.addProperty("installment",installment);
        item.addProperty("installmentCount",installmentCount);
        item.addProperty("creditAgreementNumber","");
        item.addProperty("recipientId",recipient);
        item.addProperty("active",true);
        item.addProperty("inactiveFromMonth","");
        for(String field:new String[]{"matches","skippedMonths","carriedMonths",
                "closedMonths"})item.add(field,new JsonArray());
        for(String field:new String[]{"allocations","splitSurplusesGrosz",
                "monthAmountOverrides","amountChanges","balanceAdjustmentsGrosz",
                "adjustmentReasons","adjustmentCreatedAt","closedMonthReasons",
                "closedMonthCreatedAt","creditApplicationsGrosz",
                "creditApplicationCreatedAt","invoiceDueDates",
                "plannedPaymentDates"})item.add(field,new JsonObject());

        items.add(item);
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        JsonObject row=new JsonObject();
        row.addProperty("id",stableId("budget_items",id));
        row.addProperty("entity_key",id);
        row.addProperty("payload",item.toString());
        dbItems.add(row);
        appendEvent(history,dbHistory,item,start,"ITEM_CREATED",
            amountGrosz,name);
        while(history.size()>6000||history.toString().length()>1_999_000)
            history.remove(0); // Archiwum SQLite jest append-only
        settings.addProperty("paycheckBudgetHistory",history.toString());
        DesktopBudgetIntegrity.assertSidecars(root);
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
