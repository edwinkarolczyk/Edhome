package com.edwinkarolczyk.edhome;

/** Device-width home grid and user-configurable long-press thresholds. */
final class HomeTileLayout {
    static final String SHORT_KEY = "home_tile_short_hold_ms";
    static final String DRAG_KEY = "home_tile_drag_hold_ms";
    static final int DEFAULT_SHORT_MS = 450;
    static final int DEFAULT_DRAG_MS = 1100;
    static final int[] SHORT_OPTIONS = {300, 450, 600, 800};
    static final int[] DRAG_OPTIONS = {900, 1100, 1400, 1800};
    private static final int MIN_TILE_DP = 104;
    private static final int GAP_DP = 9;
    private static final int PAGE_PADDING_DP = 32;

    private HomeTileLayout() { }

    /** Never overflow a narrow phone; use at most six readable tablet columns. */
    static int columns(int viewportDp) {
        int content = Math.max(1, viewportDp - PAGE_PADDING_DP);
        int fit = (content + GAP_DP) / (MIN_TILE_DP + GAP_DP);
        return Math.max(1, Math.min(6, fit));
    }

    static boolean validShort(int milliseconds) {
        for (int allowed : SHORT_OPTIONS)
            if (allowed == milliseconds) return true;
        return false;
    }

    static boolean validDrag(int milliseconds) {
        for (int allowed : DRAG_OPTIONS)
            if (allowed == milliseconds) return true;
        return false;
    }

    static boolean validPair(int shortMs, int dragMs) {
        return validShort(shortMs) && validDrag(dragMs)
            && dragMs - shortMs >= 200;
    }

    /** Up before the menu threshold is an ordinary tap. */
    static boolean openMenuOnRelease(long heldMs, int shortMs, int dragMs) {
        return validPair(shortMs, dragMs)
            && heldMs >= shortMs && heldMs < dragMs;
    }
}
