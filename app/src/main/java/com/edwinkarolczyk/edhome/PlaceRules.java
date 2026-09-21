package com.edwinkarolczyk.edhome;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validation shared by Android place editing and JSON import. Pure Java. */
final class PlaceRules {
    private PlaceRules() { }

    static String validateFields(String name, String kind, String icon) {
        if (name == null || name.trim().isEmpty() || name.length() > 160)
            return "Podaj nazwę miejsca (maks. 160 znaków).";
        if (kind == null || kind.length() > 80)
            return "Rodzaj miejsca może mieć maks. 80 znaków.";
        if (icon == null || !java.util.Arrays.asList(
                "places", "pantry", "audit", "today", "tasks",
                "calendar", "updates", "backup", "settings",
                "washer", "dryer", "dishwasher", "shelf", "cabinet",
                "drawer", "room", "garden", "garage", "workshop", "box"
            ).contains(icon))
            return "Wybierz ikonę z dostępnej biblioteki.";
        return null;
    }

    /** Parent must exist and never be the moved item or one of its descendants. */
    static boolean canMove(Map<Long, Long> parents, Long movingId, Long parentId) {
        if (parentId == null) return true;
        Set<Long> seen = new HashSet<>();
        Long node = parentId;
        while (node != null) {
            if (!parents.containsKey(node) || !seen.add(node)
                    || node.equals(movingId)) return false;
            node = parents.get(node);
        }
        return true;
    }

    static boolean validForest(Map<Long, Long> parents) {
        for (Long id : parents.keySet())
            if (id == null || id <= 0 || !canMove(parents, id, parents.get(id)))
                return false;
        return true;
    }
}
