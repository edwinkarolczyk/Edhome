package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Propozycje rozliczeń grupowych. To jedynie obliczenia — bez zapisów i księgowania. */
final class BankBudgetGroupRules {
    static final int MAX_BANK_PAIR_DAYS = 7;

    private BankBudgetGroupRules() { }

    /** Dwa rzeczywiste przelewy do jednej oczekującej pozycji Budżetu. */
    static boolean twoBankToOneBudget(long first, long second, long remaining,
            String dateFirst, String dateSecond, String firstKind, String secondKind) {
        if (first <= 0 || second <= 0 || remaining <= 0
                || !("income".equals(firstKind) || "expense".equals(firstKind))
                || !firstKind.equals(secondKind)) return false;
        try {
            LocalDate a=LocalDate.parse(dateFirst);
            LocalDate b=LocalDate.parse(dateSecond);
            return YearMonth.from(a).equals(YearMonth.from(b))
                && Math.abs(ChronoUnit.DAYS.between(a,b)) <= MAX_BANK_PAIR_DAYS
                && Math.addExact(first,second)==remaining;
        }catch(Exception invalid) {return false;}
    }

    /** Jeden rzeczywisty przelew do dwóch pozycji planu tego samego miesiąca. */
    static boolean oneBankToTwoBudgets(long bank, long firstDue, long secondDue,
            String bankKind, String firstKind, String secondKind) {
        if (bank<=0 || firstDue<=0 || secondDue<=0
                || !("income".equals(bankKind) || "expense".equals(bankKind))
                || !bankKind.equals(firstKind) || !bankKind.equals(secondKind))
            return false;
        try {return Math.addExact(firstDue,secondDue)==bank;}
        catch(ArithmeticException overflow) {return false;}
    }
}
