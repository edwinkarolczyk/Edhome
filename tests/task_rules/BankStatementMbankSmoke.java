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

        // Real XLSX shape: separate columns, Excel serial dates and omitted
        // blank E/F cells. The workbook reader must preserve column positions.
        String multi="<worksheet><sheetData>"
            +"<row r=\"1\">"
            +"<c r=\"A1\" t=\"inlineStr\"><is><t>#Data księgowania</t></is></c>"
            +"<c r=\"B1\" t=\"inlineStr\"><is><t>#Data operacji</t></is></c>"
            +"<c r=\"C1\" t=\"inlineStr\"><is><t>#Opis operacji</t></is></c>"
            +"<c r=\"D1\" t=\"inlineStr\"><is><t>#Tytuł</t></is></c>"
            +"<c r=\"E1\" t=\"inlineStr\"><is><t>#Nadawca/Odbiorca</t></is></c>"
            +"<c r=\"F1\" t=\"inlineStr\"><is><t>#Numer konta</t></is></c>"
            +"<c r=\"G1\" t=\"inlineStr\"><is><t>#Kwota</t></is></c>"
            +"<c r=\"H1\" t=\"inlineStr\"><is><t>#Saldo po operacji</t></is></c>"
            +"</row>"
            +"<row r=\"2\">"
            +"<c r=\"A2\"><v>46289</v></c>"
            +"<c r=\"B2\"><v>46289</v></c>"
            +"<c r=\"C2\" t=\"inlineStr\"><is><t>PŁATNOŚĆ KARTĄ</t></is></c>"
            +"<c r=\"D2\" t=\"inlineStr\"><is><t>STACJA TEST</t></is></c>"
            // E2 and F2 intentionally absent
            +"<c r=\"G2\"><v>-60.10</v></c>"
            +"<c r=\"H2\"><v>939.90</v></c>"
            +"</row></sheetData></worksheet>";
        ByteArrayOutputStream multiOut=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(multiOut)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(multi.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String reconstructed=BankStatementWorkbook.textRows(multiOut.toByteArray());
        assertTrue(reconstructed.startsWith("#Data księgowania;#Data operacji;"),
            "multi-column header must not be quoted");
        List<BankStatementCsv.Entry> multiRows=BankStatementMbank.parse(reconstructed);
        assertTrue(multiRows.size()==1
            &&multiRows.get(0).date.equals("2026-09-24")
            &&multiRows.get(0).amountGrosz==6010
            &&multiRows.get(0).kind.equals("expense"),
            "real XLSX columns, blanks and Excel serial date");
        System.out.println(
            "mBank: metadata, dates, booked balances, repeated purchases, CSV/XLSX: PASS");
    }
}
