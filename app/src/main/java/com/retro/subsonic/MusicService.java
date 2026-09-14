package com.retro.subsonic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MusicService extends Service {

    public static final String ACTION_TOGGLE = "com.retro.subsonic.TOGGLE";
    public static final String ACTION_PLAY_INDEX = "com.retro.subsonic.PLAY_INDEX";
    public static final String ACTION_PREV = "com.retro.subsonic.PREV";
    public static final String ACTION_NEXT = "com.retro.subsonic.NEXT";
    public static final String ACTION_SEEK = "com.retro.subsonic.SEEK";
    public static final String ACTION_CYCLE_MODE = "com.retro.subsonic.CYCLE_MODE";

    public static final String BROADCAST_STATUS = "com.retro.subsonic.STATUS_CHANGE";

    public static final int MODE_LOOP_ALL = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_SINGLE = 2;

    private static final int NOTIFICATION_ID = 1001;

    public static class SongItem implements Serializable {
        public String id;
        public String title;
        public String artist;
        public String streamUrl;
        public String coverArtId;
        public String quality;

        public SongItem(String id, String title, String artist, String streamUrl) {
            this(id, title, artist, streamUrl, null, "320K MP3");
        }

        public SongItem(String id, String title, String artist, String streamUrl, String coverArtId) {
            this(id, title, artist, streamUrl, coverArtId, "320K MP3");
        }

        public SongItem(String id, String title, String artist, String streamUrl, String coverArtId, String quality) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.streamUrl = streamUrl;
            this.coverArtId = coverArtId;
            this.quality = quality;
        }
    }

    private static ArrayList<SongItem> playlist = new ArrayList<SongItem>();
    private static int currentIndex = -1;
    private static int currentMode = MODE_LOOP_ALL;

    private MediaPlayer mediaPlayer;
    private FileInputStream activeFis;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    private Thread downloadThread;
    private volatile boolean cancelDownload = false;

    private boolean isBuffering = false;
    private int bufferPercent = 0;
    private boolean isPlaybackStarted = false;
    private int retryCount = 0;
    private boolean isRetrying = false;

    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaybackStarted) {
                triggerRetry("连接超时 (" + getTimeoutSeconds() + "秒)");
            }
        }
    };

    public static ArrayList<SongItem> getPlaylist() { return playlist; }
    public static int getCurrentIndex() { return currentIndex; }
    public static int getCurrentMode() { return currentMode; }

    public static void setQueue(ArrayList<SongItem> list, int index, Context context) {
        playlist.clear();
        playlist.addAll(list);
        currentIndex = index;

        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY_INDEX);
        context.startService(intent);
    }

    private int getTimeoutSeconds() {
        try {
            SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
            int sec = Integer.parseInt(sp.getString("play_timeout_sec", "20"));
            return Math.max(5, Math.min(sec, 120));
        } catch (Exception e) {
            return 20;
        }
    }

    private int getMaxRetryCount() {
        try {
            SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
            int count = Integer.parseInt(sp.getString("play_retry_count", "3"));
            return Math.max(1, Math.min(count, 10));
        } catch (Exception e) {
            return 3;
        }
    }

    // 信任所有 SSL 证书，彻底解决 Android 4.2.2 访问 HTTPS CDN 证书过期崩溃问题
    private static void enableTrustAllSSL() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
            });
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        enableTrustAllSSL();
        recreateMediaPlayer();
        startProgressTimer();
    }

    private synchronized void recreateMediaPlayer() {
        if (activeFis != null) {
            try { activeFis.close(); } catch (Throwable ignored) {}
            activeFis = null;
        }
        if (mediaPlayer != null) {
            try {
                AudioEffectsManager.getInstance().detach();
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Throwable ignored) {}
            mediaPlayer = null;
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mainHandler.removeCallbacks(timeoutRunnable);
                isPlaybackStarted = true;
                retryCount = 0;
                isRetrying = false;
                isBuffering = false;

                mp.start();
                AudioEffectsManager.getInstance().attachSession(mp.getAudioSessionId(), getApplicationContext());

                updateNotification();
                broadcastStatus();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                handleCompletion();
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                isBuffering = false;
                if (!isPlaybackStarted) {
                    triggerRetry("音频解析错误 (code:" + what + ")");
                }
                return true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_PLAY_INDEX.equals(act)) {
                int explicitIndex = intent.getIntExtra("target_index", -1);
                if (explicitIndex >= 0) {
                    currentIndex = explicitIndex;
                }
                playCurrent(false);
            } else if (ACTION_TOGGLE.equals(act)) {
                if (mediaPlayer != null) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    } else {
                        mediaPlayer.start();
                    }
                }
                updateNotification();
                broadcastStatus();
            } else if (ACTION_NEXT.equals(act)) {
                playNext();
            } else if (ACTION_PREV.equals(act)) {
                playPrev();
            } else if (ACTION_SEEK.equals(act)) {
                int pos = intent.getIntExtra("position", 0);
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(pos);
                }
            } else if (ACTION_CYCLE_MODE.equals(act)) {
                currentMode = (currentMode + 1) % 3;
                broadcastStatus();
            }
        }
        return START_NOT_STICKY;
    }

    private void handleCompletion() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SINGLE) {
            playCurrent(false);
        } else if (currentMode == MODE_SHUFFLE) {
            if (playlist.size() > 1) {
                int nextIdx;
                do {
                    nextIdx = random.nextInt(playlist.size());
                } while (nextIdx == currentIndex);
                currentIndex = nextIdx;
            }
            playCurrent(false);
        } else {
            playNext();
        }
    }

    // 核心重试：设置强制 2 秒冷却，彻底杜绝瞬间闪退循环
    private synchronized void triggerRetry(final String reason) {
        if (isRetrying) return;
        isRetrying = true;

        mainHandler.removeCallbacks(timeoutRunnable);
        cancelDownload = true;

        final int maxRetries = getMaxRetryCount();
        retryCount++;

        if (retryCount <= maxRetries) {
            showToastOnMain(reason + "，2秒后第 " + retryCount + "/" + maxRetries + " 次重试...");
            broadcastStatus();

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isRetrying = false;
                    playCurrent(true);
                }
            }, 2000);
        } else {
            showToastOnMain("连续 " + maxRetries + " 次加载失败，自动跳至下一首");
            retryCount = 0;
            isRetrying = false;
            broadcastStatus();

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            }, 1000);
        }
    }

    private synchronized void playCurrent(boolean isRetry) {
        if (!isRetry) {
            retryCount = 0;
        }
        isPlaybackStarted = false;
        cancelDownload = true;

        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.postDelayed(timeoutRunnable, getTimeoutSeconds() * 1000L);

        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            mainHandler.removeCallbacks(timeoutRunnable);
            return;
        }
        final SongItem song = playlist.get(currentIndex);

        recreateMediaPlayer();

        // 1. 如果已完整缓存，优先文件描述符秒开
        if (CacheManager.isSongCached(this, song.id)) {
            File cached = CacheManager.getSongFile(this, song.id);
            if (startPlayFile(cached)) {
                isBuffering = false;
                broadcastStatus();
                return;
            } else {
                cached.delete();
            }
        }

        // 2. 未缓存：进入全自动嗅探与智能解析下载管道
        isBuffering = true;
        bufferPercent = 0;
        broadcastStatus();

        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                cancelDownload = false;
                final File tmpFile = CacheManager.getTempFile(MusicService.this, song.id);
                final File targetFile = CacheManager.getSongFile(MusicService.this, song.id);
                if (tmpFile.exists()) tmpFile.delete();

                String resolvedAudioUrl = resolveAndFetchAudio(song.streamUrl, tmpFile, 0);

                if (resolvedAudioUrl != null && !cancelDownload && tmpFile.exists() && tmpFile.length() > 32 * 1024) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());

                        SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
                        int maxMb = 500;
                        try {
                            maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                        } catch (Exception ignored) {}
                        CacheManager.trimCache(MusicService.this, maxMb * 1024L * 1024L, song.id);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!cancelDownload) {
                                    startPlayFile(targetFile);
                                }
                            }
                        });
                        return;
                    }
                }

                if (!cancelDownload) {
                    // 若未能成功获取有效音频文件，触发明确报错
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            triggerRetry("未能获取到有效音频数据");
                        }
                    });
                }
            }
        });
        downloadThread.start();
    }

    // 智能流嗅探与重定向递归提取器
    private String resolveAndFetchAudio(String initialUrl, File destFile, int depth) {
        if (depth > 6 || cancelDownload) return null; // 防止死循环重定向

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL url = new URL(initialUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false); // 手动追踪，兼容跨协议重定向
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.connect();

            int code = conn.getResponseCode();

            // 1. 处理 301/302/307 重定向
            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    // 递归追踪重定向目标（如跳转到 CDN）
                    return resolveAndFetchAudio(location, destFile, depth + 1);
                }
                return null;
            }

            if (code != 200 && code != 206) {
                return null;
            }

            int totalLength = conn.getContentLength();
            is = conn.getInputStream();

            // 2. 嗅探前 2048 字节内容，研判是真实音频还是 JSON/HTML 链接
            byte[] previewBuf = new byte[2048];
            int previewRead = is.read(previewBuf);
            if (previewRead <= 0) return null;

            String previewStr = new String(previewBuf, 0, Math.min(previewRead, 1024), "UTF-8").trim();

            // 判断 A：如果服务器返回的是 JSON 响应！
            if (previewStr.startsWith("{") || previewStr.startsWith("[")) {
                // 读取剩余 JSON 文本
                StringBuilder jsonBuilder = new StringBuilder(previewStr);
                byte[] temp = new byte[4096];
                int len;
                while ((len = is.read(temp)) != -1) {
                    jsonBuilder.append(new String(temp, 0, len, "UTF-8"));
                }
                String jsonText = jsonBuilder.toString();

                // 检查是否为 Subsonic 明确错误
                try {
                    JSONObject rootObj = new JSONObject(jsonText);
                    JSONObject sub = rootObj.optJSONObject("subsonic-response");
                    if (sub != null && "failed".equals(sub.optString("status"))) {
                        JSONObject err = sub.optJSONObject("error");
                        String errMsg = err != null ? err.optString("message") : "认证或服务端异常";
                        showToastOnMain("服务端拒绝: " + errMsg);
                        return null;
                    }

                    // 深度寻找 JSON 内部嵌套的真实音乐链接 (url, streamUrl, playUrl 等)
                    String extractedUrl = findAudioUrlInJson(rootObj);
                    if (extractedUrl != null) {
                        showToastOnMain("检测到音频链接数据，正在解析直链播放...");
                        conn.disconnect();
                        return resolveAndFetchAudio(extractedUrl, destFile, depth + 1);
                    }
                } catch (Exception e) {
                    showToastOnMain("解析返回的 JSON 失败");
                }
                return null;
            }

            // 判断 B：如果是网页 (HTML 错误页)
            if (previewStr.startsWith("<!DOCTYPE") || previewStr.startsWith("<html") || previewStr.startsWith("<head")) {
                showToastOnMain("服务端返回了网页而非音频文件，请检查地址");
                return null;
            }

            // 判断 C：确认是真正的音频流！开始写入本地缓存
            fos = new FileOutputStream(destFile);
            fos.write(previewBuf, 0, previewRead);
            long downloadedBytes = previewRead;
            long lastBroadcastTime = 0;

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (cancelDownload) return null;
                fos.write(buffer, 0, read);
                downloadedBytes += read;

                if (totalLength > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcastTime > 400) {
                        lastBroadcastTime = now;
                        bufferPercent = (int) ((downloadedBytes * 100) / totalLength);
                        broadcastStatus();
                    }
                }
            }
            fos.flush();
            return initialUrl;

        } catch (Exception e) {
            return null;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    // 递归检索 JSON 中任意层级的音频播放直链
    private String findAudioUrlInJson(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            String[] targetKeys = new String[]{"url", "streamUrl", "playUrl", "link", "src", "audioUrl", "mp3"};
            for (String k : targetKeys) {
                String val = obj.optString(k, null);
                if (val != null && (val.startsWith("http://") || val.startsWith("https://"))) {
                    return val;
                }
            }
            java.util.Iterator<?> it = obj.keys();
            while (it.hasNext()) {
                String k = (String) it.next();
                Object child = obj.opt(k);
                String found = findAudioUrlInJson(child);
                if (found != null) return found;
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                Object child = arr.opt(i);
                String found = findAudioUrlInJson(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean startPlayFile(File file) {
        try {
            if (activeFis != null) {
                try { activeFis.close(); } catch (Throwable ignored) {}
            }
            activeFis = new FileInputStream(file);
            mediaPlayer.reset();
            mediaPlayer.setDataSource(activeFis.getFD());
            mediaPlayer.prepareAsync();
            broadcastStatus();
            return true;
        } catch (Exception e) {
            triggerRetry("本地音频载入失败");
            return false;
        }
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            int nextIdx;
            do {
                nextIdx = random.nextInt(playlist.size());
            } while (nextIdx == currentIndex);
            currentIndex = nextIdx;
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
        }
        playCurrent(false);
    }

    private void playPrev() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            currentIndex = random.nextInt(playlist.size());
        } else {
            currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        }
        playCurrent(false);
    }

    private void updateNotification() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        SongItem song = playlist.get(currentIndex);
        boolean isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();

        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent piPrev = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent toggleIntent = new Intent(this, MusicService.class).setAction(ACTION_TOGGLE);
        PendingIntent piToggle = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent piNext = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(piOpen)
                .setOngoing(isPlaying)
                .addAction(android.R.drawable.ic_media_previous, "上一首", piPrev)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                           isPlaying ? "暂停" : "播放", piToggle)
                .addAction(android.R.drawable.ic_media_next, "下一首", piNext);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void startProgressTimer() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    broadcastStatus();
                }
                mainHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void broadcastStatus() {
        Intent b = new Intent(BROADCAST_STATUS);
        b.putExtra("mode", currentMode);
        b.putExtra("isBuffering", isBuffering);
        b.putExtra("bufferPercent", bufferPercent);
        b.putExtra("retryCount", retryCount);
        b.putExtra("maxRetries", getMaxRetryCount());

        if (mediaPlayer != null && currentIndex >= 0 && currentIndex < playlist.size()) {
            SongItem song = playlist.get(currentIndex);
            b.putExtra("songId", song.id);
            b.putExtra("coverArtId", song.coverArtId);
            b.putExtra("title", song.title);
            b.putExtra("artist", song.artist);
            b.putExtra("quality", song.quality);
            b.putExtra("isPlaying", mediaPlayer.isPlaying());
            b.putExtra("position", mediaPlayer.getCurrentPosition());
            b.putExtra("duration", mediaPlayer.getDuration());
            b.putExtra("currentIndex", currentIndex);
        } else {
            b.putExtra("isPlaying", false);
        }
        sendBroadcast(b);
    }

    private void showToastOnMain(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        cancelDownload = true;
        recreateMediaPlayer();
    }
}
