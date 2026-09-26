package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final String DESKTOP_VERSION = "0.6.0.46";
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Preferences PREFS =
        Preferences.userRoot().node("edhome/desktop-beta");

    private static final String[] NAV = {
        "Pulpit", "Dzisiaj", "Kalendarz", "Zadania", "Czynności",
        "Magazyn", "Spiżarnia", "Zakupy", "PayCheck", "Pojazdy",
        "Odpady", "Timery", "Energia", "SUPLA", "Miejsca", "Ustawienia"
    };

    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel connection = new JLabel("OFFLINE • lokalna kopia");
    private JsonObject snapshot;
    private String snapshotHash = "";
    private boolean dirty;
    private boolean connected;
    private boolean connecting;
    private String current = "Pulpit";
    private QrPairingSession qrPairingSession;
    private TrayIcon trayIcon;
    private javax.swing.Timer reconnectTimer;
    private javax.swing.Timer autoSaveTimer;
    private boolean autoSaving;
    private final Map<String,Integer> sectionScrollY = new java.util.HashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EdhomeDesktop::new);
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
        if ("PayCheck".equals(name)) return tablePage("PayCheck • wspólne", "paycheck_transactions",
            cols("Typ","kind","Kategoria","category","Kwota [gr]","amount_grosz",
                 "Status","status","Źródło","confirmation_source","Data","created_at"));
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

    private JComponent calendar() {
        return tablePage("Kalendarz • najbliższe terminy", "tasks",
            cols("Termin","due_date","Zadanie","title","Powtarzanie","repeat_rule",
                 "Przypomnienie","remind_time","Wykonane","done"));
    }

    private JComponent waste() {
        JsonArray rows = table("tasks");
        JsonArray filtered = new JsonArray();
        for (JsonElement el : rows) {
            JsonObject row = el.getAsJsonObject();
            if ("waste".equalsIgnoreCase(value(row,"task_kind"))
                    || !value(row,"waste_fraction").isBlank()) filtered.add(row);
        }
        return tablePage("Odpady", filtered,
            cols("Frakcja","waste_fraction","Termin","due_date","Wystawione","done",
                 "Przypomnienie","remind_time"));
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
          + "• formularze Dodaj/Usuń dla kolejnych modułów,\n"
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
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
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
        if (!PREFS.getBoolean("autoConnect", true) || connecting || dirty) return;
        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank()) return;
        pullFromPhone(host, secret, null, silent);
    }

    private void startReconnectLoop() {
        reconnectTimer = new javax.swing.Timer(30000, e -> {
            if (connecting || autoSaving) return;
            if (dirty && PREFS.getBoolean("autoWrite", true))
                saveChangesToPhone(null, true);
            else if (PREFS.getBoolean("autoConnect", true) && !dirty)
                autoConnectSaved(true);
        });
        reconnectTimer.setInitialDelay(30000);
        reconnectTimer.start();
    }

    private void startAutoSaveLoop() {
        autoSaveTimer = new javax.swing.Timer(1200, e -> {
            if (PREFS.getBoolean("autoWrite", true) && dirty)
                saveChangesToPhone(null, true);
        });
        autoSaveTimer.setRepeats(false);
    }

    private void scheduleAutoSave() {
        if (!PREFS.getBoolean("autoWrite", true) || autoSaveTimer == null) return;
        autoSaveTimer.restart();
        connection.setText(connected
            ? "ONLINE • zmiana czeka na auto-zapis"
            : "OFFLINE • zmiana czeka na telefon");
    }

    private void markDirty() {
        dirty = true;
        scheduleAutoSave();
        if (!PREFS.getBoolean("autoWrite", true))
            connection.setText("ONLINE • niezapisane zmiany z PC");
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
        return tablePage(title, table(table), columns);
    }

    private JComponent tablePage(String title, JsonArray rows, String[][] columns) {
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
                list.add(recordCard(el.getAsJsonObject(), columns));
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
        actions.add(reload);
        actions.add(save);
        footer.add(count, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private JPanel recordCard(JsonObject row, String[][] columns) {
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
        JButton edit = actionButton("Edytuj");
        edit.addActionListener(e -> editRow(row, columns));
        header.add(edit, BorderLayout.EAST);
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

    private void editRow(JsonObject row, String[][] columns) {
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

        int result = JOptionPane.showConfirmDialog(this, form,
            "EDHOME • edytuj", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            for (Map.Entry<String,JComponent> entry : editors.entrySet())
                applyEditor(row, entry.getKey(), entry.getValue());
            markDirty();
            showSection(current);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie zapisano zmiany: " + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
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
        if (host.isBlank() || secret.isBlank() || snapshotHash.isBlank()) {
            if (automatic) {
                connection.setText("OFFLINE • zmiana czeka na świeże połączenie");
                return;
            }
            JOptionPane.showMessageDialog(this,
                "Najpierw pobierz świeże dane z telefonu. "
                    + "Kopia offline nie może nadpisać telefonu.");
            return;
        }
        if (!automatic) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Zapisać zmiany z PC do telefonu?\n"
                    + "EDHOME sprawdzi, czy dane na telefonie nie zmieniły się "
                    + "od ostatniego pobrania.",
                "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        autoSaving = true;
        if (trigger != null) trigger.setEnabled(false);
        connection.setText(automatic ? "AUTO-SYNC • zapis do telefonu…" : "ZAPIS DO TELEFONU…");
        new SwingWorker<SnapshotResult,Void>() {
            @Override protected SnapshotResult doInBackground() throws Exception {
                return new LanClient(host, PORT, secret).write(snapshot, snapshotHash);
            }
            @Override protected void done() {
                trigger.setEnabled(true);
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
                    dirty = false;
                    saveCache(snapshot);
                    connection.setText(automatic
                        ? "ONLINE • auto-sync zapisany"
                        : "ONLINE • EDYCJA • zapisano");
                    if (!automatic) showSection(current);
                    updateTrayTooltip();
                } catch (Exception ex) {
                    String message = rootMessage(ex);
                    connection.setText(message.contains("nowsze dane")
                        ? "KONFLIKT • telefon ma nowsze dane"
                        : "OFFLINE • auto-zapis oczekuje");
                    if (automatic) {
                        if (trayIcon != null && message.contains("nowsze dane"))
                            trayIcon.displayMessage("EDHOME Desktop",
                                "Konflikt synchronizacji. Zmiany z PC nie zostały nadpisane.",
                                TrayIcon.MessageType.WARNING);
                    } else {
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Nie zapisano zmian:\n" + message
                                + "\n\nJeśli telefon zmienił dane w międzyczasie, "
                                + "wybierz „Pobierz ponownie”, sprawdź różnice "
                                + "i wprowadź zmianę jeszcze raz.",
                            "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
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

        SnapshotResult(JsonObject data, String sha256) {
            this.data = data;
            this.sha256 = sha256 == null ? "" : sha256;
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
            return new SnapshotResult(root,
                response.headers().firstValue("X-EDHOME-SNAPSHOT-SHA256").orElse(""));
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
            return new SnapshotResult(root,
                response.headers().firstValue("X-EDHOME-SNAPSHOT-SHA256").orElse(""));
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
