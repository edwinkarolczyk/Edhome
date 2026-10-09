package com.edwinkarolczyk.edhome;

/**
 * Kontrola groszy jednego potwierdzonego przelewu podczas kolejnych
 * ręcznych przypisań do Budżetu. Nie księguje żadnej nowej wpłaty.
 */
final class PaycheckBudgetAllocationGuard {
    private PaycheckBudgetAllocationGuard() { }

    /**
     * Brak jawnej alokacji przy dopasowanej operacji oznacza pełną
     * kwotę przelewu; nie wolno traktować go jako 0 zł.
     */
    static long addUsed(long alreadyUsed,long paymentGrosz,boolean matched,
            Long allocatedGrosz,Long surplusGrosz) {
        if(paymentGrosz<=0L||alreadyUsed<0L||alreadyUsed>paymentGrosz)
            throw new IllegalArgumentException("Nieprawidłowa kwota przelewu.");
        if(!matched) {
            if(allocatedGrosz!=null||surplusGrosz!=null)
                throw new IllegalArgumentException(
                    "Niepowiązana alokacja lub nadpłata przelewu.");
            return alreadyUsed;
        }
        long allocated=allocatedGrosz==null?paymentGrosz:allocatedGrosz;
        long surplus=surplusGrosz==null?0L:surplusGrosz;
        if(allocated<=0L||surplus<0L)
            throw new IllegalArgumentException(
                "Nieprawidłowa alokacja albo nadpłata przelewu.");
        long used=Math.addExact(alreadyUsed,Math.addExact(allocated,surplus));
        if(used>paymentGrosz)
            throw new IllegalArgumentException(
                "Przelew jest już rozliczony lub przekroczono jego kwotę.");
        return used;
    }
}
