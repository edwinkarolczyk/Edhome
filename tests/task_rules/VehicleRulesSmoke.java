package com.edwinkarolczyk.edhome;

public final class VehicleRulesSmoke {
    private static int passed;
    private static void check(boolean condition, String caseName) {
        if (!condition) throw new AssertionError(caseName);
        passed++;
    }
    private static void reject(Runnable task, String caseName) {
        try {
            task.run();
            throw new AssertionError("Accepted: " + caseName);
        } catch (IllegalArgumentException expected) { passed++; }
    }
    public static void main(String[] args) {
        check(VehicleRules.name("  Audi A4  ").equals("Audi A4"), "name trimmed");
        reject(() -> VehicleRules.name(" "), "empty vehicle");
        check(VehicleRules.registration(" wd 123 ").equals("WD 123"), "plate");
        reject(() -> VehicleRules.registration("WD/123"), "invalid plate");
        check(VehicleRules.optionalDate("").isEmpty(), "optional date");
        check(VehicleRules.optionalDate("2027-09-01").equals("2027-09-01"), "ISO");
        reject(() -> VehicleRules.optionalDate("2027-02-30"), "invalid calendar date");
        reject(() -> VehicleRules.optionalDate("01.09.2027"), "invalid date format");
        check(VehicleRules.mileage("0")==0, "new car");
        check(VehicleRules.mileage("123456")==123456, "distance");
        reject(() -> VehicleRules.mileage("-1"), "negative distance");
        reject(() -> VehicleRules.mileage("1000000000"), "overflow");
        check(VehicleRules.eventLabel("tyres").equals("Opony"), "event mapping");
        reject(() -> VehicleRules.eventType("insurance_payment"), "no auto accounting");
        reject(() -> VehicleRules.note(" "), "empty history");
        System.out.println("Vehicle rules date, plate, mileage, history: PASS " + passed);
    }
}
