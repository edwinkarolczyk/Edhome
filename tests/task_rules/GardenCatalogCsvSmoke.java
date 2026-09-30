package com.edwinkarolczyk.edhome;

import java.util.List;

/** Portable Garden catalog CSV parser/export regression. */
public final class GardenCatalogCsvSmoke {
    public static void main(String[] args) {
        String row=GardenCatalogCsv.row(
            "pomidor.malinowy","Pomidor","Solanum lycopersicum","Malinowy",
            "Moje źródło","https://example.invalid","CC-BY-4.0",
            "2","3","5","5","7","9","50","10",
            "słońce","umiarkowanie","Uwagi; z separatorem");
        String csv=GardenCatalogCsv.HEADER+"\n"+row+"\n";
        List<GardenCatalogCsv.Entry> parsed=GardenCatalogCsv.parse(csv);
        check(parsed.size()==1,"row count");
        GardenCatalogCsv.Entry e=parsed.get(0);
        check(e.key.equals("pomidor.malinowy"),"key");
        check(e.name.equals("Pomidor"),"name");
        check(e.variety.equals("Malinowy"),"variety");
        check(e.sowFrom==2 && e.harvestTo==9,"months");
        check(e.spacing==50 && e.depth==10,"dimensions");
        check(e.notes.equals("Uwagi; z separatorem"),"quoted separator");
        try {
            GardenCatalogCsv.parse(GardenCatalogCsv.HEADER+"\n"
                +row+"\n"+row+"\n");
            throw new AssertionError("duplicate key accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Garden catalog CSV import/export: PASS");
    }

    private static void check(boolean ok,String label) {
        if(!ok) throw new AssertionError(label);
    }
}
