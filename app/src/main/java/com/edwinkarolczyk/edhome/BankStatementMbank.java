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

/** mBank semicolon export with metadata, a #Data księgowania header and no transaction ID.
 * A booking balance is mandatory to avoid treating repeated equal purchases as duplicates.
 * This is a user-provided, UNAUTHENTICATED statement; it never posts to the ledger.
 */
final class BankStatementMbank {
    private static final DateTimeFormatter PL =
        DateTimeFormatter.ofPattern("dd.MM.uuuu",Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private BankStatementMbank() { }

    static boolean recognizes(String data) {
        if(data==null)return false;
        for(String line:data.replace("\r\n","\n").replace('\r','\n').split("\n")) {
            String trimmed=line.replace("\uFEFF","").trim();
            if(trimmed.startsWith("#Data księgowania;")
                    && trimmed.contains("#Data operacji;")
                    && trimmed.contains("#Kwota;")
                    && trimmed.contains("#Saldo po operacji"))
                return true;
        }
        return false;
    }

    static List<BankStatementCsv.Entry> parse(String data) {
        if(data==null || data.getBytes(StandardCharsets.UTF_8).length
                >BankStatementCsv.MAX_BYTES)
            throw new IllegalArgumentException("Eksport mBanku: maks. 256 KB na plik.");
        String[] lines=data.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        int header=-1;
        String account="";
        for(int i=0;i<lines.length;i++) {
            String trimmed=lines[i].replace("\uFEFF","").trim();
            if(trimmed.startsWith("#Numer rachunku;")
                    || trimmed.startsWith("#Numer konta;")) {
                List<String> meta=fields(trimmed);
                if(meta.size()>1)account=meta.get(1).trim().replace(" ","");
            }
            if(recognizesHeader(trimmed)){header=i;break;}
        }
        if(header<0)throw new IllegalArgumentException(
            "Nie znaleziono tabeli operacji mBanku (#Data księgowania).");
        List<String> names=fields(lines[header]);
        int booked=column(names,"data księgowania");
        int operated=column(names,"data operacji");
        int amount=column(names,"kwota");
        int description=column(names,"opis operacji");
        int title=column(names,"tytuł");
        int balance=column(names,"saldo po operacji");
        if(booked<0||operated<0||amount<0||description<0||balance<0)
            throw new IllegalArgumentException("Brakuje kolumn wyciągu mBanku.");
        List<BankStatementCsv.Entry> result=new ArrayList<>();
        Set<String> keys=new HashSet<>();
        for(int i=header+1;i<lines.length;i++) {
            String line=lines[i].trim();
            if(line.isEmpty())continue;
            if(line.startsWith("#Saldo końcowe") || line.startsWith("Niniejszy dokument"))
                break;
            if(line.startsWith("#"))continue;
            List<String> cells=fields(line);
            if(cells.size()!=names.size())
                throw new IllegalArgumentException(
                    "mBank: niezgodna liczba kolumn w wierszu "+(i+1));
            String bookedOn=date(cells.get(booked));
            String operatedOn=date(cells.get(operated));
            String rawAmount=cells.get(amount).trim().replace(" ","")
                .replace("\u00a0","");
            boolean expense=rawAmount.startsWith("-");
            if(rawAmount.startsWith("-")||rawAmount.startsWith("+"))
                rawAmount=rawAmount.substring(1);
            long grosz=money(rawAmount,i+1);
            if(grosz==0)throw new IllegalArgumentException(
                "mBank: zerowa kwota w wierszu "+(i+1));
            String rawBalance=cells.get(balance).trim().replace(" ","")
                .replace("\u00a0","");
            if(rawBalance.isEmpty())
                throw new IllegalArgumentException(
                    "mBank: brak salda po operacji w wierszu "+(i+1)
                    +". Nie dopasowuję tej operacji automatycznie.");
            boolean balanceNegative=rawBalance.startsWith("-");
            if(balanceNegative || rawBalance.startsWith("+"))
                rawBalance=rawBalance.substring(1);
            long balanceGrosz=money(rawBalance,i+1);
            String desc=cells.get(description).trim();
            String details=title<0?"":cells.get(title).trim();
            String shown=(desc+(details.isEmpty()?"":" • "+details)).trim();
            if(shown.isEmpty())shown="Operacja mBank";
            if(shown.length()>300)shown=shown.substring(0,300);
            // Same purchase amount and merchant are insufficient; the booked
            // post-transaction balance also separates distinct ledger rows.
            String stable="mbank\n"+account+"\n"+bookedOn+"\n"+operatedOn
                +"\n"+(expense?"-":"+")+grosz+"\n"
                +(balanceNegative?"-":"+")+balanceGrosz+"\n"+desc+"\n"+details;
            String key=sha256(stable);
            if(!keys.add(key))
                throw new IllegalArgumentException(
                    "mBank: nierozróżnialne operacje w wierszu "+(i+1)
                    +". Wybierz krótszy okres lub sprawdź eksport.");
            result.add(new BankStatementCsv.Entry(bookedOn,
                expense?"expense":"income",grosz,shown,
                "mBank / wiersz "+(i+1)+" (bez ID bankowego)",key));
            if(result.size()>BankStatementCsv.MAX_ROWS)
                throw new IllegalArgumentException(
                    "mBank: maksymalnie 250 transakcji na plik.");
        }
        if(result.isEmpty())
            throw new IllegalArgumentException("mBank: brak transakcji w eksporcie.");
        return result;
    }

    private static boolean recognizesHeader(String line) {
        return line.startsWith("#Data księgowania;")
            && line.contains("#Data operacji;")
            && line.contains("#Kwota;")
            && line.contains("#Saldo po operacji");
    }

    private static int column(List<String> names,String searched) {
        for(int i=0;i<names.size();i++) {
            String value=names.get(i).trim().replaceFirst("^#","")
                .toLowerCase(Locale.ROOT);
            if(value.equals(searched))return i;
        }
        return -1;
    }

    private static List<String> fields(String line) {
        List<String> parts=new ArrayList<>();
        StringBuilder part=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"') {
                if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"') {
                    part.append('"');i++;
                } else quoted=!quoted;
            } else if(c==';'&&!quoted) {
                parts.add(part.toString());part.setLength(0);
            } else part.append(c);
        }
        if(quoted)throw new IllegalArgumentException(
            "mBank: niezamknięty cudzysłów.");
        parts.add(part.toString());
        while(parts.size()>1&&parts.get(parts.size()-1).trim().isEmpty())
            parts.remove(parts.size()-1);
        return parts;
    }

    private static String date(String raw) {
        String value=raw.trim();
        try {
            LocalDate parsed;
            // Real XLSX exports can store dates as Excel serial numbers even
            // when the sheet displays DD.MM.YYYY. 1899-12-30 handles Excel's
            // historical leap-year offset for modern dates.
            if(value.matches("[0-9]{4,6}(?:[.]0+)?")) {
                long serial=Long.parseLong(value.replaceFirst("[.]0+$",""));
                if(serial<25569||serial>73415)
                    throw new IllegalArgumentException("Excel date outside 1970-2100");
                parsed=LocalDate.of(1899,12,30).plusDays(serial);
            } else parsed=value.contains(".")
                ?LocalDate.parse(value,PL):LocalDate.parse(value);
            if(parsed.getYear()<1970||parsed.getYear()>2100)
                throw new IllegalArgumentException("Data poza zakresem.");
            return parsed.toString();
        } catch(Exception error) {
            throw new IllegalArgumentException(
                "mBank: nieprawidłowa data "+value);
        }
    }

    private static long money(String raw,int row) {
        try {return MoneyRules.parse(raw);}
        catch(IllegalArgumentException error) {
            // Zero booked balance is legitimate; an operation amount is not.
            if("0,00".equals(raw)||"0.00".equals(raw)||"0".equals(raw))
                return 0;
            throw new IllegalArgumentException(
                "mBank: nieprawidłowa kwota/saldo w wierszu "+row);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte b:hash)
                result.append(String.format(Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
