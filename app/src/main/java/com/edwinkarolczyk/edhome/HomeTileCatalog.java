package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User-owned shortcuts: a stable tile ID is NOT the module or record ID. */
final class HomeTileCatalog {
    static final String ORDER_KEY = "home_tiles_v2_order";
    static final List<String> STARTER_IDS = Collections.unmodifiableList(Arrays.asList(
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today",
        "timers", "shopping", "paycheck", "waste", "storage", "vehicles"
    ));
    static final List<String> TARGETS = Collections.unmodifiableList(Arrays.asList(
        "tasks", "today", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "timers", "shopping",
        "paycheck", "paycheck_private", "waste", "storage", "vehicles", "diagnostics"
    ));

    private HomeTileCatalog() { }

    static boolean validTileId(String id) {
        return STARTER_IDS.contains(id) || "diagnostics".equals(id)
            || id != null && id.matches("tile_[0-9a-f]{32}");
    }

    static boolean validTarget(String target, boolean beta) {
        return TARGETS.contains(target)
            && (beta || !"diagnostics".equals(target));
    }

    static String defaultTarget(String tileId) {
        return TARGETS.contains(tileId) ? tileId : "tasks";
    }

    static String label(String target) {
        switch (target) {
            case "tasks": return "Czynności";
            case "today": return "Na dziś";
            case "calendar": return "Kalendarz";
            case "places": return "Miejsca";
            case "pantry": return "Spiżarnia";
            case "audit": return "Remanent";
            case "updates": return "Aktualizacje";
            case "backup": return "Kopia danych";
            case "settings": return "Ustawienia";
            case "timers": return "Minutniki";
            case "shopping": return "Zakupy";
            case "paycheck": return "PayCheck";
            case "paycheck_private": return "Moje finanse";
            case "waste": return "Odpady";
            case "storage": return "Magazyn";
            case "vehicles": return "Pojazdy";
            case "diagnostics": return "Diagnostyka";
            default: throw new IllegalArgumentException("Unknown target");
        }
    }

    static String icon(String target) {
        switch (target) {
            case "timers": return "washer";
            case "shopping": return "box";
            case "paycheck": case "paycheck_private": return "cabinet";
            case "waste": return "box";
            case "storage": return "shelf";
            case "vehicles": return "garage";
            case "diagnostics": return "settings";
            default: return target;
        }
    }

    /** Migrate old nine fixed tiles without overwriting their order or appearance. */
    static List<String> canonical(String stored, String legacy, boolean beta) {
        if (stored == null) {
            List<String> initial = new ArrayList<>(HomeTileOrder.canonical(legacy));
            for (String id : STARTER_IDS) if (!initial.contains(id)) initial.add(id);
            if (beta) initial.add("diagnostics");
            return initial;
        }
        List<String> result = new ArrayList<>();
        for (String id : stored.split(",", -1)) {
            if (validTileId(id) && (beta || !"diagnostics".equals(id))
                    && !result.contains(id)) result.add(id);
        }
        return result;
    }

    static List<String> moved(List<String> existing, String source, int slot) {
        if (existing == null || !existing.contains(source)
                || slot < 0 || slot >= existing.size()
                || new HashSet<>(existing).size() != existing.size())
            throw new IllegalArgumentException("Invalid tile reorder");
        List<String> result = new ArrayList<>(existing);
        result.remove(source);
        result.add(slot, source);
        return result;
    }

    static String encode(List<String> ids) {
        if (ids == null || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("Invalid shortcuts");
        for (String id : ids) if (!validTileId(id))
            throw new IllegalArgumentException("Unknown shortcut");
        return String.join(",", ids);
    }
}
