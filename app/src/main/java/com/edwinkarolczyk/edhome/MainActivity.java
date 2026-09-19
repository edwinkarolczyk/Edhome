package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Small, deliberately local-only EDHOME beta preview. */
public final class MainActivity extends Activity {
    private static final int EXPORT_DIAGNOSTICS = 1210;
    private SharedPreferences prefs;
    private LocalDb db;
    private LinearLayout root;
    private LinearLayout body;
    private boolean unlocked;
    private String screen = "home";
    private int bg, surface, ink, subdued, accent;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        DiagnosticLog.init(this);
        prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        db = new LocalDb(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        DiagnosticLog.event("ACTIVITY_CREATED");
        render();
    }

    @Override public void onStop() {
        super.onStop();
        unlocked = false;
        DiagnosticLog.event("ACTIVITY_STOPPED_LOCKED");
    }

    @Override public void onResume() {
        super.onResume();
        if (root != null && !unlocked) render();
    }

    @Override public void onBackPressed() {
        if (unlocked && !"home".equals(screen)) go("home");
        else super.onBackPressed();
    }

    private int dp(float d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void palette() {
        String theme = prefs.getString("theme", "Grafitowy");
        if ("Leśny".equals(theme)) {
            bg = Color.rgb(17, 34, 27);
            surface = Color.rgb(31, 56, 44);
            ink = Color.rgb(237, 247, 234);
            subdued = Color.rgb(177, 204, 183);
            accent = Color.rgb(122, 197, 152);
        } else if ("Jasny".equals(theme)) {
            bg = Color.rgb(241, 245, 246);
            surface = Color.WHITE;
            ink = Color.rgb(30, 44, 55);
            subdued = Color.rgb(94, 115, 126);
            accent = Color.rgb(35, 115, 105);
        } else {
            bg = Color.rgb(17, 26, 37);
            surface = Color.rgb(34, 50, 67);
            ink = Color.rgb(242, 247, 251);
            subdued = Color.rgb(173, 196, 210);
            accent = Color.rgb(127, 205, 181);
        }
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ink);
        view.setPadding(0, dp(5), 0, dp(6));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void title(String value) { body.addView(text(value, 25, true)); }

    private void note(String value) {
        TextView t = text(value, 14, false);
        t.setTextColor(subdued);
        body.addView(t);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(surface));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(7));
        body.addView(box, params);
        return box;
    }

    private Button button(String value, Runnable callback) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(bg);
        b.setBackgroundTintList(ColorStateList.valueOf(accent));
        b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        body.addView(b, params);
        return b;
    }

    private EditText field(String hint, boolean number) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(ink);
        input.setHintTextColor(subdued);
        input.setTextSize(18);
        input.setSingleLine(true);
        if (number) input.setInputType(2 | 16);
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private void alert(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void go(String destination) {
        screen = destination;
        DiagnosticLog.event("SCREEN", "id=" + destination);
        render();
    }

    private void render() {
        if (root == null || prefs == null) return;
        palette();
        root.removeAllViews();
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(48));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        if (!prefs.contains("pin_hash")) setupPin();
        else if (!unlocked) unlockPin();
        else {
            switch (screen) {
                case "tasks": tasks(); break;
                case "pantry": pantry(); break;
                case "calendar": placeholder("Kalendarz", "Wspólny kalendarz i powtarzanie czynności to następny etap."); break;
                case "scanner": placeholder("Skaner i remanent", "Kamera, kody kreskowe/QR i kreator remanentu nie działają jeszcze w tej becie."); break;
                case "settings": settings(); break;
                case "diagnostics": diagnostics(); break;
                default: home();
            }
        }
    }

    private static byte[] derivedPin(String pin, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP), 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void setupPin() {
        title("EDHOME  •  " + (BuildConfig.DIAGNOSTICS_ENABLED ? "BETA" : "PROTOTYP"));
        note("Pierwsze uruchomienie. Ustaw lokalny PIN 4–8 cyfr.");
        EditText a = field("PIN", true);
        EditText b = field("Powtórz PIN", true);
        button("Utwórz PIN", () -> {
            String one = a.getText().toString();
            String two = b.getText().toString();
            if (!one.matches("[0-9]{4,8}") || !one.equals(two)) {
                DiagnosticLog.event("PIN_SETUP_INVALID");
                alert("PIN musi mieć 4–8 cyfr, identycznych w obu polach.");
                return;
            }
            byte[] saltBytes = new byte[16];
            new SecureRandom().nextBytes(saltBytes);
            String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
            try {
                byte[] hash = derivedPin(one, salt);
                prefs.edit().putString("pin_salt", salt)
                    .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();
                unlocked = true;
                DiagnosticLog.event("PIN_SETUP_OK");
                go("home");
            } catch (Exception error) {
                DiagnosticLog.error("PIN_SETUP", error);
                alert("Błąd zabezpieczenia PIN. Spróbuj ponownie.");
            }
        });
        note("To tylko prototyp blokady. Brak odzyskiwania PIN i kopii danych — używaj danych testowych.");
    }

    private void unlockPin() {
        title("EDHOME  •  " + (BuildConfig.DIAGNOSTICS_ENABLED ? "BETA" : "PROTOTYP"));
        note("Odblokuj lokalną instalację.");
        EditText input = field("PIN", true);
        button("Odblokuj", () -> {
            try {
                byte[] expected = Base64.decode(prefs.getString("pin_hash", ""), Base64.NO_WRAP);
                byte[] actual = derivedPin(input.getText().toString(),
                    prefs.getString("pin_salt", ""));
                if (MessageDigest.isEqual(expected, actual)) {
                    unlocked = true;
                    DiagnosticLog.event("PIN_UNLOCK_OK");
                    go("home");
                } else {
                    DiagnosticLog.event("PIN_UNLOCK_FAILED");
                    input.setText("");
                    alert("Nieprawidłowy PIN.");
                }
            } catch (Exception error) {
                DiagnosticLog.error("PIN_UNLOCK", error);
                alert("Nie można zweryfikować PIN.");
            }
        });
    }

    private void header(String subtitle) {
        title("EDHOME  •  " + (BuildConfig.DIAGNOSTICS_ENABLED ? "BETA" : "PROTOTYP"));
        note(subtitle);
        button("← Panel główny", () -> go("home"));
    }

    private void home() {
        title("EDHOME  •  " + (BuildConfig.DIAGNOSTICS_ENABLED ? "BETA" : "PROTOTYP"));
        note("Idea by Edwin • " + BuildConfig.VERSION_NAME);
        title(prefs.getString("household", "Moje gospodarstwo"));
        LinearLayout info = card();
        info.addView(text("Pierwsza wersja testowa", 19, true));
        info.addView(text("Działa lokalnie: PIN, motywy, proste czynności i stan spiżarni.", 15, false));
        note("Niedokończone czynności: " + db.openTasks());
        button("✓ Czynności", () -> go("tasks"));
        button("▣ Spiżarnia", () -> go("pantry"));
        button("▦ Kalendarz — w planie", () -> go("calendar"));
        button("⌗ Skaner / Remanent — w planie", () -> go("scanner"));
        button("⚙ Ustawienia", () -> go("settings"));
        if (DiagnosticLog.enabled()) {
            button("🛠 Diagnostyka BETA", () -> go("diagnostics"));
        }
        note("Nie ma jeszcze działającego skanera, remanentu, PayCheck, SUPLA ani synchronizacji.");
    }

    private void tasks() {
        header("Czynności • testowa lista lokalna");
        button("+ Nowa czynność", () -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Np. zebrać winogrona");
            new AlertDialog.Builder(this).setTitle("Dodaj czynność")
                .setView(input).setNegativeButton("Anuluj", null)
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { alert("Podaj nazwę."); return; }
                    db.addTask(name);
                    DiagnosticLog.event("TASK_ADDED");
                    render();
                }).show();
        });
        note("Długie przytrzymanie pozycji — usuń.");
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT id,title,done FROM tasks ORDER BY done ASC,id DESC", null)) {
            if (cursor.getCount() == 0) note("Lista jest pusta.");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                boolean done = cursor.getInt(2) == 1;
                LinearLayout box = card();
                CheckBox check = new CheckBox(this);
                check.setText(name);
                check.setTextColor(done ? subdued : ink);
                check.setTextSize(17);
                check.setButtonTintList(ColorStateList.valueOf(accent));
                check.setChecked(done);
                box.addView(check);
                check.setOnCheckedChangeListener((v, value) -> {
                    db.setTaskDone(id, value);
                    DiagnosticLog.event(value ? "TASK_COMPLETED" : "TASK_REOPENED");
                    render();
                });
                check.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this).setTitle("Usunąć czynność?")
                        .setMessage(name).setNegativeButton("Nie", null)
                        .setPositiveButton("Usuń", (dialog, which) -> {
                            db.deleteTask(id);
                            DiagnosticLog.event("TASK_DELETED");
                            render();
                        }).show();
                    return true;
                });
            }
        }
        note("Daty, powtarzanie, sezony i przypisanie domownikom: zaplanowane.");
    }

    private void pantry() {
        header("Spiżarnia • uproszczony stan testowy");
        button("+ Dodaj produkt (1 szt.)", () -> {
            EditText input = new EditText(this);
            input.setHint("Nazwa produktu");
            input.setSingleLine(true);
            new AlertDialog.Builder(this).setTitle("Nowy produkt")
                .setView(input).setNegativeButton("Anuluj", null)
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { alert("Podaj nazwę."); return; }
                    db.addStock(name);
                    DiagnosticLog.event("PANTRY_PRODUCT_ADDED");
                    render();
                }).show();
        });
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT id,name,qty FROM pantry ORDER BY name COLLATE NOCASE", null)) {
            if (cursor.getCount() == 0) note("Spiżarnia jest pusta.");
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                int qty = cursor.getInt(2);
                LinearLayout box = card();
                box.addView(text(name + " • " + qty + " szt.", 18, true));
                Button add = new Button(this);
                add.setText("+1 Dodaj");
                add.setAllCaps(false);
                box.addView(add);
                add.setOnClickListener(v -> {
                    db.changeStock(id, +1);
                    DiagnosticLog.event("PANTRY_INCREMENT");
                    render();
                });
                Button remove = new Button(this);
                remove.setText("−1 Wyciągnij");
                remove.setAllCaps(false);
                remove.setEnabled(qty > 0);
                box.addView(remove);
                remove.setOnClickListener(v -> {
                    db.changeStock(id, -1);
                    DiagnosticLog.event("PANTRY_DECREMENT");
                    render();
                });
            }
        }
        note("Skanowanie, remanent, kg/l i lokalizacje — zaplanowane, nie działają.");
    }

    private void placeholder(String name, String why) {
        header(name);
        LinearLayout box = card();
        box.addView(text("Moduł w przygotowaniu", 20, true));
        box.addView(text(why, 16, false));
        DiagnosticLog.event("PLACEHOLDER_VIEW");
    }

    private void settings() {
        header("Ustawienia");
        note("Aktualny motyw: " + prefs.getString("theme", "Grafitowy"));
        for (String theme : new String[]{"Grafitowy", "Leśny", "Jasny"}) {
            button("Motyw: " + theme, () -> {
                prefs.edit().putString("theme", theme).apply();
                DiagnosticLog.event("THEME_CHANGED");
                render();
            });
        }
        note("Nazwa gospodarstwa");
        EditText name = field("Nazwa gospodarstwa", false);
        name.setText(prefs.getString("household", "Moje gospodarstwo"));
        button("Zapisz nazwę", () -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) { alert("Podaj nazwę."); return; }
            prefs.edit().putString("household", value).apply();
            DiagnosticLog.event("HOUSEHOLD_RENAMED");
            render();
        });
        if (DiagnosticLog.enabled()) button("Diagnostyka BETA", () -> go("diagnostics"));
        note("Dane są lokalne, bez funkcji backupu. Nie zapisuj rzeczywistych ważnych danych.");
    }

    private void diagnostics() {
        if (!DiagnosticLog.enabled()) { go("home"); return; }
        header("Diagnostyka • tylko BETA");
        note("Zapis automatyczny w prywatnym pliku aplikacji. Maks. 1 MB + poprzedni segment.");
        note("Nie zapisujemy PIN-u, nazw produktów, treści finansów ani powiadomień. Log Androida całego telefonu nie jest zbierany.");
        button("Kopiuj log do schowka", () -> {
            DiagnosticLog.event("DIAGNOSTICS_COPIED");
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("EDHOME beta diagnostics",
                    DiagnosticLog.readText()));
                alert("Log skopiowany. Możesz wkleić go do czatu.");
            } else alert("Schowek jest niedostępny.");
        });
        button("Eksportuj plik .txt", () -> {
            DiagnosticLog.event("DIAGNOSTICS_EXPORT_REQUESTED");
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, DiagnosticLog.exportName());
            try {
                startActivityForResult(intent, EXPORT_DIAGNOSTICS);
            } catch (Exception error) {
                DiagnosticLog.error("EXPORT_PICKER", error);
                alert("Nie można otworzyć zapisywania pliku.");
            }
        });
        String log = DiagnosticLog.readText();
        note("Podgląd: " + log.length() + " znaków");
        TextView preview = text(log.substring(Math.max(0, log.length() - 5000)), 11, false);
        preview.setTextIsSelectable(true);
        card().addView(preview);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_DIAGNOSTICS || !DiagnosticLog.enabled()) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            DiagnosticLog.event("DIAGNOSTICS_EXPORT_CANCELLED");
            return;
        }
        Uri destination = data.getData();
        try (OutputStream stream = getContentResolver().openOutputStream(destination)) {
            if (stream == null) throw new IllegalStateException("No output stream");
            stream.write(DiagnosticLog.readText().getBytes(StandardCharsets.UTF_8));
            DiagnosticLog.event("DIAGNOSTICS_EXPORTED");
            alert("Zapisano plik diagnostyczny.");
        } catch (Exception error) {
            DiagnosticLog.error("DIAGNOSTICS_EXPORT", error);
            alert("Nie udało się zapisać pliku.");
        }
    }

    private static final class LocalDb extends SQLiteOpenHelper {
        LocalDb(Context context) {
            super(context, "edhome-beta-preview.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0)");
            DiagnosticLog.event("DATABASE_CREATED");
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            // Do not silently drop beta data; migrations must be explicitly designed.
            DiagnosticLog.event("DATABASE_MIGRATION_REQUIRED");
            throw new IllegalStateException("Missing EDHOME database migration");
        }

        int openTasks() {
            try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE done=0", null)) {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            }
        }

        void addTask(String title) {
            ContentValues values = new ContentValues();
            values.put("title", title);
            getWritableDatabase().insertOrThrow("tasks", null, values);
        }

        void setTaskDone(long id, boolean done) {
            ContentValues values = new ContentValues();
            values.put("done", done ? 1 : 0);
            getWritableDatabase().update("tasks", values, "id=?",
                new String[]{Long.toString(id)});
        }

        void deleteTask(long id) {
            getWritableDatabase().delete("tasks", "id=?", new String[]{Long.toString(id)});
        }

        void addStock(String name) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("qty", 1);
            getWritableDatabase().insertOrThrow("pantry", null, values);
        }

        void changeStock(long id, int diff) {
            if (diff < 0) {
                getWritableDatabase().execSQL(
                    "UPDATE pantry SET qty=qty-1 WHERE id=? AND qty>0",
                    new Object[]{id});
            } else {
                getWritableDatabase().execSQL(
                    "UPDATE pantry SET qty=qty+1 WHERE id=?", new Object[]{id});
            }
        }
    }
}
