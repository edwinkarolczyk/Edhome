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
import java.time.LocalDate;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class EdhomeDesktop extends JFrame {
    private static final int PORT = 45823;
    private static final int PAIR_PORT = 45824;
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
    private String current = "Pulpit";
    private QrPairingSession qrPairingSession;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EdhomeDesktop().setVisible(true));
    }

    private EdhomeDesktop() {
        super("EDHOME Desktop Beta");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.add(topBar(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        loadCache();
        showSection("Pulpit");
    }

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(new EmptyBorder(0, 0, 12, 0));
        JLabel title = new JLabel("EDHOME  •  DESKTOP BETA");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        connection.setHorizontalAlignment(SwingConstants.RIGHT);
        bar.add(title, BorderLayout.WEST);
        bar.add(connection, BorderLayout.EAST);
        return bar;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(0, 0, 0, 14));
        side.setPreferredSize(new Dimension(180, 1));
        for (String name : NAV) {
            JButton b = new JButton(name);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.addActionListener(e -> showSection(name));
            side.add(b);
            side.add(Box.createVerticalStrut(6));
        }
        side.add(Box.createVerticalGlue());
        JLabel mode = new JLabel("<html><b>MVP:</b> Android → PC<br>odczyt i zapis</html>");
        mode.setForeground(new Color(110, 110, 110));
        side.add(mode);
        return side;
    }

    private void showSection(String name) {
        current = name;
        content.removeAll();
        content.add(section(name), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
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
            cols("Produkt","name","Ilość ×1000","qty_milli","Jednostka","unit",
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
        JPanel cards = new JPanel(new GridLayout(2, 4, 12, 12));
        cards.add(metric("Zadania", count("tasks")));
        cards.add(metric("Do zrobienia", countWhere("tasks","done",0)));
        cards.add(metric("Spiżarnia", count("pantry")));
        cards.add(metric("Magazyn", count("storage_items")));
        cards.add(metric("Zakupy", countWhere("shopping_items","checked",0)));
        cards.add(metric("Pojazdy", count("vehicles")));
        cards.add(metric("PayCheck", count("paycheck_transactions")));
        cards.add(metric("Miejsca", count("places")));
        page.add(cards, BorderLayout.NORTH);

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setBorder(new EmptyBorder(18, 4, 4, 4));
        info.setText(snapshot == null
            ? "Brak danych. Wejdź w Ustawienia, wpisz adres telefonu i kod parowania, "
              + "a potem wybierz „Pobierz przez Wi‑Fi”."
            : "Źródło: EDHOME Android " + str(snapshot, "sourceVersion", "?")
              + "\nUtworzono snapshot: " + str(snapshot, "createdAt", "?")
              + "\n\nTa wersja PC jest celowo tylko do odczytu. Dzięki temu można już "
              + "wygodnie przeglądać dane na dużym ekranie bez ryzyka nadpisania telefonu.");
        page.add(info, BorderLayout.CENTER);
        return page;
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
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextField ip = new JTextField(PREFS.get("phoneIp", ""), 18);
        JTextField token = new JTextField(PREFS.get("token", ""), 18);
        JButton qrPair = new JButton("Pokaż QR do połączenia");
        JButton pull = new JButton("Pobierz ręcznie przez Wi‑Fi");
        JButton importFile = new JButton("Wczytaj backup JSON");
        JLabel help = new JLabel("<html><b>Najszybciej:</b> kliknij „Pokaż QR do połączenia”, "
            + "a na telefonie EDHOME wybierz <b>Ustawienia → Skanuj QR z ekranu PC</b>.<br>"
            + "Telefon i PC muszą być w tej samej sieci Wi‑Fi/LAN. "
            + "Adres i kod poniżej zostają jako awaryjne połączenie ręczne.</html>");

        g.gridx=0; g.gridy=0; g.weightx=0; form.add(new JLabel("Adres telefonu:"),g);
        g.gridx=1; g.weightx=1; form.add(ip,g);
        g.gridx=0; g.gridy=1; g.weightx=0; form.add(new JLabel("Kod parowania:"),g);
        g.gridx=1; g.weightx=1; form.add(token,g);
        g.gridx=0; g.gridy=2; g.gridwidth=2; g.weightx=1; form.add(help,g);
        g.gridy=3; g.gridwidth=2; form.add(qrPair,g);
        g.gridy=4; g.gridwidth=1; g.weightx=.5; form.add(pull,g);
        g.gridx=1; form.add(importFile,g);

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

        page.add(form, BorderLayout.NORTH);
        JTextArea notes = new JTextArea(
            "Połączenie QR jest jednorazowo potwierdzane losowym kodem i działa tylko w sieci lokalnej.\n"
          + "Po zeskanowaniu Desktop zapisuje adres telefonu oraz kod lokalnego odczytu i od razu pobiera dane.\n\n"
          + "Kolejny etap:\n"
          + "• automatyczne wykrywanie telefonu w LAN bez otwierania QR,\n"
          + "• synchronizacja przyrostowa zamiast pełnego snapshotu,\n"
          + "• edycja na PC po dodaniu updatedAt/deviceId i rozwiązywania konfliktów.");
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setBorder(new EmptyBorder(20, 4, 4, 4));
        page.add(notes, BorderLayout.CENTER);
        return page;
    }

    private void pullFromPhone(String host, String secret, JButton trigger) {
        PREFS.put("phoneIp", host);
        PREFS.put("token", secret);
        if (trigger != null) trigger.setEnabled(false);
        connection.setText("ŁĄCZENIE…");
        new SwingWorker<SnapshotResult,Void>() {
            @Override protected SnapshotResult doInBackground() throws Exception {
                return new LanClient(host, PORT, secret).snapshot();
            }
            @Override protected void done() {
                if (trigger != null) trigger.setEnabled(true);
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
                    dirty = false;
                    validate(snapshot);
                    saveCache(snapshot);
                    connection.setText("ONLINE • EDYCJA • Android "
                        + str(snapshot,"sourceVersion",""));
                    showSection(current);
                } catch (Exception ex) {
                    connection.setText("OFFLINE • błąd połączenia");
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie pobrano danych:\n" + rootMessage(ex),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
        page.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        page.add(title, BorderLayout.NORTH);
        return page;
    }

    private JPanel metric(String title, int value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210,210,210)),
            new EmptyBorder(16,16,16,16)));
        JLabel number = new JLabel(Integer.toString(value));
        number.setFont(number.getFont().deriveFont(Font.BOLD, 28f));
        card.add(new JLabel(title), BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        return card;
    }

    private JComponent placeholderPage(String title, String description) {
        JPanel page = page(title);
        JTextArea note = new JTextArea(description);
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
        JTable table = new JTable(new JsonTableModel(rows, columns));
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        page.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        JLabel count = new JLabel("Pozycji: " + rows.size()
            + "  •  kliknij komórkę, aby edytować");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton reload = new JButton("Pobierz ponownie");
        JButton save = new JButton("Zapisz zmiany do telefonu");
        reload.addActionListener(e -> reloadFromPhone(reload));
        save.addActionListener(e -> saveChangesToPhone(save));
        actions.add(reload);
        actions.add(save);
        footer.add(count, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        return page;
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
                trigger.setEnabled(true);
                try {
                    SnapshotResult result = get();
                    snapshot = result.data;
                    snapshotHash = result.sha256;
                    dirty = false;
                    validate(snapshot);
                    saveCache(snapshot);
                    connection.setText("ONLINE • EDYCJA • Android "
                        + str(snapshot,"sourceVersion",""));
                    showSection(current);
                } catch (Exception ex) {
                    connection.setText("OFFLINE • błąd odświeżania");
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie pobrano danych:\n" + rootMessage(ex),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void saveChangesToPhone(JButton trigger) {
        if (!dirty) {
            JOptionPane.showMessageDialog(this, "Nie ma zmian do zapisania.");
            return;
        }
        String host = PREFS.get("phoneIp", "").trim();
        String secret = PREFS.get("token", "").trim();
        if (host.isBlank() || secret.isBlank() || snapshotHash.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw pobierz świeże dane z telefonu. "
                    + "Kopia offline nie może nadpisać telefonu.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "Zapisać zmiany z PC do telefonu?\n"
                + "EDHOME sprawdzi, czy dane na telefonie nie zmieniły się "
                + "od ostatniego pobrania.",
            "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        trigger.setEnabled(false);
        connection.setText("ZAPIS DO TELEFONU…");
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
                    connection.setText("ONLINE • EDYCJA • zapisano");
                    showSection(current);
                } catch (Exception ex) {
                    connection.setText("ONLINE • zapis odrzucony");
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie zapisano zmian:\n" + rootMessage(ex)
                            + "\n\nJeśli telefon zmienił dane w międzyczasie, "
                            + "wybierz „Pobierz ponownie”, sprawdź różnice "
                            + "i wprowadź zmianę jeszcze raz.",
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
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
                dirty = true;
                connection.setText("ONLINE • niezapisane zmiany z PC");
                fireTableCellUpdated(row, column);
            } catch (Exception invalid) {
                JOptionPane.showMessageDialog(EdhomeDesktop.this,
                    "Nieprawidłowa wartość dla pola „" + cols[column][0] + "”.");
            }
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
