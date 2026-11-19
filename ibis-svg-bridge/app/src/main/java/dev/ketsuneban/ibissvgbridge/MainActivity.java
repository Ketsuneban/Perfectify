package dev.ketsuneban.ibissvgbridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    static final String ACTION_OPEN = "dev.ketsuneban.ibissvgbridge.OPEN";
    static final String ACTION_SAVE = "dev.ketsuneban.ibissvgbridge.SAVE";
    static final String ACTION_SHARE = "dev.ketsuneban.ibissvgbridge.SHARE";
    private static final int REQUEST_OPEN = 501;
    private static final int REQUEST_SAVE = 502;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, 500);
        }
        Intent incoming = getIntent();
        if (incoming == null) return;
        String action = incoming.getAction();
        if (ACTION_OPEN.equals(action)) {
            pickSvg();
        } else if (ACTION_SAVE.equals(action)) {
            createSvg();
        } else if (ACTION_SHARE.equals(action)) {
            sharePng();
        } else if (Intent.ACTION_VIEW.equals(action) && incoming.getData() != null) {
            importSvg(incoming.getData());
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) importSvg(uri);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(36), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(18, 23, 34));

        TextView title = new TextView(this);
        title.setText("SVG Bridge");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Плавающий SVG-редактор поверх ibisPaint X. Сохраняет SVG и отправляет PNG в ibisPaint. Это не встроенный плагин ibisPaint.");
        desc.setTextSize(15);
        desc.setTextColor(Color.rgb(197, 205, 220));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, dp(16));
        root.addView(desc, p);

        addButton(root, "1. Выбрать SVG", this::pickSvg);
        addButton(root, "2. Разрешить поверх других окон", this::requestOverlayPermission);
        addButton(root, "3. Открыть плавающий редактор", this::showOverlay);
        addButton(root, "4. Перейти в ibisPaint X", this::openIbis);

        status = new TextView(this);
        status.setTextColor(Color.rgb(166, 222, 217));
        status.setTextSize(13);
        status.setText("Все файлы обрабатываются локально. Разрешение на наложение окон нужно только для плавающего редактора.");
        root.addView(status, p);
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> callback.run());
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void pickSvg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQUEST_OPEN);
    }

    private void createSvg() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/svg+xml");
        i.putExtra(Intent.EXTRA_TITLE, "edited.svg");
        startActivityForResult(i, REQUEST_SAVE);
    }

    private void importSvg(Uri uri) {
        try {
            String svg = Store.readUri(this, uri);
            Store.writeLocal(this, Store.CURRENT, svg);
            setStatus("SVG загружен. Запусти редактор.");
            if (Settings.canDrawOverlays(this)) showOverlay();
        } catch (Exception ex) {
            setStatus("Ошибка открытия: " + ex.getMessage());
        }
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            setStatus("Разрешение уже предоставлено.");
            return;
        }
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        setStatus("Редактор открыт. Можно перейти в ibisPaint.");
    }

    private void openIbis() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("jp.ne.ibis.ibispaintx.app");
        if (launch == null) {
            setStatus("ibisPaint X не найден. Установи приложение отдельно.");
            return;
        }
        startActivity(launch);
    }

    private void sharePng() {
        try {
            Uri uri = Uri.parse("content://dev.ketsuneban.ibissvgbridge.png/shared.png");
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Передать PNG в ibisPaint X"));
        } catch (Exception ex) {
            setStatus("Ошибка передачи PNG: " + ex.getMessage());
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == REQUEST_OPEN) {
                importSvg(data.getData());
            } else if (request == REQUEST_SAVE) {
                String svg = Store.readLocal(this, Store.EXPORT);
                try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (out == null) throw new java.io.IOException("File could not be written");
                    out.write(svg.getBytes(StandardCharsets.UTF_8));
                }
                setStatus("SVG сохранён.");
            }
        } catch (Exception ex) {
            setStatus("Ошибка: " + ex.getMessage());
        }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
