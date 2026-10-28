package com.edwinkarolczyk.edhome;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Beta-only snooze duration picker opened directly from the ringing notification. */
public final class SnoozePickerActivity extends Activity {
    private int minutes = 15;
    private TextView value;
    private long taskId;
    private String occurrence;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        taskId = getIntent().getLongExtra("task_id", -1L);
        occurrence = getIntent().getStringExtra("occurrence");
        if (taskId <= 0L || occurrence == null || occurrence.trim().isEmpty()) {
            finish();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView prompt = new TextView(this);
        prompt.setText("Za ile minut ponowić alarm?");
        prompt.setTextSize(16);
        content.addView(prompt, new LinearLayout.LayoutParams(-1, -2));
        value = new TextView(this);
        value.setTextSize(24);
        value.setGravity(Gravity.CENTER);
        value.setText("15 min");
        content.addView(value, new LinearLayout.LayoutParams(-1, -2));
        SeekBar slider = new SeekBar(this);
        slider.setMax(119);
        slider.setProgress(14);
        slider.setContentDescription("Czas drzemki od 1 do 120 minut");
        content.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        TextView bounds = new TextView(this);
        bounds.setText("1 min                                      120 min");
        content.addView(bounds, new LinearLayout.LayoutParams(-1, -2));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minutes = progress + 1;
                value.setText(minutes + " min");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        new AlertDialog.Builder(this).setTitle("EDHOME • Drzemka")
            .setView(content)
            .setNegativeButton("Anuluj", (dialog, which) -> finish())
            .setPositiveButton("Odłóż alarm", (dialog, which) -> snooze())
            .setOnCancelListener(dialog -> finish()).show();
    }
    private void snooze() {
        Intent action = new Intent(this, ReminderReceiver.class)
            .setAction("com.edwinkarolczyk.edhome.SNOOZE_TASK_ALERT")
            .putExtra("task_id", taskId).putExtra("occurrence", occurrence)
            .putExtra("snooze_minutes", Math.max(1, Math.min(120, minutes)));
        sendBroadcast(action);
        finish();
    }
}