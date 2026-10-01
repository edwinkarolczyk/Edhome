package com.edwinkarolczyk.edhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Per-tile user-owned image. Files survive in-place app updates; no network or new database. */
final class TileCustomImage {
    static final String PREFIX = "custom_";
    private static final long MAX_FILE = 8L * 1024 * 1024;
    private static final int MAX_EDGE = 1600;
    private TileCustomImage() { }

    static String key(String tileId) {
        if (!HomeTileCatalog.validTileId(tileId))
            throw new IllegalArgumentException("Unknown tile");
        return PREFIX + tileId;
    }

    private static File file(Context context, String key) {
        String id = key.startsWith(PREFIX) ? key.substring(PREFIX.length()) : "";
        if (!HomeTileCatalog.validTileId(id)) return null;
        return new File(new File(context.getFilesDir(), "edhome-custom-tile-icons"),
            id + ".png");
    }

    static boolean available(Context context, String key) {
        File icon = file(context, key);
        return icon != null && icon.isFile() && icon.length() > 0;
    }

    static Bitmap bitmap(Context context, String key) {
        File icon = file(context, key);
        return icon == null ? null : BitmapFactory.decodeFile(icon.getAbsolutePath());
    }

    static void importImage(Context context, String tileId, Uri uri)
            throws Exception {
        File destination = file(context, key(tileId));
        if (destination == null) throw new IllegalArgumentException("Unknown tile");
        byte[] original;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("Nie można otworzyć zdjęcia.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (output.size() + (long)n > MAX_FILE)
                    throw new IllegalArgumentException("Ikona przekracza 8 MB.");
                output.write(buffer, 0, n);
            }
            original = output.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(original, 0, original.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1
                || bounds.outWidth > 4096 || bounds.outHeight > 4096)
            throw new IllegalArgumentException("Obraz musi mieć maks. 4096 × 4096.");
        bounds.inJustDecodeBounds = false;
        bounds.inSampleSize = 1;
        while(bounds.outWidth/bounds.inSampleSize>MAX_EDGE*2
                ||bounds.outHeight/bounds.inSampleSize>MAX_EDGE*2)
            bounds.inSampleSize*=2;
        Bitmap source = BitmapFactory.decodeByteArray(
            original, 0, original.length, bounds);
        if (source == null) throw new IllegalArgumentException("Nieprawidłowy PNG/WebP.");
        int sourceWidth=source.getWidth(),sourceHeight=source.getHeight();
        float scale=Math.min(1f,
            MAX_EDGE/(float)Math.max(sourceWidth,sourceHeight));
        Bitmap scaled=scale<1f?Bitmap.createScaledBitmap(source,
            Math.max(1,Math.round(sourceWidth*scale)),
            Math.max(1,Math.round(sourceHeight*scale)),true):source;
        try {
            File folder = destination.getParentFile();
            if (!folder.exists() && !folder.mkdirs())
                throw new IllegalStateException("Brak miejsca na ikonę.");
            File tmp = new File(folder, tileId + ".tmp");
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output))
                    throw new IllegalStateException("Nie zapisano ikony.");
            }
            // Rename over the old image only after a complete file has been written.
            if (!tmp.renameTo(destination)) {
                if (destination.exists() && destination.delete()
                        && tmp.renameTo(destination)) return;
                throw new IllegalStateException("Nie zastąpiono ikony.");
            }
        } finally {
            if(scaled!=source)scaled.recycle();
            source.recycle();
        }
    }
}
