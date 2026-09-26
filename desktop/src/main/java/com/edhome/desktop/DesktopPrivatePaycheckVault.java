package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class DesktopPrivatePaycheckVault {
    private static final String FORMAT = "EDHOME_PRIVATE_PAYCHECK_ENCRYPTED_V1";
    private static final int ITERATIONS = 310000;
    private static final byte[] AAD =
        "EDHOME_PRIVATE_PAYCHECK_V1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path STORE = Path.of(System.getProperty("user.home"),
        ".edhome", "private-paycheck-encrypted.json");
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTRIES = 15000;

    static final class Session implements AutoCloseable {
        private byte[] key;
        private final byte[] salt;
        private Session(byte[] key, byte[] salt) {
            this.key = key;
            this.salt = salt;
        }
        boolean active() { return key != null; }
        byte[] key() {
            if (key == null) throw new IllegalStateException("Sejf jest zamknięty.");
            return key;
        }
        byte[] salt() { return salt; }
        @Override public void close() {
            if (key != null) Arrays.fill(key, (byte)0);
            key = null;
            Arrays.fill(salt, (byte)0);
        }
    }

    static final class Entry {
        final String id;
        final String kind;
        final String category;
        final long grosz;
        final String note;
        final long date;
        final String status;

        Entry(String id, String kind, String category, long grosz,
                String note, long date, String status) {
            this.id=id; this.kind=kind; this.category=category; this.grosz=grosz;
            this.note=note; this.date=date; this.status=status;
        }
    }

    private DesktopPrivatePaycheckVault() { }

    static boolean configured() {
        return Files.isRegularFile(STORE);
    }

    static Session create(char[] password) throws Exception {
        if (configured())
            throw new IllegalStateException("Prywatny sejf Desktop jest już skonfigurowany.");
        validatePortablePassword(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt);
        Session session = new Session(key, salt.clone());
        try {
            JsonObject plain = new JsonObject();
            plain.addProperty("format", FORMAT);
            plain.add("entries", new JsonArray());
            savePlain(session, plain);
            return session;
        } catch (Exception error) {
            session.close();
            throw error;
        } finally {
            Arrays.fill(salt, (byte)0);
        }
    }

    static Session unlock(char[] password) throws Exception {
        validatePortablePassword(password);
        JsonObject archive = readArchive(STORE);
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(archive.get("salt").getAsString());
        } catch (Exception invalid) {
            throw new GeneralSecurityException("Nieprawidłowy salt prywatnego sejfu.");
        }
        if (salt.length != 16)
            throw new GeneralSecurityException("Nieprawidłowy salt prywatnego sejfu.");
        byte[] key = derive(password, salt);
        Session session = new Session(key, salt.clone());
        try {
            readPlain(session);
            return session;
        } catch (Exception error) {
            session.close();
            throw new GeneralSecurityException(
                "Nieprawidłowe hasło lub uszkodzony prywatny sejf.", error);
        } finally {
            Arrays.fill(salt, (byte)0);
        }
    }

    static Session importArchive(Path source, char[] password) throws Exception {
        validatePortablePassword(password);
        JsonObject archive = readArchive(source);
        byte[] salt = Base64.getDecoder().decode(archive.get("salt").getAsString());
        if (salt.length != 16)
            throw new GeneralSecurityException("Nieprawidłowy salt kopii.");
        byte[] key = derive(password, salt);
        Session verify = new Session(key, salt.clone());
        try {
            JsonObject plain = decryptArchive(archive, verify);
            validatePlain(plain);
            Files.createDirectories(STORE.getParent());
            Path temp = STORE.resolveSibling(STORE.getFileName() + ".tmp");
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, STORE, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return verify;
        } catch (Exception error) {
            verify.close();
            throw error;
        } finally {
            Arrays.fill(salt, (byte)0);
        }
    }

    static void exportArchive(Path target) throws Exception {
        if (!configured()) throw new IllegalStateException("Brak prywatnego sejfu.");
        Files.copy(STORE, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static List<Entry> entries(Session session) throws Exception {
        JsonObject plain = readPlain(session);
        JsonArray rows = plain.getAsJsonArray("entries");
        List<Entry> result = new ArrayList<>();
        for (JsonElement element : rows) {
            JsonObject item = element.getAsJsonObject();
            JsonObject entry = item.getAsJsonObject("entry");
            result.add(new Entry(
                item.get("id").getAsString(),
                entry.get("kind").getAsString(),
                entry.get("category").getAsString(),
                entry.get("grosz").getAsLong(),
                entry.get("note").getAsString(),
                entry.get("date").getAsLong(),
                entry.has("status") ? entry.get("status").getAsString() : "confirmed"));
        }
        return result;
    }

    static String add(Session session, String kind, String category, long grosz,
            String note, String status) throws Exception {
        return addWithId(session, UUID.randomUUID().toString(), kind, category,
            grosz, note, status);
    }

    static String addWithId(Session session, String id, String kind,
            String category, long grosz, String note, String status) throws Exception {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}"))
            throw new GeneralSecurityException("Nieprawidłowy identyfikator prywatnego wpisu.");
        validateEntry(kind, category, grosz, note, System.currentTimeMillis(), status);
        JsonObject plain = readPlain(session);
        JsonArray rows = plain.getAsJsonArray("entries");
        for (JsonElement element : rows) {
            JsonObject prior = element.getAsJsonObject();
            if (id.equals(prior.get("id").getAsString())) return id;
        }
        if (rows.size() >= MAX_ENTRIES)
            throw new IllegalStateException("Prywatny sejf osiągnął limit wpisów.");
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", kind);
        entry.addProperty("category", category);
        entry.addProperty("grosz", grosz);
        entry.addProperty("note", note.trim());
        entry.addProperty("date", System.currentTimeMillis());
        entry.addProperty("status", status);
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.add("entry", entry);
        rows.add(item);
        savePlain(session, plain);
        return id;
    }

    static boolean confirm(Session session, String id) throws Exception {
        JsonObject plain = readPlain(session);
        for (JsonElement element : plain.getAsJsonArray("entries")) {
            JsonObject item = element.getAsJsonObject();
            if (!id.equals(item.get("id").getAsString())) continue;
            JsonObject entry = item.getAsJsonObject("entry");
            if (!"pending".equals(entry.has("status")
                    ? entry.get("status").getAsString() : "confirmed"))
                return false;
            entry.addProperty("status", "confirmed");
            savePlain(session, plain);
            return true;
        }
        return false;
    }

    private static JsonObject readPlain(Session session) throws Exception {
        return decryptArchive(readArchive(STORE), session);
    }

    private static JsonObject decryptArchive(JsonObject archive, Session session)
            throws Exception {
        if (!FORMAT.equals(archive.get("format").getAsString()))
            throw new GeneralSecurityException("Nieobsługiwany format prywatnej kopii.");
        String plain = unseal(session.key(), archive.get("encrypted").getAsString());
        JsonObject decoded = JsonParser.parseString(plain).getAsJsonObject();
        validatePlain(decoded);
        return decoded;
    }

    private static void savePlain(Session session, JsonObject plain) throws Exception {
        validatePlain(plain);
        JsonObject archive = new JsonObject();
        archive.addProperty("format", FORMAT);
        archive.addProperty("salt", Base64.getEncoder().encodeToString(session.salt()));
        archive.addProperty("encrypted", seal(session.key(), plain.toString()));
        byte[] bytes = archive.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalStateException("Prywatny sejf jest za duży.");
        Files.createDirectories(STORE.getParent());
        Path temp = STORE.resolveSibling(STORE.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, STORE, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, STORE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject readArchive(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Prywatna kopia przekracza 8 MB.");
        JsonObject archive = JsonParser.parseString(
            new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!archive.has("format") || !archive.has("salt") || !archive.has("encrypted"))
            throw new GeneralSecurityException("Nieprawidłowy plik prywatnego PayCheck.");
        return archive;
    }

    private static void validatePlain(JsonObject plain) throws Exception {
        if (!plain.has("format") || !FORMAT.equals(plain.get("format").getAsString())
                || !plain.has("entries") || !plain.get("entries").isJsonArray())
            throw new GeneralSecurityException("Nieprawidłowe dane prywatnego PayCheck.");
        JsonArray rows = plain.getAsJsonArray("entries");
        if (rows.size() > MAX_ENTRIES)
            throw new GeneralSecurityException("Za dużo prywatnych wpisów.");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonElement element : rows) {
            JsonObject item = element.getAsJsonObject();
            String id = item.get("id").getAsString();
            if (!id.matches("[0-9a-fA-F-]{36}") || !ids.add(id))
                throw new GeneralSecurityException("Nieprawidłowy identyfikator prywatnego wpisu.");
            JsonObject entry = item.getAsJsonObject("entry");
            validateEntry(entry.get("kind").getAsString(),
                entry.get("category").getAsString(), entry.get("grosz").getAsLong(),
                entry.get("note").getAsString(), entry.get("date").getAsLong(),
                entry.has("status") ? entry.get("status").getAsString() : "confirmed");
        }
    }

    private static void validateEntry(String kind, String category, long grosz,
            String note, long date, String status) throws GeneralSecurityException {
        if (!("income".equals(kind) || "expense".equals(kind))
                || !validCategory(category)
                || grosz < 1 || grosz > 99_999_999_999L
                || note == null || note.length() > 160 || date <= 0
                || !("pending".equals(status) || "confirmed".equals(status)))
            throw new GeneralSecurityException("Nieprawidłowy prywatny wpis.");
    }

    private static boolean validCategory(String value) {
        return java.util.Set.of("shopping","bills","home","vehicle","salary",
            "food","household","beauty","pet","other").contains(value);
    }

    private static void validatePortablePassword(char[] password)
            throws GeneralSecurityException {
        if (password == null || password.length < 12 || password.length > 64)
            throw new GeneralSecurityException(
                "Hasło prywatnej kopii musi mieć 12–64 znaki.");
    }

    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String seal(byte[] key, String plaintext) throws Exception {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] joined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce,0,joined,0,nonce.length);
        System.arraycopy(encrypted,0,joined,nonce.length,encrypted.length);
        return Base64.getEncoder().encodeToString(joined);
    }

    private static String unseal(byte[] key, String payload) throws Exception {
        byte[] joined;
        try { joined = Base64.getDecoder().decode(payload); }
        catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Uszkodzony prywatny sejf.", error);
        }
        if (joined.length < 29)
            throw new GeneralSecurityException("Uszkodzony prywatny sejf.");
        byte[] nonce = Arrays.copyOfRange(joined,0,12);
        byte[] encrypted = Arrays.copyOfRange(joined,12,joined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"),
            new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
