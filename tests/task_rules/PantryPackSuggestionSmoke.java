package com.edwinkarolczyk.edhome;

public final class PantryPackSuggestionSmoke {
    public static void main(String[] args) {
        assertPack("6 x 1,5 l", 6, "l", 1500);
        assertPack("12 × 85 g", 12, "kg", 85);
        assertPack("6 szt.", 6, "szt.", 1000);
        assertPack("500 ml", 1, "l", 500);
        assertPack("4 x 330 ml", 4, "l", 330);
        PantryPackSuggestion none=PantryPackSuggestion.parse("rodzinne opakowanie");
        if(none.unitsPerScan!=1 || !"szt.".equals(none.unit)
                || none.sizeMilli!=1000)
            throw new AssertionError("fallback");
        System.out.println("Pantry pack inference: PASS");
    }

    private static void assertPack(String raw,int units,String unit,long milli) {
        PantryPackSuggestion pack=PantryPackSuggestion.parse(raw);
        if(pack.unitsPerScan!=units || !unit.equals(pack.unit)
                || pack.sizeMilli!=milli)
            throw new AssertionError(raw+" -> "+pack.unitsPerScan+" "
                +pack.unit+" "+pack.sizeMilli);
    }
}
