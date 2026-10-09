package com.edwinkarolczyk.edhome;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Long bank exports are fully parsed, not silently cut to 250 or 2,000 rows. */
final class BankStatementLongHistorySmoke {
    private static void check(boolean condition,String explanation) {
        if(!condition)throw new AssertionError(explanation);
    }
    public static void main(String[] args) throws Exception {
        StringBuilder csv=new StringBuilder("Data;Kwota;Id transakcji;Opis\n");
        for(int i=0;i<3500;i++) {
            csv.append("2026-09-24;");
            csv.append(i%2==0?"-1,00;":"+2,00;"); 
            csv.append("long-export-").append(i).append(";Zakup lub wpływ ").append(i).append('\n');
        }
        List<BankStatementCsv.Entry> csvRows=
            BankStatementCsv.parse(csv.toString(),"TestBank");
        check(csvRows.size()==3500,"CSV history truncated");
        check("expense".equals(csvRows.get(0).kind),"CSV debit");
        check("income".equals(csvRows.get(3499).kind),"CSV credit");
        check(csvRows.get(3499).evidenceKey.equals(
            BankStatementCsv.parse(csv.toString(),"TestBank").get(3499).evidenceKey),
            "CSV stable last key");
        check(!csvRows.get(0).evidenceKey.equals(csvRows.get(3499).evidenceKey),
            "CSV unique reference per row");

        StringBuilder mbank=new StringBuilder("#Numer rachunku;123\n");
        mbank.append("#Data księgowania;#Data operacji;#Opis operacji;#Tytuł;")
            .append("#Nadawca/Odbiorca;#Numer konta;#Kwota;#Saldo po operacji;\n");
        for(int i=0;i<1600;i++)
            mbank.append("2026-09-24;2026-09-24;Długi eksport;Poz. ").append(i)
                .append(";;;-1,00;").append(3000-i).append(",00;\n");
        List<BankStatementCsv.Entry> mbRows=BankStatementMbank.parse(mbank.toString());
        check(mbRows.size()==1600,"mBank history truncated");
        check(!mbRows.get(0).evidenceKey.equals(mbRows.get(1599).evidenceKey),
            "mBank dedupe must keep separate rows");

        StringBuilder velotext=new StringBuilder("VeloBank\nData transakcji: 24.09.2026\n")
            .append("Kwota transakcji: -1,00 PLN\n");
        for(int i=0;i<350;i++)
            velotext.append("24.09.2026 -1,00 PLN operacja Velo ")
                .append(i).append('\n');
        List<BankStatementCsv.Entry> pdfRows=
            BankStatementVeloPdf.parse(velotext.toString());
        check(pdfRows.size()==350,
            "Velo history PDF must not be mistaken for one labeled confirmation");

        StringBuilder sheet=new StringBuilder("<worksheet><sheetData>");
        sheet.append("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>")
            .append("#Data księgowania;#Data operacji;#Opis operacji;#Tytuł;")
            .append("#Nadawca/Odbiorca;#Numer konta;#Kwota;#Saldo po operacji;")
            .append("</t></is></c></row>");
        for(int i=1;i<=2200;i++) {
            sheet.append("<row r=\"").append(i+1)
                .append("\"><c r=\"A").append(i+1)
                .append("\" t=\"inlineStr\"><is><t>")
                .append("2026-09-24;2026-09-24;Historia banku;Poz. ")
                .append(i).append(";;;-1,00;")
                .append(3000-i).append(",00;")
                .append("</t></is></c></row>");
        }
        sheet.append("</sheetData></worksheet>");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String rowsText=BankStatementWorkbook.textRows(out.toByteArray());
        check(BankStatementMbank.parse(rowsText).size()==2200,
            "XLSX 2,200 rows truncated or changed");

        boolean refused=false;
        try {
            BankStatementCsv.parse("Data;Kwota;Id transakcji;Opis\n"
                +"2026-09-24;-1,00;ref-001;A\n"
                +"2026-09-24;-1,00;ref-001;B\n","TestBank");
        }catch(IllegalArgumentException expected){refused=true;}
        check(refused,"Bank ID collision cannot be ignored");
        System.out.println("PASS: CSV 3500, mBank 1600, XLSX 2200, Velo PDF 350; full and unique");
    }
}
