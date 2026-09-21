package com.edwinkarolczyk.edhome;

public final class PantryPackageRulesSmoke {
    private static int passed;
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
        passed++;
    }
    private static void rejects(String raw, String unit) {
        try {
            PantryPackageRules.parse(raw, unit);
            throw new AssertionError("accepted " + raw + " " + unit);
        } catch (IllegalArgumentException ok) {
            passed++;
        }
    }
    public static void main(String[] args) {
        check(PantryPackageRules.parse("0,5", "l") == 500, "half litre comma");
        check(PantryPackageRules.parse("0.5", "l") == 500, "half litre dot");
        check(PantryPackageRules.parse("1,250", "kg") == 1250, "kilograms");
        check(PantryPackageRules.parse("1", "szt.") == 1000, "pieces");
        check(PantryPackageRules.parse("2", "szt.") == 2000, "two pieces per pack");
        rejects("1,5", "szt.");
        rejects("-1", "l");
        rejects("0", "kg");
        rejects("1,0005", "l");
        rejects("1000001", "kg");
        rejects("1e4", "l");
        check(PantryPackageRules.summary(3, "l", 500).equals(
            "3 opak. × 0,5 l = 1,5 l"), "three half-litre bottles");
        check(PantryPackageRules.summary(2, "kg", 750).equals(
            "2 opak. × 0,75 kg = 1,5 kg"), "two bags");
        check(PantryPackageRules.summary(100000000, "l", 1000000000)
            .endsWith("100000000000000 l"), "safe long multiplication");
        check(PantryPackageRules.valid("szt.",1000), "one piece");
        check(!PantryPackageRules.valid("szt.",1500), "no fractional pieces");
        System.out.println("Pantry package rules: PASS " + passed);
    }
}
