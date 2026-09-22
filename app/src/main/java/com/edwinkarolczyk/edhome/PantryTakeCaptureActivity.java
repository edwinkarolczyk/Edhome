package com.edwinkarolczyk.edhome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Continuous TAKE scanner: another barcode replaces an uncommitted countdown.
 * Existing ADD/lookup camera flow stays in MainActivity. No network, no PIN.
 */
public final class PantryTakeCaptureActivity extends Activity {
    static final String EXTRA_BATCH = "batch";
    static final String EXTRA_COMMITTED = "committed";
    private static final int CAMERA_PERMISSION = 1930;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DecoratedBarcodeView camera;
    private TextView title;
    private TextView timer;
    private Button takeNow;
    private Button another;
    private PantryTakeCountdown countdown;
    private boolean resumed;
    private boolean busy;
    private boolean batch;
    private boolean closing;
    private int committed;
    private String lastBarcode = "";
    private String pendingStockBarcode;
    private long pendingPantryId = -1;
    private long lastCommittedPantryId = -1;
    private boolean approvedRepeat;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed || closing || countdown == null
                    || countdown.pending() == null) return;
            if (countdown.due(SystemClock.elapsedRealtime())) {
                commitTake();
                return;
            }
            showRemaining();
            handler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // If Android recreates the camera Activity, do not restore a pending
        // action: a countdown must never survive a lifecycle interruption.
        batch = getIntent() != null && getIntent().getBooleanExtra(EXTRA_BATCH, false);
        SharedPreferences prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        int delay = prefs.getInt(PantryTakeCountdown.DELAY_PREF,
            PantryTakeCountdown.DEFAULT_SECONDS);
        if (!PantryTakeCountdown.validSeconds(delay))
            delay = PantryTakeCountdown.DEFAULT_SECONDS;
        countdown = new PantryTakeCountdown(delay);
        createUi();
        DiagnosticLog.event("PANTRY_TAKE_SCANNER_OPENED",
            "batch=" + batch + " seconds=" + delay);
    }

    private TextView label(String content, int size) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(14, 12, 14, 12);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button action(String text, Runnable callback) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(48);
        button.setOnClickListener(v -> callback.run());
        return button;
    }

    private void createUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 25, 36));
        title = label("Skieruj aparat na produkt", 19);
        root.addView(title);
        camera = new DecoratedBarcodeView(this);
        camera.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(
            Arrays.asList(BarcodeFormat.EAN_8, BarcodeFormat.UPC_A,
                BarcodeFormat.EAN_13, BarcodeFormat.ITF, BarcodeFormat.CODE_128)));
        camera.decodeContinuous(new BarcodeCallback() {
            @Override public void barcodeResult(BarcodeResult result) {
                if (result != null) onBarcode(result.getText());
            }
            @Override public void possibleResultPoints(List<ResultPoint> points) { }
        });
        root.addView(camera, new LinearLayout.LayoutParams(-1, 0, 1));
        timer = label("Brak oczekującego wyjęcia", 18);
        root.addView(timer);
        takeNow = action("Wyjmij teraz −1", this::commitTake);
        takeNow.setEnabled(false);
        root.addView(takeNow);
        another = action("To kolejne opakowanie tego samego kodu",
            this::armSameAgain);
        another.setEnabled(false);
        root.addView(another);
        root.addView(action("Anuluj bieżące wyjęcie", () -> {
            cancelPending("PANTRY_TAKE_CANCELLED");
            title.setText("Anulowano. Skanuj inny kod lub wybierz kolejne opakowanie.");
        }));
        root.addView(action("Zakończ skanowanie", this::finishScanner));
        setContentView(root);
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return;
        }
        startCamera();
    }

    private void startCamera() {
        if (!resumed || closing) return;
        try {
            camera.resume();
        } catch (Exception error) {
            DiagnosticLog.error("PANTRY_TAKE_CAMERA_START", error);
            cancelPending("PANTRY_TAKE_CAMERA_START_CANCELLED");
            title.setText("Nie można uruchomić aparatu. Wróć i wpisz kod ręcznie.");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != CAMERA_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            startCamera();
        else {
            cancelPending("PANTRY_TAKE_CAMERA_PERMISSION_DENIED");
            title.setText("Brak zgody na aparat. Wróć i wpisz kod ręcznie.");
        }
    }

    @Override public void onPause() {
        // Never subtract anything while the screen is covered/backgrounded.
        resumed = false;
        cancelPending("PANTRY_TAKE_BACKGROUND_CANCELLED");
        if (camera != null) camera.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (countdown != null) countdown.cancel();
        super.onDestroy();
    }

    private void cancelPending(String event) {
        handler.removeCallbacks(tick);
        if (countdown != null
                && (countdown.pending() != null || countdown.needsApproval())) {
            countdown.cancel();
            DiagnosticLog.event(event);
        }
        if (timer != null) timer.setText("Brak oczekującego wyjęcia");
        if (takeNow != null) takeNow.setEnabled(false);
        if (another != null) another.setEnabled(false);
        pendingStockBarcode = null;
        pendingPantryId = -1;
        approvedRepeat = false;
    }

    private void onBarcode(String barcode) {
        if (!resumed || closing || busy || barcode == null) return;
        String previous = countdown.pending();
        boolean userApprovedRepeat = approvedRepeat;
        approvedRepeat = false;
        if (!countdown.observe(barcode, SystemClock.elapsedRealtime())) return;
        handler.removeCallbacks(tick);
        if (previous != null && !previous.equals(barcode))
            DiagnosticLog.event("PANTRY_TAKE_REPLACED");
        lastBarcode = barcode;
        another.setEnabled(false);
        if (countdown.needsApproval()) {
            takeNow.setEnabled(false);
            another.setEnabled(true);
            title.setText("Ten kod był ostatnio wyjęty. Potwierdź, że to "
                + "kolejne opakowanie; poprzedni licznik anulowano.");
            timer.setText("Oczekuje na potwierdzenie • niczego nie odejmuję");
            DiagnosticLog.event("PANTRY_TAKE_REPEAT_REQUIRES_CONFIRMATION");
            return;
        }
        if (!PantryScanRules.validBarcode(barcode)) {
            cancelPending("PANTRY_TAKE_INVALID");
            title.setText("Niepoprawny EAN/UPC/GTIN. Skanuj inny kod.");
            return;
        }
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                getDatabasePath("edhome-beta-preview.db").getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
            try {
                PantryBarcodeStore.TakeCode known =
                    PantryBarcodeStore.findTakeCode(db, barcode);
                if (known == null) {
                    cancelPending("PANTRY_TAKE_UNKNOWN");
                    title.setText("Nieznany kod. Dodaj produkt w Spiżarni przed wyjęciem.");
                    return;
                }
                PantryBarcodeStore.Item item = known.item;
                // A single physical product can be printed/decoded in several
                // UPC/EAN forms; a changed raw string must not bypass consent.
                if (item.id == lastCommittedPantryId && !userApprovedRepeat) {
                    cancelPending("PANTRY_TAKE_ALIAS_REPEAT_BLOCKED");
                    another.setEnabled(true);
                    lastBarcode = barcode;
                    title.setText("To opakowanie było już wyjęte. Potwierdź "
                        + "kolejne opakowanie tego samego produktu.");
                    timer.setText("Bez odjęcia • potrzebne potwierdzenie");
                    return;
                }
                if (!known.linkedBarcode.equals(barcode))
                    DiagnosticLog.event("PANTRY_TAKE_LOCAL_ALIAS_MATCH");
                pendingStockBarcode = known.linkedBarcode;
                pendingPantryId = item.id;
                if (item.qty <= 0) {
                    cancelPending("PANTRY_TAKE_EMPTY");
                    title.setText(item.name + " • brak opakowań. Nie odejmuję.");
                    return;
                }
                PantryPackageStore.Pack pack = PantryPackageStore.find(db, item.id);
                title.setText(item.name + " • stan: "
                    + PantryPackageRules.summary(item.qty, pack.unit, pack.sizeMilli));
            } finally { db.close(); }
        } catch (Exception problem) {
            DiagnosticLog.error("PANTRY_TAKE_READ", problem);
            cancelPending("PANTRY_TAKE_READ_CANCELLED");
            title.setText("Błąd odczytu produktu. Stan bez zmian; wróć do Spiżarni.");
            return;
        }
        takeNow.setEnabled(true);
        showRemaining();
        DiagnosticLog.event("PANTRY_TAKE_COUNTDOWN_STARTED");
        handler.postDelayed(tick, 100);
    }

    private void showRemaining() {
        if (countdown.pending() == null) return;
        long left = countdown.millisLeft(SystemClock.elapsedRealtime());
        timer.setText("−1 za " + ((left + 999) / 1000) + " s • kolejny kod anuluje ten");
    }

    private void armSameAgain() {
        if (!resumed || closing || busy || lastBarcode.isEmpty()
                || countdown.pending() != null) return;
        countdown.allowSameAgain();
        approvedRepeat = true;
        onBarcode(lastBarcode);
    }

    private void commitTake() {
        if (!resumed || closing || busy || countdown.pending() == null) return;
        handler.removeCallbacks(tick);
        String scannedBarcode = countdown.consume(); // single use before touching SQLite
        String barcode = pendingStockBarcode;
        long pantryId = pendingPantryId;
        pendingStockBarcode = null;
        pendingPantryId = -1;
        if (scannedBarcode == null || barcode == null || pantryId <= 0) {
            title.setText("Nie potwierdzono produktu. Stan spiżarni bez zmian.");
            timer.setText("Brak oczekującego wyjęcia");
            takeNow.setEnabled(false);
            return;
        }
        busy = true;
        takeNow.setEnabled(false);
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                getDatabasePath("edhome-beta-preview.db").getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
            try {
                String result = PantryBarcodeStore.commit(db, barcode, null, "TAKE",
                    UUID.randomUUID().toString());
                if (!"COMMITTED".equals(result)) {
                    DiagnosticLog.event("PANTRY_TAKE_DUPLICATE_IGNORED");
                    title.setText("Operacja już zapisana. Nie odjęto ponownie.");
                    timer.setText("Brak oczekującego wyjęcia");
                    return;
                }
            } finally { db.close(); }
            countdown.markCommitted(scannedBarcode);
            lastCommittedPantryId = pantryId;
            committed++;
            DiagnosticLog.event("PANTRY_TAKE_COMMITTED");
            title.setText("Zapisano −1 opakowanie. Możesz skanować następny kod.");
            timer.setText("W tej sesji zapisano: " + committed);
            another.setEnabled(true);
            if (!batch) finishScanner();
        } catch (Exception error) {
            DiagnosticLog.error("PANTRY_TAKE_COMMIT", error);
            title.setText("Nie potwierdzono zapisu. Sprawdź stan i historię "
                + "przed ponowną próbą.");
            timer.setText("Brak oczekującego wyjęcia");
            another.setEnabled(false);
        } finally { busy = false; }
    }

    private void finishScanner() {
        if (closing) return;
        closing = true;
        cancelPending("PANTRY_TAKE_FINISH_CANCELLED");
        DiagnosticLog.event("PANTRY_TAKE_SCANNER_FINISHED", "committed=" + committed);
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_COMMITTED, committed));
        finish();
    }

    @Override public void onBackPressed() { finishScanner(); }
}
