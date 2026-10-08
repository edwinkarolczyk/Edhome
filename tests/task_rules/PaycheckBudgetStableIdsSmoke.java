package com.edwinkarolczyk.edhome;

/** Kontrakt kluczy odpornych na różne urządzenia i kolejność synchronizacji. */
public final class PaycheckBudgetStableIdsSmoke {
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String uuid="f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        long item=PaycheckBudgetStableIds.of("budget_items",uuid);
        long copy=PaycheckBudgetStableIds.of("budget_items",uuid);
        long month=PaycheckBudgetStableIds.of("budget_occurrences",
            uuid+":2026-10");
        long future=PaycheckBudgetStableIds.of("budget_occurrences",
            uuid+":2026-11");
        long recipient=PaycheckBudgetStableIds.of("budget_recipients",uuid);
        long event=PaycheckBudgetStableIds.of("budget_history",uuid);
        check(item>0,"Klucz musi być dodatni dla SQLite i synchronizacji.");
        check(item==copy,"Ponowne uruchomienie nie może zmienić identyfikatora.");
        check(item!=recipient && item!=event,
            "Różne dziedziny danych muszą mieć odrębne klucze.");
        check(month!=future,"Dwa miesiące nie mogą nadpisywać tego samego wystąpienia.");
        check(month!=item,"Wystąpienie nie może kolidować z definicją.");
        check(PaycheckBudgetStableIds.of("budget_credits",uuid+":2026-10")>0,
            "Odliczenie nadpłaty otrzymuje stabilną tożsamość.");
        boolean invalid=false;
        try { PaycheckBudgetStableIds.of("budget_items",""); }
        catch(IllegalArgumentException expected) { invalid=true; }
        check(invalid,"Pusty klucz został zaakceptowany.");
        invalid=false;
        try { PaycheckBudgetStableIds.of("../private",uuid); }
        catch(IllegalArgumentException expected) { invalid=true; }
        check(invalid,"Niedozwolona nazwa domeny została zaakceptowana.");
        System.out.println("SQLite Budżetu etap 5A: 8 scenariuszy kluczy PASS");
    }
}
