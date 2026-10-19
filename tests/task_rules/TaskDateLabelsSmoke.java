package com.edwinkarolczyk.edhome;

import java.time.LocalDate;

/** Regresja polskich nazw dni w propozycjach zaległych terminów. */
public final class TaskDateLabelsSmoke {
    private static void check(String expected,String actual) {
        if(!expected.equals(actual))
            throw new AssertionError("Oczekiwano „"+expected+"”, otrzymano „"+actual+"”.");
    }

    public static void main(String[] args) {
        check("poniedziałek, 19.10.2026",TaskDateLabels.format("2026-10-19"));
        check("wtorek, 20.10.2026",TaskDateLabels.format(LocalDate.of(2026,10,20)));
        check("środa, 21.10.2026",TaskDateLabels.format("2026-10-21"));
        check("błędna data",TaskDateLabels.format("błędna data"));
        check("",TaskDateLabels.format((String)null));
        System.out.println("Polskie dni tygodnia w proponowanych terminach PASS");
    }
}
