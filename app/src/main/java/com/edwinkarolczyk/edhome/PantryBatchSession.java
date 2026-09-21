package com.edwinkarolczyk.edhome;

/**
 * One camera result -> one explicit confirmation -> one durable stock operation.
 * This is intentionally in-memory: a restarted Activity must not silently
 * restart the camera or change inventory.
 */
final class PantryBatchSession {
    private boolean active;
    private boolean cameraPending;
    private boolean awaitingConfirmation;
    private String mode = "";
    private String lastCommittedBarcode = "";
    private int committed;

    void start(String selectedMode) {
        if (!"ADD".equals(selectedMode) && !"TAKE".equals(selectedMode))
            throw new IllegalArgumentException("Unknown batch mode");
        if (active) throw new IllegalStateException("Batch already active");
        active = true;
        mode = selectedMode;
        cameraPending = false;
        awaitingConfirmation = false;
        committed = 0;
        lastCommittedBarcode = "";
    }

    boolean active() { return active; }
    boolean cameraPending() { return cameraPending; }
    boolean awaitingConfirmation() { return awaitingConfirmation; }
    String mode() { return mode; }
    int committed() { return committed; }

    boolean launchCamera() {
        if (!active || cameraPending || awaitingConfirmation) return false;
        cameraPending = true;
        return true;
    }

    boolean receiveScan(String barcode) {
        if (!active || !cameraPending) return false;
        cameraPending = false;
        if (barcode == null) return false;
        awaitingConfirmation = true;
        return true;
    }

    boolean repeated(String barcode) {
        return active && awaitingConfirmation && barcode != null
            && barcode.equals(lastCommittedBarcode);
    }

    boolean commit(String barcode) {
        if (!active || !awaitingConfirmation || barcode == null) return false;
        committed++;
        lastCommittedBarcode = barcode;
        awaitingConfirmation = false;
        return true;
    }

    boolean skip() {
        if (!active || !awaitingConfirmation) return false;
        awaitingConfirmation = false;
        return true;
    }

    int stop() {
        int finished = committed;
        active = false;
        cameraPending = false;
        awaitingConfirmation = false;
        mode = "";
        lastCommittedBarcode = "";
        committed = 0;
        return finished;
    }
}
