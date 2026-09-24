package com.edwinkarolczyk.edhome;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** User-selected private thumbnail, no original-photo URI dependency.
 * A small JPEG is stored as Base64 in the app's private preferences and JSON backup.
 */
final class StorageThumbs {
    static final String PREFIX="storage_thumb_v1_";
    static final int MAX_JPEG_BYTES=32*1024;
    private StorageThumbs(){}

    static String key(long itemId){return PREFIX+itemId;}

    static Bitmap read(SharedPreferences prefs,long itemId) {
        try {
            String value=prefs.getString(key(itemId),"");
            if(value.isEmpty()||value.length()>50000)return null;
            byte[] bytes=Base64.decode(value,Base64.NO_WRAP);
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
        while(probe.outWidth/sample.inSampleSize>512
                ||probe.outHeight/sample.inSampleSize>512)
            sample.inSampleSize*=2;
        Bitmap original;
        try(InputStream in=resolver.openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Nie można odczytać zdjęcia.");
            original=BitmapFactory.decodeStream(in,null,sample);
        }
        if(original==null)throw new IllegalArgumentException("Nie rozpoznano zdjęcia.");
        int w=original.getWidth(),h=original.getHeight();
        double scale=Math.min(1.0,192.0/Math.max(w,h));
        Bitmap small=Bitmap.createScaledBitmap(original,
            Math.max(1,(int)Math.round(w*scale)),
            Math.max(1,(int)Math.round(h*scale)),true);
        if(small!=original)original.recycle();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int quality:new int[]{78,65,50}) {
            out.reset();
            small.compress(Bitmap.CompressFormat.JPEG,quality,out);
            if(out.size()<=MAX_JPEG_BYTES)break;
        }
        small.recycle();
        if(out.size()==0||out.size()>MAX_JPEG_BYTES)
            throw new IllegalArgumentException("Miniatura jest za duża.");
        return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    }
}
