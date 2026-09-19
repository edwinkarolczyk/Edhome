package com.edwinkarolczyk.edhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, explicit APK handoff to Android's own package installer. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || uri == null || uri.getPathSegments().size() != 2
            || !"apk".equals(uri.getPathSegments().get(0))) throw new FileNotFoundException();
        String basename = uri.getLastPathSegment();
        if (basename == null || !basename.matches("edhome-beta-[0-9]+\\.apk"))
            throw new FileNotFoundException();
        File folder = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (folder == null) throw new FileNotFoundException();
        File file = new File(folder, basename);
        try {
            if (!file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))
                throw new FileNotFoundException();
        } catch (java.io.IOException e) { throw new FileNotFoundException(); }
        if (!file.isFile()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
