package com.edwinkarolczyk.edhome;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lokalny raport PDF projektu. Odczytuje wyłącznie dane już zapisane w EDHOME. */
final class ProjectReportPdf {
    private static final int PAGE_W=595, PAGE_H=842, LEFT=42, RIGHT=553;
    private static final int TOP=54, BOTTOM=790;
    private final PdfDocument document=new PdfDocument();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pageNumber=0, y=TOP;
    private PdfDocument.Page page;
    private Canvas canvas;

    private ProjectReportPdf() { }

    static byte[] create(SQLiteDatabase db,long projectId) throws Exception {
        ProjectReportPdf report=new ProjectReportPdf();
        try {
            report.build(db,projectId);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            report.document.writeTo(out);
            return out.toByteArray();
        } finally {
            report.document.close();
        }
    }

    private void build(SQLiteDatabase db,long projectId) throws Exception {
        ProjectStore.Project project=ProjectStore.find(db,projectId);
        if(project==null)throw new IllegalArgumentException("Projekt już nie istnieje.");
        Set<Long> ids=ProjectStore.descendantIds(db,projectId);
        StringBuilder marks=new StringBuilder();
        String[] args=new String[ids.size()];
        int n=0;
        for(Long id:ids){if(marks.length()>0)marks.append(',');marks.append('?');args[n++]=Long.toString(id);}
        startPage(project.name);
        heading("RAPORT PROJEKTU");
        line("EDHOME • Idea by Edwin",true);
        line("Data raportu: "+date(System.currentTimeMillis()),false);
        gap(12);
        heading("1. Informacje o projekcie");
        line("Nazwa: "+project.name,false);
        line("Status: "+status(project.status),false);
        line("Termin: "+(project.dueDate==null||project.dueDate.isBlank()?"Nie ustawiono":project.dueDate),false);
        try(Cursor c=db.rawQuery("SELECT created_at,parent_id FROM projects WHERE id=?",new String[]{Long.toString(projectId)})){
            if(c.moveToFirst()){
                line("Utworzono: "+date(c.getLong(0)),false);
                if(!c.isNull(1)){
                    ProjectStore.Project parent=ProjectStore.find(db,c.getLong(1));
                    if(parent!=null)line("Projekt nadrzędny: "+parent.name,false);
                }
            }
        }
        line("Zakres raportu: projekt i wszystkie podprojekty.",false);
        line("Opis projektu: brak osobnego pola opisu w zapisanych danych.",false);

        ProjectStore.Stats stats=ProjectStore.stats(db,projectId);
        gap(10);heading("2. Postęp i statystyki");
        line("Postęp: "+stats.progressPct()+"%",true);
        line("Wykonane czynności: "+stats.doneTasks+" / "+stats.tasks,false);
        line("Zaległe czynności: "+stats.overdueTasks,false);
        line("Planowany czas: "+minutes(stats.totalMinutes),false);
        line("Czas zaliczony do postępu: "+minutes(stats.doneMinutes),false);
        line("Zarejestrowany czas pracy: "+minutes(stats.workedMinutes),false);
        line("Pozostało wg planu: "+minutes(stats.remainingMinutes()),false);

        gap(10);heading("3. Czynności");
        int taskCount=0;
        try(Cursor c=db.rawQuery("SELECT id,title,done,due_date,duration_minutes FROM tasks WHERE project_id IN ("+marks+") ORDER BY project_id,project_sort_order,id",args)){
            while(c.moveToNext()){
                taskCount++;
                line((c.getInt(2)!=0?"[WYKONANA] ":"[OTWARTA] ")+c.getString(1),true);
                line("Termin: "+(c.isNull(3)?"—":c.getString(3))+" • Plan: "+minutes(c.getInt(4)),false);
                appendTaskTime(db,c.getLong(0));
            }
        }
        if(taskCount==0)line("Brak czynności.",false);

        gap(10);heading("4. Czas pracy");
        try(Cursor c=db.rawQuery("SELECT t.title,s.started_at,s.ended_at,s.worked_minutes FROM project_task_work_sessions s JOIN tasks t ON t.id=s.task_id WHERE t.project_id IN ("+marks+") ORDER BY s.started_at",args)){
            int count=0;
            while(c.moveToNext()){
                count++;
                String duration=c.isNull(2)?"sesja aktywna":minutes(c.isNull(3)?0:c.getInt(3));
                line(c.getString(0)+" • "+date(c.getLong(1))+" • "+duration,false);
            }
            if(count==0)line("Brak zapisanych sesji czasu pracy.",false);
        }

        gap(10);heading("5. Wydatki i zakupy");
        long total=0,spent=0,planned=0;int costCount=0;
        try(Cursor c=db.rawQuery("SELECT name,qty_milli,unit,unit_price_grosz,status,note,created_at FROM project_costs WHERE project_id IN ("+marks+") ORDER BY created_at,id",args)){
            while(c.moveToNext()){
                costCount++;
                long value=java.math.BigDecimal.valueOf(c.getLong(1)).multiply(java.math.BigDecimal.valueOf(c.getLong(3))).divide(java.math.BigDecimal.valueOf(1000),0,java.math.RoundingMode.HALF_UP).longValueExact();
                total=Math.addExact(total,value);
                String state="planned".equals(c.getString(4))?"Planowane":"bought".equals(c.getString(4))?"Kupione":"Zapłacone";
                if("planned".equals(c.getString(4)))planned=Math.addExact(planned,value);else spent=Math.addExact(spent,value);
                line(c.getString(0)+" • "+MoneyRules.format(value)+" • "+state,false);
                line("Ilość: "+java.math.BigDecimal.valueOf(c.getLong(1),3).stripTrailingZeros().toPlainString()+" "+c.getString(2)+" • cena jedn.: "+MoneyRules.format(c.getLong(3)),false);
                if(!c.isNull(5)&&!c.getString(5).isBlank())line("Uwagi: "+c.getString(5),false);
                line("Data dodania: "+date(c.getLong(6)),false);
            }
        }
        if(costCount==0)line("Brak zapisanych wydatków.",false);
        line("Wszystkie koszty: "+MoneyRules.format(total),true);
        line("Poniesione: "+MoneyRules.format(spent)+" • planowane: "+MoneyRules.format(planned),false);
        line("Koszty projektu nie są księgowane w PayCheck.",false);

        gap(10);heading("6. Historia zapisanych wykonań");
        try(Cursor c=db.rawQuery("SELECT title_snapshot,completed_at,due_date,next_due_date,assignee_name_snapshot FROM task_history WHERE task_id IN (SELECT id FROM tasks WHERE project_id IN ("+marks+")) ORDER BY completed_at",args)){
            int count=0;
            while(c.moveToNext()){
                count++;
                line(c.getString(0)+" • wykonano: "+date(c.getLong(1)),false);
                if(!c.isNull(2))line("Termin: "+c.getString(2),false);
                if(!c.isNull(3))line("Następny termin: "+c.getString(3),false);
                if(!c.isNull(4))line("Wykonał/a: "+c.getString(4),false);
            }
            if(count==0)line("Brak zapisanych wpisów historii wykonań.",false);
        }
        line("Uwaga: aplikacja nie przechowuje pełnego dziennika wszystkich edycji projektu. Raport nie dopisuje zdarzeń, których nie ma w bazie.",false);

        gap(10);heading("7. Zdjęcia projektu");
        addPhoto("Miniatura projektu",ProjectStore.thumbnail(db,projectId));
        try(Cursor c=db.rawQuery("SELECT id,title,task_thumbnail_1_base64,task_thumbnail_2_base64 FROM tasks WHERE project_id IN ("+marks+") ORDER BY project_id,project_sort_order,id",args)){
            while(c.moveToNext()){
                addPhoto(c.getString(1)+" • zdjęcie 1",c.isNull(2)?null:c.getString(2));
                addPhoto(c.getString(1)+" • zdjęcie 2",c.isNull(3)?null:c.getString(3));
            }
        }
        line("Uwzględniono zdjęcia dostępne w bazie EDHOME. Są to zapisane wersje skompresowane; oryginalne pliki z galerii nie są przechowywane przez ten moduł.",false);
        gap(10);heading("8. Podsumowanie");
        line("Wykonane czynności: "+stats.doneTasks+" • pozostałe: "+Math.max(0,stats.tasks-stats.doneTasks),true);
        line("Zarejestrowany czas pracy: "+minutes(stats.workedMinutes),false);
        line("Wydatki poniesione: "+MoneyRules.format(spent),false);
        line("Wydatki planowane: "+MoneyRules.format(planned),false);
        line("Raport wygenerowano lokalnie na urządzeniu. Dane projektu nie zostały zmienione.",false);
        finishPage();
    }

    private void appendTaskTime(SQLiteDatabase db,long taskId){
        int mins=ProjectStore.workedMinutes(db,taskId,true);
        line("Zarejestrowany czas: "+minutes(mins),false);
    }
    private void startPage(String title){
        pageNumber++;
        page=document.startPage(new PdfDocument.PageInfo.Builder(PAGE_W,PAGE_H,pageNumber).create());
        canvas=page.getCanvas();canvas.drawColor(0xFFFFFFFF);y=TOP;
        paint.setColor(0xFF252B35);paint.setTextSize(9);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        canvas.drawText("EDHOME • "+clip(title,70),LEFT,30,paint);
        paint.setColor(0xFFB8BEC8);canvas.drawLine(LEFT,39,RIGHT,39,paint);
    }
    private void finishPage(){
        paint.setColor(0xFF7A8492);paint.setTextSize(8);
        canvas.drawText("EDHOME • "+date(System.currentTimeMillis())+" • strona "+pageNumber,LEFT,PAGE_H-24,paint);
        document.finishPage(page);
    }
    private void ensure(int needed,String title){
        if(y+needed<=BOTTOM)return;
        finishPage();startPage(title);
    }
    private void heading(String value){
        ensure(34,"Raport projektu");
        y+=8;paint.setColor(0xFF26364A);paint.setTextSize(13);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        canvas.drawText(value,LEFT,y,paint);y+=18;
    }
    private void line(String value,boolean bold){
        paint.setTextSize(bold?9.5f:8.7f);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setColor(0xFF252B35);
        String text=value==null?"—":value;
        StringBuilder part=new StringBuilder();
        for(String word:text.split("\\s+")){
            String candidate=part.length()==0?word:part+" "+word;
            if(paint.measureText(candidate)>RIGHT-LEFT && part.length()>0){
                ensure(15,"Raport projektu");canvas.drawText(part.toString(),LEFT,y,paint);y+=13;part.setLength(0);part.append(word);
            }else{if(part.length()>0)part.append(' ');part.append(word);}
        }
        if(part.length()>0){ensure(15,"Raport projektu");canvas.drawText(part.toString(),LEFT,y,paint);y+=13;}
    }
    private void gap(int amount){y+=amount;}
    private void addPhoto(String caption,String encoded){
        if(encoded==null||encoded.isBlank())return;
        try{
            byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(bitmap==null)return;
            ensure(250,"Zdjęcia projektu");
            line(caption,true);
            int maxW=RIGHT-LEFT,maxH=210;
            float scale=Math.min((float)maxW/bitmap.getWidth(),(float)maxH/bitmap.getHeight());
            int w=Math.max(1,(int)(bitmap.getWidth()*scale)),h=Math.max(1,(int)(bitmap.getHeight()*scale));
            Rect dst=new Rect(LEFT,y,LEFT+w,y+h);
            canvas.drawBitmap(bitmap,null,dst,paint);y+=h+10;
            bitmap.recycle();
        }catch(Exception ignored){line("Nie udało się odczytać zdjęcia: "+caption,false);}
    }
    private static String status(String value){return "done".equals(value)?"Zakończony":"paused".equals(value)?"Wstrzymany":"W trakcie";}
    private static String minutes(int value){int h=Math.max(0,value)/60,m=Math.max(0,value)%60;return h==0?m+" min":m==0?h+" h":h+" h "+m+" min";}
    private static String date(long millis){return new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(millis));}
    private static String clip(String value,int max){return value==null?"":value.length()<=max?value:value.substring(0,max-1)+"…";}
}
