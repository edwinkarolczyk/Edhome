package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Local visual floor-plan editor over synchronized EDHOME places/storage.
 * Geometry is Desktop-local; place/storage identity stays in the shared data model.
 */
final class DesktopFloorMapPanel extends JPanel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of(System.getProperty("user.home"),
        ".edhome", "floor-map.json");

    private static final Color BG = new Color(16, 20, 27);
    private static final Color SURFACE = new Color(29, 35, 45);
    private static final Color SURFACE_2 = new Color(37, 44, 56);
    private static final Color TEXT = new Color(239, 243, 247);
    private static final Color MUTED = new Color(164, 174, 188);
    private static final Color ACCENT = new Color(87, 214, 181);
    private static final Color ROUTE = new Color(64, 158, 255);
    private static final Color CURRENT = new Color(57, 196, 118);
    private static final Color TARGET = new Color(255, 108, 108);

    private final JsonArray places;
    private final JsonArray storage;
    private final Consumer<Long> editPlace;
    private final Map<Long,JsonObject> placeById = new LinkedHashMap<>();
    private final Map<Long,JsonObject> storageById = new LinkedHashMap<>();

    private final Map<Long,RoomLayout> layouts = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final List<String> floors = new ArrayList<>(
        List.of("Piwnica", "Parter", "Piętro"));

    private final JPanel floorsPanel = new JPanel();
    private final JComboBox<PlaceChoice> currentCombo = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final JLabel targetTitle = new JLabel("Brak wybranego celu");
    private final JTextArea targetInfo = new JTextArea();
    private final DefaultListModel<String> routeModel = new DefaultListModel<>();
    private final JList<String> routeList = new JList<>(routeModel);
    private final JButton linkButton = new JButton("＋ Dodaj przejście");
    private final JButton addRoomButton = new JButton("＋ Dodaj pomieszczenie");
    private final JButton editModeButton = new JButton("✥ Edycja układu: WYŁ.");
    private final JLabel status = new JLabel("Gotowe.");

    private boolean editMode;
    private Long pendingLinkFrom;
    private Long targetPlaceId;
    private Long targetStorageId;
    private List<Long> activeRoute = List.of();

    DesktopFloorMapPanel(JsonArray places, JsonArray storage,
            Consumer<Long> editPlace) {
        this.places = places == null ? new JsonArray() : places;
        this.storage = storage == null ? new JsonArray() : storage;
        this.editPlace = editPlace;

        indexData();
        loadLayout();
        ensureDefaultRooms();

        setLayout(new BorderLayout(12, 12));
        setBackground(BG);
        setBorder(new EmptyBorder(4, 4, 4, 4));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        refreshCurrentChoices();
        rebuildFloorCanvases();
        updateRoute();
    }

    private void indexData() {
        for (JsonElement element : places) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            Long id = longOrNull(row, "id");
            if (id != null) placeById.put(id, row);
        }
        for (JsonElement element : storage) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            Long id = longOrNull(row, "id");
            if (id != null) storageById.put(id, row);
        }
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JLabel currentLabel = new JLabel("Jestem tutaj:");
        currentLabel.setForeground(TEXT);
        left.add(currentLabel);
        currentCombo.setPreferredSize(new Dimension(220, 30));
        currentCombo.addActionListener(e -> updateRoute());
        left.add(currentCombo);

        editModeButton.addActionListener(e -> {
            editMode = !editMode;
            editModeButton.setText(editMode
                ? "✥ Edycja układu: WŁ." : "✥ Edycja układu: WYŁ.");
            status.setText(editMode
                ? "Przeciągaj pomieszczenia myszą. Zmiany zapisują się lokalnie."
                : "Tryb edycji wyłączony.");
            rebuildFloorCanvases();
        });
        left.add(editModeButton);

        addRoomButton.addActionListener(e -> addRoomToMap());
        left.add(addRoomButton);

        linkButton.addActionListener(e -> {
            pendingLinkFrom = null;
            status.setText("Kliknij pierwsze pomieszczenie, potem drugie — utworzę przejście.");
        });
        left.add(linkButton);

        JPanel right = new JPanel(new BorderLayout(6, 0));
        right.setOpaque(false);
        JLabel find = new JLabel("Gdzie jest:");
        find.setForeground(TEXT);
        right.add(find, BorderLayout.WEST);
        search.setPreferredSize(new Dimension(250, 30));
        search.addActionListener(e -> findTarget());
        right.add(search, BorderLayout.CENTER);
        JButton go = button("Szukaj");
        go.addActionListener(e -> findTarget());
        right.add(go, BorderLayout.EAST);

        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildCenter() {
        floorsPanel.setLayout(new BoxLayout(floorsPanel, BoxLayout.Y_AXIS));
        floorsPanel.setBackground(BG);
        JScrollPane maps = new JScrollPane(floorsPanel);
        maps.setBorder(null);
        maps.getViewport().setBackground(BG);
        maps.getVerticalScrollBar().setUnitIncrement(18);

        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.setBackground(SURFACE);
        side.setBorder(new EmptyBorder(14, 16, 14, 16));
        side.setPreferredSize(new Dimension(330, 1));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel h = new JLabel("Szczegóły celu");
        h.setForeground(TEXT);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 17f));
        targetTitle.setForeground(ACCENT);
        targetTitle.setFont(targetTitle.getFont().deriveFont(Font.BOLD, 18f));
        heading.add(h);
        heading.add(Box.createVerticalStrut(8));
        heading.add(targetTitle);
        side.add(heading, BorderLayout.NORTH);

        targetInfo.setEditable(false);
        targetInfo.setOpaque(false);
        targetInfo.setForeground(TEXT);
        targetInfo.setLineWrap(true);
        targetInfo.setWrapStyleWord(true);
        targetInfo.setFont(targetInfo.getFont().deriveFont(13f));

        JPanel center = new JPanel(new BorderLayout(6, 8));
        center.setOpaque(false);
        center.add(targetInfo, BorderLayout.NORTH);

        routeList.setBackground(SURFACE);
        routeList.setForeground(TEXT);
        routeList.setSelectionBackground(SURFACE_2);
        routeList.setVisibleRowCount(12);
        routeList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focus);
                l.setBackground(selected ? SURFACE_2 : SURFACE);
                l.setForeground(TEXT);
                l.setBorder(new EmptyBorder(4, 4, 4, 4));
                return l;
            }
        });
        center.add(new JScrollPane(routeList), BorderLayout.CENTER);
        side.add(center, BorderLayout.CENTER);

        status.setForeground(MUTED);
        side.add(status, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, maps, side);
        split.setResizeWeight(0.78);
        split.setDividerLocation(950);
        split.setBorder(null);
        split.setBackground(BG);
        return split;
    }

    private void rebuildFloorCanvases() {
        floorsPanel.removeAll();
        for (String floor : floors) {
            FloorCanvas canvas = new FloorCanvas(floor);
            JPanel wrap = new JPanel(new BorderLayout(0, 5));
            wrap.setOpaque(false);
            JLabel title = new JLabel(floor);
            title.setForeground(TEXT);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setBorder(new EmptyBorder(4, 5, 0, 5));
            wrap.add(title, BorderLayout.NORTH);
            wrap.add(canvas, BorderLayout.CENTER);
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 265));
            wrap.setPreferredSize(new Dimension(900, 265));
            floorsPanel.add(wrap);
            floorsPanel.add(Box.createVerticalStrut(8));
        }
        floorsPanel.revalidate();
        floorsPanel.repaint();
    }

    private void refreshCurrentChoices() {
        Object selected = currentCombo.getSelectedItem();
        Long selectedId = selected instanceof PlaceChoice ? ((PlaceChoice) selected).id : null;
        currentCombo.removeAllItems();
        currentCombo.addItem(new PlaceChoice(null, "Wybierz pomieszczenie"));
        layouts.values().stream()
            .sorted(Comparator.comparing(a -> roomName(a.placeId), String.CASE_INSENSITIVE_ORDER))
            .forEach(layout -> currentCombo.addItem(
                new PlaceChoice(layout.placeId, roomName(layout.placeId))));
        if (selectedId != null) {
            for (int i=0;i<currentCombo.getItemCount();i++) {
                PlaceChoice choice = currentCombo.getItemAt(i);
                if (Objects.equals(choice.id, selectedId)) {
                    currentCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void addRoomToMap() {
        List<PlaceChoice> choices = new ArrayList<>();
        for (JsonObject place : placeById.values()) {
            Long id = longOrNull(place, "id");
            if (id == null || layouts.containsKey(id)) continue;
            choices.add(new PlaceChoice(id, value(place, "name")));
        }
        choices.sort(Comparator.comparing(c -> c.label, String.CASE_INSENSITIVE_ORDER));
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Wszystkie miejsca są już dodane do mapy.");
            return;
        }

        JComboBox<PlaceChoice> place = new JComboBox<>(choices.toArray(new PlaceChoice[0]));
        JComboBox<String> floor = new JComboBox<>(floors.toArray(new String[0]));
        JPanel form = new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Miejsce:")); form.add(place);
        form.add(new JLabel("Poziom:")); form.add(floor);
        int ok = JOptionPane.showConfirmDialog(this, form,
            "Dodaj pomieszczenie do mapy",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        PlaceChoice selected = (PlaceChoice) place.getSelectedItem();
        String selectedFloor = String.valueOf(floor.getSelectedItem());
        if (selected == null || selected.id == null) return;

        int count = (int) layouts.values().stream()
            .filter(r -> selectedFloor.equals(r.floor)).count();
        int col = count % 4;
        int row = count / 4;
        layouts.put(selected.id, new RoomLayout(selected.id, selectedFloor,
            20 + col * 210, 20 + row * 105, 190, 90));
        saveLayout();
        refreshCurrentChoices();
        rebuildFloorCanvases();
    }

    private void ensureDefaultRooms() {
        if (!layouts.isEmpty()) return;
        List<JsonObject> roots = new ArrayList<>();
        for (JsonObject place : placeById.values()) {
            Long parent = longOrNull(place, "parent_id");
            if (parent == null || !placeById.containsKey(parent)) roots.add(place);
        }
        roots.sort(Comparator.comparing(p -> value(p,"name"), String.CASE_INSENSITIVE_ORDER));
        int index = 0;
        for (JsonObject place : roots) {
            Long id = longOrNull(place, "id");
            if (id == null) continue;
            int col = index % 4;
            int row = index / 4;
            layouts.put(id, new RoomLayout(id, "Parter",
                20 + col * 210, 20 + row * 105, 190, 90));
            index++;
        }
        if (!layouts.isEmpty()) saveLayout();
    }

    private void findTarget() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return;

        for (RoomLayout layout : layouts.values()) {
            String name = roomName(layout.placeId);
            if (name.toLowerCase(Locale.ROOT).contains(query)) {
                targetPlaceId = layout.placeId;
                targetStorageId = null;
                updateRoute();
                return;
            }
        }

        for (JsonObject item : storageById.values()) {
            String name = value(item, "name");
            if (!name.toLowerCase(Locale.ROOT).contains(query)) continue;
            Long place = effectivePlace(item);
            if (place == null || !layouts.containsKey(place)) continue;
            targetPlaceId = place;
            targetStorageId = longOrNull(item, "id");
            updateRoute();
            return;
        }

        JOptionPane.showMessageDialog(this,
            "Nie znalazłem na mapie: " + search.getText().trim()
                + "\n\nSprawdź, czy pomieszczenie docelowe zostało dodane do mapy.",
            "EDHOME • Mapa", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateRoute() {
        PlaceChoice current = (PlaceChoice) currentCombo.getSelectedItem();
        Long from = current == null ? null : current.id;
        if (targetPlaceId == null) {
            targetTitle.setText("Brak wybranego celu");
            targetInfo.setText("Wyszukaj rzecz, pudełko, narzędzie lub pomieszczenie.");
            routeModel.clear();
            activeRoute = List.of();
            repaintFloorMaps();
            return;
        }

        String targetRoom = roomName(targetPlaceId);
        if (targetStorageId != null) {
            JsonObject item = storageById.get(targetStorageId);
            targetTitle.setText(item == null ? "Cel" : value(item,"name"));
            targetInfo.setText("Lokalizacja:\n" + storagePath(targetStorageId)
                + "\n\nPomieszczenie docelowe: " + targetRoom);
        } else {
            targetTitle.setText(targetRoom);
            targetInfo.setText("Pomieszczenie docelowe:\n" + placePath(targetPlaceId));
        }

        routeModel.clear();
        if (from == null) {
            activeRoute = List.of();
            routeModel.addElement("Wybierz „Jestem tutaj”, aby wyznaczyć trasę.");
            repaintFloorMaps();
            return;
        }

        List<Long> route = shortestPath(from, targetPlaceId);
        activeRoute = route;
        if (route.isEmpty()) {
            routeModel.addElement("Brak zdefiniowanego przejścia do celu.");
            routeModel.addElement("Użyj „Dodaj przejście” i połącz pomieszczenia/drzwi/schody.");
        } else {
            for (int i=0;i<route.size();i++) {
                long placeId = route.get(i);
                RoomLayout room = layouts.get(placeId);
                String prefix = i == 0 ? "1. Start: " : (i+1) + ". ";
                routeModel.addElement(prefix + roomName(placeId)
                    + (room == null ? "" : "  [" + room.floor + "]"));
            }
            if (targetStorageId != null)
                routeModel.addElement((route.size()+1) + ". " + storagePath(targetStorageId));
        }
        repaintFloorMaps();
    }

    private void repaintFloorMaps() {
        floorsPanel.repaint();
    }

    private List<Long> shortestPath(long from, long to) {
        if (from == to) return List.of(from);
        Map<Long,List<Long>> graph = new HashMap<>();
        for (long id : layouts.keySet()) graph.put(id, new ArrayList<>());
        for (Link link : links) {
            if (!graph.containsKey(link.a) || !graph.containsKey(link.b)) continue;
            graph.get(link.a).add(link.b);
            graph.get(link.b).add(link.a);
        }

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Map<Long,Long> prior = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            long at = queue.removeFirst();
            for (long next : graph.getOrDefault(at, List.of())) {
                if (!seen.add(next)) continue;
                prior.put(next, at);
                if (next == to) {
                    LinkedList<Long> path = new LinkedList<>();
                    long cur = to;
                    path.addFirst(cur);
                    while (cur != from) {
                        cur = prior.get(cur);
                        path.addFirst(cur);
                    }
                    return path;
                }
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private void roomClicked(long placeId) {
        if (status.getText().startsWith("Kliknij pierwsze pomieszczenie")) {
            pendingLinkFrom = placeId;
            status.setText("Pierwsze: " + roomName(placeId)
                + ". Teraz kliknij drugie pomieszczenie.");
            return;
        }
        if (pendingLinkFrom != null && status.getText().startsWith("Pierwsze:")) {
            if (!pendingLinkFrom.equals(placeId)) {
                Link link = new Link(pendingLinkFrom, placeId);
                if (!links.contains(link)) links.add(link);
                saveLayout();
                status.setText("Dodano przejście: "
                    + roomName(pendingLinkFrom) + " ↔ " + roomName(placeId));
            }
            pendingLinkFrom = null;
            updateRoute();
            return;
        }

        if (!editMode) {
            targetPlaceId = placeId;
            targetStorageId = null;
            updateRoute();
        }
    }

    private Long effectivePlace(JsonObject item) {
        Long place = longOrNull(item, "place_id");
        if (place != null) return place;
        Set<Long> seen = new HashSet<>();
        Long box = longOrNull(item, "parent_box_id");
        while (box != null && seen.add(box)) {
            JsonObject parent = storageById.get(box);
            if (parent == null) break;
            place = longOrNull(parent, "place_id");
            if (place != null) return place;
            box = longOrNull(parent, "parent_box_id");
        }
        return null;
    }

    private String storagePath(long itemId) {
        JsonObject item = storageById.get(itemId);
        if (item == null) return "Nieznana pozycja";
        LinkedList<String> names = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        JsonObject current = item;
        Long place = null;
        while (current != null) {
            Long id = longOrNull(current, "id");
            if (id != null && !seen.add(id)) break;
            String name = value(current, "name");
            if (!name.isBlank()) names.addFirst(name);
            place = longOrNull(current, "place_id");
            if (place != null) break;
            Long parent = longOrNull(current, "parent_box_id");
            current = parent == null ? null : storageById.get(parent);
        }
        if (place != null) names.addFirst(placePath(place));
        return String.join(" → ", names);
    }

    private String placePath(long placeId) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        Long at = placeId;
        while (at != null && seen.add(at)) {
            JsonObject place = placeById.get(at);
            if (place == null) break;
            String name = value(place,"name");
            if (!name.isBlank()) names.addFirst(name);
            at = longOrNull(place, "parent_id");
        }
        return names.isEmpty() ? roomName(placeId) : String.join(" → ", names);
    }

    private String roomName(long placeId) {
        JsonObject row = placeById.get(placeId);
        String name = row == null ? "" : value(row, "name");
        return name.isBlank() ? "Miejsce #" + placeId : name;
    }

    private void loadLayout() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(
                Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("floors") && root.get("floors").isJsonArray()) {
                List<String> loaded = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("floors")) {
                    String floor = e.getAsString().trim();
                    if (!floor.isBlank() && !loaded.contains(floor)) loaded.add(floor);
                }
                if (!loaded.isEmpty()) {
                    floors.clear();
                    floors.addAll(loaded);
                }
            }
            if (root.has("rooms") && root.get("rooms").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("rooms")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject r = e.getAsJsonObject();
                    long id = r.get("placeId").getAsLong();
                    if (!placeById.containsKey(id)) continue;
                    RoomLayout room = new RoomLayout(id,
                        value(r,"floor"),
                        clamp(r.get("x").getAsInt(),0,1800),
                        clamp(r.get("y").getAsInt(),0,800),
                        clamp(r.get("w").getAsInt(),100,500),
                        clamp(r.get("h").getAsInt(),60,280));
                    if (room.floor.isBlank()) room.floor = "Parter";
                    if (!floors.contains(room.floor)) floors.add(room.floor);
                    layouts.put(id, room);
                }
            }
            if (root.has("links") && root.get("links").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("links")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject l = e.getAsJsonObject();
                    long a = l.get("a").getAsLong();
                    long b = l.get("b").getAsLong();
                    if (a != b && placeById.containsKey(a) && placeById.containsKey(b))
                        links.add(new Link(a,b));
                }
            }
        } catch (Exception ignored) {
            layouts.clear();
            links.clear();
        }
    }

    private void saveLayout() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("format", "edhome-desktop-floor-map");
            root.addProperty("version", 1);
            JsonArray floorArray = new JsonArray();
            for (String floor : floors) floorArray.add(floor);
            root.add("floors", floorArray);

            JsonArray rooms = new JsonArray();
            for (RoomLayout room : layouts.values()) {
                JsonObject r = new JsonObject();
                r.addProperty("placeId", room.placeId);
                r.addProperty("floor", room.floor);
                r.addProperty("x", room.x);
                r.addProperty("y", room.y);
                r.addProperty("w", room.w);
                r.addProperty("h", room.h);
                rooms.add(r);
            }
            root.add("rooms", rooms);

            JsonArray edges = new JsonArray();
            for (Link link : links) {
                JsonObject l = new JsonObject();
                l.addProperty("a", link.a);
                l.addProperty("b", link.b);
                edges.add(l);
            }
            root.add("links", edges);

            Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, FILE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            status.setText("Nie zapisano mapy: " + error.getMessage());
        }
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(SURFACE_2);
        return button;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Long longOrNull(JsonObject row, String key) {
        try {
            JsonElement e = row == null ? null : row.get(key);
            return e == null || e.isJsonNull() ? null : e.getAsLong();
        } catch (Exception ignored) { return null; }
    }

    private static String value(JsonObject row, String key) {
        JsonElement e = row == null ? null : row.get(key);
        if (e == null || e.isJsonNull()) return "";
        try { return e.getAsString(); }
        catch (Exception ignored) { return e.toString(); }
    }

    private final class FloorCanvas extends JPanel {
        private final String floor;
        private Long dragging;
        private Point dragOffset;

        FloorCanvas(String floor) {
            this.floor = floor;
            setLayout(null);
            setOpaque(true);
            setBackground(new Color(24, 31, 40));
            setPreferredSize(new Dimension(900, 225));
            setMinimumSize(new Dimension(500, 225));
            setBorder(BorderFactory.createLineBorder(SURFACE_2, 1));

            for (RoomLayout room : layouts.values()) {
                if (!floor.equals(room.floor)) continue;
                RoomBox box = new RoomBox(room);
                add(box);
                box.setBounds(room.x, room.y, room.w, room.h);
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));

                for (Link link : links) {
                    RoomLayout a = layouts.get(link.a);
                    RoomLayout b = layouts.get(link.b);
                    if (a == null || b == null) continue;
                    if (!floor.equals(a.floor) || !floor.equals(b.floor)) continue;
                    g.setColor(new Color(125, 135, 148));
                    g.drawLine(a.x+a.w/2, a.y+a.h/2,
                        b.x+b.w/2, b.y+b.h/2);
                }

                if (activeRoute.size() > 1) {
                    g.setColor(ROUTE);
                    g.setStroke(new BasicStroke(4f,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{10f,8f}, 0f));
                    for (int i=0;i<activeRoute.size()-1;i++) {
                        RoomLayout a = layouts.get(activeRoute.get(i));
                        RoomLayout b = layouts.get(activeRoute.get(i+1));
                        if (a == null || b == null) continue;
                        if (!floor.equals(a.floor) || !floor.equals(b.floor)) continue;
                        g.drawLine(a.x+a.w/2, a.y+a.h/2,
                            b.x+b.w/2, b.y+b.h/2);
                    }
                }
            } finally {
                g.dispose();
            }
        }

        private final class RoomBox extends JPanel {
            private final RoomLayout room;

            RoomBox(RoomLayout room) {
                this.room = room;
                setLayout(new BorderLayout());
                setOpaque(true);
                setBackground(SURFACE);
                setBorder(BorderFactory.createLineBorder(new Color(210,215,220), 4));

                JLabel name = new JLabel(roomName(room.placeId), SwingConstants.CENTER);
                name.setForeground(TEXT);
                name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
                add(name, BorderLayout.CENTER);

                JLabel contents = new JLabel(roomCount(room.placeId), SwingConstants.CENTER);
                contents.setForeground(MUTED);
                contents.setFont(contents.getFont().deriveFont(11f));
                add(contents, BorderLayout.SOUTH);

                MouseAdapter mouse = new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        if (editMode) {
                            dragging = room.placeId;
                            dragOffset = e.getPoint();
                        }
                    }
                    @Override public void mouseDragged(MouseEvent e) {
                        if (!editMode || dragging == null || dragOffset == null) return;
                        Point parent = SwingUtilities.convertPoint(
                            RoomBox.this, e.getPoint(), FloorCanvas.this);
                        room.x = clamp(parent.x - dragOffset.x, 0,
                            Math.max(0, FloorCanvas.this.getWidth() - room.w));
                        room.y = clamp(parent.y - dragOffset.y, 0,
                            Math.max(0, FloorCanvas.this.getHeight() - room.h));
                        setLocation(room.x, room.y);
                        FloorCanvas.this.repaint();
                    }
                    @Override public void mouseReleased(MouseEvent e) {
                        if (editMode && dragging != null) saveLayout();
                        dragging = null;
                        dragOffset = null;
                    }
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() >= 2 && editMode && editPlace != null) {
                            editPlace.accept(room.placeId);
                            return;
                        }
                        roomClicked(room.placeId);
                    }
                };
                addMouseListener(mouse);
                addMouseMotionListener(mouse);

                PlaceChoice current = (PlaceChoice) currentCombo.getSelectedItem();
                Long currentId = current == null ? null : current.id;
                if (Objects.equals(currentId, room.placeId))
                    setBorder(BorderFactory.createLineBorder(CURRENT, 5));
                if (Objects.equals(targetPlaceId, room.placeId))
                    setBorder(BorderFactory.createLineBorder(TARGET, 5));
            }
        }
    }

    private String roomCount(long placeId) {
        int direct = 0;
        for (JsonObject item : storageById.values()) {
            Long effective = effectivePlace(item);
            if (effective != null && effective == placeId) direct++;
        }
        return direct == 0 ? "" : direct + (direct == 1 ? " element" : " elementów");
    }

    private static final class RoomLayout {
        final long placeId;
        String floor;
        int x, y, w, h;
        RoomLayout(long placeId, String floor, int x, int y, int w, int h) {
            this.placeId=placeId;
            this.floor=floor == null ? "" : floor;
            this.x=x; this.y=y; this.w=w; this.h=h;
        }
    }

    private static final class Link {
        final long a, b;
        Link(long a, long b) {
            this.a = Math.min(a,b);
            this.b = Math.max(a,b);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Link)) return false;
            Link l=(Link)o; return a==l.a && b==l.b;
        }
        @Override public int hashCode() { return Objects.hash(a,b); }
    }

    private static final class PlaceChoice {
        final Long id;
        final String label;
        PlaceChoice(Long id, String label) {
            this.id=id;
            this.label=label == null || label.isBlank()
                ? (id == null ? "—" : "Miejsce #"+id) : label;
        }
        @Override public String toString() { return label; }
    }
}
