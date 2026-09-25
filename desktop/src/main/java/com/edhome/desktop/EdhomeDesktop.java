package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

public final class EdhomeDesktop extends JFrame {
    private static final int PORT = 45823;
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
    private String current = "Pulpit";

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
        JLabel mode = new JLabel("<html><b>MVP:</b> Android → PC<br>tylko odczyt</html>");
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
        JButton pull = new JButton("Pobierz przez Wi‑Fi");
        JButton importFile = new JButton("Wczytaj backup JSON");
        JLabel help = new JLabel("<html>Na telefonie: <b>Ustawienia → EDHOME Desktop • Wi‑Fi</b>. "
            + "Przepisz adres i kod. Telefon oraz PC muszą być w tej samej sieci.<br>"
            + "Dane pobierane są tylko z telefonu do PC. Prywatny PayCheck nie trafia do zwykłego backupu.</html>");

        g.gridx=0; g.gridy=0; g.weightx=0; form.add(new JLabel("Adres telefonu:"),g);
        g.gridx=1; g.weightx=1; form.add(ip,g);
        g.gridx=0; g.gridy=1; g.weightx=0; form.add(new JLabel("Kod parowania:"),g);
        g.gridx=1; g.weightx=1; form.add(token,g);
        g.gridx=0; g.gridy=2; g.gridwidth=2; g.weightx=1; form.add(help,g);
        g.gridy=3; g.gridwidth=1; g.weightx=.5; form.add(pull,g);
        g.gridx=1; form.add(importFile,g);

        pull.addActionListener(e -> {
            String host = ip.getText().trim();
            String secret = token.getText().trim();
            if (host.isBlank() || secret.isBlank()) {
                JOptionPane.showMessageDialog(this, "Wpisz adres telefonu i kod parowania.");
                return;
            }
            PREFS.put("phoneIp", host);
            PREFS.put("token", secret);
            pull.setEnabled(false);
            connection.setText("ŁĄCZENIE…");
            new SwingWorker<JsonObject,Void>() {
                @Override protected JsonObject doInBackground() throws Exception {
                    return new LanClient(host, PORT, secret).snapshot();
                }
                @Override protected void done() {
                    pull.setEnabled(true);
                    try {
                        snapshot = get();
                        validate(snapshot);
                        saveCache(snapshot);
                        connection.setText("ONLINE • Android " + str(snapshot,"sourceVersion",""));
                        showSection(current);
                    } catch (Exception ex) {
                        connection.setText("OFFLINE • błąd połączenia");
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Nie pobrano danych:\n" + rootMessage(ex),
                            "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        importFile.addActionListener(e -> importBackup());

        page.add(form, BorderLayout.NORTH);
        JTextArea notes = new JTextArea(
            "Plan kolejnego etapu:\n"
          + "• automatyczne wykrywanie telefonu w LAN,\n"
          + "• synchronizacja przyrostowa zamiast pełnego snapshotu,\n"
          + "• edycja na PC po dodaniu updatedAt/deviceId i rozwiązywania konfliktów,\n"
          + "• później SUPLA/NAS bez wystawiania EDHOME do Internetu.");
        notes.setEditable(false);
        notes.setBorder(new EmptyBorder(20, 4, 4, 4));
        page.add(notes, BorderLayout.CENTER);
        return page;
    }

    private void importBackup() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String json = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            validate(parsed);
            snapshot = parsed;
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
        JLabel footer = new JLabel("Pozycji: " + rows.size() + "  •  tryb tylko do odczytu");
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

    private static String rootMessage(Throwable error) {
        Throwable x = error;
        while (x.getCause() != null) x = x.getCause();
        String message = x.getMessage();
        return message == null || message.isBlank() ? x.getClass().getSimpleName() : message;
    }

    private static final class JsonTableModel extends AbstractTableModel {
        private final List<JsonObject> rows = new ArrayList<>();
        private final String[][] cols;

        JsonTableModel(JsonArray input, String[][] cols) {
            for (JsonElement e : input) if (e.isJsonObject()) rows.add(e.getAsJsonObject());
            this.cols = cols;
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int column) { return cols[column][0]; }
        public Object getValueAt(int row, int column) {
            return value(rows.get(row), cols[column][1]);
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

        JsonObject snapshot() throws Exception {
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
            return root;
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
