package com.edwinkarolczyk.edhome;

/** Pure-Java waste-task regression, run without emulator. */
public final class WasteRulesSmoke {
    public static void main(String[] args) {
        check(WasteRules.validate("paper", "2026-09-23", "weekly", 1) == null);
        check(WasteRules.validate("bio", "2026-09-23", "every_weeks", 2) == null);
        check(WasteRules.validate("mixed", "2026-09-23", "monthly", 1) == null);
        check(WasteRules.validate("glass", "2026-09-23", "once", 1) == null);
        check(WasteRules.validate("unknown", "2026-09-23", "weekly", 1) != null);
        check(WasteRules.validate("paper", "", "weekly", 1) != null);
        check(WasteRules.validate("paper", "2026-02-30", "weekly", 1) != null);
        check(WasteRules.validate("paper", "2026-09-23", "invalid", 1) != null);
        check(WasteRules.validate("paper", "2026-09-23", "every_weeks", 0) != null);
        check("Papier".equals(WasteRules.label("paper")));
        // Completing the same recurring waste task must not make another task.
        check("2026-09-30".equals(TaskRules.nextDue("2026-09-23",
            "weekly", 1, java.time.LocalDate.of(2026, 9, 23))));
        check("2026-10-07".equals(TaskRules.nextDue("2026-09-23",
            "every_weeks", 2, java.time.LocalDate.of(2026, 9, 23))));
        System.out.println("Waste fraction/date/recurrence rules: PASS");
    }

    private static void check(boolean expected) {
        if (!expected) throw new AssertionError("Waste regression failed");
    }
}
