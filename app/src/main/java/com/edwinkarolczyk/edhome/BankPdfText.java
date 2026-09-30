package com.edwinkarolczyk.edhome;

import android.content.Context;
import java.lang.reflect.Method;

/**
 * Beta-only local text extraction bridge. Uses reflection so the Stable flavor
 * can compile without bundling the Beta PDF dependency. No OCR and no network.
 */
final class BankPdfText {
    private static final int MAX_PDF_BYTES=8*1024*1024;
    private static final int MAX_TEXT_CHARS=600000;

    private BankPdfText(){}

    static boolean isPdf(byte[] data) {
        return data!=null&&data.length>=5&&data[0]=='%'&&data[1]=='P'
            &&data[2]=='D'&&data[3]=='F'&&data[4]=='-';
    }

    static String extract(Context context,byte[] data) {
        if(!BetaUpdater.isBeta())
            throw new IllegalArgumentException("Import PDF banku jest dostępny tylko w Beta.");
        if(!isPdf(data))throw new IllegalArgumentException("To nie jest plik PDF.");
        if(data.length>MAX_PDF_BYTES)
            throw new IllegalArgumentException("PDF bankowy jest za duży (maks. 8 MB).");
        Object document=null;
        try {
            Class<?> loader=Class.forName(
                "com.tom_roush.pdfbox.android.PDFBoxResourceLoader");
            Method init=loader.getMethod("init",Context.class);
            init.invoke(null,context.getApplicationContext());

            Class<?> pdDocument=Class.forName(
                "com.tom_roush.pdfbox.pdmodel.PDDocument");
            Method load=pdDocument.getMethod("load",byte[].class);
            document=load.invoke(null,(Object)data);

            Class<?> stripperClass=Class.forName(
                "com.tom_roush.pdfbox.text.PDFTextStripper");
            Object stripper=stripperClass.getConstructor().newInstance();
            Method getText=stripperClass.getMethod("getText",pdDocument);
            String text=(String)getText.invoke(stripper,document);
            if(text==null||text.trim().isEmpty())
                throw new IllegalArgumentException(
                    "PDF nie zawiera warstwy tekstowej. Skan obrazu wymaga innego importu.");
            if(text.length()>MAX_TEXT_CHARS)
                throw new IllegalArgumentException("PDF zawiera zbyt dużo tekstu.");
            return text;
        }catch(IllegalArgumentException known){throw known;}
        catch(ClassNotFoundException missing) {
            throw new IllegalArgumentException(
                "Brak lokalnego modułu odczytu PDF w tej kompilacji.",missing);
        }catch(Exception error) {
            Throwable cause=error.getCause();
            if(cause instanceof IllegalArgumentException)
                throw (IllegalArgumentException)cause;
            throw new IllegalArgumentException(
                "Nie można lokalnie odczytać tekstu z PDF.",error);
        }finally {
            if(document!=null)try {
                document.getClass().getMethod("close").invoke(document);
            }catch(Exception ignored){ }
        }
    }
}
