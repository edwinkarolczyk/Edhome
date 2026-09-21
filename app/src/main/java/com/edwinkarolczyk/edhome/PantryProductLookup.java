package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    private static byte[] get(URL url, int maximum, boolean image) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept", image ? "image/jpeg,image/png,image/webp"
            : "application/json");
        c.setInstanceFollowRedirects(false);
        try {
            int responseCode = c.getResponseCode();
            if (!image && responseCode == 404) return null;
            if (responseCode != 200)
                throw new java.io.IOException("HTTP " + responseCode);
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

    /** Search food, household/cleaning goods, beauty and pet food in order.
     * A missing record must NOT prevent searching the next catalogue.
     * Transmit only the product barcode after an explicit user action.
     */
    static Product lookup(String barcode) throws Exception {
        if (!PantryScanRules.validBarcode(barcode))
            throw new IllegalArgumentException("Nieprawidłowy kod.");
        Exception lastFailure = null;
        int inaccessibleCatalogues = 0;
        for (String[] catalogue : CATALOGUES) {
            try {
                Product found = lookupOne(barcode, catalogue[0], catalogue[1]);
                if (found != null) return found;
            } catch (Exception error) {
                lastFailure = error;
                inaccessibleCatalogues++;
            }
        }
        if (inaccessibleCatalogues > 0)
            throw new java.io.IOException(
                "Nie udało się przeszukać wszystkich baz.", lastFailure);
        return null;
    }

    private static Product lookupOne(String barcode, String host, String source)
            throws Exception {
        URL endpoint = new URL("https://" + host + "/api/v2/product/"
            + barcode + ".json?fields=code,product_name_pl,product_name,"
            + "product_name_en,generic_name_pl,generic_name,brands,"
            + "image_front_url,image_url");
        byte[] result = get(endpoint, MAX_JSON_BYTES, false);
        if (result == null) return null;
        JSONObject root = new JSONObject(new String(result, StandardCharsets.UTF_8));
        if (root.optInt("status", 0) != 1 || root.optJSONObject("product") == null)
            return null;
        JSONObject row = root.getJSONObject("product");
        String name = clip(row.optString("product_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("product_name_en", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name_pl", ""), 160);
        if (name.isEmpty()) name = clip(row.optString("generic_name", ""), 160);
        if (name.isEmpty()) return null;
        String brand = clip(row.optString("brands", ""), 100);
        String url = row.optString("image_front_url", "");
        if (!safeImageUrl(url)) url = row.optString("image_url", "");
        if (!safeImageUrl(url)) url = "";
        byte[] image = null;
        if (!url.isEmpty()) {
            try { image = get(new URL(url), MAX_IMAGE_BYTES, true); }
            catch (Exception ignored) { /* Product name remains useful without a photo. */ }
        }
        return new Product(name, source, brand, url, image);
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
