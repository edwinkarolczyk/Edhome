package com.edwinkarolczyk.edhome;

/** Czyste obliczenia salda miesiąca, niezależne od Androida i bazy danych. */
final class PaycheckBudgetSettlementMath {
    private PaycheckBudgetSettlementMath() { }

    static long outstanding(long plannedGrosz, long paidGrosz,
            long closedGrosz, long appliedCreditGrosz) {
        if (plannedGrosz < 0 || paidGrosz < 0 || closedGrosz < 0
                || appliedCreditGrosz < 0)
            throw new IllegalArgumentException("Ujemna kwota rozliczenia.");
        long outstanding = Math.subtractExact(plannedGrosz,paidGrosz);
        outstanding = Math.subtractExact(outstanding,closedGrosz);
        outstanding = Math.subtractExact(outstanding,appliedCreditGrosz);
        return Math.max(0L,outstanding);
    }
}
