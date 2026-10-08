package com.edwinkarolczyk.edhome;

/** Rozdziela potwierdzone płatności przypisane do planu od całego PayCheck. */
final class PaycheckBudgetExecutionRules {
    private PaycheckBudgetExecutionRules() { }

    static long outsideBudget(long confirmedGrosz,long assignedGrosz) {
        if (confirmedGrosz<0L || assignedGrosz<0L
                || assignedGrosz>confirmedGrosz)
            throw new IllegalStateException(
                "Nieprawidłowa suma przypisanych przelewów PayCheck.");
        return confirmedGrosz-assignedGrosz;
    }

    static long coveredPlan(long plannedGrosz,long unpaidGrosz) {
        if (plannedGrosz<0L || unpaidGrosz<0L)
            throw new IllegalArgumentException("Nieprawidłowe saldo budżetu.");
        return Math.max(0L,plannedGrosz-unpaidGrosz);
    }
}
