package com.edwinkarolczyk.edhome;

import java.time.LocalDateTime;

/** Czyste reguły kolizji terminów, testowane bez Androida. */
final class TimeSlotRules {
    private TimeSlotRules() { }

    /** Sąsiednie przedziały [a,b) i [b,c) NIE kolidują. */
    static boolean overlaps(LocalDateTime aStart,LocalDateTime aEnd,
            LocalDateTime bStart,LocalDateTime bEnd) {
        if(aStart==null||aEnd==null||bStart==null||bEnd==null
                ||!aStart.isBefore(aEnd)||!bStart.isBefore(bEnd))
            throw new IllegalArgumentException("Nieprawidłowe przedziały czasu.");
        return aStart.isBefore(bEnd)&&aEnd.isAfter(bStart);
    }

    static LocalDateTime ceilQuarter(LocalDateTime instant) {
        if(instant==null)throw new IllegalArgumentException("Brak daty.");
        LocalDateTime rounded=instant.withSecond(0).withNano(0);
        int rem=rounded.getMinute()%15;
        if(rem!=0)return rounded.plusMinutes(15-rem);
        return rounded.equals(instant)?rounded:rounded.plusMinutes(15);
    }
}
