package com.edhome.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Wplywy i wydatki z wyciagu nie moga zmieniac kierunku przez typ minusa. */
final class DesktopBankAmountSignTest {
    @TempDir Path temp;

    @Test void csvRespectsPlusAndBothMinusCharacters() throws Exception {
        Path file = temp.resolve("history.csv");
        Files.writeString(file,
            "Data;Kwota;Id transakcji;Opis\n"
            + "2026-10-01;+ 100,00;ref-income-01;Wyplata\n"
            + "2026-10-02;- 25,00;ref-expense-01;Zakupy\n"
            + "2026-10-03;− 12,50;ref-expense-02;Rachunek\n",
            StandardCharsets.UTF_8);
        DesktopBankImporter.Result result = DesktopBankImporter.read(file, "Test");
        assertEquals(3, result.entries.size());
        assertEquals("income", result.entries.get(0).kind);
        assertEquals(10000L, result.entries.get(0).amountGrosz);
        assertEquals("expense", result.entries.get(1).kind);
        assertEquals(2500L, result.entries.get(1).amountGrosz);
        assertEquals("expense", result.entries.get(2).kind);
        assertEquals(1250L, result.entries.get(2).amountGrosz);
    }
}
