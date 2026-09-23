package com.edwinkarolczyk.edhome;
public final class VehicleReminderRulesSmoke {
    public static void main(String[] args) {
        for (int choice : new int[]{0,1,7,14,30})
            if (!VehicleReminderRules.allowed(choice))
                throw new AssertionError("Rejected " + choice);
        for (int choice : new int[]{-1,2,3,15,31,365})
            if (VehicleReminderRules.allowed(choice))
                throw new AssertionError("Accepted " + choice);
        if (VehicleReminderRules.allowed(null)
                || VehicleReminderRules.index(null) != 0
                || VehicleReminderRules.index(30) != 1
                || VehicleReminderRules.index(0) != 5)
            throw new AssertionError("Opt-in or menu mapping broken");
        System.out.println("Vehicle reminders 0/1/7/14/30, disabled and UI mapping: PASS");
    }
}
