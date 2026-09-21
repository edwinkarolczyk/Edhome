package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Scalable native vector icons; no font, bitmap, SVG parser or network needed. */
final class TileIcon extends View {
    static final String[] ICON_IDS = {
        "tasks", "calendar", "places", "pantry", "audit",
        "updates", "backup", "settings", "today",
        "washer", "dryer", "dishwasher",
        "shelf", "cabinet", "drawer", "room", "garden",
        "garage", "workshop", "box"
    };
    static final String[] ICON_NAMES = {
        "Czynności", "Kalendarz", "Dom", "Spiżarnia", "Remanent",
        "Aktualizacje", "Kopia danych", "Ustawienia", "Zegar",
        "Pralka", "Suszarka", "Zmywarka",
        "Półka", "Szafka", "Szuflada", "Pokój", "Ogród",
        "Garaż", "Warsztat", "Pudełko"
    };

    static boolean known(String value) {
        for (String icon : ICON_IDS) if (icon.equals(value)) return true;
        return false;
    }

    private final String id;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    TileIcon(Context context, String id, int color) {
        super(context);
        this.id = id;
        pen.setColor(color);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(3.5f);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, pen);
    }
    private void rect(Canvas c, float l, float t, float r, float b, float radius) {
        c.drawRoundRect(l, t, r, b, radius, radius, pen);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float size = Math.min(getWidth(), getHeight());
        float offsetX = (getWidth() - size) / 2f;
        float offsetY = (getHeight() - size) / 2f;
        canvas.translate(offsetX, offsetY);
        canvas.scale(size / 48f, size / 48f);
        switch (id) {
            case "tasks":
                rect(canvas, 9, 10, 39, 39, 5);
                line(canvas, 16, 25, 22, 31);
                line(canvas, 22, 31, 33, 18);
                break;
            case "calendar":
                rect(canvas, 9, 12, 39, 39, 4);
                line(canvas, 9, 21, 39, 21);
                line(canvas, 17, 8, 17, 16);
                line(canvas, 31, 8, 31, 16);
                for (int x = 17; x <= 31; x += 7)
                    for (int y = 27; y <= 34; y += 7)
                        canvas.drawCircle(x, y, 1.8f, fill);
                break;
            case "places": {
                Path roof = new Path();
                roof.moveTo(7, 23);
                roof.lineTo(24, 9);
                roof.lineTo(41, 23);
                canvas.drawPath(roof, pen);
                line(canvas, 12, 21, 12, 39);
                line(canvas, 36, 21, 36, 39);
                line(canvas, 12, 39, 36, 39);
                rect(canvas, 21, 27, 28, 39, 1);
                break;
            }
            case "pantry":
                rect(canvas, 12, 16, 36, 40, 4);
                rect(canvas, 10, 10, 38, 17, 3);
                line(canvas, 19, 27, 29, 27);
                break;
            case "audit":
                rect(canvas, 10, 26, 16, 39, 2);
                rect(canvas, 21, 18, 27, 39, 2);
                rect(canvas, 32, 9, 38, 39, 2);
                break;
            case "updates":
                canvas.drawArc(9, 9, 39, 39, -65, 300, false, pen);
                line(canvas, 37, 9, 38, 20);
                line(canvas, 38, 20, 28, 17);
                break;
            case "backup":
                canvas.drawOval(10, 9, 38, 18, pen);
                canvas.drawOval(10, 31, 38, 40, pen);
                line(canvas, 10, 14, 10, 35);
                line(canvas, 38, 14, 38, 35);
                canvas.drawArc(10, 17, 38, 26, 0, 180, false, pen);
                break;
            case "settings":
                canvas.drawCircle(24, 24, 6, pen);
                canvas.drawCircle(24, 24, 15, pen);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    line(canvas, 24f + 15f * (float) Math.cos(angle),
                        24f + 15f * (float) Math.sin(angle),
                        24f + 20f * (float) Math.cos(angle),
                        24f + 20f * (float) Math.sin(angle));
                }
                break;
            case "today":
                canvas.drawCircle(24, 24, 16, pen);
                line(canvas, 24, 24, 24, 14);
                line(canvas, 24, 24, 31, 28);
                break;
            case "washer":
                rect(canvas, 9, 7, 39, 41, 3);
                line(canvas, 9, 17, 39, 17);
                canvas.drawCircle(24, 29, 8, pen);
                canvas.drawCircle(14, 12, 1.4f, fill);
                canvas.drawCircle(19, 12, 1.4f, fill);
                break;
            case "dryer":
                rect(canvas, 9, 7, 39, 41, 3);
                line(canvas, 9, 17, 39, 17);
                canvas.drawCircle(24, 29, 8, pen);
                line(canvas, 21, 27, 27, 31);
                line(canvas, 27, 27, 21, 31);
                canvas.drawCircle(14, 12, 1.4f, fill);
                break;
            case "dishwasher":
                rect(canvas, 9, 7, 39, 41, 3);
                line(canvas, 9, 17, 39, 17);
                line(canvas, 15, 27, 33, 27);
                line(canvas, 15, 34, 33, 34);
                line(canvas, 18, 24, 18, 37);
                line(canvas, 24, 24, 24, 37);
                line(canvas, 30, 24, 30, 37);
                canvas.drawCircle(14, 12, 1.4f, fill);
                break;
            case "shelf":
                line(canvas, 8, 13, 40, 13);
                line(canvas, 8, 27, 40, 27);
                line(canvas, 8, 41, 40, 41);
                line(canvas, 12, 13, 12, 41);
                line(canvas, 36, 13, 36, 41);
                break;
            case "cabinet":
                rect(canvas, 10, 8, 38, 41, 3);
                line(canvas, 24, 8, 24, 41);
                canvas.drawCircle(21, 25, 1.4f, fill);
                canvas.drawCircle(27, 25, 1.4f, fill);
                break;
            case "drawer":
                rect(canvas, 9, 9, 39, 40, 3);
                line(canvas, 9, 19, 39, 19);
                line(canvas, 9, 30, 39, 30);
                line(canvas, 20, 15, 28, 15);
                line(canvas, 20, 26, 28, 26);
                line(canvas, 20, 36, 28, 36);
                break;
            case "room":
                rect(canvas, 8, 9, 40, 40, 3);
                rect(canvas, 20, 22, 32, 40, 1);
                canvas.drawCircle(29, 33, 1.4f, fill);
                break;
            case "garden":
                line(canvas, 24, 23, 24, 41);
                canvas.drawOval(9, 13, 25, 26, pen);
                canvas.drawOval(23, 8, 39, 23, pen);
                line(canvas, 11, 41, 37, 41);
                break;
            case "garage": {
                Path roof = new Path();
                roof.moveTo(7, 20);
                roof.lineTo(24, 8);
                roof.lineTo(41, 20);
                canvas.drawPath(roof, pen);
                rect(canvas, 10, 20, 38, 41, 1);
                rect(canvas, 16, 26, 32, 41, 1);
                line(canvas, 17, 31, 31, 31);
                line(canvas, 17, 36, 31, 36);
                break;
            }
            case "workshop":
                line(canvas, 12, 36, 36, 12);
                canvas.drawCircle(12, 36, 6, pen);
                canvas.drawCircle(36, 12, 6, pen);
                break;
            case "box":
                rect(canvas, 10, 18, 38, 39, 2);
                rect(canvas, 8, 11, 40, 19, 2);
                line(canvas, 20, 26, 28, 26);
                break;
            default:
                canvas.drawCircle(24, 24, 12, pen);
                break;
        }
        canvas.restore();
    }
}
