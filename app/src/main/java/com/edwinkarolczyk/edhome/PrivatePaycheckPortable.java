package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Separate password-encrypted, portable PRIVATE finance backup.
 * Never put decrypted entries in EDHOME's shared JSON backup or diagnostics.
 * Import is all-or-nothing and preserves operation UUIDs for idempotency.
 */
final class PrivatePaycheckPortable {
    private static final String FORMAT = "EDHOME_PRIVATE_PAYCHECK_ENCRYPTED_V1";
    private static final String DATABASE = "edhome-paycheck-private.db";
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 15000;
    private static final String UUID =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private PrivatePaycheckPortable() { }

    private static SQLiteDatabase open(Context context, PrivatePaycheckVault.Session session)
            throws Exception {
        session.secret();
        // The existing vault initializer creates its table if this is a new, empty vault.
        PrivatePaycheckVault.entries(context, session);
        return context.openOrCreateDatabase(DATABASE, Context.MODE_PRIVATE, null);
    }

    private static void validate(JSONObject entry) throws Exception {
        String kind = entry.getString("kind");
        String category = entry.getString("category");
        long money = entry.getLong("grosz");
        String note = entry.getString("note");
        long time = entry.getLong("date");
        if (!("income".equals(kind) || "expense".equals(kind))
                || !MoneyRules.category(category) || money < 1
                || money > MoneyRules.MAX_GROSZ || note.length() > 160
                || time <= 0)
            throw new GeneralSecurityException("Invalid private finance record.");
    }

    private static boolean identical(JSONObject a, JSONObject b) throws Exception {
        return a.getString("kind").equals(b.getString("kind"))
            && a.getString("category").equals(b.getString("category"))
            && a.getLong("grosz") == b.getLong("grosz")
            && a.getString("note").equals(b.getString("note"))
            && a.getLong("date") == b.getLong("date");
    }

    static String exportEncrypted(Context context, PrivatePaycheckVault.Session session,
            char[] backupPassword) throws Exception {
        if (!PrivatePaycheckCrypto.validPassword(backupPassword))
            throw new GeneralSecurityException("Backup password must be 12–64 characters.");
        JSONArray rows = new JSONArray();
        try (SQLiteDatabase database = open(context, session);
             Cursor c = database.rawQuery(
                 "SELECT operation_id,sealed FROM private_paycheck_entries ORDER BY id", null)) {
            while (c.moveToNext()) {
                if (rows.length() >= MAX_ENTRIES)
                    throw new GeneralSecurityException("Too many private records.");
                JSONObject entry = new JSONObject(PrivatePaycheckCrypto.unseal(
                    session.secret(), c.getString(1)));
                validate(entry);
                String id = c.getString(0);
                if (id == null || !id.matches(UUID))
                    throw new GeneralSecurityException("Invalid operation identifier.");
                JSONObject item = new JSONObject();
                item.put("id", id);
                item.put("entry", entry);
                rows.put(item);
            }
        }
        JSONObject plain = new JSONObject();
        plain.put("format", FORMAT);
        plain.put("entries", rows);
        byte[] salt = PrivatePaycheckCrypto.newSalt();
        byte[] key = null;
        try {
            key = PrivatePaycheckCrypto.key(backupPassword, salt);
            JSONObject archive = new JSONObject();
            archive.put("format", FORMAT);
            archive.put("salt", java.util.Base64.getEncoder().encodeToString(salt));
            archive.put("encrypted", PrivatePaycheckCrypto.seal(key, plain.toString()));
            String result = archive.toString();
            if (result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new GeneralSecurityException("Encrypted backup too large.");
            return result;
        } finally {
            Arrays.fill(salt, (byte) 0);
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    static int importEncrypted(Context context, PrivatePaycheckVault.Session session,
            String json, char[] backupPassword) throws Exception {
        session.secret();
        if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES
                || !PrivatePaycheckCrypto.validPassword(backupPassword))
            throw new GeneralSecurityException("Invalid private backup.");
        JSONObject archive = new JSONObject(json);
        if (!FORMAT.equals(archive.getString("format")))
            throw new GeneralSecurityException("Unsupported private backup format.");
        byte[] salt;
        try {
            salt = java.util.Base64.getDecoder().decode(archive.getString("salt"));
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid private backup salt.", error);
        }
        if (salt.length != 16) throw new GeneralSecurityException("Invalid salt.");
        byte[] key = null;
        String unsealed;
        try {
            key = PrivatePaycheckCrypto.key(backupPassword, salt);
            unsealed = PrivatePaycheckCrypto.unseal(key, archive.getString("encrypted"));
        } finally {
            Arrays.fill(salt, (byte) 0);
            if (key != null) Arrays.fill(key, (byte) 0);
        }
        JSONObject decoded = new JSONObject(unsealed);
        if (!FORMAT.equals(decoded.getString("format")))
            throw new GeneralSecurityException("Invalid inner backup format.");
        JSONArray rows = decoded.getJSONArray("entries");
        if (rows.length() > MAX_ENTRIES)
            throw new GeneralSecurityException("Too many private records.");
        Set<String> seen = new HashSet<>();
        // Verify ALL entries before writing even the first one.
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String id = row.getString("id");
            if (!id.matches(UUID) || !seen.add(id))
                throw new GeneralSecurityException("Invalid or repeated operation ID.");
            validate(row.getJSONObject("entry"));
        }
        int imported = 0;
        try (SQLiteDatabase database = open(context, session)) {
            database.beginTransaction();
            try {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String id = row.getString("id");
                    JSONObject entry = row.getJSONObject("entry");
                    try (Cursor prior = database.rawQuery(
                            "SELECT sealed FROM private_paycheck_entries WHERE operation_id=?",
                            new String[]{id})) {
                        if (prior.moveToFirst()) {
                            JSONObject current = new JSONObject(PrivatePaycheckCrypto.unseal(
                                session.secret(), prior.getString(0)));
                            validate(current);
                            if (!identical(current, entry))
                                throw new GeneralSecurityException(
                                    "Conflicting private operation ID; nothing imported.");
                            continue;
                        }
                    }
                    ContentValues values = new ContentValues();
                    values.put("operation_id", id);
                    values.put("sealed", PrivatePaycheckCrypto.seal(
                        session.secret(), entry.toString()));
                    database.insertOrThrow("private_paycheck_entries", null, values);
                    imported++;
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        return imported;
    }
}
