package com.edwinkarolczyk.edhome;

/** Validation for product barcodes. Keep barcodes as text to preserve leading zeros. */
final class PantryScanRules {
    private PantryScanRules() { }

    /** EAN-8, UPC-A, EAN-13 and GTIN-14 with GS1 modulo-10 check digit. */
    static boolean validBarcode(String barcode) {
        if (barcode == null) return false;
        int n = barcode.length();
        if (n != 8 && n != 12 && n != 13 && n != 14) return false;
        int sum = 0;
        for (int i = n - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            char digit = barcode.charAt(i);
            if (digit < '0' || digit > '9') return false;
            sum += (digit - '0') * weight;
        }
        char check = barcode.charAt(n - 1);
        return check >= '0' && check <= '9'
            && (10 - sum % 10) % 10 == check - '0';
    }
}
