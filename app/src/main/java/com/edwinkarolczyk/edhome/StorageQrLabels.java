package com.edwinkarolczyk.edhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Offline labels. Printed QR stores only stable kind/ID, never private content. */
final class StorageQrLabels {
    static final String[] FORMATS = {"40 × 30 mm", "50 × 30 mm",
        "70 × 50 mm", "A4 — zbiorczo"};
    static final String HISTORY = "storage_qr_print_history_v1";
    private static final int HISTORY_LIMIT = 80;
    static final class Label {
        final String kind;
        final long id;
        final String name;
        final String location;
        Label(String kind, long id, String name, String location) {
            this.kind = kind;
            this.id = id;
            this.name = name == null ? "" : name;
            this.location = location == null ? "" : location;
        }
        String payload() { return StorageQr.encode(kind,id); }
    }
    private StorageQrLabels() { }

    static float mm(float length) { return length * 72f / 25.4f; }

    static byte[] pdf(List<Label> labels, int format) throws Exception {
        if (labels == null || labels.isEmpty() || labels.size() > 250
                || format < 0 || format >= FORMATS.length)
            throw new IllegalArgumentException("Zaznacz 1–250 etykiet.");
        boolean sheet = format == 3;
        float labelW = mm(format == 0 ? 40 : format == 2 ? 70 : 50);
        float labelH = mm(format == 2 ? 50 : 30);
        int pageW = Math.round(sheet ? mm(210) : labelW);
        int pageH = Math.round(sheet ? mm(297) : labelH);
        int columns = sheet ? 3 : 1;
        int rows = sheet ? 8 : 1;
        float gap = sheet ? mm(5) : 0;
        float left = sheet ? (pageW - (columns * labelW + 2 * gap))/2f : 0;
        float top = sheet ? (pageH - (rows * labelH + 7 * gap))/2f : 0;
        int perPage = rows * columns;
        PdfDocument document = new PdfDocument();
        try {
            for (int start = 0, page = 1; start < labels.size();
                    start += perPage, page++) {
                PdfDocument.Page pdfPage = document.startPage(
                    new PdfDocument.PageInfo.Builder(pageW,pageH,page).create());
                Canvas canvas = pdfPage.getCanvas();
                canvas.drawColor(Color.WHITE);
                int end = Math.min(labels.size(), start + perPage);
                for (int n = start; n < end; n++) {
                    int cell = n - start;
                    float x = left + (cell % columns) * (labelW + gap);
                    float y = top + (cell / columns) * (labelH + gap);
                    drawLabel(canvas, labels.get(n), x,y,labelW,labelH);
                }
                document.finishPage(pdfPage);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.writeTo(bytes);
            return bytes.toByteArray();
        } finally { document.close(); }
    }

    private static void drawLabel(Canvas canvas, Label label, float x,float y,
            float width,float height) throws Exception {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.45f);
        canvas.drawRect(x+0.5f,y+0.5f,x+width-0.5f,y+height-0.5f,paint);
        paint.setStyle(Paint.Style.FILL);
        // Compact thermal labels put QR above the text; 70×50 and A4
        // may use the same layout for deterministic sizing and scanability.
        float qrSize = Math.min(width - mm(5), height * 0.68f);
        float qrX = x + (width - qrSize)/2f;
        float qrY = y + mm(1);
        BitMatrix bits = new MultiFormatWriter().encode(
            label.payload(), BarcodeFormat.QR_CODE, 384,384);
        Bitmap qr = Bitmap.createBitmap(bits.getWidth(),bits.getHeight(),
            Bitmap.Config.ARGB_8888);
        try {
            for (int row = 0; row < bits.getHeight(); row++)
                for (int col = 0; col < bits.getWidth(); col++)
                    qr.setPixel(col,row,bits.get(col,row)?Color.BLACK:Color.WHITE);
            paint.setFilterBitmap(false);
            canvas.drawBitmap(qr,null,
                new android.graphics.RectF(qrX,qrY,qrX+qrSize,qrY+qrSize),paint);
        } finally { qr.recycle(); }
        paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.BLACK);
        float textY = qrY + qrSize + mm(2.4f);
        float fontSize = Math.min(11f, Math.max(6f,height*0.10f));
        paint.setTextSize(fontSize);
        String title = shorten(label.name,paint,width-mm(3));
        canvas.drawText(title,x+width/2f,textY,paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(Math.min(7f,Math.max(4.5f,height*0.065f)));
        String path = shorten(label.location,paint,width-mm(3));
        float locationY = textY + paint.getTextSize()+1;
        if (locationY <= y+height-mm(1))
            canvas.drawText(path,x+width/2f,locationY,paint);
    }

    private static String shorten(String value, Paint paint,float maxWidth) {
        if (paint.measureText(value)<=maxWidth) return value;
        String shortened = value;
        while (!shortened.isEmpty()
                && paint.measureText(shortened+"…")>maxWidth)
            shortened=shortened.substring(0,shortened.length()-1);
        return shortened+"…";
    }

    static void print(Activity activity,byte[] pdf,int pageCount,
            int format,String jobName) {
        PrintManager manager = (PrintManager) activity.getSystemService(
            Context.PRINT_SERVICE);
        if (manager == null) throw new IllegalStateException(
            "Usługa drukowania jest niedostępna.");
        manager.print(jobName,new PrintDocumentAdapter() {
            @Override public void onLayout(PrintAttributes oldAttributes,
                    PrintAttributes newAttributes,
                    CancellationSignal cancellation,
                    LayoutResultCallback callback,android.os.Bundle extras) {
                if (cancellation.isCanceled()) { callback.onLayoutCancelled();return; }
                callback.onLayoutFinished(
                    new PrintDocumentInfo.Builder(jobName+".pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pageCount).build(),true);
            }
            @Override public void onWrite(android.print.PageRange[] pages,
                    ParcelFileDescriptor destination,
                    CancellationSignal cancellation,WriteResultCallback callback) {
                if (cancellation.isCanceled()) { callback.onWriteCancelled();return; }
                try (FileOutputStream output = new FileOutputStream(
                        destination.getFileDescriptor())) {
                    output.write(pdf);
                    output.flush();
                    callback.onWriteFinished(new android.print.PageRange[]{
                        android.print.PageRange.ALL_PAGES});
                } catch (IOException error) {
                    callback.onWriteFailed("Nie zapisano etykiet do drukarki.");
                }
            }
        },new PrintAttributes.Builder()
            .setMediaSize(format==3 ? PrintAttributes.MediaSize.ISO_A4
                : new PrintAttributes.MediaSize("EDHOME_QR_"+format,
                    FORMATS[format],
                    format==0 ? 1575 : format==1 ? 1969 : 2756,
                    format==2 ? 1969 : 1181))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build());
    }

    static int pages(int count,int format) {
        return format==3?(count+23)/24:count;
    }

    static void log(Context context, String event, Label label) {
        // History contains only the kind and stable numeric ID, not names,
        // QR payload, account data or full item location.
        writeHistory(context,event+" • "+label.kind+" #"+label.id);
    }

    static void log(Context context, String event, int count) {
        writeHistory(context,event+" • "+count+" kodów");
    }

    private static void writeHistory(Context context, String event) {
        SharedPreferences prefs=context.getSharedPreferences(
            "edhome_beta_prefs",Context.MODE_PRIVATE);
        String prior=prefs.getString(HISTORY,"");
        String line=LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            +" • "+event;
        String[] rows=prior.split("\n");
        StringBuilder output=new StringBuilder(line);
        for (int i=0;i<rows.length && i<HISTORY_LIMIT-1;i++)
            if (!rows[i].isEmpty()) output.append('\n').append(rows[i]);
        prefs.edit().putString(HISTORY,output.toString()).apply();
    }
}
