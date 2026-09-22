package com.edwinkarolczyk.edhome;

public final class PantryTakeCountdownSmoke {
    private static int count;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        count++;
    }
    public static void main(String[] args) {
        check(PantryTakeCountdown.DEFAULT_SECONDS == 5, "5s default");
        for (int seconds : PantryTakeCountdown.DELAY_OPTIONS)
            check(PantryTakeCountdown.validSeconds(seconds), "valid option " + seconds);
        check(!PantryTakeCountdown.validSeconds(0)
                && !PantryTakeCountdown.validSeconds(11), "invalid options");
        PantryTakeCountdown take = new PantryTakeCountdown(5);
        check(!take.due(10000) && take.pending() == null, "idle");
        check(take.observe("A", 1000), "start A");
        check(take.millisLeft(1000) == 5000 && !take.due(5999), "not early");
        check(!take.observe("A", 3000) && take.millisLeft(3000) == 3000,
            "same camera frame cannot reset timer");
        check(take.observe("B", 4000) && "B".equals(take.pending()),
            "new product replaces pending A");
        check(!take.due(6000) && take.due(9000), "B timer is fresh");
        check("B".equals(take.consume()) && take.consume() == null,
            "only one take per timer");
        take.markCommitted("B");
        check(!take.observe("B", 9100) || take.needsApproval(),
            "committed B cannot be taken silently again");
        take.allowSameAgain();
        check(take.observe("B", 10000), "explicit second B");
        check("B".equals(take.consume()), "second B only after approval");
        take.markCommitted("B");
        check(take.observe("A", 11000), "A pending");
        check("A".equals(take.consume()), "A committed");
        take.markCommitted("A");

        // Regression: frame A, then B, then A must NOT remove A twice.
        check(take.observe("B", 12000), "B pending after A commit");
        check(take.observe("A", 12100), "return to A interrupts B");
        check(take.pending() == null && take.needsApproval()
                && !take.due(100000), "A requires approval, B canceled");
        check(!take.observe("A", 12200), "repeated A frames do not rearm");
        check(take.consume() == null, "no stock operation before approval");
        take.allowSameAgain();
        check(take.observe("A", 13000) && !take.due(17999),
            "explicit second A starts new 5s");
        check("A".equals(take.consume()), "explicit A can be taken");
        take.markCommitted("A");
        check(take.observe("C", 19000), "C pending");
        take.cancel();
        check(take.pending() == null && !take.due(999999),
            "background never commits");
        check(!take.observe("C", 20000), "canceled C frame blocked");
        check(take.observe("D", 20000), "new distinct D accepted");
        check("D".equals(take.consume()), "D consumed");
        take.markCommitted("D");
        check(!take.observe("D", 21000) || take.needsApproval(),
            "last committed never auto-repeats");
        System.out.println("Take countdown A-B-A, cancel and single-use: PASS " + count);
    }
}
