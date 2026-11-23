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
        setContentView(root);

        LinearLayout titleBar = row();
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(17), dp(12), dp(16), dp(12));
        root.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(66)));

        LinearLayout titles = column();
        titleBar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = label("Perfectify", 23, TEXT, true);
        titles.addView(title);
        titles.addView(label("TikTok archive · preview 0.2.0", 11, MUTED, false));
        Button mod = button("Мой профиль ↗", false, this::openMyProfile);
        titleBar.addView(mod, new LinearLayout.LayoutParams(-2, dp(40)));

        LinearLayout tabs = row();
        tabs.setPadding(dp(14), 0, dp(14), dp(8));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(50)));
        browserTab = button("Браузер TikTok", true, () -> showQueue(false));
        queueTab = button("Очередь / ссылки", false, () -> showQueue(true));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(40), 1);
        half.setMargins(dp(3), 0, dp(3), 0);
        tabs.addView(browserTab, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(40), 1);
        half2.setMargins(dp(3), 0, dp(3), 0);
        tabs.addView(queueTab, half2);

        createBrowserPane();
        createQueuePane();
        showQueue(false);
    }

    private void createBrowserPane() {
        browserPane = column();
        root.addView(browserPane, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout picker = row();
        picker.setGravity(Gravity.CENTER_VERTICAL);
        picker.setPadding(dp(15), dp(2), dp(15), dp(5));
        browserPane.addView(picker, new LinearLayout.LayoutParams(-1, dp(36)));
        categoryLabel = label("Категория: " + category, 13, BLUE, true);
        picker.addView(categoryLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tip = label("▼ выбрать", 12, MUTED, false);
        picker.addView(tip);
        picker.setOnClickListener(v -> chooseCategory());

        browserStatus = label("Войди на tiktok.com в этом браузере, затем нажми «Синхронизировать аккаунт». TikTok You не требуется.", 11, MUTED, false);
        browserStatus.setPadding(dp(16), 0, dp(16), dp(6));
        browserPane.addView(browserStatus);
        syncStatus = label("Синхронизация не запущена", 12, BLUE, false);
        syncStatus.setPadding(dp(16), 0, dp(16), dp(6));
        browserPane.addView(syncStatus);

        browser = new WebView(this);
        browser.setBackgroundColor(BG);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(true);
        browser.getSettings().setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (!"https".equalsIgnoreCase(target.getScheme())) return true;
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (QueueStore.isTikTokUrl(url)) {
                    browserStatus.setText("Открой доступный раздел своего профиля. Собираются только видео, показанные на странице.");
                } else {
                    browserStatus.setText("Встроенный браузер · внешний вход может быть ограничен TikTok");
                }
            }
        });
        browserPane.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        accountSync = new AccountSync(this, browser, new AccountSync.Listener() {
            @Override public void onStatus(String status) {
                syncStatus.setText(status);
            }
            @Override public void onFinished(int count, String summary) {
                syncStatus.setText(summary);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Синхронизация")
                    .setMessage(summary + "\n\nОткрой вкладку «Очередь», чтобы скачать доступные ролики.")
                    .setPositiveButton("Очередь", (dialog, which) -> showQueue(true))
                    .setNegativeButton("ОК", null).show();
            }
        });

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        browserPane.addView(actionsScroll, new LinearLayout.LayoutParams(-1, dp(57)));
        LinearLayout actions = row();
        actions.setPadding(dp(10), dp(7), dp(10), dp(7));
        actionsScroll.addView(actions);
        addButton(actions, "Синхронизировать аккаунт", true, this::syncAccount);
        addButton(actions, "Сканировать раздел", false, this::syncCurrentSection);
        addButton(actions, "Остановить синхр.", false, this::stopSync);
        addButton(actions, "Импорт архива", false, this::openArchivePicker);
        addButton(actions, "Захватить видео", false, this::captureCurrentVideo);
        addButton(actions, "⌂", false, () -> browser.loadUrl(TIKTOK_HOME));
        addButton(actions, "↻", false, () -> browser.reload());
        addButton(actions, "Очистить вход", false, this::confirmLogout);
    }

    private void createQueuePane() {
        queuePane = column();
        root.addView(queuePane, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        queuePane.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = column();
        page.setPadding(dp(15), dp(8), dp(15), dp(20));
        scroll.addView(page);

        page.addView(label("Добавить видео", 19, TEXT, true));
        TextView intro = label("Ссылки автоматически поступают после синхронизации профиля или импорта архива TikTok. Ручное добавление необязательно.", 13, MUTED, false);
        intro.setPadding(0, dp(7), 0, dp(10));
        page.addView(intro);

        LinearLayout choice = row();
        choice.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(choice);
        TextView choiceTitle = label("Сохранять в категорию: ", 13, MUTED, false);
        choice.addView(choiceTitle);
        TextView choiceValue = label(category + "  ▼", 13, BLUE, true);
        choice.addView(choiceValue);
        choice.setOnClickListener(v -> chooseCategory());

        linksInput = new EditText(this);
        linksInput.setTextColor(TEXT);
        linksInput.setHintTextColor(MUTED);
        linksInput.setTextSize(14);
        linksInput.setMinLines(3);
        linksInput.setMaxLines(6);
        linksInput.setGravity(Gravity.TOP | Gravity.START);
        linksInput.setHint("https://www.tiktok.com/@user/video/...\nhttps://vm.tiktok.com/...");
        linksInput.setBackground(shape(PANEL, 14));
        linksInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.setMargins(0, dp(12), 0, dp(10));
        page.addView(linksInput, inputLp);

        HorizontalScrollView addScroll = new HorizontalScrollView(this);
        addScroll.setHorizontalScrollBarEnabled(false);
        page.addView(addScroll);
        LinearLayout addActions = row();
        addScroll.addView(addActions);
        addButton(addActions, "Добавить ссылки", true, this::addInputLinks);
        addButton(addActions, "Вставить", false, this::pasteClipboard);
        addButton(addActions, "Повторить ошибки", false, () -> {
            QueueStore.retryFailed(this);
            renderQueue();
        });

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(42, 54, 74));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(21), 0, dp(17));
        page.addView(divider, divLp);

        queueSummary = label("Очередь загрузок", 18, TEXT, true);
        page.addView(queueSummary);
        TextView warning = label("Скрытые или удалённые видео не открываются автоматически. Если TikTok не отдаёт видеофайл, будет показана ошибка.", 12, MUTED, false);
        warning.setPadding(0, dp(7), 0, dp(10));
        page.addView(warning);

        queueRows = column();
        page.addView(queueRows);

        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        queuePane.addView(controlsScroll, new LinearLayout.LayoutParams(-1, dp(63)));
        LinearLayout controls = row();
        controls.setPadding(dp(10), dp(5), dp(10), dp(9));
        controlsScroll.addView(controls);
        addButton(controls, "▶ Скачать очередь", true, this::startQueue);
        addButton(controls, "■ Стоп", false, this::stopQueue);
        addButton(controls, "Очистить готовые", false, () -> {
            QueueStore.clearCompleted(this);
            renderQueue();
        });
    }

    private void showQueue(boolean show) {
        queueVisible = show;
        browserPane.setVisibility(show ? View.GONE : View.VISIBLE);
        queuePane.setVisibility(show ? View.VISIBLE : View.GONE);
        browserTab.setBackground(shape(show ? PANEL2 : BLUE, 11));
        browserTab.setTextColor(show ? TEXT : BG);
        queueTab.setBackground(shape(show ? BLUE : PANEL2, 11));
        queueTab.setTextColor(show ? BG : TEXT);
        if (show) renderQueue();
    }

    private void chooseCategory() {
        String[] options = {"Ссылки", "Избранное", "Понравившиеся", "Коллекции", "Репосты", "Профиль"};
        new AlertDialog.Builder(this)
                .setTitle("Категория локального архива")
                .setSingleChoiceItems(options, indexOf(options, category), (dialog, which) -> {
                    category = options[which];
                    categoryLabel.setText("Категория: " + category);
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
            card.setBackground(shape(PANEL, 12));
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
        if (queueVisible) { showQueue(false); return; }
        if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(queueRefresh);
        if (accountSync != null && accountSync.isRunning()) accountSync.cancel();
        importExecutor.shutdownNow();
        browser.destroy();
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
        result.setBackground(shape(primary ? BLUE : PANEL2, 11));
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
        return shape;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
