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
        "tasks", "projects", "calendar", "places", "floorplan", "pantry", "audit",
        "updates", "backup", "settings", "today",
        "timers", "shopping", "paycheck", "waste", "scanner", "storage",
        "storage_things", "storage_boxes", "vehicles", "garden"
    ));
    static final List<String> TARGETS = Collections.unmodifiableList(Arrays.asList(
        "tasks", "projects", "today", "calendar", "places", "floorplan", "pantry", "audit",
        "updates", "backup", "settings", "timers", "shopping",
        "paycheck", "paycheck_private", "waste", "scanner", "storage",
        "storage_things", "storage_boxes", "vehicles", "garden", "diagnostics"
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
            case "projects": return "Projekty";
            case "today": return "Na dziś";
            case "calendar": return "Kalendarz";
            case "places": return "Miejsca";
            case "floorplan": return "Plan domu / posesji";
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
            case "scanner": return "Skaner";
            case "storage": return "Magazyn domowy";
            case "storage_things": return "Rzeczy";
            case "storage_boxes": return "Pudełka";
            case "vehicles": return "Pojazdy";
            case "garden": return "Ogród";
            case "diagnostics": return "Diagnostyka";
            default: throw new IllegalArgumentException("Unknown target");
        }
    }

    static String icon(String target) {
        switch (target) {
            case "projects": return "tasks";
            case "timers": return "washer";
            case "shopping": return "box";
            case "paycheck": case "paycheck_private": return "cabinet";
            case "waste": return "box";
            case "scanner": return "audit";
            case "storage": return "shelf";
            case "storage_things": return "audit";
            case "storage_boxes": return "box";
            case "floorplan": return "garage";
            case "vehicles": return "garage";
            case "garden": return "garden";
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
