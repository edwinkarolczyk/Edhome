package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Rozliczenie jednego rzeczywistego przelewu z jedną pozycją Budżetu.
 *
 * Preflight przed zmianą salda, potwierdzenie SQL, następnie przypisanie
 * do Budżetu. Przerwanie pomiędzy SQL i SharedPreferences można naprawić
 * funkcją repairUnlinked: nigdy nie księguje pieniędzy ponownie.
 */
final class BankBudgetSingleSettlement {
    private BankBudgetSingleSettlement() { }

    static final class Result {
        final String state;
        final long beforeGrosz;
        final long afterGrosz;
        Result(String state,long beforeGrosz,long afterGrosz) {
            this.state=state;
            this.beforeGrosz=beforeGrosz;
            this.afterGrosz=afterGrosz;
        }
    }

    static final class PartialSettlementException extends Exception {
        PartialSettlementException(Throwable cause) {
            super("Przelew zaksięgowano, ale zapis planu wymaga naprawy.",cause);
        }
    }

    private static String budgetOperationId(String itemId,YearMonth month) {
        return UUID.nameUUIDFromBytes(("edhome-budget-pending:"
            +itemId+":"+month).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static Result confirm(SharedPreferences prefs,SQLiteDatabase db,
            BankEvidenceStore.Row row,PaycheckMonthlyBudget.Item selected,
            YearMonth month) throws Exception {
        if(row==null||selected==null||month==null)
            throw new IllegalArgumentException("Brak wybranego przelewu.");
        if(row.date==null||row.date.length()<7
                ||!month.toString().equals(row.date.substring(0,7))
                ||!row.kind.equals(selected.kind))
            throw new IllegalArgumentException(
                "Data lub kierunek przelewu nie pasuje do Budżetu.");
        final String operationId=budgetOperationId(selected.id,month);

        // Przed księgowaniem pobierz aktualny plan, nie przestarzały ekran.
        PaycheckMonthlyBudget.preflightBankMatch(prefs,db,selected.id,
            operationId,row.kind,month);
        try(Cursor evidence=db.rawQuery(
                "SELECT amount_grosz,kind,booking_date,state "
                +"FROM bank_evidence_queue WHERE evidence_key=?",
                new String[]{row.evidenceKey})) {
            if(!evidence.moveToFirst()||!"open".equals(evidence.getString(3))
                    ||evidence.getLong(0)!=row.amount
                    ||!row.kind.equals(evidence.getString(1))
                    ||!row.date.equals(evidence.getString(2)))
                return new Result("CHANGED",
                    PaycheckStore.sharedBalance(db),PaycheckStore.sharedBalance(db));
        }
        long before=PaycheckStore.sharedBalance(db);
        String result=BankEvidenceStore.matchBudgetActual(
            db,row.evidenceKey,operationId);
        if(!"MATCHED".equals(result))
            return new Result(result,before,PaycheckStore.sharedBalance(db));
        // Tylko przypisanie planu: SQL już zawiera faktyczną kwotę.
        // W przypadku błędu NIE próbuj importować ani księgować ponownie.
        try {
            if(!PaycheckMonthlyBudget.match(prefs,db,selected.id,operationId))
                throw new IllegalStateException(
                    "Brak nowego przypisania do planu po potwierdzeniu banku.");
        }catch(Exception error) {
            throw new PartialSettlementException(error);
        }
        boolean confirmed=false;
        try(Cursor tx=db.rawQuery(
                "SELECT status,amount_grosz,statement_key FROM paycheck_transactions "
                +"WHERE operation_id=? AND scope='shared'",
                new String[]{operationId})) {
            if(tx.moveToFirst())
                confirmed="confirmed".equals(tx.getString(0))
                    &&tx.getLong(1)==row.amount
                    &&row.evidenceKey.equals(tx.getString(2));
        }
        boolean linked=false;
        for(PaycheckMonthlyBudget.Item item:PaycheckMonthlyBudget.load(prefs))
            if(item.id.equals(selected.id)
                    &&item.matchedOperationIds.contains(operationId)) {
                linked=true;
                break;
            }
        if(!confirmed||!linked)
            throw new PartialSettlementException(new IllegalStateException(
                "Weryfikacja salda i powiązania Budżetu nie powiodła się."));
        long after=PaycheckStore.sharedBalance(db);
        long expected=Math.addExact(before,
            "income".equals(row.kind)?row.amount:-row.amount);
        if(after!=expected)
            throw new IllegalStateException(
                "Kwota potwierdzona nie odpowiada zmianie salda.");
        return new Result("MATCHED",before,after);
    }

    /**
     * Domknięcie wcześniejszego częściowego potwierdzenia SQL -> Budżet.
     * Uznaje wyłącznie rzeczywiście potwierdzoną pozycję z dowodem bankowym,
     * której UUID zgadza się z planem i miesiącem.
     * Nie wywołuje matchBudgetActual, PaycheckStore.add ani confirm.
     */
    static int repairUnlinked(SharedPreferences prefs,SQLiteDatabase db)
            throws Exception {
        int repaired=0;
        List<PaycheckMonthlyBudget.Item> all=PaycheckMonthlyBudget.load(prefs);
        try(Cursor c=db.rawQuery(
                "SELECT t.operation_id,t.kind,t.note,t.statement_date "
                +"FROM paycheck_transactions t JOIN bank_evidence_queue e "
                +"ON e.evidence_key=t.statement_key "
                +"WHERE t.scope='shared' AND t.status='confirmed' "
                +"AND t.note LIKE 'Budżet %' AND e.state='matched' "
                +"AND e.matched_operation_id=t.operation_id "
                +"ORDER BY t.id DESC LIMIT 500",null)) {
            while(c.moveToNext()) {
                String opId=c.getString(0);
                String kind=c.getString(1);
                String note=c.getString(2);
                String date=c.getString(3);
                if(note==null||note.length()<14||date==null||date.length()<7)
                    continue;
                YearMonth month;
                try {
                    month=YearMonth.parse(note.substring(7,14));
                    if(!month.toString().equals(date.substring(0,7)))continue;
                }catch(Exception invalid){continue;}
                for(PaycheckMonthlyBudget.Item item:
                        PaycheckMonthlyBudget.activeFor(all,month)) {
                    if(!kind.equals(item.kind)
                            ||!opId.equals(budgetOperationId(item.id,month)))
                        continue;
                    if(item.matchedOperationIds.contains(opId))break;
                    if(PaycheckMonthlyBudget.match(prefs,db,item.id,opId)) {
                        repaired++;
                        all=PaycheckMonthlyBudget.load(prefs);
                    }
                    break;
                }
            }
        }
        return repaired;
    }
}
