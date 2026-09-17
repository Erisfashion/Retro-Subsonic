package com.retro.subsonic;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MusicService musicService;
    private boolean isBound = false;

    // 视图组件
    private RelativeLayout layoutMiniPlayer;
    private RelativeLayout layoutDetailPlayer;
    private LinearLayout layoutPlaylistDetail;
    private ListView lvPlaylists;
    private ListView lvPlaylistSongs;

    private ImageView ivMiniCover;
    private TextView tvMiniTitle;
    private TextView tvMiniArtist;
    private ImageButton btnMiniPrev;
    private ImageButton btnMiniPlay;
    private ImageButton btnMiniNext;
    private ImageButton btnMiniQueue;

    private ImageView ivVinyl;
    private ImageView ivCover;
    private TonearmView tonearmView;
    private TextView tvDetailTitle;
    private TextView tvDetailArtist;
    private TextView tvDetailLyrics;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBarProgress;
    private ImageButton btnDetailBack;
    private ImageButton btnDetailFav;
    private ImageButton btnDetailPlay;
    private ImageButton btnDetailPrev;
    private ImageButton btnDetailNext;
    private ImageButton btnDetailMode;
    private ImageButton btnDetailQueue;
    private TextView tvPlaylistDetailName;
    private ImageButton btnBackFromPlaylist;

    // 数据缓存
    private final List<PlaylistInfo> playlistList = new ArrayList<>();
    private final List<SongInfo> currentPlaylistSongs = new ArrayList<>();
    private ArrayAdapter<PlaylistInfo> playlistAdapter;
    private SongAdapter songAdapter;
    private String currentSelectedPlaylistId = "";

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    public static class SongInfo {
        public String id;
        public String title;
        public String artist;
        public String album;
        public int duration;
        public String coverArt;

        @Override
        public String toString() {
            return title + " - " + artist;
        }
    }

    public static class PlaylistInfo {
        public String id;
        public String name;
        public int songCount;

        @Override
        public String toString() {
            return name + " (" + songCount + ")";
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder b = (MusicService.LocalBinder) binder;
            musicService = b.getService();
            isBound = true;
            updateFullPlayerUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
        }
    };

    private final BroadcastReceiver playerStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateFullPlayerUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupIcons();
        setupBackPressHandler();
        setupListAdapters();

        // 绑定音乐后台服务
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // 注册广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_TRACK_CHANGED);
        filter.addAction(MusicService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playerStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playerStateReceiver, filter);
        }

        loadPlaylistsFromServer();
        startProgressUpdater();
    }

    private void initViews() {
        layoutMiniPlayer = findViewById(R.id.layoutMiniPlayer);
        layoutDetailPlayer = findViewById(R.id.layoutDetailPlayer);
        layoutPlaylistDetail = findViewById(R.id.layoutPlaylistDetail);
        lvPlaylists = findViewById(R.id.lvPlaylists);
        lvPlaylistSongs = findViewById(R.id.lvPlaylistSongs);

        ivMiniCover = findViewById(R.id.ivMiniCover);
        tvMiniTitle = findViewById(R.id.tvMiniTitle);
        tvMiniArtist = findViewById(R.id.tvMiniArtist);
        btnMiniPrev = findViewById(R.id.btnMiniPrev);
        btnMiniPlay = findViewById(R.id.btnMiniPlay);
        btnMiniNext = findViewById(R.id.btnMiniNext);
        btnMiniQueue = findViewById(R.id.btnMiniQueue);

        ivVinyl = findViewById(R.id.ivVinyl);
        ivCover = findViewById(R.id.ivCover);
        tonearmView = findViewById(R.id.tonearmView);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailArtist = findViewById(R.id.tvDetailArtist);
        tvDetailLyrics = findViewById(R.id.tvDetailLyrics);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBarProgress = findViewById(R.id.seekBarProgress);
        btnDetailBack = findViewById(R.id.btnDetailBack);
        btnDetailFav = findViewById(R.id.btnDetailFav);
        btnDetailPlay = findViewById(R.id.btnDetailPlay);
        btnDetailPrev = findViewById(R.id.btnDetailPrev);
        btnDetailNext = findViewById(R.id.btnDetailNext);
        btnDetailMode = findViewById(R.id.btnDetailMode);
        btnDetailQueue = findViewById(R.id.btnDetailQueue);
        tvPlaylistDetailName = findViewById(R.id.tvPlaylistDetailName);
        btnBackFromPlaylist = findViewById(R.id.btnBackFromPlaylist);

        // 底部迷你播放器点击进入详情页
        layoutMiniPlayer.setOnClickListener(v -> showDetailPlayer(true));

        // 详情页返回
        btnDetailBack.setOnClickListener(v -> showDetailPlayer(false));

        // 歌单详情页返回
        btnBackFromPlaylist.setOnClickListener(v -> showPlaylistDetail(false));

        // 控制按钮点击
        View.OnClickListener togglePlayListener = v -> {
            if (musicService != null) {
                musicService.togglePlayPause();
                updatePlayPauseIcons();
            }
        };
        btnMiniPlay.setOnClickListener(togglePlayListener);
        btnDetailPlay.setOnClickListener(togglePlayListener);

        btnMiniNext.setOnClickListener(v -> { if (musicService != null) musicService.next(); });
        btnDetailNext.setOnClickListener(v -> { if (musicService != null) musicService.next(); });

        btnMiniPrev.setOnClickListener(v -> { if (musicService != null) musicService.prev(); });
        btnDetailPrev.setOnClickListener(v -> { if (musicService != null) musicService.prev(); });

        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicService != null) {
                    int duration = musicService.getDuration();
                    if (duration > 0) {
                        int current = (int) (((float) progress / 1000f) * duration);
                        tvCurrentTime.setText(formatTime(current));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (musicService != null) {
                    int duration = musicService.getDuration();
                    int targetMs = (int) (((float) seekBar.getProgress() / 1000f) * duration);
                    musicService.seekTo(targetMs);
                }
                isUserSeeking = false;
            }
        });
    }

    private void setupIcons() {
        int iconColor = Color.parseColor("#FFFFFF");
        int subColor = Color.parseColor("#CCCCCC");

        btnMiniPrev.setImageDrawable(MediaIconHelper.getPrevDrawable(this, subColor, 20));
        btnMiniNext.setImageDrawable(MediaIconHelper.getNextDrawable(this, subColor, 20));
        btnMiniQueue.setImageDrawable(MediaIconHelper.getQueueDrawable(this, subColor, 20));

        btnDetailBack.setImageDrawable(MediaIconHelper.getBackDrawable(this, iconColor, 24));
        btnDetailPrev.setImageDrawable(MediaIconHelper.getPrevDrawable(this, iconColor, 26));
        btnDetailNext.setImageDrawable(MediaIconHelper.getNextDrawable(this, iconColor, 26));
        btnDetailQueue.setImageDrawable(MediaIconHelper.getQueueDrawable(this, iconColor, 22));
        btnDetailMode.setImageDrawable(MediaIconHelper.getQueueDrawable(this, iconColor, 22));
        btnBackFromPlaylist.setImageDrawable(MediaIconHelper.getBackDrawable(this, iconColor, 22));

        updatePlayPauseIcons();
    }

    private void updatePlayPauseIcons() {
        boolean isPlaying = musicService != null && musicService.isPlaying();
        int darkColor = Color.parseColor("#1B1B1E");
        int playIconColor = Color.parseColor("#111111");

        btnMiniPlay.setImageDrawable(isPlaying
                ? MediaIconHelper.getPauseDrawable(this, playIconColor, 24)
                : MediaIconHelper.getPlayDrawable(this, playIconColor, 24));

        btnDetailPlay.setImageDrawable(isPlaying
                ? MediaIconHelper.getPauseDrawable(this, darkColor, 32)
                : MediaIconHelper.getPlayDrawable(this, darkColor, 32));

        if (tonearmView != null) {
            tonearmView.setPlaying(isPlaying);
        }
    }

    private void setupBackPressHandler() {
        // 核心修复：按返回键逐级退出，避免直接退出至桌面
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutDetailPlayer.getVisibility() == View.VISIBLE) {
                    showDetailPlayer(false);
                    return;
                }
                if (layoutPlaylistDetail.getVisibility() == View.VISIBLE) {
                    showPlaylistDetail(false);
                    return;
                }
                // 在首页根视图时，执行默认退出行为
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void showDetailPlayer(boolean show) {
        layoutDetailPlayer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showPlaylistDetail(boolean show) {
        layoutPlaylistDetail.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupListAdapters() {
        playlistAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, playlistList);
        lvPlaylists.setAdapter(playlistAdapter);

        lvPlaylists.setOnItemClickListener((parent, view, position, id) -> {
            if (position < playlistList.size()) {
                PlaylistInfo info = playlistList.get(position);
                openPlaylistSongs(info);
            }
        });

        songAdapter = new SongAdapter(this, currentPlaylistSongs);
        lvPlaylistSongs.setAdapter(songAdapter);

        lvPlaylistSongs.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentPlaylistSongs.size() && musicService != null) {
                musicService.setPlaylist(currentPlaylistSongs, position);
                updateFullPlayerUI();
            }
        });

        // 注册歌单列表项长按上下文菜单
        registerForContextMenu(lvPlaylistSongs);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.lvPlaylistSongs) {
            menu.setHeaderTitle("操作歌曲");
            menu.add(0, 1, 0, "移出歌单");
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (info != null && info.position < currentPlaylistSongs.size()) {
                SongInfo targetSong = currentPlaylistSongs.get(info.position);
                removeSongFromPlaylist(currentSelectedPlaylistId, info.position, targetSong);
            }
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // 核心修复：歌单移出歌曲并同步至云服务器
    private void removeSongFromPlaylist(String playlistId, int songIndex, SongInfo song) {
        if (playlistId == null || playlistId.isEmpty() || "starred_virtual".equals(playlistId)) {
            Toast.makeText(this, "该列表暂不支持移出操作", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                SharedPreferences sp = getSharedPreferences("retro_subsonic_pref", MODE_PRIVATE);
                String serverUrl = sp.getString("server_url", "");
                String user = sp.getString("username", "");
                String token = sp.getString("token", "");
                String salt = sp.getString("salt", "");

                if (!serverUrl.endsWith("/")) serverUrl += "/";
                String urlStr = serverUrl + "rest/updatePlaylist.view?"
                        + "playlistId=" + URLEncoder.encode(playlistId, "UTF-8")
                        + "&songIndexToRemove=" + songIndex
                        + "&u=" + URLEncoder.encode(user, "UTF-8")
                        + "&t=" + token
                        + "&s=" + salt
                        + "&v=1.16.1&c=RetroSubsonic&f=json";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject response = root.optJSONObject("subsonic-response");
                    if (response != null && "ok".equalsIgnoreCase(response.optString("status"))) {
                        success = true;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    if (songIndex < currentPlaylistSongs.size()) {
                        currentPlaylistSongs.remove(songIndex);
                        songAdapter.notifyDataSetChanged();
                    }
                    Toast.makeText(MainActivity.this, "已成功从歌单移出并同步到云端", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "移出歌单失败，请检查网络或权限", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // 核心修复：保证“我的收藏”仅显示一个，并加载服务端歌单
    private void loadPlaylistsFromServer() {
        new Thread(() -> {
            List<PlaylistInfo> result = new ArrayList<>();

            // 唯一本地固定“我的收藏”入口
            PlaylistInfo fav = new PlaylistInfo();
            fav.id = "starred_virtual";
            fav.name = "我的收藏";
            fav.songCount = 0;
            result.add(fav);

            try {
                SharedPreferences sp = getSharedPreferences("retro_subsonic_pref", MODE_PRIVATE);
                String serverUrl = sp.getString("server_url", "");
                String user = sp.getString("username", "");
                String token = sp.getString("token", "");
                String salt = sp.getString("salt", "");

                if (!serverUrl.isEmpty()) {
                    if (!serverUrl.endsWith("/")) serverUrl += "/";
                    String urlStr = serverUrl + "rest/getPlaylists.view?"
                            + "u=" + URLEncoder.encode(user, "UTF-8")
                            + "&t=" + token
                            + "&s=" + salt
                            + "&v=1.16.1&c=RetroSubsonic&f=json";

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();

                        JSONObject root = new JSONObject(sb.toString());
                        JSONObject response = root.optJSONObject("subsonic-response");
                        if (response != null) {
                            JSONObject playlistsObj = response.optJSONObject("playlists");
                            if (playlistsObj != null) {
                                JSONArray array = playlistsObj.optJSONArray("playlist");
                                if (array != null) {
                                    for (int i = 0; i < array.length(); i++) {
                                        JSONObject p = array.getJSONObject(i);
                                        String name = p.optString("name", "");

                                        // 过滤服务端重复的“我的收藏”或“Favorites”，防止重复出现
                                        if ("我的收藏".equalsIgnoreCase(name.trim())
                                                || "Favorites".equalsIgnoreCase(name.trim())
                                                || "Starred".equalsIgnoreCase(name.trim())) {
                                            continue;
                                        }

                                        PlaylistInfo pi = new PlaylistInfo();
                                        pi.id = p.optString("id");
                                        pi.name = name;
                                        pi.songCount = p.optInt("songCount", 0);
                                        result.add(pi);
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                playlistList.clear();
                playlistList.addAll(result);
                playlistAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void openPlaylistSongs(PlaylistInfo info) {
        currentSelectedPlaylistId = info.id;
        tvPlaylistDetailName.setText(info.name);
        showPlaylistDetail(true);

        new Thread(() -> {
            List<SongInfo> songs = new ArrayList<>();
            try {
                SharedPreferences sp = getSharedPreferences("retro_subsonic_pref", MODE_PRIVATE);
                String serverUrl = sp.getString("server_url", "");
                String user = sp.getString("username", "");
                String token = sp.getString("token", "");
                String salt = sp.getString("salt", "");

                if (!serverUrl.endsWith("/")) serverUrl += "/";

                String api = "starred_virtual".equals(info.id)
                        ? "rest/getStarred2.view?"
                        : "rest/getPlaylist.view?id=" + URLEncoder.encode(info.id, "UTF-8") + "&";

                String urlStr = serverUrl + api
                        + "u=" + URLEncoder.encode(user, "UTF-8")
                        + "&t=" + token
                        + "&s=" + salt
                        + "&v=1.16.1&c=RetroSubsonic&f=json";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject response = root.optJSONObject("subsonic-response");
                    if (response != null) {
                        JSONArray songArray = null;
                        if ("starred_virtual".equals(info.id)) {
                            JSONObject starred = response.optJSONObject("starred2");
                            if (starred != null) songArray = starred.optJSONArray("song");
                        } else {
                            JSONObject playlist = response.optJSONObject("playlist");
                            if (playlist != null) songArray = playlist.optJSONArray("entry");
                        }

                        if (songArray != null) {
                            for (int i = 0; i < songArray.length(); i++) {
                                JSONObject s = songArray.getJSONObject(i);
                                SongInfo si = new SongInfo();
                                si.id = s.optString("id");
                                si.title = s.optString("title", "未知曲目");
                                si.artist = s.optString("artist", "未知歌手");
                                si.album = s.optString("album", "");
                                si.duration = s.optInt("duration", 0);
                                si.coverArt = s.optString("coverArt", "");
                                songs.add(si);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                currentPlaylistSongs.clear();
                currentPlaylistSongs.addAll(songs);
                songAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    // 核心修复：更新迷你条与全屏详情页的全部播放信息与歌词
    private void updateFullPlayerUI() {
        if (musicService == null) return;

        SongInfo song = musicService.getCurrentSong();
        if (song != null) {
            tvMiniTitle.setText(song.title);
            tvMiniArtist.setText(song.artist);

            tvDetailTitle.setText(song.title);
            tvDetailArtist.setText(song.artist + (song.album.isEmpty() ? "" : " - " + song.album));

            loadLyricsFromServer(song);
        } else {
            tvMiniTitle.setText("未在播放");
            tvMiniArtist.setText("Retro Subsonic");
            tvDetailTitle.setText("Retro Subsonic");
            tvDetailArtist.setText("请选择歌曲播放");
            tvDetailLyrics.setText("暂无歌词");
        }

        updatePlayPauseIcons();
    }

    // 核心修复：详情页异步获取 Subsonic 服务端歌词
    private void loadLyricsFromServer(SongInfo song) {
        tvDetailLyrics.setText("歌词加载中...");
        new Thread(() -> {
            String lyricsText = "（暂无歌词）";
            try {
                SharedPreferences sp = getSharedPreferences("retro_subsonic_pref", MODE_PRIVATE);
                String serverUrl = sp.getString("server_url", "");
                String user = sp.getString("username", "");
                String token = sp.getString("token", "");
                String salt = sp.getString("salt", "");

                if (!serverUrl.isEmpty()) {
                    if (!serverUrl.endsWith("/")) serverUrl += "/";
                    String urlStr = serverUrl + "rest/getLyrics.view?"
                            + "artist=" + URLEncoder.encode(song.artist, "UTF-8")
                            + "&title=" + URLEncoder.encode(song.title, "UTF-8")
                            + "&u=" + URLEncoder.encode(user, "UTF-8")
                            + "&t=" + token
                            + "&s=" + salt
                            + "&v=1.16.1&c=RetroSubsonic&f=json";

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();

                        JSONObject root = new JSONObject(sb.toString());
                        JSONObject res = root.optJSONObject("subsonic-response");
                        if (res != null) {
                            JSONObject lyricsObj = res.optJSONObject("lyrics");
                            if (lyricsObj != null) {
                                String content = lyricsObj.optString("content", "");
                                if (!content.trim().isEmpty()) {
                                    lyricsText = content;
                                }
                            }
                        }
                    }
                    conn.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final String finalText = lyricsText;
            runOnUiThread(() -> tvDetailLyrics.setText(finalText));
        }).start();
    }

    private void startProgressUpdater() {
        progressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (musicService != null && !isUserSeeking && musicService.isPlaying()) {
                    int current = musicService.getCurrentPosition();
                    int duration = musicService.getDuration();
                    if (duration > 0) {
                        int progress = (int) (((float) current / duration) * 1000f);
                        seekBarProgress.setProgress(progress);
                        tvCurrentTime.setText(formatTime(current));
                        tvTotalTime.setText(formatTime(duration));
                    }
                }
                progressHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60));
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        unregisterReceiver(playerStateReceiver);
    }

    // 自定义列表适配器
    private static class SongAdapter extends ArrayAdapter<SongInfo> {
        public SongAdapter(Context context, List<SongInfo> songs) {
            super(context, android.R.layout.simple_list_item_2, songs);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            SongInfo song = getItem(position);
            if (song != null) {
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                t1.setText(song.title);
                t1.setTextColor(Color.WHITE);
                t2.setText(song.artist);
                t2.setTextColor(Color.parseColor("#999999"));
            }
            return v;
        }
    }
}
