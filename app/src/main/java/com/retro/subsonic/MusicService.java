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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;

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
    private static final long PLAY_TIMEOUT_MS = 20000; // 20 秒超时监控
    private static final int MAX_RETRY_COUNT = 3;     // 最多重试 3 次

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
    private Handler progressHandler = new Handler();
    private Handler timeoutHandler = new Handler();
    private Random random = new Random();

    // 缓存下载与网络熔断控制
    private Thread currentDownloadThread;
    private Thread preCacheThread;
    private volatile HttpURLConnection activeDownloadConn;
    private volatile boolean cancelCurrentDownload = false;
    private volatile boolean cancelPreCache = false;
    private boolean isBuffering = false;
    private int bufferPercent = 0;

    // 重试机制控制变量
    private int retryCount = 0;

    // 20 秒看门狗 Runnable
    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handlePlaybackTimeout();
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

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        AudioEffectsManager.getInstance().attachSession(mediaPlayer.getAudioSessionId(), getApplicationContext());

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // 成功出声播放：立刻取消 20 秒超时计时，重置重试计数
                timeoutHandler.removeCallbacks(timeoutRunnable);
                retryCount = 0;
                isBuffering = false;

                mp.start();
                AudioEffectsManager.getInstance().attachSession(mp.getAudioSessionId(), getApplicationContext());
                updateNotification();
                broadcastStatus();
                triggerPreCacheNext();
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
                // 播放器抛错（如格式不支持或文件损坏），移除可能损坏的本地缓存文件并触发重试逻辑
                isBuffering = false;
                if (currentIndex >= 0 && currentIndex < playlist.size()) {
                    SongItem song = playlist.get(currentIndex);
                    File cached = CacheManager.getSongFile(MusicService.this, song.id);
                    if (cached.exists()) {
                        cached.delete();
                    }
                }
                handlePlaybackTimeout();
                return true;
            }
        });

        startProgressTimer();
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
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.start();
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

    // 处理 20 秒超时或异常重试
    private synchronized void handlePlaybackTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);

        // 如果实际上已经开始播放，则忽略
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            return;
        }

        retryCount++;
        if (retryCount < MAX_RETRY_COUNT) {
            // 终止可能卡死的网络连接与下载线程
            abortActiveConnection();
            broadcastStatus();

            showToastOnMain("加载超时，正在进行第 " + retryCount + " 次重试...");
            playCurrent(true);
        } else {
            // 累计重试 3 次依然失败，自动切换下一首
            retryCount = 0;
            abortActiveConnection();

            showToastOnMain("歌曲多次尝试加载失败，已自动跳至下一首");
            playNext();
        }
    }

    private void abortActiveConnection() {
        cancelCurrentDownload = true;
        cancelPreCache = true;
        if (activeDownloadConn != null) {
            try {
                activeDownloadConn.disconnect();
            } catch (Exception ignored) {}
            activeDownloadConn = null;
        }
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            }
        } catch (Exception ignored) {}
    }

    private void playCurrent(boolean isRetry) {
        if (!isRetry) {
            retryCount = 0;
        }

        // 取消上一轮的看门狗定时器，重新启动 20 秒倒计时
        timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutHandler.postDelayed(timeoutRunnable, PLAY_TIMEOUT_MS);

        if (currentIndex < 0 || currentIndex >= playlist.size()) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            return;
        }
        final SongItem song = playlist.get(currentIndex);

        abortActiveConnection();

        // 1. 本地已完整缓存：直接从本地文件播放
        if (CacheManager.isSongCached(this, song.id)) {
            File cachedFile = CacheManager.getSongFile(this, song.id);
            cachedFile.setLastModified(System.currentTimeMillis());
            isBuffering = false;
            bufferPercent = 100;
            broadcastStatus();
            playLocalFile(cachedFile.getAbsolutePath());
            return;
        }

        // 2. 本地未缓存：后台线程发起流式下载
        isBuffering = true;
        bufferPercent = 0;
        broadcastStatus();

        currentDownloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                cancelCurrentDownload = false;
                final File tmpFile = CacheManager.getTempFile(MusicService.this, song.id);
                final File targetFile = CacheManager.getSongFile(MusicService.this, song.id);

                if (tmpFile.exists()) {
                    tmpFile.delete();
                }

                boolean success = downloadFile(song.streamUrl, tmpFile, true);
                if (success && !cancelCurrentDownload) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());

                        SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
                        int maxMb = 500;
                        try {
                            maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                        } catch (Exception ignored) {}
                        CacheManager.trimCache(MusicService.this, maxMb * 1024L * 1024L, song.id);

                        isBuffering = false;
                        playLocalFile(targetFile.getAbsolutePath());
                        return;
                    }
                }

                // 若下载保存失败且未被取消，回退为网络直连串流
                if (!cancelCurrentDownload) {
                    isBuffering = false;
                    playOnlineDirect(song.streamUrl);
                }
            }
        });
        currentDownloadThread.start();
    }

    private boolean downloadFile(String urlStr, File destFile, boolean reportProgress) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            if (reportProgress) {
                activeDownloadConn = conn;
            }
            conn.connect();

            if (conn.getResponseCode() != 200) return false;

            int totalLength = conn.getContentLength();
            is = conn.getInputStream();
            fos = new FileOutputStream(destFile);

            byte[] buf = new byte[8192];
            int len;
            long readBytes = 0;
            long lastBroadcastTime = 0;

            while ((len = is.read(buf)) != -1) {
                if (reportProgress && cancelCurrentDownload) return false;
                if (!reportProgress && cancelPreCache) return false;

                fos.write(buf, 0, len);
                readBytes += len;

                if (reportProgress && totalLength > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcastTime > 400) {
                        lastBroadcastTime = now;
                        bufferPercent = (int) ((readBytes * 100) / totalLength);
                        broadcastStatus();
                    }
                }
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (reportProgress && activeDownloadConn == conn) {
                activeDownloadConn = null;
            }
            if (conn != null) conn.disconnect();
        }
    }

    private void playLocalFile(String filePath) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepareAsync();
            updateNotification();
            broadcastStatus();
        } catch (Exception e) {
            handlePlaybackTimeout();
        }
    }

    private void playOnlineDirect(String streamUrl) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(streamUrl);
            mediaPlayer.prepareAsync();
            updateNotification();
            broadcastStatus();
        } catch (Exception e) {
            handlePlaybackTimeout();
        }
    }

    private void triggerPreCacheNext() {
        if (playlist.isEmpty()) return;
        int nextIndex = (currentIndex + 1) % playlist.size();
        final SongItem nextSong = playlist.get(nextIndex);

        if (CacheManager.isSongCached(this, nextSong.id)) return;

        preCacheThread = new Thread(new Runnable() {
            @Override
            public void run() {
                cancelPreCache = false;
                File tmpFile = CacheManager.getTempFile(MusicService.this, nextSong.id);
                File targetFile = CacheManager.getSongFile(MusicService.this, nextSong.id);
                boolean ok = downloadFile(nextSong.streamUrl, tmpFile, false);
                if (ok && !cancelPreCache) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());
                    }
                } else {
                    tmpFile.delete();
                }
            }
        });
        preCacheThread.setPriority(Thread.MIN_PRIORITY);
        preCacheThread.start();
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
        progressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    broadcastStatus();
                }
                progressHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void broadcastStatus() {
        Intent b = new Intent(BROADCAST_STATUS);
        b.putExtra("mode", currentMode);
        b.putExtra("isBuffering", isBuffering);
        b.putExtra("bufferPercent", bufferPercent);
        b.putExtra("retryCount", retryCount);

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
        new Handler(Looper.getMainLooper()).post(new Runnable() {
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
        timeoutHandler.removeCallbacks(timeoutRunnable);
        abortActiveConnection();
        progressHandler.removeCallbacksAndMessages(null);
        AudioEffectsManager.getInstance().release();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
