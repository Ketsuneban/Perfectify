package dev.perfectify.tiktok;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.graphics.Insets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 16, 24);
    private static final int PANEL = Color.rgb(22, 30, 43);
    private static final int PANEL2 = Color.rgb(29, 39, 55);
    private static final int BLUE = Color.rgb(153, 198, 255);
    private static final int TEXT = Color.rgb(240, 245, 252);
    private static final int MUTED = Color.rgb(155, 169, 190);
    private static final int ERROR = Color.rgb(248, 139, 139);
    private static final int GOOD = Color.rgb(143, 223, 181);
    private static final String TIKTOK_HOME = "https://www.tiktok.com/";
    private static final int REQUEST_ARCHIVE = 810;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private LinearLayout browserPane;
    private LinearLayout queuePane;
    private LinearLayout queueRows;
    private WebView browser;
    private EditText linksInput;
    private TextView categoryLabel;
    private TextView browserStatus;
    private TextView syncStatus;
    private AccountSync accountSync;
    private TikTokBrowser webController;
    private ProgressBar pageProgress;
    private TextView queueCategoryLabel;
    private int lastQueueSignature = Integer.MIN_VALUE;
    private TextView queueSummary;
    private Button browserTab;
    private Button queueTab;
    private String category = "Ссылки";
    private boolean queueVisible = false;

    private final Runnable queueRefresh = new Runnable() {
        @Override public void run() {
            if (queueVisible && !isFinishing()) renderQueue();
            handler.postDelayed(this, 1800);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        buildScreen();
        if (state == null) browser.loadUrl(TIKTOK_HOME);
        else browser.loadUrl(state.getString("url", TIKTOK_HOME));
        acceptSharedText(getIntent());
        handler.postDelayed(queueRefresh, 1500);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 303);
        }
    }

    private void buildScreen() {
        root = column();
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        setContentView(root);

        // Android 15+ enforces edge-to-edge at target SDK 35. Respect BOTH
        // 3-button navigation and gesture navigation, including the IME.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets keyboard = windowInsets.getInsets(WindowInsets.Type.ime());
                root.setPadding(bars.left, bars.top, bars.right,
                        Math.max(bars.bottom, keyboard.bottom));
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        } else {
            root.setFitsSystemWindows(true);
        }

        LinearLayout titleBar = row();
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(20), dp(10), dp(16), dp(10));
        root.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(67)));

        LinearLayout branding = column();
        titleBar.addView(branding, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = label("Perfectify", 24, TEXT, true);
        title.setLetterSpacing(-0.035f);
        branding.addView(title);
        branding.addView(label("Личный архив TikTok  ·  v0.3", 11, MUTED, false));

        Button profile = button("Профиль  ↗", false, this::openMyProfile);
        titleBar.addView(profile, new LinearLayout.LayoutParams(-2, dp(40)));

        LinearLayout selector = row();
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(dp(4), dp(4), dp(4), dp(4));
        selector.setBackground(shape(PANEL, 16));
        LinearLayout.LayoutParams selectorLp = new LinearLayout.LayoutParams(-1, dp(52));
        selectorLp.setMargins(dp(16), 0, dp(16), dp(10));
        root.addView(selector, selectorLp);
        browserTab = button("◉  Аккаунт", true, () -> showQueue(false));
        queueTab = button("↓  Загрузки", false, () -> showQueue(true));
        LinearLayout.LayoutParams tabA = new LinearLayout.LayoutParams(0, -1, 1);
        tabA.setMargins(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams tabB = new LinearLayout.LayoutParams(0, -1, 1);
        tabB.setMargins(dp(2), 0, dp(2), 0);
        selector.addView(browserTab, tabA);
        selector.addView(queueTab, tabB);

        createBrowserPane();
        createQueuePane();
        showQueue(false);
    }

    private void createBrowserPane() {
        browserPane = column();
        root.addView(browserPane, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout quick = row();
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(dp(16), dp(3), dp(16), dp(6));
        browserPane.addView(quick, new LinearLayout.LayoutParams(-1, dp(47)));
        addMiniButton(quick, "‹", () -> {
            if (webController.closePopup()) return;
            if (browser.canGoBack()) browser.goBack();
        });
        addMiniButton(quick, "›", () -> { if (browser.canGoForward()) browser.goForward(); });
        addMiniButton(quick, "⟳", () -> browser.reload());
        addMiniButton(quick, "⌂", () -> browser.loadUrl(TIKTOK_HOME));
        TextView host = label("  🔒  tiktok.com", 12, MUTED, true);
        host.setGravity(Gravity.CENTER_VERTICAL);
        host.setBackground(shape(PANEL, 12));
        host.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        hostLp.setMargins(dp(5), 0, 0, 0);
        quick.addView(host, hostLp);

        LinearLayout scanMeta = row();
        scanMeta.setGravity(Gravity.CENTER_VERTICAL);
        scanMeta.setPadding(dp(16), 0, dp(16), dp(5));
        browserPane.addView(scanMeta, new LinearLayout.LayoutParams(-1, dp(32)));
        categoryLabel = label("◈  Категория: " + category + "  ▾", 12, BLUE, true);
        categoryLabel.setGravity(Gravity.CENTER_VERTICAL);
        scanMeta.addView(categoryLabel, new LinearLayout.LayoutParams(0, -1, 1));
        categoryLabel.setOnClickListener(v -> chooseCategory());
        TextView browserHint = label("Вход через TikTok", 11, MUTED, false);
        scanMeta.addView(browserHint);

        browserStatus = label("Войди на tiktok.com через доступный способ входа.", 11, MUTED, false);
        browserStatus.setPadding(dp(18), 0, dp(18), dp(7));
        browserStatus.setMaxLines(2);
        browserPane.addView(browserStatus);

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setIndeterminate(false);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        pageProgress.setVisibility(View.INVISIBLE);
        browserPane.addView(pageProgress, new LinearLayout.LayoutParams(-1, dp(2)));

        FrameLayout browserHost = new FrameLayout(this);
        browserHost.setBackground(shape(PANEL, 16));
        browserHost.setClipToOutline(true);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, 0, 1);
        webLp.setMargins(dp(12), 0, dp(12), dp(8));
        browserPane.addView(browserHost, webLp);
        webController = new TikTokBrowser(this, browserHost, pageProgress, browserStatus);
        browser = webController.view();
        webController.setAfterPageLoaded(() -> {
            if (webController.view() != browser) {
                if (accountSync != null && accountSync.isRunning()) accountSync.cancel();
                browser = webController.view();
                installAccountSync();
            }
        });
        installAccountSync();

        LinearLayout statusPanel = row();
        statusPanel.setGravity(Gravity.CENTER_VERTICAL);
        statusPanel.setPadding(dp(13), dp(7), dp(13), dp(7));
        statusPanel.setBackground(shape(PANEL, 12));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(48));
        statusLp.setMargins(dp(16), 0, dp(16), dp(9));
        browserPane.addView(statusPanel, statusLp);
        TextView dot = label("●  ", 11, GOOD, true);
        statusPanel.addView(dot);
        syncStatus = label("Готово к синхронизации", 12, MUTED, false);
        syncStatus.setMaxLines(2);
        syncStatus.setEllipsize(TextUtils.TruncateAt.END);
        statusPanel.addView(syncStatus, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout dock = row();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(16), dp(6), dp(16), dp(9));
        browserPane.addView(dock, new LinearLayout.LayoutParams(-1, dp(64)));
        Button sync = button("◉  Синхронизировать", true, this::syncAccount);
        LinearLayout.LayoutParams syncLp = new LinearLayout.LayoutParams(0, dp(47), 1);
        syncLp.setMargins(0, 0, dp(8), 0);
        dock.addView(sync, syncLp);
        Button more = button("Действия  ⋯", false, this::showBrowserActions);
        dock.addView(more, new LinearLayout.LayoutParams(dp(121), dp(47)));
    }

    private void installAccountSync() {
        accountSync = new AccountSync(this, browser, new AccountSync.Listener() {
            @Override public void onStatus(String state) {
                if (syncStatus != null) syncStatus.setText(state);
            }
            @Override public void onFinished(int count, String summary) {
                if (syncStatus != null) syncStatus.setText(summary);
                if (isFinishing()) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Синхронизация")
                        .setMessage(summary + "\n\nНайденные видео добавлены в очередь.")
                        .setPositiveButton("Посмотреть очередь", (dialog, which) -> showQueue(true))
                        .setNegativeButton("Готово", null).show();
            }
        });
    }

    private void showBrowserActions() {
        String[] options = {
                "Сканировать открытый раздел",
                "Импорт архива TikTok",
                "Сохранить открытый ролик",
                "Остановить синхронизацию",
                "Открыть TikTok в обычном браузере",
                "Помощь со входом Google",
                "Очистить вход в Perfectify"
        };
        new AlertDialog.Builder(this)
                .setTitle("Действия аккаунта")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: syncCurrentSection(); break;
                        case 1: openArchivePicker(); break;
                        case 2: captureCurrentVideo(); break;
                        case 3: stopSync(); break;
                        case 4: webController.openExternal("https://www.tiktok.com/login"); break;
                        case 5: googleLoginHelp(); break;
                        case 6: confirmLogout(); break;
                    }
                })
                .setNegativeButton("Закрыть", null).show();
    }

    private void googleLoginHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Почему вход Google не продолжается?")
                .setMessage("Google запрещает OAuth-авторизацию через встроенный Android WebView. Внешний браузер поддерживает вход, но его cookies TikTok не передаются Perfectify.\n\nДля синхронизации через встроенный TikTok используй доступный вход по телефону, почте или другой способ. Если это невозможно, запроси официальный архив данных TikTok и импортируй его в Perfectify.")
                .setNeutralButton("Импорт архива", (dialog, which) -> openArchivePicker())
                .setPositiveButton("Понятно", null).show();
    }

    private void addMiniButton(LinearLayout container, String title, Runnable click) {
        Button b = button(title, false, click);
        b.setTextSize(20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(39), dp(39));
        lp.setMargins(0, 0, dp(5), 0);
        container.addView(b, lp);
    }

    private void createQueuePane() {
        queuePane = column();
        root.addView(queuePane, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        queuePane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = column();
        page.setPadding(dp(16), dp(10), dp(16), dp(20));
        scroll.addView(page);

        TextView title = label("Моя медиатека", 22, TEXT, true);
        page.addView(title);
        TextView intro = label("Синхронизируй доступные видео или импортируй архив TikTok. Добавленные ссылки останутся на устройстве.", 13, MUTED, false);
        intro.setPadding(0, dp(7), 0, dp(15));
        page.addView(intro);

        LinearLayout addCard = column();
        addCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        addCard.setBackground(shape(PANEL, 17));
        page.addView(addCard, new LinearLayout.LayoutParams(-1, -2));

        addCard.addView(label("Добавление по ссылкам", 15, TEXT, true));
        LinearLayout categoryRow = row();
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(-1, dp(40));
        addCard.addView(categoryRow, catLp);
        queueCategoryLabel = label("◈  " + category + "   ▾", 13, BLUE, true);
        queueCategoryLabel.setGravity(Gravity.CENTER_VERTICAL);
        categoryRow.addView(queueCategoryLabel);
        categoryRow.setOnClickListener(v -> chooseCategory());

        linksInput = new EditText(this);
        linksInput.setTextColor(TEXT);
        linksInput.setHintTextColor(MUTED);
        linksInput.setTextSize(13);
        linksInput.setMinLines(2);
        linksInput.setMaxLines(5);
        linksInput.setGravity(Gravity.TOP | Gravity.START);
        linksInput.setHint("Вставь несколько ссылок tiktok.com…");
        linksInput.setBackground(shape(PANEL2, 11));
        linksInput.setPadding(dp(13), dp(12), dp(13), dp(12));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.setMargins(0, dp(5), 0, dp(9));
        addCard.addView(linksInput, inputLp);

        LinearLayout row = row();
        addCard.addView(row, new LinearLayout.LayoutParams(-1, dp(43)));
        Button add = button("Добавить", true, this::addInputLinks);
        Button paste = button("Вставить", false, this::pasteClipboard);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0, -1, 1);
        addLp.setMargins(0, 0, dp(8), 0);
        row.addView(add, addLp);
        row.addView(paste, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout queueHead = row();
        queueHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(-1, dp(56));
        headLp.setMargins(0, dp(17), 0, 0);
        page.addView(queueHead, headLp);
        queueSummary = label("Очередь загрузок", 18, TEXT, true);
        queueHead.addView(queueSummary, new LinearLayout.LayoutParams(0, -2, 1));
        Button retry = button("Повторить", false, () -> {
            QueueStore.retryFailed(this);
            lastQueueSignature = Integer.MIN_VALUE;
            renderQueue();
        });
        queueHead.addView(retry, new LinearLayout.LayoutParams(dp(103), dp(38)));

        queueRows = column();
        page.addView(queueRows);

        LinearLayout dock = row();
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(16), dp(7), dp(16), dp(8));
        queuePane.addView(dock, new LinearLayout.LayoutParams(-1, dp(68)));
        Button start = button("↓  Скачать очередь", true, this::startQueue);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        startLp.setMargins(0, 0, dp(8), 0);
        dock.addView(start, startLp);
        Button stop = button("Стоп", false, this::stopQueue);
        dock.addView(stop, new LinearLayout.LayoutParams(dp(76), dp(48)));
        Button clear = button("⋯", false, () ->
                new AlertDialog.Builder(this)
                        .setTitle("Управление загрузками")
                        .setItems(new String[]{"Очистить завершённые", "Повторить ошибки"},
                                (dialog, which) -> {
                                    if (which == 0) QueueStore.clearCompleted(this);
                                    else QueueStore.retryFailed(this);
                                    lastQueueSignature = Integer.MIN_VALUE;
                                    renderQueue();
                                }).show());
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dp(50), dp(48));
        clearLp.setMargins(dp(8), 0, 0, 0);
        dock.addView(clear, clearLp);
    }

    private void showQueue(boolean show) {
        queueVisible = show;
        lastQueueSignature = Integer.MIN_VALUE;
        browserPane.setVisibility(show ? View.GONE : View.VISIBLE);
        queuePane.setVisibility(show ? View.VISIBLE : View.GONE);
        browserTab.setBackground(shape(show ? PANEL : BLUE, 12));
        browserTab.setTextColor(show ? TEXT : BG);
        queueTab.setBackground(shape(show ? BLUE : PANEL, 12));
        queueTab.setTextColor(show ? BG : TEXT);
        if (show) renderQueue();
    }

    private void chooseCategory() {
        String[] options = {"Ссылки", "Избранное", "Понравившиеся", "Коллекции", "Репосты", "Профиль"};
        new AlertDialog.Builder(this)
                .setTitle("Категория локального архива")
                .setSingleChoiceItems(options, indexOf(options, category), (dialog, which) -> {
                    category = options[which];
                    categoryLabel.setText("◈  Категория: " + category + "  ▾");
                    if (queueCategoryLabel != null) queueCategoryLabel.setText("◈  " + category + "   ▾");
                    dialog.dismiss();
                    if ("Избранное".equals(category) || "Понравившиеся".equals(category)
                            || "Коллекции".equals(category)) {
                        toast("Открой соответствующий раздел в TikTok вручную; его доступность зависит от аккаунта");
                    }
                })
                .setNegativeButton("Отмена", null).show();
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++)
            if (values[i].equals(target)) return i;
        return 0;
    }

    private void addInputLinks() {
        int added = QueueStore.addText(this, linksInput.getText().toString(), category);
        if (added > 0) linksInput.setText("");
        toast("Добавлено: " + added + " (дубликаты пропущены)");
        renderQueue();
    }

    private void pasteClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { toast("Буфер пуст"); return; }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value != null) linksInput.append(value);
    }

    private void syncAccount() {
        if (accountSync != null && accountSync.isRunning()) {
            toast("Синхронизация уже выполняется");
            return;
        }
        if (!QueueStore.isTikTokUrl(browser.getUrl())) {
            toast("Сначала войди в TikTok во встроенном браузере");
            return;
        }
        String url = browser.getUrl();
        if (url != null && url.matches("https://[^/]*tiktok\\.com/@[^/?#]+/?([?#].*)?")) {
            accountSync.startProfile();
            return;
        }
        // Discover the signed-in user's own profile via a visible navigation link.
        String js = "(function(){let e=document.querySelector('a[data-e2e=\"profile-icon\"]')"
            + "||document.querySelector('[data-e2e=\"profile-icon\"] a')"
            + "||document.querySelector('nav a[aria-label=\"Profile\"]')"
            + "||document.querySelector('nav a[aria-label=\"Профиль\"]');"
            + "let href=e&&e.href?e.href:(e&&e.closest('a')?e.closest('a').href:'');"
            + "return href||'';})()";
        browser.evaluateJavascript(js, value -> {
            try {
                String link = decodeJS(value);
                if (link.matches("https://[^/]*tiktok\\.com/@[^/?#]+/?([?#].*)?")) {
                    browser.loadUrl(link);
                    syncStatus.setText("Открываю профиль…");
                    handler.postDelayed(() -> accountSync.startProfile(), 3300);
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Открой свой профиль")
                        .setMessage("TikTok не показал ссылку на твой аккаунт. Нажми «Профиль» внутри TikTok и затем «Синхронизировать аккаунт». Отправлять видео через «Поделиться» не нужно.")
                        .setPositiveButton("Понятно", null).show();
                }
            } catch (Exception e) {
                toast("Не удалось найти профиль. Открой его в TikTok.");
            }
        });
    }

    private void openMyProfile() {
        String js = "(function(){let e=document.querySelector('a[data-e2e=\"profile-icon\"]')"
                + "||document.querySelector('[data-e2e=\"profile-icon\"] a')"
                + "||document.querySelector('nav a[aria-label=\"Profile\"]')"
                + "||document.querySelector('nav a[aria-label=\"Профиль\"]');"
                + "return e?(e.href||(e.closest('a')?e.closest('a').href:'')):'';})()";
        browser.evaluateJavascript(js, result -> {
            try {
                String url = decodeJS(result);
                if (QueueStore.isTikTokUrl(url) && url.contains("/@")) browser.loadUrl(url);
                else toast("Открой «Профиль» непосредственно в TikTok.");
            } catch (Exception e) { toast("Ссылка на профиль пока недоступна"); }
        });
    }

    private void syncCurrentSection() {
        if (accountSync == null || accountSync.isRunning()) {
            toast("Синхронизация уже выполняется");
            return;
        }
        accountSync.startCurrent(category);
    }

    private void stopSync() {
        if (accountSync != null && accountSync.isRunning()) accountSync.cancel();
        else toast("Активной синхронизации нет");
    }

    private void openArchivePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip","application/json","text/plain","application/octet-stream"});
        startActivityForResult(intent, REQUEST_ARCHIVE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (webController != null && webController.handleFileResult(requestCode, resultCode, data)) return;
        if (requestCode != REQUEST_ARCHIVE || resultCode != RESULT_OK ||
                data == null || data.getData() == null) return;
        Uri file = data.getData();
        toast("Читаю архив локально…");
        importExecutor.execute(() -> {
            try {
                ArchiveImporter.Result result = ArchiveImporter.importFrom(
                        getApplicationContext(), file);
                runOnUiThread(() -> {
                    String msg = "Обработано файлов: " + result.files
                            + "\nСсылок: " + result.links
                            + "\nДобавлено видео: " + result.added
                            + "\nПропущено файлов: " + result.skipped;
                    new AlertDialog.Builder(this)
                        .setTitle("Импорт данных TikTok")
                        .setMessage(msg)
                        .setPositiveButton("Открыть очередь", (dialog, which) -> showQueue(true))
                        .setNegativeButton("ОК", null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Ошибка импорта")
                    .setMessage(e.getMessage())
                    .setPositiveButton("ОК", null).show());
            }
        });
    }

    private void collectVisibleLinks() {
        String current = browser.getUrl();
        if (!QueueStore.isTikTokUrl(current)) { toast("Открой страницу TikTok"); return; }
        String js = "(function(){var a=Array.from(document.querySelectorAll('a[href*=\"/video/\"]')).map(x=>x.href); return JSON.stringify(Array.from(new Set(a)).slice(0,500));})()";
        browser.evaluateJavascript(js, result -> {
            try {
                String decoded = decodeJS(result);
                JSONArray list = new JSONArray(decoded);
                ArrayList<String> urls = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) urls.add(list.optString(i));
                int added = QueueStore.add(this, urls, category, null);
                toast("На текущей странице: " + urls.size() + ", добавлено: " + added);
            } catch (Exception e) { toast("Не удалось прочитать ссылки на этой странице"); }
        });
    }

    private void captureCurrentVideo() {
        if (!QueueStore.isTikTokUrl(browser.getUrl())) { toast("Открой видео на TikTok"); return; }
        String js = "(function(){var v=document.querySelector('video');return JSON.stringify({page:location.href,media:v?(v.currentSrc||v.src):''});})()";
        browser.evaluateJavascript(js, result -> {
            try {
                JSONObject data = new JSONObject(decodeJS(result));
                String page = data.optString("page");
                String media = data.optString("media");
                if (!media.startsWith("https://")) {
                    toast("Видеопоток не открыт напрямую; попробуй добавить ссылку на ролик");
                    return;
                }
                VideoResolver.validateMediaUrl(media);
                int count = QueueStore.add(this, Collections.singletonList(page), category, media);
                toast(count > 0 ? "Видео добавлено в очередь" : "Видео уже есть в очереди");
            } catch (Exception e) { toast("Прямой адрес видео недоступен"); }
        });
    }

    private static String decodeJS(String value) throws Exception {
        return new JSONArray("[" + value + "]").getString(0);
    }

    private void startQueue() {
        List<QueueStore.Entry> entries = QueueStore.list(this);
        boolean pending = false;
        for (QueueStore.Entry e : entries) if ("queued".equals(e.state)) { pending = true; break; }
        if (!pending) { toast("Нет ожидающих видео"); return; }
        Intent intent = new Intent(this, DownloadService.class).setAction(DownloadService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            toast("Очередь запущена");
        } catch (Exception ex) { toast("Не удалось запустить службу: " + ex.getMessage()); }
    }

    private void stopQueue() {
        startService(new Intent(this, DownloadService.class).setAction(DownloadService.ACTION_STOP));
        toast("Остановка после текущей операции");
    }

    private void renderQueue() {
        if (queueRows == null) return;
        List<QueueStore.Entry> all = QueueStore.list(this);
        int fingerprint = all.size();
        for (QueueStore.Entry entry : all) {
            fingerprint = fingerprint * 31 + (entry.id == null ? 0 : entry.id.hashCode());
            fingerprint = fingerprint * 31 + (entry.state == null ? 0 : entry.state.hashCode());
            fingerprint = fingerprint * 31 + entry.progress;
            fingerprint = fingerprint * 31 + (entry.error == null ? 0 : entry.error.hashCode());
        }
        if (lastQueueSignature == fingerprint) return;
        lastQueueSignature = fingerprint;
        queueRows.removeAllViews();
        int pending = 0, done = 0, errors = 0;
        for (QueueStore.Entry e : all) {
            if ("done".equals(e.state)) done++;
            else if ("error".equals(e.state)) errors++;
            else pending++;
        }
        queueSummary.setText("Очередь · " + all.size() + "   ✓" + done + "   !" + errors + "   …" + pending);
        if (all.isEmpty()) {
            TextView empty = label("Очередь пуста. Добавь ссылки или поделись видео из TikTok You.", 14, MUTED, false);
            empty.setPadding(0, dp(14), 0, dp(14));
            queueRows.addView(empty);
            return;
        }
        int start = Math.max(0, all.size() - 120);
        if (start > 0) queueRows.addView(label("Показаны последние 120 элементов. Все элементы сохранены в очереди.", 12, MUTED, false));
        for (int i = all.size() - 1; i >= start; i--) {
            QueueStore.Entry item = all.get(i);
            LinearLayout card = column();
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(shape(PANEL, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(5), 0, dp(5));
            queueRows.addView(card, lp);
            LinearLayout line = row();
            card.addView(line);
            TextView categoryText = label(item.category, 13, TEXT, true);
            line.addView(categoryText, new LinearLayout.LayoutParams(0, -2, 1));
            String stateLabel;
            int color = MUTED;
            switch (item.state) {
                case "done": stateLabel = "ГОТОВО"; color = GOOD; break;
                case "error": stateLabel = "ОШИБКА"; color = ERROR; break;
                case "downloading": stateLabel = item.progress + "%"; color = BLUE; break;
                case "resolving": stateLabel = "ПОИСК"; color = BLUE; break;
                case "cancelled": stateLabel = "СТОП"; break;
                default: stateLabel = "В ОЧЕРЕДИ"; break;
            }
            line.addView(label(stateLabel, 12, color, true));
            TextView url = label(item.url, 11, MUTED, false);
            url.setMaxLines(2);
            url.setEllipsize(TextUtils.TruncateAt.END);
            url.setPadding(0, dp(5), 0, 0);
            card.addView(url);
            if ("error".equals(item.state) || "cancelled".equals(item.state)) {
                TextView note = label(item.error, 11, ERROR, false);
                note.setPadding(0, dp(5), 0, 0);
                card.addView(note);
            }
            if ("done".equals(item.state) && item.savedUri != null && !item.savedUri.isEmpty()) {
                card.setOnClickListener(v -> openSaved(item.savedUri));
            }
        }
    }

    private void openSaved(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri, "video/mp4");
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(open);
        } catch (Exception e) { toast("Открой видео из галереи"); }
    }

    private void launchTikTokYou() {
        String[] pkgs = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
        for (String name : pkgs) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(name);
                if (launch != null) {
                    startActivity(launch);
                    return;
                }
            } catch (Exception ignored) {}
        }
        toast("Не удалось найти TikTok You. Используй «Поделиться» в установленном моде.");
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить вход в браузере?")
                .setMessage("Будут удалены cookies встроенного браузера Perfectify. В TikTok You ты останешься авторизован.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    browser.clearCache(true);
                    browser.loadUrl(TIKTOK_HOME);
                    toast("Данные входа Perfectify очищены");
                }).show();
    }

    private void acceptSharedText(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        if (!"text/plain".equals(intent.getType())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return;
        int added = QueueStore.addText(this, text, category);
        showQueue(true);
        toast(added > 0 ? "Из TikTok You добавлено: " + added : "Новых ссылок TikTok не найдено");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptSharedText(intent);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("url", browser.getUrl() == null ? TIKTOK_HOME : browser.getUrl());
    }

    @Override public void onBackPressed() {
        if (webController != null && webController.closePopup()) return;
        if (queueVisible) { showQueue(false); return; }
        if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (webController != null) webController.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webController != null) webController.onResume();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(queueRefresh);
        if (accountSync != null && accountSync.isRunning()) accountSync.cancel();
        importExecutor.shutdownNow();
        if (webController != null) webController.destroy();
        super.onDestroy();
    }

    private LinearLayout row() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.HORIZONTAL);
        return result;
    }
    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private TextView label(String message, int size, int color, boolean strong) {
        TextView result = new TextView(this);
        result.setText(message);
        result.setTextSize(size);
        result.setTextColor(color);
        if (strong) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }
    private Button button(String caption, boolean primary, Runnable click) {
        Button result = new Button(this);
        result.setText(caption);
        result.setAllCaps(false);
        result.setTextSize(12);
        result.setTextColor(primary ? BG : TEXT);
        result.setMinHeight(0);
        result.setMinimumHeight(0);
        result.setMinWidth(0);
        result.setMinimumWidth(0);
        result.setPadding(dp(12), 0, dp(12), 0);
        result.setBackground(shape(primary ? BLUE : PANEL2, 12));
        result.setOnClickListener(v -> click.run());
        return result;
    }
    private void addButton(LinearLayout into, String text, boolean primary, Runnable action) {
        Button view = button(text, primary, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(dp(4), 0, dp(4), 0);
        into.addView(view, lp);
    }
    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        if (color != BLUE) shape.setStroke(dp(1), Color.rgb(47, 59, 77));
        return shape;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
