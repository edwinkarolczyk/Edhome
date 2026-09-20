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
