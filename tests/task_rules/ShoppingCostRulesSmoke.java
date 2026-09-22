package com.edwinkarolczyk.edhome;

public final class ShoppingCostRulesSmoke {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }
    public static void main(String[] args) {
        check(ShoppingCostRules.totalGrosz(null, 649) == null,
            "unknown qty stays unknown");
        check(ShoppingCostRules.totalGrosz(1000L, 649) == 649L,
            "one package 6,49 zł");
        check(ShoppingCostRules.totalGrosz(2000L, 649) == 1298L,
            "two packages 12,98 zł");
        check(ShoppingCostRules.totalGrosz(1500L, 649) == 974L,
            "1,5 kg -> 9,74 zł rounded once");
        check(ShoppingCostRules.totalGrosz(500L, 649) == 325L,
            "0,5 l -> 3,25 zł");
        check(ShoppingCostRules.totalGrosz(1L, 1) == 0L,
            "one thousandth of one grosz rounds to zero without inventing price");
        try {
            ShoppingCostRules.totalGrosz(0L, 649);
            throw new AssertionError("zero quantity accepted");
        } catch (IllegalArgumentException ok) { passed++; }
        try {
            ShoppingCostRules.totalGrosz(1000L, 0);
            throw new AssertionError("zero unit price accepted");
        } catch (IllegalArgumentException ok) { passed++; }
        System.out.println("Shopping unit cost, fractional quantity, unknown total: PASS "
            + passed);
    }
}
