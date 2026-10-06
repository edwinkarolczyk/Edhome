package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Pure calendar/recurrence rules. Dates are local ISO days, never UTC midnights. */
final class TaskRules {
    static final String[] RULES = {
        "once", "daily", "weekly", "monthly", "yearly",
        "every_days", "every_weeks", "every_months", "every_years",
        "before_spring", "before_summer", "before_autumn", "before_winter"
    };
    static final String[] LABELS = {
        "Jednorazowo", "Codziennie", "Co tydzień", "Co miesiąc", "Co rok",
        "Co N dni", "Co N tygodni", "Co N miesięcy", "Co N lat",
        "Przed wiosną", "Przed latem", "Przed jesienią", "Przed zimą"
    };

    private TaskRules() { }

    static int index(String rule) {
        for (int i = 0; i < RULES.length; i++) if (RULES[i].equals(rule)) return i;
        return 0;
    }

    static String label(String rule, int every) {
        int index = index(rule);
        if (index >= 5 && index <= 8) return LABELS[index].replace("N", String.valueOf(every));
        return LABELS[index];
    }

    static boolean recurring(String rule) { return !"once".equals(rule); }
    static boolean custom(String rule) { return rule.startsWith("every_"); }

    /** User-facing task duration in hours; storage stays in whole minutes. */
    static String hoursText(int minutes) {
        java.math.BigDecimal hours = java.math.BigDecimal.valueOf(minutes)
            .divide(java.math.BigDecimal.valueOf(60), 2,
                java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros();
        return hours.toPlainString().replace('.', ',');
    }

    /** Accept Polish comma or decimal point and convert hours back to minutes. */
    static Integer minutesFromHours(String text, int minMinutes, int maxMinutes) {
        if (text == null || minMinutes < 1 || maxMinutes < minMinutes) return null;
        String normalized = text.trim().replace(',', '.');
        if (normalized.isEmpty()) return null;
        try {
            java.math.BigDecimal hours = new java.math.BigDecimal(normalized);
            if (hours.compareTo(java.math.BigDecimal.ZERO) <= 0) return null;
            int minutes = hours.multiply(java.math.BigDecimal.valueOf(60))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValue();
            return minutes >= minMinutes && minutes <= maxMinutes
                ? minutes : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Parse separate whole-hour and minute fields into storage minutes. */
    static Integer minutesFromParts(String hoursText, String minutesText,
            int minMinutes, int maxMinutes, boolean clampMinimum) {
        if (minMinutes < 1 || maxMinutes < minMinutes) return null;
        String hoursValue = hoursText == null ? "" : hoursText.trim();
        String minutesValue = minutesText == null ? "" : minutesText.trim();
        if (hoursValue.isEmpty() && minutesValue.isEmpty()) return null;
        int hours = 0;
        int minutes = 0;
        try {
            if (!hoursValue.isEmpty()) hours = Integer.parseInt(hoursValue);
            if (!minutesValue.isEmpty()) minutes = Integer.parseInt(minutesValue);
        } catch (NumberFormatException error) {
            return null;
        }
        if (hours < 0 || minutes < 0 || minutes > 59) return null;
        long total = hours * 60L + minutes;
        if (total > maxMinutes) return null;
        if (total < minMinutes) return clampMinimum ? minMinutes : null;
        return (int) total;
    }

    static String validate(String title, String dueDate, String rule, int every) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 160)
            return "Podaj nazwę (maks. 160 znaków).";
        if (index(rule) == 0 && !"once".equals(rule)) return "Nieznana reguła powtarzania.";
        if (every < 1 || every > 365) return "Odstęp musi mieścić się w zakresie 1–365.";
        if (dueDate == null || dueDate.isEmpty()) {
            return recurring(rule) ? "Ustaw pierwszą datę dla czynności cyklicznej." : null;
        }
        try {
            LocalDate parsed = LocalDate.parse(dueDate);
            if (parsed.getYear() < 2000 || parsed.getYear() > 2100)
                return "Rok terminu musi mieścić się w zakresie 2000–2100.";
        } catch (DateTimeParseException error) {
            return "Data musi mieć postać RRRR-MM-DD.";
        }
        return null;
    }

    static String nextDue(String date, String rule, int every, LocalDate completed) {
        if (!recurring(rule)) return null;
        LocalDate base = LocalDate.parse(date);
        LocalDate candidate;
        // Anchor every calculation to the original due date, so 31 January
        // -> 28 February -> 31 March, and 29 February returns in leap years.
        long step = 0;
        do {
            if (++step > 50000) throw new IllegalStateException("Date out of supported range");
            long amount = step * (TaskRules.custom(rule) ? every : 1L);
            switch (rule) {
                case "daily": case "every_days": candidate = base.plusDays(amount); break;
                case "weekly": case "every_weeks": candidate = base.plusWeeks(amount); break;
                case "monthly": case "every_months": candidate = base.plusMonths(amount); break;
                case "yearly": case "every_years": candidate = base.plusYears(amount); break;
                case "before_spring": case "before_summer":
                case "before_autumn": case "before_winter":
                    candidate = base.plusYears(step); break;
                default: throw new IllegalArgumentException("Unknown recurrence rule");
            }
        } while (!candidate.isAfter(completed));
        return candidate.toString();
    }
}
