package com.edwinkarolczyk.edhome;

import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class PrivatePaycheckCryptoSmoke {
    private static int count;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        count++;
    }

    public static void main(String[] args) throws Exception {
        char[] password = "BardzoTrudneHaslo2026!".toCharArray();
        check(PrivatePaycheckCrypto.validPassword(password), "password length");
        check(PrivatePaycheckCrypto.validPassword("12345".toCharArray()),\n            "five-character vault password allowed");\n        check(!PrivatePaycheckCrypto.validPassword("1234".toCharArray()),
            "short password refused");
        check(!PrivatePaycheckCrypto.validBackupPassword("12345".toCharArray()),\n            "short backup password refused");\n        byte[] salt = PrivatePaycheckCrypto.newSalt();
        byte[] key = PrivatePaycheckCrypto.key(password, salt);
        check(key.length == 32, "256-bit key");
        String plaintext = "{\"amount\":70000,\"note\":\"Zażółć ąę €\"}";
        String one = PrivatePaycheckCrypto.seal(key, plaintext);
        String two = PrivatePaycheckCrypto.seal(key, plaintext);
        check(!one.equals(two), "random nonce");
        check(plaintext.equals(PrivatePaycheckCrypto.unseal(key, one)),
            "Unicode roundtrip");
        check(plaintext.equals(PrivatePaycheckCrypto.unseal(key, two)),
            "second roundtrip");
        byte[] wrong = PrivatePaycheckCrypto.key(
            "InneBardzoTrudneHaslo2026".toCharArray(), salt);
        try {
            PrivatePaycheckCrypto.unseal(wrong, one);
            throw new AssertionError("Wrong password decrypted data");
        } catch (GeneralSecurityException expected) { count++; }
        byte[] corrupted = java.util.Base64.getDecoder().decode(one);
        corrupted[corrupted.length - 1] ^= 1;
        try {
            PrivatePaycheckCrypto.unseal(key,
                java.util.Base64.getEncoder().encodeToString(corrupted));
            throw new AssertionError("Tampered ciphertext decrypted");
        } catch (GeneralSecurityException expected) { count++; }
        Arrays.fill(key, (byte) 0);
        Arrays.fill(password, '\0');
        check(Arrays.equals(key, new byte[32]), "key cleared");
        try {
            PrivatePaycheckCrypto.unseal(key, one);
            throw new AssertionError("Cleared key decrypted data");
        } catch (GeneralSecurityException expected) { count++; }
        System.out.println("Private PayCheck AES-GCM/PBKDF2 smoke: PASS " + count);
    }
}
