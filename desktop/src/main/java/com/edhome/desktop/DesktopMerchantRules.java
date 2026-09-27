package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class DesktopMerchantRules {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=Path.of(System.getProperty("user.home"),
        ".edhome","paycheck-merchant-rules.json");

    static final class Match {
        final String label,category;
        final boolean learned;
        Match(String label,String category,boolean learned){
            this.label=label;this.category=category;this.learned=learned;
        }
    }
    private static final class Known {
        final String label,category;
        Known(String label,String category){this.label=label;this.category=category;}
    }
    private static final Map<String,Known> KNOWN=new LinkedHashMap<>();
    static {
        known("BIEDRONKA","Biedronka","food");
        known("LIDL","Lidl","food");
        known("KAUFLAND","Kaufland","food");
        known("DINO","Dino","food");
        known("CARREFOUR","Carrefour","food");
        known("AUCHAN","Auchan","food");
        known("ZABKA","Żabka","food");
        known("ALDI","Aldi","food");
        known("ROSSMANN","Rossmann","beauty");
        known("HEBE","Hebe","beauty");
        known("ORLEN","Orlen","fuel");
        known("SHELL","Shell","fuel");
        known("CIRCLE K","Circle K","fuel");
        known("NETFLIX","Netflix","subscriptions");
        known("SPOTIFY","Spotify","subscriptions");
        known("DISNEY","Disney+","subscriptions");
        known("HBO","HBO / Max","subscriptions");
        known("YOUTUBE PREMIUM","YouTube Premium","subscriptions");
        known("ALLEGRO","Allegro","shopping");
        known("AMAZON","Amazon","shopping");
        known("TAURON","TAURON","utilities");
        known("PGE","PGE","utilities");
        known("ENERGA","Energa","utilities");
        known("MCDONALD","McDonald's","restaurants");
        known("KFC","KFC","restaurants");
        known("PIZZA HUT","Pizza Hut","restaurants");
        known("UBER","Uber","transport");
        known("BOLT","Bolt","transport");
        known("APTEKA","Apteka","health");
    }

    private DesktopMerchantRules(){}

    private static void known(String alias,String label,String category){
        KNOWN.put(normalize(alias),new Known(label,category));
    }

    static synchronized Match detect(String description){
        String normalized=normalize(description);
        if(normalized.isBlank()) return null;

        JsonObject learned=loadLearned();
        String bestKey="";
        JsonObject best=null;
        for(String key:learned.keySet()){
            if(normalized.contains(key) && key.length()>bestKey.length()
                    && learned.get(key).isJsonObject()){
                bestKey=key;best=learned.getAsJsonObject(key);
            }
        }
        if(best!=null){
            String label=value(best,"label");
            String category=value(best,"category");
            if(!label.isBlank() && !category.isBlank())
                return new Match(label,category,true);
        }
        for(Map.Entry<String,Known> entry:KNOWN.entrySet()){
            if(normalized.contains(entry.getKey())){
                Known known=entry.getValue();
                return new Match(known.label,known.category,false);
            }
        }
        return null;
    }

    static synchronized String suggestedCategory(String merchantLabel){
        if(merchantLabel==null || merchantLabel.isBlank()) return "";
        String key=normalize(merchantLabel);
        JsonObject learned=loadLearned();
        if(learned.has(key) && learned.get(key).isJsonObject()){
            String category=value(learned.getAsJsonObject(key),"category");
            if(!category.isBlank()) return category;
        }
        Known known=KNOWN.get(key);
        return known==null?"":known.category;
    }

    static synchronized void remember(String merchantLabel,String category){
        String label=merchantLabel==null?"":merchantLabel.trim();
        String key=normalize(label);
        if(label.isBlank() || key.length()<2 || category==null || category.isBlank()) return;
        try{
            JsonObject root=loadRoot();
            JsonObject merchants=root.getAsJsonObject("merchants");
            JsonObject row=merchants.has(key) && merchants.get(key).isJsonObject()
                ?merchants.getAsJsonObject(key):new JsonObject();
            row.addProperty("label",label);
            row.addProperty("category",category);
            int count=row.has("confirmed_count")?row.get("confirmed_count").getAsInt():0;
            row.addProperty("confirmed_count",count+1);
            row.addProperty("last_confirmed_at",System.currentTimeMillis());
            merchants.add(key,row);
            saveRoot(root);
        }catch(Exception ignored){ }
    }

    private static JsonObject loadLearned(){return loadRoot().getAsJsonObject("merchants");}
    private static JsonObject loadRoot(){
        try{
            if(Files.isRegularFile(FILE)){
                JsonObject root=JsonParser.parseString(
                    Files.readString(FILE,StandardCharsets.UTF_8)).getAsJsonObject();
                if(!root.has("merchants") || !root.get("merchants").isJsonObject())
                    root.add("merchants",new JsonObject());
                return root;
            }
        }catch(Exception ignored){ }
        JsonObject root=new JsonObject();
        root.addProperty("version",1);
        root.add("merchants",new JsonObject());
        return root;
    }
    private static void saveRoot(JsonObject root)throws Exception{
        Files.createDirectories(FILE.getParent());
        Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
        Files.writeString(temp,GSON.toJson(root),StandardCharsets.UTF_8);
        try{
            Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        }catch(java.nio.file.AtomicMoveNotSupportedException ignored){
            Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static String value(JsonObject row,String key){
        try{return row.has(key)&&!row.get(key).isJsonNull()?row.get(key).getAsString():"";}
        catch(Exception ignored){return "";}
    }
    private static String normalize(String raw){
        if(raw==null)return "";
        String noMarks=Normalizer.normalize(raw,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
        return noMarks.toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
}
