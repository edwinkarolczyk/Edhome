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
import java.util.concurrent.atomic.AtomicInteger;

/** Beta-only local-LAN endpoint for EDHOME Desktop with guarded incremental writes. */
final class LanSyncServer {
    static final int PORT = 45823;
    static final String TOKEN_PREF = "desktop_sync_token";
    private static final int MAX_PATCH_OPS = 500;
    private static volatile long LAST_CLIENT_SEEN_AT;
    private static volatile long LAST_SYNC_ACTIVITY_AT;
    private static volatile long LAST_CONNECTION_ATTEMPT_AT;
    private static volatile String LAST_CONNECTION_RESULT = "brak próby";
    private static volatile String LAST_REMOTE = "";
    private static final AtomicInteger ACTIVE_SYNC_COUNT = new AtomicInteger();


    static boolean hasRecentClient() {
        long seen = LAST_CLIENT_SEEN_AT;
        return seen > 0L && System.currentTimeMillis() - seen < 75000L;
    }

    static long lastClientSeenAt() {
        return LAST_CLIENT_SEEN_AT;
    }

    static long lastConnectionAttemptAt() {
        return LAST_CONNECTION_ATTEMPT_AT;
    }

    static String lastConnectionResult() {
        return LAST_CONNECTION_RESULT;
    }

    static String lastRemote() {
        return LAST_REMOTE;
    }

    private static void markAttempt(InetAddress remote, String result) {
        LAST_CONNECTION_ATTEMPT_AT = System.currentTimeMillis();
        LAST_CONNECTION_RESULT = result == null ? "" : result;
        LAST_REMOTE = remote == null ? "" : remote.getHostAddress();
    }

    private static boolean isThisDeviceAddress(InetAddress remote) {
        if (remote == null) return false;
        if (remote.isLoopbackAddress()) return true;
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress local : Collections.list(network.getInetAddresses())) {
                    if (remote.equals(local)) return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    static boolean isSyncing() {
        if (ACTIVE_SYNC_COUNT.get() > 0) return true;
        long activity = LAST_SYNC_ACTIVITY_AT;
        return activity > 0L && System.currentTimeMillis() - activity < 1500L;
    }

    static long lastSyncActivityAt() {
        return LAST_SYNC_ACTIVITY_AT;
    }

    private static void beginSyncActivity() {
        ACTIVE_SYNC_COUNT.incrementAndGet();
        LAST_SYNC_ACTIVITY_AT = System.currentTimeMillis();
    }

    private static void endSyncActivity() {
        LAST_SYNC_ACTIVITY_AT = System.currentTimeMillis();
        ACTIVE_SYNC_COUNT.decrementAndGet();
    }

    interface SnapshotProvider { String snapshot() throws Exception; }
    interface RestoreProvider { void restore(String snapshot) throws Exception; }
    interface RevisionProvider { long revision() throws Exception; }
    interface PatchProvider { String patch(String incoming) throws Exception; }

    private final String token;
    private final SnapshotProvider provider;
    private final RestoreProvider restoreProvider;
    private final RevisionProvider revisionProvider;
    private final PatchProvider patchProvider;
    private final Object writeLock = new Object();
    private volatile boolean running;
    private volatile ServerSocket server;
    private Thread acceptThread;

    LanSyncServer(String token, SnapshotProvider provider,
            RestoreProvider restoreProvider, RevisionProvider revisionProvider,
            PatchProvider patchProvider) {
        this.token = token;
        this.provider = provider;
        this.restoreProvider = restoreProvider;
        this.revisionProvider = revisionProvider;
        this.patchProvider = patchProvider;
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
            InetAddress anyIpv4 = InetAddress.getByName("0.0.0.0");
            listener.bind(new InetSocketAddress(anyIpv4, PORT));
            server = listener;
            DiagnosticLog.event("DESKTOP_SYNC_LISTENING",
                "bind=0.0.0.0 port=" + PORT);
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
        boolean syncTransfer = false;
        try (Socket peer = socket) {
            peer.setSoTimeout(10000);
            InetAddress remote = peer.getInetAddress();
            boolean selfClient = isThisDeviceAddress(remote);
            if (remote == null
                    || (!remote.isSiteLocalAddress() && !remote.isLoopbackAddress())) {
                if (!selfClient)
                    markAttempt(remote, "odrzucono: poza siecią LAN");
                reply(peer, 403, "{\"error\":\"LAN_ONLY\"}");
                return;
            }
            if (!selfClient)
                markAttempt(remote, "PC dotarł do telefonu");

            InputStream in = peer.getInputStream();
            String request = readLine(in, 4096);
            if (request == null || request.isEmpty()) {
                reply(peer, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }

            String supplied = "";
            String baseSha = "";
            String diagnosticsId = "";
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
                else if ("x-edhome-diagnostics-id".equals(name)) diagnosticsId = value;
                else if ("content-length".equals(name)) {
                    try { contentLength = Integer.parseInt(value); }
                    catch (NumberFormatException ignored) { contentLength = -1; }
                }
            }

            if (!constantTimeEquals(token, supplied)) {
                if (!selfClient)
                    markAttempt(remote, "odrzucono: nieprawidłowy kod parowania");
                DiagnosticLog.event("DESKTOP_SYNC_DENIED");
                reply(peer, 401, "{\"error\":\"PAIRING_REQUIRED\"}");
                return;
            }
            if (!selfClient) {
                LAST_CLIENT_SEEN_AT = System.currentTimeMillis();
                markAttempt(remote, "autoryzowano PC");
            }

            String[] parts = request.split(" ");
            if (parts.length < 2) {
                reply(peer, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }
            String method = parts[0];
            String path = parts[1];

            syncTransfer = !selfClient && (("GET".equals(method) && "/snapshot".equals(path))
                || ("POST".equals(method)
                    && ("/patch".equals(path) || "/snapshot".equals(path))));
            if (syncTransfer) beginSyncActivity();

            if ("GET".equals(method) && "/status".equals(path)) {
                if (!selfClient) markAttempt(remote, "połączono");
                reply(peer, 200, "{\"ok\":true,\"mode\":\"read-write-incremental\",\"version\":\""
                    + json(BuildConfig.VERSION_NAME) + "\",\"port\":" + PORT + "}");
                return;
            }

            if ("GET".equals(method) && "/state".equals(path)) {
                long revision = revisionProvider == null ? -1L : revisionProvider.revision();
                reply(peer, 200, "{\"ok\":true,\"revision\":" + revision + "}");
                return;
            }

            if ("GET".equals(method) && "/diagnostics".equals(path)) {
                String diagnostics = DiagnosticLog.readFullText();
                String id = DiagnosticLog.transferId();
                if (diagnostics.isBlank() || id.isBlank()) {
                    replyNoContent(peer);
                    return;
                }
                replyText(peer, 200, diagnostics, id);
                return;
            }

            if ("POST".equals(method) && "/diagnostics/ack".equals(path)) {
                if (diagnosticsId.isBlank()) {
                    reply(peer, 400, "{\"error\":\"DIAGNOSTICS_ID_REQUIRED\"}");
                    return;
                }
                boolean cleared = DiagnosticLog.clearIfTransferred(diagnosticsId);
                if (!cleared) {
                    reply(peer, 409, "{\"error\":\"DIAGNOSTICS_CHANGED\"}");
                    return;
                }
                reply(peer, 200, "{\"ok\":true,\"cleared\":true}");
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

            if ("POST".equals(method) && "/patch".equals(path)) {
                if (contentLength <= 0 || contentLength > DataBackup.MAX_BYTES) {
                    reply(peer, 413, "{\"error\":\"PATCH_TOO_LARGE\"}");
                    return;
                }
                byte[] body = readExact(in, contentLength);
                if (body == null) {
                    reply(peer, 400, "{\"error\":\"BODY_INCOMPLETE\"}");
                    return;
                }
                String incoming = new String(body, StandardCharsets.UTF_8);
                synchronized (writeLock) {
                    try {
                        if (patchProvider == null)
                            throw new IllegalStateException("Patch provider unavailable.");
                        String patchResult = patchProvider.patch(incoming);
                        String updated = provider.snapshot();
                        String updatedSha = sha256(updated);
                        reply(peer, 200, patchResult, updatedSha);
                        DiagnosticLog.event("DESKTOP_SYNC_PATCH_WRITTEN");
                    } catch (SyncRecordStore.SyncConflict conflict) {
                        String current = provider.snapshot();
                        reply(peer, 409,
                            "{\"error\":\"ROW_CONFLICT\",\"table\":\""
                                + json(conflict.table) + "\",\"rowKey\":\""
                                + json(conflict.rowKey) + "\",\"syncUuid\":\""
                                + json(conflict.syncUuid) + "\",\"expectedRevision\":"
                                + conflict.expectedRevision + ",\"actualRevision\":"
                                + conflict.actualRevision + "}",
                            sha256(current));
                        DiagnosticLog.event("DESKTOP_SYNC_PATCH_CONFLICT",
                            conflict.table + "#" + conflict.rowKey);
                    } catch (IllegalArgumentException invalid) {
                        reply(peer, 400, "{\"error\":\"BAD_PATCH\",\"message\":\""
                            + json(invalid.getMessage() == null ? "invalid" : invalid.getMessage())
                            + "\"}");
                    }
                }
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
        } finally {
            if (syncTransfer) endSyncActivity();
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

    private static void replyNoContent(Socket socket) throws Exception {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(), StandardCharsets.US_ASCII));
        out.write("HTTP/1.1 204 No Content\r\n");
        out.write("Content-Length: 0\r\n");
        out.write("Cache-Control: no-store\r\n");
        out.write("Connection: close\r\n\r\n");
        out.flush();
    }

    private static void replyText(Socket socket, int status, String body,
            String diagnosticsId) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(), StandardCharsets.US_ASCII));
        out.write("HTTP/1.1 " + status + " " + reason(status) + "\r\n");
        out.write("Content-Type: text/plain; charset=utf-8\r\n");
        out.write("Content-Length: " + bytes.length + "\r\n");
        if (diagnosticsId != null && diagnosticsId.matches("[0-9a-f]{64}"))
            out.write("X-EDHOME-DIAGNOSTICS-ID: " + diagnosticsId + "\r\n");
        out.write("Cache-Control: no-store\r\n");
        out.write("Connection: close\r\n\r\n");
        out.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
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
