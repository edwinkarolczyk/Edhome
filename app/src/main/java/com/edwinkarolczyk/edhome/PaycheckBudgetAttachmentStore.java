package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local image/PDF attachments for monthly budget items. */
final class PaycheckBudgetAttachmentStore {
    static final String PREF_KEY = "paycheck_budget_attachments_v1";
    static final String DIRECTORY = "paycheck-budget-attachments";
    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final int MAX_ATTACHMENTS = 300;
    static final int MAX_PER_ITEM = 10;

    static final class Attachment {
        String id;
        String itemId;
        String displayName;
        String mime;
        String fileName;
        long createdAt;
    }

    private PaycheckBudgetAttachmentStore() { }

    static File folder(Context context) {
        File folder = new File(context.getFilesDir(),DIRECTORY);
        if (!folder.exists() && !folder.mkdirs())
            throw new IllegalStateException("Nie utworzono katalogu załączników.");
        return folder;
    }

    static Attachment add(Context context, SharedPreferences prefs,
            String itemId, Uri source) throws Exception {
        if (itemId == null || !itemId.matches("[0-9a-fA-F-]{36}") || source == null)
            throw new IllegalArgumentException("Nieprawidłowy załącznik.");
        List<Attachment> all=load(prefs);
        int count=0;
        for (Attachment attachment:all)
            if (itemId.equals(attachment.itemId)) count++;
        if (count>=MAX_PER_ITEM)
            throw new IllegalArgumentException("Maksymalnie 10 załączników do jednej pozycji.");
        if (all.size()>=MAX_ATTACHMENTS)
            throw new IllegalArgumentException("Za dużo załączników Budżetu miesiąca.");

        String mime=context.getContentResolver().getType(source);
        if (mime==null) mime="";
        String displayName=displayName(context,source);
        String lower=displayName.toLowerCase(java.util.Locale.ROOT);
        boolean image=mime.startsWith("image/")
            || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".png") || lower.endsWith(".webp");
        boolean pdf="application/pdf".equals(mime) || lower.endsWith(".pdf");
        if (!image && !pdf)
            throw new IllegalArgumentException("Dozwolone są zdjęcia i pliki PDF.");
        if (mime.isEmpty()) mime=pdf?"application/pdf":"image/*";

        Attachment attachment=new Attachment();
        attachment.id=UUID.randomUUID().toString();
        attachment.itemId=itemId;
        attachment.displayName=safeDisplayName(displayName,
            pdf?"faktura.pdf":"faktura.jpg");
        attachment.mime=mime;
        attachment.fileName=attachment.id+(pdf?".pdf":".img");
        attachment.createdAt=System.currentTimeMillis();
        validate(attachment);

        File target=new File(folder(context),attachment.fileName);
        long total=0L;
        try (InputStream input=context.getContentResolver().openInputStream(source);
             FileOutputStream output=new FileOutputStream(target)) {
            if (input==null)
                throw new IllegalArgumentException("Nie można odczytać załącznika.");
            byte[] buffer=new byte[8192];
            int read;
            while((read=input.read(buffer))!=-1) {
                total+=read;
                if (total>MAX_FILE_BYTES)
                    throw new IllegalArgumentException(
                        "Załącznik przekracza 20 MB.");
                output.write(buffer,0,read);
            }
            output.flush();
        } catch(Exception error) {
            target.delete();
            throw error;
        }
        if (total<=0L) {
            target.delete();
            throw new IllegalArgumentException("Załącznik jest pusty.");
        }
        all.add(attachment);
        if (!prefs.edit().putString(PREF_KEY,serialize(all)).commit()) {
            target.delete();
            throw new IllegalStateException("Nie zapisano załącznika.");
        }
        return attachment;
    }

    static List<Attachment> list(SharedPreferences prefs,String itemId)
            throws Exception {
        List<Attachment> result=new ArrayList<>();
        for (Attachment attachment:load(prefs))
            if (itemId.equals(attachment.itemId)) result.add(attachment);
        result.sort((a,b)->Long.compare(a.createdAt,b.createdAt));
        return result;
    }

    static List<Attachment> load(SharedPreferences prefs) throws Exception {
        return parse(prefs.getString(PREF_KEY,"[]"));
    }

    static File file(Context context,Attachment attachment) {
        return new File(folder(context),attachment.fileName);
    }

    static Uri uri(Context context,Attachment attachment) {
        File file=file(context,attachment);
        if (!file.isFile()) throw new IllegalStateException("Brak pliku załącznika.");
        return FileProvider.getUriForFile(context,
            context.getPackageName()+".storage.files",file);
    }

    static boolean remove(Context context,SharedPreferences prefs,String id)
            throws Exception {
        List<Attachment> all=load(prefs);
        Attachment found=null;
        for (Attachment attachment:all)
            if (attachment.id.equals(id)) { found=attachment; break; }
        if (found==null) return false;
        all.remove(found);
        if (!prefs.edit().putString(PREF_KEY,serialize(all)).commit())
            throw new IllegalStateException("Nie zapisano usunięcia załącznika.");
        File file=file(context,found);
        if (file.exists() && !file.delete())
            DiagnosticLog.event("PAYCHECK_ATTACHMENT_FILE_DELETE_FAILED");
        return true;
    }

    static List<File> liveFiles(Context context,SharedPreferences prefs)
            throws Exception {
        List<File> result=new ArrayList<>();
        for (Attachment attachment:load(prefs)) {
            File file=file(context,attachment);
            if (file.isFile() && file.length()>0L && file.length()<=MAX_FILE_BYTES)
                result.add(file);
        }
        return result;
    }

    static String serialized(SharedPreferences prefs) throws Exception {
        return serialize(load(prefs));
    }

    static void validateSerialized(String raw) throws Exception { parse(raw); }

    static void prune(Context context,SharedPreferences prefs) {
        try {
            java.util.HashSet<String> expected=new java.util.HashSet<>();
            for (Attachment attachment:load(prefs)) expected.add(attachment.fileName);
            File[] files=folder(context).listFiles();
            if (files!=null) for (File file:files)
                if (file.isFile() && !expected.contains(file.getName())) file.delete();
        } catch(Exception error) {
            DiagnosticLog.error("PAYCHECK_ATTACHMENT_PRUNE",error);
        }
    }

    private static String displayName(Context context,Uri uri) {
        String result="";
        try (Cursor cursor=context.getContentResolver().query(
                uri,new String[]{OpenableColumns.DISPLAY_NAME},
                null,null,null)) {
            if (cursor!=null && cursor.moveToFirst() && !cursor.isNull(0))
                result=cursor.getString(0);
        } catch(Exception ignored) { }
        if (result==null || result.isBlank())
            result=uri.getLastPathSegment();
        return result==null?"":result;
    }

    private static String safeDisplayName(String value,String fallback) {
        String clean=value==null?"":value.trim()
            .replace('\n',' ').replace('\r',' ');
        if (clean.isEmpty()) clean=fallback;
        if (clean.length()>120) clean=clean.substring(0,120);
        return clean;
    }

    private static List<Attachment> parse(String raw) throws Exception {
        if (raw==null || raw.length()>500000 || raw.indexOf('\0')>=0)
            throw new IllegalArgumentException("Nieprawidłowa lista załączników.");
        JSONArray array=new JSONArray(raw);
        if (array.length()>MAX_ATTACHMENTS)
            throw new IllegalArgumentException("Za dużo załączników.");
        List<Attachment> result=new ArrayList<>();
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        java.util.HashSet<String> files=new java.util.HashSet<>();
        for (int i=0;i<array.length();i++) {
            JSONObject json=array.getJSONObject(i);
            Attachment attachment=new Attachment();
            attachment.id=json.getString("id");
            attachment.itemId=json.getString("itemId");
            attachment.displayName=json.getString("displayName");
            attachment.mime=json.getString("mime");
            attachment.fileName=json.getString("fileName");
            attachment.createdAt=json.getLong("createdAt");
            validate(attachment);
            if (!ids.add(attachment.id) || !files.add(attachment.fileName))
                throw new IllegalArgumentException("Duplikat załącznika.");
            result.add(attachment);
        }
        return result;
    }

    private static String serialize(List<Attachment> attachments) throws Exception {
        JSONArray array=new JSONArray();
        for (Attachment attachment:attachments) {
            validate(attachment);
            JSONObject json=new JSONObject();
            json.put("id",attachment.id);
            json.put("itemId",attachment.itemId);
            json.put("displayName",attachment.displayName);
            json.put("mime",attachment.mime);
            json.put("fileName",attachment.fileName);
            json.put("createdAt",attachment.createdAt);
            array.put(json);
        }
        return array.toString();
    }

    private static void validate(Attachment attachment) {
        if (attachment==null
                || attachment.id==null
                || !attachment.id.matches("[0-9a-fA-F-]{36}")
                || attachment.itemId==null
                || !attachment.itemId.matches("[0-9a-fA-F-]{36}")
                || attachment.displayName==null
                || attachment.displayName.isBlank()
                || attachment.displayName.length()>120
                || attachment.mime==null || attachment.mime.length()>80
                || attachment.fileName==null
                || !attachment.fileName.matches(
                    "[0-9a-fA-F-]{36}\\.(?:pdf|img)")
                || attachment.createdAt<=0L)
            throw new IllegalArgumentException("Nieprawidłowy załącznik budżetu.");
    }
}
