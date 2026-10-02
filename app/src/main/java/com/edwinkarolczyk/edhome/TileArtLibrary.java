package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.List;

/** Full-tile artwork library. 1P and 2P are separate files and are never substituted. */
final class TileArtLibrary {
    static final String PREF_PREFIX="tile_art_";
    static final String NONE="none";
    static final String STYLE_1="style1";
    static final String STYLE_2="style2";
    static final String STYLE_3="style3";
    static final int SINGLE_WIDTH=840;
    static final int DOUBLE_WIDTH=1728;
    static final int HEIGHT=656;
    private static final String MIGRATED="tile_art_library_migrated_v1";
    private static final String BUNDLED_INSTALLED="tile_art_bundled_compact_v2";
    private static final String BUNDLED_ASSET="EDHOME_TILE_ART_BUILTIN_COMPACT.zip";
    private static final long MAX_FILE=12L*1024*1024;

    private TileArtLibrary(){}

    static List<String> styleIds(){
        return Arrays.asList(NONE,STYLE_1,STYLE_2,STYLE_3);
    }

    static String styleLabel(String style){
        if(STYLE_1.equals(style))return "Styl 1";
        if(STYLE_2.equals(style))return "Styl 2";
        if(STYLE_3.equals(style))return "Styl 3";
        return "Brak — zwykła ikona";
    }

    static boolean knownStyle(String style){
        return NONE.equals(style)||STYLE_1.equals(style)
            ||STYLE_2.equals(style)||STYLE_3.equals(style);
    }

    static String prefKey(String tileId){return PREF_PREFIX+tileId;}

    private static boolean knownTarget(String target){
        return target!=null&&HomeTileCatalog.TARGETS.contains(target);
    }

    private static File root(Context context){
        return new File(context.getFilesDir(),"edhome-tile-art");
    }

    private static File file(Context context,String target,String style,int span){
        if(!knownTarget(target)||!knownStyle(style)||NONE.equals(style)
                ||(span!=1&&span!=2))return null;
        String suffix=span==2?"_2p.webp":"_1p.webp";
        return new File(new File(root(context),style),target+suffix);
    }

    static boolean available(Context context,String target,String style,int span){
        File f=file(context,target,style,span);
        return f!=null&&f.isFile()&&f.length()>0;
    }

    static Bitmap bitmap(Context context,String target,String style,int span){
        File f=file(context,target,style,span);
        return f==null||!f.isFile()?null:BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    static String availabilityLabel(Context context,String target,String style,int span){
        if(NONE.equals(style))return styleLabel(style);
        return styleLabel(style)+(available(context,target,style,span)
            ?" • gotowa "+(span==2?"2P":"1P")
            :" • brak "+(span==2?"2P":"1P"));
    }

    static void importImage(Context context,String target,String style,int span,Uri uri)
            throws Exception{
        if(!knownTarget(target)||!knownStyle(style)||NONE.equals(style)
                ||(span!=1&&span!=2))
            throw new IllegalArgumentException("Nieprawidłowy styl grafiki.");
        Bitmap source=decode(context,uri);
        try{
            validateRatio(source,span);
            writeNormalized(context,target,style,span,source);
        }finally{source.recycle();}
    }

    private static boolean userOwnedFullSize(File image,int span){
        if(image==null||!image.isFile())return false;
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(image.getAbsolutePath(),bounds);
        int expectedWidth=span==2?DOUBLE_WIDTH:SINGLE_WIDTH;
        return bounds.outWidth==expectedWidth&&bounds.outHeight==HEIGHT;
    }

    /**
     * Removes the white dead canvas present in some generated 1P images.
     * Kalendarz/Places-like edge-filling artwork is left untouched.
     * The result keeps the same lightweight bundled resolution.
     */
    private static void normalizeBundledSingle(File image)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap source=BitmapFactory.decodeFile(image.getAbsolutePath(),options);
        if(source==null)return;
        Bitmap cropped=null;
        Bitmap scaled=null;
        try{
            int width=source.getWidth(),height=source.getHeight();
            int left=width,top=height,right=-1,bottom=-1;
            for(int y=0;y<height;y++){
                for(int x=0;x<width;x++){
                    int pixel=source.getPixel(x,y);
                    if(Color.red(pixel)<245||Color.green(pixel)<245
                            ||Color.blue(pixel)<245){
                        if(x<left)left=x;
                        if(y<top)top=y;
                        if(x>right)right=x;
                        if(y>bottom)bottom=y;
                    }
                }
            }
            if(right<left||bottom<top)return;
            int contentWidth=right-left+1;
            int contentHeight=bottom-top+1;
            if(contentWidth>=Math.round(width*.90f)
                    &&contentHeight>=Math.round(height*.90f))return;
            int pad=4;
            left=Math.max(0,left-pad);
            top=Math.max(0,top-pad);
            right=Math.min(width-1,right+pad);
            bottom=Math.min(height-1,bottom+pad);
            cropped=Bitmap.createBitmap(source,left,top,
                right-left+1,bottom-top+1);
            scaled=Bitmap.createScaledBitmap(cropped,width,height,true);
            File normalized=new File(image.getParentFile(),
                image.getName()+".normalized");
            try(FileOutputStream out=new FileOutputStream(normalized)){
                if(!scaled.compress(Bitmap.CompressFormat.WEBP,82,out))
                    throw new IllegalStateException(
                        "Nie zapisano znormalizowanej grafiki.");
            }
            if(!image.delete()||!normalized.renameTo(image)){
                normalized.delete();
                throw new IllegalStateException(
                    "Nie zastąpiono znormalizowanej grafiki.");
            }
        }finally{
            if(scaled!=null&&scaled!=cropped&&scaled!=source)scaled.recycle();
            if(cropped!=null&&cropped!=source)cropped.recycle();
            source.recycle();
        }
    }

    /**
     * Installs the compact WebP library shipped inside the signed APK.
     * Full-size 840×656 / 1728×656 user artwork is never overwritten.
     * Bundled low-resolution defaults may be refreshed by a newer APK.
     */
    static int installBundled(Context context,SharedPreferences prefs)throws Exception{
        if(prefs.getBoolean(BUNDLED_INSTALLED,false))return 0;
        int installed=0;
        byte[] buffer=new byte[8192];
        try(InputStream asset=context.getAssets().open(BUNDLED_ASSET);
            ZipInputStream zip=new ZipInputStream(asset)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()){
                    zip.closeEntry();
                    continue;
                }
                String name=entry.getName();
                if(name.startsWith("/")||name.contains(".."))
                    throw new IllegalArgumentException("Nieprawidłowa paczka grafik.");
                String[] parts=name.split("/",-1);
                if(parts.length!=2){
                    zip.closeEntry();
                    continue;
                }
                String style=parts[0];
                if(!STYLE_1.equals(style)&&!STYLE_2.equals(style)
                        &&!STYLE_3.equals(style)){
                    zip.closeEntry();
                    continue;
                }
                String filename=parts[1];
                int span;
                if(filename.endsWith("_1p.webp"))span=1;
                else if(filename.endsWith("_2p.webp"))span=2;
                else{
                    zip.closeEntry();
                    continue;
                }
                String target=filename.substring(0,filename.length()-8);
                if(!knownTarget(target)){
                    zip.closeEntry();
                    continue;
                }
                File destination=file(context,target,style,span);
                if(destination==null){
                    zip.closeEntry();
                    continue;
                }
                if(destination.isFile()&&destination.length()>0
                        &&userOwnedFullSize(destination,span)){
                    zip.closeEntry();
                    continue;
                }
                File dir=destination.getParentFile();
                if(!dir.exists()&&!dir.mkdirs())
                    throw new IllegalStateException(
                        "Brak miejsca na wbudowane grafiki kafelków.");
                File tmp=new File(dir,destination.getName()+".bundled.tmp");
                try(FileOutputStream out=new FileOutputStream(tmp)){
                    int n;
                    long total=0;
                    while((n=zip.read(buffer))!=-1){
                        total+=n;
                        if(total>MAX_FILE)
                            throw new IllegalArgumentException(
                                "Grafika w paczce przekracza limit.");
                        out.write(buffer,0,n);
                    }
                    out.flush();
                }
                if(span==1)normalizeBundledSingle(tmp);
                if(destination.exists()&&!destination.delete()){
                    tmp.delete();
                    throw new IllegalStateException(
                        "Nie zastąpiono starej grafiki wbudowanej.");
                }
                if(!tmp.renameTo(destination)){
                    tmp.delete();
                    throw new IllegalStateException(
                        "Nie zapisano wbudowanej grafiki.");
                }
                installed++;
                zip.closeEntry();
            }
        }
        prefs.edit().putBoolean(BUNDLED_INSTALLED,true).apply();
        return installed;
    }

    /** First .23 start: preserve the full artwork already visible on Edwin's phone as Styl 1. */
    static int migrateExistingCustomArt(Context context,SharedPreferences prefs){
        if(prefs.getBoolean(MIGRATED,false))return 0;
        int copied=0;
        SharedPreferences.Editor edit=prefs.edit();
        try{
            List<String> ids=HomeTileCatalog.canonical(
                prefs.getString(HomeTileCatalog.ORDER_KEY,null),
                prefs.getString("home_tile_order",""),
                BuildConfig.DIAGNOSTICS_ENABLED);
            for(String tileId:ids){
                String key=TileCustomImage.key(tileId);
                if(!TileCustomImage.available(context,key))continue;
                Bitmap image=TileCustomImage.bitmap(context,key);
                if(image==null)continue;
                try{
                    int span=detectSpan(image);
                    if(span==0)continue; // ordinary square custom icon, not full-tile art
                    String target=prefs.getString("tile_target_"+tileId,
                        HomeTileCatalog.defaultTarget(tileId));
                    if(!knownTarget(target))continue;
                    File targetFile=file(context,target,STYLE_1,span);
                    if(targetFile!=null&&!targetFile.isFile()){
                        writeNormalized(context,target,STYLE_1,span,image);
                        copied++;
                    }
                    if(NONE.equals(prefs.getString(prefKey(tileId),NONE)))
                        edit.putString(prefKey(tileId),STYLE_1);
                }catch(Exception ignored){
                    // Keep the old custom image untouched if it is not valid full-tile art.
                }finally{image.recycle();}
            }
        }finally{
            edit.putBoolean(MIGRATED,true).apply();
        }
        return copied;
    }

    private static Bitmap decode(Context context,Uri uri)throws Exception{
        byte[] data;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalArgumentException("Nie można otworzyć grafiki.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] block=new byte[8192];
            int n;
            while((n=in.read(block))!=-1){
                if(out.size()+(long)n>MAX_FILE)
                    throw new IllegalArgumentException("Grafika przekracza 12 MB.");
                out.write(block,0,n);
            }
            data=out.toByteArray();
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1
                ||bounds.outWidth>4096||bounds.outHeight>4096)
            throw new IllegalArgumentException("Grafika może mieć maks. 4096 × 4096.");
        Bitmap result=BitmapFactory.decodeByteArray(data,0,data.length);
        if(result==null)throw new IllegalArgumentException("Nieprawidłowy PNG / WebP.");
        return result;
    }

    private static int detectSpan(Bitmap image){
        double ratio=image.getWidth()/(double)image.getHeight();
        double one=SINGLE_WIDTH/(double)HEIGHT;
        double two=DOUBLE_WIDTH/(double)HEIGHT;
        if(Math.abs(ratio-one)<=0.16)return 1;
        if(Math.abs(ratio-two)<=0.24)return 2;
        return 0;
    }

    private static void validateRatio(Bitmap image,int span){
        int detected=detectSpan(image);
        if(detected!=span)
            throw new IllegalArgumentException(span==2
                ?"Grafika 2P musi mieć proporcję 1728 × 656."
                :"Grafika 1P musi mieć proporcję 840 × 656.");
    }

    private static void writeNormalized(Context context,String target,String style,
            int span,Bitmap source)throws Exception{
        int width=span==2?DOUBLE_WIDTH:SINGLE_WIDTH;
        Bitmap normalized=Bitmap.createScaledBitmap(source,width,HEIGHT,true);
        try{
            File destination=file(context,target,style,span);
            if(destination==null)throw new IllegalArgumentException("Nieprawidłowa grafika.");
            File dir=destination.getParentFile();
            if(!dir.exists()&&!dir.mkdirs())
                throw new IllegalStateException("Brak miejsca na bibliotekę grafik.");
            File tmp=new File(dir,destination.getName()+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){
                if(!normalized.compress(Bitmap.CompressFormat.WEBP,92,out))
                    throw new IllegalStateException("Nie zapisano grafiki.");
            }
            if(destination.exists()&&!destination.delete())
                throw new IllegalStateException("Nie zastąpiono starej grafiki.");
            if(!tmp.renameTo(destination))
                throw new IllegalStateException("Nie zapisano grafiki kafelka.");
        }finally{
            if(normalized!=source)normalized.recycle();
        }
    }
}
