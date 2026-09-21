package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;

/** Opt-in online enrichment. The pantry database and scanner work without network. */
final class PantryProductLookup {
    private static final int MAX_JSON_BYTES = 96 * 1024;
    private static final int MAX_IMAGE_BYTES = 640 * 1024;
    // Public Open Facts projects provide different categories of household goods.
    private static final String[][] CATALOGUES = {
        {"world.openfoodfacts.org", "Open Food Facts"},
        {"world.openproductsfacts.org", "Open Products Facts"},
        {"world.openbeautyfacts.org", "Open Beauty Facts"},
        {"world.openpetfoodfacts.org", "Open Pet Food Facts"}
    };
    private static final String[] IMAGE_HOSTS = {
        "images.openfoodfacts.org", "images.openproductsfacts.org",
        "images.openbeautyfacts.org", "images.openpetfoodfacts.org"
    };
    private static final String USER_AGENT =
        "EDHOME-Android/0.4.0 (https://github.com/edwinkarolczyk/Edhome)";
    private PantryProductLookup() { }

    static final class Product {
        final String name;
        final String source;
        final String brand;
        final String imageUrl;
        final byte[] image;
        Product(String name, String source, String brand, String imageUrl, byte[] image) {
            this.name = name;
            this.source = source;
            this.brand = brand;
            this.imageUrl = imageUrl;
            this.image = image;
        }
    }

    static boolean safeImageUrl(String candidate) {
        if (candidate == null || candidate.length() > 1200) return false;
        try {
            URL u = new URL(candidate);
            if (!"https".equalsIgnoreCase(u.getProtocol())
                    || (u.getPort() != -1 && u.getPort() != 443)
                    || u.getUserInfo() != null) return false;
            for (String allowed : IMAGE_HOSTS)
                if (allowed.equalsIgnoreCase(u.getHost())) return true;
            return false;
        } catch (Exception ignored) { return false; }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim().replace('\n', ' ').replace('\r', ' ');
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean allowedHttps(URL u, boolean image) {
        if (!"https".equalsIgnoreCase(u.getProtocol())
                || (u.getPort() != -1 && u.getPort() != 443)
                || u.getUserInfo() != null) return false;
        if (image) return safeImageUrl(u.toString());
        for (String[] entry : CATALOGUES)
            if (entry[0].equalsIgnoreCase(u.getHost())) return true;
        return false;
    }

    /** Follow only verified HTTPS redirects to an allowed Open Facts server. */
    private static byte[] get(URL url, int maximum, boolean image) throws Exception {
        URL current = url;
        for (int hop = 0; hop <= 3; hop++) {
            if (!allowedHttps(current, image))
                throw new java.io.IOException("Przekierowanie poza zaufane serwery Open Facts.");
            HttpURLConnection c = (HttpURLConnection) current.openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", image ? "image/jpeg,image/png,image/webp"
                : "application/json");
            c.setInstanceFollowRedirects(false);
            try {
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303
                        || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null || location.isEmpty())
                        throw new java.io.IOException("Przekierowanie bez adresu.");
                    current = new URL(current, location);
                    continue;
                }
                if (!image && (code == 404 || code == 410)) return null;
                if (code != 200) throw new java.io.IOException("HTTP " + code);
                if (image) {
                    String contentType = c.getContentType();
                    if (contentType == null || !(contentType.startsWith("image/jpeg")
                            || contentType.startsWith("image/png")
                            || contentType.startsWith("image/webp")))
                        throw new java.io.IOException("Nieobsługiwany format zdjęcia.");
                }
                if (c.getContentLengthLong() > maximum)
                    throw new java.io.IOException("Odpowiedź przekracza limit.");
                try (InputStream input = c.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (output.size() + n > maximum)
                            throw new java.io.IOException("Odpowiedź przekracza limit.");
                        output.write(buffer, 0, n);
                    }
                    return output.toByteArray();
                }
            } finally { c.disconnect(); }
        }
        throw new java.io.IOException("Zbyt wiele przekierowań serwera.");
    }

    interface Progress {
        void catalogue(String label, String result);
    }

    static final class Report {
        final Product product;
        final String details;
        final boolean partialFailure;
        Report(Product product, String details, boolean partialFailure) {
            this.product = product;
            this.details = details;
            this.partialFailure = partialFailure;
        }
    }

    private static final class Attempt {
        final Product product;
        final boolean unnamed;
        Attempt(Product product, boolean unnamed) {
            this.product = product;
            this.unnamed = unnamed;
        }
    }

    /** Each project gets a separate, visible result; a failure never masks later sources. */
    static Report lookupDetailed(String barcode, Progress progress) {
        List<String> alternatives = PantryLookupCodes.candidates(barcode);
        List<String> results = new ArrayList<>();
        boolean partialFailure = false;
        for (String[] catalogue : CATALOGUES) {
            String label = catalogue[1];
            if (progress != null) progress.catalogue(label, "sprawdzam…");
            boolean unnamed = false;
            boolean failed = false;
            String lastError = "";
            Product found = null;
            int attempted = 0;
            for (String candidate : alternatives) {
                attempted++;
                try {
                    Attempt attempt = lookupOne(candidate, catalogue[0], label);
                    if (attempt.product != null) {
                        found = attempt.product;
                        break;
                    }
                    unnamed |= attempt.unnamed;
                } catch (Exception error) {
                    failed = true;
                    lastError = shortError(error);
                    break; // Do not retry a failed server using more aliases.
                }
            }
            String status;
            if (found != null) status = "znaleziono";
            else if (failed) {
                status = "problem (" + lastError + ")";
                partialFailure = true;
            } else if (unnamed) status = "rekord bez nazwy";
            else status = "brak rekordu";
            String line = label + ": " + status + " • wariantów kodu: " + attempted;
            results.add(line);
            if (progress != null) progress.catalogue(label, status);
            // Diagnostic event contains no product barcode or personal pantry data.
            DiagnosticLog.event("PANTRY_CATALOGUE_" + catalogueKey(label) + "_"
                + (found != null ? "FOUND" : failed ? "ERROR"
                    : unnamed ? "UNNAMED" : "NOT_FOUND"));
            if (found != null) return new Report(found,
                joinResults(results), partialFailure);
        }
        return new Report(null, joinResults(results), partialFailure);
    }

    static Product lookup(String barcode) throws Exception {
        Report report = lookupDetailed(barcode, null);
        if (report.partialFailure && report.product == null)
            throw new java.io.IOException(report.details);
        return report.product;
    }

    private static String catalogueKey(String name) {
        return name.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    private static String shortError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) return "brak połączenia";
        message = message.replace('\n', ' ').replace('\r', ' ');
        return message.length() > 65 ? message.substring(0, 65) : message;
    }

    private static String joinResults(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (result.length() != 0) result.append("\n");
            result.append(line);
        }
        return result.toString();
    }

    private static Attempt lookupOne(String barcode, String host, String source)
            throws Exception {
        URL endpoint = new URL("https://" + host + "/api/v2/product/"
            + barcode + ".json?fields=code,product_name_pl,product_name,"
            + "product_name_en,generic_name_pl,generic_name,brands,"
            + "image_front_url,image_url");
        byte[] result = get(endpoint, MAX_JSON_BYTES, false);
        if (result == null) return new Attempt(null, false);
        JSONObject root = new JSONObject(new String(result, StandardCharsets.UTF_8));
        if (root.optInt("status", 0) != 1 || root.optJSONObject("product") == null)
            return new Attempt(null, false);
        JSONObject row = root.getJSONObject("product");
        String name = clip(row.optString("product_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name_en", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name", ""), 160);
        if (name.isEmpty()) return new Attempt(null, true);
        String brand = clip(row.optString("brands", ""), 100);
        String url = row.optString("image_front_url", "");
        if (!safeImageUrl(url)) url = row.optString("image_url", "");
        if (!safeImageUrl(url)) url = "";
        byte[] image = null;
        if (!url.isEmpty()) {
            try { image = get(new URL(url), MAX_IMAGE_BYTES, true); }
            catch (Exception ignored) { /* A photo must not hide a recognized name. */ }
        }
        return new Attempt(new Product(name, source, brand, url, image), false);
    }


    /** User-initiated name search; never writes inventory or links a search-result GTIN. */
    static final class NameSearchReport {
        final List<Product> products;
        final String details;
        final boolean partialFailure;
        NameSearchReport(List<Product> products, String details, boolean partialFailure) {
            this.products = products;
            this.details = details;
            this.partialFailure = partialFailure;
        }
    }

    static NameSearchReport searchByName(String rawQuery, Progress progress) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 3 || query.length() > 80)
            throw new IllegalArgumentException("Wpisz od 3 do 80 znaków nazwy.");
        List<Product> results = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        boolean partialFailure = false;
        for (String[] catalogue : CATALOGUES) {
            String label = catalogue[1];
            if (progress != null) progress.catalogue(label, "sprawdzam…");
            int found = 0;
            String status;
            try {
                // API v2 search support can vary across projects; errors are reported per source.
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                URL endpoint = new URL("https://" + catalogue[0]
                    + "/api/v2/search?search_terms=" + encoded
                    + "&page_size=6&fields=code,product_name_pl,product_name,"
                    + "product_name_en,generic_name_pl,generic_name,brands,"
                    + "image_front_url,image_url");
                byte[] response = get(endpoint, MAX_JSON_BYTES, false);
                JSONObject root = response == null ? null
                    : new JSONObject(new String(response, StandardCharsets.UTF_8));
                JSONArray products = root == null ? null : root.optJSONArray("products");
                if (products != null) {
                    for (int i = 0; i < products.length(); i++) {
                        JSONObject row = products.optJSONObject(i);
                        if (row == null) continue;
                        String name = readProductName(row);
                        if (name.isEmpty()) continue;
                        String brand = clip(row.optString("brands", ""), 100);
                        String imageUrl = readImageUrl(row);
                        results.add(new Product(name, label, brand, imageUrl, null));
                        found++;
                    }
                }
                status = found == 0 ? "brak nazw" : "wyników: " + found;
            } catch (Exception error) {
                status = "problem (" + shortError(error) + ")";
                partialFailure = true;
            }
            statuses.add(label + ": " + status);
            if (progress != null) progress.catalogue(label, status);
            DiagnosticLog.event("PANTRY_NAME_SEARCH_" + catalogueKey(label)
                + (status.startsWith("problem") ? "_ERROR" : "_COMPLETE"));
        }
        return new NameSearchReport(results, joinResults(statuses), partialFailure);
    }

    private static String readProductName(JSONObject row) {
        String name = clip(row.optString("product_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name_en", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name", ""), 160);
        return name;
    }

    private static String readImageUrl(JSONObject row) {
        String url = row.optString("image_front_url", "");
        if (!safeImageUrl(url)) url = row.optString("image_url", "");
        return safeImageUrl(url) ? url : "";
    }

    static byte[] fetchImage(String imageUrl) throws Exception {
        if (!safeImageUrl(imageUrl))
            throw new IllegalArgumentException("Niebezpieczny adres zdjęcia.");
        return get(new URL(imageUrl), MAX_IMAGE_BYTES, true);
    }

    private static File photo(Context context, String url) throws Exception {
        if (!safeImageUrl(url)) throw new IllegalArgumentException("Bad image URL");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : hash) name.append(String.format(java.util.Locale.ROOT,
            "%02x", b & 0xff));
        File dir = new File(context.getFilesDir(), "pantry-photo-cache");
        if (!dir.exists() && !dir.mkdirs())
            throw new java.io.IOException("Nie można utworzyć pamięci zdjęć.");
        return new File(dir, name + ".img");
    }

    static void cache(Context context, String url, byte[] image) throws Exception {
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES) return;
        File file = photo(context, url);
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(image);
            output.flush();
        }
        if (!temp.renameTo(file)) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(image);
            }
            if (!temp.delete()) temp.deleteOnExit();
        }
    }

    static Bitmap cached(Context context, String url) {
        if (!safeImageUrl(url)) return null;
        try {
            File file = photo(context, url);
            if (!file.isFile() || file.length() > MAX_IMAGE_BYTES) return null;
            return decode(file);
        } catch (Exception ignored) { return null; }
    }

    static Bitmap thumbnail(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options option = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512)
            sample *= 2;
        option.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, option);
    }

    private static Bitmap decode(File file) throws Exception {
        if (file.length() > MAX_IMAGE_BYTES) return null;
        byte[] data;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096]; int n;
            while ((n = input.read(block)) != -1) {
                if (output.size() + n > MAX_IMAGE_BYTES) return null;
                output.write(block, 0, n);
            }
            data = output.toByteArray();
        }
        return thumbnail(data);
    }
}
