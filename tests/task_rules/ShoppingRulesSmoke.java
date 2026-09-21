package com.edwinkarolczyk.edhome;

public final class ShoppingRulesSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void invalid(String raw) {
        try {
            ShoppingRules.parseQuantity(raw);
            throw new AssertionError("Should reject: " + raw);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
    public static void main(String[] args) {
        check("Mleko".equals(ShoppingRules.validatedName(" Mleko ")),
            "Trim item name.");
        check(ShoppingRules.parseQuantity("") == null,
            "Unspecified amount must remain null, never zero.");
        check(ShoppingRules.parseQuantity("1,5") == 1500L,
            "Comma decimal support.");
        check(ShoppingRules.parseQuantity("0.001") == 1L,
            "Three decimal places allowed.");
        check("1.5".equals(ShoppingRules.formatQuantity(1500L)),
            "Display decimal without trailing zeros.");
        check("Ilość nieokreślona".equals(ShoppingRules.formatQuantity(null)),
            "Unspecified quantity display.");
        check(ShoppingRules.knownUnit("kg")
            && ShoppingRules.knownUnit("l")
            && ShoppingRules.knownUnit("szt."),
            "Known units.");
        check(!ShoppingRules.knownUnit("kWh"), "Reject unknown unit.");
        for (String value : new String[]{"0", "-1", "0.0001",
                "abc", "1000000.001"}) invalid(value);
        System.out.println("Shopping rules: optional and decimal quantities, "
            + "unit and bounds: PASS");
    }
}
