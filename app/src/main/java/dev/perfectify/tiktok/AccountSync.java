package dev.perfectify.tiktok;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads only visible, rendered links in the user's authenticated TikTok WebView.
 * It does not inspect cookies, copy credentials, or request undocumented APIs.
 */
public final class AccountSync {
    public interface Listener {
        void onStatus(String status);
        void onFinished(int added, String summary);
    }

    private static final int MAX_STEPS = 140;
    private final Context context;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<String> sections = new ArrayList<>();
    private String profileUrl = "";
    private boolean cancelled;
    private boolean running;
    private int sectionIndex;
    private int step;
    private int consecutiveStalls;
    private int totalAdded;
    private int lastCount;
    private final List<String> notes = new ArrayList<>();

    public AccountSync(Context context, WebView webView, Listener listener) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    public void cancel() {
        cancelled = true;
        running = false;
        main.removeCallbacksAndMessages(null);
        listener.onStatus("Синхронизация остановлена");
    }

    public void startCurrent(String label) {
        if (running) return;
        String url = webView.getUrl();
        if (!QueueStore.isTikTokUrl(url)) {
            listener.onFinished(0, "Открой TikTok во встроенном браузере.");
            return;
        }
        setup();
        sections.add(label);
        beginSection();
    }

    public void startProfile() {
        if (running) return;
        String url = webView.getUrl();
        if (!QueueStore.isTikTokUrl(url) || url == null ||
                !url.matches("https://[^/]*tiktok\\.com/@[^/?#]+/?([?#].*)?")) {
            listener.onFinished(0, "Открой СВОЙ профиль на сайте TikTok, затем нажми «Синхронизировать аккаунт».");
            return;
        }
        setup();
        profileUrl = url.split("[?#]")[0];
        Collections.addAll(sections, "Профиль", "Понравившиеся", "Избранное", "Репосты");
        listener.onStatus("Сканирование открытых разделов профиля…");
        beginSection();
    }

    private void setup() {
        cancelled = false;
        running = true;
        sectionIndex = 0;
        totalAdded = 0;
        sections.clear();
        notes.clear();
    }

    private void beginSection() {
        if (cancelled || !running) return;
        if (sectionIndex >= sections.size()) {
            finish("Готово. Добавлено " + totalAdded + " видео. " + String.join("; ", notes));
            return;
        }
        step = 0;
        consecutiveStalls = 0;
        lastCount = -1;
        String section = sections.get(sectionIndex);
        listener.onStatus("Раздел: " + section + " · открытие…");

        if (profileUrl.isEmpty() || sectionIndex == 0) {
            main.postDelayed(this::scan, 600);
            return;
        }
        // Return to the already opened profile, then click only a publicly exposed UI tab.
        webView.loadUrl(profileUrl);
        main.postDelayed(() -> chooseTab(section, 0), 2700);
    }

    private static final String TAB_SCRIPT_PREFIX = "(function(){";
    private void chooseTab(String section, int attempt) {
        if (cancelled || !running) return;
        String token = section.equals("Понравившиеся") ? "liked" :
                section.equals("Избранное") ? "favorites" : "reposts";
        // Only interact with the actual TikTok UI, never private/internal endpoints.
        String js = TAB_SCRIPT_PREFIX
                + "let type='" + token + "';"
                + "let selectors=type==='liked'?['[data-e2e=\"liked-tab\"]','[data-e2e=\"likes-tab\"]','[data-e2e=\"user-liked-tab\"]']:"
                + "type==='favorites'?['[data-e2e=\"favorites-tab\"]','[data-e2e=\"favorite-tab\"]','[data-e2e=\"user-favorite-tab\"]']:"
                + "['[data-e2e=\"repost-tab\"]','[data-e2e=\"reposts-tab\"]'];"
                + "let found=null;for(let s of selectors){let e=document.querySelector(s);if(e){found=e;break;}}"
                + "if(!found){let terms=type==='liked'?['liked','likes','понравив']:type==='favorites'?['favorite','избран']:['repost','репост'];"
                + "let els=[...document.querySelectorAll('[role=\"tab\"],a[role=\"tab\"],button[role=\"tab\"]')];"
                + "found=els.find(e=>terms.some(t=>(e.getAttribute('aria-label')||e.textContent||'').toLowerCase().includes(t)))||null;}"
                + "if(found){found.click();window.scrollTo(0,0);return 'clicked';}return 'missing';})()";
        webView.evaluateJavascript(js, result -> {
            if (cancelled || !running) return;
            if ("\"clicked\"".equals(result)) {
                main.postDelayed(this::scan, 1350);
            } else if (attempt < 2) {
                main.postDelayed(() -> chooseTab(section, attempt + 1), 1400);
            } else {
                notes.add(section + ": вкладка недоступна в веб-версии");
                sectionIndex++;
                beginSection();
            }
        });
    }

    private void scan() {
        if (cancelled || !running) return;
        if (!QueueStore.isTikTokUrl(webView.getUrl())) {
            notes.add(sections.get(sectionIndex) + ": открыта не страница TikTok");
            sectionIndex++;
            beginSection();
            return;
        }
        final String script = "(function(){"
                + "let links=[...document.querySelectorAll('a[href*=\"/video/\"]')].map(a=>a.href)"
                + ".filter(x=>/^https:\\/\\/([\\w.-]+\\.)?tiktok\\.com\\//i.test(x));"
                + "if(/\\/video\\/[0-9]+/.test(location.pathname))links.push(location.href);"
                + "let sc=document.scrollingElement||document.documentElement;"
                + "let candidates=[sc,...document.querySelectorAll('main,[data-e2e=\"user-post-item-list\"],div')];"
                + "let target=sc;let height=0;"
                + "for(let el of candidates){if(!el||!el.clientHeight)continue;"
                + "let diff=el.scrollHeight-el.clientHeight;"
                + "if(diff>height&&diff>400){height=diff;target=el;}}"
                + "let old=target.scrollTop;let to=Math.min(target.scrollHeight,target.scrollTop+Math.max(innerHeight*.85,480));"
                + "target.scrollTop=to;window.scrollBy(0,Math.max(innerHeight*.85,480));"
                + "return JSON.stringify({links:[...new Set(links)].slice(0,1500),pos:target.scrollTop,height:target.scrollHeight,atEnd:target.scrollTop+target.clientHeight>=target.scrollHeight-60});"
                + "})()";
        webView.evaluateJavascript(script, encoded -> {
            if (cancelled || !running) return;
            try {
                JSONObject j = new JSONObject(new JSONArray("[" + encoded + "]").getString(0));
                JSONArray arr = j.optJSONArray("links");
                List<String> urls = new ArrayList<>();
                if (arr != null) for (int k = 0; k < arr.length(); k++) urls.add(arr.optString(k));
                int newlyAdded = QueueStore.add(context, urls, sections.get(sectionIndex), null);
                totalAdded += newlyAdded;
                int count = urls.size();
                step++;
                if (count == lastCount && newlyAdded == 0) consecutiveStalls++;
                else consecutiveStalls = 0;
                lastCount = count;
                listener.onStatus(sections.get(sectionIndex) + " · найдено " + count
                        + " на странице · добавлено " + totalAdded + " · шаг " + step);
                if (step >= MAX_STEPS || consecutiveStalls >= 10) {
                    notes.add(sections.get(sectionIndex) + ": " + step + " шагов просмотра");
                    sectionIndex++;
                    beginSection();
                } else main.postDelayed(this::scan, 850);
            } catch (Exception error) {
                notes.add(sections.get(sectionIndex) + ": страница не предоставила ссылки");
                sectionIndex++;
                beginSection();
            }
        });
    }

    private void finish(String summary) {
        cancelled = true;
        running = false;
        main.removeCallbacksAndMessages(null);
        listener.onStatus("Синхронизация завершена");
        listener.onFinished(totalAdded, summary);
    }
}
