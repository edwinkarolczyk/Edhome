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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative parser for text-based VeloBank PDF exports/confirmations.
 * The PDF bytes are decoded locally by BankPdfText. This parser never posts
 * money; it only creates statement evidence candidates for manual matching.
 */
final class BankStatementVeloPdf {
    private static final DateTimeFormatter PL =
        DateTimeFormatter.ofPattern("dd.MM.uuuu",Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE=Pattern.compile(
        "(?<!\\d)(\\d{2}[.]\\d{2}[.]\\d{4}|\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern MONEY=Pattern.compile(
        "(?iu)([-+−]?\\s*[0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)"
        +"\\s*(?:PLN|zł)\\b");
    private static final Pattern LABELED_DATE=Pattern.compile(
        "(?iu)(?:data\\s+(?:transakcji|operacji|księgowania|płatności))"
        +"[^0-9]{0,30}(\\d{2}[.]\\d{2}[.]\\d{4}|\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern LABELED_AMOUNT=Pattern.compile(
        "(?iu)(?:kwota\\s+(?:transakcji|operacji|płatności|przelewu))"
        +"[^0-9+−-]{0,30}([-+−]?\\s*[0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)"
        +"\\s*(?:PLN|zł)\\b");

    private BankStatementVeloPdf(){}

    static boolean recognizes(String text) {
        if(text==null)return false;
        String t=text.toLowerCase(Locale.ROOT);
        return t.contains("velobank")||t.contains("velo bank");
    }

    static List<BankStatementCsv.Entry> parse(String text) {
        if(!recognizes(text))
            throw new IllegalArgumentException("PDF nie został rozpoznany jako dokument VeloBanku.");
        String normalized=normalize(text);
        if(normalized.getBytes(StandardCharsets.UTF_8).length>512*1024)
            throw new IllegalArgumentException("Tekst PDF VeloBanku jest za duży.");
        List<BankStatementCsv.Entry> labeled=parseLabeledConfirmation(normalized);
        if(!labeled.isEmpty())return labeled;
        return parseStatementRows(normalized);
    }

    private static List<BankStatementCsv.Entry> parseLabeledConfirmation(String text) {
        Matcher dm=LABELED_DATE.matcher(text);
        Matcher am=LABELED_AMOUNT.matcher(text);
        if(!dm.find()||!am.find())return java.util.Collections.emptyList();
        String date=date(dm.group(1));
        ParsedAmount amount=amount(am.group(1),text);
        String desc=field(text,"Odbiorca","Akceptant","Tytuł","Opis");
        if(desc.isEmpty())desc="Dokument VeloBank";
        String key=sha256("velobank-pdf-confirmation\\n"+canonical(text));
        List<BankStatementCsv.Entry> one=new ArrayList<>();
        one.add(new BankStatementCsv.Entry(date,amount.kind,amount.grosz,
            trim(desc,300),"VeloBank PDF",key));
        return one;
    }

    private static List<BankStatementCsv.Entry> parseStatementRows(String text) {
        String[] lines=text.split("\\n");
        List<BankStatementCsv.Entry> result=new ArrayList<>();
        Set<String> unique=new HashSet<>();
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            if(line.isEmpty())continue;
            Matcher dateMatcher=DATE.matcher(line);
            if(!dateMatcher.find())continue;
            List<String> moneyTokens=new ArrayList<>();
            Matcher mm=MONEY.matcher(line);
            while(mm.find())moneyTokens.add(mm.group(1).trim());
            if(moneyTokens.isEmpty())continue;

            String chosen=null;
            for(String token:moneyTokens) {
                String t=token.replace(" ","").replace("\u00a0","");
                if(t.startsWith("-")||t.startsWith("−")||t.startsWith("+")) {
                    if(chosen!=null) { chosen=null; break; }
                    chosen=token;
                }
            }
            if(chosen==null) {
                if(moneyTokens.size()!=1)continue;
                chosen=moneyTokens.get(0);
            }
            ParsedAmount parsed;
            try {parsed=amount(chosen,line);}
            catch(IllegalArgumentException ambiguous){continue;}
            String booked=date(dateMatcher.group(1));
            String description=line
                .replace(dateMatcher.group(1)," ")
                .replace(chosen," ")
                .replaceAll("(?iu)\\b(?:PLN|zł)\\b"," ")
                .replaceAll("\\s+"," ").trim();
            if(description.length()<2)description="Operacja VeloBank";
            String canonical=booked+"|"+parsed.kind+"|"+parsed.grosz+"|"
                +canonical(line);
            String key=sha256("velobank-pdf-row\\n"+canonical);
            if(!unique.add(key))
                throw new IllegalArgumentException(
                    "PDF VeloBanku zawiera nierozróżnialne powtórzone wiersze. "
                    +"Nie zgaduję, czy to jedna czy kilka transakcji.");
            result.add(new BankStatementCsv.Entry(booked,parsed.kind,
                parsed.grosz,trim(description,300),"VeloBank PDF",key));
            if(result.size()>BankStatementCsv.MAX_ROWS)
                throw new IllegalArgumentException(
                    "PDF VeloBanku: maksymalnie 250 operacji.");
        }
        if(result.isEmpty())
            throw new IllegalArgumentException(
                "Nie znaleziono jednoznacznych operacji w tekstowym PDF VeloBanku. "
                +"PDF skanowany jako obraz nie jest automatycznie odczytywany.");
        return result;
    }

    private static ParsedAmount amount(String raw,String context) {
        String cleaned=raw.replace(" ","").replace("\u00a0","")
            .replace('−','-');
        boolean explicitExpense=cleaned.startsWith("-");
        boolean explicitIncome=cleaned.startsWith("+");
        if(explicitExpense||explicitIncome)cleaned=cleaned.substring(1);
        long grosz=MoneyRules.parse(cleaned);
        String lower=context.toLowerCase(Locale.ROOT);
        boolean expense=explicitExpense||lower.matches("(?s).*(?:transakcj[aeęąi]*\\s+kart|"
            +"płatnoś|zakup|obciąż|wypłat|przelew wychodzący|przelew wysłan).*");
        boolean income=explicitIncome||lower.matches("(?s).*(?:wpływ|uznan|wpłat|"
            +"przelew przychodzący|przelew otrzymany|otrzyman).*");
        if(expense==income)
            throw new IllegalArgumentException("Nie można ustalić kierunku operacji VeloBank.");
        return new ParsedAmount(expense?"expense":"income",grosz);
    }

    private static String field(String text,String... labels) {
        String[] lines=text.split("\\n");
        for(String label:labels)for(String line:lines) {
            String lower=line.toLowerCase(Locale.ROOT);
            String needle=label.toLowerCase(Locale.ROOT);
            int at=lower.indexOf(needle);
            if(at<0)continue;
            String value=line.substring(at+label.length())
                .replaceFirst("^[\\s:–—-]+","").trim();
            if(!value.isEmpty())return value;
        }
        return "";
    }

    private static String date(String raw) {
        try {
            LocalDate value=raw.contains(".")?LocalDate.parse(raw,PL):LocalDate.parse(raw);
            if(value.getYear()<1970||value.getYear()>2100)
                throw new IllegalArgumentException("Data VeloBanku poza zakresem.");
            return value.toString();
        }catch(java.time.format.DateTimeParseException error) {
            throw new IllegalArgumentException("Nieprawidłowa data w PDF VeloBanku.");
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n","\n").replace('\r','\n')
            .replace('\u00a0',' ')
            .replaceAll("[ \\t]+"," ")
            .replaceAll("\\n{3,}","\\n\\n").trim();
    }

    private static String canonical(String text) {
        return normalize(text).toLowerCase(Locale.ROOT)
            .replaceAll("\\s+"," ").trim();
    }

    private static String trim(String value,int max) {
        String v=value.trim();
        return v.length()<=max?v:v.substring(0,max);
    }

    private static String sha256(String value) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class ParsedAmount {
        final String kind; final long grosz;
        ParsedAmount(String kind,long grosz){this.kind=kind;this.grosz=grosz;}
    }
}
