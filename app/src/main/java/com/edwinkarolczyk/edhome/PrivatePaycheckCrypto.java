package com.edwinkarolczyk.edhome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Per-device private vault encryption; never write money, descriptions or keys in cleartext. */
final class PrivatePaycheckCrypto {
    static final int ITERATIONS = 310000;
    private static final byte[] AAD =
        "EDHOME_PRIVATE_PAYCHECK_V1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private PrivatePaycheckCrypto() { }

    static boolean validPassword(char[] password) {
        return password != null && password.length >= 5
            && password.length <= 64;
    }

    // A portable encrypted backup keeps a stronger, independent password.
    static boolean validBackupPassword(char[] password) {
        return password != null && password.length >= 12
            && password.length <= 64;
    }

    static byte[] newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return salt;
    }

    static byte[] key(char[] password, byte[] salt) throws GeneralSecurityException {
        if (!validPassword(password) || salt == null || salt.length != 16)
            throw new GeneralSecurityException("Invalid private vault credentials");
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static String seal(byte[] key, String plaintext) throws GeneralSecurityException {
        if (key == null || key.length != 32 || plaintext == null)
            throw new GeneralSecurityException("Locked private vault");
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(
            plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] joined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, joined, 0, nonce.length);
        System.arraycopy(encrypted, 0, joined, nonce.length, encrypted.length);
        return Base64.getEncoder().encodeToString(joined);
    }

    static String unseal(byte[] key, String payload) throws GeneralSecurityException {
        if (key == null || key.length != 32 || payload == null)
            throw new GeneralSecurityException("Locked private vault");
        final byte[] joined;
        try {
            joined = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException problem) {
            throw new GeneralSecurityException("Damaged private vault", problem);
        }
        if (joined.length < 29)
            throw new GeneralSecurityException("Damaged private vault");
        byte[] nonce = Arrays.copyOfRange(joined, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(joined, 12, joined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
