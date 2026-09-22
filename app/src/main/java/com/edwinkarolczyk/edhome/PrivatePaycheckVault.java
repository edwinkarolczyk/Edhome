package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import org.json.JSONObject;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ONE local private owner. Dedicated encrypted SQLite database is intentionally
 * absent from portable EDHOME JSON backup and from the shared PayCheck ledger.
 * The password is never saved; losing it permanently loses private finance data.
 */
final class PrivatePaycheckVault {
    private static final String PREFS = "edhome_private_paycheck";
    private static final String PRIVATE_DB = "edhome-paycheck-private.db";
    private static final String CHECK = "EDHOME_PRIVATE_PAYCHECK_UNLOCK_V1";

    static final class Session {
        private byte[] key;
        private Session(byte[] key) { this.key = key; }
        boolean active() { return key != null; }
        byte[] secret() {
            if (key == null) throw new IllegalStateException("Sejf jest zamknięty.");
            return key;
        }
        void lock() {
            if (key != null) Arrays.fill(key, (byte) 0);
            key = null;
        }
    }

    static final class Entry {
        final String kind;
        final String category;
        final long amountGrosz;
        final String note;
        final long createdAt;
        Entry(String kind, String category, long amountGrosz, String note,
                long createdAt) {
            this.kind = kind;
            this.category = category;
            this.amountGrosz = amountGrosz;
            this.note = note;
            this.createdAt = createdAt;
        }
    }

    private PrivatePaycheckVault() { }

    static long cooldownMillis(Context context) {
        return Math.max(0L, preferences(context).getLong("blocked_until", 0L)
            - System.currentTimeMillis());
    }

    static void recordFailure(Context context) {
        SharedPreferences p = preferences(context);
        int next = p.getInt("failed_attempts", 0) + 1;
        SharedPreferences.Editor edit = p.edit();
        if (next >= 5) {
            edit.putInt("failed_attempts", 0)
                .putLong("blocked_until", System.currentTimeMillis() + 300000L);
        } else edit.putInt("failed_attempts", next);
        edit.commit();
    }

    static void clearFailures(Context context) {
        preferences(context).edit().remove("failed_attempts")
            .remove("blocked_until").commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean configured(Context context) {
        SharedPreferences p = preferences(context);
        return p.contains("salt") || p.contains("verifier");
    }

    static Session configure(Context context, char[] password)
            throws GeneralSecurityException {
        if (configured(context))
            throw new GeneralSecurityException("Sejf jest już skonfigurowany.");
        byte[] salt = PrivatePaycheckCrypto.newSalt();
        byte[] key = PrivatePaycheckCrypto.key(password, salt);
        try {
            String verifier = PrivatePaycheckCrypto.seal(key, CHECK);
            if (!preferences(context).edit()
                    .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("verifier", verifier).commit())
                throw new GeneralSecurityException("Nie zapisano hasła sejfu.");
            return new Session(key);
        } catch (GeneralSecurityException error) {
            Arrays.fill(key, (byte) 0);
            throw error;
        } finally {
            Arrays.fill(salt, (byte) 0);
        }
    }

    static Session unlock(Context context, char[] password)
            throws GeneralSecurityException {
        if (cooldownMillis(context) > 0)
            throw new GeneralSecurityException("Sejf jest czasowo zablokowany.");
        SharedPreferences p = preferences(context);
        if (!p.contains("salt") || !p.contains("verifier"))
            throw new GeneralSecurityException("Sejf nie został skonfigurowany.");
        byte[] salt;
        try {
            salt = Base64.decode(p.getString("salt", ""), Base64.NO_WRAP);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Nieprawidłowe dane sejfu.", error);
        }
        byte[] key;
        try {
            key = PrivatePaycheckCrypto.key(password, salt);
        } finally {
            Arrays.fill(salt, (byte) 0);
        }
        try {
            String plaintext = PrivatePaycheckCrypto.unseal(key,
                p.getString("verifier", ""));
            if (!MessageDigest.isEqual(
                    plaintext.getBytes(StandardCharsets.UTF_8),
                    CHECK.getBytes(StandardCharsets.UTF_8)))
                throw new GeneralSecurityException("Nieprawidłowe hasło.");
            return new Session(key);
        } catch (GeneralSecurityException error) {
            Arrays.fill(key, (byte) 0);
            throw new GeneralSecurityException("Nieprawidłowe hasło lub uszkodzony sejf.");
        }
    }

    private static SQLiteDatabase openDatabase(Context context, Session session) {
        session.secret(); // A locked activity must not even open the private file.
        SQLiteDatabase db = context.openOrCreateDatabase(
            PRIVATE_DB, Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS private_paycheck_entries ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, "
            + "sealed TEXT NOT NULL)");
        return db;
    }

    static String add(Context context, Session session, String operationId,
            String kind, String category, long grosz, String note)
            throws Exception {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}")
                || !("income".equals(kind) || "expense".equals(kind))
                || !MoneyRules.category(category) || grosz < 1
                || grosz > MoneyRules.MAX_GROSZ
                || note == null || note.length() > 160)
            throw new IllegalArgumentException("Nieprawidłowa prywatna transakcja.");
        JSONObject payload = new JSONObject();
        payload.put("kind", kind);
        payload.put("category", category);
        payload.put("grosz", grosz);
        payload.put("note", note.trim());
        payload.put("date", System.currentTimeMillis());
        String sealed = PrivatePaycheckCrypto.seal(
            session.secret(), payload.toString());
        try (SQLiteDatabase db = openDatabase(context, session)) {
            db.beginTransaction();
            try {
                try (Cursor prior = db.rawQuery(
                        "SELECT 1 FROM private_paycheck_entries WHERE operation_id=?",
                        new String[]{operationId})) {
                    if (prior.moveToFirst()) return "DUPLICATE";
                }
                ContentValues row = new ContentValues();
                row.put("operation_id", operationId);
                row.put("sealed", sealed);
                db.insertOrThrow("private_paycheck_entries", null, row);
                db.setTransactionSuccessful();
                return "COMMITTED";
            } finally {
                db.endTransaction();
            }
        }
    }

    static List<Entry> entries(Context context, Session session)
            throws Exception {
        List<Entry> rows = new ArrayList<>();
        try (SQLiteDatabase db = openDatabase(context, session);
             Cursor c = db.rawQuery("SELECT sealed FROM private_paycheck_entries "
                 + "ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                JSONObject json = new JSONObject(PrivatePaycheckCrypto.unseal(
                    session.secret(), c.getString(0)));
                String kind = json.getString("kind");
                String category = json.getString("category");
                long amount = json.getLong("grosz");
                String note = json.getString("note");
                long date = json.getLong("date");
                if (!("income".equals(kind) || "expense".equals(kind))
                        || !MoneyRules.category(category) || amount < 1
                        || amount > MoneyRules.MAX_GROSZ || note.length() > 160
                        || date <= 0)
                    throw new GeneralSecurityException("Nieprawidłowe dane sejfu.");
                rows.add(new Entry(kind, category, amount, note, date));
            }
        }
        return rows;
    }
}
