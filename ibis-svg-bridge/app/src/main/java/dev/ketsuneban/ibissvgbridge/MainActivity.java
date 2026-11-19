package dev.ketsuneban.ibissvgbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Self-contained SVG drawing application. No other apps or overlays required. */
public final class MainActivity extends Activity {
    private static final int OPEN_SVG = 701;
    private static final int SAVE_SVG = 702;
    private static final int EXPORT_PNG = 703;
    private static final String PNG_FILE = "vectora-export.png";
    private final Object ioLock = new Object();
    private WebView web;
    private boolean ready = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(15, 20, 31));
        getWindow().setNavigationBarColor(Color.rgb(15, 20, 31));
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.rgb(15, 20, 31));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(15, 20, 31));
        WebSettings options = web.getSettings();
        options.setJavaScriptEnabled(true);
        options.setAllowFileAccess(false);
        options.setAllowContentAccess(false);
        options.setBlockNetworkLoads(true);
        options.setJavaScriptCanOpenWindowsAutomatically(false);
        options.setSupportMultipleWindows(false);
        options.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        options.setDomStorageEnabled(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new NativeBridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                ready = true;
                if (!receiveIncoming(getIntent())) restorePrevious();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        try {
            String html = readAsset("editor.html");
            String fillLibrary = readAsset("vector-fill.js");
            html = html.replace("<!-- VECTOR_FILL_SCRIPT -->", "<script>\n" + fillLibrary + "\n</script>");
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/",
                    html, "text/html", "UTF-8", null);
        } catch (IOException e) {
            message("Не удалось открыть редактор: " + e.getMessage());
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ready) receiveIncoming(intent);
    }

    private String readAsset(String path) throws IOException {
        try (InputStream stream = getAssets().open(path);
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int read;
            while ((read = stream.read(bytes)) != -1) result.write(bytes, 0, read);
            return result.toString(StandardCharsets.UTF_8.name());
        }
    }

    private boolean receiveIncoming(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) uri = intent.getData();
        if (Intent.ACTION_SEND.equals(action)) {
            Object obj = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (obj instanceof Uri) uri = (Uri) obj;
        }
        if (uri != null) {
            importSvg(uri);
            intent.setAction(null);
            return true;
        }
        return false;
    }

    private void restorePrevious() {
        try {
            String source = Store.readLocal(this, Store.CURRENT);
            loadSvg(source);
        } catch (IOException ignored) {
            // The in-page editor starts with a blank SVG document.
        }
    }

    private void loadSvg(String source) {
        if (!ready || web == null) return;
        web.evaluateJavascript("window.editor && window.editor.loadSvg(" +
                JSONObject.quote(source) + ");", null);
    }

    private void importSvg(Uri uri) {
        try {
            String source = Store.readUri(this, uri);
            loadSvg(source);
        } catch (Exception error) {
            message("Ошибка открытия SVG: " + error.getMessage());
        }
    }

    private void pickSvg() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/svg+xml", "text/xml", "application/xml", "text/plain"
        });
        startActivityForResult(intent, OPEN_SVG);
    }

    private void createDocument(String mime, String name, int request) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            if (code == OPEN_SVG) {
                importSvg(uri);
            } else if (code == SAVE_SVG) {
                String svg = Store.readLocal(this, Store.EXPORT);
                writeUri(uri, svg.getBytes(StandardCharsets.UTF_8));
                message("SVG сохранён");
            } else if (code == EXPORT_PNG) {
                File input = new File(getCacheDir(), PNG_FILE);
                try (InputStream in = new java.io.FileInputStream(input);
                     OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IOException("Невозможно сохранить PNG");
                    byte[] bytes = new byte[8192];
                    int n;
                    while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
                }
                message("PNG экспортирован");
            }
        } catch (Exception error) {
            message("Ошибка сохранения: " + error.getMessage());
        }
    }

    private void writeUri(Uri uri, byte[] bytes) throws IOException {
        try (OutputStream stream = getContentResolver().openOutputStream(uri, "w")) {
            if (stream == null) throw new IOException("Не удалось открыть файл на запись");
            stream.write(bytes);
        }
    }

    private void message(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    public final class NativeBridge {
        @JavascriptInterface public void openSvg() {
            runOnUiThread(MainActivity.this::pickSvg);
        }

        @JavascriptInterface public void autosave(String source) {
            try {
                String text = Store.validate(source);
                synchronized (ioLock) { Store.writeLocal(MainActivity.this, Store.CURRENT, text); }
            } catch (Exception ignored) {
                // The editor remains usable even when its autosave quota is exceeded.
            }
        }

        @JavascriptInterface public void saveSvg(String source) {
            try {
                String text = Store.validate(source);
                synchronized (ioLock) { Store.writeLocal(MainActivity.this, Store.EXPORT, text); }
                runOnUiThread(() -> createDocument("image/svg+xml", "drawing.svg", SAVE_SVG));
            } catch (Exception e) { runOnUiThread(() -> message(e.getMessage())); }
        }

        @JavascriptInterface public void exportPng(String encoded) {
            try {
                final String prefix = "data:image/png;base64,";
                if (encoded == null || !encoded.startsWith(prefix) || encoded.length() > 28000000)
                    throw new IOException("Некорректный или слишком большой PNG");
                byte[] png = Base64.decode(encoded.substring(prefix.length()), Base64.DEFAULT);
                if (png.length < 8 || png[0] != (byte) 137 || png[1] != 80 ||
                        png[2] != 78 || png[3] != 71) throw new IOException("Неверный PNG");
                try (FileOutputStream out = new FileOutputStream(new File(getCacheDir(), PNG_FILE))) {
                    out.write(png);
                }
                runOnUiThread(() -> createDocument("image/png", "drawing.png", EXPORT_PNG));
            } catch (Exception e) { runOnUiThread(() -> message(e.getMessage())); }
        }
    }

    @Override public void onBackPressed() {
        // App has one editor screen; normal Android back exits and autosave persists.
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidBridge");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
