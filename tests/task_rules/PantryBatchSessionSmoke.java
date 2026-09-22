package com.edwinkarolczyk.edhome;

public final class PantryBatchSessionSmoke {
    private static int count;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        count++;
    }
    public static void main(String[] args) {
        PantryBatchSession batch = new PantryBatchSession();
        check(!batch.launchCamera(), "not started");
        check(!batch.receiveScan("5901234123457"), "not awaiting camera");
        batch.start("ADD");
        check(batch.active() && "ADD".equals(batch.mode()), "active ADD");
        check(batch.launchCamera(), "camera opens");
        check(!batch.launchCamera(), "no overlapping cameras");
        check(batch.receiveScan("5901234123457"), "first scan accepted");
        check(!batch.receiveScan("5901234123457"), "duplicate callback ignored");
        check(!batch.launchCamera(), "no camera before confirmation");
        check(!batch.repeated("5901234123457"), "first scan not repeated");
        check(batch.commit("5901234123457"), "one confirmation");
        check(!batch.commit("5901234123457"), "same confirmation not twice");
        check(batch.committed() == 1, "exactly one operation");
        check(batch.launchCamera(), "continue after commit");
        check(batch.receiveScan("5901234123457"), "next scan");
        check(batch.repeated("5901234123457"), "repeat needs extra confirmation");
        check(batch.skip(), "reject accidental repeat without a stock change");
        check(batch.launchCamera(), "camera after skip");
        check(batch.receiveScan("5901234123457"), "new explicit scan");
        check(batch.repeated("5901234123457"), "new scan is still repeated");
        check(batch.commit("5901234123457"), "intentional second item");
        check(batch.committed() == 2, "intentional second count");
        check(batch.stop() == 2, "summary count");
        check(!batch.active() && !batch.launchCamera(), "stop prevents relaunch");
        batch.start("TAKE");
        check("TAKE".equals(batch.mode()), "TAKE mode");
        check(batch.launchCamera(), "camera TAKE");
        check(!batch.receiveScan(null), "back/cancel is not a scan");
        check(batch.stop() == 0, "cancelled, no operations");
        check(!batch.commit("5901234123457"), "cannot commit after stop");
        try {
            batch.start("BROKEN");
            throw new AssertionError("invalid mode accepted");
        } catch (IllegalArgumentException expected) { count++; }
        System.out.println("Pantry batch scan state: PASS " + count);
    }
}
