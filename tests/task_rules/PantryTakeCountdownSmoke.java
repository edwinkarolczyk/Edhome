package com.edwinkarolczyk.edhome;

public final class PantryTakeCountdownSmoke {
    private static int checks;
    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        check(PantryTakeCountdown.DEFAULT_SECONDS == 5, "default 5s");
        check(PantryTakeCountdown.validSeconds(3) && PantryTakeCountdown.validSeconds(5)
            && PantryTakeCountdown.validSeconds(8)
            && PantryTakeCountdown.validSeconds(10), "settings options");
        check(!PantryTakeCountdown.validSeconds(0)
            && !PantryTakeCountdown.validSeconds(11), "invalid delay");
        PantryTakeCountdown take = new PantryTakeCountdown(5);
        check(!take.due(999999) && take.pending() == null, "idle does nothing");
        check(take.observe("EAN_A", 1000), "first scan starts countdown");
        check(take.millisLeft(1000) == 5000 && !take.due(5999), "not early");
        check(!take.observe("EAN_A", 3500) && take.millisLeft(3500) == 2500,
            "duplicate camera frame cannot restart timer");
        check(take.observe("EAN_B", 4700) && "EAN_B".equals(take.pending()),
            "new code replaces old");
        check(!take.due(6000) && take.due(9700), "new timer counts from new code");
        check("EAN_B".equals(take.consume()), "exact new code consumed");
        check(take.consume() == null, "same countdown cannot commit twice");
        check(!take.observe("EAN_B", 9800), "camera frame cannot take second pack");
        take.allowSameAgain();
        check(take.observe("EAN_B", 10000), "explicit second pack allowed");
        take.cancel();
        check(take.pending() == null && !take.due(100000),
            "background/cancel cannot auto commit");
        check(!take.observe("EAN_B", 11000), "cancelled code remains blocked");
        check(take.observe("EAN_C", 11000), "different code starts new timer");
        check("EAN_C".equals(take.consume()), "third code can commit");
        check(take.pending() == null && !take.due(100000),
            "no delayed action after consume");
        check(new PantryTakeCountdown(3).millisLeft(0) == 0, "other setting idle");
        System.out.println("PantryTakeCountdown lifecycle/replacement/single-use: PASS "
            + checks);
    }
}
