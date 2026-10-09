package com.edwinkarolczyk.edhome;

import java.util.List;

/** Test dzialania parsera: kierunek kwoty nie moze zniknac. */
public final class BankStatementSignsSmoke {
    private static void require(boolean ok,String name) {
        if(!ok)throw new AssertionError(name);
    }
    public static void main(String[] args) {
        String csv="Data;Kwota;Id transakcji;Opis\n"
            +"2026-10-01;+ 100,00;test-plus-001;Wplyw\n"
            +"2026-10-02;- 25,00;test-minus-001;Wydatek\n"
            +"2026-10-03;− 12,50;test-unicode-001;Wydatek minus Unicode\n";
        List<BankStatementCsv.Entry> rows=BankStatementCsv.parse(csv,"Test bank");
        require(rows.size()==3,"Brak jednej z transakcji CSV");
        require("income".equals(rows.get(0).kind)&&rows.get(0).amountGrosz==10000L,
            "Wpływ + w CSV");
        require("expense".equals(rows.get(1).kind)&&rows.get(1).amountGrosz==2500L,
            "Wydatek - w CSV");
        require("expense".equals(rows.get(2).kind)&&rows.get(2).amountGrosz==1250L,
            "Wydatek Unicode minus w CSV");
        String mbank="#Numer rachunku;12345678901234567890123456\n"
            +"#Data księgowania;#Data operacji;#Kwota;#Saldo po operacji;#Opis operacji\n"
            +"01.10.2026;01.10.2026;−25,00;+200,00;Zakupy\n"
            +"02.10.2026;02.10.2026;+100,00;+300,00;Wynagrodzenie\n";
        List<BankStatementCsv.Entry> m=BankStatementMbank.parse(mbank);
        require(m.size()==2,"Niekompletny import mBanku");
        require("expense".equals(m.get(0).kind)&&m.get(0).amountGrosz==2500L,
            "Wydatek Unicode minus mBank");
        require("income".equals(m.get(1).kind)&&m.get(1).amountGrosz==10000L,
            "Wpływ + mBank");
        System.out.println("Bank statement signs: income+, expenses-/−, amounts PASS");
    }
}
