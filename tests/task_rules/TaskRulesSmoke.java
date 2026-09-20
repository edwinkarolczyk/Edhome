package com.edwinkarolczyk.edhome;

import java.time.LocalDate;

/** Runs on CI without Android emulator or JUnit dependencies. */
public final class TaskRulesSmoke {
    private static void equals(String actual, String expected) {
        if (!expected.equals(actual)) throw new AssertionError(
            "Expected " + expected + ", got " + actual);
    }

    private static void valid(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        LocalDate feb = LocalDate.of(2026, 2, 1);
        valid(TaskRules.validate("Winogrona", "", "once", 1) == null,
            "One-time task may have no date");
        valid(TaskRules.validate("Zima", "", "before_winter", 1) != null,
            "Recurring tasks require a date");
        valid(TaskRules.validate("Ok", "2026-02-30", "once", 1) != null,
            "Invalid calendar day must be rejected");
        valid(TaskRules.validate("Ok", "2026-02-28", "every_days", 0) != null,
            "Zero interval must be rejected");
        equals(TaskRules.nextDue("2026-01-31", "monthly", 1, feb), "2026-02-28");
        equals(TaskRules.nextDue("2026-01-31", "monthly", 1,
            LocalDate.of(2026, 2, 28)), "2026-03-31");
        equals(TaskRules.nextDue("2024-02-29", "yearly", 1,
            LocalDate.of(2027, 3, 1)), "2028-02-29");
        equals(TaskRules.nextDue("2026-09-10", "every_days", 7,
            LocalDate.of(2026, 9, 20)), "2026-09-24");
        equals(TaskRules.nextDue("2026-11-15", "before_winter", 1,
            LocalDate.of(2026, 11, 15)), "2027-11-15");
        equals(TaskRules.nextDue("2026-12-31", "weekly", 1,
            LocalDate.of(2026, 12, 15)), "2027-01-07");
        valid(TaskRules.validate(" ", "2026-10-01", "once", 1) != null,
            "Empty title must be rejected");
        valid(TaskRules.validate("Ok", "2026-12-01", "every_weeks", 366) != null,
            "Interval upper bound must be enforced");
        valid(TaskRules.validate("Ok", "2026-12-01", "not_a_rule", 1) != null,
            "Unknown repeat rule must be rejected");
        equals(TaskRules.nextDue("2026-12-31", "daily", 1,
            LocalDate.of(2026, 12, 31)), "2027-01-01");
        equals(TaskRules.nextDue("2026-01-31", "every_months", 2,
            LocalDate.of(2026, 3, 30)), "2026-03-31");
        equals(TaskRules.nextDue("2026-01-31", "every_months", 2,
            LocalDate.of(2026, 3, 31)), "2026-05-31");
        valid(TaskRules.nextDue("2026-10-01", "once", 1,
            LocalDate.of(2026, 10, 1)) == null, "One-off must not repeat");
        System.out.println("TaskRulesSmoke: 17 checks passed");
    }
}
