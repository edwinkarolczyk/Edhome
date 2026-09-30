package com.edwinkarolczyk.edhome;

import java.util.List;

public final class BankStatementVeloPdfSmoke {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String card="VeloBank\nPotwierdzenie transakcji\n"
            +"Data transakcji: 24.09.2026\n"
            +"Kwota transakcji: 60,10 PLN\n"
            +"Transakcja kartą\n"
            +"Odbiorca: STACJA PALIW AELIN\n";
        List<BankStatementCsv.Entry> one=BankStatementVeloPdf.parse(card);
        check(one.size()==1,"one card confirmation");
        check("2026-09-24".equals(one.get(0).date),"date");
        check("expense".equals(one.get(0).kind),"expense");
        check(one.get(0).amountGrosz==6010,"amount");
        check(one.get(0).evidenceKey.matches("[0-9a-f]{64}"),"key");

        String incoming="VeloBank\nPotwierdzenie operacji\n"
            +"Data operacji: 25.09.2026\n"
            +"Kwota operacji: +1,00 PLN\n"
            +"Przelew przychodzący\n"
            +"Tytuł: TEST\n";
        List<BankStatementCsv.Entry> two=BankStatementVeloPdf.parse(incoming);
        check(two.size()==1&&"income".equals(two.get(0).kind)
            &&two.get(0).amountGrosz==100,"income");

        boolean rejected=false;
        try {
            BankStatementVeloPdf.parse("VeloBank\n24.09.2026 60,10 PLN Saldo 100,00 PLN");
        } catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected,"ambiguous unsigned amounts rejected");
        System.out.println("VeloBank text PDF parser: conservative manual-evidence candidates PASS");
    }
}
