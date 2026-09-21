package com.edwinkarolczyk.edhome;

public final class PantryScanRulesSmoke {
    private static int passed = 0;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        passed++;
    }
    public static void main(String[] args) {
        check(PantryScanRules.validBarcode("5901234123457"), "EAN-13");
        check(PantryScanRules.validBarcode("96385074"), "EAN-8");
        check(PantryScanRules.validBarcode("036000291452"), "UPC-A and leading zero");
        check(PantryScanRules.validBarcode("00012345600012"), "GTIN-14");
        check(!PantryScanRules.validBarcode("5901234123458"), "bad checksum");
        check(!PantryScanRules.validBarcode("590123412345x"), "not digits");
        check(!PantryScanRules.validBarcode("123"), "bad length");
        check(!PantryScanRules.validBarcode(null), "null code");
        check(!PantryScanRules.validBarcode("5901234123457 "), "spaces");
        System.out.println("Pantry scanner barcode rules: PASS " + passed);
    }
}
