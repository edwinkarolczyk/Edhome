package com.edhome.desktop;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local-only bank statement reader. Imported rows are evidence; never ledger commits. */
final class DesktopBankImporter {
    static final int MAX_ROWS = 250;
    static final int MAX_CSV_BYTES = 256 * 1024;
    static final int MAX_PDF_BYTES = 8 * 1024 * 1024;
    static final int MAX_XLSX_BYTES = 4 * 1024 * 1024;

    static final class Entry {
        final String date;
        final String kind;
        final long amountGrosz;
        final String description;
        final String reference;
        final String evidenceKey;

        Entry(String date, String kind, long amountGrosz, String description,
                String reference, String evidenceKey) {
            this.date = date;
            this.kind = kind;
            this.amountGrosz = amountGrosz;
            this.description = description;
            this.reference = reference;
            this.evidenceKey = evidenceKey;
        }
    }

    static final class Result {
        final String bank;
        final String sourceKind;
        final List<Entry> entries;
        final String originalSha256;

        Result(String bank, String sourceKind, List<Entry> entries, String originalSha256) {
            this.bank = bank;
            this.sourceKind = sourceKind;
            this.entries = entries;
            this.originalSha256 = originalSha256;
        }
    }

    private static final DateTimeFormatter PL =
        DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE = Pattern.compile(
        "(?<!\\d)(\\d{2}[.]\\d{2}[.]\\d{4}|\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern MONEY = Pattern.compile(
        "(?iu)([-+−]?\\s*[0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)"
        + "\\s*(?:PLN|zł)\\b");
    private static final Pattern LABELED_DATE = Pattern.compile(
        "(?iu)(?:data\\s+(?:transakcji|operacji|księgowania|płatności))"
        + "[^0-9]{0,30}(\\d{2}[.]\\d{2}[.]\\d{4}|\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern LABELED_AMOUNT = Pattern.compile(
        "(?iu)(?:kwota\\s+(?:transakcji|operacji|płatności|przelewu))"
        + "[^0-9+−-]{0,30}([-+−]?\\s*[0-9]{1,9}(?:[ \\u00a0][0-9]{3})*(?:[.,][0-9]{2})?)"
        + "\\s*(?:PLN|zł)\\b");

    private DesktopBankImporter() { }

    static Result read(Path file, String bankLabel) throws Exception {
        if (file == null || !Files.isRegularFile(file))
            throw new IllegalArgumentException("Nie znaleziono pliku wyciągu.");
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] bytes = Files.readAllBytes(file);
        String hash = sha256(bytes);

        if (name.endsWith(".pdf")) {
            if (bytes.length > MAX_PDF_BYTES)
                throw new IllegalArgumentException("PDF bankowy jest za duży (maks. 8 MB).");
            String text;
            try (PDDocument doc = PDDocument.load(bytes)) {
                text = new PDFTextStripper().getText(doc);
            }
            if (text == null || text.trim().isEmpty())
                throw new IllegalArgumentException(
                    "PDF nie zawiera warstwy tekstowej. Skan obrazu wymaga osobnego OCR.");
            String bank = detectBank(text);
            if ("VeloBank".equals(bank))
                return new Result(bank, "velo_pdf", parseVeloPdf(text), hash);
            String shown = bank.isBlank() ? "nieznany bank" : bank;
            throw new IllegalArgumentException(
                "Rozpoznano " + shown + ", ale ten układ PDF nie ma jeszcze bezpiecznego parsera. "
                + "Wyeksportuj CSV/XLSX albo użyj obsługiwanego PDF VeloBanku.");
        }

        if (name.endsWith(".xlsx")) {
            if (bytes.length > MAX_XLSX_BYTES)
                throw new IllegalArgumentException("XLSX jest za duży (maks. 4 MB).");
            String rows = xlsxTextRows(bytes);
            if (recognizesMbank(rows))
                return new Result("mBank", "mbank", parseMbank(rows), hash);
            String bank = cleanBankLabel(bankLabel);
            return new Result(bank, "csv", parseGenericCsv(rows, bank), hash);
        }

        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            if (bytes.length > MAX_CSV_BYTES)
                throw new IllegalArgumentException("CSV jest za duży (maks. 256 KB).");
            String text = decodeText(bytes);
            if (recognizesMbank(text))
                return new Result("mBank", "mbank", parseMbank(text), hash);
            String detected = detectBank(text);
            String bank = detected.isBlank() ? cleanBankLabel(bankLabel) : detected;
            return new Result(bank, "csv", parseGenericCsv(text, bank), hash);
        }

        throw new IllegalArgumentException("Obsługiwane pliki bankowe: PDF, CSV i XLSX.");
    }

    static Path archiveOriginalPdf(Path source, String sha256) throws Exception {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf")) return null;
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Nieprawidłowy skrót pliku.");
        Path dir = Path.of(System.getProperty("user.home"), ".edhome", "bank-documents");
        Files.createDirectories(dir);
        Path target = dir.resolve(sha256 + ".pdf");
        if (!Files.exists(target))
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    static String detectBank(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT)
            .replace('ł', 'l').replace('ó', 'o').replace('ś', 's')
            .replace('ą', 'a').replace('ę', 'e').replace('ż', 'z')
            .replace('ź', 'z').replace('ć', 'c').replace('ń', 'n');
        if (t.contains("velobank") || t.contains("velo bank")) return "VeloBank";
        if (t.contains("mbank")) return "mBank";
        if (t.contains("ing bank slaski") || t.contains("ing bank")) return "ING";
        if (t.contains("pko bank polski") || t.contains("pkobp") || t.contains("pko bp"))
            return "PKO BP";
        if (t.contains("bank pekao") || t.contains("pekao s.a")) return "Pekao";
        if (t.contains("santander")) return "Santander";
        if (t.contains("alior bank")) return "Alior Bank";
        if (t.contains("bank millennium") || t.contains("millennium")) return "Millennium";
        return "";
    }

    private static String cleanBankLabel(String bankLabel) {
        String bank = bankLabel == null ? "" : bankLabel.trim();
        if (bank.isEmpty() || bank.length() > 80)
            throw new IllegalArgumentException("Podaj nazwę banku (maks. 80 znaków).");
        return bank;
    }

    private static String decodeText(byte[] bytes) {
        String utf = new String(bytes, StandardCharsets.UTF_8);
        if (utf.indexOf('�') < 0) return utf;
        return new String(bytes, Charset.forName("windows-1250"));
    }

    private static List<Entry> parseGenericCsv(String csv, String bank) {
        if (csv == null || csv.getBytes(StandardCharsets.UTF_8).length > MAX_CSV_BYTES)
            throw new IllegalArgumentException("Wyciąg CSV jest za duży.");
        String[] lines = csv.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        if (lines.length < 2) throw new IllegalArgumentException("Wyciąg CSV jest pusty.");
        String headerLine = lines[0].replace("\uFEFF","");
        char delimiter = detectDelimiter(headerLine);
        List<String> header = fields(headerLine, delimiter);
        int date = position(header, "data","data księgowania","data operacji",
            "data transakcji","booking date","date");
        int amount = position(header, "kwota","kwota operacji","kwota [pln]",
            "amount","amount (pln)");
        int ref = position(header, "id transakcji","identyfikator transakcji",
            "identyfikator","identyfikator operacji","nr transakcji",
            "numer referencyjny","reference","transaction id");
        int desc = position(header, "opis","opis operacji","description",
            "tytuł","tytuł operacji","szczegóły");
        if (date < 0 || amount < 0 || ref < 0 || desc < 0)
            throw new IllegalArgumentException(
                "CSV wymaga kolumn: Data; Kwota; Id transakcji; Opis.");
        List<Entry> entries = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (int i=1; i<lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            if (entries.size() >= MAX_ROWS)
                throw new IllegalArgumentException("Maksymalnie 250 transakcji na plik.");
            List<String> row = fields(lines[i], delimiter);
            if (row.size() != header.size())
                throw new IllegalArgumentException("Błędna liczba kolumn CSV, wiersz " + (i+1));
            String booked = normalizeDate(row.get(date));
            String raw = row.get(amount).trim();
            boolean expense = raw.replace(" ","").startsWith("-");
            long grosz = parseMoneyAbsolute(raw);
            String reference = row.get(ref).trim();
            if (reference.length() < 4 || reference.length() > 160)
                throw new IllegalArgumentException(
                    "Brak stabilnego identyfikatora transakcji w wierszu " + (i+1));
            String description = row.get(desc).trim();
            if (description.length() > 300)
                throw new IllegalArgumentException("Za długi opis w wierszu " + (i+1));
            String key = sha256((bank.toLowerCase(Locale.ROOT) + "\n" + reference)
                .getBytes(StandardCharsets.UTF_8));
            if (!unique.add(key))
                throw new IllegalArgumentException("Powielony identyfikator bankowy w CSV.");
            entries.add(new Entry(booked, expense ? "expense" : "income",
                grosz, description, reference, key));
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("Brak transakcji w CSV.");
        return entries;
    }

    private static boolean recognizesMbank(String data) {
        if (data == null) return false;
        for (String line : data.replace("\r\n","\n").replace('\r','\n').split("\n")) {
            String trimmed = line.replace("\uFEFF","").trim();
            if (trimmed.startsWith("#Data księgowania;")
                    && trimmed.contains("#Data operacji;")
                    && trimmed.contains("#Kwota;")
                    && trimmed.contains("#Saldo po operacji")) return true;
        }
        return false;
    }

    private static List<Entry> parseMbank(String data) {
        String[] lines = data.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        int header = -1;
        String account = "";
        for (int i=0; i<lines.length; i++) {
            String trimmed = lines[i].replace("\uFEFF","").trim();
            if (trimmed.startsWith("#Numer rachunku;") || trimmed.startsWith("#Numer konta;")) {
                List<String> meta = fields(trimmed, ';');
                if (meta.size() > 1) account = meta.get(1).trim().replace(" ","");
            }
            if (trimmed.startsWith("#Data księgowania;")
                    && trimmed.contains("#Data operacji;")
                    && trimmed.contains("#Kwota;")
                    && trimmed.contains("#Saldo po operacji")) {
                header = i;
                break;
            }
        }
        if (header < 0) throw new IllegalArgumentException(
            "Nie znaleziono tabeli operacji mBanku (#Data księgowania).");
        List<String> names = fields(lines[header], ';');
        int booked = column(names, "data księgowania");
        int operated = column(names, "data operacji");
        int amount = column(names, "kwota");
        int description = column(names, "opis operacji");
        int title = column(names, "tytuł");
        int balance = column(names, "saldo po operacji");
        if (booked<0 || operated<0 || amount<0 || description<0 || balance<0)
            throw new IllegalArgumentException("Brakuje kolumn wyciągu mBanku.");

        List<Entry> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i=header+1; i<lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#Saldo końcowe") || line.startsWith("Niniejszy dokument"))
                break;
            if (line.startsWith("#")) continue;
            List<String> cells = fields(line, ';');
            while (cells.size() > names.size() && cells.get(cells.size()-1).trim().isEmpty())
                cells.remove(cells.size()-1);
            if (cells.size() != names.size())
                throw new IllegalArgumentException(
                    "mBank: niezgodna liczba kolumn w wierszu " + (i+1));
            String bookedOn = normalizeMbankDate(cells.get(booked));
            String operatedOn = normalizeMbankDate(cells.get(operated));
            String rawAmount = cells.get(amount).trim();
            boolean expense = rawAmount.replace(" ","").startsWith("-");
            long grosz = parseMoneyAbsolute(rawAmount);
            if (grosz == 0)
                throw new IllegalArgumentException("mBank: zerowa kwota w wierszu " + (i+1));
            String rawBalance = cells.get(balance).trim();
            if (rawBalance.isEmpty())
                throw new IllegalArgumentException(
                    "mBank: brak salda po operacji w wierszu " + (i+1));
            boolean balanceNegative = rawBalance.replace(" ","").startsWith("-");
            long balanceGrosz = parseMoneyAbsolute(rawBalance);
            String desc = cells.get(description).trim();
            String details = title < 0 ? "" : cells.get(title).trim();
            String shown = (desc + (details.isEmpty() ? "" : " • " + details)).trim();
            if (shown.isEmpty()) shown = "Operacja mBank";
            if (shown.length() > 300) shown = shown.substring(0,300);
            String stable = "mbank\n" + account + "\n" + bookedOn + "\n" + operatedOn
                + "\n" + (expense ? "-" : "+") + grosz + "\n"
                + (balanceNegative ? "-" : "+") + balanceGrosz + "\n" + desc + "\n" + details;
            String key = sha256(stable.getBytes(StandardCharsets.UTF_8));
            if (!keys.add(key))
                throw new IllegalArgumentException(
                    "mBank: nierozróżnialne operacje w wierszu " + (i+1));
            result.add(new Entry(bookedOn, expense ? "expense" : "income",
                grosz, shown, "mBank / wiersz " + (i+1), key));
            if (result.size() > MAX_ROWS)
                throw new IllegalArgumentException("mBank: maksymalnie 250 transakcji.");
        }
        if (result.isEmpty()) throw new IllegalArgumentException("mBank: brak transakcji.");
        return result;
    }

    private static List<Entry> parseVeloPdf(String text) {
        if (!"VeloBank".equals(detectBank(text)))
            throw new IllegalArgumentException("PDF nie został rozpoznany jako VeloBank.");
        String normalized = normalizeText(text);
        List<Entry> labeled = parseVeloLabeled(normalized);
        if (!labeled.isEmpty()) return labeled;

        String[] lines = normalized.split("\n");
        List<Entry> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher dateMatcher = DATE.matcher(line);
            if (!dateMatcher.find()) continue;
            List<String> moneyTokens = new ArrayList<>();
            Matcher mm = MONEY.matcher(line);
            while (mm.find()) moneyTokens.add(mm.group(1).trim());
            if (moneyTokens.isEmpty()) continue;

            String chosen = null;
            for (String token : moneyTokens) {
                String t = token.replace(" ","").replace("\u00a0","");
                if (t.startsWith("-") || t.startsWith("−") || t.startsWith("+")) {
                    if (chosen != null) { chosen = null; break; }
                    chosen = token;
                }
            }
            if (chosen == null) {
                if (moneyTokens.size() != 1) continue;
                chosen = moneyTokens.get(0);
            }
            ParsedAmount parsed;
            try { parsed = parseVeloAmount(chosen, line); }
            catch (IllegalArgumentException ambiguous) { continue; }
            String booked = normalizeDate(dateMatcher.group(1));
            String description = line
                .replace(dateMatcher.group(1), " ")
                .replace(chosen, " ")
                .replaceAll("(?iu)\\b(?:PLN|zł)\\b", " ")
                .replaceAll("\\s+", " ").trim();
            if (description.length() < 2) description = "Operacja VeloBank";
            if (description.length() > 300) description = description.substring(0,300);
            String canonical = booked + "|" + parsed.kind + "|" + parsed.grosz + "|"
                + canonical(line);
            String key = sha256(("velobank-pdf-row\n" + canonical)
                .getBytes(StandardCharsets.UTF_8));
            if (!unique.add(key))
                throw new IllegalArgumentException(
                    "PDF VeloBanku zawiera nierozróżnialne powtórzone wiersze.");
            result.add(new Entry(booked, parsed.kind, parsed.grosz, description,
                "VeloBank PDF", key));
            if (result.size() > MAX_ROWS)
                throw new IllegalArgumentException("PDF VeloBanku: maksymalnie 250 operacji.");
        }
        if (result.isEmpty())
            throw new IllegalArgumentException(
                "Nie znaleziono jednoznacznych operacji w tekstowym PDF VeloBanku.");
        return result;
    }

    private static List<Entry> parseVeloLabeled(String text) {
        Matcher dm = LABELED_DATE.matcher(text);
        Matcher am = LABELED_AMOUNT.matcher(text);
        if (!dm.find() || !am.find()) return java.util.Collections.emptyList();
        String date = normalizeDate(dm.group(1));
        ParsedAmount amount = parseVeloAmount(am.group(1), text);
        String desc = field(text, "Odbiorca","Akceptant","Tytuł","Opis");
        if (desc.isEmpty()) desc = "Dokument VeloBank";
        String key = sha256(("velobank-pdf-confirmation\n" + canonical(text))
            .getBytes(StandardCharsets.UTF_8));
        List<Entry> one = new ArrayList<>();
        one.add(new Entry(date, amount.kind, amount.grosz,
            trim(desc,300), "VeloBank PDF", key));
        return one;
    }

    private static ParsedAmount parseVeloAmount(String raw, String context) {
        String cleaned = raw.replace(" ","").replace("\u00a0","").replace('−','-');
        boolean explicitExpense = cleaned.startsWith("-");
        boolean explicitIncome = cleaned.startsWith("+");
        if (explicitExpense || explicitIncome) cleaned = cleaned.substring(1);
        long grosz = parseMoneyAbsolute(cleaned);
        String lower = context.toLowerCase(Locale.ROOT);
        boolean expense = explicitExpense || lower.matches(
            "(?s).*(?:transakcj[aeęąi]*\\s+kart|płatnoś|zakup|obciąż|wypłat|"
            + "przelew wychodzący|przelew wysłan).*");
        boolean income = explicitIncome || lower.matches(
            "(?s).*(?:wpływ|uznan|wpłat|przelew przychodzący|przelew otrzymany|otrzyman).*");
        if (expense == income)
            throw new IllegalArgumentException("Nie można ustalić kierunku operacji VeloBank.");
        return new ParsedAmount(expense ? "expense" : "income", grosz);
    }

    private static long parseMoneyAbsolute(String raw) {
        String value = raw == null ? "" : raw.trim()
            .replace("\u00a0","").replace(" ","")
            .replace("PLN","").replace("pln","").replace("zł","")
            .replace('−','-');
        if (value.startsWith("+") || value.startsWith("-")) value = value.substring(1);
        value = value.replace(',', '.');
        if (!value.matches("[0-9]{1,11}(?:[.][0-9]{1,2})?"))
            throw new IllegalArgumentException("Nieprawidłowa kwota: " + raw);
        java.math.BigDecimal amount = new java.math.BigDecimal(value).setScale(
            2, java.math.RoundingMode.UNNECESSARY);
        long grosz = amount.movePointRight(2).longValueExact();
        if (grosz < 0 || grosz > 99_999_999_999L)
            throw new IllegalArgumentException("Kwota poza zakresem.");
        return grosz;
    }

    private static String normalizeMbankDate(String raw) {
        String value = raw.trim();
        try {
            LocalDate parsed;
            if (value.matches("[0-9]{4,6}(?:[.]0+)?")) {
                long serial = Long.parseLong(value.replaceFirst("[.]0+$",""));
                if (serial < 25569 || serial > 73415)
                    throw new IllegalArgumentException("Excel date outside 1970-2100");
                parsed = LocalDate.of(1899,12,30).plusDays(serial);
            } else parsed = value.contains(".") ? LocalDate.parse(value, PL)
                : LocalDate.parse(value);
            if (parsed.getYear() < 1970 || parsed.getYear() > 2100)
                throw new IllegalArgumentException("Data poza zakresem.");
            return parsed.toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("mBank: nieprawidłowa data " + value);
        }
    }

    private static String normalizeDate(String value) {
        try {
            String text = value.trim();
            LocalDate parsed = text.contains(".") ? LocalDate.parse(text, PL)
                : LocalDate.parse(text);
            if (parsed.getYear() < 1970 || parsed.getYear() > 2100)
                throw new IllegalArgumentException("Data poza zakresem.");
            return parsed.toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("Błędna data wyciągu: " + value);
        }
    }

    private static int position(List<String> cols, String... names) {
        for (int i=0; i<cols.size(); i++) {
            String label = cols.get(i).trim().replaceFirst("^#","")
                .toLowerCase(Locale.ROOT);
            for (String name : names) if (name.equals(label)) return i;
        }
        return -1;
    }

    private static int column(List<String> names, String searched) {
        return position(names, searched);
    }

    private static char detectDelimiter(String header) {
        for (char candidate : new char[]{';','\t',','}) {
            List<String> columns = fields(header, candidate);
            if (position(columns,"data","data księgowania","data operacji",
                    "data transakcji","booking date","date") >= 0
                    && position(columns,"kwota","kwota operacji","kwota [pln]",
                        "amount","amount (pln)") >= 0)
                return candidate;
        }
        throw new IllegalArgumentException("Nieznany układ CSV.");
    }

    private static List<String> fields(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i+1 < line.length() && line.charAt(i+1) == '"') {
                    current.append('"'); i++;
                } else quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Niepoprawny cudzysłów w pliku.");
        values.add(current.toString());
        return values;
    }

    private static String xlsxTextRows(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K'
                || content[2] != 3 || content[3] != 4)
            throw new IllegalArgumentException("To nie jest XLSX.");
        byte[] sheet = null, shared = null;
        int files = 0, total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++files > 150)
                    throw new IllegalArgumentException("Zbyt dużo elementów arkusza.");
                String name = entry.getName();
                if (!"xl/sharedStrings.xml".equals(name)
                        && !name.startsWith("xl/worksheets/sheet")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_XLSX_BYTES)
                        throw new IllegalArgumentException("Za duży arkusz po rozpakowaniu.");
                    out.write(buffer,0,n);
                }
                if ("xl/sharedStrings.xml".equals(name)) shared = out.toByteArray();
                if (name.matches("xl/worksheets/sheet[0-9]+[.]xml") && sheet == null)
                    sheet = out.toByteArray();
            }
        } catch (IllegalArgumentException invalid) { throw invalid; }
        catch (Exception error) {
            throw new IllegalArgumentException("Nie można odczytać struktury XLSX.", error);
        }
        if (sheet == null) throw new IllegalArgumentException("XLSX nie zawiera arkusza.");

        List<String> strings = new ArrayList<>();
        if (shared != null) {
            NodeList nodes = parseXml(shared).getElementsByTagName("si");
            for (int i=0; i<nodes.getLength(); i++)
                strings.add(texts((Element)nodes.item(i), "t"));
        }
        NodeList rows = parseXml(sheet).getElementsByTagName("row");
        StringBuilder result = new StringBuilder();
        for (int r=0; r<rows.getLength(); r++) {
            Element row = (Element)rows.item(r);
            NodeList cells = row.getElementsByTagName("c");
            List<String> values = new ArrayList<>();
            boolean any = false;
            for (int ci=0; ci<cells.getLength(); ci++) {
                Element cell = (Element)cells.item(ci);
                String type = cell.getAttribute("t");
                String value = texts(cell, "v");
                if ("s".equals(type)) {
                    int index = Integer.parseInt(value);
                    if (index < 0 || index >= strings.size())
                        throw new IllegalArgumentException("Brak wartości tekstowej XLSX.");
                    value = strings.get(index);
                } else if ("inlineStr".equals(type)) value = texts(cell, "t");
                int column = columnIndex(cell.getAttribute("r"), ci);
                while (values.size() <= column) values.add("");
                values.set(column, value);
                if (!value.isEmpty()) any = true;
            }
            if (!any) continue;
            while (values.size() > 1 && values.get(values.size()-1).isEmpty())
                values.remove(values.size()-1);
            for (int i=0; i<values.size(); i++) {
                if (i > 0) result.append(';');
                appendSemicolonField(result, values.get(i));
            }
            result.append('\n');
            if (result.length() > MAX_CSV_BYTES)
                throw new IllegalArgumentException("Za dużo danych w XLSX.");
        }
        return result.toString();
    }

    private static Document parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception error) {
            throw new IllegalArgumentException("Niebezpieczny lub błędny XML XLSX.", error);
        }
    }

    private static String texts(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        StringBuilder value = new StringBuilder();
        for (int i=0; i<nodes.getLength(); i++)
            value.append(nodes.item(i).getTextContent());
        return value.toString();
    }

    private static int columnIndex(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) return fallback;
        int value = 0, letters = 0;
        for (int i=0; i<ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch>='A' && ch<='Z') { value=value*26+(ch-'A'+1); letters++; }
            else if (ch>='a' && ch<='z') { value=value*26+(ch-'a'+1); letters++; }
            else break;
        }
        return letters == 0 ? fallback : value - 1;
    }

    private static void appendSemicolonField(StringBuilder out, String value) {
        if (value.indexOf(';')<0 && value.indexOf('"')<0
                && value.indexOf('\n')<0 && value.indexOf('\r')<0) {
            out.append(value);
            return;
        }
        out.append('"').append(value.replace("\"","\"\"")).append('"');
    }

    private static String field(String text, String... labels) {
        String[] lines = text.split("\n");
        for (String label : labels) for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            String needle = label.toLowerCase(Locale.ROOT);
            int at = lower.indexOf(needle);
            if (at < 0) continue;
            String value = line.substring(at + label.length())
                .replaceFirst("^[\\s:–—-]+","").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String normalizeText(String text) {
        return text.replace("\r\n","\n").replace('\r','\n')
            .replace('\u00a0',' ').replaceAll("[ \\t]+"," ")
            .replaceAll("\\n{3,}","\n\n").trim();
    }

    private static String canonical(String text) {
        return normalizeText(text).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }

    private static String trim(String value, int max) {
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0,max);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder out = new StringBuilder(64);
            for (byte b : hash) out.append(String.format(Locale.ROOT,"%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class ParsedAmount {
        final String kind;
        final long grosz;
        ParsedAmount(String kind, long grosz) {
            this.kind = kind;
            this.grosz = grosz;
        }
    }
}
