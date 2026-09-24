package com.edwinkarolczyk.edhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only scoped PDF handoff. No access to private finance or other files. */
public final class StorageQrPdfProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File source(Uri uri) throws FileNotFoundException {
        if (uri == null || uri.getPathSegments().size() != 2
                || !"labels".equals(uri.getPathSegments().get(0)))
            throw new FileNotFoundException();
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("edhome-qr-[a-f0-9]{32}\\.pdf"))
            throw new FileNotFoundException();
        File parent = new File(getContext().getCacheDir(), "qr-labels");
        File file = new File(parent,name);
        try {
            if (!file.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()))
                throw new FileNotFoundException();
        } catch (java.io.IOException invalid) { throw new FileNotFoundException(); }
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(source(uri),
            ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
            String[] arguments,String sortOrder) {
        try {
            File file = source(uri);
            String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}
                : projection;
            MatrixCursor result = new MatrixCursor(columns,1);
            Object[] row = new Object[columns.length];
            for (int i=0;i<columns.length;i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i]))
                    row[i]="EDHOME-etykiety-QR.pdf";
                if (OpenableColumns.SIZE.equals(columns[i])) row[i]=file.length();
            }
            result.addRow(row);
            return result;
        } catch (FileNotFoundException missing) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }
    @Override public int update(Uri uri,ContentValues values,String selection,
            String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) {
        throw new UnsupportedOperationException();
    }
}