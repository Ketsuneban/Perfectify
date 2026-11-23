package dev.perfectify.tiktok;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DownloadService extends Service {
    public static final String ACTION_START = "dev.perfectify.tiktok.START";
    public static final String ACTION_STOP = "dev.perfectify.tiktok.STOP";
    private static final String CHANNEL = "perfectify_downloads";
    private static final int NOTIFICATION_ID = 1101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean cancelled = false;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Загрузки Perfectify",
                NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY;
        cancelled = false;
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification("Подготовка очереди", 0),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification("Подготовка очереди", 0));
        }
        executor.execute(this::processQueue);
        return START_NOT_STICKY;
    }

    private Notification notification(String text, int progress) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, DownloadService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Perfectify · TikTok")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(pending)
                .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPending);
        if (progress > 0) builder.setProgress(100, Math.min(100, progress), false);
        else builder.setProgress(0, 0, true);
        return builder.build();
    }

    private void notifyStatus(String status, int progress) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, notification(status, progress));
    }

    private void processQueue() {
        try {
            List<QueueStore.Entry> items = QueueStore.list(this);
            for (QueueStore.Entry item : items) {
                if (cancelled) break;
                if (!"queued".equals(item.state)) continue;
                Uri pendingUri = null;
                try {
                    QueueStore.update(this, item.id, "resolving", 0, "", null);
                    notifyStatus("Поиск видео · " + item.category, 0);
                    String media = VideoResolver.resolve(item);
                    if (cancelled) throw new InterruptedException("Загрузка остановлена");
                    QueueStore.update(this, item.id, "downloading", 0, "", null);
                    pendingUri = download(item, media);
                    QueueStore.update(this, item.id, "done", 100, "", pendingUri.toString());
                    notifyStatus("Видео сохранено", 100);
                } catch (InterruptedException interrupted) {
                    QueueStore.update(this, item.id, "cancelled", 0, "Остановлено пользователем", null);
                    break;
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "Неизвестная ошибка" : e.getMessage();
                    QueueStore.update(this, item.id, "error", 0, msg, null);
                    notifyStatus("Не удалось скачать видео", 0);
                }
            }
        } finally {
            running.set(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private Uri download(QueueStore.Entry item, String mediaUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(mediaUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "video/mp4,video/*,*/*;q=0.6");
        connection.setRequestProperty("Accept-Encoding", "identity");
        Uri outputUri = null;
        boolean successful = false;
        try {
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300)
                throw new Exception("Сервер медиа ответил HTTP " + response);
            String mime = connection.getContentType();
            if (mime != null && (mime.toLowerCase(Locale.ROOT).startsWith("text/")
                    || mime.toLowerCase(Locale.ROOT).contains("json")
                    || mime.toLowerCase(Locale.ROOT).contains("html")))
                throw new Exception("Получена страница вместо видео (срок ссылки истёк или доступ ограничен)");
            String name = filename(item);
            OutputStream output;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Perfectify TikTok");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) throw new Exception("Не удалось создать файл в галерее");
                output = getContentResolver().openOutputStream(outputUri, "w");
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Perfectify TikTok");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Не удалось создать папку для видео");
                File file = new File(dir, name);
                outputUri = Uri.fromFile(file);
                output = new FileOutputStream(file);
            }
            if (output == null) throw new Exception("Нет доступа к файлу назначения");
            long total = connection.getContentLengthLong();
            long written = 0;
            int lastProgress = -1;
            try (InputStream in = connection.getInputStream(); OutputStream out = output) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled) throw new InterruptedException("Остановлено пользователем");
                    out.write(buffer, 0, n);
                    written += n;
                    int percent = total > 0 ? (int) Math.min(99, written * 100 / total) : 0;
                    if (percent >= lastProgress + 8) {
                        lastProgress = percent;
                        QueueStore.update(this, item.id, "downloading", percent, null, null);
                        notifyStatus("Загрузка · " + item.category, percent);
                    }
                }
                out.flush();
            }
            if (written == 0) throw new Exception("Видео пустое");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(outputUri, done, null, null);
            }
            successful = true;
            return outputUri;
        } finally {
            connection.disconnect();
            if (!successful && outputUri != null) {
                if ("content".equals(outputUri.getScheme())) {
                    getContentResolver().delete(outputUri, null, null);
                } else if ("file".equals(outputUri.getScheme()) && outputUri.getPath() != null) {
                    new File(outputUri.getPath()).delete();
                }
            }
        }
    }

    private static String filename(QueueStore.Entry entry) {
        Matcher matcher = Pattern.compile("/video/(\\d+)").matcher(entry.url);
        String id = matcher.find() ? matcher.group(1) : entry.id.substring(0, 8);
        return "tiktok_" + id + ".mp4";
    }

    @Override public void onDestroy() {
        cancelled = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
