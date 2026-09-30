package com.edhome.desktop;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads household-budget documents locally. This is deliberately separate from
 * DesktopBankImporter: an invoice/loan schedule is a PLAN source, not evidence
 * that money has already left the bank account.
 */
final class DesktopBudgetDocumentReader {
    static final int MAX_PDF_BYTES = 12 * 1024 * 1024;
    static final int MAX_XLSX_BYTES = 8 * 1024 * 1024;
    static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    static final int MAX_AMOUNTS = 40;

    private static final Pattern MONEY_WITH_CURRENCY = Pattern.compile(
        "(?iu)([-+−]?\\s*[0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2}))"
            + "\\s*(?:PLN|zł)\\b");
    private static final Pattern LABELED_MONEY = Pattern.compile(
        "(?iu)(?:do\\s+zapłaty|kwota|razem|suma|rata|należność|wartość)"
            + "[^0-9]{0,40}([0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2}))");

    static final class DocumentData {
        final String fileName;
        final String sha256;
        final String suggestedName;
        final String providerHint;
        final String typeHint;
        final String categoryHint;
        final List<Long> amountsGrosz;

        DocumentData(String fileName, String sha256, String suggestedName,
                String providerHint, String typeHint, String categoryHint,
                List<Long> amountsGrosz) {
            this.fileName = fileName;
            this.sha256 = sha256;
            this.suggestedName = suggestedName;
            this.providerHint = providerHint;
            this.typeHint = typeHint;
            this.categoryHint = categoryHint;
            this.amountsGrosz = amountsGrosz;
        }
    }

    private DesktopBudgetDocumentReader() { }

    static DocumentData read(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file))
            throw new IllegalArgumentException("Nie znaleziono dokumentu.");
        byte[] bytes = Files.readAllBytes(file);
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String text;

        if (lowerName.endsWith(".pdf")) {
            if (bytes.length > MAX_PDF_BYTES)
                throw new IllegalArgumentException("PDF jest za duży (maks. 12 MB).");
            try (PDDocument doc = PDDocument.load(bytes)) {
                text = new PDFTextStripper().getText(doc);
            }
            if (text == null || text.trim().isEmpty())
                throw new IllegalArgumentException(
                    "PDF nie ma warstwy tekstowej. Skan obrazu nie jest jeszcze "
                        + "automatycznie czytany przez kreator budżetu.");
        } else if (lowerName.endsWith(".xlsx")) {
            if (bytes.length > MAX_XLSX_BYTES)
                throw new IllegalArgumentException("XLSX jest za duży (maks. 8 MB).");
            text = xlsxText(bytes);
        } else if (lowerName.endsWith(".csv") || lowerName.endsWith(".txt")) {
            if (bytes.length > MAX_TEXT_BYTES)
                throw new IllegalArgumentException("Plik tekstowy jest za duży (maks. 2 MB).");
            text = decodeText(bytes);
        } else {
            throw new IllegalArgumentException(
                "Kreator budżetu obsługuje PDF, XLSX, CSV i TXT.");
        }

        String provider = detectProvider(text);
        String type = detectType(text, provider);
        String category = detectCategory(text, type, provider);
        String name = suggestedName(text, provider, type);
        List<Long> amounts = extractAmounts(text);
        return new DocumentData(file.getFileName().toString(), sha256(bytes),
            name, provider, type, category, amounts);
    }

    private static String decodeText(byte[] bytes) {
        String utf = new String(bytes, StandardCharsets.UTF_8);
        if (utf.indexOf('�') < 0) return utf;
        return new String(bytes, Charset.forName("windows-1250"));
    }

    private static String xlsxText(byte[] bytes) throws Exception {
        byte[] sharedXml = null;
        List<byte[]> sheets = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] data = zip.readAllBytes();
                if ("xl/sharedStrings.xml".equals(name)) sharedXml = data;
                else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                    sheets.add(data);
            }
        }
        List<String> shared = sharedXml == null
            ? List.of() : parseSharedStrings(sharedXml);
        StringBuilder out = new StringBuilder();
        for (byte[] sheet : sheets) parseSheet(sheet, shared, out);
        if (out.length() == 0)
            throw new IllegalArgumentException("XLSX nie zawiera czytelnych komórek.");
        return out.toString();
    }

    private static DocumentBuilderFactory xmlFactory() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setExpandEntityReferences(false);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
        catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); }
        catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
        catch (Exception ignored) { }
        return f;
    }

    private static List<String> parseSharedStrings(byte[] xml) throws Exception {
        Document doc = xmlFactory().newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml));
        NodeList si = doc.getElementsByTagName("si");
        List<String> result = new ArrayList<>(si.getLength());
        for (int i=0; i<si.getLength(); i++) {
            Node item = si.item(i);
            NodeList textNodes = ((Element)item).getElementsByTagName("t");
            StringBuilder value = new StringBuilder();
            for (int j=0; j<textNodes.getLength(); j++)
                value.append(textNodes.item(j).getTextContent());
            result.add(value.toString());
        }
        return result;
    }

    private static void parseSheet(byte[] xml, List<String> shared,
            StringBuilder out) throws Exception {
        Document doc = xmlFactory().newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml));
        NodeList cells = doc.getElementsByTagName("c");
        for (int i=0; i<cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            String type = cell.getAttribute("t");
            String value = "";
            if ("inlineStr".equals(type)) {
                NodeList t = cell.getElementsByTagName("t");
                if (t.getLength() > 0) value = t.item(0).getTextContent();
            } else {
                NodeList v = cell.getElementsByTagName("v");
                if (v.getLength() > 0) value = v.item(0).getTextContent();
                if ("s".equals(type) && !value.isBlank()) {
                    try {
                        int index = Integer.parseInt(value.trim());
                        if (index >= 0 && index < shared.size()) value = shared.get(index);
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (!value.isBlank()) out.append(value).append('\n');
        }
    }

    private static List<Long> extractAmounts(String text) {
        Set<Long> unique = new LinkedHashSet<>();
        collectAmounts(MONEY_WITH_CURRENCY, text, unique);
        collectAmounts(LABELED_MONEY, text, unique);
        return new ArrayList<>(unique);
    }

    private static void collectAmounts(Pattern pattern, String text, Set<Long> out) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find() && out.size() < MAX_AMOUNTS) {
            try {
                long value = parseMoney(matcher.group(1));
                if (value >= 100L && value <= 1_000_000_000_00L) out.add(value);
            } catch (Exception ignored) { }
        }
    }

    private static long parseMoney(String raw) {
        String normalized = raw.replace("\u00a0","").replace(" ","")
            .replace("−","-").replace(",",".").trim();
        boolean negative = normalized.startsWith("-");
        if (negative || normalized.startsWith("+")) normalized = normalized.substring(1);
        java.math.BigDecimal amount = new java.math.BigDecimal(normalized);
        long grosz = amount.movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        return Math.abs(grosz);
    }

    private static String detectProvider(String text) {
        String t = normalize(text);
        if (t.contains("tauron")) return "TAURON";
        if (t.contains("pge")) return "PGE";
        if (t.contains("energa")) return "Energa";
        if (t.contains("e.on") || t.contains("eon")) return "E.ON";
        if (t.contains("orlen") && (t.contains("gaz") || t.contains("energia")))
            return "ORLEN";
        if (t.contains("wodociag") || t.contains("wodociąg")
                || t.contains("przedsiebiorstwo wod"))
            return "Wodociągi";
        if (t.contains("orange")) return "Orange";
        if (t.contains("t-mobile") || t.contains("t‑mobile")) return "T-Mobile";
        if (t.contains("play")) return "Play";
        if (t.contains("netia")) return "Netia";
        if (t.contains("velobank") || t.contains("velo bank")) return "VeloBank";
        if (t.contains("mbank")) return "mBank";
        if (t.contains("pko bank polski") || t.contains("pko bp")) return "PKO BP";
        if (t.contains("santander")) return "Santander";
        if (t.contains("ing bank")) return "ING";
        if (t.contains("alior bank")) return "Alior Bank";
        if (t.contains("bank pekao")) return "Pekao";
        if (t.contains("millennium")) return "Millennium";
        return "";
    }

    private static String detectType(String text, String provider) {
        String t = normalize(text);
        if (t.contains("kredyt") || t.contains("pozyczk") || t.contains("pożyczk")
                || t.contains("harmonogram splat") || t.contains("harmonogram spłat")
                || (t.contains("rata") && !t.contains("rabat")))
            return "loan";
        if (t.contains("wynagrodzenie") || t.contains("pensja")
                || t.contains("lista plac") || t.contains("lista płac"))
            return "recurring_income";
        if (!provider.isBlank() || t.contains("faktura") || t.contains("rachunek"))
            return "bill";
        return "";
    }

    private static String detectCategory(String text, String type, String provider) {
        String t = normalize(text);
        if ("recurring_income".equals(type)) return "salary";
        if ("loan".equals(type)) return "loans";
        if ("TAURON".equals(provider) || "PGE".equals(provider)
                || "Energa".equals(provider) || "E.ON".equals(provider)
                || "ORLEN".equals(provider) || "Wodociągi".equals(provider))
            return "utilities";
        if (t.contains("ubezpieczen") || t.contains("polisa") || t.contains("oc "))
            return "insurance";
        if (t.contains("czynsz") || t.contains("dom") || t.contains("mieszkan"))
            return "home";
        return "bills";
    }

    private static String suggestedName(String text, String provider, String type) {
        if (!provider.isBlank()) {
            if ("loan".equals(type)) return "Rata kredytu • " + provider;
            if ("recurring_income".equals(type)) return "Dochód • " + provider;
            return "Rachunek • " + provider;
        }
        if ("loan".equals(type)) return "Rata kredytu";
        if ("recurring_income".equals(type)) return "Wynagrodzenie";
        String t = normalize(text);
        if (t.contains("woda") || t.contains("wodoci")) return "Rachunek za wodę";
        if (t.contains("energia") || t.contains("prad") || t.contains("prąd"))
            return "Rachunek za energię";
        return "Pozycja z dokumentu";
    }

    private static String normalize(String raw) {
        return (raw == null ? "" : raw).toLowerCase(Locale.ROOT)
            .replace('\u00a0',' ');
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
