package com.edwinkarolczyk.edhome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe, conservative parser for catalogue quantity text such as 6 x 1.5 L or 12 x 85 g. */
final class PantryPackSuggestion {
    final int unitsPerScan;
    final String unit;
    final long sizeMilli;
    final String label;

    private PantryPackSuggestion(int unitsPerScan, String unit, long sizeMilli,
            String label) {
        this.unitsPerScan = unitsPerScan;
        this.unit = unit;
        this.sizeMilli = sizeMilli;
        this.label = label == null ? "" : label.trim();
    }

    static PantryPackSuggestion none() {
        return new PantryPackSuggestion(1, "szt.", 1000, "");
    }

    static PantryPackSuggestion verified(int unitsPerScan, String unit,
            long sizeMilli, String label) {
        if (unitsPerScan < 1 || unitsPerScan > 10000
                || !PantryPackageRules.valid(unit, sizeMilli))
            return none();
        return new PantryPackSuggestion(unitsPerScan, unit, sizeMilli, label);
    }

    static PantryPackSuggestion parse(String raw) {
        if (raw == null) return none();
        String text = raw.trim().toLowerCase(Locale.ROOT)
            .replace('×', 'x').replace(',', '.');
        if (text.isEmpty() || text.length() > 120) return none();

        Pattern multi = Pattern.compile(
            "(?:^|\\s)([1-9][0-9]{0,3})\\s*x\\s*([0-9]{1,7}(?:\\.[0-9]{1,3})?)\\s*(ml|cl|dl|l|g|kg|szt\\.?|pcs?|pieces?)(?:\\b|$)");
        Matcher m = multi.matcher(text);
        if (m.find()) {
            int count = Integer.parseInt(m.group(1));
            UnitAmount amount = amount(m.group(2), m.group(3));
            if (count <= 1000 && amount != null)
                return new PantryPackSuggestion(count, amount.unit,
                    amount.milli, raw.trim());
        }

        Pattern pieces = Pattern.compile(
            "(?:^|\\s)([1-9][0-9]{0,3})\\s*(szt\\.?|pcs?|pieces?)(?:\\b|$)");
        m = pieces.matcher(text);
        if (m.find()) {
            int count = Integer.parseInt(m.group(1));
            if (count <= 1000)
                return new PantryPackSuggestion(count, "szt.", 1000, raw.trim());
        }

        Pattern single = Pattern.compile(
            "(?:^|\\s)([0-9]{1,7}(?:\\.[0-9]{1,3})?)\\s*(ml|cl|dl|l|g|kg)(?:\\b|$)");
        m = single.matcher(text);
        if (m.find()) {
            UnitAmount amount = amount(m.group(1), m.group(2));
            if (amount != null)
                return new PantryPackSuggestion(1, amount.unit,
                    amount.milli, raw.trim());
        }
        return none();
    }

    private static UnitAmount amount(String number, String rawUnit) {
        try {
            BigDecimal value = new BigDecimal(number);
            String unit = rawUnit.toLowerCase(Locale.ROOT);
            BigDecimal milli;
            String base;
            if ("ml".equals(unit)) { base = "l"; milli = value; }
            else if ("cl".equals(unit)) { base = "l"; milli = value.multiply(BigDecimal.TEN); }
            else if ("dl".equals(unit)) { base = "l"; milli = value.multiply(BigDecimal.valueOf(100)); }
            else if ("l".equals(unit)) { base = "l"; milli = value.multiply(BigDecimal.valueOf(1000)); }
            else if ("g".equals(unit)) { base = "kg"; milli = value; }
            else if ("kg".equals(unit)) { base = "kg"; milli = value.multiply(BigDecimal.valueOf(1000)); }
            else return null;
            long parsed = milli.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (!PantryPackageRules.valid(base, parsed)) return null;
            return new UnitAmount(base, parsed);
        } catch (Exception invalid) {
            return null;
        }
    }

    private static final class UnitAmount {
        final String unit;
        final long milli;
        UnitAmount(String unit, long milli) { this.unit = unit; this.milli = milli; }
    }
}
