package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Configurable overnight quiet-hours and reminder adjustment regression. */
public final class QuietHoursRulesSmoke {
    public static void main(String[] args) {
        check(QuietHoursRules.validWindow("22:00", "07:00"));
        check(QuietHoursRules.validWindow("23:30", "06:15"));
        check(!QuietHoursRules.validWindow("07:00", "22:00"));
        check(!QuietHoursRules.validWindow("22:00", "22:00"));
        check(!QuietHoursRules.validWindow("xx", "07:00"));

        LocalDate day = LocalDate.of(2026, 9, 21);
        check(QuietHoursRules.adjustRequested(day, LocalTime.of(23, 0),
            "22:00", "07:00").equals(day.atTime(21, 0)));
        check(QuietHoursRules.adjustRequested(day, LocalTime.of(5, 30),
            "22:00", "07:00").equals(day.atTime(7, 0)));
        check(QuietHoursRules.adjustRequested(day, LocalTime.of(19, 0),
            "22:00", "07:00").equals(day.atTime(19, 0)));

        LocalDateTime late = day.atTime(23, 30);
        check(QuietHoursRules.isQuiet(late, "23:00", "06:00"));
        check(QuietHoursRules.nextAllowed(late, "23:00", "06:00")
            .equals(day.plusDays(1).atTime(6, 0)));
        LocalDateTime early = day.atTime(5, 0);
        check(QuietHoursRules.isQuiet(early, "23:00", "06:00"));
        check(QuietHoursRules.nextAllowed(early, "23:00", "06:00")
            .equals(day.atTime(6, 0)));
        check(!QuietHoursRules.isQuiet(day.atTime(12, 0),
            "23:00", "06:00"));

        check(ReminderRules.target("2026-09-21", "23:00", 0,
            "22:00", "07:00").equals(day.atTime(21, 0)));
        check(ReminderRules.target("2026-09-21", "05:00", 0,
            "23:00", "06:00").equals(day.atTime(6, 0)));
        System.out.println("Configurable quiet hours and reminder adjustment: PASS");
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Quiet-hours regression failed");
    }
}
