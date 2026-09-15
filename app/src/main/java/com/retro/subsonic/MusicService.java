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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

public class MusicService extends Service {

    public static final String ACTION_TOGGLE = "com.retro.subsonic.TOGGLE";
    public static final String ACTION_PLAY_INDEX = "com.retro.subsonic.PLAY_INDEX";
    public static final String ACTION_PREV = "com.retro.subsonic.PREV";
    public static final String ACTION_NEXT = "com.retro.subsonic.NEXT";
    public static final String ACTION_SEEK = "com.retro.subsonic.SEEK";
    public static final String ACTION_CYCLE_MODE = "com.retro.subsonic.CYCLE_MODE";
    public static final String ACTION_STOP = "com.retro.subsonic.STOP";

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
            this(id, title, artist, streamUrl, null, "标准音质");
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

    private LocalStreamProxy currentProxy;

    private Thread preCacheThread;
    private volatile HttpURLConnection preCacheConn;
    private volatile boolean cancelPreCache = false;

    private boolean isBuffering = false;
    private int bufferPercent = 0;
    private boolean isPlaybackStarted = false;
    private int retryCount = 0;
    private boolean isRetrying = false;

    // 记忆播放点
    private static int pendingSeekPosition = 0;

    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaybackStarted) {
                triggerRetry("加载起播超时 (" + getTimeoutSeconds() + "秒)");
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
        pendingSeekPosition = 0;

        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY_INDEX);
        context.startService(intent);
    }

    // 保存播放列表与播放记录
    public static void savePlaybackState(Context context, MediaPlayer mp, boolean started) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getSharedPreferences("retro_playback_state", MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putInt("saved_index", currentIndex);

            if (mp != null && started) {
                try {
                    ed.putInt("saved_position", mp.getCurrentPosition());
                    ed.putInt("saved_duration", mp.getDuration());
                } catch (Throwable ignored) {}
            }

            JSONArray arr = new JSONArray();
            for (SongItem item : playlist) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("artist", item.artist);
                obj.put("streamUrl", item.streamUrl);
                obj.put("coverArtId", item.coverArtId);
                obj.put("quality", item.quality);
                arr.put(obj);
            }
            ed.putString("saved_queue", arr.toString());
            ed.commit();
        } catch (Throwable ignored) {}
    }

    // 启动时恢复上次播放的列表与进度
    public static boolean restorePlaybackState(Context context) {
        if (context == null) return false;
        try {
            SharedPreferences sp = context.getSharedPreferences("retro_playback_state", MODE_PRIVATE);
            String queueJson = sp.getString("saved_queue", "");
            if (queueJson == null || queueJson.length() == 0 || "[]".equals(queueJson)) {
                return false;
            }

            JSONArray arr = new JSONArray(queueJson);
            playlist.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                playlist.add(new SongItem(
                        o.getString("id"),
                        o.getString("title"),
                        o.optString("artist", "未知歌手"),
                        o.optString("streamUrl", ""),
                        o.optString("coverArtId", null),
                        o.optString("quality", "标准音质")
                ));
            }

            currentIndex = sp.getInt("saved_index", 0);
            if (currentIndex < 0 || currentIndex >= playlist.size()) {
                currentIndex = 0;
            }
            pendingSeekPosition = sp.getInt("saved_position", 0);
            return !playlist.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
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

    @Override
    public void onCreate() {
        super.onCreate();
        TLSSocketFactory.install();
        recreateMediaPlayer();
        startProgressTimer();
    }

    private synchronized void recreateMediaPlayer() {
        isPlaybackStarted = false;
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

                // 核心修复：挂接系统辅助混响并开通通道
                AudioEffectsManager.getInstance().attachMediaPlayer(mp, getApplicationContext());

                if (pendingSeekPosition > 0) {
                    try {
                        mp.seekTo(pendingSeekPosition);
                    } catch (Throwable ignored) {}
                    pendingSeekPosition = 0;
                }

                mp.start();
                updateNotification();
                broadcastStatus();
                savePlaybackState(getApplicationContext(), mediaPlayer, true);
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
                if (what == -38 || extra == -38) return true;
                if (!isPlaybackStarted) {
                    triggerRetry("音频解码错误 (code:" + what + ")");
                }
                return true;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_STOP.equals(act)) {
                savePlaybackState(this, mediaPlayer, isPlaybackStarted);
                cancelPreCacheTask();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_PLAY_INDEX.equals(act)) {
                int explicitIndex = intent.getIntExtra("target_index", -1);
                if (explicitIndex >= 0) {
                    currentIndex = explicitIndex;
                }
                playCurrent(false);
            } else if (ACTION_TOGGLE.equals(act)) {
                if (mediaPlayer != null && isPlaybackStarted) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            savePlaybackState(this, mediaPlayer, isPlaybackStarted);
                        } else {
                            mediaPlayer.start();
                        }
                    } catch (Throwable ignored) {}
                } else {
                    playCurrent(false);
                }
                updateNotification();
                broadcastStatus();
            } else if (ACTION_NEXT.equals(act)) {
                playNext();
            } else if (ACTION_PREV.equals(act)) {
                playPrev();
            } else if (ACTION_SEEK.equals(act)) {
                int pos = intent.getIntExtra("position", 0);
                if (mediaPlayer != null && isPlaybackStarted) {
                    try {
                        mediaPlayer.seekTo(pos);
                        savePlaybackState(this, mediaPlayer, isPlaybackStarted);
                    } catch (Throwable ignored) {}
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

    private synchronized void triggerRetry(final String reason) {
        if (isRetrying) return;
        isRetrying = true;

        mainHandler.removeCallbacks(timeoutRunnable);
        stopCurrentProxy();
        cancelPreCacheTask();

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
            showToastOnMain("歌曲连续 " + maxRetries + " 次加载失败，自动跳至下一首");
            retryCount = 0;
            isRetrying = false;
            broadcastStatus();

            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            }, 1200);
        }
    }

    private void stopCurrentProxy() {
        if (currentProxy != null) {
            try { currentProxy.stop(); } catch (Exception ignored) {}
            currentProxy = null;
        }
    }

    private synchronized void cancelPreCacheTask() {
        cancelPreCache = true;
        if (preCacheConn != null) {
            try { preCacheConn.disconnect(); } catch (Exception ignored) {}
            preCacheConn = null;
        }
        if (preCacheThread != null) {
            preCacheThread.interrupt();
            preCacheThread = null;
        }
    }

    private SongItem getNextSongToPreCache() {
        if (playlist == null || playlist.isEmpty()) return null;
        int nextIdx = (currentIndex + 1) % playlist.size();
        if (nextIdx >= 0 && nextIdx < playlist.size()) {
            return playlist.get(nextIdx);
        }
        return null;
    }

    private synchronized void triggerPreCacheNext() {
        final SongItem nextSong = getNextSongToPreCache();
        if (nextSong == null) return;
        if (CacheManager.isSongCached(this, nextSong.id)) return;

        cancelPreCacheTask();
        cancelPreCache = false;

        preCacheThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                if (cancelPreCache) return;

                File tmpFile = CacheManager.getTempFile(MusicService.this, nextSong.id);
                File targetFile = CacheManager.getSongFile(MusicService.this, nextSong.id);
                if (tmpFile.exists()) tmpFile.delete();

                boolean ok = downloadPreCachePipeline(nextSong.streamUrl, tmpFile, 0);
                if (ok && !cancelPreCache && CacheManager.isValidAudioFile(tmpFile)) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());

                        SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
                        int maxMb = 500;
                        try {
                            maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                        } catch (Exception ignored) {}
                        CacheManager.trimCache(MusicService.this, maxMb * 1024L * 1024L, nextSong.id);
                    }
                } else {
                    if (tmpFile.exists()) tmpFile.delete();
                }
            }
        });
        preCacheThread.setPriority(Thread.MIN_PRIORITY);
        preCacheThread.start();
    }

    private boolean downloadPreCachePipeline(String targetUrl, File destFile, int depth) {
        if (depth > 6 || cancelPreCache) return false;

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            preCacheConn = conn;
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);

            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }

            conn.connect();
            int code = conn.getResponseCode();

            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && location.length() > 0) {
                    URL redirectUrl = new URL(url, location);
                    return downloadPreCachePipeline(redirectUrl.toString(), destFile, depth + 1);
                }
                return false;
            }

            if (code != 200 && code != 206) return false;

            is = conn.getInputStream();
            byte[] previewBuf = new byte[2048];
            int previewRead = is.read(previewBuf);
            if (previewRead <= 0) return false;

            String previewStr = new String(previewBuf, 0, previewRead, "UTF-8").trim();
            if (previewStr.startsWith("{") || previewStr.startsWith("[")) {
                StringBuilder sb = new StringBuilder(previewStr);
                byte[] temp = new byte[4096];
                int l;
                while ((l = is.read(temp)) != -1) {
                    sb.append(new String(temp, 0, l, "UTF-8"));
                }
                conn.disconnect();
                JSONObject root = new JSONObject(sb.toString());
                String directUrl = findAudioUrlInPreCacheJson(root);
                if (directUrl != null) {
                    return downloadPreCachePipeline(directUrl, destFile, depth + 1);
                }
                return false;
            }

            if (previewStr.startsWith("<?xml") || previewStr.startsWith("<!DOCTYPE") || previewStr.startsWith("<html")) {
                return false;
            }

            fos = new FileOutputStream(destFile);
            fos.write(previewBuf, 0, previewRead);
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                if (cancelPreCache) return false;
                fos.write(buf, 0, r);
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
            if (preCacheConn == conn) preCacheConn = null;
        }
    }

    private String findAudioUrlInPreCacheJson(Object json) {
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            String[] targetKeys = new String[]{"url", "streamUrl", "playUrl", "link", "src", "audioUrl", "musicUrl", "data"};
            for (String k : targetKeys) {
                Object val = obj.opt(k);
                if (val instanceof String) {
                    String strVal = (String) val;
                    if (strVal.startsWith("http://") || strVal.startsWith("https://")) {
                        return strVal;
                    }
                }
            }
            Iterator<?> it = obj.keys();
            while (it.hasNext()) {
                String k = (String) it.next();
                String found = findAudioUrlInPreCacheJson(obj.opt(k));
                if (found != null) return found;
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                String found = findAudioUrlInPreCacheJson(arr.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private synchronized void playCurrent(boolean isRetry) {
        if (!isRetry) {
            retryCount = 0;
        }
        isPlaybackStarted = false;
        stopCurrentProxy();
        cancelPreCacheTask();

        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.postDelayed(timeoutRunnable, getTimeoutSeconds() * 1000L);

        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            mainHandler.removeCallbacks(timeoutRunnable);
            return;
        }
        final SongItem song = playlist.get(currentIndex);

        recreateMediaPlayer();

        // 1. 本地缓存直接秒开
        if (CacheManager.isSongCached(this, song.id)) {
            File cached = CacheManager.getSongFile(this, song.id);
            if (startPlayFile(cached)) {
                isBuffering = false;
                bufferPercent = 100;
                broadcastStatus();
                triggerPreCacheNext();
                savePlaybackState(this, mediaPlayer, true);
                return;
            }
        }

        // 2. 流式代理边播边存
        isBuffering = true;
        bufferPercent = 0;
        broadcastStatus();

        try {
            currentProxy = new LocalStreamProxy(this, song.id, song.streamUrl, new LocalStreamProxy.ProxyListener() {
                @Override
                public void onProgress(final int percent) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            bufferPercent = percent;
                            broadcastStatus();
                        }
                    });
                }

                @Override
                public void onCached(File cachedFile) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isBuffering = false;
                            bufferPercent = 100;
                            broadcastStatus();
                            triggerPreCacheNext();
                        }
                    });
                }

                @Override
                public void onError(final String reason) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isPlaybackStarted) {
                                triggerRetry(reason);
                            }
                        }
                    });
                }
            });

            String localStreamUrl = currentProxy.start();
            mediaPlayer.setDataSource(localStreamUrl);
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            triggerRetry("启动本地串流代理失败: " + e.getMessage());
        }
    }

    private boolean startPlayFile(File file) {
        try {
            if (activeFis != null) {
                try { activeFis.close(); } catch (Throwable ignored) {}
            }
            activeFis = new FileInputStream(file);
            mediaPlayer.reset();
            mediaPlayer.setDataSource(activeFis.getFD(), 0, file.length());
            mediaPlayer.prepareAsync();
            broadcastStatus();
            return true;
        } catch (Exception e) {
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
        pendingSeekPosition = 0;
        playCurrent(false);
    }

    private void playPrev() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            currentIndex = random.nextInt(playlist.size());
        } else {
            currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        }
        pendingSeekPosition = 0;
        playCurrent(false);
    }

    private void updateNotification() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        SongItem song = playlist.get(currentIndex);
        boolean isPlaying = isPlaybackStarted && mediaPlayer != null && mediaPlayer.isPlaying();

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

    private int saveProgressCounter = 0;

    private void startProgressTimer() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaybackStarted && mediaPlayer != null) {
                    broadcastStatus();
                    saveProgressCounter++;
                    if (saveProgressCounter >= 5) { // 每 5 秒静默存档一次进度
                        saveProgressCounter = 0;
                        savePlaybackState(getApplicationContext(), mediaPlayer, isPlaybackStarted);
                    }
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

        boolean isPlaying = false;
        int position = 0;
        int duration = 0;

        if (isPlaybackStarted && mediaPlayer != null) {
            try {
                isPlaying = mediaPlayer.isPlaying();
                position = mediaPlayer.getCurrentPosition();
                duration = mediaPlayer.getDuration();
            } catch (Throwable ignored) {}
        }

        b.putExtra("isPlaying", isPlaying);
        b.putExtra("position", position);
        b.putExtra("duration", duration);

        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            SongItem song = playlist.get(currentIndex);
            b.putExtra("songId", song.id);
            b.putExtra("coverArtId", song.coverArtId);
            b.putExtra("title", song.title);
            b.putExtra("artist", song.artist);
            b.putExtra("quality", song.quality);
            b.putExtra("currentIndex", currentIndex);
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
        savePlaybackState(this, mediaPlayer, isPlaybackStarted);
        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        cancelPreCacheTask();
        stopCurrentProxy();
        recreateMediaPlayer();
    }
}
