package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Lokalny magazyn sekretu SUPLA Cloud.
 *
 * Token nigdy nie trafia do zwykłego backupu EDHOME, logów ani synchronizacji.
 * Klucz szyfrujący pozostaje w Android Keystore tego urządzenia.
 */
final class SuplaSecretStore {
    private static final String PREFS = "edhome_supla_secrets";
    private static final String KEY_ALIAS = "edhome_supla_cloud_token_v1";
    private static final String CIPHER_KEY = "token_ciphertext";
    private static final String IV_KEY = "token_iv";

    private SuplaSecretStore() {}

    static boolean hasToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains(CIPHER_KEY) && prefs.contains(IV_KEY);
    }

    static void saveToken(Context context, String token) throws Exception {
        String clean = token == null ? "" : token.trim();
        if (clean.length() < 16 || clean.length() > 4096)
            throw new IllegalArgumentException("Token SUPLA ma nieprawidłową długość.");

        SecretKey key = key();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean saved = prefs.edit()
            .putString(CIPHER_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .commit();
        if (!saved) throw new IllegalStateException("Nie udało się bezpiecznie zapisać tokenu SUPLA.");
    }

    static String loadToken(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encryptedText = prefs.getString(CIPHER_KEY, "");
        String ivText = prefs.getString(IV_KEY, "");
        if (encryptedText == null || encryptedText.isEmpty()
                || ivText == null || ivText.isEmpty()) return "";

        byte[] encrypted = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivText, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] clear = cipher.doFinal(encrypted);
        return new String(clear, StandardCharsets.UTF_8);
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(CIPHER_KEY).remove(IV_KEY).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }
}
