package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Locally retained metadata only. Never store bank notification body or passcode.
 * No PayCheck DB writes: these are unverified suggestions until user attests.
 */
final class BankNotificationHints {
    private static final String PREFS="edhome_bank_hint_prefs";
    private static final String ENABLED="bank_notification_opt_in";
    private static final String PACKAGES="selected_banking_packages";
    private static final String ROWS="bank_notification_hints_v1";
    private static final int MAX=40;
    private static final long TTL=14L*24*60*60*1000;

    static final class Entry {
        final String key,source,kind;
        final long amount,received;
        Entry(String key,String source,String kind,long amount,long received) {
            this.key=key;this.source=source;this.kind=kind;
            this.amount=amount;this.received=received;
        }
    }
    private BankNotificationHints(){}

    private static SharedPreferences pref(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static boolean enabled(Context c){return pref(c).getBoolean(ENABLED,false);}
    static Set<String> selected(Context c){
        return new java.util.HashSet<>(
            pref(c).getStringSet(PACKAGES,java.util.Collections.emptySet()));
    }
    static void configure(Context c,Set<String> packages,boolean enabled) {
        SharedPreferences.Editor edit=pref(c).edit()
            .putStringSet(PACKAGES,new java.util.HashSet<>(packages))
            .putBoolean(ENABLED,enabled&&!packages.isEmpty());
        if(!enabled||packages.isEmpty())edit.remove(ROWS);
        edit.apply();
    }
    static List<Entry> list(Context context) {
        List<Entry> result=new ArrayList<>();
        long now=System.currentTimeMillis();
        try {
            JSONArray data=new JSONArray(pref(context).getString(ROWS,"[]"));
            for(int i=0;i<data.length();i++) {
                JSONObject row=data.getJSONObject(i);
                String key=row.getString("key");
                String source=row.getString("source");
                String kind=row.getString("kind");
                long amount=row.getLong("amount");
                long received=row.getLong("received");
                if(key.matches("[0-9a-f]{64}")&&source.length()<=255
                        &&("income".equals(kind)||"expense".equals(kind))
                        &&amount>0&&amount<=MoneyRules.MAX_GROSZ
                        &&received<=now+60000&&received>now-TTL
                        &&selected(context).contains(source))
                    result.add(new Entry(key,source,kind,amount,received));
            }
        }catch(Exception invalid){ /* discard damaged local hint cache */ }
        return result;
    }
    static void remove(Context context,String key) {
        List<Entry> entries=list(context);
        entries.removeIf(e->e.key.equals(key));
        persist(context,entries);
    }
    static void collect(Context context,StatusBarNotification sbn,
            String notificationText) {
        if(sbn==null||!enabled(context)
                ||!selected(context).contains(sbn.getPackageName()))return;
        BankNotificationRules.Hint hint=BankNotificationRules.parse(notificationText);
        if(hint==null)return;
        // Same Android notification, even after service reconnect, has same key.
        // Never hash or log/store unredacted notification title/text.
        String unique=digest(sbn.getPackageName()+"\n"+sbn.getKey()
            +"\n"+sbn.getPostTime());
        List<Entry> entries=list(context);
        for(Entry existing:entries)if(existing.key.equals(unique))return;
        entries.add(0,new Entry(unique,sbn.getPackageName(),hint.kind,
            hint.amountGrosz,System.currentTimeMillis()));
        if(entries.size()>MAX)entries=new ArrayList<>(entries.subList(0,MAX));
        persist(context,entries);
    }
    private static void persist(Context context,List<Entry> entries) {
        JSONArray result=new JSONArray();
        for(Entry e:entries) {
            JSONObject row=new JSONObject();
            try {
                row.put("key",e.key);
                row.put("source",e.source);
                row.put("kind",e.kind);
                row.put("amount",e.amount);
                row.put("received",e.received);
                result.put(row);
            }catch(Exception invalid){ /* impossible for primitives */ }
        }
        pref(context).edit().putString(ROWS,result.toString()).apply();
    }
    private static String digest(String data) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte b:hash)result.append(
                String.format(Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
