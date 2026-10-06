package com.edwinkarolczyk.edhome;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.LruCache;
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
    static final int MAX_EDGE_PX=384;
    static final int LOW_RAM_EDGE_PX=256;
    static final int MAX_JPEG_BYTES=256*1024;
    static final int MAX_BASE64_CHARS=360000;
    private static final int CACHE_KB=8*1024;
    private static final int LOW_RAM_CACHE_KB=2*1024;
    private static volatile boolean lowRamDevice;
    private static volatile int renderEdgePx=MAX_EDGE_PX;
    private static LruCache<Long,CachedBitmap> cache=createCache(CACHE_KB);
    private StorageThumbs(){}

    private static final class CachedBitmap {
        final int sourceHash;
        final int sourceLength;
        final Bitmap bitmap;
        CachedBitmap(int sourceHash,int sourceLength,Bitmap bitmap) {
            this.sourceHash=sourceHash;
            this.sourceLength=sourceLength;
            this.bitmap=bitmap;
        }
    }

    private static LruCache<Long,CachedBitmap> createCache(int maxKb) {
        return new LruCache<Long,CachedBitmap>(maxKb) {
            @Override protected int sizeOf(Long key,CachedBitmap value) {
                if(value==null||value.bitmap==null)return 1;
                return Math.max(1,value.bitmap.getAllocationByteCount()/1024);
            }
        };
    }

    static synchronized void configure(Context context) {
        boolean low=false;
        try {
            ActivityManager manager=(ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
            low=manager!=null&&manager.isLowRamDevice();
        } catch(Exception ignored) { }
        lowRamDevice=low;
        renderEdgePx=low?LOW_RAM_EDGE_PX:MAX_EDGE_PX;
        int maxKb=low?LOW_RAM_CACHE_KB:CACHE_KB;
        if(cache==null||cache.maxSize()!=maxKb) {
            if(cache!=null)cache.evictAll();
            cache=createCache(maxKb);
        }
        DiagnosticLog.event("LOW_RAM_DEVICE",
            "enabled="+lowRamDevice+" thumbEdge="+renderEdgePx
                +" cacheKb="+maxKb);
    }

    static boolean isLowRamDevice(){return lowRamDevice;}
    static int renderEdgePx(){return renderEdgePx;}

    static synchronized void trimMemory(int level) {
        if(cache==null)return;
        if(level>=ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            cache.evictAll();
        } else if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(Math.max(512,cache.maxSize()/2));
        } else return;
        DiagnosticLog.event("THUMBNAIL_CACHE_TRIM",
            "level="+level+" cacheKb="+cache.size());
    }

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
            synchronized(StorageThumbs.class){if(cache!=null)cache.remove(id);}
            removed++;
        }
        if(editor!=null)editor.apply();
        return removed;
    }

    static Bitmap read(SharedPreferences prefs,long itemId) {
        try {
            String value=prefs.getString(key(itemId),"");
            if(value.isEmpty()||value.length()>MAX_BASE64_CHARS)return null;
            int sourceHash=value.hashCode();
            int sourceLength=value.length();
            CachedBitmap cached;
            synchronized(StorageThumbs.class) {
                cached=cache==null?null:cache.get(itemId);
            }
            if(cached!=null&&cached.sourceHash==sourceHash
                    &&cached.sourceLength==sourceLength
                    &&cached.bitmap!=null&&!cached.bitmap.isRecycled())
                return cached.bitmap;

            byte[] bytes=Base64.decode(value,Base64.NO_WRAP);
            if(bytes.length<4||bytes.length>MAX_JPEG_BYTES)return null;

            BitmapFactory.Options probe=new BitmapFactory.Options();
            probe.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,probe);
            if(probe.outWidth<1||probe.outHeight<1)return null;

            int target=Math.max(128,renderEdgePx);
            BitmapFactory.Options sample=new BitmapFactory.Options();
            sample.inSampleSize=1;
            while(Math.max(probe.outWidth/sample.inSampleSize,
                    probe.outHeight/sample.inSampleSize)>target*2)
                sample.inSampleSize*=2;

            Bitmap decoded=BitmapFactory.decodeByteArray(
                bytes,0,bytes.length,sample);
            if(decoded==null)return null;
            Bitmap result=decoded;
            int max=Math.max(decoded.getWidth(),decoded.getHeight());
            if(max>target) {
                double scale=target/(double)max;
                result=Bitmap.createScaledBitmap(decoded,
                    Math.max(1,(int)Math.round(decoded.getWidth()*scale)),
                    Math.max(1,(int)Math.round(decoded.getHeight()*scale)),true);
                if(result!=decoded)decoded.recycle();
            }
            synchronized(StorageThumbs.class) {
                if(cache!=null)
                    cache.put(itemId,new CachedBitmap(
                        sourceHash,sourceLength,result));
            }
            return result;
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
        while(probe.outWidth/sample.inSampleSize>renderEdgePx*2
                ||probe.outHeight/sample.inSampleSize>renderEdgePx*2)
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
        double scale=Math.min(1.0,renderEdgePx/(double)Math.max(w,h));
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
