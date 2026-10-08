package com.edwinkarolczyk.edhome;

/** Test zachowania: prawdziwe kwoty w groszach, bez mockowania algorytmu. */
public final class PaycheckBudgetFinanceSmoke {
    private static void eq(long expected, long actual, String test) {
        if (expected != actual)
            throw new AssertionError(test + ": oczekiwano " + expected
                + ", otrzymano " + actual);
    }
    private static void testSplit(long[] due, long transfer,
            long[] expected, long difference, String test) {
        PaycheckBudgetSplitMath.Result result =
            PaycheckBudgetSplitMath.calculate(due,transfer);
        eq(difference,result.differenceGrosz,test+" różnica");
        long allocated=0L;
        for(int i=0;i<expected.length;i++) {
            eq(expected[i],result.allocationsGrosz[i],test+" poz."+i);
            allocated+=result.allocationsGrosz[i];
        }
        eq(transfer,allocated+Math.max(0L,result.differenceGrosz),
            test+" suma przelewu");
    }
    public static void main(String[] args) {
        eq(0L,PaycheckBudgetSettlementMath.outstanding(10000L,5000L,0L,5000L),
            "50 zł płatności + 50 zł nadpłaty");
        eq(5000L,PaycheckBudgetSettlementMath.outstanding(10000L,5000L,0L,0L),
            "faktyczna zaległość 50 zł");
        eq(0L,PaycheckBudgetSettlementMath.outstanding(10000L,10000L,0L,0L),
            "zapłacona całość");
        eq(0L,PaycheckBudgetSettlementMath.outstanding(10000L,12000L,0L,0L),
            "zapłata ponad plan");
        eq(0L,PaycheckBudgetSettlementMath.outstanding(10000L,3000L,2000L,5000L),
            "częściowa płatność, zamknięcie i nadpłata");
        eq(20000L,PaycheckBudgetSettlementMath.outstanding(30000L,10000L,0L,0L),
            "częściowo opłacony rachunek nadal kwalifikuje się do podziału");
        testSplit(new long[]{30000L,66650L},97000L,
            new long[]{30000L,66650L},350L,"nadpłata tylko osobno");
        testSplit(new long[]{30000L,66650L},96000L,
            new long[]{30000L,66000L},-650L,"niedopłata jawna");
        testSplit(new long[]{20000L,66650L},87000L,
            new long[]{20000L,66650L},350L,"wcześniejsza wpłata 100 zł");
        testSplit(new long[]{30000L,66650L},96650L,
            new long[]{30000L,66650L},0L,"kwota dokładna");
        boolean rejected=false;
        try { PaycheckBudgetSplitMath.calculate(
            new long[]{30000L,66650L},1L); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if (!rejected) throw new AssertionError("Podział 1 grosza na 2 pozycje!");
        System.out.println("Budżet miesiąca etap 1: 11 scenariuszy finansowych PASS");
    }
}
