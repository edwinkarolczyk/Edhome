package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Etap 5A: bezstratny pomost starych ustawień do pięciu tabel SQLite.
 *
 * Podczas migracji dotychczasowe JSON-y pozostają źródłem zapisu; tabel nie
 * zastępujemy nimi w ciemno. Synchronizacja i backup obejmują wiersze, a
 * historia w budget_history jest append-only (bez limitu 6000).
 *
 * entity_key zachowuje oryginalny UUID i miesiąc, id jest deterministycznym
 * dodatnim INTEGER dla SyncRecordStore (również na innym urządzeniu).
 */
final class PaycheckBudgetSqliteStore {
    static final String[] TABLE_NAMES = {
        "budget_items","budget_occurrences","budget_recipients",
        "budget_credits","budget_history"
    };
    private static volatile Context appContext;

    private PaycheckBudgetSqliteStore() { }

    static void configure(Context context) {
        appContext=context.getApplicationContext();
    }

    static void create(SQLiteDatabase db) {
        for (String table:TABLE_NAMES) {
            db.execSQL("CREATE TABLE IF NOT EXISTS "+table+" ("
                +"id INTEGER PRIMARY KEY, "
                +"entity_key TEXT NOT NULL UNIQUE, "
                +"payload TEXT NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "+table+"_entity_idx "
                +"ON "+table+"(entity_key)");
        }
    }

    static long stableId(String table, String key) throws Exception {
        if (!known(table) || key==null || key.isBlank())
            throw new IllegalArgumentException("Nieprawidłowa tożsamość budżetu.");
        return PaycheckBudgetStableIds.of(table,key);
    }

    private static boolean known(String table) {
        for(String candidate:TABLE_NAMES) if(candidate.equals(table)) return true;
        return false;
    }

    private static void upsert(SQLiteDatabase db,String table,String key,
            JSONObject payload) throws Exception {
        long id=stableId(table,key);
        try(Cursor collision=db.query(table,new String[]{"entity_key"},
                "id=?",new String[]{Long.toString(id)},null,null,null)) {
            if(collision.moveToFirst() && !key.equals(collision.getString(0)))
                throw new IllegalStateException(
                    "Kolizja identyfikatorów budżetu: nie zapisano zmian.");
        }
        ContentValues values=new ContentValues();
        values.put("id",id);
        values.put("entity_key",key);
        values.put("payload",payload.toString());
        if(db.insertWithOnConflict(table,null,values,
                SQLiteDatabase.CONFLICT_REPLACE)==-1)
            throw new IllegalStateException("Nie zapisano rekordu budżetu.");
    }

    /** Historia zachowuje wszystkie ID, nawet gdy stary cache prefs się zapełnia. */
    static void appendHistory(SQLiteDatabase db,JSONArray events)
            throws Exception {
        create(db);
        for(int i=0;i<events.length();i++) {
            JSONObject json=events.getJSONObject(i);
            String key=json.getString("id");
            long id=stableId("budget_history",key);
            try(Cursor current=db.query("budget_history",
                    new String[]{"entity_key","payload"},"id=?",
                    new String[]{Long.toString(id)},null,null,null)) {
                if(current.moveToFirst()) {
                    if(!key.equals(current.getString(0))
                            || !json.toString().equals(
                                new JSONObject(current.getString(1)).toString()))
                        throw new IllegalStateException(
                            "Historia zawiera sprzeczne wersje zdarzenia.");
                    continue;
                }
            }
            upsert(db,"budget_history",key,json);
        }
    }

    /** Zapisy z aplikacji są archiwizowane zanim ulegnie skróceniu cache. */
    static void archiveBeforeTrim(List<PaycheckBudgetHistoryStore.Event> events)
            throws Exception {
        if(appContext==null)
            throw new IllegalStateException(
                "Archiwum historii nie jest gotowe; nie skrócono dziennika.");
        JSONArray payload=new JSONArray();
        for(PaycheckBudgetHistoryStore.Event event:events)
            payload.put(PaycheckBudgetHistoryStore.eventJson(event));
        MainActivity.LocalDb helper=new MainActivity.LocalDb(appContext);
        try {
            SQLiteDatabase database=helper.getWritableDatabase();
            database.beginTransaction();
            try {
                appendHistory(database,payload);
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
        } finally { helper.close(); }
    }

    /** Synchronizuje istniejące źródła bez usuwania żadnej pozycji historii. */
    static void reconcile(SQLiteDatabase db,SharedPreferences prefs)
            throws Exception {
        reconcileRaw(db,prefs.getString(PaycheckMonthlyBudget.PREF_KEY,"[]"),
            prefs.getString(PaycheckRecipientStore.PREF_KEY,"[]"),
            prefs.getString(PaycheckBudgetHistoryStore.PREF_KEY,"[]"));
    }

    static void reconcileRaw(SQLiteDatabase db,String itemsJson,
            String recipientsJson,String historyJson) throws Exception {
        create(db);
        JSONArray items=new JSONArray(itemsJson);
        JSONArray recipients=new JSONArray(recipientsJson);
        JSONArray history=new JSONArray(historyJson);

        Set<String> itemKeys=new HashSet<>();
        Set<String> occurrenceKeys=new HashSet<>();
        Set<String> creditKeys=new HashSet<>();
        Set<String> recipientKeys=new HashSet<>();
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.getJSONObject(i);
            String id=item.getString("id");
            itemKeys.add(id);
            upsert(db,"budget_items",id,item);
            // Osobne rekordy dla faktur, korekt kwoty i planowanych płatności.
            Map<String,JSONObject> months=new HashMap<>();
            for(String field:new String[]{"invoiceDueDates","plannedPaymentDates",
                    "monthAmountOverrides","balanceAdjustmentsGrosz",
                    "closedMonthReasons","closedMonthCreatedAt"}) {
                JSONObject values=item.optJSONObject(field);
                if(values==null)continue;
                java.util.Iterator<String> keys=values.keys();
                while(keys.hasNext()) {
                    String month=keys.next();
                    YearMonth.parse(month);
                    JSONObject occurrence=months.get(month);
                    if(occurrence==null) {
                        occurrence=new JSONObject();
                        months.put(month,occurrence);
                    }
                    occurrence.put(field,values.get(month));
                }
            }
            for(Map.Entry<String,JSONObject> entry:months.entrySet()) {
                String key=id+":"+entry.getKey();
                occurrenceKeys.add(key);
                upsert(db,"budget_occurrences",key,entry.getValue());
            }
            JSONObject credits=item.optJSONObject("creditApplicationsGrosz");
            if(credits!=null) {
                java.util.Iterator<String> keys=credits.keys();
                while(keys.hasNext()) {
                    String credit=keys.next();
                    String key=id+":"+credit;
                    creditKeys.add(key);
                    JSONObject payload=new JSONObject();
                    payload.put("itemId",id);
                    payload.put("creditKey",credit);
                    payload.put("amountGrosz",credits.get(credit));
                    upsert(db,"budget_credits",key,payload);
                }
            }
        }
        for(int i=0;i<recipients.length();i++) {
            JSONObject recipient=recipients.getJSONObject(i);
            String key=recipient.getString("id");
            recipientKeys.add(key);
            upsert(db,"budget_recipients",key,recipient);
        }
        // Definicje w starym JSON pozostają autorytatywne podczas fazy A.
        pruneMissing(db,"budget_items",itemKeys);
        pruneMissing(db,"budget_occurrences",occurrenceKeys);
        pruneMissing(db,"budget_credits",creditKeys);
        pruneMissing(db,"budget_recipients",recipientKeys);
        appendHistory(db,history);
    }

    private static void pruneMissing(SQLiteDatabase db,String table,
            Set<String> keys) {
        List<String> removed=new ArrayList<>();
        try(Cursor c=db.query(table,new String[]{"entity_key"},
                null,null,null,null,null)) {
            while(c.moveToNext())
                if(!keys.contains(c.getString(0)))removed.add(c.getString(0));
        }
        for(String key:removed)
            db.delete(table,"entity_key=?",new String[]{key});
    }

    static JSONArray archivedHistory(SQLiteDatabase db,String itemId)
            throws Exception {
        create(db);
        JSONArray result=new JSONArray();
        try(Cursor c=db.query("budget_history",new String[]{"payload"},
                null,null,null,null,"id")) {
            while(c.moveToNext()) {
                JSONObject row=new JSONObject(c.getString(0));
                if(itemId==null || itemId.equals(row.optString("itemId","")))
                    result.put(row);
            }
        }
        return result;
    }
}
