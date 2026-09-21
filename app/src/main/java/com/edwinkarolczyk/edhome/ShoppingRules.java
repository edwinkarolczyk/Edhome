package com.edwinkarolczyk.edhome;

import java.math.BigDecimal;

/** Pure validation for the offline shopping list; 1/1000 unit integer storage. */
final class ShoppingRules {
    static final String[] UNITS = {"szt.", "kg", "l"};
    static final long MAX_MILLI = 1_000_000_000L;

    private ShoppingRules() { }

    static String validatedName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 160)
            throw new IllegalArgumentException("Nazwa: od 1 do 160 znaków.");
        return name;
    }

    static boolean knownUnit(String unit) {
        for (String item : UNITS) if (item.equals(unit)) return true;
        return false;
    }

    /** Empty means quantity unspecified; zero never means unspecified. */
    static Long parseQuantity(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        try {
            long milli = new BigDecimal(value.replace(',', '.'))
                .movePointRight(3).longValueExact();
            if (milli < 1 || milli > MAX_MILLI)
                throw new IllegalArgumentException("Ilość musi być większa od zera.");
            return milli;
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException(
                "Ilość: dodatnia liczba, maks. 3 cyfry po przecinku.");
        }
    }

    static String formatQuantity(Long milli) {
        return milli == null ? "Ilość nieokreślona"
            : BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString();
    }
}
