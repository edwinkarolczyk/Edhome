package com.edhome.desktop;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

final class DesktopPaycheckChart extends JPanel {
    private final List<String> labels;
    private final List<Long> expenses;
    private final List<Long> income;

    DesktopPaycheckChart(List<String> labels, List<Long> expenses, List<Long> income) {
        this.labels = labels;
        this.expenses = expenses;
        this.income = income;
        setPreferredSize(new Dimension(820, 360));
        setBackground(Color.WHITE);
    }

    @Override protected void paintComponent(Graphics raw) {
        super.paintComponent(raw);
        Graphics2D g = (Graphics2D) raw.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int left = 58, right = 24, top = 28, bottom = 62;
            int chartW = Math.max(1, w - left - right);
            int chartH = Math.max(1, h - top - bottom);

            long max = 1;
            for (Long v : expenses) if (v != null) max = Math.max(max, v);
            for (Long v : income) if (v != null) max = Math.max(max, v);

            g.setColor(new Color(230, 233, 238));
            for (int i=0; i<=4; i++) {
                int y = top + chartH * i / 4;
                g.drawLine(left, y, left + chartW, y);
            }

            int n = Math.max(1, labels.size());
            int slot = Math.max(24, chartW / n);
            int barW = Math.max(5, Math.min(24, (slot - 8) / 2));
            for (int i=0; i<labels.size(); i++) {
                int x = left + i * slot + Math.max(2, (slot - 2*barW - 4)/2);
                long exp = i < expenses.size() && expenses.get(i) != null ? expenses.get(i) : 0;
                long inc = i < income.size() && income.get(i) != null ? income.get(i) : 0;
                int eh = (int)Math.round(chartH * (exp / (double)max));
                int ih = (int)Math.round(chartH * (inc / (double)max));

                g.setColor(new Color(214, 93, 93));
                g.fillRoundRect(x, top + chartH - eh, barW, eh, 6, 6);
                g.setColor(new Color(63, 172, 131));
                g.fillRoundRect(x + barW + 4, top + chartH - ih, barW, ih, 6, 6);

                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                String label = labels.get(i);
                int tw = g.getFontMetrics().stringWidth(label);
                g.drawString(label, x + barW - tw/2, top + chartH + 20);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(new Color(214, 93, 93));
            g.fillRect(left, h - 24, 14, 10);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Wydatki", left + 20, h - 14);
            g.setColor(new Color(63, 172, 131));
            g.fillRect(left + 100, h - 24, 14, 10);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Wpływy", left + 120, h - 14);
        } finally {
            g.dispose();
        }
    }
}
