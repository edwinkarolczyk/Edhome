package com.edwinkarolczyk.edhome;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** PLN stored as integer grosze; no floating-point money in PayCheck. */
final class MoneyRules {
    static final long MAX_GROSZ = 99_999_999_999L;
    static final String[] CATEGORIES = {
        "shopping","bills","home","vehicle","salary","other"
    };
    static final String[] CATEGORY_LABELS = {
        "Zakupy","Rachunki","Dom","Pojazdy","Wynagrodzenie","Inne"
    };
    private MoneyRules() { }

    static long parse(String raw) {
        if (raw == null || !raw.trim().matches(
                "[0-9]{1,9}([.,][0-9]{1,2})?"))
            throw new IllegalArgumentException(
                "Podaj kwotę PLN z maksymalnie 2 miejscami po przecinku.");
        try {
            long grosz = new BigDecimal(raw.trim().replace(',','.'))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0,RoundingMode.UNNECESSARY).longValueExact();
            if (grosz < 1 || grosz > MAX_GROSZ)
                throw new IllegalArgumentException("Kwota musi być dodatnia.");
            return grosz;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Nieprawidłowa kwota.",error);
        }
    }

    static String format(long grosz) {
        String sign=grosz<0?"−":"";
        return sign + BigDecimal.valueOf(grosz<0?-grosz:grosz,2)
            .setScale(2,RoundingMode.UNNECESSARY)
            .toPlainString().replace('.',',') + " zł";
    }

    static boolean category(String value) {
        for(String candidate:CATEGORIES)
            if(candidate.equals(value))return true;
        return false;
    }

    static String categoryLabel(String id) {
        for(int i=0;i<CATEGORIES.length;i++)
            if(CATEGORIES[i].equals(id))return CATEGORY_LABELS[i];
        return "Inne";
    }
}
