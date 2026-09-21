package com.edwinkarolczyk.edhome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Qty in pantry is WHOLE PACKAGES; size_milli is content of ONE package. */
final class PantryPackageRules {
    static final String[] UNITS = {"szt.", "kg", "l"};
    static final int MAX_MILLI = 1_000_000_000;
    private PantryPackageRules() { }
    static boolean valid(String unit, long milli) {
        if (milli < 1 || milli > MAX_MILLI) return false;
        if ("szt.".equals(unit)) return milli % 1000 == 0;
        return "kg".equals(unit) || "l".equals(unit);
    }
    static long parse(String input, String unit) {
        if (input == null || !input.trim().matches("[0-9]{1,7}([.,][0-9]{1,3})?"))
            throw new IllegalArgumentException("Podaj ilość na opakowanie (maks. 3 cyfry po przecinku).");
        try {
            long milli = new BigDecimal(input.trim().replace(',', '.'))
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (!valid(unit, milli))
                throw new IllegalArgumentException("Nieprawidłowa wielkość opakowania.");
            return milli;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Nieprawidłowa wielkość opakowania.", error);
        }
    }
    static String format(long milli) {
        return BigDecimal.valueOf(milli, 3)
            .stripTrailingZeros().toPlainString().replace('.', ',');
    }
    static String summary(int packages, String unit, long sizeMilli) {
        if (!valid(unit, sizeMilli) || packages < 0 || packages > 100_000_000)
            throw new IllegalArgumentException("Nieprawidłowy stan opakowań.");
        return packages + " opak. × " + format(sizeMilli) + " " + unit
            + " = " + format(packages * sizeMilli) + " " + unit;
    }
}
