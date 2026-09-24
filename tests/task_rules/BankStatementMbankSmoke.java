package com.edwinkarolczyk.edhome;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic metadata+ledger, not a user's real bank statement. */
final class BankStatementMbankSmoke {
    private static void assertTrue(boolean good,String why) {
        if(!good)throw new AssertionError(why);
    }
    private static void reject(String export) {
        try {BankStatementMbank.parse(export);
            throw new AssertionError("Invalid mBank statement accepted");
        } catch(IllegalArgumentException expected) { /* fail closed */ }
    }

    public static void main(String[] args) throws Exception {
        String text="#Klient;\n#Numer rachunku;0012345678\n"
            +"#Saldo początkowe;100,00 PLN\n"
            +"#Data księgowania;#Data operacji;#Opis operacji;#Tytuł;"
            +"#Nadawca/Odbiorca;#Numer konta;#Kwota;#Saldo po operacji;\n"
            +"2026-09-24;2026-09-22;BLIK ZAKUP E-COMMERCE;Koszyk;"
            +";;-3,50;96,50;\n"
            +"2026-09-24;2026-09-22;BLIK ZAKUP E-COMMERCE;Koszyk;"
            +";;-3,50;93,00;\n"
            +"2026-09-24;2026-09-24;PRZELEW ZEWNĘTRZNY PRZYCHODZĄCY;"
            +"PRZELEW ŚRODKÓW;;;871,00;964,00;\n"
            +"#Saldo końcowe;964,00 PLN\n"
            +"Niniejszy dokument sporządzono na podstawie ustawy.";
        assertTrue(BankStatementMbank.recognizes(text),"mBank detected");
        List<BankStatementCsv.Entry> rows=BankStatementMbank.parse(text);
        assertTrue(rows.size()==3,"skip metadata/footer");
        assertTrue(rows.get(0).kind.equals("expense")
            && rows.get(0).date.equals("2026-09-24")
            && rows.get(0).amountGrosz==350,"date/amount/expense");
        assertTrue(rows.get(2).kind.equals("income")
            && rows.get(2).amountGrosz==87100,"income");
        assertTrue(!rows.get(0).evidenceKey.equals(rows.get(1).evidenceKey),
            "same amount and merchant with different booked balance are distinct");
        assertTrue(rows.get(0).evidenceKey.equals(
            BankStatementMbank.parse(text).get(0).evidenceKey),
            "reimport stable evidence key");
        reject(text.replace("-3,50;96,50;","-3,50;;"));
        reject(text.replace("2026-09-24;2026-09-22","not-a-date;2026-09-22"));
        reject(text.replace("-3,50;93,00;","-3,50;96,50;"));

        String sheet="<worksheet><sheetData>";
        String[] lines=text.split("\n");
        for(int i=0;i<lines.length;i++) {
            sheet+="<row r=\""+(i+1)+"\"><c r=\"A"+(i+1)
                +"\" t=\"inlineStr\"><is><t>"
                +lines[i].replace("&","&amp;").replace("<","&lt;")
                +"</t></is></c></row>";
        }
        sheet+="</sheetData></worksheet>";
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(BankStatementWorkbook.isXlsx(output.toByteArray()),
            "xlsx signature");
        List<BankStatementCsv.Entry> excel=BankStatementMbank.parse(
            BankStatementWorkbook.textRows(output.toByteArray()));
        assertTrue(excel.size()==3
            && excel.get(0).evidenceKey.equals(rows.get(0).evidenceKey),
            "XLSX column A roundtrip");
        System.out.println(
            "mBank: metadata, dates, booked balances, repeated purchases, CSV/XLSX: PASS");
    }
}
