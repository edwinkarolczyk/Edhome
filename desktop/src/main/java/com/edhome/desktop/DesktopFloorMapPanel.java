package com.edhome.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * EDHOME Desktop • Plan domu / posesji.
 *
 * Layout follows the accepted desktop mockup: structure tree on the left,
 * CAD-like working canvas in the center and object properties on the right.
 * Geometry remains Desktop-local for now; place/storage identity comes from
 * synchronized EDHOME data. Existing floor-map.json v1 is read without loss.
 */
final class DesktopFloorMapPanel extends JPanel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of(System.getProperty("user.home"),
        ".edhome", "floor-map.json");
    private static final Path ASSET_DIR = Path.of(System.getProperty("user.home"),
        ".edhome", "floor-map-assets");
    private static final Path JPG_BACKGROUND = ASSET_DIR.resolve("posesja-background.jpg");

    private static final Color BG = new Color(13, 18, 25);
    private static final Color PANEL = new Color(20, 27, 36);
    private static final Color PANEL_2 = new Color(28, 37, 49);
    private static final Color PANEL_3 = new Color(35, 46, 59);
    private static final Color TEXT = new Color(239, 244, 248);
    private static final Color MUTED = new Color(159, 171, 184);
    private static final Color ACCENT = new Color(53, 163, 255);
    private static final Color GREEN = new Color(64, 211, 137);
    private static final Color BORDER = new Color(56, 70, 86);
    private static final Color GRID = new Color(48, 62, 78);
    private static final Color ROOM_FILL = new Color(34, 47, 61, 218);
    private static final Color ROOM_BORDER = new Color(92, 190, 255);
    private static final Color SELECTED = new Color(51, 174, 255);
    private static final Color ROUTE = new Color(71, 157, 255);
    private static final Color CURRENT = new Color(57, 196, 118);
    private static final Color TARGET = new Color(255, 121, 121);

    private final JsonArray places;
    private final JsonArray storage;
    private final Consumer<Long> editPlace;
    private final Map<Long,JsonObject> placeById = new LinkedHashMap<>();
    private final Map<Long,JsonObject> storageById = new LinkedHashMap<>();
    private final Map<Long,RoomLayout> layouts = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final List<String> floors = new ArrayList<>(
        List.of("Podwórko", "Parter", "Piętro", "Piwnica"));

    private final JPanel floorTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
    private final JPanel canvasHolder = new JPanel(new BorderLayout());
    private final JTree structureTree = new JTree();
    private final JPanel propertiesBody = new JPanel();
    private final JLabel propertiesTitle = new JLabel("Właściwości");
    private final JLabel selectionName = new JLabel("Nic nie zaznaczono");
    private final JLabel selectionPath = new JLabel("Pełna lokalizacja: —");
    private final JLabel status = new JLabel("Gotowe.");
    private final JTextField search = new JTextField();
    private final JComboBox<PlaceChoice> currentCombo = new JComboBox<>();
    private final JButton editPlanButton = toolButton("✎ Edytuj plan");
    private final JButton gridButton = toolButton("▦ Siatka: WŁ.");
    private final JButton snapButton = toolButton("⌁ Snap: WŁ.");

    private FloorCanvas currentCanvas;
    private String activeFloor = "Parter";
    private boolean editMode;
    private boolean gridEnabled = true;
    private boolean snapEnabled = true;
    private int gridPx = 20;
    private double propertyWidthMeters = 28.0;
    private double propertyHeightMeters = 20.0;
    private Long selectedPlaceId;
    private Long selectedStorageId;
    private Long targetPlaceId;
    private Long targetStorageId;
    private Long pendingLinkFrom;
    private List<Long> activeRoute = List.of();
    private BufferedImage backgroundImage;
    private String backgroundSourceName = "";
    private String dxfSourceName = "";

    DesktopFloorMapPanel(JsonArray places, JsonArray storage,
            Consumer<Long> editPlace) {
        this.places = places == null ? new JsonArray() : places;
        this.storage = storage == null ? new JsonArray() : storage;
        this.editPlace = editPlace;

        indexData();
        loadLayout();
        ensureDefaultRooms();
        loadBackground();

        setLayout(new BorderLayout(10,10));
        setBackground(BG);
        setBorder(new EmptyBorder(4,4,4,4));

        add(buildTop(), BorderLayout.NORTH);
        add(buildMain(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        rebuildStructureTree();
        refreshCurrentChoices();
        rebuildFloorTabs();
        showFloor(activeFloor);
        refreshProperties();
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

    private JComponent buildTop() {
        JPanel wrapper = new JPanel(new BorderLayout(0,8));
        wrapper.setOpaque(false);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));
        tools.setOpaque(false);

        JButton jpg = toolButton("▧ Import JPG");
        JButton dxf = toolButton("▣ Import DXF");
        JButton size = toolButton("⌁ Rozmiar posesji");
        JButton building = toolButton("⌂ Dodaj budynek");
        JButton room = toolButton("▢ Dodaj pomieszczenie");
        JButton zone = toolButton("▧ Dodaj strefę");

        jpg.addActionListener(e -> importJpg());
        dxf.addActionListener(e -> importDxfShell());
        size.addActionListener(e -> choosePropertySize());
        building.addActionListener(e -> addExistingPlaceToMap("budynek"));
        room.addActionListener(e -> addExistingPlaceToMap("pomieszczenie"));
        zone.addActionListener(e -> addExistingPlaceToMap("strefa"));

        tools.add(jpg);
        tools.add(dxf);
        tools.add(size);
        tools.add(separator());
        tools.add(building);
        tools.add(room);
        tools.add(zone);
        tools.add(separator());
        tools.add(editPlanButton);
        tools.add(gridButton);
        tools.add(snapButton);

        editPlanButton.addActionListener(e -> {
            editMode = !editMode;
            editPlanButton.setText(editMode ? "✓ Edycja planu: WŁ." : "✎ Edytuj plan");
            status.setText(editMode
                ? "Tryb edycji: przeciągaj pomieszczenia. Snap działa wg siatki."
                : "Plan zablokowany. Zaznacz obiekt, aby zobaczyć właściwości.");
            rebuildCanvas();
        });
        gridButton.addActionListener(e -> {
            gridEnabled = !gridEnabled;
            gridButton.setText(gridEnabled ? "▦ Siatka: WŁ." : "▦ Siatka: WYŁ.");
            if (currentCanvas != null) currentCanvas.repaint();
            saveLayout();
        });
        snapButton.addActionListener(e -> {
            snapEnabled = !snapEnabled;
            snapButton.setText(snapEnabled ? "⌁ Snap: WŁ." : "⌁ Snap: WYŁ.");
            saveLayout();
        });

        JPanel levels = new JPanel(new BorderLayout());
        levels.setOpaque(false);
        floorTabs.setOpaque(false);
        levels.add(floorTabs, BorderLayout.WEST);

        wrapper.add(tools, BorderLayout.NORTH);
        wrapper.add(levels, BorderLayout.SOUTH);
        return wrapper;
    }

    private JComponent buildMain() {
        JPanel left = buildStructurePanel();
        JPanel right = buildPropertiesPanel();

        canvasHolder.setBackground(BG);
        canvasHolder.setBorder(BorderFactory.createLineBorder(BORDER));

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            left, canvasHolder);
        leftSplit.setResizeWeight(0.0);
        leftSplit.setDividerLocation(275);
        leftSplit.setDividerSize(7);
        leftSplit.setBorder(null);
        leftSplit.setBackground(BG);

        JSplitPane full = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            leftSplit, right);
        full.setResizeWeight(1.0);
        full.setDividerLocation(1120);
        full.setDividerSize(7);
        full.setBorder(null);
        full.setBackground(BG);
        return full;
    }

    private JPanel buildStructurePanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BorderLayout(0,8));
        panel.setPreferredSize(new Dimension(270, 1));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("⚭  Struktura");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
        JButton plus = compactButton("+");
        plus.addActionListener(e -> addExistingPlaceToMap("pomieszczenie"));
        head.add(title, BorderLayout.WEST);
        head.add(plus, BorderLayout.EAST);

        structureTree.setRootVisible(true);
        structureTree.setShowsRootHandles(true);
        structureTree.setBackground(PANEL);
        structureTree.setForeground(TEXT);
        structureTree.setRowHeight(28);
        structureTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel label=(JLabel)super.getTreeCellRendererComponent(
                    tree,value,selected,expanded,leaf,row,hasFocus);
                label.setOpaque(true);
                label.setBackground(selected ? new Color(18,72,112) : PANEL);
                label.setForeground(TEXT);
                label.setBorder(new EmptyBorder(2,4,2,4));
                label.setIcon(null);
                return label;
            }
        });
        structureTree.addTreeSelectionListener(e -> treeSelectionChanged());

        JScrollPane scroll = new JScrollPane(structureTree);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);

        panel.add(head, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPropertiesPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BorderLayout(0,10));
        panel.setPreferredSize(new Dimension(290,1));

        propertiesTitle.setForeground(TEXT);
        propertiesTitle.setFont(propertiesTitle.getFont().deriveFont(Font.BOLD,17f));
        selectionName.setForeground(ACCENT);
        selectionName.setFont(selectionName.getFont().deriveFont(Font.BOLD,17f));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(propertiesTitle);
        head.add(Box.createVerticalStrut(8));
        head.add(selectionName);

        propertiesBody.setOpaque(false);
        propertiesBody.setLayout(new BoxLayout(propertiesBody, BoxLayout.Y_AXIS));

        JScrollPane bodyScroll = new JScrollPane(propertiesBody);
        bodyScroll.setBorder(null);
        bodyScroll.getViewport().setBackground(PANEL);

        JPanel actions = new JPanel(new GridLayout(2,2,7,7));
        actions.setOpaque(false);
        JButton edit = toolButton("✎ Edytuj");
        JButton move = toolButton("✥ Przenieś");
        JButton shelf = toolButton("▤ Dodaj regał");
        JButton box = toolButton("□ Dodaj pudełko");
        edit.addActionListener(e -> {
            if (selectedPlaceId != null && editPlace != null) editPlace.accept(selectedPlaceId);
            else JOptionPane.showMessageDialog(this,
                "Edycja rzeczy/pudełka jest dostępna w Pomieszczenia lub Magazyn.");
        });
        move.addActionListener(e -> {
            editMode = true;
            editPlanButton.setText("✓ Edycja planu: WŁ.");
            status.setText("Przeciągnij zaznaczone pomieszczenie na planie.");
            rebuildCanvas();
        });
        shelf.addActionListener(e -> showStructureHint("regał"));
        box.addActionListener(e -> showStructureHint("pudełko"));
        actions.add(edit); actions.add(move); actions.add(shelf); actions.add(box);

        JPanel south = new JPanel(new BorderLayout(0,8));
        south.setOpaque(false);
        selectionPath.setForeground(MUTED);
        selectionPath.setFont(selectionPath.getFont().deriveFont(11f));
        south.add(selectionPath, BorderLayout.NORTH);
        south.add(actions, BorderLayout.CENTER);

        panel.add(head, BorderLayout.NORTH);
        panel.add(bodyScroll, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildBottom() {
        JPanel bar = new JPanel(new BorderLayout(12,0));
        bar.setBackground(PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(7,10,7,10)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));
        left.setOpaque(false);
        JLabel currentLabel = new JLabel("Jestem tutaj:");
        currentLabel.setForeground(MUTED);
        left.add(currentLabel);
        currentCombo.setPreferredSize(new Dimension(190,28));
        currentCombo.addActionListener(e -> updateRoute());
        left.add(currentCombo);

        JButton link = compactButton("＋ Dodaj przejście");
        link.addActionListener(e -> {
            pendingLinkFrom = null;
            status.setText("Dodaj przejście: kliknij pierwsze, potem drugie pomieszczenie.");
        });
        left.add(link);

        JPanel center = new JPanel(new BorderLayout(6,0));
        center.setOpaque(false);
        JLabel where = new JLabel("Gdzie jest:");
        where.setForeground(MUTED);
        search.setPreferredSize(new Dimension(230,28));
        search.addActionListener(e -> findTarget());
        JButton find = compactButton("Szukaj");
        find.addActionListener(e -> findTarget());
        center.add(where, BorderLayout.WEST);
        center.add(search, BorderLayout.CENTER);
        center.add(find, BorderLayout.EAST);

        status.setForeground(MUTED);
        status.setHorizontalAlignment(SwingConstants.RIGHT);

        bar.add(left, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(status, BorderLayout.EAST);
        return bar;
    }

    private void rebuildFloorTabs() {
        floorTabs.removeAll();
        for (String floor : floors) {
            JButton b = toolButton(floor);
            if (floor.equals(activeFloor)) {
                b.setBackground(new Color(18,89,150));
                b.setBorder(BorderFactory.createLineBorder(ACCENT));
            }
            b.addActionListener(e -> showFloor(floor));
            floorTabs.add(b);
        }
        floorTabs.revalidate();
        floorTabs.repaint();
    }

    private void showFloor(String floor) {
        activeFloor = floors.contains(floor) ? floor : "Parter";
        rebuildFloorTabs();
        rebuildCanvas();
    }

    private void rebuildCanvas() {
        canvasHolder.removeAll();
        currentCanvas = new FloorCanvas(activeFloor);
        JScrollPane scroll = new JScrollPane(currentCanvas);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        canvasHolder.add(scroll, BorderLayout.CENTER);
        canvasHolder.revalidate();
        canvasHolder.repaint();
    }

    private void rebuildStructureTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            new TreeRef("root",null,"⌂ Posesja"));
        Map<Long,DefaultMutableTreeNode> nodes = new LinkedHashMap<>();

        List<JsonObject> placeRows = new ArrayList<>(placeById.values());
        placeRows.sort(Comparator.comparing(p -> value(p,"name"), String.CASE_INSENSITIVE_ORDER));
        for (JsonObject place : placeRows) {
            Long id = longOrNull(place,"id");
            if (id == null) continue;
            nodes.put(id,new DefaultMutableTreeNode(new TreeRef(
                "place",id,placeIcon(place)+" "+safeName(place))));
        }
        for (JsonObject place : placeRows) {
            Long id=longOrNull(place,"id");
            if (id==null) continue;
            Long parent=longOrNull(place,"parent_id");
            DefaultMutableTreeNode node=nodes.get(id);
            DefaultMutableTreeNode parentNode=parent==null?null:nodes.get(parent);
            (parentNode==null?root:parentNode).add(node);
        }

        for (JsonObject item : storageById.values()) {
            Long id=longOrNull(item,"id");
            if (id==null) continue;
            Long placeId=effectivePlace(item);
            DefaultMutableTreeNode parent=placeId==null?root:nodes.get(placeId);
            if (parent==null) parent=root;
            parent.add(new DefaultMutableTreeNode(new TreeRef(
                "storage",id,storageIcon(item)+" "+safeName(item))));
        }

        structureTree.setModel(new javax.swing.tree.DefaultTreeModel(root));
        for (int i=0;i<Math.min(20,structureTree.getRowCount());i++)
            structureTree.expandRow(i);
    }

    private void treeSelectionChanged() {
        TreePath path=structureTree.getSelectionPath();
        if (path==null) return;
        Object last=path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) return;
        Object obj=((DefaultMutableTreeNode)last).getUserObject();
        if (!(obj instanceof TreeRef)) return;
        TreeRef ref=(TreeRef)obj;
        selectedPlaceId=null; selectedStorageId=null;
        if ("place".equals(ref.kind)) {
            selectedPlaceId=ref.id;
            RoomLayout room=layouts.get(ref.id);
            if (room!=null && !room.floor.equals(activeFloor)) showFloor(room.floor);
        } else if ("storage".equals(ref.kind)) {
            selectedStorageId=ref.id;
            JsonObject item=storageById.get(ref.id);
            Long place=effectivePlace(item);
            if (place!=null) {
                selectedPlaceId=place;
                RoomLayout room=layouts.get(place);
                if (room!=null && !room.floor.equals(activeFloor)) showFloor(room.floor);
            }
        }
        refreshProperties();
        if (currentCanvas!=null) currentCanvas.repaint();
    }

    private void refreshProperties() {
        propertiesBody.removeAll();

        if (selectedStorageId != null) {
            JsonObject row=storageById.get(selectedStorageId);
            selectionName.setText(safeName(row));
            addProperty("Typ", value(row,"kind").isBlank()?"rzecz / pudełko":value(row,"kind"));
            addProperty("Pomieszczenie", selectedPlaceId==null?"—":roomName(selectedPlaceId));
            addProperty("Lokalizacja", storagePath(selectedStorageId));
            selectionPath.setText("Pełna lokalizacja: "+storagePath(selectedStorageId));
        } else if (selectedPlaceId != null) {
            JsonObject row=placeById.get(selectedPlaceId);
            RoomLayout room=layouts.get(selectedPlaceId);
            selectionName.setText(safeName(row));
            addProperty("Typ", value(row,"kind").isBlank()?"miejsce":value(row,"kind"));
            addProperty("Poziom", room==null?"Nie dodano do mapy":room.floor);
            addProperty("Elementy", Integer.toString(roomItemCount(selectedPlaceId)));
            if (room!=null) {
                addProperty("Pozycja", room.x+" × "+room.y+" px");
                addProperty("Rozmiar", room.w+" × "+room.h+" px");
            }
            selectionPath.setText("Pełna lokalizacja: "+placePath(selectedPlaceId));
        } else {
            selectionName.setText("Nic nie zaznaczono");
            addProperty("Plan", "Wybierz element z drzewa lub kliknij na mapie.");
            addProperty("Obszar posesji",
                formatMeters(propertyWidthMeters)+" × "+formatMeters(propertyHeightMeters)+" m");
            if (!backgroundSourceName.isBlank())
                addProperty("Podkład JPG", backgroundSourceName);
            if (!dxfSourceName.isBlank())
                addProperty("DXF", dxfSourceName+" (slot importu)");
            selectionPath.setText("Pełna lokalizacja: —");
        }

        propertiesBody.revalidate();
        propertiesBody.repaint();
    }

    private void addProperty(String label, String value) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row,BoxLayout.Y_AXIS));
        row.setBorder(new EmptyBorder(5,0,6,0));
        JLabel key = new JLabel(label);
        key.setForeground(MUTED);
        key.setFont(key.getFont().deriveFont(11f));
        JLabel val = new JLabel("<html>"+html(value)+"</html>");
        val.setForeground(TEXT);
        val.setFont(val.getFont().deriveFont(13f));
        row.add(key);
        row.add(Box.createVerticalStrut(3));
        row.add(val);
        propertiesBody.add(row);
        propertiesBody.add(new JSeparator());
    }

    private void importJpg() {
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Wybierz podkład JPG/JPEG planu");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Obraz JPG/JPEG","jpg","jpeg"));
        if (chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        Path src=chooser.getSelectedFile().toPath();
        try {
            BufferedImage img=ImageIO.read(src.toFile());
            if (img==null) throw new IOException("Plik nie jest poprawnym JPG.");
            Files.createDirectories(ASSET_DIR);
            Files.copy(src,JPG_BACKGROUND,StandardCopyOption.REPLACE_EXISTING);
            backgroundImage=img;
            backgroundSourceName=src.getFileName().toString();
            saveLayout();
            refreshProperties();
            rebuildCanvas();
            status.setText("Podkład JPG wczytany: "+backgroundSourceName);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,
                "Nie udało się wczytać JPG:\n"+rootMessage(error),
                "EDHOME • Plan posesji",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importDxfShell() {
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Wybierz DXF jako szablon planu");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "AutoCAD DXF","dxf"));
        if (chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        Path src=chooser.getSelectedFile().toPath();
        dxfSourceName=src.getFileName().toString();
        saveLayout();
        refreshProperties();
        status.setText("DXF wybrany: "+dxfSourceName+" • parser geometrii będzie kolejnym krokiem.");
        JOptionPane.showMessageDialog(this,
            "DXF został przypięty do planu jako źródło szablonu.\n"
                +"W tej wersji nie rysuję jeszcze jego geometrii — interfejs i zapis źródła są gotowe.",
            "EDHOME • Import DXF",JOptionPane.INFORMATION_MESSAGE);
    }

    private void choosePropertySize() {
        JTextField w=new JTextField(formatMeters(propertyWidthMeters));
        JTextField h=new JTextField(formatMeters(propertyHeightMeters));
        JPanel form=new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Szerokość posesji [m]:")); form.add(w);
        form.add(new JLabel("Wysokość posesji [m]:")); form.add(h);
        int ok=JOptionPane.showConfirmDialog(this,form,
            "Rozmiar posesji",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if (ok!=JOptionPane.OK_OPTION) return;
        try {
            double width=parseMeters(w.getText());
            double height=parseMeters(h.getText());
            if (width<2 || height<2 || width>500 || height>500)
                throw new IllegalArgumentException("Zakres: 2–500 m.");
            propertyWidthMeters=width;
            propertyHeightMeters=height;
            saveLayout();
            refreshProperties();
            rebuildCanvas();
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this,rootMessage(error));
        }
    }

    private void addExistingPlaceToMap(String requestedKind) {
        List<PlaceChoice> choices=new ArrayList<>();
        for (JsonObject place:placeById.values()) {
            Long id=longOrNull(place,"id");
            if (id==null || layouts.containsKey(id)) continue;
            choices.add(new PlaceChoice(id,safeName(place)));
        }
        choices.sort(Comparator.comparing(c->c.label,String.CASE_INSENSITIVE_ORDER));
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Brak wolnych rekordów Miejsca do dodania na plan.\n"
                    +"Nowe "+requestedKind+" utwórz w module Miejsca, a potem wróć tutaj.");
            return;
        }
        JComboBox<PlaceChoice> place=new JComboBox<>(choices.toArray(new PlaceChoice[0]));
        JComboBox<String> floor=new JComboBox<>(floors.toArray(new String[0]));
        floor.setSelectedItem(activeFloor);
        JPanel form=new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Miejsce:")); form.add(place);
        form.add(new JLabel("Poziom:")); form.add(floor);
        int ok=JOptionPane.showConfirmDialog(this,form,
            "Dodaj "+requestedKind+" do planu",
            JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if (ok!=JOptionPane.OK_OPTION) return;
        PlaceChoice selected=(PlaceChoice)place.getSelectedItem();
        String selectedFloor=String.valueOf(floor.getSelectedItem());
        if (selected==null || selected.id==null) return;

        int count=(int)layouts.values().stream()
            .filter(r->selectedFloor.equals(r.floor)).count();
        int x=60+(count%4)*180;
        int y=80+(count/4)*130;
        layouts.put(selected.id,new RoomLayout(selected.id,selectedFloor,x,y,160,105));
        selectedPlaceId=selected.id;
        activeFloor=selectedFloor;
        saveLayout();
        refreshCurrentChoices();
        rebuildStructureTree();
        rebuildFloorTabs();
        rebuildCanvas();
        refreshProperties();
    }

    private void showStructureHint(String kind) {
        JOptionPane.showMessageDialog(this,
            "Docelowo „Dodaj "+kind+"” będzie tworzyć element bezpośrednio z mapy.\n"
                +"Na tym etapie zachowuję jedną bazę danych: dodaj "+kind
                +" w Magazyn/Pomieszczenia i przypisz go do zaznaczonego miejsca.",
            "EDHOME • Plan posesji",JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshCurrentChoices() {
        Long selected=null;
        Object old=currentCombo.getSelectedItem();
        if (old instanceof PlaceChoice) selected=((PlaceChoice)old).id;
        currentCombo.removeAllItems();
        currentCombo.addItem(new PlaceChoice(null,"Wybierz pomieszczenie"));
        layouts.values().stream()
            .sorted(Comparator.comparing(r->roomName(r.placeId),String.CASE_INSENSITIVE_ORDER))
            .forEach(r->currentCombo.addItem(new PlaceChoice(r.placeId,roomName(r.placeId))));
        if (selected!=null) {
            for (int i=0;i<currentCombo.getItemCount();i++) {
                PlaceChoice p=currentCombo.getItemAt(i);
                if (Objects.equals(selected,p.id)) {
                    currentCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void findTarget() {
        String q=search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) return;
        for (RoomLayout room:layouts.values()) {
            if (roomName(room.placeId).toLowerCase(Locale.ROOT).contains(q)) {
                targetPlaceId=room.placeId;
                targetStorageId=null;
                selectedPlaceId=room.placeId;
                selectedStorageId=null;
                showFloor(room.floor);
                refreshProperties();
                updateRoute();
                return;
            }
        }
        for (JsonObject item:storageById.values()) {
            String n=safeName(item);
            if (!n.toLowerCase(Locale.ROOT).contains(q)) continue;
            Long place=effectivePlace(item);
            if (place==null || !layouts.containsKey(place)) continue;
            targetPlaceId=place;
            targetStorageId=longOrNull(item,"id");
            selectedPlaceId=place;
            selectedStorageId=targetStorageId;
            showFloor(layouts.get(place).floor);
            refreshProperties();
            updateRoute();
            return;
        }
        JOptionPane.showMessageDialog(this,"Nie znalazłem na mapie: "+search.getText().trim());
    }

    private void updateRoute() {
        PlaceChoice current=(PlaceChoice)currentCombo.getSelectedItem();
        Long from=current==null?null:current.id;
        if (targetPlaceId==null || from==null) {
            activeRoute=List.of();
            if (currentCanvas!=null) currentCanvas.repaint();
            return;
        }
        activeRoute=shortestPath(from,targetPlaceId);
        if (activeRoute.isEmpty()) {
            status.setText("Brak przejścia do celu. Użyj „Dodaj przejście”.");
        } else {
            status.setText("Trasa: "+activeRoute.size()+" punktów → "+roomName(targetPlaceId));
        }
        if (currentCanvas!=null) currentCanvas.repaint();
    }

    private List<Long> shortestPath(long from,long to) {
        if (from==to) return List.of(from);
        Map<Long,List<Long>> graph=new HashMap<>();
        for (long id:layouts.keySet()) graph.put(id,new ArrayList<>());
        for (Link link:links) {
            if (!graph.containsKey(link.a) || !graph.containsKey(link.b)) continue;
            graph.get(link.a).add(link.b);
            graph.get(link.b).add(link.a);
        }
        ArrayDeque<Long> queue=new ArrayDeque<>();
        Map<Long,Long> prior=new HashMap<>();
        Set<Long> seen=new HashSet<>();
        queue.add(from); seen.add(from);
        while(!queue.isEmpty()) {
            long at=queue.removeFirst();
            for (long next:graph.getOrDefault(at,List.of())) {
                if (!seen.add(next)) continue;
                prior.put(next,at);
                if (next==to) {
                    LinkedList<Long> path=new LinkedList<>();
                    long cur=to;
                    path.addFirst(cur);
                    while(cur!=from) {
                        cur=prior.get(cur);
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
        if (status.getText().startsWith("Dodaj przejście:")) {
            pendingLinkFrom=placeId;
            status.setText("Pierwsze: "+roomName(placeId)+". Kliknij drugie pomieszczenie.");
            return;
        }
        if (pendingLinkFrom!=null && status.getText().startsWith("Pierwsze:")) {
            if (!pendingLinkFrom.equals(placeId)) {
                Link link=new Link(pendingLinkFrom,placeId);
                if (!links.contains(link)) links.add(link);
                saveLayout();
                status.setText("Dodano przejście: "+roomName(pendingLinkFrom)
                    +" ↔ "+roomName(placeId));
            }
            pendingLinkFrom=null;
            updateRoute();
            return;
        }
        selectedPlaceId=placeId;
        selectedStorageId=null;
        targetPlaceId=placeId;
        targetStorageId=null;
        refreshProperties();
        if (currentCanvas!=null) currentCanvas.repaint();
    }

    private void ensureDefaultRooms() {
        if (!layouts.isEmpty()) return;
        List<JsonObject> roots=new ArrayList<>();
        for (JsonObject place:placeById.values()) {
            Long parent=longOrNull(place,"parent_id");
            if (parent==null || !placeById.containsKey(parent)) roots.add(place);
        }
        roots.sort(Comparator.comparing(p->safeName(p),String.CASE_INSENSITIVE_ORDER));
        int index=0;
        for (JsonObject place:roots) {
            Long id=longOrNull(place,"id");
            if (id==null) continue;
            layouts.put(id,new RoomLayout(id,"Parter",
                80+(index%4)*180,90+(index/4)*130,160,105));
            index++;
        }
        if (!layouts.isEmpty()) saveLayout();
    }

    private void loadBackground() {
        if (!Files.isRegularFile(JPG_BACKGROUND)) return;
        try { backgroundImage=ImageIO.read(JPG_BACKGROUND.toFile()); }
        catch (Exception ignored) { backgroundImage=null; }
    }

    private void loadLayout() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject root=JsonParser.parseString(
                Files.readString(FILE,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("floors") && root.get("floors").isJsonArray()) {
                List<String> loaded=new ArrayList<>();
                for (JsonElement e:root.getAsJsonArray("floors")) {
                    String f=e.getAsString().trim();
                    if (!f.isBlank() && !loaded.contains(f)) loaded.add(f);
                }
                for (String required:List.of("Podwórko","Parter","Piętro","Piwnica"))
                    if (!loaded.contains(required)) loaded.add(required);
                if (!loaded.isEmpty()) { floors.clear(); floors.addAll(loaded); }
            }
            propertyWidthMeters=number(root,"propertyWidthMeters",28.0);
            propertyHeightMeters=number(root,"propertyHeightMeters",20.0);
            gridEnabled=bool(root,"gridEnabled",true);
            snapEnabled=bool(root,"snapEnabled",true);
            backgroundSourceName=value(root,"backgroundSourceName");
            dxfSourceName=value(root,"dxfSourceName");
            String floor=value(root,"activeFloor");
            if (!floor.isBlank() && floors.contains(floor)) activeFloor=floor;

            if (root.has("rooms") && root.get("rooms").isJsonArray()) {
                for (JsonElement e:root.getAsJsonArray("rooms")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject r=e.getAsJsonObject();
                    long id=r.get("placeId").getAsLong();
                    if (!placeById.containsKey(id)) continue;
                    RoomLayout room=new RoomLayout(id,value(r,"floor"),
                        clamp(r.get("x").getAsInt(),0,5000),
                        clamp(r.get("y").getAsInt(),0,5000),
                        clamp(r.get("w").getAsInt(),80,900),
                        clamp(r.get("h").getAsInt(),60,700));
                    if (room.floor.isBlank()) room.floor="Parter";
                    if (!floors.contains(room.floor)) floors.add(room.floor);
                    layouts.put(id,room);
                }
            }
            if (root.has("links") && root.get("links").isJsonArray()) {
                for (JsonElement e:root.getAsJsonArray("links")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject l=e.getAsJsonObject();
                    long a=l.get("a").getAsLong();
                    long b=l.get("b").getAsLong();
                    if (a!=b && placeById.containsKey(a) && placeById.containsKey(b))
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
            JsonObject root=new JsonObject();
            root.addProperty("format","edhome-desktop-floor-map");
            root.addProperty("version",2);
            root.addProperty("propertyWidthMeters",propertyWidthMeters);
            root.addProperty("propertyHeightMeters",propertyHeightMeters);
            root.addProperty("gridEnabled",gridEnabled);
            root.addProperty("snapEnabled",snapEnabled);
            root.addProperty("activeFloor",activeFloor);
            root.addProperty("backgroundSourceName",backgroundSourceName);
            root.addProperty("dxfSourceName",dxfSourceName);

            JsonArray floorArray=new JsonArray();
            for (String floor:floors) floorArray.add(floor);
            root.add("floors",floorArray);

            JsonArray rooms=new JsonArray();
            for (RoomLayout room:layouts.values()) {
                JsonObject r=new JsonObject();
                r.addProperty("placeId",room.placeId);
                r.addProperty("floor",room.floor);
                r.addProperty("x",room.x); r.addProperty("y",room.y);
                r.addProperty("w",room.w); r.addProperty("h",room.h);
                rooms.add(r);
            }
            root.add("rooms",rooms);

            JsonArray edges=new JsonArray();
            for (Link link:links) {
                JsonObject l=new JsonObject();
                l.addProperty("a",link.a); l.addProperty("b",link.b);
                edges.add(l);
            }
            root.add("links",edges);

            Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            Files.writeString(temp,GSON.toJson(root),StandardCharsets.UTF_8);
            Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
            try {
                Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
                Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) { }
        } catch (Exception error) {
            status.setText("Nie zapisano mapy: "+rootMessage(error));
        }
    }

    private Long effectivePlace(JsonObject item) {
        if (item==null) return null;
        Long place=longOrNull(item,"place_id");
        if (place!=null) return place;
        Set<Long> seen=new HashSet<>();
        Long box=longOrNull(item,"parent_box_id");
        while(box!=null && seen.add(box)) {
            JsonObject parent=storageById.get(box);
            if (parent==null) break;
            place=longOrNull(parent,"place_id");
            if (place!=null) return place;
            box=longOrNull(parent,"parent_box_id");
        }
        return null;
    }

    private String storagePath(long itemId) {
        JsonObject item=storageById.get(itemId);
        if (item==null) return "Nieznana pozycja";
        LinkedList<String> names=new LinkedList<>();
        Set<Long> seen=new HashSet<>();
        JsonObject current=item;
        Long place=null;
        while(current!=null) {
            Long id=longOrNull(current,"id");
            if (id!=null && !seen.add(id)) break;
            names.addFirst(safeName(current));
            place=longOrNull(current,"place_id");
            if (place!=null) break;
            Long parent=longOrNull(current,"parent_box_id");
            current=parent==null?null:storageById.get(parent);
        }
        if (place!=null) names.addFirst(placePath(place));
        return String.join(" → ",names);
    }

    private String placePath(long id) {
        LinkedList<String> names=new LinkedList<>();
        Set<Long> seen=new HashSet<>();
        Long current=id;
        while(current!=null && seen.add(current)) {
            JsonObject row=placeById.get(current);
            if (row==null) break;
            names.addFirst(safeName(row));
            current=longOrNull(row,"parent_id");
        }
        return names.isEmpty()?"Nieznane miejsce":String.join(" → ",names);
    }

    private String roomName(long id) {
        JsonObject row=placeById.get(id);
        return row==null?"Miejsce #"+id:safeName(row);
    }

    private int roomItemCount(long placeId) {
        int count=0;
        for (JsonObject item:storageById.values()) {
            Long p=effectivePlace(item);
            if (p!=null && p==placeId) count++;
        }
        return count;
    }

    private String roomCount(long placeId) {
        int count=roomItemCount(placeId);
        return count==0?"":count+(count==1?" element":" elementów");
    }

    private String placeIcon(JsonObject row) {
        String kind=value(row,"kind").toLowerCase(Locale.ROOT);
        if (kind.contains("room") || kind.contains("pom")) return "▣";
        if (kind.contains("zone") || kind.contains("stref")) return "▧";
        if (kind.contains("building") || kind.contains("bud")) return "⌂";
        return "◇";
    }

    private String storageIcon(JsonObject row) {
        String kind=value(row,"kind").toLowerCase(Locale.ROOT);
        if (kind.contains("box") || kind.contains("pude")) return "□";
        if (kind.contains("tool") || kind.contains("narz")) return "⌁";
        return "•";
    }

    private String safeName(JsonObject row) {
        if (row==null) return "—";
        String name=value(row,"name");
        if (name.isBlank()) name=value(row,"title");
        if (name.isBlank()) name="Pozycja";
        return name;
    }

    private JPanel cardPanel() {
        JPanel panel=new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(12,12,12,12)));
        return panel;
    }

    private JButton toolButton(String text) {
        JButton b=new JButton(text);
        b.setFocusPainted(false);
        b.setForeground(TEXT);
        b.setBackground(PANEL_2);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(8,12,8,12)));
        return b;
    }

    private JButton compactButton(String text) {
        JButton b=toolButton(text);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(4,9,4,9)));
        return b;
    }

    private JComponent separator() {
        JSeparator s=new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1,32));
        return s;
    }

    private static Long longOrNull(JsonObject row,String key) {
        try {
            JsonElement e=row==null?null:row.get(key);
            return e==null || e.isJsonNull()?null:e.getAsLong();
        } catch(Exception ignored) { return null; }
    }

    private static String value(JsonObject row,String key) {
        JsonElement e=row==null?null:row.get(key);
        if (e==null || e.isJsonNull()) return "";
        try { return e.getAsString(); } catch(Exception ignored) { return e.toString(); }
    }

    private static double number(JsonObject row,String key,double fallback) {
        try {
            JsonElement e=row==null?null:row.get(key);
            return e==null || e.isJsonNull()?fallback:e.getAsDouble();
        } catch(Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject row,String key,boolean fallback) {
        try {
            JsonElement e=row==null?null:row.get(key);
            return e==null || e.isJsonNull()?fallback:e.getAsBoolean();
        } catch(Exception ignored) { return fallback; }
    }

    private static int clamp(int v,int min,int max) {
        return Math.max(min,Math.min(max,v));
    }

    private static double parseMeters(String raw) {
        return Double.parseDouble(raw.trim().replace(",","."));
    }

    private static String formatMeters(double value) {
        return String.format(Locale.forLanguageTag("pl-PL"),"%.1f",value);
    }

    private static String html(String raw) {
        if (raw==null) return "";
        return raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static String rootMessage(Throwable error) {
        Throwable current=error;
        while(current.getCause()!=null) current=current.getCause();
        String message=current.getMessage();
        return message==null || message.isBlank()?current.getClass().getSimpleName():message;
    }

    private final class FloorCanvas extends JPanel {
        private Long dragging;
        private Point dragOffset;

        FloorCanvas(String floor) {
            setLayout(null);
            setBackground(new Color(15,22,30));
            setOpaque(true);
            int width=Math.max(1100,(int)Math.round(propertyWidthMeters*42));
            int height=Math.max(720,(int)Math.round(propertyHeightMeters*42));
            setPreferredSize(new Dimension(width,height));
            setMinimumSize(new Dimension(700,520));

            for (RoomLayout room:layouts.values()) {
                if (!floor.equals(room.floor)) continue;
                RoomBox box=new RoomBox(room);
                add(box);
                box.setBounds(room.x,room.y,room.w,room.h);
            }

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectedPlaceId=null;
                        selectedStorageId=null;
                        refreshProperties();
                        repaint();
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g=(Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                int margin=48;
                int w=getWidth()-margin*2;
                int h=getHeight()-margin*2;

                if (backgroundImage!=null) {
                    g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER,0.58f));
                    g.drawImage(backgroundImage,margin,margin,w,h,null);
                    g.setComposite(AlphaComposite.SrcOver);
                }

                if (gridEnabled) {
                    g.setColor(GRID);
                    g.setStroke(new BasicStroke(1f));
                    for (int x=margin;x<getWidth()-margin;x+=gridPx)
                        g.drawLine(x,margin,x,getHeight()-margin);
                    for (int y=margin;y<getHeight()-margin;y+=gridPx)
                        g.drawLine(margin,y,getWidth()-margin,y);
                }

                g.setColor(new Color(208,219,228));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,1f,new float[]{8f,6f},0f));
                g.drawRect(margin,margin,w,h);

                g.setColor(TEXT);
                g.setFont(g.getFont().deriveFont(Font.BOLD,12f));
                g.drawString(formatMeters(propertyWidthMeters)+" m",
                    margin+w/2-20,margin-10);
                g.rotate(-Math.PI/2);
                g.drawString(formatMeters(propertyHeightMeters)+" m",
                    -(margin+h/2+20),margin-12);
                g.rotate(Math.PI/2);

                for (Link link:links) {
                    RoomLayout a=layouts.get(link.a), b=layouts.get(link.b);
                    if (a==null || b==null || !activeFloor.equals(a.floor)
                            || !activeFloor.equals(b.floor)) continue;
                    g.setColor(new Color(130,142,154));
                    g.setStroke(new BasicStroke(3f));
                    g.drawLine(a.x+a.w/2,a.y+a.h/2,b.x+b.w/2,b.y+b.h/2);
                }

                if (activeRoute.size()>1) {
                    g.setColor(ROUTE);
                    g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,1f,new float[]{10f,8f},0f));
                    for (int i=0;i<activeRoute.size()-1;i++) {
                        RoomLayout a=layouts.get(activeRoute.get(i));
                        RoomLayout b=layouts.get(activeRoute.get(i+1));
                        if (a==null || b==null || !activeFloor.equals(a.floor)
                                || !activeFloor.equals(b.floor)) continue;
                        g.drawLine(a.x+a.w/2,a.y+a.h/2,b.x+b.w/2,b.y+b.h/2);
                    }
                }

                g.setColor(MUTED);
                g.setFont(g.getFont().deriveFont(11f));
                String info="Poziom: "+activeFloor
                    +"  •  JPG: "+(backgroundSourceName.isBlank()?"brak":backgroundSourceName)
                    +"  •  DXF: "+(dxfSourceName.isBlank()?"brak":dxfSourceName);
                g.drawString(info,margin,getHeight()-18);
            } finally {
                g.dispose();
            }
        }

        private final class RoomBox extends JPanel {
            private final RoomLayout room;

            RoomBox(RoomLayout room) {
                this.room=room;
                setLayout(new BorderLayout());
                setBackground(ROOM_FILL);
                setOpaque(true);
                refreshBorder();

                JLabel name=new JLabel(roomName(room.placeId),SwingConstants.CENTER);
                name.setForeground(TEXT);
                name.setFont(name.getFont().deriveFont(Font.BOLD,14f));
                JLabel area=new JLabel(roomCount(room.placeId),SwingConstants.CENTER);
                area.setForeground(MUTED);
                area.setFont(area.getFont().deriveFont(11f));
                add(name,BorderLayout.CENTER);
                add(area,BorderLayout.SOUTH);

                MouseAdapter mouse=new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        roomClicked(room.placeId);
                        if (editMode) {
                            dragging=room.placeId;
                            dragOffset=e.getPoint();
                        }
                    }
                    @Override public void mouseDragged(MouseEvent e) {
                        if (!editMode || dragging==null || dragOffset==null) return;
                        Point p=SwingUtilities.convertPoint(RoomBox.this,e.getPoint(),
                            FloorCanvas.this);
                        int nx=clamp(p.x-dragOffset.x,48,
                            Math.max(48,FloorCanvas.this.getWidth()-room.w-48));
                        int ny=clamp(p.y-dragOffset.y,48,
                            Math.max(48,FloorCanvas.this.getHeight()-room.h-48));
                        if (snapEnabled) {
                            nx=Math.round(nx/(float)gridPx)*gridPx;
                            ny=Math.round(ny/(float)gridPx)*gridPx;
                        }
                        room.x=nx; room.y=ny;
                        setLocation(nx,ny);
                        refreshProperties();
                        FloorCanvas.this.repaint();
                    }
                    @Override public void mouseReleased(MouseEvent e) {
                        if (editMode && dragging!=null) saveLayout();
                        dragging=null; dragOffset=null;
                    }
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount()>=2 && editPlace!=null)
                            editPlace.accept(room.placeId);
                    }
                };
                addMouseListener(mouse);
                addMouseMotionListener(mouse);
                name.addMouseListener(mouse);
                name.addMouseMotionListener(mouse);
                area.addMouseListener(mouse);
                area.addMouseMotionListener(mouse);
            }

            private void refreshBorder() {
                Color color=ROOM_BORDER;
                int thickness=3;
                PlaceChoice current=(PlaceChoice)currentCombo.getSelectedItem();
                Long currentId=current==null?null:current.id;
                if (Objects.equals(currentId,room.placeId)) {
                    color=CURRENT; thickness=4;
                }
                if (Objects.equals(targetPlaceId,room.placeId)) {
                    color=TARGET; thickness=4;
                }
                if (Objects.equals(selectedPlaceId,room.placeId)) {
                    color=SELECTED; thickness=5;
                }
                setBorder(BorderFactory.createLineBorder(color,thickness));
            }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                refreshBorder();
            }
        }
    }

    private static final class RoomLayout {
        final long placeId;
        String floor;
        int x,y,w,h;
        RoomLayout(long placeId,String floor,int x,int y,int w,int h) {
            this.placeId=placeId;
            this.floor=floor==null?"":floor;
            this.x=x; this.y=y; this.w=w; this.h=h;
        }
    }

    private static final class Link {
        final long a,b;
        Link(long a,long b) { this.a=Math.min(a,b); this.b=Math.max(a,b); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Link)) return false;
            Link l=(Link)o; return a==l.a && b==l.b;
        }
        @Override public int hashCode() { return Objects.hash(a,b); }
    }

    private static final class PlaceChoice {
        final Long id;
        final String label;
        PlaceChoice(Long id,String label) {
            this.id=id;
            this.label=label==null || label.isBlank()
                ? (id==null?"—":"Miejsce #"+id):label;
        }
        @Override public String toString() { return label; }
    }

    private static final class TreeRef {
        final String kind;
        final Long id;
        final String label;
        TreeRef(String kind,Long id,String label) {
            this.kind=kind; this.id=id; this.label=label;
        }
        @Override public String toString() { return label; }
    }
}
