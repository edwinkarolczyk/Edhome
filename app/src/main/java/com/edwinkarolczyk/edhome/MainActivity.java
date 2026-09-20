package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.view.Gravity;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    private String calendarMonth = YearMonth.now().toString();
    private String calendarDay = LocalDate.now().toString();
    private String calendarView = "month";
    private String tasksFilter = "all";
    private String pantrySearch = "";
    private static final String[] HOME_TILE_IDS = {
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today"
    };
    private static final String[] TASK_PRIORITIES = {"low", "normal", "high", "urgent"};
    private static final String[] TASK_PRIORITY_LABELS = {
        "Niski", "Normalny", "Wysoki", "Pilny"
    };
    private static final int MIN_TASK_MINUTES = 1;
    private static final int MAX_TASK_MINUTES = 480;
    private static final String[] PLACE_TYPES = {"Dom", "Ogród", "Garaż", "Warsztat", "Pomieszczenie", "Inne"};
    private int bg, surface, ink, subdued, accent;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        DiagnosticLog.init(this);
        prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        // Beta DEV is deliberately PIN-free; never clear an old PIN or user data.
        unlocked = BetaUpdater.isBeta();
        db = new LocalDb(this);
        ReminderReceiver.schedule(this);
        updater = new BetaUpdater(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        DiagnosticLog.event("ACTIVITY_CREATED");
        render();
    }

    @Override public void onStop() {
        super.onStop();
        if (!BetaUpdater.isBeta()) unlocked = false;
        DiagnosticLog.event(BetaUpdater.isBeta()
            ? "ACTIVITY_STOPPED_BETA_NO_PIN" : "ACTIVITY_STOPPED_LOCKED");
        if (updater != null) updater.stop();
    }

    @Override public void onResume() {
        super.onResume();
        if (BetaUpdater.isBeta()) unlocked = true;
        if (root != null && !unlocked) render();
        if (unlocked && updater != null) updater.start();
    }

    @Override public void onBackPressed() {
        if (unlocked && "updates_advanced".equals(screen)) go("updates");
        else if (unlocked && "places".equals(screen)) go("home");
        else if (unlocked && "task_history".equals(screen)) go("tasks");
        else if (unlocked && !"home".equals(screen)) go("home");
        else super.onBackPressed();
    }

    private int dp(float d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void palette() {
        String theme = prefs.getString("theme", "Grafitowy");
        if ("Trener 2".equals(theme)) {
            bg = Color.rgb(9, 9, 9);
            surface = Color.rgb(20, 20, 20);
            ink = Color.rgb(244, 244, 244);
            subdued = Color.rgb(168, 168, 168);
            accent = Color.rgb(239, 43, 45);
        } else if ("Leśny".equals(theme)) {
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
        if ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                && color == surface)
            drawable.setStroke(dp(1), Color.rgb(48, 48, 48));
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
        b.setTextColor("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
            ? Color.WHITE : bg);
        b.setBackground(rounded(accent));
        b.setMinHeight(dp(56));
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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(12));
        TextView heading = text("EDHOME", 18, true);
        heading.setTextColor(accent);
        content.addView(heading);
        TextView bodyText = text(message, 16, false);
        content.addView(bodyText);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton("OK", null)
            .create();
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(rounded(surface));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
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
        int systemBarFlags = "Jasny".equals(prefs.getString("theme", "Grafitowy"))
            ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        getWindow().getDecorView().setSystemUiVisibility(systemBarFlags);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(48));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        if (!BetaUpdater.isBeta() && !prefs.contains("pin_hash")) setupPin();
        else if (!BetaUpdater.isBeta() && !unlocked) unlockPin();
        else {
            switch (screen) {
                case "tasks": tasks(); break;
                case "task_history": taskHistoryScreen(); break;
                case "pantry": pantry(); break;
                case "places": places(); break;
                case "calendar": calendar(); break;
                case "scanner": placeholder("Skaner", "Kamera i kody kreskowe/QR nie działają jeszcze w tej becie."); break;
                case "audit": audit(); break;
                case "settings": settings(); break;
                case "updates": updates(); break;
                case "updates_advanced": updatesAdvanced(); break;
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

        LinearLayout summary = card();
        summary.addView(text("Twój domowy plan", 21, true));
        int overdue = db.overdueTasks();
        summary.addView(text("Do zrobienia: " + db.openTasks()
            + "     Zaległe: " + overdue, 16, true));
        TextView reminder = text(overdue == 0
            ? "✓  Brak zaległych czynności."
            : "•  Otwórz zaległe i wybierz, co wykonać dziś.", 14, false);
        reminder.setTextColor(overdue == 0 ? accent : ink);
        summary.addView(reminder);
        summary.setOnClickListener(v -> {
            tasksFilter = overdue == 0 ? "today" : "overdue";
            go("tasks");
        });

        note("Menu • przytrzymaj kafelek, aby zmienić kolejność; przewijaj góra–dół ↓");
        LinearLayout tiles = tileGrid();
        for (String tileId : homeTileOrder()) {
            LinearLayout tile = homeTile(tiles, tileId);
            tile.setOnLongClickListener(v -> {
                android.content.ClipData data = android.content.ClipData.newPlainText(
                    "edhome-home-tile", tileId);
                return tile.startDragAndDrop(data, new View.DragShadowBuilder(tile),
                    null, 0);
            });
            tile.setOnDragListener((v, event) -> {
                if (event.getAction() == android.view.DragEvent.ACTION_DRAG_STARTED)
                    return event.getClipDescription() != null
                        && event.getClipDescription().hasMimeType(
                            android.content.ClipDescription.MIMETYPE_TEXT_PLAIN);
                if (event.getAction() == android.view.DragEvent.ACTION_DROP) {
                    if (event.getClipData() == null
                            || event.getClipData().getItemCount() == 0) return false;
                    CharSequence item = event.getClipData().getItemAt(0).getText();
                    if (item == null) return false;
                    return moveHomeTile(item.toString(), tileId);
                }
                return true;
            });
        }

        LinearLayout today = card();
        today.addView(text("Najbliższe czynności", 19, true));
        int displayed = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT title,due_date FROM tasks WHERE done=0 AND due_date IS NOT NULL "
                + "ORDER BY due_date ASC,id ASC LIMIT 4", null)) {
            while (c.moveToNext()) {
                displayed++;
                today.addView(text("• " + c.getString(1) + " — " + c.getString(0), 14, false));
            }
        }
        if (displayed == 0)
            today.addView(text("Brak zaplanowanych terminów.", 14, false));
        smallButton(today, "Zobacz wszystkie czynności →", () -> {
            tasksFilter = "all";
            go("tasks");
        });
        if (DiagnosticLog.enabled())
            button("Diagnostyka BETA", () -> go("diagnostics"));
        note("Działa offline. Przypomnienia włączysz w Ustawieniach; skaner "
            + "i synchronizacja są w kolejnych etapach.");
    }

    /** A validated, persisted nine-tile order; unknown and repeated IDs are ignored. */
    private java.util.List<String> homeTileOrder() {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        String saved = prefs.getString("home_tile_order", "");
        for (String id : saved.split(",")) {
            if (java.util.Arrays.asList(HOME_TILE_IDS).contains(id)
                    && !result.contains(id)) result.add(id);
        }
        for (String id : HOME_TILE_IDS) if (!result.contains(id)) result.add(id);
        return result;
    }

    private boolean moveHomeTile(String from, String to) {
        java.util.List<String> order = homeTileOrder();
        if (!order.contains(from) || !order.contains(to)) return false;
        if (from.equals(to)) return true;
        order.remove(from);
        order.add(order.indexOf(to), from);
        prefs.edit().putString("home_tile_order", android.text.TextUtils.join(",", order))
            .apply();
        DiagnosticLog.event("HOME_TILES_REORDERED");
        render();
        return true;
    }

    private LinearLayout homeTile(LinearLayout grid, String id) {
        switch (id) {
            case "tasks": return updateTile(grid, "✓", "Czynności", true, () -> {
                tasksFilter = "all"; go("tasks");
            });
            case "calendar": return updateTile(grid, "▦", "Kalendarz", false,
                () -> go("calendar"));
            case "places": return updateTile(grid, "⌂", "Miejsca", false,
                () -> go("places"));
            case "pantry": return updateTile(grid, "▣", "Spiżarnia", false,
                () -> go("pantry"));
            case "audit": return updateTile(grid, "◫", "Remanent", false,
                () -> go("audit"));
            case "updates": return updateTile(grid, "↻", "Aktualizacje", false,
                () -> go("updates"));
            case "backup": return updateTile(grid, "▤", "Kopia danych", false,
                () -> go("backup"));
            case "settings": return updateTile(grid, "⚙", "Ustawienia", false,
                () -> go("settings"));
            case "today": return updateTile(grid, "◷", "Na dziś", false, () -> {
                tasksFilter = "today"; go("tasks");
            });
            default: throw new IllegalArgumentException("Unknown home tile");
        }
    }

    private void tasks() {
        header("Czynności • plan i wykonania");
        note("Czynności mogą działać samodzielnie lub być opcjonalnie przypięte do miejsca.");
        button("⌂ Miejsca", () -> go("places"));
        button("+ Nowa czynność", () -> editTask(null, "", "", "once", 1));
        button("▦ Kalendarz czynności", () -> go("calendar"));
        note("Zaległe: " + db.overdueTasks()
            + " • Wykonanie cyklicznej wyznacza kolejny termin.");
        HorizontalScrollView filters = new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp(5), 0, dp(8));
        filters.addView(filterRow);
        body.addView(filters);
        for (String[] filter : new String[][] {
            {"all", "Wszystkie"}, {"today", "Dzisiaj"},
            {"overdue", "Zaległe"}, {"upcoming", "Nadchodzące"},
            {"done", "Wykonane"} }) {
            Button chip = new Button(this);
            chip.setAllCaps(false);
            chip.setText(filter[1]);
            chip.setTextColor(filter[0].equals(tasksFilter)
                ? ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                    ? Color.WHITE : bg) : ink);
            chip.setBackground(rounded(filter[0].equals(tasksFilter) ? accent : surface));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(48));
            cp.setMargins(0, 0, dp(8), 0);
            filterRow.addView(chip, cp);
            chip.setOnClickListener(v -> {
                tasksFilter = filter[0];
                render();
            });
        }
        button("Historia wszystkich wykonań", () -> go("task_history"));
        String today = LocalDate.now().toString();
        String condition;
        String[] args = null;
        switch (tasksFilter) {
            case "today": condition = "done=0 AND due_date=?"; args = new String[]{today}; break;
            case "overdue": condition = "done=0 AND due_date<?"; args = new String[]{today}; break;
            case "upcoming": condition = "done=0 AND due_date>?"; args = new String[]{today}; break;
            case "done": condition = "done=1"; break;
            default: condition = "1=1";
        }
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT id,title,done,due_date,repeat_rule,repeat_every FROM tasks "
                + "WHERE " + condition + " ORDER BY done ASC,"
                + "CASE WHEN due_date IS NULL THEN 1 ELSE 0 END,"
                + "due_date ASC,id DESC", args)) {
            if (cursor.getCount() == 0)
                note("Brak czynności w tym widoku. Wybierz inny filtr lub dodaj nową.");
            while (cursor.moveToNext()) {
                drawTask(cursor.getLong(0), cursor.getString(1), cursor.getInt(2) == 1,
                    cursor.isNull(3) ? "" : cursor.getString(3), cursor.getString(4),
                    cursor.getInt(5));
            }
        }
    }

    private void smallButton(LinearLayout container, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        if ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))) {
            b.setTextColor(Color.WHITE);
            b.setBackground(rounded(Color.rgb(41, 41, 41)));
            b.setAllCaps(false);
        }
        b.setOnClickListener(v -> action.run());
        container.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }

    private void drawTask(long id, String name, boolean done, String due,
            String rule, int every) {
        LinearLayout box = card();
        CheckBox check = new CheckBox(this);
        check.setText(name);
        check.setChecked(done);
        check.setTextSize(17);
        check.setTextColor(done ? subdued : ink);
        check.setButtonTintList(ColorStateList.valueOf(accent));
        box.addView(check);
        String[] planning = db.taskPlanning(id);
        String description = (due.isEmpty() ? "Bez terminu" : "Termin: " + due)
            + " • " + TaskRules.label(rule, every)
            + " • " + planning[1] + " min"
            + " • Priorytet: " + TASK_PRIORITY_LABELS[
                Math.max(0, java.util.Arrays.asList(TASK_PRIORITIES)
                    .indexOf(planning[0]))];
        if (!done && !due.isEmpty() && due.compareTo(LocalDate.now().toString()) < 0)
            description += " • ZALEGŁE";
        if (done) description += " • Wykonane";
        String place = db.placeLabel(id);
        if (!place.isEmpty()) description += " • " + place;
        TextView meta = text(description, 13, false);
        meta.setTextColor(subdued);
        box.addView(meta);
        check.setOnCheckedChangeListener((v, value) -> {
            if (value) {
                db.completeTask(id);
                DiagnosticLog.event("TASK_COMPLETED");
            } else {
                db.reopenTask(id);
                DiagnosticLog.event("TASK_REOPENED");
            }
            render();
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(actions);
        taskAction(actions, "Edytuj", () -> editTask(id, name, due, rule, every));
        taskAction(actions, "Historia", () -> showTaskHistory(id, name));
        taskAction(actions, "Usuń", () ->
            new AlertDialog.Builder(this).setTitle("Usunąć czynność?")
                .setMessage(name + "\\nHistoria jej wykonań zostanie zachowana.")
                .setNegativeButton("Nie", null)
                .setPositiveButton("Usuń", (dialog, which) -> {
                    db.deleteTask(id);
                    DiagnosticLog.event("TASK_DELETED");
                    render();
                }).show());
    }

    private void taskAction(LinearLayout row, String label, Runnable run) {
        TextView action = text(label, 13, true);
        action.setGravity(Gravity.CENTER);
        action.setBackground(rounded(surface == bg ? accent : bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.setMargins(dp(2), dp(5), dp(2), 0);
        row.addView(action, lp);
        action.setOnClickListener(v -> run.run());
    }

    private ArrayAdapter<String> themeSpinnerAdapter(java.util.List<String> items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int position, View convertView,
                    android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(ink);
                    view.setPadding(dp(9), dp(12), dp(9), dp(12));
                }
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                    android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(ink);
                    view.setBackgroundColor(surface);
                    view.setPadding(dp(10), dp(14), dp(10), dp(14));
                }
                return view;
            }
        };
    }

    private void editTask(Long id, String existingName, String existingDate,
            String existingRule, int existingEvery) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.addView(text(id == null ? "Nowa czynność" : "Edytuj czynność",
            21, true));

        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Nazwa czynności");
        name.setText(existingName);
        name.setTextColor(ink);
        name.setHintTextColor(subdued);
        name.setTextSize(18);
        form.addView(text("Co trzeba zrobić?", 16, true));
        form.addView(name);

        String[] currentPlanning = id == null
            ? new String[]{"normal", "30"} : db.taskPlanning(id);
        form.addView(text("Priorytet", 16, true));
        Spinner priority = new Spinner(this);
        priority.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(TASK_PRIORITY_LABELS)));
        priority.setSelection(Math.max(0,
            java.util.Arrays.asList(TASK_PRIORITIES).indexOf(currentPlanning[0])));
        form.addView(priority);
        form.addView(text("Szacowany czas wykonania (minuty)", 16, true));
        EditText duration = new EditText(this);
        duration.setSingleLine(true);
        duration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        duration.setHint("Od 1 do 480 minut");
        duration.setText(currentPlanning[1]);
        duration.setTextColor(ink);
        duration.setHintTextColor(subdued);
        form.addView(duration);

        EditText date = new EditText(this);
        date.setSingleLine(true);
        date.setHint("Termin: RRRR-MM-DD (opcjonalnie)");
        date.setText(existingDate);
        date.setTextColor(ink);
        date.setHintTextColor(subdued);
        date.setFocusable(false);
        form.addView(text("Termin wykonania", 16, true));
        date.setOnClickListener(v -> {
            LocalDate initial;
            try { initial = LocalDate.parse(date.getText().toString()); }
            catch (Exception ignored) { initial = LocalDate.now(); }
            DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, day) ->
                    date.setText(LocalDate.of(year, month + 1, day).toString()),
                initial.getYear(), initial.getMonthValue() - 1,
                initial.getDayOfMonth());
            picker.show();
        });
        form.addView(date);
        smallButton(form, "Wybierz datę", () -> date.performClick());
        smallButton(form, "Bez terminu", () -> date.setText(""));

        TextView label = text("Powtarzanie", 15, true);
        form.addView(label);
        Spinner repeat = new Spinner(this);
        repeat.setAdapter(themeSpinnerAdapter(java.util.Arrays.asList(TaskRules.LABELS)));
        repeat.setSelection(TaskRules.index(existingRule));
        form.addView(repeat);
        EditText interval = new EditText(this);
        interval.setSingleLine(true);
        interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        interval.setHint("N: co ile dni / tygodni / miesięcy / lat");
        interval.setText(String.valueOf(Math.max(1, existingEvery)));
        interval.setTextColor(ink);
        interval.setHintTextColor(subdued);
        interval.setVisibility(TaskRules.custom(existingRule) ? View.VISIBLE : View.GONE);
        repeat.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int position, long selectedId) {
                interval.setVisibility(TaskRules.custom(TaskRules.RULES[position])
                    ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        form.addView(interval);
        form.addView(text("Dla pór roku wybierz termin przygotowania przed sezonem. "
            + "Będzie powtarzany w kolejnych latach.", 13, false));
        form.addView(text("Miejsce (opcjonalnie)", 15, true));
        java.util.ArrayList<Long> placeIds = new java.util.ArrayList<>();
        java.util.ArrayList<String> placeNames = new java.util.ArrayList<>();
        placeIds.add(null);
        placeNames.add("Bez przypisanego miejsca");
        try (Cursor places = db.getReadableDatabase().rawQuery(
                "SELECT id,name,kind FROM places ORDER BY name COLLATE NOCASE", null)) {
            while (places.moveToNext()) {
                placeIds.add(places.getLong(0));
                placeNames.add(places.getString(1) + " • " + places.getString(2));
            }
        }
        Spinner chosenPlace = new Spinner(this);
        chosenPlace.setAdapter(themeSpinnerAdapter(placeNames));
        if (id != null) {
            Long currentPlace = db.taskPlaceId(id);
            if (currentPlace != null) {
                for (int i = 1; i < placeIds.size(); i++) {
                    if (currentPlace.equals(placeIds.get(i))) {
                        chosenPlace.setSelection(i);
                        break;
                    }
                }
            }
        }
        form.addView(chosenPlace);
        form.addView(text("Miejsca dodasz w module Miejsca. "
            + "Czynności bez miejsca działają normalnie.", 12, false));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String rule = TaskRules.RULES[repeat.getSelectedItemPosition()];
                int every = 1;
                if (TaskRules.custom(rule)) {
                    try { every = Integer.parseInt(interval.getText().toString().trim()); }
                    catch (NumberFormatException error) {
                        interval.setError("Wpisz liczbę od 1 do 365.");
                        return;
                    }
                }
                int estimatedMinutes;
                try {
                    estimatedMinutes = Integer.parseInt(
                        duration.getText().toString().trim());
                } catch (NumberFormatException problem) {
                    duration.setError("Podaj czas od 1 do 480 minut.");
                    return;
                }
                if (estimatedMinutes < MIN_TASK_MINUTES
                        || estimatedMinutes > MAX_TASK_MINUTES) {
                    duration.setError("Podaj czas od 1 do 480 minut.");
                    return;
                }
                String selectedPriority = TASK_PRIORITIES[
                    priority.getSelectedItemPosition()];
                String title = name.getText().toString().trim();
                String due = date.getText().toString().trim();
                String error = TaskRules.validate(title, due, rule, every);
                if (error != null) { alert(error); return; }
                db.saveTask(id, title, due, rule, every,
                    placeIds.get(chosenPlace.getSelectedItemPosition()),
                    selectedPriority, estimatedMinutes);
                ReminderReceiver.schedule(this);
                DiagnosticLog.event(id == null ? "TASK_ADDED" : "TASK_EDITED");
                dialog.dismiss();
                render();
            }));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(surface));
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics()
                .widthPixels * 0.94f), -2);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ink);
    }

    private void showTaskHistory(long id, String name) {
        StringBuilder history = new StringBuilder();
        int found = 0;
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT completed_at,due_date,next_due_date FROM task_history "
                + "WHERE task_id=? ORDER BY id DESC LIMIT 100",
                new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                found++;
                String time = Instant.ofEpochMilli(cursor.getLong(0))
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                history.append(time);
                if (!cursor.isNull(1)) history.append(" • termin ").append(cursor.getString(1));
                if (!cursor.isNull(2)) history.append(" → ").append(cursor.getString(2));
                history.append("\n");
            }
        }
        new AlertDialog.Builder(this).setTitle("Historia: " + name)
            .setMessage(found == 0 ? "Brak zapisanych wykonań." : history.toString())
            .setPositiveButton("OK", null).show();
    }

    private void taskHistoryScreen() {
        title("Historia wykonanych czynności");
        button("← Czynności", () -> go("tasks"));
        note("Ostatnie 100 wykonań. Historia zostaje również po usunięciu czynności z listy.");
        int count = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT title_snapshot,completed_at,due_date,next_due_date "
                + "FROM task_history ORDER BY id DESC LIMIT 100", null)) {
            while (c.moveToNext()) {
                count++;
                LinearLayout entry = card();
                entry.addView(text(c.getString(0), 17, true));
                String finished = Instant.ofEpochMilli(c.getLong(1))
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                entry.addView(text("Wykonano: " + finished, 14, false));
                if (!c.isNull(2)) entry.addView(text("Termin: " + c.getString(2), 13, false));
                if (!c.isNull(3)) entry.addView(text("Następnie: " + c.getString(3), 13, false));
            }
        }
        if (count == 0) note("Brak zapisanych wykonań.");
        button("Wróć do czynności", () -> go("tasks"));
    }

    private void calendar() {
        header("Kalendarz • czynności");
        YearMonth month = YearMonth.parse(calendarMonth);
        LocalDate selected = LocalDate.parse(calendarDay);
        note(month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE,
            new Locale("pl", "PL")) + " " + month.getYear());
        LinearLayout views = new LinearLayout(this);
        views.setOrientation(LinearLayout.VERTICAL);
        body.addView(views);
        String[][] modes = {{"month", "Miesiąc"}, {"week", "Tydzień"},
            {"day", "Dzień"}, {"agenda", "Agenda"}};
        for (int i = 0; i < modes.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            views.addView(row);
            for (int k = i; k < i + 2; k++) {
                String[] mode = modes[k];
                Button view = new Button(this);
                view.setText(mode[1] + (mode[0].equals(calendarView) ? " ✓" : ""));
                view.setAllCaps(false);
                view.setTextColor(mode[0].equals(calendarView)
                    ? ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                        ? Color.WHITE : bg) : ink);
                view.setBackground(rounded(mode[0].equals(calendarView)
                    ? accent : surface));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
                p.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(view, p);
                view.setOnClickListener(v -> { calendarView = mode[0]; render(); });
            }
        }
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(nav);
        Button prev = new Button(this);
        prev.setText("← " + ("week".equals(calendarView) ? "Tydzień"
            : "day".equals(calendarView) ? "Dzień"
            : "agenda".equals(calendarView) ? "30 dni" : "Miesiąc"));
        prev.setAllCaps(false);
        nav.addView(prev, new LinearLayout.LayoutParams(0, -2, 1));
        prev.setOnClickListener(v -> {
            LocalDate previous = "week".equals(calendarView) ? selected.minusWeeks(1)
                : "day".equals(calendarView) ? selected.minusDays(1)
                : "agenda".equals(calendarView) ? selected.minusDays(30)
                : month.minusMonths(1).atDay(1);
            calendarMonth = YearMonth.from(previous).toString();
            calendarDay = previous.toString();
            render();
        });
        Button next = new Button(this);
        next.setText(("week".equals(calendarView) ? "Tydzień"
            : "day".equals(calendarView) ? "Dzień"
            : "agenda".equals(calendarView) ? "30 dni" : "Miesiąc") + " →");
        next.setAllCaps(false);
        nav.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        next.setOnClickListener(v -> {
            LocalDate following = "week".equals(calendarView) ? selected.plusWeeks(1)
                : "day".equals(calendarView) ? selected.plusDays(1)
                : "agenda".equals(calendarView) ? selected.plusDays(30)
                : month.plusMonths(1).atDay(1);
            calendarMonth = YearMonth.from(following).toString();
            calendarDay = following.toString();
            render();
        });
        button("Dzisiaj", () -> {
            calendarDay = LocalDate.now().toString();
            calendarMonth = YearMonth.from(LocalDate.now()).toString();
            render();
        });
        LinearLayout summary = card();
        summary.addView(text("Wybrano: " + selected.format(
            DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", new Locale("pl", "PL"))),
            18, true));
        int overdue = db.overdueTasks();
        TextView warning = text(overdue == 0
            ? "✓  Bez zaległości" : "•  Zaległe czynności: " + overdue, 15, false);
        warning.setTextColor(overdue == 0 ? accent : ink);
        summary.addView(warning);
        if (overdue > 0) smallButton(summary, "Pokaż zaległe →", () -> {
            tasksFilter = "overdue";
            go("tasks");
        });

        Map<String, Integer> counts = new HashMap<>();
        LocalDate countStart = "week".equals(calendarView)
            ? selected.with(java.time.DayOfWeek.MONDAY) : month.atDay(1);
        LocalDate countEnd = "week".equals(calendarView)
            ? countStart.plusDays(6) : month.atEndOfMonth();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT due_date,COUNT(*) FROM tasks WHERE done=0 AND due_date>=? "
                + "AND due_date<=? GROUP BY due_date",
                new String[]{countStart.toString(), countEnd.toString()})) {
            while (c.moveToNext()) counts.put(c.getString(0), c.getInt(1));
        }

        if (!"day".equals(calendarView) && !"agenda".equals(calendarView)) {
        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(headings);
        for (String day : new String[]{"Pn","Wt","Śr","Cz","Pt","So","Nd"}) {
            TextView label = text(day, 12, true);
            label.setTextColor(subdued);
            label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            headings.addView(label, new LinearLayout.LayoutParams(0, dp(32), 1));
        }

        LocalDate first = "week".equals(calendarView)
            ? selected.with(java.time.DayOfWeek.MONDAY) : month.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - 1);
        int weeks = "week".equals(calendarView) ? 1
            : (first.getDayOfWeek().getValue() - 1
                + month.lengthOfMonth() + 6) / 7;
        if (!"day".equals(calendarView) && !"agenda".equals(calendarView))
        for (int week = 0; week < weeks; week++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            body.addView(row);
            for (int weekday = 0; weekday < 7; weekday++) {
                LocalDate day = gridStart.plusDays(week * 7L + weekday);
                String iso = day.toString();
                boolean inMonth = "week".equals(calendarView)
                    || YearMonth.from(day).equals(month);
                TextView tile = text(String.valueOf(day.getDayOfMonth())
                    + (inMonth && counts.containsKey(iso)
                        ? "\n• " + counts.get(iso) : ""), 13, false);
                tile.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tile.setMinHeight(dp(54));
                tile.setTextColor(inMonth ? ink : subdued);
                tile.setPadding(dp(1), dp(7), dp(1), dp(4));
                if (iso.equals(selected.toString())) {
                    tile.setBackground(rounded(accent));
                    tile.setTextColor("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                    ? Color.WHITE : bg);
                } else if (iso.equals(LocalDate.now().toString())) {
                    tile.setBackground(rounded(surface));
                }
                row.addView(tile, new LinearLayout.LayoutParams(0, dp(58), 1));
                tile.setOnClickListener(v -> {
                    calendarDay = iso;
                    calendarMonth = YearMonth.from(day).toString();
                    render();
                });
            }
        }
        }
        if ("agenda".equals(calendarView)) {
            LinearLayout agenda = card();
            agenda.addView(text("Nadchodzące 30 dni", 17, true));
            int shown = 0;
            try (Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT due_date,title FROM tasks WHERE done=0 AND due_date>=? "
                    + "AND due_date<=? ORDER BY due_date,id LIMIT 100",
                    new String[]{selected.toString(), selected.plusDays(29).toString()})) {
                while (c.moveToNext()) {
                    shown++;
                    agenda.addView(text("• " + c.getString(0) + " — " + c.getString(1),
                        14, false));
                }
            }
            if (shown == 0) agenda.addView(text("Brak terminów.", 14, false));
        }
        title("Termin: " + selected.toString());
        int found = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,title,done,due_date,repeat_rule,repeat_every "
                + "FROM tasks WHERE done=0 AND due_date=? ORDER BY title COLLATE NOCASE",
                new String[]{selected.toString()})) {
            while (c.moveToNext()) {
                found++;
                drawTask(c.getLong(0), c.getString(1), false,
                    c.getString(3), c.getString(4), c.getInt(5));
            }
        }
        if (found == 0) note("Brak zaplanowanych czynności na ten dzień.");
        button("+ Dodaj czynność", () ->
            editTask(null, "", selected.toString(), "once", 1));
        button("Wszystkie czynności", () -> {
            tasksFilter = "all";
            go("tasks");
        });
        note("Miesiąc, tydzień, dzień i agenda pokazują te same zapisane czynności. "
            + "Planowanie dostępności domowników będzie rozwijane osobno.");
    }

    private void places() {
        header("Miejsca • gospodarstwo");
        note("Miejsca są opcjonalnym powiązaniem czynności. "
            + "Usunięcie miejsca nie usunie przypisanych czynności.");
        button("+ Dodaj miejsce", () -> placeEditor(null, "", "Dom"));
        int count = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,name,kind FROM places ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                count++;
                long id = c.getLong(0);
                String name = c.getString(1), kind = c.getString(2);
                LinearLayout card = card();
                card.addView(text(name, 19, true));
                card.addView(text(kind, 13, false));
                try (Cursor countTasks = db.getReadableDatabase().rawQuery(
                        "SELECT COUNT(*) FROM tasks WHERE place_id=?",
                        new String[]{Long.toString(id)})) {
                    if (countTasks.moveToFirst())
                        card.addView(text("Przypisane czynności: "
                            + countTasks.getInt(0), 14, false));
                }
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(actions);
                taskAction(actions, "Edytuj", () -> placeEditor(id, name, kind));
                taskAction(actions, "Usuń", () -> new AlertDialog.Builder(this)
                    .setTitle("Usunąć miejsce?")
                    .setMessage(name + "\\nCzynności pozostaną bez przypisania.")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Usuń", (d, w) -> {
                        db.deletePlace(id);
                        DiagnosticLog.event("PLACE_DELETED");
                        render();
                    }).show());
            }
        }
        if (count == 0) note("Dodaj np. Ogród, Garaż lub Kuchnia.");
        button("Wróć do czynności", () -> go("tasks"));
    }

    private void placeEditor(Long id, String name, String kind) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(8));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(name);
        input.setHint("Nazwa miejsca");
        layout.addView(input);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, PLACE_TYPES));
        for (int i = 0; i < PLACE_TYPES.length; i++)
            if (PLACE_TYPES[i].equals(kind)) type.setSelection(i);
        layout.addView(type);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(id == null ? "Nowe miejsce" : "Edycja miejsca")
            .setView(layout)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty() || value.length() > 160) {
                    input.setError("Podaj nazwę (maks. 160 znaków).");
                    return;
                }
                try {
                    if (!db.savePlace(id, value,
                            PLACE_TYPES[type.getSelectedItemPosition()])) {
                        input.setError("Miejsce o tej nazwie już istnieje.");
                        return;
                    }
                    DiagnosticLog.event(id == null ? "PLACE_ADDED" : "PLACE_EDITED");
                    dialog.dismiss();
                    render();
                } catch (Exception error) {
                    DiagnosticLog.error("PLACE_SAVE", error);
                    alert("Nie można zapisać miejsca.");
                }
            }));
        dialog.show();
    }

    private void pantry() {
        header("Spiżarnia • lokalne zapasy");
        button("+ Dodaj produkt", () -> pantryProductDialog(null, ""));
        button("◫ Rozpocznij / wznów remanent", () -> go("audit"));
        button(pantrySearch.isEmpty() ? "⌕ Szukaj produktu" :
            "⌕ Szukaj: " + pantrySearch, () -> {
            EditText search = new EditText(this);
            search.setSingleLine(true);
            search.setText(pantrySearch);
            search.setHint("Nazwa produktu");
            new AlertDialog.Builder(this).setTitle("Wyszukaj produkt")
                .setView(search).setNegativeButton("Anuluj", null)
                .setNeutralButton("Wyczyść", (d, w) -> {
                    pantrySearch = "";
                    render();
                })
                .setPositiveButton("Szukaj", (d, w) -> {
                    pantrySearch = search.getText().toString().trim();
                    render();
                }).show();
        });
        int matched = 0;
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT id,name,qty FROM pantry ORDER BY name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                if (!pantrySearch.isEmpty() && !name.toLowerCase(Locale.ROOT)
                    .contains(pantrySearch.toLowerCase(Locale.ROOT))) continue;
                matched++;
                int qty = cursor.getInt(2);
                LinearLayout box = card();
                box.addView(text(name + "  •  " + qty + " szt.", 18, true));
                LinearLayout quick = new LinearLayout(this);
                quick.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(quick);
                taskAction(quick, "＋ 1", () -> {
                    db.changeStock(id, 1);
                    DiagnosticLog.event("PANTRY_INCREMENT");
                    render();
                });
                taskAction(quick, "－ 1", () -> {
                    db.changeStock(id, -1);
                    DiagnosticLog.event("PANTRY_DECREMENT");
                    render();
                });
                taskAction(quick, "Ustaw ilość", () -> {
                    EditText count = new EditText(this);
                    count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    count.setSingleLine(true);
                    count.setText(String.valueOf(qty));
                    new AlertDialog.Builder(this).setTitle(name + " • ilość")
                        .setView(count).setNegativeButton("Anuluj", null)
                        .setPositiveButton("Zapisz", (d, w) -> {
                            try {
                                int value = Integer.parseInt(count.getText().toString().trim());
                                if (value < 0 || value > 100000000) {
                                    alert("Podaj ilość od 0 do 100 000 000.");
                                    return;
                                }
                                db.setStock(id, value);
                                DiagnosticLog.event("PANTRY_QUANTITY_SET");
                                render();
                            } catch (NumberFormatException invalid) {
                                alert("Podaj liczbę całkowitą.");
                            }
                        }).show();
                });
                LinearLayout management = new LinearLayout(this);
                management.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(management);
                taskAction(management, "Zmień nazwę", () -> pantryProductDialog(id, name));
                taskAction(management, "Usuń", () -> {
                    if (db.openAuditId() != 0) {
                        alert("Najpierw zakończ lub anuluj remanent. "
                            + "W jego trakcie nie można usuwać produktów.");
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("Usunąć produkt?")
                        .setMessage(name + " • " + qty + " szt.")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Usuń", (d, w) -> {
                            db.deleteStock(id);
                            DiagnosticLog.event("PANTRY_PRODUCT_DELETED");
                            render();
                        }).show();
                });
            }
        }
        if (matched == 0) note(pantrySearch.isEmpty()
            ? "Spiżarnia jest pusta. Dodaj pierwszy produkt."
            : "Nie znaleziono produktów. Wyczyść wyszukiwanie.");
        note("Ta wersja zapisuje ilości w sztukach. Jednostki kg/l i skaner będą kolejnym etapem.");
    }

    private void pantryProductDialog(Long id, String existingName) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(existingName);
        input.setHint("Nazwa produktu");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(id == null ? "Dodaj do spiżarni" : "Zmień nazwę produktu")
            .setView(input).setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.length() > 160) {
                    input.setError("Podaj nazwę (maks. 160 znaków).");
                    return;
                }
                if (id == null) {
                    db.addStock(name);
                    DiagnosticLog.event("PANTRY_PRODUCT_ADDED");
                } else if (!db.renameStock(id, name)) {
                    input.setError("Produkt o tej nazwie już istnieje.");
                    return;
                } else DiagnosticLog.event("PANTRY_PRODUCT_RENAMED");
                dialog.dismiss();
                render();
            }));
        dialog.show();
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
        note("Trener 2: niemal czarne tło, grafitowe karty, czerwone przyciski "
            + "i jasne podpisy — paleta z aplikacji Trener 2.");
        for (String theme : new String[]{"Grafitowy", "Leśny", "Jasny", "Trener 2"}) {
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
        boolean reminders = prefs.getBoolean("reminders_enabled", false);
        button(reminders ? "Przypomnienia: WŁĄCZONE" : "Przypomnienia: WYŁĄCZONE", () -> {
            boolean enabled = !prefs.getBoolean("reminders_enabled", false);
            prefs.edit().putBoolean("reminders_enabled", enabled).apply();
            if (enabled && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    7130);
            ReminderReceiver.schedule(this);
            render();
        });
        note("Lokalne przypomnienie o 09:00 dla zaległych i dzisiejszych czynności. "
            + "Android może opóźnić nieprecyzyjny alarm przez oszczędzanie baterii. "
            + "Tytuły czynności nie pojawiają się na ekranie blokady.");
        if (DiagnosticLog.enabled()) button("Diagnostyka BETA", () -> go("diagnostics"));
        button("Kopia danych / przenoszenie", () -> go("backup"));
        note("Dane pozostają lokalne. Przed zmianą instalacji zapisz kopię poza aplikacją.");
    }


    private void backup() {
        header("Kopia danych • przenoszenie między instalacjami");
        note("Eksport zawiera czynności, miejsca i ich przypisania, spiżarnię, historię, bieżący remanent oraz ustawienia gospodarstwa.");
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
        note("Przed aktualizacją sprawdź zapisany plik JSON. Poprzednie kopie 0.1.4–0.1.8 można zaimportować.");
    }

    /** Update dashboard: no feed URL, SHA, manifest or developer text in primary UI. */
    private void updates() {
        title("EDHOME  •  " + (BetaUpdater.isBeta() ? "BETA" : "STABLE"));
        note("Aktualizacje aplikacji");
        button("← Panel główny", () -> go("home"));
        LinearLayout version = card();
        version.addView(text("Zainstalowana wersja", 15, false));
        version.addView(text(BuildConfig.VERSION_NAME, 29, true));
        version.addView(text("Wersja instalacyjna: " + BuildConfig.VERSION_CODE, 13, false));
        TextView installed = text("✓  Gotowa do użycia", 15, true);
        installed.setTextColor(accent);
        version.addView(installed);

        LinearLayout status = card();
        status.addView(text("Aktualizacje automatyczne", 19, true));
        if (!BetaUpdater.isBeta()) {
            status.addView(text("Google Play • aktualizacja lub Później", 15, false));
        } else if (updater.configuredFeed().isEmpty()) {
            TextView line = text("○  Kanał pobierania nie jest podłączony", 16, true);
            line.setTextColor(subdued);
            status.addView(line);
            status.addView(text("Na razie wybierasz APK z telefonu. "
                + "Sam podpis aplikacji nie uruchamia automatycznych pobrań.", 14, false));
        } else if (updater.channelVerified()) {
            TextView line = text("✓  Kanał aktualizacji dostępny", 16, true);
            line.setTextColor(accent);
            status.addView(line);
            status.addView(text("Nowe wydania będą pobierane z oficjalnego kanału EDHOME. "
                + "Instalację potwierdzasz w Androidzie.", 14, false));
        } else {
            TextView line = text("!  Kanał aktualizacji jeszcze niedostępny", 16, true);
            line.setTextColor(ink);
            status.addView(line);
            status.addView(text("Adres jest już wpisany w aplikację — niczego nie musisz "
                + "konfigurować ani szukać w internecie. Gdy plik aktualizacji "
                + "zostanie opublikowany, aplikacja wykryje go sama. "
                + "Na razie wybierz podpisany APK przyciskiem Instaluj APK.", 14, false));
        }

        note("Menu • 3 × 3, przewijaj ekran góra–dół ↓");
        LinearLayout tiles = tileGrid();
        updateTile(tiles, "↻", "Sprawdź\naktualizację", true, () -> {
            if (!BetaUpdater.isBeta()) updater.openPlay();
            else if (updater.configuredFeed().isEmpty())
                alert("Automatyczny kanał nie jest jeszcze podłączony. "
                    + "Możesz zainstalować nowy APK z telefonu.");
            else updater.check(true);
        });
        if (BetaUpdater.isBeta()) {
            updateTile(tiles, "↓", "Instaluj\nAPK", false, () -> {
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("application/vnd.android.package-archive");
                try { startActivityForResult(picker, IMPORT_BETA_APK); }
                catch (Exception error) {
                    DiagnosticLog.error("UPDATE_PICKER", error);
                    alert("Nie można wybrać APK.");
                }
            });
            updateTile(tiles, "✓", "Pobrany\nAPK", false, () -> updater.installReady());
        } else {
            updateTile(tiles, "▶", "Otwórz\nGoogle Play", false, () -> updater.openPlay());
            updateTile(tiles, "i", "Kanał\nStable", false, () ->
                alert("Stable pobiera aktualizacje z Google Play. Dostępny jest przycisk Później."));
        }
        updateTile(tiles, "▣", "Kopia\ndanych", false, () -> go("backup"));
        updateTile(tiles, "⚙", "Opcje\nzaawansowane", false, () -> go("updates_advanced"));
        updateTile(tiles, "◉", "Status\nkanału", false, () ->
            alert(!BetaUpdater.isBeta() ? "Kanał Stable: Google Play."
                : updater.configuredFeed().isEmpty()
                    ? "Brak źródła HTTPS. Możesz instalować APK ręcznie."
                    : updater.channelVerified()
                        ? "Kanał EDHOME został odczytany. Aktualizacje są dostępne."
                        : "Kanał EDHOME nie został jeszcze potwierdzony. "
                            + "Adres jest zapisany automatycznie. "
                            + "Nie musisz wpisywać żadnego HTTPS; na razie "
                            + "wybierz podpisany APK z GitHub Actions."));
        updateTile(tiles, "i", "Wersja\naplikacji", false, () ->
            alert("EDHOME " + BuildConfig.VERSION_NAME
                + "\nversionCode: " + BuildConfig.VERSION_CODE));
        updateTile(tiles, "◷", "Co\nnowego", false, () ->
            alert("EDHOME " + BuildConfig.VERSION_NAME
                + "\nUkład kafelków 3 × 3, przewijanie pionowe. "
                + "Kolejne wydania zachowują zgodność podpisu APK."));
        updateTile(tiles, "⌂", "Panel\ngłówny", false, () -> go("home"));
        note(BetaUpdater.isBeta()
            ? "Beta: nowa, zweryfikowana wersja ma pierwszeństwo. Instalację potwierdzasz w Androidzie."
            : "Stable: możesz wybrać Aktualizuj lub Później.");
    }

    /** Exactly three square tiles per row; the outer page owns vertical scrolling. */
    private LinearLayout tileGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, dp(12));
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        return grid;
    }

    private LinearLayout updateTile(LinearLayout grid, String symbol, String caption,
            boolean primary, Runnable callback) {
        LinearLayout row;
        if (grid.getChildCount() == 0
                || ((LinearLayout) grid.getChildAt(grid.getChildCount() - 1))
                    .getChildCount() == 3) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(8));
            grid.addView(row, rowParams);
        } else {
            row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        }

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        // Page padding: 18dp left + 18dp right. Two gaps: 8dp each.
        int widthSide = (width - dp(52)) / 3;
        int heightSide = (height - dp(340)) / 3;
        int side = Math.max(dp(76), Math.min(widthSide, heightSide));
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(7), dp(4), dp(7));
        tile.setBackground(rounded(primary ? accent : surface));
        tile.setOnClickListener(v -> callback.run());
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(caption.replace("\n", " "));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(side, side);
        if (row.getChildCount() > 0) params.setMargins(dp(8), 0, 0, 0);
        row.addView(tile, params);

        TextView pictogram = text(symbol, 27, true);
        pictogram.setTextColor(primary
            ? ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                ? Color.WHITE : bg) : accent);
        pictogram.setGravity(Gravity.CENTER);
        tile.addView(pictogram, new LinearLayout.LayoutParams(-1, dp(39)));
        TextView captionView = text(caption, 12, true);
        captionView.setTextColor(primary
            ? ("Trener 2".equals(prefs.getString("theme", "Grafitowy"))
                ? Color.WHITE : bg) : ink);
        captionView.setGravity(Gravity.CENTER);
        captionView.setMaxLines(3);
        tile.addView(captionView);
        return tile;
    }

    private void updatesAdvanced() {
        title("Aktualizacje • opcje zaawansowane");
        button("← Aktualizacje", () -> go("updates"));
        note("Zainstalowany versionCode: " + BuildConfig.VERSION_CODE);
        if (!BetaUpdater.isBeta()) {
            note("Stable używa wyłącznie Google Play.");
            button("Sprawdź w Google Play", () -> updater.openPlay());
            return;
        }
        note("Kanał Beta sprawdza manifest co 30 sekund, kiedy aplikacja jest aktywna.");
        if (updater.feedFromBuild()) {
            note("Adres zatwierdzonego kanału Beta jest zapisany w aplikacji. "
                + "Poprzednie lokalne wyłączenie aktualizacji nie blokuje go.");
            note("Źródło: " + updater.configuredFeed());
            note("Adres nie jest dowodem działania serwera. Sprawdź połączenie poniżej.");
            button("Sprawdź aktualizację i połączenie", () -> updater.check(true));
            button("← Aktualizacje", () -> go("updates"));
            return;
        }
        note(updater.configuredFeed().isEmpty() ? "Kanał nie jest jeszcze skonfigurowany."
            : "Kanał ustawiono ręcznie dla tej instalacji.");
        note("Źródło musi udostępniać HTTPS JSON i podpisane APK bez logowania. "
            + "Nie wpisuj tu tokenu GitHub, hasła ani adresu strony kompilacji Actions.");
        EditText source = field("Adres HTTPS manifestu JSON", false);
        source.setInputType(17);
        source.setText(updater.configuredFeed());
        button("Zapisz źródło aktualizacji", () -> {
            if (!updater.setFeed(source.getText().toString())) {
                alert("Podaj HTTPS adres manifestu JSON bez tokenu ani strony GitHub Actions.");
                return;
            }
            go("updates");
        });
        if (!BuildConfig.EDHOME_BETA_FEED_URL.isEmpty())
            button("Przywróć domyślny kanał Beta", () -> {
                updater.resetFeedToBuildDefault();
                go("updates");
            });
        button("Wyłącz zdalne sprawdzanie", () -> {
            updater.setFeed("");
            DiagnosticLog.event("UPDATES_FEED_DISABLED");
            go("updates");
        });
        button("Sprawdź teraz", () -> updater.check(true));
        note("Manifest: channel=beta, versionCode, versionName, changelog, apkUrl, sha256. "
            + "Plik musi mieć zgodny podpis, pakiet i wyższy numer wersji.");
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
                            unlocked = BetaUpdater.isBeta();
                            render();
                            alert(BetaUpdater.isBeta()
                                ? "Dane przywrócone. EDHOME Beta jest gotowa — bez PIN-u."
                                : "Dane przywrócone. Odblokuj aplikację swoim obecnym PIN-em.");
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
            super(context, "edhome-beta-preview.db", null, 5);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, "
                + "due_date TEXT, repeat_rule TEXT NOT NULL DEFAULT 'once', "
                + "repeat_every INTEGER NOT NULL DEFAULT 1, place_id INTEGER, "
                + "priority TEXT NOT NULL DEFAULT 'normal', "
                + "duration_minutes INTEGER NOT NULL DEFAULT 30)");
            database.execSQL("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0)");
            addAuditTables(database);
            addTaskHistory(database);
            addPlaces(database);
            DiagnosticLog.event("DATABASE_CREATED");
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion < 1 || newVersion > 5) {
                DiagnosticLog.event("DATABASE_MIGRATION_REQUIRED");
                throw new IllegalStateException("Unsupported EDHOME database migration");
            }
            if (oldVersion < 2) {
                addAuditTables(database);
                DiagnosticLog.event("DATABASE_MIGRATED_1_TO_2");
            }
            if (oldVersion < 3) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN due_date TEXT");
                database.execSQL("ALTER TABLE tasks ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT 'once'");
                database.execSQL("ALTER TABLE tasks ADD COLUMN repeat_every INTEGER NOT NULL DEFAULT 1");
                addTaskHistory(database);
                DiagnosticLog.event("DATABASE_MIGRATED_2_TO_3");
            }
            if (oldVersion < 4) {
                addPlaces(database);
                database.execSQL("ALTER TABLE tasks ADD COLUMN place_id INTEGER");
                DiagnosticLog.event("DATABASE_MIGRATED_3_TO_4");
            }
            if (oldVersion < 5) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'");
                database.execSQL("ALTER TABLE tasks ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 30");
                DiagnosticLog.event("DATABASE_MIGRATED_4_TO_5");
            }
        }

        private static void addPlaces(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL COLLATE NOCASE UNIQUE, kind TEXT NOT NULL DEFAULT 'Inne')");
        }

        private static void addTaskHistory(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE task_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, "
                + "title_snapshot TEXT NOT NULL, completed_at INTEGER NOT NULL, "
                + "due_date TEXT, next_due_date TEXT)");
            database.execSQL("CREATE INDEX task_history_task_idx ON task_history(task_id,id)");
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

        int overdueTasks() {
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM tasks WHERE done=0 AND due_date IS NOT NULL "
                    + "AND due_date<?", new String[]{LocalDate.now().toString()})) {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            }
        }

        void saveTask(Long id, String title, String dueDate, String rule, int every,
                Long placeId, String priority, int durationMinutes) {
            String error = TaskRules.validate(title, dueDate, rule, every);
            if (error != null) throw new IllegalArgumentException(error);
            if (!java.util.Arrays.asList(TASK_PRIORITIES).contains(priority))
                throw new IllegalArgumentException("Nieznany priorytet czynności.");
            if (durationMinutes < MIN_TASK_MINUTES || durationMinutes > MAX_TASK_MINUTES)
                throw new IllegalArgumentException("Czas musi wynosić 1–480 minut.");
            ContentValues values = new ContentValues();
            values.put("priority", priority);
            values.put("duration_minutes", durationMinutes);
            values.put("title", title);
            if (dueDate.isEmpty()) values.putNull("due_date");
            else values.put("due_date", dueDate);
            values.put("repeat_rule", rule);
            values.put("repeat_every", every);
            if (placeId == null) values.putNull("place_id");
            else {
                try (Cursor c = getReadableDatabase().rawQuery(
                        "SELECT id FROM places WHERE id=?",
                        new String[]{Long.toString(placeId)})) {
                    if (!c.moveToFirst()) throw new IllegalArgumentException("Miejsce nie istnieje");
                }
                values.put("place_id", placeId);
            }
            if (id == null) getWritableDatabase().insertOrThrow("tasks", null, values);
            else {
                // Editing a completed one-off into a recurring task reopens it.
                if (TaskRules.recurring(rule)) values.put("done", 0);
                getWritableDatabase().update("tasks", values, "id=?",
                    new String[]{Long.toString(id)});
            }
        }

        void completeTask(long id) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try (Cursor cursor = database.rawQuery(
                    "SELECT title,done,due_date,repeat_rule,repeat_every FROM tasks WHERE id=?",
                    new String[]{Long.toString(id)})) {
                if (!cursor.moveToFirst() || cursor.getInt(1) != 0) return;
                String title = cursor.getString(0);
                String due = cursor.isNull(2) ? null : cursor.getString(2);
                String rule = cursor.getString(3);
                int every = cursor.getInt(4);
                String next = TaskRules.recurring(rule)
                    ? TaskRules.nextDue(due, rule, every, LocalDate.now()) : null;
                ContentValues changed = new ContentValues();
                changed.put("done", next == null ? 1 : 0);
                if (next != null) changed.put("due_date", next);
                int affected = database.update("tasks", changed, "id=? AND done=0",
                    new String[]{Long.toString(id)});
                if (affected != 1) return;
                ContentValues history = new ContentValues();
                history.put("task_id", id);
                history.put("title_snapshot", title);
                history.put("completed_at", System.currentTimeMillis());
                if (due == null) history.putNull("due_date");
                else history.put("due_date", due);
                if (next == null) history.putNull("next_due_date");
                else history.put("next_due_date", next);
                database.insertOrThrow("task_history", null, history);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        void reopenTask(long id) {
            ContentValues values = new ContentValues();
            values.put("done", 0);
            getWritableDatabase().update("tasks", values,
                "id=? AND repeat_rule='once'", new String[]{Long.toString(id)});
        }

        void deleteTask(long id) {
            getWritableDatabase().delete("tasks", "id=?", new String[]{Long.toString(id)});
        }

        String[] taskPlanning(long taskId) {
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT priority,duration_minutes FROM tasks WHERE id=?",
                    new String[]{Long.toString(taskId)})) {
                if (!cursor.moveToFirst())
                    return new String[]{"normal", "30"};
                return new String[]{cursor.getString(0),
                    Integer.toString(cursor.getInt(1))};
            }
        }

        Long taskPlaceId(long taskId) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT place_id FROM tasks WHERE id=?",
                    new String[]{Long.toString(taskId)})) {
                return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : null;
            }
        }

        String placeLabel(long taskId) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT p.name FROM tasks t JOIN places p ON t.place_id=p.id "
                    + "WHERE t.id=?", new String[]{Long.toString(taskId)})) {
                return c.moveToFirst() ? c.getString(0) : "";
            }
        }

        boolean savePlace(Long id, String name, String kind) {
            if (name.isEmpty() || name.length() > 160)
                throw new IllegalArgumentException("Invalid place name");
            SQLiteDatabase database = getWritableDatabase();
            try (Cursor c = database.rawQuery(
                    "SELECT id FROM places WHERE name=? COLLATE NOCASE"
                    + (id == null ? "" : " AND id!=?"),
                    id == null ? new String[]{name}
                        : new String[]{name, Long.toString(id)})) {
                if (c.moveToFirst()) return false;
            }
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("kind", kind);
            if (id == null) database.insertOrThrow("places", null, values);
            else database.update("places", values, "id=?",
                new String[]{Long.toString(id)});
            return true;
        }

        void deletePlace(long id) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                database.execSQL("UPDATE tasks SET place_id=NULL WHERE place_id=?",
                    new Object[]{id});
                database.delete("places", "id=?", new String[]{Long.toString(id)});
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        void addStock(String name) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                long existing = 0;
                try (Cursor c = database.rawQuery(
                        "SELECT id FROM pantry WHERE name=? COLLATE NOCASE LIMIT 1",
                        new String[]{name})) {
                    if (c.moveToFirst()) existing = c.getLong(0);
                }
                if (existing > 0) {
                    database.execSQL(
                        "UPDATE pantry SET qty=qty+1 WHERE id=? AND qty<100000000",
                        new Object[]{existing});
                } else {
                    ContentValues values = new ContentValues();
                    values.put("name", name);
                    values.put("qty", 1);
                    database.insertOrThrow("pantry", null, values);
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        void changeStock(long id, int diff) {
            if (diff < 0) getWritableDatabase().execSQL(
                "UPDATE pantry SET qty=qty-1 WHERE id=? AND qty>0",
                new Object[]{id});
            else getWritableDatabase().execSQL(
                "UPDATE pantry SET qty=qty+1 WHERE id=? AND qty<100000000",
                new Object[]{id});
        }

        void setStock(long id, int qty) {
            if (qty < 0 || qty > 100000000) throw new IllegalArgumentException("Invalid quantity");
            ContentValues values = new ContentValues();
            values.put("qty", qty);
            getWritableDatabase().update("pantry", values, "id=?",
                new String[]{Long.toString(id)});
        }

        boolean renameStock(long id, String name) {
            SQLiteDatabase database = getWritableDatabase();
            try (Cursor c = database.rawQuery(
                    "SELECT id FROM pantry WHERE name=? COLLATE NOCASE AND id!=? LIMIT 1",
                    new String[]{name, Long.toString(id)})) {
                if (c.moveToFirst()) return false;
            }
            ContentValues values = new ContentValues();
            values.put("name", name);
            return database.update("pantry", values, "id=?",
                new String[]{Long.toString(id)}) == 1;
        }

        void deleteStock(long id) {
            if (openAuditId() != 0)
                throw new IllegalStateException("An audit is open");
            getWritableDatabase().delete("pantry", "id=?",
                new String[]{Long.toString(id)});
        }

    }
}
