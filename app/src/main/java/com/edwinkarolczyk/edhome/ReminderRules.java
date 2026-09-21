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
        return target(date, hhmm, leadDays,
            QuietHoursRules.DEFAULT_START, QuietHoursRules.DEFAULT_END);
    }

    static LocalDateTime target(String date, String hhmm, int leadDays,
            String quietStart, String quietEnd) {
        if (!validTime(hhmm) || !allowedLead(leadDays))
            throw new IllegalArgumentException("Invalid reminder time or lead");
        LocalDate day = LocalDate.parse(date).minusDays(leadDays);
        LocalTime time = LocalTime.parse(hhmm);
        return QuietHoursRules.adjustRequested(
            day, time, quietStart, quietEnd);
    }

    static LocalDateTime nextAllowed(LocalDateTime candidate) {
        return nextAllowed(candidate,
            QuietHoursRules.DEFAULT_START, QuietHoursRules.DEFAULT_END);
    }

    static LocalDateTime nextAllowed(LocalDateTime candidate,
            String quietStart, String quietEnd) {
        return QuietHoursRules.nextAllowed(candidate, quietStart, quietEnd);
    }
}
