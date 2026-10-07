package com.edwinkarolczyk.edhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimalny klient SUPLA Cloud dla etapu 0.8.0: wyłącznie odczyt.
 */
final class SuplaCloudClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final String API_VERSION = "3";

    static final class Snapshot {
        final String baseUrl;
        final long fetchedAt;
        final JSONArray channels;

        Snapshot(String baseUrl, long fetchedAt, JSONArray channels) {
            this.baseUrl = baseUrl;
            this.fetchedAt = fetchedAt;
            this.channels = channels;
        }
    }

    private SuplaCloudClient() {}

    static String normalizeBaseUrl(String raw) {
        try {
            String text = raw == null ? "" : raw.trim();
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null || host.isBlank())
                throw new IllegalArgumentException("Adres SUPLA musi używać HTTPS.");
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Podaj sam adres serwera SUPLA, bez loginu i parametrów.");
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path))
                throw new IllegalArgumentException("Podaj główny adres serwera SUPLA, bez dodatkowej ścieżki.");
            int port = uri.getPort();
            String out = "https://" + host.toLowerCase(java.util.Locale.ROOT)
                + (port > 0 && port != 443 ? ":" + port : "");
            return out;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Nieprawidłowy adres SUPLA Cloud.");
        }
    }

    static Snapshot fetchChannels(String baseUrl, String token) throws Exception {
        String endpoint = normalizeBaseUrl(baseUrl) + "/api/channels?include=state";
        String cleanToken = token == null ? "" : token.trim();
        if (cleanToken.length() < 16)
            throw new IllegalArgumentException("Najpierw zapisz Personal Access Token SUPLA.");

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Accept-Version", API_VERSION);
        connection.setRequestProperty("Authorization", "Bearer " + cleanToken);
        try {
            int status = connection.getResponseCode();
            if (status == 401 || status == 403)
                throw new IllegalStateException("SUPLA odrzuciła token lub jego uprawnienia.");
            if (status >= 300 && status < 400)
                throw new IllegalStateException("SUPLA zwróciła przekierowanie. Sprawdź adres serwera.");
            if (status != 200)
                throw new IllegalStateException("SUPLA Cloud odpowiedziała HTTP " + status + ".");

            long declared = connection.getContentLength();
            if (declared > MAX_RESPONSE_BYTES)
                throw new IllegalStateException("Odpowiedź SUPLA jest zbyt duża.");

            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readBounded(input);
            }
            Object parsed = new JSONTokener(
                new String(bytes, StandardCharsets.UTF_8)).nextValue();
            JSONArray channels;
            if (parsed instanceof JSONArray) {
                channels = (JSONArray) parsed;
            } else if (parsed instanceof JSONObject
                    && ((JSONObject) parsed).optJSONArray("channels") != null) {
                channels = ((JSONObject) parsed).getJSONArray("channels");
            } else {
                throw new IllegalStateException("SUPLA zwróciła nieoczekiwany format listy kanałów.");
            }
            return new Snapshot(normalizeBaseUrl(baseUrl),
                System.currentTimeMillis(), channels);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_RESPONSE_BYTES)
                throw new IllegalStateException("Odpowiedź SUPLA jest zbyt duża.");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
