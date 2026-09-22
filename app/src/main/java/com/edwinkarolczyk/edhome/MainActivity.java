package com.edwinkarolczyk.edhome;

import android.app.Activity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
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
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.DragEvent;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.view.HapticFeedbackConstants;
import android.graphics.drawable.RippleDrawable;
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
    private static final int EXPORT_PRIVATE_BACKUP = 1214;
    private static final int IMPORT_PRIVATE_BACKUP = 1215;
    private SharedPreferences prefs;
    private LocalDb db;
    private BetaUpdater updater;
    private PrivatePaycheckVault.Session privatePaycheckSession;
    private String privateBackupForSave;
    private AlertDialog privateAuthDialog;
    private AlertDialog privateEntryDialog;
    private boolean privateNeedsRender;
    private LinearLayout root;
    private LinearLayout body;
    private boolean unlocked;
    private boolean stableUpdateChecked;
    private String screen = "home";
    private String calendarMonth = YearMonth.now().toString();
    private String calendarDay = LocalDate.now().toString();
    private String calendarView = "month";
    private String tasksFilter = "all";
    private long selectedMemberId;
    private String pantrySearch = "";
    private String pantryCategoryFilter = "";
    private static final String SCAN_MODE_PREF = "pantry_scan_mode";
    private final PantryBatchSession pantryBatch = new PantryBatchSession();
    private boolean pantrySingleCameraPending;
    private boolean storageQrCameraPending;
    private static final String[] HOME_TILE_IDS = {
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today"
    };
    private static final String[] SHIFT_VALUES = {
        "unset", "off", "morning", "afternoon", "night"
    };
    private static final String[] SHIFT_LABELS = {
        "Nie ustawiono", "Wolne", "06:00–14:00", "14:00–22:00",
        "22:00–06:00 (nocna)"
    };
    private static final String[] WEEKDAY_LABELS = {
        "Poniedziałek", "Wtorek", "Środa", "Czwartek",
        "Piątek", "Sobota", "Niedziela"
    };
    private static final String[] TASK_PRIORITIES = {"low", "normal", "high", "urgent"};
    private static final String[] TASK_PRIORITY_LABELS = {
        "Niski", "Normalny", "Wysoki", "Pilny"
    };
    private static final int MIN_TASK_MINUTES = 1;
    private static final int MAX_TASK_MINUTES = 480;
    private int bg, surface, ink, subdued, accent;
    private UiSkin skin;
    private boolean homeEditMode;
    private ScrollView pageScroll;
    private final java.util.Map<String, LinearLayout> homeTileViews =
        new java.util.LinkedHashMap<>();
    private final java.util.Map<String, int[]> homeTileSlots =
        new java.util.LinkedHashMap<>();
    private java.util.List<String> homeDragOrder;
    private LinearLayout homeTileGrid;
    private String homeDragSource;
    private int homeDragTargetIndex = -1;
    private boolean homeDragDropped;
    private boolean homeDragFinishQueued;
    private TextView homeDragHint;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        DiagnosticLog.init(this);
        prefs = getSharedPreferences("edhome_beta_prefs", MODE_PRIVATE);
        // Beta DEV is deliberately PIN-free; never clear an old PIN or user data.
        unlocked = BetaUpdater.isBeta();
        db = new LocalDb(this);
        // Upgrade schema before reading reminder columns for rearming alarms.
        db.getWritableDatabase();
        ReminderReceiver.schedule(this);
        DeviceTimerReceiver.scheduleAll(this);
        if (getIntent() != null && getIntent().getBooleanExtra("open_timers", false))
            screen = "timers";
        updater = new BetaUpdater(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        DiagnosticLog.event("ACTIVITY_CREATED");
        render();
    }

    @Override public void onPause() {
        // Hide every private view BEFORE dropping FLAG_SECURE or taking a Recents snapshot.
        if (privateAuthDialog != null) {
            AlertDialog dialog = privateAuthDialog;
            privateAuthDialog = null;
            dialog.dismiss();
        }
        if (privateEntryDialog != null) {
            AlertDialog dialog = privateEntryDialog;
            privateEntryDialog = null;
            dialog.dismiss();
        }
        if (privatePaycheckSession != null) {
            privatePaycheckSession.lock();
            privatePaycheckSession = null;
        }
        if ("paycheck_private".equals(screen)) {
            if (root != null) root.removeAllViews();
            screen = "paycheck";
            privateNeedsRender = true;
        }
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
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
        if (privateNeedsRender && root != null) {
            privateNeedsRender = false;
            render();
        }
        if (unlocked && updater != null) updater.start();
        if (unlocked && root != null && "timers".equals(screen)) render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_timers", false))
            go("timers");
    }

    @Override public void onBackPressed() {
        if (unlocked && "paycheck_private".equals(screen)) go("paycheck");
        else if (unlocked && "updates_advanced".equals(screen)) go("updates");
        else if (unlocked && "places".equals(screen)) go("home");
        else if (unlocked && "storage".equals(screen)) go("places");
        else if (unlocked && "shopping".equals(screen)) go("pantry");
        else if (unlocked && "waste".equals(screen)) go("tasks");
        else if (unlocked && "member_schedule".equals(screen)) go("members");
        else if (unlocked && ("task_history".equals(screen)
                || "members".equals(screen))) go("tasks");
        else if (unlocked && !"home".equals(screen)) go("home");
        else super.onBackPressed();
    }

    private int dp(float d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void palette() {
        skin = UiSkin.forName(prefs.getString("theme", UiSkin.NEON));
        bg = skin.background;
        surface = skin.surface;
        ink = skin.foreground;
        subdued = skin.secondary;
        accent = skin.accent;
    }

    private GradientDrawable rounded(int color) {
        return skin.panel(this, color, 24);
    }

    private void touchFeedback(View view) {
        view.setForeground(new RippleDrawable(
            ColorStateList.valueOf(skin.light ? 0x22000000 : 0x44FFFFFF),
            null, skin.panel(this, Color.WHITE, 26)));
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ink);
        view.setPadding(0, dp(5), 0, dp(6));
        if (bold) view.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
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
        box.setPadding(dp(18), dp(17), dp(18), dp(17));
        box.setBackground(skin.panel(this, surface, 28));
        box.setElevation(dp(3));
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
        b.setTextColor(skin.accentInk);
        b.setBackground(skin.pill(this, accent));
        b.setElevation(dp(2));
        touchFeedback(b);
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
        if (!"paycheck_private".equals(destination)
                && privatePaycheckSession != null) {
            privatePaycheckSession.lock();
            privatePaycheckSession = null;
        }
        screen = destination;
        if (!"home".equals(destination)) homeEditMode = false;
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
        if ("paycheck_private".equals(screen) && privatePaycheckSession != null
                && privatePaycheckSession.active())
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else if (privateAuthDialog == null)
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        root.removeAllViews();
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int systemBarFlags = skin.light
            ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        getWindow().getDecorView().setSystemUiVisibility(systemBarFlags);

        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(48));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        if (!BetaUpdater.isBeta() && !prefs.contains("pin_hash")) setupPin();
        else if (!BetaUpdater.isBeta() && !unlocked) unlockPin();
        else {
            switch (screen) {
                case "tasks": tasks(); break;
                case "timers": timers(); break;
                case "waste": waste(); break;
                case "members": members(); break;
                case "member_schedule": memberSchedule(); break;
                case "task_history": taskHistoryScreen(); break;
                case "pantry": pantry(); break;
                case "shopping": shopping(); break;
                case "places": places(); break;
                case "storage": storage(); break;
                case "paycheck": paycheck(); break;
                case "paycheck_private": privatePaycheck(); break;
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
        LinearLayout planHeader = new LinearLayout(this);
        planHeader.setOrientation(LinearLayout.HORIZONTAL);
        planHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView planTitle = text("Twój domowy plan", 21, true);
        planHeader.addView(planTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        HomeIllustration homeMark = new HomeIllustration(this, skin);
        planHeader.addView(homeMark, new LinearLayout.LayoutParams(dp(68), dp(52)));
        summary.addView(planHeader);
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
        touchFeedback(summary);
        summary.setContentDescription("Twój domowy plan. Do zrobienia: "
            + db.openTasks() + ". Zaległe: " + overdue
            + ". Dotknij, aby przejść do czynności.");

        TextView editHint = text(homeEditMode
            ? "✥  TRYB UKŁADU • przytrzymaj kafelek lub przesuń za uchwyt ⋮⋮"
            : "✥  Przytrzymaj kafelek: Edytuj / Przesuń • dotknij tutaj, aby układać", 13, false);
        editHint.setTextColor(homeEditMode ? accent : ink);
        editHint.setMinHeight(dp(48));
        editHint.setGravity(Gravity.CENTER_VERTICAL);
        editHint.setPadding(dp(12), dp(8), dp(12), dp(8));
        editHint.setBackground(skin.panel(this, skin.tileTop, 22));
        editHint.setClickable(true);
        editHint.setFocusable(true);
        editHint.setContentDescription(homeEditMode
            ? "Zakończ układanie kafelków"
            : "Uruchom układanie kafelków. Przytrzymaj kafelek, aby edytować.");
        editHint.setOnClickListener(v -> {
            if (homeDragSource != null) return;
            homeEditMode = !homeEditMode;
            render();
        });
        touchFeedback(editHint);
        body.addView(editHint);
        homeDragHint = editHint;
        homeTileViews.clear();
        homeTileSlots.clear();
        homeDragSource = null;
        homeDragOrder = null;
        homeDragTargetIndex = -1;
        homeDragDropped = false;
        homeDragFinishQueued = false;
        if (homeEditMode) button("✓  Zakończ układanie", () -> {
            homeEditMode = false;
            render();
        });
        LinearLayout tiles = tileGrid();
        homeTileGrid = tiles;
        tiles.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
                return isHomeTileDrag(event);
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                previewHomeDragAt(tiles, event.getX(), event.getY());
                return true;
            }
            if (event.getAction() == DragEvent.ACTION_DROP) {
                acceptHomeDrop(tiles, event.getX(), event.getY(), event);
                return true;
            }
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                scheduleHomeDragFinish();
                return true;
            }
            return true;
        });
        for (String tileId : homeTileOrder()) {
            LinearLayout tile = homeTile(tiles, tileId);
            tile.setTag(tileId);
            homeTileViews.put(tileId, tile);
            TextView caption = (TextView) tile.getChildAt(tile.getChildCount() - 1);
            caption.setText(homeTileLabel(tileId));
            tile.setContentDescription(homeTileLabel(tileId)
                + ". Dotknij, aby otworzyć. Przytrzymaj, aby edytować "
                + "lub przesuń za uchwyt.");
            tile.setOnLongClickListener(v -> {
                if (homeEditMode) return beginHomeDrag(tile, tileId);
                showTileActions(tile, tileId);
                return true;
            });
            tile.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
                    return isHomeTileDrag(event);
                if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION
                        || event.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                    float[] position = homeDragPosition(tile,
                        event.getX(), event.getY());
                    previewHomeDragAt(tiles, position[0], position[1]);
                    scrollHomeDuringDrag(tile, event.getY());
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    float[] position = homeDragPosition(tile,
                        event.getX(), event.getY());
                    acceptHomeDrop(tiles, position[0], position[1], event);
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                    scheduleHomeDragFinish();
                    return true;
                }
                return true;
            });
        }

        button("＋ Dodaj kafelek", this::showAddTileDialog);
        if (!hiddenHomeTiles().isEmpty())
            button("◉ Przywróć ukryte kafelki", this::restoreHiddenHomeTile);

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
        // All module navigation belongs in user-configurable tiles above.
        // Keep this card informative; no second fixed menu below the tile grid.
        note("Działa offline. Przypomnienia włączysz w Ustawieniach; skaner "
            + "i synchronizacja są w kolejnych etapach.");
    }

    private String homeTileTarget(String id) {
        String target = prefs.getString("tile_target_" + id,
            HomeTileCatalog.defaultTarget(id));
        return HomeTileCatalog.validTarget(target, BetaUpdater.isBeta())
            ? target : "tasks";
    }

    private String defaultHomeTileLabel(String id) {
        return HomeTileCatalog.label(homeTileTarget(id));
    }

    private String homeTileLabel(String id) {
        String label = prefs.getString("tile_label_" + id, defaultHomeTileLabel(id));
        return label.trim().isEmpty() ? defaultHomeTileLabel(id) : label;
    }

    private void openHomeTile(String id) {
        String target = homeTileTarget(id);
        if ("diagnostics".equals(target) && !DiagnosticLog.enabled()) {
            alert("Diagnostyka jest dostępna wyłącznie w Beta.");
            return;
        }
        if ("paycheck_private".equals(target)) {
            openPrivatePaycheck();
            return;
        }
        if ("tasks".equals(target) || "today".equals(target)) {
            tasksFilter = "today".equals(target) ? "today" : "all";
            go("tasks");
            return;
        }
        go(target);
    }

    private void showAddTileDialog() {
        java.util.List<String> targets = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String target : HomeTileCatalog.TARGETS) {
            if (!HomeTileCatalog.validTarget(target, BetaUpdater.isBeta()))
                continue;
            targets.add(target);
            names.add(HomeTileCatalog.label(target));
        }
        new AlertDialog.Builder(this).setTitle("Dodaj kafelek — wybierz cel")
            .setItems(names.toArray(new String[0]), (dialog, which) -> {
                String id = "tile_" + java.util.UUID.randomUUID().toString()
                    .replace("-", "");
                java.util.List<String> order = homeTileOrder();
                order.add(id);
                boolean ok = prefs.edit()
                    .putString("tile_target_" + id, targets.get(which))
                    .putString(HomeTileCatalog.ORDER_KEY,
                        HomeTileCatalog.encode(order)).commit();
                if (!ok) { alert("Nie można zapisać kafelka."); return; }
                DiagnosticLog.event("HOME_TILE_ADDED");
                render();
                editHomeTile(id);
            }).setNegativeButton("Anuluj", null).show();
    }

    private void restoreHiddenHomeTile() {
        java.util.List<String> hidden = hiddenHomeTiles();
        String[] names = new String[hidden.size()];
        for (int i = 0; i < hidden.size(); i++)
            names[i] = homeTileLabel(hidden.get(i));
        new AlertDialog.Builder(this).setTitle("Przywróć kafelek")
            .setItems(names, (dialog, which) -> {
                hidden.remove(which);
                if (!prefs.edit().putString("home_tiles_v2_hidden",
                        HomeTileCatalog.encode(hidden)).commit()) {
                    alert("Nie udało się przywrócić kafelka.");
                    return;
                }
                DiagnosticLog.event("HOME_TILE_RESTORED");
                render();
            }).setNegativeButton("Anuluj", null).show();
    }

    private void hideHomeTile(String id) {
        java.util.List<String> order = allHomeTiles();
        if (!order.contains(id)) return;
        java.util.List<String> hidden = hiddenHomeTiles();
        if (!hidden.contains(id)) hidden.add(id);
        boolean saved = prefs.edit()
            .putString(HomeTileCatalog.ORDER_KEY, HomeTileCatalog.encode(order))
            .putString("home_tiles_v2_hidden",
                HomeTileCatalog.encode(hidden)).commit();
        if (!saved) { alert("Nie można ukryć kafelka."); return; }
        DiagnosticLog.event("HOME_TILE_HIDDEN");
        render();
    }

    private void removeHomeTile(String id) {
        java.util.List<String> order = allHomeTiles();
        if (!order.remove(id)) return;
        java.util.List<String> hidden = hiddenHomeTiles();
        hidden.remove(id);
        boolean saved = prefs.edit()
            .putString(HomeTileCatalog.ORDER_KEY, HomeTileCatalog.encode(order))
            .putString("home_tiles_v2_hidden",
                HomeTileCatalog.encode(hidden))
            .remove("tile_target_" + id).remove("tile_label_" + id)
            .remove("tile_tint_" + id).remove("tile_icon_" + id)
            .remove("tile_width_" + id).commit();
        if (!saved) { alert("Nie można usunąć skrótu."); return; }
        DiagnosticLog.event("HOME_TILE_REMOVED");
        render();
    }

    private boolean isHomeTileDrag(DragEvent event) {
        return event.getClipDescription() != null
            && "edhome-home-tile".equals(event.getClipDescription().getLabel())
            && event.getClipDescription().hasMimeType(
                android.content.ClipDescription.MIMETYPE_TEXT_PLAIN);
    }

    private boolean beginHomeDrag(View tile, String id) {
        if (!homeTileOrder().contains(id)
                || homeTileGrid == null || homeDragSource != null) return false;
        homeDragOrder = homeTileOrder();
        homeTileSlots.clear();
        for (String item : homeDragOrder) {
            View child = homeTileViews.get(item);
            if (child == null || !(child.getParent() instanceof View))
                return false;
            View row = (View) child.getParent();
            homeTileSlots.put(item, new int[]{
                child.getLeft() + row.getLeft(),
                child.getTop() + row.getTop()
            });
        }
        homeDragSource = id;
        homeDragTargetIndex = homeDragOrder.indexOf(id);
        homeDragDropped = false;
        homeDragFinishQueued = false;
        ClipData data = ClipData.newPlainText("edhome-home-tile", id);
        boolean started = tile.startDragAndDrop(data,
            new View.DragShadowBuilder(tile), null, 0);
        if (started) {
            tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            previewHomeTilePlacement(homeDragTargetIndex);
            DiagnosticLog.event("HOME_TILE_DRAG_STARTED");
        } else {
            resetHomeDragPreview();
        }
        return started;
    }

    /** Transform a translated tile's local drag coordinates into stable grid coordinates. */
    private float[] homeDragPosition(View tile, float x, float y) {
        int[] position = new int[2];
        int[] grid = new int[2];
        tile.getLocationOnScreen(position);
        homeTileGrid.getLocationOnScreen(grid);
        return new float[]{position[0] + x - grid[0],
            position[1] + y - grid[1]};
    }

    private void previewHomeDragAt(LinearLayout grid, float x, float y) {
        if (homeDragSource == null || homeDragOrder == null
                || homeTileSlots.size() != homeDragOrder.size()) return;
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < homeDragOrder.size(); i++) {
            String id = homeDragOrder.get(i);
            int[] slot = homeTileSlots.get(id);
            View tile = homeTileViews.get(id);
            double dx = x - (slot[0] + tile.getWidth() / 2.0);
            double dy = y - (slot[1] + tile.getHeight() / 2.0);
            double distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        previewHomeTilePlacement(nearest);
    }

    /** Preview and final save use the exact same insertion index. */
    private void previewHomeTilePlacement(int slot) {
        if (homeDragSource == null || homeDragOrder == null
                || slot < 0 || slot >= homeDragOrder.size()
                || slot == homeDragTargetIndex && homeDragDropped) return;
        if (slot == homeDragTargetIndex
                && homeTileViews.get(homeDragSource).getAlpha() < 1f) return;
        java.util.List<String> next = HomeTileOrder.moved(
            homeDragOrder, homeDragSource, slot);
        for (int i = 0; i < next.size(); i++) {
            String id = next.get(i);
            View tile = homeTileViews.get(id);
            int[] old = homeTileSlots.get(id);
            int[] target = homeTileSlots.get(homeDragOrder.get(i));
            if (tile == null || old == null || target == null) continue;
            tile.animate().cancel();
            if (id.equals(homeDragSource)) {
                tile.setAlpha(0.34f); // Visible placeholder: NEVER hide a tile.
                tile.setScaleX(1.04f);
                tile.setScaleY(1.04f);
            } else {
                tile.setAlpha(1f);
                tile.setScaleX(1f);
                tile.setScaleY(1f);
            }
            tile.animate()
                .translationX(target[0] - old[0])
                .translationY(target[1] - old[1])
                .setDuration(140)
                .start();
        }
        homeDragTargetIndex = slot;
        if (homeDragHint != null) {
            homeDragHint.setText("✥  Upuść tutaj: pozycja "
                + (slot + 1) + " z " + homeDragOrder.size()
                + " • " + homeTileLabel(homeDragSource));
            homeDragHint.setTextColor(accent);
        }
    }

    private void acceptHomeDrop(LinearLayout grid, float x, float y,
            DragEvent event) {
        if (homeDragSource == null || !isHomeTileDrag(event)
                || event.getClipData() == null
                || event.getClipData().getItemCount() != 1) return;
        CharSequence item = event.getClipData().getItemAt(0).getText();
        if (item == null || !homeDragSource.equals(item.toString())) return;
        previewHomeDragAt(grid, x, y);
        homeDragDropped = homeDragTargetIndex >= 0;
    }

    /** Android sends ACTION_DRAG_ENDED even if the finger is released outside a tile. */
    private void scheduleHomeDragFinish() {
        if (homeDragSource == null || homeDragFinishQueued
                || homeTileGrid == null) return;
        homeDragFinishQueued = true;
        homeTileGrid.post(() -> {
            String source = homeDragSource;
            int slot = homeDragTargetIndex;
            boolean save = homeDragDropped;
            resetHomeDragPreview();
            if (save && source != null && slot >= 0)
                moveHomeTileAtIndex(source, slot);
        });
    }

    private void resetHomeDragPreview() {
        for (View tile : homeTileViews.values()) {
            tile.animate().cancel();
            tile.setTranslationX(0f);
            tile.setTranslationY(0f);
            tile.setAlpha(1f);
            tile.setScaleX(1f);
            tile.setScaleY(1f);
            tile.setVisibility(View.VISIBLE);
        }
        homeDragSource = null;
        homeDragOrder = null;
        homeDragTargetIndex = -1;
        homeDragDropped = false;
        homeDragFinishQueued = false;
        homeTileSlots.clear();
        if (homeDragHint != null) {
            homeDragHint.setText(homeEditMode
                ? "✥  TRYB UKŁADU • przytrzymaj kafelek lub przesuń za uchwyt ⋮⋮"
                : "✥  Przytrzymaj kafelek: Edytuj / Przesuń • dotknij tutaj, aby układać");
            homeDragHint.setTextColor(subdued);
        }
    }

    private void scrollHomeDuringDrag(View tile, float localY) {
        if (pageScroll == null || !"home".equals(screen)) return;
        int[] tileLocation = new int[2];
        int[] scrollLocation = new int[2];
        tile.getLocationOnScreen(tileLocation);
        pageScroll.getLocationOnScreen(scrollLocation);
        float y = tileLocation[1] + localY;
        if (y < scrollLocation[1] + dp(70)) {
            pageScroll.scrollBy(0, -dp(14));
        } else if (y > scrollLocation[1] + pageScroll.getHeight() - dp(70)) {
            pageScroll.scrollBy(0, dp(14));
        }
    }

    private void showTileActions(View anchor, String id) {
        if (!"home".equals(screen)) return;
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setBackground(skin.panel(this, surface, 24));
        PopupWindow popup = new PopupWindow(menu, dp(226), -2, true);
        popup.setBackgroundDrawable(skin.panel(this, surface, 24));
        popup.setElevation(dp(12));
        popup.setOutsideTouchable(true);
        TextView heading = text(homeTileLabel(id), 16, true);
        heading.setPadding(dp(12), dp(6), dp(12), dp(8));
        menu.addView(heading);
        TextView edit = text("✎  Edytuj kafelek", 16, true);
        edit.setPadding(dp(14), dp(13), dp(14), dp(13));
        edit.setBackground(skin.panel(this, skin.tileTop, 18));
        menu.addView(edit);
        edit.setOnClickListener(v -> {
            popup.dismiss();
            editHomeTile(id);
        });
        TextView move = text("✥  Przesuń kafelek", 16, true);
        move.setPadding(dp(14), dp(13), dp(14), dp(13));
        move.setBackground(skin.panel(this, skin.tileTop, 18));
        menu.addView(move);
        move.setOnClickListener(v -> {
            popup.dismiss();
            homeEditMode = true;
            render();
            android.widget.Toast.makeText(this,
                "Przeciągnij kafelek za uchwyt ⋮⋮ lub przytrzymaj kafelek.",
                android.widget.Toast.LENGTH_LONG).show();
        });
        TextView hide = text("◉  Ukryj kafelek", 16, true);
        hide.setPadding(dp(14), dp(13), dp(14), dp(13));
        hide.setBackground(skin.panel(this, skin.tileTop, 18));
        menu.addView(hide);
        hide.setOnClickListener(v -> {
            popup.dismiss();
            hideHomeTile(id);
        });
        TextView remove = text("−  Usuń skrót z panelu", 16, true);
        remove.setPadding(dp(14), dp(13), dp(14), dp(13));
        remove.setBackground(skin.panel(this, skin.tileTop, 18));
        menu.addView(remove);
        remove.setOnClickListener(v -> {
            popup.dismiss();
            new AlertDialog.Builder(this).setTitle("Usunąć kafelek?")
                .setMessage("Usuniemy tylko skrót, a nie dane ani moduł.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Usuń skrót", (d, which) ->
                    removeHomeTile(id)).show();
        });
        popup.showAsDropDown(anchor, 0, -dp(14));
        DiagnosticLog.event("HOME_TILE_ACTIONS_OPENED");
    }

    private void editHomeTile(String id) {
        if (!homeTileOrder().contains(id)) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(8));
        form.addView(text("Edytuj kafelek", 21, true));
        form.addView(text("Możesz zmienić cel tego skrótu bez zmiany danych modułu.", 13, false));
        form.addView(text("Co otwiera kafelek", 15, true));
        java.util.List<String> targets = new java.util.ArrayList<>();
        java.util.List<String> targetLabels = new java.util.ArrayList<>();
        for (String target : HomeTileCatalog.TARGETS) {
            if (!HomeTileCatalog.validTarget(target, BetaUpdater.isBeta())) continue;
            targets.add(target);
            targetLabels.add(HomeTileCatalog.label(target));
        }
        Spinner destination = new Spinner(this);
        destination.setAdapter(themeSpinnerAdapter(targetLabels));
        destination.setSelection(Math.max(0, targets.indexOf(homeTileTarget(id))));
        form.addView(destination);
        form.addView(text("Podpis (maks. 24 znaki)", 15, true));
        EditText label = new EditText(this);
        label.setSingleLine(true);
        label.setText(homeTileLabel(id));
        label.setTextColor(ink);
        label.setHintTextColor(subdued);
        form.addView(label);
        form.addView(text("Kolor kafelka", 15, true));
        String[] tints = {"Domyślny", "Miętowy", "Niebieski",
            "Bursztynowy", "Fioletowy"};
        String[] codes = {"default", "mint", "blue", "amber", "violet"};
        Spinner color = new Spinner(this);
        color.setAdapter(themeSpinnerAdapter(java.util.Arrays.asList(tints)));
        String selected = prefs.getString("tile_tint_" + id, "default");
        color.setSelection(Math.max(0,
            java.util.Arrays.asList(codes).indexOf(selected)));
        form.addView(color);
        form.addView(text("Ikona — wybierz z lokalnej biblioteki", 15, true));
        Spinner icon = new Spinner(this);
        icon.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(TileIcon.ICON_NAMES)));
        String previousIcon = prefs.getString("tile_icon_" + id,
            HomeTileCatalog.icon(homeTileTarget(id)));
        icon.setSelection(Math.max(0,
            java.util.Arrays.asList(TileIcon.ICON_IDS).indexOf(previousIcon)));
        form.addView(icon);
        form.addView(text("Rozmiar kafelka", 15, true));
        String[] sizeLabels = {"Mały (1 pole)", "Podwójny (2 pola)"};
        String[] sizeValues = {"small", "double"};
        Spinner size = new Spinner(this);
        size.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(sizeLabels)));
        size.setSelection("double".equals(
            prefs.getString("tile_width_" + id, "small")) ? 1 : 0);
        form.addView(size);
        LinearLayout previewFrame = new LinearLayout(this);
        previewFrame.setGravity(Gravity.CENTER);
        previewFrame.setPadding(0, dp(9), 0, dp(9));
        form.addView(previewFrame);
        icon.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> p,
                    View view, int position, long rowId) {
                previewFrame.removeAllViews();
                TileIcon pictogram = new TileIcon(MainActivity.this,
                    TileIcon.ICON_IDS[position], accent);
                pictogram.setPadding(dp(8), dp(8), dp(8), dp(8));
                pictogram.setBackground(skin.panel(MainActivity.this,
                    skin.iconBacking, 24));
                previewFrame.addView(pictogram,
                    new LinearLayout.LayoutParams(dp(64), dp(64)));
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Przywróć wygląd", null)
            .setPositiveButton("Zapisz", null)
            .create();
        dialog.setOnShowListener(ignore -> {
            dialog.getWindow().setBackgroundDrawable(skin.panel(this, surface, 28));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String newLabel = label.getText().toString().trim();
                    if (newLabel.isEmpty() || newLabel.length() > 24
                            || newLabel.contains("\n")) {
                        label.setError("Wpisz od 1 do 24 znaków.");
                        return;
                    }
                    String target = targets.get(destination.getSelectedItemPosition());
                    SharedPreferences.Editor change = prefs.edit()
                        .putString("tile_target_" + id, target);
                    if (newLabel.equals(HomeTileCatalog.label(target)))
                        change.remove("tile_label_" + id);
                    else change.putString("tile_label_" + id, newLabel);
                    String code = codes[color.getSelectedItemPosition()];
                    if ("default".equals(code)) change.remove("tile_tint_" + id);
                    else change.putString("tile_tint_" + id, code);
                    String iconId = TileIcon.ICON_IDS[
                        icon.getSelectedItemPosition()];
                    if (HomeTileCatalog.icon(target).equals(iconId))
                        change.remove("tile_icon_" + id);
                    else change.putString("tile_icon_" + id, iconId);
                    String tileSize = sizeValues[size.getSelectedItemPosition()];
                    if ("small".equals(tileSize)) change.remove("tile_width_" + id);
                    else change.putString("tile_width_" + id, tileSize);
                    if (!change.commit()) {
                        alert("Nie udało się zapisać ustawień kafelka.");
                        return;
                    }
                    DiagnosticLog.event("HOME_TILE_APPEARANCE_SAVED");
                    dialog.dismiss();
                    render();
                });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    prefs.edit().remove("tile_label_" + id)
                        .remove("tile_tint_" + id)
                        .remove("tile_icon_" + id)
                        .remove("tile_width_" + id).apply();
                    DiagnosticLog.event("HOME_TILE_APPEARANCE_RESET");
                    dialog.dismiss();
                    render();
                });
        });
        dialog.show();
    }

    /** Keep hidden tile identity and appearance; only visible IDs participate in drag. */
    private java.util.List<String> allHomeTiles() {
        return HomeTileCatalog.canonical(
            prefs.getString(HomeTileCatalog.ORDER_KEY, null),
            prefs.getString("home_tile_order", ""),
            BetaUpdater.isBeta());
    }

    private java.util.List<String> hiddenHomeTiles() {
        java.util.List<String> hidden = new java.util.ArrayList<>();
        String saved = prefs.getString("home_tiles_v2_hidden", "");
        java.util.List<String> known = allHomeTiles();
        for (String id : saved.split(",", -1))
            if (known.contains(id) && !hidden.contains(id)) hidden.add(id);
        return hidden;
    }

    private java.util.List<String> homeTileOrder() {
        java.util.List<String> visible = allHomeTiles();
        visible.removeAll(hiddenHomeTiles());
        return visible;
    }

    private boolean moveHomeTileAtIndex(String source, int slot) {
        java.util.List<String> before = homeTileOrder();
        final java.util.List<String> next;
        try {
            next = HomeTileCatalog.moved(before, source, slot);
        } catch (IllegalArgumentException problem) {
            DiagnosticLog.event("HOME_TILE_DRAG_INVALID");
            render();
            return false;
        }
        if (before.equals(next)) {
            render(); // Also clears all preview transformations.
            return true;
        }
        // Retain hidden shortcuts while persisting the visible order.
        java.util.List<String> full = allHomeTiles();
        full.removeAll(next);
        java.util.List<String> nextFull = new java.util.ArrayList<>(next);
        nextFull.addAll(full);
        boolean saved = prefs.edit().putString(HomeTileCatalog.ORDER_KEY,
            HomeTileCatalog.encode(nextFull)).commit();
        if (!saved) {
            DiagnosticLog.event("HOME_TILE_DRAG_SAVE_FAILED");
            render();
            alert("Nie udało się zapisać kolejności kafelków.");
            return false;
        }
        DiagnosticLog.event("HOME_TILES_REORDERED");
        render();
        android.widget.Toast.makeText(this, "Zapisano układ kafelków",
            android.widget.Toast.LENGTH_SHORT).show();
        return true;
    }

    private LinearLayout homeTile(LinearLayout grid, String id) {
        String target = homeTileTarget(id);
        return updateTile(grid, id, "•",
            HomeTileCatalog.label(target), "tasks".equals(target),
            () -> openHomeTile(id));
    }

    private void members() {
        header("Domownicy • wykonawcy czynności");
        note("Lokalna lista wykonawców. Możesz pozostawić czynność bez osoby. "
            + "Grafik i wyjątki ustawiasz osobno dla każdej osoby.");
        EditText person = field("Imię lub nazwa osoby", false);
        person.setSingleLine(true);
        button("+ Dodaj domownika", () -> {
            try {
                if (!db.addMember(person.getText().toString())) {
                    person.setError("Ta osoba jest już na liście.");
                    return;
                }
                DiagnosticLog.event("MEMBER_ADDED");
                render();
            } catch (IllegalArgumentException error) {
                person.setError("Podaj nazwę osoby (1–80 znaków).");
            }
        });
        int count = 0;
        try (Cursor people = db.getReadableDatabase().rawQuery(
                "SELECT id,name FROM household_members ORDER BY name COLLATE NOCASE",
                null)) {
            while (people.moveToNext()) {
                count++;
                long memberId = people.getLong(0);
                String memberName = people.getString(1);
                LinearLayout memberCard = card();
                memberCard.addView(text(memberName, 18, true));
                smallButton(memberCard, "Grafik i wyjątki →", () -> {
                    selectedMemberId = memberId;
                    go("member_schedule");
                });
                smallButton(memberCard, "Usuń domownika", () ->
                    new AlertDialog.Builder(this)
                        .setTitle("Usunąć domownika?")
                        .setMessage("Czynności przypisane do " + memberName
                            + " pozostaną, ale bez wykonawcy.")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Usuń", (dialog, which) -> {
                            db.deleteMember(memberId);
                            DiagnosticLog.event("MEMBER_REMOVED");
                            render();
                        }).show());
            }
        }
        if (count == 0) note("Nie ma jeszcze domowników. Dodaj osobę, aby móc "
            + "wybierać wykonawcę w formularzu czynności.");
        button("← Czynności", () -> go("tasks"));
    }

    private void memberSchedule() {
        String name = db.memberName(selectedMemberId);
        if (name.isEmpty()) {
            go("members");
            return;
        }
        header("Grafik pracy • " + name);
        note("Lokalny grafik pon.–niedz. Puste dni są nieustalone, "
            + "a nie automatycznie wolne. Zmiana nocna kończy się następnego dnia.");
        button("← Domownicy", () -> go("members"));
        for (int day = 1; day <= 7; day++) {
            final int weekday = day;
            LinearLayout shiftCard = card();
            shiftCard.addView(text(WEEKDAY_LABELS[day - 1], 17, true));
            Spinner chosenShift = new Spinner(this);
            chosenShift.setAdapter(themeSpinnerAdapter(
                java.util.Arrays.asList(SHIFT_LABELS)));
            String configured = db.weeklyShift(selectedMemberId, weekday);
            chosenShift.setSelection(Math.max(0, java.util.Arrays.asList(
                SHIFT_VALUES).indexOf(configured)));
            shiftCard.addView(chosenShift);
            smallButton(shiftCard, "Zapisz dzień", () -> {
                db.setWeeklyShift(selectedMemberId, weekday,
                    SHIFT_VALUES[chosenShift.getSelectedItemPosition()]);
                DiagnosticLog.event("MEMBER_WEEKLY_SHIFT_SAVED");
                render();
            });
        }

        LinearLayout exception = card();
        exception.addView(text("Wyjątek na konkretny dzień", 18, true));
        exception.addView(text("Nadpisuje tygodniowy grafik tylko dla tej daty. "
            + "„Nie ustawiono” usuwa wyjątek i przywraca grafik tygodniowy.",
            13, false));
        EditText selectedDate = new EditText(this);
        selectedDate.setSingleLine(true);
        selectedDate.setFocusable(false);
        selectedDate.setText(LocalDate.now().toString());
        selectedDate.setTextColor(ink);
        selectedDate.setOnClickListener(v -> {
            LocalDate initial = LocalDate.parse(selectedDate.getText().toString());
            new DatePickerDialog(this, (view, year, month, day) ->
                selectedDate.setText(LocalDate.of(
                    year, month + 1, day).toString()),
                initial.getYear(), initial.getMonthValue() - 1,
                initial.getDayOfMonth()).show();
        });
        exception.addView(selectedDate);
        smallButton(exception, "Wybierz datę", () -> selectedDate.performClick());
        Spinner exceptionShift = new Spinner(this);
        exceptionShift.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(SHIFT_LABELS)));
        exception.addView(exceptionShift);
        smallButton(exception, "Zapisz wyjątek / usuń wyjątek", () -> {
            String date = selectedDate.getText().toString();
            db.setShiftException(selectedMemberId, date,
                SHIFT_VALUES[exceptionShift.getSelectedItemPosition()]);
            DiagnosticLog.event("MEMBER_SHIFT_EXCEPTION_SAVED");
            render();
        });
        LinearLayout saved = card();
        saved.addView(text("Zapisane wyjątki", 18, true));
        int exceptions = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT date,shift FROM member_shift_exceptions "
                + "WHERE member_id=? ORDER BY date ASC",
                new String[]{Long.toString(selectedMemberId)})) {
            while (c.moveToNext()) {
                exceptions++;
                String date = c.getString(0);
                String shift = c.getString(1);
                saved.addView(text(date + " • " + SHIFT_LABELS[
                    Math.max(0, java.util.Arrays.asList(SHIFT_VALUES)
                        .indexOf(shift))], 15, false));
                smallButton(saved, "Usuń wyjątek " + date, () -> {
                    db.setShiftException(selectedMemberId, date, "unset");
                    DiagnosticLog.event("MEMBER_SHIFT_EXCEPTION_REMOVED");
                    render();
                });
            }
        }
        if (exceptions == 0)
            saved.addView(text("Brak wyjątków. Każda data korzysta z grafiku "
                + "tygodniowego lub jest nieustalona.", 13, false));
    }

    private void waste() {
        header("Odpady • wystawianie i terminy");
        note("Ustaw dzień WYSTAWIENIA pojemnika, nie datę odbioru śmieci. "
            + "Czynności pojawiają się także w kalendarzu i głównej liście. "
            + "Wykonanie odnotowuje historię, a reguła cykliczna "
            + "wyznacza kolejny termin.");
        note("Powiadomienia są opcjonalne: włącz je w Ustawieniach. "
            + "Android sprawdza zaległe i dzisiejsze zadania około 9:00 "
            + "(system może opóźnić powiadomienie). "
            + "Harmonogram gminy nie jest pobierany automatycznie.");
        LinearLayout form = card();
        form.addView(text("Nowy termin wystawienia", 19, true));
        form.addView(text("Frakcja", 15, true));
        Spinner fraction = new Spinner(this);
        fraction.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(WasteRules.LABELS)));
        form.addView(fraction);
        form.addView(text("Pierwszy dzień wystawienia", 15, true));
        EditText date = new EditText(this);
        date.setSingleLine(true);
        date.setFocusable(false);
        date.setText(LocalDate.now().toString());
        date.setTextColor(ink);
        date.setOnClickListener(v -> {
            LocalDate initial = LocalDate.parse(date.getText().toString());
            new DatePickerDialog(this, (picker, year, month, day) ->
                date.setText(LocalDate.of(year, month + 1, day).toString()),
                initial.getYear(), initial.getMonthValue() - 1,
                initial.getDayOfMonth()).show();
        });
        form.addView(date);
        smallButton(form, "Wybierz datę", () -> date.performClick());
        form.addView(text("Powtarzanie", 15, true));
        Spinner cycle = new Spinner(this);
        cycle.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(WasteRules.CYCLES)));
        form.addView(cycle);
        smallButton(form, "+ Dodaj do Czynności i kalendarza", () -> {
            String rule;
            int every = 1;
            switch (cycle.getSelectedItemPosition()) {
                case 1: rule = "weekly"; break;
                case 2: rule = "every_weeks"; every = 2; break;
                case 3: rule = "monthly"; break;
                default: rule = "once"; break;
            }
            String selectedFraction = WasteRules.FRACTIONS[
                fraction.getSelectedItemPosition()];
            String due = date.getText().toString();
            String error = WasteRules.validate(
                selectedFraction, due, rule, every);
            if (error != null) {
                date.setError(error);
                alert(error);
                return;
            }
            if (!db.createWasteTask(selectedFraction, due, rule, every)) {
                alert("Ta frakcja ma już niezakończoną czynność "
                    + "z tą datą. Edytuj istniejącą zamiast tworzyć duplikat.");
                return;
            }
            ReminderReceiver.schedule(this);
            DiagnosticLog.event("WASTE_TASK_ADDED");
            render();
        });
        button("▦ Zobacz w kalendarzu", () -> go("calendar"));
        button("✓ Wszystkie czynności", () -> {
            tasksFilter = "all";
            go("tasks");
        });
        title("Terminy i potwierdzenia");
        int count = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,title,done,due_date,repeat_rule,repeat_every "
                + "FROM tasks WHERE task_kind='waste' "
                + "ORDER BY done ASC,due_date ASC,id DESC", null)) {
            while (c.moveToNext()) {
                count++;
                drawTask(c.getLong(0), c.getString(1),
                    c.getInt(2) == 1, c.isNull(3) ? "" : c.getString(3),
                    c.getString(4), c.getInt(5));
            }
        }
        if (count == 0) note("Brak terminów. Dodaj pierwszą frakcję i dzień "
            + "wystawienia z lokalnego harmonogramu odbioru.");
    }

    /** Local appliance timing is separate from recurring chores and their history. */
    private void timers() {
        header("Minutniki • urządzenia domowe");
        note("Pralka, suszarka i zmywarka. Czas jest liczony od rozpoczęcia "
            + "i zapisany lokalnie. Android może opóźnić powiadomienie "
            + "(oszczędzanie baterii); odczyt w aplikacji pokazuje rzeczywisty termin.");
        boolean enabled = prefs.getBoolean("timer_notifications_enabled", false);
        button(enabled ? "🔔 Powiadomienia minutników: WŁĄCZONE"
                : "○ Powiadomienia minutników: WYŁĄCZONE", () -> {
            boolean newValue = !prefs.getBoolean(
                "timer_notifications_enabled", false);
            prefs.edit().putBoolean("timer_notifications_enabled",
                newValue).apply();
            if (newValue && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS}, 7132);
            }
            DeviceTimerReceiver.scheduleAll(this);
            render();
        });
        note("Powiadomienia są opcjonalne i niezależne od przypomnień czynności. "
            + "Cisza: " + quietHoursStart() + "–" + quietHoursEnd()
            + ". Minutnik działa i kończy się również bez zgody na powiadomienia.");
        LinearLayout editor = card();
        editor.addView(text("Uruchom minutnik", 20, true));
        editor.addView(text("Urządzenie", 15, true));
        Spinner device = new Spinner(this);
        device.setAdapter(themeSpinnerAdapter(java.util.Arrays.asList(
            DeviceTimerRules.LABELS)));
        editor.addView(device);
        editor.addView(text("Nazwa / program (opcjonalnie)", 15, true));
        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setTextColor(ink);
        titleInput.setHintTextColor(subdued);
        titleInput.setHint("np. Pralka Bosch • Bawełna");
        editor.addView(titleInput);
        editor.addView(text("Czas w minutach (1–1440)", 15, true));
        EditText minutes = new EditText(this);
        minutes.setSingleLine(true);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setText("60");
        minutes.setTextColor(ink);
        editor.addView(minutes);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        editor.addView(presets);
        for (int choice : new int[]{30, 45, 60, 90}) {
            taskAction(presets, choice + " min",
                () -> minutes.setText(Integer.toString(choice)));
        }
        smallButton(editor, "▶ Rozpocznij", () -> {
            int duration;
            try {
                duration = Integer.parseInt(
                    minutes.getText().toString().trim());
            } catch (NumberFormatException problem) {
                minutes.setError("Podaj czas od 1 do 1440 minut.");
                return;
            }
            if (duration < 1 || duration > 1440) {
                minutes.setError("Podaj czas od 1 do 1440 minut.");
                return;
            }
            String type = DeviceTimerRules.TYPES[
                device.getSelectedItemPosition()];
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty())
                title = DeviceTimerRules.LABELS[
                    device.getSelectedItemPosition()];
            if (!DeviceTimerRules.validTitle(title)) {
                titleInput.setError("Nazwa: maks. 80 znaków.");
                return;
            }
            try {
                long timerId = db.startDeviceTimer(type, title, duration);
                DeviceTimerReceiver.scheduleAll(this);
                DiagnosticLog.event("DEVICE_TIMER_STARTED");
                render();
                android.widget.Toast.makeText(this,
                    "Minutnik uruchomiony", android.widget.Toast.LENGTH_SHORT)
                    .show();
            } catch (Exception problem) {
                DiagnosticLog.error("DEVICE_TIMER_START", problem);
                alert("Nie udało się uruchomić minutnika.");
            }
        });
        button("↻ Odśwież pozostały czas", () -> render());
        long now = System.currentTimeMillis();
        int active = 0, finished = 0, past = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,device_type,title,start_at,end_at,status,"
                + "acknowledged_at FROM device_timers "
                + "ORDER BY CASE WHEN status='running' THEN 0 ELSE 1 END,"
                + "end_at ASC,id DESC LIMIT 200", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String type = c.getString(1), name = c.getString(2);
                long end = c.getLong(4);
                String status = c.getString(5);
                boolean running = "running".equals(status);
                if (running && end > now) active++;
                else if (running) finished++;
                else past++;
                LinearLayout row = card();
                LinearLayout heading = new LinearLayout(this);
                heading.setOrientation(LinearLayout.HORIZONTAL);
                heading.setGravity(Gravity.CENTER_VERTICAL);
                TileIcon icon = new TileIcon(this, type, accent);
                icon.setPadding(dp(7), dp(7), dp(7), dp(7));
                icon.setBackground(skin.panel(this, skin.iconBacking, 22));
                heading.addView(icon, new LinearLayout.LayoutParams(
                    dp(49), dp(49)));
                TextView label = text(name, 19, true);
                LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(0, -2, 1f);
                labelParams.setMargins(dp(12), 0, 0, 0);
                heading.addView(label, labelParams);
                row.addView(heading);
                String finishTime = java.time.Instant.ofEpochMilli(end)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter
                        .ofPattern("dd.MM.yyyy • HH:mm"));
                row.addView(text("Koniec: " + finishTime, 13, false));
                if (running && end > now) {
                    TextView timeLeft = text("◷ Pozostało około "
                        + DeviceTimerRules.minutesLeft(end, now)
                        + " min", 16, true);
                    timeLeft.setTextColor(accent);
                    row.addView(timeLeft);
                    smallButton(row, "■ Zatrzymaj bez wykonania", () ->
                        new AlertDialog.Builder(this)
                            .setTitle("Zatrzymać minutnik?")
                            .setMessage(name + "\\nZapis pozostanie w historii.")
                            .setNegativeButton("Wróć", null)
                            .setPositiveButton("Zatrzymaj", (dialog, which) -> {
                                if (db.updateDeviceTimer(id,
                                        "cancelled",
                                        System.currentTimeMillis())) {
                                    DeviceTimerReceiver.cancel(this, id);
                                    DiagnosticLog.event(
                                        "DEVICE_TIMER_CANCELLED");
                                }
                                render();
                            }).show());
                } else if (running) {
                    TextView done = text("✓ Program zakończony "
                        + "• oczekuje na potwierdzenie", 16, true);
                    done.setTextColor(accent);
                    row.addView(done);
                    smallButton(row, "✓ Potwierdź zakończenie", () -> {
                        if (db.updateDeviceTimer(id, "acknowledged",
                                System.currentTimeMillis())) {
                            DeviceTimerReceiver.cancel(this, id);
                            DiagnosticLog.event(
                                "DEVICE_TIMER_ACKNOWLEDGED");
                            render();
                        }
                    });
                } else {
                    row.addView(text("acknowledged".equals(status)
                        ? "✓ Potwierdzono" : "■ Zatrzymano", 13, false));
                }
            }
        }
        if (active + finished + past == 0)
            note("Brak minutników. Wybierz urządzenie i czas powyżej.");
        else note("W trakcie: " + active + " • Do potwierdzenia: "
            + finished + " • Historia na ekranie: " + past);
    }

    private void tasks() {
        header("Czynności • plan i wykonania");
        note("Czynności mogą działać samodzielnie lub być opcjonalnie przypięte do miejsca.");
        button("⌂ Miejsca", () -> go("places"));
        button("◷ Minutniki urządzeń", () -> go("timers"));
        button("♻ Odpady i wystawianie", () -> go("waste"));
        button("♙ Domownicy / wykonawcy", () -> go("members"));
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
                ? skin.accentInk : ink);
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
        boolean onHome = "home".equals(screen);
        b.setTextColor(onHome ? skin.buttonForeground : ink);
        b.setBackground(skin.pill(this, onHome
            ? (skin.light ? 0xFFEFF8F2 : 0xFFEAF5F5) : skin.tileTop));
        b.setElevation(dp(2));
        b.setMinHeight(dp(52));
        touchFeedback(b);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        container.addView(b, params);
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
        String assignee = db.assigneeLabel(id);
        if (!assignee.isEmpty()) description += " • Wykonawca: " + assignee;
        String rotation = db.rotationLabel(id);
        if (!rotation.isEmpty()) description += " • Rotacja: " + rotation;
        String[] alertTime = db.taskReminder(id);
        if (!alertTime[0].isEmpty()) {
            java.time.LocalDateTime when = ReminderRules.target(
                due, alertTime[0], Integer.parseInt(alertTime[1]),
                quietHoursStart(), quietHoursEnd());
            description += " • Przypomnienie: " + when.toLocalDate()
                + " " + when.toLocalTime() + " (orientacyjnie)";
        }
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
            ReminderReceiver.schedule(this);
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
                    ReminderReceiver.cancelTask(this, id);
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

    private String memberNameFromLists(Long id,
            java.util.ArrayList<Long> memberIds,
            java.util.ArrayList<String> memberNames) {
        int index = memberIds.indexOf(id);
        return index >= 0 && index < memberNames.size()
            ? memberNames.get(index) : "Nieznana osoba";
    }

    private void renderRotationEditor(LinearLayout container,
            java.util.ArrayList<Long> rotationIds,
            java.util.ArrayList<Long> memberIds,
            java.util.ArrayList<String> memberNames,
            Spinner chosenMember) {
        container.removeAllViews();
        if (rotationIds.isEmpty()) {
            TextView off = text("Rotacja wyłączona. Wykonawca pozostaje stały.", 13, false);
            off.setTextColor(subdued);
            container.addView(off);
        } else {
            TextView on = text("Kolejność rotacji • następna osoba po wykonaniu:", 13, true);
            on.setTextColor(accent);
            container.addView(on);
            for (int i = 0; i < rotationIds.size(); i++) {
                final int index = i;
                Long memberId = rotationIds.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text((i + 1) + ". "
                    + memberNameFromLists(memberId, memberIds, memberNames), 14, true);
                row.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1f));
                if (i > 0) {
                    TextView up = text("↑", 18, true);
                    up.setGravity(Gravity.CENTER);
                    row.addView(up, new LinearLayout.LayoutParams(dp(44), dp(44)));
                    up.setOnClickListener(v -> {
                        java.util.List<Long> moved = RotationRules.moved(
                            rotationIds, index, index - 1);
                        rotationIds.clear();
                        rotationIds.addAll(moved);
                        renderRotationEditor(container, rotationIds,
                            memberIds, memberNames, chosenMember);
                    });
                }
                if (i < rotationIds.size() - 1) {
                    TextView down = text("↓", 18, true);
                    down.setGravity(Gravity.CENTER);
                    row.addView(down, new LinearLayout.LayoutParams(dp(44), dp(44)));
                    down.setOnClickListener(v -> {
                        java.util.List<Long> moved = RotationRules.moved(
                            rotationIds, index, index + 1);
                        rotationIds.clear();
                        rotationIds.addAll(moved);
                        renderRotationEditor(container, rotationIds,
                            memberIds, memberNames, chosenMember);
                    });
                }
                TextView remove = text("Usuń", 12, true);
                remove.setGravity(Gravity.CENTER);
                row.addView(remove, new LinearLayout.LayoutParams(dp(62), dp(44)));
                remove.setOnClickListener(v -> {
                    rotationIds.remove(index);
                    if (!rotationIds.isEmpty()) {
                        Long current = memberIds.get(chosenMember.getSelectedItemPosition());
                        if (current == null || !rotationIds.contains(current)) {
                            int selected = memberIds.indexOf(rotationIds.get(0));
                            if (selected >= 0) chosenMember.setSelection(selected);
                        }
                    }
                    renderRotationEditor(container, rotationIds,
                        memberIds, memberNames, chosenMember);
                });
                container.addView(row);
            }
        }

        smallButton(container, "+ Dodaj osobę do rotacji", () -> {
            java.util.ArrayList<Long> availableIds = new java.util.ArrayList<>();
            java.util.ArrayList<String> availableNames = new java.util.ArrayList<>();
            for (int i = 1; i < memberIds.size(); i++) {
                if (!rotationIds.contains(memberIds.get(i))) {
                    availableIds.add(memberIds.get(i));
                    availableNames.add(memberNames.get(i));
                }
            }
            if (availableIds.isEmpty()) {
                alert(memberIds.size() <= 1
                    ? "Najpierw dodaj domowników w Czynności → Domownicy."
                    : "Wszyscy domownicy są już w rotacji.");
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle("Dodaj do rotacji")
                .setItems(availableNames.toArray(new String[0]), (dialog, which) -> {
                    Long added = availableIds.get(which);
                    rotationIds.add(added);
                    if (rotationIds.size() == 1) {
                        int selected = memberIds.indexOf(added);
                        if (selected >= 0) chosenMember.setSelection(selected);
                    }
                    renderRotationEditor(container, rotationIds,
                        memberIds, memberNames, chosenMember);
                }).show();
        });
        if (!rotationIds.isEmpty()) {
            smallButton(container, "Wyłącz rotację", () -> {
                rotationIds.clear();
                renderRotationEditor(container, rotationIds,
                    memberIds, memberNames, chosenMember);
            });
        }
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
                placeNames.add(db.placePath(places.getLong(0)));
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

        form.addView(text("Wykonawca (opcjonalnie)", 15, true));
        java.util.ArrayList<Long> memberIds = new java.util.ArrayList<>();
        java.util.ArrayList<String> memberNames = new java.util.ArrayList<>();
        memberIds.add(null);
        memberNames.add("Bez wyznaczonej osoby");
        try (Cursor members = db.getReadableDatabase().rawQuery(
                "SELECT id,name FROM household_members ORDER BY name COLLATE NOCASE", null)) {
            while (members.moveToNext()) {
                memberIds.add(members.getLong(0));
                memberNames.add(members.getString(1));
            }
        }
        Spinner chosenMember = new Spinner(this);
        chosenMember.setAdapter(themeSpinnerAdapter(memberNames));
        if (id != null) {
            Long currentMember = db.taskAssigneeId(id);
            if (currentMember != null) {
                for (int i = 1; i < memberIds.size(); i++) {
                    if (currentMember.equals(memberIds.get(i))) {
                        chosenMember.setSelection(i);
                        break;
                    }
                }
            }
        }
        form.addView(chosenMember);
        form.addView(text("Osoby dodasz w Czynności → Domownicy. "
            + "Bez wykonawcy czynność nadal działa.", 12, false));

        form.addView(text("Rotacja wykonawców (opcjonalnie)", 16, true));
        form.addView(text("Działa tylko dla czynności powtarzalnych. "
            + "Po wykonaniu kolejny termin automatycznie dostaje następną "
            + "osobę z ustawionej kolejki.", 12, false));
        java.util.ArrayList<Long> rotationIds = id == null
            ? new java.util.ArrayList<>() : db.taskRotation(id);
        LinearLayout rotationEditor = new LinearLayout(this);
        rotationEditor.setOrientation(LinearLayout.VERTICAL);
        form.addView(rotationEditor);
        renderRotationEditor(rotationEditor, rotationIds,
            memberIds, memberNames, chosenMember);

        form.addView(text("Przypomnienie dla tej czynności", 16, true));
        form.addView(text("Standardowe: około 09:00 w dniu terminu lub dla "
            + "zaległych. Własne: wybrana godzina i wyprzedzenie. "
            + "Aktualna cisza: " + quietHoursStart() + "–" + quietHoursEnd()
            + ". Alert ustawiony po początku ciszy przesuwamy godzinę przed "
            + "jej początkiem; po północy — na koniec ciszy. "
            + "Android może opóźnić alarm.", 12, false));
        String[] savedReminder = id == null
            ? new String[]{"", "0"} : db.taskReminder(id);
        Spinner reminderMode = new Spinner(this);
        reminderMode.setAdapter(themeSpinnerAdapter(java.util.Arrays.asList(
            "Standardowo ok. 09:00", "Moja godzina i wyprzedzenie")));
        reminderMode.setSelection(savedReminder[0].isEmpty() ? 0 : 1);
        form.addView(reminderMode);
        EditText reminderHour = new EditText(this);
        reminderHour.setSingleLine(true);
        reminderHour.setFocusable(false);
        reminderHour.setText(savedReminder[0].isEmpty()
            ? "19:00" : savedReminder[0]);
        reminderHour.setTextColor(ink);
        reminderHour.setOnClickListener(v -> {
            java.time.LocalTime initial = java.time.LocalTime.parse(
                reminderHour.getText().toString());
            new android.app.TimePickerDialog(this,
                (picker, hour, minute) -> reminderHour.setText(
                    String.format(java.util.Locale.ROOT, "%02d:%02d",
                        hour, minute)),
                initial.getHour(), initial.getMinute(), true).show();
        });
        form.addView(reminderHour);
        smallButton(form, "Wybierz godzinę", () -> reminderHour.performClick());
        Spinner lead = new Spinner(this);
        lead.setAdapter(themeSpinnerAdapter(java.util.Arrays.asList(
            ReminderRules.LEAD_LABELS)));
        int savedLead = Integer.parseInt(savedReminder[1]);
        for (int i = 0; i < ReminderRules.LEADS.length; i++)
            if (ReminderRules.LEADS[i] == savedLead) lead.setSelection(i);
        form.addView(lead);
        form.addView(text("Włącz przypomnienia globalnie w Ustawieniach. "
            + "Bez daty można używać tylko opcji standardowej.", 12, false));

        form.addView(text("Propozycje terminu (opcjonalnie)", 16, true));
        form.addView(text("Do 3 dat w najbliższych 28 dniach, według grafiku "
            + "wybranej osoby, wyjątków i czasu wykonania. Godziny są orientacyjne: "
            + "nie sprawdzamy jeszcze innych czynności, dojazdu ani prywatnego "
            + "kalendarza. Wybranie daty NIE zapisuje zadania — kliknij Zapisz.",
            12, false));
        LinearLayout proposals = new LinearLayout(this);
        proposals.setOrientation(LinearLayout.VERTICAL);
        form.addView(proposals);
        smallButton(form, "Zaproponuj 3 terminy", () -> {
            proposals.removeAllViews();
            Long chosen = memberIds.get(chosenMember.getSelectedItemPosition());
            if (chosen == null) {
                proposals.addView(text("Wybierz wykonawcę. Bez jego grafiku "
                    + "nie wyznaczam fikcyjnych wolnych terminów.", 13, false));
                return;
            }
            int minutes;
            try {
                minutes = Integer.parseInt(
                    duration.getText().toString().trim());
            } catch (NumberFormatException error) {
                duration.setError("Podaj czas od 1 do 480 minut.");
                return;
            }
            if (minutes < MIN_TASK_MINUTES || minutes > MAX_TASK_MINUTES) {
                duration.setError("Podaj czas od 1 do 480 minut.");
                return;
            }
            java.util.List<TimeSuggestions.Option> candidates =
                TimeSuggestions.propose(java.time.LocalDateTime.now(), minutes,
                    day -> db.effectiveShift(chosen, day.toString()));
            if (candidates.isEmpty()) {
                proposals.addView(text("Brak potwierdzonych okien w grafiku "
                    + "tej osoby przez 28 dni. Ustaw dni pracy/wolne i wyjątki "
                    + "lub wybierz termin ręcznie.", 13, false));
            } else {
                for (TimeSuggestions.Option option : candidates) {
                    String caption = option.date
                        + " • orientacyjnie " + option.start
                        + "–" + option.end;
                    smallButton(proposals, "Wybierz " + caption, () -> {
                        date.setText(option.date.toString());
                        proposals.removeAllViews();
                        proposals.addView(text("Wybrano " + caption
                            + ". Zapisz formularz, aby zatwierdzić termin.",
                            13, false));
                        DiagnosticLog.event("TASK_DATE_SUGGESTION_CHOSEN");
                    });
                }
                if (candidates.size() < 3)
                    proposals.addView(text("W grafiku znaleziono tylko "
                        + candidates.size() + " terminy. Nie dopisuję "
                        + "nieznanych dni jako wolnych.", 13, false));
            }
            DiagnosticLog.event("TASK_DATE_SUGGESTIONS_VIEWED");
        });

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
                if (id != null && db.isWasteTask(id) && due.isEmpty()) {
                    date.setError("Wystawianie odpadów wymaga terminu.");
                    return;
                }
                String customTime = reminderMode.getSelectedItemPosition() == 0
                    ? null : reminderHour.getText().toString();
                int leadDays = reminderMode.getSelectedItemPosition() == 0
                    ? 0 : ReminderRules.LEADS[lead.getSelectedItemPosition()];
                if (customTime != null && due.isEmpty()) {
                    date.setError("Własne przypomnienie wymaga terminu.");
                    return;
                }
                if (id != null) ReminderReceiver.cancelTask(this, id);
                Long selectedAssignee =
                    memberIds.get(chosenMember.getSelectedItemPosition());
                String rotationError = RotationRules.validate(rotationIds,
                    TaskRules.recurring(rule));
                if (rotationError != null) {
                    alert(rotationError);
                    return;
                }
                if (!rotationIds.isEmpty()
                        && (selectedAssignee == null
                            || !rotationIds.contains(selectedAssignee))) {
                    alert("Aktualny wykonawca musi należeć do rotacji.");
                    return;
                }
                db.saveTask(id, title, due, rule, every,
                    placeIds.get(chosenPlace.getSelectedItemPosition()),
                    selectedPriority, estimatedMinutes,
                    selectedAssignee, customTime, leadDays,
                    new java.util.ArrayList<>(rotationIds));
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
                "SELECT completed_at,due_date,next_due_date,assignee_name_snapshot "
                + "FROM task_history WHERE task_id=? ORDER BY id DESC LIMIT 100",
                new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) {
                found++;
                String time = Instant.ofEpochMilli(cursor.getLong(0))
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                history.append(time);
                if (!cursor.isNull(1)) history.append(" • termin ").append(cursor.getString(1));
                if (!cursor.isNull(2)) history.append(" → ").append(cursor.getString(2));
                if (!cursor.isNull(3))
                    history.append(" • wykonał/a: ").append(cursor.getString(3));
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
                "SELECT title_snapshot,completed_at,due_date,next_due_date,"
                + "assignee_name_snapshot FROM task_history "
                + "ORDER BY id DESC LIMIT 100", null)) {
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
                if (!c.isNull(4)) entry.addView(text("Wykonał/a: "
                    + c.getString(4), 13, false));
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
                    ? skin.accentInk : ink);
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
                    tile.setTextColor(skin.accentInk);
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

    private static final class PlaceEntry {
        final long id;
        final String name, kind, icon;
        final Long parent;
        PlaceEntry(long id, String name, String kind, Long parent, String icon) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.parent = parent;
            this.icon = icon;
        }
    }

    private java.util.List<PlaceEntry> readPlaces() {
        java.util.List<PlaceEntry> entries = new java.util.ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,name,kind,parent_id,icon FROM places "
                + "ORDER BY name COLLATE NOCASE,id", null)) {
            while (c.moveToNext()) entries.add(new PlaceEntry(
                c.getLong(0), c.getString(1), c.getString(2),
                c.isNull(3) ? null : c.getLong(3), c.getString(4)));
        }
        return entries;
    }

    private void places() {
        header("Miejsca • moje gospodarstwo");
        button("▣ Rzeczy i pudełka / QR", () -> go("storage"));
        note("Ty nazywasz lokalizacje. Dom → Kuchnia → Szafka → "
            + "Półka to przykładowa ścieżka, nie narzucona lista. "
            + "Rodzaj jest opcjonalny; miejsce może mieć dowolną liczbę podmiejsc.");
        button("+ Dodaj miejsce główne", () ->
            placeEditor(null, "", "", null, "places"));
        java.util.List<PlaceEntry> entries = readPlaces();
        java.util.Set<Long> drawn = new java.util.HashSet<>();
        for (PlaceEntry entry : entries) {
            if (entry.parent == null)
                renderPlaceBranch(entry, entries, drawn, 0);
        }
        // Display orphaned records too; the editor still validates any new move.
        for (PlaceEntry entry : entries) {
            if (!drawn.contains(entry.id))
                renderPlaceBranch(entry, entries, drawn, 0);
        }
        if (entries.isEmpty())
            note("Najpierw dodaj Dom, Ogród lub Garaż. Później wejdź "
                + "w miejsce i dodaj Kuchnię, regał lub półkę.");
        button("Wróć do czynności", () -> go("tasks"));
    }

    private void renderPlaceBranch(PlaceEntry entry,
            java.util.List<PlaceEntry> all, java.util.Set<Long> drawn,
            int depth) {
        if (!drawn.add(entry.id)) return;
        LinearLayout box = card();
        if (depth > 0) {
            TextView indent = text("↳  Poziom " + (depth + 1), 12, false);
            indent.setTextColor(subdued);
            box.addView(indent);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TileIcon pictogram = new TileIcon(this, entry.icon, accent);
        pictogram.setPadding(dp(7), dp(7), dp(7), dp(7));
        pictogram.setBackground(skin.panel(this, skin.iconBacking, 20));
        row.addView(pictogram,
            new LinearLayout.LayoutParams(dp(47), dp(47)));
        TextView heading = text(entry.name, 19, true);
        LinearLayout.LayoutParams headingParams =
            new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMargins(dp(12), 0, 0, 0);
        row.addView(heading, headingParams);
        box.addView(row);
        TextView path = text(db.placePath(entry.id), 13, false);
        path.setTextColor(subdued);
        box.addView(path);
        if (!entry.kind.isEmpty()) {
            TextView category = text("Rodzaj: " + entry.kind, 12, false);
            category.setTextColor(subdued);
            box.addView(category);
        }
        int children = 0;
        for (PlaceEntry candidate : all)
            if (candidate.parent != null && candidate.parent == entry.id)
                children++;
        final int childCount = children;
        try (Cursor tasks = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE place_id=?",
                new String[]{Long.toString(entry.id)})) {
            if (tasks.moveToFirst() && tasks.getInt(0) > 0)
                box.addView(text("Przypisane czynności: "
                    + tasks.getInt(0), 13, false));
        }
        box.addView(text("Podmiejsca: " + childCount, 13, false));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(actions);
        taskAction(actions, "+ Wewnątrz", () ->
            placeEditor(null, "", "", entry.id, "places"));
        taskAction(actions, "Edytuj", () -> placeEditor(entry.id,
            entry.name, entry.kind, entry.parent, entry.icon));
        box.setOnLongClickListener(v -> {
            String[] options = {"Edytuj", "Przenieś", "Dodaj miejsce wewnątrz",
                "Usuń"};
            new AlertDialog.Builder(this)
                .setTitle(db.placePath(entry.id))
                .setItems(options, (dialog, choice) -> {
                    if (choice == 0 || choice == 1)
                        placeEditor(entry.id, entry.name, entry.kind,
                            entry.parent, entry.icon);
                    else if (choice == 2)
                        placeEditor(null, "", "", entry.id, "places");
                    else confirmDeletePlace(entry, childCount);
                }).show();
            return true;
        });
        for (PlaceEntry child : all) {
            if (child.parent != null && child.parent == entry.id)
                renderPlaceBranch(child, all, drawn, depth + 1);
        }
    }

    private void confirmDeletePlace(PlaceEntry entry, int childCount) {
        if (childCount > 0) {
            alert("Najpierw przenieś lub usuń podmiejsca. "
                + "Nie usuwamy całej gałęzi przypadkowo.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Usunąć miejsce?")
            .setMessage(db.placePath(entry.id)
                + "\nPrzypisane czynności pozostaną bez miejsca. "
                + "Historia wykonań zostanie zachowana.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń", (dialog, which) -> {
                if (!db.deletePlace(entry.id)) {
                    alert("Miejsce ma podmiejsca lub rzeczy/pudełka. Przenieś je najpierw.");
                    return;
                }
                DiagnosticLog.event("PLACE_DELETED");
                render();
            }).show();
    }

    private void placeEditor(Long id, String name, String kind,
            Long selectedParent, String currentIcon) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));
        layout.addView(text("Nazwa miejsca • dowolna, Twoja", 16, true));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(name);
        input.setTextColor(ink);
        input.setHintTextColor(subdued);
        input.setHint("np. Kuchnia, Regał A, Półka 1");
        layout.addView(input);
        layout.addView(text("Znajduje się w (opcjonalnie)", 16, true));
        java.util.ArrayList<Long> parents = new java.util.ArrayList<>();
        java.util.ArrayList<String> paths = new java.util.ArrayList<>();
        parents.add(null);
        paths.add("Brak • poziom główny");
        for (PlaceEntry candidate : readPlaces()) {
            if (db.canPlaceWithin(id, candidate.id)) {
                parents.add(candidate.id);
                paths.add(db.placePath(candidate.id));
            }
        }
        Spinner parent = new Spinner(this);
        parent.setAdapter(themeSpinnerAdapter(paths));
        for (int i = 0; i < parents.size(); i++) {
            if (java.util.Objects.equals(parents.get(i), selectedParent)) {
                parent.setSelection(i);
                break;
            }
        }
        layout.addView(parent);
        layout.addView(text("Rodzaj (opcjonalnie, własny opis)", 16, true));
        EditText type = new EditText(this);
        type.setSingleLine(true);
        type.setText(kind);
        type.setTextColor(ink);
        type.setHintTextColor(subdued);
        type.setHint("np. pomieszczenie, półka, szafka, regał");
        layout.addView(type);
        layout.addView(text("Ikona", 16, true));
        Spinner icon = new Spinner(this);
        icon.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(TileIcon.ICON_NAMES)));
        icon.setSelection(Math.max(0,
            java.util.Arrays.asList(TileIcon.ICON_IDS).indexOf(currentIcon)));
        layout.addView(icon);
        LinearLayout preview = new LinearLayout(this);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, dp(9), 0, dp(9));
        layout.addView(preview);
        icon.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(
                        android.widget.AdapterView<?> view) { }
                @Override public void onItemSelected(
                        android.widget.AdapterView<?> view, View v,
                        int index, long rowId) {
                    preview.removeAllViews();
                    TileIcon picture = new TileIcon(MainActivity.this,
                        TileIcon.ICON_IDS[index], accent);
                    picture.setPadding(dp(8), dp(8), dp(8), dp(8));
                    picture.setBackground(skin.panel(MainActivity.this,
                        skin.iconBacking, 24));
                    preview.addView(picture,
                        new LinearLayout.LayoutParams(dp(60), dp(60)));
                }
            });
        TextView pathPreview = text("Nowa ścieżka: …", 13, true);
        pathPreview.setTextColor(accent);
        layout.addView(pathPreview);
        Runnable refresh = () -> {
            String segment = input.getText().toString().trim();
            String prefix = parents.get(parent.getSelectedItemPosition()) == null
                ? "" : paths.get(parent.getSelectedItemPosition()) + " → ";
            pathPreview.setText("Nowa ścieżka: " + prefix
                + (segment.isEmpty() ? "(nazwa miejsca)" : segment));
        };
        parent.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(
                        android.widget.AdapterView<?> view) { }
                @Override public void onItemSelected(
                        android.widget.AdapterView<?> view, View v,
                        int index, long rowId) { refresh.run(); }
            });
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,
                    int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s,
                    int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(
                    android.text.Editable value) { }
        });
        refresh.run();
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(id == null ? "Nowe miejsce" : "Edytuj / przenieś miejsce")
            .setView(scroller)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(
                skin.panel(this, surface, 28));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    String category = type.getText().toString().trim();
                    Long parentId = parents.get(parent.getSelectedItemPosition());
                    String iconId = TileIcon.ICON_IDS[
                        icon.getSelectedItemPosition()];
                    String problem = PlaceRules.validateFields(
                        value, category, iconId);
                    if (problem != null) {
                        input.setError(problem);
                        return;
                    }
                    try {
                        if (!db.savePlace(id, value, category,
                                parentId, iconId)) {
                            input.setError("Ta nazwa już istnieje w "
                                + "wybranej lokalizacji.");
                            return;
                        }
                        DiagnosticLog.event(id == null
                            ? "PLACE_ADDED" : "PLACE_EDITED");
                        dialog.dismiss();
                        render();
                    } catch (IllegalArgumentException error) {
                        alert(error.getMessage());
                    } catch (Exception error) {
                        DiagnosticLog.error("PLACE_SAVE", error);
                        alert("Nie można zapisać miejsca.");
                    }
                });
        });
        dialog.show();
    }


    private void storage() {
        header("Rzeczy • pudełka • QR");
        note("Rzeczy dziedziczą lokalizację po pudełku. Przeniesienie pudełka "
            + "zmienia ich wyświetlaną lokalizację, ale nie zmienia indywidualnego QR.");
        button("+ Dodaj rzecz", () -> storageEditor("thing", null));
        button("+ Dodaj pudełko", () -> storageEditor("box", null));
        button("▣ Skanuj QR rzeczy lub pudełka", () -> {
            if (storageQrCameraPending || pantrySingleCameraPending
                    || pantryBatch.active()) return;
            storageQrCameraPending = true;
            IntentIntegrator qr = new IntentIntegrator(this);
            qr.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            qr.setPrompt("EDHOME: QR rzeczy lub pudełka");
            qr.setBeepEnabled(false);
            qr.setOrientationLocked(false);
            qr.initiateScan();
        });
        button("← Miejsca", () -> go("places"));
        int count=0;
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT id FROM storage_items ORDER BY kind,name COLLATE NOCASE,id",
                null)) {
            while(c.moveToNext()) {
                StorageStore.Item item=StorageStore.find(db.getReadableDatabase(),
                    c.getLong(0));
                if(item==null)continue;
                count++;
                LinearLayout box=card();
                box.addView(text(("box".equals(item.kind)?"▣ Pudełko: ":"◉ Rzecz: ")
                    +item.name,18,true));
                box.addView(text(StorageStore.location(db.getReadableDatabase(),item),
                    13,false));
                if(item.lentTo!=null)
                    box.addView(text("Wypożyczono: "+item.lentTo,13,false));
                smallButton(box,"Pokaż QR",()->showStorageQr(item));
                if(item.lentTo==null)
                    smallButton(box,"Przenieś",()->storageEditor(item.kind,item.id));
                if("thing".equals(item.kind)){
                    if(item.lentTo==null)
                        smallButton(box,"Wypożycz",()->askStorageLend(item));
                    else smallButton(box,"Zwrot",()->{
                        try{
                            StorageStore.returned(db.getWritableDatabase(),item.id);
                            DiagnosticLog.event("STORAGE_RETURNED");render();
                        }catch(Exception e){alert(e.getMessage());}
                    });
                }
                smallButton(box,"Usuń",()->new AlertDialog.Builder(this)
                    .setTitle("Usunąć rzecz z magazynu?")
                    .setMessage(item.name+" — QR przestanie działać. Historia pozostanie.")
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Usuń",(d,w)->{
                        try {
                            StorageStore.remove(db.getWritableDatabase(),item.id);
                            DiagnosticLog.event("STORAGE_REMOVED");render();
                        }catch(Exception e){alert(e.getMessage());}
                    }).show());
            }
        }
        if(count==0)note("Dodaj pierwszą rzecz albo pudełko; nazwy i miejsca wybierasz sam.");
        title("Ostatnie ruchy magazynu");
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT name_snapshot,action,details FROM storage_events "
                +"ORDER BY id DESC LIMIT 12",null)){
            while(c.moveToNext())note(c.getString(0)+" • "+c.getString(1)
                +" • "+c.getString(2));
        }
    }

    private void storageEditor(String kind, Long itemId) {
        StorageStore.Item existing=itemId==null?null:
            StorageStore.find(db.getReadableDatabase(),itemId);
        if(itemId!=null && existing==null){
            alert("Rzecz już nie istnieje.");return;
        }
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(12),dp(18),dp(12));
        EditText name=new EditText(this);
        name.setSingleLine(true);
        name.setHint("Nazwa rzeczy albo pudełka");
        name.setText(existing==null?"":existing.name);
        if(existing==null)layout.addView(name);
        else layout.addView(text("Przenieś: "+existing.name,18,true));
        java.util.List<Long> boxIds=new java.util.ArrayList<>();
        java.util.List<Long> placeIds=new java.util.ArrayList<>();
        java.util.List<String> labels=new java.util.ArrayList<>();
        boxIds.add(null);placeIds.add(null);labels.add("Bez lokalizacji");
        for(PlaceEntry place:readPlaces()){
            boxIds.add(null);placeIds.add(place.id);
            labels.add("Miejsce: "+db.placePath(place.id));
        }
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT id,name FROM storage_items WHERE kind='box' ORDER BY name",
                null)) {
            while(c.moveToNext()){
                if(itemId!=null && c.getLong(0)==itemId)continue;
                boxIds.add(c.getLong(0));placeIds.add(null);
                labels.add("W pudełku: "+c.getString(1));
            }
        }
        Spinner destination=new Spinner(this);
        destination.setAdapter(themeSpinnerAdapter(labels));
        if(existing!=null)for(int i=0;i<labels.size();i++){
            if(java.util.Objects.equals(boxIds.get(i),existing.boxId)
                    &&java.util.Objects.equals(placeIds.get(i),existing.placeId)){
                destination.setSelection(i);break;
            }
        }
        layout.addView(text("Położenie (rzeczy w pudełku dziedziczą jego miejsce)",
            14,false));
        layout.addView(destination);
        new AlertDialog.Builder(this)
            .setTitle(existing==null?"Dodaj do magazynu":"Przenieś")
            .setView(layout).setNegativeButton("Anuluj",null)
            .setPositiveButton(existing==null?"Dodaj":"Przenieś",(d,w)->{
                try{
                    int i=destination.getSelectedItemPosition();
                    if(existing==null)StorageStore.create(db.getWritableDatabase(),
                        name.getText().toString(),kind,boxIds.get(i),placeIds.get(i));
                    else StorageStore.move(db.getWritableDatabase(),existing.id,
                        boxIds.get(i),placeIds.get(i));
                    DiagnosticLog.event(existing==null?
                        "STORAGE_CREATED":"STORAGE_MOVED");
                    render();
                }catch(Exception problem){alert(problem.getMessage());}
            }).show();
    }

    private void askStorageLend(StorageStore.Item item){
        EditText recipient=new EditText(this);
        recipient.setSingleLine(true);recipient.setHint("Komu wypożyczono?");
        new AlertDialog.Builder(this).setTitle("Wypożycz: "+item.name)
            .setView(recipient).setNegativeButton("Anuluj",null)
            .setPositiveButton("Wypożycz",(d,w)->{
                try{
                    StorageStore.lend(db.getWritableDatabase(),item.id,
                        recipient.getText().toString());
                    DiagnosticLog.event("STORAGE_LENT");render();
                }catch(Exception problem){alert(problem.getMessage());}
            }).show();
    }

    private void showStorageQr(StorageStore.Item item) {
        String payload=StorageQr.encode(item.kind,item.id);
        try{
            com.google.zxing.common.BitMatrix bits =
                new com.google.zxing.MultiFormatWriter().encode(payload,
                    com.google.zxing.BarcodeFormat.QR_CODE,384,384);
            Bitmap bmp=Bitmap.createBitmap(bits.getWidth(),bits.getHeight(),
                Bitmap.Config.ARGB_8888);
            for(int y=0;y<bits.getHeight();y++)
                for(int x=0;x<bits.getWidth();x++)
                    bmp.setPixel(x,y,bits.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView picture=new ImageView(this);
            picture.setImageBitmap(bmp);
            picture.setAdjustViewBounds(true);
            new AlertDialog.Builder(this).setTitle("QR • "+item.name)
                .setMessage("Identyfikator rzeczy pozostaje ten sam po przeniesieniu. "
                    +"QR działa na tym urządzeniu; synchronizacja w kolejnym etapie.")
                .setView(picture).setNegativeButton("Zamknij",null)
                .setPositiveButton("Kopiuj kod",(d,w)->{
                    ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("EDHOME QR",payload));
                }).show();
        }catch(Exception error){
            DiagnosticLog.error("STORAGE_QR_DRAW",error);
            alert("Nie udało się wyświetlić QR.");
        }
    }

    private void openStorageQr(String value) {
        StorageQr.Target target=StorageQr.decode(value);
        if(target==null){alert("To nie jest QR rzeczy/pudełka EDHOME.");return;}
        StorageStore.Item item=StorageStore.find(db.getReadableDatabase(),target.id);
        if(item==null||!item.kind.equals(target.kind)){
            alert("Nie znaleziono obiektu o tym QR w lokalnym magazynie.");
            return;
        }
        new AlertDialog.Builder(this).setTitle(item.name)
            .setMessage(("box".equals(item.kind)?"Pudełko":"Rzecz")
                +"\n"+StorageStore.location(db.getReadableDatabase(),item)
                +(item.lentTo==null?"":"\nWypożyczono: "+item.lentTo))
            .setNegativeButton("Zamknij",null)
            .setPositiveButton("Magazyn",(d,w)->go("storage")).show();
    }

    private void paycheck() {
        header("PayCheck • wspólny budżet");
        note("Wspólne finanse pozostają dostępne bez PIN-u Bety. "
            + "Prywatne finanse mają osobny sejf z hasłem i szyfrowaniem.");
        button("🔒 Prywatny sejf PayCheck", this::openPrivatePaycheck);
        note("Zakup z listy i przyjęcie do spiżarni nie księgują wydatku. "
            + "Podaj rzeczywistą kwotę dopiero po dokonanej płatności.");
        title("Saldo wspólne: " + MoneyRules.format(
            PaycheckStore.sharedBalance(db.getReadableDatabase())));
        Spinner kind=new Spinner(this);
        kind.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList("Wydatek −","Przychód +")));
        body.addView(kind);
        Spinner category=new Spinner(this);
        category.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(MoneyRules.CATEGORY_LABELS)));
        body.addView(category);
        EditText amount=field("Kwota w PLN, np. 12,50",false);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText noteField=field("Opis (opcjonalnie, maks. 160 znaków)",false);
        button("Dodaj transakcję do wspólnego budżetu",()->{
            long grosz;
            try{grosz=MoneyRules.parse(amount.getText().toString());}
            catch(IllegalArgumentException error){amount.setError(error.getMessage());return;}
            String description=noteField.getText().toString().trim();
            if(description.length()>160){
                noteField.setError("Opis ma maksymalnie 160 znaków.");return;
            }
            String type=kind.getSelectedItemPosition()==1?"income":"expense";
            String group=MoneyRules.CATEGORIES[category.getSelectedItemPosition()];
            String operationId=java.util.UUID.randomUUID().toString();
            new AlertDialog.Builder(this)
                .setTitle("Potwierdź transakcję wspólną")
                .setMessage(("income".equals(type)?"Przychód: ":"Wydatek: ")
                    +MoneyRules.format(grosz)+"\n"
                    +MoneyRules.categoryLabel(group)
                    +(description.isEmpty()?"":"\n"+description)
                    +"\n\nBez automatycznego powiązania z zakupami.")
                .setNegativeButton("Anuluj",null)
                .setPositiveButton("Zapisz",(d,w)->{
                    try{
                        String outcome=PaycheckStore.add(
                            db.getWritableDatabase(),operationId,
                            type,group,grosz,description);
                        if("COMMITTED".equals(outcome)){
                            DiagnosticLog.event("PAYCHECK_SHARED_COMMITTED");
                            render();
                        }else alert("Ta operacja była już zapisana.");
                    }catch(Exception problem){
                        DiagnosticLog.error("PAYCHECK_SHARED",problem);
                        alert("Nie zapisano transakcji.");
                    }
                }).show();
        });
        title("Historia wspólna");
        int count=0;
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT kind,category,amount_grosz,note,created_at "
                +"FROM paycheck_transactions WHERE scope='shared' "
                +"ORDER BY id DESC LIMIT 40",null)){
            while(c.moveToNext()){
                count++;
                LinearLayout entry=card();
                boolean income="income".equals(c.getString(0));
                entry.addView(text((income?"+ ":"− ")
                    +MoneyRules.format(c.getLong(2)),18,true));
                entry.addView(text(MoneyRules.categoryLabel(c.getString(1))
                    +(c.getString(3).isEmpty()?"":" • "+c.getString(3)),14,false));
                entry.addView(text(Instant.ofEpochMilli(c.getLong(4))
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    12,false));
            }
        }
        if(count==0)note("Brak transakcji wspólnych. Niczego nie księgujemy automatycznie.");
        sharedPaycheckGoals();
    }

    private void openPrivatePaycheck() {
        if (privatePaycheckSession != null && privatePaycheckSession.active()) {
            go("paycheck_private");
            return;
        }
        final boolean first = !PrivatePaycheckVault.configured(this);
        if (!first && PrivatePaycheckVault.cooldownMillis(this) > 0) {
            alert("Sejf czasowo zablokowany po błędnych hasłach. "
                + "Spróbuj za kilka minut.");
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(14), dp(18), dp(14));
        form.addView(text(first
            ? "Utwórz osobne hasło sejfu: 12–64 znaki. "
                + "Nie jest to PIN aplikacji. Bez hasła nie odzyskasz danych. "
                + "Sejf nie wchodzi do zwykłej kopii JSON."
            : "Wpisz hasło prywatnego sejfu. "
                + "Wspólny PayCheck pozostaje bez zmian.", 14, false));
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Hasło prywatnego sejfu");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        password.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        form.addView(password);
        EditText confirmation = null;
        if (first) {
            confirmation = new EditText(this);
            confirmation.setSingleLine(true);
            confirmation.setHint("Powtórz hasło");
            confirmation.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            confirmation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            confirmation.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            form.addView(confirmation);
        }
        final EditText again = confirmation;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(first ? "Utwórz prywatny sejf" : "Otwórz prywatny sejf")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton(first ? "Utwórz" : "Otwórz", null)
            .create();
        privateAuthDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (privateAuthDialog == dialog) privateAuthDialog = null;
            if (!"paycheck_private".equals(screen))
                getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        });
        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                char[] secret = password.getText().toString().toCharArray();
                char[] confirm = again == null ? null
                    : again.getText().toString().toCharArray();
                try {
                    if (first && (!PrivatePaycheckCrypto.validPassword(secret)
                            || !java.util.Arrays.equals(secret, confirm))) {
                        password.setError("Hasła muszą być identyczne "
                            + "i mieć 12–64 znaki.");
                        return;
                    }
                    if (!first && PrivatePaycheckVault.cooldownMillis(this) > 0) {
                        password.setError("Poczekaj kilka minut.");
                        return;
                    }
                    PrivatePaycheckVault.Session session = first
                        ? PrivatePaycheckVault.configure(this, secret)
                        : PrivatePaycheckVault.unlock(this, secret);
                    PrivatePaycheckVault.clearFailures(this);
                    privatePaycheckSession = session;
                    password.getText().clear();
                    if (again != null) again.getText().clear();
                    dialog.dismiss();
                    go("paycheck_private");
                } catch (Exception denied) {
                    if (!first) PrivatePaycheckVault.recordFailure(this);
                    password.setError(first
                        ? "Nie utworzono sejfu. Spróbuj ponownie."
                        : "Nieprawidłowe hasło lub uszkodzony sejf.");
                } finally {
                    java.util.Arrays.fill(secret, '\0');
                    if (confirm != null) java.util.Arrays.fill(confirm, '\0');
                }
            }));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void privatePaycheck() {
        if (privatePaycheckSession == null || !privatePaycheckSession.active()) {
            go("paycheck");
            return;
        }
        header("🔒 PayCheck • prywatny sejf");
        note("Tylko ten telefon, osobne hasło i zaszyfrowane wpisy. "
            + "Sejf zamyka się po opuszczeniu aplikacji. Prywatne dane nie "
            + "trafiają do wspólnej historii, diagnostyki ani kopii JSON.");
        note("Prywatny sejf nie wchodzi do zwykłej kopii JSON EDHOME. "
            + "Osobna zaszyfrowana kopia wymaga hasła kopii (12–64 znaki). "
            + "Bez hasła kopii nie odzyskasz jej zawartości.");
        button("🔐 Eksportuj zaszyfrowaną kopię prywatną",
            this::exportPrivatePaycheckDialog);
        button("↥ Importuj zaszyfrowaną kopię prywatną",
            this::selectPrivatePaycheckBackup);
        button("🔒 Zablokuj i wróć do wspólnego", () -> go("paycheck"));
        final java.util.List<PrivatePaycheckVault.Entry> entries;
        try {
            entries = PrivatePaycheckVault.entries(this, privatePaycheckSession);
        } catch (Exception damaged) {
            go("paycheck");
            alert("Nie można otworzyć prywatnych danych. "
                + "Sprawdź hasło lub integralność sejfu.");
            return;
        }
        long balance = 0;
        try {
            for (PrivatePaycheckVault.Entry entry : entries)
                balance = Math.addExact(balance,
                    "income".equals(entry.kind)
                        ? entry.amountGrosz : -entry.amountGrosz);
        } catch (ArithmeticException overflow) {
            go("paycheck");
            alert("Saldo sejfu przekracza dopuszczalny zakres.");
            return;
        }
        title("Saldo prywatne: " + MoneyRules.format(balance));
        Spinner kind = new Spinner(this);
        kind.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList("Wydatek −", "Przychód +")));
        body.addView(kind);
        Spinner category = new Spinner(this);
        category.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(MoneyRules.CATEGORY_LABELS)));
        body.addView(category);
        EditText amount = field("Kwota prywatna w PLN, np. 12,50", false);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText noteField = field("Opis prywatny (opcjonalnie)", false);
        button("Dodaj prywatną transakcję", () -> {
            if (privatePaycheckSession == null
                    || !privatePaycheckSession.active()) return;
            final long grosz;
            try {
                grosz = MoneyRules.parse(amount.getText().toString());
            } catch (IllegalArgumentException error) {
                amount.setError(error.getMessage());
                return;
            }
            String description = noteField.getText().toString().trim();
            if (description.length() > 160) {
                noteField.setError("Opis ma maks. 160 znaków.");
                return;
            }
            String type = kind.getSelectedItemPosition() == 1
                ? "income" : "expense";
            String group = MoneyRules.CATEGORIES[
                category.getSelectedItemPosition()];
            String operationId = java.util.UUID.randomUUID().toString();
            AlertDialog confirmationDialog = new AlertDialog.Builder(this)
                .setTitle("Potwierdź prywatną transakcję")
                .setMessage(("income".equals(type) ? "Przychód: " : "Wydatek: ")
                    + MoneyRules.format(grosz) + "\n"
                    + MoneyRules.categoryLabel(group)
                    + "\\nBez wpisu do wspólnego PayCheck.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Zapisz w sejfie", (d,w) -> {
                    if (privatePaycheckSession == null
                            || !privatePaycheckSession.active()) return;
                    try {
                        String status = PrivatePaycheckVault.add(
                            this, privatePaycheckSession, operationId,
                            type, group, grosz, description);
                        if ("COMMITTED".equals(status)) render();
                        else alert("Ta prywatna operacja była już zapisana.");
                    } catch (Exception error) {
                        alert("Nie zapisano prywatnej transakcji.");
                    }
                }).create();
            privateEntryDialog = confirmationDialog;
            confirmationDialog.setOnDismissListener(d -> {
                if (privateEntryDialog == confirmationDialog)
                    privateEntryDialog = null;
            });
            confirmationDialog.show();
            if (confirmationDialog.getWindow() != null)
                confirmationDialog.getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        });
        title("Historia prywatna • " + entries.size());
        int shown = 0;
        for (PrivatePaycheckVault.Entry entry : entries) {
            if (shown++ >= 40) break;
            LinearLayout row = card();
            boolean income = "income".equals(entry.kind);
            row.addView(text((income ? "+ " : "− ")
                + MoneyRules.format(entry.amountGrosz), 18, true));
            row.addView(text(MoneyRules.categoryLabel(entry.category)
                + (entry.note.isEmpty() ? "" : " • " + entry.note), 14, false));
            row.addView(text(Instant.ofEpochMilli(entry.createdAt)
                .atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                12, false));
        }
        if (entries.isEmpty())
            note("Brak prywatnych wpisów. Wspólny budżet pozostaje osobny.");
    }


    private EditText securePrivatePassword(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        return input;
    }

    private void exportPrivatePaycheckDialog() {
        if (privatePaycheckSession == null || !privatePaycheckSession.active()) {
            alert("Odblokuj najpierw prywatny sejf."); return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));
        form.addView(text("Utwórz OSOBNE hasło kopii 12–64 znaki. "
            + "Nie jest to PIN ani hasło do sejfu. Zachowaj je — bez niego nie odtworzysz pliku.",
            14, false));
        EditText pass = securePrivatePassword("Hasło kopii");
        EditText again = securePrivatePassword("Powtórz hasło kopii");
        form.addView(pass);
        form.addView(again);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Szyfrowana kopia PayCheck").setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zaszyfruj i zapisz", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null)
                dialog.getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                char[] password = pass.getText().toString().toCharArray();
                char[] repeat = again.getText().toString().toCharArray();
                try {
                    if (!PrivatePaycheckCrypto.validPassword(password)
                            || !java.util.Arrays.equals(password, repeat)) {
                        pass.setError("Hasło kopii musi mieć 12–64 znaki i zgadzać się z powtórzeniem.");
                        return;
                    }
                    if (privatePaycheckSession == null
                            || !privatePaycheckSession.active()) {
                        alert("Sejf zablokowany — otwórz go ponownie."); return;
                    }
                    privateBackupForSave = PrivatePaycheckPortable.exportEncrypted(
                        this, privatePaycheckSession, password);
                    pass.getText().clear();
                    again.getText().clear();
                    dialog.dismiss();
                    Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType("application/json");
                    picker.putExtra(Intent.EXTRA_TITLE,
                        "edhome-paycheck-prywatny-zaszyfrowany.json");
                    startActivityForResult(picker, EXPORT_PRIVATE_BACKUP);
                } catch (Exception error) {
                    privateBackupForSave = null;
                    DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_EXPORT_FAILED");
                    alert("Nie udało się przygotować zaszyfrowanej kopii. Dane sejfu pozostają bez zmian.");
                } finally {
                    java.util.Arrays.fill(password, (char) 0);
                    java.util.Arrays.fill(repeat, (char) 0);
                }
            });
        });
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private void selectPrivatePaycheckBackup() {
        if (!PrivatePaycheckVault.configured(this)) {
            alert("Najpierw utwórz prywatny sejf i hasło. "
                + "Potem zaimportujesz do niego zaszyfrowaną kopię.");
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json");
        startActivityForResult(picker, IMPORT_PRIVATE_BACKUP);
    }

    private void privateBackupImportDialog(final String archive) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));
        form.addView(text("Import nie zastępuje istniejących transakcji. "
            + "Identyczne operacje zostaną pominięte, konflikt przerwie CAŁY import.",
            14, false));
        EditText vaultPassword = securePrivatePassword("Hasło obecnego sejfu");
        EditText archivePassword = securePrivatePassword("Hasło kopii");
        form.addView(vaultPassword);
        form.addView(archivePassword);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Przywróć prywatny PayCheck").setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Sprawdź i importuj", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null)
                dialog.getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                char[] vaultPass = vaultPassword.getText().toString().toCharArray();
                char[] backupPass = archivePassword.getText().toString().toCharArray();
                PrivatePaycheckVault.Session session = null;
                try {
                    if (PrivatePaycheckVault.cooldownMillis(this) > 0) {
                        alert("Sejf czasowo zablokowany. Spróbuj później."); return;
                    }
                    try {
                        session = PrivatePaycheckVault.unlock(this, vaultPass);
                        PrivatePaycheckVault.clearFailures(this);
                    } catch (Exception wrongPassword) {
                        PrivatePaycheckVault.recordFailure(this);
                        vaultPassword.setError("Nieprawidłowe hasło sejfu.");
                        return;
                    }
                    if (!PrivatePaycheckCrypto.validPassword(backupPass)) {
                        archivePassword.setError("Podaj hasło kopii (12–64 znaki).");
                        return;
                    }
                    int imported = PrivatePaycheckPortable.importEncrypted(
                        this, session, archive, backupPass);
                    vaultPassword.getText().clear();
                    archivePassword.getText().clear();
                    dialog.dismiss();
                    DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_IMPORTED");
                    alert("Import zakończony. Nowe prywatne transakcje: " + imported
                        + ". Pozostałe operacje nie zostały nadpisane.");
                } catch (Exception error) {
                    DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_IMPORT_REJECTED");
                    archivePassword.setError("Nieprawidłowe hasło kopii, uszkodzony plik "
                        + "lub konflikt transakcji. Niczego nie nadpisano.");
                } finally {
                    if (session != null) session.lock();
                    java.util.Arrays.fill(vaultPass, (char) 0);
                    java.util.Arrays.fill(backupPass, (char) 0);
                }
            });
        });
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(d -> {
            if (!"paycheck_private".equals(screen))
                getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        });
        dialog.show();
    }

    private void sharedPaycheckGoals() {
        title("Wspólne cele finansowe");
        note("Odkładanie na cel to plan oszczędzania, a nie nowy wydatek "
            + "ani rzeczywisty przelew. Nie zmienia salda wspólnego PayCheck.");
        button("+ Nowy cel wspólny", this::createSharedPaycheckGoal);
        int goals = 0;
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,name,target_grosz FROM paycheck_goals "
                + "WHERE scope='shared' ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                goals++;
                long id = c.getLong(0);
                String name = c.getString(1);
                long target = c.getLong(2);
                long saved = PaycheckGoalsStore.allocated(
                    db.getReadableDatabase(), id);
                LinearLayout entry = card();
                entry.addView(text(name, 19, true));
                entry.addView(text("Odłożone: " + MoneyRules.format(saved)
                    + " / " + MoneyRules.format(target), 16, false));
                entry.addView(text("Do celu: "
                    + MoneyRules.format(Math.max(0, target - saved)), 14, false));
                if (saved < target) {
                    smallButton(entry, "+ Odłóż na cel", () ->
                        allocateSharedPaycheckGoal(id, name, target, saved));
                } else entry.addView(text("✓ Cel osiągnięty", 14, true));
            }
        }
        if (goals == 0) note("Nie masz jeszcze wspólnych celów finansowych.");
    }

    private void createSharedPaycheckGoal() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Cel, np. OC samochodu");
        form.addView(name);
        EditText amount = new EditText(this);
        amount.setSingleLine(true);
        amount.setHint("Kwota celu w PLN, np. 700,00");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amount);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Nowy wspólny cel")
            .setView(form).setNegativeButton("Anuluj", null)
            .setPositiveButton("Utwórz", null).create();
        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    long target = MoneyRules.parse(amount.getText().toString());
                    PaycheckGoalsStore.addGoal(db.getWritableDatabase(),
                        name.getText().toString(), target);
                    DiagnosticLog.event("PAYCHECK_SHARED_GOAL_CREATED");
                    dialog.dismiss();
                    render();
                } catch (IllegalArgumentException error) {
                    amount.setError(error.getMessage());
                } catch (Exception error) {
                    DiagnosticLog.error("PAYCHECK_GOAL_CREATE", error);
                    alert("Nie udało się utworzyć celu.");
                }
            }));
        dialog.show();
    }

    private void allocateSharedPaycheckGoal(long id, String name,
            long target, long previous) {
        EditText amount = new EditText(this);
        amount.setSingleLine(true);
        amount.setHint("Kwota w PLN");
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));
        form.addView(text(name + "\nPozostało: "
            + MoneyRules.format(target - previous)
            + "\nOdkładanie nie księguje wydatku i nie zmienia salda.",
            15, false));
        form.addView(amount);
        String operationId = java.util.UUID.randomUUID().toString();
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Potwierdź odłożenie na cel")
            .setView(form).setNegativeButton("Anuluj", null)
            .setPositiveButton("Odłóż", null).create();
        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    long grosz = MoneyRules.parse(amount.getText().toString());
                    String outcome = PaycheckGoalsStore.allocate(
                        db.getWritableDatabase(), id, operationId, grosz);
                    if ("COMMITTED".equals(outcome)) {
                        DiagnosticLog.event("PAYCHECK_SHARED_GOAL_ALLOCATED");
                        dialog.dismiss();
                        render();
                    } else if ("DUPLICATE".equals(outcome)) {
                        dialog.dismiss();
                        alert("Ta wpłata została już zapisana.");
                        render();
                    } else amount.setError("OVER_TARGET".equals(outcome)
                        ? "Kwota przekracza kwotę pozostałą do celu."
                        : "Ten cel już nie istnieje.");
                } catch (IllegalArgumentException error) {
                    amount.setError(error.getMessage());
                } catch (Exception error) {
                    DiagnosticLog.error("PAYCHECK_GOAL_ALLOCATE", error);
                    alert("Nie udało się odłożyć kwoty.");
                }
            }));
        dialog.show();
    }

    private void shopping() {
        header("Lista zakupów • offline");
        note("Kupione ≠ przyjęte. Samo zaznaczenie nie zmienia stanu; "
            + "dopiero osobny przycisk Przyjmij dopisuje wybrane opakowania "
            + "do wskazanego produktu w spiżarni. Cena zakupu jest opcjonalna; "
            + "nie księgujemy jej automatycznie w PayCheck.");
        button("← Spiżarnia", () -> go("pantry"));
        EditText item = field("Co kupić?", false);
        EditText quantity = field("Ilość (opcjonalnie, np. 1,5)", false);
        quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        Spinner unit = new Spinner(this);
        unit.setAdapter(themeSpinnerAdapter(
            java.util.Arrays.asList(ShoppingRules.UNITS)));
        body.addView(unit);
        button("+ Dodaj do listy", () -> {
            try {
                String name = ShoppingRules.validatedName(
                    item.getText().toString());
                Long amount = ShoppingRules.parseQuantity(
                    quantity.getText().toString());
                if (!db.addShoppingItem(name, amount,
                        ShoppingRules.UNITS[unit.getSelectedItemPosition()])) {
                    item.setError("Produkt jest już na liście.");
                    return;
                }
                DiagnosticLog.event("SHOPPING_ITEM_ADDED");
                render();
            } catch (IllegalArgumentException problem) {
                alert(problem.getMessage());
            }
        });
        int count = 0;
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT id,name,qty_milli,unit,checked FROM shopping_items "
                + "ORDER BY checked ASC,name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) {
                count++;
                final long shoppingId = cursor.getLong(0);
                final String name = cursor.getString(1);
                final Long amount = cursor.isNull(2)
                    ? null : cursor.getLong(2);
                final String purchaseUnit = cursor.getString(3);
                final boolean done = cursor.getInt(4) == 1;
                final boolean received = ShoppingReceiptStore.received(
                    db.getReadableDatabase(), shoppingId);
                LinearLayout box = card();
                CheckBox check = new CheckBox(this);
                check.setText(name + " • " + ShoppingRules.formatQuantity(amount)
                    + (amount == null ? "" : " " + purchaseUnit));
                check.setTextColor(done ? subdued : ink);
                check.setTextSize(17);
                check.setButtonTintList(ColorStateList.valueOf(accent));
                check.setChecked(done);
                box.addView(check);
                try (Cursor price = PantryPriceHistoryStore.forShoppingItem(
                        db.getReadableDatabase(), shoppingId)) {
                    if (price.moveToFirst()) {
                        box.addView(text("Ostatnio zapłacono: "
                            + MoneyRules.format(price.getLong(0)) + " / "
                            + price.getString(1)
                            + (price.getString(2).isEmpty() ? ""
                                : " • " + price.getString(2)),
                            13, false));
                    }
                }
                if (received) {
                    box.addView(text("✓ Przyjęte do spiżarni — zapisano historię",
                        13, false));
                } else if (done) {
                    smallButton(box, "Przyjmij do spiżarni", () ->
                        chooseShoppingReceipt(shoppingId, name));
                }
                check.setOnCheckedChangeListener((view, isChecked) -> {
                    if (received) {
                        render();
                        alert("Ta pozycja była już przyjęta. Aby kupić ją ponownie, "
                            + "usuń ją z listy i dodaj nową.");
                        return;
                    }
                    if (isChecked && !done) {
                        shoppingBoughtDialog(shoppingId, name, purchaseUnit);
                        return;
                    }
                    db.setShoppingChecked(shoppingId, isChecked);
                    DiagnosticLog.event("SHOPPING_ITEM_CHECKED");
                    render();
                });
                smallButton(box, "Usuń z listy", () ->
                    new AlertDialog.Builder(this)
                        .setTitle("Usunąć z listy zakupów?")
                        .setMessage(name)
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Usuń", (dialog, which) -> {
                            db.deleteShoppingItem(shoppingId);
                            DiagnosticLog.event("SHOPPING_ITEM_DELETED");
                            render();
                        }).show());
            }
        }
        if (count == 0) note("Lista jest pusta. Dodaj pierwszy produkt.");
    }


    private void shoppingBoughtDialog(long shoppingId, String productName,
            String purchaseUnit) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(12), dp(18), dp(12));
        form.addView(text("Oznaczasz „Kupione”: " + productName
            + ". Cena jest DOBROWOLNA. Nie dodajemy tu zapasu ani wydatku PayCheck.",
            14, false));
        EditText price = new EditText(this);
        price.setSingleLine(true);
        price.setHint("Cena za 1 " + purchaseUnit + ", np. 6,49");
        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(price);
        EditText shop = new EditText(this);
        shop.setSingleLine(true);
        shop.setHint("Sklep (opcjonalnie)");
        form.addView(shop);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Zakup • cena opcjonalna").setView(form)
            .setNegativeButton("Anuluj", (d,w) -> render())
            .setNeutralButton("Kupione bez ceny", null)
            .setPositiveButton("Kupione i zapisz cenę", null)
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    String status = PantryPriceHistoryStore.markBought(
                        db.getWritableDatabase(), shoppingId, null, "");
                    if (!"COMMITTED".equals(status)) {
                        alert("Pozycja już kupiona albo została usunięta."); return;
                    }
                    DiagnosticLog.event("SHOPPING_BOUGHT_WITHOUT_PRICE");
                    dialog.dismiss();
                    render();
                } catch (Exception error) {
                    DiagnosticLog.event("SHOPPING_BOUGHT_FAILED");
                    alert("Nie zapisano zakupu. Spróbuj ponownie.");
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final long grosz;
                try {
                    grosz = MoneyRules.parse(price.getText().toString());
                } catch (IllegalArgumentException invalid) {
                    price.setError("Podaj cenę większą od 0 w zł, np. 6,49.");
                    return;
                }
                if (shop.getText().toString().trim().length() > 80) {
                    shop.setError("Maksymalnie 80 znaków."); return;
                }
                try {
                    String status = PantryPriceHistoryStore.markBought(
                        db.getWritableDatabase(), shoppingId, grosz,
                        shop.getText().toString());
                    if (!"COMMITTED".equals(status)) {
                        alert("Pozycja już kupiona albo została usunięta."); return;
                    }
                    DiagnosticLog.event("SHOPPING_BOUGHT_PRICE_RECORDED");
                    dialog.dismiss();
                    render();
                } catch (Exception error) {
                    DiagnosticLog.event("SHOPPING_BOUGHT_PRICE_FAILED");
                    alert("Nie zapisano zakupu ani ceny. Stan pozostał bez zmian.");
                }
            });
        });
        dialog.show();
    }

    private void chooseShoppingReceipt(long shoppingId, String shoppingName) {
        java.util.List<Long> productIds = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        try (Cursor items = db.getReadableDatabase().rawQuery(
                "SELECT id,name,qty FROM pantry ORDER BY name COLLATE NOCASE", null)) {
            while (items.moveToNext()) {
                long id = items.getLong(0);
                PantryPackageStore.Pack pack = PantryPackageStore.find(
                    db.getReadableDatabase(), id);
                productIds.add(id);
                labels.add(items.getString(1) + " • "
                    + PantryPackageRules.summary(items.getInt(2),
                        pack.unit, pack.sizeMilli));
            }
        }
        if (productIds.isEmpty()) {
            alert("Najpierw utwórz produkt w Spiżarni; niczego nie przyjęto.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Do którego produktu przyjąć: " + shoppingName + "?")
            .setItems(labels.toArray(new String[0]), (dialog, which) ->
                confirmShoppingReceipt(shoppingId, shoppingName,
                    productIds.get(which), labels.get(which)))
            .setNegativeButton("Anuluj", null).show();
    }

    private void confirmShoppingReceipt(long shoppingId, String shoppingName,
            long pantryId, String productLabel) {
        EditText count = new EditText(this);
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setText("1");
        count.setSelectAllOnFocus(true);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(14), dp(18), dp(14));
        form.addView(text(shoppingName + "\n→ " + productLabel
            + "\nSamo „kupione” nie dodaje zapasu. Potwierdź liczbę "
            + "pełnych opakowań.", 15, false));
        form.addView(count);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Potwierdź przyjęcie")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Przyjmij", null).create();
        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int packages = Integer.parseInt(count.getText().toString().trim());
                    String outcome = ShoppingReceiptStore.accept(
                        db.getWritableDatabase(), shoppingId, pantryId, packages);
                    if ("COMMITTED".equals(outcome)) {
                        DiagnosticLog.event("SHOPPING_RECEIPT_COMMITTED");
                        dialog.dismiss();
                        render();
                    } else if ("ALREADY_RECEIVED".equals(outcome)) {
                        dialog.dismiss();
                        alert("Ta pozycja była już przyjęta — nie dodano jej ponownie.");
                        render();
                    } else alert("Nie przyjęto: "
                        + ("NOT_PURCHASED".equals(outcome) ? "pozycja nie jest kupiona."
                        : "MISSING_PRODUCT".equals(outcome)
                            ? "produkt już nie istnieje." : "przekroczony limit."));
                } catch (NumberFormatException invalid) {
                    count.setError("Podaj całkowitą liczbę opakowań.");
                } catch (IllegalArgumentException invalid) {
                    count.setError(invalid.getMessage());
                } catch (Exception problem) {
                    DiagnosticLog.error("SHOPPING_RECEIPT", problem);
                    alert("Nie udało się przyjąć produktu. Stan nie został zmieniony.");
                }
            }));
        dialog.show();
    }

    private void pantry() {
        header("Spiżarnia • lokalne zapasy");
        button("☷ Lista zakupów", () -> go("shopping"));
        button("+ Dodaj produkt", () -> pantryProductDialog(null, ""));
        button("☷ Kategoria: " + (pantryCategoryFilter.isEmpty()
                ? "Wszystkie" : PantryCategories.label(pantryCategoryFilter)), () -> {
            int chosen = java.util.Arrays.asList(PantryCategories.FILTER_IDS)
                .indexOf(pantryCategoryFilter);
            new AlertDialog.Builder(this).setTitle("Filtr kategorii")
                .setSingleChoiceItems(PantryCategories.FILTER_LABELS,
                    Math.max(0, chosen), (dialog, index) -> {
                        pantryCategoryFilter = PantryCategories.FILTER_IDS[index];
                        dialog.dismiss();
                        render();
                    }).setNegativeButton("Anuluj", null).show();
        });
        button("📷 Skanuj i dodaj +1", () -> {
            finishPantryBatch();
            openPantryCamera(false);
        });
        button("📷 Skanuj i wyciągnij −1", () -> {
            finishPantryBatch();
            openPantryCamera(true);
        });
        if (!pantryBatch.active()) {
            button("📷 Skanuj serię — dodawaj +1", () -> startPantryBatch("ADD"));
            button("📷 Skanuj serię — wyciągaj −1", () -> startPantryBatch("TAKE"));
        } else {
            button("⏹ Zakończ serię • zapisano " + pantryBatch.committed(),
                this::finishPantryBatch);
        }
        button("⌨ Wpisz kod ręcznie", this::manualPantryBarcode);
        button("Historia skanów", this::showPantryScanHistory);
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
                "SELECT id,name,qty,category FROM pantry ORDER BY name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String category = cursor.getString(3);
                if (!pantryCategoryFilter.isEmpty()
                        && !pantryCategoryFilter.equals(category)) continue;
                if (!pantrySearch.isEmpty() && !name.toLowerCase(Locale.ROOT)
                    .contains(pantrySearch.toLowerCase(Locale.ROOT))) continue;
                matched++;
                int qty = cursor.getInt(2);
                LinearLayout box = card();
                PantryBarcodeStore.Details details = PantryBarcodeStore.details(
                    db.getReadableDatabase(), id);
                if (details != null && !details.imageUrl.isEmpty()) {
                    Bitmap thumbnail = PantryProductLookup.cached(this, details.imageUrl);
                    if (thumbnail != null) {
                        ImageView photo = new ImageView(this);
                        int px = (int) (getResources().getDisplayMetrics().density * 88);
                        photo.setLayoutParams(new LinearLayout.LayoutParams(px, px));
                        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        photo.setImageBitmap(thumbnail);
                        box.addView(photo);
                    } else {
                        taskAction(box, "⬇ Pobierz zdjęcie produktu",
                            () -> refreshPantryPhoto(details.imageUrl));
                    }
                }
                PantryPackageStore.Pack pack = PantryPackageStore.find(
                    db.getReadableDatabase(), id);
                box.addView(text(name + " • " + qty + " opak.", 18, true));
                box.addView(text(PantryPackageRules.summary(
                    qty, pack.unit, pack.sizeMilli), 14, false));
                box.addView(text(PantryCategories.label(category), 13, false));
                if (details != null && !details.brand.isEmpty())
                    box.addView(text("Marka: " + details.brand, 13, false));
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
                    new AlertDialog.Builder(this).setTitle(name + " • liczba opakowań")
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
                taskAction(management, "Edytuj", () -> pantryProductDialog(id, name));
                taskAction(management, "Usuń", () -> {
                    if (db.openAuditId() != 0) {
                        alert("Najpierw zakończ lub anuluj remanent. "
                            + "W jego trakcie nie można usuwać produktów.");
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("Usunąć produkt?")
                        .setMessage(name + " • " + qty + " opak.")
                        .setNegativeButton("Anuluj", null)
                        .setPositiveButton("Usuń", (d, w) -> {
                            db.deleteStock(id);
                            DiagnosticLog.event("PANTRY_PRODUCT_DELETED");
                            render();
                        }).show();
                });
            }
        }
        if (matched == 0) note(pantrySearch.isEmpty() && pantryCategoryFilter.isEmpty()
            ? "Spiżarnia jest pusta. Dodaj pierwszy produkt."
            : "Brak produktów dla wyszukiwania lub kategorii. Wyczyść filtr.");
        note("Stan zapisujemy w pełnych opakowaniach; np. 3 × 0,5 l = 1,5 l. "
            + "W serii aparat wraca po każdym zatwierdzonym skanie; "
            + "ten sam kod wymaga dodatkowego potwierdzenia.");
    }


    private void startPantryBatch(String mode) {
        if (pantryBatch.active()) return;
        pantryBatch.start(mode);
        DiagnosticLog.event("PANTRY_BATCH_STARTED");
        render();
        root.post(() -> {
            if (pantryBatch.active() && !isFinishing() && !isDestroyed())
                openPantryCamera("TAKE".equals(pantryBatch.mode()));
        });
    }

    private void finishPantryBatch() {
        if (!pantryBatch.active()) return;
        int saved = pantryBatch.stop();
        DiagnosticLog.event("PANTRY_BATCH_FINISHED");
        if ("pantry".equals(screen)) render();
        alert("Seria zakończona. Zapisane operacje: " + saved + ".");
    }

    private void continuePantryBatch() {
        if (!pantryBatch.active()) return;
        root.post(() -> {
            if (!pantryBatch.active() || isFinishing() || isDestroyed()) return;
            if (!"pantry".equals(screen)) {
                finishPantryBatch();
                return;
            }
            openPantryCamera("TAKE".equals(pantryBatch.mode()));
        });
    }

    /** Start an offline barcode scan; never mutate stock in the camera callback. */
    private void openPantryCamera(boolean take) {
        if (pantryBatch.active()) {
            if (!pantryBatch.launchCamera()) return;
        } else {
            if (pantrySingleCameraPending) return;
            pantrySingleCameraPending = true;
        }
        prefs.edit().putString(SCAN_MODE_PREF, take ? "TAKE" : "ADD").apply();
        IntentIntegrator scanner = new IntentIntegrator(this);
        scanner.setDesiredBarcodeFormats(IntentIntegrator.PRODUCT_CODE_TYPES);
        scanner.setPrompt(take ? "EDHOME: wyciągnij ze spiżarni" : "EDHOME: dodaj do spiżarni");
        scanner.setBeepEnabled(false);
        scanner.setOrientationLocked(false);
        scanner.initiateScan();
    }

    private void manualPantryBarcode() {
        finishPantryBatch();
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("EAN-8 / UPC-A / EAN-13 / GTIN-14");
        new AlertDialog.Builder(this).setTitle("Wpisz kod kreskowy")
            .setView(input).setNegativeButton("Anuluj", null)
            .setNeutralButton("Wyciągnij −1", (d, w) -> onPantryBarcode(
                input.getText().toString().trim(), "TAKE"))
            .setPositiveButton("Dodaj +1", (d, w) -> onPantryBarcode(
                input.getText().toString().trim(), "ADD")).show();
    }

    private void onPantryBarcode(String barcode, String mode) {
        onPantryBarcode(barcode, mode, false);
    }

    private void onPantryBarcode(String barcode, String mode, boolean repeatedApproved) {
        if (!PantryScanRules.validBarcode(barcode)) {
            alert("Niepoprawny kod EAN/UPC/GTIN — sprawdź cyfrę kontrolną.");
            DiagnosticLog.event("PANTRY_BARCODE_INVALID");
            finishPantryBatch();
            return;
        }
        if (!repeatedApproved && pantryBatch.repeated(barcode)) {
            new AlertDialog.Builder(this).setTitle("Ten sam kod co poprzednio")
                .setMessage("Czy to kolejne opakowanie tego samego produktu? "
                    + "Nie naliczam go ponownie bez Twojego potwierdzenia.")
                .setNegativeButton("Zakończ serię", (d,w) -> finishPantryBatch())
                .setNeutralButton("Skanuj inny", (d,w) -> {
                    if (pantryBatch.skip()) continuePantryBatch();
                })
                .setPositiveButton("Tak, kolejne opakowanie", (d,w) ->
                    onPantryBarcode(barcode, mode, true))
                .setOnCancelListener(d -> finishPantryBatch()).show();
            return;
        }
        PantryBarcodeStore.Item item = PantryBarcodeStore.find(
            db.getReadableDatabase(), barcode);
        String operationId = java.util.UUID.randomUUID().toString();
        if (item == null && "TAKE".equals(mode)) {
            finishPantryBatch();
            alert("Nieznany kod. Najpierw dodaj produkt do spiżarni.");
            return;
        }
        if (item == null) {
            new AlertDialog.Builder(this).setTitle("Nieznany kod: " + barcode)
                .setMessage("Przeszukać bazy Open Facts? Mogą zawierać żywność, "
                    + "proszki do prania, kosmetyki i karmę. Do baz zostanie "
                    + "wysłany tylko kod kreskowy. Jeśli nazwy nie będzie, "
                    + "aplikacja zaproponuje ręczny wpis.")
                .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
                .setNeutralButton("Moje produkty", (d,w) ->
                    choosePantryProductForCode(barcode, operationId))
                .setPositiveButton("Szukaj produktu", (d,w) ->
                    lookupPantryProduct(barcode, operationId))
                .setOnCancelListener(d -> finishPantryBatch()).show();
            return;
        }
        PantryPackageStore.Pack pack = PantryPackageStore.find(
            db.getReadableDatabase(), item.id);
        String question = "TAKE".equals(mode) ? "Wyciągnąć 1 opak.?" : "Dodać 1 opak.?";
        new AlertDialog.Builder(this).setTitle(item.name)
            .setMessage("Kod: " + barcode + "\nObecny stan: "
                + PantryPackageRules.summary(item.qty, pack.unit, pack.sizeMilli)
                + "\n" + question)
            .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
            .setPositiveButton("TAKE".equals(mode) ? "Wyciągnij −1" : "Dodaj +1",
                (d,w) -> commitPantryBarcode(barcode, null, mode, operationId))
            .setOnCancelListener(d -> finishPantryBatch()).show();
    }


    /** A second barcode can refer to an item already stored offline. */
    private void choosePantryProductForCode(String barcode, String operationId) {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        java.util.ArrayList<String> captions = new java.util.ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT id,name,qty FROM pantry ORDER BY name COLLATE NOCASE LIMIT 250",
                null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                int quantity = c.getInt(2);
                PantryPackageStore.Pack pack = PantryPackageStore.find(
                    db.getReadableDatabase(), id);
                ids.add(id);
                captions.add(name + "\n" + PantryPackageRules.summary(quantity,
                    pack.unit, pack.sizeMilli));
            }
        }
        if (ids.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Nie masz jeszcze produktów")
                .setMessage("Wyszukaj kod w katalogach albo dodaj produkt ręcznie.")
                .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
                .setPositiveButton("Szukaj w bazach", (d,w) ->
                    lookupPantryProduct(barcode, operationId))
                .setOnCancelListener(d -> finishPantryBatch()).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Wybierz swój produkt")
            .setMessage("Powiąż zeskanowany kod z produktem, który masz już w spiżarni. "
                + "Nazwa i ilość zostaną zachowane; po zatwierdzeniu dodam 1 opakowanie.")
            .setItems(captions.toArray(new String[0]), (d,which) ->
                confirmExistingPantryCode(barcode, operationId, ids.get(which)))
            .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
            .setOnCancelListener(d -> finishPantryBatch()).show();
    }

    private void confirmExistingPantryCode(String barcode,
            String operationId, long pantryId) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT name,qty FROM pantry WHERE id=?",
                new String[]{Long.toString(pantryId)})) {
            if (!c.moveToFirst()) {
                finishPantryBatch();
                alert("Wybrany produkt już nie istnieje.");
                return;
            }
            String name = c.getString(0);
            int qty = c.getInt(1);
            PantryPackageStore.Pack pack = PantryPackageStore.find(
                db.getReadableDatabase(), pantryId);
            new AlertDialog.Builder(this).setTitle("Przypisz kod i dodaj +1")
                .setMessage(name + "\n" + PantryPackageRules.summary(
                    qty, pack.unit, pack.sizeMilli) + "\nNowy kod: " + barcode
                    + "\n\nPotwierdź, że to dokładnie ten sam produkt i opakowanie.")
                .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
                .setPositiveButton("Powiąż i dodaj +1", (d,w) ->
                    commitPantryBarcode(barcode, null, "ADD", operationId, null,
                        null, pack.unit, pack.sizeMilli, pantryId))
                .setOnCancelListener(d -> finishPantryBatch()).show();
        }
    }

    private void lookupPantryProduct(String barcode, String operationId) {
        java.util.concurrent.atomic.AtomicBoolean cancelled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        AlertDialog loading = new AlertDialog.Builder(this)
            .setTitle("Bazy Open Facts")
            .setMessage("Szukam nazwy i zdjęcia dla kodu " + barcode + "…")
            .setNegativeButton("Anuluj", (d,w) -> {
                cancelled.set(true);
                finishPantryBatch();
            })
            .create();
        loading.setOnCancelListener(d -> {
            cancelled.set(true);
            finishPantryBatch();
        });
        loading.show();
        new Thread(() -> {
            PantryProductLookup.Report report = null;
            Exception failure = null;
            try {
                report = PantryProductLookup.lookupDetailed(barcode,
                    (catalogue, status) -> runOnUiThread(() -> {
                        if (!cancelled.get() && loading.isShowing())
                            loading.setMessage("Przeszukuję katalogi Open Facts…\n"
                                + catalogue + ": " + status);
                    }));
                PantryProductLookup.Product product = report.product;
                if (product != null && product.image != null
                        && !product.imageUrl.isEmpty()) {
                    try {
                        PantryProductLookup.cache(this, product.imageUrl, product.image);
                    } catch (Exception photoError) {
                        DiagnosticLog.error("PANTRY_OFF_PHOTO_CACHE", photoError);
                    }
                }
            } catch (Exception error) {
                failure = error;
            }
            final PantryProductLookup.Report checked = report;
            final Exception problem = failure;
            runOnUiThread(() -> {
                loading.dismiss();
                if (cancelled.get() || isFinishing() || isDestroyed()) return;
                if (problem != null || checked == null
                        || checked.partialFailure && checked.product == null) {
                    if (problem != null)
                        DiagnosticLog.error("PANTRY_OFF_LOOKUP", problem);
                    String details = checked == null ? "Nie udało się rozpocząć wyszukiwania."
                        : checked.details;
                    new AlertDialog.Builder(this).setTitle("Nie wszystkie bazy odpowiedziały")
                        .setMessage("Nie mogę potwierdzić, że kodu nie ma w bazach. "
                            + "Stan spiżarni nie został zmieniony.\n\n"
                            + details + "\n\nMożesz spróbować później albo wpisać nazwę ręcznie.")
                        .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
                        .setNeutralButton("Szukaj po nazwie", (d,w) ->
                            promptPantryNameSearch(barcode, operationId))
                        .setPositiveButton("Wpisz ręcznie", (d,w) ->
                            showNewPantryProductDialog(barcode, operationId, null))
                        .setOnCancelListener(d -> finishPantryBatch()).show();
                } else if (checked.product == null) {
                    new AlertDialog.Builder(this).setTitle("Brak nazwy w sprawdzonych katalogach")
                        .setMessage("Sprawdzono także inne zapisy UPC/EAN tego kodu.\n\n"
                            + checked.details + "\n\nStan nie został zmieniony. "
                            + "Możesz wpisać nazwę ręcznie.")
                        .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
                        .setNeutralButton("Szukaj po nazwie", (d,w) ->
                            promptPantryNameSearch(barcode, operationId))
                        .setPositiveButton("Wpisz ręcznie", (d,w) ->
                            showNewPantryProductDialog(barcode, operationId, null))
                        .setOnCancelListener(d -> finishPantryBatch()).show();
                } else {
                    DiagnosticLog.event("PANTRY_OPEN_FACTS_FOUND");
                    showNewPantryProductDialog(barcode, operationId,
                        checked.product, checked.details);
                }
            });
        }, "edhome-off-lookup").start();
    }


    /** Search after GTIN lookup fails. Query is never treated as a confirmed name. */
    private void promptPantryNameSearch(String scannedBarcode, String operationId) {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("np. proszek do prania, marka");
        new AlertDialog.Builder(this).setTitle("Szukaj produktu po nazwie")
            .setMessage("Wyślij wpisaną frazę do katalogów Open Facts. "
                + "Wybór podobnego produktu nie potwierdza jego zgodności z kodem — "
                + "sprawdź nazwę na opakowaniu.")
            .setView(query)
            .setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
            .setNeutralButton("Wpisz ręcznie", (d,w) ->
                showNewPantryProductDialog(scannedBarcode, operationId, null))
            .setPositiveButton("Szukaj", (d,w) ->
                searchPantryName(scannedBarcode, operationId,
                    query.getText().toString()))
            .setOnCancelListener(d -> finishPantryBatch()).show();
    }

    private void searchPantryName(String scannedBarcode, String operationId,
            String rawQuery) {
        String query = rawQuery.trim();
        if (query.length() < 3 || query.length() > 80) {
            alert("Wpisz od 3 do 80 znaków nazwy produktu.");
            promptPantryNameSearch(scannedBarcode, operationId);
            return;
        }
        java.util.concurrent.atomic.AtomicBoolean cancelled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        AlertDialog loading = new AlertDialog.Builder(this)
            .setTitle("Szukam po nazwie")
            .setMessage("Sprawdzam katalogi produktów…")
            .setNegativeButton("Anuluj", (d,w) -> {
                cancelled.set(true);
                finishPantryBatch();
            }).create();
        loading.setOnCancelListener(d -> {
            cancelled.set(true);
            finishPantryBatch();
        });
        loading.show();
        new Thread(() -> {
            PantryProductLookup.NameSearchReport report = null;
            Exception failure = null;
            try {
                report = PantryProductLookup.searchByName(query,
                    (catalogue, status) -> runOnUiThread(() -> {
                        if (!cancelled.get() && loading.isShowing())
                            loading.setMessage(catalogue + ": " + status);
                    }));
            } catch (Exception problem) {
                failure = problem;
            }
            final PantryProductLookup.NameSearchReport checked = report;
            final Exception problem = failure;
            runOnUiThread(() -> {
                loading.dismiss();
                if (cancelled.get() || isFinishing() || isDestroyed()) return;
                if (problem != null || checked == null) {
                    DiagnosticLog.error("PANTRY_NAME_SEARCH", problem == null
                        ? new IllegalStateException("Empty search report") : problem);
                    new AlertDialog.Builder(this).setTitle("Nie udało się wyszukać")
                        .setMessage("Możesz wpisać produkt ręcznie. Stan jest bez zmian.")
                        .setNegativeButton("Zakończ", (d,w) -> finishPantryBatch())
                        .setPositiveButton("Wpisz ręcznie", (d,w) ->
                            showNewPantryProductDialog(scannedBarcode, operationId, null))
                        .setOnCancelListener(d -> finishPantryBatch()).show();
                    return;
                }
                if (checked.products.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Brak wyników po nazwie")
                        .setMessage(checked.details + "\n\nNie znaleziono propozycji. "
                            + "Możesz spróbować innej frazy lub wpisać nazwę ręcznie.")
                        .setNegativeButton("Zakończ", (d,w) -> finishPantryBatch())
                        .setNeutralButton("Szukaj ponownie", (d,w) ->
                            promptPantryNameSearch(scannedBarcode, operationId))
                        .setPositiveButton("Wpisz ręcznie", (d,w) ->
                            showNewPantryProductDialog(scannedBarcode, operationId, null))
                        .setOnCancelListener(d -> finishPantryBatch()).show();
                    return;
                }
                String[] options = new String[checked.products.size()];
                for (int i = 0; i < options.length; i++) {
                    PantryProductLookup.Product candidate = checked.products.get(i);
                    options[i] = candidate.name + (candidate.brand.isEmpty()
                        ? "" : " • " + candidate.brand) + "\n" + candidate.source;
                }
                new AlertDialog.Builder(this).setTitle("Wybierz zgodny produkt")
                    .setMessage("Wyniki są propozycjami po nazwie. "
                        + "Sprawdź markę i wariant z opakowaniem.")
                    .setItems(options, (d, which) ->
                        previewNameSearchProduct(scannedBarcode, operationId,
                            checked.products.get(which), checked.details))
                    .setNegativeButton("Zakończ", (d,w) -> finishPantryBatch())
                    .setNeutralButton("Szukaj ponownie", (d,w) ->
                        promptPantryNameSearch(scannedBarcode, operationId))
                    .setOnCancelListener(d -> finishPantryBatch()).show();
            });
        }, "edhome-name-search").start();
    }

    private void previewNameSearchProduct(String scannedBarcode,
            String operationId, PantryProductLookup.Product candidate,
            String sourceDetails) {
        // Fetching a photo is optional; it must not prevent selecting a named product.
        new Thread(() -> {
            byte[] image = null;
            if (!candidate.imageUrl.isEmpty()) {
                try {
                    image = PantryProductLookup.fetchImage(candidate.imageUrl);
                    PantryProductLookup.cache(this, candidate.imageUrl, image);
                } catch (Exception ignored) {
                    DiagnosticLog.event("PANTRY_NAME_SEARCH_PHOTO_UNAVAILABLE");
                }
            }
            final PantryProductLookup.Product selected =
                new PantryProductLookup.Product(candidate.name, candidate.source,
                    candidate.brand, candidate.imageUrl, image);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed())
                    showNewPantryProductDialog(scannedBarcode, operationId,
                        selected, sourceDetails + "\nDopasowano po nazwie; "
                            + "sprawdź zgodność z kodem na opakowaniu.");
            });
        }, "edhome-name-search-photo").start();
    }

    /** No stock change until this confirmation, even if Open Food Facts responded. */
    private void showNewPantryProductDialog(String barcode, String operationId,
            PantryProductLookup.Product found) {
        showNewPantryProductDialog(barcode, operationId, found, "");
    }

    private void showNewPantryProductDialog(String barcode, String operationId,
            PantryProductLookup.Product found, String sourceDetails) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        if (found != null && found.image != null) {
            Bitmap thumbnail = PantryProductLookup.thumbnail(found.image);
            if (thumbnail != null) {
                ImageView picture = new ImageView(this);
                int px = (int) (120 * getResources().getDisplayMetrics().density);
                picture.setLayoutParams(new LinearLayout.LayoutParams(px, px));
                picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
                picture.setImageBitmap(thumbnail);
                form.addView(picture);
            }
        }
        EditText name = null;
        if (found == null) {
            name = new EditText(this);
            name.setSingleLine(true);
            name.setHint("Nazwa produktu / opakowania");
            form.addView(name);
        } else {
            form.addView(text(found.name, 18, true));
            if (!found.brand.isEmpty())
                form.addView(text("Marka: " + found.brand, 14, false));
        }
        form.addView(text(found == null
            ? "Kod: " + barcode + " • nazwa ręczna, offline"
            : "Źródło: " + found.source + " • " + barcode
                + " • sprawdź zgodność z opakowaniem.", 13, false));
        if (!sourceDetails.isEmpty())
            form.addView(text("Sprawdzone katalogi:\n" + sourceDetails, 12, false));
        final EditText manualName = name;
        final Spinner categorySpinner = pantryCategorySpinner(
            found == null ? "other" : PantryCategories.fromSource(found.source));
        form.addView(text("Kategoria produktu", 14, false));
        form.addView(categorySpinner);
        form.addView(text("Zawartość jednego opakowania", 14, false));
        final Spinner packUnit = pantryPackageUnitSpinner("szt.");
        final EditText packSize = new EditText(this);
        packSize.setSingleLine(true);
        packSize.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        packSize.setText("1");
        packSize.setHint("np. 0,5");
        form.addView(packUnit);
        form.addView(packSize);
        new AlertDialog.Builder(this)
            .setTitle(found == null ? "Nowy produkt" : "Potwierdź produkt")
            .setView(form).setNegativeButton("Anuluj", (d,w) -> finishPantryBatch())
            .setPositiveButton("Dodaj +1", (d,w) -> {
                String entered = found != null ? found.name
                    : manualName.getText().toString().trim();
                if (entered.isEmpty() || entered.length() > 160) {
                    alert("Nazwa produktu musi mieć 1–160 znaków.");
                    return;
                }
                String unit = PantryPackageRules.UNITS[
                    packUnit.getSelectedItemPosition()];
                long sizeMilli;
                try {
                    sizeMilli = PantryPackageRules.parse(
                        packSize.getText().toString(), unit);
                } catch (IllegalArgumentException wrong) {
                    alert(wrong.getMessage());
                    return;
                }
                commitPantryBarcode(barcode, entered, "ADD", operationId,
                    found, PantryCategories.IDS[categorySpinner.getSelectedItemPosition()],
                    unit, sizeMilli);
            }).setOnCancelListener(d -> finishPantryBatch()).show();
    }

    private void refreshPantryPhoto(String imageUrl) {
        if (!PantryProductLookup.safeImageUrl(imageUrl)) return;
        new Thread(() -> {
            try {
                byte[] data = PantryProductLookup.fetchImage(imageUrl);
                PantryProductLookup.cache(this, imageUrl, data);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        DiagnosticLog.event("PANTRY_OFF_PHOTO_CACHED");
                        if ("pantry".equals(screen)) render();
                    }
                });
            } catch (Exception problem) {
                DiagnosticLog.error("PANTRY_OFF_PHOTO", problem);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        alert("Nie udało się pobrać zdjęcia. Produkt nadal działa offline.");
                });
            }
        }, "edhome-off-photo").start();
    }

    private void commitPantryBarcode(String barcode, String name,
            String mode, String operationId) {
        commitPantryBarcode(barcode, name, mode, operationId, null);
    }

    private void commitPantryBarcode(String barcode, String name,
            String mode, String operationId, PantryProductLookup.Product found) {
        commitPantryBarcode(barcode, name, mode, operationId, found, null);
    }

    private void commitPantryBarcode(String barcode, String name,
            String mode, String operationId, PantryProductLookup.Product found,
            String newCategory) {
        commitPantryBarcode(barcode, name, mode, operationId, found, newCategory,
            "szt.", 1000);
    }

    private void commitPantryBarcode(String barcode, String name,
            String mode, String operationId, PantryProductLookup.Product found,
            String newCategory, String unit, long sizeMilli) {
        commitPantryBarcode(barcode, name, mode, operationId, found, newCategory,
            unit, sizeMilli, 0L);
    }

    private void commitPantryBarcode(String barcode, String name,
            String mode, String operationId, PantryProductLookup.Product found,
            String newCategory, String unit, long sizeMilli, long selectedPantryId) {
        try {
            String result = PantryBarcodeStore.commit(db.getWritableDatabase(),
                barcode, name, mode, operationId, unit, sizeMilli, selectedPantryId);
            DiagnosticLog.event("COMMITTED".equals(result) ?
                "PANTRY_SCAN_COMMITTED" : "PANTRY_SCAN_DUPLICATE_IGNORED");
            if (newCategory != null && "COMMITTED".equals(result)) {
                try {
                    PantryBarcodeStore.Item product = PantryBarcodeStore.find(
                        db.getReadableDatabase(), barcode);
                    if (product != null) {
                        PantryCategoriesStore.setIfOther(db.getWritableDatabase(),
                            product.id, newCategory);
                        if (found != null) PantryBarcodeStore.saveDetails(
                            db.getWritableDatabase(), product.id,
                            found.brand, found.imageUrl);
                    }
                } catch (Exception detailsError) {
                    DiagnosticLog.error("PANTRY_CATEGORY_DETAILS", detailsError);
                }
            }
            if (pantryBatch.active()) {
                if ("COMMITTED".equals(result) && pantryBatch.commit(barcode)) {
                    DiagnosticLog.event("PANTRY_BATCH_ITEM_COMMITTED");
                    render();
                    continuePantryBatch();
                } else finishPantryBatch();
            } else render();
        } catch (Exception problem) {
            finishPantryBatch();
            DiagnosticLog.error("PANTRY_SCAN_COMMIT", problem);
            alert(problem.getMessage() == null ? "Nie udało się zapisać skanu."
                : problem.getMessage());
        }
    }

    private void showPantryScanHistory() {
        StringBuilder history = new StringBuilder();
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT name_snapshot,kind,before_qty,after_qty "
                + "FROM pantry_movements ORDER BY id DESC LIMIT 30", null)) {
            while (cursor.moveToNext()) {
                history.append("TAKE".equals(cursor.getString(1)) ? "−1 opak. " : "+1 opak. ")
                    .append(cursor.getString(0)).append(" • ")
                    .append(cursor.getInt(2)).append(" → ").append(cursor.getInt(3))
                    .append(" opak.\n");
            }
        }
        new AlertDialog.Builder(this).setTitle("Ostatnie skany")
            .setMessage(history.length() == 0 ? "Brak skanów." : history.toString())
            .setPositiveButton("Zamknij", null).show();
    }

    private Spinner pantryCategorySpinner(String category) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, PantryCategories.LABELS);
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int initial = java.util.Arrays.asList(PantryCategories.IDS)
            .indexOf(category);
        spinner.setSelection(Math.max(0, initial));
        return spinner;
    }

    private Spinner pantryPackageUnitSpinner(String initialUnit) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, PantryPackageRules.UNITS);
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int chosen = java.util.Arrays.asList(PantryPackageRules.UNITS)
            .indexOf(initialUnit);
        spinner.setSelection(Math.max(0, chosen));
        return spinner;
    }

    private void pantryProductDialog(Long id, String existingName) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(existingName);
        input.setHint("Nazwa produktu");
        form.addView(input);
        String oldCategory = id == null ? "other"
            : PantryCategoriesStore.find(db.getReadableDatabase(), id);
        form.addView(text("Kategoria", 14, false));
        Spinner category = pantryCategorySpinner(oldCategory);
        form.addView(category);
        PantryPackageStore.Pack current = id == null
            ? new PantryPackageStore.Pack("szt.", 1000)
            : PantryPackageStore.find(db.getReadableDatabase(), id);
        form.addView(text("Zawartość jednego opakowania", 14, false));
        Spinner packUnit = pantryPackageUnitSpinner(current.unit);
        EditText packSize = new EditText(this);
        packSize.setSingleLine(true);
        packSize.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        packSize.setText(PantryPackageRules.format(current.sizeMilli));
        packSize.setHint("np. 0,5");
        form.addView(packUnit);
        form.addView(packSize);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(id == null ? "Dodaj do spiżarni" : "Edytuj produkt")
            .setView(form).setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                String categoryId = PantryCategories.IDS[category.getSelectedItemPosition()];
                if (name.isEmpty() || name.length() > 160) {
                    input.setError("Podaj nazwę (maks. 160 znaków).");
                    return;
                }
                String unit = PantryPackageRules.UNITS[
                    packUnit.getSelectedItemPosition()];
                long milli;
                try {
                    milli = PantryPackageRules.parse(packSize.getText().toString(), unit);
                    if (id == null) {
                        db.addStock(name, categoryId, unit, milli);
                        DiagnosticLog.event("PANTRY_PRODUCT_ADDED");
                    } else if (!db.editStock(id, name, categoryId, unit, milli)) {
                        input.setError("Produkt o tej nazwie już istnieje.");
                        return;
                    } else DiagnosticLog.event("PANTRY_PRODUCT_EDITED");
                } catch (IllegalArgumentException problem) {
                    packSize.setError(problem.getMessage());
                    return;
                }
                dialog.dismiss();
                render();
            }));
        dialog.show();
    }

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

    private String quietHoursStart() {
        String value = prefs.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String end = prefs.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        return QuietHoursRules.validWindow(value, end)
            ? value : QuietHoursRules.DEFAULT_START;
    }

    private String quietHoursEnd() {
        String start = prefs.getString("quiet_hours_start",
            QuietHoursRules.DEFAULT_START);
        String value = prefs.getString("quiet_hours_end",
            QuietHoursRules.DEFAULT_END);
        return QuietHoursRules.validWindow(start, value)
            ? value : QuietHoursRules.DEFAULT_END;
    }

    private void editQuietHours() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(10));
        form.addView(text("Cisza powiadomień", 21, true));
        form.addView(text("W tym czasie EDHOME nie pokazuje nowych "
            + "powiadomień czynności ani minutników. Minutniki i terminy "
            + "nadal biegną normalnie.", 13, false));
        final String[] start = {quietHoursStart()};
        final String[] end = {quietHoursEnd()};
        Button startButton = new Button(this);
        startButton.setAllCaps(false);
        startButton.setText("Początek: " + start[0]);
        startButton.setOnClickListener(v -> {
            java.time.LocalTime time = java.time.LocalTime.parse(start[0]);
            new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
                start[0] = String.format(java.util.Locale.ROOT,
                    "%02d:%02d", hour, minute);
                startButton.setText("Początek: " + start[0]);
            }, time.getHour(), time.getMinute(), true).show();
        });
        form.addView(startButton);
        Button endButton = new Button(this);
        endButton.setAllCaps(false);
        endButton.setText("Koniec: " + end[0]);
        endButton.setOnClickListener(v -> {
            java.time.LocalTime time = java.time.LocalTime.parse(end[0]);
            new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
                end[0] = String.format(java.util.Locale.ROOT,
                    "%02d:%02d", hour, minute);
                endButton.setText("Koniec: " + end[0]);
            }, time.getHour(), time.getMinute(), true).show();
        });
        form.addView(endButton);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null)
            .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawable(rounded(surface));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!QuietHoursRules.validWindow(start[0], end[0])) {
                        alert("Godziny ciszy muszą tworzyć okno przez północ, "
                            + "np. 22:00–07:00.");
                        return;
                    }
                    prefs.edit()
                        .putString("quiet_hours_start", start[0])
                        .putString("quiet_hours_end", end[0]).apply();
                    ReminderReceiver.schedule(this);
                    DeviceTimerReceiver.scheduleAll(this);
                    DiagnosticLog.event("QUIET_HOURS_CHANGED");
                    dialog.dismiss();
                    render();
                });
        });
        dialog.show();
    }

    private void settings() {
        header("Ustawienia");
        note("Aktywny styl: " + skin.name
            + " • sześć wariantów tej samej aplikacji bez zmiany danych.");
        note("Wybierz styl i sprawdź go od razu. Zmiana działa dla "
            + "całej aplikacji i pozostaje po ponownym uruchomieniu.");
        String[] descriptions = {
            "Ciemny granat • mięta • wyraźne kafle",
            "Leśna zieleń • ciepłe, naturalne akcenty",
            "Jasny krem • łagodne kolory • wysoki kontrast",
            "Głęboki błękit • szklane karty • subtelny połysk",
            "WMM • warsztatowy grafit • turkusowe akcenty",
            "Trener 2 • głęboka czerń • czerwone akcenty"
        };
        for (int i = 0; i < UiSkin.THEMES.length; i++) {
            String theme = UiSkin.THEMES[i];
            String description = descriptions[i];
            LinearLayout example = card();
            example.addView(text((skin.name.equals(theme) ? "✓  " : "")
                + theme, 18, true));
            example.addView(text(description, 13, false));
            smallButton(example, "Wybierz motyw " + theme, () -> {
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
        LinearLayout quiet = card();
        quiet.addView(text("Cisza powiadomień", 19, true));
        quiet.addView(text("Aktualnie: " + quietHoursStart() + "–"
            + quietHoursEnd(), 15, true));
        quiet.addView(text("Dotyczy przypomnień czynności i minutników. "
            + "Okno musi przechodzić przez północ, np. 22:00–07:00.",
            13, false));
        smallButton(quiet, "Zmień godziny ciszy", this::editQuietHours);
        smallButton(quiet, "Przywróć 22:00–07:00", () -> {
            prefs.edit()
                .putString("quiet_hours_start", QuietHoursRules.DEFAULT_START)
                .putString("quiet_hours_end", QuietHoursRules.DEFAULT_END)
                .apply();
            ReminderReceiver.schedule(this);
            DeviceTimerReceiver.scheduleAll(this);
            DiagnosticLog.event("QUIET_HOURS_RESET");
            render();
        });
        note("Standardowo przypomnienia przychodzą około 09:00. "
            + "Dla poszczególnych czynności ustawisz godzinę i wyprzedzenie "
            + "w ich edycji. Aktualna cisza: " + quietHoursStart() + "–"
            + quietHoursEnd() + ". Android może opóźnić alarm przez "
            + "oszczędzanie baterii. Tytuły czynności nie pojawiają się "
            + "na ekranie blokady.");
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

    /** Three rounded tiles per row; the outer page owns vertical scrolling. */
    private LinearLayout tileGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(6), 0, dp(12));
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        return grid;
    }

    /** Non-home action tiles keep the legacy five-argument signature. */
    private LinearLayout updateTile(LinearLayout grid, String symbol,
            String caption, boolean primary, Runnable callback) {
        return updateTile(grid, "", symbol, caption, primary, callback);
    }

    private LinearLayout updateTile(LinearLayout grid, String id, String symbol,
            String caption, boolean primary, Runnable callback) {
        boolean isHome = "home".equals(screen);
        int span = isHome && "double".equals(
            prefs.getString("tile_width_" + id, "small")) ? 2 : 1;
        LinearLayout row = grid.getChildCount() == 0
            ? null : (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        int used = row == null || !(row.getTag() instanceof Integer)
            ? 0 : (Integer) row.getTag();
        if (row == null || used + span > 3) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setTag(0);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(9));
            grid.addView(row, rowParams);
            used = 0;
        }
        row.setTag(used + span);

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        // Page margin 16dp each; two gaps 9dp. Keep three visible columns.
        int widthSide = (width - dp(50)) / 3;
        int heightSide = (height - dp(340)) / 3;
        int side = Math.max(dp(78), Math.min(widthSide, heightSide));
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(3), dp(4), dp(5));

        boolean customTint = false;
        int tileTint = accent;
        if (isHome) {
            String selected = prefs.getString("tile_tint_" + id, "default");
            customTint = !"default".equals(selected);
            if (customTint) tileTint = skin.tileTint(selected);
        }
        boolean highlighted = primary || customTint;
        tile.setBackground(skin.tile(this, highlighted, tileTint));
        tile.setElevation(dp(highlighted ? 5 : 3));
        tile.setOnClickListener(v -> callback.run());
        touchFeedback(tile);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(caption.replace("\n", " "));
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(side * span + dp(9) * (span - 1), side);
        if (row.getChildCount() > 0) params.setMargins(dp(9), 0, 0, 0);
        row.addView(tile, params);

        if (isHome) {
            TextView handle = text("⋮⋮", 17, true);
            handle.setTextColor(highlighted ? skin.tileText(tileTint) : subdued);
            handle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            handle.setPadding(dp(3), 0, dp(8), 0);
            handle.setContentDescription("Edytuj lub przeciągnij " + caption);
            tile.addView(handle, new LinearLayout.LayoutParams(-1, dp(19)));
            handle.setOnClickListener(v -> {
                Object tileTag = tile.getTag();
                if (tileTag instanceof String)
                    showTileActions(tile, (String) tileTag);
            });
            handle.setOnTouchListener(new View.OnTouchListener() {
                private float startX, startY;
                private boolean dragging;
                @Override public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            startY = event.getRawY();
                            dragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (!dragging && (Math.abs(event.getRawX() - startX)
                                    > ViewConfiguration.get(MainActivity.this)
                                        .getScaledTouchSlop()
                                    || Math.abs(event.getRawY() - startY)
                                    > ViewConfiguration.get(MainActivity.this)
                                        .getScaledTouchSlop())) {
                                Object tileTag = tile.getTag();
                                if (tileTag instanceof String)
                                    dragging = beginHomeDrag(tile, (String) tileTag);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!dragging) v.performClick();
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default: return false;
                    }
                }
            });
        }

        LinearLayout.LayoutParams iconParams =
            new LinearLayout.LayoutParams(dp(46), dp(46));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        if (isHome) {
            String target = homeTileTarget(id);
            String iconId = prefs.getString("tile_icon_" + id,
                HomeTileCatalog.icon(target));
            if (!TileIcon.known(iconId))
                iconId = HomeTileCatalog.icon(target);
            TileIcon pictogram = new TileIcon(this, iconId,
                highlighted && skin.light ? skin.accentInk : accent);
            pictogram.setPadding(dp(5), dp(5), dp(5), dp(5));
            pictogram.setBackground(skin.panel(this, skin.iconBacking, 24));
            tile.addView(pictogram, iconParams);
        } else {
            TextView pictogram = text(symbol, 29, true);
            pictogram.setTextColor(highlighted && skin.light
                ? skin.accentInk : accent);
            pictogram.setGravity(Gravity.CENTER);
            pictogram.setBackground(skin.panel(this, skin.iconBacking, 24));
            tile.addView(pictogram, iconParams);
        }
        TextView captionView = text(caption, 12, true);
        captionView.setTextColor(highlighted
            ? skin.tileText(tileTint) : ink);
        captionView.setGravity(Gravity.CENTER);
        captionView.setMaxLines(2);
        captionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(captionView,
            new LinearLayout.LayoutParams(-1, dp(31)));
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
        IntentResult scan = IntentIntegrator.parseActivityResult(request, result, data);
        if (scan != null) {
            if (storageQrCameraPending) {
                storageQrCameraPending = false;
                if (scan.getContents() != null) openStorageQr(scan.getContents());
                return;
            }
            if (pantryBatch.active()) {
                if (!pantryBatch.receiveScan(scan.getContents())) {
                    if (scan.getContents() == null) finishPantryBatch();
                    else DiagnosticLog.event("PANTRY_BATCH_DUPLICATE_RESULT_IGNORED");
                    return;
                }
                onPantryBarcode(scan.getContents(), pantryBatch.mode());
            } else if (pantrySingleCameraPending) {
                pantrySingleCameraPending = false;
                if (scan.getContents() != null) onPantryBarcode(scan.getContents(),
                    prefs.getString(SCAN_MODE_PREF, "ADD"));
            } else DiagnosticLog.event("PANTRY_UNEXPECTED_CAMERA_RESULT_IGNORED");
            return;
        }
        if (request == IMPORT_BETA_APK) {
            if (result == RESULT_OK && data != null && data.getData() != null)
                updater.importSelected(data.getData());
            return;
        }
        if (request == EXPORT_PRIVATE_BACKUP) {
            String encrypted = privateBackupForSave;
            privateBackupForSave = null;
            if (result != RESULT_OK || data == null || data.getData() == null
                    || encrypted == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IllegalStateException("Brak dostępu do pliku.");
                out.write(encrypted.getBytes(StandardCharsets.UTF_8));
                out.flush();
                DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_EXPORTED");
                alert("Zapisano ZASZYFROWANĄ kopię prywatną. "
                    + "Zachowaj hasło kopii i sprawdź, czy plik istnieje.");
            } catch (Exception error) {
                DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_EXPORT_FAILED");
                alert("Nie zapisano kopii prywatnej. Nie odinstalowuj aplikacji.");
            }
            return;
        }
        if (request == IMPORT_PRIVATE_BACKUP) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            try (InputStream in = getContentResolver().openInputStream(data.getData());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IllegalStateException("Brak dostępu do pliku.");
                byte[] block = new byte[8192];
                int n;
                while ((n = in.read(block)) != -1) {
                    if (out.size() + n > PrivatePaycheckPortable.MAX_BYTES)
                        throw new IllegalArgumentException("Plik zbyt duży.");
                    out.write(block, 0, n);
                }
                privateBackupImportDialog(new String(
                    out.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception error) {
                DiagnosticLog.event("PAYCHECK_PRIVATE_BACKUP_READ_FAILED");
                alert("Nie można odczytać zaszyfrowanej kopii prywatnej.");
            }
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
                            // Imported IDs can refer to different tasks/occurrences.
                            // Reset only reminder delivery receipts, never user settings.
                            SharedPreferences.Editor reminderReset = prefs.edit();
                            for (String key : prefs.getAll().keySet()) {
                                if (key.startsWith("reminder_fired_")
                                        || key.startsWith("reminder_custom_day_")
                                        || "reminder_legacy_day".equals(key)
                                        || key.startsWith("timer_notified_"))
                                    reminderReset.remove(key);
                            }
                            reminderReset.apply();
                            ReminderReceiver.schedule(this);
                            DeviceTimerReceiver.scheduleAll(this);
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
            super(context, "edhome-beta-preview.db", null, 22);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, "
                + "due_date TEXT, repeat_rule TEXT NOT NULL DEFAULT 'once', "
                + "repeat_every INTEGER NOT NULL DEFAULT 1, place_id INTEGER, "
                + "priority TEXT NOT NULL DEFAULT 'normal', "
                + "duration_minutes INTEGER NOT NULL DEFAULT 30, "
                + "assignee_id INTEGER, "
                + "task_kind TEXT NOT NULL DEFAULT 'general', waste_fraction TEXT, "
                + "remind_time TEXT, reminder_lead_days INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0, "
                + "category TEXT NOT NULL DEFAULT 'other' CHECK(category IN "
                + "('other','food','household','beauty','pet')))");
            addAuditTables(database);
            addTaskHistory(database);
            addPlaces(database);
            addMembers(database);
            addMemberSchedules(database);
            addShopping(database);
            ShoppingReceiptStore.create(database);
            StorageStore.createTables(database);
            PaycheckStore.create(database);
            PaycheckGoalsStore.create(database);
            PantryPriceHistoryStore.create(database);
            addDeviceTimers(database);
            addTaskRotations(database);
            PantryBarcodeStore.createTables(database);
            PantryBarcodeStore.createDetails(database);
            PantryPackageStore.create(database);
            DiagnosticLog.event("DATABASE_CREATED");
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion < 1 || newVersion > 22) {
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
            if (oldVersion < 6) {
                addMembers(database);
                database.execSQL("ALTER TABLE tasks ADD COLUMN assignee_id INTEGER");
                DiagnosticLog.event("DATABASE_MIGRATED_5_TO_6");
            }
            if (oldVersion < 7) {
                addMemberSchedules(database);
                DiagnosticLog.event("DATABASE_MIGRATED_6_TO_7");
            }
            if (oldVersion < 8) {
                addShopping(database);
                DiagnosticLog.event("DATABASE_MIGRATED_7_TO_8");
            }
            if (oldVersion < 9) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN task_kind TEXT NOT NULL DEFAULT 'general'");
                database.execSQL("ALTER TABLE tasks ADD COLUMN waste_fraction TEXT");
                DiagnosticLog.event("DATABASE_MIGRATED_8_TO_9");
            }
            if (oldVersion < 10) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN remind_time TEXT");
                database.execSQL("ALTER TABLE tasks ADD COLUMN reminder_lead_days "
                    + "INTEGER NOT NULL DEFAULT 0");
                DiagnosticLog.event("DATABASE_MIGRATED_9_TO_10");
            }
            if (oldVersion < 11 && oldVersion >= 4) {
                // The old global UNIQUE(name) must be replaced with sibling-scoped
                // uniqueness. Preserve IDs so task.place_id remains valid.
                database.execSQL("CREATE TABLE places_v11 (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL COLLATE NOCASE, kind TEXT NOT NULL DEFAULT '', "
                    + "parent_id INTEGER, icon TEXT NOT NULL DEFAULT 'places', "
                    + "CHECK(parent_id IS NULL OR parent_id!=id))");
                database.execSQL("INSERT INTO places_v11 (id,name,kind) "
                    + "SELECT id,name,kind FROM places");
                database.execSQL("DROP TABLE places");
                database.execSQL("ALTER TABLE places_v11 RENAME TO places");
                addPlaceSiblingIndex(database);
                DiagnosticLog.event("DATABASE_MIGRATED_10_TO_11_PLACES");
            }
            if (oldVersion < 12) {
                addDeviceTimers(database);
                DiagnosticLog.event("DATABASE_MIGRATED_11_TO_12_DEVICE_TIMERS");
            }
            if (oldVersion < 13) {
                addTaskRotations(database);
                database.execSQL("ALTER TABLE task_history ADD COLUMN assignee_id INTEGER");
                database.execSQL("ALTER TABLE task_history ADD COLUMN "
                    + "assignee_name_snapshot TEXT");
                DiagnosticLog.event("DATABASE_MIGRATED_12_TO_13_TASK_ROTATION");
            }
            if (oldVersion < 14) {
                PantryBarcodeStore.createTables(database);
                DiagnosticLog.event("DATABASE_MIGRATED_13_TO_14_PANTRY_BARCODES");
            }
            if (oldVersion < 15) {
                PantryBarcodeStore.createDetails(database);
                DiagnosticLog.event("DATABASE_MIGRATED_14_TO_15_PANTRY_DETAILS");
            }
            if (oldVersion < 16) {
                database.execSQL("ALTER TABLE pantry ADD COLUMN category TEXT "
                    + "NOT NULL DEFAULT 'other' CHECK(category IN "
                    + "('other','food','household','beauty','pet'))");
                DiagnosticLog.event("DATABASE_MIGRATED_15_TO_16_PANTRY_CATEGORIES");
            }
            if (oldVersion < 17) {
                PantryPackageStore.create(database);
                PantryPackageStore.fillLegacy(database);
                DiagnosticLog.event("DATABASE_MIGRATED_16_TO_17_PANTRY_PACKAGES");
            }
            if (oldVersion < 18) {
                ShoppingReceiptStore.create(database);
                DiagnosticLog.event("DATABASE_MIGRATED_17_TO_18_SHOPPING_RECEIPTS");
            }
            if (oldVersion < 19) {
                StorageStore.createTables(database);
                DiagnosticLog.event("DATABASE_MIGRATED_18_TO_19_STORAGE_QR");
            }
            if (oldVersion < 20) {
                PaycheckStore.create(database);
                DiagnosticLog.event("DATABASE_MIGRATED_19_TO_20_PAYCHECK_SHARED");
            }
            if (oldVersion < 21) {
                PaycheckGoalsStore.create(database);
                DiagnosticLog.event("DATABASE_MIGRATED_20_TO_21_PAYCHECK_GOALS");
            }
            if (oldVersion < 22) {
                PantryPriceHistoryStore.create(database);
                DiagnosticLog.event("DATABASE_MIGRATED_21_TO_22_PANTRY_PRICES");
            }
        }

        private static void addPlaceSiblingIndex(SQLiteDatabase database) {
            database.execSQL("CREATE UNIQUE INDEX places_siblings_idx "
                + "ON places (COALESCE(parent_id,0), name COLLATE NOCASE)");
        }

        private static void addPlaces(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL COLLATE NOCASE, kind TEXT NOT NULL DEFAULT '', "
                + "parent_id INTEGER, icon TEXT NOT NULL DEFAULT 'places', "
                + "CHECK(parent_id IS NULL OR parent_id!=id))");
            addPlaceSiblingIndex(database);
        }

        private static void addTaskHistory(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE task_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, "
                + "title_snapshot TEXT NOT NULL, completed_at INTEGER NOT NULL, "
                + "due_date TEXT, next_due_date TEXT, "
                + "assignee_id INTEGER, assignee_name_snapshot TEXT)");
            database.execSQL("CREATE INDEX task_history_task_idx ON task_history(task_id,id)");
        }


        private static void addTaskRotations(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE task_rotation_members ("
                + "task_id INTEGER NOT NULL, member_id INTEGER NOT NULL, "
                + "position INTEGER NOT NULL CHECK(position>=0), "
                + "PRIMARY KEY(task_id,member_id), UNIQUE(task_id,position))");
            database.execSQL("CREATE INDEX task_rotation_task_idx "
                + "ON task_rotation_members(task_id,position)");
        }

        private static void addShopping(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE shopping_items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                + "qty_milli INTEGER, unit TEXT NOT NULL DEFAULT 'szt.', "
                + "checked INTEGER NOT NULL DEFAULT 0)");
        }

        private static void addMemberSchedules(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE member_weekly_shifts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "member_id INTEGER NOT NULL, weekday INTEGER NOT NULL "
                + "CHECK(weekday BETWEEN 1 AND 7), shift TEXT NOT NULL, "
                + "UNIQUE(member_id,weekday))");
            database.execSQL("CREATE TABLE member_shift_exceptions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "member_id INTEGER NOT NULL, date TEXT NOT NULL, "
                + "shift TEXT NOT NULL, UNIQUE(member_id,date))");
        }

        private static void addMembers(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE household_members ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL COLLATE NOCASE UNIQUE)");
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
                Long placeId, String priority, int durationMinutes,
                Long assigneeId, String customTime, int reminderLeadDays,
                java.util.List<Long> rotationMembers) {
            String error = TaskRules.validate(title, dueDate, rule, every);
            if (error != null) throw new IllegalArgumentException(error);
            if (id != null && isWasteTask(id) && dueDate.isEmpty())
                throw new IllegalArgumentException("Odpady wymagają daty wystawienia.");
            if (customTime != null && (!ReminderRules.validTime(customTime)
                    || dueDate.isEmpty()
                    || !ReminderRules.allowedLead(reminderLeadDays)))
                throw new IllegalArgumentException("Nieprawidłowe przypomnienie.");
            String rotationError = RotationRules.validate(rotationMembers,
                TaskRules.recurring(rule));
            if (rotationError != null) throw new IllegalArgumentException(rotationError);
            if (rotationMembers != null && !rotationMembers.isEmpty()
                    && (assigneeId == null || !rotationMembers.contains(assigneeId)))
                throw new IllegalArgumentException(
                    "Aktualny wykonawca musi należeć do rotacji.");
            if (!java.util.Arrays.asList(TASK_PRIORITIES).contains(priority))
                throw new IllegalArgumentException("Nieznany priorytet czynności.");
            if (durationMinutes < MIN_TASK_MINUTES || durationMinutes > MAX_TASK_MINUTES)
                throw new IllegalArgumentException("Czas musi wynosić 1–480 minut.");
            ContentValues values = new ContentValues();
            values.put("priority", priority);
            values.put("duration_minutes", durationMinutes);
            if (customTime == null) values.putNull("remind_time");
            else values.put("remind_time", customTime);
            values.put("reminder_lead_days",
                customTime == null ? 0 : reminderLeadDays);
            if (assigneeId == null) values.putNull("assignee_id");
            else {
                try (Cursor person = getReadableDatabase().rawQuery(
                        "SELECT id FROM household_members WHERE id=?",
                        new String[]{Long.toString(assigneeId)})) {
                    if (!person.moveToFirst())
                        throw new IllegalArgumentException("Wykonawca nie istnieje.");
                }
                values.put("assignee_id", assigneeId);
            }
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
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                long taskId;
                if (id == null) {
                    taskId = database.insertOrThrow("tasks", null, values);
                } else {
                    taskId = id;
                    // Editing a completed one-off into a recurring task reopens it.
                    if (TaskRules.recurring(rule)) values.put("done", 0);
                    database.update("tasks", values, "id=?",
                        new String[]{Long.toString(id)});
                }
                database.delete("task_rotation_members", "task_id=?",
                    new String[]{Long.toString(taskId)});
                if (rotationMembers != null) {
                    for (int position = 0; position < rotationMembers.size(); position++) {
                        Long memberId = rotationMembers.get(position);
                        try (Cursor person = database.rawQuery(
                                "SELECT id FROM household_members WHERE id=?",
                                new String[]{Long.toString(memberId)})) {
                            if (!person.moveToFirst())
                                throw new IllegalArgumentException(
                                    "Osoba w rotacji już nie istnieje.");
                        }
                        ContentValues rotation = new ContentValues();
                        rotation.put("task_id", taskId);
                        rotation.put("member_id", memberId);
                        rotation.put("position", position);
                        database.insertOrThrow("task_rotation_members", null, rotation);
                    }
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        java.util.ArrayList<Long> taskRotation(long taskId) {
            java.util.ArrayList<Long> result = new java.util.ArrayList<>();
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT member_id FROM task_rotation_members WHERE task_id=? "
                    + "ORDER BY position ASC",
                    new String[]{Long.toString(taskId)})) {
                while (cursor.moveToNext()) result.add(cursor.getLong(0));
            }
            return result;
        }

        String rotationLabel(long taskId) {
            StringBuilder result = new StringBuilder();
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT m.name FROM task_rotation_members r "
                    + "JOIN household_members m ON m.id=r.member_id "
                    + "WHERE r.task_id=? ORDER BY r.position ASC",
                    new String[]{Long.toString(taskId)})) {
                while (cursor.moveToNext()) {
                    if (result.length() > 0) result.append(" → ");
                    result.append(cursor.getString(0));
                }
            }
            return result.toString();
        }

        boolean isWasteTask(long id) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT task_kind FROM tasks WHERE id=?",
                    new String[]{Long.toString(id)})) {
                return c.moveToFirst() && "waste".equals(c.getString(0));
            }
        }

        boolean createWasteTask(String fraction, String date, String rule,
                int every) {
            String error = WasteRules.validate(fraction, date, rule, every);
            if (error != null) throw new IllegalArgumentException(error);
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try (Cursor existing = database.rawQuery(
                    "SELECT id FROM tasks WHERE task_kind='waste' "
                    + "AND waste_fraction=? AND due_date=? AND done=0 LIMIT 1",
                    new String[]{fraction, date})) {
                if (existing.moveToFirst()) return false;
                ContentValues values = new ContentValues();
                values.put("title", "Wystaw: " + WasteRules.label(fraction));
                values.put("due_date", date);
                values.put("repeat_rule", rule);
                values.put("repeat_every", every);
                values.put("priority", "normal");
                values.put("duration_minutes", 10);
                values.put("task_kind", "waste");
                values.put("waste_fraction", fraction);
                database.insertOrThrow("tasks", null, values);
                database.setTransactionSuccessful();
                return true;
            } finally {
                database.endTransaction();
            }
        }

        void completeTask(long id) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try (Cursor cursor = database.rawQuery(
                    "SELECT title,done,due_date,repeat_rule,repeat_every,assignee_id "
                    + "FROM tasks WHERE id=?",
                    new String[]{Long.toString(id)})) {
                if (!cursor.moveToFirst() || cursor.getInt(1) != 0) return;
                String title = cursor.getString(0);
                String due = cursor.isNull(2) ? null : cursor.getString(2);
                String rule = cursor.getString(3);
                int every = cursor.getInt(4);
                Long completedBy = cursor.isNull(5) ? null : cursor.getLong(5);
                String completedByName = null;
                if (completedBy != null) {
                    try (Cursor person = database.rawQuery(
                            "SELECT name FROM household_members WHERE id=?",
                            new String[]{Long.toString(completedBy)})) {
                        if (person.moveToFirst()) completedByName = person.getString(0);
                    }
                }
                String next = TaskRules.recurring(rule)
                    ? TaskRules.nextDue(due, rule, every, LocalDate.now()) : null;
                ContentValues changed = new ContentValues();
                changed.put("done", next == null ? 1 : 0);
                if (next != null) {
                    changed.put("due_date", next);
                    java.util.ArrayList<Long> rotation = taskRotation(id);
                    if (rotation.size() >= 2) {
                        Long nextAssignee = RotationRules.next(rotation, completedBy);
                        if (nextAssignee == null) changed.putNull("assignee_id");
                        else changed.put("assignee_id", nextAssignee);
                    }
                }
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
                if (completedBy == null) history.putNull("assignee_id");
                else history.put("assignee_id", completedBy);
                if (completedByName == null) history.putNull("assignee_name_snapshot");
                else history.put("assignee_name_snapshot", completedByName);
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
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                database.delete("task_rotation_members", "task_id=?",
                    new String[]{Long.toString(id)});
                database.delete("tasks", "id=?", new String[]{Long.toString(id)});
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        String[] taskReminder(long taskId) {
            try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT remind_time,reminder_lead_days FROM tasks WHERE id=?",
                    new String[]{Long.toString(taskId)})) {
                if (!cursor.moveToFirst()) return new String[]{"", "0"};
                return new String[]{cursor.isNull(0) ? "" : cursor.getString(0),
                    Integer.toString(cursor.getInt(1))};
            }
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

        Long taskAssigneeId(long taskId) {
            try (Cursor person = getReadableDatabase().rawQuery(
                    "SELECT assignee_id FROM tasks WHERE id=?",
                    new String[]{Long.toString(taskId)})) {
                return person.moveToFirst() && !person.isNull(0)
                    ? person.getLong(0) : null;
            }
        }

        String assigneeLabel(long taskId) {
            try (Cursor person = getReadableDatabase().rawQuery(
                    "SELECT m.name FROM tasks t JOIN household_members m "
                    + "ON t.assignee_id=m.id WHERE t.id=?",
                    new String[]{Long.toString(taskId)})) {
                return person.moveToFirst() ? person.getString(0) : "";
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
            Long placeId = taskPlaceId(taskId);
            return placeId == null ? "" : placePath(placeId);
        }

        boolean addMember(String name) {
            String trimmed = name.trim();
            if (trimmed.isEmpty() || trimmed.length() > 80)
                throw new IllegalArgumentException("Nazwa osoby: 1–80 znaków.");
            ContentValues values = new ContentValues();
            values.put("name", trimmed);
            return getWritableDatabase().insertWithOnConflict(
                "household_members", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
        }

        void deleteMember(long memberId) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                java.util.ArrayList<Long> affectedRotations = new java.util.ArrayList<>();
                try (Cursor affected = database.rawQuery(
                        "SELECT DISTINCT task_id FROM task_rotation_members "
                        + "WHERE member_id=?", new String[]{Long.toString(memberId)})) {
                    while (affected.moveToNext())
                        affectedRotations.add(affected.getLong(0));
                }
                database.delete("task_rotation_members", "member_id=?",
                    new String[]{Long.toString(memberId)});
                ContentValues clear = new ContentValues();
                clear.putNull("assignee_id");
                database.update("tasks", clear, "assignee_id=?",
                    new String[]{Long.toString(memberId)});
                for (Long taskId : affectedRotations) {
                    java.util.ArrayList<Long> remaining = new java.util.ArrayList<>();
                    try (Cursor rows = database.rawQuery(
                            "SELECT member_id FROM task_rotation_members "
                            + "WHERE task_id=? ORDER BY position",
                            new String[]{Long.toString(taskId)})) {
                        while (rows.moveToNext()) remaining.add(rows.getLong(0));
                    }
                    if (remaining.size() < 2) {
                        database.delete("task_rotation_members", "task_id=?",
                            new String[]{Long.toString(taskId)});
                        if (remaining.size() == 1) {
                            ContentValues fixed = new ContentValues();
                            fixed.put("assignee_id", remaining.get(0));
                            database.update("tasks", fixed,
                                "id=? AND assignee_id IS NULL",
                                new String[]{Long.toString(taskId)});
                        }
                    } else {
                        try (Cursor current = database.rawQuery(
                                "SELECT assignee_id FROM tasks WHERE id=?",
                                new String[]{Long.toString(taskId)})) {
                            if (current.moveToFirst() && current.isNull(0)) {
                                ContentValues next = new ContentValues();
                                next.put("assignee_id", remaining.get(0));
                                database.update("tasks", next, "id=?",
                                    new String[]{Long.toString(taskId)});
                            }
                        }
                        // Re-number positions after removing a person.
                        database.delete("task_rotation_members", "task_id=?",
                            new String[]{Long.toString(taskId)});
                        for (int i = 0; i < remaining.size(); i++) {
                            ContentValues row = new ContentValues();
                            row.put("task_id", taskId);
                            row.put("member_id", remaining.get(i));
                            row.put("position", i);
                            database.insertOrThrow("task_rotation_members", null, row);
                        }
                    }
                }
                database.delete("member_weekly_shifts", "member_id=?",
                    new String[]{Long.toString(memberId)});
                database.delete("member_shift_exceptions", "member_id=?",
                    new String[]{Long.toString(memberId)});
                database.delete("household_members", "id=?",
                    new String[]{Long.toString(memberId)});
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        String memberName(long memberId) {
            try (Cursor person = getReadableDatabase().rawQuery(
                    "SELECT name FROM household_members WHERE id=?",
                    new String[]{Long.toString(memberId)})) {
                return person.moveToFirst() ? person.getString(0) : "";
            }
        }

        private void requireMember(long memberId) {
            if (memberName(memberId).isEmpty())
                throw new IllegalArgumentException("Domownik nie istnieje.");
        }

        private static void validateShift(String shift) {
            if (!java.util.Arrays.asList(SHIFT_VALUES).contains(shift))
                throw new IllegalArgumentException("Nieprawidłowy rodzaj zmiany.");
        }

        String weeklyShift(long memberId, int weekday) {
            if (weekday < 1 || weekday > 7)
                throw new IllegalArgumentException("Nieprawidłowy dzień tygodnia.");
            try (Cursor shift = getReadableDatabase().rawQuery(
                    "SELECT shift FROM member_weekly_shifts "
                    + "WHERE member_id=? AND weekday=?",
                    new String[]{Long.toString(memberId), Integer.toString(weekday)})) {
                return shift.moveToFirst() ? shift.getString(0) : "unset";
            }
        }

        void setWeeklyShift(long memberId, int weekday, String value) {
            requireMember(memberId);
            validateShift(value);
            if (weekday < 1 || weekday > 7)
                throw new IllegalArgumentException("Nieprawidłowy dzień tygodnia.");
            SQLiteDatabase database = getWritableDatabase();
            if ("unset".equals(value)) {
                database.delete("member_weekly_shifts",
                    "member_id=? AND weekday=?",
                    new String[]{Long.toString(memberId), Integer.toString(weekday)});
                return;
            }
            ContentValues values = new ContentValues();
            values.put("member_id", memberId);
            values.put("weekday", weekday);
            values.put("shift", value);
            database.insertWithOnConflict("member_weekly_shifts", null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        String effectiveShift(long memberId, String date) {
            LocalDate parsed = LocalDate.parse(date);
            try (Cursor shift = getReadableDatabase().rawQuery(
                    "SELECT shift FROM member_shift_exceptions "
                    + "WHERE member_id=? AND date=?",
                    new String[]{Long.toString(memberId), date})) {
                if (shift.moveToFirst()) return shift.getString(0);
            }
            return weeklyShift(memberId, parsed.getDayOfWeek().getValue());
        }

        void setShiftException(long memberId, String date, String shift) {
            requireMember(memberId);
            LocalDate.parse(date);
            validateShift(shift);
            SQLiteDatabase database = getWritableDatabase();
            if ("unset".equals(shift)) {
                database.delete("member_shift_exceptions",
                    "member_id=? AND date=?",
                    new String[]{Long.toString(memberId), date});
                return;
            }
            ContentValues values = new ContentValues();
            values.put("member_id", memberId);
            values.put("date", date);
            values.put("shift", shift);
            database.insertWithOnConflict("member_shift_exceptions", null,
                values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        String placePath(long id) {
            java.util.ArrayList<String> pieces = new java.util.ArrayList<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            Long current = id;
            while (current != null && seen.add(current)
                    && seen.size() <= 100) {
                try (Cursor c = getReadableDatabase().rawQuery(
                        "SELECT name,parent_id FROM places WHERE id=?",
                        new String[]{Long.toString(current)})) {
                    if (!c.moveToFirst()) break;
                    pieces.add(0, c.getString(0));
                    current = c.isNull(1) ? null : c.getLong(1);
                }
            }
            return android.text.TextUtils.join(" → ", pieces);
        }

        boolean canPlaceWithin(Long movingId, Long parentId) {
            if (parentId == null) return true;
            java.util.Map<Long, Long> parents = new java.util.HashMap<>();
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,parent_id FROM places", null)) {
                while (c.moveToNext())
                    parents.put(c.getLong(0),
                        c.isNull(1) ? null : c.getLong(1));
            }
            return PlaceRules.canMove(parents, movingId, parentId);
        }

        boolean savePlace(Long id, String name, String kind,
                Long parentId, String icon) {
            String error = PlaceRules.validateFields(name, kind, icon);
            if (error != null) throw new IllegalArgumentException(error);
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                if (id != null) {
                    try (Cursor found = database.rawQuery(
                            "SELECT id FROM places WHERE id=?",
                            new String[]{Long.toString(id)})) {
                        if (!found.moveToFirst())
                            throw new IllegalArgumentException("Miejsce już nie istnieje.");
                    }
                }
                if (!canPlaceWithin(id, parentId))
                    throw new IllegalArgumentException(
                        "Nie można przenieść miejsca do niego samego, "
                        + "jego podmiejsca ani nieistniejącej lokalizacji.");
                try (Cursor duplicate = database.rawQuery(
                        "SELECT id FROM places WHERE COALESCE(parent_id,0)=? "
                        + "AND name=? COLLATE NOCASE" + (id == null ? "" : " AND id!=?"),
                        id == null
                            ? new String[]{Long.toString(parentId == null ? 0 : parentId),
                                name}
                            : new String[]{Long.toString(parentId == null ? 0 : parentId),
                                name, Long.toString(id)})) {
                    if (duplicate.moveToFirst()) return false;
                }
                ContentValues values = new ContentValues();
                values.put("name", name);
                values.put("kind", kind);
                values.put("icon", icon);
                if (parentId == null) values.putNull("parent_id");
                else values.put("parent_id", parentId);
                if (id == null) database.insertOrThrow("places", null, values);
                else database.update("places", values, "id=?",
                    new String[]{Long.toString(id)});
                database.setTransactionSuccessful();
                return true;
            } finally {
                database.endTransaction();
            }
        }

        boolean deletePlace(long id) {
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                try (Cursor children = database.rawQuery(
                        "SELECT COUNT(*) FROM places WHERE parent_id=?",
                        new String[]{Long.toString(id)})) {
                    if (children.moveToFirst() && children.getInt(0) != 0)
                        return false;
                }
                try (Cursor occupied = database.rawQuery(
                        "SELECT 1 FROM storage_items WHERE place_id=? LIMIT 1",
                        new String[]{Long.toString(id)})) {
                    if (occupied.moveToFirst()) return false;
                }
                database.execSQL("UPDATE tasks SET place_id=NULL WHERE place_id=?",
                    new Object[]{id});
                database.delete("places", "id=?", new String[]{Long.toString(id)});
                database.setTransactionSuccessful();
                return true;
            } finally {
                database.endTransaction();
            }
        }

        private static void addDeviceTimers(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE device_timers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "device_type TEXT NOT NULL, title TEXT NOT NULL, "
                + "start_at INTEGER NOT NULL, end_at INTEGER NOT NULL, "
                + "status TEXT NOT NULL DEFAULT 'running', "
                + "acknowledged_at INTEGER)");
            database.execSQL("CREATE INDEX device_timers_status_end_idx "
                + "ON device_timers(status,end_at)");
        }

        long startDeviceTimer(String type, String title, int minutes) {
            long now = System.currentTimeMillis();
            long end = DeviceTimerRules.endAt(now, minutes);
            if (!DeviceTimerRules.validType(type)
                    || !DeviceTimerRules.validTitle(title))
                throw new IllegalArgumentException("Wybierz urządzenie i nazwę.");
            ContentValues values = new ContentValues();
            values.put("device_type", type);
            values.put("title", title.trim());
            values.put("start_at", now);
            values.put("end_at", end);
            values.put("status", "running");
            return getWritableDatabase().insertOrThrow(
                "device_timers", null, values);
        }

        boolean updateDeviceTimer(long id, String nextStatus, long now) {
            if (!"acknowledged".equals(nextStatus)
                    && !"cancelled".equals(nextStatus))
                throw new IllegalArgumentException("Nieznany stan minutnika.");
            ContentValues values = new ContentValues();
            values.put("status", nextStatus);
            if ("acknowledged".equals(nextStatus))
                values.put("acknowledged_at", now);
            else values.putNull("acknowledged_at");
            return getWritableDatabase().update("device_timers", values,
                "id=? AND status='running'"
                    + ("acknowledged".equals(nextStatus)
                        ? " AND end_at<=?" : ""),
                "acknowledged".equals(nextStatus)
                    ? new String[]{Long.toString(id), Long.toString(now)}
                    : new String[]{Long.toString(id)}) == 1;
        }

        boolean addShoppingItem(String name, Long amount, String unit) {
            String clean = ShoppingRules.validatedName(name);
            if (!ShoppingRules.knownUnit(unit)
                    || amount != null && (amount < 1 || amount > ShoppingRules.MAX_MILLI))
                throw new IllegalArgumentException("Nieprawidłowa ilość lub jednostka.");
            ContentValues values = new ContentValues();
            values.put("name", clean);
            if (amount == null) values.putNull("qty_milli");
            else values.put("qty_milli", amount);
            values.put("unit", unit);
            return getWritableDatabase().insertWithOnConflict(
                "shopping_items", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
        }

        void setShoppingChecked(long id, boolean checked) {
            ContentValues values = new ContentValues();
            values.put("checked", checked ? 1 : 0);
            getWritableDatabase().update("shopping_items", values, "id=?",
                new String[]{Long.toString(id)});
        }

        void deleteShoppingItem(long id) {
            getWritableDatabase().delete("shopping_items", "id=?",
                new String[]{Long.toString(id)});
        }

        void addStock(String name, String category, String unit, long milli) {
            if (!PantryPackageRules.valid(unit, milli))
                throw new IllegalArgumentException("Nieprawidłowe opakowanie.");
            if (!PantryCategories.known(category))
                throw new IllegalArgumentException("Nieznana kategoria.");
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
                    PantryPackageStore.requireSame(database, existing, unit, milli);
                    database.execSQL(
                        "UPDATE pantry SET qty=qty+1 WHERE id=? AND qty<100000000",
                        new Object[]{existing});
                } else {
                    ContentValues values = new ContentValues();
                    values.put("name", name);
                    values.put("qty", 1);
                    values.put("category", category);
                    long newId = database.insertOrThrow("pantry", null, values);
                    PantryPackageStore.set(database, newId, unit, milli);
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

        boolean editStock(long id, String name, String category, String unit,
                          long milli) {
            if (!PantryCategories.known(category)
                    || !PantryPackageRules.valid(unit, milli))
                throw new IllegalArgumentException("Nieprawidłowe dane opakowania.");
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                try (Cursor c = database.rawQuery(
                        "SELECT id FROM pantry WHERE name=? COLLATE NOCASE AND id!=? LIMIT 1",
                        new String[]{name, Long.toString(id)})) {
                    if (c.moveToFirst()) return false;
                }
                ContentValues values = new ContentValues();
                values.put("name", name);
                values.put("category", category);
                if (database.update("pantry", values, "id=?",
                        new String[]{Long.toString(id)}) != 1)
                    throw new IllegalArgumentException("Nie znaleziono produktu.");
                PantryPackageStore.set(database, id, unit, milli);
                database.setTransactionSuccessful();
                return true;
            } finally { database.endTransaction(); }
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
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                database.delete("pantry_barcodes", "pantry_id=?",
                    new String[]{Long.toString(id)});
                database.delete("pantry_product_details", "pantry_id=?",
                    new String[]{Long.toString(id)});
                database.delete("pantry_packages", "pantry_id=?",
                    new String[]{Long.toString(id)});
                database.delete("pantry", "id=?",
                    new String[]{Long.toString(id)});
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
        }

    }
}
