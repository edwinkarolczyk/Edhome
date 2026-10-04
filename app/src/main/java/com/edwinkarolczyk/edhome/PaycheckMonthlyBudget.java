package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Monthly PayCheck plan.
 *
 * A plan is never a ledger entry. Only confirmed PayCheck transactions are
 * counted as actual money. Pending bank evidence stays outside the real saldo
 * until the user verifies it.
 */
final class PaycheckMonthlyBudget {
    static final String PREF_KEY = "paycheck_monthly_budget_v1";
    static final int MAX_ITEMS = 200;
    private static final int MAX_MATCHES_PER_ITEM = 2000;
    private static final int[] ALLOWED_CYCLES = {0, 1, 2, 3, 6, 12};

    static final class Item {
        String id;
        String name;
        String kind;
        String category;
        long amountGrosz;
        String amountMode;
        String startMonth;
        String endMonth;
        int cycleMonths;
        boolean active;
        final List<String> matchedOperationIds = new ArrayList<>();
    }

    static final class Totals {
        long income;
        long expense;

        long net() {
            return income - expense;
        }

        boolean empty() {
            return income == 0L && expense == 0L;
        }
    }

    private PaycheckMonthlyBudget() { }

    static Item newItem(String name, String kind, String category,
            long amountGrosz, String amountMode, String startMonth,
            String endMonth, int cycleMonths) {
        Item item = new Item();
        item.id = UUID.randomUUID().toString();
        item.name = name == null ? "" : name.trim();
        item.kind = kind;
        item.category = category;
        item.amountGrosz = amountGrosz;
        item.amountMode = amountMode;
        item.startMonth = startMonth;
        item.endMonth = endMonth == null ? "" : endMonth.trim();
        item.cycleMonths = cycleMonths;
        item.active = true;
        validate(item);
        return item;
    }

    static List<Item> load(SharedPreferences prefs) throws Exception {
        return parseSerialized(prefs.getString(PREF_KEY, "[]"));
    }

    static void add(SharedPreferences prefs, Item item) throws Exception {
        validate(item);
        List<Item> items = load(prefs);
        if (items.size() >= MAX_ITEMS)
            throw new IllegalArgumentException("Plan ma już maksymalną liczbę pozycji.");
        for (Item existing : items)
            if (existing.id.equals(item.id))
                throw new IllegalArgumentException("Ta pozycja planu już istnieje.");
        items.add(item);
        save(prefs, items);
    }

    static boolean delete(SharedPreferences prefs, String id) throws Exception {
        List<Item> items = load(prefs);
        boolean removed = items.removeIf(item -> item.id.equals(id));
        if (removed) save(prefs, items);
        return removed;
    }

    static boolean match(SharedPreferences prefs, String itemId,
            String operationId) throws Exception {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Nieprawidłowa transakcja.");
        List<Item> items = load(prefs);
        Item target = null;
        boolean changed = false;
        for (Item item : items) {
            if (item.id.equals(itemId)) target = item;
            if (item.matchedOperationIds.remove(operationId)) changed = true;
        }
        if (target == null)
            throw new IllegalArgumentException("Pozycja budżetu już nie istnieje.");
        if (!target.matchedOperationIds.contains(operationId)) {
            if (target.matchedOperationIds.size() >= MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Za dużo realizacji tej pozycji budżetu.");
            target.matchedOperationIds.add(operationId);
            changed = true;
        }
        if (changed) save(prefs, items);
        return changed;
    }

    static boolean unmatch(SharedPreferences prefs, String operationId)
            throws Exception {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}"))
            return false;
        List<Item> items = load(prefs);
        boolean changed = false;
        for (Item item : items)
            if (item.matchedOperationIds.remove(operationId)) changed = true;
        if (changed) save(prefs, items);
        return changed;
    }

    static String serialized(SharedPreferences prefs) throws Exception {
        return serialize(load(prefs));
    }

    static void restoreSerialized(SharedPreferences.Editor editor, String json)
            throws Exception {
        List<Item> parsed = parseSerialized(json);
        editor.putString(PREF_KEY, serialize(parsed));
    }

    static void validateSerialized(String json) throws Exception {
        parseSerialized(json);
    }

    static List<Item> activeFor(List<Item> items, YearMonth month) {
        List<Item> result = new ArrayList<>();
        for (Item item : items)
            if (item.active && occurs(item, month)) result.add(item);
        return result;
    }

    static Totals planned(List<Item> items, YearMonth month) {
        Totals totals = new Totals();
        for (Item item : activeFor(items, month)) {
            if ("income".equals(item.kind)) totals.income += item.amountGrosz;
            else totals.expense += item.amountGrosz;
        }
        return totals;
    }

    /**
     * Suggest only a strong, unique match. The caller still asks the user.
     * Fixed items need a very similar amount; estimates allow a wider range.
     */
    static Item suggest(List<Item> items, YearMonth month, String kind,
            String category, long amountGrosz) {
        Item best = null;
        int bestScore = -1;
        int secondScore = -1;
        for (Item item : activeFor(items, month)) {
            if (!kind.equals(item.kind)) continue;
            long diff = Math.abs(item.amountGrosz - amountGrosz);
            long tolerance = "estimate".equals(item.amountMode)
                ? Math.max(500L, item.amountGrosz * 35L / 100L)
                : Math.max(100L, item.amountGrosz * 5L / 100L);
            if (diff > tolerance) continue;

            int score = 0;
            if (diff == 0L) score += 100;
            else if ("estimate".equals(item.amountMode)) score += 55;
            else score += 75;
            if (item.category.equals(category)) score += 35;
            if (item.cycleMonths > 0) score += 10;

            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = item;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best == null || bestScore < 70) return null;
        if (secondScore >= bestScore - 10) return null;
        return best;
    }

    static Totals sharedActual(SQLiteDatabase db, YearMonth month, String status) {
        Totals totals = new Totals();
        try (Cursor cursor = db.rawQuery(
                "SELECT kind,amount_grosz,created_at,statement_date "
                + "FROM paycheck_transactions WHERE scope='shared' AND status=?",
                new String[]{status})) {
            while (cursor.moveToNext()) {
                YearMonth rowMonth = transactionMonth(
                    cursor.getLong(2), cursor.isNull(3) ? null : cursor.getString(3));
                if (!month.equals(rowMonth)) continue;
                if ("income".equals(cursor.getString(0)))
                    totals.income += cursor.getLong(1);
                else totals.expense += cursor.getLong(1);
            }
        }
        return totals;
    }

    static long sharedMatchedActual(SQLiteDatabase db, Item item, YearMonth month) {
        long total = 0L;
        for (String operationId : item.matchedOperationIds) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT kind,amount_grosz,created_at,statement_date,status "
                    + "FROM paycheck_transactions "
                    + "WHERE scope='shared' AND operation_id=?",
                    new String[]{operationId})) {
                if (!cursor.moveToFirst()
                        || !"confirmed".equals(cursor.getString(4))
                        || !item.kind.equals(cursor.getString(0)))
                    continue;
                YearMonth rowMonth = transactionMonth(
                    cursor.getLong(2), cursor.isNull(3) ? null : cursor.getString(3));
                if (month.equals(rowMonth))
                    total = Math.addExact(total, cursor.getLong(1));
            }
        }
        return total;
    }

    static Totals privateActual(List<PrivatePaycheckVault.Entry> entries,
            YearMonth month, String status) {
        Totals totals = new Totals();
        for (PrivatePaycheckVault.Entry entry : entries) {
            if (!status.equals(entry.status)) continue;
            YearMonth rowMonth = YearMonth.from(
                Instant.ofEpochMilli(entry.createdAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate());
            if (!month.equals(rowMonth)) continue;
            if ("income".equals(entry.kind)) totals.income += entry.amountGrosz;
            else totals.expense += entry.amountGrosz;
        }
        return totals;
    }

    static long privateMatchedActual(List<PrivatePaycheckVault.Entry> entries,
            Item item, YearMonth month) {
        if (item.matchedOperationIds.isEmpty()) return 0L;
        Set<String> wanted = new HashSet<>(item.matchedOperationIds);
        long total = 0L;
        for (PrivatePaycheckVault.Entry entry : entries) {
            if (!wanted.contains(entry.operationId)
                    || !"confirmed".equals(entry.status)
                    || !item.kind.equals(entry.kind))
                continue;
            YearMonth rowMonth = YearMonth.from(
                Instant.ofEpochMilli(entry.createdAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate());
            if (month.equals(rowMonth))
                total = Math.addExact(total, entry.amountGrosz);
        }
        return total;
    }

    static String itemLabel(Item item) {
        String mode = "estimate".equals(item.amountMode) ? " • zmienna" : "";
        String cycle;
        switch (item.cycleMonths) {
            case 0: cycle = "jednorazowo"; break;
            case 1: cycle = "co miesiąc"; break;
            case 2: cycle = "co 2 mies."; break;
            case 3: cycle = "co kwartał"; break;
            case 6: cycle = "co 6 mies."; break;
            case 12: cycle = "co rok"; break;
            default: cycle = "cykl"; break;
        }
        return ("income".equals(item.kind) ? "+ " : "− ")
            + MoneyRules.format(item.amountGrosz) + " • " + item.name
            + " • " + cycle + mode;
    }

    static JSONObject toJson(Item item) throws Exception {
        validate(item);
        JSONObject json = new JSONObject();
        json.put("id", item.id);
        json.put("name", item.name);
        json.put("kind", item.kind);
        json.put("category", item.category);
        json.put("amountGrosz", item.amountGrosz);
        json.put("amountMode", item.amountMode);
        json.put("startMonth", item.startMonth);
        json.put("endMonth", item.endMonth);
        json.put("cycleMonths", item.cycleMonths);
        json.put("active", item.active);
        JSONArray matches = new JSONArray();
        for (String operationId : item.matchedOperationIds)
            matches.put(operationId);
        json.put("matches", matches);
        return json;
    }

    static Item fromJson(JSONObject json) throws Exception {
        Item item = new Item();
        item.id = json.getString("id");
        item.name = json.getString("name");
        item.kind = json.getString("kind");
        item.category = json.getString("category");
        item.amountGrosz = json.getLong("amountGrosz");
        item.amountMode = json.getString("amountMode");
        item.startMonth = json.getString("startMonth");
        item.endMonth = json.optString("endMonth", "");
        item.cycleMonths = json.getInt("cycleMonths");
        item.active = !json.has("active") || json.getBoolean("active");
        JSONArray matches = json.optJSONArray("matches");
        if (matches != null) {
            if (matches.length() > MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Za dużo realizacji pozycji budżetu.");
            for (int i = 0; i < matches.length(); i++)
                item.matchedOperationIds.add(matches.getString(i));
        }
        validate(item);
        return item;
    }

    private static List<Item> parseSerialized(String json) throws Exception {
        if (json == null || json.length() > 400000 || json.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Nieprawidłowy plan PayCheck.");
        JSONArray array = new JSONArray(json);
        if (array.length() > MAX_ITEMS)
            throw new IllegalArgumentException("Za dużo pozycji w planie PayCheck.");
        List<Item> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> matched = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Item item = fromJson(array.getJSONObject(i));
            if (!ids.add(item.id))
                throw new IllegalArgumentException("Powtórzona pozycja planu PayCheck.");
            for (String operationId : item.matchedOperationIds)
                if (!matched.add(operationId))
                    throw new IllegalArgumentException(
                        "Jedna transakcja nie może realizować dwóch pozycji budżetu.");
            result.add(item);
        }
        return result;
    }

    private static String serialize(List<Item> items) throws Exception {
        JSONArray array = new JSONArray();
        for (Item item : items) array.put(toJson(item));
        return array.toString();
    }

    private static void save(SharedPreferences prefs, List<Item> items)
            throws Exception {
        if (!prefs.edit().putString(PREF_KEY, serialize(items)).commit())
            throw new IllegalStateException("Nie zapisano planu PayCheck.");
    }

    private static void validate(Item item) {
        if (item == null || item.id == null
                || !item.id.matches("[0-9a-fA-F-]{36}")
                || item.name == null || item.name.trim().isEmpty()
                || item.name.length() > 80
                || !("income".equals(item.kind) || "expense".equals(item.kind))
                || !MoneyRules.category(item.category)
                || item.amountGrosz < 1 || item.amountGrosz > MoneyRules.MAX_GROSZ
                || !("fixed".equals(item.amountMode)
                    || "estimate".equals(item.amountMode))
                || !allowedCycle(item.cycleMonths))
            throw new IllegalArgumentException("Nieprawidłowa pozycja planu PayCheck.");
        Set<String> matched = new HashSet<>();
        if (item.matchedOperationIds.size() > MAX_MATCHES_PER_ITEM)
            throw new IllegalArgumentException("Za dużo realizacji pozycji budżetu.");
        for (String operationId : item.matchedOperationIds)
            if (operationId == null
                    || !operationId.matches("[0-9a-fA-F-]{36}")
                    || !matched.add(operationId))
                throw new IllegalArgumentException(
                    "Nieprawidłowe powiązanie transakcji z budżetem.");

        YearMonth start;
        try {
            start = YearMonth.parse(item.startMonth);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Nieprawidłowy miesiąc startu.");
        }
        if (item.endMonth != null && !item.endMonth.isBlank()) {
            try {
                YearMonth end = YearMonth.parse(item.endMonth);
                if (end.isBefore(start))
                    throw new IllegalArgumentException(
                        "Miesiąc końcowy nie może być wcześniejszy od startu.");
            } catch (IllegalArgumentException known) {
                throw known;
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Nieprawidłowy miesiąc końcowy.");
            }
        }
    }

    private static boolean allowedCycle(int cycle) {
        for (int allowed : ALLOWED_CYCLES) if (allowed == cycle) return true;
        return false;
    }

    private static boolean occurs(Item item, YearMonth month) {
        YearMonth start = YearMonth.parse(item.startMonth);
        if (month.isBefore(start)) return false;
        if (item.endMonth != null && !item.endMonth.isBlank()
                && month.isAfter(YearMonth.parse(item.endMonth))) return false;
        if (item.cycleMonths == 0) return month.equals(start);
        long offset = ChronoUnit.MONTHS.between(start, month);
        return offset >= 0 && offset % item.cycleMonths == 0;
    }

    static YearMonth transactionMonth(long createdAt, String statementDate) {
        if (statementDate != null && !statementDate.isBlank()) {
            try {
                return YearMonth.from(LocalDate.parse(statementDate));
            } catch (Exception ignored) { }
        }
        return YearMonth.from(Instant.ofEpochMilli(createdAt)
            .atZone(ZoneId.systemDefault()).toLocalDate());
    }
}
