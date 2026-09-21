package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TimeSuggestionsSmoke {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        LocalDate monday = LocalDate.of(2026, 9, 21);
        Map<LocalDate, String> days = new HashMap<>();
        TimeSuggestions.ShiftSource source =
            date -> days.getOrDefault(date, "unset");
        LocalDateTime now = monday.atTime(7, 0);

        check(TimeSuggestions.propose(now, 30, source).isEmpty(),
            "Unconfigured shift must not be considered free.");
        days.put(monday, "morning");
        days.put(monday.plusDays(1), "afternoon");
        days.put(monday.plusDays(2), "off");
        List<TimeSuggestions.Option> options =
            TimeSuggestions.propose(now, 90, source);
        check(options.size() == 3, "Three distinct dates expected.");
        check(options.get(0).date.equals(monday)
                && options.get(0).start.equals(LocalTime.of(16, 0)),
            "Morning shift should recommend an after-work slot.");
        check(options.get(1).date.equals(monday.plusDays(1))
                && options.get(1).start.equals(LocalTime.of(8, 0)),
            "Afternoon shift should recommend a morning slot.");
        check(options.get(2).date.equals(monday.plusDays(2))
                && options.get(2).start.equals(LocalTime.of(8, 0)),
            "Off day can be suggested, unknown day cannot.");
        check(TimeSuggestions.propose(now, 480, source).stream()
                .noneMatch(x -> x.date.equals(monday)),
            "480-minute task must not overlap early blocked hours.");

        days.clear();
        days.put(monday, "night");
        days.put(monday.plusDays(1), "off");
        days.put(monday.plusDays(2), "morning");
        options = TimeSuggestions.propose(now, 60, source);
        check(options.size() == 3, "Night/off/morning should offer dates.");
        check(options.get(1).date.equals(monday.plusDays(1))
                && !options.get(1).start.isBefore(LocalTime.of(14, 0)),
            "Reserve rest until 14:00 after previous night shift.");
        days.put(monday.plusDays(1), "unset");
        options = TimeSuggestions.propose(now, 60, source);
        check(options.stream().noneMatch(
                x -> x.date.equals(monday.plusDays(1))),
            "Unset exception must suppress that day's suggestion.");
        days.put(monday.plusDays(1), "off");
        options = TimeSuggestions.propose(monday.atTime(19, 50), 90, source);
        check(options.stream().noneMatch(
                x -> x.date.equals(monday)),
            "Do not suggest a start already passed or an interval after 21.");
        boolean invalid = false;
        try {
            TimeSuggestions.propose(now, 0, source);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid, "Reject invalid estimated duration.");
        System.out.println("Time suggestions: unknown/off/day/night/rest,"
            + " 3 distinct dates, now and duration boundaries: PASS");
    }
}
