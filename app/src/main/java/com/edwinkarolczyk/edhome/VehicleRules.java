package com.edwinkarolczyk.edhome;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Pure offline validation shared by vehicle UI and backup import. */
final class VehicleRules {
    private VehicleRules() { }

    static String name(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty() || clean.length() > 120)
            throw new IllegalArgumentException("Nazwa pojazdu: 1–120 znaków.");
        return clean;
    }

    static String registration(String input) {
        String clean = input == null ? "" : input.trim().toUpperCase(java.util.Locale.ROOT);
        if (clean.length() > 20 || !clean.matches("[A-Z0-9 -]*"))
            throw new IllegalArgumentException("Nieprawidłowy numer rejestracyjny.");
        return clean;
    }

    static String optionalDate(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty()) return "";
        try {
            LocalDate date = LocalDate.parse(clean);
            if (date.getYear() < 1900 || date.getYear() > 2200)
                throw new IllegalArgumentException("Data poza obsługiwanym zakresem.");
            return date.toString();
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Wpisz datę RRRR-MM-DD.");
        }
    }

    static long mileage(String input) {
        String clean = input == null ? "" : input.trim();
        if (!clean.matches("[0-9]{1,9}"))
            throw new IllegalArgumentException("Przebieg: 0–999999999 km.");
        long value = Long.parseLong(clean);
        if (value > 999999999L) throw new IllegalArgumentException("Zbyt duży przebieg.");
        return value;
    }

    static String eventType(String kind) {
        if (!("service".equals(kind) || "tyres".equals(kind) || "other".equals(kind)))
            throw new IllegalArgumentException("Nieznany rodzaj wpisu.");
        return kind;
    }

    static String eventLabel(String kind) {
        switch (eventType(kind)) {
            case "service": return "Serwis";
            case "tyres": return "Opony";
            default: return "Inne";
        }
    }

    static String note(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.isEmpty() || clean.length() > 500)
            throw new IllegalArgumentException("Opis: 1–500 znaków.");
        return clean;
    }
}
