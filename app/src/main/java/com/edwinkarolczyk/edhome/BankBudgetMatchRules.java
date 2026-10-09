package com.edwinkarolczyk.edhome;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Bezpieczne, konserwatywne dopasowanie opisu z wyciągu do odbiorcy planu. */
final class BankBudgetMatchRules {
    private static final Set<String> GENERIC=new HashSet<>(Arrays.asList(
        "PRZELEW","TRANSAKCJA","OPERACJA","PLATNOSC","WPLATA","WYPLATA",
        "WYDATEK","PRZYCHOD","DOCHOD","WYNAGRODZENIE","PENSJA","RATA",
        "RATY","CZYNSZ","ZAKUP","ZAKUPY","OPLATA","OPLATY","FAKTURA",
        "BANK","ENERGIA","PRAD","GAZ","WODA","INTERNET","TELEFON",
        "KREDYT","POZYCJA","BUDZET","MIESIAC","ZWROT","ZAPLATY",
        "KARTA","BLIK","PODATEK","UBEZPIECZENIE","ABONAMENT",
        "ZOBOWIAZANIE","MIESIECZNY","CYKLICZNY","ODBIORCA"));

    private BankBudgetMatchRules() {}

    static String normalize(String text) {
        if(text==null)return "";
        String decomposed=Normalizer.normalize(text,Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+","")
            .toUpperCase(Locale.ROOT).replace('Ł','L').replaceAll("[^A-Z0-9]+"," ").trim();
    }

    /** Dopasowanie po nazwie identyfikującej odbiorcę, NIE tylko po kwocie. */
    static boolean descriptionIdentifies(String bankDescription,
            String budgetName,String recipientName) {
        String bank=" "+normalize(bankDescription)+" ";
        if(bank.trim().isEmpty())return false;
        String recipient=normalize(recipientName);
        if(!recipient.isEmpty())return hasSpecificName(bank,recipient);
        return hasSpecificName(bank,normalize(budgetName));
    }

    private static boolean hasSpecificName(String bank,String label) {
        if(label.isEmpty())return false;
        // Pełna nazwa, np. NETFLIX albo TAURON DYSTRYBUCJA.
        if(!GENERIC.contains(label) && label.length()>=5
                && bank.contains(" "+label+" "))return true;
        // Dopuszczamy pojedynczy wyróżniający człon, ale nigdy ogólne
        // słowa takie jak RATA, PRZELEW, ENERGIA, INTERNET, OPŁATA.
        String[] tokens=label.split(" ");
        int distinctive=0;
        for(String word:tokens)
            if(word.length()>=6 && !GENERIC.contains(word)
                    && !word.matches("[0-9]+")
                    && bank.contains(" "+word+" "))
                distinctive++;
        return distinctive>0;
    }
}
