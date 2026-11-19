package dev.ketsuneban.ibissvgbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class OverlayService extends Service {
    static final String ACTION_SHOW = "dev.ketsuneban.ibissvgbridge.SHOW";
    private static final String CHANNEL = "svg_bridge";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private WebView web;
    private TextView title;
    private boolean minimized;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "SVG Bridge overlay", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setContentTitle("SVG Bridge")
                .setContentText("Плавающий редактор SVG работает")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(117, notice);
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (overlay == null) {
            if (!Settings.canDrawOverlays(this)) stopSelf();
            return START_NOT_STICKY;
        }
        if (minimized) toggleMinimize();
        loadCurrent();
        return START_NOT_STICKY;
    }

    private int dp(float n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private void createOverlay() {
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.rgb(21, 28, 42));
        overlay.setElevation(dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(9), 0, dp(5), 0);
        header.setBackgroundColor(Color.rgb(31, 42, 59));
        title = new TextView(this);
        title.setText("✦ SVG");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button collapse = new Button(this);
        collapse.setText("—");
        collapse.setAllCaps(false);
        header.addView(collapse, new LinearLayout.LayoutParams(dp(50), dp(46)));
        collapse.setOnClickListener(v -> toggleMinimize());

        Button close = new Button(this);
        close.setText("×");
        close.setAllCaps(false);
        header.addView(close, new LinearLayout.LayoutParams(dp(50), dp(46)));
        close.setOnClickListener(v -> stopSelf());
        overlay.addView(header);

        title.setOnTouchListener(new View.OnTouchListener() {
            float initialX, initialY, pointerX, pointerY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (params == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        pointerX = event.getRawX();
                        pointerY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + event.getRawX() - pointerX);
                        params.y = (int) (initialY + event.getRawY() - pointerY);
                        manager.updateViewLayout(overlay, params);
                        return true;
                    default:
                        return true;
                }
            }
        });

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setBackgroundColor(Color.rgb(19, 25, 35));
        overlay.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                loadCurrent();
            }
        });

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(screenWidth - dp(12), dp(540));
        int height = Math.min(screenHeight - dp(75), dp(650));
        params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = Math.max(0, screenWidth - width - dp(8));
        params.y = dp(45);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        manager.addView(overlay, params);

        try (InputStream in = getAssets().open("editor.html");
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) buffer.write(bytes, 0, n);
            String html = buffer.toString(StandardCharsets.UTF_8.name());
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/", html,
                    "text/html", "UTF-8", null);
        } catch (Exception e) {
            title.setText("Ошибка редактора: " + e.getMessage());
        }
    }

    private void toggleMinimize() {
        if (overlay == null) return;
        minimized = !minimized;
        if (minimized) {
            web.setVisibility(View.GONE);
            params.width = dp(174);
            params.height = dp(47);
            title.setText("✦ SVG Bridge");
        } else {
            web.setVisibility(View.VISIBLE);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            params.width = Math.min(w - dp(12), dp(540));
            params.height = Math.min(h - dp(75), dp(650));
            title.setText("✦ SVG");
        }
        manager.updateViewLayout(overlay, params);
    }

    private void loadCurrent() {
        if (web == null) return;
        try {
            String svg = Store.readLocal(this, Store.CURRENT);
            web.evaluateJavascript("window.editor && window.editor.loadSvg(" +
                    JSONObject.quote(svg) + ");", null);
        } catch (Exception ignored) { // Newly opened editor starts with a blank document.
        }
    }

    private void launch(String action) {
        handler.post(() -> {
            Intent intent = new Intent(OverlayService.this, MainActivity.class);
            intent.setAction(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }

    public final class Bridge {
        @JavascriptInterface public void openSvg() { launch(MainActivity.ACTION_OPEN); }

        @JavascriptInterface public void saveSvg(String source) {
            try {
                Store.writeLocal(OverlayService.this, Store.EXPORT, Store.validate(source));
                launch(MainActivity.ACTION_SAVE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }

        @JavascriptInterface public void sharePng(String dataUrl) {
            try {
                String prefix = "data:image/png;base64,";
                if (dataUrl == null || !dataUrl.startsWith(prefix) ||
                        dataUrl.length() > 16 * 1024 * 1024) {
                    throw new IllegalArgumentException("PNG too large or invalid");
                }
                byte[] bytes = Base64.decode(dataUrl.substring(prefix.length()), Base64.DEFAULT);
                if (bytes.length < 8 || bytes[0] != (byte) 137 ||
                        bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71) {
                    throw new IllegalArgumentException("Invalid PNG signature");
                }
                File dest = new File(getCacheDir(), Store.PNG);
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    out.write(bytes);
                }
                launch(MainActivity.ACTION_SHARE);
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }

        @JavascriptInterface public void close() {
            handler.post(OverlayService.this::stopSelf);
        }

        private void showError(String error) {
            handler.post(() -> {
                if (web != null) web.evaluateJavascript(
                        "window.editor && window.editor.showError(" +
                                JSONObject.quote(error) + ");", null);
            });
        }
    }

    @Override public void onDestroy() {
        if (manager != null && overlay != null) {
            manager.removeView(overlay);
            overlay = null;
        }
        if (web != null) {
            web.removeJavascriptInterface("AndroidBridge");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
