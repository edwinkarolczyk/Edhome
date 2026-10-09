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
        String broken="VeloBank\n"
            +"05.10.2026 Operacja kartą -6,50 PLN\n"
            +"06.10.2026 Operacja kartą -7,50 PLN\n"
            +"09.10.2026 Przelew wychodzący\n";
        boolean splitTransferRejected=false;
        try {BankStatementVeloPdf.parse(broken);}
        catch(IllegalArgumentException expected){splitTransferRejected=true;}
        check(splitTransferRejected,"A split transfer must not disappear silently");
        String splitTransfer="VeloBank\n"
            +"05.10.2026 Operacja kartą -6,50 PLN\n"
            +"06.10.2026 Operacja kartą -7,50 PLN\n"
            +"09.10.2026 Przelew wychodzący do odbiorcy\n"
            +"Kwota przelewu -35,49 PLN\n";
        List<BankStatementCsv.Entry> recovered=
            BankStatementVeloPdf.parse(splitTransfer);
        check(recovered.size()==3,"all three rows including split transfer");
        check("2026-10-09".equals(recovered.get(2).date),"new transfer date");
        check("expense".equals(recovered.get(2).kind),"outgoing transfer");
        check(recovered.get(2).amountGrosz==3549,"transfer exact grosze");
        check(BankStatementVeloPdf.parse(splitTransfer).get(2).evidenceKey
            .equals(recovered.get(2).evidenceKey),"split transfer stable ID");

        String separateDate="VeloBank\n"
            +"05.10.2026 Operacja kartą -6,50 PLN\n"
            +"06.10.2026 Operacja kartą -7,50 PLN\n"
            +"09.10.2026\n"
            +"Przelew wychodzący do odbiorcy\n"
            +"Kwota przelewu -35,49 PLN\n";
        List<BankStatementCsv.Entry> separate=
            BankStatementVeloPdf.parse(separateDate);
        check(separate.size()==3
            && "2026-10-09".equals(separate.get(2).date),
            "Velo transfer with separate date line must be recovered");
        System.out.println("VeloBank text PDF parser: conservative manual-evidence candidates PASS");
    }
}
