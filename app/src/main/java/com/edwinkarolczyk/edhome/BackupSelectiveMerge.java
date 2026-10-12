package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Przygotowuje pełną, spójną migawkę do importu jednej lub kilku kategorii.
 * Nie zapisuje niczego do bazy. Całą migawkę waliduje DataBackup.restoreJson.
 */
final class BackupSelectiveMerge {
    private static final int CURRENT_SCHEMA = 46;
    private static final int OLDEST_SELECTIVE_SCHEMA = 45;

    private BackupSelectiveMerge() { }

    static Set<String> available(String json) throws Exception {
        JSONObject source = BackupModuleSnapshot.index(json);
        Set<String> available = new HashSet<>();
        for (Iterator<String> names = source.keys(); names.hasNext();)
            available.add(names.next());
        return available;
    }

    static String merge(String installed, String archived, Set<String> requested)
            throws Exception {
        if (requested == null || requested.isEmpty())
            throw new IllegalArgumentException("Wybierz przynajmniej jeden moduł.");
        JSONObject current = new JSONObject(installed);
        JSONObject old = new JSONObject(archived);
        int liveVersion = current.getInt("databaseVersion");
        int sourceVersion = old.getInt("databaseVersion");
        if (liveVersion != CURRENT_SCHEMA || sourceVersion < OLDEST_SELECTIVE_SCHEMA
                || sourceVersion > CURRENT_SCHEMA)
            throw new IllegalArgumentException(
                "Ta wersja kopii wymaga pełnego przywrócenia; "
                + "wybór kategorii nie jest jeszcze bezpieczny.");
        if (!"edhome-data-backup".equals(old.optString("format"))
                || old.optInt("formatVersion", -1) != 1
                || !"edhome-data-backup".equals(current.optString("format")))
            throw new IllegalArgumentException("Nieprawidłowy format danych kopii.");
        if (old.optJSONArray("syncRecords") == null
                || current.optJSONArray("syncRecords") == null)
            throw new IllegalArgumentException(
                "Brakuje informacji synchronizacji w kopii.");

        JSONObject installedTables = current.getJSONObject("tables");
        JSONObject archivedTables = old.getJSONObject("tables");
        Set<String> available = available(archived);
        Set<String> names = new HashSet<>(requested);
        if (!available.containsAll(names))
            throw new IllegalArgumentException("Nie znaleziono wybranego modułu w kopii.");
        for (String name : names)
            if (!BackupModuleSnapshot.allowedPath(
                    BackupModuleSnapshot.PREFIX + name + ".json"))
                throw new IllegalArgumentException("Nieznana kategoria kopii: " + name);

        for (Iterator<String> tables = installedTables.keys(); tables.hasNext();) {
            String table = tables.next();
            if (!names.contains(BackupModuleSnapshot.categoryForTable(table))) continue;
            if (!archivedTables.has(table))
                throw new IllegalArgumentException(
                    "Kopia nie zawiera wymaganej tabeli: " + table);
            installedTables.put(table, archivedTables.getJSONArray(table));
        }

        JSONObject settings = current.getJSONObject("settings");
        JSONObject incomingSettings = old.getJSONObject("settings");
        if (names.contains("settings")) {
            // Nie przywracaj omyłkowo miniatur, historii QR ani zapisów Budżetu,
            // jeżeli te moduły nie zostały wybrane.
            String[] storageKeys = {"storageThumbnails", "storageQrHistory"};
            String[] budgetKeys = {"paycheckMonthlyBudget", "paycheckRecipients",
                "paycheckBudgetHistory", "paycheckBudgetAttachments"};
            JSONObject replacement = new JSONObject(incomingSettings.toString());
            if (!names.contains("storage"))
                copyKeys(settings, replacement, storageKeys);
            if (!names.contains("budget"))
                copyKeys(settings, replacement, budgetKeys);
            current.put("settings", replacement);
            settings = replacement;
        } else {
            if (names.contains("storage"))
                copyKeys(incomingSettings, settings,
                    new String[]{"storageThumbnails", "storageQrHistory"});
            if (names.contains("budget"))
                copyKeys(incomingSettings, settings,
                    new String[]{"paycheckMonthlyBudget", "paycheckRecipients",
                        "paycheckBudgetHistory", "paycheckBudgetAttachments"});
        }

        // Metadane tabel nieodtwarzanych pochodzą z bieżącej instalacji.
        // Wybrane tabele dostają identyfikatory ze źródła i nowy znacznik
        // aktualizacji, aby synchronizacja LAN mogła rozesłać zmiany.
        JSONArray joinedSync = new JSONArray();
        JSONArray currentSync = current.getJSONArray("syncRecords");
        JSONArray backupSync = old.getJSONArray("syncRecords");
        Set<String> uuids = new HashSet<>();
        for (int i = 0; i < currentSync.length(); i++) {
            JSONObject meta = currentSync.getJSONObject(i);
            if (names.contains(BackupModuleSnapshot.categoryForTable(
                    meta.getString("table")))) continue;
            String uuid = meta.getString("syncUuid");
            if (!uuids.add(uuid))
                throw new IllegalArgumentException("Zduplikowana tożsamość synchronizacji.");
            joinedSync.put(meta);
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < backupSync.length(); i++) {
            JSONObject from = backupSync.getJSONObject(i);
            if (!names.contains(BackupModuleSnapshot.categoryForTable(
                    from.getString("table")))) continue;
            JSONObject meta = new JSONObject(from.toString());
            if (!uuids.add(meta.getString("syncUuid")))
                throw new IllegalArgumentException(
                    "Konflikt identyfikatorów synchronizacji między modułami.");
            meta.put("revision", Math.max(1L, meta.getLong("revision") + 1L));
            meta.put("updatedAt", now);
            if (!meta.isNull("deletedAt")) meta.put("deletedAt", now);
            joinedSync.put(meta);
        }
        current.put("syncRecords", joinedSync);
        // formatVersion i databaseVersion pochodzą z aktualnej instalacji.
        // W ten sposób brakujących tabel schema v45 nie da się uznać za puste
        // i bez ostrzeżenia usunąć.
        String merged = current.toString();
        if (merged.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > DataBackup.MAX_BYTES)
            throw new IllegalArgumentException("Połączona kopia przekracza limit 32 MB.");
        return merged;
    }

    private static void copyKeys(JSONObject source, JSONObject target, String[] keys)
            throws Exception {
        for (String key : keys) {
            if (source.has(key)) target.put(key, source.get(key));
            else target.remove(key);
        }
    }
}
