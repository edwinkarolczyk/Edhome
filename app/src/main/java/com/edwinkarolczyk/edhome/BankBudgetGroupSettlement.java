package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Grupowe rozliczenie WYŁĄCZNIE po świadomym wyborze użytkownika.
 * Każdy wiersz bankowy to maksymalnie jedna potwierdzona transakcja PayCheck.
 * SQLite potwierdza przelewy i dziennik naprawy PRZED zapisem planu.
 * Po awarii plan jest rekonstruowany z dziennika bez ponownego księgowania.
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


    private static final String JOURNAL="bank_group_settlement_recovery";

    private static void ensureJournal(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS "+JOURNAL+" ("
            +"evidence_key TEXT PRIMARY KEY NOT NULL,mode TEXT NOT NULL,"
            +"month TEXT NOT NULL,first_item TEXT NOT NULL,second_item TEXT NOT NULL,"
            +"first_operation TEXT NOT NULL,second_operation TEXT NOT NULL,"
            +"second_evidence TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }

    private static void requireNoUnfinished(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM "+JOURNAL+" LIMIT 1",null)) {
            if(c.moveToFirst())throw new IllegalStateException(
                "Najpierw napraw przerwane rozliczenie w PayCheck.");
        }
    }

    private static void journal(SQLiteDatabase db,String evidenceKey,String mode,
            YearMonth month,String firstItem,String secondItem,
            String firstOperation,String secondOperation,String secondEvidence) {
        ContentValues v=new ContentValues();
        v.put("evidence_key",evidenceKey);
        v.put("mode",mode);
        v.put("month",month.toString());
        v.put("first_item",firstItem);
        v.put("second_item",secondItem);
        v.put("first_operation",firstOperation);
        v.put("second_operation",secondOperation);
        v.put("second_evidence",secondEvidence);
        v.put("created_at",System.currentTimeMillis());
        if(db.insertOrThrow(JOURNAL,null,v)<0)
            throw new IllegalStateException("Nie zapisano dziennika rozliczenia.");
    }

    private static void verifiedPayment(SQLiteDatabase db,String operationId,
            String evidenceKey,YearMonth month) {
        try(Cursor c=db.rawQuery(
                "SELECT t.status,t.statement_key,t.statement_date,e.state,"
                +"e.matched_operation_id FROM paycheck_transactions t "
                +"JOIN bank_evidence_queue e ON e.evidence_key=t.statement_key "
                +"WHERE t.operation_id=? AND t.scope='shared'",
                new String[]{operationId})) {
            if(!c.moveToFirst() || !"confirmed".equals(c.getString(0))
                    || !evidenceKey.equals(c.getString(1))
                    || c.isNull(2) || !c.getString(2).startsWith(month.toString())
                    || !"matched".equals(c.getString(3))
                    || !operationId.equals(c.getString(4)))
                throw new IllegalStateException(
                    "Dziennik rozliczenia nie zgadza się z dowodem bankowym.");
        }
    }

    /**
     * Naprawa przerywanego 2:1 lub 1:2. Nie tworzy transakcji PayCheck,
     * nie odwołuje matchBudgetActual i nie zmienia salda po raz drugi.
     */
    static int repairInterrupted(SharedPreferences prefs,SQLiteDatabase db)
            throws Exception {
        ensureJournal(db);
        List<String[]> entries=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT evidence_key,mode,month,first_item,second_item,"
                +"first_operation,second_operation,second_evidence "
                +"FROM "+JOURNAL+" ORDER BY created_at ASC",null)) {
            while(c.moveToNext()) {
                String[] entry=new String[8];
                for(int i=0;i<entry.length;i++)entry[i]=c.getString(i);
                entries.add(entry);
            }
        }
        int repaired=0;
        for(String[] entry:entries) {
            String key=entry[0],mode=entry[1];
            YearMonth month=YearMonth.parse(entry[2]);
            String firstId=entry[3],secondId=entry[4];
            String firstOp=entry[5],secondOp=entry[6];
            verifiedPayment(db,firstOp,key,month);
            if("two_to_one".equals(mode))
                verifiedPayment(db,secondOp,entry[7],month);
            else if(!"one_to_two".equals(mode))
                throw new IllegalStateException("Nieznany typ dziennika bankowego.");
            boolean firstLinked=false,secondLinked=false;
            for(PaycheckMonthlyBudget.Item plan:PaycheckMonthlyBudget.load(prefs)) {
                if(firstId.equals(plan.id)) {
                    firstLinked=plan.matchedOperationIds.contains(firstOp);
                    if("two_to_one".equals(mode))
                        secondLinked=plan.matchedOperationIds.contains(secondOp);
                }
                if("one_to_two".equals(mode)&&secondId.equals(plan.id))
                    secondLinked=plan.matchedOperationIds.contains(firstOp);
            }
            if(firstLinked!=secondLinked)
                throw new IllegalStateException(
                    "Niepełne przypisanie planu. Nie księguj ponownie.");
            if(!firstLinked) {
                if("two_to_one".equals(mode))
                    PaycheckMonthlyBudget.matchBankParts(prefs,db,firstId,month,
                        Arrays.asList(firstOp,secondOp));
                else {
                    long amount;
                    try(Cursor c=db.rawQuery(
                            "SELECT amount_grosz FROM paycheck_transactions "
                            +"WHERE operation_id=? AND status='confirmed'",
                            new String[]{firstOp})) {
                        if(!c.moveToFirst())
                            throw new IllegalStateException("Brak potwierdzonej kwoty.");
                        amount=c.getLong(0);
                    }
                    PaycheckMonthlyBudget.allocateSplit(prefs,db,
                        Arrays.asList(firstId,secondId),month,firstOp,amount);
                }
                repaired++;
            }
            if(db.delete(JOURNAL,"evidence_key=?",new String[]{key})!=1)
                throw new IllegalStateException("Nie zamknięto dziennika naprawy.");
        }
        return repaired;
    }

    static void twoBankToOneBudget(SharedPreferences prefs, SQLiteDatabase db,
            YearMonth month,String itemId,String budgetOperationId,
            String firstKey,String secondKey) throws Exception {
        if(firstKey==null||firstKey.equals(secondKey))
            throw new IllegalArgumentException("Wymagane dwa różne przelewy.");
        ensureJournal(db);
        requireNoUnfinished(db);
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
            journal(db,a.key,"two_to_one",month,item.id,item.id,
                budgetOperationId,childId,b.key);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        repairInterrupted(prefs,db);
    }

    static void oneBankToTwoBudgets(SharedPreferences prefs,SQLiteDatabase db,
            YearMonth month,String evidenceKey,String firstId,String secondId,
            String firstOperationId,String secondOperationId) throws Exception {
        if(firstId==null||firstId.equals(secondId))
            throw new IllegalArgumentException("Wybierz dwie różne pozycje planu.");
        ensureJournal(db);
        requireNoUnfinished(db);
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
            journal(db,bank.key,"one_to_two",month,first.id,second.id,
                firstOperationId,secondOperationId,"");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        repairInterrupted(prefs,db);
    }
}
