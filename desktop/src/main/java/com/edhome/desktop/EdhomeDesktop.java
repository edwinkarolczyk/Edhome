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
import javax.imageio.ImageIO;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
    private static final String DESKTOP_VERSION = "0.7.0.96";
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
    private static final Path PC_BACKUP_DIR = Path.of(
        System.getProperty("user.home"), "EDHOME", "Backup");
    private static final Path INSTANCE_LOCK_PATH = Path.of(
        System.getProperty("user.home"), ".edhome", "desktop-instance.lock");
    private static FileChannel instanceLockChannel;
    private static FileLock instanceLock;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Preferences PREFS =
        Preferences.userRoot().node("edhome/desktop-beta");

    private static final String[] NAV = {
        "Pulpit", "Dzisiaj", "Kalendarz", "Zadania", "Czynności", "Projekty",
        "Magazyn", "Pomieszczenia", "Mapa", "Spiżarnia", "Zakupy", "PayCheck", "Pojazdy",
        "Ogród", "Odpady", "Timery", "Energia", "SUPLA", "Miejsca", "Skaner", "Ustawienia"
    };

    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel connection = new JLabel("OFFLINE • lokalna kopia");
    private JsonObject snapshot;
    private JsonObject syncedSnapshot;
    private String snapshotHash = "";
    private long phoneRevision = -1L;
    private long phoneChangeCursorAt;
    private String phoneChangeCursorUuid = "";
    private long lastFullReconcileAt;
    private long localEditGeneration;
    private boolean stateChecking;
    private boolean syncConflictPaused;
    private boolean dirty;
    private boolean connected;
    private boolean connecting;
    private String current = "Pulpit";
    private long desktopProjectId;
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

        DesktopDiagnosticLog.init(DESKTOP_VERSION);

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
                DesktopDiagnosticLog.error("APP_START", error);
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

    /** Czyści stary zapis typu 192.168.x.x:45823 i nigdy nie uznaje
     * własnego adresu komputera za adres telefonu. */
    private static String sanitizedPhoneHost() {
        String raw = PREFS.get("phoneIp", "").trim();
        if (raw.isBlank()) return "";
        try {
            String normalized = LanClient.normalizeHost(raw);
            if (QrPairingSession.localAddresses().contains(normalized)) {
                PREFS.remove("phoneIp");
                DesktopDiagnosticLog.event("PHONE_IP_SELF_REJECTED",
                    "host=" + normalized);
                return "";
            }
            if (!normalized.equals(raw)) {
                PREFS.put("phoneIp", normalized);
                DesktopDiagnosticLog.event("PHONE_IP_NORMALIZED",
                    "host=" + normalized);
            }
            return normalized;
        } catch (Exception error) {
            PREFS.remove("phoneIp");
            DesktopDiagnosticLog.error("PHONE_IP_INVALID_SAVED", error);
            return "";
        }
    }

    private EdhomeDesktop() {
        super("EDHOME Desktop Beta " + DESKTOP_VERSION);
        DesktopDiagnosticLog.event("WINDOW_CREATED");
        sanitizedPhoneHost();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 680));
        Rectangle usableScreen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int initialWidth = Math.max(1050, Math.min(1360, usableScreen.width - 16));
        int initialHeight = Math.max(680, Math.min(940, usableScreen.height - 16));
        setSize(initialWidth, initialHeight);
        setLocationRelativeTo(null);
        // Desktop ma od razu wykorzystywać cały dostępny pulpit Windows.
        // Zostawiamy ramkę systemową, pasek zadań i standardowe przyciski okna.
        setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        DesktopDiagnosticLog.event("WINDOW_MAXIMIZED_START");

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
        if ("Projekty".equals(name)) return desktopProjects();
        if ("Magazyn".equals(name)) return tablePage("Magazyn", "storage_items",
            cols("Nazwa","name","Typ","kind","Pudełko","parent_box_id",
                 "Miejsce","place_id","Wypożyczone","lent_to"));
        if ("Pomieszczenia".equals(name)) return rooms();
        if ("Mapa".equals(name)) return floorMap();
        if ("Spiżarnia".equals(name)) return tablePage("Spiżarnia", "pantry",
            cols("Produkt","name","Ilość","qty","Kategoria","category"));
        if ("Zakupy".equals(name)) return tablePage("Lista zakupów", "shopping_items",
            cols("Produkt","name","Ilość","qty_milli","Jednostka","unit",
                 "Kupione","checked","Miejsce","place_id"));
        if ("PayCheck".equals(name)) return paycheck();
        if ("Pojazdy".equals(name)) return tablePage("Pojazdy", "vehicles",
            cols("Nazwa","name","Rejestracja","registration","Przebieg","mileage",
                 "OC do","oc_until","Przegląd do","inspection_until"));
        if ("Ogród".equals(name)) return garden();
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

    private JComponent desktopProjects() {
        JPanel page = page("Projekty • szybka edycja");
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBackground(APP_BG);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        toolbar.setBackground(APP_BG);
        JButton addProject = actionButton("＋ Projekt");
        JButton addSubproject = actionButton("＋ Podprojekt");
        JButton editProject = actionButton("Edytuj projekt");
        JButton addTask = actionButton("＋ Czynność");
        JButton pasteTasks = actionButton("Wklej czynności");
        toolbar.add(addProject);
        toolbar.add(addSubproject);
        toolbar.add(editProject);
        toolbar.add(addTask);
        toolbar.add(pasteTasks);
        root.add(toolbar,BorderLayout.NORTH);

        javax.swing.tree.DefaultMutableTreeNode treeRoot =
            new javax.swing.tree.DefaultMutableTreeNode("Projekty");
        java.util.Map<Long,javax.swing.tree.DefaultMutableTreeNode> nodes =
            new java.util.LinkedHashMap<>();
        java.util.Map<Long,JsonObject> rowsById = new java.util.LinkedHashMap<>();
        for (JsonElement element : table("projects")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            long id = longValue(row,"id");
            if (id <= 0) continue;
            rowsById.put(id,row);
            nodes.put(id,new javax.swing.tree.DefaultMutableTreeNode(
                new DesktopProjectRef(row)));
        }
        for (java.util.Map.Entry<Long,javax.swing.tree.DefaultMutableTreeNode> entry
                : nodes.entrySet()) {
            JsonObject row = rowsById.get(entry.getKey());
            long parent = longValue(row,"parent_id");
            javax.swing.tree.DefaultMutableTreeNode parentNode = nodes.get(parent);
            if (parentNode == null || parent == entry.getKey()) treeRoot.add(entry.getValue());
            else parentNode.add(entry.getValue());
        }

        JTree tree = new JTree(treeRoot);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(APP_SURFACE);
        tree.setForeground(APP_TEXT);
        tree.setRowHeight(24);
        tree.getSelectionModel().setSelectionMode(
            javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);

        JPanel detail = new JPanel(new BorderLayout());
        detail.setBackground(APP_BG);

        java.util.function.Consumer<JsonObject> renderDetail = project -> {
            detail.removeAll();
            detail.add(desktopProjectDetail(project), BorderLayout.CENTER);
            detail.revalidate();
            detail.repaint();
        };

        tree.addTreeSelectionListener(e -> {
            Object nodeObject = tree.getLastSelectedPathComponent();
            if (!(nodeObject instanceof javax.swing.tree.DefaultMutableTreeNode)) return;
            Object userObject =
                ((javax.swing.tree.DefaultMutableTreeNode) nodeObject).getUserObject();
            if (!(userObject instanceof DesktopProjectRef)) return;
            JsonObject project = ((DesktopProjectRef) userObject).row;
            desktopProjectId = longValue(project,"id");
            renderDetail.accept(project);
        });

        if (desktopProjectId > 0 && nodes.containsKey(desktopProjectId)) {
            javax.swing.tree.TreePath pathToSelect =
                new javax.swing.tree.TreePath(nodes.get(desktopProjectId).getPath());
            tree.setSelectionPath(pathToSelect);
            tree.scrollPathToVisible(pathToSelect);
        } else if (tree.getRowCount() > 0) {
            tree.setSelectionRow(0);
        } else {
            renderDetail.accept(null);
        }

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createLineBorder(APP_SURFACE_2));
        treeScroll.setPreferredSize(new Dimension(300,1));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,treeScroll,detail);
        split.setResizeWeight(0.28);
        split.setDividerLocation(300);
        split.setBorder(null);
        root.add(split,BorderLayout.CENTER);

        addProject.addActionListener(e -> showDesktopProjectEditor(null,null));
        addSubproject.addActionListener(e -> {
            JsonObject selected = desktopProjectById(desktopProjectId);
            if (selected == null) {
                JOptionPane.showMessageDialog(this,"Najpierw wybierz projekt nadrzędny.");
                return;
            }
            showDesktopProjectEditor(null,desktopProjectId);
        });
        editProject.addActionListener(e -> {
            JsonObject selected = desktopProjectById(desktopProjectId);
            if (selected == null) {
                JOptionPane.showMessageDialog(this,"Najpierw wybierz projekt.");
                return;
            }
            showDesktopProjectEditor(selected,null);
        });
        addTask.addActionListener(e -> addDesktopProjectTask(desktopProjectId));
        pasteTasks.addActionListener(e -> pasteDesktopProjectTasks(desktopProjectId));

        page.add(root,BorderLayout.CENTER);
        return page;
    }

    private JComponent desktopProjectDetail(JsonObject project) {
        JPanel wrapper = new JPanel(new BorderLayout(0,10));
        wrapper.setBackground(APP_BG);
        if (project == null) {
            JLabel empty = new JLabel(
                "<html><b>Brak projektów.</b><br>Dodaj pierwszy projekt z paska u góry.</html>");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(18,18,18,18));
            wrapper.add(empty,BorderLayout.NORTH);
            return wrapper;
        }

        long projectId = longValue(project,"id");
        java.util.List<JsonObject> tasks = desktopProjectTasks(projectId);
        int done = 0;
        int planned = 0;
        int remaining = 0;
        for (JsonObject task : tasks) {
            int duration = Math.max(20,intValue(task,"duration_minutes"));
            planned += duration;
            if (intValue(task,"done") != 0) {
                done++;
            } else {
                int worked = desktopTaskWorkedMinutes(longValue(task,"id"));
                remaining += Math.max(0,duration-worked);
            }
        }
        int progress = tasks.isEmpty() ? 0 :
            (int)Math.round(done * 100.0 / tasks.size());

        JPanel head = new RoundedPanel(APP_SURFACE,18);
        head.setLayout(new BorderLayout(10,4));
        head.setBorder(new EmptyBorder(12,14,12,14));
        JLabel title = new JLabel(value(project,"name"));
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD,20f));
        head.add(title,BorderLayout.NORTH);
        String due = value(project,"due_date");
        String budget = value(project,"budget_grosz");
        JLabel meta = new JLabel("<html>Status: <b>"
            + html(friendlyValue("status",project))
            + "</b> • Postęp: <b>" + progress + "%</b>"
            + " • Czynności: <b>" + done + "/" + tasks.size() + "</b>"
            + " • Pozostało: <b>" + formatMinutes(remaining) + "</b>"
            + (due.isBlank() ? "" : " • Termin: <b>" + html(due) + "</b>")
            + (budget.isBlank() ? "" : " • Budżet: <b>" + html(money(budget)) + "</b>")
            + "</html>");
        meta.setForeground(APP_MUTED);
        head.add(meta,BorderLayout.CENTER);

        java.util.List<JsonObject> selectedTasks = new ArrayList<>();
        java.util.List<JCheckBox> selectors = new ArrayList<>();
        JLabel selection = new JLabel("Zaznaczone: 0");
        selection.setForeground(APP_ACCENT);

        JPanel batchBar = new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        batchBar.setBackground(APP_BG);
        JButton selectAll = actionButton("Zaznacz wszystkie");
        JButton clearSelection = actionButton("Wyczyść");
        JButton batchEdit = actionButton("Edytuj zaznaczone");
        batchBar.add(selection);
        batchBar.add(selectAll);
        batchBar.add(clearSelection);
        batchBar.add(batchEdit);

        JPanel north = new JPanel(new BorderLayout(0,8));
        north.setBackground(APP_BG);
        north.add(head,BorderLayout.NORTH);
        north.add(batchBar,BorderLayout.SOUTH);
        wrapper.add(north,BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setBackground(APP_BG);
        list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));
        if (tasks.isEmpty()) {
            JLabel empty = new JLabel("Brak czynności w tym projekcie.");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(18,8,18,8));
            list.add(empty);
        } else {
            for (JsonObject task : tasks) {
                JPanel row = new JPanel(new BorderLayout(8,0));
                row.setOpaque(false);
                JCheckBox selector = new JCheckBox();
                selector.setOpaque(false);
                selector.setToolTipText("Zaznacz czynność do edycji zbiorczej");
                selector.addActionListener(e -> {
                    if (selector.isSelected()) {
                        if (!selectedTasks.contains(task)) selectedTasks.add(task);
                    } else selectedTasks.remove(task);
                    selection.setText("Zaznaczone: " + selectedTasks.size());
                });
                selectors.add(selector);
                row.add(selector,BorderLayout.WEST);
                row.add(desktopProjectTaskCard(task),BorderLayout.CENTER);
                list.add(row);
                list.add(Box.createVerticalStrut(8));
            }
        }

        selectAll.addActionListener(e -> {
            selectedTasks.clear();
            selectedTasks.addAll(tasks);
            for (JCheckBox selector : selectors) selector.setSelected(true);
            selection.setText("Zaznaczone: " + selectedTasks.size());
        });
        clearSelection.addActionListener(e -> {
            selectedTasks.clear();
            for (JCheckBox selector : selectors) selector.setSelected(false);
            selection.setText("Zaznaczone: 0");
        });
        batchEdit.addActionListener(e ->
            showDesktopProjectBatchEdit(projectId,new ArrayList<>(selectedTasks)));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        wrapper.add(scroll,BorderLayout.CENTER);

        JLabel hint = new JLabel(
            "Desktop: szybka organizacja projektu • telefon: wykonanie, Start/Stop, skan i zdjęcia");
        hint.setForeground(APP_MUTED);
        wrapper.add(hint,BorderLayout.SOUTH);
        return wrapper;
    }

    private void showDesktopProjectBatchEdit(long currentProjectId,
            java.util.List<JsonObject> selected) {
        if (selected == null || selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw zaznacz co najmniej jedną czynność.");
            return;
        }

        JCheckBox changeAssignee = new JCheckBox("Zmień wykonawcę");
        JComboBox<Choice> assignee = referenceCombo("household_members","",true);
        assignee.setEnabled(false);
        changeAssignee.addActionListener(e -> assignee.setEnabled(changeAssignee.isSelected()));

        JCheckBox changeDue = new JCheckBox("Zmień termin");
        JTextField due = new JTextField(12);
        due.setToolTipText("RRRR-MM-DD albo puste = bez terminu");
        due.setEnabled(false);
        changeDue.addActionListener(e -> due.setEnabled(changeDue.isSelected()));

        JCheckBox changePriority = new JCheckBox("Zmień priorytet");
        JComboBox<Choice> priority = new JComboBox<>(new Choice[]{
            new Choice("low","Niski"),
            new Choice("normal","Normalny"),
            new Choice("high","Wysoki"),
            new Choice("urgent","Pilny")
        });
        priority.setEnabled(false);
        changePriority.addActionListener(e ->
            priority.setEnabled(changePriority.isSelected()));

        JCheckBox moveProject = new JCheckBox("Przenieś do projektu / podprojektu");
        JComboBox<Choice> targetProject =
            desktopProjectBatchTargetCombo(currentProjectId);
        targetProject.setEnabled(false);
        moveProject.addActionListener(e ->
            targetProject.setEnabled(moveProject.isSelected()));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8,8,8,8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5,5,5,5);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        int y=0;

        g.gridx=0;g.gridy=y;g.gridwidth=2;
        form.add(new JLabel("Zaznaczone czynności: " + selected.size()),g);
        y++;

        g.gridwidth=1;g.gridx=0;g.gridy=y;form.add(changeAssignee,g);
        g.gridx=1;form.add(assignee,g);y++;
        g.gridx=0;g.gridy=y;form.add(changeDue,g);
        g.gridx=1;form.add(due,g);y++;
        g.gridx=0;g.gridy=y;form.add(changePriority,g);
        g.gridx=1;form.add(priority,g);y++;
        g.gridx=0;g.gridy=y;form.add(moveProject,g);
        g.gridx=1;form.add(targetProject,g);

        int result = JOptionPane.showConfirmDialog(this,form,
            "EDHOME Desktop • edycja zbiorcza czynności",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        if (!changeAssignee.isSelected() && !changeDue.isSelected()
                && !changePriority.isSelected() && !moveProject.isSelected()) {
            JOptionPane.showMessageDialog(this,
                "Nie wybrano żadnej zmiany.");
            return;
        }

        String dueValue = due.getText().trim();
        if (changeDue.isSelected() && !dueValue.isBlank()) {
            try { LocalDate.parse(dueValue); }
            catch (Exception invalid) {
                JOptionPane.showMessageDialog(this,
                    "Nieprawidłowy termin. Użyj formatu RRRR-MM-DD.",
                    "EDHOME Desktop",JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        Choice assigneeValue = (Choice)assignee.getSelectedItem();
        Choice priorityValue = (Choice)priority.getSelectedItem();
        Choice projectValue = (Choice)targetProject.getSelectedItem();
        long targetId = moveProject.isSelected() && projectValue != null
            && !projectValue.value.isBlank()
            ? Long.parseLong(projectValue.value) : currentProjectId;

        if (moveProject.isSelected()
                && desktopProjectRootId(targetId)
                    != desktopProjectRootId(currentProjectId)) {
            JOptionPane.showMessageDialog(this,
                "Czynności można przenosić zbiorczo tylko w obrębie "
                    + "tego samego projektu głównego. Dzięki temu zależności pozostają poprawne.",
                "EDHOME Desktop",JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.Map<JsonObject,JsonObject> before = new java.util.LinkedHashMap<>();
        try {
            long nextOrder = moveProject.isSelected()
                ? desktopNextProjectSortOrder(targetId) : 0L;
            for (JsonObject task : selected) {
                before.put(task,task.deepCopy());
                if (changeAssignee.isSelected()) {
                    String value = assigneeValue == null ? "" : assigneeValue.value;
                    if (value.isBlank())
                        task.add("assignee_id",com.google.gson.JsonNull.INSTANCE);
                    else task.addProperty("assignee_id",Long.parseLong(value));
                }
                if (changeDue.isSelected()) {
                    if (dueValue.isBlank())
                        task.add("due_date",com.google.gson.JsonNull.INSTANCE);
                    else task.addProperty("due_date",dueValue);
                }
                if (changePriority.isSelected() && priorityValue != null)
                    task.addProperty("priority",priorityValue.value);
                if (moveProject.isSelected()) {
                    task.addProperty("project_id",targetId);
                    task.addProperty("project_sort_order",nextOrder);
                    nextOrder += 10L;
                }
            }
            markDirty();
            desktopProjectId = moveProject.isSelected() ? targetId : currentProjectId;
            showSection("Projekty");
            JOptionPane.showMessageDialog(this,
                "Zmieniono czynności: " + selected.size()
                    + ". Zapisano lokalnie; synchronizacja wyśle tylko zmienione rekordy.");
        } catch (Exception error) {
            for (java.util.Map.Entry<JsonObject,JsonObject> entry : before.entrySet())
                restoreJsonObject(entry.getKey(),entry.getValue());
            JOptionPane.showMessageDialog(this,
                "Nie wykonano edycji zbiorczej:\n" + rootMessage(error),
                "EDHOME Desktop",JOptionPane.ERROR_MESSAGE);
        }
    }

    private JComboBox<Choice> desktopProjectBatchTargetCombo(long projectId) {
        java.util.List<Choice> options = new ArrayList<>();
        long rootId = desktopProjectRootId(projectId);
        for (JsonElement element : table("projects")) {
            if (!element.isJsonObject()) continue;
            JsonObject project = element.getAsJsonObject();
            long id = longValue(project,"id");
            if (id <= 0 || desktopProjectRootId(id) != rootId) continue;
            options.add(new Choice(Long.toString(id),desktopProjectPath(project)));
        }
        options.sort((a,b) -> a.label.compareToIgnoreCase(b.label));
        JComboBox<Choice> combo = new JComboBox<>(options.toArray(new Choice[0]));
        for (int i=0;i<options.size();i++)
            if (Long.toString(projectId).equals(options.get(i).value))
                combo.setSelectedIndex(i);
        return combo;
    }

    private JPanel desktopProjectTaskCard(JsonObject task) {
        long taskId = longValue(task,"id");
        int duration = Math.max(20,intValue(task,"duration_minutes"));
        boolean done = intValue(task,"done") != 0;
        JsonObject activeSession = desktopActiveProjectWorkSession(taskId);

        JPanel card = new RoundedPanel(APP_SURFACE,16);
        card.setLayout(new BorderLayout(10,0));
        card.setBorder(new EmptyBorder(9,11,9,11));
        card.setMaximumSize(new Dimension(
            Integer.MAX_VALUE,activeSession == null ? 86 : 108));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
        String titleText = value(task,"title");
        JLabel title = new JLabel((done ? "✓ " : "• ") + titleText);
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD,14f));
        text.add(title);

        int worked = desktopTaskWorkedMinutes(taskId);
        int left = done ? 0 : Math.max(0,duration-worked);
        int deps = desktopDependencyCount(taskId);
        String due = value(task,"due_date");
        JLabel meta = new JLabel("Plan: " + formatMinutes(duration)
            + " • Zrobiono: " + formatMinutes(worked)
            + " • Zostało: " + formatMinutes(left)
            + (deps > 0 ? " • Zależności: " + deps : "")
            + (due.isBlank() ? "" : " • Termin: " + due));
        meta.setForeground(APP_MUTED);
        meta.setFont(meta.getFont().deriveFont(11f));
        text.add(meta);

        if (activeSession != null) {
            JLabel clock = new JLabel();
            clock.setForeground(APP_ACCENT);
            clock.setFont(clock.getFont().deriveFont(Font.BOLD,14f));
            text.add(Box.createVerticalStrut(3));
            text.add(clock);
            bindDesktopProjectClock(clock,taskId,duration);
        }
        card.add(text,BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,8));
        actions.setOpaque(false);

        if (activeSession != null) {
            JButton stop = compactActionButton("■ Stop");
            stop.setToolTipText("Zatrzymaj pomiar czasu");
            stop.addActionListener(e -> stopDesktopProjectWork(task));
            actions.add(stop);
        } else if (!done) {
            JButton startTime = compactActionButton("▶ Start czasu");
            startTime.setToolTipText("Uruchom pomiar czasu tej czynności");
            startTime.addActionListener(e -> startDesktopProjectWork(task));
            actions.add(startTime);
        }

        JButton edit = compactActionButton("Edytuj");
        edit.addActionListener(e -> editDesktopProjectTask(task));
        JButton up = compactActionButton("↑");
        up.setToolTipText("Przesuń czynność wyżej");
        up.addActionListener(e -> moveDesktopProjectTask(task,-1));
        JButton down = compactActionButton("↓");
        down.setToolTipText("Przesuń czynność niżej");
        down.addActionListener(e -> moveDesktopProjectTask(task,1));
        JButton depsButton = compactActionButton("Zależności");
        depsButton.addActionListener(e -> showDesktopTaskDependencies(task));
        JButton delete = compactActionButton("Usuń");
        delete.addActionListener(e -> deleteDesktopProjectTask(task));
        actions.add(up);
        actions.add(down);
        actions.add(edit);
        actions.add(depsButton);
        actions.add(delete);
        card.add(actions,BorderLayout.EAST);
        return card;
    }

    private JsonObject desktopTaskById(long taskId) {
        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject task = element.getAsJsonObject();
            if (longValue(task,"id") == taskId) return task;
        }
        return null;
    }

    private JsonObject desktopActiveProjectWorkSession(long taskId) {
        JsonObject newest = null;
        long newestStarted = Long.MIN_VALUE;
        for (JsonElement element : table("project_task_work_sessions")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (longValue(row,"task_id") != taskId
                    || !value(row,"ended_at").isBlank()) continue;
            long started = longValue(row,"started_at");
            if (started >= newestStarted) {
                newestStarted = started;
                newest = row;
            }
        }
        return newest;
    }

    private long desktopClosedWorkedSeconds(long taskId) {
        long seconds = 0L;
        for (JsonElement element : table("project_task_work_sessions")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (longValue(row,"task_id") != taskId
                    || value(row,"ended_at").isBlank()) continue;
            seconds += Math.max(0L,longValue(row,"worked_minutes")) * 60L;
        }
        return seconds;
    }

    private static String desktopProjectClockText(long seconds) {
        long safe = Math.max(0L,seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long rest = safe % 60L;
        return String.format(Locale.ROOT,"%02d:%02d:%02d",
            hours,minutes,rest);
    }

    private void updateDesktopProjectClock(JLabel clock,long taskId,int plannedMinutes) {
        JsonObject active = desktopActiveProjectWorkSession(taskId);
        if (active == null) {
            clock.setText("Pomiar zatrzymany");
            return;
        }
        long started = longValue(active,"started_at");
        long elapsed = Math.max(0L,
            (System.currentTimeMillis()-started)/1000L);
        long worked = desktopClosedWorkedSeconds(taskId)+elapsed;
        long planned = Math.max(0L,(long)plannedMinutes)*60L;
        long delta = planned-worked;
        if (delta >= 0L)
            clock.setText("● Pozostało " + desktopProjectClockText(delta)
                + "   •   Trwa " + desktopProjectClockText(elapsed));
        else
            clock.setText("● Ponad plan +" + desktopProjectClockText(-delta)
                + "   •   Trwa " + desktopProjectClockText(elapsed));
    }

    private void bindDesktopProjectClock(JLabel clock,long taskId,int plannedMinutes) {
        updateDesktopProjectClock(clock,taskId,plannedMinutes);
        javax.swing.Timer timer = new javax.swing.Timer(1000,null);
        timer.addActionListener(e -> {
            if (!clock.isShowing() || !"Projekty".equals(current)) {
                timer.stop();
                return;
            }
            updateDesktopProjectClock(clock,taskId,plannedMinutes);
        });
        timer.setInitialDelay(1000);
        timer.start();
    }

    private int desktopOpenDependencyCount(long taskId) {
        return desktopOpenDependencyTitles(taskId).size();
    }

    private java.util.List<String> desktopOpenDependencyTitles(long taskId) {
        java.util.List<String> titles=new ArrayList<>();
        for (JsonElement element : table("project_task_dependencies")) {
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            if (longValue(link,"task_id") != taskId) continue;
            JsonObject dependency = desktopTaskById(
                longValue(link,"depends_on_task_id"));
            if (dependency != null && intValue(dependency,"done") == 0)
                titles.add(value(dependency,"title"));
        }
        return titles;
    }

    private int desktopOpenDependentCount(long taskId) {
        int open=0;
        for(JsonElement element:table("project_task_dependencies")) {
            if(!element.isJsonObject())continue;
            JsonObject link=element.getAsJsonObject();
            if(longValue(link,"depends_on_task_id")!=taskId)continue;
            JsonObject waiter=desktopTaskById(longValue(link,"task_id"));
            if(waiter!=null&&intValue(waiter,"done")==0)open++;
        }
        return open;
    }

    private String desktopHardBlockReason(long taskId) {
        java.util.List<String> labels = new ArrayList<>();
        int total = 0;
        for (JsonElement element : table("project_task_blockers")) {
            if (!element.isJsonObject()) continue;
            JsonObject blocker = element.getAsJsonObject();
            if (longValue(blocker,"task_id") != taskId
                    || intValue(blocker,"hard") != 1
                    || intValue(blocker,"resolved") != 0) continue;
            total++;
            if (labels.size() < 3) {
                String label = value(blocker,"label").trim();
                labels.add(label.isBlank() ? "wymaganie" : label);
            }
        }
        if (total == 0) return "";
        return "Czynność jest zablokowana. Czeka na: "
            + String.join(", ",labels)
            + (total > labels.size() ? " (+" + (total-labels.size()) + ")" : "")
            + ".";
    }

    private String desktopProjectWorkBlockReason(JsonObject task) {
        long projectId=longValue(task,"project_id");
        java.util.HashSet<Long> seen=new java.util.HashSet<>();
        while(projectId>0L) {
            if(!seen.add(projectId))
                return "Wykryto pętlę w hierarchii projektów.";
            JsonObject project=desktopProjectById(projectId);
            if(project==null)return "Projekt tej czynności już nie istnieje.";
            String status=value(project,"status");
            if("paused".equals(status))
                return "Projekt „"+value(project,"name")+"” jest wstrzymany.";
            if("done".equals(status))
                return "Projekt „"+value(project,"name")+"” jest zakończony.";
            projectId=longValue(project,"parent_id");
        }
        return "";
    }

    private void startDesktopProjectWork(JsonObject task) {
        long taskId = longValue(task,"id");
        if (taskId <= 0 || desktopTaskById(taskId) == null) {
            JOptionPane.showMessageDialog(this,"Czynność już nie istnieje.");
            return;
        }
        if (intValue(task,"done") != 0) {
            JOptionPane.showMessageDialog(this,
                "Czynność jest już oznaczona jako wykonana.");
            return;
        }
        if (desktopActiveProjectWorkSession(taskId) != null) {
            JOptionPane.showMessageDialog(this,
                "Ta czynność ma już uruchomiony pomiar czasu.");
            showSection("Projekty");
            return;
        }
        String projectBlock=desktopProjectWorkBlockReason(task);
        if(!projectBlock.isBlank()) {
            JOptionPane.showMessageDialog(this,projectBlock);
            return;
        }
        java.util.List<String> dependencyTitles=desktopOpenDependencyTitles(taskId);
        if (!dependencyTitles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw zakończ:\n• "+String.join("\n• ",dependencyTitles));
            return;
        }
        String blocker = desktopHardBlockReason(taskId);
        if (!blocker.isBlank()) {
            JOptionPane.showMessageDialog(this,blocker);
            return;
        }

        JsonObject session = new JsonObject();
        session.addProperty("id",nextId("project_task_work_sessions"));
        session.addProperty("task_id",taskId);
        session.addProperty("started_at",System.currentTimeMillis());
        session.add("ended_at",com.google.gson.JsonNull.INSTANCE);
        session.add("worked_minutes",com.google.gson.JsonNull.INSTANCE);
        table("project_task_work_sessions").add(session);
        markDirty();
        DesktopDiagnosticLog.event("PROJECT_WORK_STARTED_DESKTOP",
            "task="+taskId);
        showSection("Projekty");
    }

    private void stopDesktopProjectWork(JsonObject task) {
        long taskId = longValue(task,"id");
        JsonObject session = desktopActiveProjectWorkSession(taskId);
        if (session == null) {
            JOptionPane.showMessageDialog(this,
                "Ta czynność nie ma aktywnego pomiaru czasu.");
            showSection("Projekty");
            return;
        }
        long ended = System.currentTimeMillis();
        long started = longValue(session,"started_at");
        int minutes = (int)Math.max(1L,
            Math.round(Math.max(0L,ended-started)/60000.0));
        session.addProperty("ended_at",ended);
        session.addProperty("worked_minutes",minutes);
        markDirty();
        DesktopDiagnosticLog.event("PROJECT_WORK_STOPPED_DESKTOP",
            "task="+taskId+" minutes="+minutes);
        showSection("Projekty");
    }

    private void addDesktopProjectTask(long projectId) {
        if (projectId <= 0 || desktopProjectById(projectId) == null) {
            JOptionPane.showMessageDialog(this,"Najpierw wybierz projekt.");
            return;
        }
        JsonObject row = newRowTemplate("tasks");
        if (row == null) return;
        row.addProperty("project_id",projectId);
        row.addProperty("project_sort_order",desktopNextProjectSortOrder(projectId));
        JsonObject before = row.deepCopy();
        if (!editRow(row,cols(
                "Nazwa","title",
                "Czas [min]","duration_minutes",
                "Termin","due_date",
                "Priorytet","priority",
                "Osoba","assignee_id"))) return;
        if (intValue(row,"duration_minutes") < 20) {
            restoreJsonObject(row,before);
            JOptionPane.showMessageDialog(this,
                "Czynność projektu musi mieć co najmniej 20 minut.",
                "EDHOME Desktop",JOptionPane.WARNING_MESSAGE);
            return;
        }
        table("tasks").add(row);
        markDirty();
        desktopProjectId = projectId;
        showSection("Projekty");
    }

    private void editDesktopProjectTask(JsonObject task) {
        if (!editRow(task,cols(
                "Nazwa","title",
                "Czas [min]","duration_minutes",
                "Termin","due_date",
                "Priorytet","priority",
                "Osoba","assignee_id",
                "Wykonane","done"))) return;
        showSection("Projekty");
    }

    private void deleteDesktopProjectTask(JsonObject task) {
        long taskId=longValue(task,"id");
        java.util.List<String> dependents=new ArrayList<>();
        for(JsonElement element:table("project_task_dependencies")) {
            if(!element.isJsonObject())continue;
            JsonObject link=element.getAsJsonObject();
            if(longValue(link,"depends_on_task_id")!=taskId)continue;
            JsonObject waiter=desktopTaskById(longValue(link,"task_id"));
            if(waiter!=null&&intValue(waiter,"done")==0)
                dependents.add(value(waiter,"title"));
        }
        StringBuilder message=new StringBuilder("Usunąć „")
            .append(value(task,"title")).append("”?\n");
        if(!dependents.isEmpty()) {
            message.append("\nTa czynność jest wymagana przez:\n• ")
                .append(String.join("\n• ",dependents))
                .append("\n\nUsunięcie zerwie ")
                .append(dependents.size())
                .append(dependents.size()==1?" zależność.":" zależności.");
        }
        message.append("\n\nUsunięte zostaną też wymagania i zapis czasu.");
        int choice=JOptionPane.showConfirmDialog(this,message.toString(),
            "EDHOME Desktop • Projekty",JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if(choice!=JOptionPane.YES_OPTION)return;
        removeRowsByLong("project_task_work_sessions","task_id",taskId);
        removeRowsByLong("project_task_blockers","task_id",taskId);
        JsonArray links=table("project_task_dependencies");
        for(int i=links.size()-1;i>=0;i--) {
            if(!links.get(i).isJsonObject())continue;
            JsonObject link=links.get(i).getAsJsonObject();
            if(longValue(link,"task_id")==taskId
                    ||longValue(link,"depends_on_task_id")==taskId)
                links.remove(i);
        }
        JsonArray tasks=table("tasks");
        for(int i=tasks.size()-1;i>=0;i--)
            if(tasks.get(i).isJsonObject()
                    &&longValue(tasks.get(i).getAsJsonObject(),"id")==taskId)
                tasks.remove(i);
        markDirty();
        showSection("Projekty");
    }

    private void pasteDesktopProjectTasks(long projectId) {
        if (projectId <= 0 || desktopProjectById(projectId) == null) {
            JOptionPane.showMessageDialog(this,"Najpierw wybierz projekt.");
            return;
        }
        JTextArea area = new JTextArea(14,48);
        area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(area);
        int result = JOptionPane.showConfirmDialog(this,scroll,
            "Wklej czynności • jedna linia = jedna czynność",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        java.util.List<String> names = parseQuickTasks(area.getText(),true);
        if (names.isEmpty()) return;
        int added = 0;
        long sortOrder=desktopNextProjectSortOrder(projectId);
        for (String name : names) {
            JsonObject task = newRowTemplate("tasks");
            task.addProperty("title",name);
            task.addProperty("project_id",projectId);
            task.addProperty("project_sort_order",sortOrder);
            task.addProperty("duration_minutes",30);
            table("tasks").add(task);
            sortOrder+=10L;
            added++;
        }
        if (added > 0) markDirty();
        desktopProjectId = projectId;
        showSection("Projekty");
        JOptionPane.showMessageDialog(this,
            "Dodano czynności: " + added + ". Domyślny czas: 30 min.");
    }

    private void showDesktopProjectEditor(JsonObject existing,Long parentPreset) {
        boolean isNew = existing == null;
        JsonObject row = isNew ? newRowTemplate("projects") : existing;
        if (row == null) return;
        if (isNew && parentPreset != null)
            row.addProperty("parent_id",parentPreset);
        long id = longValue(row,"id");
        if (!editRow(row,cols(
                "Nazwa","name",
                "Projekt nadrzędny","parent_id",
                "Miejsce","place_id",
                "Osoba","assignee_id",
                "Status","status",
                "Termin","due_date",
                "Budżet [zł]","budget_grosz"))) return;
        if (isNew) table("projects").add(row);
        markDirty();
        desktopProjectId = id;
        showSection("Projekty");
    }

    private void validateDesktopProjectRow(JsonObject row) {
        String name = value(row,"name").trim();
        if (name.isBlank() || name.length() > 160)
            throw new IllegalArgumentException("Podaj nazwę projektu 1–160 znaków.");
        String status = value(row,"status");
        if (!"active".equals(status) && !"paused".equals(status) && !"done".equals(status))
            throw new IllegalArgumentException("Nieprawidłowy status projektu.");
        String due = value(row,"due_date");
        if (!due.isBlank()) try { LocalDate.parse(due); }
        catch (Exception invalid) {
            throw new IllegalArgumentException("Nieprawidłowy termin projektu.");
        }
        String budget = value(row,"budget_grosz");
        if (!budget.isBlank() && Long.parseLong(budget) < 0)
            throw new IllegalArgumentException("Budżet nie może być ujemny.");

        long id = longValue(row,"id");
        long parent = longValue(row,"parent_id");
        if (parent <= 0) return;
        if (parent == id)
            throw new IllegalArgumentException("Projekt nie może być swoim projektem nadrzędnym.");
        java.util.Set<Long> seen = new java.util.HashSet<>();
        seen.add(id);
        long cursor = parent;
        for (int depth=0; depth<128 && cursor>0; depth++) {
            if (!seen.add(cursor))
                throw new IllegalArgumentException("Nie można utworzyć pętli w drzewie projektów.");
            JsonObject parentRow = desktopProjectById(cursor);
            if (parentRow == null)
                throw new IllegalArgumentException("Projekt nadrzędny już nie istnieje.");
            cursor = longValue(parentRow,"parent_id");
        }
    }

    private JComboBox<Choice> projectParentCombo(JsonObject row,String selected) {
        java.util.List<Choice> options = new ArrayList<>();
        options.add(new Choice("","— projekt główny —"));
        long ownId = longValue(row,"id");
        for (JsonElement element : table("projects")) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            long id = longValue(candidate,"id");
            if (id <= 0 || id == ownId) continue;
            options.add(new Choice(Long.toString(id),desktopProjectPath(candidate)));
        }
        JComboBox<Choice> combo = new JComboBox<>(options.toArray(new Choice[0]));
        for (int i=0;i<options.size();i++)
            if (options.get(i).value.equals(selected)) combo.setSelectedIndex(i);
        return combo;
    }

    private String desktopProjectPath(JsonObject row) {
        if (row == null) return "";
        java.util.List<String> parts = new ArrayList<>();
        JsonObject currentRow = row;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int depth=0; depth<64 && currentRow!=null; depth++) {
            parts.add(0,value(currentRow,"name"));
            long parent = longValue(currentRow,"parent_id");
            if (parent <= 0 || !seen.add(parent)) break;
            currentRow = desktopProjectById(parent);
        }
        return String.join(" / ",parts);
    }

    private JsonObject desktopProjectById(long id) {
        if (id <= 0) return null;
        for (JsonElement element : table("projects"))
            if (element.isJsonObject()
                    && longValue(element.getAsJsonObject(),"id") == id)
                return element.getAsJsonObject();
        return null;
    }

    private int desktopProjectTaskRank(JsonObject task) {
        if(intValue(task,"done")!=0)return 4;
        long id=longValue(task,"id");
        if(desktopActiveProjectWorkSession(id)!=null)return 0;
        boolean ready=desktopOpenDependencyCount(id)==0
            &&desktopHardBlockReason(id).isBlank();
        if(ready&&desktopOpenDependentCount(id)>0)return 1;
        if(ready)return 2;
        return 3;
    }

    private java.util.List<JsonObject> desktopProjectTasks(long projectId) {
        java.util.List<JsonObject> out = new ArrayList<>();
        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject task = element.getAsJsonObject();
            if (longValue(task,"project_id") == projectId) out.add(task);
        }
        out.sort((a,b) -> {
            int rank=Integer.compare(desktopProjectTaskRank(a),
                desktopProjectTaskRank(b));
            if(rank!=0)return rank;
            String ad=value(a,"due_date"),bd=value(b,"due_date");
            if(ad.isBlank()!=bd.isBlank())return ad.isBlank()?1:-1;
            int due=ad.compareTo(bd);
            if(due!=0)return due;
            int order=Long.compare(longValue(a,"project_sort_order"),
                longValue(b,"project_sort_order"));
            if(order!=0)return order;
            return Long.compare(longValue(a,"id"),longValue(b,"id"));
        });
        return out;
    }

    private long desktopNextProjectSortOrder(long projectId) {
        long max=0L;
        for(JsonObject task:desktopProjectTasks(projectId))
            max=Math.max(max,longValue(task,"project_sort_order"));
        return max>=Long.MAX_VALUE-10L?Long.MAX_VALUE:max+10L;
    }

    private void moveDesktopProjectTask(JsonObject task,int delta) {
        long projectId=longValue(task,"project_id");
        if(projectId<=0 || delta==0)return;
        java.util.List<JsonObject> tasks=desktopProjectTasks(projectId);
        int index=-1;
        long taskId=longValue(task,"id");
        for(int i=0;i<tasks.size();i++)
            if(longValue(tasks.get(i),"id")==taskId){index=i;break;}
        int target=index+delta;
        if(index<0 || target<0 || target>=tasks.size())return;
        java.util.Collections.swap(tasks,index,target);
        long order=10L;
        for(JsonObject row:tasks) {
            row.addProperty("project_sort_order",order);
            order+=10L;
        }
        markDirty();
        desktopProjectId=projectId;
        showSection("Projekty");
    }

    private int desktopTaskWorkedMinutes(long taskId) {
        int total = 0;
        long now = System.currentTimeMillis();
        for (JsonElement element : table("project_task_work_sessions")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (longValue(row,"task_id") != taskId) continue;
            String ended = value(row,"ended_at");
            if (ended.isBlank()) {
                long start = longValue(row,"started_at");
                if (start > 0) total += (int)Math.max(0,(now-start)/60000L);
            } else total += Math.max(0,intValue(row,"worked_minutes"));
        }
        return total;
    }

    private int desktopDependencyCount(long taskId) {
        int count = 0;
        for (JsonElement element : table("project_task_dependencies"))
            if (element.isJsonObject()
                    && longValue(element.getAsJsonObject(),"task_id") == taskId)
                count++;
        return count;
    }

    private void showDesktopTaskDependencies(JsonObject task) {
        long taskId = longValue(task,"id");
        long projectId = longValue(task,"project_id");
        if (taskId <= 0 || projectId <= 0) {
            JOptionPane.showMessageDialog(this,
                "Zależności można ustawiać tylko dla czynności projektu.");
            return;
        }

        long rootProjectId = desktopProjectRootId(projectId);
        if (rootProjectId <= 0) {
            JOptionPane.showMessageDialog(this,
                "Nie można ustalić projektu głównego tej czynności.");
            return;
        }

        java.util.Set<Long> current = desktopDependencyIds(taskId);
        java.util.LinkedHashMap<Long,JCheckBox> choices = new java.util.LinkedHashMap<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(8,8,8,8));

        for (JsonElement element : table("tasks")) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            long candidateId = longValue(candidate,"id");
            long candidateProject = longValue(candidate,"project_id");
            if (candidateId <= 0 || candidateId == taskId || candidateProject <= 0)
                continue;
            if (desktopProjectRootId(candidateProject) != rootProjectId) continue;

            JsonObject project = desktopProjectById(candidateProject);
            String projectPath = project == null ? "Projekt" : desktopProjectPath(project);
            JCheckBox box = new JCheckBox(projectPath + "  •  "
                + value(candidate,"title"));
            box.setSelected(current.contains(candidateId));
            box.setToolTipText(intValue(candidate,"done") != 0
                ? "Czynność wykonana" : "Czynność do wykonania");
            choices.put(candidateId,box);
            list.add(box);
        }

        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "W tym projekcie głównym nie ma innych czynności, "
                    + "które można ustawić jako poprzednik.");
            return;
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(560,Math.min(430,
            Math.max(180,choices.size()*32+24))));
        int result = JOptionPane.showConfirmDialog(this,scroll,
            "„" + value(task,"title") + "” • zależy od",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        java.util.LinkedHashSet<Long> desired = new java.util.LinkedHashSet<>();
        for (java.util.Map.Entry<Long,JCheckBox> entry : choices.entrySet())
            if (entry.getValue().isSelected()) desired.add(entry.getKey());

        try {
            for (Long dependency : desired) {
                if (desktopDependencyReaches(
                        dependency,taskId,taskId,desired,new java.util.HashSet<>()))
                    throw new IllegalArgumentException(
                        "Ta zmiana utworzyłaby pętlę zależności.");
            }

            JsonArray links = table("project_task_dependencies");
            java.util.Set<Long> existing = new java.util.HashSet<>();
            for (int i=links.size()-1; i>=0; i--) {
                JsonElement element = links.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject link = element.getAsJsonObject();
                if (longValue(link,"task_id") != taskId) continue;
                long dependency = longValue(link,"depends_on_task_id");
                if (!desired.contains(dependency)) links.remove(i);
                else existing.add(dependency);
            }

            long now = System.currentTimeMillis();
            for (Long dependency : desired) {
                if (existing.contains(dependency)) continue;
                JsonObject link = new JsonObject();
                link.addProperty("task_id",taskId);
                link.addProperty("depends_on_task_id",dependency);
                link.addProperty("created_at",now++);
                links.add(link);
            }
            markDirty();
            showSection("Projekty");
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie zapisano zależności:\n" + rootMessage(error),
                "EDHOME Desktop",JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.util.Set<Long> desktopDependencyIds(long taskId) {
        java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
        for (JsonElement element : table("project_task_dependencies")) {
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            if (longValue(link,"task_id") == taskId)
                out.add(longValue(link,"depends_on_task_id"));
        }
        return out;
    }

    private boolean desktopDependencyReaches(long start,long target,
            long editedTask,java.util.Set<Long> editedDependencies,
            java.util.Set<Long> seen) {
        if (start == target) return true;
        if (!seen.add(start)) return false;
        java.util.Set<Long> next = start == editedTask
            ? editedDependencies : desktopDependencyIds(start);
        for (Long dependency : next)
            if (desktopDependencyReaches(
                    dependency,target,editedTask,editedDependencies,seen))
                return true;
        return false;
    }

    private long desktopProjectRootId(long projectId) {
        long current = projectId;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int depth=0; depth<128 && current>0; depth++) {
            if (!seen.add(current)) return 0L;
            JsonObject project = desktopProjectById(current);
            if (project == null) return 0L;
            long parent = longValue(project,"parent_id");
            if (parent <= 0) return current;
            current = parent;
        }
        return 0L;
    }

    private static final class DesktopProjectRef {
        final JsonObject row;
        DesktopProjectRef(JsonObject row) { this.row=row; }
        @Override public String toString() {
            String name=value(row,"name");
            String status=value(row,"status");
            if ("done".equals(status)) return "✓ " + name;
            if ("paused".equals(status)) return "Ⅱ " + name;
            return name;
        }
    }

    private JComponent garden() {
        JPanel page=page("Ogród • uprawy");
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setBackground(APP_BG);

        JPanel metrics=new JPanel(new GridLayout(1,4,10,10));
        metrics.setBackground(APP_BG);
        metrics.add(metric("Obszary",count("garden_areas")));
        metrics.add(metric("Nasadzenia",count("garden_plantings")));
        metrics.add(metric("Zbiory",count("garden_harvests")));
        metrics.add(metric("Katalog",count("garden_catalog")));
        root.add(metrics,BorderLayout.NORTH);

        JPanel list=new JPanel();
        list.setBackground(APP_BG);
        list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));
        JsonArray plantings=table("garden_plantings");
        if(plantings.size()==0) {
            JLabel empty=new JLabel("Brak nasadzeń. Dodaj je w EDHOME na telefonie.");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(18,8,18,8));
            list.add(empty);
        } else {
            for(JsonElement element:plantings) {
                if(!element.isJsonObject()) continue;
                JsonObject p=element.getAsJsonObject();
                long id=longValue(p,"id");
                String plant=gardenPlantName(p);
                String area=gardenAreaName(longValue(p,"area_id"));
                String variety=gardenVariety(p);
                String status=value(p,"status");
                String season=value(p,"season_year");
                String planned=gardenDateLine(p,"planned_sow","planned_plant","planned_harvest");
                String actual=gardenDateLine(p,"actual_sow","actual_plant","actual_harvest");
                String harvest=gardenHarvestSummary(id);

                JPanel card=new RoundedPanel(APP_SURFACE,18);
                card.setLayout(new BorderLayout(10,4));
                card.setBorder(new EmptyBorder(10,12,10,12));
                JPanel text=new JPanel();
                text.setOpaque(false);
                text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));
                JLabel title=new JLabel(plant+(variety.isBlank()?"":" • "+variety));
                title.setForeground(APP_TEXT);
                title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
                text.add(title);
                JLabel meta=new JLabel(area+" • sezon "+(season.isBlank()?"—":season)
                    +" • "+(status.isBlank()?"—":status));
                meta.setForeground(APP_MUTED);
                text.add(meta);
                if(!planned.isBlank()) {
                    JLabel line=new JLabel("Plan: "+planned);
                    line.setForeground(APP_TEXT); text.add(line);
                }
                if(!actual.isBlank()) {
                    JLabel line=new JLabel("Wykonane: "+actual);
                    line.setForeground(APP_TEXT); text.add(line);
                }
                if(!harvest.isBlank()) {
                    JLabel line=new JLabel("Zebrano: "+harvest);
                    line.setForeground(APP_ACCENT); text.add(line);
                }
                card.add(text,BorderLayout.CENTER);
                list.add(card);
                list.add(Box.createVerticalStrut(8));
            }
        }
        JScrollPane scroll=new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        root.add(scroll,BorderLayout.CENTER);

        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        footer.setBackground(APP_BG);
        JButton reload=actionButton("↻ Pobierz z telefonu");
        reload.addActionListener(e->reloadFromPhone(reload));
        footer.add(reload);
        root.add(footer,BorderLayout.SOUTH);
        page.add(root,BorderLayout.CENTER);
        return page;
    }

    private String gardenPlantName(JsonObject planting) {
        long catalogId=longValue(planting,"catalog_id");
        long customId=longValue(planting,"custom_plant_id");
        JsonObject row=catalogId>0?scannerRowById("garden_catalog",catalogId):
            scannerRowById("garden_custom_plants",customId);
        return row==null?"Roślina":value(row,"name");
    }

    private String gardenVariety(JsonObject planting) {
        long catalogId=longValue(planting,"catalog_id");
        long customId=longValue(planting,"custom_plant_id");
        JsonObject row=catalogId>0?scannerRowById("garden_catalog",catalogId):
            scannerRowById("garden_custom_plants",customId);
        return row==null?"":value(row,"variety");
    }

    private String gardenAreaName(long areaId) {
        JsonObject area=scannerRowById("garden_areas",areaId);
        return area==null?"Nieznany obszar":value(area,"name");
    }

    private static String gardenDateLine(JsonObject row,String sow,String plant,String harvest) {
        StringBuilder out=new StringBuilder();
        String a=value(row,sow), b=value(row,plant), c=value(row,harvest);
        if(!a.isBlank()) out.append("siew ").append(a);
        if(!b.isBlank()) {
            if(out.length()>0) out.append(" • ");
            out.append("sadzenie ").append(b);
        }
        if(!c.isBlank()) {
            if(out.length()>0) out.append(" • ");
            out.append("zbiór ").append(c);
        }
        return out.toString();
    }

    private String gardenHarvestSummary(long plantingId) {
        Map<String,Long> sums=new LinkedHashMap<>();
        for(JsonElement element:table("garden_harvests")) {
            if(!element.isJsonObject()) continue;
            JsonObject row=element.getAsJsonObject();
            if(longValue(row,"planting_id")!=plantingId) continue;
            String unit=value(row,"unit");
            long milli=longValue(row,"quantity_milli");
            sums.put(unit,sums.getOrDefault(unit,0L)+milli);
        }
        StringBuilder out=new StringBuilder();
        for(Map.Entry<String,Long> entry:sums.entrySet()) {
            if(out.length()>0) out.append(" • ");
            java.math.BigDecimal value=new java.math.BigDecimal(entry.getValue())
                .divide(new java.math.BigDecimal("1000")).stripTrailingZeros();
            out.append(value.toPlainString().replace('.',','))
                .append(' ').append(entry.getKey());
        }
        return out.toString();
    }

    private JComponent floorMap() {
        return new DesktopFloorMapPanel(
            table("places"),
            table("storage_items"),
            placeId -> {
                JsonObject row = scannerRowById("places", placeId);
                if (row == null) return;
                editRow(row, cols(
                    "Nazwa","name",
                    "Typ","kind",
                    "Nadrzędne","parent_id"));
                showSection("Mapa");
            });
    }

    private JComponent rooms() {
        JsonObject settings = null;
        if (snapshot != null && snapshot.has("settings")
                && snapshot.get("settings").isJsonObject())
            settings = snapshot.getAsJsonObject("settings");

        return new DesktopRoomsPanel(
            table("places"),
            table("storage_items"),
            settings,
            this::editRoomNode);
    }

    private void editRoomNode(DesktopRoomsPanel.NodeRef ref) {
        if (ref == null) return;
        if ("place".equals(ref.type)) {
            JsonObject row = scannerRowById("places", ref.id);
            if (row == null) return;
            editRow(row, cols(
                "Nazwa","name",
                "Typ","kind",
                "Nadrzędne","parent_id"));
            showSection("Pomieszczenia");
            return;
        }
        if ("storage".equals(ref.type)) {
            JsonObject row = scannerRowById("storage_items", ref.id);
            if (row == null) return;
            editRow(row, cols(
                "Nazwa","name",
                "Typ","kind",
                "Pudełko","parent_box_id",
                "Miejsce","place_id",
                "Wypożyczone","lent_to"));
            showSection("Pomieszczenia");
        }
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
            cols("Zadanie","title","Termin","due_date","Priorytet","priority","Osoba","assignee_id"),
            "tasks");
    }

    private JComponent paycheck() {
        JPanel page = page("PayCheck • wspólny budżet");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(APP_BG);

        long[] stats = paycheckDashboardStats();
        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 10));
        metrics.setBackground(APP_BG);
        metrics.add(paycheckMetricCard("Saldo potwierdzone",
            money(Long.toString(stats[0])), "Tylko zaksięgowane pozycje"));
        metrics.add(paycheckMetricCard("Wpływy • ten miesiąc",
            money(Long.toString(stats[1])), "Potwierdzone wpływy"));
        metrics.add(paycheckMetricCard("Wydatki • ten miesiąc",
            money(Long.toString(stats[2])), "Potwierdzone wydatki"));
        metrics.add(paycheckMetricCard("Do potwierdzenia",
            Long.toString(stats[3]), "Nie zmieniają jeszcze salda"));

        JPanel actions = new RoundedPanel(APP_SURFACE, 22);
        actions.setLayout(new BorderLayout(10, 10));
        actions.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel buttons = new JPanel(new GridLayout(2, 5, 8, 8));
        buttons.setOpaque(false);
        JButton importBank = actionButton("＋ Import banku");
        JButton queue = actionButton("Banki i potwierdzenia");
        JButton futureBudget = actionButton("Budżet miesiąca");
        JButton analysis = actionButton("Analiza 12 mies.");
        JButton goals = actionButton("Cele");
        JButton bulk = actionButton("Masowa edycja");
        JButton history = actionButton("Historia importów");
        JButton deleteTx = actionButton("Usuń wpisy");
        JButton privatePay = actionButton("Prywatny PayCheck");
        JButton refresh = actionButton("↻ Odśwież");

        buttons.add(importBank);
        buttons.add(queue);
        buttons.add(futureBudget);
        buttons.add(analysis);
        buttons.add(goals);
        buttons.add(bulk);
        buttons.add(history);
        buttons.add(deleteTx);
        buttons.add(privatePay);
        buttons.add(refresh);

        int open = 0;
        for (JsonElement element : table("bank_evidence_queue")) {
            if (element.isJsonObject()
                    && "open".equals(value(element.getAsJsonObject(), "state"))) open++;
        }
        JLabel bankState = new JLabel(open == 0
            ? "Bank • brak pozycji wymagających sprawdzenia"
            : "Bank • do sprawdzenia: " + open);
        bankState.setForeground(open == 0 ? APP_MUTED : APP_ACCENT);
        actions.add(buttons, BorderLayout.CENTER);
        actions.add(bankState, BorderLayout.SOUTH);

        importBank.addActionListener(e -> importBankStatement());
        queue.addActionListener(e -> showBankEvidenceQueue());
        futureBudget.addActionListener(e ->
            DesktopBudgetPlanner.show(this, table("paycheck_transactions")));
        analysis.addActionListener(e -> showPaycheckAnalysis());
        goals.addActionListener(e -> showPaycheckGoals());
        bulk.addActionListener(e -> showPaycheckBulkEdit());
        history.addActionListener(e -> showBankImportHistory());
        deleteTx.addActionListener(e -> showPaycheckDelete());
        privatePay.addActionListener(e -> showPrivatePaycheck());
        refresh.addActionListener(e -> showSection("PayCheck"));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setBackground(APP_BG);
        top.add(metrics, BorderLayout.NORTH);
        top.add(actions, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(APP_BG);
        tabs.setForeground(APP_TEXT);
        tabs.addTab("Transakcje", paycheckTransactionsView());
        tabs.addTab("Sklepy", paycheckMerchantStatsPanel());
        tabs.addTab("Kategorie", paycheckCategoryStatsPanel());
        body.add(tabs, BorderLayout.CENTER);

        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private long[] paycheckDashboardStats() {
        long balance = 0L;
        long monthIncome = 0L;
        long monthExpense = 0L;
        long pending = 0L;
        java.time.YearMonth now = java.time.YearMonth.now();

        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))) continue;
            if ("pending".equals(value(tx, "status"))) {
                pending++;
                continue;
            }
            if (!"confirmed".equals(value(tx, "status"))) continue;

            long amount;
            try { amount = Long.parseLong(value(tx, "amount_grosz")); }
            catch (Exception invalid) { continue; }

            boolean income = "income".equals(value(tx, "kind"));
            balance += income ? amount : -amount;
            LocalDate date = paycheckTransactionDate(tx);
            if (date != null && now.equals(java.time.YearMonth.from(date))) {
                if (income) monthIncome += amount;
                else monthExpense += amount;
            }
        }
        return new long[]{balance, monthIncome, monthExpense, pending};
    }

    private JPanel paycheckMetricCard(String title, String value, String subtitle) {
        JPanel card = new RoundedPanel(APP_SURFACE, 20);
        card.setLayout(new BorderLayout(0, 5));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel name = new JLabel(title);
        name.setForeground(APP_MUTED);
        name.setFont(name.getFont().deriveFont(12f));
        JLabel number = new JLabel(value);
        number.setForeground(APP_TEXT);
        number.setFont(number.getFont().deriveFont(Font.BOLD, 22f));
        JLabel hint = new JLabel(subtitle);
        hint.setForeground(APP_MUTED);
        hint.setFont(hint.getFont().deriveFont(10f));

        card.add(name, BorderLayout.NORTH);
        card.add(number, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    private JComponent paycheckTransactionsView() {
        JPanel section = new RoundedPanel(APP_SURFACE, 22);
        section.setLayout(new BorderLayout(0, 10));
        section.setBorder(new EmptyBorder(12, 14, 12, 14));

        java.util.List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if ("shared".equals(value(tx, "scope"))) rows.add(tx);
        }
        rows.sort((a,b) -> Long.compare(paycheckSortTime(b), paycheckSortTime(a)));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Ostatnie transakcje");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel count = new JLabel(rows.size() + " wpisów");
        count.setForeground(APP_MUTED);
        heading.add(title, BorderLayout.WEST);
        heading.add(count, BorderLayout.EAST);
        section.add(heading, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setBackground(APP_SURFACE);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        int shown = 0;
        for (JsonObject tx : rows) {
            list.add(paycheckTransactionRow(tx));
            list.add(Box.createVerticalStrut(6));
            if (++shown >= 100) break;
        }
        if (shown == 0) {
            JLabel empty = new JLabel(
                "Brak transakcji. Zaimportuj bank albo dodaj wpis na telefonie.");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(20, 8, 20, 8));
            list.add(empty);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JComponent paycheckTransactionRow(JsonObject tx) {
        JPanel row = new RoundedPanel(APP_SURFACE_2, 16);
        row.setLayout(new BorderLayout(12, 0));
        row.setBorder(new EmptyBorder(9, 12, 9, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));

        boolean income = "income".equals(value(tx, "kind"));
        JLabel amount = new JLabel((income ? "+ " : "− ")
            + money(value(tx, "amount_grosz")));
        amount.setForeground(income ? APP_ACCENT : APP_TEXT);
        amount.setFont(amount.getFont().deriveFont(Font.BOLD, 17f));
        amount.setPreferredSize(new Dimension(145, 44));
        row.add(amount, BorderLayout.WEST);

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        String note = value(tx, "note");
        JLabel main = new JLabel(note.isBlank()
            ? friendlyValueStaticCategory(value(tx, "category")) : note);
        main.setForeground(APP_TEXT);
        main.setFont(main.getFont().deriveFont(Font.BOLD, 13f));

        String meta = friendlyValueStaticCategory(value(tx, "category"))
            + "   •   " + paycheckDateLabel(tx)
            + "   •   " + paycheckStatusLabel(tx);
        JLabel secondary = new JLabel(meta);
        secondary.setForeground("pending".equals(value(tx, "status"))
            ? APP_ACCENT : APP_MUTED);
        secondary.setFont(secondary.getFont().deriveFont(11f));

        details.add(main);
        details.add(Box.createVerticalStrut(5));
        details.add(secondary);
        row.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        JButton category = paycheckSmallButton("Kategoria");
        JButton delete = paycheckSmallButton("Usuń");
        category.addActionListener(e -> editPaycheckCategory(tx));
        delete.addActionListener(e -> deletePaycheckTransaction(tx));
        actions.add(category);
        if ("pending".equals(value(tx, "status"))) {
            JButton bank = paycheckSmallButton("Bank");
            bank.addActionListener(e -> showBankEvidenceQueue());
            actions.add(bank);
        }
        actions.add(delete);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private JButton paycheckSmallButton(String label) {
        JButton button = actionButton(label);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(APP_SURFACE_2),
            new EmptyBorder(5, 8, 5, 8)));
        return button;
    }

    private void editPaycheckCategory(JsonObject tx) {
        Choice[] choices = privateCategoryChoices();
        Choice selected = choices[0];
        String currentCategory = value(tx, "category");
        for (Choice choice : choices)
            if (choice.value.equals(currentCategory)) selected = choice;

        Choice category = (Choice) JOptionPane.showInputDialog(this,
            "Kategoria tej transakcji:",
            "PayCheck • kategoria",
            JOptionPane.QUESTION_MESSAGE, null, choices, selected);
        if (category == null || category.value.equals(currentCategory)) return;

        tx.addProperty("category", category.value);
        DesktopMerchantRules.Match merchant =
            DesktopMerchantRules.detect(value(tx, "note"));
        if (merchant != null)
            DesktopMerchantRules.remember(merchant.label, category.value);
        markDirty();
        showSection("PayCheck");
    }

    private JComponent paycheckMerchantStatsPanel() {
        JPanel panel = new RoundedPanel(APP_SURFACE, 22);
        panel.setLayout(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel title = new JLabel(
            "Sklepy / odbiorcy • rozpoznane z opisów i Twoich reguł");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        java.time.YearMonth now = java.time.YearMonth.now();
        java.time.YearMonth first = now.minusMonths(11);
        java.util.Map<String,long[]> stats = new java.util.HashMap<>();
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))
                    || !"confirmed".equals(value(tx, "status"))
                    || !"expense".equals(value(tx, "kind"))) continue;
            LocalDate date = paycheckTransactionDate(tx);
            if (date == null) continue;
            java.time.YearMonth month = java.time.YearMonth.from(date);
            if (month.isBefore(first) || month.isAfter(now)) continue;

            DesktopMerchantRules.Match merchant =
                DesktopMerchantRules.detect(value(tx, "note"));
            if (merchant == null) continue;
            long amount;
            try { amount = Long.parseLong(value(tx, "amount_grosz")); }
            catch (Exception invalid) { continue; }

            long[] row = stats.computeIfAbsent(merchant.label, ignored -> new long[3]);
            row[0] += amount;
            row[1]++;
            if (now.equals(month)) row[2] += amount;
        }

        java.util.List<java.util.Map.Entry<String,long[]>> rows =
            new ArrayList<>(stats.entrySet());
        rows.sort((a,b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        panel.add(paycheckStatsRows(rows, true), BorderLayout.CENTER);
        return panel;
    }

    private JComponent paycheckCategoryStatsPanel() {
        JPanel panel = new RoundedPanel(APP_SURFACE, 22);
        panel.setLayout(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel title = new JLabel("Kategorie wydatków • ostatnie 12 miesięcy");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        java.time.YearMonth now = java.time.YearMonth.now();
        java.time.YearMonth first = now.minusMonths(11);
        java.util.Map<String,long[]> stats = new java.util.HashMap<>();
        for (JsonElement element : table("paycheck_transactions")) {
            if (!element.isJsonObject()) continue;
            JsonObject tx = element.getAsJsonObject();
            if (!"shared".equals(value(tx, "scope"))
                    || !"confirmed".equals(value(tx, "status"))
                    || !"expense".equals(value(tx, "kind"))) continue;
            LocalDate date = paycheckTransactionDate(tx);
            if (date == null) continue;
            java.time.YearMonth month = java.time.YearMonth.from(date);
            if (month.isBefore(first) || month.isAfter(now)) continue;

            long amount;
            try { amount = Long.parseLong(value(tx, "amount_grosz")); }
            catch (Exception invalid) { continue; }
            String category = friendlyValueStaticCategory(value(tx, "category"));
            long[] row = stats.computeIfAbsent(category, ignored -> new long[3]);
            row[0] += amount;
            row[1]++;
            if (now.equals(month)) row[2] += amount;
        }

        java.util.List<java.util.Map.Entry<String,long[]>> rows =
            new ArrayList<>(stats.entrySet());
        rows.sort((a,b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        panel.add(paycheckStatsRows(rows, false), BorderLayout.CENTER);
        return panel;
    }

    private JComponent paycheckStatsRows(
            java.util.List<java.util.Map.Entry<String,long[]>> rows,
            boolean merchants) {
        JPanel list = new JPanel();
        list.setBackground(APP_SURFACE);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new GridLayout(1, 4, 8, 0));
        header.setBackground(APP_SURFACE_2);
        header.setBorder(new EmptyBorder(7, 10, 7, 10));
        header.add(paycheckStatsLabel(merchants ? "Sklep / odbiorca" : "Kategoria", true));
        header.add(paycheckStatsLabel("Ten miesiąc", true));
        header.add(paycheckStatsLabel("12 miesięcy", true));
        header.add(paycheckStatsLabel("Transakcje / średnia", true));
        list.add(header);
        list.add(Box.createVerticalStrut(5));

        if (rows.isEmpty()) {
            JLabel empty = new JLabel(merchants
                ? "Brak rozpoznanych sklepów. EDHOME zacznie je kojarzyć po klasyfikacji importów."
                : "Brak potwierdzonych wydatków do statystyk.");
            empty.setForeground(APP_MUTED);
            empty.setBorder(new EmptyBorder(18, 10, 18, 10));
            list.add(empty);
        } else {
            for (java.util.Map.Entry<String,long[]> entry : rows) {
                long total = entry.getValue()[0];
                long count = entry.getValue()[1];
                long current = entry.getValue()[2];
                JPanel row = new JPanel(new GridLayout(1, 4, 8, 0));
                row.setBackground(APP_SURFACE);
                row.setBorder(new EmptyBorder(7, 10, 7, 10));
                row.add(paycheckStatsLabel(entry.getKey(), false));
                row.add(paycheckStatsLabel(money(Long.toString(current)), false));
                row.add(paycheckStatsLabel(money(Long.toString(total)), false));
                row.add(paycheckStatsLabel(count + " • śr. "
                    + money(Long.toString(count == 0 ? 0 : total / count)), false));
                list.add(row);
                list.add(new JSeparator());
            }
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(APP_SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JLabel paycheckStatsLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setForeground(bold ? APP_TEXT : APP_MUTED);
        label.setFont(label.getFont().deriveFont(
            bold ? Font.BOLD : Font.PLAIN, 12f));
        return label;
    }

    private LocalDate paycheckTransactionDate(JsonObject tx) {
        String statement = value(tx, "statement_date");
        if (!statement.isBlank()) {
            try { return LocalDate.parse(statement); }
            catch (Exception ignored) { }
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value(tx, "created_at")))
                .atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception ignored) { return null; }
    }

    private long paycheckSortTime(JsonObject tx) {
        try { return Long.parseLong(value(tx, "created_at")); }
        catch (Exception ignored) { }
        LocalDate date = paycheckTransactionDate(tx);
        if (date == null) return 0L;
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String paycheckDateLabel(JsonObject tx) {
        LocalDate date = paycheckTransactionDate(tx);
        return date == null ? "bez daty"
            : date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private String paycheckStatusLabel(JsonObject tx) {
        if ("pending".equals(value(tx, "status"))) return "Do potwierdzenia";
        if (!value(tx, "statement_key").isBlank()) return "Potwierdzone • bank";
        if ("manual".equals(value(tx, "confirmation_source")))
            return "Potwierdzone ręcznie";
        return "Potwierdzone";
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
        if ("food".equals(raw)) return "Żywność";
        if ("subscriptions".equals(raw)) return "Subskrypcje";
        if ("utilities".equals(raw)) return "Media • prąd / woda / gaz";
        if ("bills".equals(raw)) return "Rachunki";
        if ("home".equals(raw)) return "Dom";
        if ("household".equals(raw)) return "Domowe";
        if ("vehicle".equals(raw)) return "Pojazdy";
        if ("fuel".equals(raw)) return "Paliwo";
        if ("transport".equals(raw)) return "Transport";
        if ("health".equals(raw)) return "Zdrowie";
        if ("beauty".equals(raw)) return "Higiena";
        if ("clothing".equals(raw)) return "Odzież";
        if ("restaurants".equals(raw)) return "Restauracje / jedzenie na mieście";
        if ("entertainment".equals(raw)) return "Rozrywka";
        if ("education".equals(raw)) return "Edukacja";
        if ("children".equals(raw)) return "Dzieci";
        if ("pet".equals(raw)) return "Zwierzęta";
        if ("insurance".equals(raw)) return "Ubezpieczenia";
        if ("loans".equals(raw)) return "Kredyty i raty";
        if ("salary".equals(raw)) return "Wynagrodzenie";
        if ("benefits".equals(raw)) return "Świadczenia";
        if ("savings".equals(raw)) return "Oszczędności / inwestycje";
        if ("transfers".equals(raw)) return "Przelewy / transfery";
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

            Object[] actions = {"Nowy cel", "Odłóż na cel", "Usuń cel", "Zamknij"};
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
            } else if (action == 2) {
                Choice selected = list.getSelectedValue();
                if (selected == null) {
                    JOptionPane.showMessageDialog(this, "Najpierw wybierz cel.");
                    continue;
                }
                long goalId=Long.parseLong(selected.value);
                JsonObject goal=scannerRowById("paycheck_goals",goalId);
                if(goal==null)continue;
                int yes=JOptionPane.showConfirmDialog(this,
                    "Usunąć cel „"+value(goal,"name")+"”?\n"
                        +"Przypisane kwoty celu zostaną usunięte. "
                        +"Nie usuwa to transakcji PayCheck.",
                    "Usuń cel",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
                if(yes==JOptionPane.YES_OPTION){
                    removeRowsByLong("paycheck_goal_allocations","goal_id",goalId);
                    setNullWhere("vehicle_policies","goal_id",goalId);
                    table("paycheck_goals").remove(goal);
                    markDirty();
                    showSection("PayCheck");
                }
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

        Choice[] choices = privateCategoryChoices();
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

    private void showPaycheckDelete() {
        java.util.List<JsonObject> rows=new ArrayList<>();
        DefaultListModel<Choice> model=new DefaultListModel<>();
        for(JsonElement element:table("paycheck_transactions")){
            if(!element.isJsonObject())continue;
            JsonObject tx=element.getAsJsonObject();
            if(!"shared".equals(value(tx,"scope")))continue;
            rows.add(tx);
            String note=value(tx,"note");
            model.addElement(new Choice(Integer.toString(rows.size()-1),
                ("income".equals(value(tx,"kind"))?"+ ":"− ")
                    +money(value(tx,"amount_grosz"))+" • "
                    +friendlyValueStaticCategory(value(tx,"category"))
                    +(note.isBlank()?"":" • "+note)));
        }
        if(rows.isEmpty()){
            JOptionPane.showMessageDialog(this,"Brak wpisów PayCheck do usunięcia.");
            return;
        }

        JList<Choice> list=new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(Math.min(16,model.size()));
        list.setSelectedIndex(0);
        JScrollPane pane=new JScrollPane(list);
        pane.setPreferredSize(new Dimension(860,360));

        JButton all=new JButton("Zaznacz wszystko");
        JButton none=new JButton("Wyczyść zaznaczenie");
        all.addActionListener(e->{
            if(!model.isEmpty())list.setSelectionInterval(0,model.size()-1);
        });
        none.addActionListener(e->list.clearSelection());
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        controls.add(all);
        controls.add(none);
        controls.add(new JLabel("Ctrl / Shift także działa"));

        JPanel content=new JPanel(new BorderLayout(0,8));
        content.add(pane,BorderLayout.CENTER);
        content.add(controls,BorderLayout.SOUTH);

        if(JOptionPane.showConfirmDialog(this,content,
                "Zaznacz wpisy PayCheck do usunięcia",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)
                !=JOptionPane.OK_OPTION) return;

        int[] indices=list.getSelectedIndices();
        if(indices.length==0){
            JOptionPane.showMessageDialog(this,"Zaznacz co najmniej jeden wpis.");
            return;
        }
        java.util.List<JsonObject> selected=new ArrayList<>();
        for(int index:indices)selected.add(rows.get(index));
        deletePaycheckTransactions(selected);
    }

    private void deletePaycheckTransaction(JsonObject tx) {
        if(tx==null)return;
        deletePaycheckTransactions(java.util.List.of(tx));
    }

    private void deletePaycheckTransactions(java.util.List<JsonObject> selected) {
        if(selected==null||selected.isEmpty())return;
        int confirmed=0,matched=0;
        for(JsonObject tx:selected){
            if("confirmed".equals(value(tx,"status")))confirmed++;
            if(!value(tx,"statement_key").isBlank())matched++;
        }

        String message="Usunąć zaznaczone wpisy: "+selected.size()+"?\n\n"
            +"Potwierdzone: "+confirmed+" — saldo zostanie przeliczone.\n"
            +"Oczekujące: "+(selected.size()-confirmed)+" — nie wpływają na saldo."
            +(matched>0?"\nPowiązane z bankiem: "+matched
                +" — ich dowody wrócą do kolejki „do sprawdzenia”。":"")
            +"\nPowiązane koszty pojazdów pozostaną w historii pojazdów poza PayCheck.";
        int yes=JOptionPane.showConfirmDialog(this,message,
            "Usuń wpisy PayCheck",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
        if(yes!=JOptionPane.YES_OPTION)return;

        int removed=0;
        for(JsonObject tx:new ArrayList<>(selected)){
            String operationId=value(tx,"operation_id");
            String statementKey=value(tx,"statement_key");
            if(!statementKey.isBlank()){
                for(JsonElement e:mutableTable("bank_evidence_queue")){
                    if(!e.isJsonObject())continue;
                    JsonObject evidence=e.getAsJsonObject();
                    if(statementKey.equals(value(evidence,"evidence_key"))
                            &&"matched".equals(value(evidence,"state"))){
                        evidence.addProperty("state","open");
                        evidence.add("matched_operation_id",com.google.gson.JsonNull.INSTANCE);
                        evidence.add("matched_at",com.google.gson.JsonNull.INSTANCE);
                    }
                }
            }
            if(!operationId.isBlank()){
                for(JsonElement e:mutableTable("vehicle_costs")){
                    if(!e.isJsonObject())continue;
                    JsonObject cost=e.getAsJsonObject();
                    if(operationId.equals(value(cost,"paycheck_operation_id")))
                        cost.add("paycheck_operation_id",com.google.gson.JsonNull.INSTANCE);
                }
            }
            if(mutableTable("paycheck_transactions").remove(tx))removed++;
        }
        if(removed>0)markDirty();
        showSection("PayCheck");
        JOptionPane.showMessageDialog(this,"Usunięto wpisów PayCheck: "+removed+".");
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
                list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
                    "Usuń zaznaczone",
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
                else if (action == 3) {
                    int[] selected=list.getSelectedIndices();
                    if(selected.length==0){
                        JOptionPane.showMessageDialog(this,"Zaznacz prywatne wpisy do usunięcia.");
                        continue;
                    }
                    java.util.List<String> ids=new ArrayList<>();
                    for(int index:selected)ids.add(entries.get(index).id);
                    int yes=JOptionPane.showConfirmDialog(this,
                        "Usunąć zaznaczone prywatne wpisy: "+ids.size()+"?",
                        "Prywatny PayCheck",JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if(yes==JOptionPane.YES_OPTION)
                        DesktopPrivatePaycheckVault.delete(session,ids);
                }
                else if (action == 4) exportPrivatePaycheck();
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

    private void importBankStatement() {
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this,
                "Najpierw połącz Desktop z EDHOME na telefonie.");
            return;
        }

        FileDialog chooser=new FileDialog(this,
            "Wybierz wyciąg bankowy PDF / CSV / XLSX",FileDialog.LOAD);
        chooser.setMultipleMode(false);
        chooser.setFilenameFilter((dir,name)->{
            String lower=name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".pdf")||lower.endsWith(".csv")
                ||lower.endsWith(".xlsx")||lower.endsWith(".txt");
        });
        chooser.setVisible(true);
        java.io.File[] files=chooser.getFiles();
        if(files==null||files.length==0)return;
        Path file=files[0].toPath();

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
                 "Przypomnienie","remind_time"), "tasks"), BorderLayout.CENTER);
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

        commitDesktopNfcLink(uid, parts[0], id);
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
        // Ustawienia mają być operacyjne, nie serwisowym pulpitem.
        // Połączenie, diagnostyka i komplet logów są jedynymi akcjami
        // potrzebnymi użytkownikowi na co dzień.
        PREFS.putBoolean("autoConnect", true);
        PREFS.putBoolean("autoWrite", true);

        JPanel page = page("Ustawienia • telefon i diagnostyka");

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(APP_BG);

        JPanel statusCard = new RoundedPanel(APP_SURFACE, 20);
        statusCard.setLayout(new BoxLayout(statusCard, BoxLayout.Y_AXIS));
        statusCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel title = new JLabel("Połączenie EDHOME");
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        statusCard.add(title);
        statusCard.add(Box.createVerticalStrut(6));

        String savedHost = sanitizedPhoneHost();
        String savedToken = PREFS.get("token", "").trim();
        String stateText;
        if (savedHost.isBlank() || savedToken.isBlank())
            stateText = "Nie sparowano telefonu • użyj kodu QR";
        else if (connected)
            stateText = "ONLINE • telefon połączony • " + savedHost + ":" + PORT;
        else
            stateText = "OFFLINE • sparowano • " + savedHost + ":" + PORT;
        JLabel pairState = new JLabel(stateText);
        pairState.setForeground(connected ? APP_ACCENT : APP_MUTED);
        statusCard.add(pairState);
        content.add(statusCard);
        content.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new GridLayout(5, 1, 0, 8));
        actions.setBackground(APP_BG);
        JButton qrPair = actionButton("Połącz telefon przez QR");
        JButton diagnose = actionButton("Sprawdź połączenie PC ↔ telefon");
        JButton updateDesktop = actionButton("Aktualizuj EDHOME Desktop — 1 klik");
        JButton downloadAllLogs =
            actionButton("Pobierz logi telefonu + Desktop na Pulpit");
        JButton openBackups = actionButton("Otwórz backup EDHOME na PC");
        actions.add(qrPair);
        actions.add(diagnose);
        actions.add(updateDesktop);
        actions.add(downloadAllLogs);
        actions.add(openBackups);
        content.add(actions);
        content.add(Box.createVerticalStrut(12));

        JLabel help = new JLabel(
            "<html><b>Połączenie:</b> PC i telefon muszą być w tej samej sieci "
          + "Wi‑Fi/LAN. Kliknij „Połącz telefon przez QR”, a w EDHOME Android "
          + "zeskanuj kod z ekranu komputera.<br><br>"
          + "<b>Logi:</b> jednym kliknięciem zapisujesz diagnostykę Desktopu "
          + "oraz nowe logi telefonu na Pulpicie. Log telefonu jest usuwany "
          + "z aplikacji dopiero po potwierdzonym zapisie na PC.<br><br>"
          + "<b>Backup PC:</b> EDHOME zapisuje bieżącą kopię automatycznie "
          + "w folderze EDHOME\\Backup oraz utrzymuje dzienne kopie.</html>");
        help.setForeground(APP_MUTED);
        content.add(help);

        qrPair.addActionListener(e -> showQrPairing(pairState, qrPair));
        diagnose.addActionListener(e -> diagnosePhoneConnection(
            PREFS.get("phoneIp", "").trim(),
            PREFS.get("token", "").trim(), diagnose));
        updateDesktop.addActionListener(e -> oneClickDesktopUpdate(updateDesktop));
        downloadAllLogs.addActionListener(e -> saveAllDiagnosticsToDesktop(
            PREFS.get("phoneIp", "").trim(),
            PREFS.get("token", "").trim(), downloadAllLogs));
        openBackups.addActionListener(e -> openPcBackupFolder());

        page.add(content, BorderLayout.NORTH);
        return page;
    }

    private String desktopDiagnosticsText() {
        String host = sanitizedPhoneHost();
        return "EDHOME Desktop " + DESKTOP_VERSION + "\n"
            + "System: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "\n"
            + "Java: " + System.getProperty("java.version") + "\n"
            + "Stan telefonu: " + (connected ? "ONLINE" : "OFFLINE") + "\n"
            + "Adres telefonu: " + (host.isBlank() ? "brak" : host) + "\n"
            + "Katalog logów: " + DesktopDiagnosticLog.logDirectory() + "\n\n"
            + "--- LOG EDHOME DESKTOP ---\n"
            + DesktopDiagnosticLog.readFullText();
    }

    private void copyDesktopDiagnostics() {
        String report = desktopDiagnosticsText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
            new StringSelection(report), null);
        DesktopDiagnosticLog.event("DIAGNOSTICS_COPIED");
        JOptionPane.showMessageDialog(this,
            "Skopiowano diagnostykę EDHOME Desktop do schowka.");
    }

    private void saveDesktopDiagnostics() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Zapisz diagnostykę EDHOME Desktop");
        chooser.setSelectedFile(new java.io.File("EDHOME-Desktop-diagnostyka.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.writeString(chooser.getSelectedFile().toPath(),
                desktopDiagnosticsText(), StandardCharsets.UTF_8);
            DesktopDiagnosticLog.event("DIAGNOSTICS_TXT_SAVED");
            JOptionPane.showMessageDialog(this,
                "Zapisano diagnostykę Desktop:\n"
                    + chooser.getSelectedFile().toPath().toAbsolutePath());
        } catch (Exception error) {
            DesktopDiagnosticLog.error("DIAGNOSTICS_TXT_SAVE", error);
            JOptionPane.showMessageDialog(this,
                "Nie zapisano diagnostyki:\n" + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }


    private static Path diagnosticsDesktopFolder() {
        try {
            java.io.File shellDesktop =
                javax.swing.filechooser.FileSystemView.getFileSystemView()
                    .getHomeDirectory();
            if (shellDesktop != null && shellDesktop.isDirectory())
                return shellDesktop.toPath();
        } catch (Exception ignored) { }

        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        if (Files.isDirectory(desktop)) return desktop;
        return Path.of(System.getProperty("user.home"));
    }

    private void saveAllDiagnosticsToDesktop(String rawHost, String secret,
            JButton trigger) {
        trigger.setEnabled(false);
        DesktopDiagnosticLog.event("DIAGNOSTICS_EXPORT_STARTED");
        new SwingWorker<String,Void>() {
            @Override protected String doInBackground() throws Exception {
                Path desktop = diagnosticsDesktopFolder();
                Files.createDirectories(desktop);
                String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());

                Path desktopTarget = desktop.resolve(
                    "EDHOME-Desktop-diagnostyka-" + stamp + ".txt");
                Files.writeString(desktopTarget, desktopDiagnosticsText(),
                    StandardCharsets.UTF_8);
                DesktopDiagnosticLog.event("DIAGNOSTICS_TXT_SAVED",
                    "target=" + desktopTarget.toAbsolutePath());

                StringBuilder summary = new StringBuilder();
                summary.append("Zapisano na Pulpicie:\n")
                    .append(desktopTarget.getFileName());

                if (rawHost == null || rawHost.isBlank()
                        || secret == null || secret.isBlank()) {
                    summary.append("\n\nTelefon: pominięty — brak aktywnego parowania.");
                    return summary.toString();
                }

                PhoneDiagnosticsResult phone;
                String phoneHost = rawHost;
                try {
                    phone = new LanClient(phoneHost, PORT, secret).diagnostics();
                } catch (Exception first) {
                    String discovered = LanClient.discover(secret, PORT);
                    if (discovered == null) {
                        DesktopDiagnosticLog.error("PHONE_DIAGNOSTICS_DOWNLOAD", first);
                        summary.append("\n\nTelefon: nie pobrano logów — ")
                            .append(rootMessage(first));
                        return summary.toString();
                    }
                    phoneHost = discovered;
                    PREFS.put("phoneIp", discovered);
                    try {
                        phone = new LanClient(phoneHost, PORT, secret).diagnostics();
                    } catch (Exception retry) {
                        DesktopDiagnosticLog.error(
                            "PHONE_DIAGNOSTICS_DOWNLOAD", retry);
                        summary.append("\n\nTelefon: nie pobrano logów — ")
                            .append(rootMessage(retry));
                        return summary.toString();
                    }
                }

                if (phone == null) {
                    DesktopDiagnosticLog.event("PHONE_DIAGNOSTICS_EMPTY");
                    summary.append("\n\nTelefon: brak nowych logów.");
                    return summary.toString();
                }

                Path phoneTarget = desktop.resolve(
                    "EDHOME-Android-diagnostyka-" + stamp + ".txt");
                try {
                    Files.writeString(phoneTarget, phone.text, StandardCharsets.UTF_8);
                } catch (Exception error) {
                    DesktopDiagnosticLog.error("PHONE_DIAGNOSTICS_SAVE", error);
                    summary.append("\n\nTelefon: pobrano log, ale nie zapisano go na Pulpicie — ")
                        .append(rootMessage(error));
                    return summary.toString();
                }

                DesktopDiagnosticLog.event("PHONE_DIAGNOSTICS_SAVED",
                    "bytes=" + phone.text.getBytes(StandardCharsets.UTF_8).length
                        + " target=" + phoneTarget.toAbsolutePath());
                summary.append("\n").append(phoneTarget.getFileName());

                try {
                    boolean cleared = new LanClient(phoneHost, PORT, secret)
                        .ackDiagnostics(phone.id);
                    DesktopDiagnosticLog.event(
                        cleared ? "PHONE_DIAGNOSTICS_CLEARED"
                            : "PHONE_DIAGNOSTICS_NOT_CLEARED");
                    summary.append(cleared
                        ? "\n\nLog telefonu po udanym zapisie został usunięty z aplikacji."
                        : "\n\nTelefon nie potwierdził usunięcia — log pozostaje w aplikacji.");
                } catch (Exception error) {
                    DesktopDiagnosticLog.error("PHONE_DIAGNOSTICS_ACK", error);
                    summary.append("\n\nLog telefonu zapisano na PC, ale nie udało się go usunąć z aplikacji.");
                }

                return summary.toString();
            }

            @Override protected void done() {
                trigger.setEnabled(true);
                try {
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        get(), "EDHOME Desktop • diagnostyka",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception error) {
                    DesktopDiagnosticLog.error("DIAGNOSTICS_EXPORT", error);
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie zapisano diagnostyki na Pulpicie:\n" + rootMessage(error),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void downloadPhoneDiagnostics(String rawHost, String secret,
            JButton trigger) {
        if (rawHost == null || rawHost.isBlank()
                || secret == null || secret.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw połącz Desktop z telefonem i zapisz kod parowania.");
            return;
        }
        trigger.setEnabled(false);
        new SwingWorker<PhoneDiagnosticsResult,Void>() {
            @Override protected PhoneDiagnosticsResult doInBackground() throws Exception {
                return new LanClient(rawHost, PORT, secret).diagnostics();
            }

            @Override protected void done() {
                trigger.setEnabled(true);
                try {
                    PhoneDiagnosticsResult result = get();
                    if (result == null) {
                        DesktopDiagnosticLog.event("PHONE_DIAGNOSTICS_EMPTY");
                        JOptionPane.showMessageDialog(EdhomeDesktop.this,
                            "Brak nowych logów diagnostycznych na telefonie.");
                        return;
                    }

                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Zapisz diagnostykę EDHOME Android");
                    chooser.setSelectedFile(new java.io.File(
                        "EDHOME-Android-diagnostyka.txt"));
                    if (chooser.showSaveDialog(EdhomeDesktop.this)
                            != JFileChooser.APPROVE_OPTION) return;

                    Path target = chooser.getSelectedFile().toPath();
                    Files.writeString(target, result.text, StandardCharsets.UTF_8);
                    DesktopDiagnosticLog.event("PHONE_DIAGNOSTICS_SAVED",
                        "bytes=" + result.text.getBytes(StandardCharsets.UTF_8).length);

                    trigger.setEnabled(false);
                    new SwingWorker<Boolean,Void>() {
                        @Override protected Boolean doInBackground() throws Exception {
                            return new LanClient(rawHost, PORT, secret)
                                .ackDiagnostics(result.id);
                        }
                        @Override protected void done() {
                            trigger.setEnabled(true);
                            try {
                                boolean cleared = get();
                                DesktopDiagnosticLog.event(
                                    cleared ? "PHONE_DIAGNOSTICS_CLEARED"
                                        : "PHONE_DIAGNOSTICS_NOT_CLEARED");
                                JOptionPane.showMessageDialog(EdhomeDesktop.this,
                                    cleared
                                        ? "Zapisano logi telefonu i usunięto pobraną kopię z aplikacji.\n"
                                            + target.toAbsolutePath()
                                        : "Log zapisano na PC, ale telefon nie potwierdził usunięcia. "
                                            + "Logi na telefonie pozostają bezpieczne.");
                            } catch (Exception error) {
                                DesktopDiagnosticLog.error(
                                    "PHONE_DIAGNOSTICS_ACK", error);
                                JOptionPane.showMessageDialog(EdhomeDesktop.this,
                                    "Log zapisano na PC, ale nie usunięto go z telefonu:\n"
                                        + rootMessage(error),
                                    "EDHOME Desktop", JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    }.execute();
                } catch (Exception error) {
                    DesktopDiagnosticLog.error("PHONE_DIAGNOSTICS_DOWNLOAD", error);
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie pobrano logów telefonu:\n" + rootMessage(error),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void diagnosePhoneConnection(String rawHost, String secret,
            JButton trigger) {
        if (rawHost == null || rawHost.isBlank()
                || secret == null || secret.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Telefon nie jest jeszcze sparowany. Połącz go najpierw przez QR.");
            return;
        }
        trigger.setEnabled(false);
        connection.setText("DIAGNOSTYKA SIECI…");
        new SwingWorker<String,Void>() {
            @Override protected String doInBackground() {
                String host;
                try {
                    host = LanClient.normalizeHost(rawHost);
                } catch (Exception error) {
                    return "Błędny adres telefonu: " + rootMessage(error);
                }

                java.util.List<String> locals = QrPairingSession.localAddresses();
                StringBuilder report = new StringBuilder();
                report.append("EDHOME Desktop ").append(DESKTOP_VERSION).append("\n")
                    .append("Telefon: ").append(host).append(":").append(PORT).append("\n")
                    .append("IPv4 Windows: ")
                    .append(locals.isEmpty() ? "BRAK" : String.join(", ", locals))
                    .append("\n\n");

                boolean tcp = false;
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, PORT), 2500);
                    tcp = true;
                    report.append("TCP ").append(PORT).append(": OK — PC dociera do telefonu.\n");
                } catch (Exception error) {
                    report.append("TCP ").append(PORT).append(": BRAK POŁĄCZENIA — ")
                        .append(rootMessage(error)).append("\n")
                        .append("Najczęściej: nieaktualny IP telefonu, sieć gościnna/AP isolation, ")
                        .append("VPN albo filtr/firewall.\n");
                }

                if (tcp && secret != null && !secret.isBlank()) {
                    try {
                        long revision = new LanClient(host, PORT, secret).state();
                        report.append("Kod parowania / EDHOME: OK")
                            .append(revision >= 0 ? " • rewizja " + revision : "")
                            .append("\n");
                    } catch (Exception error) {
                        report.append("Kod parowania / EDHOME: BŁĄD — ")
                            .append(rootMessage(error)).append("\n");
                    }
                } else if (tcp) {
                    report.append("Kod parowania: nie podano — pominięto autoryzację.\n");
                }

                if (!tcp && secret != null && !secret.isBlank()) {
                    report.append("\nAutomatyczne szukanie telefonu: ");
                    String discovered = LanClient.discover(secret, PORT);
                    if (discovered == null) {
                        report.append("nie znaleziono EDHOME w aktywnych podsieciach.");
                    } else {
                        report.append("znaleziono ").append(discovered).append(":").append(PORT);
                        PREFS.put("phoneIp", discovered);
                    }
                }
                return report.toString();
            }

            @Override protected void done() {
                trigger.setEnabled(true);
                connection.setText(connected
                    ? "ONLINE • połączono" : "OFFLINE • diagnostyka zakończona");
                try {
                    JTextArea area = new JTextArea(get(), 13, 58);
                    area.setEditable(false);
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    area.setCaretPosition(0);
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        new JScrollPane(area),
                        "EDHOME Desktop • diagnostyka sieci",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        "Nie wykonano diagnostyki:\n" + rootMessage(error),
                        "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
                    updatePhoneChangeCursorFromSnapshot(snapshot);
                    lastFullReconcileAt = System.currentTimeMillis();
                    connected = true;
                    syncConflictPaused = false;
                    dirty = false;
                    DesktopDiagnosticLog.event("SYNC_PULL_OK");
                    validate(snapshot);
                    saveCache(snapshot);
                    savePcBackup(snapshot);
                    connection.setText("ONLINE • Android "
                        + str(snapshot,"sourceVersion",""));
                    connection.setForeground(APP_ACCENT);
                    showSection(current);
                    updateTrayTooltip();
                } catch (Exception ex) {
                    DesktopDiagnosticLog.error("SYNC_PULL", ex);
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
                if (!syncConflictPaused) saveChangesToPhone(null, true);
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
        if (lastFullReconcileAt > 0 && now - lastFullReconcileAt >= 1800000L) {
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
                        if (revision < phoneRevision) {
                            DesktopDiagnosticLog.event("SYNC_PHONE_REVISION_REGRESSED",
                                "from=" + phoneRevision + " to=" + revision);
                            connection.setText("SYNC • wykryto przywrócenie kopii • pełne pojednanie…");
                            pullFromPhone(host, secret, null, true);
                        } else {
                            pullChangesFromPhone(host, secret, revision);
                        }
                    } else {
                        phoneRevision = revision;
                    }
                } catch (Exception ignored) {
                    connected = false;
                    connection.setText("OFFLINE • szukam telefonu po zmianie IP");
                    connection.setForeground(new Color(230, 175, 95));
                    updateTrayTooltip();
                    SwingUtilities.invokeLater(() -> autoConnectSaved(true));
                }
            }
        }.execute();
    }

    private void pullChangesFromPhone(String host, String secret,
            long observedRevision) {
        if (connecting || dirty || autoSaving) return;
        if (snapshot == null || syncedSnapshot == null) {
            pullFromPhone(host, secret, null, true);
            return;
        }
        connecting = true;
        connection.setText("SYNC • pobieram zmiany z telefonu…");
        final JsonObject working = snapshot.deepCopy();
        final long previousRevision = phoneRevision;
        final long startAt = Math.max(0L, phoneChangeCursorAt - 2000L);
        new SwingWorker<PhoneDeltaSyncResult,Void>() {
            @Override protected PhoneDeltaSyncResult doInBackground() throws Exception {
                LanClient client = new LanClient(host, PORT, secret);
                long cursorAt = startAt;
                String cursorUuid = "";
                int applied = 0;
                int batches = 0;
                while (true) {
                    DeltaBatch batch = client.changes(cursorAt, cursorUuid);
                    applied += applyPhoneChanges(working, batch.changes);
                    batches++;
                    if (!batch.hasMore) {
                        long finalRevision;
                        try { finalRevision = client.state(); }
                        catch (Exception ignored) { finalRevision = observedRevision; }
                        return new PhoneDeltaSyncResult(working, finalRevision,
                            applied, batch.cursorUpdatedAt, batch.cursorSyncUuid);
                    }
                    if (batch.cursorUpdatedAt < cursorAt
                            || (batch.cursorUpdatedAt == cursorAt
                                && batch.cursorSyncUuid.equals(cursorUuid)))
                        throw new IOException("Telefon nie przesunął kursora zmian.");
                    cursorAt = batch.cursorUpdatedAt;
                    cursorUuid = batch.cursorSyncUuid;
                    if (batches > 200)
                        throw new IOException("Za dużo paczek zmian — wymagane pełne pojednanie.");
                }
            }
            @Override protected void done() {
                connecting = false;
                try {
                    PhoneDeltaSyncResult result = get();
                    if (result.applied == 0 && previousRevision >= 0
                            && observedRevision != previousRevision) {
                        DesktopDiagnosticLog.event("SYNC_PULL_EMPTY_REVISION_CHANGE",
                            "from=" + previousRevision + " to=" + observedRevision);
                        connection.setText(
                            "SYNC • zmieniona rewizja bez delty • pełne pojednanie…");
                        pullFromPhone(host, secret, null, true);
                        return;
                    }
                    snapshot = result.data;
                    syncedSnapshot = snapshot.deepCopy();
                    phoneRevision = result.revision;
                    if (result.cursorUpdatedAt >= phoneChangeCursorAt) {
                        phoneChangeCursorAt = result.cursorUpdatedAt;
                        phoneChangeCursorUuid = result.cursorSyncUuid;
                    }
                    connected = true;
                    syncConflictPaused = false;
                    dirty = false;
                    saveCache(snapshot);
                    savePcBackup(snapshot);
                    DesktopDiagnosticLog.event("SYNC_PULL_DELTA_OK",
                        "changes=" + result.applied);
                    connection.setText(result.applied == 0
                        ? "ONLINE • dane aktualne"
                        : "ONLINE • pobrano " + result.applied
                            + (result.applied == 1 ? " zmianę" : " zmian"));
                    showSection(current);
                    updateTrayTooltip();
                } catch (Exception error) {
                    DesktopDiagnosticLog.error("SYNC_PULL_DELTA", error);
                    connection.setText("SYNC • pełne pojednanie awaryjne…");
                    pullFromPhone(host, secret, null, true);
                }
            }
        }.execute();
    }

    private static int applyPhoneChanges(JsonObject root, JsonArray changes)
            throws Exception {
        if (root == null || !root.has("tables") || !root.get("tables").isJsonObject()
                || !root.has("syncRecords") || !root.get("syncRecords").isJsonArray())
            throw new IOException("Brak metadanych synchronizacji w lokalnej kopii.");
        JsonObject tables = root.getAsJsonObject("tables");
        JsonArray metadata = root.getAsJsonArray("syncRecords");
        int applied = 0;
        for (JsonElement element : changes) {
            if (!element.isJsonObject())
                throw new IOException("Nieprawidłowa zmiana z telefonu.");
            JsonObject change = element.getAsJsonObject();
            if (!change.has("meta") || !change.get("meta").isJsonObject())
                throw new IOException("Zmiana z telefonu nie ma metadanych.");
            JsonObject remoteMeta = change.getAsJsonObject("meta");
            String uuid = value(remoteMeta, "syncUuid").toLowerCase(Locale.ROOT);
            String tableName = value(remoteMeta, "table");
            String rowKey = value(remoteMeta, "rowKey");
            long remoteRevision = longValue(remoteMeta, "revision");
            if (!uuid.matches("[0-9a-f-]{36}") || remoteRevision < 1L
                    || !tables.has(tableName) || !tables.get(tableName).isJsonArray())
                throw new IOException("Nieprawidłowa metadana zmiany z telefonu.");
            int metaIndex = syncMetaIndexByUuid(metadata, uuid);
            if (metaIndex >= 0) {
                JsonObject localMeta = metadata.get(metaIndex).getAsJsonObject();
                long localRevision = longValue(localMeta, "revision");
                if (localRevision > remoteRevision) continue;
                if (localRevision == remoteRevision
                        && value(localMeta, "rowHash").equals(value(remoteMeta, "rowHash")))
                    continue;
            }
            JsonArray rows = tables.getAsJsonArray(tableName);
            int rowIndex = rowIndexByKey(tableName, rows, rowKey);
            boolean deleted = remoteMeta.has("deletedAt")
                && !remoteMeta.get("deletedAt").isJsonNull();
            if (deleted) {
                if (rowIndex >= 0) rows.remove(rowIndex);
            } else {
                if (!change.has("row") || !change.get("row").isJsonObject())
                    throw new IOException("Zmiana rekordu nie zawiera danych.");
                JsonObject row = change.getAsJsonObject("row");
                if (!rowKey.equals(patchRowKey(tableName, row)))
                    throw new IOException("Klucz zmiany nie pasuje do rekordu.");
                if (rowIndex >= 0) rows.set(rowIndex, row.deepCopy());
                else rows.add(row.deepCopy());
            }
            if (metaIndex >= 0) metadata.set(metaIndex, remoteMeta.deepCopy());
            else metadata.add(remoteMeta.deepCopy());
            applied++;
        }
        return applied;
    }

    private static int syncMetaIndexByUuid(JsonArray metadata, String uuid) {
        for (int i = 0; i < metadata.size(); i++) {
            JsonElement element = metadata.get(i);
            if (!element.isJsonObject()) continue;
            if (uuid.equalsIgnoreCase(value(element.getAsJsonObject(), "syncUuid"))) return i;
        }
        return -1;
    }

    private static int rowIndexByKey(String tableName, JsonArray rows, String rowKey) {
        for (int i = 0; i < rows.size(); i++) {
            JsonElement element = rows.get(i);
            if (!element.isJsonObject()) continue;
            if (rowKey.equals(patchRowKey(tableName, element.getAsJsonObject()))) return i;
        }
        return -1;
    }

    private void updatePhoneChangeCursorFromSnapshot(JsonObject root) {
        long bestAt = 0L;
        String bestUuid = "";
        if (root != null && root.has("syncRecords")
                && root.get("syncRecords").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("syncRecords")) {
                if (!element.isJsonObject()) continue;
                JsonObject meta = element.getAsJsonObject();
                long at = longValue(meta, "updatedAt");
                String uuid = value(meta, "syncUuid").toLowerCase(Locale.ROOT);
                if (at > bestAt || (at == bestAt && uuid.compareTo(bestUuid) > 0)) {
                    bestAt = at;
                    bestUuid = uuid;
                }
            }
        }
        phoneChangeCursorAt = bestAt;
        phoneChangeCursorUuid = bestUuid;
    }

    private void startAutoSaveLoop() {
        autoSaveTimer = new javax.swing.Timer(1500, e -> {
            if (PREFS.getBoolean("autoWrite", true) && dirty
                    && !syncConflictPaused)
                saveChangesToPhone(null, true);
        });
        autoSaveTimer.setRepeats(false);
    }

    private void scheduleAutoSave() {
        if (!PREFS.getBoolean("autoWrite", true) || autoSaveTimer == null) return;
        if (syncConflictPaused) {
            connection.setText("KONFLIKT • lokalne zmiany zachowane • synchronizacja wstrzymana");
            return;
        }
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
                savePcBackup(snapshot);
            }
        } catch (Exception error) {
            DesktopDiagnosticLog.error("LOCAL_CACHE_SAVE", error);
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

    private void showQrPairing(JLabel pairState, JButton trigger) {
        if (qrPairingSession != null) {
            qrPairingSession.close();
            qrPairingSession = null;
        }
        final JDialog[] dialog = new JDialog[1];
        final JLabel status = new JLabel("Czekam na skan z telefonu…");
        if (trigger != null) trigger.setEnabled(false);
        try {
            prepareWindowsPairingFirewall();
            QrPairingSession session = QrPairingSession.start(payload ->
                SwingUtilities.invokeLater(() -> {
                    String pairedHost = LanClient.normalizeHost(payload.phoneIp);
                    if (QrPairingSession.localAddresses().contains(pairedHost)) {
                        status.setText("Odrzucono błędny adres telefonu — wskazuje ten komputer.");
                        DesktopDiagnosticLog.event("QR_PAIRING_SELF_IP_REJECTED",
                            "host=" + pairedHost);
                        return;
                    }
                    PREFS.put("phoneIp", pairedHost);
                    PREFS.put("token", payload.token);
                    PREFS.putBoolean("autoConnect", true);
                    PREFS.putBoolean("autoWrite", true);
                    pairState.setText("Połączono z Androidem " + payload.version
                        + " • " + pairedHost + ":" + PORT);
                    pairState.setForeground(APP_ACCENT);
                    status.setText("Połączono z Androidem " + payload.version + ".");
                    DesktopDiagnosticLog.event("QR_PAIRING_OK",
                        "host=" + pairedHost + " android=" + payload.version);
                    if (dialog[0] != null) dialog[0].dispose();
                    if (qrPairingSession != null) {
                        qrPairingSession.close();
                        qrPairingSession = null;
                    }
                    if (trigger != null) trigger.setEnabled(true);
                    pullFromPhone(pairedHost, payload.token, null);
                }));
            qrPairingSession = session;

            BufferedImage image = qrImage(session.qrText(), 320);
            JLabel qr = new JLabel(new ImageIcon(image));
            qr.setHorizontalAlignment(SwingConstants.CENTER);

            JPanel body = new JPanel(new BorderLayout(10, 10));
            body.setBorder(new EmptyBorder(14, 18, 14, 18));
            JLabel instructions = new JLabel(
                "<html><b>Na telefonie:</b> EDHOME → Ustawienia "
              + "→ Skanuj QR z ekranu PC.<br>"
              + "QR wygasa po 3 minutach i działa wyłącznie w sieci lokalnej."
              + "</html>");
            body.add(instructions, BorderLayout.NORTH);
            body.add(qr, BorderLayout.CENTER);
            body.add(status, BorderLayout.SOUTH);

            JDialog window = new JDialog(this,
                "EDHOME • połącz telefon przez QR", false);
            dialog[0] = window;
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            window.setAlwaysOnTop(true);
            window.setContentPane(body);
            window.pack();
            window.setResizable(false);
            window.setLocationRelativeTo(this);
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowOpened(java.awt.event.WindowEvent e) {
                    window.toFront();
                    window.requestFocus();
                    DesktopDiagnosticLog.event("QR_PAIRING_WINDOW_VISIBLE");
                }

                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (qrPairingSession == session) {
                        qrPairingSession.close();
                        qrPairingSession = null;
                    }
                    if (trigger != null) trigger.setEnabled(true);
                }
            });
            window.setVisible(true);
        } catch (Exception ex) {
            if (trigger != null) trigger.setEnabled(true);
            DesktopDiagnosticLog.error("QR_PAIRING_START", ex);
            JOptionPane.showMessageDialog(this,
                "Nie można przygotować QR do połączenia:\n" + rootMessage(ex)
                    + "\nSprawdź, czy PC jest połączony z tą samą siecią co telefon.",
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void prepareWindowsPairingFirewall() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win") || PREFS.getBoolean("pairFirewallReady", false)) return;

        int answer = JOptionPane.showConfirmDialog(this,
            "Aby telefon mógł połączyć się z tym komputerem przez QR, "
                + "EDHOME musi zezwolić w Zaporze Windows na TCP 45824.\n"
                + "Reguła będzie ograniczona do urządzeń z lokalnej podsieci.\n\n"
                + "Windows może poprosić o zgodę administratora.",
            "EDHOME • zezwolenie na połączenie telefonu",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            DesktopDiagnosticLog.event("PAIR_FIREWALL_SKIPPED");
            return;
        }

        try {
            String command =
                "$p=Start-Process -FilePath 'netsh.exe' "
                    + "-ArgumentList 'advfirewall firewall add rule "
                    + "name=EDHOME_Desktop_QR_45824 dir=in action=allow "
                    + "protocol=TCP localport=45824 profile=any remoteip=LocalSubnet' "
                    + "-Verb RunAs -Wait -PassThru; exit $p.ExitCode";
            Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-Command", command)
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(
                    "Przekroczono czas oczekiwania na zgodę Zapory Windows.");
            }
            if (process.exitValue() != 0) {
                throw new IOException(
                    "Zapora Windows zwróciła kod " + process.exitValue() + ".");
            }
            PREFS.putBoolean("pairFirewallReady", true);
            DesktopDiagnosticLog.event("PAIR_FIREWALL_READY",
                "tcp=" + PAIR_PORT + " remote=LocalSubnet");
        } catch (Exception error) {
            DesktopDiagnosticLog.error("PAIR_FIREWALL_SETUP", error);
            JOptionPane.showMessageDialog(this,
                "Nie udało się automatycznie zezwolić na połączenie QR.\n"
                    + "Możesz kontynuować, ale jeśli telefon nadal nie połączy się, "
                    + "zezwól EDHOME w Zaporze Windows.\n\n"
                    + rootMessage(error),
                "EDHOME Desktop", JOptionPane.WARNING_MESSAGE);
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

        boolean storageBatch = "storage_items".equals(tableName);
        java.util.List<JsonObject> selectedRows = new ArrayList<>();
        java.util.List<JCheckBox> selectors = new ArrayList<>();
        JLabel selectedCount = new JLabel(storageBatch
            ? "Zaznaczone: 0" : "");
        selectedCount.setForeground(APP_ACCENT);

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
                JsonObject row = el.getAsJsonObject();
                if (storageBatch) {
                    JPanel selectable = new JPanel(new BorderLayout(8, 0));
                    selectable.setOpaque(false);
                    JCheckBox selector = new JCheckBox();
                    selector.setOpaque(false);
                    selector.setToolTipText("Zaznacz do operacji zbiorczej");
                    selector.addActionListener(e -> {
                        if (selector.isSelected()) {
                            if (!selectedRows.contains(row)) selectedRows.add(row);
                        } else selectedRows.remove(row);
                        selectedCount.setText("Zaznaczone: " + selectedRows.size());
                    });
                    selectors.add(selector);
                    selectable.add(selector, BorderLayout.WEST);
                    selectable.add(recordCard(row, columns, tableName), BorderLayout.CENTER);
                    list.add(selectable);
                } else {
                    list.add(recordCard(row, columns, tableName));
                }
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
        JPanel footerInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        footerInfo.setOpaque(false);
        JLabel count = new JLabel("Pozycji: " + rows.size()
            + "  •  widok użytkowy — bez technicznych ID");
        count.setForeground(APP_MUTED);
        footerInfo.add(count);
        if (storageBatch) footerInfo.add(selectedCount);

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
        if (storageBatch) {
            JButton selectAll = actionButton("Zaznacz wszystko");
            selectAll.addActionListener(e -> {
                for (JCheckBox selector : selectors) selector.setSelected(true);
                selectedRows.clear();
                for (JsonElement element : rows)
                    if (element.isJsonObject()) selectedRows.add(element.getAsJsonObject());
                selectedCount.setText("Zaznaczone: " + selectedRows.size());
            });
            JButton clear = actionButton("Wyczyść");
            clear.addActionListener(e -> {
                for (JCheckBox selector : selectors) selector.setSelected(false);
                selectedRows.clear();
                selectedCount.setText("Zaznaczone: 0");
            });
            JButton moveSelected = actionButton("⇄ Przenieś zaznaczone");
            moveSelected.addActionListener(e -> showBatchStorageMove(selectedRows));
            actions.add(selectAll);
            actions.add(clear);
            actions.add(moveSelected);
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
        footer.add(footerInfo, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private void showBatchStorageMove(java.util.List<JsonObject> selectedRows) {
        if (selectedRows == null || selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Najpierw zaznacz co najmniej jedną rzecz albo pudełko.");
            return;
        }

        java.util.List<JsonObject> selected = new ArrayList<>(selectedRows);
        boolean allThings = true;
        for (JsonObject row : selected) {
            if (!"thing".equals(value(row, "kind"))) allThings = false;
            if (!value(row, "lent_to").isBlank()) {
                JOptionPane.showMessageDialog(this,
                    "W zaznaczeniu znajduje się wypożyczona rzecz: "
                        + value(row, "name")
                        + ".\nNajpierw odnotuj jej zwrot.",
                    "EDHOME Desktop", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        java.util.List<Choice> modes = new ArrayList<>();
        modes.add(new Choice("place", "Przenieś do miejsca"));
        if (allThings) modes.add(new Choice("box", "Włóż do pudełka"));
        modes.add(new Choice("none", "Usuń przypisanie lokalizacji"));

        JComboBox<Choice> mode = new JComboBox<>(modes.toArray(new Choice[0]));
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.add(new JLabel("Zaznaczono: " + selected.size()));
        form.add(new JLabel("Operacja:"));
        form.add(mode);

        int first = JOptionPane.showConfirmDialog(this, form,
            "EDHOME Desktop • operacja zbiorcza",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (first != JOptionPane.OK_OPTION) return;

        Choice chosenMode = (Choice) mode.getSelectedItem();
        if (chosenMode == null) return;

        Choice destination = new Choice("", "—");
        if ("place".equals(chosenMode.value)) {
            JComboBox<Choice> places = referenceCombo("places", "", false);
            if (places.getItemCount() == 0) {
                JOptionPane.showMessageDialog(this, "Nie ma żadnego miejsca docelowego.");
                return;
            }
            int result = JOptionPane.showConfirmDialog(this, places,
                "Wybierz miejsce dla " + selected.size() + " pozycji",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            destination = (Choice) places.getSelectedItem();
        } else if ("box".equals(chosenMode.value)) {
            JComboBox<Choice> boxes = storageBatchBoxCombo();
            if (boxes.getItemCount() == 0) {
                JOptionPane.showMessageDialog(this, "Nie ma żadnego pudełka docelowego.");
                return;
            }
            int result = JOptionPane.showConfirmDialog(this, boxes,
                "Wybierz pudełko dla " + selected.size() + " rzeczy",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            destination = (Choice) boxes.getSelectedItem();
        }

        String destinationText = "none".equals(chosenMode.value)
            ? "bez lokalizacji" : destination == null ? "—" : destination.label;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Zmienić lokalizację " + selected.size() + " pozycji na:\n"
                + destinationText + "?",
            "EDHOME Desktop • potwierdź operację zbiorczą",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        java.util.Map<JsonObject,JsonObject> before =
            new java.util.LinkedHashMap<>();
        try {
            for (JsonObject row : selected) {
                before.put(row, row.deepCopy());
                if ("place".equals(chosenMode.value)) {
                    row.add("parent_box_id", com.google.gson.JsonNull.INSTANCE);
                    row.addProperty("place_id", Long.parseLong(destination.value));
                } else if ("box".equals(chosenMode.value)) {
                    row.add("place_id", com.google.gson.JsonNull.INSTANCE);
                    row.addProperty("parent_box_id", Long.parseLong(destination.value));
                } else {
                    row.add("parent_box_id", com.google.gson.JsonNull.INSTANCE);
                    row.add("place_id", com.google.gson.JsonNull.INSTANCE);
                }
                validateDesktopStorageRow(row);
            }

            for (JsonObject row : selected) {
                JsonObject old = before.get(row);
                boolean moved = !value(old, "parent_box_id").equals(
                        value(row, "parent_box_id"))
                    || !value(old, "place_id").equals(value(row, "place_id"));
                if (moved) appendDesktopStorageMove(row, old);
            }
            markDirty();
            showSection("Magazyn");
            JOptionPane.showMessageDialog(this,
                "Zmieniono lokalizację " + selected.size()
                    + (selected.size() == 1 ? " pozycji." : " pozycji.")
                    + "\nZmiany zapisano lokalnie i trafią do telefonu przez synchronizację.");
        } catch (Exception error) {
            for (java.util.Map.Entry<JsonObject,JsonObject> entry : before.entrySet())
                restoreJsonObject(entry.getKey(), entry.getValue());
            JOptionPane.showMessageDialog(this,
                "Nie wykonano operacji zbiorczej:\n" + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JComboBox<Choice> storageBatchBoxCombo() {
        java.util.List<Choice> options = new ArrayList<>();
        for (JsonElement element : table("storage_items")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (!"box".equals(value(row, "kind"))) continue;
            String id = value(row, "id");
            if (id.isBlank()) continue;
            String name = value(row, "name");
            options.add(new Choice(id, name.isBlank() ? "Pudełko #" + id : name));
        }
        return new JComboBox<>(options.toArray(new Choice[0]));
    }

    private JPanel recordCard(JsonObject row, String[][] columns, String tableName) {
        JPanel card = new RoundedPanel(APP_SURFACE, 18);
        card.setLayout(new BorderLayout(10, 0));
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        card.setPreferredSize(new Dimension(1, 82));

        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);

        if ("storage_items".equals(tableName)) {
            JLabel thumbnail = storageThumbnailLabel(row);
            if (thumbnail != null) left.add(thumbnail, BorderLayout.WEST);
        }

        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);

        String mainKey = columns.length == 0 ? "" : columns[0][1];
        String main = columns.length == 0 ? "Pozycja"
            : friendlyValue(mainKey, row);
        if (main.isBlank() || "—".equals(main)) main = "Pozycja";
        JLabel title = new JLabel(compactText(main, 52));
        title.setToolTipText(main);
        title.setForeground(APP_TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        text.add(title, BorderLayout.NORTH);

        int detailCount = Math.max(0, columns.length - 1);
        JPanel details = new JPanel(new GridLayout(1, Math.max(1, detailCount), 10, 0));
        details.setOpaque(false);
        for (int i = 1; i < columns.length; i++) {
            String full = friendlyValue(columns[i][1], row);
            JLabel value = new JLabel("<html><span style='color:#A4AEBB'>"
                + html(columns[i][0]) + ":</span> "
                + html(compactText(full, 24)) + "</html>");
            value.setToolTipText(columns[i][0] + ": " + full);
            value.setForeground(APP_TEXT);
            value.setFont(value.getFont().deriveFont(11.5f));
            details.add(value);
        }
        text.add(details, BorderLayout.CENTER);
        left.add(text, BorderLayout.CENTER);
        card.add(left, BorderLayout.CENTER);

        JPanel rowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 13));
        rowActions.setOpaque(false);
        JButton edit = compactActionButton("Edytuj");
        edit.addActionListener(e -> editRow(row, columns));
        rowActions.add(edit);
        if ("storage_items".equals(tableName) || "places".equals(tableName)) {
            JButton qr = compactActionButton("QR");
            qr.setToolTipText("QR / etykieta");
            qr.addActionListener(e -> {
                DesktopQrLabels.Label label = qrLabel(tableName, row);
                if (label != null) showQrLabelWorkflow(java.util.List.of(label));
            });
            rowActions.add(qr);
        }
        String nfcKind = nfcKindForRecord(tableName, row);
        if (nfcKind != null) {
            JButton nfc = compactActionButton("NFC");
            JsonObject link = nfcLinkForTarget(nfcKind, longValue(row, "id"));
            nfc.setToolTipText(link == null
                ? "Przypisz tag NFC"
                : "NFC " + compactText(value(link, "uid"), 16)
                    + " • Zmień / Usuń powiązanie");
            nfc.addActionListener(e -> showDesktopNfcManager(tableName, row));
            rowActions.add(nfc);
        }
        if (canDeleteTable(tableName)) {
            JButton remove = compactActionButton("Usuń");
            remove.addActionListener(e -> deleteRecord(tableName, row));
            rowActions.add(remove);
        }
        card.add(rowActions, BorderLayout.EAST);
        return card;
    }

    private String nfcKindForRecord(String tableName, JsonObject row) {
        if ("storage_items".equals(tableName)) {
            String kind = value(row, "kind");
            return "thing".equals(kind) || "box".equals(kind) ? kind : null;
        }
        if ("places".equals(tableName)) return "place";
        if ("pantry".equals(tableName)) return "pantry";
        if ("vehicles".equals(tableName)) return "vehicle";
        return null;
    }

    private JsonObject nfcLinkForTarget(String kind, long targetId) {
        if (kind == null || targetId <= 0) return null;
        for (JsonElement element : table("nfc_links")) {
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            try {
                if (kind.equals(value(link, "target_kind"))
                        && link.get("target_id").getAsLong() == targetId)
                    return link;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private JsonObject nfcLinkForUid(String uid) {
        if (uid == null) return null;
        for (JsonElement element : table("nfc_links")) {
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            if (uid.equalsIgnoreCase(value(link, "uid"))) return link;
        }
        return null;
    }

    private void showDesktopNfcManager(String tableName, JsonObject row) {
        String kind = nfcKindForRecord(tableName, row);
        long id = longValue(row, "id");
        if (kind == null || id <= 0) return;
        String name = value(row, "name");
        if (name.isBlank()) name = "#" + id;
        JsonObject link = nfcLinkForTarget(kind, id);
        if (link == null) {
            readAndAssignDesktopNfc(kind, id, name);
            return;
        }
        Object[] actions = {"Zmień tag NFC", "Usuń powiązanie NFC",
            "Pokaż UID", "Anuluj"};
        int choice = JOptionPane.showOptionDialog(this,
            "Obiekt: " + name + "\nUID: " + value(link, "uid"),
            "EDHOME Desktop • NFC", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, actions, actions[0]);
        if (choice == 0) readAndAssignDesktopNfc(kind, id, name);
        else if (choice == 1) {
            int yes = JOptionPane.showConfirmDialog(this,
                "Usunąć powiązanie NFC z „" + name + "”?\n"
                    + "Tag fizyczny nie zostanie zapisany ani skasowany.",
                "EDHOME Desktop • NFC", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (yes == JOptionPane.YES_OPTION) {
                removeDesktopNfcTarget(kind, id);
                markDirty();
                showSection(current);
            }
        } else if (choice == 2) {
            JOptionPane.showMessageDialog(this,
                "UID NFC: " + value(link, "uid"),
                "EDHOME Desktop • NFC", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void readAndAssignDesktopNfc(String kind, long id, String name) {
        JOptionPane.showMessageDialog(this,
            "Po zamknięciu tego komunikatu przyłóż naklejkę NFC albo brelok "
                + "do czytnika PC/SC.\nEDHOME odczyta UID i nie zapisuje niczego "
                + "w pamięci taga.",
            "Przypisz tag NFC • " + name, JOptionPane.INFORMATION_MESSAGE);
        new SwingWorker<String,Void>() {
            @Override protected String doInBackground() throws Exception {
                return DesktopHardwareScanner.readNfcUid(Duration.ofSeconds(15));
            }
            @Override protected void done() {
                try {
                    String uid = get();
                    assignDesktopNfc(uid, kind, id, name);
                } catch (Exception error) {
                    JOptionPane.showMessageDialog(EdhomeDesktop.this,
                        rootMessage(error), "EDHOME Desktop • NFC",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void assignDesktopNfc(String uid, String kind, long id, String name) {
        JsonObject existing = nfcLinkForUid(uid);
        if (existing != null) {
            String oldKind = value(existing, "target_kind");
            long oldId = longValue(existing, "target_id");
            if (!oldKind.equals(kind) || oldId != id) {
                int yes = JOptionPane.showConfirmDialog(this,
                    "Ten tag jest już przypisany do "
                        + oldKind + " #" + oldId + ".\nPrzenieść go do „"
                        + name + "”?",
                    "EDHOME Desktop • NFC", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (yes != JOptionPane.YES_OPTION) return;
            }
        }
        commitDesktopNfcLink(uid, kind, id);
        markDirty();
        showSection(current);
        JOptionPane.showMessageDialog(this,
            "NFC przypisany do: " + name + "\nUID: " + uid,
            "EDHOME Desktop • NFC", JOptionPane.INFORMATION_MESSAGE);
    }

    private void commitDesktopNfcLink(String uid, String kind, long id) {
        JsonArray links = mutableTable("nfc_links");
        for (int i = links.size() - 1; i >= 0; i--) {
            JsonElement element = links.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            boolean sameUid = uid.equalsIgnoreCase(value(link, "uid"));
            boolean sameTarget = kind.equals(value(link, "target_kind"))
                && longValue(link, "target_id") == id;
            if (sameUid || sameTarget) links.remove(i);
        }
        JsonObject row = new JsonObject();
        row.addProperty("id", nextId("nfc_links"));
        row.addProperty("uid", uid);
        row.addProperty("target_kind", kind);
        row.addProperty("target_id", id);
        row.addProperty("created_at", System.currentTimeMillis());
        links.add(row);
    }

    private void removeDesktopNfcTarget(String kind, long id) {
        JsonArray links = table("nfc_links");
        for (int i = links.size() - 1; i >= 0; i--) {
            JsonElement element = links.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject link = element.getAsJsonObject();
            if (kind.equals(value(link, "target_kind"))
                    && longValue(link, "target_id") == id)
                links.remove(i);
        }
    }

    private JButton compactActionButton(String label) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setForeground(APP_TEXT);
        button.setBackground(APP_SURFACE_2);
        button.setFont(button.getFont().deriveFont(11f));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(APP_SURFACE_2),
            new EmptyBorder(5, 8, 5, 8)));
        return button;
    }

    private JLabel storageThumbnailLabel(JsonObject row) {
        long itemId;
        try { itemId = row.get("id").getAsLong(); }
        catch (Exception invalid) { return null; }
        if (snapshot == null || !snapshot.has("settings")
                || !snapshot.get("settings").isJsonObject()) return null;
        JsonElement thumbs = snapshot.getAsJsonObject("settings")
            .get("storageThumbnails");
        if (thumbs == null || !thumbs.isJsonArray()) return null;

        for (JsonElement element : thumbs.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject thumb = element.getAsJsonObject();
            try {
                if (!thumb.has("itemId") || thumb.get("itemId").getAsLong() != itemId)
                    continue;
                String encoded = value(thumb, "jpegBase64");
                if (encoded.isBlank() || encoded.length() > 50000) return null;
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(encoded); }
                catch (IllegalArgumentException plainFailed) {
                    bytes = Base64.getMimeDecoder().decode(encoded);
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) return null;
                Image scaled = image.getScaledInstance(56, 56, Image.SCALE_SMOOTH);
                JLabel label = new JLabel(new ImageIcon(scaled));
                label.setPreferredSize(new Dimension(56, 56));
                label.setToolTipText("Miniatura zdjęcia");
                return label;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String compactText(String text, int max) {
        if (text == null) return "";
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= max) return normalized;
        return normalized.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private static String html(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
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
            || "pantry".equals(tableName)
            || "shopping_items".equals(tableName)
            || "storage_items".equals(tableName)
            || "vehicles".equals(tableName)
            || "device_timers".equals(tableName)
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
            task = capitalizeLabel(task);
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
        // editRow() zapisuje cache przed dołączeniem nowego rekordu do tabeli.
        // Zapisz ponownie po dołączeniu, aby nowy rekord przetrwał awarię sync/restart PC.
        markDirty();
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
            row.add("project_id", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("project_sort_order", 0L);
            return row;
        }
        if ("projects".equals(tableName)) {
            row.addProperty("id", id);
            row.addProperty("name", "Nowy projekt");
            row.add("parent_id", com.google.gson.JsonNull.INSTANCE);
            row.add("place_id", com.google.gson.JsonNull.INSTANCE);
            row.add("assignee_id", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("status", "active");
            row.add("due_date", com.google.gson.JsonNull.INSTANCE);
            row.add("budget_grosz", com.google.gson.JsonNull.INSTANCE);
            row.addProperty("created_at", now);
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
        // New Desktop-created IDs come from a huge positive space instead of
        // MAX(id)+1. Multiple PCs can therefore work offline without both
        // creating the same local key. sync_uuid remains the sync identity.
        for (int attempt = 0; attempt < 128; attempt++) {
            long random = new SecureRandom().nextLong() & 0x001FFFFFFFFFFFFFL;
            long candidate = 1_000_000_000_000L + random;
            boolean used = false;
            for (JsonElement element : table(tableName)) {
                if (!element.isJsonObject()) continue;
                try {
                    JsonElement id = element.getAsJsonObject().get("id");
                    if (id != null && !id.isJsonNull()
                            && id.getAsLong() == candidate) {
                        used = true;
                        break;
                    }
                } catch (Exception ignored) { }
            }
            if (!used) return candidate;
        }
        throw new IllegalStateException(
            "Nie udało się wygenerować unikalnego identyfikatora.");
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
        String impact="";
        if("vehicles".equals(tableName))
            impact="\nHistoria serwisu, opony, polisy, dokumenty i koszty tego pojazdu "
                +"zostaną usunięte. Transakcje PayCheck pozostaną.";
        else if("pantry".equals(tableName))
            impact="\nKody i dane bieżącego produktu zostaną usunięte. "
                +"Historia ruchów i zakupów pozostanie.";
        else if("device_timers".equals(tableName))
            impact="\nTimer zniknie po synchronizacji także z telefonu.";
        int choice = JOptionPane.showConfirmDialog(this,
            "Usunąć „" + (label.isBlank() ? "pozycję" : label) + "”?"
                +impact,
            "EDHOME Desktop", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            if ("tasks".equals(tableName)) {
                removeRowsByLong("task_rotation_members", "task_id", id);
                removeRowsByLong("project_task_dependencies", "task_id", id);
                removeRowsByLong("project_task_dependencies", "depends_on_task_id", id);
                removeRowsByLong("project_task_work_sessions", "task_id", id);
                removeRowsByLong("project_task_blockers", "task_id", id);
                table("tasks").remove(row);
            } else if ("pantry".equals(tableName)) {
                if (hasOpenAuditSession())
                    throw new IllegalStateException(
                        "Najpierw zakończ lub anuluj aktywny remanent.");
                removeRowsByLong("pantry_barcodes","pantry_id",id);
                removeRowsByLong("pantry_product_details","pantry_id",id);
                removeRowsByLong("pantry_packages","pantry_id",id);
                removeDesktopNfcTarget("pantry",id);
                table("pantry").remove(row);
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
                removeDesktopNfcTarget(value(row,"kind"),id);
                table("storage_items").remove(row);
                removeStorageThumbnail(id);
            } else if ("vehicles".equals(tableName)) {
                removeRowsByLong("vehicle_events","vehicle_id",id);
                removeRowsByLong("vehicle_tyre_sets","vehicle_id",id);
                removeRowsByLong("vehicle_policies","vehicle_id",id);
                removeRowsByLong("vehicle_documents","vehicle_id",id);
                removeRowsByLong("vehicle_costs","vehicle_id",id);
                removeDesktopNfcTarget("vehicle",id);
                table("vehicles").remove(row);
            } else if ("device_timers".equals(tableName)) {
                table("device_timers").remove(row);
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
                removeDesktopNfcTarget("place",id);
                table("places").remove(row);
            } else return;
            markDirty();
            showSection(current);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hasOpenAuditSession() {
        for(JsonElement element:table("audit_sessions")){
            if(!element.isJsonObject())continue;
            JsonObject row=element.getAsJsonObject();
            String status=value(row,"status");
            String completed=value(row,"completed_at");
            if(completed.isBlank()
                    &&!("completed".equals(status)||"cancelled".equals(status)))
                return true;
        }
        return false;
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
        if ("parent_id".equals(key) && row.has("budget_grosz"))
            return referenceName("projects", raw);
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
                case "paused": return "Wstrzymane";
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
        if ("category".equals(key)) return friendlyValueStaticCategory(raw);
        if ("amount_grosz".equals(key) || "budget_grosz".equals(key)) return money(raw);
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
        JsonObject before=row.deepCopy();
        boolean storageRow=isDesktopStorageRow(row);
        boolean storageRowLive=storageRow&&desktopStorageRowIsLive(row);
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
            if (row.has("budget_grosz") && row.has("parent_id")
                    && row.has("status") && row.has("created_at"))
                validateDesktopProjectRow(row);
            if(storageRow) {
                if(storageRowLive&&!value(before,"kind").equals(value(row,"kind")))
                    throw new IllegalArgumentException(
                        "Typ istniejącej rzeczy/pudełka jest stały ze względu na QR i NFC.");
                validateDesktopStorageRow(row);
                boolean moved=!value(before,"parent_box_id").equals(
                        value(row,"parent_box_id"))
                    ||!value(before,"place_id").equals(value(row,"place_id"));
                if(storageRowLive&&moved&&!value(before,"lent_to").isBlank())
                    throw new IllegalArgumentException(
                        "Najpierw odnotuj zwrot wypożyczonej rzeczy.");
                if(storageRowLive&&moved)
                    appendDesktopStorageMove(row,before);
            }
            boolean projectTask=row.has("project_id")
                &&!value(row,"project_id").isBlank()
                &&row.has("duration_minutes")&&row.has("done");
            if(projectTask) {
                if(intValue(row,"duration_minutes")<20)
                    throw new IllegalArgumentException(
                        "Czynność projektu musi mieć co najmniej 20 minut.");
                boolean completing=intValue(before,"done")==0&&intValue(row,"done")!=0;
                if(completing) {
                    long taskId=longValue(row,"id");
                    if(desktopActiveProjectWorkSession(taskId)!=null)
                        throw new IllegalArgumentException(
                            "Najpierw zatrzymaj pomiar czasu tej czynności.");
                    java.util.List<String> waits=desktopOpenDependencyTitles(taskId);
                    if(!waits.isEmpty())
                        throw new IllegalArgumentException(
                            "Najpierw zakończ: "+String.join(", ",waits)+".");
                    String blocker=desktopHardBlockReason(taskId);
                    if(!blocker.isBlank())
                        throw new IllegalArgumentException(blocker);
                }
            }
            markDirty();
            showSection(current);
            return true;
        } catch (Exception error) {
            restoreJsonObject(row,before);
            JOptionPane.showMessageDialog(this,
                "Nie zapisano zmiany: " + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static void restoreJsonObject(JsonObject target,JsonObject source) {
        for(String key:new ArrayList<>(target.keySet()))target.remove(key);
        for(Map.Entry<String,JsonElement> entry:source.entrySet())
            target.add(entry.getKey(),entry.getValue().deepCopy());
    }

    private boolean isDesktopStorageRow(JsonObject row) {
        return row!=null&&row.has("parent_box_id")&&row.has("place_id")
            &&row.has("created_at")&&row.has("kind");
    }

    private boolean desktopStorageRowIsLive(JsonObject row) {
        for(JsonElement element:table("storage_items"))
            if(element.isJsonObject()&&element.getAsJsonObject()==row)return true;
        return false;
    }

    private void validateDesktopStorageRow(JsonObject row) {
        String kind=value(row,"kind");
        String parent=value(row,"parent_box_id");
        String place=value(row,"place_id");
        String lent=value(row,"lent_to");
        String lentAt=value(row,"lent_at");
        if(!"thing".equals(kind)&&!"box".equals(kind))
            throw new IllegalArgumentException("Wybierz rzecz albo pudełko.");
        if(!parent.isBlank()&&!place.isBlank())
            throw new IllegalArgumentException(
                "Wybierz jedno położenie: pudełko albo miejsce.");
        if("box".equals(kind)&&!parent.isBlank())
            throw new IllegalArgumentException(
                "Pudełko można przypisać tylko do miejsca.");
        if(!lent.isBlank()&&!"thing".equals(kind))
            throw new IllegalArgumentException("Tylko rzecz może być wypożyczona.");
        if(lent.isBlank()!=lentAt.isBlank())
            throw new IllegalArgumentException(
                "Wypożyczenie zmieniaj przez akcję Wypożycz / Zwrot.");
        if(!parent.isBlank()) {
            JsonObject box;
            try{box=scannerRowById("storage_items",Long.parseLong(parent));}
            catch(Exception invalid){box=null;}
            if(box==null||!"box".equals(value(box,"kind"))||box==row)
                throw new IllegalArgumentException(
                    "Rzecz możesz włożyć tylko do istniejącego pudełka.");
        }
        if(!place.isBlank()) {
            JsonObject destination;
            try{destination=scannerRowById("places",Long.parseLong(place));}
            catch(Exception invalid){destination=null;}
            if(destination==null)
                throw new IllegalArgumentException(
                    "Wybrane miejsce już nie istnieje.");
        }
    }

    private JComboBox<Choice> storageParentBoxCombo(JsonObject row,String selected) {
        java.util.List<Choice> options=new ArrayList<>();
        options.add(new Choice("","—"));
        if("thing".equals(value(row,"kind"))) {
            long current=-1;
            try{current=row.get("id").getAsLong();}catch(Exception ignored){}
            for(JsonElement element:table("storage_items")) {
                if(!element.isJsonObject())continue;
                JsonObject candidate=element.getAsJsonObject();
                if(!"box".equals(value(candidate,"kind")))continue;
                long id;
                try{id=candidate.get("id").getAsLong();}catch(Exception invalid){continue;}
                if(id==current)continue;
                options.add(new Choice(Long.toString(id),value(candidate,"name")));
            }
        }
        JComboBox<Choice> combo=new JComboBox<>(options.toArray(new Choice[0]));
        for(int i=0;i<options.size();i++)
            if(options.get(i).value.equals(selected))combo.setSelectedIndex(i);
        return combo;
    }

    private String desktopStorageLocation(JsonObject row) {
        String parent=value(row,"parent_box_id");
        if(!parent.isBlank())
            return "Pudełko: "+referenceName("storage_items",parent);
        String place=value(row,"place_id");
        if(!place.isBlank())return placePath(place);
        return "Bez miejsca";
    }

    private void appendDesktopStorageMove(JsonObject row,JsonObject before) {
        long id=row.get("id").getAsLong();
        JsonObject event=new JsonObject();
        event.addProperty("id",nextId("storage_events"));
        event.addProperty("item_id",id);
        event.addProperty("name_snapshot",value(row,"name"));
        event.addProperty("action","moved");
        String details="EDHOME Desktop: "+desktopStorageLocation(before)
            +" → "+desktopStorageLocation(row);
        if(details.length()>300)details=details.substring(0,300);
        event.addProperty("details",details);
        event.addProperty("happened_at",System.currentTimeMillis());
        table("storage_events").add(event);
    }

    private JComponent editorFor(String key, JsonObject row) {
        String raw = value(row, key);
        if("kind".equals(key)&&isDesktopStorageRow(row)
                &&desktopStorageRowIsLive(row)) {
            Choice locked="box".equals(raw)
                ?new Choice("box","Pudełko"):new Choice("thing","Rzecz");
            JComboBox<Choice> combo=new JComboBox<>(new Choice[]{locked});
            combo.setEnabled(false);
            combo.setToolTipText(
                "Typ jest stały, ponieważ QR i NFC zapisują typ obiektu.");
            return combo;
        }
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
        if ("parent_id".equals(key) && row.has("budget_grosz"))
            return projectParentCombo(row,raw);
        if ("place_id".equals(key) || "parent_id".equals(key))
            return referenceCombo("places", raw, true);
        if ("parent_box_id".equals(key))
            return storageParentBoxCombo(row,raw);
        if ("lent_to".equals(key)&&isDesktopStorageRow(row)) {
            JTextField field=new JTextField(raw);
            field.setEditable(false);
            field.setToolTipText(
                "Wypożyczenie zmieniaj przez akcję Wypożycz / Zwrot.");
            return field;
        }

        JTextField field = new JTextField();
        if ("amount_grosz".equals(key) || "budget_grosz".equals(key)) {
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
        } else if ("status".equals(key) && row.has("budget_grosz")) {
            out.add(new Choice("active", "Aktywny"));
            out.add(new Choice("paused", "Wstrzymany"));
            out.add(new Choice("done", "Zakończony"));
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
            for (Choice category : privateCategoryChoices()) out.add(category);
        } else if ("category".equals(key)) {
            out.add(new Choice("food", "Żywność"));
            out.add(new Choice("household", "Domowe"));
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
        if ("amount_grosz".equals(key) || "budget_grosz".equals(key)) {
            if (text.isBlank()) {
                row.add(key, com.google.gson.JsonNull.INSTANCE);
                return;
            }
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
            else row.addProperty(key, shouldCapitalizeDesktopField(key)
                ? capitalizeLabel(text) : text);
        }
    }

    private static boolean shouldCapitalizeDesktopField(String key) {
        return "name".equals(key) || "title".equals(key)
            || "display_name".equals(key) || "label".equals(key);
    }

    private static String capitalizeLabel(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.isEmpty()) return text;
        for (int i=0; i<text.length(); i++) {
            char ch=text.charAt(i);
            if (!Character.isLetter(ch)) continue;
            char upper=Character.toUpperCase(ch);
            if (upper==ch) return text;
            return text.substring(0,i)+upper+text.substring(i+1);
        }
        return text;
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

    private static synchronized void saveCache(JsonObject data) throws IOException {
        Files.createDirectories(CACHE.getParent());
        Path temp = Files.createTempFile(CACHE.getParent(),
            CACHE.getFileName().toString() + ".", ".tmp");
        boolean committed = false;
        try {
            Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);

            IOException atomicFailure = null;
            try {
                Files.move(temp, CACHE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                committed = true;
                return;
            } catch (IOException error) {
                atomicFailure = error;
            }

            IOException last = atomicFailure;
            for (int attempt = 0; attempt < 4; attempt++) {
                try {
                    if (attempt > 0) {
                        try { Thread.sleep(75L * attempt); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Przerwano ponawianie zapisu cache.", interrupted);
                        }
                    }
                    Files.move(temp, CACHE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    committed = true;
                    return;
                } catch (IOException retryError) {
                    last = retryError;
                }
            }
            throw last == null
                ? new IOException("Nie udało się zapisać lokalnej kopii EDHOME.")
                : last;
        } finally {
            if (!committed) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private static synchronized void savePcBackup(JsonObject data) {
        if (data == null) return;
        try {
            Files.createDirectories(PC_BACKUP_DIR);
            String json = GSON.toJson(data);
            writeBackupFile(PC_BACKUP_DIR.resolve("EDHOME-PC-latest.json"), json);
            String day = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path daily = PC_BACKUP_DIR.resolve("EDHOME-PC-" + day + ".json");
            boolean refreshDaily = !Files.isRegularFile(daily);
            if (!refreshDaily) {
                try {
                    refreshDaily = System.currentTimeMillis()
                        - Files.getLastModifiedTime(daily).toMillis() >= 15 * 60 * 1000L;
                } catch (IOException ignored) { refreshDaily = true; }
            }
            if (refreshDaily) writeBackupFile(daily, json);
            try (java.util.stream.Stream<Path> stream = Files.list(PC_BACKUP_DIR)) {
                java.util.List<Path> dailyFiles = stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                        .matches("EDHOME-PC-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.json"))
                    .sorted(java.util.Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList());
                for (int i = 30; i < dailyFiles.size(); i++) Files.deleteIfExists(dailyFiles.get(i));
            }
        } catch (Exception error) {
            DesktopDiagnosticLog.error("PC_BACKUP_SAVE", error);
        }
    }

    private static void writeBackupFile(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(PC_BACKUP_DIR, target.getFileName().toString() + ".", ".tmp");
        boolean committed = false;
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnavailable) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            committed = true;
        } finally {
            if (!committed) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private void openPcBackupFolder() {
        try {
            Files.createDirectories(PC_BACKUP_DIR);
            if (!Desktop.isDesktopSupported())
                throw new IOException("System nie obsługuje otwierania folderów.");
            Desktop.getDesktop().open(PC_BACKUP_DIR.toFile());
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie można otworzyć folderu backupu:\n" + PC_BACKUP_DIR.toAbsolutePath()
                    + "\n\n" + rootMessage(error),
                "EDHOME Desktop", JOptionPane.ERROR_MESSAGE);
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

    private static long longValue(JsonObject row, String key) {
        try { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsLong() : 0L; }
        catch (Exception ignored) { return 0L; }
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
                    updatePhoneChangeCursorFromSnapshot(snapshot);
                    lastFullReconcileAt = System.currentTimeMillis();
                    connected = true;
                    dirty = false;
                    validate(snapshot);
                    saveCache(snapshot);
                    savePcBackup(snapshot);
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
                try {
                    client.state();
                } catch (Exception staleHost) {
                    String discovered = LanClient.discover(secret, PORT);
                    if (discovered == null || discovered.equals(host))
                        throw staleHost;
                    PREFS.put("phoneIp", discovered);
                    DesktopDiagnosticLog.event("SYNC_PUSH_PHONE_REDISCOVERED",
                        "host=" + discovered);
                    client = new LanClient(discovered, PORT, secret);
                    client.state();
                }
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
                    syncConflictPaused = false;
                    syncedSnapshot = result.data.deepCopy();
                    snapshotHash = result.sha256;
                    phoneRevision = result.revision;
                    if (!result.patchUsed) {
                        updatePhoneChangeCursorFromSnapshot(result.data);
                        lastFullReconcileAt = System.currentTimeMillis();
                    }

                    if (localEditGeneration == generation) {
                        snapshot = result.data.deepCopy();
                        dirty = false;
                        saveCache(snapshot);
                        savePcBackup(snapshot);
                    } else {
                        // Użytkownik zdążył zrobić kolejne zmiany w czasie synchronizacji.
                        dirty = true;
                        saveCache(snapshot);
                        scheduleAutoSave();
                    }

                    DesktopDiagnosticLog.event("SYNC_PUSH_OK",
                        "patch=" + result.patchUsed + " operations=" + result.operations);
                    connection.setText(result.patchUsed
                        ? "ONLINE • zsynchronizowano " + result.operations
                            + (result.operations == 1 ? " zmianę" : " zmiany")
                        : "ONLINE • pełne pojednanie zakończone");
                    if (!automatic) showSection(current);
                    updateTrayTooltip();

                    if (result.patchUsed && !dirty) {
                        String catchUpHost = PREFS.get("phoneIp", host).trim();
                        if (!catchUpHost.isBlank()) {
                            DesktopDiagnosticLog.event("SYNC_PUSH_CATCHUP_START");
                            SwingUtilities.invokeLater(() ->
                                pullChangesFromPhone(catchUpHost, secret, result.revision));
                        }
                    }
                } catch (Exception ex) {
                    DesktopDiagnosticLog.error("SYNC_PUSH", ex);
                    String message = rootMessage(ex);
                    boolean conflict = message.contains("Konflikt")
                        || message.contains("nowsze dane");
                    boolean rejected = message.contains(
                        "Telefon odrzucił zmianę rekordową");
                    boolean pauseRequired=conflict||rejected;
                    boolean firstPause=pauseRequired&&!syncConflictPaused;
                    if(pauseRequired)syncConflictPaused=true;
                    connection.setText(conflict
                        ? "KONFLIKT • lokalne zmiany zachowane • auto-sync wstrzymany"
                        : rejected
                            ? "BŁĄD DANYCH • lokalne zmiany zachowane • auto-sync wstrzymany"
                            : "ZAPISANO LOKALNIE • synchronizacja oczekuje");
                    if (automatic) {
                        if (trayIcon != null && firstPause)
                            trayIcon.displayMessage("EDHOME Desktop",
                                conflict
                                    ?"Konflikt konkretnego rekordu. Lokalne zmiany zostały zachowane. "
                                        +"Automatyczne ponawianie zostało wstrzymane."
                                    :"Telefon odrzucił konkretną zmianę danych. "
                                        +"Lokalna kopia została zachowana; sprawdź diagnostykę.",
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
            if ("project_task_dependencies".equals(table))
                return canonicalId(row.get("task_id")) + ":"
                    + canonicalId(row.get("depends_on_task_id"));
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

    private static final class DeltaBatch {
        final JsonArray changes;
        final boolean hasMore;
        final long cursorUpdatedAt;
        final String cursorSyncUuid;
        DeltaBatch(JsonArray changes, boolean hasMore, long cursorUpdatedAt, String cursorSyncUuid) {
            this.changes = changes == null ? new JsonArray() : changes;
            this.hasMore = hasMore;
            this.cursorUpdatedAt = cursorUpdatedAt;
            this.cursorSyncUuid = cursorSyncUuid == null ? "" : cursorSyncUuid;
        }
    }

    private static final class PhoneDeltaSyncResult {
        final JsonObject data;
        final long revision;
        final int applied;
        final long cursorUpdatedAt;
        final String cursorSyncUuid;
        PhoneDeltaSyncResult(JsonObject data, long revision, int applied,
                long cursorUpdatedAt, String cursorSyncUuid) {
            this.data = data;
            this.revision = revision;
            this.applied = applied;
            this.cursorUpdatedAt = cursorUpdatedAt;
            this.cursorSyncUuid = cursorSyncUuid == null ? "" : cursorSyncUuid;
        }
    }

    private static final class DeltaUnsupportedException extends IOException {
        DeltaUnsupportedException() { super("Telefon ma starszą wersję synchronizacji telefon → PC."); }
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
            // QR zawiera wyłącznie IPv4, więc słuchamy jawnie na 0.0.0.0.
            // Unikamy zależności od domyślnego dual-stack/JVM na Windows.
            server.bind(new InetSocketAddress(
                InetAddress.getByName("0.0.0.0"), PAIR_PORT), 8);
            server.setSoTimeout(180000);
            DesktopDiagnosticLog.event("QR_PAIRING_LISTENING",
                "port=" + PAIR_PORT + " hosts=" + String.join(",", localAddresses()));

            QrPairingSession session =
                new QrPairingSession(server, host, nonce, callback);
            session.thread = new Thread(session::acceptLoop, "edhome-desktop-qr-pair");
            session.thread.setDaemon(true);
            session.thread.start();
            return session;
        }

        String qrText() {
            java.util.List<String> addresses = localAddresses();
            String candidates = addresses.isEmpty()
                ? host : String.join(",", addresses);
            return "edhome://desktop-pair?v=1&host=" + host
                + "&hosts=" + candidates
                + "&port=" + PAIR_PORT + "&nonce=" + nonce;
        }

        private void acceptLoop() {
            long deadline = System.currentTimeMillis() + 180000L;
            try {
                while (!closed && System.currentTimeMillis() < deadline) {
                    try (Socket peer = server.accept()) {
                        if (handle(peer)) return;
                    } catch (SocketTimeoutException timeout) {
                        DesktopDiagnosticLog.event("QR_PAIRING_TIMEOUT");
                        return;
                    } catch (IOException error) {
                        if (!closed) {
                            DesktopDiagnosticLog.error("QR_PAIRING_ACCEPT", error);
                            continue;
                        }
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
            String remoteText = remote == null ? "brak" : remote.getHostAddress();
            DesktopDiagnosticLog.event("QR_PAIRING_PEER",
                "remote=" + remoteText);
            if (remote == null
                    || (!(remote instanceof Inet4Address))
                    || (!remote.isSiteLocalAddress() && !remote.isLoopbackAddress())) {
                DesktopDiagnosticLog.event("QR_PAIRING_REJECTED",
                    "reason=LAN_ONLY remote=" + remoteText);
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
                DesktopDiagnosticLog.event("QR_PAIRING_REJECTED",
                    "reason=PAIR_NONCE remote=" + remoteText);
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
                DesktopDiagnosticLog.event("QR_PAIRING_REJECTED",
                    "reason=PAIR_DATA remote=" + remoteText);
                reply(peer, 400, "{\"error\":\"PAIR_DATA\"}");
                return false;
            }

            String phoneIp = remote.getHostAddress();
            DesktopDiagnosticLog.event("QR_PAIRING_ACCEPTED",
                "remote=" + phoneIp + " android=" + version);
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

        private static java.util.List<String> localAddresses() {
            java.util.LinkedHashSet<String> preferred = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> fallback = new java.util.LinkedHashSet<>();
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
                        || name.contains("vmware") || name.contains("wsl");
                    boolean preferredNetwork = name.contains("wi-fi") || name.contains("wifi")
                        || name.contains("wireless") || name.contains("wlan")
                        || name.contains("ethernet") || name.startsWith("eth");
                    for (InetAddress address :
                            Collections.list(network.getInetAddresses())) {
                        if (!(address instanceof Inet4Address)
                                || !address.isSiteLocalAddress()
                                || address.isLoopbackAddress()) continue;
                        String value = address.getHostAddress();
                        if (virtual) continue;
                        if (preferredNetwork) preferred.add(value);
                        else fallback.add(value);
                    }
                }
            } catch (Exception ignored) { }
            java.util.ArrayList<String> result = new java.util.ArrayList<>(preferred);
            result.addAll(fallback);
            return result;
        }

        private static String localAddress() {
            java.util.List<String> addresses = localAddresses();
            return addresses.isEmpty() ? null : addresses.get(0);
        }
    }

    private static final class PhoneDiagnosticsResult {
        final String id;
        final String text;
        PhoneDiagnosticsResult(String id, String text) {
            this.id = id;
            this.text = text;
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

        PhoneDiagnosticsResult diagnostics() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/diagnostics"))
                .timeout(Duration.ofSeconds(8))
                .header("X-EDHOME-TOKEN", token)
                .header("Accept", "text/plain")
                .GET().build();
            HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204) return null;
            if (response.statusCode() == 404)
                throw new IOException("Telefon wymaga aktualizacji EDHOME do wersji z pobieraniem diagnostyki.");
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odpowiedział HTTP "
                    + response.statusCode() + ".");
            String id = response.headers()
                .firstValue("X-EDHOME-DIAGNOSTICS-ID").orElse("");
            if (!id.matches("[0-9a-f]{64}") || response.body().isBlank())
                throw new IOException("Telefon zwrócił niepełny pakiet diagnostyczny.");
            return new PhoneDiagnosticsResult(id, response.body());
        }

        boolean ackDiagnostics(String diagnosticsId) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/diagnostics/ack"))
                .timeout(Duration.ofSeconds(5))
                .header("X-EDHOME-TOKEN", token)
                .header("X-EDHOME-DIAGNOSTICS-ID", diagnosticsId)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 409) return false;
            if (response.statusCode() == 401)
                throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon nie potwierdził usunięcia logów: HTTP "
                    + response.statusCode() + ".");
            return true;
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

        DeltaBatch changes(long afterUpdatedAt, String afterUuid) throws Exception {
            String safeUuid = afterUuid == null ? "" : afterUuid.toLowerCase(Locale.ROOT);
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + host + ":" + port + "/changes?after="
                        + afterUpdatedAt + "&uuid=" + safeUuid))
                .timeout(Duration.ofSeconds(15))
                .header("X-EDHOME-TOKEN", token)
                .header("Accept", "application/json")
                .GET().build();
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) throw new DeltaUnsupportedException();
            if (response.statusCode() == 401) throw new IOException("Nieprawidłowy kod parowania.");
            if (response.statusCode() != 200)
                throw new IOException("Telefon odrzucił pobranie zmian: HTTP " + response.statusCode() + ".");
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray changes = body.has("changes") && body.get("changes").isJsonArray()
                ? body.getAsJsonArray("changes") : new JsonArray();
            boolean hasMore = body.has("hasMore") && body.get("hasMore").getAsBoolean();
            long cursorAt = body.has("cursorUpdatedAt")
                ? body.get("cursorUpdatedAt").getAsLong() : afterUpdatedAt;
            String cursorUuid = body.has("cursorSyncUuid")
                ? body.get("cursorSyncUuid").getAsString() : safeUuid;
            return new DeltaBatch(changes, hasMore, cursorAt, cursorUuid);
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
            if (response.statusCode() == 400) {
                String detail="";
                try {
                    JsonObject error=JsonParser.parseString(response.body())
                        .getAsJsonObject();
                    detail=error.has("message")
                        ?error.get("message").getAsString().trim():"";
                } catch(Exception ignored) { }
                throw new IOException("Telefon odrzucił zmianę rekordową"
                    +(detail.isBlank() ? ": HTTP 400." : ": "+detail));
            }
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
            java.util.List<String> locals = QrPairingSession.localAddresses();
            if (locals.isEmpty()) return null;

            java.util.LinkedHashSet<String> localSet =
                new java.util.LinkedHashSet<>(locals);
            java.util.LinkedHashSet<String> prefixes =
                new java.util.LinkedHashSet<>();
            for (String local : locals) {
                if (local == null || !local.matches("[0-9]+(\\.[0-9]+){3}"))
                    continue;
                int dot = local.lastIndexOf('.');
                if (dot > 0) prefixes.add(local.substring(0, dot + 1));
            }
            if (prefixes.isEmpty()) return null;

            ExecutorService pool = Executors.newFixedThreadPool(96);
            java.util.concurrent.ExecutorCompletionService<String> completion =
                new java.util.concurrent.ExecutorCompletionService<>(pool);
            int submitted = 0;
            try {
                for (String prefix : prefixes) {
                    for (int i = 1; i <= 254; i++) {
                        String candidate = prefix + i;
                        if (localSet.contains(candidate)) continue;
                        completion.submit(() ->
                            probe(candidate, port, token) ? candidate : null);
                        submitted++;
                    }
                }

                long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(8);
                for (int i = 0; i < submitted; i++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) break;
                    Future<String> future = completion.poll(
                        remaining, TimeUnit.NANOSECONDS);
                    if (future == null) break;
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
