package com.edwinkarolczyk.edhome;

import java.util.List;

public final class PantryLookupCodesSmoke {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++;
    }
    private static void candidates(String code, String... expected) {
        List<String> codes = PantryLookupCodes.candidates(code);
        check(codes.size() == expected.length, code + " variant count: " + codes);
        for (int i = 0; i < expected.length; i++)
            check(expected[i].equals(codes.get(i)), code + " variant " + i);
    }
    public static void main(String[] args) {
        candidates("036000291452","036000291452","0036000291452","00036000291452");
        candidates("0036000291452","0036000291452","036000291452","00036000291452");
        candidates("00036000291452","00036000291452","0036000291452","036000291452");
        candidates("5901234123457","5901234123457","05901234123457");
        candidates("96385074","96385074");
        try {
            PantryLookupCodes.candidates("1234567890123");
            throw new AssertionError("invalid accepted");
        } catch (IllegalArgumentException expected) { passed++; }
        System.out.println("Pantry alternate barcode lookup: PASS " + passed);
    }
}
