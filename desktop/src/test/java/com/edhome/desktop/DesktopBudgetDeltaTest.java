package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopBudgetDeltaTest {
    private static final String A="11111111-1111-4111-8111-111111111111";
    private static final String B="22222222-2222-4222-8222-222222222222";
    private static final String E="33333333-3333-4333-8333-333333333333";

    private JsonObject item(String id,long amount) {
        JsonObject obj=new JsonObject();
        obj.addProperty("id",id);
        obj.addProperty("name",id.equals(A)?"Prąd":"Czynsz");
        obj.addProperty("amountGrosz",amount);
        return obj;
    }

    private JsonObject snapshot() {
        JsonArray items=new JsonArray();
        items.add(item(A,10000));
        items.add(item(B,20000));
        JsonObject settings=new JsonObject();
        settings.addProperty("paycheckMonthlyBudget",items.toString());
        settings.addProperty("paycheckRecipients","[]");
        settings.addProperty("paycheckBudgetHistory","[]");
        JsonObject state=new JsonObject();
        state.add("settings",settings);
        return state;
    }

    private JsonObject change(String id,JsonObject before,JsonObject after) {
        JsonObject c=new JsonObject();
        c.addProperty("id",id);
        c.add("before",before==null?JsonNull.INSTANCE:before);
        c.add("after",after==null?JsonNull.INSTANCE:after);
        return c;
    }

    private JsonObject delta(String field,JsonObject... changes) {
        JsonArray list=new JsonArray();
        for(JsonObject c:changes)list.add(c);
        JsonObject delta=new JsonObject();
        delta.add(field,list);
        return delta;
    }

    private JsonArray items(JsonObject state) {
        return JsonParser.parseString(state.getAsJsonObject("settings")
            .get("paycheckMonthlyBudget").getAsString()).getAsJsonArray();
    }

    @Test void independentOfflineEditsMergeWithoutOverwritingEachOther()
            throws Exception {
        JsonObject server=snapshot();
        JsonObject desktopA=item(A,15000);
        JsonArray editedA=new JsonArray();
        editedA.add(desktopA);
        editedA.add(item(B,20000));
        server.getAsJsonObject("settings").addProperty(
            "paycheckMonthlyBudget",editedA.toString());
        JsonObject androidB=item(B,24000);
        int count=DesktopBudgetDelta.apply(server,delta("paycheckMonthlyBudget",
            change(B,item(B,20000),androidB)));
        assertEquals(1,count);
        assertEquals(15000,items(server).get(0).getAsJsonObject()
            .get("amountGrosz").getAsLong());
        assertEquals(24000,items(server).get(1).getAsJsonObject()
            .get("amountGrosz").getAsLong());
    }

    @Test void simultaneousEditOfSameBillConflictsAndKeepsSnapshot()
            throws Exception {
        JsonObject server=snapshot();
        JsonArray editedA=new JsonArray();
        editedA.add(item(A,18000));
        editedA.add(item(B,20000));
        server.getAsJsonObject("settings").addProperty(
            "paycheckMonthlyBudget",editedA.toString());
        String before=server.toString();
        assertThrows(DesktopHubServer.Conflict.class,()->{
            JsonObject work=server.deepCopy();
            DesktopBudgetDelta.apply(work,delta("paycheckMonthlyBudget",
                change(A,item(A,10000),item(A,12000))));
        });
        assertEquals(before,server.toString());
    }

    @Test void retryItemChangeAfterLostAckIsIdempotent() throws Exception {
        JsonObject server=snapshot();
        JsonObject oldItem=item(A,10000);
        JsonObject newItem=item(A,13500);
        JsonObject packageDelta=delta("paycheckMonthlyBudget",
            change(A,oldItem,newItem));
        assertEquals(1,DesktopBudgetDelta.apply(server,packageDelta));
        String first=server.getAsJsonObject("settings")
            .get("paycheckMonthlyBudget").getAsString();
        assertEquals(1,DesktopBudgetDelta.apply(server,packageDelta));
        assertEquals(first,server.getAsJsonObject("settings")
            .get("paycheckMonthlyBudget").getAsString());
        assertEquals(2,items(server).size());
    }

    @Test void retryCannotHideNewerConflictingEdit() throws Exception {
        JsonObject server=snapshot();
        DesktopBudgetDelta.apply(server,delta("paycheckMonthlyBudget",
            change(A,item(A,10000),item(A,15000))));
        assertThrows(DesktopHubServer.Conflict.class,()->{
            DesktopBudgetDelta.apply(server,delta("paycheckMonthlyBudget",
                change(A,item(A,10000),item(A,17000))));
        });
        assertEquals(15000,items(server).get(0).getAsJsonObject()
            .get("amountGrosz").getAsLong());
    }

    @Test void historyIsIdempotentAndAppendOnly() throws Exception {
        JsonObject server=snapshot();
        JsonObject event=new JsonObject();
        event.addProperty("id",E);
        event.addProperty("amountGrosz",4500);
        JsonObject add=delta("paycheckBudgetHistory",change(E,null,event));
        assertEquals(1,DesktopBudgetDelta.apply(server,add));
        assertEquals(1,DesktopBudgetDelta.apply(server,add));
        assertEquals(1,JsonParser.parseString(server.getAsJsonObject("settings")
            .get("paycheckBudgetHistory").getAsString()).getAsJsonArray().size());
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,delta("paycheckBudgetHistory",
                change(E,event,null))));
        JsonObject tampered=event.deepCopy();
        tampered.addProperty("amountGrosz",7000);
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,delta("paycheckBudgetHistory",
                change(E,null,tampered))));
    }

    @Test void addingRecipientDoesNotEraseExistingBudget() throws Exception {
        JsonObject server=snapshot();
        JsonObject r=new JsonObject();
        r.addProperty("id",E);
        r.addProperty("name","Zakład energetyczny");
        int count=DesktopBudgetDelta.apply(server,delta("paycheckRecipients",
            change(E,null,r)));
        assertEquals(1,count);
        assertEquals(2,items(server).size());
        assertEquals(1,JsonParser.parseString(server.getAsJsonObject("settings")
            .get("paycheckRecipients").getAsString()).getAsJsonArray().size());
    }

    @Test void conflictInSecondFieldRollsBackFirstField() throws Exception {
        JsonObject server=snapshot();
        String before=server.toString();
        JsonObject changedRecipient=new JsonObject();
        changedRecipient.addProperty("id",E);
        changedRecipient.addProperty("name","Nowy odbiorca");
        JsonObject packageDelta=new JsonObject();
        JsonArray recipients=new JsonArray();
        recipients.add(change(E,null,changedRecipient));
        packageDelta.add("paycheckRecipients",recipients);
        JsonArray items=new JsonArray();
        items.add(change(A,item(A,9999),item(A,12000)));
        packageDelta.add("paycheckMonthlyBudget",items);
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,packageDelta));
        assertEquals(before,server.toString(),
            "Konflikt w drugim polu nie może zapisać pierwszego pola.");
    }

    @Test void malformedUuidAndDuplicateChangesAreRejectedAtomically()
            throws Exception {
        JsonObject server=snapshot();
        String before=server.toString();
        JsonObject invalid=delta("paycheckMonthlyBudget",
            change("not-a-valid-uuid",null,item(E,100)));
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,invalid));
        JsonObject repeated=delta("paycheckMonthlyBudget",
            change(A,item(A,10000),item(A,12000)),
            change(A,item(A,12000),item(A,13000)));
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,repeated));
        assertEquals(before,server.toString());
    }

    @Test void uuidCaseVariantsCannotCreateSecondRecipient() throws Exception {
        JsonObject server=snapshot();
        JsonObject recipient=new JsonObject();
        recipient.addProperty("id",E);
        recipient.addProperty("name","Odbiorca");
        DesktopBudgetDelta.apply(server,delta("paycheckRecipients",
            change(E,null,recipient)));
        String before=server.toString();
        JsonObject altered=recipient.deepCopy();
        altered.addProperty("id",E.toUpperCase(java.util.Locale.ROOT));
        altered.addProperty("name","Inny odbiorca");
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(server,delta("paycheckRecipients",
                change(E.toUpperCase(java.util.Locale.ROOT),null,altered))));
        assertEquals(before,server.toString());
    }

    @Test void rejectingUnknownFieldsPreventsSettingsInjection() {
        JsonObject state=snapshot();
        JsonObject event=new JsonObject();
        event.addProperty("id",E);
        JsonObject change=delta("homeTileOrder",change(E,null,event));
        assertThrows(DesktopHubServer.Conflict.class,
            ()->DesktopBudgetDelta.apply(state,change));
    }
}
