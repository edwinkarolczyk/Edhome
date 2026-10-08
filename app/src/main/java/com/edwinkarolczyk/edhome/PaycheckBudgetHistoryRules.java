package com.edwinkarolczyk.edhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;

/** Reguły dziennika płatności bez zależności od Androida i SQLite. */
final class PaycheckBudgetHistoryRules {
    private PaycheckBudgetHistoryRules() { }

    static LocalDate bankDate(long createdAt, String statementDate, ZoneId zone) {
        if (statementDate != null && !statementDate.isBlank()) {
            try { return LocalDate.parse(statementDate); }
            catch (Exception ignored) { }
        }
        if (createdAt <= 0L) throw new IllegalArgumentException("Brak daty transakcji.");
        return Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate();
    }

    static String differenceType(long confirmedGrosz, long dueBeforeGrosz) {
        if (confirmedGrosz <= 0L || dueBeforeGrosz < 0L)
            throw new IllegalArgumentException("Nieprawidłowa kwota płatności.");
        int cmp = Long.compare(confirmedGrosz,dueBeforeGrosz);
        return cmp > 0 ? "OVERPAYMENT" : (cmp < 0 ? "UNDERPAYMENT" : "");
    }

    static long differenceGrosz(long confirmedGrosz, long dueBeforeGrosz) {
        return Math.abs(Math.subtractExact(confirmedGrosz,dueBeforeGrosz));
    }

    static YearMonth bankMonth(long createdAt, String statementDate, ZoneId zone) {
        return YearMonth.from(bankDate(createdAt,statementDate,zone));
    }
}
