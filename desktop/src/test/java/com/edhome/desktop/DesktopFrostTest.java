package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopFrostTest {
    private static JsonObject root() {
        JsonObject root=new JsonObject(),tables=new JsonObject();
        for(String key:new String[]{"places","storage_items","frost_checks","frost_settings"})
            tables.add(key,new JsonArray());
        root.add("tables",tables);
        return root;
    }
    private static JsonObject place(long id,Long parent,boolean frost) {
        JsonObject r=new JsonObject();
        r.addProperty("id",id);
        r.addProperty("name","Miejsce "+id);
        if(parent==null)r.add("parent_id",com.google.gson.JsonNull.INSTANCE);
        else r.addProperty("parent_id",parent);
        r.addProperty("frost_risk",frost?1:0);
        return r;
    }
    private static JsonObject item(long id,String kind,Long box,Long place,boolean protect){
        JsonObject r=new JsonObject();r.addProperty("id",id);
        r.addProperty("name","Rzecz "+id);r.addProperty("kind",kind);
        if(box==null)r.add("parent_box_id",com.google.gson.JsonNull.INSTANCE);
        else r.addProperty("parent_box_id",box);
        if(place==null)r.add("place_id",com.google.gson.JsonNull.INSTANCE);
        else r.addProperty("place_id",place);
        r.addProperty("protect_frost",protect?1:0);return r;
    }
    @Test void dueDefaultsAndConfigUpdates() {
        JsonObject root=root();
        assertEquals(LocalDate.of(2026,11,1),
            DesktopFrost.due(DesktopFrost.settings(root),2026));
        DesktopFrost.updateSettings(root,12,1,20,true,false);
        assertEquals(LocalDate.of(2026,11,11),
            DesktopFrost.due(DesktopFrost.settings(root),2026));
    }
    @Test void ancestryBoxContentsAndYearHistory() {
        JsonObject root=root();JsonArray places=DesktopFrost.table(root,"places");
        places.add(place(1,null,true));places.add(place(2,1L,false));
        JsonArray things=DesktopFrost.table(root,"storage_items");
        JsonObject box=item(10,"box",null,2L,false);
        JsonObject thing=item(11,"thing",10L,null,true);
        things.add(box);things.add(thing);
        assertTrue(DesktopFrost.riskyPlace(root,"2"));
        assertTrue(DesktopFrost.sensitive(root,box));
        assertTrue(DesktopFrost.riskyDestination(root,box));
        assertTrue(DesktopFrost.riskyDestination(root,thing));
        assertEquals(2,DesktopFrost.entries(root,2026).size());
        DesktopFrost.check(root,2026,11,true);
        assertEquals(1,DesktopFrost.entries(root,2026).stream().filter(e->e.done).count());
        assertEquals(0,DesktopFrost.entries(root,2027).stream().filter(e->e.done).count());
        assertEquals(1,DesktopFrost.table(root,"frost_checks").size());
        assertFalse(DesktopFrost.riskyPlace(root,"999"));
    }
}
