package com.edhome.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class DesktopCalendarPanel extends JPanel {
    static final class Event {
        final String key;
        final LocalDate date;
        final String title;
        final String subtitle;
        final boolean done;
        final boolean editable;

        Event(String key, LocalDate date, String title, String subtitle,
                boolean done, boolean editable) {
            this.key = key;
            this.date = date;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.done = done;
            this.editable = editable;
        }
    }

    private final List<Event> events;
    private final Consumer<String> openEvent;
    private final Consumer<LocalDate> addTask;
    private final Runnable printCalendar;

    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();

    private final JLabel monthTitle = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 6, 6));
    private final DefaultListModel<Event> agendaModel = new DefaultListModel<>();
    private final JList<Event> agenda = new JList<>(agendaModel);
    private final JLabel selectedTitle = new JLabel();

    private static final Color BG = new Color(22, 28, 36);
    private static final Color SURFACE = new Color(31, 39, 49);
    private static final Color SURFACE_2 = new Color(42, 51, 63);
    private static final Color TEXT = new Color(237, 241, 246);
    private static final Color MUTED = new Color(164, 174, 188);
    private static final Color ACCENT = new Color(87, 214, 181);
    private static final Color TODAY = new Color(51, 76, 83);
    private static final Color SELECTED = new Color(58, 89, 102);

    DesktopCalendarPanel(List<Event> events, Consumer<String> openEvent,
            Consumer<LocalDate> addTask, Runnable printCalendar) {
        this.events = new ArrayList<>(events == null ? List.of() : events);
        this.events.sort(Comparator.comparing((Event e) -> e.date)
            .thenComparing(e -> e.title, String.CASE_INSENSITIVE_ORDER));
        this.openEvent = openEvent;
        this.addTask = addTask;
        this.printCalendar = printCalendar;

        setLayout(new BorderLayout(12, 12));
        setBackground(BG);
        setBorder(new EmptyBorder(4, 4, 4, 4));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCalendar(), BorderLayout.CENTER);
        add(buildAgenda(), BorderLayout.SOUTH);

        render();
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JButton prev = button("‹ Poprzedni");
        JButton today = button("Dziś");
        JButton next = button("Następny ›");
        prev.addActionListener(e -> {
            month = month.minusMonths(1);
            selected = month.atDay(1);
            render();
        });
        today.addActionListener(e -> {
            selected = LocalDate.now();
            month = YearMonth.from(selected);
            render();
        });
        next.addActionListener(e -> {
            month = month.plusMonths(1);
            selected = month.atDay(1);
            render();
        });
        left.add(prev); left.add(today); left.add(next);

        monthTitle.setForeground(TEXT);
        monthTitle.setFont(monthTitle.getFont().deriveFont(Font.BOLD, 20f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JButton add = button("＋ Zadanie na wybrany dzień");
        add.addActionListener(e -> addTask.accept(selected));
        right.add(add);
        if (printCalendar != null) {
            JButton print = button("🖨 Drukuj");
            print.addActionListener(e -> printCalendar.run());
            right.add(print);
        }

        bar.add(left, BorderLayout.WEST);
        bar.add(monthTitle, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildCalendar() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 7));
        wrapper.setOpaque(false);

        JPanel weekdays = new JPanel(new GridLayout(1, 7, 6, 6));
        weekdays.setOpaque(false);
        for (String day : new String[]{"Pon","Wt","Śr","Czw","Pt","Sob","Niedz"}) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setForeground(MUTED);
            label.setBorder(new EmptyBorder(2, 2, 2, 2));
            weekdays.add(label);
        }
        grid.setOpaque(false);

        wrapper.add(weekdays, BorderLayout.NORTH);
        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent buildAgenda() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        selectedTitle.setForeground(TEXT);
        selectedTitle.setFont(selectedTitle.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(selectedTitle, BorderLayout.NORTH);

        agenda.setBackground(SURFACE);
        agenda.setForeground(TEXT);
        agenda.setSelectionBackground(SELECTED);
        agenda.setSelectionForeground(TEXT);
        agenda.setVisibleRowCount(4);
        agenda.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
                Event event = (Event) value;
                label.setText((event.done ? "✓ " : "○ ") + event.title
                    + (event.subtitle.isBlank() ? "" : "   •   " + event.subtitle));
                label.setBorder(new EmptyBorder(5, 4, 5, 4));
                return label;
            }
        });
        agenda.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    Event event = agenda.getSelectedValue();
                    if (event != null && event.editable) openEvent.accept(event.key);
                }
            }
        });
        panel.add(new JScrollPane(agenda), BorderLayout.CENTER);

        JLabel hint = new JLabel("Podwójne kliknięcie zadania otwiera edycję.");
        hint.setForeground(MUTED);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private void render() {
        monthTitle.setText(capitalize(month.getMonth()
            .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl-PL")))
            + " " + month.getYear());

        grid.removeAll();
        LocalDate first = month.atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1;
        int days = month.lengthOfMonth();

        int cells = ((offset + days + 6) / 7) * 7;
        for (int cell = 0; cell < cells; cell++) {
            int day = cell - offset + 1;
            if (day < 1 || day > days) {
                JPanel blank = new JPanel();
                blank.setOpaque(false);
                grid.add(blank);
            } else {
                LocalDate date = month.atDay(day);
                grid.add(dayCell(date));
            }
        }
        renderAgenda();

        revalidate();
        repaint();
    }

    private JComponent dayCell(LocalDate date) {
        List<Event> dayEvents = eventsOn(date);
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selected);
        card.setBackground(isSelected ? SELECTED : (isToday ? TODAY : SURFACE));
        card.setBorder(new EmptyBorder(7, 8, 7, 8));
        card.setPreferredSize(new Dimension(120, 95));

        JLabel day = new JLabel(Integer.toString(date.getDayOfMonth()));
        day.setForeground(isToday ? ACCENT : TEXT);
        day.setFont(day.getFont().deriveFont(Font.BOLD, 15f));
        card.add(day);
        card.add(Box.createVerticalStrut(5));

        int shown = 0;
        for (Event event : dayEvents) {
            if (shown >= 3) break;
            JLabel item = new JLabel((event.done ? "✓ " : "• ") + crop(event.title, 22));
            item.setForeground(event.done ? MUTED : TEXT);
            item.setToolTipText(event.title);
            card.add(item);
            shown++;
        }
        if (dayEvents.size() > shown) {
            JLabel more = new JLabel("+" + (dayEvents.size() - shown) + " więcej");
            more.setForeground(ACCENT);
            card.add(more);
        }

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selected = date;
                render();
            }
        });
        return card;
    }

    private void renderAgenda() {
        selectedTitle.setText("Agenda • " + selected.format(
            DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        agendaModel.clear();
        for (Event event : eventsOn(selected)) agendaModel.addElement(event);
    }

    private List<Event> eventsOn(LocalDate date) {
        List<Event> result = new ArrayList<>();
        for (Event event : events) if (date.equals(event.date)) result.add(event);
        return result;
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(SURFACE_2);
        return button;
    }

    private static String crop(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
