package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.YearMonth;

/** Regresja rozdzielenia dnia szablonu od faktury konkretnego miesiąca. */
public final class PaycheckBudgetInvoiceDatesSmoke {
    private static void eq(String label,String expected,LocalDate actual) {
        if (!expected.equals(actual.toString()))
            throw new AssertionError(label+": "+actual+" zamiast "+expected);
    }
    private static void eqDay(String label,int expected,int actual) {
        if (expected!=actual) throw new AssertionError(label+": "+actual);
    }
    public static void main(String[] args) {
        YearMonth october=YearMonth.of(2026,10);
        YearMonth november=YearMonth.of(2026,11);
        eqDay("bez dnia → dziesiąty",10,PaycheckBudgetInvoiceDates.templateDay(0));
        eq("brak faktury","2026-10-10",
            PaycheckBudgetInvoiceDates.planned(october,0,null));
        eq("wcześniejsza faktura","2026-10-07",
            PaycheckBudgetInvoiceDates.planned(october,0,LocalDate.of(2026,10,7)));
        eq("kolejny miesiąc zachowuje dziesiąty","2026-11-10",
            PaycheckBudgetInvoiceDates.planned(november,0,null));
        eq("termin faktury późniejszy niż plan","2026-10-10",
            PaycheckBudgetInvoiceDates.planned(october,10,LocalDate.of(2026,10,23)));
        eq("własny dzień 15, termin 14","2026-10-14",
            PaycheckBudgetInvoiceDates.planned(october,15,LocalDate.of(2026,10,14)));
        eq("własny dzień 15 bez faktury","2026-10-15",
            PaycheckBudgetInvoiceDates.planned(october,15,null));
        eq("luty i dzień 31","2027-02-28",
            PaycheckBudgetInvoiceDates.planned(YearMonth.of(2027,2),31,null));
        eqDay("stary błąd dueDay=7 po pierwszej fakturze",10,
            PaycheckBudgetInvoiceDates.migrateLegacyDay(7,
                LocalDate.of(2026,10,7),LocalDate.of(2026,10,7)));
        eqDay("własny dzień 7 bez faktury pozostaje",7,
            PaycheckBudgetInvoiceDates.migrateLegacyDay(7,null,null));
        eqDay("prawidłowy dzień 15 pozostaje",15,
            PaycheckBudgetInvoiceDates.migrateLegacyDay(15,
                LocalDate.of(2026,10,7),LocalDate.of(2026,10,7)));
        boolean wrongMonth=false;
        try { PaycheckBudgetInvoiceDates.planned(october,10,
                LocalDate.of(2026,11,7)); }
        catch(IllegalArgumentException expected) { wrongMonth=true; }
        if(!wrongMonth) throw new AssertionError("Faktura obcego miesiąca zaakceptowana");
        System.out.println("Budżet faktury: 12 scenariuszy dat PASS");
    }
}
