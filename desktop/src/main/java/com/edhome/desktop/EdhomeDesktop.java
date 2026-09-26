package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EdhomeDesktop extends JFrame {
    private static final int PORT = 45823;
    private static final int PAIR_PORT = 45824;
    private static final String DESKTOP_VERSION = "0.6.0.53";
    private static final Color APP_BG = new Color(16, 20, 27);
    private static final Color APP_SURFACE = new Color(29, 35, 45);
    private static final Color APP_SURFACE_2 = new Color(37, 44, 56);
    private static final Color APP_TEXT = new Color(239, 243, 247);
    private static final Color APP_MUTED = new Color(164, 174, 188);
    private static final Color APP_ACCENT = new Color(87, 214, 181);
    private static final String DESKTOP_UPDATE_URL =
        "https://github.com/edwinkarolczyk/Edhome/releases/download/"
        + "desktop-beta-latest/EDHOME-Desktop-Beta-Windows.zip";
    private static final Path CACHE = Path.of(System.getProperty("user.home"),
        ".edhome", "desktop-cache.json");
    private static final Path INSTANCE_LOCK_PATH = Path.of(
        System.getProperty("user.home"), ".edhome", "desktop-instance.lock");
    private static FileChannel instanceLockChannel;
    private static FileLock instanceLock;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Preferences PREFS =
        Preferences.userRoot().node("edhome/desktop-beta");

    private static final String[] NAV = {
        "Pulpit", "Dzisiaj", "Kalendarz", "Zadania", "Czynności",
        "Magazyn", "Spiżarnia", "Zakupy", "PayCheck", "Pojazdy",
        "Odpady", "Timery", "Energia", "SUPLA", "Miejsca", "Skaner", "Ustawienia"
    };

    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel connection = new JLabel("OFFLINE • lokalna kopia");
    private JsonObject snapshot;
    private JsonObject syncedSnapshot;
    private String snapshotHash = "";
    private long phoneRevision = -1L;
    private long lastFullReconcileAt;
    private long localEditGeneration;
    private boolean stateChecking;
    private boolean dirty;
    private boolean connected;
    private boolean connecting;
    private String current = "Pulpit";
    private QrPairingSession qrPairingSession;
    private TrayIcon trayIcon;
    private javax.swing.Timer reconnectTimer;
    private javax.swing.Timer autoSaveTimer;
    private boolean autoSaving;
    private boolean editingDialog;
    private final Map<String,Integer> sectionScrollY = new java.util.HashMap<>();

    public static void main(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("--smoke-test".equals(arg)) {
                    System.out.println("EDHOME_DESKTOP_SMOKE_OK " + DESKTOP_VERSION);
                    return;
                }
            }
        }

        if (!acquireSingleInstanceLock()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                "EDHOME Desktop jest już uruchomiony.\n"
                    + "Sprawdź pasek zadań lub ikonę EDHOME obok zegara.",
                "EDHOME Desktop",
                JOptionPane.INFORMATION_MESSAGE));
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(
            EdhomeDesktop::releaseSingleInstanceLock,
            "edhome-single-instance-release"));

        SwingUtilities.invokeLater(() -> {
            try {
                new EdhomeDesktop();
            } catch (Throwable error) {
                releaseSingleInstanceLock();
                JOptionPane.showMessageDialog(null,
                    "Nie udało się uruchomić EDHOME Desktop:\n"
                        + rootMessage(error),
                    "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static synchronized boolean acquireSingleInstanceLock() {
        try {
            Files.createDirectories(INSTANCE_LOCK_PATH.getParent());
            instanceLockChannel = FileChannel.open(INSTANCE_LOCK_PATH,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                instanceLock = instanceLockChannel.tryLock();
            } catch (OverlappingFileLockException alreadyLocked) {
                instanceLock = null;
            }
            if (instanceLock == null) {
                try { instanceLockChannel.close(); } catch (Exception ignored) { }
                instanceLockChannel = null;
                return false;
            }
            return true;
        } catch (Exception error) {
            try {
                if (instanceLockChannel != null) instanceLockChannel.close();
            } catch (Exception ignored) { }
            instanceLockChannel = null;
            instanceLock = null;
            JOptionPane.showMessageDialog(null,
                "Nie można sprawdzić blokady pojedynczej instancji EDHOME.\n"
                    + "Program nie zostanie uruchomiony, aby nie otworzyć dwóch kopii.\n\n"
                    + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static synchronized void releaseSingleInstanceLock() {
        if (instanceLock != null) {
            try { instanceLock.release(); } catch (Exception ignored) { }
            instanceLock = null;
        }
        if (instanceLockChannel != null) {
            try { instanceLockChannel.close(); } catch (Exception ignored) { }
            instanceLockChannel = null;
        }
    }

    private EdhomeDesktop() {
        super("EDHOME Desktop Beta " + DESKTOP_VERSION);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (trayIcon != null) {
                    setVisible(false);
                    trayIcon.displayMessage("EDHOME Desktop",
                        "Program działa w tle.", TrayIcon.MessageType.INFO);
                } else {
                    shutdownDesktop();
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(APP_BG);
        content.setBackground(APP_BG);
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.add(topBar(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        loadCache();
        showSection("Pulpit");
        initTray();
        startReconnectLoop();
        startAutoSaveLoop();

        boolean startMinimized = PREFS.getBoolean("startMinimized", false);
        setVisible(!(startMinimized && trayIcon != null));
        SwingUtilities.invokeLater(() -> autoConnectSaved(true));
    }

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(APP_BG);
        bar.setBorder(new EmptyBorder(0, 0, 12, 0));
        JLabel title = new JLabel("EDHOME  •  DESKTOP BETA  " + DESKTOP_VERSION);
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        connection.setForeground(APP_ACCENT);
        connection.setHorizontalAlignment(SwingConstants.RIGHT);
        bar.add(title, BorderLayout.WEST);
        bar.add(connection, BorderLayout.EAST);
        return bar;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel();
        side.setBackground(APP_BG);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(0, 0, 0, 14));
        side.setPreferredSize(new Dimension(190, 1));
        for (String name : NAV) {
            JButton b = new JButton(name);
            b.setFocusPainted(false);
            b.setForeground(APP_TEXT);
            b.setBackground(APP_SURFACE);
            b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(APP_SURFACE_2),
                new EmptyBorder(9, 12, 9, 12)));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.addActionListener(e -> showSection(name));
            side.add(b);
            side.add(Box.createVerticalStrut(7));
        }
        side.add(Box.createVerticalGlue());
        JLabel mode = new JLabel("<html><b>EDHOME</b><br>Android ↔ PC</html>");
        mode.setForeground(APP_MUTED);
        side.add(mode);
        return side;
    }

    private void showSection(String name) {
        rememberCurrentScroll();
        current = name;
        content.removeAll();
        content.add(section(name), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
        int restore = sectionScrollY.getOrDefault(name, 0);
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = firstScrollPane(content);
            if (scroll != null)
                scroll.getVerticalScrollBar().setValue(Math.max(0, restore));
        });
    }

    private void rememberCurrentScroll() {
        if (current == null || current.isBlank()) return;
        JScrollPane scroll = firstScrollPane(content);
        if (scroll != null)
            sectionScrollY.put(current, scroll.getVerticalScrollBar().getValue());
    }

    private static JScrollPane firstScrollPane(Component root) {
        if (root instanceof JScrollPane) return (JScrollPane) root;
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                JScrollPane found = firstScrollPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JComponent section(String name) {
        if ("Pulpit".equals(name)) return dashboard();
        if ("Dzisiaj".equals(name)) return today();
        if ("Kalendarz".equals(name)) return calendar();
        if ("Zadania".equals(name)) return tablePage("Zadania", "tasks",
            cols("Tytuł","title","Termin","due_date","Priorytet","priority",
                 "Wykonane","done","Osoba","assignee_id"));
        if ("Czynności".equals(name)) return tablePage("Czynności", "tasks",
            cols("Nazwa","title","Typ","task_kind","Powtarzanie","repeat_rule",
                 "Co ile","repeat_every","Miejsce","place_id"));
        if ("Magazyn".equals(name)) return tablePage("Magazyn", "storage_items",
            cols("Nazwa","name","Typ","kind","Pudełko","parent_box_id",
                 "Miejsce","place_id","Wypożyczone","lent_to"));
        if ("Spiżarnia".equals(name)) return tablePage("Spiżarnia", "pantry",
            cols("Produkt","name","Ilość","qty","Kategoria","category"));
        if ("Zakupy".equals(name)) return tablePage("Lista zakupów", "shopping_items",
            cols("Produkt","name","Ilość","qty_milli","Jednostka","unit",
                 "Kupione","checked","Miejsce","place_id"));
        if ("PayCheck".equals(name)) return paycheck();
        if ("Pojazdy".equals(name)) return tablePage("Pojazdy", "vehicles",
            cols("Nazwa","name","Rejestracja","registration","Przebieg","mileage",
                 "OC do","oc_until","Przegląd do","inspection_until"));
        if ("Odpady".equals(name)) return waste();
        if ("Timery".equals(name)) return tablePage("Timery urządzeń", "device_timers",
            cols("Urządzenie","device_type","Nazwa","title","Start","start_at","Koniec","end_at"));
        if ("Energia".equals(name)) return placeholderPage("Energia i ogrzewanie",
            "Podstawowy ekran desktopowy jest gotowy. Dane PV, CWU, bufora i ogrzewania "
            + "dołączymy do wspólnego modelu po stronie Androida, żeby PC nie tworzył osobnej bazy.");
        if ("SUPLA".equals(name)) return placeholderPage("SUPLA",
            "Miejsce na podgląd pralki, suszarki i automatyki domowej. "
            + "W pierwszym desktop MVP integracja nie odpytuje jeszcze SUPLA.");
        if ("Miejsca".equals(name)) return tablePage("Miejsca", "places",
            cols("Nazwa","name","Typ","kind","Nadrzędne","parent_id"));
        if ("Skaner".equals(name)) return scanner();
        return settings();
    }

    private JComponent dashboard() {
        JPanel page = page("Pulpit");

        JPanel hero = new RoundedPanel(APP_SURFACE, 24);
        hero.setLayout(new BorderLayout(16, 10));
        hero.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        JLabel home = new JLabel(snapshot == null
            ? "EDHOME • brak połączenia"
            : "EDHOME • " + householdName());
        home.setForeground(APP_TEXT);
        home.setFont(home.getFont().deriveFont(Font.BOLD, 22f));
        JLabel state = new JLabel(snapshot == null
            ? "Połącz telefon w Ustawieniach, żeby zobaczyć wspólne dane."
            : connected
                ? "Telefon ONLINE • dane zsynchronizowane lokalnie"
                : "Telefon OFFLINE • pokazuję ostatnią lokalną kopię");
        state.setForeground(connected ? APP_ACCENT : APP_MUTED);
        heroText.add(home);
        heroText.add(Box.createVerticalStrut(6));
        heroText.add(state);
        hero.add(heroText, BorderLayout.CENTER);

        JButton syncNow = actionButton("↻ Synchronizuj teraz");
        syncNow.addActionListener(e -> autoConnectSaved(false));
        hero.add(syncNow, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.setBackground(APP_BG);
        north.add(hero, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(2, 4, 12, 12));
        cards.setBackground(APP_BG);
        cards.add(metric("Do zrobienia", countWhere("tasks","done",0), "Dzisiaj"));
        cards.add(metric("Na dziś", countTodayOpenTasks(), "Dzisiaj"));
        cards.add(metric("Zakupy", countWhere("shopping_items","checked",0), "Zakupy"));
        cards.add(metric("Magazyn", count("storage_items"), "Magazyn"));
        cards.add(metric("Spiżarnia", count("pantry"), "Spiżarnia"));
        cards.add(metric("Pojazdy", count("vehicles"), "Pojazdy"));
        cards.add(metric("PayCheck", count("paycheck_transactions"), "PayCheck"));
        cards.add(metric("Miejsca", count("places"), "Miejsca"));
        north.add(cards, BorderLayout.CENTER);
        page.add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 12));
        center.setBackground(APP_BG);
        center.setBorder(new EmptyBorder(18, 0, 0, 0));
        center.add(dashboardTasksCard());
        center.add(dashboardShoppingCard());
        page.add(center, BorderLayout.CENTER);

        JPanel quick = new JPanel(new GridLayout(1, 4, 10, 10));
        quick.setBackground(APP_BG);
        quick.setBorder(new EmptyBorder(14, 0, 0, 0));
        quick.add(quickButton("Dzisiaj", "Dzisiaj"));
        quick.add(quickButton("Kalendarz", "Kalendarz"));
        quick.add(quickButton("Magazyn", "Magazyn"));
        quick.add(quickButton("Zakupy", "Zakupy"));
        page.add(quick, BorderLayout.SOUTH);
        return page;
    }

    private int countTodayOpenTasks() {
        String today = LocalDate.now().toString();
        int count = 0;
        for (JsonElement el : table("tasks")) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (intValue(row, "done") != 0) continue;
            String due = value(row, "due_date");
            if (due.isBlank() || due.equals(today)) count++;
        }
        return count;
    }

    private JComponent dashboardTasksCard() {
        JPanel card = new RoundedPanel(APP_SURFACE, 22);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("Dzisiaj");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        card.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        String today = LocalDate.now().toString();
        int shown = 0;
        for (JsonElement el : table("tasks")) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (intValue(row, "done") != 0) continue;
            String due = value(row, "due_date");
            if (!due.isBlank() && !due.equals(today)) continue;
            list.add(dashboardLine(value(row, "title"),
                due.isBlank() ? "Bez terminu" : "Na dziś"));
            shown++;
            if (shown >= 5) break;
        }
        if (shown == 0) list.add(dashboardEmpty("Brak zadań na dziś."));
        card.add(list, BorderLayout.CENTER);

        JButton open = actionButton("Otwórz Dzisiaj");
        open.addActionListener(e -> showSection("Dzisiaj"));
        card.add(open, BorderLayout.SOUTH);
        return card;
    }

    private JComponent dashboardShoppingCard() {
        JPanel card = new RoundedPanel(APP_SURFACE, 22);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel title = new JLabel("Lista zakupów");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        card.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        int shown = 0;
        for (JsonElement el : table("shopping_items")) {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (intValue(row, "checked") != 0) continue;
            String qty = friendlyValue("qty_milli", row);
            String unit = value(row, "unit");
            String detail = "Do kupienia";
            if (!qty.isBlank() && !"—".equals(qty))
                detail = qty + (unit.isBlank() ? "" : " " + unit);
            list.add(dashboardLine(value(row, "name"), detail));
            shown++;
            if (shown >= 5) break;
        }
        if (shown == 0) list.add(dashboardEmpty("Lista zakupów jest pusta."));
        card.add(list, BorderLayout.CENTER);

        JButton open = actionButton("Otwórz Zakupy");
        open.addActionListener(e -> showSection("Zakupy"));
        card.add(open, BorderLayout.SOUTH);
        return card;
    }

    private JComponent dashboardLine(String primary, String secondary) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 0, 5, 0));
        JLabel main = new JLabel(primary == null || primary.isBlank() ? "Pozycja" : primary);
        main.setForeground(APP_TEXT);
        JLabel detail = new JLabel(secondary == null ? "" : secondary);
        detail.setForeground(APP_MUTED);
        row.add(main, BorderLayout.CENTER);
        row.add(detail, BorderLayout.EAST);
        return row;
    }

    private JComponent dashboardEmpty(String text) {
        JLabel empty = new JLabel(text);
        empty.setForeground(APP_MUTED);
        empty.setBorder(new EmptyBorder(10, 0, 10, 0));
        return empty;
    }

    private JComponent today() {
        JsonArray rows = table("tasks");
        JsonArray filtered = new JsonArray();
        String today = LocalDate.now().toString();
        for (JsonElement el : rows) {
            JsonObject row = el.getAsJsonObject();
            if (intValue(row, "done") != 0) continue;
            String due = value(row, "due_date");
            if (due.isBlank() || due.equals(today)) filtered.add(row);
        }
        return tablePage("Dzisiaj", filtered,
            cols("Zadanie","title","Termin","due_date","Priorytet","priority","Osoba","assignee_id"));
    }

    private JComponent paycheck() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setBackground(APP_BG);

        JPanel tools = new RoundedPanel(APP_SURFACE, 22);
        tools.setLayout(new BorderLayout(10, 10));
        tools.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JButton importBank = actionButton("＋ Importuj PDF / CSV / XLSX");
        JButton queue = actionButton("Banki i potwierdzenia");
        JButton history = actionButton("Historia importów");
        JButton analysis = actionButton("Analiza");
        JButton goals = actionButton("Cele");
        JButton bulk = actionButton("Masowa edycja");
        JButton privatePay = actionButton("Prywatny PayCheck");
        left.add(importBank);
        left.add(queue);
        left.add(history);
        left.add(analysis);
        left.add(goals);
        left.add(bulk);
        left.add(privatePay);
        tools.add(left, BorderLayout.WEST);

        int open = 0;
        for (JsonElement element : table("bank_evidence_queue")) {
            if (element.isJsonObject()
                    && "open".equals(value(element.getAsJsonObject(), "state"))) open++;
        }
        JLabel state = new JLabel("Do sprawdzenia z banku: " + open);
        state.setForeground(open == 0 ? APP_MUTED : APP_ACCENT);
        tools.add(state, BorderLayout.EAST);

        importBank.addActionListener(e -> importBankStatement());
        queue.addActionListener(e -> showBankEvidenceQueue());
        history.addActionListener(e -> showBankImportHistory());
        analysis.addActionListener(e -> showPaycheckAnalysis());
        goals.addActionListener(e -> showPaycheckGoals());
        bulk.addActionListener(e -> showPaycheckBulkEdit());
        privatePay.addActionListener(e -> showPrivatePaycheck());

        wrapper.add(tools, BorderLayout.NORTH);
        wrapper.add(tablePage("PayCheck • wspólne", "paycheck_transactions",
            cols("Typ","kind","Kategoria","category","Kwota [gr]","amount_grosz",
                 "Status","status","Źródło","confirmation_source","Data","created_at")),
            BorderLayout.CENTER);
        return wrapper;
    }

    private void showPaycheckAnalysis() {
        java.time.YearMonth now = java.time.YearMonth.now();
        java.util.SortedMap<java.time.YearMonth,long[]> months = new java.util.TreeMap<>();
        for (int i=11; i>=0; i--) months.put(now.minusMonths(i), new long[]{0L,0L});

        java.util.Map<String,Long> categories = new java.util.HashMap<>();
        long currentExpense = 0L, currentIncome = 0L;
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))
                    || !"confirmed".equals(value(tx, "status"))) continue;
            long amount;
            try { amount = Long.parseLong(value(tx, "amount_grosz")); }
            catch (Exception invalid) { continue; }

            java.time.YearMonth month = null;
            String statementDate = value(tx, "statement_date");
            if (!statementDate.isBlank()) {
                try { month = java.time.YearMonth.from(LocalDate.parse(statementDate)); }
                catch (Exception ignored) { }
            }
            if (month == null) {
                try {
                    month = java.time.YearMonth.from(
                        Instant.ofEpochMilli(Long.parseLong(value(tx, "created_at")))
                            .atZone(ZoneId.systemDefault()).toLocalDate());
                } catch (Exception ignored) { }
            }
            if (month == null) continue;

            long[] totals = months.get(month);
            if (totals != null) {
                if ("expense".equals(value(tx, "kind"))) totals[0] += amount;
                else if ("income".equals(value(tx, "kind"))) totals[1] += amount;
            }
            if (now.equals(month)) {
                if ("expense".equals(value(tx, "kind"))) {
                    currentExpense += amount;
                    categories.merge(value(tx, "category"), amount, Long::sum);
                } else if ("income".equals(value(tx, "kind"))) currentIncome += amount;
            }
        }

        java.util.List<String> labels = new ArrayList<>();
        java.util.List<Long> expenses = new ArrayList<>();
        java.util.List<Long> incomes = new ArrayList<>();
        DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("MM/yy");
        for (java.util.Map.Entry<java.time.YearMonth,long[]> entry : months.entrySet()) {
            labels.add(entry.getKey().format(monthFormat));
            expenses.add(entry.getValue()[0]);
            incomes.add(entry.getValue()[1]);
        }

        JPanel panel = new JPanel(new BorderLayout(0,10));
        panel.add(new DesktopPaycheckChart(labels, expenses, incomes), BorderLayout.CENTER);

        StringBuilder summary = new StringBuilder();
        summary.append("Bieżący miesiąc • wpływy ")
            .append(money(Long.toString(currentIncome)))
            .append(" • wydatki ").append(money(Long.toString(currentExpense)))
            .append(" • bilans ").append(money(Long.toString(currentIncome-currentExpense)))
            .append("\n\nKategorie wydatków:");
        java.util.List<java.util.Map.Entry<String,Long>> categoryRows =
            new ArrayList<>(categories.entrySet());
        categoryRows.sort((a,b) -> Long.compare(b.getValue(), a.getValue()));
        for (java.util.Map.Entry<String,Long> row : categoryRows)
            summary.append("\n• ").append(friendlyValueStaticCategory(row.getKey()))
                .append(": ").append(money(Long.toString(row.getValue())));
        JTextArea text = new JTextArea(summary.toString());
        text.setEditable(false);
        text.setOpaque(false);
        text.setRows(Math.min(10, 3 + categoryRows.size()));
        panel.add(text, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel,
            "PayCheck • analiza 12 miesięcy", JOptionPane.PLAIN_MESSAGE);
    }

    private static String friendlyValueStaticCategory(String raw) {
        if ("shopping".equals(raw)) return "Zakupy";
        if ("bills".equals(raw)) return "Rachunki";
        if ("home".equals(raw)) return "Dom";
        if ("vehicle".equals(raw)) return "Pojazdy";
        if ("salary".equals(raw)) return "Wynagrodzenie";
        if ("food".equals(raw)) return "Żywność";
        if ("household".equals(raw)) return "Domowe";
        if ("beauty".equals(raw)) return "Higiena";
        if ("pet".equals(raw)) return "Zwierzęta";
        if ("other".equals(raw) || raw == null || raw.isBlank()) return "Inne";
        return raw;
    }

    private void showPaycheckGoals() {
        while (true) {
            java.util.List<JsonObject> goals = new ArrayList<>();
            DefaultListModel<Choice> model = new DefaultListModel<>();
            for (JsonElement element : table("paycheck_goals")) {
                if (!element.isJsonObject()) continue;
                JsonObject goal = element.getAsJsonObject();
                if (!"shared".equals(value(goal, "scope"))) continue;
                long id;
                long target;
                try {
                    id = goal.get("id").getAsLong();
                    target = goal.get("target_grosz").getAsLong();
                } catch (Exception invalid) { continue; }
                long saved = allocatedToGoal(id);
                goals.add(goal);
                model.addElement(new Choice(Long.toString(id),
                    value(goal, "name") + " • " + money(Long.toString(saved))
                        + " / " + money(Long.toString(target))));
            }

            JList<Choice> list = new JList<>(model);
            list.setVisibleRowCount(Math.min(10, Math.max(3, model.size())));
            if (!model.isEmpty()) list.setSelectedIndex(0);
            JScrollPane pane = new JScrollPane(list);
            pane.setPreferredSize(new Dimension(620, 260));

            Object[] actions = {"Nowy cel", "Odłóż na cel", "Zamknij"};
            int action = JOptionPane.showOptionDialog(this, pane,
                "PayCheck • wspólne cele oszczędnościowe",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, actions, actions[0]);
            if (action == 0) {
                if (createPaycheckGoal()) showSection("PayCheck");
            } else if (action == 1) {
                Choice selected = list.getSelectedValue();
                if (selected == null) {
                    JOptionPane.showMessageDialog(this, "Najpierw wybierz cel.");
                    continue;
                }
                if (allocatePaycheckGoal(Long.parseLong(selected.value)))
                    showSection("PayCheck");
            } else return;
        }
    }

    private boolean createPaycheckGoal() {
        JTextField name = new JTextField();
        JTextField amount = new JTextField();
        JPanel form = new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Nazwa celu:")); form.add(name);
        form.add(new JLabel("Kwota celu [PLN]:")); form.add(amount);
        int ok = JOptionPane.showConfirmDialog(this, form,
            "Nowy wspólny cel", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return false;
        String title = name.getText().trim();
        if (title.isEmpty() || title.length() > 120) {
            JOptionPane.showMessageDialog(this, "Nazwa celu: 1–120 znaków.");
            return false;
        }
        try {
            long target = parsePln(amount.getText());
            JsonObject goal = new JsonObject();
            goal.addProperty("id", nextId("paycheck_goals"));
            goal.addProperty("scope", "shared");
            goal.addProperty("name", title);
            goal.addProperty("target_grosz", target);
            goal.addProperty("created_at", System.currentTimeMillis());
            mutableTable("paycheck_goals").add(goal);
            markDirty();
            return true;
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, rootMessage(error));
            return false;
        }
    }

    private boolean allocatePaycheckGoal(long goalId) {
        JsonObject goal = scannerRowById("paycheck_goals", goalId);
        if (goal == null) return false;
        long target;
        try { target = goal.get("target_grosz").getAsLong(); }
        catch (Exception invalid) { return false; }
        long saved = allocatedToGoal(goalId);
        long remaining = Math.max(0L, target - saved);
        if (remaining == 0) {
            JOptionPane.showMessageDialog(this, "Ten cel jest już osiągnięty.");
            return false;
        }
        String raw = JOptionPane.showInputDialog(this,
            value(goal, "name") + "\nPozostało: " + money(Long.toString(remaining))
                + "\n\nKwota do odłożenia [PLN]:");
        if (raw == null) return false;
        try {
            long amount = parsePln(raw);
            if (amount > remaining)
                throw new IllegalArgumentException(
                    "Kwota przekracza pozostałą wartość celu.");
            JsonObject allocation = new JsonObject();
            allocation.addProperty("id", nextId("paycheck_goal_allocations"));
            allocation.addProperty("operation_id", java.util.UUID.randomUUID().toString());
            allocation.addProperty("goal_id", goalId);
            allocation.addProperty("amount_grosz", amount);
            allocation.addProperty("created_at", System.currentTimeMillis());
            mutableTable("paycheck_goal_allocations").add(allocation);
            markDirty();
            return true;
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, rootMessage(error));
            return false;
        }
    }

    private long allocatedToGoal(long goalId) {
        long total = 0L;
        for (JsonElement element : table("paycheck_goal_allocations")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            try {
                if (row.get("goal_id").getAsLong() == goalId)
                    total += row.get("amount_grosz").getAsLong();
            } catch (Exception ignored) { }
        }
        return total;
    }

    private void showPaycheckBulkEdit() {
        java.util.List<JsonObject> rows = new ArrayList<>();
        DefaultListModel<Choice> model = new DefaultListModel<>();
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))) continue;
            rows.add(tx);
            model.addElement(new Choice(Integer.toString(rows.size()-1),
                ("expense".equals(value(tx, "kind")) ? "− " : "+ ")
                    + money(value(tx, "amount_grosz")) + " • "
                    + value(tx, "note") + " • "
                    + friendlyValueStaticCategory(value(tx, "category"))));
        }
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Brak transakcji do edycji.");
            return;
        }

        JList<Choice> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(Math.min(14, model.size()));
        JScrollPane pane = new JScrollPane(list);
        pane.setPreferredSize(new Dimension(760, 340));
        int ok = JOptionPane.showConfirmDialog(this, pane,
            "Zaznacz transakcje do masowej edycji",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION || list.getSelectedIndices().length == 0) return;

        String[][] categories = {
            {"shopping","Zakupy"},{"bills","Rachunki"},{"home","Dom"},
            {"vehicle","Pojazdy"},{"salary","Wynagrodzenie"},{"food","Żywność"},
            {"household","Domowe"},{"beauty","Higiena"},{"pet","Zwierzęta"},
            {"other","Inne"}
        };
        Choice[] choices = new Choice[categories.length];
        for (int i=0; i<categories.length; i++)
            choices[i] = new Choice(categories[i][0], categories[i][1]);
        Choice category = (Choice) JOptionPane.showInputDialog(this,
            "Nowa kategoria dla " + list.getSelectedIndices().length + " pozycji:",
            "PayCheck • masowa edycja", JOptionPane.QUESTION_MESSAGE,
            null, choices, choices[0]);
        if (category == null) return;

        int changed = 0;
        for (int index : list.getSelectedIndices()) {
            Choice selected = model.get(index);
            JsonObject tx = rows.get(Integer.parseInt(selected.value));
            tx.addProperty("category", category.value);
            changed++;
        }
        if (changed > 0) {
            markDirty();
            showSection("PayCheck");
            JOptionPane.showMessageDialog(this,
                "Zmieniono kategorię dla " + changed + " transakcji.");
        }
    }

    private static long parsePln(String raw) {
        String text = raw == null ? "" : raw.trim().replace(" ","").replace(',', '.');
        if (!text.matches("[0-9]{1,9}(?:[.][0-9]{1,2})?"))
            throw new IllegalArgumentException("Podaj poprawną kwotę w PLN.");
        java.math.BigDecimal value = new java.math.BigDecimal(text)
            .setScale(2, java.math.RoundingMode.UNNECESSARY);
        long grosz = value.movePointRight(2).longValueExact();
        if (grosz < 1 || grosz > 99_999_999_999L)
            throw new IllegalArgumentException("Kwota poza zakresem.");
        return grosz;
    }

    private void showPrivatePaycheck() {
        DesktopPrivatePaycheckVault.Session session = null;
        try {
            session = openPrivatePaycheckSession();
            if (session == null) return;

            while (session.active()) {
                java.util.List<DesktopPrivatePaycheckVault.Entry> entries =
                    DesktopPrivatePaycheckVault.entries(session);
                DefaultListModel<Choice> model = new DefaultListModel<>();
                long balance = 0L;
                int pending = 0;
                for (int i=0; i<entries.size(); i++) {
                    DesktopPrivatePaycheckVault.Entry entry = entries.get(i);
                    if ("pending".equals(entry.status)) pending++;
                    else balance += "income".equals(entry.kind)
                        ? entry.grosz : -entry.grosz;
                    String label = ("pending".equals(entry.status) ? "OCZEKUJE • " : "")
                        + ("income".equals(entry.kind) ? "+ " : "− ")
                        + money(Long.toString(entry.grosz)) + " • "
                        + friendlyValueStaticCategory(entry.category) + " • "
                        + entry.note + " • "
                        + timeValue(Long.toString(entry.date));
                    model.addElement(new Choice(Integer.toString(i), label));
                }

                JList<Choice> list = new JList<>(model);
                list.setVisibleRowCount(Math.min(12, Math.max(4, model.size())));
                if (!model.isEmpty()) list.setSelectedIndex(0);
                JScrollPane pane = new JScrollPane(list);
                pane.setPreferredSize(new Dimension(760, 320));

                JPanel view = new JPanel(new BorderLayout(0,8));
                JLabel summary = new JLabel("Saldo potwierdzone: "
                    + money(Long.toString(balance))
                    + "   •   oczekujące: " + pending
                    + "   •   wpisy: " + entries.size());
                view.add(summary, BorderLayout.NORTH);
                view.add(pane, BorderLayout.CENTER);

                Object[] actions = {
                    "Dodaj prywatny wpis",
                    "Potwierdź oczekującą",
                    "Prywatne operacje z banku",
                    "Eksportuj zaszyfrowaną kopię",
                    "Zamknij"
                };
                int action = JOptionPane.showOptionDialog(this, view,
                    "Prywatny PayCheck • zaszyfrowany lokalnie",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, actions, actions[0]);
                if (action == 0) addPrivatePaycheckEntry(session);
                else if (action == 1) confirmPrivatePending(session);
                else if (action == 2) importPrivateBankEvidence(session);
                else if (action == 3) exportPrivatePaycheck();
                else break;
            }
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Prywatny PayCheck: " + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (session != null) session.close();
        }
    }

    private DesktopPrivatePaycheckVault.Session openPrivatePaycheckSession()
            throws Exception {
        if (!DesktopPrivatePaycheckVault.configured()) {
            Object[] setup = {
                "Utwórz pusty sejf",
                "Importuj zaszyfrowaną kopię z telefonu",
                "Anuluj"
            };
            int choice = JOptionPane.showOptionDialog(this,
                "Prywatny PayCheck nie jest jeszcze skonfigurowany na tym komputerze.\n"
                    + "Nie trafia do zwykłej synchronizacji Android ↔ PC.",
                "Prywatny PayCheck", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, setup, setup[0]);
            if (choice == 0) {
                JPasswordField first = new JPasswordField();
                JPasswordField second = new JPasswordField();
                JPanel form = new JPanel(new GridLayout(0,2,8,8));
                form.add(new JLabel("Hasło kopii/sejfu (12–64):")); form.add(first);
                form.add(new JLabel("Powtórz hasło:")); form.add(second);
                int ok = JOptionPane.showConfirmDialog(this, form,
                    "Utwórz prywatny sejf", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) return null;
                char[] a = first.getPassword();
                char[] b = second.getPassword();
                try {
                    if (!java.util.Arrays.equals(a,b))
                        throw new IllegalArgumentException("Hasła nie są takie same.");
                    return DesktopPrivatePaycheckVault.create(a);
                } finally {
                    java.util.Arrays.fill(a,'\0');
                    java.util.Arrays.fill(b,'\0');
                }
            }
            if (choice == 1) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Wybierz zaszyfrowaną kopię prywatnego PayCheck");
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
                char[] password = askPrivatePassword(
                    "Hasło zaszyfrowanej kopii z telefonu");
                if (password == null) return null;
                try {
                    return DesktopPrivatePaycheckVault.importArchive(
                        chooser.getSelectedFile().toPath(), password);
                } finally {
                    java.util.Arrays.fill(password,'\0');
                }
            }
            return null;
        }

        char[] password = askPrivatePassword("Odblokuj prywatny PayCheck");
        if (password == null) return null;
        try {
            return DesktopPrivatePaycheckVault.unlock(password);
        } finally {
            java.util.Arrays.fill(password,'\0');
        }
    }

    private char[] askPrivatePassword(String title) {
        JPasswordField field = new JPasswordField();
        JPanel panel = new JPanel(new BorderLayout(0,8));
        panel.add(new JLabel("Hasło prywatnego sejfu / kopii:"), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(this, panel, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return ok == JOptionPane.OK_OPTION ? field.getPassword() : null;
    }

    private void addPrivatePaycheckEntry(DesktopPrivatePaycheckVault.Session session)
            throws Exception {
        Choice[] kinds = {
            new Choice("expense","Wydatek"),
            new Choice("income","Wpływ")
        };
        Choice[] categories = privateCategoryChoices();
        JComboBox<Choice> kind = new JComboBox<>(kinds);
        JComboBox<Choice> category = new JComboBox<>(categories);
        JTextField amount = new JTextField();
        JTextField note = new JTextField();
        JPanel form = new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Typ:")); form.add(kind);
        form.add(new JLabel("Kategoria:")); form.add(category);
        form.add(new JLabel("Kwota [PLN]:")); form.add(amount);
        form.add(new JLabel("Opis:")); form.add(note);
        int ok = JOptionPane.showConfirmDialog(this, form,
            "Nowy prywatny wpis", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        Choice k = (Choice)kind.getSelectedItem();
        Choice cat = (Choice)category.getSelectedItem();
        if (k == null || cat == null) return;
        long grosz = parsePln(amount.getText());
        String description = note.getText().trim();
        if (description.length() > 160)
            throw new IllegalArgumentException("Opis prywatny może mieć maks. 160 znaków.");
        DesktopPrivatePaycheckVault.add(session, k.value, cat.value,
            grosz, description, "confirmed");
    }

    private void confirmPrivatePending(DesktopPrivatePaycheckVault.Session session)
            throws Exception {
        java.util.List<DesktopPrivatePaycheckVault.Entry> pending = new ArrayList<>();
        java.util.List<Choice> choices = new ArrayList<>();
        for (DesktopPrivatePaycheckVault.Entry entry :
                DesktopPrivatePaycheckVault.entries(session)) {
            if (!"pending".equals(entry.status)) continue;
            pending.add(entry);
            choices.add(new Choice(entry.id,
                ("income".equals(entry.kind) ? "+ " : "− ")
                    + money(Long.toString(entry.grosz)) + " • "
                    + entry.note + " • " + timeValue(Long.toString(entry.date))));
        }
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Brak prywatnych wpisów oczekujących.");
            return;
        }
        Choice selected = (Choice) JOptionPane.showInputDialog(this,
            "Wybierz wpis do potwierdzenia:",
            "Prywatny PayCheck", JOptionPane.QUESTION_MESSAGE,
            null, choices.toArray(), choices.get(0));
        if (selected != null)
            DesktopPrivatePaycheckVault.confirm(session, selected.value);
    }

    private void importPrivateBankEvidence(
            DesktopPrivatePaycheckVault.Session session) throws Exception {
        int imported = 0;
        for (JsonElement element : table("bank_evidence_queue")) {
            if (!element.isJsonObject()) continue;
            JsonObject evidence = element.getAsJsonObject();
            if (!"open".equals(value(evidence, "state"))) continue;
            String key = value(evidence, "evidence_key");
            if (!"private".equals(PREFS.get("bank.scope." + key, ""))) continue;

            String deterministic = java.util.UUID.nameUUIDFromBytes(
                ("EDHOME_PRIVATE_BANK:" + key)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            DesktopPrivatePaycheckVault.addWithId(session, deterministic,
                value(evidence, "kind"), "other",
                Long.parseLong(value(evidence, "amount_grosz")),
                value(evidence, "description"), "pending");
            evidence.addProperty("state", "dismissed");
            evidence.add("matched_operation_id", com.google.gson.JsonNull.INSTANCE);
            evidence.add("matched_at", com.google.gson.JsonNull.INSTANCE);
            PREFS.remove("bank.scope." + key);
            imported++;
        }
        if (imported > 0) {
            markDirty();
            JOptionPane.showMessageDialog(this,
                "Dodano do prywatnego PayCheck jako oczekujące: " + imported);
        } else JOptionPane.showMessageDialog(this,
            "Brak prywatnych operacji bankowych oczekujących na przeniesienie.");
    }

    private void exportPrivatePaycheck() throws Exception {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Eksportuj zaszyfrowaną kopię prywatnego PayCheck");
        chooser.setSelectedFile(new java.io.File(
            "EDHOME-private-paycheck-encrypted.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        DesktopPrivatePaycheckVault.exportArchive(target);
        JOptionPane.showMessageDialog(this,
            "Zapisano zaszyfrowaną kopię:\n" + target.toAbsolutePath());
    }

    private static Choice[] privateCategoryChoices() {
        return new Choice[]{
            new Choice("shopping","Zakupy"),
            new Choice("bills","Rachunki"),
            new Choice("home","Dom"),
            new Choice("vehicle","Pojazdy"),
            new Choice("salary","Wynagrodzenie"),
            new Choice("food","Żywność"),
            new Choice("household","Domowe"),
            new Choice("beauty","Higiena"),
            new Choice("pet","Zwierzęta"),
            new Choice("other","Inne")
        };
    }

    private void importBankStatement() {
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this,
                "Najpierw połącz Desktop z EDHOME na telefonie.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Wybierz wyciąg bankowy PDF / CSV / XLSX");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();

        String labelHint = JOptionPane.showInputDialog(this,
            "Nazwa banku / konta.\n"
                + "mBank i VeloBank są rozpoznawane automatycznie; możesz dopisać np. „konto wspólne”.",
            PREFS.get("bankImportLabel", ""));
        if (labelHint == null) return;
        final String hint = labelHint.trim();

        connection.setText("IMPORT BANKU…");
        new SwingWorker<DesktopBankImporter.Result,Void>() {
            @Override protected DesktopBankImporter.Result doInBackground() throws Exception {
                return DesktopBankImporter.read(file, hint);
            }

            @Override protected void done() {
                try {
                    DesktopBankImporter.Result result = get();
                    String sourceLabel = hint.isBlank() ? result.bank : hint;
                    if (sourceLabel.isBlank()) sourceLabel = result.bank;
                    if (sourceLabel.length() > 80)
                        throw new IllegalArgumentException(
                            "Nazwa banku/konta może mieć maksymalnie 80 znaków.");
                    PREFS.put("bankImportLabel", sourceLabel);

                    Object[] scopes = {
                        "Wspólne — brakujące utwórz jako oczekujące",
                        "Prywatne — zachowaj do prywatnego PayCheck",
                        "Tylko kolejka — zdecyduję później"
                    };
                    int scope = JOptionPane.showOptionDialog(EdhomeDesktop.this,
                        "Rozpoznano: " + result.bank + "\n"
                            + "Operacji: " + result.entries.size() + "\n\n"
                            + "Jak potraktować ten import?",
                        "EDHOME Desktop • PayCheck", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, scopes, scopes[0]);
                    if (scope < 0) return;

                    BankIngestSummary summary = ingestBankEvidence(
                        file, result, sourceLabel, scope);
                    showSection("PayCheck");
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Import zakończony.\n"
                            + "Nowe dowody: " + summary.inserted + "\n"
                            + "Duplikaty pominięte: " + summary.duplicates + "\n"
                            + "Pasujące oczekujące wpisy: " + summary.suggestions + "\n"
                            + "Nowe oczekujące wpisy wspólne: " + summary.pendingCreated
                            + (summary.archivedPdf == null ? ""
                                : "\nPDF zachowany lokalnie:\n" + summary.archivedPdf),
                        "EDHOME Desktop • PayCheck", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie zaimportowano wyciągu:\n" + rootMessage(error),
                        "EDHOME Desktop • PayCheck", JOptionPane.ERROR_MESSAGE);
                } finally {
                    connection.setText(connected
                        ? "ONLINE • dane zsynchronizowane" : "OFFLINE • lokalna kopia");
                }
            }
        }.execute();
    }

    private BankIngestSummary ingestBankEvidence(Path file,
            DesktopBankImporter.Result result, String sourceLabel, int scope)
            throws Exception {
        int inserted = 0, duplicates = 0, suggestions = 0, pendingCreated = 0;
        JsonArray queue = mutableTable("bank_evidence_queue");

        for (DesktopBankImporter.Entry entry : result.entries) {
            if (bankEvidenceExists(entry.evidenceKey)) {
                duplicates++;
                continue;
            }

            JsonObject row = new JsonObject();
            row.addProperty("id", nextId("bank_evidence_queue"));
            row.addProperty("evidence_key", entry.evidenceKey);
            row.addProperty("source_kind", result.sourceKind);
            row.addProperty("source_label", sourceLabel);
            row.addProperty("kind", entry.kind);
            row.addProperty("amount_grosz", entry.amountGrosz);
            row.addProperty("booking_date", entry.date);
            row.addProperty("description", entry.description);
            row.addProperty("imported_at", System.currentTimeMillis());
            row.addProperty("state", "open");
            row.add("matched_operation_id", com.google.gson.JsonNull.INSTANCE);
            row.add("matched_at", com.google.gson.JsonNull.INSTANCE);
            queue.add(row);
            inserted++;

            java.util.List<JsonObject> matches = pendingMatches(row);
            if (!matches.isEmpty()) suggestions++;

            if (scope == 0 && matches.isEmpty()) {
                createPendingFromEvidence(row);
                pendingCreated++;
            } else if (scope == 1) {
                PREFS.put("bank.scope." + entry.evidenceKey, "private");
            }
        }

        Path archived = DesktopBankImporter.archiveOriginalPdf(
            file, result.originalSha256);
        if (inserted > 0 || pendingCreated > 0) markDirty();
        appendBankImportHistory(sourceLabel, file, result.originalSha256,
            inserted, duplicates, archived);
        return new BankIngestSummary(inserted, duplicates, suggestions,
            pendingCreated, archived);
    }

    private boolean bankEvidenceExists(String key) {
        for (JsonElement element : table("bank_evidence_queue")) {
            if (element.isJsonObject()
                    && key.equals(value(element.getAsJsonObject(), "evidence_key")))
                return true;
        }
        for (JsonElement element : table("paycheck_transactions")) {
            if (element.isJsonObject()
                    && key.equals(value(element.getAsJsonObject(), "statement_key")))
                return true;
        }
        return false;
    }

    private java.util.List<JsonObject> pendingMatches(JsonObject evidence) {
        java.util.List<JsonObject> matches = new ArrayList<>();
        String kind = value(evidence, "kind");
        String amount = value(evidence, "amount_grosz");
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))
                    || !"pending".equals(value(tx, "status"))
                    || !kind.equals(value(tx, "kind"))
                    || !amount.equals(value(tx, "amount_grosz")))
                continue;
            matches.add(tx);
        }
        return matches;
    }

    private JsonObject createPendingFromEvidence(JsonObject evidence) {
        JsonObject tx = new JsonObject();
        tx.addProperty("id", nextId("paycheck_transactions"));
        tx.addProperty("operation_id", java.util.UUID.randomUUID().toString());
        tx.addProperty("scope", "shared");
        tx.addProperty("kind", value(evidence, "kind"));
        tx.addProperty("category", "other");
        tx.addProperty("amount_grosz",
            Long.parseLong(value(evidence, "amount_grosz")));
        tx.addProperty("note", value(evidence, "description"));
        tx.addProperty("created_at", System.currentTimeMillis());
        tx.addProperty("status", "pending");
        tx.addProperty("confirmation_source", "none");
        tx.add("confirmed_at", com.google.gson.JsonNull.INSTANCE);
        tx.add("statement_key", com.google.gson.JsonNull.INSTANCE);
        tx.add("statement_date", com.google.gson.JsonNull.INSTANCE);
        mutableTable("paycheck_transactions").add(tx);
        return tx;
    }

    private void showBankEvidenceQueue() {
        java.util.List<JsonObject> openRows = new ArrayList<>();
        DefaultListModel<Choice> model = new DefaultListModel<>();
        for (JsonElement element : table("bank_evidence_queue")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (!"open".equals(value(row, "state"))) continue;
            openRows.add(row);
            int candidates = pendingMatches(row).size();
            String shown = value(row, "booking_date") + " • "
                + ("expense".equals(value(row, "kind")) ? "Wydatek " : "Wpływ ")
                + money(value(row, "amount_grosz")) + " • "
                + value(row, "description") + " • " + value(row, "source_label")
                + (candidates == 0 ? " • brak dopasowania"
                    : " • pasujących: " + candidates);
            model.addElement(new Choice(Integer.toString(openRows.size()-1), shown));
        }
        if (openRows.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Brak otwartych pozycji bankowych do potwierdzenia.");
            return;
        }

        JList<Choice> list = new JList<>(model);
        list.setVisibleRowCount(Math.min(14, model.size()));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        JScrollPane pane = new JScrollPane(list);
        pane.setPreferredSize(new Dimension(820, 360));

        int ok = JOptionPane.showConfirmDialog(this, pane,
            "Banki i potwierdzenia • wybierz operację",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION || list.getSelectedValue() == null) return;

        int index = Integer.parseInt(list.getSelectedValue().value);
        JsonObject evidence = openRows.get(index);
        Object[] actions = {
            "Dopasuj i potwierdź",
            "Utwórz brakujący oczekujący",
            "Odrzuć dowód",
            "Zamknij"
        };
        int action = JOptionPane.showOptionDialog(this,
            value(evidence, "description") + "\n"
                + value(evidence, "booking_date") + " • "
                + money(value(evidence, "amount_grosz")),
            "EDHOME Desktop • bank", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, actions, actions[0]);

        if (action == 0) matchBankEvidence(evidence);
        else if (action == 1) {
            java.util.List<JsonObject> existing = pendingMatches(evidence);
            if (!existing.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Istnieje już oczekujący wpis o tej kwocie i typie. "
                        + "Użyj „Dopasuj i potwierdź”, aby uniknąć duplikatu.");
            } else {
                createPendingFromEvidence(evidence);
                markDirty();
                JOptionPane.showMessageDialog(this,
                    "Utworzono oczekujący wpis wspólny. Nie zmienia salda do czasu potwierdzenia.");
            }
        } else if (action == 2) {
            evidence.addProperty("state", "dismissed");
            evidence.add("matched_operation_id", com.google.gson.JsonNull.INSTANCE);
            evidence.add("matched_at", com.google.gson.JsonNull.INSTANCE);
            markDirty();
        }
        showSection("PayCheck");
    }

    private void matchBankEvidence(JsonObject evidence) {
        java.util.List<JsonObject> matches = pendingMatches(evidence);
        if (matches.isEmpty()) {
            int create = JOptionPane.showConfirmDialog(this,
                "Nie ma pasującego oczekującego wpisu. Utworzyć go teraz?",
                "EDHOME Desktop • PayCheck", JOptionPane.YES_NO_OPTION);
            if (create != JOptionPane.YES_OPTION) return;
            matches.add(createPendingFromEvidence(evidence));
        }

        Choice[] options = new Choice[matches.size()];
        for (int i=0; i<matches.size(); i++) {
            JsonObject tx = matches.get(i);
            options[i] = new Choice(value(tx, "operation_id"),
                money(value(tx, "amount_grosz")) + " • "
                    + value(tx, "note") + " • "
                    + timeValue(value(tx, "created_at")));
        }
        Choice selected = (Choice) JOptionPane.showInputDialog(this,
            "Wybierz oczekujący wpis do potwierdzenia:",
            "EDHOME Desktop • dopasowanie banku",
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (selected == null) return;

        JsonObject tx = null;
        for (JsonObject candidate : matches)
            if (selected.value.equals(value(candidate, "operation_id"))) {
                tx = candidate;
                break;
            }
        if (tx == null) return;

        long now = System.currentTimeMillis();
        tx.addProperty("status", "confirmed");
        tx.addProperty("confirmation_source", "manual");
        tx.addProperty("confirmed_at", now);
        tx.addProperty("statement_key", value(evidence, "evidence_key"));
        tx.addProperty("statement_date", value(evidence, "booking_date"));
        evidence.addProperty("state", "matched");
        evidence.addProperty("matched_operation_id", value(tx, "operation_id"));
        evidence.addProperty("matched_at", now);
        markDirty();

        JOptionPane.showMessageDialog(this,
            "Dopasowano i potwierdzono wpis. Saldo PayCheck uwzględni go zgodnie z regułami aplikacji.");
    }

    private void appendBankImportHistory(String sourceLabel, Path file, String sha,
            int inserted, int duplicates, Path archived) {
        String line = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault()).format(Instant.now())
            + " • " + sourceLabel + " • " + file.getFileName()
            + " • nowe " + inserted + " • duplikaty " + duplicates
            + (archived == null ? "" : " • PDF zachowany");
        String prior = PREFS.get("bankImportHistory", "");
        StringBuilder out = new StringBuilder(line);
        int kept = 0;
        for (String row : prior.split("\n")) {
            if (row.isBlank()) continue;
            if (++kept > 24) break;
            out.append('\n').append(row);
        }
        PREFS.put("bankImportHistory", out.toString());
    }

    private void showBankImportHistory() {
        String history = PREFS.get("bankImportHistory", "");
        JTextArea area = new JTextArea(history.isBlank()
            ? "Brak importów bankowych na tym komputerze." : history);
        area.setEditable(false);
        area.setRows(18);
        area.setColumns(80);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
            "EDHOME Desktop • historia importów bankowych",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class BankIngestSummary {
        final int inserted;
        final int duplicates;
        final int suggestions;
        final int pendingCreated;
        final Path archivedPdf;

        BankIngestSummary(int inserted, int duplicates, int suggestions,
                int pendingCreated, Path archivedPdf) {
            this.inserted = inserted;
            this.duplicates = duplicates;
            this.suggestions = suggestions;
            this.pendingCreated = pendingCreated;
            this.archivedPdf = archivedPdf;
        }
    }

    private JComponent calendar() {
        java.util.List<DesktopCalendarPanel.Event> events = new ArrayList<>();

        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject task = element.getAsJsonObject();
            String due = value(task, "due_date");
            if (due.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(due);
                long id = task.get("id").getAsLong();
                String title = value(task, "title");
                String kind = value(task, "task_kind");
                String subtitle = "waste".equals(kind)
                    ? "Odpady"
                    : friendlyValue("priority", task);
                String assignee = friendlyValue("assignee_id", task);
                if (!"—".equals(assignee) && !"Nieznane".equals(assignee))
                    subtitle += " • " + assignee;
                events.add(new DesktopCalendarPanel.Event(
                    "task:" + id, date, title, subtitle,
                    intValue(task, "done") == 1, true));
            } catch (Exception ignored) { }
        }

        for (JsonElement element : table("vehicles")) {
            if (!element.isJsonObject()) continue;
            JsonObject vehicle = element.getAsJsonObject();
            long id;
            try { id = vehicle.get("id").getAsLong(); }
            catch (Exception invalid) { continue; }
            String name = value(vehicle, "name");
            if (name.isBlank()) name = value(vehicle, "registration");
            addVehicleCalendarEvent(events, vehicle, id, name,
                "oc_until", "OC");
            addVehicleCalendarEvent(events, vehicle, id, name,
                "inspection_until", "Przegląd");
        }

        JPanel page = page("Kalendarz");
        DesktopCalendarPanel calendar = new DesktopCalendarPanel(
            events,
            this::openCalendarEvent,
            this::addTaskOnCalendarDate,
            () -> printTableReport("Kalendarz", table("tasks"),
                cols("Zadanie","title","Termin","due_date",
                     "Priorytet","priority","Wykonane","done")));
        page.add(calendar, BorderLayout.CENTER);
        return page;
    }

    private void addVehicleCalendarEvent(
            java.util.List<DesktopCalendarPanel.Event> events,
            JsonObject vehicle, long id, String name, String key, String label) {
        String raw = value(vehicle, key);
        if (raw.isBlank()) return;
        try {
            LocalDate date = LocalDate.parse(raw);
            events.add(new DesktopCalendarPanel.Event(
                "vehicle:" + id, date,
                label + " • " + (name.isBlank() ? "Pojazd" : name),
                "Pojazdy", false, true));
        } catch (Exception ignored) { }
    }

    private void openCalendarEvent(String key) {
        if (key == null || !key.contains(":")) return;
        String[] parts = key.split(":", 2);
        long id;
        try { id = Long.parseLong(parts[1]); }
        catch (Exception invalid) { return; }

        if ("task".equals(parts[0])) {
            JsonObject row = scannerRowById("tasks", id);
            if (row == null) return;
            editRow(row, cols(
                "Tytuł","title",
                "Termin","due_date",
                "Priorytet","priority",
                "Wykonane","done",
                "Osoba","assignee_id"));
            return;
        }
        if ("vehicle".equals(parts[0])) {
            JsonObject row = scannerRowById("vehicles", id);
            if (row == null) return;
            editRow(row, cols(
                "Nazwa","name",
                "Rejestracja","registration",
                "Przebieg","mileage",
                "OC do","oc_until",
                "Przegląd do","inspection_until"));
        }
    }

    private void addTaskOnCalendarDate(LocalDate date) {
        JsonObject row = newRowTemplate("tasks");
        if (row == null) return;
        row.addProperty("due_date", date.toString());
        if (!editRow(row, cols(
                "Tytuł","title",
                "Termin","due_date",
                "Priorytet","priority",
                "Osoba","assignee_id"))) return;
        table("tasks").add(row);
        markDirty();
        showSection("Kalendarz");
    }

    private JComponent waste() {
        JsonArray rows = table("tasks");
        JsonArray filtered = new JsonArray();
        for (JsonElement el : rows) {
            JsonObject row = el.getAsJsonObject();
            if ("waste".equalsIgnoreCase(value(row,"task_kind"))
                    || !value(row,"waste_fraction").isBlank()) filtered.add(row);
        }

        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setBackground(APP_BG);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(APP_BG);
        JButton year = actionButton("🗓 Roczny kreator odpadów");
        year.addActionListener(e -> showWasteYearWizard());
        actions.add(year);
        JLabel hint = new JLabel(
            "Styczeń → grudzień • kilka frakcji i kilka terminów w miesiącu");
        hint.setForeground(APP_MUTED);
        actions.add(hint);
        wrapper.add(actions, BorderLayout.NORTH);
        wrapper.add(tablePage("Odpady", filtered,
            cols("Frakcja","waste_fraction","Termin","due_date","Wystawione","done",
                 "Przypomnienie","remind_time")), BorderLayout.CENTER);
        return wrapper;
    }

    private void showWasteYearWizard() {
        JSpinner year = new JSpinner(new SpinnerNumberModel(
            LocalDate.now().getYear(), 2020, 2100, 1));
        JComboBox<Choice> mode = new JComboBox<>(new Choice[]{
            new Choice("pickup", "Wpisuję daty ODBIORU — EDHOME wyliczy dzień wystawienia"),
            new Choice("putout", "Wpisuję bezpośrednio daty WYSTAWIENIA")
        });
        JSpinner leadDays = new JSpinner(new SpinnerNumberModel(1, 0, 7, 1));
        JLabel leadLabel = new JLabel("Wystaw ile dni przed odbiorem:");
        mode.addActionListener(e -> {
            Choice selected = (Choice) mode.getSelectedItem();
            boolean pickup = selected != null && "pickup".equals(selected.value);
            leadDays.setEnabled(pickup);
            leadLabel.setEnabled(pickup);
        });

        JPanel start = new JPanel(new GridLayout(0, 2, 8, 8));
        start.add(new JLabel("Rok harmonogramu:")); start.add(year);
        start.add(new JLabel("Jakie daty wpisujesz:")); start.add(mode);
        start.add(leadLabel); start.add(leadDays);

        int setup = JOptionPane.showConfirmDialog(this, start,
            "EDHOME • roczny kreator odpadów",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (setup != JOptionPane.OK_OPTION) return;

        int selectedYear = ((Number) year.getValue()).intValue();
        Choice selectedMode = (Choice) mode.getSelectedItem();
        boolean pickupMode = selectedMode != null
            && "pickup".equals(selectedMode.value);
        int lead = pickupMode ? ((Number) leadDays.getValue()).intValue() : 0;

        String[] keys = {"mixed","plastic","paper","glass","bio","other"};
        String[] labels = {
            "Zmieszane", "Metale i tworzywa", "Papier", "Szkło", "Bio", "Inne"
        };
        java.util.Map<Integer,java.util.Map<String,String>> months =
            new java.util.LinkedHashMap<>();

        int month = 1;
        while (month <= 12) {
            java.time.YearMonth ym = java.time.YearMonth.of(selectedYear, month);
            String monthName = capitalizeMonth(ym.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL,
                    Locale.forLanguageTag("pl-PL")));

            java.util.Map<String,String> saved =
                months.getOrDefault(month, new java.util.LinkedHashMap<>());
            JCheckBox[] enabled = new JCheckBox[keys.length];
            JTextField[] days = new JTextField[keys.length];

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(4, 4, 4, 4);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;

            JLabel title = new JLabel("<html><b>" + monthName + " "
                + selectedYear + "</b><br>Podaj dni "
                + (pickupMode ? "odbioru" : "wystawienia")
                + ". Możesz wpisać kilka, np. <b>5, 19</b>.</html>");
            g.gridx=0; g.gridy=0; g.gridwidth=2;
            panel.add(title, g);

            for (int i=0; i<keys.length; i++) {
                String prior = saved.getOrDefault(keys[i], "");
                enabled[i] = new JCheckBox(labels[i], !prior.isBlank());
                days[i] = new JTextField(prior, 14);
                days[i].setEnabled(enabled[i].isSelected());
                final int index = i;
                enabled[i].addActionListener(e ->
                    days[index].setEnabled(enabled[index].isSelected()));

                g.gridy=i+1; g.gridwidth=1; g.gridx=0;
                panel.add(enabled[i], g);
                g.gridx=1;
                panel.add(days[i], g);
            }

            Object[] options = month == 1
                ? new Object[]{"Dalej →", "Pomiń miesiąc", "Anuluj"}
                : new Object[]{"← Wstecz", "Dalej →", "Pomiń miesiąc", "Anuluj"};
            int action = JOptionPane.showOptionDialog(this, panel,
                "Odpady " + selectedYear + " • " + monthName + " • "
                    + month + "/12",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[month == 1 ? 0 : 1]);
            if (action < 0 || "Anuluj".equals(String.valueOf(options[action]))) return;
            String chosen = String.valueOf(options[action]);

            if ("← Wstecz".equals(chosen)) {
                month = Math.max(1, month - 1);
                continue;
            }
            if ("Pomiń miesiąc".equals(chosen)) {
                months.remove(month);
                month++;
                continue;
            }

            java.util.Map<String,String> entered = new java.util.LinkedHashMap<>();
            boolean invalid = false;
            for (int i=0; i<keys.length; i++) {
                if (!enabled[i].isSelected()) continue;
                String raw = days[i].getText().trim();
                try {
                    parseWasteMonthDays(selectedYear, month, raw);
                    entered.put(keys[i], raw);
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(this,
                        labels[i] + ": " + rootMessage(error),
                        "EDHOME • " + monthName, JOptionPane.WARNING_MESSAGE);
                    invalid = true;
                    break;
                }
            }
            if (invalid) continue;
            if (entered.isEmpty()) months.remove(month);
            else months.put(month, entered);
            month++;
        }

        java.util.List<WasteScheduleEntry> schedule = new ArrayList<>();
        for (java.util.Map.Entry<Integer,java.util.Map<String,String>> monthEntry
                : months.entrySet()) {
            int monthNumber = monthEntry.getKey();
            for (int i=0; i<keys.length; i++) {
                String raw = monthEntry.getValue().get(keys[i]);
                if (raw == null || raw.isBlank()) continue;
                for (int day : parseWasteMonthDays(selectedYear, monthNumber, raw)) {
                    LocalDate entered = LocalDate.of(selectedYear, monthNumber, day);
                    LocalDate putOut = pickupMode ? entered.minusDays(lead) : entered;
                    schedule.add(new WasteScheduleEntry(
                        keys[i], labels[i], entered, putOut, pickupMode));
                }
            }
        }
        schedule.sort(java.util.Comparator.comparing(entry -> entry.putOutDate));

        if (schedule.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Nie podano żadnego terminu odpadów.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Rok: ").append(selectedYear)
            .append("\nTerminy: ").append(schedule.size())
            .append("\nTryb: ")
            .append(pickupMode
                ? "daty odbioru → wystawienie " + lead + " dni wcześniej"
                : "daty wystawienia")
            .append("\n\n");
        for (int i=0; i<Math.min(22, schedule.size()); i++) {
            WasteScheduleEntry entry = schedule.get(i);
            summary.append("• ")
                .append(entry.putOutDate.format(DateTimeFormatter.ofPattern("dd.MM")))
                .append(" • ").append(entry.label);
            if (entry.pickupMode)
                summary.append(" • odbiór ")
                    .append(entry.enteredDate.format(
                        DateTimeFormatter.ofPattern("dd.MM")));
            summary.append('\n');
        }
        if (schedule.size() > 22)
            summary.append("… i ").append(schedule.size() - 22)
                .append(" kolejnych terminów\n");

        int save = JOptionPane.showConfirmDialog(this,
            summary + "\nZapisać cały harmonogram do Czynności i Kalendarza?",
            "EDHOME • odpady " + selectedYear,
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (save != JOptionPane.YES_OPTION) return;

        int added = 0, duplicates = 0;
        for (WasteScheduleEntry entry : schedule) {
            String due = entry.putOutDate.toString();
            if (wasteTaskExists(entry.fraction, due)) {
                duplicates++;
                continue;
            }
            JsonObject task = newRowTemplate("tasks");
            task.addProperty("id", nextId("tasks"));
            task.addProperty("title", "Wystaw: " + entry.label
                + (entry.pickupMode
                    ? " • odbiór "
                        + entry.enteredDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : ""));
            task.addProperty("done", 0);
            task.addProperty("due_date", due);
            task.addProperty("repeat_rule", "once");
            task.addProperty("repeat_every", 1);
            task.addProperty("priority", "normal");
            task.addProperty("duration_minutes", 5);
            task.addProperty("task_kind", "waste");
            task.addProperty("waste_fraction", entry.fraction);
            task.add("remind_time", com.google.gson.JsonNull.INSTANCE);
            task.addProperty("reminder_lead_days", 0);
            table("tasks").add(task);
            added++;
        }

        if (added > 0) markDirty();
        showSection("Odpady");
        JOptionPane.showMessageDialog(this,
            "Dodano terminów: " + added
                + (duplicates > 0 ? "\nPominięto istniejące: " + duplicates : "")
                + "\n\nHarmonogram jest widoczny także w Kalendarzu.",
            "EDHOME • odpady " + selectedYear,
            JOptionPane.INFORMATION_MESSAGE);
    }

    private static java.util.List<Integer> parseWasteMonthDays(
            int year, int month, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank())
            throw new IllegalArgumentException("Wpisz co najmniej jeden dzień miesiąca.");
        int max = java.time.YearMonth.of(year, month).lengthOfMonth();
        java.util.Set<Integer> unique = new java.util.TreeSet<>();
        for (String token : text.split("[,;\\s]+")) {
            if (token.isBlank()) continue;
            int day;
            try { day = Integer.parseInt(token); }
            catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                    "Wpisuj numery dni, np. 5, 19.");
            }
            if (day < 1 || day > max)
                throw new IllegalArgumentException(
                    "Dzień " + day + " nie istnieje w tym miesiącu.");
            unique.add(day);
        }
        if (unique.isEmpty())
            throw new IllegalArgumentException("Wpisz co najmniej jeden dzień miesiąca.");
        return new ArrayList<>(unique);
    }

    private boolean wasteTaskExists(String fraction, String dueDate) {
        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if ("waste".equals(value(row, "task_kind"))
                    && fraction.equals(value(row, "waste_fraction"))
                    && dueDate.equals(value(row, "due_date")))
                return true;
        }
        return false;
    }

    private static String capitalizeMonth(String text) {
        if (text == null || text.isBlank()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static final class WasteScheduleEntry {
        final String fraction;
        final String label;
        final LocalDate enteredDate;
        final LocalDate putOutDate;
        final boolean pickupMode;

        WasteScheduleEntry(String fraction, String label, LocalDate enteredDate,
                LocalDate putOutDate, boolean pickupMode) {
            this.fraction = fraction;
            this.label = label;
            this.enteredDate = enteredDate;
            this.putOutDate = putOutDate;
            this.pickupMode = pickupMode;
        }
    }


    private JComponent scanner() {
        JPanel page = page("Skaner • QR / kody produktów / NFC");
        JPanel card = new RoundedPanel(APP_SURFACE, 22);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel intro = new JLabel("<html><b>Skanowanie na PC jest pełnoprawną funkcją EDHOME.</b><br>"
            + "Możesz użyć skanera USB działającego jak klawiatura, odczytać QR/kod z obrazu "
            + "albo użyć czytnika NFC zgodnego z Windows PC/SC.</html>");
        intro.setForeground(APP_TEXT);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(intro);
        card.add(Box.createVerticalStrut(14));

        JTextField input = new JTextField();
        input.setToolTipText("Zeskanuj kod czytnikiem USB albo wklej treść QR/kodu");
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(input);
        card.add(Box.createVerticalStrut(10));

        JLabel status = new JLabel("Gotowy do skanowania.");
        status.setForeground(APP_MUTED);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton read = actionButton("Odczytaj kod");
        JButton image = actionButton("QR / kod z obrazu");
        JButton camera = actionButton("Skanuj QR kamerą");
        JButton nfc = actionButton("Skanuj NFC");
        actions.add(read);
        actions.add(image);
        actions.add(camera);
        actions.add(nfc);
        card.add(actions);
        card.add(Box.createVerticalStrut(12));
        card.add(status);

        Runnable process = () -> {
            String raw = input.getText().trim();
            if (raw.isEmpty()) {
                status.setText("Brak kodu.");
                input.requestFocusInWindow();
                return;
            }
            processDesktopScan(raw, status);
            input.selectAll();
            input.requestFocusInWindow();
        };
        read.addActionListener(e -> process.run());
        input.addActionListener(e -> process.run());

        image.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Wybierz obraz z QR lub kodem kreskowym");
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            image.setEnabled(false);
            status.setText("Odczytuję kod z obrazu…");
            new SwingWorker<String,Void>() {
                @Override protected String doInBackground() throws Exception {
                    return DesktopHardwareScanner.readCodeFromImage(
                        chooser.getSelectedFile().toPath());
                }
                @Override protected void done() {
                    image.setEnabled(true);
                    try {
                        String raw = get();
                        input.setText(raw);
                        processDesktopScan(raw, status);
                    } catch (Exception error) {
                        status.setText("Nie udało się odczytać obrazu.");
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Nie znaleziono poprawnego QR/kodu:\n" + rootMessage(error),
                            "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        camera.addActionListener(e -> {
            camera.setEnabled(false);
            status.setText("Uruchamiam kamerę — pokaż QR lub kod do obiektywu…");
            new SwingWorker<String,Void>() {
                @Override protected String doInBackground() throws Exception {
                    return DesktopHardwareScanner.readCodeFromWebcam(Duration.ofSeconds(20));
                }
                @Override protected void done() {
                    camera.setEnabled(true);
                    try {
                        String raw = get();
                        input.setText(raw);
                        processDesktopScan(raw, status);
                    } catch (Exception error) {
                        status.setText("Kamera: brak odczytu.");
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            rootMessage(error), "EDHOME Desktop",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        nfc.addActionListener(e -> {
            nfc.setEnabled(false);
            status.setText("Przyłóż tag do czytnika NFC…");
            new SwingWorker<String,Void>() {
                @Override protected String doInBackground() throws Exception {
                    return DesktopHardwareScanner.readNfcUid(Duration.ofSeconds(15));
                }
                @Override protected void done() {
                    nfc.setEnabled(true);
                    try {
                        String uid = get();
                        input.setText("NFC:" + uid);
                        processNfcUid(uid, status);
                    } catch (Exception error) {
                        status.setText("NFC: brak odczytu.");
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            rootMessage(error), "EDHOME Desktop",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        page.add(card, BorderLayout.NORTH);

        JTextArea help = new JTextArea(
            "Obsługiwane ścieżki:\n"
          + "• skaner USB/bezprzewodowy w trybie klawiatury — EAN/UPC/GTIN i QR,\n"
          + "• QR/kod bezpośrednio z kamery/webcam,\n"
          + "• QR/kod z pliku PNG/JPG lub zrzutu ekranu,\n"
          + "• NFC przez czytnik Windows PC/SC — odczyt UID i przypisanie tagu do "
          + "rzeczy, pudełka, miejsca, produktu albo pojazdu.\n\n"
          + "Kod EDHOME STORAGE otwiera właściwą rzecz/pudełko/miejsce. "
          + "Kod produktu może od razu zmienić stan Spiżarni po potwierdzeniu. "
          + "Nieznany poprawny kod produktu można utworzyć jako nowy produkt.");
        help.setEditable(false);
        help.setOpaque(false);
        help.setForeground(APP_MUTED);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setBorder(new EmptyBorder(18, 4, 4, 4));
        page.add(help, BorderLayout.CENTER);
        SwingUtilities.invokeLater(input::requestFocusInWindow);
        return page;
    }

    private void processDesktopScan(String raw, JLabel status) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) return;
        if (code.regionMatches(true, 0, "NFC:", 0, 4)) {
            processNfcUid(code.substring(4), status);
            return;
        }
        ScannerTarget storage = parseStorageQr(code);
        if (storage != null) {
            openScannedTarget(storage.kind, storage.id,
                "qr:" + storage.kind + ":" + storage.id, status);
            return;
        }
        if (validProductBarcode(code)) {
            processPantryBarcode(code, status);
            return;
        }
        status.setText("Nieznany kod: " + code);
        JOptionPane.showMessageDialog(this,
            "Kod został odczytany, ale EDHOME nie rozpoznaje jeszcze jego typu.\n\n" + code,
            "EDHOME Desktop • Skaner", JOptionPane.INFORMATION_MESSAGE);
    }

    private static ScannerTarget parseStorageQr(String value) {
        final String prefix = "EDHOME:STORAGE:1:";
        if (value == null || !value.startsWith(prefix)) return null;
        String rest = value.substring(prefix.length());
        int colon = rest.indexOf(':');
        if (colon <= 0 || rest.indexOf(':', colon + 1) >= 0) return null;
        String kind = rest.substring(0, colon);
        if (!"thing".equals(kind) && !"box".equals(kind) && !"place".equals(kind))
            return null;
        try {
            long id = Long.parseLong(rest.substring(colon + 1));
            return id > 0 ? new ScannerTarget(kind, id) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void openScannedTarget(String kind, long id, String sourceKey,
            JLabel status) {
        String tableName;
        String sectionName;
        if ("place".equals(kind)) {
            tableName = "places";
            sectionName = "Miejsca";
        } else if ("pantry".equals(kind)) {
            tableName = "pantry";
            sectionName = "Spiżarnia";
        } else if ("vehicle".equals(kind)) {
            tableName = "vehicles";
            sectionName = "Pojazdy";
        } else {
            tableName = "storage_items";
            sectionName = "Magazyn";
        }
        JsonObject row = scannerRowById(tableName, id);
        if (row == null) {
            status.setText("Tag wskazuje obiekt, którego nie ma w aktualnych danych.");
            JOptionPane.showMessageDialog(this,
                "Obiekt #" + id + " (" + kind + ") nie istnieje w aktualnym snapshotcie.",
                "EDHOME Desktop • Skaner", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = value(row, "name");
        if (name.isBlank()) name = value(row, "title");
        if (name.isBlank()) name = "#" + id;
        status.setText("Odczytano: " + name);

        String prefKey = scanDefaultKey(sourceKey);
        String remembered = prefKey.isBlank() ? "" : PREFS.get(prefKey, "");
        if (!remembered.isBlank()
                && performScannedAction(remembered, kind, tableName, sectionName, row, status))
            return;

        java.util.List<String> actions = new ArrayList<>();
        actions.add("Otwórz");
        actions.add("Edytuj");
        if ("thing".equals(kind) || "box".equals(kind)) {
            actions.add("Przenieś");
            actions.add(value(row, "lent_to").isBlank() ? "Wypożycz" : "Zwrot");
            actions.add("QR / etykieta");
        } else if ("place".equals(kind)) {
            actions.add("QR / etykieta");
        }
        actions.add("Ustaw domyślną akcję…");
        actions.add("Anuluj");

        int selected = JOptionPane.showOptionDialog(this,
            "Odczytano: " + name + "\nTyp: " + kind + "\n\nWybierz działanie:",
            "EDHOME Desktop • Skaner", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, actions.toArray(), actions.get(0));
        if (selected < 0 || selected >= actions.size()) return;
        String label = actions.get(selected);
        if ("Anuluj".equals(label)) return;
        if ("Ustaw domyślną akcję…".equals(label)) {
            chooseDefaultScanAction(prefKey, kind, row);
            return;
        }
        performScannedAction(scanActionId(label), kind, tableName,
            sectionName, row, status);
    }

    private boolean performScannedAction(String action, String kind,
            String tableName, String sectionName, JsonObject row, JLabel status) {
        if ("open".equals(action)) {
            showSection(sectionName);
            return true;
        }
        if ("edit".equals(action) || "move".equals(action)) {
            String[][] columns = scannedColumns(kind);
            if (columns.length == 0) return false;
            editRow(row, columns);
            return true;
        }
        if ("lend".equals(action) && ("thing".equals(kind) || "box".equals(kind))) {
            if (value(row, "lent_to").isBlank()) {
                String person = JOptionPane.showInputDialog(this,
                    "Komu wypożyczono?", "EDHOME • wypożyczenie",
                    JOptionPane.QUESTION_MESSAGE);
                if (person == null) return true;
                person = person.trim();
                if (person.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Podaj osobę lub nazwę odbiorcy.");
                    return true;
                }
                row.addProperty("lent_to", person);
                row.addProperty("lent_at", System.currentTimeMillis());
                markDirty();
                status.setText("Wypożyczono: " + value(row, "name") + " → " + person);
            } else {
                row.add("lent_to", com.google.gson.JsonNull.INSTANCE);
                row.add("lent_at", com.google.gson.JsonNull.INSTANCE);
                markDirty();
                status.setText("Zwrot: " + value(row, "name"));
            }
            showSection("Magazyn");
            return true;
        }
        if ("label".equals(action) && ("thing".equals(kind)
                || "box".equals(kind) || "place".equals(kind))) {
            DesktopQrLabels.Label label = qrLabel(tableName, row);
            if (label != null) showQrLabelWorkflow(java.util.List.of(label));
            return true;
        }
        return false;
    }

    private void chooseDefaultScanAction(String prefKey, String kind, JsonObject row) {
        if (prefKey.isBlank()) return;
        java.util.List<String> labels = new ArrayList<>();
        labels.add("Otwórz");
        labels.add("Edytuj");
        if ("thing".equals(kind) || "box".equals(kind)) {
            labels.add("Przenieś");
            labels.add(value(row, "lent_to").isBlank() ? "Wypożycz / Zwrot" : "Zwrot / Wypożycz");
            labels.add("QR / etykieta");
        } else if ("place".equals(kind)) {
            labels.add("QR / etykieta");
        }
        labels.add("Zawsze pytaj");

        Object chosen = JOptionPane.showInputDialog(this,
            "Domyślna akcja dla tego konkretnego QR/NFC:",
            "EDHOME Desktop • domyślna akcja",
            JOptionPane.QUESTION_MESSAGE, null, labels.toArray(), labels.get(0));
        if (chosen == null) return;
        String label = String.valueOf(chosen);
        if ("Zawsze pytaj".equals(label)) PREFS.remove(prefKey);
        else PREFS.put(prefKey, scanActionId(label));
    }

    private static String scanActionId(String label) {
        if (label.startsWith("Otwórz")) return "open";
        if (label.startsWith("Edytuj")) return "edit";
        if (label.startsWith("Przenieś")) return "move";
        if (label.startsWith("Wypożycz") || label.startsWith("Zwrot")) return "lend";
        if (label.startsWith("QR / etykieta")) return "label";
        return "";
    }

    private static String scanDefaultKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return "";
        String safe = sourceKey.replaceAll("[^A-Za-z0-9:_-]", "_");
        if (safe.length() > 70) safe = safe.substring(0, 70);
        return "scan." + safe;
    }

    private static String[][] scannedColumns(String kind) {
        if ("thing".equals(kind) || "box".equals(kind))
            return cols("Nazwa","name","Typ","kind","Pudełko","parent_box_id",
                "Miejsce","place_id","Wypożyczone","lent_to");
        if ("place".equals(kind))
            return cols("Nazwa","name","Typ","kind","Nadrzędne","parent_id");
        if ("pantry".equals(kind))
            return cols("Produkt","name","Ilość","qty","Kategoria","category");
        if ("vehicle".equals(kind))
            return cols("Nazwa","name","Rejestracja","registration","Przebieg","mileage",
                "OC do","oc_until","Przegląd do","inspection_until");
        return new String[0][0];
    }

    private void processPantryBarcode(String barcode, JLabel status) {
        JsonObject link = scannerPantryLink(barcode);
        if (link == null) {
            int add = JOptionPane.showConfirmDialog(this,
                "Nieznany kod produktu:\n" + barcode
                    + "\n\nDodać nowy produkt do Spiżarni?",
                "EDHOME Desktop • Skaner", JOptionPane.YES_NO_OPTION);
            if (add == JOptionPane.YES_OPTION)
                addUnknownPantryBarcode(barcode, status);
            else
                status.setText("Nieznany kod produktu.");
            return;
        }
        long pantryId;
        try { pantryId = link.get("pantry_id").getAsLong(); }
        catch (Exception invalid) {
            status.setText("Uszkodzone powiązanie kodu.");
            return;
        }
        JsonObject item = scannerRowById("pantry", pantryId);
        if (item == null) {
            status.setText("Kod wskazuje nieistniejący produkt.");
            return;
        }
        String name = value(item, "name");
        int qty = intValue(item, "qty");
        Object[] options = {"Dodaj +1", "Wyjmij −1", "Otwórz Spiżarnię", "Anuluj"};
        int choice = JOptionPane.showOptionDialog(this,
            name + "\nStan: " + qty + "\nKod: " + barcode,
            "EDHOME Desktop • Spiżarnia", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) adjustPantryFromScan(item, barcode, 1, status);
        else if (choice == 1) adjustPantryFromScan(item, barcode, -1, status);
        else if (choice == 2) showSection("Spiżarnia");
    }

    private JsonObject scannerPantryLink(String barcode) {
        for (String candidate : barcodeCandidates(barcode)) {
            for (JsonElement element : table("pantry_barcodes")) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                if (candidate.equals(value(row, "barcode"))) return row;
            }
        }
        return null;
    }

    private static List<String> barcodeCandidates(String barcode) {
        List<String> result = new ArrayList<>();
        if (!validProductBarcode(barcode)) return result;
        result.add(barcode);
        if (barcode.length() == 12) {
            if (validProductBarcode("0" + barcode)) result.add("0" + barcode);
            if (validProductBarcode("00" + barcode)) result.add("00" + barcode);
        } else if (barcode.length() == 13) {
            if (barcode.startsWith("0") && validProductBarcode(barcode.substring(1)))
                result.add(barcode.substring(1));
            if (validProductBarcode("0" + barcode)) result.add("0" + barcode);
        } else if (barcode.length() == 14 && barcode.startsWith("0")) {
            if (validProductBarcode(barcode.substring(1))) result.add(barcode.substring(1));
            if (barcode.startsWith("00") && validProductBarcode(barcode.substring(2)))
                result.add(barcode.substring(2));
        }
        return result;
    }

    private static boolean validProductBarcode(String barcode) {
        if (barcode == null) return false;
        int n = barcode.length();
        if (n != 8 && n != 12 && n != 13 && n != 14) return false;
        int sum = 0;
        for (int i = n - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            char digit = barcode.charAt(i);
            if (digit < '0' || digit > '9') return false;
            sum += (digit - '0') * weight;
        }
        char check = barcode.charAt(n - 1);
        return check >= '0' && check <= '9'
            && (10 - sum % 10) % 10 == check - '0';
    }

    private void addUnknownPantryBarcode(String barcode, JLabel status) {
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this, "Najpierw połącz EDHOME z telefonem.");
            return;
        }
        String name = JOptionPane.showInputDialog(this,
            "Nazwa nowego produktu:", "Nowy produkt", JOptionPane.QUESTION_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty() || name.length() > 160) {
            JOptionPane.showMessageDialog(this, "Nazwa musi mieć 1–160 znaków.");
            return;
        }
        long pantryId = nextId("pantry");
        JsonObject item = new JsonObject();
        item.addProperty("id", pantryId);
        item.addProperty("name", name);
        item.addProperty("qty", 0);
        item.addProperty("category", "other");
        mutableTable("pantry").add(item);

        JsonObject link = new JsonObject();
        link.addProperty("id", nextId("pantry_barcodes"));
        link.addProperty("pantry_id", pantryId);
        link.addProperty("barcode", barcode);
        mutableTable("pantry_barcodes").add(link);

        JsonObject pack = new JsonObject();
        pack.addProperty("pantry_id", pantryId);
        pack.addProperty("unit", "szt.");
        pack.addProperty("size_milli", 1000);
        mutableTable("pantry_packages").add(pack);

        adjustPantryFromScan(item, barcode, 1, status);
    }

    private void adjustPantryFromScan(JsonObject item, String barcode, int delta,
            JLabel status) {
        int before = intValue(item, "qty");
        int after = before + delta;
        if (after < 0) {
            JOptionPane.showMessageDialog(this, "Stan produktu nie może spaść poniżej zera.");
            return;
        }
        item.addProperty("qty", after);
        JsonObject move = new JsonObject();
        move.addProperty("id", nextId("pantry_movements"));
        move.addProperty("operation_id", java.util.UUID.randomUUID().toString());
        move.addProperty("pantry_id", item.get("id").getAsLong());
        move.addProperty("barcode", barcode);
        move.addProperty("name_snapshot", value(item, "name"));
        move.addProperty("kind", delta > 0 ? "ADD" : "TAKE");
        move.addProperty("qty", 1);
        move.addProperty("before_qty", before);
        move.addProperty("after_qty", after);
        move.addProperty("happened_at", System.currentTimeMillis());
        mutableTable("pantry_movements").add(move);
        markDirty();
        status.setText(value(item, "name") + " • stan: " + after
            + (delta > 0 ? " (+1)" : " (−1)"));
    }

    private void processNfcUid(String rawUid, JLabel status) {
        String uid = rawUid == null ? "" : rawUid.replaceAll("[^0-9A-Fa-f]", "")
            .toUpperCase(Locale.ROOT);
        if (uid.length() < 4 || uid.length() > 64) {
            status.setText("Nieprawidłowy UID NFC.");
            return;
        }
        JsonObject link = null;
        for (JsonElement element : table("nfc_links")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (uid.equalsIgnoreCase(value(row, "uid"))) {
                link = row;
                break;
            }
        }
        if (link != null) {
            try {
                openScannedTarget(value(link, "target_kind"),
                    link.get("target_id").getAsLong(), "nfc:" + uid, status);
            } catch (Exception invalid) {
                status.setText("Uszkodzone powiązanie NFC.");
            }
            return;
        }
        status.setText("NFC " + uid + " • nieprzypisany.");
        int bind = JOptionPane.showConfirmDialog(this,
            "Tag NFC " + uid + " nie jest jeszcze przypisany.\nPrzypisać go do obiektu EDHOME?",
            "EDHOME Desktop • NFC", JOptionPane.YES_NO_OPTION);
        if (bind == JOptionPane.YES_OPTION) bindNfcUid(uid, status);
    }

    private void bindNfcUid(String uid, JLabel status) {
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this, "Najpierw połącz EDHOME z telefonem.");
            return;
        }
        int dbVersion = 0;
        try { dbVersion = snapshot.get("databaseVersion").getAsInt(); }
        catch (Exception ignored) { }
        if (dbVersion < 35) {
            JOptionPane.showMessageDialog(this,
                "Telefon ma starszy format danych. Zaktualizuj EDHOME Beta na telefonie, "
                    + "a potem zsynchronizuj ponownie przed przypisaniem NFC.",
                "EDHOME Desktop • NFC", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Choice> choices = new ArrayList<>();
        for (JsonElement e : table("storage_items")) {
            if (!e.isJsonObject()) continue;
            JsonObject row = e.getAsJsonObject();
            String kind = value(row, "kind");
            if (!"thing".equals(kind) && !"box".equals(kind)) continue;
            choices.add(new Choice(kind + ":" + value(row, "id"),
                ("thing".equals(kind) ? "Rzecz • " : "Pudełko • ") + value(row, "name")));
        }
        for (JsonElement e : table("places")) {
            if (!e.isJsonObject()) continue;
            JsonObject row = e.getAsJsonObject();
            choices.add(new Choice("place:" + value(row, "id"),
                "Miejsce • " + value(row, "name")));
        }
        for (JsonElement e : table("pantry")) {
            if (!e.isJsonObject()) continue;
            JsonObject row = e.getAsJsonObject();
            choices.add(new Choice("pantry:" + value(row, "id"),
                "Spiżarnia • " + value(row, "name")));
        }
        for (JsonElement e : table("vehicles")) {
            if (!e.isJsonObject()) continue;
            JsonObject row = e.getAsJsonObject();
            choices.add(new Choice("vehicle:" + value(row, "id"),
                "Pojazd • " + value(row, "name")));
        }
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nie ma obiektu, do którego można przypisać NFC.");
            return;
        }
        JComboBox<Choice> target = new JComboBox<>(choices.toArray(new Choice[0]));
        int answer = JOptionPane.showConfirmDialog(this, target,
            "Przypisz NFC " + uid, JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        Choice selected = (Choice) target.getSelectedItem();
        if (selected == null) return;
        String[] parts = selected.value.split(":", 2);
        if (parts.length != 2) return;
        long id;
        try { id = Long.parseLong(parts[1]); }
        catch (NumberFormatException invalid) { return; }

        JsonObject row = new JsonObject();
        row.addProperty("id", nextId("nfc_links"));
        row.addProperty("uid", uid);
        row.addProperty("target_kind", parts[0]);
        row.addProperty("target_id", id);
        row.addProperty("created_at", System.currentTimeMillis());
        mutableTable("nfc_links").add(row);
        markDirty();
        status.setText("NFC " + uid + " przypisany do: " + selected.label);
    }

    private JsonObject scannerRowById(String tableName, long id) {
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            try {
                if (row.has("id") && !row.get("id").isJsonNull()
                        && row.get("id").getAsLong() == id) return row;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private JsonArray mutableTable(String name) {
        if (snapshot == null || !snapshot.has("tables")
                || !snapshot.get("tables").isJsonObject())
            throw new IllegalStateException("Brak danych EDHOME.");
        JsonObject tables = snapshot.getAsJsonObject("tables");
        JsonElement currentRows = tables.get(name);
        if (currentRows == null || currentRows.isJsonNull()) {
            JsonArray created = new JsonArray();
            tables.add(name, created);
            return created;
        }
        if (!currentRows.isJsonArray())
            throw new IllegalStateException("Nieprawidłowa tabela " + name + ".");
        return currentRows.getAsJsonArray();
    }

    private static final class ScannerTarget {
        final String kind;
        final long id;
        ScannerTarget(String kind, long id) {
            this.kind = kind;
            this.id = id;
        }
    }

    private JComponent settings() {
        JPanel page = page("Ustawienia • połączenie z telefonem");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(APP_BG);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField ip = new JTextField(PREFS.get("phoneIp", ""), 18);
        JTextField token = new JTextField(PREFS.get("token", ""), 18);
        JButton qrPair = new JButton("Pokaż QR do połączenia");
        JButton pull = new JButton("Pobierz ręcznie przez Wi‑Fi");
        JButton importFile = new JButton("Wczytaj backup JSON");
        JButton updateDesktop = new JButton("↻ Aktualizuj EDHOME Desktop — 1 klik  •  " + DESKTOP_VERSION);
        JCheckBox autostart = new JCheckBox("Uruchamiaj EDHOME Desktop razem z Windows");
        autostart.setOpaque(false);
        autostart.setForeground(APP_TEXT);
        autostart.setSelected(isAutostartEnabled());

        JCheckBox startMinimized = new JCheckBox("Po starcie Windows uruchamiaj zminimalizowany do zasobnika");
        startMinimized.setOpaque(false);
        startMinimized.setForeground(APP_TEXT);
        startMinimized.setSelected(PREFS.getBoolean("startMinimized", false));

        JCheckBox autoConnect = new JCheckBox("Automatycznie pobieraj zmiany z telefonu w tle");
        autoConnect.setOpaque(false);
        autoConnect.setForeground(APP_TEXT);
        autoConnect.setSelected(PREFS.getBoolean("autoConnect", true));

        JCheckBox autoWrite = new JCheckBox("Automatycznie zapisuj zmiany z PC do telefonu");
        autoWrite.setOpaque(false);
        autoWrite.setForeground(APP_TEXT);
        autoWrite.setSelected(PREFS.getBoolean("autoWrite", true));
        JLabel help = new JLabel("<html><b>Najszybciej:</b> kliknij „Pokaż QR do połączenia”, "
            + "a na telefonie EDHOME wybierz <b>Ustawienia → Skanuj QR z ekranu PC</b>.<br>"
            + "Telefon i PC muszą być w tej samej sieci Wi‑Fi/LAN. "
            + "Adres i kod poniżej zostają jako awaryjne połączenie ręczne.</html>");
        help.setForeground(APP_MUTED);

        g.gridx=0; g.gridy=0; g.weightx=0; form.add(new JLabel("Adres telefonu:"),g);
        g.gridx=1; g.weightx=1; form.add(ip,g);
        g.gridx=0; g.gridy=1; g.weightx=0; form.add(new JLabel("Kod parowania:"),g);
        g.gridx=1; g.weightx=1; form.add(token,g);
        g.gridx=0; g.gridy=2; g.gridwidth=2; g.weightx=1; form.add(help,g);
        g.gridy=3; g.gridwidth=2; form.add(qrPair,g);
        g.gridy=4; g.gridwidth=1; g.weightx=.5; form.add(pull,g);
        g.gridx=1; form.add(importFile,g);
        g.gridx=0; g.gridy=5; g.gridwidth=2; g.weightx=1; form.add(updateDesktop,g);
        g.gridy=6; form.add(autostart,g);
        g.gridy=7; form.add(startMinimized,g);
        g.gridy=8; form.add(autoConnect,g);
        g.gridy=9; form.add(autoWrite,g);

        qrPair.addActionListener(e -> showQrPairing(ip, token, pull));
        pull.addActionListener(e -> {
            String host = ip.getText().trim();
            String secret = token.getText().trim();
            if (host.isBlank() || secret.isBlank()) {
                JOptionPane.showMessageDialog(this, "Wpisz adres telefonu i kod parowania.");
                return;
            }
            pullFromPhone(host, secret, pull);
        });

        importFile.addActionListener(e -> importBackup());
        updateDesktop.addActionListener(e -> oneClickDesktopUpdate(updateDesktop));
        autostart.addActionListener(e -> {
            boolean wanted = autostart.isSelected();
            try {
                setAutostartEnabled(wanted);
                autostart.setSelected(isAutostartEnabled());
            } catch (Exception error) {
                autostart.setSelected(!wanted);
                JOptionPane.showMessageDialog(this,
                    "Nie udało się zmienić autostartu Windows:\n" + rootMessage(error),
                    "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
            }
        });
        startMinimized.addActionListener(e ->
            PREFS.putBoolean("startMinimized", startMinimized.isSelected()));
        autoConnect.addActionListener(e -> {
            PREFS.putBoolean("autoConnect", autoConnect.isSelected());
            if (autoConnect.isSelected()) autoConnectSaved(true);
        });
        autoWrite.addActionListener(e -> {
            PREFS.putBoolean("autoWrite", autoWrite.isSelected());
            if (autoWrite.isSelected() && dirty) scheduleAutoSave();
        });

        page.add(form, BorderLayout.NORTH);
        JTextArea notes = new JTextArea(
            "Połączenie QR jest jednorazowo potwierdzane losowym kodem i działa tylko w sieci lokalnej.\n"
          + "Po zeskanowaniu Desktop zapisuje adres telefonu oraz kod lokalnego odczytu i od razu pobiera dane.\n\n"
          + "Automatyczna wymiana działa w obie strony: telefon → PC jest odświeżany w tle, "
          + "a zmiany wykonane na PC są automatycznie zapisywane do telefonu po krótkiej chwili.\n"
          + "Zapis nadal używa kontroli wersji SHA-256 — przy równoczesnej zmianie tych samych danych "
          + "Desktop nie nadpisze telefonu po cichu.\n\n"
          + "Kolejny etap:\n"
          + "• synchronizacja przyrostowa rekordów zamiast pełnego snapshotu,\n"
          + "• kolejne operacje modułowe poza już dodanym CRUD zadań, zakupów, magazynu i miejsc,\n"
          + "• pełna zgodność funkcji Android ↔ Desktop.\n"
          + "Pulpit pokazuje zadania na dziś, zakupy i kafle modułów jak EDHOME.\n"
          + "Autostart Windows, zasobnik i ponowne wykrywanie telefonu po zmianie IP pozostają aktywne.");
        notes.setBackground(APP_BG);
        notes.setForeground(APP_MUTED);
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(20, 4, 4, 4));
        page.add(notes, BorderLayout.CENTER);
        return page;
    }

    private void pullFromPhone(String host, String secret, JButton trigger) {
        pullFromPhone(host, secret, trigger, false);
    }

    private void pullFromPhone(String host, String secret, JButton trigger,
            boolean silent) {
        if (connecting || (silent && dirty)) return;
        connecting = true;
        PREFS.put("phoneIp", host);
        PREFS.put("token", secret);
        if (trigger != null) trigger.setEnabled(false);
        connection.setText(silent ? "SYNCHRONIZACJA…" : "ŁĄCZENIE…");

        new SwingWorker<SnapshotResult,Void>() {
            @Override protected SnapshotResult doInBackground() throws Exception {
                Exception first;
                try {
                    return new LanClient(host, PORT, secret).snapshot();
                } catch (Exception error) {
                    first = error;
                }

                SwingUtilities.invokeLater(() ->
                    connection.setText("SZUKAM TELEFONU W SIECI LAN…"));
                String discovered = LanClient.discover(secret, PORT);
                if (discovered != null && !discovered.equals(host)) {
                    PREFS.put("phoneIp", discovered);
                    return new LanClient(discovered, PORT, secret).snapshot();
                }
                throw first;
            }

            @Override protected void done() {
                connecting = false;
                if (trigger != null) trigger.setEnabled(true);
                if (editingDialog) {
                    connection.setText("ONLINE • odświeżę po zakończeniu edycji");
                    return;
                }
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
                    syncedSnapshot = snapshot.deepCopy();
                    phoneRevision = result.revision;
                    lastFullReconcileAt = System.currentTimeMillis();
                    connected = true;
                    dirty = false;
                    validate(snapshot);
                    saveCache(snapshot);
                    connection.setText("ONLINE • Android "
                        + str(snapshot,"sourceVersion",""));
                    connection.setForeground(APP_ACCENT);
                    showSection(current);
                    updateTrayTooltip();
                } catch (Exception ex) {
                    connected = false;
                    connection.setText("OFFLINE • ponawiam w tle");
                    connection.setForeground(new Color(230, 175, 95));
                    updateTrayTooltip();
                    if (!silent) {
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Nie pobrano danych:\n" + rootMessage(ex),
                            "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void autoConnectSaved(boolean silent) {
        if (!PREFS.getBoolean("autoConnect", true) || connecting || dirty || editingDialog) return;
        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank()) return;
        pullFromPhone(host, secret, null, silent);
    }

    private void startReconnectLoop() {
        reconnectTimer = new javax.swing.Timer(10000, e -> {
            if (connecting || autoSaving || editingDialog || stateChecking) return;
            if (dirty && PREFS.getBoolean("autoWrite", true)) {
                saveChangesToPhone(null, true);
                return;
            }
            if (!PREFS.getBoolean("autoConnect", true) || dirty) return;
            if (!connected) {
                autoConnectSaved(true);
                return;
            }
            checkPhoneState();
        });
        reconnectTimer.setInitialDelay(10000);
        reconnectTimer.start();
    }

    private void checkPhoneState() {
        if (stateChecking || connecting || autoSaving || dirty || editingDialog) return;
        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank()) return;

        long now = System.currentTimeMillis();
        if (lastFullReconcileAt > 0 && now - lastFullReconcileAt >= 180000L) {
            pullFromPhone(host, secret, null, true);
            return;
        }

        stateChecking = true;
        new SwingWorker<Long,Void>() {
            @Override protected Long doInBackground() throws Exception {
                return new LanClient(host, PORT, secret).state();
            }
            @Override protected void done() {
                stateChecking = false;
                try {
                    long revision = get();
                    if (revision < 0) return; // starszy Android: pełna kontrola co 3 min
                    if (phoneRevision >= 0 && revision != phoneRevision) {
                        phoneRevision = revision;
                        pullFromPhone(host, secret, null, true);
                    } else {
                        phoneRevision = revision;
                    }
                } catch (Exception ignored) {
                    // Lekki heartbeat nie zmienia stanu offline. Pełny reconnect zrobi pętla.
                }
            }
        }.execute();
    }

    private void startAutoSaveLoop() {
        autoSaveTimer = new javax.swing.Timer(1500, e -> {
            if (PREFS.getBoolean("autoWrite", true) && dirty)
                saveChangesToPhone(null, true);
        });
        autoSaveTimer.setRepeats(false);
    }

    private void scheduleAutoSave() {
        if (!PREFS.getBoolean("autoWrite", true) || autoSaveTimer == null) return;
        autoSaveTimer.restart();
        connection.setText(connected
            ? "ZAPISANO LOKALNIE • synchronizacja za chwilę"
            : "ZAPISANO LOKALNIE • czeka na telefon");
    }

    private void markDirty() {
        dirty = true;
        localEditGeneration++;
        try {
            if (snapshot != null) {
                ensureDesktopSyncMetadata(snapshot);
                saveCache(snapshot);
            }
        } catch (Exception error) {
            connection.setText("BŁĄD • nie zapisano lokalnej kopii");
        }
        scheduleAutoSave();
        if (!PREFS.getBoolean("autoWrite", true))
            connection.setText("ZAPISANO LOKALNIE • synchronizacja ręczna");
    }

    private void initTray() {
        if (!SystemTray.isSupported()) return;
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem show = new MenuItem("Pokaż EDHOME");
            show.addActionListener(e -> SwingUtilities.invokeLater(() -> {
                setVisible(true);
                setState(Frame.NORMAL);
                toFront();
            }));

            MenuItem sync = new MenuItem("Synchronizuj teraz");
            sync.addActionListener(e ->
                SwingUtilities.invokeLater(() -> autoConnectSaved(false)));

            MenuItem settingsItem = new MenuItem("Ustawienia");
            settingsItem.addActionListener(e -> SwingUtilities.invokeLater(() -> {
                setVisible(true);
                setState(Frame.NORMAL);
                showSection("Ustawienia");
                toFront();
            }));

            MenuItem exit = new MenuItem("Zakończ");
            exit.addActionListener(e ->
                SwingUtilities.invokeLater(this::shutdownDesktop));

            menu.add(show);
            menu.add(sync);
            menu.addSeparator();
            menu.add(settingsItem);
            menu.addSeparator();
            menu.add(exit);

            trayIcon = new TrayIcon(createTrayImage(), "EDHOME Desktop", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> SwingUtilities.invokeLater(() -> {
                setVisible(true);
                setState(Frame.NORMAL);
                toFront();
            }));
            SystemTray.getSystemTray().add(trayIcon);
            updateTrayTooltip();
        } catch (Exception ignored) {
            trayIcon = null;
        }
    }

    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(APP_SURFACE);
            g.fillRoundRect(1, 1, 30, 30, 10, 10);
            g.setColor(APP_ACCENT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("ED", 6, 21);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void updateTrayTooltip() {
        if (trayIcon == null) return;
        trayIcon.setToolTip("EDHOME Desktop " + DESKTOP_VERSION
            + (connected ? " • ONLINE" : " • OFFLINE"));
    }

    private void shutdownDesktop() {
        if (reconnectTimer != null) reconnectTimer.stop();
        if (autoSaveTimer != null) autoSaveTimer.stop();
        if (qrPairingSession != null) qrPairingSession.close();
        if (trayIcon != null) {
            try { SystemTray.getSystemTray().remove(trayIcon); }
            catch (Exception ignored) { }
        }
        dispose();
        releaseSingleInstanceLock();
        System.exit(0);
    }

    private void showQrPairing(JTextField ip, JTextField token, JButton pull) {
        if (qrPairingSession != null) {
            qrPairingSession.close();
            qrPairingSession = null;
        }
        final JDialog[] dialog = new JDialog[1];
        final JLabel status = new JLabel("Czekam na skan z telefonu…");
        try {
            QrPairingSession session = QrPairingSession.start(payload ->
                SwingUtilities.invokeLater(() -> {
                    ip.setText(payload.phoneIp);
                    token.setText(payload.token);
                    PREFS.put("phoneIp", payload.phoneIp);
                    PREFS.put("token", payload.token);
                    status.setText("Połączono z Androidem " + payload.version + ".");
                    if (dialog[0] != null) dialog[0].dispose();
                    if (qrPairingSession != null) {
                        qrPairingSession.close();
                        qrPairingSession = null;
                    }
                    pullFromPhone(payload.phoneIp, payload.token, pull);
                }));
            qrPairingSession = session;

            BufferedImage image = qrImage(session.qrText(), 320);
            JLabel qr = new JLabel(new ImageIcon(image));
            qr.setHorizontalAlignment(SwingConstants.CENTER);

            JPanel body = new JPanel(new BorderLayout(10, 10));
            body.setBorder(new EmptyBorder(14, 18, 14, 18));
            JLabel instructions = new JLabel("<html><b>Na telefonie:</b> EDHOME → Ustawienia "
                + "→ Skanuj QR z ekranu PC.<br>QR wygasa po 3 minutach i działa wyłącznie w LAN.</html>");
            body.add(instructions, BorderLayout.NORTH);
            body.add(qr, BorderLayout.CENTER);
            body.add(status, BorderLayout.SOUTH);

            JDialog window = new JDialog(this, "EDHOME • połącz telefon przez QR", false);
            dialog[0] = window;
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setContentPane(body);
            window.pack();
            window.setResizable(false);
            window.setLocationRelativeTo(this);
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (qrPairingSession == session) {
                        qrPairingSession.close();
                        qrPairingSession = null;
                    }
                }
            });
            window.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Nie można przygotować QR do połączenia:\n" + rootMessage(ex)
                    + "\nSprawdź, czy PC jest połączony z tą samą siecią co telefon.",
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static BufferedImage qrImage(String value, int size) throws Exception {
        BitMatrix bits = new MultiFormatWriter().encode(
            value, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = new BufferedImage(
            size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                image.setRGB(x, y, bits.get(x, y) ? 0x000000 : 0xFFFFFF);
        return image;
    }

    private static final String AUTOSTART_REGISTRY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String AUTOSTART_NAME = "EDHOME Desktop Beta";

    private static boolean isAutostartEnabled() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            return false;
        try {
            Process process = new ProcessBuilder("reg", "query", AUTOSTART_REGISTRY,
                "/v", AUTOSTART_NAME)
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0 || !output.contains(AUTOSTART_NAME)) return false;
            Path launcher = desktopLauncher();
            return output.toLowerCase(Locale.ROOT)
                .contains(launcher.toString().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void setAutostartEnabled(boolean enabled) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
            throw new IOException("Autostart jest dostępny tylko w Windows.");

        Process process;
        if (enabled) {
            Path launcher = desktopLauncher();
            String command = "\"" + launcher.toString() + "\"";
            process = new ProcessBuilder("reg", "add", AUTOSTART_REGISTRY,
                "/v", AUTOSTART_NAME, "/t", "REG_SZ", "/d", command, "/f")
                .redirectErrorStream(true)
                .start();
        } else {
            process = new ProcessBuilder("reg", "delete", AUTOSTART_REGISTRY,
                "/v", AUTOSTART_NAME, "/f")
                .redirectErrorStream(true)
                .start();
        }
        String output = new String(process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0 && !( !enabled && code == 1 ))
            throw new IOException("Windows zwrócił błąd " + code + ": " + output.trim());
    }

    private void oneClickDesktopUpdate(JButton trigger) {
        trigger.setEnabled(false);
        connection.setText("AKTUALIZACJA • POBIERANIE 0%");
        new SwingWorker<Path,Void>() {
            @Override protected Path doInBackground() throws Exception {
                Path launcher = desktopLauncher();
                Path installDir = launcher.getParent();
                if (installDir == null)
                    throw new IOException("Nie można ustalić folderu EDHOME Desktop.");

                HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(DESKTOP_UPDATE_URL))
                    .timeout(Duration.ofMinutes(4))
                    .header("Accept", "application/octet-stream")
                    .GET().build();
                HttpResponse<byte[]> response = client.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200)
                    throw new IOException("Serwer aktualizacji odpowiedział HTTP "
                        + response.statusCode() + ".");
                if (response.body().length < 5_000_000)
                    throw new IOException("Pobrana paczka aktualizacji jest niekompletna.");

                SwingUtilities.invokeLater(() ->
                    connection.setText("AKTUALIZACJA • ROZPAKOWYWANIE 55%"));

                Path work = Path.of(System.getProperty("user.home"), ".edhome");
                Files.createDirectories(work);
                Path zip = Files.createTempFile(work, "desktop-update-", ".zip");
                Files.write(zip, response.body());
                Path stage = Files.createTempDirectory(work, "desktop-stage-");
                extractUpdateZip(zip, stage);

                Path stagedExe = stage.resolve("EDHOME-Desktop-Beta.exe");
                if (!Files.isRegularFile(stagedExe))
                    throw new IOException(
                        "Paczka aktualizacji nie zawiera EDHOME-Desktop-Beta.exe.");

                SwingUtilities.invokeLater(() ->
                    connection.setText("AKTUALIZACJA • GOTOWA DO RESTARTU 90%"));

                Path log = work.resolve("desktop-update.log");
                Path script = Files.createTempFile(work, "desktop-updater-", ".cmd");
                long pid = ProcessHandle.current().pid();

                String target = installDir.toAbsolutePath().normalize().toString();
                String exe = installDir.resolve("EDHOME-Desktop-Beta.exe")
                    .toAbsolutePath().normalize().toString();
                String batch =
                    "@echo off\r\n"
                    + "setlocal\r\n"
                    + "set \"LOG=" + batEscape(log.toString()) + "\"\r\n"
                    + "echo [%date% %time%] Start aktualizacji "
                    + DESKTOP_VERSION + ">>\"%LOG%\"\r\n"
                    + ":wait\r\n"
                    + "tasklist /FI \"PID eq " + pid
                    + "\" /FO CSV /NH | findstr /C:\"" + pid
                    + "\" >nul 2>&1\r\n"
                    + "if not errorlevel 1 (\r\n"
                    + "  >nul 2>&1 ping 127.0.0.1 -n 2\r\n"
                    + "  goto wait\r\n"
                    + ")\r\n"
                    + "echo [%date% %time%] Kopiowanie plikow>>\"%LOG%\"\r\n"
                    + "robocopy \"" + batEscape(stage.toString()) + "\" \""
                    + batEscape(target)
                    + "\" /E /COPY:DAT /DCOPY:DAT /R:5 /W:1 /NFL /NDL /NJH /NJS >>\"%LOG%\" 2>&1\r\n"
                    + "set RC=%ERRORLEVEL%\r\n"
                    + "if %RC% GEQ 8 goto fail\r\n"
                    + "if not exist \"" + batEscape(exe) + "\" goto fail\r\n"
                    + "echo [%date% %time%] Restart programu>>\"%LOG%\"\r\n"
                    + "start \"\" /D \"" + batEscape(target) + "\" \""
                    + batEscape(exe) + "\"\r\n"
                    + ">nul 2>&1 ping 127.0.0.1 -n 3\r\n"
                    + "echo [%date% %time%] Restart wydany>>\"%LOG%\"\r\n"
                    + "rmdir /S /Q \"" + batEscape(stage.toString())
                    + "\" >nul 2>&1\r\n"
                    + "del /Q \"" + batEscape(zip.toString())
                    + "\" >nul 2>&1\r\n"
                    + "del /Q \"%~f0\" >nul 2>&1\r\n"
                    + "exit /b 0\r\n"
                    + ":fail\r\n"
                    + "echo [%date% %time%] BLAD aktualizacji, robocopy=%RC%>>\"%LOG%\"\r\n"
                    + "if exist \"" + batEscape(exe)
                    + "\" start \"\" /D \"" + batEscape(target)
                    + "\" \"" + batEscape(exe) + "\"\r\n"
                    + "exit /b 1\r\n";
                Files.writeString(script, batch, StandardCharsets.UTF_8);
                return script;
            }

            @Override protected void done() {
                try {
                    Path script = get();
                    connection.setText("AKTUALIZACJA • RESTART 100%");
                    ProcessBuilder updater = new ProcessBuilder(
                        "cmd.exe", "/d", "/c", "call \"" + script.toString() + "\"");
                    updater.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    updater.redirectError(ProcessBuilder.Redirect.DISCARD);
                    updater.start();
                    dispose();
                    System.exit(0);
                } catch (Exception ex) {
                    trigger.setEnabled(true);
                    connection.setText("ONLINE • aktualizacja nieudana");
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie udało się zaktualizować Desktopu:\n" + rootMessage(ex)
                            + "\n\nObecna wersja pozostaje bez zmian."
                            + "\nLog aktualizacji: "
                            + Path.of(System.getProperty("user.home"),
                                ".edhome", "desktop-update.log"),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void extractUpdateZip(Path zip, Path stage) throws IOException {
        Path root = stage.toAbsolutePath().normalize();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root))
                    throw new IOException("Nieprawidłowa ścieżka w paczce aktualizacji.");
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.copy(input, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                input.closeEntry();
            }
        }
    }

    private static String batEscape(String value) {
        return value.replace("%", "%%").replace("\"", "\"\"");
    }

    private static Path desktopLauncher() throws IOException {
        java.util.List<Path> candidates = new ArrayList<>();

        String appPath = System.getProperty("jpackage.app-path", "").trim();
        if (!appPath.isBlank()) candidates.add(Path.of(appPath));

        String command = ProcessHandle.current().info().command().orElse("").trim();
        if (!command.isBlank()) candidates.add(Path.of(command));

        String userDir = System.getProperty("user.dir", "").trim();
        if (!userDir.isBlank()) candidates.add(Path.of(userDir));

        for (Path candidate : candidates) {
            Path current = Files.isDirectory(candidate)
                ? candidate.toAbsolutePath().normalize()
                : candidate.toAbsolutePath().normalize().getParent();
            for (int depth = 0; current != null && depth < 6; depth++) {
                Path launcher = current.resolve("EDHOME-Desktop-Beta.exe");
                if (Files.isRegularFile(launcher)) return launcher;
                current = current.getParent();
            }
        }
        throw new IOException(
            "Nie znaleziono EDHOME-Desktop-Beta.exe. "
            + "Uruchom Desktop z rozpakowanego folderu programu.");
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private void importBackup() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String json = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            validate(parsed);
            snapshot = parsed;
            snapshotHash = "";
            dirty = false;
            saveCache(parsed);
            connection.setText("OFFLINE • backup " + str(parsed,"sourceVersion",""));
            showSection(current);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Nie wczytano backupu:\n" + rootMessage(ex),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel page(String titleText) {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(APP_BG);
        page.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel(titleText);
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        page.add(title, BorderLayout.NORTH);
        return page;
    }

    private JPanel metric(String title, int value) {
        return metric(title, value, null);
    }

    private JPanel metric(String title, int value, String target) {
        JPanel card = new RoundedPanel(APP_SURFACE, 22);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel name = new JLabel(title);
        name.setForeground(APP_MUTED);
        JLabel number = new JLabel(Integer.toString(value));
        number.setForeground(APP_TEXT);
        number.setFont(number.getFont().deriveFont(Font.BOLD, 30f));
        card.add(name, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        if (target != null) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter open = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    showSection(target);
                }
            };
            card.addMouseListener(open);
            name.addMouseListener(open);
            number.addMouseListener(open);
        }
        return card;
    }

    private JButton quickButton(String label, String target) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setForeground(APP_TEXT);
        button.setBackground(APP_SURFACE_2);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(APP_SURFACE_2),
            new EmptyBorder(12, 14, 12, 14)));
        button.addActionListener(e -> showSection(target));
        return button;
    }

    private JComponent placeholderPage(String title, String description) {
        JPanel page = page(title);
        JTextArea note = new JTextArea(description);
        note.setBackground(APP_BG);
        note.setForeground(APP_MUTED);
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setBorder(new EmptyBorder(20, 4, 4, 4));
        page.add(note, BorderLayout.CENTER);
        return page;
    }

    private JComponent tablePage(String title, String table, String[][] columns) {
        return tablePage(title, table(table), columns, table);
    }

    private JComponent tablePage(String title, JsonArray rows, String[][] columns) {
        return tablePage(title, rows, columns, null);
    }

    private JComponent tablePage(String title, JsonArray rows, String[][] columns,
            String tableName) {
        JPanel page = page(title);

        JPanel list = new JPanel();
        list.setBackground(APP_BG);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (rows.size() == 0) {
            JLabel empty = new JLabel("Brak pozycji.");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(18, 8, 18, 8));
            list.add(empty);
        } else {
            for (JsonElement el : rows) {
                if (!el.isJsonObject()) continue;
                list.add(recordCard(el.getAsJsonObject(), columns, tableName));
                list.add(Box.createVerticalStrut(10));
            }
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        page.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBackground(APP_BG);
        JLabel count = new JLabel("Pozycji: " + rows.size()
            + "  •  widok użytkowy — bez technicznych ID");
        count.setForeground(APP_MUTED);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBackground(APP_BG);
        JButton reload = actionButton("↻ Pobierz z telefonu");
        JButton save = actionButton("✓ Zapisz zmiany do telefonu");
        reload.addActionListener(e -> reloadFromPhone(reload));
        save.addActionListener(e -> saveChangesToPhone(save));
        if (canAddTable(tableName)) {
            JButton add = actionButton("＋ Dodaj");
            add.addActionListener(e -> addRecord(tableName, columns));
            actions.add(add);
        }
        if ("tasks".equals(tableName)) {
            JButton quick = actionButton("⚡ Szybkie zadania");
            quick.addActionListener(e -> showQuickTaskWizard());
            actions.add(quick);
            JButton paste = actionButton("Wklej listę");
            paste.addActionListener(e -> showQuickTaskBulkPaste());
            actions.add(paste);
        }
        if ("storage_items".equals(tableName) || "places".equals(tableName)) {
            JButton labels = actionButton("▣ Etykiety QR");
            labels.addActionListener(e -> showBulkQrLabels(tableName, rows));
            actions.add(labels);
        }
        if (printableReportTitle(title)) {
            JButton print = actionButton("🖨 Drukuj");
            print.addActionListener(e -> printTableReport(title, rows, columns));
            actions.add(print);
        }
        actions.add(reload);
        actions.add(save);
        footer.add(count, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private JPanel recordCard(JsonObject row, String[][] columns, String tableName) {
        JPanel card = new RoundedPanel(APP_SURFACE, 22);
        card.setLayout(new BorderLayout(12, 12));
        card.setBorder(new EmptyBorder(15, 18, 15, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        String mainKey = columns.length == 0 ? "" : columns[0][1];
        String main = columns.length == 0 ? "Pozycja"
            : friendlyValue(mainKey, row);
        if (main.isBlank() || "—".equals(main)) main = "Pozycja";
        JLabel title = new JLabel(main);
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(title, BorderLayout.CENTER);
        JPanel rowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rowActions.setOpaque(false);
        JButton edit = actionButton("Edytuj");
        edit.addActionListener(e -> editRow(row, columns));
        rowActions.add(edit);
        if ("storage_items".equals(tableName) || "places".equals(tableName)) {
            JButton qr = actionButton("QR / etykieta");
            qr.addActionListener(e -> {
                DesktopQrLabels.Label label = qrLabel(tableName, row);
                if (label != null) showQrLabelWorkflow(java.util.List.of(label));
            });
            rowActions.add(qr);
        }
        if (canDeleteTable(tableName)) {
            JButton remove = actionButton("Usuń");
            remove.addActionListener(e -> deleteRecord(tableName, row));
            rowActions.add(remove);
        }
        header.add(rowActions, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 2, 14, 7));
        details.setOpaque(false);
        for (int i = 1; i < columns.length; i++) {
            JLabel label = new JLabel(columns[i][0]);
            label.setForeground(APP_MUTED);
            JLabel value = new JLabel(friendlyValue(columns[i][1], row));
            value.setForeground(APP_TEXT);
            details.add(label);
            details.add(value);
        }
        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private static boolean printableReportTitle(String title) {
        return title != null && (title.startsWith("Kalendarz")
            || title.startsWith("Zadania") || title.startsWith("Czynności"));
    }

    private void printTableReport(String title, JsonArray rows, String[][] columns) {
        try {
            byte[] pdf = DesktopReportPdf.table(title, rows, columns);
            DesktopQrLabels.showPreview(this, pdf);

            String[] printers = DesktopQrLabels.printerNames();
            if (printers.length == 0) {
                JOptionPane.showMessageDialog(this,
                    "Windows nie widzi żadnej drukarki.",
                    "EDHOME Desktop", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JComboBox<String> printer = new JComboBox<>(printers);
            String remembered = PREFS.get("reportPrinter", "");
            if (!remembered.isBlank()) printer.setSelectedItem(remembered);
            JSpinner copies = new JSpinner(new SpinnerNumberModel(1,1,20,1));
            JPanel form = new JPanel(new GridLayout(0,2,8,8));
            form.add(new JLabel("Drukarka:")); form.add(printer);
            form.add(new JLabel("Liczba kopii:")); form.add(copies);
            int ok = JOptionPane.showConfirmDialog(this, form,
                "Drukuj • " + title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;

            String selected = String.valueOf(printer.getSelectedItem());
            PREFS.put("reportPrinter", selected);
            DesktopQrLabels.print(pdf, selected,
                ((Number)copies.getValue()).intValue());
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie wydrukowano raportu:\n" + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DesktopQrLabels.Label qrLabel(String tableName, JsonObject row) {
        try {
            long id = row.get("id").getAsLong();
            String name = value(row, "name");
            if ("places".equals(tableName)) {
                return new DesktopQrLabels.Label("place", id, name,
                    placePath(Long.toString(id)));
            }
            if ("storage_items".equals(tableName)) {
                String kind = value(row, "kind");
                if (!"thing".equals(kind) && !"box".equals(kind)) return null;
                String location = placePath(value(row, "place_id"));
                String parent = value(row, "parent_box_id");
                if (!parent.isBlank()) {
                    String box = referenceName("storage_items", parent);
                    location = "Pudełko: " + box
                        + ("—".equals(location) ? "" : " • " + location);
                }
                return new DesktopQrLabels.Label(kind, id, name, location);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void showBulkQrLabels(String tableName, JsonArray rows) {
        DefaultListModel<DesktopQrLabels.Label> model = new DefaultListModel<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) continue;
            DesktopQrLabels.Label label = qrLabel(tableName, element.getAsJsonObject());
            if (label != null) model.addElement(label);
        }
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Brak pozycji możliwych do wydrukowania.");
            return;
        }
        JList<DesktopQrLabels.Label> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(Math.min(12, model.size()));
        list.setSelectionInterval(0, model.size() - 1);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(620, Math.min(420, 40 + model.size() * 28)));
        int answer = JOptionPane.showConfirmDialog(this, scroll,
            "Zaznacz etykiety QR", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        java.util.List<DesktopQrLabels.Label> selected = list.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Zaznacz przynajmniej jedną etykietę.");
            return;
        }
        showQrLabelWorkflow(selected);
    }

    private void showQrLabelWorkflow(java.util.List<DesktopQrLabels.Label> labels) {
        JComboBox<String> format = new JComboBox<>(DesktopQrLabels.FORMATS);
        JTextField customW = new JTextField("40", 6);
        JTextField customH = new JTextField("30", 6);
        JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));

        String[] printers = DesktopQrLabels.printerNames();
        JComboBox<String> printer = new JComboBox<>(printers.length == 0
            ? new String[]{"— brak drukarki —"} : printers);
        String remembered = PREFS.get("labelPrinter", "");
        if (!remembered.isBlank()) printer.setSelectedItem(remembered);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Format:"));
        form.add(format);
        form.add(new JLabel("Własna szerokość [mm]:"));
        form.add(customW);
        form.add(new JLabel("Własna wysokość [mm]:"));
        form.add(customH);
        form.add(new JLabel("Drukarka etykiet:"));
        form.add(printer);
        form.add(new JLabel("Liczba kopii:"));
        form.add(copies);
        form.add(new JLabel("Etykiet:"));
        form.add(new JLabel(Integer.toString(labels.size())));

        int ok = JOptionPane.showConfirmDialog(this, form,
            "EDHOME • etykiety QR", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        try {
            float width = parseMillimeters(customW.getText());
            float height = parseMillimeters(customH.getText());
            DesktopQrLabels.FormatSpec spec =
                DesktopQrLabels.preset(format.getSelectedIndex(), width, height);
            byte[] pdf = DesktopQrLabels.pdf(labels, spec);

            DesktopQrLabels.showPreview(this, pdf);

            Object[] options = {"Drukuj", "Zapisz PDF", "Drukuj + PDF", "Zamknij"};
            int action = JOptionPane.showOptionDialog(this,
                "Podgląd gotowy. Co zrobić z etykietami?",
                "EDHOME • etykiety QR", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            String chosenPrinter = printers.length == 0 ? ""
                : String.valueOf(printer.getSelectedItem());
            if (!chosenPrinter.isBlank()) PREFS.put("labelPrinter", chosenPrinter);
            int copyCount = ((Number)copies.getValue()).intValue();

            if (action == 0 || action == 2)
                DesktopQrLabels.print(pdf, chosenPrinter, copyCount);
            if (action == 1 || action == 2) {
                Path saved = DesktopQrLabels.savePdf(this, pdf);
                if (saved != null)
                    JOptionPane.showMessageDialog(this,
                        "Zapisano etykiety:\n" + saved.toAbsolutePath());
            }
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie udało się przygotować etykiet QR:\n" + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static float parseMillimeters(String raw) {
        try {
            return Float.parseFloat(raw.trim().replace(',', '.'));
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Podaj poprawny rozmiar etykiety w milimetrach.");
        }
    }

    private boolean canAddTable(String tableName) {
        return "tasks".equals(tableName)
            || "pantry".equals(tableName)
            || "shopping_items".equals(tableName)
            || "storage_items".equals(tableName)
            || "vehicles".equals(tableName)
            || "places".equals(tableName);
    }

    private boolean canDeleteTable(String tableName) {
        return "tasks".equals(tableName)
            || "shopping_items".equals(tableName)
            || "storage_items".equals(tableName)
            || "places".equals(tableName);
    }

    private void showQuickTaskWizard() {
        JCheckBox askDuration = new JCheckBox("Ile to zajmie?",
            PREFS.getBoolean("quick.ask.duration", true));
        JCheckBox askDue = new JCheckBox("Kiedy ma być zrobione?",
            PREFS.getBoolean("quick.ask.due", true));
        JCheckBox askPriority = new JCheckBox("Jaki priorytet?",
            PREFS.getBoolean("quick.ask.priority", false));
        JCheckBox askAssignee = new JCheckBox("Kto ma zrobić?",
            PREFS.getBoolean("quick.ask.assignee", false));
        JCheckBox askPlace = new JCheckBox("Gdzie / w jakim miejscu?",
            PREFS.getBoolean("quick.ask.place", false));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel intro = new JLabel("<html><b>Wybierz, o co kreator ma pytać przy każdym zadaniu.</b><br>"
            + "Niezaznaczone pola dostaną wartości domyślne:<br>"
            + "30 min • bez terminu • priorytet normalny • bez osoby • bez miejsca.</html>");
        fields.add(intro);
        fields.add(Box.createVerticalStrut(10));
        fields.add(askDuration);
        fields.add(askDue);
        fields.add(askPriority);
        fields.add(askAssignee);
        fields.add(askPlace);

        int setup = JOptionPane.showConfirmDialog(this, fields,
            "EDHOME • szybkie zadania • wybór pytań",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (setup != JOptionPane.OK_OPTION) return;

        PREFS.putBoolean("quick.ask.duration", askDuration.isSelected());
        PREFS.putBoolean("quick.ask.due", askDue.isSelected());
        PREFS.putBoolean("quick.ask.priority", askPriority.isSelected());
        PREFS.putBoolean("quick.ask.assignee", askAssignee.isSelected());
        PREFS.putBoolean("quick.ask.place", askPlace.isSelected());

        java.util.List<QuickTaskDraft> drafts = new ArrayList<>();
        int totalMinutes = 0;

        while (true) {
            String progress = drafts.isEmpty()
                ? "Pierwsze zadanie"
                : "Dodano do kreatora: " + drafts.size()
                    + " • razem około " + formatMinutes(totalMinutes);
            String title = (String) JOptionPane.showInputDialog(this,
                progress + "\n\nCo trzeba zrobić?\n"
                    + "Enter = dalej • puste pole lub Anuluj = zakończ serię",
                "EDHOME • szybkie zadania",
                JOptionPane.QUESTION_MESSAGE, null, null, "");
            if (title == null || title.trim().isEmpty()) break;
            title = title.trim();
            if (title.length() > 160) {
                JOptionPane.showMessageDialog(this,
                    "Nazwa zadania może mieć maksymalnie 160 znaków.");
                continue;
            }

            int minutes = 30;
            if (askDuration.isSelected()) {
                String raw = (String) JOptionPane.showInputDialog(this,
                    "Ile to zajmie?\n"
                        + "Przykłady: 20, 45 min, 1h, 1h 30, 1:30",
                    "EDHOME • " + title,
                    JOptionPane.QUESTION_MESSAGE, null, null, "30 min");
                if (raw == null) break;
                try {
                    minutes = parseQuickDuration(raw);
                } catch (Exception invalid) {
                    JOptionPane.showMessageDialog(this,
                        "Nie rozumiem czasu. Wpisz np. 30 min, 1h albo 1h 30.");
                    continue;
                }
            }

            String dueDate = "";
            if (askDue.isSelected()) {
                String raw = (String) JOptionPane.showInputDialog(this,
                    "Kiedy ma być zrobione?\n"
                        + "Możesz wpisać: dziś, jutro, +7, bez terminu, "
                        + "27.09.2026 lub 2026-09-27",
                    "EDHOME • " + title,
                    JOptionPane.QUESTION_MESSAGE, null, null, "bez terminu");
                if (raw == null) break;
                try {
                    LocalDate parsed = parseQuickDueDate(raw);
                    dueDate = parsed == null ? "" : parsed.toString();
                } catch (Exception invalid) {
                    JOptionPane.showMessageDialog(this,
                        "Nie rozumiem terminu. Wpisz np. dziś, jutro, +7, "
                            + "bez terminu albo konkretną datę.");
                    continue;
                }
            }

            String priority = "normal";
            if (askPriority.isSelected()) {
                Choice chosen = (Choice) JOptionPane.showInputDialog(this,
                    "Jaki priorytet?", "EDHOME • " + title,
                    JOptionPane.QUESTION_MESSAGE, null,
                    new Choice[]{
                        new Choice("low","Niski"),
                        new Choice("normal","Normalny"),
                        new Choice("high","Wysoki"),
                        new Choice("urgent","Pilny")
                    },
                    new Choice("normal","Normalny"));
                if (chosen == null) break;
                priority = chosen.value;
            }

            String assigneeId = "";
            if (askAssignee.isSelected()) {
                JComboBox<Choice> people = quickReferenceCombo(
                    "household_members", "Każdy / nieprzypisane");
                int choice = JOptionPane.showConfirmDialog(this, people,
                    "Kto ma zrobić? • " + title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) break;
                Choice selected = (Choice) people.getSelectedItem();
                assigneeId = selected == null ? "" : selected.value;
            }

            String placeId = "";
            if (askPlace.isSelected()) {
                JComboBox<Choice> places = quickReferenceCombo(
                    "places", "Bez miejsca");
                int choice = JOptionPane.showConfirmDialog(this, places,
                    "Gdzie? • " + title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) break;
                Choice selected = (Choice) places.getSelectedItem();
                placeId = selected == null ? "" : selected.value;
            }

            if (openTaskExists(title)) {
                int duplicate = JOptionPane.showConfirmDialog(this,
                    "Otwarte zadanie o tej nazwie już istnieje:\n"
                        + title + "\n\nDodać mimo to?",
                    "EDHOME • możliwy duplikat",
                    JOptionPane.YES_NO_OPTION);
                if (duplicate != JOptionPane.YES_OPTION) continue;
            }

            drafts.add(new QuickTaskDraft(
                title, minutes, dueDate, priority, assigneeId, placeId));
            totalMinutes += minutes;
        }

        if (drafts.isEmpty()) return;

        StringBuilder summary = new StringBuilder();
        summary.append("Zadania: ").append(drafts.size())
            .append("\nŁączny szacowany czas: ")
            .append(formatMinutes(totalMinutes)).append("\n\n");
        for (int i=0; i<Math.min(12, drafts.size()); i++) {
            QuickTaskDraft draft = drafts.get(i);
            summary.append("• ").append(draft.title)
                .append(" • ").append(formatMinutes(draft.minutes));
            if (!draft.dueDate.isBlank())
                summary.append(" • ").append(formatQuickDate(draft.dueDate));
            summary.append('\n');
        }
        if (drafts.size() > 12)
            summary.append("… i ").append(drafts.size() - 12)
                .append(" kolejnych\n");

        int save = JOptionPane.showConfirmDialog(this,
            summary + "\nZapisać wszystkie do „Do zrobienia”?",
            "EDHOME • podsumowanie szybkich zadań",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (save != JOptionPane.YES_OPTION) return;

        long id = nextId("tasks");
        for (QuickTaskDraft draft : drafts) {
            JsonObject row = newRowTemplate("tasks");
            row.addProperty("id", id++);
            row.addProperty("title", draft.title);
            row.addProperty("duration_minutes", draft.minutes);
            row.addProperty("priority", draft.priority);
            if (draft.dueDate.isBlank())
                row.add("due_date", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("due_date", draft.dueDate);
            if (draft.assigneeId.isBlank())
                row.add("assignee_id", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("assignee_id", Long.parseLong(draft.assigneeId));
            if (draft.placeId.isBlank())
                row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("place_id", Long.parseLong(draft.placeId));
            table("tasks").add(row);
        }

        markDirty();
        showSection("Zadania");
        JOptionPane.showMessageDialog(this,
            "Dodano " + drafts.size() + " zadań.\n"
                + "Łączny szacowany czas: " + formatMinutes(totalMinutes)
                + "\n\nMożesz teraz zsynchronizować je z telefonem.",
            "EDHOME • szybkie zadania", JOptionPane.INFORMATION_MESSAGE);
    }

    private static int parseQuickDuration(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
            .replace("godziny","h").replace("godzin","h").replace("godz.","h")
            .replace("minuty","min").replace("minut","min").replace("min.","min");
        if (text.matches("\\d{1,4}")) {
            int minutes = Integer.parseInt(text);
            if (minutes >= 1 && minutes <= 1440) return minutes;
        }
        if (text.matches("\\d{1,2}:\\d{1,2}")) {
            String[] parts = text.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int total = hours * 60 + minutes;
            if (minutes < 60 && total >= 1 && total <= 1440) return total;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "^(?:(\\d{1,2})\\s*h)?\\s*(?:(\\d{1,3})\\s*min)?$").matcher(text);
        if (matcher.matches() && (matcher.group(1) != null || matcher.group(2) != null)) {
            int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
            int minutes = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int total = hours * 60 + minutes;
            if (minutes < 60 && total >= 1 && total <= 1440) return total;
        }
        throw new IllegalArgumentException("duration");
    }

    private static LocalDate parseQuickDueDate(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.forLanguageTag("pl-PL"));
        if (text.isEmpty() || "bez".equals(text) || "brak".equals(text)
                || "bez terminu".equals(text)) return null;
        if ("dziś".equals(text) || "dzis".equals(text) || "dzisiaj".equals(text))
            return LocalDate.now();
        if ("jutro".equals(text)) return LocalDate.now().plusDays(1);
        if ("pojutrze".equals(text)) return LocalDate.now().plusDays(2);
        if (text.matches("\\+\\d{1,3}"))
            return LocalDate.now().plusDays(Long.parseLong(text.substring(1)));
        try {
            if (text.matches("\\d{2}[.]\\d{2}[.]\\d{4}"))
                return LocalDate.parse(text,
                    DateTimeFormatter.ofPattern("dd.MM.uuuu")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            return LocalDate.parse(text);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("date");
        }
    }

    private static String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) return rest + " min";
        if (rest == 0) return hours + " h";
        return hours + " h " + rest + " min";
    }

    private static String formatQuickDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignored) {
            return iso;
        }
    }

    private static final class QuickTaskDraft {
        final String title;
        final int minutes;
        final String dueDate;
        final String priority;
        final String assigneeId;
        final String placeId;

        QuickTaskDraft(String title, int minutes, String dueDate,
                String priority, String assigneeId, String placeId) {
            this.title = title;
            this.minutes = minutes;
            this.dueDate = dueDate == null ? "" : dueDate;
            this.priority = priority == null ? "normal" : priority;
            this.assigneeId = assigneeId == null ? "" : assigneeId;
            this.placeId = placeId == null ? "" : placeId;
        }
    }

    private void showQuickTaskBulkPaste() {
        JTextArea input = new JTextArea(12, 52);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setText("");
        input.setToolTipText("Jedno zadanie w jednej linii.");

        JComboBox<Choice> priority = new JComboBox<>(new Choice[]{
            new Choice("low","Niski"),
            new Choice("normal","Normalny"),
            new Choice("high","Wysoki"),
            new Choice("urgent","Pilny")
        });
        priority.setSelectedIndex(1);

        JComboBox<Choice> assignee = quickReferenceCombo(
            "household_members", "Każdy / nieprzypisane");
        JComboBox<Choice> place = quickReferenceCombo(
            "places", "Bez miejsca");

        JCheckBox hasDate = new JCheckBox("Ustaw wspólny termin");
        JTextField due = new JTextField(LocalDate.now().toString(), 10);
        due.setEnabled(false);
        JButton tomorrow = new JButton("Jutro");
        JButton nextWeek = new JButton("+7 dni");
        tomorrow.setEnabled(false);
        nextWeek.setEnabled(false);
        hasDate.addActionListener(e -> {
            boolean on = hasDate.isSelected();
            due.setEnabled(on);
            tomorrow.setEnabled(on);
            nextWeek.setEnabled(on);
        });
        tomorrow.addActionListener(e -> due.setText(LocalDate.now().plusDays(1).toString()));
        nextWeek.addActionListener(e -> due.setText(LocalDate.now().plusDays(7).toString()));

        JCheckBox trimNumbers = new JCheckBox(
            "Usuń numerację i znaczniki z początku linii", true);
        JCheckBox skipDuplicates = new JCheckBox(
            "Nie dodawaj identycznych otwartych zadań", true);

        JLabel counter = new JLabel("Pozycji do dodania: 0");
        counter.setForeground(APP_MUTED);
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refresh() {
                counter.setText("Pozycji do dodania: "
                    + parseQuickTasks(input.getText(), trimNumbers.isSelected()).size());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        trimNumbers.addActionListener(e -> counter.setText("Pozycji do dodania: "
            + parseQuickTasks(input.getText(), trimNumbers.isSelected()).size()));

        JPanel options = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        int y = 0;
        g.gridx=0; g.gridy=y; g.gridwidth=2;
        options.add(new JLabel("Wklej lub wpisz listę — jedno zadanie w jednej linii:"), g);
        y++;
        g.gridy=y;
        JScrollPane area = new JScrollPane(input);
        area.setPreferredSize(new Dimension(640, 250));
        options.add(area, g);
        y++;
        g.gridwidth=1;
        g.gridx=0; g.gridy=y; options.add(new JLabel("Priorytet:"), g);
        g.gridx=1; options.add(priority, g);
        y++;
        g.gridx=0; g.gridy=y; options.add(new JLabel("Osoba:"), g);
        g.gridx=1; options.add(assignee, g);
        y++;
        g.gridx=0; g.gridy=y; options.add(new JLabel("Miejsce:"), g);
        g.gridx=1; options.add(place, g);
        y++;

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        dateRow.add(hasDate); dateRow.add(due); dateRow.add(tomorrow); dateRow.add(nextWeek);
        g.gridx=0; g.gridy=y; g.gridwidth=2; options.add(dateRow, g);
        y++;
        g.gridy=y; options.add(trimNumbers, g);
        y++;
        g.gridy=y; options.add(skipDuplicates, g);
        y++;
        g.gridy=y; options.add(counter, g);

        Object[] actions = {"Dodaj wszystko", "Anuluj"};
        int result = JOptionPane.showOptionDialog(this, options,
            "EDHOME • szybkie dodawanie wielu zadań",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, actions, actions[0]);
        if (result != 0) return;

        java.util.List<String> tasks =
            parseQuickTasks(input.getText(), trimNumbers.isSelected());
        if (tasks.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Wpisz przynajmniej jedno zadanie.");
            return;
        }
        if (tasks.size() > 100) {
            JOptionPane.showMessageDialog(this,
                "Jednorazowo można dodać maksymalnie 100 zadań.");
            return;
        }

        String dueDate = "";
        if (hasDate.isSelected()) {
            try {
                LocalDate parsed = LocalDate.parse(due.getText().trim());
                if (parsed.getYear() < 2000 || parsed.getYear() > 2100)
                    throw new IllegalArgumentException();
                dueDate = parsed.toString();
            } catch (Exception invalid) {
                JOptionPane.showMessageDialog(this,
                    "Termin musi mieć poprawną datę, np. 2026-09-27.");
                return;
            }
        }

        Choice prio = (Choice) priority.getSelectedItem();
        Choice person = (Choice) assignee.getSelectedItem();
        Choice selectedPlace = (Choice) place.getSelectedItem();

        int added = 0, duplicates = 0;
        java.util.Set<String> batch = new java.util.LinkedHashSet<>();
        for (String title : tasks) {
            String normalized = title.trim();
            if (!batch.add(normalized.toLowerCase(Locale.ROOT))) {
                duplicates++;
                continue;
            }
            if (skipDuplicates.isSelected() && openTaskExists(normalized)) {
                duplicates++;
                continue;
            }

            JsonObject row = newRowTemplate("tasks");
            row.addProperty("id", nextId("tasks"));
            row.addProperty("title", normalized);
            row.addProperty("priority", prio == null ? "normal" : prio.value);
            if (dueDate.isBlank()) row.add("due_date", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("due_date", dueDate);

            if (person == null || person.value.isBlank())
                row.add("assignee_id", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("assignee_id", Long.parseLong(person.value));

            if (selectedPlace == null || selectedPlace.value.isBlank())
                row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            else row.addProperty("place_id", Long.parseLong(selectedPlace.value));

            table("tasks").add(row);
            added++;
        }

        if (added > 0) markDirty();
        showSection("Zadania");
        JOptionPane.showMessageDialog(this,
            "Dodano do „Do zrobienia”: " + added
                + (duplicates > 0 ? "\nPominięto duplikaty: " + duplicates : "")
                + "\n\nZmiany są lokalne do czasu synchronizacji z telefonem.",
            "EDHOME • szybkie zadania", JOptionPane.INFORMATION_MESSAGE);
    }

    private JComboBox<Choice> quickReferenceCombo(String tableName, String emptyLabel) {
        java.util.List<Choice> values = new ArrayList<>();
        values.add(new Choice("", emptyLabel));
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String id = value(row, "id");
            String name = value(row, "name");
            if ("household_members".equals(tableName) && name.isBlank())
                name = value(row, "display_name");
            if (!id.isBlank())
                values.add(new Choice(id, name.isBlank() ? "Pozycja #" + id : name));
        }
        return new JComboBox<>(values.toArray(new Choice[0]));
    }

    private java.util.List<String> parseQuickTasks(String raw, boolean stripPrefix) {
        java.util.List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.replace("\r\n","\n").replace('\r','\n').split("\n")) {
            String task = line.trim();
            if (stripPrefix) {
                task = task.replaceFirst(
                    "^(?:[-*•]+|\\[[ xX]?\\]|\\d{1,3}[.)-])\\s*", "");
            }
            task = task.trim();
            if (task.isEmpty()) continue;
            if (task.length() > 160) task = task.substring(0, 160).trim();
            out.add(task);
        }
        return out;
    }

    private boolean openTaskExists(String title) {
        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject task = element.getAsJsonObject();
            if (intValue(task, "done") != 0) continue;
            if (title.equalsIgnoreCase(value(task, "title").trim()))
                return true;
        }
        return false;
    }

    private void addRecord(String tableName, String[][] columns) {
        JsonObject row = newRowTemplate(tableName);
        if (row == null) return;
        if (!editRow(row, columns)) return;
        table(tableName).add(row);
        showSection(current);
    }

    private JsonObject newRowTemplate(String tableName) {
        JsonObject row = new JsonObject();
        long id = nextId(tableName);
        long now = System.currentTimeMillis();
        if ("tasks".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("title", "Nowa czynność");
            row.addProperty("done", 0);
            row.add("due_date", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("repeat_rule", "once");
            row.addProperty("repeat_every", 1);
            row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("priority", "normal");
            row.addProperty("duration_minutes", 30);
            row.add("assignee_id", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("task_kind", "general");
            row.add("waste_fraction", com.google.gson.JsonNull.INSTANCE);
            row.add("remind_time", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("reminder_lead_days", 0);
            return row;
        }
        if ("pantry".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowy produkt");
            row.addProperty("qty", 0);
            row.addProperty("category", "other");
            return row;
        }
        if ("shopping_items".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowy zakup " + id);
            row.add("qty_milli", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("unit", "szt.");
            row.addProperty("checked", 0);
            row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            return row;
        }
        if ("storage_items".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowa rzecz");
            row.addProperty("kind", "thing");
            row.add("parent_box_id", com.google.gson.JsonNull.INSTANCE);
            row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            row.add("lent_to", com.google.gson.JsonNull.INSTANCE);
            row.add("lent_at", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("created_at", now);
            return row;
        }
        if ("vehicles".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowy pojazd");
            row.addProperty("registration", "");
            row.addProperty("mileage", 0);
            row.addProperty("oc_until", "");
            row.addProperty("inspection_until", "");
            row.addProperty("notes", "");
            row.add("oc_reminder_lead", com.google.gson.JsonNull.INSTANCE);
            row.add("inspection_reminder_lead", com.google.gson.JsonNull.INSTANCE);
            return row;
        }
        if ("places".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowe miejsce " + id);
            row.addProperty("kind", "");
            row.add("parent_id", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("icon", "places");
            return row;
        }
        return null;
    }

    private long nextId(String tableName) {
        long max = 0;
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            try {
                JsonElement id = element.getAsJsonObject().get("id");
                if (id != null && !id.isJsonNull()) max = Math.max(max, id.getAsLong());
            } catch (Exception ignored) { }
        }
        if (max == Long.MAX_VALUE)
            throw new IllegalStateException("Brak wolnego identyfikatora.");
        return max + 1;
    }

    private void deleteRecord(String tableName, JsonObject row) {
        long id;
        try { id = Long.parseLong(value(row, "id")); }
        catch (Exception invalid) {
            JOptionPane.showMessageDialog(this, "Ta pozycja nie ma poprawnego ID.");
            return;
        }
        String label = value(row, "name");
        if (label.isBlank()) label = value(row, "title");
        int choice = JOptionPane.showConfirmDialog(this,
            "Usunąć „" + (label.isBlank() ? "pozycję" : label) + "”?",
            "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            if ("tasks".equals(tableName)) {
                removeRowsByLong("task_rotation_members", "task_id", id);
                table("tasks").remove(row);
            } else if ("shopping_items".equals(tableName)) {
                table("shopping_items").remove(row);
            } else if ("storage_items".equals(tableName)) {
                if (!value(row, "lent_to").isBlank())
                    throw new IllegalStateException("Najpierw odnotuj zwrot wypożyczonej rzeczy.");
                if (hasReference("storage_items", "parent_box_id", id))
                    throw new IllegalStateException("Najpierw opróżnij pudełko.");
                JsonObject event = new JsonObject();
                event.addProperty("id", nextId("storage_events"));
                event.addProperty("item_id", id);
                event.addProperty("name_snapshot", value(row, "name"));
                event.addProperty("action", "removed");
                event.addProperty("details", "Usunięto z EDHOME Desktop");
                event.addProperty("happened_at", System.currentTimeMillis());
                table("storage_events").add(event);
                table("storage_items").remove(row);
                removeStorageThumbnail(id);
            } else if ("places".equals(tableName)) {
                if (hasReference("places", "parent_id", id))
                    throw new IllegalStateException("Najpierw przenieś lub usuń podmiejsca.");
                if (hasReference("storage_items", "place_id", id))
                    throw new IllegalStateException("Najpierw przenieś rzeczy i pudełka z tego miejsca.");
                if (hasReference("vehicle_tyre_sets", "place_id", id))
                    throw new IllegalStateException("Najpierw przenieś komplet opon z tego miejsca.");
                setNullWhere("tasks", "place_id", id);
                setNullWhere("shopping_items", "place_id", id);
                setNullWhere("shopping_receipts", "place_id", id);
                table("places").remove(row);
            } else return;
            markDirty();
            showSection(current);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hasReference(String tableName, String key, long id) {
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            try {
                JsonElement value = element.getAsJsonObject().get(key);
                if (value != null && !value.isJsonNull() && value.getAsLong() == id)
                    return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void removeRowsByLong(String tableName, String key, long id) {
        JsonArray rows = table(tableName);
        for (int i = rows.size() - 1; i >= 0; i--) {
            JsonElement element = rows.get(i);
            if (!element.isJsonObject()) continue;
            try {
                JsonElement value = element.getAsJsonObject().get(key);
                if (value != null && !value.isJsonNull() && value.getAsLong() == id)
                    rows.remove(i);
            } catch (Exception ignored) { }
        }
    }

    private void setNullWhere(String tableName, String key, long id) {
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            try {
                JsonElement value = candidate.get(key);
                if (value != null && !value.isJsonNull() && value.getAsLong() == id)
                    candidate.add(key, com.google.gson.JsonNull.INSTANCE);
            } catch (Exception ignored) { }
        }
    }

    private void removeStorageThumbnail(long itemId) {
        if (snapshot == null || !snapshot.has("settings")
                || !snapshot.get("settings").isJsonObject()) return;
        JsonObject settings = snapshot.getAsJsonObject("settings");
        JsonElement thumbsElement = settings.get("storageThumbnails");
        if (thumbsElement == null || !thumbsElement.isJsonArray()) return;
        JsonArray thumbs = thumbsElement.getAsJsonArray();
        for (int i = thumbs.size() - 1; i >= 0; i--) {
            JsonElement element = thumbs.get(i);
            if (!element.isJsonObject()) continue;
            try {
                JsonElement id = element.getAsJsonObject().get("itemId");
                if (id != null && !id.isJsonNull() && id.getAsLong() == itemId)
                    thumbs.remove(i);
            } catch (Exception ignored) { }
        }
    }

    private JButton actionButton(String label) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setForeground(APP_TEXT);
        button.setBackground(APP_SURFACE_2);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(APP_SURFACE_2),
            new EmptyBorder(8, 12, 8, 12)));
        return button;
    }

    private String friendlyValue(String key, JsonObject row) {
        String raw = value(row, key);
        if (raw.isBlank()) return "—";

        if ("assignee_id".equals(key)) return referenceName("household_members", raw);
        if ("place_id".equals(key) || "parent_id".equals(key))
            return placePath(raw);
        if ("parent_box_id".equals(key)) return referenceName("storage_items", raw);
        if ("goal_id".equals(key)) return referenceName("paycheck_goals", raw);
        if ("vehicle_id".equals(key)) return referenceName("vehicles", raw);

        if ("done".equals(key))
            return "1".equals(raw) ? "Wykonane" : "Do zrobienia";
        if ("checked".equals(key))
            return "1".equals(raw) ? "Kupione" : "Do kupienia";
        if ("mounted".equals(key)) return "1".equals(raw) ? "Założone" : "Zdjęte";
        if ("current".equals(key)) return "1".equals(raw) ? "Aktualne" : "Archiwalne";

        if ("priority".equals(key)) {
            switch (raw) {
                case "low": return "Niski";
                case "normal": return "Normalny";
                case "high": return "Wysoki";
                case "urgent": return "Pilny";
                default: return raw;
            }
        }
        if ("repeat_rule".equals(key)) {
            switch (raw) {
                case "once": return "Jednorazowo";
                case "daily": return "Codziennie";
                case "weekly": return "Co tydzień";
                case "monthly": return "Co miesiąc";
                case "yearly": return "Co rok";
                case "every_days": return "Co N dni";
                case "every_weeks": return "Co N tygodni";
                case "every_months": return "Co N miesięcy";
                case "every_years": return "Co N lat";
                case "before_spring": return "Przed wiosną";
                case "before_summer": return "Przed latem";
                case "before_autumn": return "Przed jesienią";
                case "before_winter": return "Przed zimą";
                default: return raw;
            }
        }
        if ("task_kind".equals(key)) {
            if ("waste".equals(raw)) return "Odpady";
            if ("general".equals(raw)) return "Czynność";
        }
        if ("kind".equals(key)) {
            switch (raw) {
                case "thing": return "Rzecz";
                case "box": return "Pudełko";
                case "income": return "Wpływ";
                case "expense": return "Wydatek";
                default: return raw;
            }
        }
        if ("scope".equals(key)) {
            if ("shared".equals(raw)) return "Wspólne";
            if ("private".equals(raw)) return "Prywatne";
        }
        if ("status".equals(key)) {
            switch (raw) {
                case "pending": return "Do potwierdzenia";
                case "confirmed": return "Potwierdzone";
                case "active": return "Aktywne";
                case "done": return "Wykonane";
                default: return raw;
            }
        }
        if ("confirmation_source".equals(key)) {
            switch (raw) {
                case "none": return "Brak";
                case "manual": return "Ręcznie";
                case "legacy": return "Starszy wpis";
                default: return raw;
            }
        }
        if ("category".equals(key)) {
            switch (raw) {
                case "shopping": return "Zakupy";
                case "bills": return "Rachunki";
                case "home": return "Dom";
                case "vehicle": return "Pojazdy";
                case "salary": return "Wynagrodzenie";
                case "food": return "Żywność";
                case "household": return "Dom";
                case "beauty": return "Higiena";
                case "pet": return "Zwierzęta";
                case "other": return "Inne";
                default: return raw;
            }
        }
        if ("amount_grosz".equals(key)) return money(raw);
        if ("qty_milli".equals(key)) return milli(raw);
        if (key.endsWith("_at")) return timeValue(raw);
        return raw;
    }

    private String referenceName(String tableName, String idText) {
        if (idText == null || idText.isBlank()) return "—";
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            if (idText.equals(value(candidate, "id"))) {
                String name = value(candidate, "name");
                return name.isBlank() ? "Pozycja" : name;
            }
        }
        return "Nieznane";
    }

    private String placePath(String idText) {
        if (idText == null || idText.isBlank()) return "—";
        java.util.List<String> names = new ArrayList<>();
        String currentId = idText;
        for (int depth = 0; depth < 32 && currentId != null && !currentId.isBlank(); depth++) {
            JsonObject found = null;
            for (JsonElement element : table("places")) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                if (currentId.equals(value(candidate, "id"))) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) break;
            names.add(0, value(found, "name"));
            currentId = value(found, "parent_id");
        }
        return names.isEmpty() ? "Nieznane" : String.join(" / ", names);
    }

    private static String money(String raw) {
        try {
            long grosz = Long.parseLong(raw);
            java.math.BigDecimal amount = java.math.BigDecimal.valueOf(grosz, 2);
            return amount.setScale(2).toPlainString().replace('.', ',') + " zł";
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String milli(String raw) {
        try {
            java.math.BigDecimal quantity =
                java.math.BigDecimal.valueOf(Long.parseLong(raw), 3).stripTrailingZeros();
            return quantity.toPlainString().replace('.', ',');
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String timeValue(String raw) {
        try {
            long millis = Long.parseLong(raw);
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(millis));
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String householdName() {
        if (snapshot == null || !snapshot.has("settings")) return "Moje gospodarstwo";
        JsonObject settings = snapshot.getAsJsonObject("settings");
        return str(settings, "household", "Moje gospodarstwo");
    }

    private boolean editRow(JsonObject row, String[][] columns) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 5, 5, 5);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        Map<String,JComponent> editors = new LinkedHashMap<>();
        int y = 0;
        for (String[] column : columns) {
            String label = column[0];
            String key = column[1];
            JLabel name = new JLabel(label);
            g.gridx = 0; g.gridy = y; g.weightx = 0;
            form.add(name, g);
            JComponent editor = editorFor(key, row);
            editors.put(key, editor);
            g.gridx = 1; g.weightx = 1;
            form.add(editor, g);
            y++;
        }

        int result;
        editingDialog = true;
        try {
            result = JOptionPane.showConfirmDialog(this, form,
                "EDHOME • edytuj", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        } finally {
            editingDialog = false;
        }
        if (result != JOptionPane.OK_OPTION) return false;

        try {
            for (Map.Entry<String,JComponent> entry : editors.entrySet())
                applyEditor(row, entry.getKey(), entry.getValue());
            markDirty();
            showSection(current);
            return true;
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie zapisano zmiany: " + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private JComponent editorFor(String key, JsonObject row) {
        String raw = value(row, key);
        java.util.List<Choice> choices = choicesFor(key, row);
        if (!choices.isEmpty()) {
            JComboBox<Choice> box = new JComboBox<>(
                choices.toArray(new Choice[0]));
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).value.equals(raw)) {
                    box.setSelectedIndex(i);
                    break;
                }
            }
            return box;
        }

        if ("assignee_id".equals(key))
            return referenceCombo("household_members", raw, true);
        if ("place_id".equals(key) || "parent_id".equals(key))
            return referenceCombo("places", raw, true);
        if ("parent_box_id".equals(key))
            return referenceCombo("storage_items", raw, true);

        JTextField field = new JTextField();
        if ("amount_grosz".equals(key)) {
            try {
                field.setText(java.math.BigDecimal.valueOf(
                    Long.parseLong(raw), 2).toPlainString());
            } catch (Exception ignored) { field.setText(raw); }
        } else if ("qty_milli".equals(key)) {
            try {
                field.setText(java.math.BigDecimal.valueOf(
                    Long.parseLong(raw), 3).stripTrailingZeros().toPlainString());
            } catch (Exception ignored) { field.setText(raw); }
        } else field.setText(raw);
        return field;
    }

    private java.util.List<Choice> choicesFor(String key, JsonObject row) {
        java.util.List<Choice> out = new ArrayList<>();
        if ("done".equals(key) || "checked".equals(key)
                || "mounted".equals(key) || "current".equals(key)) {
            out.add(new Choice("0", "Nie"));
            out.add(new Choice("1", "Tak"));
        } else if ("priority".equals(key)) {
            out.add(new Choice("low", "Niski"));
            out.add(new Choice("normal", "Normalny"));
            out.add(new Choice("high", "Wysoki"));
            out.add(new Choice("urgent", "Pilny"));
        } else if ("repeat_rule".equals(key)) {
            String[][] values = {
                {"once","Jednorazowo"},{"daily","Codziennie"},{"weekly","Co tydzień"},
                {"monthly","Co miesiąc"},{"yearly","Co rok"},{"every_days","Co N dni"},
                {"every_weeks","Co N tygodni"},{"every_months","Co N miesięcy"},
                {"every_years","Co N lat"},{"before_spring","Przed wiosną"},
                {"before_summer","Przed latem"},{"before_autumn","Przed jesienią"},
                {"before_winter","Przed zimą"}
            };
            for (String[] value : values) out.add(new Choice(value[0], value[1]));
        } else if ("task_kind".equals(key)) {
            out.add(new Choice("general", "Czynność"));
            out.add(new Choice("waste", "Odpady"));
        } else if ("kind".equals(key) && row.has("parent_box_id")) {
            out.add(new Choice("thing", "Rzecz"));
            out.add(new Choice("box", "Pudełko"));
        } else if ("kind".equals(key) && row.has("amount_grosz")) {
            out.add(new Choice("income", "Wpływ"));
            out.add(new Choice("expense", "Wydatek"));
        } else if ("category".equals(key) && row.has("amount_grosz")) {
            out.add(new Choice("shopping", "Zakupy"));
            out.add(new Choice("bills", "Rachunki"));
            out.add(new Choice("home", "Dom"));
            out.add(new Choice("vehicle", "Pojazdy"));
            out.add(new Choice("salary", "Wynagrodzenie"));
            out.add(new Choice("other", "Inne"));
        } else if ("category".equals(key)) {
            out.add(new Choice("food", "Żywność"));
            out.add(new Choice("household", "Dom"));
            out.add(new Choice("beauty", "Higiena"));
            out.add(new Choice("pet", "Zwierzęta"));
            out.add(new Choice("other", "Inne"));
        }
        return out;
    }

    private JComboBox<Choice> referenceCombo(String tableName,
            String selected, boolean nullable) {
        java.util.List<Choice> options = new ArrayList<>();
        if (nullable) options.add(new Choice("", "—"));
        for (JsonElement element : table(tableName)) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String id = value(row, "id");
            String name = value(row, "name");
            if (!id.isBlank()) options.add(new Choice(id,
                name.isBlank() ? "Pozycja" : name));
        }
        JComboBox<Choice> box = new JComboBox<>(options.toArray(new Choice[0]));
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).value.equals(selected)) box.setSelectedIndex(i);
        return box;
    }

    private void applyEditor(JsonObject row, String key, JComponent editor) {
        if (editor instanceof JComboBox<?>) {
            Object selected = ((JComboBox<?>) editor).getSelectedItem();
            if (selected instanceof Choice) {
                String value = ((Choice) selected).value;
                if (value.isBlank()
                        && (key.endsWith("_id") || "parent_id".equals(key)))
                    row.add(key, com.google.gson.JsonNull.INSTANCE);
                else if (isNumericKey(key)) row.addProperty(key, Long.parseLong(value));
                else row.addProperty(key, value);
            }
            return;
        }

        String text = ((JTextField) editor).getText().trim();
        if ("amount_grosz".equals(key)) {
            long grosz = new java.math.BigDecimal(text.replace(',', '.'))
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValueExact();
            row.addProperty(key, grosz);
            return;
        }
        if ("qty_milli".equals(key)) {
            long milli = new java.math.BigDecimal(text.replace(',', '.'))
                .multiply(java.math.BigDecimal.valueOf(1000))
                .longValueExact();
            row.addProperty(key, milli);
            return;
        }
        if (isNumericKey(key)) {
            if (text.isBlank() && key.endsWith("_id"))
                row.add(key, com.google.gson.JsonNull.INSTANCE);
            else row.addProperty(key, Long.parseLong(text));
        } else {
            if (text.isBlank() && (key.endsWith("_date") || key.endsWith("_until")))
                row.add(key, com.google.gson.JsonNull.INSTANCE);
            else row.addProperty(key, text);
        }
    }

    private static boolean isNumericKey(String key) {
        return "done".equals(key) || "checked".equals(key)
            || "repeat_every".equals(key) || "duration_minutes".equals(key)
            || "qty".equals(key) || "qty_milli".equals(key)
            || "amount_grosz".equals(key) || "mileage".equals(key)
            || key.endsWith("_id");
    }

    private static final class Choice {
        final String value;
        final String label;
        Choice(String value, String label) {
            this.value = value == null ? "" : value;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }

    private int count(String name) {
        return table(name).size();
    }

    private int countWhere(String name, String key, int expected) {
        int count = 0;
        for (JsonElement el : table(name))
            if (intValue(el.getAsJsonObject(), key) == expected) count++;
        return count;
    }

    private JsonArray table(String name) {
        if (snapshot == null || !snapshot.has("tables")
                || !snapshot.get("tables").isJsonObject()) return new JsonArray();
        JsonObject tables = snapshot.getAsJsonObject("tables");
        JsonElement rows = tables.get(name);
        return rows != null && rows.isJsonArray() ? rows.getAsJsonArray() : new JsonArray();
    }

    private void loadCache() {
        if (!Files.isRegularFile(CACHE)) return;
        try {
            JsonObject cached = JsonParser.parseString(
                Files.readString(CACHE, StandardCharsets.UTF_8)).getAsJsonObject();
            validate(cached);
            snapshot = cached;
            snapshotHash = "";
            dirty = false;
            connection.setText("OFFLINE • cache Android " + str(cached,"sourceVersion",""));
        } catch (Exception ignored) {
            snapshot = null;
        }
    }

    private static void saveCache(JsonObject data) throws IOException {
        Files.createDirectories(CACHE.getParent());
        Path temp = CACHE.resolveSibling(CACHE.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
        try {
            Files.move(temp, CACHE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, CACHE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validate(JsonObject root) {
        if (!"edhome-data-backup".equals(str(root,"format",""))
                || !root.has("tables") || !root.get("tables").isJsonObject())
            throw new IllegalArgumentException("To nie jest backup danych EDHOME.");
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }

    private static String value(JsonObject row, String key) {
        JsonElement e = row.get(key);
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) return e.getAsJsonPrimitive().getAsString();
        return e.toString();
    }

    private static int intValue(JsonObject row, String key) {
        try { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsInt() : 0; }
        catch (Exception ignored) { return 0; }
    }

    private static String[][] cols(String... pairs) {
        String[][] result = new String[pairs.length/2][2];
        for (int i=0;i<pairs.length;i+=2) {
            result[i/2][0]=pairs[i];
            result[i/2][1]=pairs[i+1];
        }
        return result;
    }

    private void reloadFromPhone(JButton trigger) {
        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw połącz Desktop z telefonem w Ustawieniach.");
            return;
        }
        if (dirty) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Masz niezapisane zmiany z PC. Pobrać dane z telefonu i je odrzucić?",
                "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        trigger.setEnabled(false);
        connection.setText("ODŚWIEŻANIE…");
        new SwingWorker<SnapshotResult,Void>() {
            @Override protected SnapshotResult doInBackground() throws Exception {
                return new LanClient(host, PORT, secret).snapshot();
            }
            @Override protected void done() {
                autoSaving = false;
                if (trigger != null) trigger.setEnabled(true);
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
                    syncedSnapshot = snapshot.deepCopy();
                    phoneRevision = result.revision;
                    lastFullReconcileAt = System.currentTimeMillis();
                    connected = true;
                    dirty = false;
                    validate(snapshot);
                    saveCache(snapshot);
                    connection.setText("ONLINE • EDYCJA • Android "
                        + str(snapshot,"sourceVersion",""));
                    showSection(current);
                } catch (Exception ex) {
                    connected = false;
                    connection.setText("OFFLINE • błąd odświeżania");
                    updateTrayTooltip();
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie pobrano danych:\n" + rootMessage(ex),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveChangesToPhone(JButton trigger) {
        saveChangesToPhone(trigger, false);
    }

    private void saveChangesToPhone(JButton trigger, boolean automatic) {
        if (!dirty) {
            if (!automatic)
                JOptionPane.showMessageDialog(this, "Nie ma zmian do zapisania.");
            return;
        }
        if (autoSaving || connecting) {
            if (automatic) scheduleAutoSave();
            return;
        }

        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank() || snapshotHash.isBlank()
                || syncedSnapshot == null) {
            if (automatic) {
                connection.setText("ZAPISANO LOKALNIE • czeka na świeże połączenie");
                return;
            }
            JOptionPane.showMessageDialog(this,
                "Najpierw pobierz świeże dane z telefonu. "
                    + "Zmiany lokalne są bezpieczne, ale wymagają aktualnej bazy do scalenia.");
            return;
        }

        if (!automatic) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Zsynchronizować zmiany z PC?\n"
                    + "EDHOME wyśle tylko zmienione rekordy i sprawdzi konflikty "
                    + "na poziomie konkretnej pozycji.",
                "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        final JsonObject outgoing = snapshot.deepCopy();
        final JsonObject baseline = syncedSnapshot.deepCopy();
        final long generation = localEditGeneration;
        final RecordPatchPlan patchPlan = buildRecordPatch(baseline, outgoing);
        final String baseSnapshotSha = snapshotHash;

        autoSaving = true;
        if (trigger != null) trigger.setEnabled(false);
        connection.setText(patchPlan != null && patchPlan.operations > 0
            ? "SYNC • wysyłam " + patchPlan.operations + " zmian rekordowych…"
            : "SYNC • pełne pojednanie danych…");

        new SwingWorker<SyncWriteResult,Void>() {
            @Override protected SyncWriteResult doInBackground() throws Exception {
                LanClient client = new LanClient(host, PORT, secret);
                if (patchPlan != null && patchPlan.operations > 0) {
                    try {
                        PatchResult patched = client.patch(patchPlan.payload);
                        applyPatchAck(outgoing, patched.results);
                        return new SyncWriteResult(outgoing, patched.sha256,
                            patched.revision, true, patchPlan.operations);
                    } catch (PatchUnsupportedException oldAndroid) {
                        // Kompatybilność: starszy Android nadal przyjmie bezpieczny snapshot.
                    }
                }
                SnapshotResult full = client.write(outgoing, baseSnapshotSha);
                return new SyncWriteResult(full.data, full.sha256,
                    full.revision, false, 0);
            }

            @Override protected void done() {
                autoSaving = false;
                if (trigger != null) trigger.setEnabled(true);
                try {
                    SyncWriteResult result = get();
                    connected = true;
                    syncedSnapshot = result.data.deepCopy();
                    snapshotHash = result.sha256;
                    phoneRevision = result.revision;
                    if (!result.patchUsed)
                        lastFullReconcileAt = System.currentTimeMillis();

                    if (localEditGeneration == generation) {
                        snapshot = result.data.deepCopy();
                        dirty = false;
                        saveCache(snapshot);
                    } else {
                        // Użytkownik zdążył zrobić kolejne zmiany w czasie synchronizacji.
                        dirty = true;
                        saveCache(snapshot);
                        scheduleAutoSave();
                    }

                    connection.setText(result.patchUsed
                        ? "ONLINE • zsynchronizowano " + result.operations
                            + (result.operations == 1 ? " zmianę" : " zmiany")
                        : "ONLINE • pełne pojednanie zakończone");
                    if (!automatic) showSection(current);
                    updateTrayTooltip();
                } catch (Exception ex) {
                    String message = rootMessage(ex);
                    boolean conflict = message.contains("Konflikt")
                        || message.contains("nowsze dane");
                    connection.setText(conflict
                        ? "KONFLIKT • ten sam rekord zmieniono na innym urządzeniu"
                        : "ZAPISANO LOKALNIE • synchronizacja oczekuje");
                    if (automatic) {
                        if (trayIcon != null && conflict)
                            trayIcon.displayMessage("EDHOME Desktop",
                                "Konflikt konkretnego rekordu. Lokalne zmiany zostały zachowane.",
                                TrayIcon.MessageType.WARNING);
                    } else {
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Nie zsynchronizowano zmian:\n" + message
                                + "\n\nLokalna kopia na PC została zachowana.",
                            "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private static RecordPatchPlan buildRecordPatch(
            JsonObject baseline, JsonObject current) {
        if (baseline == null || current == null) return null;
        try {
            JsonElement baseSettings = baseline.get("settings");
            JsonElement nowSettings = current.get("settings");
            if (!canonicalJson(baseSettings).equals(canonicalJson(nowSettings)))
                return null; // ustawienia nadal idą pełnym, chronionym snapshotem

            if (!baseline.has("tables") || !baseline.get("tables").isJsonObject()
                    || !current.has("tables") || !current.get("tables").isJsonObject())
                return null;

            if (baseline.has("syncRecords") && baseline.get("syncRecords").isJsonArray()
                    && current.has("syncRecords") && current.get("syncRecords").isJsonArray())
                return buildRecordPatchV2(baseline, current);

            return buildLegacyRecordPatch(baseline, current);
        } catch (Exception invalid) {
            return null;
        }
    }

    private static RecordPatchPlan buildRecordPatchV2(
            JsonObject baseline, JsonObject current) throws Exception {
        JsonObject baseTables = baseline.getAsJsonObject("tables");
        JsonObject nowTables = current.getAsJsonObject("tables");
        java.util.Map<String,JsonObject> baseMeta = syncMetaByRow(baseline);
        java.util.Map<String,JsonObject> nowMeta = syncMetaByRow(current);

        java.util.Set<String> names = new java.util.TreeSet<>();
        names.addAll(baseTables.keySet());
        names.addAll(nowTables.keySet());

        JsonArray operations = new JsonArray();
        for (String table : names) {
            if (!baseTables.has(table) || !nowTables.has(table)
                    || !baseTables.get(table).isJsonArray()
                    || !nowTables.get(table).isJsonArray())
                return null;

            JsonArray before = baseTables.getAsJsonArray(table);
            JsonArray after = nowTables.getAsJsonArray(table);
            if (canonicalJson(before).equals(canonicalJson(after))) continue;

            java.util.Map<String,JsonObject> beforeRows =
                patchRowsByKey(table, before);
            java.util.Map<String,JsonObject> afterRows =
                patchRowsByKey(table, after);
            if (beforeRows == null || afterRows == null) return null;

            java.util.Set<String> keys = new java.util.TreeSet<>();
            keys.addAll(beforeRows.keySet());
            keys.addAll(afterRows.keySet());

            for (String rowKey : keys) {
                JsonObject oldRow = beforeRows.get(rowKey);
                JsonObject newRow = afterRows.get(rowKey);
                if (oldRow != null && newRow != null
                        && canonicalJson(oldRow).equals(canonicalJson(newRow)))
                    continue;

                String metaKey = table + "\u0000" + rowKey;
                JsonObject meta = oldRow != null
                    ? baseMeta.get(metaKey) : nowMeta.get(metaKey);
                if (meta == null
                        || !meta.has("syncUuid")
                        || !meta.has("revision"))
                    return null;

                String syncUuid = meta.get("syncUuid").getAsString();
                long revision = meta.get("revision").getAsLong();
                if (!syncUuid.matches("[0-9a-fA-F-]{36}")
                        || (oldRow == null && revision != 0L)
                        || (oldRow != null && revision < 1L))
                    return null;

                JsonObject operation = new JsonObject();
                operation.addProperty("table", table);
                operation.addProperty("rowKey", rowKey);
                operation.addProperty("syncUuid",
                    syncUuid.toLowerCase(Locale.ROOT));
                operation.addProperty("baseRevision", revision);
                if (newRow == null) {
                    operation.addProperty("action", "delete");
                } else {
                    operation.addProperty("action", "upsert");
                    operation.add("row", newRow.deepCopy());
                }
                operations.add(operation);
                if (operations.size() > 500) return null;
            }
        }

        if (operations.size() == 0) return null;
        JsonObject payload = new JsonObject();
        payload.addProperty("format", "edhome-record-patch");
        payload.addProperty("version", 2);
        payload.add("operations", operations);
        return new RecordPatchPlan(payload, operations.size());
    }

    private static RecordPatchPlan buildLegacyRecordPatch(
            JsonObject baseline, JsonObject current) throws Exception {
        JsonObject baseTables = baseline.getAsJsonObject("tables");
        JsonObject nowTables = current.getAsJsonObject("tables");
        java.util.Set<String> names = new java.util.TreeSet<>();
        names.addAll(baseTables.keySet());
        names.addAll(nowTables.keySet());

        JsonArray operations = new JsonArray();
        for (String table : names) {
            if (!baseTables.has(table) || !nowTables.has(table)
                    || !baseTables.get(table).isJsonArray()
                    || !nowTables.get(table).isJsonArray())
                return null;

            JsonArray before = baseTables.getAsJsonArray(table);
            JsonArray after = nowTables.getAsJsonArray(table);
            if (canonicalJson(before).equals(canonicalJson(after))) continue;

            java.util.Map<String,JsonObject> beforeRows = patchRowsById(before);
            java.util.Map<String,JsonObject> afterRows = patchRowsById(after);
            if (beforeRows == null || afterRows == null) return null;

            java.util.Set<String> ids = new java.util.TreeSet<>();
            ids.addAll(beforeRows.keySet());
            ids.addAll(afterRows.keySet());
            for (String id : ids) {
                JsonObject oldRow = beforeRows.get(id);
                JsonObject newRow = afterRows.get(id);
                if (oldRow != null && newRow != null
                        && canonicalJson(oldRow).equals(canonicalJson(newRow)))
                    continue;

                JsonObject operation = new JsonObject();
                operation.addProperty("table", table);
                operation.addProperty("id", id);
                if (newRow == null) {
                    operation.addProperty("action", "delete");
                    operation.addProperty("baseRowSha256", rowSha256(oldRow));
                } else {
                    operation.addProperty("action", "upsert");
                    operation.addProperty("baseRowSha256",
                        oldRow == null ? "ABSENT" : rowSha256(oldRow));
                    operation.add("row", newRow.deepCopy());
                }
                operations.add(operation);
                if (operations.size() > 500) return null;
            }
        }

        if (operations.size() == 0) return null;
        JsonObject payload = new JsonObject();
        payload.addProperty("format", "edhome-record-patch");
        payload.addProperty("version", 1);
        payload.add("operations", operations);
        return new RecordPatchPlan(payload, operations.size());
    }

    private static java.util.Map<String,JsonObject> syncMetaByRow(JsonObject root) {
        java.util.Map<String,JsonObject> result = new java.util.LinkedHashMap<>();
        if (root == null || !root.has("syncRecords")
                || !root.get("syncRecords").isJsonArray()) return result;
        for (JsonElement element : root.getAsJsonArray("syncRecords")) {
            if (!element.isJsonObject()) continue;
            JsonObject meta = element.getAsJsonObject();
            if (!meta.has("table") || !meta.has("rowKey")) continue;
            if (meta.has("deletedAt") && !meta.get("deletedAt").isJsonNull()) continue;
            result.put(meta.get("table").getAsString() + "\u0000"
                + meta.get("rowKey").getAsString(), meta);
        }
        return result;
    }

    private static java.util.Map<String,JsonObject> patchRowsByKey(
            String table, JsonArray rows) {
        java.util.Map<String,JsonObject> result = new java.util.LinkedHashMap<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) return null;
            JsonObject row = element.getAsJsonObject();
            String key = patchRowKey(table, row);
            if (key == null || key.isBlank() || result.put(key, row) != null)
                return null;
        }
        return result;
    }

    private static String patchRowKey(String table, JsonObject row) {
        try {
            if ("task_rotation_members".equals(table))
                return canonicalId(row.get("task_id")) + ":"
                    + canonicalId(row.get("member_id"));
            if ("pantry_packages".equals(table))
                return canonicalId(row.get("pantry_id"));
            return canonicalId(row.get("id"));
        } catch (Exception invalid) {
            return null;
        }
    }

    private static String canonicalId(JsonElement id) {
        if (id == null || id.isJsonNull() || !id.isJsonPrimitive()
                || !id.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Brak numerycznego klucza rekordu.");
        java.math.BigDecimal number =
            new java.math.BigDecimal(id.getAsString());
        return Long.toString(number.longValueExact());
    }

    private static java.util.Map<String,JsonObject> patchRowsById(JsonArray rows) {
        java.util.Map<String,JsonObject> result = new java.util.LinkedHashMap<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) return null;
            JsonObject row = element.getAsJsonObject();
            String id = patchRowId(row);
            if (id == null || id.isBlank() || result.put(id, row) != null)
                return null;
        }
        return result;
    }

    private static String patchRowId(JsonObject row) {
        try {
            return canonicalId(row.get("id"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureDesktopSyncMetadata(JsonObject root) throws Exception {
        if (root == null || !root.has("syncRecords")
                || !root.get("syncRecords").isJsonArray()
                || !root.has("tables") || !root.get("tables").isJsonObject())
            return;

        JsonArray metadata = root.getAsJsonArray("syncRecords");
        java.util.Map<String,JsonObject> active = syncMetaByRow(root);
        JsonObject tables = root.getAsJsonObject("tables");
        long now = System.currentTimeMillis();

        for (String table : tables.keySet()) {
            JsonElement tableElement = tables.get(table);
            if (!tableElement.isJsonArray()) continue;
            for (JsonElement element : tableElement.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                String rowKey = patchRowKey(table, row);
                if (rowKey == null) continue;
                String key = table + "\u0000" + rowKey;
                if (active.containsKey(key)) continue;

                JsonObject meta = new JsonObject();
                meta.addProperty("syncUuid", java.util.UUID.randomUUID().toString());
                meta.addProperty("table", table);
                meta.addProperty("rowKey", rowKey);
                meta.addProperty("revision", 0L);
                meta.addProperty("updatedAt", now);
                meta.add("deletedAt", com.google.gson.JsonNull.INSTANCE);
                meta.addProperty("rowHash", rowSha256(row));
                metadata.add(meta);
                active.put(key, meta);
            }
        }
    }

    private static void applyPatchAck(JsonObject root, JsonArray results) {
        if (root == null || results == null || results.size() == 0
                || !root.has("syncRecords") || !root.get("syncRecords").isJsonArray())
            return;
        JsonArray metadata = root.getAsJsonArray("syncRecords");
        java.util.Map<String,Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < metadata.size(); i++) {
            JsonElement element = metadata.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject meta = element.getAsJsonObject();
            if (meta.has("syncUuid"))
                index.put(meta.get("syncUuid").getAsString().toLowerCase(Locale.ROOT), i);
        }
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            JsonObject meta = element.getAsJsonObject();
            if (!meta.has("syncUuid")) continue;
            String uuid = meta.get("syncUuid").getAsString().toLowerCase(Locale.ROOT);
            Integer position = index.get(uuid);
            if (position == null) {
                metadata.add(meta.deepCopy());
                index.put(uuid, metadata.size() - 1);
            } else {
                metadata.set(position, meta.deepCopy());
            }
        }
    }

    private static String rowSha256(JsonObject row) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(row).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest)
            out.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
        return out.toString();
    }

    private static String canonicalJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            java.util.List<String> keys = new ArrayList<>(object.keySet());
            java.util.Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(GSON.toJson(key)).append(':')
                    .append(canonicalJson(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) out.append(',');
                out.append(canonicalJson(array.get(i)));
            }
            return out.append(']').toString();
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return Boolean.toString(primitive.getAsBoolean());
        if (primitive.isNumber()) {
            try {
                java.math.BigDecimal number =
                    new java.math.BigDecimal(primitive.getAsString());
                if (number.compareTo(java.math.BigDecimal.ZERO) == 0) return "0";
                return number.stripTrailingZeros().toPlainString();
            } catch (Exception ignored) {
                return primitive.getAsString();
            }
        }
        return GSON.toJson(primitive.getAsString());
    }

    private static final class RecordPatchPlan {
        final JsonObject payload;
        final int operations;

        RecordPatchPlan(JsonObject payload, int operations) {
            this.payload = payload;
            this.operations = operations;
        }
    }

    private static final class SyncWriteResult {
        final JsonObject data;
        final String sha256;
        final long revision;
        final boolean patchUsed;
        final int operations;

        SyncWriteResult(JsonObject data, String sha256, long revision,
                boolean patchUsed, int operations) {
            this.data = data;
            this.sha256 = sha256 == null ? "" : sha256;
            this.revision = revision;
            this.patchUsed = patchUsed;
            this.operations = operations;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable x = error;
        while (x.getCause() != null) x = x.getCause();
        String message = x.getMessage();
        return message == null || message.isBlank() ? x.getClass().getSimpleName() : message;
    }

    private final class JsonTableModel extends AbstractTableModel {
        private final List<JsonObject> rows = new ArrayList<>();
        private final String[][] cols;

        JsonTableModel(JsonArray input, String[][] cols) {
            for (JsonElement e : input)
                if (e.isJsonObject()) rows.add(e.getAsJsonObject());
            this.cols = cols;
        }

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int column) { return cols[column][0]; }

        public Object getValueAt(int row, int column) {
            return value(rows.get(row), cols[column][1]);
        }

        @Override public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override public void setValueAt(Object input, int row, int column) {
            JsonObject target = rows.get(row);
            String key = cols[column][1];
            JsonElement currentValue = target.get(key);
            String text = input == null ? "" : input.toString().trim();
            try {
                if (currentValue != null && !currentValue.isJsonNull()
                        && currentValue.isJsonPrimitive()
                        && currentValue.getAsJsonPrimitive().isNumber()) {
                    if (text.isBlank()) target.add(key, com.google.gson.JsonNull.INSTANCE);
                    else target.addProperty(key, Long.parseLong(text));
                } else if (currentValue != null && !currentValue.isJsonNull()
                        && currentValue.isJsonPrimitive()
                        && currentValue.getAsJsonPrimitive().isBoolean()) {
                    target.addProperty(key, Boolean.parseBoolean(text));
                } else {
                    if (text.isBlank() && (currentValue == null || currentValue.isJsonNull()))
                        target.add(key, com.google.gson.JsonNull.INSTANCE);
                    else target.addProperty(key, text);
                }
                markDirty();
                fireTableCellUpdated(row, column);
            } catch (Exception invalid) {
                JOptionPane.showMessageDialog(EdhomeDesktop.this,
                    "Nieprawidłowa wartość dla pola „" + cols[column][0] + "”.");
            }
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;
        RoundedPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class SnapshotResult {
        final JsonObject data;
        final String sha256;
        final long revision;

        SnapshotResult(JsonObject data, String sha256) {
            this(data, sha256, -1L);
        }

        SnapshotResult(JsonObject data, String sha256, long revision) {
            this.data = data;
            this.sha256 = sha256 == null ? "" : sha256;
            this.revision = revision;
        }
    }

    private static final class PatchResult {
        final String sha256;
        final long revision;
        final JsonArray results;

        PatchResult(String sha256, long revision, JsonArray results) {
            this.sha256 = sha256 == null ? "" : sha256;
            this.revision = revision;
            this.results = results == null ? new JsonArray() : results;
        }
    }

    private static final class PatchUnsupportedException extends IOException {
        PatchUnsupportedException() {
            super("Telefon ma starszą wersję synchronizacji przyrostowej.");
        }
    }

    private static final class PairPayload {
        final String phoneIp;
        final String token;
        final String version;
        PairPayload(String phoneIp, String token, String version) {
            this.phoneIp = phoneIp;
            this.token = token;
            this.version = version;
        }
    }

    private static final class QrPairingSession implements AutoCloseable {
        private final ServerSocket server;
        private final String host;
        private final String nonce;
        private final Consumer<PairPayload> callback;
        private volatile boolean closed;
        private Thread thread;

        private QrPairingSession(ServerSocket server, String host, String nonce,
                Consumer<PairPayload> callback) {
            this.server = server;
            this.host = host;
            this.nonce = nonce;
            this.callback = callback;
        }

        static QrPairingSession start(Consumer<PairPayload> callback) throws Exception {
            String host = localAddress();
            if (host == null)
                throw new IOException("Brak lokalnego adresu IPv4 Wi‑Fi/LAN.");
            byte[] random = new byte[18];
            new SecureRandom().nextBytes(random);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName(host), PAIR_PORT), 4);
            server.setSoTimeout(180000);

            QrPairingSession session =
                new QrPairingSession(server, host, nonce, callback);
            session.thread = new Thread(session::acceptLoop, "edhome-desktop-qr-pair");
            session.thread.setDaemon(true);
            session.thread.start();
            return session;
        }

        String qrText() {
            return "edhome://desktop-pair?v=1&host=" + host
                + "&port=" + PAIR_PORT + "&nonce=" + nonce;
        }

        private void acceptLoop() {
            long deadline = System.currentTimeMillis() + 180000L;
            try {
                while (!closed && System.currentTimeMillis() < deadline) {
                    try (Socket peer = server.accept()) {
                        if (handle(peer)) return;
                    } catch (SocketTimeoutException timeout) {
                        return;
                    } catch (IOException error) {
                        if (!closed) continue;
                        return;
                    }
                }
            } finally {
                close();
            }
        }

        private boolean handle(Socket peer) throws IOException {
            peer.setSoTimeout(7000);
            InetAddress remote = peer.getInetAddress();
            if (remote == null
                    || (!(remote instanceof Inet4Address))
                    || (!remote.isSiteLocalAddress() && !remote.isLoopbackAddress())) {
                reply(peer, 403, "{\"error\":\"LAN_ONLY\"}");
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(
                peer.getInputStream(), StandardCharsets.US_ASCII));
            String request = in.readLine();
            if (request == null || !request.startsWith("POST /pair ")) {
                reply(peer, 405, "{\"error\":\"PAIR_POST_ONLY\"}");
                return false;
            }

            String suppliedNonce = "";
            int contentLength = -1;
            int headerBytes = request.length();
            for (String line; (line = in.readLine()) != null && !line.isEmpty();) {
                headerBytes += line.length();
                if (headerBytes > 16384) {
                    reply(peer, 431, "{\"error\":\"HEADERS_TOO_LARGE\"}");
                    return false;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if ("x-edhome-nonce".equals(name)) suppliedNonce = value;
                if ("content-length".equals(name)) {
                    try { contentLength = Integer.parseInt(value); }
                    catch (NumberFormatException ignored) { contentLength = -1; }
                }
            }

            if (!constantTimeEquals(nonce, suppliedNonce)) {
                reply(peer, 401, "{\"error\":\"PAIR_NONCE\"}");
                return false;
            }
            if (contentLength <= 0 || contentLength > 2048) {
                reply(peer, 400, "{\"error\":\"PAIR_BODY\"}");
                return false;
            }

            char[] chars = new char[contentLength];
            int offset = 0;
            while (offset < chars.length) {
                int n = in.read(chars, offset, chars.length - offset);
                if (n < 0) break;
                offset += n;
            }
            if (offset != chars.length) {
                reply(peer, 400, "{\"error\":\"PAIR_BODY_SHORT\"}");
                return false;
            }

            String token = "";
            String version = "";
            for (String line : new String(chars).split("\\r?\\n")) {
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                if ("token".equals(key)) token = value;
                else if ("version".equals(key)) version = value;
            }
            if (!token.matches("[A-Za-z0-9_-]{10,128}")
                    || !version.matches("[A-Za-z0-9._-]{1,64}")) {
                reply(peer, 400, "{\"error\":\"PAIR_DATA\"}");
                return false;
            }

            String phoneIp = remote.getHostAddress();
            reply(peer, 200, "{\"ok\":true}");
            callback.accept(new PairPayload(phoneIp, token, version));
            return true;
        }

        @Override public void close() {
            closed = true;
            try { server.close(); } catch (IOException ignored) { }
            if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        }

        private static void reply(Socket socket, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.US_ASCII));
            out.write("HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\n");
            out.write("Content-Type: application/json\r\n");
            out.write("Content-Length: " + bytes.length + "\r\n");
            out.write("Connection: close\r\n\r\n");
            out.flush();
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
        }

        private static boolean constantTimeEquals(String expected, String supplied) {
            if (expected == null || supplied == null) return false;
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
        }

        private static String localAddress() {
            String fallback = null;
            try {
                for (NetworkInterface network :
                        Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!network.isUp() || network.isLoopback()) continue;
                    String name = ((network.getName() == null ? "" : network.getName()) + " "
                        + (network.getDisplayName() == null ? "" : network.getDisplayName()))
                        .toLowerCase(Locale.ROOT);
                    boolean virtual = name.contains("virtual") || name.contains("vpn")
                        || name.contains("tun") || name.contains("tap")
                        || name.contains("docker") || name.contains("hyper-v")
                        || name.contains("vmware");
                    boolean preferred = name.contains("wi-fi") || name.contains("wifi")
                        || name.contains("wireless") || name.contains("wlan")
                        || name.contains("ethernet") || name.startsWith("eth");
                    for (InetAddress address :
                            Collections.list(network.getInetAddresses())) {
                        if (!(address instanceof Inet4Address)
                                || !address.isSiteLocalAddress()
                                || address.isLoopbackAddress()) continue;
                        String value = address.getHostAddress();
                        if (preferred && !virtual) return value;
                        if (!virtual && fallback == null) fallback = value;
                    }
                }
            } catch (Exception ignored) { }
            return fallback;
        }
    }

    private static final class LanClient {
        private final String host;
        private final int port;
        private final String token;
        private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

        LanClient(String host, int port, String token) {
            this.host = normalizeHost(host);
            this.port = port;
            this.token = token;
        }

        SnapshotResult snapshot() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/snapshot"))
                .timeout(Duration.ofSeconds(8))
                .header("X-EDHOME-TOKEN", token)
                .header("Accept", "application/json")
                .GET().build();
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odpowiedział HTTP " + response.statusCode() + ".");
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            validate(root);
            long revision = -1L;
            try { revision = state(); } catch (Exception ignored) { }
            return new SnapshotResult(root,
                response.headers().firstValue("X-EDHOME-SNAPSHOT-SHA256").orElse(""),
                revision);
        }

        long state() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/state"))
                .timeout(Duration.ofSeconds(4))
                .header("X-EDHOME-TOKEN", token)
                .header("Accept", "application/json")
                .GET().build();
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) return -1L;
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odpowiedział HTTP " + response.statusCode() + ".");
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return root.has("revision") ? root.get("revision").getAsLong() : -1L;
        }

        PatchResult patch(JsonObject patch) throws Exception {
            String json = GSON.toJson(patch);
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/patch"))
                .timeout(Duration.ofSeconds(15))
                .header("X-EDHOME-TOKEN", token)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404)
                throw new PatchUnsupportedException();
            if (response.statusCode() == 409) {
                String detail = "";
                try {
                    JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (error.has("table")) {
                        String key = error.has("rowKey")
                            ? error.get("rowKey").getAsString()
                            : (error.has("id") ? error.get("id").getAsString() : "");
                        detail = " (" + error.get("table").getAsString()
                            + (key.isBlank() ? "" : " #" + key) + ")";
                    }
                } catch (Exception ignored) { }
                throw new IOException("Konflikt rekordu" + detail
                    + " — ten sam element zmienił się na innym urządzeniu.");
            }
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odrzucił zmianę rekordową: HTTP "
                    + response.statusCode() + ".");
            String sha = response.headers()
                .firstValue("X-EDHOME-SNAPSHOT-SHA256").orElse("");
            JsonArray results = new JsonArray();
            try {
                JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                if (body.has("results") && body.get("results").isJsonArray())
                    results = body.getAsJsonArray("results");
            } catch (Exception ignored) { }
            long revision = -1L;
            try { revision = state(); } catch (Exception ignored) { }
            return new PatchResult(sha, revision, results);
        }

        SnapshotResult write(JsonObject data, String baseSha) throws Exception {
            String json = GSON.toJson(data);
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/snapshot"))
                .timeout(Duration.ofSeconds(15))
                .header("X-EDHOME-TOKEN", token)
                .header("X-EDHOME-BASE-SHA256", baseSha)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 409)
                throw new IOException("Telefon ma nowsze dane niż kopia na PC.");
            if (response.statusCode() == 428)
                throw new IOException("Brak wersji bazowej — pobierz dane ponownie.");
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odrzucił zapis: HTTP "
                    + response.statusCode() + ".");
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            validate(root);
            long revision = -1L;
            try { revision = state(); } catch (Exception ignored) { }
            return new SnapshotResult(root,
                response.headers().firstValue("X-EDHOME-SNAPSHOT-SHA256").orElse(""),
                revision);
        }

        static String discover(String token, int port) {
            String local = QrPairingSession.localAddress();
            if (local == null || !local.matches("[0-9]+(\\.[0-9]+){3}"))
                return null;
            int dot = local.lastIndexOf('.');
            if (dot <= 0) return null;
            String prefix = local.substring(0, dot + 1);

            ExecutorService pool = Executors.newFixedThreadPool(32);
            try {
                java.util.List<Callable<String>> tasks = new ArrayList<>();
                for (int i = 1; i <= 254; i++) {
                    String candidate = prefix + i;
                    if (candidate.equals(local)) continue;
                    tasks.add(() -> probe(candidate, port, token) ? candidate : null);
                }
                java.util.List<Future<String>> futures =
                    pool.invokeAll(tasks, 6, TimeUnit.SECONDS);
                for (Future<String> future : futures) {
                    if (future.isCancelled()) continue;
                    try {
                        String host = future.get();
                        if (host != null) return host;
                    } catch (Exception ignored) { }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                pool.shutdownNow();
            }
            return null;
        }

        private static boolean probe(String host, int port, String token) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 450);
                socket.setSoTimeout(900);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
                out.write("GET /status HTTP/1.1\\r\\n");
                out.write("Host: " + host + "\\r\\n");
                out.write("X-EDHOME-TOKEN: " + token + "\\r\\n");
                out.write("Connection: close\\r\\n\\r\\n");
                out.flush();
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
                String status = in.readLine();
                return status != null && status.contains(" 200 ");
            } catch (Exception ignored) {
                return false;
            }
        }

        private static String normalizeHost(String raw) {
            String value = raw.trim()
                .replaceFirst("^https?://", "")
                .replaceAll("/.*$", "");
            int colon = value.lastIndexOf(':');
            if (colon > 0 && value.indexOf(':') == colon) value = value.substring(0, colon);
            if (!value.matches("[A-Za-z0-9._-]+"))
                throw new IllegalArgumentException("Nieprawidłowy adres telefonu.");
            return value.toLowerCase(Locale.ROOT);
        }
    }
}
