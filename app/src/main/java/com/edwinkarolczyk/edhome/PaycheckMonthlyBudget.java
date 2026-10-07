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
import java.util.Map;
import java.util.LinkedHashMap;
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
        int dueDay;
        boolean optional;
        boolean installment;
        int installmentCount;
        boolean active;
        final List<String> matchedOperationIds = new ArrayList<>();
        final Map<String, Long> matchedAllocationsGrosz = new LinkedHashMap<>();
        final Set<String> skippedMonths = new HashSet<>();
        final Set<String> carriedMonths = new HashSet<>();
        final Map<String, Long> monthAmountOverrides = new LinkedHashMap<>();
        final Map<String, Long> amountChanges = new LinkedHashMap<>();
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
        return newItem(name, kind, category, amountGrosz, amountMode,
            startMonth, endMonth, cycleMonths, 0, false, false, 0);
    }

    static Item newItem(String name, String kind, String category,
            long amountGrosz, String amountMode, String startMonth,
            String endMonth, int cycleMonths, int dueDay, boolean optional,
            boolean installment, int installmentCount) {
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
        item.dueDay = dueDay;
        item.optional = optional;
        item.installment = installment;
        item.installmentCount = installmentCount;
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
            if (item.matchedAllocationsGrosz.remove(operationId) != null)
                changed = true;
        }
        if (target == null)
            throw new IllegalArgumentException("Pozycja budżetu już nie istnieje.");
        if (!target.matchedOperationIds.contains(operationId)) {
            if (target.matchedOperationIds.size() >= MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Za dużo realizacji tej pozycji budżetu.");
            target.matchedOperationIds.add(operationId);
            target.matchedAllocationsGrosz.remove(operationId);
            changed = true;
        }
        if (changed) save(prefs, items);
        return changed;
    }

    static boolean allocateMatch(SharedPreferences prefs, String itemId,
            String operationId, long allocationGrosz) throws Exception {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}")
                || allocationGrosz < 1 || allocationGrosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowy podział transakcji.");
        List<Item> items = load(prefs);
        Item target = null;
        for (Item item : items)
            if (item.id.equals(itemId)) {
                target = item;
                break;
            }
        if (target == null)
            throw new IllegalArgumentException("Pozycja budżetu już nie istnieje.");
        if (!target.matchedOperationIds.contains(operationId)) {
            if (target.matchedOperationIds.size() >= MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Za dużo realizacji tej pozycji budżetu.");
            target.matchedOperationIds.add(operationId);
        }
        target.matchedAllocationsGrosz.put(operationId, allocationGrosz);
        save(prefs, items);
        return true;
    }

    static long allocatedForOperation(List<Item> items, String operationId) {
        long total = 0L;
        for (Item item : items) {
            Long value = item.matchedAllocationsGrosz.get(operationId);
            if (value != null) total = Math.addExact(total, value);
        }
        return total;
    }

    static boolean unmatch(SharedPreferences prefs, String operationId)
            throws Exception {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}"))
            return false;
        List<Item> items = load(prefs);
        boolean changed = false;
        for (Item item : items) {
            if (item.matchedOperationIds.remove(operationId)) changed = true;
            if (item.matchedAllocationsGrosz.remove(operationId) != null)
                changed = true;
        }
        if (changed) save(prefs, items);
        return changed;
    }

    static boolean moveOptionalToNextMonth(SharedPreferences prefs,
            String itemId, YearMonth month) throws Exception {
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        if (!target.optional)
            throw new IllegalArgumentException("Tylko wydatek opcjonalny można przenieść.");
        if (!occurs(target, month))
            throw new IllegalArgumentException("Ta pozycja nie występuje w wybranym miesiącu.");
        String source = month.toString();
        String destination = month.plusMonths(1).toString();
        target.skippedMonths.add(source);
        target.carriedMonths.add(destination);
        save(prefs, items);
        return true;
    }

    static boolean closeOptionalForMonth(SharedPreferences prefs,
            String itemId, YearMonth month) throws Exception {
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        if (!target.optional)
            throw new IllegalArgumentException("Tylko wydatek opcjonalny można zamknąć bez realizacji.");
        if (!occurs(target, month))
            throw new IllegalArgumentException("Ta pozycja nie występuje w wybranym miesiącu.");
        target.skippedMonths.add(month.toString());
        save(prefs, items);
        return true;
    }

    static void changeAmountForMonth(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz) throws Exception {
        validateAmount(amountGrosz);
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        target.monthAmountOverrides.put(month.toString(), amountGrosz);
        save(prefs, items);
    }

    static void changeAmountFromMonth(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz) throws Exception {
        validateAmount(amountGrosz);
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        target.amountChanges.put(month.toString(), amountGrosz);
        save(prefs, items);
    }

    static long plannedAmount(Item item, YearMonth month) {
        Long oneMonth = item.monthAmountOverrides.get(month.toString());
        if (oneMonth != null) return oneMonth;
        long amount = item.amountGrosz;
        YearMonth best = null;
        for (Map.Entry<String, Long> change : item.amountChanges.entrySet()) {
            try {
                YearMonth effective = YearMonth.parse(change.getKey());
                if (!effective.isAfter(month)
                        && (best == null || effective.isAfter(best))) {
                    best = effective;
                    amount = change.getValue();
                }
            } catch (Exception ignored) { }
        }
        return amount;
    }

    static int installmentPosition(Item item, YearMonth month) {
        if (!item.installment || !occurs(item, month)) return 0;
        YearMonth start = YearMonth.parse(item.startMonth);
        return (int) ChronoUnit.MONTHS.between(start, month) + 1;
    }

    static int installmentTotal(Item item) {
        if (!item.installment) return 0;
        if (item.installmentCount > 0) return item.installmentCount;
        if (item.endMonth != null && !item.endMonth.isBlank()) {
            YearMonth start = YearMonth.parse(item.startMonth);
            YearMonth end = YearMonth.parse(item.endMonth);
            return (int) ChronoUnit.MONTHS.between(start, end) + 1;
        }
        return 0;
    }

    private static Item find(List<Item> items, String itemId) {
        for (Item item : items)
            if (item.id.equals(itemId)) return item;
        throw new IllegalArgumentException("Pozycja budżetu już nie istnieje.");
    }

    private static void validateAmount(long amountGrosz) {
        if (amountGrosz < 1 || amountGrosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowa kwota.");
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
            long amount = plannedAmount(item, month);
            if ("income".equals(item.kind)) totals.income += amount;
            else totals.expense += amount;
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
            long planned = plannedAmount(item, month);
            long diff = Math.abs(planned - amountGrosz);
            long tolerance = "estimate".equals(item.amountMode)
                ? Math.max(500L, planned * 35L / 100L)
                : Math.max(100L, planned * 5L / 100L);
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
                if (month.equals(rowMonth)) {
                    Long allocated = item.matchedAllocationsGrosz.get(operationId);
                    total = Math.addExact(total,
                        allocated == null ? cursor.getLong(1) : allocated);
                }
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
            if (month.equals(rowMonth)) {
                Long allocated = item.matchedAllocationsGrosz.get(entry.operationId);
                total = Math.addExact(total,
                    allocated == null ? entry.amountGrosz : allocated);
            }
        }
        return total;
    }

    static String itemLabel(Item item) {
        return itemLabel(item, YearMonth.parse(item.startMonth));
    }

    static String itemLabel(Item item, YearMonth month) {
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
        if (item.installment) {
            int position = installmentPosition(item, month);
            int total = installmentTotal(item);
            cycle = position > 0 && total > 0
                ? "rata " + position + "/" + total
                : "rata";
        }
        String optional = item.optional ? " • opcjonalny" : "";
        return ("income".equals(item.kind) ? "+ " : "− ")
            + MoneyRules.format(plannedAmount(item, month)) + " • " + item.name
            + " • " + cycle + mode + optional;
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
        json.put("dueDay", item.dueDay);
        json.put("optional", item.optional);
        json.put("installment", item.installment);
        json.put("installmentCount", item.installmentCount);
        json.put("active", item.active);
        JSONArray matches = new JSONArray();
        for (String operationId : item.matchedOperationIds)
            matches.put(operationId);
        json.put("matches", matches);
        JSONObject allocations = new JSONObject();
        for (Map.Entry<String, Long> entry : item.matchedAllocationsGrosz.entrySet())
            allocations.put(entry.getKey(), entry.getValue());
        json.put("allocations", allocations);
        json.put("skippedMonths", strings(item.skippedMonths));
        json.put("carriedMonths", strings(item.carriedMonths));
        json.put("monthAmountOverrides", amounts(item.monthAmountOverrides));
        json.put("amountChanges", amounts(item.amountChanges));
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
        item.dueDay = json.optInt("dueDay", 0);
        item.optional = json.optBoolean("optional", false);
        item.installment = json.optBoolean("installment", false);
        item.installmentCount = json.optInt("installmentCount", 0);
        item.active = !json.has("active") || json.getBoolean("active");
        JSONArray matches = json.optJSONArray("matches");
        if (matches != null) {
            if (matches.length() > MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Za dużo realizacji pozycji budżetu.");
            for (int i = 0; i < matches.length(); i++)
                item.matchedOperationIds.add(matches.getString(i));
        }
        JSONObject allocations = json.optJSONObject("allocations");
        if (allocations != null) {
            java.util.Iterator<String> keys = allocations.keys();
            while (keys.hasNext()) {
                String operationId = keys.next();
                item.matchedAllocationsGrosz.put(
                    operationId, allocations.getLong(operationId));
            }
        }
        readStrings(json.optJSONArray("skippedMonths"), item.skippedMonths);
        readStrings(json.optJSONArray("carriedMonths"), item.carriedMonths);
        readAmounts(json.optJSONObject("monthAmountOverrides"),
            item.monthAmountOverrides);
        readAmounts(json.optJSONObject("amountChanges"), item.amountChanges);
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
        for (int i = 0; i < array.length(); i++) {
            Item item = fromJson(array.getJSONObject(i));
            if (!ids.add(item.id))
                throw new IllegalArgumentException("Powtórzona pozycja planu PayCheck.");
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
                || !allowedCycle(item.cycleMonths)
                || item.dueDay < 0 || item.dueDay > 31
                || item.installmentCount < 0 || item.installmentCount > 600)
            throw new IllegalArgumentException("Nieprawidłowa pozycja planu PayCheck.");
        if (item.installment) {
            if (!"expense".equals(item.kind) || item.cycleMonths != 1)
                throw new IllegalArgumentException("Rata musi być miesięcznym wydatkiem.");
            boolean hasCount = item.installmentCount > 0;
            boolean hasEnd = item.endMonth != null && !item.endMonth.isBlank();
            if (hasCount == hasEnd)
                throw new IllegalArgumentException(
                    "Dla rat podaj liczbę rat albo miesiąc końca — nie oba.");
        } else if (item.installmentCount != 0) {
            throw new IllegalArgumentException("Liczba rat jest dozwolona tylko dla rat.");
        }
        Set<String> matched = new HashSet<>();
        if (item.matchedOperationIds.size() > MAX_MATCHES_PER_ITEM)
            throw new IllegalArgumentException("Za dużo realizacji pozycji budżetu.");
        for (String operationId : item.matchedOperationIds)
            if (operationId == null
                    || !operationId.matches("[0-9a-fA-F-]{36}")
                    || !matched.add(operationId))
                throw new IllegalArgumentException(
                    "Nieprawidłowe powiązanie transakcji z budżetem.");
        for (Map.Entry<String, Long> allocation
                : item.matchedAllocationsGrosz.entrySet()) {
            if (!matched.contains(allocation.getKey())
                    || allocation.getValue() == null
                    || allocation.getValue() < 1
                    || allocation.getValue() > MoneyRules.MAX_GROSZ)
                throw new IllegalArgumentException(
                    "Nieprawidłowy podział transakcji w budżecie.");
        }
        validateMonthSet(item.skippedMonths);
        validateMonthSet(item.carriedMonths);
        validateAmountMap(item.monthAmountOverrides);
        validateAmountMap(item.amountChanges);

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
        String key = month.toString();
        if (item.skippedMonths.contains(key)) return false;
        if (item.carriedMonths.contains(key)) return true;
        YearMonth start = YearMonth.parse(item.startMonth);
        if (month.isBefore(start)) return false;
        if (item.endMonth != null && !item.endMonth.isBlank()
                && month.isAfter(YearMonth.parse(item.endMonth))) return false;
        if (item.cycleMonths == 0) return month.equals(start);
        long offset = ChronoUnit.MONTHS.between(start, month);
        if (offset < 0 || offset % item.cycleMonths != 0) return false;
        if (item.installment && item.installmentCount > 0
                && offset / item.cycleMonths >= item.installmentCount)
            return false;
        return true;
    }

    private static JSONArray strings(Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    private static JSONObject amounts(Map<String, Long> values) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Long> entry : values.entrySet())
            json.put(entry.getKey(), entry.getValue());
        return json;
    }

    private static void readStrings(JSONArray array, Set<String> target) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++)
            target.add(array.optString(i, ""));
    }

    private static void readAmounts(JSONObject json, Map<String, Long> target) {
        if (json == null) return;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key, json.optLong(key, -1L));
        }
    }

    private static void validateMonthSet(Set<String> months) {
        if (months.size() > 600)
            throw new IllegalArgumentException("Za dużo wyjątków miesięcznych.");
        for (String month : months) {
            try { YearMonth.parse(month); }
            catch (Exception invalid) {
                throw new IllegalArgumentException("Nieprawidłowy wyjątek miesiąca.");
            }
        }
    }

    private static void validateAmountMap(Map<String, Long> amounts) {
        if (amounts.size() > 600)
            throw new IllegalArgumentException("Za dużo zmian kwoty.");
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            try { YearMonth.parse(entry.getKey()); }
            catch (Exception invalid) {
                throw new IllegalArgumentException("Nieprawidłowy miesiąc zmiany kwoty.");
            }
            validateAmount(entry.getValue() == null ? -1L : entry.getValue());
        }
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
