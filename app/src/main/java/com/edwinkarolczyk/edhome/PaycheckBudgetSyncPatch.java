package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Budżet Desktop -> Android: paczka wyłącznie zmienionych UUID.
 * Nie modyfikuje tabel ani preferencji przed sprawdzeniem konfliktów.
 * Odtwarzanie zatwierdzonego snapshotu korzysta z transakcyjnego
 * DataBackup.restoreJson, a historia pozostaje append-only.
 */
final class PaycheckBudgetSyncPatch {
    private static final String[] FIELDS = {
        "paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"
    };
    private static final String[] TABLES = {
        "budget_items","budget_occurrences","budget_recipients",
        "budget_credits","budget_history"
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
                    if(archived!=null&&!same(archived,desired))
                        throw new IllegalArgumentException(
                            "Konflikt zdarzenia w archiwum Budżetu.");
                    if(current!=null&&!same(current,desired))
                        throw new IllegalArgumentException(
                            "Konflikt historii Budżetu.");
                    if(current==null)rows.put(id,desired);
                } else {
                    if(!same(current,previous))
                        throw new IllegalArgumentException(
                            "Konflikt Budżetu: pozycja zmieniona na drugim urządzeniu.");
                    if(desired==null)rows.remove(id);
                    else rows.put(id,desired);
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

        // Odtwórz pięć tabel pochodnych w odizolowanej bazie SQLite.
        // Zapewnia ten sam układ payload co Androidowy reconcileRaw().
        SQLiteDatabase temp=SQLiteDatabase.create(null);
        try {
            PaycheckBudgetSqliteStore.create(temp);
            JSONArray history=original.getJSONObject("tables")
                .getJSONArray("budget_history");
            for(int i=0;i<history.length();i++) {
                JSONObject row=history.getJSONObject(i);
                android.content.ContentValues values=new android.content.ContentValues();
                values.put("id",row.getLong("id"));
                values.put("entity_key",row.getString("entity_key"));
                values.put("payload",row.getString("payload"));
                temp.insertOrThrow("budget_history",null,values);
            }
            PaycheckBudgetSqliteStore.reconcileRaw(temp,
                settings.getString("paycheckMonthlyBudget"),
                settings.getString("paycheckRecipients"),
                settings.getString("paycheckBudgetHistory"));
            JSONObject tables=merged.getJSONObject("tables");
            for(String table:TABLES) {
                JSONArray result=new JSONArray();
                try(Cursor cursor=temp.query(table,
                        new String[]{"id","entity_key","payload"},
                        null,null,null,null,"id")) {
                    while(cursor.moveToNext()) {
                        JSONObject row=new JSONObject();
                        row.put("id",cursor.getLong(0));
                        row.put("entity_key",cursor.getString(1));
                        row.put("payload",cursor.getString(2));
                        result.put(row);
                    }
                }
                tables.put(table,result);
            }
        } finally {temp.close();}

        // Metadane skasowanych rekordów to tombstones; zmienione rekordy
        // zachowują UUID, SyncRecordStore.ensureAll nada im nową rewizję.
        JSONObject tables=merged.getJSONObject("tables");
        JSONArray metadata=merged.getJSONArray("syncRecords");
        Map<String,Set<String>> retained=new HashMap<>();
        for(String table:TABLES) {
            Set<String> keys=new HashSet<>();
            JSONArray rows=tables.getJSONArray(table);
            for(int i=0;i<rows.length();i++)
                keys.add(Long.toString(rows.getJSONObject(i).getLong("id")));
            retained.put(table,keys);
        }
        long now=System.currentTimeMillis();
        for(int i=0;i<metadata.length();i++) {
            JSONObject m=metadata.getJSONObject(i);
            String table=m.optString("table","");
            Set<String> keys=retained.get(table);
            if(keys==null||!m.isNull("deletedAt")
                    ||keys.contains(m.optString("rowKey","")))continue;
            m.put("deletedAt",now);
            m.put("updatedAt",now);
            m.put("revision",m.getLong("revision")+1L);
            m.put("rowHash","DELETED");
        }
        DataBackup.restoreJson(db,prefs,merged.toString());
        JSONObject result=new JSONObject();
        result.put("ok",true);
        result.put("operations",changes);
        result.put("results",new JSONArray());
        return result.toString();
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
        return JSONObject.valueToString(value);
    }
}
