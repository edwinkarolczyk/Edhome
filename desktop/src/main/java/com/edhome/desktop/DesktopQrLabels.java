package com.edhome.desktop;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class DesktopQrLabels {
    static final String[] FORMATS = {
        "40 × 30 mm", "50 × 30 mm", "70 × 50 mm", "A4 — zbiorczo", "Własny rozmiar"
    };

    static final class Label {
        final String kind;
        final long id;
        final String name;
        final String location;

        Label(String kind, long id, String name, String location) {
            this.kind = kind;
            this.id = id;
            this.name = name == null ? "" : name;
            this.location = location == null ? "" : location;
        }

        String payload() {
            return "EDHOME:STORAGE:1:" + kind + ":" + id;
        }

        @Override public String toString() {
            return name + (location.isBlank() ? "" : " • " + location);
        }
    }

    static final class FormatSpec {
        final float pageWmm;
        final float pageHmm;
        final float labelWmm;
        final float labelHmm;
        final int columns;
        final int rows;
        final float gapMm;

        FormatSpec(float pageWmm, float pageHmm, float labelWmm, float labelHmm,
                int columns, int rows, float gapMm) {
            this.pageWmm = pageWmm;
            this.pageHmm = pageHmm;
            this.labelWmm = labelWmm;
            this.labelHmm = labelHmm;
            this.columns = columns;
            this.rows = rows;
            this.gapMm = gapMm;
        }

        int perPage() { return columns * rows; }
    }

    private DesktopQrLabels() { }

    static FormatSpec preset(int index, float customW, float customH) {
        if (index == 0) return new FormatSpec(40, 30, 40, 30, 1, 1, 0);
        if (index == 1) return new FormatSpec(50, 30, 50, 30, 1, 1, 0);
        if (index == 2) return new FormatSpec(70, 50, 70, 50, 1, 1, 0);
        if (index == 3) return new FormatSpec(210, 297, 50, 30, 3, 8, 5);
        if (index != 4 || customW < 20 || customW > 210 || customH < 20 || customH > 297)
            throw new IllegalArgumentException("Własna etykieta: szerokość 20–210 mm, wysokość 20–297 mm.");
        return new FormatSpec(customW, customH, customW, customH, 1, 1, 0);
    }

    static byte[] pdf(List<Label> labels, FormatSpec format) throws Exception {
        if (labels == null || labels.isEmpty() || labels.size() > 250)
            throw new IllegalArgumentException("Zaznacz 1–250 etykiet.");
        try (PDDocument doc = new PDDocument()) {
            int perPage = format.perPage();
            for (int start = 0; start < labels.size(); start += perPage) {
                PDPage page = new PDPage(new PDRectangle(mm(format.pageWmm), mm(format.pageHmm)));
                doc.addPage(page);
                try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                    float labelW = mm(format.labelWmm);
                    float labelH = mm(format.labelHmm);
                    float gap = mm(format.gapMm);
                    float usedW = format.columns * labelW + Math.max(0, format.columns - 1) * gap;
                    float usedH = format.rows * labelH + Math.max(0, format.rows - 1) * gap;
                    float left = (mm(format.pageWmm) - usedW) / 2f;
                    float top = (mm(format.pageHmm) - usedH) / 2f;
                    int end = Math.min(labels.size(), start + perPage);
                    for (int n = start; n < end; n++) {
                        int cell = n - start;
                        int col = cell % format.columns;
                        int row = cell / format.columns;
                        float x = left + col * (labelW + gap);
                        float yTop = top + row * (labelH + gap);
                        BufferedImage image = renderLabel(labels.get(n),
                            format.labelWmm, format.labelHmm);
                        PDImageXObject ximage = LosslessFactory.createFromImage(doc, image);
                        float y = mm(format.pageHmm) - yTop - labelH;
                        out.drawImage(ximage, x, y, labelW, labelH);
                    }
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            return bytes.toByteArray();
        }
    }

    static BufferedImage preview(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            BufferedImage raw = new PDFRenderer(doc).renderImageWithDPI(0, 110);
            int maxW = 760, maxH = 720;
            double scale = Math.min(1d, Math.min(maxW / (double) raw.getWidth(),
                maxH / (double) raw.getHeight()));
            if (scale >= .999) return raw;
            int w = Math.max(1, (int)Math.round(raw.getWidth() * scale));
            int h = Math.max(1, (int)Math.round(raw.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(raw.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
            } finally { g.dispose(); }
            return scaled;
        }
    }

    static void showPreview(Component parent, byte[] pdf) throws Exception {
        JOptionPane.showMessageDialog(parent, new ImageIcon(preview(pdf)),
            "EDHOME • podgląd etykiet QR", JOptionPane.PLAIN_MESSAGE);
    }

    static Path savePdf(Component parent, byte[] pdf) throws Exception {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Zapisz etykiety QR jako PDF");
        chooser.setSelectedFile(new java.io.File("EDHOME-etykiety-QR.pdf"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        Path target = chooser.getSelectedFile().toPath();
        String name = target.getFileName().toString().toLowerCase();
        if (!name.endsWith(".pdf")) target = target.resolveSibling(target.getFileName() + ".pdf");
        Files.write(target, pdf);
        return target;
    }

    static String[] printerNames() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        List<String> names = new ArrayList<>();
        for (PrintService service : services) names.add(service.getName());
        return names.toArray(new String[0]);
    }

    static void print(byte[] pdf, String printerName, int copies) throws Exception {
        if (printerName == null || printerName.isBlank())
            throw new IllegalArgumentException("Wybierz drukarkę etykiet.");
        PrintService selected = null;
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (printerName.equals(service.getName())) {
                selected = service;
                break;
            }
        }
        if (selected == null)
            throw new IllegalStateException("Wybrana drukarka nie jest teraz dostępna.");
        try (PDDocument doc = PDDocument.load(pdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(selected);
            job.setCopies(Math.max(1, Math.min(99, copies)));
            job.setPageable(new PDFPageable(doc));
            job.print();
        }
    }

    private static BufferedImage renderLabel(Label label, float widthMm, float heightMm)
            throws Exception {
        final float pxPerMm = 12f;
        int width = Math.max(240, Math.round(widthMm * pxPerMm));
        int height = Math.max(180, Math.round(heightMm * pxPerMm));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(java.awt.Color.BLACK);
            g.drawRect(1, 1, width - 3, height - 3);

            int qrSize = Math.min(width - 36, Math.round(height * 0.67f));
            qrSize = Math.max(110, qrSize);
            BufferedImage qr = qr(label.payload(), qrSize);
            int qrX = (width - qrSize) / 2;
            int qrY = Math.max(8, Math.round(height * 0.03f));
            g.drawImage(qr, qrX, qrY, qrSize, qrSize, null);

            int titleSize = Math.max(11, Math.min(28, Math.round(height * 0.085f)));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, titleSize));
            FontMetrics fm = g.getFontMetrics();
            String title = shorten(label.name, fm, width - 30);
            int titleY = Math.min(height - 34, qrY + qrSize + titleSize + 4);
            drawCentered(g, title, width, titleY);

            int pathSize = Math.max(9, Math.min(20, Math.round(height * 0.055f)));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, pathSize));
            FontMetrics pm = g.getFontMetrics();
            String path = shorten(label.location, pm, width - 30);
            int pathY = Math.min(height - 10, titleY + pathSize + 5);
            if (!path.isBlank()) drawCentered(g, path, width, pathY);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage qr(String payload, int size) throws Exception {
        BitMatrix bits = new MultiFormatWriter().encode(payload,
            BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                image.setRGB(x, y, bits.get(x, y) ? 0x000000 : 0xFFFFFF);
        return image;
    }

    private static void drawCentered(Graphics2D g, String text, int width, int y) {
        int x = Math.max(4, (width - g.getFontMetrics().stringWidth(text)) / 2);
        g.drawString(text, x, y);
    }

    private static String shorten(String value, FontMetrics fm, int maxWidth) {
        String text = value == null ? "" : value.trim();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String suffix = "…";
        while (!text.isEmpty() && fm.stringWidth(text + suffix) > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + suffix;
    }

    private static float mm(float value) {
        return value * 72f / 25.4f;
    }
}
