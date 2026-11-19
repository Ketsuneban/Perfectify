package dev.ketsuneban.ibissvgbridge;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Store {
    static final int MAX_SVG_BYTES = 2 * 1024 * 1024;
    static final String CURRENT = "current.svg";
    static final String EXPORT = "pending-export.svg";
    static final String PNG = "shared.png";

    private Store() { }

    static String readUri(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No SVG file selected");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot access file");
            return validate(readLimited(in));
        }
    }

    static String readLocal(Context context, String name) throws IOException {
        try (InputStream in = new FileInputStream(new File(context.getFilesDir(), name))) {
            return validate(readLimited(in));
        }
    }

    static void writeLocal(Context context, String name, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SVG_BYTES) throw new IOException("SVG exceeds 2 MiB limit");
        try (FileOutputStream out = context.openFileOutput(name, Context.MODE_PRIVATE)) {
            out.write(bytes);
        }
    }

    static String validate(String content) throws IOException {
        String text = content.replace("\uFEFF", "").trim();
        if (text.length() == 0 || !text.contains("<svg")) {
            throw new IOException("This is not an SVG document");
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<!entity")) {
            throw new IOException("DTD and XML entities are not supported");
        }
        return text;
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int n;
        while ((n = in.read(block)) != -1) {
            if (buffer.size() + n > MAX_SVG_BYTES) throw new IOException("SVG exceeds 2 MiB limit");
            buffer.write(block, 0, n);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }
}
