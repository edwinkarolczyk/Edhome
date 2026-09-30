package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.List;

/** Portable semicolon CSV for an EDHOME garden reference catalog. */
final class GardenCatalogCsv {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final String HEADER =
        "catalog_key;nazwa;nazwa_lacinska;odmiana;zrodlo;url;licencja;"
        + "siew_od;siew_do;sadzenie_od;sadzenie_do;zbior_od;zbior_do;"
        + "rozstaw_cm;glebokosc_mm;stanowisko;podlewanie;uwagi";

    private GardenCatalogCsv() { }

    static final class Entry {
        final String key,name,latin,variety,source,url,license,sunlight,watering,notes;
        final int sowFrom,sowTo,plantFrom,plantTo,harvestFrom,harvestTo,spacing,depth;

        Entry(List<String> v) {
            if(v.size()!=18) throw new IllegalArgumentException(
                "Każdy wiersz katalogu musi mieć 18 kolumn.");
            key=required(v.get(0),120,"Brak catalog_key.");
            if(!key.matches("[A-Za-z0-9._:-]{1,120}"))
                throw new IllegalArgumentException("Nieprawidłowy catalog_key: "+key);
            name=required(v.get(1),120,"Brak nazwy rośliny.");
            latin=optional(v.get(2),160);
            variety=optional(v.get(3),120);
            source=optional(v.get(4),200);
            url=optional(v.get(5),500);
            license=optional(v.get(6),120);
            sowFrom=month(v.get(7)); sowTo=month(v.get(8));
            plantFrom=month(v.get(9)); plantTo=month(v.get(10));
            harvestFrom=month(v.get(11)); harvestTo=month(v.get(12));
            spacing=number(v.get(13),0,10000,"rozstaw_cm");
            depth=number(v.get(14),0,10000,"glebokosc_mm");
            sunlight=optional(v.get(15),200);
            watering=optional(v.get(16),300);
            notes=optional(v.get(17),4000);
        }
    }

    static List<Entry> parse(String text) {
        if(text==null) throw new IllegalArgumentException("Brak danych CSV.");
        if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Katalog przekracza 2 MB.");
        String normalized=text.replace("\r\n","\n").replace('\r','\n');
        String[] lines=normalized.split("\n",-1);
        int first=0;
        while(first<lines.length && lines[first].trim().isEmpty()) first++;
        if(first>=lines.length || !HEADER.equals(lines[first].trim()))
            throw new IllegalArgumentException(
                "Nieznany nagłówek CSV. Wyeksportuj wzór z EDHOME.");
        List<Entry> out=new ArrayList<>();
        java.util.HashSet<String> keys=new java.util.HashSet<>();
        for(int i=first+1;i<lines.length;i++) {
            if(lines[i].trim().isEmpty()) continue;
            Entry e=new Entry(parseLine(lines[i]));
            if(!keys.add(e.key))
                throw new IllegalArgumentException("Powielony catalog_key: "+e.key);
            out.add(e);
            if(out.size()>20000)
                throw new IllegalArgumentException("Za dużo pozycji katalogu.");
        }
        return out;
    }

    static String row(String... fields) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<fields.length;i++) {
            if(i>0) out.append(';');
            String value=fields[i]==null?"":fields[i];
            boolean quote=value.indexOf(';')>=0 || value.indexOf('"')>=0
                || value.indexOf('\n')>=0 || value.indexOf('\r')>=0;
            if(quote) out.append('"');
            for(int p=0;p<value.length();p++) {
                char ch=value.charAt(p);
                if(ch=='"') out.append("\"\"");
                else if(ch=='\n'||ch=='\r') out.append(' ');
                else out.append(ch);
            }
            if(quote) out.append('"');
        }
        return out.toString();
    }

    private static List<String> parseLine(String line) {
        List<String> out=new ArrayList<>();
        StringBuilder value=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(quoted) {
                if(ch=='"' && i+1<line.length() && line.charAt(i+1)=='"') {
                    value.append('"'); i++;
                } else if(ch=='"') quoted=false;
                else value.append(ch);
            } else if(ch==';') {
                out.add(value.toString()); value.setLength(0);
            } else if(ch=='"' && value.length()==0) quoted=true;
            else value.append(ch);
        }
        if(quoted) throw new IllegalArgumentException("Niedomknięty cudzysłów w CSV.");
        out.add(value.toString());
        return out;
    }

    private static int month(String raw) {
        return number(raw,0,12,"miesiąc");
    }

    private static int number(String raw,int min,int max,String label) {
        String x=raw==null?"":raw.trim();
        if(x.isEmpty()) return 0;
        try {
            int n=Integer.parseInt(x);
            if(n<min||n>max) throw new NumberFormatException();
            return n;
        } catch(NumberFormatException invalid) {
            throw new IllegalArgumentException("Nieprawidłowa wartość "+label+": "+x);
        }
    }

    private static String required(String raw,int max,String error) {
        String x=optional(raw,max);
        if(x.isEmpty()) throw new IllegalArgumentException(error);
        return x;
    }

    private static String optional(String raw,int max) {
        String x=raw==null?"":raw.trim();
        if(x.length()>max || x.indexOf('\0')>=0)
            throw new IllegalArgumentException("Pole tekstowe jest zbyt długie.");
        return x;
    }
}
