package com.edwinkarolczyk.edhome;

public final class PantryCategoriesSmoke {
    private static int count;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        count++;
    }
    public static void main(String[] args) {
        check(PantryCategories.IDS.length == 5, "five categories");
        check(PantryCategories.FILTER_IDS.length == 6, "all plus five");
        for (String category : PantryCategories.IDS) {
            check(PantryCategories.known(category), category);
            check(!PantryCategories.label(category).isEmpty(), "label for " + category);
        }
        check(!PantryCategories.known("unknown"), "reject unknown");
        check(PantryCategories.fromSource("Open Food Facts").equals("food"), "food");
        check(PantryCategories.fromSource("Open Beauty Facts").equals("beauty"), "beauty");
        check(PantryCategories.fromSource("Open Pet Food Facts").equals("pet"), "pet");
        check(PantryCategories.fromSource("Open Products Facts").equals("other"),
            "general products are not automatically cleaning goods");
        check(PantryCategories.fromSource(null).equals("other"), "offline manual");
        System.out.println("Pantry category rules: PASS " + count);
    }
}
