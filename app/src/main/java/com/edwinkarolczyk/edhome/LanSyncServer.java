package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.util.Base64;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/** Beta-only local-LAN snapshot endpoint for EDHOME Desktop with guarded write-back. */
final class LanSyncServer {
    static final int PORT = 45823;
    static final String TOKEN_PREF = "desktop_sync_token";

    interface SnapshotProvider { String snapshot() throws Exception; }
    interface RestoreProvider { void restore(String snapshot) throws Exception; }

    private final String token;
    private final SnapshotProvider provider;
    private final RestoreProvider restoreProvider;
    private final Object writeLock = new Object();
    private volatile boolean running;
    private volatile ServerSocket server;
    private Thread acceptThread;

    LanSyncServer(String token, SnapshotProvider provider,
            RestoreProvider restoreProvider) {
        this.token = token;
        this.provider = provider;
        this.restoreProvider = restoreProvider;
    }

    static String ensureToken(SharedPreferences prefs) {
        String existing = prefs.getString(TOKEN_PREF, "");
        if (existing != null && existing.length() >= 10) return existing;
        byte[] random = new byte[9];
        new SecureRandom().nextBytes(random);
        String created = Base64.encodeToString(random,
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        if (!prefs.edit().putString(TOKEN_PREF, created).commit())
            throw new IllegalStateException("Nie zapisano kodu parowania PC.");
        return created;
    }

    synchronized void start() {
        if (acceptThread != null && acceptThread.isAlive()) return;
        acceptThread = null;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "edhome-lan-sync");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    synchronized void stop() {
        running = false;
        ServerSocket current = server;
        server = null;
        if (current != null) {
            try { current.close(); } catch (Exception ignored) { }
        }
        Thread thread = acceptThread;
        acceptThread = null;
        if (thread != null) thread.interrupt();
    }

    synchronized boolean isRunning() {
        return running && server != null && !server.isClosed();
    }

    private void acceptLoop() {
        try {
            ServerSocket listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(PORT));
            server = listener;
            DiagnosticLog.event("DESKTOP_SYNC_LISTENING", "port=" + PORT);
            while (running) {
                Socket socket = listener.accept();
                Thread worker = new Thread(() -> handle(socket), "edhome-lan-client");
                worker.setDaemon(true);
                worker.start();
            }
        } catch (Exception error) {
            if (running) DiagnosticLog.error("DESKTOP_SYNC_SERVER", error);
        } finally {
            running = false;
            ServerSocket current = server;
            server = null;
            if (current != null) {
                try { current.close(); } catch (Exception ignored) { }
            }
            synchronized (this) {
                if (acceptThread == Thread.currentThread()) acceptThread = null;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket peer = socket) {
            peer.setSoTimeout(8000);
            InetAddress remote = peer.getInetAddress();
            if (remote == null
                    || (!remote.isSiteLocalAddress() && !remote.isLoopbackAddress())) {
                reply(peer, 403, "{\"error\":\"LAN_ONLY\"}");
                return;
            }

            InputStream in = peer.getInputStream();
            String request = readLine(in, 4096);
            if (request == null || request.isEmpty()) {
                reply(peer, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }

            String supplied = "";
            String baseSha = "";
            int contentLength = 0;
            int headerBytes = request.length();
            while (true) {
                String line = readLine(in, 8192);
                if (line == null) {
                    reply(peer, 400, "{\"error\":\"BAD_HEADERS\"}");
                    return;
                }
                if (line.isEmpty()) break;
                headerBytes += line.length();
                if (headerBytes > 32768) {
                    reply(peer, 431, "{\"error\":\"HEADERS_TOO_LARGE\"}");
                    return;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if ("x-edhome-token".equals(name)) supplied = value;
                else if ("x-edhome-base-sha256".equals(name)) baseSha = value;
                else if ("content-length".equals(name)) {
                    try { contentLength = Integer.parseInt(value); }
                    catch (NumberFormatException ignored) { contentLength = -1; }
                }
            }

            if (!constantTimeEquals(token, supplied)) {
                DiagnosticLog.event("DESKTOP_SYNC_DENIED");
                reply(peer, 401, "{\"error\":\"PAIRING_REQUIRED\"}");
                return;
            }

            String[] parts = request.split(" ");
            if (parts.length < 2) {
                reply(peer, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }
            String method = parts[0];
            String path = parts[1];

            if ("GET".equals(method) && "/status".equals(path)) {
                reply(peer, 200, "{\"ok\":true,\"mode\":\"read-write\",\"version\":\""
                    + json(BuildConfig.VERSION_NAME) + "\",\"port\":" + PORT + "}");
                return;
            }

            if ("GET".equals(method) && "/snapshot".equals(path)) {
                String snapshot = provider.snapshot();
                if (snapshot.getBytes(StandardCharsets.UTF_8).length > DataBackup.MAX_BYTES) {
                    reply(peer, 413, "{\"error\":\"SNAPSHOT_TOO_LARGE\"}");
                    return;
                }
                reply(peer, 200, snapshot, sha256(snapshot));
                DiagnosticLog.event("DESKTOP_SYNC_SNAPSHOT_SENT");
                return;
            }

            if ("POST".equals(method) && "/snapshot".equals(path)) {
                if (contentLength <= 0 || contentLength > DataBackup.MAX_BYTES) {
                    reply(peer, 413, "{\"error\":\"SNAPSHOT_TOO_LARGE\"}");
                    return;
                }
                if (!baseSha.matches("[0-9a-f]{64}")) {
                    reply(peer, 428, "{\"error\":\"BASE_REQUIRED\"}");
                    return;
                }
                byte[] body = readExact(in, contentLength);
                if (body == null) {
                    reply(peer, 400, "{\"error\":\"BODY_INCOMPLETE\"}");
                    return;
                }
                String incoming = new String(body, StandardCharsets.UTF_8);
                synchronized (writeLock) {
                    String current = provider.snapshot();
                    String currentSha = sha256(current);
                    if (!constantTimeEquals(currentSha, baseSha)) {
                        reply(peer, 409, "{\"error\":\"PHONE_CHANGED\"}", currentSha);
                        return;
                    }
                    restoreProvider.restore(incoming);
                    String updated = provider.snapshot();
                    reply(peer, 200, updated, sha256(updated));
                }
                DiagnosticLog.event("DESKTOP_SYNC_SNAPSHOT_WRITTEN");
                return;
            }

            if ("POST".equals(method)) {
                reply(peer, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            reply(peer, 405, "{\"error\":\"METHOD_NOT_ALLOWED\"}");
        } catch (Exception error) {
            DiagnosticLog.error("DESKTOP_SYNC_CLIENT", error);
        }
    }

    private static String readLine(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int previous = -1;
        while (out.size() <= max) {
            int value = in.read();
            if (value < 0) return out.size() == 0 ? null
                : out.toString(StandardCharsets.US_ASCII.name());
            if (previous == '\r' && value == '\n') {
                byte[] bytes = out.toByteArray();
                int length = Math.max(0, bytes.length - 1);
                return new String(bytes, 0, length, StandardCharsets.US_ASCII);
            }
            out.write(value);
            previous = value;
        }
        throw new IllegalArgumentException("HTTP line too long");
    }

    private static byte[] readExact(InputStream in, int length) throws Exception {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) return null;
            offset += read;
        }
        return bytes;
    }

    private static void reply(Socket socket, int status, String body) throws Exception {
        reply(socket, status, body, null);
    }

    private static void reply(Socket socket, int status, String body,
            String snapshotSha) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(), StandardCharsets.US_ASCII));
        out.write("HTTP/1.1 " + status + " " + reason(status) + "\r\n");
        out.write("Content-Type: application/json; charset=utf-8\r\n");
        out.write("Content-Length: " + bytes.length + "\r\n");
        if (snapshotSha != null && snapshotSha.matches("[0-9a-f]{64}"))
            out.write("X-EDHOME-SNAPSHOT-SHA256: " + snapshotSha + "\r\n");
        out.write("Cache-Control: no-store\r\n");
        out.write("Connection: close\r\n\r\n");
        out.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 428: return "Precondition Required";
            case 431: return "Request Header Fields Too Large";
            default: return "Error";
        }
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = supplied.getBytes(StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            diff |= av ^ bv;
        }
        return diff == 0;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }


    static String localAddress() {
        String fallback = null;
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                String name = network.getName() == null ? ""
                    : network.getName().toLowerCase(Locale.ROOT);
                boolean preferred = name.startsWith("wlan")
                    || name.startsWith("wifi")
                    || name.startsWith("eth");
                boolean virtual = name.startsWith("tun")
                    || name.startsWith("tap")
                    || name.startsWith("rmnet")
                    || name.startsWith("p2p");
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!(address instanceof Inet4Address)
                            || !address.isSiteLocalAddress()
                            || address.isLoopbackAddress()) continue;
                    String value = address.getHostAddress();
                    if (preferred) return value;
                    if (!virtual && fallback == null) fallback = value;
                }
            }
        } catch (Exception ignored) { }
        return fallback;
    }
}
