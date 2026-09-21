package com.edwinkarolczyk.edhome;

/** Timer constraints, due-time math and import-status regression without Android. */
public final class DeviceTimerRulesSmoke {
    public static void main(String[] args) {
        final long now = 1789980000000L;
        check(DeviceTimerRules.endAt(now, 1) == now + 60000);
        check(DeviceTimerRules.endAt(now, 90) == now + 5400000);
        check(DeviceTimerRules.endAt(now, 1440) == now + 86400000);
        check(DeviceTimerRules.minutesLeft(now + 60001, now) == 2);
        check(DeviceTimerRules.minutesLeft(now + 60000, now) == 1);
        check(DeviceTimerRules.minutesLeft(now - 1, now) == 0);
        for (String type : DeviceTimerRules.TYPES) {
            check(DeviceTimerRules.validRecord(type, "Program",
                now, now + 60000, "running", null));
            check(DeviceTimerRules.validRecord(type, "Program",
                now, now + 60000, "acknowledged", now + 70000));
            check(DeviceTimerRules.validRecord(type, "Program",
                now, now + 60000, "cancelled", null));
        }
        check(!DeviceTimerRules.validRecord("fake", "Program",
            now, now + 60000, "running", null));
        check(!DeviceTimerRules.validRecord("washer", " ",
            now, now + 60000, "running", null));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now, "running", null));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now + 60000, "acknowledged", null));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now + 60000, "acknowledged", now + 1));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now + 60000, "cancelled", now + 60000));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now + 60000, "wrong", null));
        check(!DeviceTimerRules.validRecord("washer", "Program",
            now, now + 86400001, "running", null));
        for (int minutes : new int[]{-1, 0, 1441}) {
            try {
                DeviceTimerRules.endAt(now, minutes);
                throw new AssertionError("Invalid duration accepted");
            } catch (IllegalArgumentException expected) { }
        }
        System.out.println("Device timers: duration, completion, status and import: PASS");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Timer regression failed");
    }
}
