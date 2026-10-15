package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Beta: one tap from the launcher, a short foreground-exclusive ReaderMode session,
 * then reuse MainActivity's existing NFC routing. Never toggles device-wide NFC.
 */
public final class NfcShortcutScannerActivity extends Activity {
    private static final String ACTION_QUICK_NFC =
        "com.edwinkarolczyk.edhome.action.QUICK_NFC";
    private static final long SCAN_WINDOW_MS = 7_000L;

    private boolean handled;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> {
        if (!handled) finish();
    };
    private NfcAdapter adapter;
    private boolean armed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ACTION_QUICK_NFC.equals(getIntent().getAction())) {
            finish();
            return;
        }
        TextView info = new TextView(this);
        info.setText("Przyłóż tag NFC do telefonu\nSkanowanie przez 7 sekund");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(19f);
        info.setPadding(28, 28, 28, 28);
        setContentView(info);
        adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter == null) {
            Toast.makeText(this, "Ten telefon nie obsługuje NFC.", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!adapter.isEnabled()) {
            Toast.makeText(this, "Włącz NFC w ustawieniach telefonu.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing() || adapter == null || !adapter.isEnabled() || handled)
            return;
        int flags = NfcAdapter.FLAG_READER_NFC_A
            | NfcAdapter.FLAG_READER_NFC_B
            | NfcAdapter.FLAG_READER_NFC_F
            | NfcAdapter.FLAG_READER_NFC_V
            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
        try {
            adapter.enableReaderMode(this, this::onTagRead, flags, null);
            armed = true;
            handler.postDelayed(timeout, SCAN_WINDOW_MS);
        } catch (Exception error) {
            Toast.makeText(this, "Nie udało się uruchomić NFC.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void onTagRead(Tag tag) {
        if (tag == null) return;
        runOnUiThread(() -> {
            if (handled || isFinishing()) return;
            handled = true;
            stopScan();
            // Hand the real Tag parcelable to the established EDHOME NFC handler.
            Intent open = new Intent(this, MainActivity.class);
            open.setAction(NfcAdapter.ACTION_TAG_DISCOVERED);
            open.putExtra(NfcAdapter.EXTRA_TAG, tag);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
            finish();
        });
    }

    private void stopScan() {
        handler.removeCallbacks(timeout);
        if (armed && adapter != null) {
            armed = false;
            try {
                adapter.disableReaderMode(this);
            } catch (RuntimeException ignored) {
                // The OS will also release foreground ReaderMode on pause.
            }
        }
    }

    @Override protected void onPause() {
        stopScan();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopScan();
        super.onDestroy();
    }
}
