package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.*;

/** Stan ochrony przed mrozem współdzielony z Androidem przez tabele JSON. */
final class DesktopFrost {
    private DesktopFrost(){}
    static JsonArray table(JsonObject data,String name){
        if(data==null||!data.has("tables")||!data.get("tables").isJsonObject())
            return new JsonArray();
        JsonObject tables=data.getAsJsonObject("tables");
        JsonElement rows=tables.get(name);
        return rows!=null&&rows.isJsonArray()?rows.getAsJsonArray():new JsonArray();
    }
    private static JsonArray writable(JsonObject data,String name){
        JsonObject tables=data.getAsJsonObject("tables");
        JsonElement previous=tables.get(name);
        if(previous!=null&&previous.isJsonArray())return previous.getAsJsonArray();
        JsonArray rows=new JsonArray();tables.add(name,rows);return rows;
    }
    static String value(JsonObject obj,String key) {
        JsonElement v=obj==null?null:obj.get(key);
        return v==null||v.isJsonNull()?"":v.getAsString();
    }
    static long number(JsonObject obj,String key,long fallback){
        try{return Long.parseLong(value(obj,key));}
        catch(Exception ignored){return fallback;}
    }
    static Map<Long,JsonObject> byId(JsonObject root,String table){
        Map<Long,JsonObject>map=new HashMap<>();
        for(JsonElement el:table(root,table))
            if(el.isJsonObject()){
                JsonObject row=el.getAsJsonObject();
                long id=number(row,"id",-1L);if(id>=0)map.put(id,row);
            }
        return map;
    }
    static final class Settings{
        final int month,day,lead;final boolean first,before;
        Settings(int month,int day,int lead,boolean first,boolean before){
            this.month=month;this.day=day;this.lead=lead;
            this.first=first;this.before=before;
        }
    }
    static Settings settings(JsonObject root) {
        for(JsonElement el:table(root,"frost_settings")){
            if(!el.isJsonObject())continue;
            JsonObject row=el.getAsJsonObject();
            if(number(row,"id",0)!=1)continue;
            return new Settings((int)number(row,"start_month",12),
                (int)number(row,"start_day",1),(int)number(row,"lead_days",30),
                number(row,"remind_start",1)==1,number(row,"remind_before",1)==1);
        }
        return new Settings(12,1,30,true,true);
    }
    static LocalDate due(Settings cfg,int year){
        return LocalDate.of(year,cfg.month,cfg.day).minusDays(cfg.lead);
    }
    static void updateSettings(JsonObject root,int month,int day,int lead,
            boolean first,boolean before){
        if(month<1||month>12||day<1||day>28||lead<1||lead>90)
            throw new IllegalArgumentException("Dzień 1–28 i przygotowanie 1–90 dni.");
        JsonArray rows=writable(root,"frost_settings");
        JsonObject row=null;
        for(JsonElement el:rows)
            if(el.isJsonObject()&&number(el.getAsJsonObject(),"id",0)==1){
                row=el.getAsJsonObject();break;
            }
        if(row==null){row=new JsonObject();rows.add(row);}
        row.addProperty("id",1);
        row.addProperty("start_month",month);row.addProperty("start_day",day);
        row.addProperty("lead_days",lead);
        row.addProperty("remind_start",first?1:0);
        row.addProperty("remind_before",before?1:0);
    }
    static boolean riskyPlace(JsonObject root,String placeId) {
        Map<Long,JsonObject>places=byId(root,"places");
        Set<Long>seen=new HashSet<>();
        while(placeId!=null&&!placeId.isBlank()) {
            long id;
            try{id=Long.parseLong(placeId);}catch(Exception invalid){return false;}
            if(!seen.add(id)||seen.size()>128)return false;
            JsonObject place=places.get(id);
            if(place==null)return false;
            if(number(place,"frost_risk",0)==1)return true;
            placeId=value(place,"parent_id");
        }
        return false;
    }
    static boolean sensitive(JsonObject root,JsonObject item){
        if(item==null)return false;
        if(number(item,"protect_frost",0)==1)return true;
        if(!"box".equals(value(item,"kind")))return false;
        String boxId=value(item,"id");
        for(JsonElement el:table(root,"storage_items")){
            if(el.isJsonObject()){
                JsonObject child=el.getAsJsonObject();
                if(boxId.equals(value(child,"parent_box_id"))
                        &&number(child,"protect_frost",0)==1)return true;
            }
        }
        return false;
    }
    static boolean riskyDestination(JsonObject root,JsonObject moving){
        if(!sensitive(root,moving))return false;
        String placeId=value(moving,"place_id");
        String parent=value(moving,"parent_box_id");
        if(!parent.isEmpty()) {
            JsonObject box;
            try{box=byId(root,"storage_items").get(Long.parseLong(parent));}
            catch(Exception e){return false;}
            placeId=value(box,"place_id");
        }
        return riskyPlace(root,placeId);
    }
    static final class Entry {
        final long id;final String name,place;final boolean box,done;
        Entry(long id,String name,String place,boolean box,boolean done){
            this.id=id;this.name=name;this.place=place;
            this.box=box;this.done=done;
        }
    }
    static String location(JsonObject root,JsonObject item) {
        String p=value(item,"place_id"),box=value(item,"parent_box_id");
        if(!box.isEmpty()){
            JsonObject parent;
            try{parent=byId(root,"storage_items").get(Long.parseLong(box));}
            catch(Exception e){return "Pudełko bez lokalizacji";}
            return "Pudełko: "+value(parent,"name")+" → "+location(root,parent);
        }
        Map<Long,JsonObject> places=byId(root,"places");
        List<String>names=new ArrayList<>();
        Set<Long>visited=new HashSet<>();
        while(!p.isEmpty()) {
            long id;
            try{id=Long.parseLong(p);}catch(Exception e){break;}
            if(!visited.add(id)||visited.size()>128)break;
            JsonObject place=places.get(id);if(place==null)break;
            names.add(0,value(place,"name"));p=value(place,"parent_id");
        }
        return names.isEmpty()?"Bez lokalizacji":String.join(" → ",names);
    }
    static List<Entry> entries(JsonObject root,int year) {
        Map<Long,JsonObject>items=byId(root,"storage_items");
        Map<Long,Boolean>checks=new HashMap<>();
        for(JsonElement el:table(root,"frost_checks")){
            if(!el.isJsonObject())continue;
            JsonObject row=el.getAsJsonObject();
            if(number(row,"season_year",0)==year)
                checks.put(number(row,"item_id",-1L),number(row,"done",0)==1);
        }
        List<Entry>out=new ArrayList<>();
        for(JsonObject item:items.values()){
            if(!sensitive(root,item))continue;
            long id=number(item,"id",-1L);
            out.add(new Entry(id,value(item,"name"),location(root,item),
                "box".equals(value(item,"kind")),Boolean.TRUE.equals(checks.get(id))));
        }
        out.sort(Comparator.comparing((Entry e)->e.name,String.CASE_INSENSITIVE_ORDER)
            .thenComparingLong(e->e.id));
        return out;
    }
    static void check(JsonObject root,int year,long itemId,boolean done){
        JsonObject item=byId(root,"storage_items").get(itemId);
        if(year<2000||year>2100||!sensitive(root,item))
            throw new IllegalArgumentException("Brak oznaczonej rzeczy.");
        JsonArray rows=writable(root,"frost_checks");
        JsonObject row=null;
        for(JsonElement el:rows)
            if(el.isJsonObject()){
                JsonObject current=el.getAsJsonObject();
                if(number(current,"season_year",0)==year
                        &&number(current,"item_id",-1L)==itemId){row=current;break;}
            }
        if(row==null){row=new JsonObject();rows.add(row);}
        row.addProperty("id",year*1000000000000L+itemId);
        row.addProperty("season_year",year);row.addProperty("item_id",itemId);
        row.addProperty("done",done?1:0);
        row.addProperty("changed_at",System.currentTimeMillis());
    }
}
