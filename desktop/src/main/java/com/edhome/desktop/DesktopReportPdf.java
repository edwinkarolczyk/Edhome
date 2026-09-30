package com.edhome.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class DesktopReportPdf {
    private DesktopReportPdf() { }

    static byte[] table(String title, JsonArray rows, String[][] columns) throws Exception {
        List<JsonObject> data = new ArrayList<>();
        if (rows != null) for (JsonElement element : rows)
            if (element.isJsonObject()) data.add(element.getAsJsonObject());

        final int pageW = 1240;
        final int pageH = 1754;
        final int margin = 70;
        final int rowGap = 16;
        final int headerH = 90;

        List<BufferedImage> pages = new ArrayList<>();
        BufferedImage image = newPage(pageW, pageH);
        Graphics2D g = image.createGraphics();
        int y = drawHeader(g, title, margin, pageW, headerH);
        int pageNo = 1;

        try {
            for (JsonObject row : data) {
                List<String> lines = rowLines(row, columns);
                int needed = Math.max(58, 32 + lines.size() * 26);
                if (y + needed > pageH - margin) {
                    drawFooter(g, pageNo++, pageW, pageH, margin);
                    g.dispose();
                    pages.add(image);
                    image = newPage(pageW, pageH);
                    g = image.createGraphics();
                    y = drawHeader(g, title, margin, pageW, headerH);
                }

                g.setColor(new java.awt.Color(240, 243, 247));
                g.fillRoundRect(margin, y, pageW - 2*margin, needed - 6, 18, 18);
                g.setColor(java.awt.Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));

                String main = lines.isEmpty() ? "Pozycja" : lines.get(0);
                g.drawString(shorten(main, g.getFontMetrics(), pageW - 2*margin - 32),
                    margin + 16, y + 28);

                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
                int lineY = y + 54;
                for (int i=1; i<lines.size(); i++) {
                    g.drawString(shorten(lines.get(i), g.getFontMetrics(),
                        pageW - 2*margin - 32), margin + 16, lineY);
                    lineY += 24;
                }
                y += needed + rowGap;
            }

            if (data.isEmpty()) {
                g.setColor(java.awt.Color.DARK_GRAY);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                g.drawString("Brak pozycji.", margin, y + 40);
            }
            drawFooter(g, pageNo, pageW, pageH, margin);
        } finally {
            g.dispose();
        }
        pages.add(image);

        try (PDDocument doc = new PDDocument()) {
            for (BufferedImage pageImage : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject ximage = LosslessFactory.createFromImage(doc, pageImage);
                try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                    out.drawImage(ximage, 0, 0,
                        PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static BufferedImage newPage(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
        } finally { g.dispose(); }
        return image;
    }

    private static int drawHeader(Graphics2D g, String title, int margin,
            int pageW, int headerH) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(java.awt.Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(shorten(title, g.getFontMetrics(), pageW - 2*margin),
            margin, margin);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        g.setColor(java.awt.Color.DARK_GRAY);
        g.drawString("EDHOME Desktop", margin, margin + 28);
        return margin + headerH;
    }

    private static void drawFooter(Graphics2D g, int page, int w, int h, int margin) {
        g.setColor(java.awt.Color.GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("EDHOME • strona " + page, margin, h - 28);
    }

    private static List<String> rowLines(JsonObject row, String[][] columns) {
        List<String> lines = new ArrayList<>();
        for (int i=0; i<columns.length; i++) {
            String label = columns[i][0];
            String key = columns[i][1];
            String value = "";
            try {
                if (row.has(key) && !row.get(key).isJsonNull())
                    value = row.get(key).getAsString();
            } catch (Exception ignored) { }
            if (i == 0) lines.add(value.isBlank() ? "Pozycja" : value);
            else lines.add(label + ": " + (value.isBlank() ? "—" : value));
        }
        return lines;
    }

    private static String shorten(String value, FontMetrics fm, int maxWidth) {
        String text = value == null ? "" : value;
        if (fm.stringWidth(text) <= maxWidth) return text;
        while (!text.isEmpty() && fm.stringWidth(text + "…") > maxWidth)
            text = text.substring(0, text.length()-1);
        return text + "…";
    }
}
