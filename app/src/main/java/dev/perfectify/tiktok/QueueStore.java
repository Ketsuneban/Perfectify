package dev.perfectify.tiktok;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueueStore {
    private static final String PREFS = "perfectify_queue_v1";
    private static final String KEY = "entries";
    private static final Pattern LINKS = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private QueueStore() {}

    public static final class Entry {
        public String id;
        public String url;
        public String mediaUrl;
        public String category;
        public String state;
        public String error;
        public String savedUri;
        public int progress;

        JSONObject json() {
            JSONObject j = new JSONObject();
            try {
                j.put("id", id);
                j.put("url", url);
                j.put("mediaUrl", mediaUrl == null ? "" : mediaUrl);
                j.put("category", category);
                j.put("state", state);
                j.put("error", error == null ? "" : error);
                j.put("savedUri", savedUri == null ? "" : savedUri);
                j.put("progress", progress);
            } catch (Exception ignored) {}
            return j;
        }

        static Entry from(JSONObject j) {
            Entry e = new Entry();
            e.id = j.optString("id", UUID.randomUUID().toString());
            e.url = j.optString("url", "");
            e.mediaUrl = j.optString("mediaUrl", "");
            e.category = j.optString("category", "Ссылки");
            e.state = j.optString("state", "queued");
            e.error = j.optString("error", "");
            e.savedUri = j.optString("savedUri", "");
            e.progress = j.optInt("progress", 0);
            return e;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Entry> list(Context context) {
        ArrayList<Entry> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.optJSONObject(i);
                if (row != null) out.add(Entry.from(row));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) array.put(e.json());
        prefs(context).edit().putString(KEY, array.toString()).commit();
    }

    public static boolean isTikTokUrl(String input) {
        try {
            Uri uri = Uri.parse(input);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("tiktok.com")
                || host.toLowerCase(Locale.ROOT).endsWith(".tiktok.com"));
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalize(String raw) {
        String value = raw.trim().replaceAll("[),.;!]+$", "");
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (host == null) return value;
            String path = uri.getEncodedPath();
            if (path == null) path = "/";
            return "https://" + host.toLowerCase(Locale.ROOT) + path;
        } catch (Exception ex) {
            return value;
        }
    }

    public static synchronized int addText(Context context, String raw, String category) {
        Matcher matcher = LINKS.matcher(raw == null ? "" : raw);
        ArrayList<String> links = new ArrayList<>();
        while (matcher.find()) {
            String url = normalize(matcher.group());
            if (isTikTokUrl(url)) links.add(url);
        }
        return add(context, links, category, null);
    }

    public static synchronized int add(Context context, List<String> links, String category, String directMediaUrl) {
        List<Entry> entries = list(context);
        HashSet<String> seen = new HashSet<>();
        for (Entry e : entries) seen.add(normalize(e.url));
        int added = 0;
        for (String incoming : links) {
            String url = normalize(incoming);
            if (!isTikTokUrl(url) || !seen.add(url)) continue;
            Entry e = new Entry();
            e.id = UUID.randomUUID().toString();
            e.url = url;
            e.mediaUrl = directMediaUrl == null ? "" : directMediaUrl;
            e.category = category;
            e.state = "queued";
            e.error = "";
            e.savedUri = "";
            e.progress = 0;
            entries.add(e);
            added++;
        }
        if (added > 0) save(context, entries);
        return added;
    }

    public static synchronized void update(Context context, String id, String state, int progress,
                                           String error, String savedUri) {
        List<Entry> entries = list(context);
        for (Entry e : entries) {
            if (!e.id.equals(id)) continue;
            if (state != null) e.state = state;
            if (progress >= 0) e.progress = progress;
            if (error != null) e.error = error;
            if (savedUri != null) e.savedUri = savedUri;
            save(context, entries);
            return;
        }
    }

    public static synchronized void retryFailed(Context context) {
        List<Entry> entries = list(context);
        for (Entry e : entries) {
            if ("error".equals(e.state) || "cancelled".equals(e.state)) {
                e.state = "queued";
                e.error = "";
                e.progress = 0;
            }
        }
        save(context, entries);
    }

    public static synchronized void clearCompleted(Context context) {
        List<Entry> entries = list(context);
        entries.removeIf(e -> "done".equals(e.state));
        save(context, entries);
    }

    public static synchronized void recoverInterrupted(Context context) {
        List<Entry> entries = list(context);
        boolean changed = false;
        for (Entry e : entries) {
            if ("resolving".equals(e.state) || "downloading".equals(e.state)) {
                e.state = "queued";
                e.progress = 0;
                changed = true;
            }
        }
        if (changed) save(context, entries);
    }
}
