package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Persistent, local queue of user-imported bank evidence.
 *
 * Imported files are not authenticated bank connections. Rows stay OPEN until
 * the user explicitly matches or dismisses them. Raw files are never stored.
 */
final class BankEvidenceStore {
    static final int MAX_ROWS=5000;

    static final class Incoming {
        final BankStatementCsv.Entry entry;
        final String sourceKind,sourceLabel;
        Incoming(BankStatementCsv.Entry entry,String sourceKind,String sourceLabel) {
            this.entry=entry;this.sourceKind=sourceKind;this.sourceLabel=sourceLabel;
        }
    }

    static final class Row {
        final long id,amount,importedAt;
        final String evidenceKey,sourceKind,sourceLabel,kind,date,description,state;
        final String matchedOperationId;
        final Long matchedAt;
        Row(long id,String evidenceKey,String sourceKind,String sourceLabel,
                String kind,long amount,String date,String description,long importedAt,
                String state,String matchedOperationId,Long matchedAt) {
            this.id=id;this.evidenceKey=evidenceKey;this.sourceKind=sourceKind;
            this.sourceLabel=sourceLabel;this.kind=kind;this.amount=amount;
            this.date=date;this.description=description;this.importedAt=importedAt;
            this.state=state;this.matchedOperationId=matchedOperationId;
            this.matchedAt=matchedAt;
        }
    }

    static final class IngestResult {
        final int inserted,duplicates;
        IngestResult(int inserted,int duplicates) {
            this.inserted=inserted;this.duplicates=duplicates;
        }
    }

    private BankEvidenceStore() {}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bank_evidence_queue ("
            +"id INTEGER PRIMARY KEY AUTOINCREMENT, "
            +"evidence_key TEXT NOT NULL UNIQUE, "
            +"source_kind TEXT NOT NULL CHECK(source_kind IN ('csv','mbank','velo_pdf')), "
            +"source_label TEXT NOT NULL, "
            +"kind TEXT NOT NULL CHECK(kind IN ('income','expense')), "
            +"amount_grosz INTEGER NOT NULL CHECK(amount_grosz BETWEEN 1 AND 99999999999), "
            +"booking_date TEXT NOT NULL, "
            +"description TEXT NOT NULL DEFAULT '', "
            +"imported_at INTEGER NOT NULL, "
            +"state TEXT NOT NULL DEFAULT 'open' "
            +"CHECK(state IN ('open','matched','dismissed')), "
            +"matched_operation_id TEXT, matched_at INTEGER, "
            +"CHECK((state='matched' AND matched_operation_id IS NOT NULL "
            +"AND matched_at IS NOT NULL) OR "
            +"(state!='matched' AND matched_operation_id IS NULL AND matched_at IS NULL)))");
        db.execSQL("CREATE INDEX bank_evidence_state_idx "
            +"ON bank_evidence_queue(state,booking_date,id)");
    }

    static IngestResult ingest(SQLiteDatabase db,List<Incoming> incoming) {
        if(incoming==null||incoming.isEmpty())return new IngestResult(0,0);
        if(incoming.size()>BankStatementCsv.MAX_ROWS)
            throw new IllegalArgumentException("Za dużo pozycji w jednej partii.");
        db.beginTransaction();
        try {
            long total;
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM bank_evidence_queue",null)) {
                total=c.moveToFirst()?c.getLong(0):0;
            }
            int inserted=0,duplicates=0;
            for(Incoming candidate:incoming) {
                validate(candidate);
                if(existsEvidence(db,candidate.entry.evidenceKey)) {
                    duplicates++;continue;
                }
                if(total+inserted>=MAX_ROWS)
                    throw new IllegalArgumentException(
                        "Kolejka bankowa osiągnęła limit 5000 pozycji. "
                        +"Zamknij stare wpisy przed kolejnym importem.");
                ContentValues v=new ContentValues();
                v.put("evidence_key",candidate.entry.evidenceKey);
                v.put("source_kind",candidate.sourceKind);
                v.put("source_label",candidate.sourceLabel.trim());
                v.put("kind",candidate.entry.kind);
                v.put("amount_grosz",candidate.entry.amountGrosz);
                v.put("booking_date",candidate.entry.date);
                v.put("description",candidate.entry.description.trim());
                v.put("imported_at",System.currentTimeMillis());
                v.put("state","open");
                db.insertOrThrow("bank_evidence_queue",null,v);
                inserted++;
            }
            db.setTransactionSuccessful();
            return new IngestResult(inserted,duplicates);
        } finally {db.endTransaction();}
    }

    private static boolean existsEvidence(SQLiteDatabase db,String key) {
        try(Cursor q=db.rawQuery(
                "SELECT 1 FROM bank_evidence_queue WHERE evidence_key=? "
                +"UNION SELECT 1 FROM paycheck_transactions WHERE statement_key=? LIMIT 1",
                new String[]{key,key})) {
            return q.moveToFirst();
        }
    }

    private static void validate(Incoming c) {
        if(c==null||c.entry==null
                ||c.entry.evidenceKey==null
                ||!c.entry.evidenceKey.matches("[0-9a-f]{64}")
                ||!("csv".equals(c.sourceKind)||"mbank".equals(c.sourceKind)
                    ||"velo_pdf".equals(c.sourceKind))
                ||c.sourceLabel==null||c.sourceLabel.trim().isEmpty()
                ||c.sourceLabel.trim().length()>80
                ||!("income".equals(c.entry.kind)||"expense".equals(c.entry.kind))
                ||c.entry.amountGrosz<1||c.entry.amountGrosz>MoneyRules.MAX_GROSZ
                ||c.entry.description==null||c.entry.description.length()>300)
            throw new IllegalArgumentException("Nieprawidłowy wpis kolejki bankowej.");
        try {
            LocalDate d=LocalDate.parse(c.entry.date);
            if(d.getYear()<1970||d.getYear()>2100)throw new IllegalArgumentException();
        }catch(Exception invalid) {
            throw new IllegalArgumentException("Nieprawidłowa data kolejki bankowej.");
        }
    }

    static List<Row> list(SQLiteDatabase db,String state) {
        if(!("open".equals(state)||"matched".equals(state)||"dismissed".equals(state)))
            throw new IllegalArgumentException("Nieprawidłowy filtr kolejki.");
        List<Row> result=new ArrayList<>();
        try(Cursor c=db.rawQuery(
                "SELECT id,evidence_key,source_kind,source_label,kind,amount_grosz,"
                +"booking_date,description,imported_at,state,matched_operation_id,matched_at "
                +"FROM bank_evidence_queue WHERE state=? "
                +"ORDER BY booking_date DESC,id DESC LIMIT 500",
                new String[]{state})) {
            while(c.moveToNext())
                result.add(new Row(c.getLong(0),c.getString(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getLong(5),c.getString(6),
                    c.getString(7),c.getLong(8),c.getString(9),
                    c.isNull(10)?null:c.getString(10),
                    c.isNull(11)?null:c.getLong(11)));
        }
        return result;
    }

    static int pendingMatches(SQLiteDatabase db,Row row) {
        try(Cursor c=db.rawQuery(
                "SELECT COUNT(*) FROM paycheck_transactions "
                +"WHERE scope='shared' AND status='pending' "
                +"AND kind=? AND amount_grosz=?",
                new String[]{row.kind,Long.toString(row.amount)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    /** Atomically matches one OPEN evidence row with one pending shared entry. */
    static String match(SQLiteDatabase db,String evidenceKey,String operationId) {
        if(evidenceKey==null||!evidenceKey.matches("[0-9a-f]{64}")
                ||operationId==null||!operationId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Nieprawidłowe uzgodnienie.");
        db.beginTransaction();
        try {
            String kind,date;
            long amount;
            try(Cursor c=db.rawQuery(
                    "SELECT kind,amount_grosz,booking_date,state "
                    +"FROM bank_evidence_queue WHERE evidence_key=?",
                    new String[]{evidenceKey})) {
                if(!c.moveToFirst())return "MISSING";
                if(!"open".equals(c.getString(3)))return "CLOSED";
                kind=c.getString(0);amount=c.getLong(1);date=c.getString(2);
            }
            try(Cursor used=db.rawQuery(
                    "SELECT operation_id FROM paycheck_transactions WHERE statement_key=?",
                    new String[]{evidenceKey})) {
                if(used.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return operationId.equals(used.getString(0))
                        ?"ALREADY_MATCHED":"EVIDENCE_USED";
                }
            }
            long now=System.currentTimeMillis();
            ContentValues tx=new ContentValues();
            tx.put("status","confirmed");
            tx.put("confirmation_source","manual");
            tx.put("confirmed_at",now);
            tx.put("statement_key",evidenceKey);
            tx.put("statement_date",date);
            int changed=db.update("paycheck_transactions",tx,
                "operation_id=? AND scope='shared' AND status='pending' "
                +"AND kind=? AND amount_grosz=? AND statement_key IS NULL",
                new String[]{operationId,kind,Long.toString(amount)});
            if(changed!=1)return "NOT_PENDING_OR_MISMATCH";
            ContentValues evidence=new ContentValues();
            evidence.put("state","matched");
            evidence.put("matched_operation_id",operationId);
            evidence.put("matched_at",now);
            if(db.update("bank_evidence_queue",evidence,
                    "evidence_key=? AND state='open'",
                    new String[]{evidenceKey})!=1)
                throw new IllegalStateException("Nie zamknięto kolejki bankowej.");
            db.setTransactionSuccessful();
            return "MATCHED";
        } finally {db.endTransaction();}
    }

    static boolean dismiss(SQLiteDatabase db,String evidenceKey) {
        if(evidenceKey==null||!evidenceKey.matches("[0-9a-f]{64}"))return false;
        ContentValues v=new ContentValues();
        v.put("state","dismissed");
        return db.update("bank_evidence_queue",v,
            "evidence_key=? AND state='open'",new String[]{evidenceKey})==1;
    }

    static boolean reopen(SQLiteDatabase db,String evidenceKey) {
        if(evidenceKey==null||!evidenceKey.matches("[0-9a-f]{64}"))return false;
        ContentValues v=new ContentValues();
        v.put("state","open");
        return db.update("bank_evidence_queue",v,
            "evidence_key=? AND state='dismissed'",new String[]{evidenceKey})==1;
    }
}
