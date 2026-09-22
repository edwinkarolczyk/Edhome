package com.edwinkarolczyk.edhome;

/**
 * One barcode at a time. Only a still-pending, due code may be consumed.
 * All times are monotonic elapsedRealtime milliseconds, not wall-clock time.
 * An unchanged camera frame must not remove multiple packages.
 */
final class PantryTakeCountdown {
    static final String DELAY_PREF = "pantry_take_delay_seconds";
    static final int DEFAULT_SECONDS = 5;
    static final int[] DELAY_OPTIONS = {3, 5, 8, 10};

    private final long delayMillis;
    private String pending;
    private String blocked = "";
    private String lastCommitted = "";
    private String awaitingApproval = "";
    private long deadline;

    PantryTakeCountdown(int seconds) {
        if (!validSeconds(seconds)) throw new IllegalArgumentException("Invalid countdown");
        delayMillis = seconds * 1000L;
    }

    static boolean validSeconds(int seconds) {
        for (int candidate : DELAY_OPTIONS)
            if (candidate == seconds) return true;
        return false;
    }

    /** A different code replaces a pending timer; the last TAKEN code needs approval. */
    boolean observe(String barcode, long now) {
        if (barcode == null || barcode.isEmpty() || barcode.equals(pending))
            return false;
        // Camera frames A, B, A can otherwise charge A a second time while
        // B has not even been taken. Cancel B and require deliberate approval.
        if (barcode.equals(lastCommitted)) {
            if (pending == null && barcode.equals(awaitingApproval)) return false;
            pending = null;
            awaitingApproval = barcode;
            return true;
        }
        if (pending == null && barcode.equals(blocked)) return false;
        awaitingApproval = "";
        pending = barcode;
        deadline = now + delayMillis;
        return true;
    }

    boolean needsApproval() { return !awaitingApproval.isEmpty(); }

    /** Call only after SQLite confirms COMMITTED, never when the timer merely fires. */
    void markCommitted(String barcode) { lastCommitted = barcode; }

    String pending() { return pending; }
    long millisLeft(long now) {
        return pending == null ? 0 : Math.max(0, deadline - now);
    }
    boolean due(long now) { return pending != null && now >= deadline; }

    /** Consume BEFORE making a database call, never twice for the same timer. */
    String consume() {
        String result = pending;
        if (result != null) {
            blocked = result;
            pending = null;
            awaitingApproval = "";
        }
        return result;
    }

    /** Cancels a proposed stock change and suppresses repeated camera frames. */
    void cancel() {
        consume();
        awaitingApproval = "";
    }

    /** A deliberate second package can reuse the same barcode. */
    void allowSameAgain() {
        blocked = "";
        lastCommitted = "";
        awaitingApproval = "";
    }
}
