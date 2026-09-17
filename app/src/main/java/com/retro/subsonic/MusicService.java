package com.retro.subsonic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String ACTION_TRACK_CHANGED = "com.retro.subsonic.ACTION_TRACK_CHANGED";
    public static final String ACTION_STATE_CHANGED = "com.retro.subsonic.ACTION_STATE_CHANGED";
    private static final String CHANNEL_ID = "retro_music_channel";

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private final List<MainActivity.SongInfo> playlist = new ArrayList<>();
    private int currentIndex = -1;

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaPlayer();
        createNotificationChannel();
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Retro Music Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public void setPlaylist(List<MainActivity.SongInfo> songs, int startIndex) {
        playlist.clear();
        if (songs != null) {
            playlist.addAll(songs);
        }
        if (startIndex >= 0 && startIndex < playlist.size()) {
            currentIndex = startIndex;
            playSong(playlist.get(currentIndex));
        }
    }

    public void playSong(MainActivity.SongInfo song) {
        if (song == null) return;
        try {
            mediaPlayer.reset();

            SharedPreferences sp = getSharedPreferences("retro_subsonic_pref", MODE_PRIVATE);
            String serverUrl = sp.getString("server_url", "");
            String user = sp.getString("username", "");
            String token = sp.getString("token", "");
            String salt = sp.getString("salt", "");

            if (!serverUrl.endsWith("/")) serverUrl += "/";
            String streamUrl = serverUrl + "rest/stream.view?"
                    + "id=" + URLEncoder.encode(song.id, "UTF-8")
                    + "&u=" + URLEncoder.encode(user, "UTF-8")
                    + "&t=" + token
                    + "&s=" + salt
                    + "&v=1.16.1&c=RetroSubsonic";

            mediaPlayer.setDataSource(streamUrl);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                notifyTrackChanged();
                startForegroundNotification(song);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.start();
        }
        notifyStateChanged();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        playSong(playlist.get(currentIndex));
    }

    public void prev() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playSong(playlist.get(currentIndex));
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }

    public void seekTo(int ms) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(ms);
        }
    }

    public MainActivity.SongInfo getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    private void notifyTrackChanged() {
        Intent intent = new Intent(ACTION_TRACK_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyStateChanged() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void startForegroundNotification(MainActivity.SongInfo song) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(101, notification);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        next();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mediaPlayer.reset();
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
