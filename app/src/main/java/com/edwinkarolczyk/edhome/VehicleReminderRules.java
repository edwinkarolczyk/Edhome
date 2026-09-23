package com.edwinkarolczyk.edhome;

/** Vehicle-specific, opt-in reminder choices (independent from task reminders). */
final class VehicleReminderRules {
    static final Integer[] LEADS = {null, 30, 14, 7, 1, 0};
    static final String[] LABELS = {
        "Wyłączone", "30 dni wcześniej", "14 dni wcześniej",
        "7 dni wcześniej", "1 dzień wcześniej", "W dniu terminu"
    };

    private VehicleReminderRules() { }

    static boolean allowed(Integer lead) {
        if (lead == null) return false;
        for (Integer choice : LEADS)
            if (choice != null && choice.equals(lead)) return true;
        return false;
    }

    static int index(Integer lead) {
        for (int i = 0; i < LEADS.length; i++)
            if (lead == null ? LEADS[i] == null : lead.equals(LEADS[i])) return i;
        return 0;
    }
}
