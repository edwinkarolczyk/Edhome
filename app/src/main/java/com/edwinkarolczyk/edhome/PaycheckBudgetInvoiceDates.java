package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.YearMonth;

/** Terminy faktur są odrębne od dnia zapłaty szablonu zobowiązania. */
final class PaycheckBudgetInvoiceDates {
    private PaycheckBudgetInvoiceDates() { }

    static int templateDay(int selectedDay) {
        if (selectedDay == 0) return 10;
        if (selectedDay < 1 || selectedDay > 31)
            throw new IllegalArgumentException("Dzień zapłaty musi być od 1 do 31.");
        return selectedDay;
    }

    static LocalDate planned(YearMonth month, int selectedDay, LocalDate invoiceDue) {
        if (month == null)
            throw new IllegalArgumentException("Brak miesiąca budżetu.");
        if (invoiceDue != null && !month.equals(YearMonth.from(invoiceDue)))
            throw new IllegalArgumentException("Termin faktury ma inny miesiąc.");
        LocalDate planned = month.atDay(Math.min(templateDay(selectedDay),month.lengthOfMonth()));
        return invoiceDue != null && invoiceDue.isBefore(planned) ? invoiceDue : planned;
    }

    /**
     * Stara wersja nadpisywała dzień szablonu datą pierwszej faktury
     * wcześniejszą niż 10. Jeśli nie ma znacznika nowego schematu,
     * przywróć domyślny 10. dzień dla kolejnych miesięcy.
     */
    static int migrateLegacyDay(int savedDay, LocalDate firstInvoiceDue,
            LocalDate firstPlannedDate) {
        if (savedDay > 0 && savedDay < 10 && firstInvoiceDue != null
                && firstPlannedDate != null
                && firstInvoiceDue.getDayOfMonth() == savedDay
                && firstPlannedDate.getDayOfMonth() == savedDay)
            return 10;
        return templateDay(savedDay);
    }
}
