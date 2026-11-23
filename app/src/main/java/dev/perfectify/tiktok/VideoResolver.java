package dev.perfectify.tiktok;

import android.net.Uri;
import android.webkit.CookieManager;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort reader for already accessible TikTok pages.
 * No device emulation, private API impersonation or privacy bypass.
 */
public final class VideoResolver {
    private static final Pattern VIDEO_FIELD = Pattern.compile(
        "\"(?:playAddr|downloadAddr|playUrl|src|url)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final Pattern URL_LIST = Pattern.compile(
        "\"urlList\"\\s*:\\s*\\[\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final int HTML_LIMIT = 3 * 1024 * 1024;

    private VideoResolver() {}

    public static String resolve(QueueStore.Entry entry) throws Exception {
        if (entry.mediaUrl != null && !entry.mediaUrl.isEmpty()) {
            return validateMediaUrl(entry.mediaUrl);
        }
        if (!QueueStore.isTikTokUrl(entry.url)) throw new Exception("Это не ссылка TikTok");
        String url = entry.url;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        // Cookies are sent only to TikTok itself, never to arbitrary third-party downloaders.
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("TikTok ответил HTTP " + code);
            String mime = connection.getContentType();
            if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
                return validateMediaUrl(connection.getURL().toString());
            }
            String html = readLimited(connection.getInputStream());
            // The video page may contain a signed, short-lived CDN URL in hydration data.
            Matcher list = URL_LIST.matcher(html);
            while (list.find()) {
                String candidate = unquote(list.group(1));
                if (looksLikeMedia(candidate)) return validateMediaUrl(candidate);
            }
            Matcher matcher = VIDEO_FIELD.matcher(html);
            while (matcher.find()) {
                String candidate = unquote(matcher.group(1));
                if (looksLikeMedia(candidate)) return validateMediaUrl(candidate);
            }
            throw new Exception("Адрес видео недоступен. Открой ролик во встроенном TikTok и попробуй «Захватить видео»");
        } finally {
            connection.disconnect();
        }
    }

    private static String unquote(String quoted) {
        try { return new JSONArray("[" + quoted + "]").getString(0); }
        catch (Exception e) { return ""; }
    }

    private static boolean looksLikeMedia(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            String h = host.toLowerCase(Locale.ROOT);
            return h.contains("tiktok") || h.contains("byte") ||
                   h.contains("muscdn") || h.contains("akamaized") ||
                   url.toLowerCase(Locale.ROOT).contains(".mp4");
        } catch (Exception e) { return false; }
    }

    public static String validateMediaUrl(String value) throws Exception {
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !"https".equalsIgnoreCase(scheme) || host == null ||
                host.isEmpty() || host.equalsIgnoreCase("localhost") ||
                host.endsWith(".local") || host.matches("[0-9.]+") ||
                host.contains(":")) throw new Exception("Небезопасный адрес медиа");
        return value;
    }

    private static String readLimited(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > HTML_LIMIT) throw new Exception("Страница слишком большая для обработки");
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
