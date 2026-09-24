package com.edwinkarolczyk.edhome;
import java.util.List;
final class BankStatementCsvSmoke {
    private static void fail(String message) { throw new AssertionError(message); }
    private static void invalid(String csv) {
        try { BankStatementCsv.parse(csv,"MojBank");fail("Accepted invalid CSV"); }
        catch(IllegalArgumentException expected) { /* Safe rejection. */ }
    }
    public static void main(String[] args) {
        String csv="Data;Kwota;Id transakcji;Opis\n"
            +"23.09.2026;-650,00;bank-001;OC Audi\n"
            +"2026-09-24;+12.50;bank-002;\"Paliwo; test\"\n";
        List<BankStatementCsv.Entry> rows=BankStatementCsv.parse(csv,"MojBank");
        if(rows.size()!=2 || !rows.get(0).date.equals("2026-09-23")
                || !rows.get(0).kind.equals("expense")
                || rows.get(0).amountGrosz!=65000
                || !rows.get(1).kind.equals("income")
                || rows.get(1).amountGrosz!=1250
                || !rows.get(1).description.equals("Paliwo; test"))
            fail("Wrong date, money, sign or quoted separator");
        if(!rows.get(0).evidenceKey.equals(
                BankStatementCsv.parse(csv,"MojBank").get(0).evidenceKey))
            fail("Re-import must be idempotent");
        if(rows.get(0).evidenceKey.equals(
                BankStatementCsv.parse(csv,"Inny bank").get(0).evidenceKey))
            fail("Bank namespace collision");
        invalid("Data;Kwota;Id transakcji;Opis\n2026-09-23;-1,00;;test\n");
        invalid("Data;Kwota;Id transakcji;Opis\n2026-09-23;0;bank-001;test\n");
        invalid("Data;Kwota;Id transakcji;Opis\n2026-09-23;-1,999;bank-001;test\n");
        invalid("Data;Kwota;Id transakcji;Opis\n2026-09-23;-1,00;bank-001;test\n"
            +"2026-09-24;-1,00;bank-001;test\n");
        System.out.println("Bank CSV: exact grosze, sign/date, bank-key dedupe and invalid-input PASS");
    }
}
