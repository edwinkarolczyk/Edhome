package com.edwinkarolczyk.edhome;

import java.util.Arrays;
import java.util.Collections;

/** Samodzielny test matematyki rozliczania (bez Android SDK). */
public final class ProjectLaborCalculatorSmoke {
    private static void check(boolean ok, String info) {
        if (!ok) throw new AssertionError(info);
    }

    private static ProjectLaborCalculator.Session session(long task, long start,
            Long end, long rate, long worker) {
        return new ProjectLaborCalculator.Session(task,start,end,rate,worker);
    }

    public static void main(String[] args) {
        long now = 120_000L;
        // 60,00 zl/h = 1 grosz na 600 ms, koszt rosnie przed STOP.
        check(ProjectLaborCalculator.calculate(
            Collections.singletonList(session(1,0L,null,6000,1)),600L)
                .costGrosz == 1, "Pierwszy grosz po 600 ms");
        check(ProjectLaborCalculator.calculate(
            Collections.singletonList(session(1,0L,null,6000,1)),1200L)
                .costGrosz == 2, "Drugi grosz po 1200 ms");
        // Bez sztucznego minimum jednej minuty po zatrzymaniu sesji.
        check(ProjectLaborCalculator.calculate(
            Collections.singletonList(session(1,0L,600L,6000,1)),now)
                .costGrosz == 1, "Krotka sesja");
        check(ProjectLaborCalculator.calculate(
            Collections.singletonList(session(1,0L,0L,6000,1)),now)
                .costGrosz == 0, "Zero sekund");
        // Dwie sesje jednej czynnosci: sumowanie przed zaokragleniem.
        var repeated = ProjectLaborCalculator.calculate(Arrays.asList(
            session(1,0L,350L,6000,1),
            session(1,1_000L,1_350L,6000,1)),now);
        check(repeated.costGrosz == 1, "Sumuj sesje przed zaokragleniem");
        check(repeated.elapsedMillis == 700L, "Sumuj rzeczywisty czas");

        // Dwie czynnosci i rozne stawki, wycena kazdej oddzielnie.
        var mixed = ProjectLaborCalculator.calculate(Arrays.asList(
            session(1,0L,3_600_000L,6000,1),
            session(2,0L,1_800_000L,10000,2)),3_600_000L);
        check(mixed.costGrosz == 11_000, "Suma 60 zl i 50 zl");
        check(mixed.taskCostsGrosz.get(1L)==6000L, "Koszt czynnosci 1");
        check(mixed.taskCostsGrosz.get(2L)==5000L, "Koszt czynnosci 2");
        check(!mixed.overlappingWorkerSessions, "Rozni wykonawcy");
        // Rozne czynnosci tej samej osoby w tym samym czasie: ostrzezenie.
        var overlap = ProjectLaborCalculator.calculate(Arrays.asList(
            session(1,0L,null,6000,1),
            session(2,1L,null,6000,1)),120_000L);
        check(overlap.activeSessions == 2, "Dwa aktywne liczniki");
        check(overlap.overlappingWorkerSessions, "Wykryj konflikt");
        // Brak wykonawcy: nie zgaduj, ze jest to ta sama osoba.
        var unknown = ProjectLaborCalculator.calculate(Arrays.asList(
            session(1,0L,null,6000,0),
            session(2,0L,null,6000,0)),120_000L);
        check(!unknown.overlappingWorkerSessions, "Nie zgaduj wykonawcy");
        // Zmiana stawki pomiedzy sesjami nie zmienia starej sesji.
        var rates = ProjectLaborCalculator.calculate(Arrays.asList(
            session(1,0L,3_600_000L,5000,1),
            session(1,3_600_000L,7_200_000L,6000,1)),7_200_000L);
        check(rates.costGrosz == 11000L, "Historyczne stawki sesji");
        try {
            session(1,100L,90L,6000L,1L);
            throw new AssertionError("Odrzuc bledne znaczniki czasu");
        } catch (IllegalArgumentException expected) { /* OK */ }
        System.out.println("Projektowe koszty robocizny: OK");
    }
}
