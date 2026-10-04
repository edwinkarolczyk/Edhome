package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Future household budget planner. Planned rows never alter real PayCheck ledger. */
final class DesktopBudgetPlanner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORE = Path.of(System.getProperty("user.home"),
        ".edhome", "paycheck-budget-plan.json");
    private static final Path BACKUP = STORE.resolveSibling("paycheck-budget-plan.json.bak");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DecimalFormat PLN = new DecimalFormat("#,##0.00");

    static final class PlanFile {
        int version = 1;
        List<BudgetItem> items = new ArrayList<>();
    }

    static final class BudgetItem {
        String id;
        String name;
        String kind;
        String type;
        String category;
        long amountGrosz;
        String amountMode;
        String provider;
        String startDate;
        String endDate;
        int recurrenceMonths;
        int dayOfMonth;
        int maxOccurrences;
        boolean active = true;
        long createdAt;
        String sourceDocumentName;
        String sourceDocumentSha256;
    }

    private static final class Choice {
        final String value;
        final String label;
        Choice(String value, String label) { this.value=value; this.label=label; }
        @Override public String toString() { return label; }
    }

    private static final class AmountChoice {
        final long grosz;
        AmountChoice(long grosz) { this.grosz = grosz; }
        @Override public String toString() { return money(grosz); }
    }

    private static final class ImportSeed {
        long amountGrosz;
        String suggestedName = "";
        String provider = "";
        String sourceLabel = "";
        String merchantLabel = "";
        String typeHint = "";
        String categoryHint = "";
        String startDate = "";
        String sourceDocumentName = "";
        String sourceDocumentSha256 = "";
        String description = "";
        String kind = "";
    }

    private static final int REVIEW_ADD = 0;
    private static final int REVIEW_SKIP = 1;
    private static final int REVIEW_SKIP_FILE = 2;
    private static final int REVIEW_STOP_ALL = 3;

    private DesktopBudgetPlanner() { }

    static void show(Component owner, JsonArray paycheckTransactions) {
        PlanFile plan;
        try {
            plan = load();
        } catch (Exception error) {
            JOptionPane.showMessageDialog(owner,
                "Nie można otworzyć planu budżetu:\n" + rootMessage(error),
                "PayCheck • Budżet miesiąca", JOptionPane.ERROR_MESSAGE);
            return;
        }

        while (true) {
            DefaultListModel<Choice> model = new DefaultListModel<>();
            for (int i=0; i<plan.items.size(); i++) {
                BudgetItem item = plan.items.get(i);
                model.addElement(new Choice(Integer.toString(i), rowLabel(item)));
            }
            JList<Choice> list = new JList<>(model);
            list.setVisibleRowCount(Math.min(13, Math.max(5, model.size())));
            if (!model.isEmpty()) list.setSelectedIndex(0);
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(880, 330));

            JPanel body = new JPanel(new BorderLayout(0,8));
            body.add(new JLabel("<html><b>Plan ≠ wykonanie.</b> "
                + "Pozycje poniżej prognozują przyszłe miesiące i nie zmieniają salda "
                + "PayCheck do czasu rzeczywistego potwierdzenia.</html>"),
                BorderLayout.NORTH);
            body.add(scroll, BorderLayout.CENTER);
            body.add(new JLabel("Plik lokalny: " + STORE), BorderLayout.SOUTH);

            Object[] actions = {
                "＋ Dodaj ręcznie",
                "Importuj PDF / XLSX / CSV",
                "Edytuj",
                "Usuń",
                "Prognoza 12 miesięcy",
                "Zamknij"
            };
            int action = JOptionPane.showOptionDialog(owner, body,
                "PayCheck • Budżet miesiąca",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, actions, actions[0]);
            if (action < 0 || action == 5) return;

            try {
                if (action == 0) {
                    BudgetItem created = runWizard(owner, null, null, null);
                    if (created != null) {
                        plan.items.add(created);
                        save(plan);
                    }
                } else if (action == 1) {
                    if (importDocuments(owner, plan)) save(plan);
                } else if (action == 2) {
                    int index = selectedIndex(list);
                    if (index < 0) continue;
                    BudgetItem edited = runWizard(owner, plan.items.get(index), null, null);
                    if (edited != null) {
                        plan.items.set(index, edited);
                        save(plan);
                    }
                } else if (action == 3) {
                    int index = selectedIndex(list);
                    if (index < 0) continue;
                    BudgetItem item = plan.items.get(index);
                    int yes = JOptionPane.showConfirmDialog(owner,
                        "Usunąć z planu „" + item.name + "”?\n"
                            + "Nie usuwa to żadnej transakcji PayCheck.",
                        "Budżet miesiąca", JOptionPane.YES_NO_OPTION);
                    if (yes == JOptionPane.YES_OPTION) {
                        plan.items.remove(index);
                        save(plan);
                    }
                } else if (action == 4) {
                    showForecast(owner, plan, paycheckTransactions);
                }
            } catch (Exception error) {
                JOptionPane.showMessageDialog(owner,
                    "Nie zapisano zmian:\n" + rootMessage(error),
                    "PayCheck • Budżet miesiąca", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static boolean importDocuments(Component owner, PlanFile plan) throws Exception {
        java.io.File[] files = nativeOpenFiles(owner,
            "Wybierz dokumenty budżetu • PDF / XLSX / CSV / TXT");
        if (files.length == 0) return false;
        boolean changed = false;

        for (java.io.File selected : files) {
            java.nio.file.Path path = selected.toPath();

            DesktopBankImporter.Result bank = tryReadBankStatement(path);
            if (bank != null && bank.entries != null && !bank.entries.isEmpty()) {
                for (int i=0; i<bank.entries.size(); i++) {
                    DesktopBankImporter.Entry entry = bank.entries.get(i);
                    ImportSeed seed = seedFromBank(selected.getName(), bank, entry);
                    int decision = reviewImportedCandidate(owner, seed,
                        i + 1, bank.entries.size(), true);
                    if (decision == REVIEW_STOP_ALL) return changed;
                    if (decision == REVIEW_SKIP_FILE) break;
                    if (decision == REVIEW_SKIP) continue;

                    BudgetItem item = runWizard(owner, null, null,
                        seed.amountGrosz, seed);
                    if (item != null) {
                        plan.items.add(item);
                        save(plan);
                        changed = true;
                    }
                }
                continue;
            }

            DesktopBudgetDocumentReader.DocumentData doc;
            try {
                doc = DesktopBudgetDocumentReader.read(path);
            } catch (Exception error) {
                JOptionPane.showMessageDialog(owner,
                    selected.getName() + ":\n" + rootMessage(error),
                    "Import dokumentu budżetu", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            if (doc.amountsGrosz.isEmpty()) {
                Object[] actions = {
                    "Dodaj ręcznie",
                    "Pomiń ten plik",
                    "Zakończ import"
                };
                int action = JOptionPane.showOptionDialog(owner,
                    "<html><b>" + escapeHtml(doc.fileName) + "</b><br><br>"
                        + "Nie znalazłem jednoznacznej kwoty do pokazania.<br>"
                        + "Możesz przejść do ręcznego kreatora albo pominąć dokument.</html>",
                    "Najpierw sprawdź dane",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, actions, actions[1]);
                if (action == 2 || action < 0) return changed;
                if (action == 0) {
                    BudgetItem item = runWizard(owner, null, doc, null, null);
                    if (item != null) {
                        plan.items.add(item);
                        save(plan);
                        changed = true;
                    }
                }
                continue;
            }

            for (int i=0; i<doc.amountsGrosz.size(); i++) {
                ImportSeed seed = seedFromDocument(doc, doc.amountsGrosz.get(i));
                int decision = reviewImportedCandidate(owner, seed,
                    i + 1, doc.amountsGrosz.size(), false);
                if (decision == REVIEW_STOP_ALL) return changed;
                if (decision == REVIEW_SKIP_FILE) break;
                if (decision == REVIEW_SKIP) continue;

                BudgetItem item = runWizard(owner, null, doc,
                    seed.amountGrosz, seed);
                if (item != null) {
                    plan.items.add(item);
                    save(plan);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static DesktopBankImporter.Result tryReadBankStatement(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".pdf") || lower.endsWith(".csv")
                || lower.endsWith(".xlsx") || lower.endsWith(".txt"))) return null;
        try {
            // The bank parser gives us structured date/description/kind/amount.
            // A neutral label is only used by generic CSV when the file has no bank name.
            return DesktopBankImporter.read(file, "Wyciąg bankowy");
        } catch (Exception ignored) {
            // Not every household document is a bank statement. Fall back to the
            // general document reader instead of treating that as an import error.
            return null;
        }
    }

    private static ImportSeed seedFromBank(String fileName,
            DesktopBankImporter.Result bank, DesktopBankImporter.Entry entry) {
        ImportSeed seed = new ImportSeed();
        seed.amountGrosz = entry.amountGrosz;
        seed.description = entry.description == null ? "" : entry.description.trim();
        seed.suggestedName = seed.description.isBlank()
            ? ("expense".equals(entry.kind) ? "Wydatek z banku" : "Wpływ z banku")
            : truncate(seed.description, 140);
        seed.sourceLabel = bank.bank == null ? "" : bank.bank;
        DesktopMerchantRules.Match merchant=DesktopMerchantRules.detect(seed.description);
        if(merchant!=null){
            seed.merchantLabel=merchant.label;
            seed.provider=merchant.label;
        }
        seed.kind = entry.kind == null ? "" : entry.kind;
        seed.typeHint = "income".equals(seed.kind) ? "recurring_income" : "bill";
        seed.categoryHint = merchant!=null ? merchant.category
            : "income".equals(seed.kind) ? "salary" : "other";
        seed.startDate = entry.date == null ? "" : entry.date;
        seed.sourceDocumentName = fileName == null ? "" : fileName;
        seed.sourceDocumentSha256 = bank.originalSha256 == null ? "" : bank.originalSha256;
        return seed;
    }

    private static ImportSeed seedFromDocument(
            DesktopBudgetDocumentReader.DocumentData doc, long amount) {
        ImportSeed seed = new ImportSeed();
        seed.amountGrosz = amount;
        seed.suggestedName = doc.suggestedName == null ? "" : doc.suggestedName;
        seed.provider = doc.providerHint == null ? "" : doc.providerHint;
        seed.merchantLabel = seed.provider;
        seed.sourceLabel = seed.provider;
        seed.typeHint = doc.typeHint == null ? "" : doc.typeHint;
        seed.categoryHint = doc.categoryHint == null ? "" : doc.categoryHint;
        seed.sourceDocumentName = doc.fileName == null ? "" : doc.fileName;
        seed.sourceDocumentSha256 = doc.sha256 == null ? "" : doc.sha256;
        seed.description = "Kwota znaleziona w dokumencie";
        return seed;
    }

    private static int reviewImportedCandidate(Component owner, ImportSeed seed,
            int index, int total, boolean bankStructured) {
        JPanel panel=new JPanel(new BorderLayout(0,10));
        String source=seed.sourceLabel==null||seed.sourceLabel.isBlank()
            ?seed.sourceDocumentName:seed.sourceLabel;
        panel.add(new JLabel("<html><b>Pozycja "+index+" z "+total+"</b>"
            +(source==null||source.isBlank()?"":" • "+escapeHtml(source))+"</html>"),
            BorderLayout.NORTH);

        JPanel data=new JPanel(new GridLayout(0,2,8,8));
        data.add(new JLabel("Data:"));
        data.add(new JLabel(seed.startDate==null||seed.startDate.isBlank()?"—":seed.startDate));
        data.add(new JLabel("Typ z pliku:"));
        data.add(new JLabel("income".equals(seed.kind)?"Wpływ":
            "expense".equals(seed.kind)?"Wydatek":"Nieustalony"));
        data.add(new JLabel("Kwota:"));
        JLabel amount=new JLabel(money(seed.amountGrosz));
        amount.setFont(amount.getFont().deriveFont(Font.BOLD,16f));
        data.add(amount);
        data.add(new JLabel("Bank / źródło:"));
        data.add(new JLabel(source==null||source.isBlank()?"—":source));

        JTextField merchant=new JTextField(seed.merchantLabel==null?"":seed.merchantLabel);
        data.add(new JLabel("Sklep / odbiorca:"));
        data.add(merchant);
        String suggestion=DesktopMerchantRules.suggestedCategory(merchant.getText().trim());
        data.add(new JLabel("Podpowiedź kategorii:"));
        data.add(new JLabel(categoryLabel(suggestion.isBlank()?seed.categoryHint:suggestion)));
        panel.add(data,BorderLayout.CENTER);

        JTextArea description=new JTextArea(seed.description==null||seed.description.isBlank()
            ?"(brak dodatkowego opisu)":seed.description);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(bankStructured?5:3);
        description.setBorder(BorderFactory.createTitledBorder(
            bankStructured?"Opis transakcji":"Dane rozpoznane"));
        panel.add(new JScrollPane(description),BorderLayout.SOUTH);

        Object[] actions={"Dodaj do budżetu","Pomiń","Pomiń resztę pliku","Zakończ import"};
        int action=JOptionPane.showOptionDialog(owner,panel,
            "Najpierw sprawdź dane • "+index+"/"+total,
            JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,
            null,actions,actions[0]);
        if(action<0)return REVIEW_STOP_ALL;
        if(action==REVIEW_ADD){
            seed.merchantLabel=merchant.getText().trim();
            seed.provider=seed.merchantLabel;
            String learned=DesktopMerchantRules.suggestedCategory(seed.merchantLabel);
            if(!learned.isBlank())seed.categoryHint=learned;
        }
        return action;
    }

    private static BudgetItem runWizard(Component owner, BudgetItem existing,
            DesktopBudgetDocumentReader.DocumentData doc, Long importedAmount) {
        return runWizard(owner, existing, doc, importedAmount, null);
    }

    private static BudgetItem runWizard(Component owner, BudgetItem existing,
            DesktopBudgetDocumentReader.DocumentData doc, Long importedAmount,
            ImportSeed importSeed) {
        BudgetItem base = existing == null ? new BudgetItem() : copy(existing);

        Choice[] types = {
            new Choice("bill","Rachunek cykliczny"),
            new Choice("loan","Rata / kredyt"),
            new Choice("one_time_expense","Wydatek jednorazowy"),
            new Choice("recurring_income","Dochód cykliczny"),
            new Choice("one_time_income","Dochód jednorazowy"),
            new Choice("recurring_other","Inny koszt cykliczny")
        };
        String suggestedType = existing != null ? existing.type
            : importSeed != null && importSeed.typeHint != null
                && !importSeed.typeHint.isBlank() ? importSeed.typeHint
            : doc != null && doc.typeHint != null && !doc.typeHint.isBlank()
                ? doc.typeHint : "bill";
        Choice type = choose(owner, "Co oznacza ta kwota?",
            "Kreator budżetu • rodzaj pozycji", types, suggestedType);
        if (type == null) return null;
        base.type = type.value;
        base.kind = ("recurring_income".equals(base.type)
            || "one_time_income".equals(base.type)) ? "income" : "expense";

        String suggestedName = existing != null ? existing.name
            : importSeed != null && importSeed.suggestedName != null
                && !importSeed.suggestedName.isBlank() ? importSeed.suggestedName
            : doc != null ? doc.suggestedName : "";
        String name = askText(owner, "Nazwa pozycji:", suggestedName,
            "Kreator budżetu • nazwa", false);
        if (name == null) return null;
        if (name.isBlank() || name.length() > 140) {
            JOptionPane.showMessageDialog(owner, "Nazwa musi mieć 1–140 znaków.");
            return null;
        }
        base.name = name.trim();

        if (importedAmount != null) {
            // Amount was already shown in the review step. Do not force the user
            // to remember/retype bank data that EDHOME has just parsed.
            base.amountGrosz = importedAmount;
        } else {
            String amountDefault = existing != null ? formatInput(existing.amountGrosz) : "";
            String amountRaw = askText(owner, "Kwota [PLN]:", amountDefault,
                "Kreator budżetu • kwota", false);
            if (amountRaw == null) return null;
            try { base.amountGrosz = parsePln(amountRaw); }
            catch (Exception error) {
                JOptionPane.showMessageDialog(owner, rootMessage(error));
                return null;
            }
        }

        String providerDefault = existing != null ? existing.provider
            : importSeed != null && importSeed.provider != null
                && !importSeed.provider.isBlank() ? importSeed.provider
            : doc != null ? doc.providerHint : "";
        String providerLabel = "loan".equals(base.type) ? "Bank / pożyczkodawca:"
            : "Dostawca / odbiorca (opcjonalnie):";
        String provider = askText(owner, providerLabel, providerDefault,
            "Kreator budżetu • źródło", true);
        if (provider == null) return null;
        base.provider = provider.trim();

        Choice[] categories = budgetCategoryChoices();
        String suggestedCategory = existing != null ? existing.category
            : importSeed != null && importSeed.categoryHint != null
                && !importSeed.categoryHint.isBlank() ? importSeed.categoryHint
            : doc != null && doc.categoryHint != null && !doc.categoryHint.isBlank()
                ? doc.categoryHint
                : "recurring_income".equals(base.type) || "one_time_income".equals(base.type)
                    ? "salary" : "bills";
        Choice category = choose(owner, "Kategoria:", "Kreator budżetu • kategoria",
            categories, suggestedCategory);
        if (category == null) return null;
        base.category = category.value;

        boolean recurring = !"one_time_expense".equals(base.type)
            && !"one_time_income".equals(base.type);
        if ("bill".equals(base.type) || "recurring_other".equals(base.type)) {
            Choice mode = choose(owner,
                "Czy kwota jest stała, czy ma być traktowana jako prognoza?",
                "Kreator budżetu • charakter kwoty",
                new Choice[]{
                    new Choice("fixed","Stała kwota"),
                    new Choice("estimate","Kwota zmienna / prognoza")
                },
                existing != null ? existing.amountMode
                    : "bill".equals(base.type) ? "estimate" : "fixed");
            if (mode == null) return null;
            base.amountMode = mode.value;
        } else base.amountMode = "fixed";

        String dateDefault = existing != null && existing.startDate != null
            && !existing.startDate.isBlank() ? existing.startDate
            : importSeed != null && importSeed.startDate != null
                && !importSeed.startDate.isBlank() ? importSeed.startDate
            : LocalDate.now().toString();
        String dateLabel = recurring ? "Od kiedy? [RRRR-MM-DD]:" : "Data [RRRR-MM-DD]:";
        String startRaw = askText(owner, dateLabel, dateDefault,
            "Kreator budżetu • termin", false);
        if (startRaw == null) return null;
        LocalDate start;
        try { start = LocalDate.parse(startRaw.trim(), DATE); }
        catch (Exception error) {
            JOptionPane.showMessageDialog(owner, "Data musi mieć format RRRR-MM-DD.");
            return null;
        }
        base.startDate = start.toString();
        base.dayOfMonth = start.getDayOfMonth();
        base.endDate = "";
        base.maxOccurrences = 0;

        if (!recurring) {
            base.recurrenceMonths = 0;
        } else {
            Choice[] frequencies = {
                new Choice("1","Co miesiąc"),
                new Choice("2","Co 2 miesiące"),
                new Choice("3","Co kwartał"),
                new Choice("6","Co 6 miesięcy"),
                new Choice("12","Co rok"),
                new Choice("-1","Nieregularnie — tylko prognoza najbliższego terminu")
            };
            String defaultFreq = existing != null
                ? Integer.toString(existing.recurrenceMonths) : "1";
            Choice frequency = choose(owner, "Jak często?", "Kreator budżetu • cykl",
                frequencies, defaultFreq);
            if (frequency == null) return null;
            base.recurrenceMonths = Integer.parseInt(frequency.value);

            if (base.recurrenceMonths > 0) {
                String dayRaw = askText(owner, "Którego dnia miesiąca? [1–31]:",
                    Integer.toString(existing != null && existing.dayOfMonth > 0
                        ? existing.dayOfMonth : start.getDayOfMonth()),
                    "Kreator budżetu • dzień płatności", false);
                if (dayRaw == null) return null;
                try {
                    int day = Integer.parseInt(dayRaw.trim());
                    if (day < 1 || day > 31) throw new NumberFormatException();
                    base.dayOfMonth = day;
                } catch (NumberFormatException error) {
                    JOptionPane.showMessageDialog(owner, "Dzień miesiąca: 1–31.");
                    return null;
                }
            }

            Choice[] endModes = "loan".equals(base.type)
                ? new Choice[]{
                    new Choice("date","Znam datę końcową"),
                    new Choice("count","Znam liczbę pozostałych rat"),
                    new Choice("none","Jeszcze nie wiem kiedy się kończy")
                }
                : new Choice[]{
                    new Choice("none","Bez daty końcowej"),
                    new Choice("date","Ma datę końcową"),
                    new Choice("count","Ma określoną liczbę wystąpień")
                };
            String defaultEnd = existing != null && existing.maxOccurrences > 0
                ? "count" : existing != null && existing.endDate != null
                    && !existing.endDate.isBlank() ? "date" : "none";
            Choice endMode = choose(owner, "Kiedy ten cykl się kończy?",
                "Kreator budżetu • koniec", endModes, defaultEnd);
            if (endMode == null) return null;
            if ("date".equals(endMode.value)) {
                String endDefault = existing != null && existing.endDate != null
                    && !existing.endDate.isBlank() ? existing.endDate : start.plusYears(1).toString();
                String endRaw = askText(owner, "Data końcowa [RRRR-MM-DD]:",
                    endDefault, "Kreator budżetu • koniec", false);
                if (endRaw == null) return null;
                try {
                    LocalDate end = LocalDate.parse(endRaw.trim(), DATE);
                    if (end.isBefore(start))
                        throw new IllegalArgumentException("Data końcowa jest przed początkiem.");
                    base.endDate = end.toString();
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(owner,
                        "Nieprawidłowa data końcowa: " + rootMessage(error));
                    return null;
                }
            } else if ("count".equals(endMode.value)) {
                String countRaw = askText(owner, "Ile wystąpień / rat pozostało?",
                    existing != null && existing.maxOccurrences > 0
                        ? Integer.toString(existing.maxOccurrences) : "12",
                    "Kreator budżetu • liczba rat", false);
                if (countRaw == null) return null;
                try {
                    int count = Integer.parseInt(countRaw.trim());
                    if (count < 1 || count > 1200) throw new NumberFormatException();
                    base.maxOccurrences = count;
                    if (base.recurrenceMonths > 0) {
                        LocalDate last = occurrenceDate(start, base.dayOfMonth)
                            .plusMonths((long)(count-1) * base.recurrenceMonths);
                        base.endDate = last.toString();
                    }
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(owner,
                        "Liczba wystąpień musi być w zakresie 1–1200.");
                    return null;
                }
            }
        }

        if (existing == null) {
            base.id = UUID.randomUUID().toString();
            base.createdAt = System.currentTimeMillis();
        }
        base.active = true;
        if (importSeed != null) {
            base.sourceDocumentName = importSeed.sourceDocumentName;
            base.sourceDocumentSha256 = importSeed.sourceDocumentSha256;
        } else if (doc != null) {
            base.sourceDocumentName = doc.fileName;
            base.sourceDocumentSha256 = doc.sha256;
        }

        int save = JOptionPane.showConfirmDialog(owner,
            summary(base) + "\n\nZapisać do Budżetu przyszłego?",
            "Kreator budżetu • podsumowanie",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if(save==JOptionPane.OK_OPTION && importSeed!=null
                && importSeed.merchantLabel!=null && !importSeed.merchantLabel.isBlank())
            DesktopMerchantRules.remember(importSeed.merchantLabel,base.category);
        return save == JOptionPane.OK_OPTION ? base : null;
    }

    private static void showForecast(Component owner, PlanFile plan,
            JsonArray paycheckTransactions) {
        YearMonth first = YearMonth.now();
        Object[][] rows = new Object[12][6];
        long totalPlanIncome=0, totalPlanExpense=0, totalActualIncome=0, totalActualExpense=0;

        for (int i=0; i<12; i++) {
            YearMonth month = first.plusMonths(i);
            long plannedIncome=0, plannedExpense=0;
            for (BudgetItem item : plan.items) {
                if (!item.active) continue;
                int count = occurrencesInMonth(item, month);
                if (count <= 0) continue;
                long value = item.amountGrosz * count;
                if ("income".equals(item.kind)) plannedIncome += value;
                else plannedExpense += value;
            }
            long[] actual = actualForMonth(paycheckTransactions, month);
            totalPlanIncome += plannedIncome; totalPlanExpense += plannedExpense;
            totalActualExpense += actual[0]; totalActualIncome += actual[1];

            rows[i][0] = month.format(DateTimeFormatter.ofPattern("MM/yyyy"));
            rows[i][1] = money(plannedIncome);
            rows[i][2] = money(plannedExpense);
            rows[i][3] = money(plannedIncome-plannedExpense);
            rows[i][4] = money(actual[1]);
            rows[i][5] = money(actual[0]);
        }

        DefaultTableModel model = new DefaultTableModel(rows,
            new Object[]{"Miesiąc","Plan wpływów","Plan wydatków","Plan zostaje",
                "Faktyczne wpływy","Faktyczne wydatki"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        JScrollPane pane = new JScrollPane(table);
        pane.setPreferredSize(new Dimension(900, 310));

        JTextArea footer = new JTextArea(
            "12 miesięcy • planowane wpływy: " + money(totalPlanIncome)
            + " • planowane wydatki: " + money(totalPlanExpense)
            + " • planowany bilans: " + money(totalPlanIncome-totalPlanExpense)
            + "\nPotwierdzone już w PayCheck • wpływy: " + money(totalActualIncome)
            + " • wydatki: " + money(totalActualExpense)
            + "\n\nKwoty oznaczone jako „prognoza” są planem, nie gwarantowaną wartością rachunku.");
        footer.setEditable(false); footer.setOpaque(false);
        footer.setLineWrap(true); footer.setWrapStyleWord(true);

        JPanel body = new JPanel(new BorderLayout(0,10));
        body.add(pane, BorderLayout.CENTER); body.add(footer, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(owner, body,
            "PayCheck • prognoza 12 miesięcy", JOptionPane.PLAIN_MESSAGE);
    }

    private static int occurrencesInMonth(BudgetItem item, YearMonth month) {
        LocalDate start;
        try { start = LocalDate.parse(item.startDate); }
        catch (Exception invalid) { return 0; }
        LocalDate end = null;
        try {
            if (item.endDate != null && !item.endDate.isBlank())
                end = LocalDate.parse(item.endDate);
        } catch (Exception ignored) { }

        if (item.recurrenceMonths == 0 || item.recurrenceMonths == -1) {
            LocalDate occurrence = item.recurrenceMonths == 0
                ? start : occurrenceDate(start, item.dayOfMonth);
            return YearMonth.from(occurrence).equals(month) ? 1 : 0;
        }

        LocalDate occurrence = occurrenceDate(start, item.dayOfMonth);
        int ordinal = 0;
        for (int guard=0; guard<1500; guard++) {
            if (end != null && occurrence.isAfter(end)) return 0;
            if (item.maxOccurrences > 0 && ordinal >= item.maxOccurrences) return 0;
            YearMonth om = YearMonth.from(occurrence);
            if (om.equals(month)) return 1;
            if (om.isAfter(month)) return 0;
            occurrence = occurrence.plusMonths(item.recurrenceMonths)
                .withDayOfMonth(Math.min(item.dayOfMonth,
                    occurrence.plusMonths(item.recurrenceMonths).lengthOfMonth()));
            ordinal++;
        }
        return 0;
    }

    private static LocalDate occurrenceDate(LocalDate start, int requestedDay) {
        int day = requestedDay <= 0 ? start.getDayOfMonth() : requestedDay;
        YearMonth ym = YearMonth.from(start);
        LocalDate candidate = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        if (candidate.isBefore(start)) {
            ym = ym.plusMonths(1);
            candidate = ym.atDay(Math.min(day, ym.lengthOfMonth()));
        }
        return candidate;
    }

    private static long[] actualForMonth(JsonArray transactions, YearMonth month) {
        long expenses=0, incomes=0;
        if (transactions == null) return new long[]{0,0};
        for (JsonElement element : transactions) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx,"scope"))
                    || !"confirmed".equals(value(tx,"status"))) continue;
            YearMonth txMonth = null;
            String statementDate = value(tx,"statement_date");
            if (!statementDate.isBlank()) {
                try { txMonth = YearMonth.from(LocalDate.parse(statementDate)); }
                catch (Exception ignored) { }
            }
            if (txMonth == null) {
                try {
                    txMonth = YearMonth.from(
                        Instant.ofEpochMilli(Long.parseLong(value(tx,"created_at")))
                            .atZone(ZoneId.systemDefault()).toLocalDate());
                } catch (Exception ignored) { }
            }
            if (!month.equals(txMonth)) continue;
            try {
                long amount = Long.parseLong(value(tx,"amount_grosz"));
                if ("expense".equals(value(tx,"kind"))) expenses += amount;
                else if ("income".equals(value(tx,"kind"))) incomes += amount;
            } catch (Exception ignored) { }
        }
        return new long[]{expenses,incomes};
    }

    private static Choice[] budgetCategoryChoices() {
        return new Choice[]{
            new Choice("shopping","Zakupy"),new Choice("food","Żywność"),
            new Choice("subscriptions","Subskrypcje"),
            new Choice("utilities","Media • prąd / woda / gaz"),
            new Choice("bills","Rachunki"),new Choice("home","Dom"),
            new Choice("household","Domowe"),new Choice("vehicle","Pojazdy"),
            new Choice("fuel","Paliwo"),new Choice("transport","Transport"),
            new Choice("health","Zdrowie"),new Choice("beauty","Higiena"),
            new Choice("clothing","Odzież"),
            new Choice("restaurants","Restauracje / jedzenie na mieście"),
            new Choice("entertainment","Rozrywka"),new Choice("education","Edukacja"),
            new Choice("children","Dzieci"),new Choice("pet","Zwierzęta"),
            new Choice("insurance","Ubezpieczenia"),new Choice("loans","Kredyty i raty"),
            new Choice("salary","Wynagrodzenie"),new Choice("benefits","Świadczenia"),
            new Choice("savings","Oszczędności / inwestycje"),
            new Choice("transfers","Przelewy / transfery"),new Choice("other","Inne")
        };
    }
    private static String categoryLabel(String id){
        for(Choice c:budgetCategoryChoices())if(c.value.equals(id))return c.label;
        return "Inne";
    }
    private static java.io.File[] nativeOpenFiles(Component owner,String title){
        Window window=owner instanceof Window?(Window)owner:SwingUtilities.getWindowAncestor(owner);
        Frame frame=window instanceof Frame?(Frame)window:null;
        FileDialog dialog=new FileDialog(frame,title,FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setFilenameFilter((dir,name)->{
            String lower=name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".pdf")||lower.endsWith(".xlsx")
                ||lower.endsWith(".csv")||lower.endsWith(".txt");
        });
        dialog.setVisible(true);
        java.io.File[] files=dialog.getFiles();
        return files==null?new java.io.File[0]:files;
    }

    private static int selectedIndex(JList<Choice> list) {
        Choice selected = list.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(list, "Najpierw wybierz pozycję.");
            return -1;
        }
        return Integer.parseInt(selected.value);
    }

    private static Choice choose(Component owner, String message, String title,
            Choice[] choices, String defaultValue) {
        Choice selectedDefault = choices[0];
        for (Choice choice : choices)
            if (choice.value.equals(defaultValue)) selectedDefault = choice;
        return (Choice) JOptionPane.showInputDialog(owner, message, title,
            JOptionPane.QUESTION_MESSAGE, null, choices, selectedDefault);
    }

    private static String askText(Component owner, String label, String initial,
            String title, boolean optional) {
        JTextField field = new JTextField(initial == null ? "" : initial, 28);
        JPanel form = new JPanel(new BorderLayout(8,6));
        form.add(new JLabel(label), BorderLayout.NORTH);
        form.add(field, BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(owner, form, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return null;
        String value = field.getText().trim();
        if (!optional && value.isBlank()) {
            JOptionPane.showMessageDialog(owner, "To pole nie może być puste.");
            return null;
        }
        return value;
    }

    private static PlanFile load() throws Exception {
        if (!Files.isRegularFile(STORE)) return new PlanFile();
        PlanFile plan = GSON.fromJson(Files.readString(STORE, StandardCharsets.UTF_8),
            PlanFile.class);
        if (plan == null || plan.version != 1 || plan.items == null)
            throw new IllegalArgumentException("Nieobsługiwany format planu budżetu.");
        return plan;
    }

    private static void save(PlanFile plan) throws IOException {
        Files.createDirectories(STORE.getParent());
        if (Files.isRegularFile(STORE))
            Files.copy(STORE, BACKUP, StandardCopyOption.REPLACE_EXISTING);
        Path temp = STORE.resolveSibling(STORE.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(plan), StandardCharsets.UTF_8);
        try {
            Files.move(temp, STORE, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, STORE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BudgetItem copy(BudgetItem source) {
        BudgetItem out = new BudgetItem();
        out.id=source.id; out.name=source.name; out.kind=source.kind; out.type=source.type;
        out.category=source.category; out.amountGrosz=source.amountGrosz;
        out.amountMode=source.amountMode; out.provider=source.provider;
        out.startDate=source.startDate; out.endDate=source.endDate;
        out.recurrenceMonths=source.recurrenceMonths; out.dayOfMonth=source.dayOfMonth;
        out.maxOccurrences=source.maxOccurrences; out.active=source.active;
        out.createdAt=source.createdAt; out.sourceDocumentName=source.sourceDocumentName;
        out.sourceDocumentSha256=source.sourceDocumentSha256;
        return out;
    }

    private static long parsePln(String raw) {
        String normalized = raw.replace("\u00a0","").replace(" ","")
            .replace(",",".").replace("zł","").replace("PLN","").trim();
        java.math.BigDecimal value = new java.math.BigDecimal(normalized);
        if (value.signum() <= 0)
            throw new IllegalArgumentException("Kwota musi być większa od zera.");
        long result = value.movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        if (result > 1_000_000_000_00L)
            throw new IllegalArgumentException("Kwota jest za duża.");
        return result;
    }

    private static String rowLabel(BudgetItem item) {
        String provider = item.provider == null || item.provider.isBlank()
            ? "" : " • " + item.provider;
        String estimate = "estimate".equals(item.amountMode) ? " • prognoza" : "";
        return ("income".equals(item.kind) ? "+ " : "− ")
            + money(item.amountGrosz) + " • " + item.name + provider
            + " • " + cycleLabel(item) + estimate;
    }

    private static String cycleLabel(BudgetItem item) {
        if (item.recurrenceMonths == 0) return "jednorazowo " + item.startDate;
        if (item.recurrenceMonths == -1) return "nieregularnie";
        if (item.recurrenceMonths == 1) return "co miesiąc";
        if (item.recurrenceMonths == 2) return "co 2 miesiące";
        if (item.recurrenceMonths == 3) return "co kwartał";
        if (item.recurrenceMonths == 6) return "co 6 miesięcy";
        if (item.recurrenceMonths == 12) return "co rok";
        return "co " + item.recurrenceMonths + " mies.";
    }

    private static String summary(BudgetItem item) {
        StringBuilder out = new StringBuilder();
        out.append(item.name).append("\nTyp: ").append(typeLabel(item.type))
            .append("\nKwota: ").append(money(item.amountGrosz));
        if ("estimate".equals(item.amountMode)) out.append(" (prognoza)");
        out.append("\nCykl: ").append(cycleLabel(item))
            .append("\nStart: ").append(item.startDate);
        if (item.endDate != null && !item.endDate.isBlank())
            out.append("\nKoniec: ").append(item.endDate);
        if (item.maxOccurrences > 0)
            out.append("\nLiczba wystąpień/rat: ").append(item.maxOccurrences);
        if (item.provider != null && !item.provider.isBlank())
            out.append("\nDostawca/bank: ").append(item.provider);
        if (item.sourceDocumentName != null && !item.sourceDocumentName.isBlank())
            out.append("\nDokument: ").append(item.sourceDocumentName);
        return out.toString();
    }

    private static String typeLabel(String type) {
        if ("bill".equals(type)) return "Rachunek cykliczny";
        if ("loan".equals(type)) return "Rata / kredyt";
        if ("one_time_expense".equals(type)) return "Wydatek jednorazowy";
        if ("recurring_income".equals(type)) return "Dochód cykliczny";
        if ("one_time_income".equals(type)) return "Dochód jednorazowy";
        return "Inny koszt cykliczny";
    }

    private static String formatInput(long grosz) {
        return String.format(Locale.ROOT, "%.2f", grosz / 100.0);
    }

    private static String money(long grosz) {
        return PLN.format(grosz / 100.0) + " zł";
    }

    private static String value(JsonObject row, String key) {
        JsonElement element = row == null ? null : row.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName() : message;
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static String truncate(String raw, int max) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }
}

