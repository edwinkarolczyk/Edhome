package com.edhome.desktop;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class DesktopHardwareScanner {
    private DesktopHardwareScanner() { }

    static String readCodeFromImage(Path file) throws Exception {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IllegalArgumentException("Nieobsługiwany format obrazu.");
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
            new RGBLuminanceSource(width, height, pixels)));
        Result result = new MultiFormatReader().decode(bitmap);
        String text = result.getText();
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Kod nie zawiera danych.");
        return text.trim();
    }

    static String readNfcUid(Duration timeout) throws Exception {
        List<CardTerminal> terminals = TerminalFactory.getDefault().terminals().list();
        if (terminals.isEmpty())
            throw new IllegalStateException(
                "Windows nie widzi czytnika NFC/Smart Card (PC/SC). Podłącz czytnik i spróbuj ponownie.");
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeout.toMillis());
        while (System.currentTimeMillis() < deadline) {
            for (CardTerminal terminal : terminals) {
                if (!terminal.isCardPresent()) continue;
                return readUid(terminal);
            }
            Thread.sleep(150L);
        }
        throw new IllegalStateException("Nie wykryto tagu NFC w ciągu "
            + Math.max(1L, timeout.toSeconds()) + " s.");
    }

    private static String readUid(CardTerminal terminal) throws Exception {
        Card card = terminal.connect("*");
        try {
            ResponseAPDU response = card.getBasicChannel().transmit(
                new CommandAPDU(new byte[] {(byte)0xFF,(byte)0xCA,0x00,0x00,0x00}));
            if (response.getSW() != 0x9000 || response.getData().length == 0)
                throw new IllegalStateException(
                    "Czytnik wykrył tag, ale nie udostępnił UID (SW="
                        + Integer.toHexString(response.getSW()).toUpperCase() + ").");
            StringBuilder out = new StringBuilder(response.getData().length * 2);
            for (byte value : response.getData())
                out.append(String.format("%02X", value & 0xFF));
            return out.toString();
        } finally {
            try { card.disconnect(false); } catch (Exception ignored) { }
        }
    }
}
