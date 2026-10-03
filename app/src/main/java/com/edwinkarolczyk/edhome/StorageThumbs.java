package com.edwinkarolczyk.edhome;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Prywatna miniatura wybrana przez użytkownika, bez zależności od URI oryginału.
 * Wyraźny JPEG jest przechowywany jako Base64 w prywatnych preferencjach i kopii JSON.
 */
final class StorageThumbs {
    static final String PREFIX="storage_thumb_v1_";
    static final int MAX_EDGE_PX=1024;
    static final int MAX_JPEG_BYTES=256*1024;
    static final int MAX_BASE64_CHARS=360000;
    private StorageThumbs(){}

    static String key(long itemId){return PREFIX+itemId;}

    static int prune(SharedPreferences prefs,SQLiteDatabase db) {
        Set<Long> live=new HashSet<>();
        try(Cursor cursor=db.rawQuery("SELECT id FROM storage_items",null)) {
            while(cursor.moveToNext())live.add(cursor.getLong(0));
        }
        SharedPreferences.Editor editor=null;
        int removed=0;
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet()) {
            String key=entry.getKey();
            if(!key.startsWith(PREFIX))continue;
            long id;
            try{id=Long.parseLong(key.substring(PREFIX.length()));}
            catch(Exception invalid){id=-1;}
            if(id>0&&live.contains(id))continue;
            if(editor==null)editor=prefs.edit();
            editor.remove(key);
            removed++;
        }
        if(editor!=null)editor.apply();
        return removed;
    }

    static Bitmap read(SharedPreferences prefs,long itemId) {
        try {
            String value=prefs.getString(key(itemId),"");
            if(value.isEmpty()||value.length()>MAX_BASE64_CHARS)return null;
            byte[] bytes=Base64.decode(value,Base64.NO_WRAP);
            if(bytes.length<4||bytes.length>MAX_JPEG_BYTES)return null;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        }catch(Exception invalid){return null;}
    }

    static String compress(ContentResolver resolver,Uri uri)throws Exception {
        BitmapFactory.Options probe=new BitmapFactory.Options();
        probe.inJustDecodeBounds=true;
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Nie można odczytać zdjęcia.");
            BitmapFactory.decodeStream(in,null,probe);
        }
        if(probe.outWidth<1||probe.outHeight<1
                ||probe.outWidth>20000||probe.outHeight>20000)
            throw new IllegalArgumentException("Nieobsługiwany format zdjęcia.");
        BitmapFactory.Options sample=new BitmapFactory.Options();
        sample.inSampleSize=1;
        while(probe.outWidth/sample.inSampleSize>MAX_EDGE_PX*2
                ||probe.outHeight/sample.inSampleSize>MAX_EDGE_PX*2)
            sample.inSampleSize*=2;
        Bitmap original;
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Nie można odczytać zdjęcia.");
            original=BitmapFactory.decodeStream(in,null,sample);
        }
        if(original==null)throw new IllegalArgumentException("Nie rozpoznano zdjęcia.");
        try { return compress(original); }
        finally { original.recycle(); }
    }

    /** Normalizuje obraz do wyraźnej miniatury zachowującej szczegóły przy podglądzie. */
    static String compress(Bitmap original)throws Exception {
        if(original==null||original.getWidth()<1||original.getHeight()<1)
            throw new IllegalArgumentException("Nie rozpoznano zdjęcia.");
        int w=original.getWidth(),h=original.getHeight();
        double scale=Math.min(1.0,MAX_EDGE_PX/(double)Math.max(w,h));
        Bitmap small=scale<1.0?Bitmap.createScaledBitmap(original,
            Math.max(1,(int)Math.round(w*scale)),
            Math.max(1,(int)Math.round(h*scale)),true):original;
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            for(int quality:new int[]{92,88,84,80,76,72,68}) {
                out.reset();
                small.compress(Bitmap.CompressFormat.JPEG,quality,out);
                if(out.size()<=MAX_JPEG_BYTES)break;
            }
            if(out.size()==0||out.size()>MAX_JPEG_BYTES)
                throw new IllegalArgumentException(
                    "Zdjęcie jest zbyt złożone. Wybierz inne zdjęcie.");
            return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        } finally {
            if(small!=original)small.recycle();
        }
    }
}
