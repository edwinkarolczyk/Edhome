package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Waste collection is an ordinary task with typed fraction metadata. */
final class WasteRules {
    static final String[] FRACTIONS = {
        "mixed", "plastic", "paper", "glass", "bio", "other"
    };
    static final String[] LABELS = {
        "Zmieszane", "Metale i tworzywa", "Papier", "Szkło", "Bio", "Inne"
    };
    static final String[] CYCLES = {
        "Jednorazowo", "Co tydzień", "Co 2 tygodnie", "Co miesiąc"
    };

    private WasteRules() { }

    static boolean known(String fraction) {
        for (String key : FRACTIONS) if (key.equals(fraction)) return true;
        return false;
    }

    static String label(String fraction) {
        for (int i = 0; i < FRACTIONS.length; i++)
            if (FRACTIONS[i].equals(fraction)) return LABELS[i];
        throw new IllegalArgumentException("Unknown waste fraction");
    }

    static String validate(String fraction, String due, String rule, int every) {
        if (!known(fraction)) return "Nieznana frakcja odpadów.";
        if (due == null || due.isEmpty()) return "Wybierz dzień wystawienia odpadów.";
        try {
            LocalDate date = LocalDate.parse(due);
            if (!date.toString().equals(due)
                    || date.getYear() < 2000 || date.getYear() > 2100)
                return "Nieprawidłowy dzień wystawienia.";
        } catch (DateTimeParseException error) {
            return "Nieprawidłowy dzień wystawienia.";
        }
        // The quick-add offers four intervals. Existing waste tasks may later
        // be edited with the complete ordinary-task recurrence editor.
        return TaskRules.validate("Wystaw: " + label(fraction),
            due, rule, every);
    }
}
