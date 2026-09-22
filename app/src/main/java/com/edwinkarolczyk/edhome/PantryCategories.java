package com.edwinkarolczyk.edhome;

/** Explicit product categories; don't guess an Open Products Facts item's type. */
final class PantryCategories {
    static final String[] IDS = {"other","food","household","beauty","pet"};
    static final String[] LABELS = {
        "Pozostałe", "Żywność i napoje", "Chemia domowa",
        "Kosmetyki i higiena", "Karma dla zwierząt"
    };
    static final String[] FILTER_LABELS = {
        "Wszystkie", "Żywność i napoje", "Chemia domowa",
        "Kosmetyki i higiena", "Karma dla zwierząt", "Pozostałe"
    };
    static final String[] FILTER_IDS = {
        "", "food", "household", "beauty", "pet", "other"
    };
    private PantryCategories() { }
    static boolean known(String id) {
        for (String candidate : IDS) if (candidate.equals(id)) return true;
        return false;
    }
    static String label(String id) {
        for (int i = 0; i < IDS.length; i++)
            if (IDS[i].equals(id)) return LABELS[i];
        return LABELS[0];
    }
    static String fromSource(String source) {
        if ("Open Food Facts".equals(source)) return "food";
        if ("Open Beauty Facts".equals(source)) return "beauty";
        if ("Open Pet Food Facts".equals(source)) return "pet";
        // Open Products Facts covers many unrelated non-food items.
        return "other";
    }
}
