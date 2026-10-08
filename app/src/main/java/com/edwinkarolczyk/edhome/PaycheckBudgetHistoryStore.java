package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only history for Budżet miesiąca.
 *
 * This log is intentionally separate from the current plan so deleting or
 * ending a definition never removes evidence needed for later statistics.
 */
final class PaycheckBudgetHistoryStore {
    static final String PREF_KEY = "paycheck_budget_history_v1";
    static final int MAX_EVENTS = 6000;

    static final class Event {
        String id;
        String itemId;
        String recipientId;
        String month;
        String type;
        long amountGrosz;
        String note;
        String operationId = "";
        String transactionDate = "";
        long createdAt;
    }

    private PaycheckBudgetHistoryStore() { }

    static void append(SharedPreferences prefs, PaycheckMonthlyBudget.Item item,
            YearMonth month, String type, long amountGrosz, String note)
            throws Exception {
        if (prefs == null || item == null || type == null || type.isBlank())
            throw new IllegalArgumentException("Nieprawidłowe zdarzenie budżetu.");
        Event event = make(item,month,type,amountGrosz,note,"",null);
        SharedPreferences.Editor editor = prefs.edit();
        stage(prefs,editor,java.util.Collections.singletonList(event));
        if (!editor.commit())
            throw new IllegalStateException("Nie zapisano historii Budżetu miesiąca.");
    }

    /** Operacja bankowa i data księgowania są jawne; data potwierdzenia jest osobna. */
    static Event make(PaycheckMonthlyBudget.Item item, YearMonth month,
            String type, long amountGrosz, String note, String operationId,
            LocalDate transactionDate) {
        if (item == null || month == null)
            throw new IllegalArgumentException("Brak pozycji lub miesiąca historii.");
        Event event = new Event();
        event.id = UUID.randomUUID().toString();
        event.itemId = item.id;
        event.recipientId = item.recipientId == null ? "" : item.recipientId;
        event.month = month.toString();
        event.type = type.trim();
        event.amountGrosz = amountGrosz;
        event.note = note == null ? "" : note.trim();
        event.operationId = operationId == null ? "" : operationId;
        event.transactionDate = transactionDate == null
            ? "" : transactionDate.toString();
        event.createdAt = System.currentTimeMillis();
        validate(event);
        return event;
    }

    /** Służy do atomowego zapisania planu razem z całą paczką zdarzeń. */
    static void stage(SharedPreferences prefs, SharedPreferences.Editor editor,
            List<Event> additions) throws Exception {
        if (additions == null || additions.isEmpty()) return;
        List<Event> events = load(prefs);
        for (Event event : additions) {
            validate(event);
            events.add(event);
        }
        // Docelowa migracja dziennika bez limitu do SQLite: etap 5.
        while (events.size() > MAX_EVENTS) events.remove(0);
        editor.putString(PREF_KEY,serialize(events));
    }

    static List<Event> load(SharedPreferences prefs) throws Exception {
        return parse(prefs.getString(PREF_KEY,"[]"));
    }

    static String serialized(SharedPreferences prefs) throws Exception {
        return serialize(load(prefs));
    }

    static void restoreSerialized(SharedPreferences.Editor editor, String raw)
            throws Exception {
        editor.putString(PREF_KEY,serialize(parse(raw)));
    }

    static void validateSerialized(String raw) throws Exception {
        parse(raw);
    }

    private static List<Event> parse(String raw) throws Exception {
        if (raw == null || raw.length() > 2_000_000 || raw.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Nieprawidłowa historia budżetu.");
        JSONArray array = new JSONArray(raw);
        if (array.length() > MAX_EVENTS)
            throw new IllegalArgumentException("Za dużo zdarzeń budżetu.");
        List<Event> result = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i=0;i<array.length();i++) {
            JSONObject json = array.getJSONObject(i);
            Event event = new Event();
            event.id = json.getString("id");
            event.itemId = json.getString("itemId");
            event.recipientId = json.optString("recipientId","");
            event.month = json.getString("month");
            event.type = json.getString("type");
            event.amountGrosz = json.optLong("amountGrosz",0L);
            event.note = json.optString("note","");
            event.operationId = json.optString("operationId","");
            event.transactionDate = json.optString("transactionDate","");
            event.createdAt = json.getLong("createdAt");
            validate(event);
            if (!ids.add(event.id))
                throw new IllegalArgumentException("Powtórzone zdarzenie budżetu.");
            result.add(event);
        }
        return result;
    }

    private static String serialize(List<Event> events) throws Exception {
        JSONArray array = new JSONArray();
        for (Event event : events) {
            validate(event);
            JSONObject json = new JSONObject();
            json.put("id",event.id);
            json.put("itemId",event.itemId);
            json.put("recipientId",event.recipientId);
            json.put("month",event.month);
            json.put("type",event.type);
            json.put("amountGrosz",event.amountGrosz);
            json.put("note",event.note);
            json.put("operationId",event.operationId);
            json.put("transactionDate",event.transactionDate);
            json.put("createdAt",event.createdAt);
            array.put(json);
        }
        return array.toString();
    }

    private static void validate(Event event) {
        if (event == null
                || event.id == null || !event.id.matches("[0-9a-fA-F-]{36}")
                || event.itemId == null || !event.itemId.matches("[0-9a-fA-F-]{36}")
                || event.recipientId == null
                || (!event.recipientId.isBlank()
                    && !event.recipientId.matches("[0-9a-fA-F-]{36}"))
                || event.type == null || !event.type.matches("[A-Z0-9_]{2,48}")
                || event.note == null || event.note.length() > 240
                || event.operationId == null
                || (!event.operationId.isBlank()
                    && !event.operationId.matches("[0-9a-fA-F-]{36}"))
                || event.transactionDate == null
                || event.createdAt <= 0L) {
            throw new IllegalArgumentException("Nieprawidłowe zdarzenie budżetu.");
        }
        try {
            YearMonth.parse(event.month);
            if (!event.transactionDate.isBlank()
                    && !YearMonth.from(LocalDate.parse(event.transactionDate))
                        .equals(YearMonth.parse(event.month)))
                throw new IllegalArgumentException("Niespójny miesiąc operacji.");
        }
        catch (Exception invalid) {
            throw new IllegalArgumentException("Nieprawidłowy miesiąc historii budżetu.");
        }
    }
}
