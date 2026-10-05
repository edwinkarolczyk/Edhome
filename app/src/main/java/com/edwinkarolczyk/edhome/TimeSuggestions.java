package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deliberately modest, local-only planning suggestions. A configured shift
 * describes known work time, NOT proof of availability around other events.
 * Never stores or books a term; the user must choose and save the date.
 */
final class TimeSuggestions {
    interface ShiftSource {
        /** "unset", "off", "morning", "afternoon", "night". */
        String shift(LocalDate date);
    }

    interface WindowSource {
        List<Window> windows(LocalDate date);
    }

    static final class Window {
        final LocalTime start;
        final LocalTime end;

        Window(LocalTime start, LocalTime end) {
            if (start == null || end == null || !start.isBefore(end))
                throw new IllegalArgumentException("Invalid project availability window");
            this.start = start;
            this.end = end;
        }
    }

    static final class Option {
        final LocalDate date;
        final LocalTime start;
        final LocalTime end;

        Option(LocalDate date, LocalTime start, LocalTime end) {
            this.date = date;
            this.start = start;
            this.end = end;
        }
    }

    private static final LocalTime[] CANDIDATES = {
        LocalTime.of(8, 0), LocalTime.of(10, 0),
        LocalTime.of(12, 0), LocalTime.of(14, 0),
        LocalTime.of(16, 0), LocalTime.of(18, 0),
        LocalTime.of(19, 0)
    };

    private TimeSuggestions() { }

    static List<Option> propose(LocalDateTime now, int durationMinutes,
            ShiftSource source) {
        if (now == null || source == null || durationMinutes < 1
                || durationMinutes > 600)
            throw new IllegalArgumentException("Invalid planning inputs");
        ArrayList<Option> options = new ArrayList<>();
        for (int day = 0; day <= 27 && options.size() < 3; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            String shift = source.shift(date);
            // Unknown is not a day off: don't fabricate availability.
            if (!known(shift) || "unset".equals(shift)) continue;
            String priorShift = source.shift(date.minusDays(1));
            for (LocalTime start : CANDIDATES) {
                LocalDateTime begin = date.atTime(start);
                LocalDateTime end = begin.plusMinutes(durationMinutes);
                if (!begin.isAfter(now) || end.toLocalDate().isAfter(date)
                        || end.toLocalTime().isAfter(LocalTime.of(21, 0)))
                    continue;
                if (overlapsWorkOrRest(date, start, end.toLocalTime(),
                        shift, priorShift)) continue;
                options.add(new Option(date, start, end.toLocalTime()));
                break; // Different dates, not three nearly identical hours.
            }
        }
        return Collections.unmodifiableList(options);
    }

    static List<Option> proposeAvailability(LocalDateTime now,
            int durationMinutes, WindowSource source) {
        if (now == null || source == null || durationMinutes < 1
                || durationMinutes > 600)
            throw new IllegalArgumentException("Invalid planning inputs");
        ArrayList<Option> options = new ArrayList<>();
        for (int day = 0; day <= 27 && options.size() < 3; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            List<Window> windows = source.windows(date);
            if (windows == null || windows.isEmpty()) continue;
            for (Window window : windows) {
                LocalDateTime begin = date.atTime(window.start);
                if (begin.isBefore(now)) begin = ceilQuarterHour(now);
                LocalDateTime finish = begin.plusMinutes(durationMinutes);
                LocalDateTime limit = date.atTime(window.end);
                if (begin.isBefore(date.atTime(window.start))
                        || finish.isAfter(limit) || !finish.isAfter(begin))
                    continue;
                options.add(new Option(date, begin.toLocalTime(),
                    finish.toLocalTime()));
                break;
            }
        }
        return Collections.unmodifiableList(options);
    }

    private static LocalDateTime ceilQuarterHour(LocalDateTime value) {
        LocalDateTime rounded=value.withSecond(0).withNano(0);
        int remainder=rounded.getMinute()%15;
        int add=remainder==0?0:15-remainder;
        if(add==0&&(value.getSecond()>0||value.getNano()>0))add=15;
        return rounded.plusMinutes(add);
    }

    private static boolean known(String shift) {
        return "unset".equals(shift) || "off".equals(shift)
            || "morning".equals(shift) || "afternoon".equals(shift)
            || "night".equals(shift);
    }

    private static boolean overlapsWorkOrRest(LocalDate date,
            LocalTime start, LocalTime end, String shift, String priorShift) {
        // After the previous day's night shift (22–06), reserve rest till 14.
        if ("night".equals(priorShift)
                && start.isBefore(LocalTime.of(14, 0))) return true;
        // One hour before and after scheduled working time.
        if ("morning".equals(shift))
            return intersects(start, end, LocalTime.of(5, 0),
                LocalTime.of(15, 0));
        if ("afternoon".equals(shift))
            return intersects(start, end, LocalTime.of(13, 0),
                LocalTime.of(23, 0));
        if ("night".equals(shift))
            return intersects(start, end, LocalTime.of(21, 0),
                LocalTime.MAX);
        return false;
    }

    private static boolean intersects(LocalTime start, LocalTime end,
            LocalTime blockedStart, LocalTime blockedEnd) {
        return start.isBefore(blockedEnd) && end.isAfter(blockedStart);
    }
}
