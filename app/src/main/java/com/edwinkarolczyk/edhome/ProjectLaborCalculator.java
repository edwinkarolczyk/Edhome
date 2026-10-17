package com.edwinkarolczyk.edhome;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robocizna projektu: podglad na zywo na podstawie rzeczywistych znacznikow
 * czasu. Nie zmienia salda PayCheck, ani tabel sesji.
 *
 * Stawki podawane sa w groszach/godzine; kwoty zaokraglamy do 1 grosza
 * dopiero po zsumowaniu sesji tej samej czynnosci. To nie jest czas planowany
 * ani pole worked_minutes, ktore historycznie zaokragla do pelnych minut.
 */
final class ProjectLaborCalculator {
    static final long MAX_HOURLY_RATE_GROSZ = 100_000_000L;
    private static final BigInteger MILLIS_PER_HOUR =
        BigInteger.valueOf(3_600_000L);

    static final class Session {
        final long taskId;
        final long startedAt;
        final Long endedAt;
        final long hourlyRateGrosz;
        final long workerId;

        Session(long taskId, long startedAt, Long endedAt,
                long hourlyRateGrosz, long workerId) {
            if (taskId <= 0 || startedAt < 0
                    || (endedAt != null && endedAt < startedAt))
                throw new IllegalArgumentException("Nieprawidlowa sesja pracy.");
            if (hourlyRateGrosz < 0
                    || hourlyRateGrosz > MAX_HOURLY_RATE_GROSZ)
                throw new IllegalArgumentException("Nieprawidlowa stawka robocizny.");
            this.taskId = taskId;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.hourlyRateGrosz = hourlyRateGrosz;
            this.workerId = workerId;
        }
    }

    static final class Summary {
        final long elapsedMillis;
        final long costGrosz;
        final int activeSessions;
        final boolean overlappingWorkerSessions;
        final Map<Long, Long> taskCostsGrosz;

        Summary(long elapsedMillis, long costGrosz, int activeSessions,
                boolean overlappingWorkerSessions, Map<Long, Long> taskCostsGrosz) {
            this.elapsedMillis = elapsedMillis;
            this.costGrosz = costGrosz;
            this.activeSessions = activeSessions;
            this.overlappingWorkerSessions = overlappingWorkerSessions;
            this.taskCostsGrosz = Collections.unmodifiableMap(taskCostsGrosz);
        }
    }

    private static final class Interval {
        final long start;
        final long end;
        Interval(long start, long end) {this.start = start; this.end = end;}
    }

    private ProjectLaborCalculator() { }

    /** 'now' jest jednym wspolnym odczytem zegara dla wszystkich czynnosci. */
    static Summary calculate(List<Session> sessions, long now) {
        if (now < 0) throw new IllegalArgumentException("Nieprawidlowy zegar.");
        if (sessions == null) throw new IllegalArgumentException("Brak sesji.");
        Map<Long, BigInteger> taskWeightedMillis = new HashMap<>();
        Map<Long, List<Interval>> workers = new HashMap<>();
        long elapsedMillis = 0L;
        int activeSessions = 0;
        for (Session session : sessions) {
            if (session == null) throw new IllegalArgumentException("Pusta sesja.");
            long end = session.endedAt == null
                ? Math.max(now, session.startedAt) : session.endedAt;
            if (session.endedAt == null) activeSessions++;
            long duration = end - session.startedAt;
            elapsedMillis = Math.addExact(elapsedMillis, duration);
            BigInteger weighted = BigInteger.valueOf(duration)
                .multiply(BigInteger.valueOf(session.hourlyRateGrosz));
            taskWeightedMillis.merge(session.taskId, weighted, BigInteger::add);
            if (session.workerId > 0 && duration > 0)
                workers.computeIfAbsent(session.workerId, ignored -> new ArrayList<>())
                    .add(new Interval(session.startedAt, end));
        }

        boolean overlap = false;
        for (List<Interval> intervals : workers.values()) {
            intervals.sort(Comparator.comparingLong(i -> i.start));
            long previousEnd = Long.MIN_VALUE;
            for (Interval interval : intervals) {
                if (interval.start < previousEnd) overlap = true;
                previousEnd = Math.max(previousEnd, interval.end);
            }
        }

        Map<Long, Long> costs = new HashMap<>();
        long totalGrosz = 0L;
        for (Map.Entry<Long, BigInteger> item : taskWeightedMillis.entrySet()) {
            // Dodatnie liczby: zaokraglenie matematyczne do pelnego grosza.
            long amount = item.getValue().add(MILLIS_PER_HOUR.shiftRight(1))
                .divide(MILLIS_PER_HOUR).longValueExact();
            costs.put(item.getKey(), amount);
            totalGrosz = Math.addExact(totalGrosz, amount);
        }
        return new Summary(elapsedMillis, totalGrosz, activeSessions, overlap, costs);
    }
}
