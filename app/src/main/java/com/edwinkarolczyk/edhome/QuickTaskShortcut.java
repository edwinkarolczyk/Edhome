package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import java.time.LocalDate;
import java.util.List;

/** Szybkie tworzenie czynności w istniejącej bazie, bez osobnej aplikacji. */
final class QuickTaskShortcut {
    private QuickTaskShortcut() { }

    interface Suggest {
        List<TimeSuggestions.Option> find(int durationMinutes);
    }
    interface Save {
        void save(String name,String due,int minutes,String reminder);
    }

    static AlertDialog show(Activity activity,Suggest suggest,Save onSave) {
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(18*activity.getResources().getDisplayMetrics().density);
        form.setPadding(pad,pad/2,pad,pad/2);

        EditText title=new EditText(activity);
        title.setSingleLine(true);
        title.setHint("Co trzeba zrobić?");
        title.setInputType(InputType.TYPE_CLASS_TEXT
            |InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(title);

        TextView durationLabel=new TextView(activity);
        durationLabel.setText("Czas wykonania");
        form.addView(durationLabel);
        Spinner duration=new Spinner(activity);
        int[] minutes={20,30,45,60,90,120,180,240};
        ArrayAdapter<String> durationAdapter=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item,
            new String[]{"20 min","30 min","45 min","1 h","1 h 30 min",
                "2 h","3 h","4 h"});
        durationAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        duration.setAdapter(durationAdapter);
        duration.setSelection(1);
        form.addView(duration);

        Spinner date=new Spinner(activity);
        String[] options={"Dzisiaj","Jutro","Bez terminu",
            "Znajdź wolny termin","Wybierz datę…"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        date.setAdapter(adapter);
        form.addView(date);

        TextView selected=new TextView(activity);
        selected.setText("Termin: dzisiaj");
        selected.setPadding(0,pad/2,0,0);
        form.addView(selected);
        TextView caveat=new TextView(activity);
        caveat.setText("Propozycje sprawdzają grafik i zajęte dni w EDHOME. "
            +"Wybrana godzina stanie się przypomnieniem, a nie rezerwacją.");
        caveat.setTextSize(12f);
        form.addView(caveat);
        final LocalDate[] customDate={null};
        final TimeSuggestions.Option[] chosen={null};

        duration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent,View view,
                    int position,long id) {
                if(date.getSelectedItemPosition()==3&&chosen[0]!=null) {
                    chosen[0]=null;
                    date.setSelection(0);
                    selected.setText("Zmieniono czas — wyszukaj ponownie.");
                }
            }
        });
        date.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent,View view,
                    int position,long id) {
                if(position!=3)chosen[0]=null;
                if(position==0)selected.setText("Termin: dzisiaj");
                else if(position==1)selected.setText("Termin: jutro");
                else if(position==2)selected.setText("Bez terminu");
                else if(position==3) {
                    List<TimeSuggestions.Option> slots=suggest.find(
                        minutes[duration.getSelectedItemPosition()]);
                    if(slots==null||slots.isEmpty()) {
                        date.setSelection(0);
                        new AlertDialog.Builder(activity)
                            .setTitle("Nie znaleziono wolnego dnia")
                            .setMessage("Ustaw grafik aktywnej osoby lub "
                                +"wybierz datę ręcznie. Dni z innymi "
                                +"czynnościami nie są proponowane.")
                            .setPositiveButton("Rozumiem",null).show();
                        return;
                    }
                    String[] labels=new String[slots.size()];
                    for(int i=0;i<slots.size();i++) {
                        TimeSuggestions.Option o=slots.get(i);
                        labels[i]=o.date+" • "+o.start+"–"+o.end;
                    }
                    new AlertDialog.Builder(activity)
                        .setTitle("Wybierz proponowany termin")
                        .setItems(labels,(dialog,which)->{
                            chosen[0]=slots.get(which);
                            selected.setText("Propozycja: "+labels[which]);
                        })
                        .setNegativeButton("Anuluj",(dialog,which)->{
                            if(chosen[0]==null)date.setSelection(0);
                        }).show();
                } else {
                    LocalDate initial=customDate[0]==null
                        ?LocalDate.now():customDate[0];
                    DatePickerDialog picker=new DatePickerDialog(activity,
                        (viewDate,year,month,day)->{
                            customDate[0]=LocalDate.of(year,month+1,day);
                            selected.setText("Termin: "+customDate[0]);
                        },initial.getYear(),initial.getMonthValue()-1,
                        initial.getDayOfMonth());
                    picker.setOnCancelListener(ignored->{
                        if(customDate[0]==null)date.setSelection(0);
                    });
                    picker.show();
                }
            }
        });

        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("+ Szybka czynność")
            .setView(form)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Dodaj",null)
            .create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v->{
                    String name=title.getText().toString().trim();
                    int choice=date.getSelectedItemPosition();
                    if(choice==3&&chosen[0]==null) {
                        selected.setError("Wybierz propozycję.");
                        return;
                    }
                    if(choice==4&&customDate[0]==null) {
                        selected.setError("Wybierz datę.");
                        return;
                    }
                    String due=choice==0?LocalDate.now().toString()
                        :choice==1?LocalDate.now().plusDays(1).toString()
                        :choice==2?"":choice==3?chosen[0].date.toString()
                        :customDate[0].toString();
                    String validation=TaskRules.validate(name,due,"once",1);
                    if(validation!=null) {
                        title.setError(validation);
                        title.requestFocus();
                        return;
                    }
                    try {
                        onSave.save(name,due,
                            minutes[duration.getSelectedItemPosition()],
                            choice==3?chosen[0].start.toString():null);
                        dialog.dismiss();
                    } catch(Exception error) {
                        title.setError("Nie zapisano czynności: "
                            +(error.getMessage()==null
                                ?"spróbuj ponownie":error.getMessage()));
                    }
                });
            title.requestFocus();
            if(dialog.getWindow()!=null)
                dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
        return dialog;
    }
}
