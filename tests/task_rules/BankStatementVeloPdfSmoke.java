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
        // W części wyciągów waluta występuje tylko w nagłówku tabeli.
        String currencyHeader="VeloBank\nHistoria operacji PLN\n"
            +"05.10.2026 Operacja kartą -6,50\n"
            +"06.10.2026 Operacja kartą -7,50\n"
            +"09.10.2026 Przelew wychodzący do TEST -35,49\n";
        List<BankStatementCsv.Entry> unsignedCurrency=
            BankStatementVeloPdf.parse(currencyHeader);
        check(unsignedCurrency.size()==3,"PDF with PLN only in header");
        check("2026-10-09".equals(unsignedCurrency.get(2).date),
            "no-currency transfer date");
        check(unsignedCurrency.get(2).amountGrosz==3549
            &&"expense".equals(unsignedCurrency.get(2).kind),
            "no-currency transfer amount and direction");

        String currencySplit="VeloBank\nHistoria operacji PLN\n"
            +"05.10.2026 Operacja kartą -6,50\n"
            +"06.10.2026 Operacja kartą -7,50\n"
            +"09.10.2026\n"
            +"Przelew przychodzący od PRACODAWCY\n"
            +"Kwota: +4 300,00\n";
        List<BankStatementCsv.Entry> splitWithoutPln=
            BankStatementVeloPdf.parse(currencySplit);
        check(splitWithoutPln.size()==3,
            "PDF split transfer with amount and PLN only in header");
        check("income".equals(splitWithoutPln.get(2).kind)
            &&splitWithoutPln.get(2).amountGrosz==430000,
            "salary +4300 from split no-currency PDF");

        boolean noSilentTruncation=false;
        try {
            BankStatementVeloPdf.parse("VeloBank\n"
                +"05.10.2026 Operacja kartą -6,50 PLN\n"
                +"09.10.2026 Przelew wychodzący bez kwoty\n");
        }catch(IllegalArgumentException expected) {
            noSilentTruncation=expected.getMessage().contains("nie dało");
        }
        check(noSilentTruncation,
            "one recognized row and one unreadable bank transfer MUST fail");

        boolean noSaldoGuess=false;
        try {
            BankStatementVeloPdf.parse("VeloBank\n"
                +"05.10.2026 Operacja kartą -6,50 PLN\n"
                +"09.10.2026 Przelew wychodzący\n"
                +"Saldo po operacji -199,99\n");
        }catch(IllegalArgumentException expected) {
            noSaldoGuess=true;
        }
        check(noSaldoGuess,"do not use signed balance as transfer amount");

        // Regresja z wyciągu: dwie daty w wierszu, opis i kwota
        // przelewu oddzielone od siebie, PLN tylko w nagłówku tabeli.
        String multiLine="VeloBank\nHistoria PLN\n"
            +"07.10.2026 08.10.2026\n"
            +"Przelew przychodzący\n"
            +"Od nadawcy\n"
            +"Wynagrodzenie\n"
            +"Numer referencyjny\n"
            +"Za październik\n"
            +"Kwota przelewu: 4 300,00\n"
            +"09.10.2026 Przelew wychodzący\n"
            +"Tytuł: rachunek\n"
            +"Kwota przelewu: 280,00\n";
        List<BankStatementCsv.Entry> varied=
            BankStatementVeloPdf.parse(multiLine);
        check(varied.size()==2,"both multiline Velo transfers without signs");
        check("2026-10-07".equals(varied.get(0).date),
            "keep first source date until booking/operation field confirmed");
        check("income".equals(varied.get(0).kind)
            &&varied.get(0).amountGrosz==430000,"unsigned labeled salary");
        check("expense".equals(varied.get(1).kind)
            &&varied.get(1).amountGrosz==28000,"unsigned labeled bill");
        check(BankStatementVeloPdf.parse(multiLine).get(0).evidenceKey
            .equals(varied.get(0).evidenceKey),"multiline import stable key");

        // Jeżeli siedem pozycji pozostanie nierozpoznanych, komunikat
        // musi podać powody, bez ujawniania treści operacji.
        StringBuilder seven=new StringBuilder("VeloBank\nHistoria PLN\n")
            .append("05.10.2026 Operacja kartą -6,50 PLN\n");
        for(int n=0;n<7;n++)
            seven.append("08.10.2026 Przelew wychodzący\n");
        boolean sevenReported=false;
        try {BankStatementVeloPdf.parse(seven.toString());}
        catch(IllegalArgumentException expected) {
            sevenReported=expected.getMessage().contains("7 potencjalnych")
                &&expected.getMessage().contains("brak jednoznacznej kwoty 7");
        }
        check(sevenReported,"report full count and category of 7 unreadable rows");

        String balanceOnly="VeloBank\nHistoria PLN\n"
            +"08.10.2026 Przelew wychodzący\n"
            +"Saldo po operacji +4 300,00\n";
        boolean balanceRejected=false;
        try {BankStatementVeloPdf.parse(balanceOnly);}
        catch(IllegalArgumentException expected){balanceRejected=true;}
        check(balanceRejected,"do not treat a signed balance as transaction amount");

        System.out.println("VeloBank text PDF parser: conservative manual-evidence candidates PASS");
    }
}
