package com.edwinkarolczyk.edhome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** User-selected CSV file. This does not connect to a bank or authenticate the file. */
final class BankStatementCsv {
    static final int MAX_BYTES = 256 * 1024;
    static final int MAX_ROWS = 250;
    private static final DateTimeFormatter POLISH_DATE =
        DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    static final class Entry {
        final String date;
        final String kind;
        final long amountGrosz;
        final String description;
        final String reference;
        final String evidenceKey;
        Entry(String date, String kind, long amountGrosz, String description,
                String reference, String evidenceKey) {
            this.date=date; this.kind=kind; this.amountGrosz=amountGrosz;
            this.description=description; this.reference=reference;
            this.evidenceKey=evidenceKey;
        }
    }

    private BankStatementCsv() { }

    static List<Entry> parse(String csv, String bankLabel) {
        if (csv == null || csv.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Wyciąg CSV jest za duży (maks. 256 KB).");
        String bank=bankLabel==null?"":bankLabel.trim();
        if (bank.isEmpty() || bank.length()>80)
            throw new IllegalArgumentException("Podaj nazwę banku (maks. 80 znaków).");
        String[] lines=csv.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        if (lines.length < 2)
            throw new IllegalArgumentException("Wyciąg CSV jest pusty.");
        String headerLine=lines[0].replace("\\uFEFF","");
        char delimiter=detectDelimiter(headerLine);
        List<String> header=fields(headerLine,delimiter);
        int date=position(header,"data","data księgowania","data operacji",
            "data transakcji","booking date","date");
        int amount=position(header,"kwota","kwota operacji","kwota [pln]",
            "amount","amount (pln)");
        int ref=position(header,"id transakcji","identyfikator transakcji",
            "identyfikator","identyfikator operacji","nr transakcji",
            "numer referencyjny","reference","transaction id");
        int desc=position(header,"opis","opis operacji","description",
            "tytuł","tytuł operacji","szczegóły");
        if (date<0 || amount<0 || ref<0 || desc<0)
            throw new IllegalArgumentException(
                "CSV wymaga kolumn: Data;Kwota;Id transakcji;Opis.");
        List<Entry> entries=new ArrayList<>();
        Set<String> unique=new HashSet<>();
        for(int i=1;i<lines.length;i++) {
            if(lines[i].trim().isEmpty())continue;
            if(entries.size()>=MAX_ROWS)
                throw new IllegalArgumentException("Maksymalnie 250 transakcji na plik.");
            List<String> row=fields(lines[i],delimiter);
            if(row.size()!=header.size())
                throw new IllegalArgumentException("Błędna liczba kolumn CSV, wiersz "+(i+1));
            String dateValue=normalizeDate(row.get(date));
            String raw=row.get(amount).trim().replace(" ","");
            boolean expense=raw.startsWith("-");
            if(raw.startsWith("-")||raw.startsWith("+"))raw=raw.substring(1);
            long grosz;
            try {grosz=MoneyRules.parse(raw);}
            catch(IllegalArgumentException bad){
                throw new IllegalArgumentException("Nieprawidłowa kwota w wierszu "+(i+1));
            }
            String reference=row.get(ref).trim();
            if(reference.length()<4 || reference.length()>160)
                throw new IllegalArgumentException(
                    "Brak stabilnego identyfikatora transakcji w wierszu "+(i+1));
            String description=row.get(desc).trim();
            if(description.length()>300)
                throw new IllegalArgumentException("Za długi opis w wierszu "+(i+1));
            String key=sha256(bank.toLowerCase(Locale.ROOT)+"\n"+reference);
            if(!unique.add(key))
                throw new IllegalArgumentException("Powielony identyfikator bankowy w CSV.");
            entries.add(new Entry(dateValue,expense?"expense":"income",
                grosz,description,reference,key));
        }
        if(entries.isEmpty())throw new IllegalArgumentException("Brak transakcji w CSV.");
        return entries;
    }

    private static int position(List<String> cols,String... names) {
        for(int i=0;i<cols.size();i++){
            String label=cols.get(i).trim().toLowerCase(Locale.ROOT);
            for(String name:names)if(name.equals(label))return i;
        }
        return -1;
    }

    private static String normalizeDate(String value) {
        try {
            String text=value.trim();
            LocalDate parsed=text.contains(".")
                ?LocalDate.parse(text,POLISH_DATE):LocalDate.parse(text);
            if(parsed.getYear()<1970 || parsed.getYear()>2100)
                throw new IllegalArgumentException("Data transakcji poza zakresem.");
            return parsed.toString();
        }catch(java.time.format.DateTimeParseException error){
            throw new IllegalArgumentException("Błędna data wyciągu: "+value);
        }
    }

    private static char detectDelimiter(String header) {
        for(char candidate : new char[]{';','\\t',','}) {
            List<String> columns=fields(header,candidate);
            if(position(columns,"data","data księgowania","data operacji",
                    "data transakcji","booking date","date")>=0
                    && position(columns,"kwota","kwota operacji","kwota [pln]",
                        "amount","amount (pln)")>=0)
                return candidate;
        }
        throw new IllegalArgumentException(
            "Nieznany układ CSV. Wymagane są kolumny daty i kwoty.");
    }

    private static List<String> fields(String line, char delimiter) {
        List<String> values=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='"') {
                if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){
                    current.append('"');i++;
                }else quoted=!quoted;
            }else if(c==delimiter && !quoted){
                values.add(current.toString());current.setLength(0);
            }else current.append(c);
        }
        if(quoted)throw new IllegalArgumentException("Niepoprawny cudzysłów w CSV.");
        values.add(current.toString());
        return values;
    }

    private static String sha256(String value) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
}
