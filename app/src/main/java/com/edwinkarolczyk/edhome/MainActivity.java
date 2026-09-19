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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Small, deliberately local-only EDHOME beta preview. */
public final class MainActivity extends Activity {
    private static final int EXPORT_DIAGNOSTICS = 1210;
    private static final int IMPORT_BETA_APK = 1211;
    private static final int EXPORT_DATA_BACKUP = 1212;
    private static final int IMPORT_DATA_BACKUP = 1213;
    private SharedPreferences prefs;
    private LocalDb db;
    private BetaUpdater updater;
    private LinearLayout root;
    private LinearLayout body;
    private boolean unlocked;
    private boolean stableUpdateChecked;
    private String screen = "home";
    private int bg, surface, ink, subdued, accent;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        DiagnosticLog.init(this);
        prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        db = new LocalDb(this);
        updater = new BetaUpdater(this);
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
        if (updater != null) updater.stop();
    }

    @Override public void onResume() {
        super.onResume();
        if (root != null && !unlocked) render();
        if (unlocked && updater != null) updater.start();
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
        if (unlocked && updater != null) updater.start();
        if (unlocked && !BetaUpdater.isBeta() && !stableUpdateChecked) {
            stableUpdateChecked = true;
            PlayUpdateBridge.checkOnce(this);
        }
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
                case "scanner": placeholder("Skaner", "Kamera i kody kreskowe/QR nie działają jeszcze w tej becie."); break;
                case "audit": audit(); break;
                case "settings": settings(); break;
                case "updates": updates(); break;
                case "backup": backup(); break;
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
        button("⌗ Skaner kodów — w planie", () -> go("scanner"));
        button("◫ Remanent spiżarni", () -> go("audit"));
        button("↻ Aktualizacje", () -> go("updates"));
        button("↧ Kopia danych / przenoszenie", () -> go("backup"));
        button("⚙ Ustawienia", () -> go("settings"));
        if (DiagnosticLog.enabled()) {
            button("🛠 Diagnostyka BETA", () -> go("diagnostics"));
        }
        note("Remanent działa testowo offline. Skanera kodów, cyklicznych przypomnień, PayCheck, SUPLA i synchronizacji jeszcze nie ma.");
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
        button("◫ Remanent — sprawdź ilości", () -> go("audit"));
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
        note("Remanent dostępny osobno. Skanowanie kodów, kg/l i lokalizacje — w planie.");
    }


    /** Minimalny remanent wersji 0.1.1: stały snapshot, zapis postępu, korekta dopiero na końcu. */
    private void audit() {
        header("Spiżarnia / Remanent • BETA");
        long session = db.openAuditId();
        if (session == 0) {
            note("Sprawdź produkty po kolei. „Zgadza się” nie wymaga przepisywania ilości.");
            note("Sesja zapisuje postęp lokalnie. Ilości zmienią się dopiero po zatwierdzeniu raportu.");
            button("Rozpocznij remanent", () -> {
                long created = db.startAudit();
                if (created <= 0) {
                    alert("Spiżarnia jest pusta. Dodaj najpierw produkt.");
                    return;
                }
                DiagnosticLog.event("AUDIT_STARTED");
                render();
            });
            note("Harmonogram cykliczny, skanowanie kodów i przegląd wybranych miejsc będą dodawane etapami.");
            return;
        }
        int[] progress = db.auditProgress(session);
        title("Sprawdzono " + progress[1] + " z " + progress[0]);
        if (progress[1] < progress[0]) {
            try (Cursor item = db.auditNext(session)) {
                if (!item.moveToFirst()) { alert("Nie można znaleźć następnego produktu."); return; }
                long rowId = item.getLong(0);
                String product = item.getString(1);
                int expected = item.getInt(2);
                LinearLayout entry = card();
                entry.addView(text(product, 23, true));
                entry.addView(text("Zapisany stan: " + expected + " szt.", 19, false));
                button("✓ Zgadza się — dalej", () -> {
                    db.auditAnswer(rowId, expected, "match");
                    DiagnosticLog.event("AUDIT_MATCH");
                    render();
                });
                button("Podaj faktyczną liczbę", () -> {
                    EditText amount = new EditText(this);
                    amount.setInputType(2);
                    amount.setText(Integer.toString(expected));
                    amount.setSelectAllOnFocus(true);
                    amount.requestFocus();
                    new AlertDialog.Builder(this).setTitle("Faktyczna liczba sztuk")
                        .setView(amount).setNegativeButton("Anuluj", null)
                        .setPositiveButton("Dalej", (dialog, which) -> {
                            String value = amount.getText().toString().trim();
                            try {
                                int number = Integer.parseInt(value);
                                if (number < 0) { alert("Liczba nie może być ujemna."); return; }
                                db.auditAnswer(rowId, number, "count");
                                DiagnosticLog.event("AUDIT_COUNTED");
                                render();
                            } catch (NumberFormatException invalid) {
                                DiagnosticLog.event("AUDIT_INVALID_COUNT");
                                alert("Podaj poprawną liczbę całkowitą.");
                            }
                        }).show();
                });
                button("Brak na półce — 0 szt.", () -> {
                    db.auditAnswer(rowId, 0, "count");
                    DiagnosticLog.event("AUDIT_MISSING");
                    render();
                });
                button("Pomiń / sprawdź później", () -> {
                    db.auditAnswer(rowId, null, "skip");
                    DiagnosticLog.event("AUDIT_SKIPPED");
                    render();
                });
            }
        } else {
            title("Raport remanentu");
            int changes = 0, skipped = 0;
            try (Cursor rows = db.auditRows(session)) {
                while (rows.moveToNext()) {
                    String name = rows.getString(0);
                    int expected = rows.getInt(1);
                    String status = rows.getString(3);
                    if ("skip".equals(status)) {
                        skipped++;
                        card().addView(text(name + ": nie sprawdzono", 16, false));
                    } else {
                        int actual = rows.getInt(2);
                        if (actual != expected) {
                            changes++;
                            card().addView(text(name + ": " + expected + " → " + actual + " szt.", 16, false));
                        }
                    }
                }
            }
            if (changes == 0) note("Bez rozbieżności w sprawdzonych pozycjach.");
            note("Proponowane korekty: " + changes + " • Pominięte: " + skipped);
            button("Zatwierdź raport i ewentualne korekty", () -> {
                String outcome = db.finishAudit(session);
                if ("OK".equals(outcome)) {
                    DiagnosticLog.event("AUDIT_COMMITTED");
                    alert("Remanent zakończony. Korekty zapisano w historii.");
                    render();
                } else if ("CONFLICT".equals(outcome)) {
                    DiagnosticLog.event("AUDIT_CONFLICT");
                    alert("Stan produktu zmienił się podczas remanentu. Niczego nie nadpisano. Sprawdź zmiany; możesz anulować sesję i policzyć ponownie.");
                } else alert("Remanent nie jest gotowy do zatwierdzenia.");
            });
        }
        button("← Cofnij ostatnią odpowiedź", () -> {
            if (!db.auditUndo(session)) { alert("Nie ma czego cofać."); return; }
            DiagnosticLog.event("AUDIT_UNDO");
            render();
        });
        button("Zapisz postęp i wróć do spiżarni", () -> go("pantry"));
        button("Anuluj remanent (bez korekt)", () -> {
            new AlertDialog.Builder(this).setTitle("Anulować remanent?")
                .setMessage("Wyniki sesji zostaną oznaczone jako anulowane. Nie zmienimy stanów spiżarni.")
                .setNegativeButton("Nie", null)
                .setPositiveButton("Anuluj", (dialog, which) -> {
                    db.cancelAudit(session);
                    DiagnosticLog.event("AUDIT_CANCELLED");
                    go("pantry");
                }).show();
        });
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
        button("Kopia danych / przenoszenie", () -> go("backup"));
        note("Dane pozostają lokalne. Przed zmianą instalacji zapisz kopię poza aplikacją.");
    }


    private void backup() {
        header("Kopia danych • przenoszenie między instalacjami");
        note("Eksport zawiera czynności, spiżarnię, historię i bieżący postęp remanentu, nazwę gospodarstwa oraz motyw.");
        note("Nie zawiera PIN-u, dziennika diagnostycznego ani adresu aktualizacji. Plik JSON nie jest szyfrowany: przechowuj go prywatnie.");
        note("Kopia umożliwia przeniesienie danych do nowej instalacji, ale nie omija wymogu tego samego podpisu APK przy zwykłej aktualizacji Androida.");
        button("Eksportuj kopię danych (.json)", () -> {
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("application/json");
            save.putExtra(Intent.EXTRA_TITLE, "EDHOME-backup-v1-" + System.currentTimeMillis() + ".json");
            try { startActivityForResult(save, EXPORT_DATA_BACKUP); }
            catch (Exception error) {
                DiagnosticLog.error("DATA_BACKUP_PICKER", error);
                alert("Nie można otworzyć wyboru miejsca zapisu.");
            }
        });
        button("Przywróć kopię z pliku (.json)", () -> {
            Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.addCategory(Intent.CATEGORY_OPENABLE);
            open.setType("application/json");
            try { startActivityForResult(open, IMPORT_DATA_BACKUP); }
            catch (Exception error) {
                DiagnosticLog.error("DATA_RESTORE_PICKER", error);
                alert("Nie można otworzyć wyboru kopii.");
            }
        });
        note("Przywrócenie zastępuje CAŁĄ obecną spiżarnię, czynności i remanenty. Przed importem wykonaj eksport obecnego stanu.");
        note("Odzyskiwanie istniejących danych ze starszej wersji 0.1.2, która nie ma eksportu, wymaga osobnego planu — nie odinstalowuj jej bez kopii.");
    }

    private void updates() {
        header("Aktualizacje • " + BuildConfig.VERSION_NAME);
        note("Zainstalowany versionCode: " + BuildConfig.VERSION_CODE);
        if (!BetaUpdater.isBeta()) {
            note("Stable korzysta z aktualizacji Google Play; bez pobierania APK z aplikacji.");
            button("Sprawdź w Google Play", () -> updater.openPlay());
            return;
        }
        note("Beta DEV sprawdza manifest HTTPS co 30 sekund, kiedy aplikacja jest aktywna. Pobranie nowego pliku jest automatyczne, lecz instalacja wymaga Twojej zgody w Androidzie.");
        note("UWAGA: link github.com/.../actions/runs/... jest STRONĄ kompilacji, a nie źródłem aktualizacji. Nie wklejaj tu takiego linku.");
        note("Automatyczne aktualizacje wymagają osobno opublikowanego pliku manifestu JSON i APK na HTTPS. Repozytorium pozostaje prywatne. Nigdy nie wpisuj tokenu GitHub ani hasła.");
        EditText source = field("HTTPS adres manifestu (bez tokenów)", false);
        source.setInputType(17);
        source.setText(updater.configuredFeed());
        button("Zapisz źródło aktualizacji", () -> {
            if (!updater.setFeed(source.getText().toString())) {
                alert("To musi być URL HTTPS pliku manifestu JSON, a nie strona GitHub Actions, token lub hasło. Gdy nie mamy serwera aktualizacji, wyczyść to pole.");
                return;
            }
            alert(updater.configuredFeed().isEmpty()
                ? "Źródło usunięte. Zdalne sprawdzanie jest wyłączone."
                : "Zapisano adres. Beta sprawdzi nowy manifest bez dodatkowego logowania.");
        });
        button("Wyłącz zdalne sprawdzanie (wyczyść link)", () -> {
            updater.setFeed("");
            DiagnosticLog.event("UPDATES_FEED_DISABLED");
            render();
            alert("Źródło usunięte. Praca EDHOME i ręczny import APK nadal działają offline.");
        });
        button("Sprawdź aktualizację teraz", () -> updater.check(true));
        button("Sprawdź pobieranie / instaluj gotowy APK", () -> updater.installReady());
        button("Wybierz APK z telefonu (bez internetu)", () -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("application/vnd.android.package-archive");
            try { startActivityForResult(picker, IMPORT_BETA_APK); }
            catch (Exception error) {
                DiagnosticLog.error("UPDATE_PICKER", error);
                alert("Nie można otworzyć wyboru APK.");
            }
        });
        note("Manifest: channel=beta, versionCode, versionName, changelog, apkUrl HTTPS, sha256. APK musi mieć ten sam identyfikator pakietu i podpis co obecna instalacja.");
        button("Kopia danych przed zmianą wersji", () -> go("backup"));
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
        if (request == IMPORT_BETA_APK) {
            if (result == RESULT_OK && data != null && data.getData() != null)
                updater.importSelected(data.getData());
            return;
        }
        if (request == EXPORT_DATA_BACKUP) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            try {
                String json = DataBackup.exportJson(db.getReadableDatabase(), prefs);
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("Brak dostępu do pliku.");
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                DiagnosticLog.event("DATA_BACKUP_EXPORTED");
                alert("Zapisano kopię. Sprawdź, czy plik .json jest widoczny w wybranym miejscu, zanim usuniesz aplikację.");
            } catch (Exception error) {
                DiagnosticLog.error("DATA_BACKUP_EXPORT", error);
                alert("Eksport kopii nie powiódł się. Nie usuwaj aplikacji.");
            }
            return;
        }
        if (request == IMPORT_DATA_BACKUP) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(data.getData());
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new IllegalStateException("Nie można odczytać pliku.");
                    byte[] block = new byte[8192];
                    int count;
                    while ((count = in.read(block)) != -1) {
                        if (out.size() + count > DataBackup.MAX_BYTES)
                            throw new IllegalArgumentException("Kopia przekracza limit 8 MB.");
                        out.write(block, 0, count);
                    }
                    bytes = out.toByteArray();
                }
                final String json = new String(bytes, StandardCharsets.UTF_8);
                new AlertDialog.Builder(this)
                    .setTitle("Zastąpić wszystkie dane?")
                    .setMessage("Przywrócenie NADPISZE obecną spiżarnię, czynności i remanenty. Zachowa PIN nowej instalacji. Czy masz kopię bieżącego stanu?")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Przywróć", (dialog, which) -> {
                        try {
                            DataBackup.restoreJson(db.getWritableDatabase(), prefs, json);
                            DiagnosticLog.event("DATA_BACKUP_RESTORED");
                            screen = "home";
                            unlocked = false;
                            render();
                            alert("Dane przywrócone. Odblokuj aplikację swoim obecnym PIN-em.");
                        } catch (Exception error) {
                            DiagnosticLog.error("DATA_BACKUP_RESTORE", error);
                            alert("Nie udało się przywrócić kopii. Dane bazy nie zostały nadpisane, jeśli walidacja lub transakcja zakończyła się błędem.");
                        }
                    }).show();
            } catch (Exception error) {
                DiagnosticLog.error("DATA_BACKUP_READ", error);
                alert("Nie można odczytać kopii danych.");
            }
            return;
        }
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
            super(context, "edhome-beta-preview.db", null, 2);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0)");
            addAuditTables(database);
            DiagnosticLog.event("DATABASE_CREATED");
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) {
                addAuditTables(database);
                DiagnosticLog.event("DATABASE_MIGRATED_1_TO_2");
            } else {
                DiagnosticLog.event("DATABASE_MIGRATION_REQUIRED");
                throw new IllegalStateException("Missing EDHOME database migration");
            }
        }


        private static void addAuditTables(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE audit_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, completed_at INTEGER, status TEXT NOT NULL)");
            database.execSQL("CREATE TABLE audit_rows (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, pantry_id INTEGER NOT NULL, name_snapshot TEXT NOT NULL, expected_qty INTEGER NOT NULL, counted_qty INTEGER, status TEXT NOT NULL DEFAULT 'pending', UNIQUE(session_id, pantry_id))");
            database.execSQL("CREATE TABLE audit_corrections (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, pantry_id INTEGER NOT NULL, old_qty INTEGER NOT NULL, new_qty INTEGER NOT NULL, changed_at INTEGER NOT NULL, UNIQUE(session_id, pantry_id))");
        }

        long openAuditId() {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id FROM audit_sessions WHERE status='open' ORDER BY id DESC LIMIT 1", null)) {
                return c.moveToFirst() ? c.getLong(0) : 0;
            }
        }

        long startAudit() {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                if (openAuditId() != 0) return openAuditId();
                long id;
                ContentValues session = new ContentValues();
                session.put("started_at", System.currentTimeMillis());
                session.put("status", "open");
                id = database.insertOrThrow("audit_sessions", null, session);
                database.execSQL("INSERT INTO audit_rows (session_id,pantry_id,name_snapshot,expected_qty) SELECT ?,id,name,qty FROM pantry",
                    new Object[]{id});
                long count;
                try (Cursor c = database.rawQuery(
                        "SELECT COUNT(*) FROM audit_rows WHERE session_id=?", new String[]{Long.toString(id)})) {
                    c.moveToFirst();
                    count = c.getLong(0);
                }
                if (count == 0) return 0; // rollback empty session
                database.setTransactionSuccessful();
                return id;
            } finally {
                database.endTransaction();
            }
        }

        int[] auditProgress(long id) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*),SUM(CASE WHEN status!='pending' THEN 1 ELSE 0 END) FROM audit_rows WHERE session_id=?",
                    new String[]{Long.toString(id)})) {
                if (!c.moveToFirst()) return new int[]{0, 0};
                return new int[]{c.getInt(0), c.getInt(1)};
            }
        }

        Cursor auditNext(long id) {
            return getReadableDatabase().rawQuery(
                "SELECT id,name_snapshot,expected_qty FROM audit_rows WHERE session_id=? AND status='pending' ORDER BY id ASC LIMIT 1",
                new String[]{Long.toString(id)});
        }

        Cursor auditRows(long id) {
            return getReadableDatabase().rawQuery(
                "SELECT name_snapshot,expected_qty,counted_qty,status FROM audit_rows WHERE session_id=? ORDER BY id",
                new String[]{Long.toString(id)});
        }

        void auditAnswer(long rowId, Integer count, String status) {
            ContentValues values = new ContentValues();
            values.put("status", status);
            if (count == null) values.putNull("counted_qty");
            else values.put("counted_qty", count);
            getWritableDatabase().update("audit_rows", values,
                "id=? AND status='pending' AND session_id IN (SELECT id FROM audit_sessions WHERE status='open')",
                new String[]{Long.toString(rowId)});
        }

        boolean auditUndo(long session) {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("status", "pending");
            values.putNull("counted_qty");
            return database.update("audit_rows", values,
                "id=(SELECT MAX(id) FROM audit_rows WHERE session_id=? AND status!='pending') AND session_id=?",
                new String[]{Long.toString(session), Long.toString(session)}) > 0;
        }

        void cancelAudit(long id) {
            ContentValues values = new ContentValues();
            values.put("status", "cancelled");
            values.put("completed_at", System.currentTimeMillis());
            getWritableDatabase().update("audit_sessions", values,
                "id=? AND status='open'", new String[]{Long.toString(id)});
        }

        String finishAudit(long session) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                try (Cursor status = database.rawQuery(
                        "SELECT status FROM audit_sessions WHERE id=?", new String[]{Long.toString(session)})) {
                    if (!status.moveToFirst() || !"open".equals(status.getString(0))) return "NOT_OPEN";
                }
                try (Cursor unreviewed = database.rawQuery(
                        "SELECT COUNT(*) FROM audit_rows WHERE session_id=? AND status='pending'",
                        new String[]{Long.toString(session)})) {
                    if (!unreviewed.moveToFirst() || unreviewed.getInt(0) != 0) return "PENDING";
                }
                try (Cursor items = database.rawQuery(
                        "SELECT r.pantry_id,r.expected_qty,r.counted_qty,r.status,p.qty FROM audit_rows r LEFT JOIN pantry p ON p.id=r.pantry_id WHERE r.session_id=?",
                        new String[]{Long.toString(session)})) {
                    while (items.moveToNext()) {
                        String state = items.getString(3);
                        if ("skip".equals(state)) continue;
                        if (items.isNull(4) || items.getInt(1) != items.getInt(4)) {
                            return "CONFLICT"; // snapshot is stale; no corrections applied
                        }
                    }
                }
                try (Cursor items = database.rawQuery(
                        "SELECT pantry_id,expected_qty,counted_qty FROM audit_rows WHERE session_id=? AND status!='skip' AND expected_qty!=counted_qty",
                        new String[]{Long.toString(session)})) {
                    while (items.moveToNext()) {
                        long itemId = items.getLong(0);
                        int before = items.getInt(1), after = items.getInt(2);
                        ContentValues changed = new ContentValues();
                        changed.put("qty", after);
                        int updated = database.update("pantry", changed, "id=? AND qty=?",
                            new String[]{Long.toString(itemId), Integer.toString(before)});
                        if (updated != 1) return "CONFLICT";
                        ContentValues history = new ContentValues();
                        history.put("session_id", session);
                        history.put("pantry_id", itemId);
                        history.put("old_qty", before);
                        history.put("new_qty", after);
                        history.put("changed_at", System.currentTimeMillis());
                        database.insertOrThrow("audit_corrections", null, history);
                    }
                }
                ContentValues complete = new ContentValues();
                complete.put("status", "completed");
                complete.put("completed_at", System.currentTimeMillis());
                if (database.update("audit_sessions", complete, "id=? AND status='open'",
                        new String[]{Long.toString(session)}) != 1) return "NOT_OPEN";
                database.setTransactionSuccessful();
                return "OK";
            } finally {
                database.endTransaction();
            }
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
