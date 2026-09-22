package com.edwinkarolczyk.edhome;

public final class HomeTileLayoutSmoke {
    private static void equal(int actual, int expected, String name) {
        if (actual != expected) throw new AssertionError(
            name + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        equal(HomeTileLayout.columns(160), 1, "very narrow");
        equal(HomeTileLayout.columns(320), 2, "small phone");
        equal(HomeTileLayout.columns(360), 2, "narrow phone");
        equal(HomeTileLayout.columns(393), 3, "regular phone");
        equal(HomeTileLayout.columns(600), 5, "large phone/tablet");
        equal(HomeTileLayout.columns(800), 6, "tablet");
        equal(HomeTileLayout.columns(1500), 6, "readability cap");
        if (!HomeTileLayout.validPair(450, 1100)
                || !HomeTileLayout.validPair(800, 1100)
                || HomeTileLayout.validPair(800, 900)
                || HomeTileLayout.validPair(200, 1100)
                || HomeTileLayout.validPair(450, 2500))
            throw new AssertionError("hold threshold validation");
        if (HomeTileLayout.openMenuOnRelease(449, 450, 1100)
                || !HomeTileLayout.openMenuOnRelease(450, 450, 1100)
                || !HomeTileLayout.openMenuOnRelease(1099, 450, 1100)
                || HomeTileLayout.openMenuOnRelease(1100, 450, 1100))
            throw new AssertionError("short hold must not steal long drag");
        System.out.println("Home tiles: adaptive columns and two configurable thresholds: PASS");
    }
}
