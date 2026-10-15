package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.LocalDate;

/** Lekki formularz ze skrótu launchera. Bez drugiej aktywności i bez osobnej bazy. */
final class QuickTaskShortcut {
    private QuickTaskShortcut() { }

    interface Save {
        void save(String name, String due);
    }

    static AlertDialog show(Activity activity, Save onSave) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(18 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, pad / 2);

        EditText title = new EditText(activity);
        title.setSingleLine(true);
        title.setHint("Co trzeba zrobić?");
        title.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(title);

        Spinner date = new Spinner(activity);
        String[] options = {"Dzisiaj", "Jutro", "Bez terminu", "Wybierz datę…"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            activity, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        date.setAdapter(adapter);
        form.addView(date);

        TextView selected = new TextView(activity);
        selected.setText("Termin: dzisiaj");
        selected.setPadding(0, pad / 2, 0, 0);
        form.addView(selected);
        final LocalDate[] customDate = {null};
        date.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position == 0) selected.setText("Termin: dzisiaj");
                else if (position == 1) selected.setText("Termin: jutro");
                else if (position == 2) selected.setText("Bez terminu");
                else {
                    LocalDate initial = customDate[0] == null
                        ? LocalDate.now() : customDate[0];
                    DatePickerDialog picker = new DatePickerDialog(activity,
                        (pickerView, year, month, day) -> {
                            customDate[0] = LocalDate.of(year, month + 1, day);
                            selected.setText("Termin: " + customDate[0]);
                        }, initial.getYear(), initial.getMonthValue() - 1,
                        initial.getDayOfMonth());
                    picker.setOnCancelListener(ignored -> {
                        if (customDate[0] == null) date.setSelection(0);
                    });
                    picker.show();
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("+ Szybka czynność")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Dodaj", null)
            .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = title.getText().toString().trim();
                    int choice = date.getSelectedItemPosition();
                    if (choice == 3 && customDate[0] == null) {
                        selected.setError("Wybierz datę.");
                        return;
                    }
                    String due = choice == 0 ? LocalDate.now().toString()
                        : choice == 1 ? LocalDate.now().plusDays(1).toString()
                        : choice == 2 ? "" : customDate[0].toString();
                    String validation = TaskRules.validate(name, due, "once", 1);
                    if (validation != null) {
                        title.setError(validation);
                        title.requestFocus();
                        return;
                    }
                    try {
                        onSave.save(name, due);
                        dialog.dismiss();
                    } catch (Exception error) {
                        title.setError("Nie zapisano czynności. Spróbuj ponownie.");
                    }
                });
            title.requestFocus();
            if (dialog.getWindow() != null)
                dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
        return dialog;
    }
}
