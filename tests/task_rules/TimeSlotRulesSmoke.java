package com.edwinkarolczyk.edhome;

import java.time.LocalDateTime;

/** Wykonywalna regresja: styczne przedziały, kolizje i kwadranse. */
public final class TimeSlotRulesSmoke {
    private static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LocalDateTime a=LocalDateTime.of(2026,10,19,16,0);
        LocalDateTime b=a.plusMinutes(30);
        check(TimeSlotRules.overlaps(a,b,a.plusMinutes(15),b.plusMinutes(15)),
            "Częściowo nachodzące czynności powinny kolidować.");
        check(!TimeSlotRules.overlaps(a,b,b,b.plusMinutes(20)),
            "Przedziały sąsiednie muszą być dozwolone.");
        check(!TimeSlotRules.overlaps(a,b,a.minusHours(1),a),
            "Przedział kończący się na początku następnego nie koliduje.");
        check(TimeSlotRules.overlaps(a,b,a,b),
            "Dwa identyczne przedziały muszą kolidować.");
        check(TimeSlotRules.ceilQuarter(a).equals(a),
            "Pełny kwadrans nie może się przesuwać.");
        check(TimeSlotRules.ceilQuarter(a.plusSeconds(1))
                .equals(a.plusMinutes(15)),
            "Sekunda po pełnym kwadransie przesuwa do kolejnego.");
        check(TimeSlotRules.ceilQuarter(a.plusMinutes(17))
                .equals(a.plusMinutes(30)),
            "Zaokrąglenie minut w górę.");
        check(TimeSlotRules.ceilQuarter(a.plusHours(7).plusMinutes(47))
                .equals(a.plusHours(8)),
            "Kwadrans po północy zmienia też datę.");
        boolean failed=false;
        try {
            TimeSlotRules.overlaps(a,a,b,b.plusMinutes(5));
        }catch(IllegalArgumentException expected) {failed=true;}
        check(failed,"Niepoprawny pusty przedział musi zostać odrzucony.");
        System.out.println("Smart planner: kolizje, granice i kwadranse PASS");
    }
}
