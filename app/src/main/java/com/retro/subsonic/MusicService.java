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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service {

    public static final String BROADCAST_STATUS = "com.retro.subsonic.STATUS";

    public static final String ACTION_TOGGLE = "TOGGLE";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";
    public static final String ACTION_SEEK = "SEEK";
    public static final String ACTION_PLAY_INDEX = "PLAY_INDEX";
    public static final String ACTION_CYCLE_MODE = "CYCLE_MODE";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_MUTE = "MUTE";       // 方案A核心：伴随投播静音
    public static final String ACTION_UNMUTE = "UNMUTE";   // 方案A核心：断开投播恢复出声

    public static final int MODE_LOOP_ALL = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_SINGLE = 2;

    public static class SongItem implements Serializable {
        public String id;
        public String title;
        public String artist;
        public String streamUrl;
        public String coverArtId;
        public String quality;

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
    private Handler handler = new Handler();
    private boolean isBuffering = false;
    private int bufferPercent = 0;
    private int retryCount = 0;
    private int maxRetries = 3;

    private boolean isCastMuted = false; // 伴随投播静音标记

    private AudioEffectsManager audioEffectsManager;
    private LocalStreamProxy localStreamProxy;

    public static ArrayList<SongItem> getPlaylist() {
        return playlist;
    }

    public static int getCurrentIndex() {
        return currentIndex;
    }

    public static int getCurrentMode() {
        return currentMode;
    }

    public static boolean isCastMutedMode() {
        return false;
    }

    public static void setQueue(ArrayList<SongItem> newQueue, int startIndex, Context context) {
        playlist.clear();
        if (newQueue != null) {
            playlist.addAll(newQueue);
        }
        currentIndex = startIndex;

        savePlaybackState(context, 0, 0);

        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY_INDEX);
        intent.putExtra("target_index", startIndex);
        context.startService(intent);
    }

    private static void savePlaybackState(Context context, int position, int duration) {
        try {
            SharedPreferences sp = context.getSharedPreferences("retro_playback_state", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt("saved_index", currentIndex);
            editor.putInt("saved_mode", currentMode);
            editor.putInt("saved_position", position);
            editor.putInt("saved_duration", duration);

            JSONArray arr = new JSONArray();
            for (SongItem song : playlist) {
                JSONObject obj = new JSONObject();
                obj.put("id", song.id);
                obj.put("title", song.title);
                obj.put("artist", song.artist);
                obj.put("streamUrl", song.streamUrl);
                obj.put("coverArtId", song.coverArtId);
                obj.put("quality", song.quality);
                arr.put(obj);
            }
            editor.putString("saved_playlist_json", arr.toString());
            editor.commit();
        } catch (Exception ignored) {}
    }

    public static boolean restorePlaybackState(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences("retro_playback_state", Context.MODE_PRIVATE);
            String json = sp.getString("saved_playlist_json", null);
            if (json != null && json.length() > 0) {
                JSONArray arr = new JSONArray(json);
                ArrayList<SongItem> list = new ArrayList<SongItem>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new SongItem(
                            obj.getString("id"),
                            obj.getString("title"),
                            obj.getString("artist"),
                            obj.getString("streamUrl"),
                            obj.optString("coverArtId", null),
                            obj.optString("quality", "标准音质")
                    ));
                }
                if (!list.isEmpty()) {
                    playlist.clear();
                    playlist.addAll(list);
                    currentIndex = sp.getInt("saved_index", 0);
                    currentMode = sp.getInt("saved_mode", MODE_LOOP_ALL);
                    if (currentIndex < 0 || currentIndex >= playlist.size()) {
                        currentIndex = 0;
                    }
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        localStreamProxy = new LocalStreamProxy(this);
        localStreamProxy.start();

        audioEffectsManager = AudioEffectsManager.getInstance(this);

        initMediaPlayer();
        startStatusBroadcastTask();
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        // 应用静音状态
        applyVolumeState();

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isBuffering = false;
                retryCount = 0;
                applyVolumeState();
                mp.start();
                audioEffectsManager.attachAudioSession(0);
                triggerSmartPreCacheNextSong();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (currentMode == MODE_SINGLE) {
                    playSongAt(currentIndex);
                } else {
                    playNextSong();
                }
            }
        });

        mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                bufferPercent = percent;
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                isBuffering = false;
                if (retryCount < maxRetries) {
                    retryCount++;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            playSongAt(currentIndex);
                        }
                    }, 1200);
                } else {
                    retryCount = 0;
                    playNextSong();
                }
                return true;
            }
        });
    }

    private void applyVolumeState() {
        if (mediaPlayer == null) return;
        try {
            if (isCastMuted) {
                mediaPlayer.setVolume(0.0f, 0.0f);
            } else {
                mediaPlayer.setVolume(1.0f, 1.0f);
            }
        } catch (Exception ignored) {}
    }

    private void triggerSmartPreCacheNextSong() {
        if (playlist.isEmpty() || currentIndex < 0) return;
        int nextIdx = (currentIndex + 1) % playlist.size();
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            nextIdx = new Random().nextInt(playlist.size());
        }
        if (nextIdx >= 0 && nextIdx < playlist.size()) {
            SongItem nextSong = playlist.get(nextIdx);
            if (!CacheManager.isSongCached(this, nextSong.id)) {
                CacheManager.preCacheSong(this, nextSong.id, nextSong.streamUrl);
            }
        }
    }

    private void startStatusBroadcastTask() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(BROADCAST_STATUS);
                    boolean isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
                    int pos = 0;
                    int dur = 0;
                    if (mediaPlayer != null) {
                        try {
                            pos = mediaPlayer.getCurrentPosition();
                            dur = mediaPlayer.getDuration();
                        } catch (Exception ignored) {}
                    }

                    intent.putExtra("isPlaying", isPlaying);
                    intent.putExtra("position", pos);
                    intent.putExtra("duration", dur);
                    intent.putExtra("mode", currentMode);
                    intent.putExtra("isBuffering", isBuffering);
                    intent.putExtra("bufferPercent", bufferPercent);
                    intent.putExtra("retryCount", retryCount);
                    intent.putExtra("maxRetries", maxRetries);
                    intent.putExtra("isCastMuted", isCastMuted);

                    if (currentIndex >= 0 && currentIndex < playlist.size()) {
                        SongItem cur = playlist.get(currentIndex);
                        intent.putExtra("songId", cur.id);
                        intent.putExtra("title", cur.title);
                        intent.putExtra("artist", cur.artist);
                        intent.putExtra("coverArtId", cur.coverArtId);
                        intent.putExtra("quality", cur.quality);

                        savePlaybackState(MusicService.this, pos, dur);
                    }

                    sendBroadcast(intent);
                } catch (Exception ignored) {}

                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void playSongAt(int index) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        SongItem song = playlist.get(currentIndex);

        initMediaPlayer();
        isBuffering = true;
        bufferPercent = 0;

        try {
            if (CacheManager.isSongCached(this, song.id)) {
                File cachedFile = CacheManager.getSongFile(this, song.id);
                mediaPlayer.setDataSource(cachedFile.getAbsolutePath());
            } else if (localStreamProxy != null && localStreamProxy.isRunning()) {
                String proxyUrl = localStreamProxy.getProxyUrl(song.id, song.streamUrl);
                mediaPlayer.setDataSource(proxyUrl);
            } else {
                mediaPlayer.setDataSource(song.streamUrl);
            }
            mediaPlayer.prepareAsync();
            updateForegroundNotification(song.title, song.artist, true);
        } catch (Exception e) {
            isBuffering = false;
        }
    }

    private void playNextSong() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            int r;
            do {
                r = new Random().nextInt(playlist.size());
            } while (r == currentIndex);
            playSongAt(r);
        } else {
            int next = (currentIndex + 1) % playlist.size();
            playSongAt(next);
        }
    }

    private void playPrevSong() {
        if (playlist.isEmpty()) return;
        if (currentMode == MODE_SHUFFLE && playlist.size() > 1) {
            int r;
            do {
                r = new Random().nextInt(playlist.size());
            } while (r == currentIndex);
            playSongAt(r);
        } else {
            int prev = (currentIndex - 1 + playlist.size()) % playlist.size();
            playSongAt(prev);
        }
    }

    private void updateForegroundNotification(String title, String artist, boolean isPlaying) {
        try {
            Intent notifIntent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setContentIntent(pi)
                    .setOngoing(isPlaying);

            startForeground(1001, builder.build());
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) {
                if (mediaPlayer != null) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    } else {
                        mediaPlayer.start();
                    }
                }
            } else if (ACTION_NEXT.equals(action)) {
                playNextSong();
            } else if (ACTION_PREV.equals(action)) {
                playPrevSong();
            } else if (ACTION_PLAY_INDEX.equals(action)) {
                int target = intent.getIntExtra("target_index", 0);
                playSongAt(target);
            } else if (ACTION_SEEK.equals(action)) {
                int pos = intent.getIntExtra("position", 0);
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(pos);
                }
            } else if (ACTION_CYCLE_MODE.equals(action)) {
                currentMode = (currentMode + 1) % 3;
            } else if (ACTION_MUTE.equals(action)) {
                // 伴随投播：静音本机音轨，保持播放和时间广播运行
                isCastMuted = true;
                applyVolumeState();
            } else if (ACTION_UNMUTE.equals(action)) {
                // 断开投播：恢复本机音量
                isCastMuted = false;
                applyVolumeState();
            } else if (ACTION_STOP.equals(action)) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (localStreamProxy != null) {
            localStreamProxy.stop();
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
