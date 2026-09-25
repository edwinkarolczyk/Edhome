package com.edwinkarolczyk.edhome;

import android.content.SharedPreferences;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/** Beta-only local-LAN, read-only snapshot endpoint for EDHOME Desktop. */
final class LanSyncServer {
    static final int PORT = 45823;
    static final String TOKEN_PREF = "desktop_sync_token";

    interface SnapshotProvider { String snapshot() throws Exception; }

    private final String token;
    private final SnapshotProvider provider;
    private volatile boolean running;
    private volatile ServerSocket server;
    private Thread acceptThread;

    LanSyncServer(String token, SnapshotProvider provider) {
        this.token = token;
        this.provider = provider;
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
            peer.setSoTimeout(5000);
            InetAddress remote = peer.getInetAddress();
            if (remote == null
                    || (!remote.isSiteLocalAddress() && !remote.isLoopbackAddress())) {
                reply(peer, 403, "{\"error\":\"LAN_ONLY\"}");
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(
                peer.getInputStream(), StandardCharsets.US_ASCII));
            String request = in.readLine();
            if (request == null || request.length() > 4096) {
                reply(peer, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }

            String supplied = "";
            int headerBytes = request.length();
            for (String line; (line = in.readLine()) != null && !line.isEmpty();) {
                headerBytes += line.length();
                if (headerBytes > 32768) {
                    reply(peer, 431, "{\"error\":\"HEADERS_TOO_LARGE\"}");
                    return;
                }
                int colon = line.indexOf(':');
                if (colon > 0 && "x-edhome-token".equals(
                        line.substring(0, colon).trim().toLowerCase(Locale.ROOT))) {
                    supplied = line.substring(colon + 1).trim();
                }
            }

            if (!constantTimeEquals(token, supplied)) {
                DiagnosticLog.event("DESKTOP_SYNC_DENIED");
                reply(peer, 401, "{\"error\":\"PAIRING_REQUIRED\"}");
                return;
            }

            String[] parts = request.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                reply(peer, 405, "{\"error\":\"READ_ONLY\"}");
                return;
            }

            if ("/status".equals(parts[1])) {
                reply(peer, 200, "{\"ok\":true,\"mode\":\"read-only\",\"version\":\""
                    + json(BuildConfig.VERSION_NAME) + "\",\"port\":" + PORT + "}");
                return;
            }

            if ("/snapshot".equals(parts[1])) {
                String snapshot = provider.snapshot();
                if (snapshot.getBytes(StandardCharsets.UTF_8).length > DataBackup.MAX_BYTES) {
                    reply(peer, 413, "{\"error\":\"SNAPSHOT_TOO_LARGE\"}");
                    return;
                }
                reply(peer, 200, snapshot);
                DiagnosticLog.event("DESKTOP_SYNC_SNAPSHOT_SENT");
                return;
            }

            reply(peer, 404, "{\"error\":\"NOT_FOUND\"}");
        } catch (Exception error) {
            DiagnosticLog.error("DESKTOP_SYNC_CLIENT", error);
        }
    }

    private static void reply(Socket socket, int status, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(), StandardCharsets.US_ASCII));
        out.write("HTTP/1.1 " + status + " " + reason(status) + "\r\n");
        out.write("Content-Type: application/json; charset=utf-8\r\n");
        out.write("Content-Length: " + bytes.length + "\r\n");
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
            case 413: return "Payload Too Large";
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
