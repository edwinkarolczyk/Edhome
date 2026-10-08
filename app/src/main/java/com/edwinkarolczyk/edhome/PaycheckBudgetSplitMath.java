package com.edwinkarolczyk.edhome;

/**
 * Bezstratny podział pojedynczego przelewu według pozostałych kwot zobowiązań.
 * Różnica jest zwracana oddzielnie: dodatnia = nadpłata,
 * ujemna = niedopłata. Nie rozkładamy jej proporcjonalnie.
 */
final class PaycheckBudgetSplitMath {
    static final class Result {
        final long[] allocationsGrosz;
        final long differenceGrosz;

        Result(long[] allocationsGrosz, long differenceGrosz) {
            this.allocationsGrosz = allocationsGrosz;
            this.differenceGrosz = differenceGrosz;
        }
    }

    private PaycheckBudgetSplitMath() { }

    static Result calculate(long[] remainingGrosz, long transactionGrosz) {
        if (remainingGrosz == null || remainingGrosz.length < 2
                || transactionGrosz < remainingGrosz.length)
            throw new IllegalArgumentException("Nie można podzielić przelewu.");
        long plannedTotal = 0L;
        for (long due : remainingGrosz) {
            if (due <= 0L)
                throw new IllegalArgumentException("Pozycja nie ma kwoty do zapłaty.");
            plannedTotal = Math.addExact(plannedTotal, due);
        }
        long[] allocations = new long[remainingGrosz.length];
        long available = transactionGrosz;
        for (int i = 0; i < remainingGrosz.length; i++) {
            long minimumForNext = remainingGrosz.length - i - 1L;
            long allocated = Math.min(remainingGrosz[i], available - minimumForNext);
            if (allocated < 1L)
                throw new IllegalArgumentException("Za mała kwota przelewu.");
            allocations[i] = allocated;
            available -= allocated;
        }
        // Niewykorzystana część przelewu zostaje jedną odrębną nadpłatą.
        // Przy niedopłacie niedobór pozostaje na ostatniej pozycji.
        return new Result(allocations, Math.subtractExact(transactionGrosz, plannedTotal));
    }
}
