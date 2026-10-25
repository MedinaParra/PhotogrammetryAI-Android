package org.terreno.piloto;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Narrow private provider: only UUID-named JPEGs in our camera cache, via temporary grants. */
public final class CameraProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        if (!"org.terreno.piloto.camera".equals(uri.getAuthority()) ||
                uri.getPath() == null || !uri.getPath().matches("/capture/[a-f0-9-]{36}\\.jpg"))
            throw new FileNotFoundException("Invalid capture URI");
        File directory = new File(getContext().getCacheDir(), "camera");
        File file = new File(directory, uri.getLastPathSegment());
        try {
            if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))
                throw new FileNotFoundException("Invalid capture path");
        } catch (IOException e) { throw new FileNotFoundException("Invalid capture path"); }
        return file;
    }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i=0;i<columns.length;i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
