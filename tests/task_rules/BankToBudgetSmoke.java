package com.edwinkarolczyk.edhome;

import java.util.Arrays;

public final class BankToBudgetSmoke {
    private static void expect(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        expect(BankToBudgetRules.suggestedName(
            "Operacja kartą 5375 **** na kwotę 33,81 w AllegroPay, POZNAN, PL -")
            .equals("AllegroPay"),"Karta AllegroPay");
        expect(BankToBudgetRules.suggestedName(
            "Przelew na rachunek 12 000... Odbiorca: Gmina Klucze, Tytuł: Odpady")
            .equals("Gmina Klucze"),"Odbiorca przelewu");
        expect(BankToBudgetRules.suggestedName(" ").equals("Przelew bankowy"),
            "Bez opisu");
        expect(BankToBudgetRules.matchingMonths("AllegroPay",
            Arrays.asList("2026-09-13","2026-10-12"),
            Arrays.asList("Operacja w AllegroPay, Poznan",
                "Operacja w AllegroPay, Poznan"))==2,
            "Powtarzający się odbiorca przez dwa miesiące");
        expect(BankToBudgetRules.matchingMonths("AllegroPay",
            Arrays.asList("2026-10-09","2026-10-12"),
            Arrays.asList("w AllegroPay, Poznan","w AllegroPay, Poznan"))==1,
            "Dwa przelewy w tym samym miesiącu to nie dwa cykle");
        expect(BankToBudgetRules.matchingMonths("AllegroPay",
            Arrays.asList("2026-09-09","2026-10-12"),
            Arrays.asList("w AllegroPay, Poznan","w Metalbox, Olkusz"))==1,
            "Przelewy do różnych odbiorców nie sugerują cyklu");
        expect(BankToBudgetRules.matchingMonths("Przelew",
            Arrays.asList("2026-09-09","2026-10-12"),
            Arrays.asList("Przelew zwykły","Przelew zwykły"))==0,
            "Ogólny tytuł bez odbiorcy nie sugeruje cyklu");
        System.out.println("PASS bank -> budzet: nazwy, odbiorcy, powtarzalnosc");
    }
}
