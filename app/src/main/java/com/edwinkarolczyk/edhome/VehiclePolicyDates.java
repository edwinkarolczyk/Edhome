package com.edwinkarolczyk.edhome;

import java.time.LocalDate;

/** Optional OC date suggestion: a calendar year from the start, minus one day.
 * Calendar-year arithmetic matters around leap years and month boundaries.
 */
final class VehiclePolicyDates {
    private VehiclePolicyDates() { }

    static String yearMinusDay(String startIso) {
        return LocalDate.parse(startIso).plusYears(1).minusDays(1).toString();
    }
}
