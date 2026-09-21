package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Pure local-time reminder rules. No exact-delivery guarantee from Android. */
final class ReminderRules {
    static final int[] LEADS = {0, 1, 2, 3, 7};
    static final String[] LEAD_LABELS = {
        "W dniu terminu", "1 dzień wcześniej", "2 dni wcześniej",
        "3 dni wcześniej", "7 dni wcześniej"
    };

    private ReminderRules() { }

    static boolean allowedLead(int days) {
        for (int value : LEADS) if (value == days) return true;
        return false;
    }

    static boolean validTime(String time) {
        if (time == null || !time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            return false;
        try { return LocalTime.parse(time).toString().equals(time); }
        catch (DateTimeParseException bad) { return false; }
    }

    static LocalDateTime target(String date, String hhmm, int leadDays) {
        if (!validTime(hhmm) || !allowedLead(leadDays))
            throw new IllegalArgumentException("Invalid reminder time or lead");
        LocalDate day = LocalDate.parse(date).minusDays(leadDays);
        LocalTime time = LocalTime.parse(hhmm);
        // Quiet hours are 22:00–07:00. Advance late reminders to 21:00
        // on the chosen reminder day instead of announcing overnight.
        if (!time.isBefore(LocalTime.of(22, 0)))
            time = LocalTime.of(21, 0);
        if (time.isBefore(LocalTime.of(7, 0)))
            time = LocalTime.of(7, 0);
        return day.atTime(time);
    }

    static LocalDateTime nextAllowed(LocalDateTime candidate) {
        LocalTime time = candidate.toLocalTime();
        if (!time.isBefore(LocalTime.of(22, 0)))
            return candidate.toLocalDate().plusDays(1).atTime(7, 0);
        if (time.isBefore(LocalTime.of(7, 0)))
            return candidate.toLocalDate().atTime(7, 0);
        return candidate;
    }
}
