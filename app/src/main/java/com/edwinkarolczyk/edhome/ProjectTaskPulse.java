package com.edwinkarolczyk.edhome;

/** Kolor pulsowania aktywnej czynności; bez osobnego timera i zależności Android UI. */
final class ProjectTaskPulse {
    private static final int BLUE = 0xFF1976D2;

    private ProjectTaskPulse() { }

    /** Tło zmienia się raz na sekundę: niebieski / zwykły / niebieski. */
    static boolean isBlue(long tick) {
        return (tick & 1L) == 0L;
    }

    /** Czytelny odcień na motywie jasnym i ciemnym. */
    static int blueSurface(int surface, boolean light) {
        float amount = light ? 0.35f : 0.46f;
        int red = mix((surface >>> 16) & 255, (BLUE >>> 16) & 255, amount);
        int green = mix((surface >>> 8) & 255, (BLUE >>> 8) & 255, amount);
        int blue = mix(surface & 255, BLUE & 255, amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    static int color(int original, int blue, long tick) {
        return isBlue(tick) ? blue : original;
    }

    private static int mix(int background, int tint, float fraction) {
        return Math.round(background * (1f - fraction) + tint * fraction);
    }
}
