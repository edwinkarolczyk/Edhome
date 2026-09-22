package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Check common UPC-A / EAN-13 / GTIN-14 representations without changing stock keys. */
final class PantryLookupCodes {
    private PantryLookupCodes() { }

    static List<String> candidates(String raw) {
        if (!PantryScanRules.validBarcode(raw))
            throw new IllegalArgumentException("Nieprawidłowy kod produktu.");
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.add(raw);
        if (raw.length() == 12) {
            codes.add("0" + raw);
            codes.add("00" + raw);
        } else if (raw.length() == 13) {
            if (raw.startsWith("0")) codes.add(raw.substring(1));
            codes.add("0" + raw);
        } else if (raw.length() == 14) {
            if (raw.startsWith("0")) codes.add(raw.substring(1));
            if (raw.startsWith("00")) codes.add(raw.substring(2));
        }
        List<String> validated = new ArrayList<>();
        for (String code : codes)
            if (PantryScanRules.validBarcode(code)) validated.add(code);
        return validated;
    }
}
