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
import java.io.FileInputStream;
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
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    private Thread backgroundCacheThread;
    private Thread preCacheThread;
    private volatile boolean cancelDownload = false;

    private boolean isBuffering = false;
    private boolean isPlaybackStarted = false;
    private int retryCount = 0;
    private boolean isRetrying = false;

    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaybackStarted) {
                triggerRetry("加载超时 (" + getTimeoutSeconds() + "秒)");
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

    @Override
    public void onCreate() {
        super.onCreate();
        recreateMediaPlayer();
        startProgressTimer();
    }

    // 核心：遇到错误彻底重建 MediaPlayer，绝不进入死循环
    private synchronized void recreateMediaPlayer() {
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
                // 仅在出声播放后才按需挂载音效
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
                isBuffering = false;
                if (!isPlaybackStarted) {
                    triggerRetry("解码错误 (code:" + what + ")");
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

    // 重试机制：强制 2 秒冷却，彻底杜绝瞬间空转
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
            showToastOnMain("歌曲连续 " + maxRetries + " 次加载失败，自动跳至下一首");
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

        // 策略 1：如果本地有完整缓存，通过文件描述符 (FileDescriptor) 秒开
        if (CacheManager.isSongCached(this, song.id)) {
            File cached = CacheManager.getSongFile(this, song.id);
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(cached);
                mediaPlayer.setDataSource(fis.getFD());
                mediaPlayer.prepareAsync();
                isBuffering = false;
                broadcastStatus();
                return;
            } catch (Exception e) {
                cached.delete(); // 本地文件损坏则清除
            } finally {
                if (fis != null) try { fis.close(); } catch (Exception ignored) {}
            }
        }

        // 策略 2：本地未缓存，直接在线串流，零等待立刻出声！
        try {
            isBuffering = true;
            mediaPlayer.setDataSource(song.streamUrl);
            mediaPlayer.prepareAsync();
            broadcastStatus();
        } catch (Exception e) {
            triggerRetry("网络串流启动失败");
            return;
        }

        // 策略 3：在后台线程静默缓存本首歌曲，供后续秒开和离线使用
        startBackgroundCache(song);
    }

    private void startBackgroundCache(final SongItem song) {
        backgroundCacheThread = new Thread(new Runnable() {
            @Override
            public void run() {
                cancelDownload = false;
                File tmpFile = CacheManager.getTempFile(MusicService.this, song.id);
                File targetFile = CacheManager.getSongFile(MusicService.this, song.id);
                if (tmpFile.exists()) tmpFile.delete();

                boolean ok = downloadSongStream(song.streamUrl, tmpFile);
                if (ok && !cancelDownload && tmpFile.length() > 64 * 1024) {
                    if (tmpFile.renameTo(targetFile)) {
                        targetFile.setLastModified(System.currentTimeMillis());
                        SharedPreferences sp = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
                        int maxMb = 500;
                        try {
                            maxMb = Integer.parseInt(sp.getString("cache_size_mb", "500"));
                        } catch (Exception ignored) {}
                        CacheManager.trimCache(MusicService.this, maxMb * 1024L * 1024L, song.id);
                    }
                } else {
                    tmpFile.delete();
                }
            }
        });
        backgroundCacheThread.setPriority(Thread.MIN_PRIORITY);
        backgroundCacheThread.start();
    }

    private boolean downloadSongStream(String urlStr, File destFile) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String redUrl = conn.getHeaderField("Location");
                if (redUrl != null) {
                    conn.disconnect();
                    url = new URL(redUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(12000);
                    conn.connect();
                    code = conn.getResponseCode();
                }
            }

            if (code != 200 && code != 206) return false;

            is = conn.getInputStream();
            fos = new FileOutputStream(destFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                if (cancelDownload) return false;
                fos.write(buf, 0, len);
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
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
                File tmp = CacheManager.getTempFile(MusicService.this, nextSong.id);
                File target = CacheManager.getSongFile(MusicService.this, nextSong.id);
                boolean ok = downloadSongStream(nextSong.streamUrl, tmp);
                if (ok && tmp.length() > 64 * 1024) {
                    tmp.renameTo(target);
                } else {
                    tmp.delete();
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
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Throwable ignored) {}
            mediaPlayer = null;
        }
    }
}
