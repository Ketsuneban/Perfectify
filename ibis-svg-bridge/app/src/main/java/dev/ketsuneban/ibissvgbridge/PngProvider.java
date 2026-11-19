package dev.ketsuneban.ibissvgbridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class PngProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File file(Uri uri) throws FileNotFoundException {
        if (!"shared.png".equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException("Unknown image");
        }
        return new File(getContext().getCacheDir(), Store.PNG);
    }

    @Override public String getType(Uri uri) { return "image/png"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only image");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File png = file(uri);
            String[] columns = projection == null
                    ? new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }
                    : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int j = 0; j < columns.length; j++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[j])) values[j] = "ibis-svg-bridge.png";
                if (OpenableColumns.SIZE.equals(columns[j])) values[j] = png.length();
            }
            cursor.addRow(values);
            return cursor;
        } catch (FileNotFoundException ex) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] args) { return 0; }
}
