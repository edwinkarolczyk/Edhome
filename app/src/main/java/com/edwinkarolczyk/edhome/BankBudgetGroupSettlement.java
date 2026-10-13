package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Grupowe rozliczenie WYŁĄCZNIE po świadomym wyborze użytkownika.
 * Każdy wiersz bankowy to maksymalnie jedna potwierdzona transakcja PayCheck.
 * SQL jest transakcyjny; plan i historia zapisywane są jednym commit prefs.
 */
final class BankBudgetGroupSettlement {
    private BankBudgetGroupSettlement() { }

    private static final class Evidence {
        final String key, kind, date, state;
        final long amount;
        Evidence(String key,String kind,String date,String state,long amount) {
            this.key=key; this.kind=kind; this.date=date; this.state=state;
            this.amount=amount;
        }
    }

    private static Evidence read(SQLiteDatabase db,String key) {
        try(Cursor c=db.rawQuery(
                "SELECT kind,booking_date,state,amount_grosz "
                +"FROM bank_evidence_queue WHERE evidence_key=?",
                new String[]{key})) {
            if(!c.moveToFirst())
                throw new IllegalArgumentException("Brak operacji w historii bankowej.");
            return new Evidence(key,c.getString(0),c.getString(1),
                c.getString(2),c.getLong(3));
        }
    }

    private static void pending(SQLiteDatabase db,String operationId,String kind) {
        try(Cursor c=db.rawQuery(
                "SELECT 1 FROM paycheck_transactions "
                +"WHERE operation_id=? AND scope='shared' AND status='pending' "
                +"AND kind=? AND note LIKE 'Budżet %' AND statement_key IS NULL",
                new String[]{operationId,kind})) {
            if(!c.moveToFirst())
                throw new IllegalArgumentException(
                    "Pozycja planu nie jest już oczekująca.");
        }
    }

    private static PaycheckMonthlyBudget.Item active(SharedPreferences prefs,
            String itemId,YearMonth month) throws Exception {
        for(PaycheckMonthlyBudget.Item item:PaycheckMonthlyBudget.activeFor(
                PaycheckMonthlyBudget.load(prefs),month))
            if(item.id.equals(itemId))return item;
        throw new IllegalArgumentException("Pozycja Budżetu nie jest aktywna.");
    }

    static void twoBankToOneBudget(SharedPreferences prefs, SQLiteDatabase db,
            YearMonth month,String itemId,String budgetOperationId,
            String firstKey,String secondKey) throws Exception {
        if(firstKey==null||firstKey.equals(secondKey))
            throw new IllegalArgumentException("Wymagane dwa różne przelewy.");
        db.beginTransaction();
        try {
            Evidence a=read(db,firstKey),b=read(db,secondKey);
            PaycheckMonthlyBudget.Item item=active(prefs,itemId,month);
            if(!"open".equals(a.state)||!"open".equals(b.state)
                    ||!item.kind.equals(a.kind)
                    ||!month.equals(YearMonth.parse(a.date.substring(0,7)))
                    ||!BankBudgetGroupRules.twoBankToOneBudget(
                        a.amount,b.amount,
                        PaycheckMonthlyBudget.remainingDue(db,item,month),
                        a.date,b.date,a.kind,b.kind))
                throw new IllegalArgumentException("Propozycja wygasła albo nie pasuje.");
            pending(db,budgetOperationId,item.kind);
            if(!"MATCHED".equals(BankEvidenceStore.matchBudgetActual(
                    db,a.key,budgetOperationId)))
                throw new IllegalStateException("Nie rozliczono pierwszego dowodu.");
            String childId=UUID.nameUUIDFromBytes(
                ("edhome-budget-bank-part:"+itemId+":"+month+":"+b.key)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            String note="Budżet "+month+" • "+item.name;
            if(note.length()>160)note=note.substring(0,160);
            if(!"COMMITTED".equals(PaycheckStore.add(
                    db,childId,item.kind,item.category,b.amount,note)))
                throw new IllegalStateException("Powtórzony identyfikator części przelewu.");
            if(!"MATCHED".equals(BankEvidenceStore.matchBudgetActual(db,b.key,childId)))
                throw new IllegalStateException("Nie rozliczono drugiego dowodu.");
            PaycheckMonthlyBudget.matchBankParts(prefs,db,item.id,month,
                Arrays.asList(budgetOperationId,childId));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    static void oneBankToTwoBudgets(SharedPreferences prefs,SQLiteDatabase db,
            YearMonth month,String evidenceKey,String firstId,String secondId,
            String firstOperationId,String secondOperationId) throws Exception {
        if(firstId==null||firstId.equals(secondId))
            throw new IllegalArgumentException("Wybierz dwie różne pozycje planu.");
        db.beginTransaction();
        try {
            Evidence bank=read(db,evidenceKey);
            PaycheckMonthlyBudget.Item first=active(prefs,firstId,month);
            PaycheckMonthlyBudget.Item second=active(prefs,secondId,month);
            if(!"open".equals(bank.state)
                    ||!month.equals(YearMonth.parse(bank.date.substring(0,7)))
                    ||!BankBudgetGroupRules.oneBankToTwoBudgets(
                        bank.amount,
                        PaycheckMonthlyBudget.remainingDue(db,first,month),
                        PaycheckMonthlyBudget.remainingDue(db,second,month),
                        bank.kind,first.kind,second.kind))
                throw new IllegalArgumentException("Propozycja wygasła albo nie pasuje.");
            pending(db,firstOperationId,first.kind);
            pending(db,secondOperationId,second.kind);
            if(!"MATCHED".equals(BankEvidenceStore.matchBudgetActual(
                    db,bank.key,firstOperationId)))
                throw new IllegalStateException("Nie rozliczono przelewu bankowego.");
            // Zweryfikuj i usuń oczekujący dublet PRZED zapisem planu
            // w SharedPreferences, aby błąd SQL nie pozostawił planu bez księgi.
            // Drugi plan pokrywa część PIERWSZEJ rzeczywistej transakcji.
            int removed=db.delete("paycheck_transactions",
                "operation_id=? AND scope='shared' AND kind=? AND status='pending' "
                +"AND statement_key IS NULL AND note LIKE 'Budżet %'",
                new String[]{secondOperationId,second.kind});
            if(removed!=1)
                throw new IllegalStateException("Nie wyczyszczono dubletu planu.");
            PaycheckMonthlyBudget.allocateSplit(prefs,db,
                Arrays.asList(first.id,second.id),month,firstOperationId,bank.amount);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
