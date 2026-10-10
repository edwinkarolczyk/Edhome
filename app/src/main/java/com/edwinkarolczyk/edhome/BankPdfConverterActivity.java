package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Lokalny konwerter VeloBank PDF do CSV zgodnego z importem EDHOME. */
public final class BankPdfConverterActivity extends Activity {
    private static final int PICK=4101, SAVE=4102;
    private TextView status;
    private Button export;
    private BankStatementVeloPdf.ParseReport report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(20*getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        TextView title=new TextView(this);
        title.setText("EDHOME • Konwerter PDF → CSV");
        title.setTextSize(22);
        root.addView(title);
        status=new TextView(this);
        status.setText("Wybierz tekstowy PDF VeloBanku. Plik zostanie odczytany lokalnie. Nieczytelne operacje nie będą zgadywane.");
        status.setTextSize(16);
        root.addView(status);
        Button open=new Button(this);
        open.setText("Wybierz PDF");
        root.addView(open);
        export=new Button(this);
        export.setText("Zapisz CSV dla EDHOME");
        export.setEnabled(false);
        root.addView(export);
        setContentView(root);
        open.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            startActivityForResult(intent,PICK);
        });
        export.setOnClickListener(v->{
            if(report==null||report.accepted.isEmpty())return;
            if(!report.complete()) {
                new AlertDialog.Builder(this)
                    .setTitle("Niepełny odczyt PDF")
                    .setMessage("Poprawne operacje: "+report.accepted.size()
                        +"\nNieodczytane: "+report.unreadableCount
                        +"\nCSV NIE zawiera nieodczytanych operacji. Porównaj go z PDF przed importem.")
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Zapisz tylko poprawne",(d,w)->save())
                    .show();
            } else save();
        });
    }
    private void save() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,"EDHOME-VeloBank.csv");
        startActivityForResult(intent,SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK) {
            report=null;
            export.setEnabled(false);
            try {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=getContentResolver().openInputStream(data.getData())) {
                    if(in==null)throw new IllegalArgumentException("Nie można otworzyć PDF.");
                    byte[] buffer=new byte[8192];
                    int count;
                    while((count=in.read(buffer))!=-1) {
                        bytes.write(buffer,0,count);
                        if(bytes.size()>8*1024*1024)
                            throw new IllegalArgumentException("PDF przekracza 8 MB.");
                    }
                }
                String text=BankPdfText.extract(this,bytes.toByteArray());
                report=BankStatementVeloPdf.inspect(text);
                export.setEnabled(!report.accepted.isEmpty());
                status.setText("Poprawne: "+report.accepted.size()
                    +"\nDo ręcznej kontroli: "+report.unreadableCount
                    +"\nBrak kwoty: "+report.missingMoneyCount
                    +"\nKilka kwot: "+report.ambiguousMoneyCount
                    +"\nNieznany kierunek: "+report.directionCount
                    +"\nPierwsza niejasna data: "+report.firstUnreadable
                    +"\nCSV nie zmienia salda i wymaga osobnego importu.");
            } catch(Exception ex) {
                status.setText("Błąd odczytu: "+ex.getMessage());
            }
        } else if(request==SAVE&&report!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null)throw new IllegalArgumentException("Nie można zapisać CSV.");
                out.write(csv(report.accepted).getBytes(StandardCharsets.UTF_8));
                status.append("\nZapisano CSV. Sprawdź niejasne operacje w oryginalnym PDF.");
            }catch(Exception ex){status.append("\nBłąd zapisu: "+ex.getMessage());}
        }
    }
    private static String quote(String value) {
        return "\"" + value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ") + "\"";
    }
    private static String csv(List<BankStatementCsv.Entry> entries) {
        StringBuilder out=new StringBuilder("\uFEFFData;Kwota;Id transakcji;Opis\r\n");
        for(BankStatementCsv.Entry row:entries) {
            long whole=row.amountGrosz/100, cents=row.amountGrosz%100;
            String amount=(row.kind.equals("expense")?"-":"+")+
                String.format(Locale.ROOT,"%d,%02d",whole,cents);
            out.append(quote(row.date)).append(';')
                .append(quote(amount)).append(';')
                .append(quote("velo-pdf-"+row.evidenceKey)).append(';')
                .append(quote(row.description)).append("\r\n");
        }
        return out.toString();
    }
}
