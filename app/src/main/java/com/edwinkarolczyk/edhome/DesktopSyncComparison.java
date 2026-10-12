package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Porównanie migawki Telefon/PC przed rozstrzygnięciem konfliktu.
 * Działa wyłącznie w pamięci; nie modyfikuje żadnego urządzenia.
 */
final class DesktopSyncComparison {
    static final class Report {
        final String phoneFingerprint;
        final String desktopFingerprint;
        final String text;
        final boolean identical;
        Report(String phoneFingerprint,String desktopFingerprint,
                String text,boolean identical) {
            this.phoneFingerprint=phoneFingerprint;
            this.desktopFingerprint=desktopFingerprint;
            this.text=text;
            this.identical=identical;
        }
    }

    private DesktopSyncComparison() { }

    static Report compare(String phone,String desktop) throws Exception {
        JSONObject local=new JSONObject(phone);
        JSONObject remote=new JSONObject(desktop);
        String phoneHash=fingerprint(phone);
        String desktopHash=fingerprint(desktop);
        JSONObject localTables=local.getJSONObject("tables");
        JSONObject remoteTables=remote.getJSONObject("tables");

        Set<String> tables=new TreeSet<>();
        for(Iterator<String> it=localTables.keys();it.hasNext();)tables.add(it.next());
        for(Iterator<String> it=remoteTables.keys();it.hasNext();)tables.add(it.next());

        int phoneCount=0,desktopCount=0,onlyPhone=0,onlyDesktop=0,changed=0;
        int differingTables=0;
        List<String> details=new ArrayList<>();

        for(String table:tables) {
            JSONArray a=localTables.optJSONArray(table);
            JSONArray b=remoteTables.optJSONArray(table);
            if((localTables.has(table)&&a==null)
                    ||(remoteTables.has(table)&&b==null))
                throw new IllegalArgumentException(
                    "Niezgodny format tabeli "+table+". Nic nie nadpisano.");
            if(a==null)a=new JSONArray();
            if(b==null)b=new JSONArray();
            Map<String,JSONObject> left=rows(table,a);
            Map<String,JSONObject> right=rows(table,b);
            int lp=0,rp=0,both=0;
            Set<String> identities=new TreeSet<>();
            identities.addAll(left.keySet());
            identities.addAll(right.keySet());
            for(String id:identities) {
                JSONObject l=left.get(id),r=right.get(id);
                if(l==null)rp++;
                else if(r==null)lp++;
                else if(!canonical(l).equals(canonical(r)))both++;
            }
            phoneCount+=left.size();
            desktopCount+=right.size();
            onlyPhone+=lp;
            onlyDesktop+=rp;
            changed+=both;
            if(lp+rp+both>0) {
                differingTables++;
                details.add(label(table)+": Telefon "+left.size()
                    +" / PC "+right.size()+" • tylko tel. "+lp
                    +" • tylko PC "+rp+" • różne "+both);
            }
        }

        JSONObject lSettings=local.getJSONObject("settings");
        JSONObject rSettings=remote.getJSONObject("settings");
        String[] budgetFields={
            "paycheckMonthlyBudget","paycheckRecipients","paycheckBudgetHistory"
        };
        JSONObject leftOther=new JSONObject(lSettings.toString());
        JSONObject rightOther=new JSONObject(rSettings.toString());
        for(String field:budgetFields) {
            leftOther.remove(field);
            rightOther.remove(field);
            String l=lSettings.optString(field,"[]");
            String r=rSettings.optString(field,"[]");
            JSONArray la=new JSONArray(l);
            JSONArray ra=new JSONArray(r);
            Map<String,JSONObject> lm=rowsByUuid(la,field);
            Map<String,JSONObject> rm=rowsByUuid(ra,field);
            int lp=0,rp=0,different=0;
            Set<String> ids=new TreeSet<>();
            ids.addAll(lm.keySet());
            ids.addAll(rm.keySet());
            for(String id:ids) {
                JSONObject lrow=lm.get(id),rrow=rm.get(id);
                if(lrow==null)rp++;
                else if(rrow==null)lp++;
                else if(!canonical(lrow).equals(canonical(rrow)))different++;
            }
            if(lp+rp+different>0) {
                details.add(budgetLabel(field)+": Telefon "+lm.size()
                    +" / PC "+rm.size()+" • tylko tel. "+lp
                    +" • tylko PC "+rp+" • różne "+different);
                differingTables++;
            }
        }
        boolean otherSettingsDifferent=
            !canonical(leftOther).equals(canonical(rightOther));
        boolean metadataDifferent=!canonical(local.opt("syncRecords"))
            .equals(canonical(remote.opt("syncRecords")));
        if(otherSettingsDifferent)
            details.add("Inne ustawienia: różnią się między urządzeniami.");
        if(metadataDifferent)
            details.add("Metadane synchronizacji: różne rewizje rekordów.");

        StringBuilder text=new StringBuilder();
        text.append("PORÓWNANIE PRZED SYNCHRONIZACJĄ\n\n")
            .append("Wszystkie tabele: Telefon ").append(phoneCount)
            .append(" rekordów • Desktop ").append(desktopCount)
            .append(" rekordów\n")
            .append("Tylko telefon: ").append(onlyPhone)
            .append(" • Tylko Desktop: ").append(onlyDesktop)
            .append(" • Ten sam ID, różna treść: ").append(changed)
            .append("\nRóżniące się zestawy: ").append(differingTables)
            .append("\n\n");
        if(details.isEmpty())text.append("Nie wykryto różnic w danych.\n");
        else {
            text.append("Różnice według modułów:\n");
            int limit=Math.min(24,details.size());
            for(int i=0;i<limit;i++)
                text.append("• ").append(details.get(i)).append('\n');
            if(details.size()>limit)
                text.append("… oraz ").append(details.size()-limit)
                    .append(" pozostałych pozycji.\n");
        }
        text.append("\nUWAGA: To porównanie, nie automatyczne scalanie. ")
            .append("Wybór Telefon lub Desktop może zastąpić dane tylko ")
            .append("na drugim urządzeniu. Zachowaj niezależne kopie ZIP ")
            .append("telefonu i kopię PC przed zatwierdzeniem.");
        return new Report(phoneHash,desktopHash,text.toString(),
            details.isEmpty() && !otherSettingsDifferent
                && !metadataDifferent);
    }

    static String fingerprint(String snapshot) throws Exception {
        JSONObject root=new JSONObject(snapshot);
        // createdAt/sourceVersion zmieniają się między eksportami.
        // Podpis obejmuje wyłącznie trwałe, synchronizowane sekcje.
        String canonicalData=canonical(root.getJSONObject("settings"))+"\n"
            +canonical(root.getJSONObject("tables"))+"\n"
            +canonical(root.opt("syncRecords"));
        byte[] sum=MessageDigest.getInstance("SHA-256").digest(
            canonicalData.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder(64);
        for(byte b:sum)
            result.append(String.format(java.util.Locale.ROOT,
                "%02x",b&255));
        return result.toString();
    }

    private static Map<String,JSONObject> rows(String table,JSONArray data)
            throws Exception {
        Map<String,JSONObject> result=new LinkedHashMap<>();
        for(int i=0;i<data.length();i++) {
            JSONObject row=data.optJSONObject(i);
            if(row==null)
                throw new IllegalArgumentException(
                    "Niepoprawny rekord w tabeli "+table+".");
            String id;
            if("task_rotation_members".equals(table))
                id=longId(row,"task_id")+":"+longId(row,"member_id");
            else if("project_task_dependencies".equals(table))
                id=longId(row,"task_id")+":"+longId(row,"depends_on_task_id");
            else if("pantry_packages".equals(table))
                id=longId(row,"pantry_id");
            else id=longId(row,"id");
            if(result.put(id,row)!=null)
                throw new IllegalArgumentException(
                    "Powtarzający się identyfikator w tabeli "+table+".");
        }
        return result;
    }

    private static Map<String,JSONObject> rowsByUuid(JSONArray data,String field)
            throws Exception {
        Map<String,JSONObject> result=new LinkedHashMap<>();
        for(int i=0;i<data.length();i++) {
            JSONObject row=data.optJSONObject(i);
            if(row==null||!row.has("id"))
                throw new IllegalArgumentException(
                    "Nie da się porównać historii budżetu: "+field+".");
            String id=row.getString("id");
            if(id.isBlank()||result.put(id,row)!=null)
                throw new IllegalArgumentException(
                    "Niejednoznaczne ID budżetu: "+field+".");
        }
        return result;
    }

    private static String longId(JSONObject row,String field) throws Exception {
        return new BigDecimal(String.valueOf(row.get(field)))
            .toBigIntegerExact().toString();
    }

    private static String budgetLabel(String key) {
        switch(key) {
            case "paycheckMonthlyBudget": return "Budżet • plan";
            case "paycheckRecipients": return "Budżet • odbiorcy";
            default: return "Budżet • historia";
        }
    }

    private static String label(String table) {
        if(table.startsWith("project_")||"projects".equals(table))
            return "Projekty • "+table;
        if("tasks".equals(table)||table.startsWith("task_")
                ||table.startsWith("member_"))
            return "Zadania i czas • "+table;
        if(table.startsWith("storage_")||"places".equals(table)
                ||"nfc_links".equals(table))
            return "Magazyn domowy • "+table;
        if(table.startsWith("budget_"))return "Budżet • "+table;
        if(table.startsWith("paycheck_")||table.startsWith("bank_"))
            return "PayCheck • "+table;
        if(table.startsWith("pantry_")||"pantry".equals(table)
                ||table.startsWith("shopping_"))
            return "Spiżarnia / zakupy • "+table;
        if(table.startsWith("vehicle_")||"vehicles".equals(table))
            return "Pojazdy • "+table;
        return table;
    }

    private static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject) {
            JSONObject json=(JSONObject)value;
            List<String> keys=new ArrayList<>();
            for(Iterator<String> it=json.keys();it.hasNext();)keys.add(it.next());
            Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");
            for(int i=0;i<keys.size();i++) {
                if(i>0)out.append(',');
                String key=keys.get(i);
                out.append(JSONObject.quote(key)).append(':')
                    .append(canonical(json.get(key)));
            }
            return out.append('}').toString();
        }
        if(value instanceof JSONArray) {
            JSONArray list=(JSONArray)value;
            StringBuilder out=new StringBuilder("[");
            for(int i=0;i<list.length();i++) {
                if(i>0)out.append(',');
                out.append(canonical(list.get(i)));
            }
            return out.append(']').toString();
        }
        if(value instanceof Number) {
            BigDecimal number=new BigDecimal(String.valueOf(value));
            return number.compareTo(BigDecimal.ZERO)==0?"0":
                number.stripTrailingZeros().toPlainString();
        }
        if(value instanceof Boolean)return value.toString();
        return JSONObject.quote(String.valueOf(value));
    }
}
