package com.edwinkarolczyk.edhome;

/** Independent local appliance timers. Never claim exact Android alarm delivery. */
final class DeviceTimerRules {
    static final String[] TYPES = {"washer", "dryer", "dishwasher"};
    static final String[] LABELS = {"Pralka", "Suszarka", "Zmywarka"};
    static final int MIN_MINUTES = 1;
    static final int MAX_MINUTES = 1440;
    private static final long MINUTE_MS = 60000L;
    private static final long MAX_EPOCH = 4102444800000L; // year 2100

    private DeviceTimerRules() { }

    static boolean validType(String type) {
        for (String allowed : TYPES) if (allowed.equals(type)) return true;
        return false;
    }

    static boolean validTitle(String name) {
        return name != null && !name.trim().isEmpty()
            && name.length() <= 80;
    }

    static long endAt(long start, int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES
                || start <= 0 || start >= MAX_EPOCH - MAX_MINUTES * MINUTE_MS)
            throw new IllegalArgumentException("Czas: 1–1440 minut.");
        return start + minutes * MINUTE_MS;
    }

    static boolean validRecord(String type, String title, long start, long end,
            String status, Long acknowledgedAt) {
        if (!validType(type) || !validTitle(title) || start <= 0
                || end > MAX_EPOCH || end < start + MINUTE_MS
                || end > start + MAX_MINUTES * MINUTE_MS) return false;
        if ("running".equals(status))
            return acknowledgedAt == null;
        if ("acknowledged".equals(status))
            return acknowledgedAt != null && acknowledgedAt >= end
                && acknowledgedAt <= MAX_EPOCH;
        if ("cancelled".equals(status)) return acknowledgedAt == null;
        return false;
    }

    static long minutesLeft(long end, long now) {
        return Math.max(0L, (end - now + MINUTE_MS - 1) / MINUTE_MS);
    }
}
