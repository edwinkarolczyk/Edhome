package com.edwinkarolczyk.edhome;

/** Day-accurate, optional policy end-date suggestion. */
public final class VehiclePolicyDatesSmoke {
    private static void check(String begin, String end) {
        String actual = VehiclePolicyDates.yearMinusDay(begin);
        if (!end.equals(actual))
            throw new AssertionError(begin + ": expected " + end + ", got " + actual);
    }

    public static void main(String[] args) {
        check("2026-09-09", "2027-09-08");
        check("2023-09-09", "2024-09-08");
        check("2024-02-28", "2025-02-27");
        check("2024-02-29", "2025-02-27");
        check("2026-01-01", "2026-12-31");
        check("2026-12-31", "2027-12-30");
        System.out.println("OC suggestion: calendar year minus one day, leap years: PASS");
    }
}
