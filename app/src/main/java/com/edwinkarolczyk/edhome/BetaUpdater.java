package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BETA DEV update client. No account token, no private GitHub artifacts, no silent installs.
 * Read-only HTTPS feed configured explicitly by the user. Stable is Play-only, no APK download.
 */
public final class BetaUpdater {
    private static final long POLL_MS = 30000L;
    private static final int MAX_MANIFEST = 16384;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (BuildConfig.DIAGNOSTICS_ENABLED) {
                if (activeDownload >= 0) inspectDownload(false);
                check(false);
            }
            handler.postDelayed(this, POLL_MS);
        }
    };
    private boolean running;
    private boolean checking;
    private boolean notifying;
    private boolean verifying;
    private long activeDownload = -1;
    private int targetCode;
    private String expectedHash = "";
    private String releaseNotes = "";
    private File targetFile;

    public BetaUpdater(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("edhome_beta_prefs", Context.MODE_PRIVATE);
        activeDownload = prefs.getLong("update_download_id", -1);
        targetCode = prefs.getInt("update_target_code", 0);
        expectedHash = prefs.getString("update_target_hash", "");
        releaseNotes = prefs.getString("update_target_notes", "");
        if (targetCode > 0) {
            File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (folder != null) targetFile = new File(folder, "edhome-beta-" + targetCode + ".apk");
        }
    }

    public static boolean isBeta() {
        return BuildConfig.DIAGNOSTICS_ENABLED;
    }

    public String configuredFeed() {
        return prefs.getString("updates_feed", "");
    }

    public boolean setFeed(String url) {
        String value = url == null ? "" : url.trim();
        // A GitHub Actions run is an HTML page, NOT the update JSON manifest.
        if (!value.isEmpty() && (!isSecureUrl(value) || isGitHubActionsPage(value))) return false;
        prefs.edit().putString("updates_feed", value).apply();
        DiagnosticLog.event(value.isEmpty() ? "UPDATES_FEED_CLEARED" : "UPDATES_FEED_SET");
        return true;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    public void check(boolean manual) {
        if (!isBeta()) {
            if (manual) openPlay();
            return;
        }
        String endpoint = configuredFeed();
        if (endpoint.isEmpty()) {
            if (manual) inform("Brak źródła aktualizacji. Repozytorium GitHub jest prywatne, więc APK z Actions nie jest publicznym kanałem aktualizacji. Ustaw dostępny bez logowania adres HTTPS manifestu wydania Beta DEV.");
            return;
        }
        if (checking) return;
        checking = true;
        background.execute(() -> {
            JSONObject manifest = null;
            String error = null;
            try {
                manifest = readManifest(endpoint);
            } catch (Exception e) {
                error = e.getClass().getSimpleName();
            }
            JSONObject result = manifest;
            String failure = error;
            handler.post(() -> {
                checking = false;
                if (!running && !manual) return;
                if (failure != null) {
                    DiagnosticLog.event("UPDATE_CHECK_FAILED");
                    if (manual) inform("Nie udało się odczytać źródła aktualizacji HTTPS. Sprawdź połączenie i manifest.");
                    return;
                }
                handleManifest(result, manual);
            });
        });
    }

    private static JSONObject readManifest(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(6500);
        conn.setReadTimeout(6500);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "application/json");
        try {
            if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP_NOT_OK");
            if (conn.getContentLengthLong() > MAX_MANIFEST) throw new IllegalStateException("MANIFEST_TOO_LARGE");
            try (InputStream in = conn.getInputStream()) {
                byte[] data = new byte[MAX_MANIFEST + 1];
                int offset = 0, n;
                while (offset < data.length && (n = in.read(data, offset, data.length - offset)) > 0)
                    offset += n;
                if (offset > MAX_MANIFEST) throw new IllegalStateException("MANIFEST_TOO_LARGE");
                return new JSONObject(new String(data, 0, offset, java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static boolean isGitHubActionsPage(String value) {
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        String path = uri.getPath();
        return host != null && ("github.com".equalsIgnoreCase(host)
            || "www.github.com".equalsIgnoreCase(host))
            && path != null && path.contains("/actions/");
    }

    private static boolean isSecureUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                && !TextUtils.isEmpty(uri.getHost())
                && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private void handleManifest(JSONObject json, boolean manual) {
        if (json == null) return;
        String channel = json.optString("channel", "");
        int code = json.optInt("versionCode", 0);
        String link = json.optString("apkUrl", "");
        String digest = json.optString("sha256", "").toLowerCase(java.util.Locale.ROOT);
        if (!"beta".equals(channel) || code <= 0 || !isSecureUrl(link)
            || !digest.matches("[0-9a-f]{64}")) {
            DiagnosticLog.event("UPDATE_MANIFEST_INVALID");
            if (manual) inform("Manifest nieprawidłowy: wymagane channel=beta, większy versionCode, apkUrl HTTPS i SHA-256.");
            return;
        }
        if (code <= BuildConfig.VERSION_CODE) {
            DiagnosticLog.event("UPDATE_UP_TO_DATE");
            if (manual) inform("Masz aktualną wersję: " + BuildConfig.VERSION_NAME);
            return;
        }
        DiagnosticLog.event("UPDATE_AVAILABLE", "versionCode=" + code);
        if (code == targetCode && activeDownload >= 0) {
            inspectDownload(manual);
            return;
        }
        if (code == targetCode && targetFile != null && targetFile.exists()) {
            inspectFile();
            return;
        }
        targetCode = code;
        expectedHash = digest;
        releaseNotes = json.optString("changelog", "");
        File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null) {
            if (manual) inform("Nie można przygotować katalogu pobierania.");
            return;
        }
        targetFile = new File(folder, "edhome-beta-" + code + ".apk");
        if (targetFile.exists() && !targetFile.delete()) {
            if (manual) inform("Nie można usunąć poprzedniego pobrania.");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
            request.setTitle("EDHOME Beta — aktualizacja");
            request.setDescription("Pobieranie kompilacji testowej; instalacja wymaga potwierdzenia.");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                targetFile.getName());
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("NO_DOWNLOAD_MANAGER");
            activeDownload = dm.enqueue(request);
            prefs.edit().putLong("update_download_id", activeDownload)
                .putInt("update_target_code", targetCode)
                .putString("update_target_hash", expectedHash)
                .putString("update_target_notes", releaseNotes).apply();
            DiagnosticLog.event("UPDATE_DOWNLOAD_STARTED", "versionCode=" + code);
            if (manual) inform("Znaleziono wersję " + json.optString("versionName", Integer.toString(code)) + ". Rozpoczęto pobieranie. Instalacja nie nastąpi bez Twojej zgody.");
        } catch (Exception error) {
            activeDownload = -1;
            DiagnosticLog.error("UPDATE_DOWNLOAD_START", error);
            if (manual) inform("Nie można rozpocząć pobierania aktualizacji.");
        }
    }

    public void inspectDownload(boolean manual) {
        if (activeDownload < 0) {
            if (manual) check(true);
            return;
        }
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        try (android.database.Cursor c = dm.query(new DownloadManager.Query().setFilterById(activeDownload))) {
            if (c == null || !c.moveToFirst()) return;
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                activeDownload = -1;
                prefs.edit().remove("update_download_id").apply();
                inspectFile();
            } else if (status == DownloadManager.STATUS_FAILED) {
                activeDownload = -1;
                prefs.edit().remove("update_download_id").apply();
                DiagnosticLog.event("UPDATE_DOWNLOAD_FAILED");
                if (manual) inform("Pobranie nie powiodło się. Sprawdź adres HTTPS i połączenie.");
            } else if (manual) inform("Pobieranie trwa. Wróć do Aktualizacji, aby sprawdzić ponownie.");
        } catch (Exception error) {
            DiagnosticLog.error("UPDATE_DOWNLOAD_CHECK", error);
        }
    }

    private void inspectFile() {
        if (targetFile == null || !targetFile.exists() || verifying) return;
        verifying = true;
        File candidate = targetFile;
        String expected = expectedHash;
        int requestedCode = targetCode;
        background.execute(() -> {
            boolean correct = verify(candidate, expected, requestedCode);
            handler.post(() -> {
                verifying = false;
                if (correct) {
                    DiagnosticLog.event("UPDATE_APK_VERIFIED");
                    if (!notifying) {
                        notifying = true;
                        // Beta DEV: a verified newer APK is mandatory; Stable alone
                        // offers "Później" through PlayUpdateBridge.
                        int padding = (int) (24 * activity.getResources()
                            .getDisplayMetrics().density + 0.5f);
                        LinearLayout content = new LinearLayout(activity);
                        content.setOrientation(LinearLayout.VERTICAL);
                        content.setPadding(padding, padding, padding, padding / 2);
                        TextView heading = new TextView(activity);
                        heading.setText("EDHOME  •  BETA");
                        heading.setTextColor(0xff7dd2ba);
                        heading.setTextSize(14);
                        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        content.addView(heading);
                        TextView version = new TextView(activity);
                        version.setText("Wymagana aktualizacja\nWersja " + requestedCode);
                        version.setTextColor(0xfff7fbff);
                        version.setTextSize(23);
                        version.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        version.setPadding(0, padding / 2, 0, padding / 2);
                        content.addView(version);
                        TextView description = new TextView(activity);
                        description.setText((releaseNotes.isEmpty()
                            ? "Nowe wydanie EDHOME Beta jest gotowe."
                            : releaseNotes) + "\n\nZweryfikowano plik i podpis APK. "
                            + "Android poprosi Cię o zgodę na instalację.");
                        description.setTextColor(0xffc5d3dd);
                        description.setTextSize(15);
                        content.addView(description);
                        AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setView(content)
                            .setPositiveButton("Aktualizuj", (d, w) -> {
                                notifying = false;
                                install(candidate);
                            })
                            .setCancelable(false)
                            .create();
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.show();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff7dd2ba);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
                        GradientDrawable panel = new GradientDrawable();
                        panel.setColor(0xff203344);
                        panel.setCornerRadius(activity.getResources()
                            .getDisplayMetrics().density * 24f);
                        if (dialog.getWindow() != null)
                            dialog.getWindow().setBackgroundDrawable(panel);
                    }
                } else {
                    DiagnosticLog.event("UPDATE_APK_REJECTED");
                    candidate.delete();
                    inform("Odrzucono pobrany APK: niezgodny skrót, pakiet, wersja lub podpis. Nie instaluj go.");
                }
            });
        });
    }

    private boolean verify(File apk, String sha256, int code) {
        if (!apk.isFile() || apk.length() == 0) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[32768];
            try (FileInputStream in = new FileInputStream(apk)) {
                int read;
                while ((read = in.read(block)) != -1) digest.update(block, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder actual = new StringBuilder();
            for (byte b : hash) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            if (!sha256.isEmpty() && !MessageDigest.isEqual(
                  actual.toString().getBytes(StandardCharsets.US_ASCII),
                  sha256.getBytes(StandardCharsets.US_ASCII))) return false;

            PackageManager pm = activity.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
            if (archive == null || !activity.getPackageName().equals(archive.packageName)) return false;
            long archiveCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
            if ((code > 0 && archiveCode != code) || archiveCode <= BuildConfig.VERSION_CODE) return false;
            Signature[] from = Build.VERSION.SDK_INT >= 28
                ? archive.signingInfo.getApkContentsSigners() : archive.signatures;
            Signature[] existing = Build.VERSION.SDK_INT >= 28
                ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
            return from != null && existing != null && from.length == existing.length
                && Arrays.equals(from, existing);
        } catch (Exception problem) {
            DiagnosticLog.error("UPDATE_VERIFY", problem);
            return false;
        }
    }

    private void install(File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                DiagnosticLog.event("UPDATE_INSTALL_PERMISSION_NEEDED");
                inform("Android wymaga zgody na instalowanie aktualizacji przez EDHOME Beta. Nadaj zgodę w ustawieniach i ponownie otwórz Aktualizacje.");
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return;
            }
            Uri uri = Uri.parse("content://" + activity.getPackageName()
                + ".updates/apk/" + apk.getName());
            Intent action = new Intent(Intent.ACTION_VIEW);
            action.setDataAndType(uri, "application/vnd.android.package-archive");
            action.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(action);
            DiagnosticLog.event("UPDATE_INSTALLER_OPENED");
        } catch (Exception failure) {
            DiagnosticLog.error("UPDATE_INSTALLER", failure);
            inform("Nie udało się uruchomić instalatora. Otwórz Aktualizacje ponownie.");
        }
    }


    /** Offline fallback for APK shared in chat or copied locally. Package signer is still checked. */
    public void importSelected(Uri picked) {
        if (!isBeta() || picked == null) return;
        File folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null) { inform("Brak miejsca na plik APK."); return; }
        File candidate = new File(folder, "edhome-beta-manual.apk");
        activeDownload = -1;
        targetCode = 0;
        expectedHash = "";
        releaseNotes = "Aktualizacja wybrana z plików urządzenia. Zawsze sprawdzamy identyfikator i podpis.";
        targetFile = candidate;
        notifying = false;
        background.execute(() -> {
            boolean copied = false;
            try (InputStream in = activity.getContentResolver().openInputStream(picked);
                 FileOutputStream out = new FileOutputStream(candidate, false)) {
                if (in == null) throw new IllegalStateException("No file stream");
                byte[] buffer = new byte[32768];
                int count;
                long total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 250L * 1024L * 1024L)
                        throw new IllegalStateException("APK too large");
                    out.write(buffer, 0, count);
                }
                copied = total > 0;
            } catch (Exception failure) {
                DiagnosticLog.error("UPDATE_LOCAL_IMPORT", failure);
            }
            boolean ok = copied;
            handler.post(() -> {
                if (ok) inspectFile();
                else {
                    candidate.delete();
                    inform("Nie można otworzyć wybranego pliku APK.");
                }
            });
        });
    }

    public void installReady() {
        if (targetFile == null || !targetFile.exists()) {
            inform("Brak zweryfikowanego APK w tej sesji. Sprawdź aktualizacje.");
            return;
        }
        inspectFile();
    }

    public void openPlay() {
        String packageName = activity.getPackageName();
        Intent intent = new Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=" + packageName));
        try { activity.startActivity(intent); }
        catch (Exception e) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

    private void inform(String message) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        new AlertDialog.Builder(activity).setMessage(message).setPositiveButton("OK", null).show();
    }
}
