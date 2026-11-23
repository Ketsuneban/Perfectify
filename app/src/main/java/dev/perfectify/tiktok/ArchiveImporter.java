package dev.perfectify.tiktok;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline import of a TikTok "Download your data" archive, without uploading it. */
public final class ArchiveImporter {
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 160L * 1024 * 1024;
    private static final Pattern VIDEO_LINK = Pattern.compile(
        "https?://[^\\s\\\"'<>\\\\]+", Pattern.CASE_INSENSITIVE);

    private ArchiveImporter() {}

    public static final class Result {
        public int files;
        public int links;
        public int added;
        public int skipped;
    }

    public static Result importFrom(Context context, Uri uri) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme())) throw new Exception("Выбери файл через Android");
        Result result = new Result();
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new Exception("Не удалось открыть файл");
            BufferedInputStream stream = new BufferedInputStream(raw);
            stream.mark(4);
            byte[] header = new byte[4];
            int n = stream.read(header);
            stream.reset();
            if (n >= 4 && header[0] == 'P' && header[1] == 'K') {
                try (ZipInputStream zip = new ZipInputStream(stream)) {
                    ZipEntry entry;
                    long total = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory()) continue;
                        String filename = entry.getName().toLowerCase(Locale.ROOT);
                        if (!(filename.endsWith(".json") || filename.endsWith(".txt")
                                || filename.endsWith(".csv"))) {
                            result.skipped++;
                            continue;
                        }
                        if (entry.getSize() > MAX_FILE_BYTES) {
                            result.skipped++;
                            continue;
                        }
                        byte[] bytes = boundedRead(zip, MAX_FILE_BYTES);
                        total += bytes.length;
                        if (total > MAX_TOTAL_BYTES) throw new Exception("Архив превышает безопасный размер обработки");
                        process(context, entry.getName(), new String(bytes, StandardCharsets.UTF_8), result);
                        zip.closeEntry();
                    }
                }
            } else {
                String filename = displayName(context, uri);
                byte[] bytes = boundedRead(stream, MAX_FILE_BYTES);
                process(context, filename, new String(bytes, StandardCharsets.UTF_8), result);
            }
        }
        return result;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return "export.json";
    }

    private static byte[] boundedRead(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (output.size() + size > limit) throw new Exception("Слишком большой файл данных; максимальный размер одного файла: 20 МБ");
            output.write(buffer, 0, size);
        }
        return output.toByteArray();
    }

    private static void process(Context context, String name, String text, Result result) {
        result.files++;
        String category = classify(name, "Импорт");
        try {
            Object json = new JSONTokener(text).nextValue();
            visit(context, json, category, result, 0);
        } catch (Exception ignored) {
            extract(context, text, category, result);
        }
    }

    private static void visit(Context context, Object node, String category, Result result, int depth) {
        if (depth >= 32 || node == null || node == JSONObject.NULL) return;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                visit(context, object.opt(key), classify(key, category), result, depth + 1);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length() && i < 150000; i++)
                visit(context, array.opt(i), category, result, depth + 1);
        } else if (node instanceof String) {
            extract(context, (String) node, category, result);
        }
    }

    private static String classify(String text, String fallback) {
        String input = text.toLowerCase(Locale.ROOT);
        if (input.contains("favorite") || input.contains("favourite") || input.contains("избран"))
            return "Избранное";
        if (input.contains("like") || input.contains("понрав"))
            return "Понравившиеся";
        if (input.contains("repost") || input.contains("репост"))
            return "Репосты";
        if (input.contains("watch") || input.contains("history") || input.contains("история"))
            return "История";
        if (input.contains("post") || input.contains("upload") || input.contains("публикац"))
            return "Профиль";
        return fallback;
    }

    private static void extract(Context context, String input, String category, Result result) {
        // TikTok JSON exports may escape URL slashes.
        String text = input.replace("\\/", "/").replace("\\u0026", "&");
        Matcher matcher = VIDEO_LINK.matcher(text);
        List<String> batch = new ArrayList<>();
        while (matcher.find() && batch.size() < 20000) {
            String url = matcher.group();
            if (QueueStore.isTikTokUrl(url)) batch.add(url);
        }
        if (!batch.isEmpty()) {
            result.links += batch.size();
            result.added += QueueStore.add(context, batch, category, null);
        }
    }
}
