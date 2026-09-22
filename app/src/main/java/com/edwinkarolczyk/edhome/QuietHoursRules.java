package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Pure rules for one configurable overnight notification quiet window. */
final class QuietHoursRules {
    static final String DEFAULT_START = "22:00";
    static final String DEFAULT_END = "07:00";

    private QuietHoursRules() { }

    static boolean validTime(String value) {
        if (value == null || !value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            return false;
        try { return LocalTime.parse(value).toString().equals(value); }
        catch (DateTimeParseException invalid) { return false; }
    }

    /** EDHOME quiet hours are an overnight window, e.g. 22:00–07:00. */
    static boolean validWindow(String start, String end) {
        if (!validTime(start) || !validTime(end)) return false;
        return LocalTime.parse(start).isAfter(LocalTime.parse(end));
    }

    static boolean isQuiet(LocalDateTime moment, String start, String end) {
        requireWindow(start, end);
        LocalTime time = moment.toLocalTime();
        LocalTime quietStart = LocalTime.parse(start);
        LocalTime quietEnd = LocalTime.parse(end);
        return !time.isBefore(quietStart) || time.isBefore(quietEnd);
    }

    /** If Android delivers during quiet hours, move to the first allowed time. */
    static LocalDateTime nextAllowed(LocalDateTime candidate,
            String start, String end) {
        requireWindow(start, end);
        LocalTime time = candidate.toLocalTime();
        LocalTime quietStart = LocalTime.parse(start);
        LocalTime quietEnd = LocalTime.parse(end);
        if (!time.isBefore(quietStart))
            return candidate.toLocalDate().plusDays(1).atTime(quietEnd);
        if (time.isBefore(quietEnd))
            return candidate.toLocalDate().atTime(quietEnd);
        return candidate;
    }

    /**
     * User-selected task reminder inside quiet hours:
     * late-night requests move one hour before quiet starts (legacy EDHOME policy),
     * after-midnight requests move to the quiet end.
     */
    static LocalDateTime adjustRequested(LocalDate day, LocalTime requested,
            String start, String end) {
        requireWindow(start, end);
        LocalTime quietStart = LocalTime.parse(start);
        LocalTime quietEnd = LocalTime.parse(end);
        if (!requested.isBefore(quietStart))
            return day.atTime(quietStart.minusHours(1));
        if (requested.isBefore(quietEnd))
            return day.atTime(quietEnd);
        return day.atTime(requested);
    }

    static String label(String start, String end) {
        requireWindow(start, end);
        return start + "–" + end;
    }

    private static void requireWindow(String start, String end) {
        if (!validWindow(start, end))
            throw new IllegalArgumentException("Invalid quiet-hours window");
    }
}
