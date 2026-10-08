package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/** Wykonywalna regresja dziennika przelewów: daty i jawne różnice. */
public final class PaycheckBudgetHistorySmoke {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void equal(String wanted,String actual,String label) {
        check(wanted.equals(actual),label+" oczekiwano "+wanted+", otrzymano "+actual);
    }
    private static void money(long wanted,long actual,String label) {
        check(wanted==actual,label+" oczekiwano "+wanted+", otrzymano "+actual);
    }
    public static void main(String[] args) {
        ZoneId warsaw=ZoneId.of("Europe/Warsaw");
        long recorded=LocalDate.of(2026,10,8).atStartOfDay(warsaw)
            .toInstant().toEpochMilli();
        equal("2026-09",PaycheckBudgetHistoryRules.bankMonth(
            recorded,"2026-09-30",warsaw).toString(),
            "przelew we wrześniu, akceptacja w październiku");
        equal("2026-09-30",PaycheckBudgetHistoryRules.bankDate(
            recorded,"2026-09-30",warsaw).toString(),
            "rzeczywista data księgowania");
        equal("2026-10",PaycheckBudgetHistoryRules.bankMonth(
            recorded,null,warsaw).toString(),
            "wpis ręczny bez daty wyciągu");
        equal("2026-10",PaycheckBudgetHistoryRules.bankMonth(
            recorded,"invalid",warsaw).toString(),
            "niepoprawna data wyciągu: bezpieczny fallback");
        equal("UNDERPAYMENT",PaycheckBudgetHistoryRules.differenceType(
            5000L,10000L),"wpłata częściowa 50 ze 100");
        money(5000L,PaycheckBudgetHistoryRules.differenceGrosz(
            5000L,10000L),"niedopłata 50 zł");
        equal("",PaycheckBudgetHistoryRules.differenceType(
            10000L,10000L),"pełne pokrycie planu");
        equal("OVERPAYMENT",PaycheckBudgetHistoryRules.differenceType(
            97000L,96650L),"nadpłata 3.50 zł");
        money(350L,PaycheckBudgetHistoryRules.differenceGrosz(
            97000L,96650L),"wielkość nadpłaty");
        money(650L,PaycheckBudgetHistoryRules.differenceGrosz(
            96000L,96650L),"wielkość niedopłaty");
        equal("OVERPAYMENT",PaycheckBudgetHistoryRules.differenceType(
            5000L,0L),"dodatkowa wpłata po pełnym rozliczeniu");
        boolean rejected=false;
        try { PaycheckBudgetHistoryRules.differenceType(0L,100L); }
        catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"zero zł nie jest potwierdzoną płatnością");
        System.out.println("Historia PayCheck etap 3: 12 scenariuszy PASS");
    }
}
