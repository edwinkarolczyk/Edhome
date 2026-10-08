package com.edwinkarolczyk.edhome;

/**
 * Ochrona salda odliczanej nadpłaty w groszach, niezależna od SQLite.
 * Kwota dostępna to nadwyżka potwierdzonych płatności nad planem,
 * a nie suma poprzednio rozliczonych kwot na innych miesiącach.
 */
final class PaycheckBudgetCreditMath {
    private PaycheckBudgetCreditMath() { }

    static void requireSourceAvailable(long confirmedGrosz,long splitSurplusGrosz,
            long plannedGrosz,long alreadyAppliedGrosz) {
        if(confirmedGrosz<0L||splitSurplusGrosz<0L||plannedGrosz<0L
                ||alreadyAppliedGrosz<0L)
            throw new IllegalArgumentException("Nieprawidłowe ujemne kwoty nadpłaty.");
        long gross=Math.addExact(confirmedGrosz,splitSurplusGrosz);
        long credit=Math.max(0L,Math.subtractExact(gross,plannedGrosz));
        if(alreadyAppliedGrosz>credit)
            throw new IllegalArgumentException(
                "Nadpłata została odliczona więcej niż wynosi dostępna kwota.");
    }

    static void requireTargetWithinPlan(long plannedGrosz,long creditUsedGrosz) {
        if(plannedGrosz<0L||creditUsedGrosz<0L||creditUsedGrosz>plannedGrosz)
            throw new IllegalArgumentException(
                "Odliczenia nadpłaty przekraczają planowaną kwotę miesiąca.");
    }
}
