package com.edwinkarolczyk.edhome;

import java.util.HashMap;
import java.util.Map;

/** Prevent missing parents, cycles and invalid metadata without Android runtime. */
public final class PlaceRulesSmoke {
    public static void main(String[] args) {
        Map<Long, Long> parents = new HashMap<>();
        parents.put(1L, null); // Dom
        parents.put(2L, 1L); // Kuchnia
        parents.put(3L, 2L); // Półka
        parents.put(4L, null); // Garaż
        check(PlaceRules.validForest(parents));
        check(PlaceRules.canMove(parents, 3L, 4L));
        check(PlaceRules.canMove(parents, 3L, null));
        check(!PlaceRules.canMove(parents, 1L, 3L));
        check(!PlaceRules.canMove(parents, 2L, 2L));
        check(!PlaceRules.canMove(parents, null, 999L));
        parents.put(1L, 3L);
        check(!PlaceRules.validForest(parents));
        parents.put(1L, null);
        parents.put(2L, 777L);
        check(!PlaceRules.validForest(parents));
        check(PlaceRules.validateFields("Półka 1", "", "shelf") == null);
        check(PlaceRules.validateFields("Szafka", "meble", "cabinet") == null);
        check(PlaceRules.validateFields("Dom", "Dom", "places") == null);
        check(PlaceRules.validateFields("  ", "", "shelf") != null);
        check(PlaceRules.validateFields("Dom", "", "unknown") != null);
        System.out.println("Place hierarchy, cycles, metadata and icons: PASS");
    }

    private static void check(boolean result) {
        if (!result) throw new AssertionError("Place regression failed");
    }
}
