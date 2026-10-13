package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bez księgowania: podpowiedzi nazwy i cykliczności z faktycznego wyciągu. */
final class BankToBudgetRules {
    private static final Pattern PARTY=Pattern.compile(
        "(?i)(?:odbiorca|nadawca|zleceniodawca)\\s*:\\s*([^,;\\n\\r]+)");
    private static final Pattern CARD=Pattern.compile(
        "(?i)\\b(?:w|u)\\s+([^,\\n\\r]{3,70}),\\s*[^,\\n\\r]{2,50}");
    private BankToBudgetRules() {}

    static String suggestedName(String bankDescription) {
        if(bankDescription==null || bankDescription.trim().isEmpty())
            return "Przelew bankowy";
        Matcher m=PARTY.matcher(bankDescription);
        String candidate=m.find()?m.group(1).trim():"";
        if(candidate.isEmpty()) {
            m=CARD.matcher(bankDescription);
            if(m.find())candidate=m.group(1).trim();
        }
        if(candidate.isEmpty()) {
            candidate=bankDescription.replaceAll("\\s+"," ").trim();
            if(candidate.length()>65)candidate=candidate.substring(0,65);
        }
        return candidate.length()>80?candidate.substring(0,80):candidate;
    }

    /** Co najmniej dwa odrębne miesiące podobnego odbiorcy = propozycja cyklu.
     *  Nigdy nie jest dowodem abonamentu ani automatyczną zgodą na księgowanie.
     */
    static int matchingMonths(String label,List<String> dates,List<String> descriptions) {
        if(label==null||dates==null||descriptions==null
                ||dates.size()!=descriptions.size())return 0;
        Set<YearMonth> months=new HashSet<>();
        for(int i=0;i<dates.size();i++) {
            if(!BankBudgetMatchRules.descriptionIdentifies(
                    descriptions.get(i),label,""))continue;
            try{months.add(YearMonth.from(LocalDate.parse(dates.get(i))));}
            catch(Exception ignored) { }
        }
        return months.size();
    }
}
