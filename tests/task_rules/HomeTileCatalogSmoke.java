package com.edwinkarolczyk.edhome;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** User-owned tiles are not limited to old nine and cannot duplicate IDs. */
public final class HomeTileCatalogSmoke {
    public static void main(String[] args) {
        List<String> initial = HomeTileCatalog.canonical(null, "today,tasks,calendar", true);
        check(initial.size() == 16, "starter Beta should expose 16 targets");
        check(initial.get(0).equals("today"), "legacy tile order must survive");
        check(initial.containsAll(Arrays.asList("timers", "shopping",
            "paycheck", "waste", "storage", "vehicles", "diagnostics")), "missing feature shortcuts");
        String extra = "tile_0123456789abcdef0123456789abcdef";
        List<String> custom = new ArrayList<>(initial);
        custom.add(extra);
        check(HomeTileCatalog.canonical(HomeTileCatalog.encode(custom), "", true)
            .equals(custom), "custom tile did not survive restart");
        List<String> moved = HomeTileCatalog.moved(custom, extra, 1);
        check(moved.size() == 17 && moved.get(1).equals(extra),
            "dynamic reorder failed");
        check(new HashSet<>(moved).size() == moved.size(), "duplicate tile");
        check(HomeTileCatalog.canonical("tasks,tasks,invalid," + extra, "", true)
            .equals(Arrays.asList("tasks", extra)), "invalid ID accepted");
        check(HomeTileCatalog.canonical("tasks,diagnostics,shopping", "", false)
            .equals(Arrays.asList("tasks", "shopping")), "diagnostics leaked into Stable");
        check(HomeTileCatalog.canonical("", "", true).isEmpty(), "empty panel not saved");
        check(HomeTileCatalog.validTarget("paycheck_private", true), "private target missing");
        check(!HomeTileCatalog.validTarget("diagnostics", false),
            "Stable must reject beta diagnostics");
        check(!HomeTileCatalog.validTileId("tile_../../bad"), "unsafe tile ID");
        check(HomeTileCatalog.label("timers").equals("Minutniki"), "incorrect label");
        check(HomeTileCatalog.icon("timers").equals("washer"), "missing icon mapping");
        try {
            HomeTileCatalog.moved(custom, extra, 99);
            throw new AssertionError("Invalid slot accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Home tile catalog migration, 17 shortcuts, target guard, drag: PASS");
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
