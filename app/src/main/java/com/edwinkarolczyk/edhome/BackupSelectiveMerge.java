package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Przygotowuje pełną, spójną migawkę do importu jednej lub kilku kategorii.
 * Nie zapisuje niczego do bazy. Całą migawkę waliduje DataBackup.restoreJson.
 */
final class BackupSelectiveMerge {
    private static final int CURRENT_SCHEMA = 47;
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
        Set<String> required = BackupRestoreDependencies.resolve(
            installed, archived, names);
        if (!names.containsAll(required))
            throw new IllegalArgumentException(
                "Wybór nie obejmuje wymaganych powiązań: " + required
                + ". Wróć do listy i zatwierdź dodatkowe moduły.");
        if (!available.containsAll(names))
            throw new IllegalArgumentException("Nie znaleziono wybranego modułu w kopii.");
        for (String name : names)
            if (!BackupModuleSnapshot.allowedPath(
                    BackupModuleSnapshot.PREFIX + name + ".json"))
                throw new IllegalArgumentException("Nieznana kategoria kopii: " + name);

        for (Iterator<String> tables = installedTables.keys(); tables.hasNext();) {
            String table = tables.next();
            if (!names.contains(BackupModuleSnapshot.categoryForTable(table))) continue;
            if (!archivedTables.has(table)) {
                // W archiwach sprzed v47 nie istnieją jeszcze tabele zimowe.
                // Zachowaj obecne odznaczenia i ustawienia, zamiast je usuwać.
                if (sourceVersion < 47 && table.startsWith("frost_")) continue;
                throw new IllegalArgumentException(
                    "Kopia nie zawiera wymaganej tabeli: " + table);
            }
            if ("budget_history".equals(table)) {
                installedTables.put(table, mergeBudgetHistory(
                    installedTables.getJSONArray(table), archivedTables.getJSONArray(table)));
            } else if (sourceVersion < 47
                    && ("places".equals(table) || "storage_items".equals(table))) {
                // Schema v45/v46 nie zna flag mrozu. Uzupełnij je wyłącznie
                // w przywracanej kategorii, bez naruszania pozostałych modułów.
                JSONArray migrated = new JSONArray();
                JSONArray legacy = archivedTables.getJSONArray(table);
                String flag = "places".equals(table) ? "frost_risk" : "protect_frost";
                for (int i = 0; i < legacy.length(); i++) {
                    JSONObject row = new JSONObject(legacy.getJSONObject(i).toString());
                    if (!row.has(flag)) row.put(flag, 0);
                    migrated.put(row);
                }
                installedTables.put(table, migrated);
            } else installedTables.put(table, archivedTables.getJSONArray(table));
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

        // Zachowaj tożsamości niezmienionych tabel oraz historię Budżetu,
        // której nie wolno usuwać (append-only). Zmiany wybranych modułów
        // trafiają do LAN z rosnącą rewizją i znacznikami usunięcia.
        JSONArray joinedSync = new JSONArray();
        JSONArray currentSync = current.getJSONArray("syncRecords");
        JSONArray backupSync = old.getJSONArray("syncRecords");
        Set<String> uuids = new HashSet<>();
        Set<String> restoredUuids = new HashSet<>();
        Map<String,JSONObject> liveSelected = new HashMap<>();
        Map<String,String> retainedHistoryRows = new HashMap<>();

        for (int i = 0; i < currentSync.length(); i++) {
            JSONObject meta = currentSync.getJSONObject(i);
            String table = meta.getString("table");
            String uuid = meta.getString("syncUuid");
            // Nie oznaczaj nowych rekordów frost_* jako usunięte przy imporcie
            // starego modułu „Inne”: kopia v46 nie mogła ich zawierać.
            boolean selected = names.contains(BackupModuleSnapshot.categoryForTable(table))
                && !(sourceVersion < 47 && table.startsWith("frost_"));
            if (selected) liveSelected.put(uuid, meta);
            boolean appendOnly = selected && "budget_history".equals(table);
            if (appendOnly) {
                retainedHistoryRows.put(meta.getString("rowKey"), uuid);
            }
            if (selected && !appendOnly) continue;
            if (!uuids.add(uuid))
                throw new IllegalArgumentException("Zduplikowana tożsamość synchronizacji.");
            joinedSync.put(meta);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < backupSync.length(); i++) {
            JSONObject from = backupSync.getJSONObject(i);
            String table = from.getString("table");
            if (!names.contains(BackupModuleSnapshot.categoryForTable(table)))
                continue;
            String uuid = from.getString("syncUuid");
            if ("budget_history".equals(table)
                    && retainedHistoryRows.containsKey(from.getString("rowKey"))) {
                // Zachowaj lokalną tożsamość tej samej, niezmiennej operacji.
                continue;
            }
            JSONObject installedMeta = liveSelected.get(uuid);
            if (installedMeta != null
                    && (!table.equals(installedMeta.getString("table"))
                        || !from.getString("rowKey").equals(
                            installedMeta.getString("rowKey"))))
                throw new IllegalArgumentException(
                    "UUID synchronizacji wskazuje dwa różne rekordy.");
            JSONObject meta = new JSONObject(from.toString());
            long revision = Math.max(meta.getLong("revision"),
                installedMeta == null ? 0L : installedMeta.getLong("revision"));
            if (revision == Long.MAX_VALUE)
                throw new IllegalArgumentException("Przekroczony licznik synchronizacji.");
            meta.put("revision", revision + 1);
            meta.put("updatedAt", now);
            if (!meta.isNull("deletedAt")) meta.put("deletedAt", now);
            if (!uuids.add(uuid))
                throw new IllegalArgumentException(
                    "Konflikt identyfikatorów synchronizacji między modułami.");
            restoredUuids.add(uuid);
            joinedSync.put(meta);
        }

        for (Map.Entry<String,JSONObject> existing : liveSelected.entrySet()) {
            JSONObject oldMeta = existing.getValue();
            String table = oldMeta.getString("table");
            if ("budget_history".equals(table) || restoredUuids.contains(existing.getKey()))
                continue;
            // Bez tombstone inne urządzenie przy następnej synchronizacji
            // wskrzesiłoby wpis usunięty przy odtwarzaniu modułu.
            JSONObject meta = new JSONObject(oldMeta.toString());
            if (meta.isNull("deletedAt")) {
                long revision = meta.getLong("revision");
                if (revision == Long.MAX_VALUE)
                    throw new IllegalArgumentException(
                        "Przekroczony licznik synchronizacji.");
                meta.put("revision", revision + 1);
                meta.put("updatedAt", now);
                meta.put("deletedAt", now);
                meta.put("rowHash", "DELETED");
            }
            if (!uuids.add(existing.getKey()))
                throw new IllegalArgumentException("Powielona tożsamość usunięcia.");
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

    /** Historia Budżetu jest nieusuwalna: zachowuj wszystkie poprawne zdarzenia. */
    private static JSONArray mergeBudgetHistory(JSONArray installed, JSONArray archived)
            throws Exception {
        LinkedHashMap<String,JSONObject> rows = new LinkedHashMap<>();
        for (JSONArray source : new JSONArray[]{installed, archived}) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject row = source.getJSONObject(i);
                String key = row.getString("entity_key");
                JSONObject existing = rows.get(key);
                if (existing != null) {
                    if (existing.getLong("id") != row.getLong("id")
                            || !PaycheckBudgetSqliteStore.sameJson(
                                new JSONObject(existing.getString("payload")),
                                new JSONObject(row.getString("payload"))))
                        throw new IllegalArgumentException(
                            "Historia Budżetu zawiera sprzeczne wersje zdarzenia.");
                } else rows.put(key, row);
            }
        }
        JSONArray merged = new JSONArray();
        for (JSONObject row : rows.values()) merged.put(row);
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
