package com.edwinkarolczyk.edhome;

import java.time.LocalDateTime;

/** Pure-Java reminder schedule and quiet-hours regression without emulator. */
public final class ReminderRulesSmoke {
    private static void expect(boolean good) {
        if (!good) throw new AssertionError("Reminder rule regression");
    }

    public static void main(String[] args) {
        expect(ReminderRules.validTime("19:30"));
        expect(ReminderRules.validTime("00:00"));
        expect(!ReminderRules.validTime("24:00"));
        expect(!ReminderRules.validTime("9:30"));
        expect(!ReminderRules.validTime("19:60"));
        expect(ReminderRules.allowedLead(0) && ReminderRules.allowedLead(7)
            && !ReminderRules.allowedLead(8));
        expect(ReminderRules.target("2026-09-23", "19:30", 1)
            .equals(LocalDateTime.of(2026, 9, 22, 19, 30)));
        expect(ReminderRules.target("2026-09-23", "22:15", 1)
            .equals(LocalDateTime.of(2026, 9, 22, 21, 0)));
        expect(ReminderRules.target("2026-09-23", "05:20", 0)
            .equals(LocalDateTime.of(2026, 9, 23, 7, 0)));
        expect(ReminderRules.nextAllowed(LocalDateTime.of(
            2026, 9, 23, 23, 30)).equals(LocalDateTime.of(2026, 9, 24, 7, 0)));
        expect(ReminderRules.nextAllowed(LocalDateTime.of(
            2026, 9, 23, 5, 0)).equals(LocalDateTime.of(2026, 9, 23, 7, 0)));
        expect(ReminderRules.nextAllowed(LocalDateTime.of(
            2026, 9, 23, 19, 30)).equals(LocalDateTime.of(2026, 9, 23, 19, 30)));
        System.out.println("Reminder rules: time/lead/quiet hours PASS");
    }
}
