package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class DesktopRoomsPanel extends JPanel {
    static final class NodeRef {
        final String type;
        final long id;
        final String name;
        final String kind;
        final String path;

        NodeRef(String type, long id, String name, String kind, String path) {
            this.type = type;
            this.id = id;
            this.name = name == null ? "" : name;
            this.kind = kind == null ? "" : kind;
            this.path = path == null ? "" : path;
        }

        boolean editable() {
            return "place".equals(type) || "storage".equals(type);
        }

        String typeLabel() {
            if ("place".equals(type)) return kind.isBlank() ? "Pomieszczenie / miejsce" : "Miejsce • " + kind;
            if ("storage".equals(type)) {
                if ("box".equals(kind)) return "Pudełko";
                return "Rzecz / narzędzie";
            }
            return "";
        }

        @Override public String toString() {
            if ("root".equals(type)) return name;
            if ("group".equals(type)) return name;
            if ("place".equals(type)) return "⌂  " + name;
            if ("storage".equals(type) && "box".equals(kind)) return "▣  " + name;
            if ("storage".equals(type)) return "•  " + name;
            return name;
        }
    }

    private static final Color BG = new Color(16, 20, 27);
    private static final Color SURFACE = new Color(29, 35, 45);
    private static final Color SURFACE_2 = new Color(37, 44, 56);
    private static final Color TEXT = new Color(239, 243, 247);
    private static final Color MUTED = new Color(164, 174, 188);
    private static final Color ACCENT = new Color(87, 214, 181);

    private final JsonArray places;
    private final JsonArray storage;
    private final Consumer<NodeRef> editAction;
    private final Map<Long, JsonObject> placeById = new HashMap<>();
    private final Map<Long, JsonObject> storageById = new HashMap<>();
    private final Map<Long, ImageIcon> thumbs = new HashMap<>();
    private final List<DefaultMutableTreeNode> searchable = new ArrayList<>();

    private final DefaultMutableTreeNode root =
        new DefaultMutableTreeNode(new NodeRef("root", -1, "Pomieszczenia", "", ""));
    private final JTree tree = new JTree(new DefaultTreeModel(root));
    private final JTextField search = new JTextField();
    private final JLabel title = new JLabel("Wybierz pomieszczenie, pudełko lub rzecz");
    private final JLabel type = new JLabel(" ");
    private final JTextArea path = new JTextArea();
    private final JLabel thumbnail = new JLabel();
    private final JButton edit = new JButton("Edytuj zaznaczone");

    DesktopRoomsPanel(JsonArray places, JsonArray storage, JsonObject settings,
            Consumer<NodeRef> editAction) {
        this.places = places == null ? new JsonArray() : places;
        this.storage = storage == null ? new JsonArray() : storage;
        this.editAction = editAction;

        setLayout(new BorderLayout(12, 12));
        setBackground(BG);

        indexData();
        readThumbnails(settings);
        buildTree();

        add(buildSearchBar(), BorderLayout.NORTH);
        add(buildSplit(), BorderLayout.CENTER);
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

    private void readThumbnails(JsonObject settings) {
        if (settings == null || !settings.has("storageThumbnails")
                || !settings.get("storageThumbnails").isJsonArray()) return;
        for (JsonElement element : settings.getAsJsonArray("storageThumbnails")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            Long itemId = longOrNull(row, "itemId");
            String encoded = value(row, "jpegBase64");
            if (itemId == null || encoded.isBlank() || encoded.length() > 50000) continue;
            try {
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(encoded); }
                catch (IllegalArgumentException plainFailed) {
                    bytes = Base64.getMimeDecoder().decode(encoded);
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) continue;
                Image scaled = image.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
                thumbs.put(itemId, new ImageIcon(scaled));
            } catch (Exception ignored) { }
        }
    }

    private JComponent buildSearchBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);

        JLabel label = new JLabel("Gdzie jest:");
        label.setForeground(TEXT);
        bar.add(label, BorderLayout.WEST);

        search.setToolTipText("Wpisz nazwę rzeczy, narzędzia, pudełka lub pomieszczenia");
        search.addActionListener(e -> findFirst());
        bar.add(search, BorderLayout.CENTER);

        JButton find = button("Szukaj");
        find.addActionListener(e -> findFirst());
        bar.add(find, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildSplit() {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(SURFACE);
        tree.setForeground(TEXT);
        tree.setRowHeight(25);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree,
                    Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus);
                label.setOpaque(true);
                label.setBackground(selected ? SURFACE_2 : SURFACE);
                label.setForeground(TEXT);
                label.setBorder(new EmptyBorder(2, 4, 2, 4));
                setOpenIcon(null); setClosedIcon(null); setLeafIcon(null);
                return label;
            }
        });
        tree.addTreeSelectionListener(this::selectionChanged);

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createLineBorder(SURFACE_2));
        treeScroll.getViewport().setBackground(SURFACE);

        JPanel details = new JPanel(new BorderLayout(10, 10));
        details.setBackground(SURFACE);
        details.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
        type.setForeground(ACCENT);
        heading.add(title);
        heading.add(Box.createVerticalStrut(5));
        heading.add(type);
        details.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);

        thumbnail.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnail.setVerticalAlignment(SwingConstants.TOP);
        thumbnail.setPreferredSize(new Dimension(0, 0));
        body.add(thumbnail, BorderLayout.WEST);

        path.setEditable(false);
        path.setLineWrap(true);
        path.setWrapStyleWord(true);
        path.setOpaque(false);
        path.setForeground(TEXT);
        path.setFont(path.getFont().deriveFont(15f));
        path.setText("Kliknij element w drzewie, aby zobaczyć pełną ścieżkę lokalizacji.");
        body.add(path, BorderLayout.CENTER);
        details.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        edit.setEnabled(false);
        edit.addActionListener(e -> {
            NodeRef selected = selectedRef();
            if (selected != null && selected.editable() && editAction != null)
                editAction.accept(selected);
        });
        actions.add(edit);
        details.add(actions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            treeScroll, details);
        split.setResizeWeight(0.42);
        split.setDividerLocation(430);
        split.setBorder(null);
        split.setBackground(BG);
        return split;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(SURFACE_2);
        return button;
    }

    private void buildTree() {
        searchable.clear();
        root.removeAllChildren();
        Set<Long> visitedPlaces = new HashSet<>();
        Set<Long> visitedStorage = new HashSet<>();

        List<JsonObject> roots = new ArrayList<>();
        for (JsonObject place : placeById.values()) {
            Long parent = longOrNull(place, "parent_id");
            if (parent == null || !placeById.containsKey(parent)) roots.add(place);
        }
        roots.sort((a,b) -> value(a,"name").compareToIgnoreCase(value(b,"name")));
        for (JsonObject place : roots)
            appendPlace(place, root, "", visitedPlaces, visitedStorage);

        DefaultMutableTreeNode unassigned = new DefaultMutableTreeNode(
            new NodeRef("group", -1, "Bez przypisanego miejsca", "", ""));
        for (JsonObject item : storageById.values()) {
            Long id = longOrNull(item, "id");
            if (id == null || visitedStorage.contains(id)) continue;
            Long parentBox = longOrNull(item, "parent_box_id");
            Long placeId = longOrNull(item, "place_id");
            boolean orphan = (parentBox == null || !storageById.containsKey(parentBox))
                && (placeId == null || !placeById.containsKey(placeId));
            if (orphan) appendStorage(item, unassigned,
                "Bez przypisanego miejsca", visitedStorage);
        }
        if (unassigned.getChildCount() > 0) root.add(unassigned);

        ((DefaultTreeModel) tree.getModel()).reload();
        for (int row = 0; row < Math.min(tree.getRowCount(), 8); row++)
            tree.expandRow(row);
    }

    private void appendPlace(JsonObject place, DefaultMutableTreeNode parent,
            String parentPath, Set<Long> visitedPlaces, Set<Long> visitedStorage) {
        Long id = longOrNull(place, "id");
        if (id == null || !visitedPlaces.add(id)) return;
        String name = value(place, "name");
        if (name.isBlank()) name = "Miejsce #" + id;
        String path = parentPath.isBlank() ? name : parentPath + " → " + name;
        NodeRef ref = new NodeRef("place", id, name, value(place,"kind"), path);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(ref);
        parent.add(node);
        searchable.add(node);

        List<JsonObject> childPlaces = new ArrayList<>();
        for (JsonObject candidate : placeById.values()) {
            Long parentId = longOrNull(candidate, "parent_id");
            if (parentId != null && parentId.equals(id)) childPlaces.add(candidate);
        }
        childPlaces.sort((a,b) -> value(a,"name").compareToIgnoreCase(value(b,"name")));
        for (JsonObject child : childPlaces)
            appendPlace(child, node, path, visitedPlaces, visitedStorage);

        List<JsonObject> directItems = new ArrayList<>();
        for (JsonObject item : storageById.values()) {
            Long placeId = longOrNull(item, "place_id");
            Long parentBox = longOrNull(item, "parent_box_id");
            if (placeId != null && placeId.equals(id) && parentBox == null)
                directItems.add(item);
        }
        directItems.sort((a,b) -> {
            boolean abox = "box".equals(value(a,"kind"));
            boolean bbox = "box".equals(value(b,"kind"));
            if (abox != bbox) return abox ? -1 : 1;
            return value(a,"name").compareToIgnoreCase(value(b,"name"));
        });
        for (JsonObject item : directItems)
            appendStorage(item, node, path, visitedStorage);
    }

    private void appendStorage(JsonObject item, DefaultMutableTreeNode parent,
            String parentPath, Set<Long> visitedStorage) {
        Long id = longOrNull(item, "id");
        if (id == null || !visitedStorage.add(id)) return;
        String name = value(item, "name");
        if (name.isBlank()) name = "Pozycja #" + id;
        String path = parentPath.isBlank() ? name : parentPath + " → " + name;
        NodeRef ref = new NodeRef("storage", id, name, value(item,"kind"), path);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(ref);
        parent.add(node);
        searchable.add(node);

        List<JsonObject> children = new ArrayList<>();
        for (JsonObject candidate : storageById.values()) {
            Long parentBox = longOrNull(candidate, "parent_box_id");
            if (parentBox != null && parentBox.equals(id)) children.add(candidate);
        }
        children.sort((a,b) -> value(a,"name").compareToIgnoreCase(value(b,"name")));
        for (JsonObject child : children)
            appendStorage(child, node, path, visitedStorage);
    }

    private void selectionChanged(TreeSelectionEvent event) {
        NodeRef ref = selectedRef();
        if (ref == null || "group".equals(ref.type) || "root".equals(ref.type)) {
            title.setText(ref == null ? "Wybierz element" : ref.name);
            type.setText(" ");
            path.setText(ref == null
                ? "Kliknij element w drzewie, aby zobaczyć pełną ścieżkę lokalizacji."
                : "Ta pozycja grupuje elementy bez konkretnej lokalizacji.");
            thumbnail.setIcon(null);
            thumbnail.setPreferredSize(new Dimension(0,0));
            edit.setEnabled(false);
            return;
        }

        title.setText(ref.name);
        type.setText(ref.typeLabel());
        path.setText("Pełna lokalizacja:\n" + ref.path);
        edit.setEnabled(ref.editable());

        if ("storage".equals(ref.type) && thumbs.containsKey(ref.id)) {
            thumbnail.setIcon(thumbs.get(ref.id));
            thumbnail.setPreferredSize(new Dimension(108, 108));
        } else {
            thumbnail.setIcon(null);
            thumbnail.setPreferredSize(new Dimension(0, 0));
        }
        revalidate();
        repaint();
    }

    private NodeRef selectedRef() {
        Object selected = tree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode)) return null;
        Object user = ((DefaultMutableTreeNode) selected).getUserObject();
        return user instanceof NodeRef ? (NodeRef) user : null;
    }

    private void findFirst() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return;
        for (DefaultMutableTreeNode node : searchable) {
            Object user = node.getUserObject();
            if (!(user instanceof NodeRef)) continue;
            NodeRef ref = (NodeRef) user;
            String haystack = (ref.name + " " + ref.path + " " + ref.kind)
                .toLowerCase(Locale.ROOT);
            if (!haystack.contains(query)) continue;
            TreePath target = new TreePath(node.getPath());
            tree.expandPath(target.getParentPath());
            tree.setSelectionPath(target);
            tree.scrollPathToVisible(target);
            return;
        }
        JOptionPane.showMessageDialog(this,
            "Nie znaleziono: " + search.getText().trim(),
            "EDHOME • Pomieszczenia", JOptionPane.INFORMATION_MESSAGE);
    }

    private static Long longOrNull(JsonObject row, String key) {
        try {
            JsonElement element = row.get(key);
            if (element == null || element.isJsonNull()) return null;
            return element.getAsLong();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String value(JsonObject row, String key) {
        JsonElement element = row == null ? null : row.get(key);
        if (element == null || element.isJsonNull()) return "";
        try { return element.getAsString(); }
        catch (Exception ignored) { return element.toString(); }
    }
}
