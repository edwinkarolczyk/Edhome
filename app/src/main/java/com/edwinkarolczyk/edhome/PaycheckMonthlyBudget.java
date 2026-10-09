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
        String creditAgreementNumber;
        String recipientId;
        boolean active;
        String inactiveFromMonth;
        final List<String> matchedOperationIds = new ArrayList<>();
        final Map<String, Long> matchedAllocationsGrosz = new LinkedHashMap<>();
        // Reszta z przelewu grupowego: osobna nadpłata (nie część rachunku).
        final Map<String, Long> splitSurplusesGrosz = new LinkedHashMap<>();
        final Set<String> skippedMonths = new HashSet<>();
        final Set<String> carriedMonths = new HashSet<>();
        final Map<String, Long> monthAmountOverrides = new LinkedHashMap<>();
        final Map<String, Long> amountChanges = new LinkedHashMap<>();
        final Map<String, Long> balanceAdjustmentsGrosz = new LinkedHashMap<>();
        final Map<String, String> adjustmentReasons = new LinkedHashMap<>();
        final Map<String, Long> adjustmentCreatedAt = new LinkedHashMap<>();
        final Set<String> closedMonths = new HashSet<>();
        final Map<String, String> closedMonthReasons = new LinkedHashMap<>();
        final Map<String, Long> closedMonthCreatedAt = new LinkedHashMap<>();
        final Map<String, Long> creditApplicationsGrosz = new LinkedHashMap<>();
        final Map<String, Long> creditApplicationCreatedAt = new LinkedHashMap<>();
        final Map<String, String> invoiceDueDates = new LinkedHashMap<>();
        final Map<String, String> plannedPaymentDates = new LinkedHashMap<>();
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

    static final class Arrear {
        final YearMonth sourceMonth;
        final long amountGrosz;

        Arrear(YearMonth sourceMonth, long amountGrosz) {
            this.sourceMonth = sourceMonth;
            this.amountGrosz = amountGrosz;
        }
    }

    static final class Credit {
        final YearMonth sourceMonth;
        final long amountGrosz;

        Credit(YearMonth sourceMonth, long amountGrosz) {
            this.sourceMonth = sourceMonth;
            this.amountGrosz = amountGrosz;
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
        return newItem(name, kind, category, amountGrosz, amountMode,
            startMonth, endMonth, cycleMonths, dueDay, optional,
            installment, installmentCount, "");
    }

    static Item newItem(String name, String kind, String category,
            long amountGrosz, String amountMode, String startMonth,
            String endMonth, int cycleMonths, int dueDay, boolean optional,
            boolean installment, int installmentCount,
            String creditAgreementNumber) {
        return newItem(name,kind,category,amountGrosz,amountMode,startMonth,
            endMonth,cycleMonths,dueDay,optional,installment,installmentCount,
            creditAgreementNumber,"");
    }

    static Item newItem(String name, String kind, String category,
            long amountGrosz, String amountMode, String startMonth,
            String endMonth, int cycleMonths, int dueDay, boolean optional,
            boolean installment, int installmentCount,
            String creditAgreementNumber, String recipientId) {
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
        item.dueDay = PaycheckBudgetInvoiceDates.templateDay(dueDay);
        item.optional = optional;
        item.installment = installment;
        item.installmentCount = installmentCount;
        item.creditAgreementNumber = creditAgreementNumber == null
            ? "" : creditAgreementNumber.trim();
        item.recipientId = recipientId == null ? "" : recipientId.trim();
        item.active = true;
        item.inactiveFromMonth = "";
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
        PaycheckBudgetHistoryStore.append(prefs,item,
            YearMonth.parse(item.startMonth),"ITEM_CREATED",item.amountGrosz,
            item.name);
    }

    static boolean delete(SharedPreferences prefs, String id) throws Exception {
        return deleteFromMonth(prefs,id,YearMonth.now());
    }

    /** Zakończenie definicji od miesiąca wskazanego przez użytkownika. */
    static boolean deleteFromMonth(SharedPreferences prefs, String id,
            YearMonth from) throws Exception {
        if (from == null) throw new IllegalArgumentException("Wybierz miesiąc.");
        List<Item> items = load(prefs);
        Item target = find(items,id);
        if (from.isBefore(YearMonth.parse(target.startMonth)))
            throw new IllegalArgumentException(
                "Nie można zakończyć definicji przed jej początkiem.");
        if (!target.active && target.inactiveFromMonth != null
                && !target.inactiveFromMonth.isBlank()
                && !from.isBefore(YearMonth.parse(target.inactiveFromMonth)))
            return false;
        target.active = false;
        target.inactiveFromMonth = from.toString();
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,from,
            "ITEM_DEACTIVATED",plannedAmount(target,from),
            "Zakończono definicję od " + from
                + ". Transakcje PayCheck i wcześniejsze miesiące zachowane.");
        return true;
    }

    /** Dane oryginalnej operacji, pobrane przed ewentualnym usunięciem. */
    static final class SharedTransaction {
        final String operationId;
        final String kind;
        final long amountGrosz;
        final YearMonth month;
        final LocalDate date;
        final boolean confirmed;
        SharedTransaction(String operationId, String kind, long amountGrosz,
                LocalDate date, boolean confirmed) {
            this.operationId=operationId;
            this.kind=kind;
            this.amountGrosz=amountGrosz;
            this.date=date;
            this.month=YearMonth.from(date);
            this.confirmed=confirmed;
        }
    }

    static SharedTransaction sharedTransaction(SQLiteDatabase db,String operationId) {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Nieprawidłowy identyfikator przelewu.");
        try (Cursor c = db.rawQuery(
                "SELECT kind,amount_grosz,created_at,statement_date,status "
                + "FROM paycheck_transactions WHERE scope='shared' AND operation_id=?",
                new String[]{operationId})) {
            if (!c.moveToFirst())
                throw new IllegalArgumentException("Nie znaleziono operacji bankowej.");
            long timestamp = c.getLong(2);
            String statement = c.isNull(3) ? null : c.getString(3);
            LocalDate date=PaycheckBudgetHistoryRules.bankDate(
                timestamp,statement,ZoneId.systemDefault());
            return new SharedTransaction(operationId,c.getString(0),c.getLong(1),
                date,"confirmed".equals(c.getString(4)));
        }
    }

    /** Atomowy zapis obu kluczy SharedPreferences dla każdego potwierdzenia. */
    private static void saveWithEvents(SharedPreferences prefs, List<Item> items,
            List<PaycheckBudgetHistoryStore.Event> events) throws Exception {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_KEY,serialize(items));
        PaycheckBudgetHistoryStore.stage(prefs,editor,events);
        if (!editor.commit())
            throw new IllegalStateException("Nie zapisano planu i historii płatności.");
    }

    private static void addPaymentEvents(
            List<PaycheckBudgetHistoryStore.Event> events, Item item,
            SharedTransaction tx, long paymentGrosz, long dueBefore,
            String auditType) {
        events.add(PaycheckBudgetHistoryStore.make(item,tx.month,auditType,
            paymentGrosz,"Powiązanie z operacją bankową",tx.operationId,tx.date));
        events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
            "PAYMENT_CONFIRMED",paymentGrosz,"Potwierdzona płatność",
            tx.operationId,tx.date));
        if ("expense".equals(item.kind)) {
            String differenceType=PaycheckBudgetHistoryRules.differenceType(
                paymentGrosz,dueBefore);
            if (!differenceType.isBlank())
                events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                    differenceType,
                    PaycheckBudgetHistoryRules.differenceGrosz(paymentGrosz,dueBefore),
                    "Różnica względem kwoty pozostałej",
                    tx.operationId,tx.date));
        }
    }

    static boolean match(SharedPreferences prefs, SQLiteDatabase db,
            String itemId, String operationId) throws Exception {
        SharedTransaction tx=sharedTransaction(db,operationId);
        if (!tx.confirmed)
            throw new IllegalArgumentException("Można przypisywać tylko potwierdzone płatności.");
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        if (!target.kind.equals(tx.kind))
            throw new IllegalArgumentException("Niezgodny typ operacji bankowej.");
        boolean existing=false;
        for (Item item:items)
            if (item.matchedOperationIds.contains(operationId)) {
                if (item.id.equals(itemId)
                        && item.matchedAllocationsGrosz.get(operationId)==null
                        && item.splitSurplusesGrosz.get(operationId)==null)
                    existing=true;
                else
                    throw new IllegalArgumentException(
                        "Operacja jest już rozdzielona. Najpierw odłącz ją od budżetu.");
            }
        if (existing) return false; // ponowny klik nie tworzy zdarzenia ani wpłaty
        if (target.matchedOperationIds.size() >= MAX_MATCHES_PER_ITEM)
            throw new IllegalArgumentException("Za dużo realizacji pozycji budżetu.");
        long dueBefore=remainingDue(db,target,tx.month);
        target.matchedOperationIds.add(operationId);
        List<PaycheckBudgetHistoryStore.Event> events=new ArrayList<>();
        addPaymentEvents(events,target,tx,tx.amountGrosz,dueBefore,"TRANSACTION_MATCHED");
        saveWithEvents(prefs,items,events);
        return true;
    }

    static boolean allocateMatch(SharedPreferences prefs,SQLiteDatabase db,
            String itemId,String operationId,long allocationGrosz) throws Exception {
        SharedTransaction tx=sharedTransaction(db,operationId);
        if (!tx.confirmed || allocationGrosz<1 || allocationGrosz>tx.amountGrosz)
            throw new IllegalArgumentException("Nieprawidłowy podział potwierdzonej transakcji.");
        List<Item> items=load(prefs);
        Item target=find(items,itemId);
        if (!target.kind.equals(tx.kind))
            throw new IllegalArgumentException("Niezgodny typ operacji.");
        if (target.matchedOperationIds.contains(operationId)) {
            if (Long.valueOf(allocationGrosz).equals(
                    target.matchedAllocationsGrosz.get(operationId)))
                return false;
            throw new IllegalArgumentException(
                "Istniejący podział trzeba najpierw odłączyć od budżetu.");
        }
        if (target.matchedOperationIds.size()>=MAX_MATCHES_PER_ITEM)
            throw new IllegalArgumentException("Za dużo realizacji.");
        long allocated=allocatedForOperation(items,operationId,tx.amountGrosz);
        if (Math.addExact(allocated,allocationGrosz)>tx.amountGrosz)
            throw new IllegalArgumentException("Podział przekracza wartość przelewu.");
        long dueBefore=remainingDue(db,target,tx.month);
        target.matchedOperationIds.add(operationId);
        target.matchedAllocationsGrosz.put(operationId,allocationGrosz);
        List<PaycheckBudgetHistoryStore.Event> events=new ArrayList<>();
        addPaymentEvents(events,target,tx,allocationGrosz,dueBefore,
            "TRANSACTION_ALLOCATED");
        saveWithEvents(prefs,items,events);
        return true;
    }

    /**
     * Reszta przelewu grupowego nie jest dopisywana do przydziału żadnego
     * rachunku. Staje się nadpłatą przypisaną do jednego odbiorcy/pozycji.
     * Liczymy ją wyłącznie dla nadal potwierdzonej transakcji bankowej.
     */
    static long sharedSplitSurplus(SQLiteDatabase db, Item item, YearMonth month) {
        long total = 0L;
        for (Map.Entry<String,Long> entry : item.splitSurplusesGrosz.entrySet()) {
            if (!item.matchedOperationIds.contains(entry.getKey())) continue;
            try (Cursor cursor = db.rawQuery(
                    "SELECT kind,created_at,statement_date,status "
                    + "FROM paycheck_transactions "
                    + "WHERE scope='shared' AND operation_id=?",
                    new String[]{entry.getKey()})) {
                if (!cursor.moveToFirst() || !"confirmed".equals(cursor.getString(3))
                        || !item.kind.equals(cursor.getString(0))) continue;
                YearMonth actualMonth = transactionMonth(cursor.getLong(1),
                    cursor.isNull(2) ? null : cursor.getString(2));
                if (month.equals(actualMonth))
                    total = Math.addExact(total,entry.getValue());
            }
        }
        return total;
    }

    /** Pozostało do zapłaty po wcześniejszych wpłatach i jawnych odliczeniach. */
    static long remainingDue(SQLiteDatabase db, Item item, YearMonth month) {
        long planned = plannedAmount(item,month);
        long paid = sharedMatchedActual(db,item,month);
        long applied = "expense".equals(item.kind)
            ? creditAppliedTo(item,month) : 0L;
        long closed = item.balanceAdjustmentsGrosz.getOrDefault(month.toString(),0L);
        return PaycheckBudgetSettlementMath.outstanding(
            planned,paid,closed,applied);
    }

    /**
     * Atomowe przypisanie jednego przelewu do wielu rachunków.
     * Rozdziela tylko ich niezapłacone części; dodatnia różnica powstaje
     * jako oddzielna nadpłata, a ujemna pozostaje jawnie w historii.
     */
    static PaycheckBudgetSplitMath.Result allocateSplit(SharedPreferences prefs,
            SQLiteDatabase db, List<String> itemIds, YearMonth month,
            String operationId, long transactionGrosz) throws Exception {
        if (itemIds == null || itemIds.size() < 2 || month == null
                || operationId == null || !operationId.matches("[0-9a-fA-F-]{36}")
                || transactionGrosz < itemIds.size()
                || transactionGrosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowy podział przelewu.");

        SharedTransaction tx=sharedTransaction(db,operationId);
        if (!tx.confirmed || tx.amountGrosz!=transactionGrosz
                || !month.equals(tx.month))
            throw new IllegalArgumentException("Brak potwierdzonego przelewu w tym miesiącu.");
        String kind=tx.kind;

        List<Item> all = load(prefs);
        for (Item item : all)
            if (item.matchedOperationIds.contains(operationId))
                throw new IllegalArgumentException(
                    "Przelew jest już przypisany. Najpierw usuń poprzednie przypisanie.");

        Set<String> unique = new HashSet<>(itemIds);
        if (unique.size() != itemIds.size())
            throw new IllegalArgumentException("Powtórzona pozycja w podziale.");
        List<Item> ordered = new ArrayList<>();
        for (String id : itemIds) {
            Item item = find(all,id);
            if (!kind.equals(item.kind)
                    || !activeFor(java.util.Collections.singletonList(item),month).contains(item)
                    || item.matchedOperationIds.size() >= MAX_MATCHES_PER_ITEM)
                throw new IllegalArgumentException("Pozycja nie kwalifikuje się do podziału.");
            ordered.add(item);
        }
        ordered.sort((a,b)->Long.compare(
            remainingDue(db,a,month),remainingDue(db,b,month)));
        long[] outstanding = new long[ordered.size()];
        for (int i=0;i<ordered.size();i++)
            outstanding[i] = remainingDue(db,ordered.get(i),month);
        PaycheckBudgetSplitMath.Result result =
            PaycheckBudgetSplitMath.calculate(outstanding,transactionGrosz);
        for (int i=0;i<ordered.size();i++) {
            Item item = ordered.get(i);
            item.matchedOperationIds.add(operationId);
            item.matchedAllocationsGrosz.put(operationId,result.allocationsGrosz[i]);
        }
        Item differenceOwner = ordered.get(ordered.size()-1);
        if (result.differenceGrosz > 0L)
            differenceOwner.splitSurplusesGrosz.put(operationId,result.differenceGrosz);
        List<PaycheckBudgetHistoryStore.Event> events=new ArrayList<>();
        for (int i=0;i<ordered.size();i++) {
            Item item=ordered.get(i);
            long allocation=result.allocationsGrosz[i];
            events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                "TRANSACTION_ALLOCATED",allocation,
                "Część przelewu grupowego",tx.operationId,tx.date));
            events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                "PAYMENT_CONFIRMED",allocation,
                "Potwierdzona część przelewu",tx.operationId,tx.date));
        }
        if (result.differenceGrosz != 0L)
            events.add(PaycheckBudgetHistoryStore.make(differenceOwner,tx.month,
                result.differenceGrosz > 0L ? "OVERPAYMENT" : "UNDERPAYMENT",
                Math.abs(result.differenceGrosz),
                "Różnica grupowego przelewu względem pozycji",
                tx.operationId,tx.date));
        saveWithEvents(prefs,all,events);
        return result;
    }

    /**
     * Suma wykorzystania jednego potwierdzonego przelewu. Pełne
     * dopasowanie bez kwoty częściowej oznacza CAŁY przelew; uwzględniamy
     * też nadwyżkę z podziału, żeby nie wydać jej po raz drugi.
     */
    static long allocatedForOperation(List<Item> items, String operationId,
            long paymentGrosz) {
        long total = 0L;
        for (Item item : items)
            total = PaycheckBudgetAllocationGuard.addUsed(
                total,paymentGrosz,
                item.matchedOperationIds.contains(operationId),
                item.matchedAllocationsGrosz.get(operationId),
                item.splitSurplusesGrosz.get(operationId));
        return total;
    }

    static boolean unmatch(SharedPreferences prefs,String operationId,
            SharedTransaction tx) throws Exception {
        if (tx==null || !operationId.equals(tx.operationId))
            throw new IllegalArgumentException(
                "Brak danych przelewu przed odłączeniem z budżetu.");
        List<Item> items=load(prefs);
        List<PaycheckBudgetHistoryStore.Event> events=new ArrayList<>();
        boolean changed=false;
        for (Item item:items) {
            if (!item.matchedOperationIds.contains(operationId)) continue;
            Long allocated=item.matchedAllocationsGrosz.get(operationId);
            Long surplus=item.splitSurplusesGrosz.get(operationId);
            long reversed=allocated==null ? tx.amountGrosz : allocated;
            item.matchedOperationIds.remove(operationId);
            item.matchedAllocationsGrosz.remove(operationId);
            item.splitSurplusesGrosz.remove(operationId);
            changed=true;
            events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                "TRANSACTION_UNMATCHED",reversed,
                "Odłączono operację bankową",tx.operationId,tx.date));
            events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                "PAYMENT_REVERSED",reversed,
                "Cofnięto przypisanie potwierdzonej płatności",
                tx.operationId,tx.date));
            if (surplus!=null && surplus>0)
                events.add(PaycheckBudgetHistoryStore.make(item,tx.month,
                    "OVERPAYMENT_REVERSED",surplus,
                    "Cofnięto nadwyżkę z przelewu grupowego",
                    tx.operationId,tx.date));
        }
        if (changed) saveWithEvents(prefs,items,events);
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
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "OPTIONAL_MOVED",plannedAmount(target,month),
            "Przeniesiono do " + destination);
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
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "OPTIONAL_CLOSED",plannedAmount(target,month),
            "Zamknięto opcjonalny wydatek bez realizacji.");
        return true;
    }

    static void changeAmountForMonth(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz) throws Exception {
        validateAmount(amountGrosz);
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        long previous = plannedAmount(target,month);
        target.monthAmountOverrides.put(month.toString(), amountGrosz);
        save(prefs, items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "AMOUNT_MONTH_CHANGED",amountGrosz,
            "Poprzednio " + previous + " gr.");
    }

    static void changeAmountFromMonth(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz) throws Exception {
        validateAmount(amountGrosz);
        List<Item> items = load(prefs);
        Item target = find(items, itemId);
        long previous = plannedAmount(target,month);
        target.amountChanges.put(month.toString(), amountGrosz);
        save(prefs, items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "AMOUNT_FROM_CHANGED",amountGrosz,
            "Poprzednio " + previous + " gr.");
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

    static YearMonth installmentEndMonth(Item item) {
        int total = installmentTotal(item);
        if (!item.installment || total < 1) return null;
        return YearMonth.parse(item.startMonth).plusMonths(total - 1L);
    }

    static long installmentTotalPlanned(Item item) {
        int total = installmentTotal(item);
        if (!item.installment || total < 1) return 0L;
        YearMonth month = YearMonth.parse(item.startMonth);
        long sum = 0L;
        for (int i=0;i<total;i++)
            sum = Math.addExact(sum,plannedAmount(item,month.plusMonths(i)));
        return sum;
    }

    static long sharedMatchedActualAll(SQLiteDatabase db, Item item) {
        long total = 0L;
        for (String operationId : item.matchedOperationIds) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT kind,amount_grosz,status FROM paycheck_transactions "
                    + "WHERE scope='shared' AND operation_id=?",
                    new String[]{operationId})) {
                if (!cursor.moveToFirst()
                        || !"confirmed".equals(cursor.getString(2))
                        || !item.kind.equals(cursor.getString(0)))
                    continue;
                Long allocated = item.matchedAllocationsGrosz.get(operationId);
                total = Math.addExact(total,
                    allocated == null ? cursor.getLong(1) : allocated);
            }
        }
        return total;
    }

    static LocalDate defaultPlannedPaymentDate(LocalDate invoiceDueDate) {
        if (invoiceDueDate == null)
            throw new IllegalArgumentException("Brak terminu faktury.");
        return PaycheckBudgetInvoiceDates.planned(
            YearMonth.from(invoiceDueDate),10,invoiceDueDate);
    }

    static void setInvoiceForMonth(Item item, LocalDate dueDate,
            LocalDate plannedDate) {
        if (item == null || dueDate == null)
            throw new IllegalArgumentException("Brak terminu faktury.");
        LocalDate plan = plannedDate == null
            ? PaycheckBudgetInvoiceDates.planned(
                YearMonth.from(dueDate),item.dueDay,dueDate) : plannedDate;
        YearMonth month = YearMonth.from(dueDate);
        if (!YearMonth.from(plan).equals(month) || plan.isAfter(dueDate))
            throw new IllegalArgumentException(
                "Planowana zapłata musi być w miesiącu faktury i nie później niż termin.");
        item.invoiceDueDates.put(month.toString(),dueDate.toString());
        item.plannedPaymentDates.put(month.toString(),plan.toString());
    }

    /**
     * Zapis faktury dotyczy wyłącznie wskazanego miesiąca.
     * Nie zmienia dnia zapłaty szablonu ani następnych miesięcy.
     */
    static void updateInvoiceForMonth(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz, LocalDate dueDate,
            LocalDate plannedDate) throws Exception {
        validateAmount(amountGrosz);
        if (month == null || dueDate == null
                || !month.equals(YearMonth.from(dueDate)))
            throw new IllegalArgumentException(
                "Termin faktury musi należeć do wybranego miesiąca.");
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        if (!"expense".equals(target.kind)
                || activeFor(java.util.Collections.singletonList(target),month).isEmpty())
            throw new IllegalArgumentException(
                "Fakturę można dodać tylko do istniejącego wydatku w tym miesiącu.");
        long previousAmount = plannedAmount(target,month);
        LocalDate previousDue = invoiceDueDate(target,month);
        setInvoiceForMonth(target,dueDate,plannedDate);
        target.monthAmountOverrides.put(month.toString(),amountGrosz);
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            previousDue == null ? "INVOICE_ADDED" : "INVOICE_UPDATED",
            amountGrosz,
            "Termin " + dueDate + ", poprzednio "
                + previousAmount + " gr"
                + (previousDue == null ? "" : ", termin " + previousDue));
    }

    static LocalDate invoiceDueDate(Item item, YearMonth month) {
        String raw = item.invoiceDueDates.get(month.toString());
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw); }
        catch (Exception invalid) { return null; }
    }

    static LocalDate plannedPaymentDate(Item item, YearMonth month) {
        String raw = item.plannedPaymentDates.get(month.toString());
        if (raw != null && !raw.isBlank()) {
            try { return LocalDate.parse(raw); }
            catch (Exception ignored) { }
        }
        return PaycheckBudgetInvoiceDates.planned(
            month,item.dueDay,invoiceDueDate(item,month));
    }

    /**
     * Zaległości są liczone osobno per miesiąc źródłowy.
     * Nadpłata nigdy nie kompensuje ich automatycznie.
     */
    static List<Arrear> sharedArrearsBefore(SQLiteDatabase db, Item item,
            YearMonth month) {
        List<Arrear> result = new ArrayList<>();
        if (item.optional || !"expense".equals(item.kind)) return result;
        YearMonth cursor = YearMonth.parse(item.startMonth);
        int guard = 0;
        while (cursor.isBefore(month) && guard++ < 600) {
            if (occurs(item,cursor)) {
                long planned = plannedAmount(item,cursor);
                long actual = sharedMatchedActual(db,item,cursor);
                long closed = item.balanceAdjustmentsGrosz.getOrDefault(
                    cursor.toString(),0L);
                long appliedCredit = creditAppliedTo(item,cursor);
                long missing = PaycheckBudgetSettlementMath.outstanding(
                    planned,actual,closed,appliedCredit);
                if (missing > 0L) result.add(new Arrear(cursor,missing));
            }
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    static long sharedCarryBefore(SQLiteDatabase db, Item item, YearMonth month) {
        long total = 0L;
        for (Arrear arrear : sharedArrearsBefore(db,item,month))
            total = Math.addExact(total,arrear.amountGrosz);
        return total;
    }

    static List<Credit> sharedCreditsBefore(SQLiteDatabase db, Item item,
            YearMonth month) {
        List<Credit> result = new ArrayList<>();
        if (item.optional || !"expense".equals(item.kind)) return result;
        YearMonth cursor = YearMonth.parse(item.startMonth);
        int guard = 0;
        while (cursor.isBefore(month) && guard++ < 600) {
            if (occurs(item,cursor)) {
                long extra = Math.addExact(sharedMatchedActual(db,item,cursor),
                    sharedSplitSurplus(db,item,cursor)) - plannedAmount(item,cursor);
                if (extra > 0L) {
                    long used = creditAppliedFrom(item,cursor);
                    long available = extra - used;
                    if (available > 0L)
                        result.add(new Credit(cursor,available));
                }
            }
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    static long sharedCreditBefore(SQLiteDatabase db, Item item, YearMonth month) {
        long total = 0L;
        for (Credit credit : sharedCreditsBefore(db,item,month))
            total = Math.addExact(total,credit.amountGrosz);
        return total;
    }

    static long creditAppliedTo(Item item, YearMonth targetMonth) {
        long total = 0L;
        String suffix = "|" + targetMonth;
        for (Map.Entry<String,Long> entry
                : item.creditApplicationsGrosz.entrySet())
            if (entry.getKey().endsWith(suffix))
                total = Math.addExact(total,entry.getValue());
        return total;
    }

    private static long creditAppliedFrom(Item item, YearMonth sourceMonth) {
        long total = 0L;
        String prefix = sourceMonth + "|";
        for (Map.Entry<String,Long> entry
                : item.creditApplicationsGrosz.entrySet())
            if (entry.getKey().startsWith(prefix))
                total = Math.addExact(total,entry.getValue());
        return total;
    }

    /**
     * Weryfikacja nadpłat otrzymanych z innego urządzenia.
     * Zabezpiecza przed dwukrotnym odliczeniem jednego źródłowego miesiąca,
     * także gdy docelowe miesiące są różne.
     */
    static void assertCreditConservation(SQLiteDatabase db,String serialized)
            throws Exception {
        for(Item item:parseSerialized(serialized)) {
            if(item.creditApplicationsGrosz.isEmpty())continue;
            if(!"expense".equals(item.kind))
                throw new IllegalArgumentException(
                    "Wpływ nie może odliczać nadpłaty zobowiązania.");
            Map<YearMonth,Long> usedSources=new LinkedHashMap<>();
            Map<YearMonth,Long> usedTargets=new LinkedHashMap<>();
            for(Map.Entry<String,Long> application
                    :item.creditApplicationsGrosz.entrySet()) {
                String[] months=application.getKey().split("\\|",-1);
                if(months.length!=2)
                    throw new IllegalArgumentException(
                        "Błędny miesiąc odliczenia nadpłaty.");
                YearMonth from=YearMonth.parse(months[0]);
                YearMonth to=YearMonth.parse(months[1]);
                if(!from.isBefore(to)||application.getValue()==null
                        ||application.getValue()<=0L)
                    throw new IllegalArgumentException(
                        "Nieprawidłowa kolejność lub kwota odliczenia nadpłaty.");
                usedSources.put(from,Math.addExact(
                    usedSources.getOrDefault(from,0L),application.getValue()));
                usedTargets.put(to,Math.addExact(
                    usedTargets.getOrDefault(to,0L),application.getValue()));
            }
            for(Map.Entry<YearMonth,Long> source:usedSources.entrySet()) {
                YearMonth month=source.getKey();
                PaycheckBudgetCreditMath.requireSourceAvailable(
                    sharedMatchedActual(db,item,month),
                    sharedSplitSurplus(db,item,month),
                    plannedAmount(item,month),source.getValue());
            }
            for(Map.Entry<YearMonth,Long> target:usedTargets.entrySet())
                PaycheckBudgetCreditMath.requireTargetWithinPlan(
                    plannedAmount(item,target.getKey()),target.getValue());
        }
    }

    static void applyCredit(SharedPreferences prefs, SQLiteDatabase db,
            String itemId, YearMonth sourceMonth, YearMonth targetMonth,
            long amountGrosz) throws Exception {
        validateAmount(amountGrosz);
        if (!sourceMonth.isBefore(targetMonth))
            throw new IllegalArgumentException(
                "Nadpłatę można odliczyć tylko w późniejszym miesiącu.");
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        long available = 0L;
        for (Credit credit : sharedCreditsBefore(db,target,targetMonth))
            if (credit.sourceMonth.equals(sourceMonth))
                available = credit.amountGrosz;
        if (amountGrosz > available)
            throw new IllegalArgumentException(
                "Kwota odliczenia przekracza dostępną nadpłatę.");
        long planned = plannedAmount(target,targetMonth);
        long alreadyApplied = creditAppliedTo(target,targetMonth);
        if (amountGrosz > planned - alreadyApplied)
            throw new IllegalArgumentException(
                "Odliczenie nie może przekroczyć pozostałego planu tego miesiąca.");
        String key = sourceMonth + "|" + targetMonth;
        target.creditApplicationsGrosz.put(key,
            Math.addExact(target.creditApplicationsGrosz.getOrDefault(key,0L),
                amountGrosz));
        target.creditApplicationCreatedAt.put(key,System.currentTimeMillis());
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,targetMonth,
            "CREDIT_APPLIED",amountGrosz,
            "Nadpłata z " + sourceMonth);
    }

    static void closeArrears(SharedPreferences prefs, String itemId,
            YearMonth month, long amountGrosz, String reason) throws Exception {
        validateAmount(amountGrosz);
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > 160)
            throw new IllegalArgumentException(
                "Podaj powód zamknięcia zaległości (maks. 160 znaków).");
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        target.balanceAdjustmentsGrosz.put(month.toString(),
            Math.addExact(target.balanceAdjustmentsGrosz.getOrDefault(
                month.toString(),0L),amountGrosz));
        target.adjustmentReasons.put(month.toString(),clean);
        target.adjustmentCreatedAt.put(month.toString(),System.currentTimeMillis());
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "ARREAR_CLOSED",amountGrosz,clean);
    }

    static void closeOccurrence(SharedPreferences prefs, String itemId,
            YearMonth month, String reason) throws Exception {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > 160)
            throw new IllegalArgumentException(
                "Podaj powód zamknięcia pozycji (maks. 160 znaków).");
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        if (!occurs(target,month))
            throw new IllegalArgumentException(
                "Ta pozycja nie występuje w wybranym miesiącu.");
        target.closedMonths.add(month.toString());
        target.closedMonthReasons.put(month.toString(),clean);
        target.closedMonthCreatedAt.put(month.toString(),System.currentTimeMillis());
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "OCCURRENCE_CLOSED",plannedAmount(target,month),clean);
    }

    static void endCycleAt(SharedPreferences prefs, String itemId,
            YearMonth month) throws Exception {
        List<Item> items = load(prefs);
        Item target = find(items,itemId);
        if (target.cycleMonths <= 0)
            throw new IllegalArgumentException("To nie jest pozycja cykliczna.");
        YearMonth start = YearMonth.parse(target.startMonth);
        if (month.isBefore(start))
            throw new IllegalArgumentException(
                "Koniec cyklu nie może być przed początkiem.");
        target.endMonth = month.toString();
        if (target.installment) target.installmentCount = 0;
        validate(target);
        save(prefs,items);
        PaycheckBudgetHistoryStore.append(prefs,target,month,
            "CYCLE_ENDED",plannedAmount(target,month),
            "Cykl zakończony na " + month);
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
            if (activeInMonth(item,month) && occurs(item,month)) result.add(item);
        return result;
    }

    private static boolean activeInMonth(Item item, YearMonth month) {
        if (item.active) return true;
        if (item.inactiveFromMonth == null || item.inactiveFromMonth.isBlank())
            return false;
        try {
            return month.isBefore(YearMonth.parse(item.inactiveFromMonth));
        } catch(Exception invalid) {
            return false;
        }
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

    /**
     * Realizacja Budżetu to tylko potwierdzone wpłaty przypisane do pozycji.
     * Reszta potwierdzonego PayCheck nie może udawać realizacji planu.
     */
    static Totals sharedAssignedActual(SQLiteDatabase db, List<Item> all,
            YearMonth month) {
        Totals assigned = new Totals();
        for (Item item : all) {
            long amount = Math.addExact(sharedMatchedActual(db,item,month),
                sharedSplitSurplus(db,item,month));
            if ("income".equals(item.kind))
                assigned.income=Math.addExact(assigned.income,amount);
            else
                assigned.expense=Math.addExact(assigned.expense,amount);
        }
        return assigned;
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
        json.put("templateDueDay", item.dueDay);
        json.put("optional", item.optional);
        json.put("installment", item.installment);
        json.put("installmentCount", item.installmentCount);
        json.put("creditAgreementNumber", item.creditAgreementNumber);
        json.put("recipientId", item.recipientId);
        json.put("active", item.active);
        json.put("inactiveFromMonth", item.inactiveFromMonth);
        JSONArray matches = new JSONArray();
        for (String operationId : item.matchedOperationIds)
            matches.put(operationId);
        json.put("matches", matches);
        JSONObject allocations = new JSONObject();
        for (Map.Entry<String, Long> entry : item.matchedAllocationsGrosz.entrySet())
            allocations.put(entry.getKey(), entry.getValue());
        json.put("allocations", allocations);
        json.put("splitSurplusesGrosz",amounts(item.splitSurplusesGrosz));
        json.put("skippedMonths", strings(item.skippedMonths));
        json.put("carriedMonths", strings(item.carriedMonths));
        json.put("monthAmountOverrides", amounts(item.monthAmountOverrides));
        json.put("amountChanges", amounts(item.amountChanges));
        json.put("balanceAdjustmentsGrosz",
            amounts(item.balanceAdjustmentsGrosz));
        json.put("adjustmentCreatedAt",
            amountsAllowZero(item.adjustmentCreatedAt));
        json.put("closedMonths",strings(item.closedMonths));
        JSONObject closedReasons = new JSONObject();
        for (Map.Entry<String,String> entry : item.closedMonthReasons.entrySet())
            closedReasons.put(entry.getKey(),entry.getValue());
        json.put("closedMonthReasons",closedReasons);
        json.put("closedMonthCreatedAt",
            amountsAllowZero(item.closedMonthCreatedAt));
        json.put("creditApplicationsGrosz",
            amounts(item.creditApplicationsGrosz));
        json.put("creditApplicationCreatedAt",
            amountsAllowZero(item.creditApplicationCreatedAt));
        json.put("invoiceDueDates", stringsMap(item.invoiceDueDates));
        json.put("plannedPaymentDates", stringsMap(item.plannedPaymentDates));
        JSONObject reasons = new JSONObject();
        for (Map.Entry<String, String> entry : item.adjustmentReasons.entrySet())
            reasons.put(entry.getKey(),entry.getValue());
        json.put("adjustmentReasons",reasons);
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
        item.dueDay = json.optInt("templateDueDay",
            json.optInt("dueDay", 0));
        item.optional = json.optBoolean("optional", false);
        item.installment = json.optBoolean("installment", false);
        item.installmentCount = json.optInt("installmentCount", 0);
        item.creditAgreementNumber =
            json.optString("creditAgreementNumber", "").trim();
        item.recipientId = json.optString("recipientId", "").trim();
        item.active = !json.has("active") || json.getBoolean("active");
        item.inactiveFromMonth =
            json.optString("inactiveFromMonth","").trim();
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
        readAmounts(json.optJSONObject("splitSurplusesGrosz"),
            item.splitSurplusesGrosz);
        readStrings(json.optJSONArray("skippedMonths"), item.skippedMonths);
        readStrings(json.optJSONArray("carriedMonths"), item.carriedMonths);
        readAmounts(json.optJSONObject("monthAmountOverrides"),
            item.monthAmountOverrides);
        readAmounts(json.optJSONObject("amountChanges"), item.amountChanges);
        readAmounts(json.optJSONObject("balanceAdjustmentsGrosz"),
            item.balanceAdjustmentsGrosz);
        JSONObject adjustmentTimesJson =
            json.optJSONObject("adjustmentCreatedAt");
        readAmountsAllowZero(adjustmentTimesJson,item.adjustmentCreatedAt);
        if (adjustmentTimesJson == null
                && !item.balanceAdjustmentsGrosz.isEmpty()) {
            long migratedAt = System.currentTimeMillis();
            for (String key : item.balanceAdjustmentsGrosz.keySet())
                item.adjustmentCreatedAt.put(key,migratedAt);
        }
        readStrings(json.optJSONArray("closedMonths"),item.closedMonths);
        JSONObject closedReasons = json.optJSONObject("closedMonthReasons");
        if (closedReasons != null) {
            java.util.Iterator<String> keys = closedReasons.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                item.closedMonthReasons.put(key,closedReasons.optString(key,""));
            }
        }
        readAmountsAllowZero(json.optJSONObject("closedMonthCreatedAt"),
            item.closedMonthCreatedAt);
        readAmounts(json.optJSONObject("creditApplicationsGrosz"),
            item.creditApplicationsGrosz);
        readAmountsAllowZero(json.optJSONObject("creditApplicationCreatedAt"),
            item.creditApplicationCreatedAt);
        readStringsMap(json.optJSONObject("invoiceDueDates"),
            item.invoiceDueDates);
        readStringsMap(json.optJSONObject("plannedPaymentDates"),
            item.plannedPaymentDates);
        if (!json.has("templateDueDay")) {
            LocalDate firstDue = invoiceDueDate(item,YearMonth.parse(item.startMonth));
            LocalDate firstPlan = null;
            try {
                String rawPlan = item.plannedPaymentDates.get(item.startMonth);
                if (rawPlan != null) firstPlan = LocalDate.parse(rawPlan);
            } catch (Exception ignored) { }
            item.dueDay = PaycheckBudgetInvoiceDates.migrateLegacyDay(
                item.dueDay,firstDue,firstPlan);
        }
        JSONObject reasons = json.optJSONObject("adjustmentReasons");
        if (reasons != null) {
            java.util.Iterator<String> reasonKeys = reasons.keys();
            while (reasonKeys.hasNext()) {
                String key = reasonKeys.next();
                item.adjustmentReasons.put(key,reasons.optString(key,""));
            }
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
                || item.installmentCount < 0 || item.installmentCount > 600
                || item.creditAgreementNumber == null
                || item.creditAgreementNumber.length() > 80
                || item.recipientId == null
                || (!item.recipientId.isBlank()
                    && !item.recipientId.matches("[0-9a-fA-F-]{36}"))
                || item.inactiveFromMonth == null)
            throw new IllegalArgumentException("Nieprawidłowa pozycja planu PayCheck.");
        if (!item.inactiveFromMonth.isBlank()) {
            try { YearMonth.parse(item.inactiveFromMonth); }
            catch(Exception invalid) {
                throw new IllegalArgumentException(
                    "Nieprawidłowy miesiąc wyłączenia pozycji.");
            }
        }
        if (!item.creditAgreementNumber.isBlank()
                && (!"expense".equals(item.kind)
                    || !"loans".equals(item.category)))
            throw new IllegalArgumentException(
                "Numer umowy kredytowej można zapisać tylko przy wydatku Kredyty i raty.");
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
        for (Map.Entry<String,Long> surplus : item.splitSurplusesGrosz.entrySet()) {
            if (!matched.contains(surplus.getKey())
                    || !item.matchedAllocationsGrosz.containsKey(surplus.getKey())
                    || surplus.getValue() == null || surplus.getValue() < 1L
                    || surplus.getValue() > MoneyRules.MAX_GROSZ)
                throw new IllegalArgumentException("Nieprawidłowa nadpłata z podziału.");
        }
        validateMonthSet(item.skippedMonths);
        validateMonthSet(item.carriedMonths);
        validateAmountMap(item.monthAmountOverrides);
        validateAmountMap(item.amountChanges);
        validateAmountMap(item.balanceAdjustmentsGrosz);
        validateHistoryMap(item.balanceAdjustmentsGrosz,
            item.adjustmentReasons,item.adjustmentCreatedAt,
            "korekty zaległości");
        validateClosedMonths(item.closedMonths,item.closedMonthReasons,
            item.closedMonthCreatedAt);
        validateCreditApplications(item.creditApplicationsGrosz);
        validateCreditApplicationTimes(item.creditApplicationsGrosz,
            item.creditApplicationCreatedAt);
        validateInvoiceDates(item);
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

    private static JSONObject stringsMap(Map<String,String> values)
            throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String,String> entry : values.entrySet())
            json.put(entry.getKey(),entry.getValue());
        return json;
    }

    private static void readStringsMap(JSONObject json, Map<String,String> target) {
        if (json == null) return;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key,json.optString(key,""));
        }
    }

    private static void validateInvoiceDates(Item item) {
        if (item.invoiceDueDates.size() > 600
                || item.plannedPaymentDates.size() > 600)
            throw new IllegalArgumentException("Za dużo terminów faktur.");
        for (Map.Entry<String,String> entry : item.invoiceDueDates.entrySet()) {
            final YearMonth month;
            final LocalDate due;
            try {
                month = YearMonth.parse(entry.getKey());
                due = LocalDate.parse(entry.getValue());
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Nieprawidłowy termin faktury.");
            }
            if (!YearMonth.from(due).equals(month))
                throw new IllegalArgumentException("Termin faktury ma zły miesiąc.");
            String rawPlan = item.plannedPaymentDates.get(entry.getKey());
            if (rawPlan == null || rawPlan.isBlank())
                throw new IllegalArgumentException("Brak planowanej daty zapłaty.");
            final LocalDate plan;
            try { plan = LocalDate.parse(rawPlan); }
            catch (Exception invalid) {
                throw new IllegalArgumentException("Nieprawidłowa planowana data zapłaty.");
            }
            if (!YearMonth.from(plan).equals(month) || plan.isAfter(due))
                throw new IllegalArgumentException(
                    "Planowana data zapłaty nie może być po terminie faktury.");
        }
        for (String month : item.plannedPaymentDates.keySet())
            if (!item.invoiceDueDates.containsKey(month))
                throw new IllegalArgumentException(
                    "Planowana data zapłaty bez faktury.");
    }

    private static boolean allowedCycle(int cycle) {
        for (int allowed : ALLOWED_CYCLES) if (allowed == cycle) return true;
        return false;
    }

    private static boolean occurs(Item item, YearMonth month) {
        String key = month.toString();
        if (item.closedMonths.contains(key)) return false;
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

    private static JSONObject amountsAllowZero(
            Map<String,Long> values) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String,Long> entry : values.entrySet())
            json.put(entry.getKey(),entry.getValue());
        return json;
    }

    private static void readAmountsAllowZero(
            JSONObject json, Map<String,Long> target) {
        if (json == null) return;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key,json.optLong(key,0L));
        }
    }

    private static void validateHistoryMap(
            Map<String,Long> amounts, Map<String,String> reasons,
            Map<String,Long> createdAt, String label) {
        if (amounts.size() > 600 || reasons.size() != amounts.size()
                || createdAt.size() != amounts.size())
            throw new IllegalArgumentException(
                "Niepełna historia " + label + ".");
        for (String key : amounts.keySet()) {
            try { YearMonth.parse(key); }
            catch(Exception invalid) {
                throw new IllegalArgumentException(
                    "Nieprawidłowy miesiąc historii " + label + ".");
            }
            String reason = reasons.get(key);
            Long at = createdAt.get(key);
            if (reason == null || reason.trim().isEmpty()
                    || reason.length() > 160 || at == null || at <= 0L)
                throw new IllegalArgumentException(
                    "Nieprawidłowy wpis historii " + label + ".");
        }
    }

    private static void validateClosedMonths(Set<String> months,
            Map<String,String> reasons, Map<String,Long> createdAt) {
        validateMonthSet(months);
        if (reasons.size() != months.size() || createdAt.size() != months.size())
            throw new IllegalArgumentException(
                "Niepełna historia ręcznie zamkniętych pozycji.");
        for (String key : months) {
            String reason = reasons.get(key);
            Long at = createdAt.get(key);
            if (reason == null || reason.trim().isEmpty()
                    || reason.length() > 160 || at == null || at <= 0L)
                throw new IllegalArgumentException(
                    "Nieprawidłowa historia ręcznie zamkniętej pozycji.");
        }
    }

    private static void validateCreditApplications(Map<String,Long> values) {
        if (values.size() > 1200)
            throw new IllegalArgumentException("Za dużo odliczeń nadpłat.");
        for (Map.Entry<String,Long> entry : values.entrySet()) {
            String[] parts = entry.getKey().split("\\|",-1);
            if (parts.length != 2)
                throw new IllegalArgumentException(
                    "Nieprawidłowe odliczenie nadpłaty.");
            try {
                YearMonth source = YearMonth.parse(parts[0]);
                YearMonth target = YearMonth.parse(parts[1]);
                if (!source.isBefore(target))
                    throw new IllegalArgumentException(
                        "Nieprawidłowy okres odliczenia nadpłaty.");
            } catch (IllegalArgumentException known) {
                throw known;
            } catch (Exception invalid) {
                throw new IllegalArgumentException(
                    "Nieprawidłowy miesiąc odliczenia nadpłaty.");
            }
            validateAmount(entry.getValue() == null ? -1L : entry.getValue());
        }
    }

    private static void validateCreditApplicationTimes(
            Map<String,Long> applications, Map<String,Long> times) {
        if (times.size() != applications.size())
            throw new IllegalArgumentException(
                "Niepełna historia odliczeń nadpłat.");
        for (Map.Entry<String,Long> time : times.entrySet()) {
            if (!applications.containsKey(time.getKey())
                    || time.getValue() == null || time.getValue() <= 0L)
                throw new IllegalArgumentException(
                    "Nieprawidłowa data odliczenia nadpłaty.");
        }
    }

    static YearMonth transactionMonth(long createdAt, String statementDate) {
        return PaycheckBudgetHistoryRules.bankMonth(
            createdAt,statementDate,ZoneId.systemDefault());
    }
}
