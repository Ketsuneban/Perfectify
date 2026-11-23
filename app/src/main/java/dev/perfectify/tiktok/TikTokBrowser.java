package dev.perfectify.tiktok;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Secure TikTok web browser with popup/file chooser/error support.
 *
 * Google OAuth is never run in an embedded WebView: Google disallows it.
 * External browser sessions stay external and cannot be imported into WebView.
 */
public final class TikTokBrowser {
    public static final int FILE_REQUEST = 912;
    private static final int DARK = Color.rgb(12, 17, 27);
    private static final int LIGHT = Color.rgb(237, 244, 255);
    private static final int ACCENT = Color.rgb(144, 187, 255);

    private final Activity activity;
    private final FrameLayout holder;
    private final ProgressBar progress;
    private final TextView status;
    private WebView primary;
    private WebView popup;
    private LinearLayout popupPanel;
    private ValueCallback<Uri[]> fileCallback;
    private boolean googleDialogOpen;
    private Runnable afterPageLoaded;

    public TikTokBrowser(Activity activity, FrameLayout holder, ProgressBar progress, TextView status) {
        this.activity = activity;
        this.holder = holder;
        this.progress = progress;
        this.status = status;
        createPrimary();
    }

    public void setAfterPageLoaded(Runnable callback) {
        this.afterPageLoaded = callback;
    }

    public WebView view() { return primary; }

    private void createPrimary() {
        primary = new WebView(activity);
        configure(primary, false);
        holder.addView(primary, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    private void configure(WebView view, boolean child) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(!child);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDefaultTextEncodingName("UTF-8");
        view.setBackgroundColor(DARK);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        // Some official TikTok login methods rely on cookies set after redirects.
        cookies.setAcceptThirdPartyCookies(view, true);
        view.setWebChromeClient(new ChromeClient(child));
        view.setWebViewClient(new PageClient(child));
        view.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            if (url == null || !url.startsWith("https://")) return;
            new AlertDialog.Builder(activity)
                    .setTitle("Скачивание через браузер")
                    .setMessage("Эта ссылка ведёт на файл. Открыть его в системном браузере для сохранения?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Открыть", (dialog, which) -> openExternal(url))
                    .show();
        });
    }

    private static boolean isGoogleAuthentication(Uri url) {
        if (url == null || !"https".equalsIgnoreCase(url.getScheme())) return false;
        String host = url.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("accounts.google.com") || host.equals("accounts.google.ru")
                || (host.equals("google.com") && url.getPath() != null
                    && url.getPath().startsWith("/accounts/"));
    }

    private void explainGoogle(Uri url) {
        if (googleDialogOpen || activity.isFinishing()) return;
        googleDialogOpen = true;
        status.setText("Вход Google требует обычный браузер — встроенный вход заблокирован Google");
        new AlertDialog.Builder(activity)
                .setTitle("Вход через Google")
                .setMessage("Google не разрешает вход в аккаунт через встроенные Android WebView. Поэтому кнопка «Продолжить» может не работать.\n\nМожно войти через Google в Chrome, но Chrome не передаёт авторизацию TikTok обратно в Perfectify. Для синхронизации внутри приложения используй вход через телефон, почту или другой способ, доступный на tiktok.com.\n\nЕсли Google — единственный способ входа, используй импорт официального архива данных TikTok.")
                .setNegativeButton("Вернуться к TikTok", (dialog, which) -> googleDialogOpen = false)
                .setNeutralButton("Как импортировать", (dialog, which) -> {
                    googleDialogOpen = false;
                    new AlertDialog.Builder(activity)
                            .setTitle("Архив TikTok")
                            .setMessage("В обычном TikTok открой Настройки → Аккаунт → Скачать ваши данные. После подготовки архива вернись в Perfectify и выбери «Импорт архива» в меню действий.")
                            .setPositiveButton("Понятно", null).show();
                })
                .setPositiveButton("Открыть Chrome", (dialog, which) -> {
                    googleDialogOpen = false;
                    // Open the original TikTok login page, never a captured OAuth redirect.
                    openExternal("https://www.tiktok.com/login");
                })
                .setOnCancelListener(dialog -> googleDialogOpen = false)
                .show();
    }

    public void openExternal(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Prefer the full browser and not the installed TikTok You handler.
            try {
                Intent chrome = new Intent(intent);
                chrome.setPackage("com.android.chrome");
                activity.startActivity(chrome);
                return;
            } catch (Exception ignored) {}
            activity.startActivity(Intent.createChooser(intent, "Открыть в браузере"));
        } catch (Exception e) {
            status.setText("Не удалось открыть внешний браузер");
        }
    }

    private final class PageClient extends WebViewClient {
        private final boolean child;
        PageClient(boolean child) { this.child = child; }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) return false;
            Uri uri = request.getUrl();
            if (isGoogleAuthentication(uri)) {
                if (child) closePopup();
                explainGoogle(uri);
                return true;
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                if ("http".equalsIgnoreCase(uri.getScheme())) {
                    status.setText("Незащищённая ссылка HTTP заблокирована");
                }
                return true;
            }
            return false;
        }

        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            if (url == null) return;
            Uri parsed = Uri.parse(url);
            if (isGoogleAuthentication(parsed)) {
                view.stopLoading();
                if (child) closePopup();
                explainGoogle(parsed);
                return;
            }
            if (!child) status.setText("Загружается: " + (parsed.getHost() == null ? "страница" : parsed.getHost()));
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (!child) {
                status.setText(QueueStore.isTikTokUrl(url)
                        ? "TikTok · браузер готов. Для сохранения аккаунта открой свой профиль."
                        : "Открыта страница авторизации или внешнего сервиса");
                CookieManager.getInstance().flush();
                if (afterPageLoaded != null) afterPageLoaded.run();
            }
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                              WebResourceError error) {
            if (!child && request.isForMainFrame())
                status.setText("Не удалось открыть страницу · проверь интернет и попробуй обновить");
        }

        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (child) {
                closePopup();
                return true;
            }
            status.setText("Веб-процесс перезапущен после сбоя. Нажми «Обновить».");
            holder.removeView(view);
            view.destroy();
            primary = null;
            createPrimary();
            primary.loadUrl("https://www.tiktok.com/");
            if (afterPageLoaded != null) afterPageLoaded.run();
            return true;
        }
    }

    private final class ChromeClient extends WebChromeClient {
        private final boolean child;
        ChromeClient(boolean child) { this.child = child; }

        @Override public void onProgressChanged(WebView view, int newProgress) {
            if (child) return;
            progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.INVISIBLE);
            progress.setProgress(newProgress);
        }

        @Override public boolean onCreateWindow(WebView view, boolean isDialog,
                                                 boolean isUserGesture, Message resultMsg) {
            if (child || popup != null || !QueueStore.isTikTokUrl(view.getUrl())) return false;
            popup = new WebView(activity);
            configure(popup, true);
            popupPanel = new LinearLayout(activity);
            popupPanel.setOrientation(LinearLayout.VERTICAL);
            popupPanel.setBackgroundColor(DARK);
            LinearLayout toolbar = new LinearLayout(activity);
            toolbar.setPadding(16, 8, 16, 8);
            toolbar.setGravity(Gravity.CENTER_VERTICAL);
            TextView caption = new TextView(activity);
            caption.setText("Окно авторизации");
            caption.setTextColor(LIGHT);
            caption.setTextSize(15);
            caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            toolbar.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
            Button close = new Button(activity);
            close.setAllCaps(false);
            close.setText("Закрыть ✕");
            close.setTextColor(ACCENT);
            close.setOnClickListener(v -> closePopup());
            toolbar.addView(close, new LinearLayout.LayoutParams(-2, -2));
            popupPanel.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
            popupPanel.addView(popup, new LinearLayout.LayoutParams(-1, 0, 1));
            holder.addView(popupPanel, new FrameLayout.LayoutParams(-1, -1));
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }

        @Override public void onCloseWindow(WebView window) { closePopup(); }

        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                   FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                Intent intent = params.createIntent();
                activity.startActivityForResult(intent, FILE_REQUEST);
                return true;
            } catch (Exception error) {
                fileCallback.onReceiveValue(null);
                fileCallback = null;
                status.setText("Не удалось открыть выбор файла");
                return false;
            }
        }
    }

    public boolean handleFileResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_REQUEST) return false;
        if (fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
        return true;
    }

    public boolean closePopup() {
        if (popup == null) return false;
        WebView old = popup;
        popup = null;
        if (popupPanel != null) holder.removeView(popupPanel);
        popupPanel = null;
        old.stopLoading();
        old.destroy();
        return true;
    }

    public void onPause() {
        CookieManager.getInstance().flush();
        if (primary != null) primary.onPause();
    }

    public void onResume() {
        if (primary != null) primary.onResume();
    }

    public void destroy() {
        closePopup();
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (primary != null) {
            holder.removeView(primary);
            primary.destroy();
            primary = null;
        }
    }
}
