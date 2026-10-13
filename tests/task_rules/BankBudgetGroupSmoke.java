package com.edwinkarolczyk.edhome;

final class BankBudgetGroupSmoke {
    private static void check(boolean correct,String reason) {
        if(!correct)throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(BankBudgetGroupRules.twoBankToOneBudget(
            20000,30000,50000,"2026-10-09","2026-10-10","expense","expense"),
            "Dwa przelewy 200+300 rozliczają plan 500");
        check(BankBudgetGroupRules.oneBankToTwoBudgets(
            50000,20000,30000,"expense","expense","expense"),
            "Jeden przelew 500 rozlicza plany 200 i 300");
        check(BankBudgetGroupRules.twoBankToOneBudget(
            20000,30000,50000,"2026-10-09","2026-10-16","income","income"),
            "Dokładnie 7 dni jest dopuszczalne");
        check(!BankBudgetGroupRules.twoBankToOneBudget(
            20000,30000,50000,"2026-10-09","2026-10-17","expense","expense"),
            "Ponad 7 dni nie jest podpowiedzią");
        check(!BankBudgetGroupRules.twoBankToOneBudget(
            20000,30000,50000,"2026-09-30","2026-10-01","expense","expense"),
            "Nie przenoś między miesiącami");
        check(!BankBudgetGroupRules.twoBankToOneBudget(
            20000,30000,50000,"2026-10-09","2026-10-10","income","expense"),
            "Nie łącz wpływu z wydatkiem");
        check(!BankBudgetGroupRules.oneBankToTwoBudgets(
            50000,20000,29999,"expense","expense","expense"),
            "Różne kwoty nie są zgodną sumą");
        check(!BankBudgetGroupRules.oneBankToTwoBudgets(
            50000,20000,30000,"income","income","expense"),
            "Nie łącz przeciwnych znaków");
        check(!BankBudgetGroupRules.oneBankToTwoBudgets(
            Long.MAX_VALUE,Long.MAX_VALUE,5,"expense","expense","expense"),
            "Przepełnienie kwoty nie dopasowuje");
        System.out.println("PASS: dopasowania grupowe 200+300=500 i 500=200+300, daty i znaki");
    }
}
