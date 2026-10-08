package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Budżet Desktop -> Android: paczka wyłącznie zmienionych UUID.
 * Nie modyfikuje tabel ani preferencji przed sprawdzeniem konfliktów.
 * Aktualizuje wyłącznie trzy preferencje i pięć tabel Budżetu w transakcji.
 * Nie odtwarza danych pozostałych modułów.
 */
final class PaycheckBudgetSyncPatch {
    private static final String[] FIELDS = {
        "paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"
    };
    private PaycheckBudgetSyncPatch() { }

    static String apply(SQLiteDatabase db,SharedPreferences prefs,String incoming)
            throws Exception {
        JSONObject patch=new JSONObject(incoming);
        if(!"edhome-record-patch".equals(patch.optString("format"))
                ||patch.optInt("version",-1)!=2
                ||patch.optJSONObject("budgetDelta")==null
                ||patch.optJSONArray("operations")==null
                ||patch.getJSONArray("operations").length()!=0)
            throw new IllegalArgumentException(
                "Pakiet Budżetu zawiera nieobsługiwane operacje.");
        JSONObject delta=patch.getJSONObject("budgetDelta");
        if(delta.length()==0)throw new IllegalArgumentException(
            "Pusty pakiet zmian Budżetu.");
        for(java.util.Iterator<String> it=delta.keys();it.hasNext();) {
            String field=it.next();
            if(!allowed(field))throw new IllegalArgumentException(
                "Niedozwolone pole zmian Budżetu.");
        }

        JSONObject original=new JSONObject(DataBackup.exportJson(db,prefs));
        JSONObject merged=new JSONObject(original.toString());
        JSONObject settings=merged.getJSONObject("settings");
        int changes=0;
        for(String field:FIELDS) {
            if(delta.has(field)&&!(delta.opt(field) instanceof JSONArray))
                throw new IllegalArgumentException(
                    "Nieprawidłowa tablica zmian Budżetu: "+field);
            JSONArray edits=delta.optJSONArray(field);
            if(edits==null)continue;
            JSONArray existing=new JSONArray(settings.getString(field));
            java.util.LinkedHashMap<String,JSONObject> rows=new java.util.LinkedHashMap<>();
            for(int i=0;i<existing.length();i++) {
                JSONObject row=existing.getJSONObject(i);
                String id=row.getString("id");
                if(!validId(id)||rows.put(id,row)!=null)
                    throw new IllegalArgumentException("Duplikat ID Budżetu.");
            }
            for(int i=0;i<edits.length();i++) {
                JSONObject edit=edits.getJSONObject(i);
                String id=edit.getString("id");
                if(!validId(id)||!edit.has("before")||!edit.has("after"))
                    throw new IllegalArgumentException(
                        "Nieprawidłowy identyfikator zmiany Budżetu.");
                JSONObject previous=edit.isNull("before")?null:
                    edit.getJSONObject("before");
                JSONObject desired=edit.isNull("after")?null:
                    edit.getJSONObject("after");
                if(previous!=null&&!id.equals(previous.optString("id"))
                        ||desired!=null&&!id.equals(desired.optString("id")))
                    throw new IllegalArgumentException(
                        "Niespójny identyfikator zmiany.");
                JSONObject current=rows.get(id);
                if("paycheckBudgetHistory".equals(field)) {
                    if(previous!=null||desired==null)
                        throw new IllegalArgumentException(
                            "Historii Budżetu nie można edytować ani usuwać.");
                    JSONObject archived=findArchived(merged,id);
                    if(archived!=null&&!same(archived,desired)
                            ||current!=null&&!same(current,desired))
                        throw new SyncRecordStore.SyncConflict(
                            "budget_history",id,"",0L,-1L);
                    if(current==null)rows.put(id,desired);
                } else {
                    // Ponowienie po utracie odpowiedzi jest bezpieczne.
                    if(!same(current,desired)) {
                        if(!same(current,previous))
                            throw new SyncRecordStore.SyncConflict(
                                field,id,"",0L,-1L);
                        if(desired==null)rows.remove(id);
                        else rows.put(id,desired);
                    }
                }
                if(++changes>500)throw new IllegalArgumentException(
                    "Zbyt wiele zmian w paczce Budżetu.");
            }
            JSONArray out=new JSONArray();
            for(JSONObject value:rows.values())out.put(value);
            if("paycheckBudgetHistory".equals(field)) {
                while(out.length()>PaycheckBudgetHistoryStore.MAX_EVENTS
                        ||out.toString().length()>1_900_000)
                    out=withoutFirst(out);
            }
            settings.put(field,out.toString());
        }
        if(changes==0)throw new IllegalArgumentException(
            "Pusty pakiet zmian Budżetu.");
        PaycheckMonthlyBudget.validateSerialized(
            settings.getString("paycheckMonthlyBudget"));
        PaycheckRecipientStore.validateSerialized(
            settings.getString("paycheckRecipients"));
        PaycheckBudgetHistoryStore.validateSerialized(
            settings.getString("paycheckBudgetHistory"));

        // P0: patch finansowy nie może odtwarzać wszystkich modułów EDHOME.
        // Aktualizujemy wyłącznie Budżet, archiwum historii pozostaje intact.
        // Pobierz wartości z tego samego snapshotu, którego zawartość
        // służyła do porównania UUID, nie z późniejszego odczytu preferencji.
        String oldItems=original.getJSONObject("settings")
            .getString("paycheckMonthlyBudget");
        String oldRecipients=original.getJSONObject("settings")
            .getString("paycheckRecipients");
        String oldHistory=original.getJSONObject("settings")
            .getString("paycheckBudgetHistory");
        String updatedItems=settings.getString("paycheckMonthlyBudget");
        String updatedRecipients=settings.getString("paycheckRecipients");
        String updatedHistory=settings.getString("paycheckBudgetHistory");
        db.beginTransaction();
        try {
            if(!oldItems.equals(prefs.getString(PaycheckMonthlyBudget.PREF_KEY,"[]"))
                    ||!oldRecipients.equals(
                        prefs.getString(PaycheckRecipientStore.PREF_KEY,"[]"))
                    ||!oldHistory.equals(
                        prefs.getString(PaycheckBudgetHistoryStore.PREF_KEY,"[]")))
                throw new SyncRecordStore.SyncConflict(
                    "budget_items","local-edits","",0L,-1L);
            // Kontrola wpłat musi być po stronie odbiorcy Androida.
            // Inaczej dwie zmiany z PC mogą podwójnie rozliczyć przelew.
            verifySharedAllocations(db,updatedItems);
            try {
                PaycheckMonthlyBudget.assertCreditConservation(db,updatedItems);
            } catch(IllegalArgumentException|ArithmeticException invalid) {
                throw new SyncRecordStore.SyncConflict(
                    "budget_credits","", "",0L,-1L);
            }
            PaycheckBudgetSqliteStore.reconcileRaw(db,
                updatedItems,updatedRecipients,updatedHistory);
            SyncRecordStore.ensureAll(db);
            SharedPreferences.Editor editor=prefs.edit()
                .putString(PaycheckMonthlyBudget.PREF_KEY,updatedItems)
                .putString(PaycheckRecipientStore.PREF_KEY,updatedRecipients)
                .putString(PaycheckBudgetHistoryStore.PREF_KEY,updatedHistory);
            if(!editor.commit())
                throw new IllegalStateException(
                    "Nie zapisano ustawień Budżetu; SQL został wycofany.");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        JSONObject result=new JSONObject();
        result.put("ok",true);
        result.put("operations",changes);
        result.put("results",new JSONArray());
        return result.toString();
    }

    /**
     * Ten sam potwierdzony przelew można rozdzielić na wiele rachunków,
     * ale suma alokacji i wydzielonych nadpłat nie może przekroczyć wpłaty.
     * Weryfikujemy stan faktyczny w SQLite, a nie deklarację z PC.
     */
    private static void verifySharedAllocations(SQLiteDatabase db,String itemsJson)
            throws Exception {
        Map<String,Long> confirmed=new HashMap<>();
        Map<String,String> kinds=new HashMap<>();
        try(Cursor cursor=db.rawQuery(
                "SELECT operation_id,amount_grosz,kind "
                    +"FROM paycheck_transactions "
                    +"WHERE scope='shared' AND status='confirmed'",null)) {
            while(cursor.moveToNext()) {
                String id=cursor.getString(0);
                long amount=cursor.getLong(1);
                if(id==null||id.isBlank()||amount<=0
                        ||confirmed.put(id,amount)!=null)
                    throw new SyncRecordStore.SyncConflict(
                        "paycheck_transactions",id==null?"":id,"",0L,-1L);
                kinds.put(id,cursor.getString(2));
            }
        }
        Map<String,Long> used=new HashMap<>();
        JSONArray items=new JSONArray(itemsJson);
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.getJSONObject(i);
            JSONArray matches=item.optJSONArray("matches");
            if(matches==null)continue;
            JSONObject allocations=item.optJSONObject("allocations");
            JSONObject surplus=item.optJSONObject("splitSurplusesGrosz");
            for(int j=0;j<matches.length();j++) {
                String id=matches.getString(j);
                Long available=confirmed.get(id);
                if(available==null)continue;
                try {
                    if(!item.optString("kind","").equals(kinds.get(id)))
                        throw new ArithmeticException("Niezgodny typ transakcji.");
                    long allocated=allocations!=null&&allocations.has(id)
                        ?allocations.getLong(id):available;
                    long extra=surplus!=null&&surplus.has(id)
                        ?surplus.getLong(id):0L;
                    if(allocated<=0||extra<0)throw new ArithmeticException();
                    long total=Math.addExact(
                        used.getOrDefault(id,0L),
                        Math.addExact(allocated,extra));
                    if(total>available)throw new ArithmeticException();
                    used.put(id,total);
                } catch(ArithmeticException invalid) {
                    throw new SyncRecordStore.SyncConflict(
                        "paycheck_transactions",id,"",0L,-1L);
                }
            }
        }
    }

    private static JSONObject findArchived(JSONObject snapshot,String id)
            throws Exception {
        JSONArray rows=snapshot.getJSONObject("tables")
            .getJSONArray("budget_history");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            if(id.equals(row.getString("entity_key")))
                return new JSONObject(row.getString("payload"));
        }
        return null;
    }

    /** Cache może zostać skrócony, ale pełna historia zostaje w SQLite. */
    private static JSONArray withoutFirst(JSONArray original) throws Exception {
        JSONArray result=new JSONArray();
        for(int i=1;i<original.length();i++)result.put(original.get(i));
        return result;
    }

    private static boolean allowed(String key) {
        for(String field:FIELDS)if(field.equals(key))return true;
        return false;
    }

    private static boolean validId(String id) {
        return id!=null&&id.matches("[0-9a-fA-F-]{36}");
    }

    private static boolean same(JSONObject first,JSONObject second)
            throws Exception {
        if(first==null||second==null)return first==second;
        return canonical(first).equals(canonical(second));
    }

    private static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;
            java.util.List<String> keys=new java.util.ArrayList<>();
            for(java.util.Iterator<String> it=object.keys();it.hasNext();)
                keys.add(it.next());
            java.util.Collections.sort(keys);
            StringBuilder result=new StringBuilder("{");
            for(String key:keys) {
                if(result.length()>1)result.append(',');
                result.append(JSONObject.quote(key)).append(':')
                    .append(canonical(object.get(key)));
            }
            return result.append('}').toString();
        }
        if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value;
            StringBuilder result=new StringBuilder("[");
            for(int i=0;i<array.length();i++) {
                if(i>0)result.append(',');
                result.append(canonical(array.get(i)));
            }
            return result.append(']').toString();
        }
        if(value instanceof String)return JSONObject.quote((String)value);
        if(value instanceof Number||value instanceof Boolean)
            return String.valueOf(value);
        return JSONObject.quote(String.valueOf(value));
    }
}
