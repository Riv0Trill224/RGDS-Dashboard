package com.rgds.dashboard;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** Read-only URI grants for individual report/APK files, never arbitrary filesystem paths. */
public final class SharedFiles extends ContentProvider {
    static File directory(Context context) {
        File dir = new File(context.getCacheDir(), "shared");
        dir.mkdirs();
        return dir;
    }
    static File create(Context context, String extension) {
        File dir = directory(context);
        File[] old = dir.listFiles();
        if (old != null) for (File file : old)
            if (System.currentTimeMillis() - file.lastModified() > 7L * 86400000) file.delete();
        return new File(dir, UUID.randomUUID() + extension);
    }
    static Uri uri(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".files")
                .appendPath(file.getName()).build();
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (uri.getPathSegments().size() != 1 || name == null
                || !name.matches("[a-f0-9-]{36}\\.(log|apk)")) throw new FileNotFoundException("Invalid shared file");
        File file = new File(directory(getContext()), name);
        if (!file.isFile()) throw new FileNotFoundException("Shared file expired");
        return file;
    }
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        return uri.getPath() != null && uri.getPath().endsWith(".apk")
                ? "application/vnd.android.package-archive" : "text/plain";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "RGDS-" + file.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values); return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
