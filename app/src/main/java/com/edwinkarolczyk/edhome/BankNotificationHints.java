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
    private static final String RECEIPT="bank_receipt_notifications_enabled";
    private static final String ROWS="bank_notification_hints_v1";
    // Processed IDs survive dismissals / Android reconnects; no bank text stored.
    private static final String HANDLED="bank_notification_handled_v1";
    private static final int MAX_HANDLED=512;
    // Unassigned financial drafts must not silently disappear after 14 days
    // or when more than 40 bank notifications arrive. User closes them.

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
    static boolean receiptEnabled(Context c){
        return pref(c).getBoolean(RECEIPT,true);
    }
    static void setReceiptEnabled(Context c,boolean enabled){
        pref(c).edit().putBoolean(RECEIPT,enabled).apply();
    }
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
                        &&received<=now+60000&&received>0
                        &&selected(context).contains(source))
                    result.add(new Entry(key,source,kind,amount,received));
            }
        }catch(Exception invalid){ /* discard damaged local hint cache */ }
        return result;
    }
    /** Close a draft without forgetting its ID: a replay must not recreate it. */
    static boolean remove(Context context,String key) {
        List<Entry> entries=list(context);
        boolean found=entries.removeIf(e->e.key.equals(key));
        if(!found)return alreadyHandled(context,key);
        // Save the tombstone first. If the subsequent queue write fails, a
        // stale card may remain, but it cannot generate a second ledger entry.
        if(!rememberHandled(context,key))return false;
        return persist(context,entries);
    }

    static boolean alreadyHandled(Context context,String key) {
        return pref(context).getString(HANDLED,"").contains("|"+key+"|");
    }

    private static boolean rememberHandled(Context context,String key) {
        if(key==null||!key.matches("[0-9a-f]{64}"))return false;
        String old=pref(context).getString(HANDLED,"");
        if(old.contains("|"+key+"|"))return true;
        String[] ids=(old+"|"+key+"|").split("\\|");
        StringBuilder bounded=new StringBuilder();
        for(int i=Math.max(0,ids.length-MAX_HANDLED);i<ids.length;i++)
            if(ids[i].matches("[0-9a-f]{64}"))
                bounded.append('|').append(ids[i]).append('|');
        return pref(context).edit().putString(HANDLED,bounded.toString()).commit();
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
        if(alreadyHandled(context,unique))return;
        List<Entry> entries=list(context);
        for(Entry existing:entries)if(existing.key.equals(unique))return;
        Entry saved=new Entry(unique,sbn.getPackageName(),hint.kind,
            hint.amountGrosz,System.currentTimeMillis());
        entries.add(0,saved);
        // Never truncate older unassigned drafts to make room for a new one.
        // A receipt is allowed only after durable storage succeeds.
        if(persist(context,entries))BankReceiptNotifier.show(context,saved);
    }
    private static boolean persist(Context context,List<Entry> entries) {
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
        return pref(context).edit().putString(ROWS,result.toString()).commit();
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
