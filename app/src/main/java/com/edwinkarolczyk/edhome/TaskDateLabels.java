package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Czytelna polska data w propozycjach; zapis w bazie pozostaje ISO-8601. */
final class TaskDateLabels {
    private static final DateTimeFormatter POLISH_DATE =
        DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.forLanguageTag("pl-PL"));

    private TaskDateLabels() { }

    static String format(LocalDate date) {
        return date == null ? "" : date.format(POLISH_DATE);
    }

    static String format(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return "";
        try {
            return format(LocalDate.parse(isoDate));
        } catch (DateTimeParseException error) {
            return isoDate;
        }
    }
}
