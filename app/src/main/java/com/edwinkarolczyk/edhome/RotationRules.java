package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure rules for ordered recurring-assignee rotation. */
final class RotationRules {
    private RotationRules() { }

    static String validate(List<Long> members, boolean recurring) {
        if (members == null || members.isEmpty()) return null; // Rotation disabled.
        if (!recurring) return "Rotacja wykonawców wymaga czynności powtarzalnej.";
        if (members.size() < 2)
            return "Rotacja wymaga co najmniej dwóch domowników.";
        if (members.size() > 50)
            return "Rotacja może zawierać maks. 50 osób.";
        Set<Long> seen = new HashSet<>();
        for (Long id : members) {
            if (id == null || id <= 0 || !seen.add(id))
                return "Rotacja zawiera nieprawidłową lub powtórzoną osobę.";
        }
        return null;
    }

    static Long next(List<Long> ordered, Long current) {
        if (ordered == null || ordered.isEmpty()) return current;
        int index = current == null ? -1 : ordered.indexOf(current);
        return ordered.get((index + 1 + ordered.size()) % ordered.size());
    }

    static List<Long> moved(List<Long> ordered, int from, int to) {
        if (ordered == null || from < 0 || to < 0
                || from >= ordered.size() || to >= ordered.size())
            throw new IllegalArgumentException("Invalid rotation move");
        List<Long> copy = new ArrayList<>(ordered);
        if (from == to) return copy;
        Long item = copy.remove(from);
        copy.add(to, item);
        return copy;
    }
}
