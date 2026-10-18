package com.edwinkarolczyk.edhome;

/** Regresja 1-sekundowego pulsowania aktywnej karty Projektu. */
public final class ProjectTaskPulseSmoke {
    private static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int light=0xFFF7F7FA;
        int dark=0xFF22272D;
        int blueLight=ProjectTaskPulse.blueSurface(light,true);
        int blueDark=ProjectTaskPulse.blueSurface(dark,false);

        check(blueLight!=light && blueDark!=dark,
            "Aktywna czynność musi mieć zauważalny odcień niebieski.");
        check((blueLight>>>24)==255 && (blueDark>>>24)==255,
            "Tło ma pozostać w pełni nieprzezroczyste.");
        check(ProjectTaskPulse.isBlue(0) && !ProjectTaskPulse.isBlue(1)
                && ProjectTaskPulse.isBlue(2),
            "W każdej sekundzie faza ma się zmieniać.");
        check(ProjectTaskPulse.color(light,blueLight,0)==blueLight,
            "Start powinien od razu podświetlić kartę.");
        check(ProjectTaskPulse.color(light,blueLight,1)==light,
            "Po sekundzie wraca zwykły kolor.");
        check(ProjectTaskPulse.color(light,blueLight,2)==blueLight,
            "Po dwóch sekundach znów niebieski.");
        check(ProjectTaskPulse.color(dark,blueDark,1)==dark,
            "Motyw ciemny wraca do własnego zwykłego tła.");
        check((blueLight & 255) > ((blueLight >>> 16)&255),
            "Kolor musi zachować dominującą składową niebieską.");
        System.out.println("ProjectTaskPulse: 1s, jasny/ciemny, fazy i przywracanie PASS");
    }
}
