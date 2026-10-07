package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Trwała kartoteka odbiorców PayCheck.
 * Odbiorca jest osobnym bytem od zobowiązania/budżetowej pozycji.
 */
final class PaycheckRecipientStore {
    static final String PREF_KEY = "paycheck_recipients_v1";
    static final int MAX_RECIPIENTS = 200;

    static final class Recipient {
        String id;
        String name;
        boolean active;
    }

    private PaycheckRecipientStore() { }

    static List<Recipient> load(SharedPreferences prefs) throws Exception {
        return parse(prefs.getString(PREF_KEY, "[]"));
    }

    static Recipient find(List<Recipient> recipients, String id) {
        if (id == null || id.isBlank()) return null;
        for (Recipient recipient : recipients)
            if (id.equals(recipient.id)) return recipient;
        return null;
    }

    static String name(List<Recipient> recipients, String id) {
        Recipient recipient = find(recipients,id);
        return recipient == null ? "" : recipient.name;
    }

    static Recipient getOrCreate(SharedPreferences prefs, String rawName)
            throws Exception {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty())
            throw new IllegalArgumentException("Podaj nazwę odbiorcy.");
        if (name.length() > 80)
            throw new IllegalArgumentException("Nazwa odbiorcy ma maks. 80 znaków.");

        List<Recipient> recipients = load(prefs);
        String key = normalized(name);
        for (Recipient existing : recipients)
            if (normalized(existing.name).equals(key)) {
                if (!existing.active) {
                    existing.active = true;
                    save(prefs,recipients);
                }
                return existing;
            }
        if (recipients.size() >= MAX_RECIPIENTS)
            throw new IllegalArgumentException("Za dużo odbiorców PayCheck.");
        Recipient recipient = new Recipient();
        recipient.id = UUID.randomUUID().toString();
        recipient.name = name;
        recipient.active = true;
        recipients.add(recipient);
        save(prefs,recipients);
        return recipient;
    }

    static String serialized(SharedPreferences prefs) throws Exception {
        return serialize(load(prefs));
    }

    static void restoreSerialized(SharedPreferences.Editor editor, String json)
            throws Exception {
        List<Recipient> recipients = parse(json);
        editor.putString(PREF_KEY,serialize(recipients));
    }

    static void validateSerialized(String json) throws Exception {
        parse(json);
    }

    private static List<Recipient> parse(String raw) throws Exception {
        if (raw == null || raw.length() > 120000 || raw.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Nieprawidłowa kartoteka odbiorców.");
        JSONArray array = new JSONArray(raw);
        if (array.length() > MAX_RECIPIENTS)
            throw new IllegalArgumentException("Za dużo odbiorców.");
        List<Recipient> result = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (int i=0;i<array.length();i++) {
            JSONObject json = array.getJSONObject(i);
            Recipient recipient = new Recipient();
            recipient.id = json.getString("id");
            recipient.name = json.getString("name").trim();
            recipient.active = !json.has("active") || json.getBoolean("active");
            validate(recipient);
            if (!ids.add(recipient.id) || !names.add(normalized(recipient.name)))
                throw new IllegalArgumentException("Powtórzony odbiorca PayCheck.");
            result.add(recipient);
        }
        return result;
    }

    private static String serialize(List<Recipient> recipients) throws Exception {
        JSONArray array = new JSONArray();
        for (Recipient recipient : recipients) {
            validate(recipient);
            JSONObject json = new JSONObject();
            json.put("id",recipient.id);
            json.put("name",recipient.name);
            json.put("active",recipient.active);
            array.put(json);
        }
        return array.toString();
    }

    private static void save(SharedPreferences prefs, List<Recipient> recipients)
            throws Exception {
        if (!prefs.edit().putString(PREF_KEY,serialize(recipients)).commit())
            throw new IllegalStateException("Nie zapisano odbiorcy PayCheck.");
    }

    private static void validate(Recipient recipient) {
        if (recipient == null || recipient.id == null
                || !recipient.id.matches("[0-9a-fA-F-]{36}")
                || recipient.name == null || recipient.name.trim().isEmpty()
                || recipient.name.length() > 80)
            throw new IllegalArgumentException("Nieprawidłowy odbiorca PayCheck.");
    }

    private static String normalized(String value) {
        String plain = Normalizer.normalize(value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+"," ")
            .trim();
        return plain;
    }
}
