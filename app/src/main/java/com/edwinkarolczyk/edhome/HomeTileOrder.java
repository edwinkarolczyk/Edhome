package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One insertion rule for on-screen preview and durable saved order. */
final class HomeTileOrder {
    static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today"
    ));

    private HomeTileOrder() { }

    static List<String> canonical(String saved) {
        List<String> result = new ArrayList<>(IDS.size());
        for (String id : (saved == null ? "" : saved).split(",")) {
            if (IDS.contains(id) && !result.contains(id)) result.add(id);
        }
        for (String id : IDS) if (!result.contains(id)) result.add(id);
        return result;
    }

    /** Slot means the final zero-based visible position, not "before the target". */
    static List<String> moved(List<String> existing, String source, int slot) {
        if (existing == null || existing.size() != IDS.size()
                || new HashSet<>(existing).size() != IDS.size()
                || !new HashSet<>(existing).equals(new HashSet<>(IDS))
                || !existing.contains(source) || slot < 0 || slot >= IDS.size())
            throw new IllegalArgumentException("Invalid home tile reorder");
        List<String> result = new ArrayList<>(existing);
        result.remove(source);
        result.add(slot, source);
        if (result.size() != IDS.size()
                || !new HashSet<>(result).equals(new HashSet<>(IDS)))
            throw new IllegalStateException("Home tile was lost during reorder");
        return result;
    }
}
