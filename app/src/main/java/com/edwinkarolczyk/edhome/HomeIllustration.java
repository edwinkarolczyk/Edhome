package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Tiny home-and-garden illustration drawn in the active UI palette. */
final class HomeIllustration extends View {
    private final int accent, surface, foreground;
    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);

    HomeIllustration(Context context, UiSkin skin) {
        super(context);
        accent = skin.accent;
        surface = skin.tileBottom;
        foreground = skin.light ? 0xFF376451 : 0xFFE9F9ED;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void use(int color) {
        brush.setColor(color);
        brush.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 84f, getHeight() / 58f);
        use(surface);
        canvas.drawOval(0, 38, 84, 59, brush);
        use(accent);
        canvas.drawCircle(67, 9, 5, brush);
        canvas.drawRect(58, 30, 63, 47, brush);
        canvas.drawCircle(60, 25, 10, brush);
        canvas.drawCircle(51, 30, 7, brush);
        canvas.drawCircle(69, 30, 8, brush);
        Path roof = new Path();
        roof.moveTo(7, 29);
        roof.lineTo(31, 7);
        roof.lineTo(56, 29);
        roof.close();
        canvas.drawPath(roof, brush);
        use(foreground);
        canvas.drawRect(13, 29, 49, 48, brush);
        use(surface);
        canvas.drawRoundRect(28, 34, 38, 48, 2, 2, brush);
        canvas.drawRect(17, 33, 24, 40, brush);
        canvas.restore();
    }
}
