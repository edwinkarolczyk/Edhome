package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Every one of the nine slots must match the preview and preserve all tiles. */
public final class HomeTileOrderSmoke {
    public static void main(String[] args) {
        List<String> original = HomeTileOrder.canonical("");
        check(original.size() == 9);
        for (String dragged : original) {
            for (int slot = 0; slot < 9; slot++) {
                List<String> result = HomeTileOrder.moved(original, dragged, slot);
                check(result.size() == 9);
                check(new HashSet<>(result).equals(new HashSet<>(original)));
                check(result.get(slot).equals(dragged));
                check(HomeTileOrder.canonical(String.join(",", result))
                    .equals(result)); // Same exact order after restart.
                check(new HashSet<>(result).size() == 9);
            }
        }
        check(HomeTileOrder.canonical("tasks,tasks,bad,calendar").size() == 9);
        check(HomeTileOrder.canonical("tasks,tasks,bad,calendar")
            .get(0).equals("tasks"));
        check(HomeTileOrder.canonical("tasks,tasks,bad,calendar")
            .get(1).equals("calendar"));
        check(HomeTileOrder.moved(original, "tasks", 8).get(8).equals("tasks"));
        check(HomeTileOrder.moved(original, "today", 0).get(0).equals("today"));
        check(HomeTileOrder.moved(original, "audit", 4).equals(original));
        mustReject(original, "invalid", 0);
        mustReject(original, "tasks", -1);
        mustReject(original, "tasks", 9);
        List<String> broken = new ArrayList<>(original);
        broken.remove("today");
        mustReject(broken, "tasks", 3);
        broken.add("tasks");
        mustReject(broken, "tasks", 3);
        System.out.println("Home tile 9x9 drop slots, persistence, no lost/duplicate tiles: PASS");
    }

    private static void mustReject(List<String> existing, String source, int slot) {
        try {
            HomeTileOrder.moved(existing, source, slot);
            throw new AssertionError("Invalid reorder accepted");
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
    }

    private static void check(boolean test) {
        if (!test) throw new AssertionError("Home tile order regression failed");
    }
}
