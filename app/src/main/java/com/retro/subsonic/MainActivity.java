package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private EditText etServer, etUsername, etPassword, etSearchKeyword, etCacheSize;
    private EditText etTimeoutSec, etRetryCount, etDownloadPath;
    private Button btnConnect, btnClearCache, btnToggleConfig, btnTabPlaylists, btnTabSearch, btnSearchSubmit, btnBack;
    private Button btnPrev, btnPlayPause, btnNext, btnMode, btnToggleQueue, btnCloseQueue, btnOpenDetail, btnOpenEq;
    private Button btnExitApp, btnDetailExitApp, btnBottomFav, btnDetailFav, btnDetailDownload;
    private LinearLayout layoutConfigPanel, layoutSearchBar, layoutQueuePanel, layoutDetailOverlay, layoutBottomPlayer;
    private TextView tvListTitle, tvCurrentSong, tvTime;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    // 详情页组件
    private Button btnCloseDetail, btnDetailPrev, btnDetailPlayPause, btnDetailNext, btnDetailMode, btnDetailEq;
    private Button btnDetailKeepScreen, btnDetailQueue;
    private ImageView ivDetailCover;
    private LinearLayout layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailTime;
    private SeekBar detailSeekBar;
    private LinearLayout layoutDetailLyricsView, layoutDetailQueueView;
    private ListView lvDetailQueue;

    private boolean isKeepScreenOn = false;

    // 歌词滚动相关
    private ScrollView scrollLyrics;
    private LinearLayout layoutLyricsContainer;
    private Handler lyricHandler = new Handler();
    private boolean isUserTouchingLyrics = false;
    private int currentLyricIndex = -1;

    private static class LyricRow {
        long timeMs;
        String text;
        TextView view;

        LyricRow(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }
    private ArrayList<LyricRow> lyricRows = new ArrayList<LyricRow>();

    private SharedPreferences prefs;
    private Set<String> favSongIds = new HashSet<String>();

    private static class DisplayEntry {
        String id;
        String title;
        String artist;
        String subtitle;
        String coverArt;
        String quality;
        boolean isSong;

        DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.subtitle = subtitle;
            this.coverArt = coverArt;
            this.quality = quality;
            this.isSong = isSong;
        }
    }

    private ArrayList<DisplayEntry> currentItems = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> listData = new ArrayList<Map<String, String>>();
    private SimpleAdapter adapter;

    private ArrayList<Map<String, String>> queueData = new ArrayList<Map<String, String>>();
    private SimpleAdapter queueAdapter;
    private SimpleAdapter detailQueueAdapter;

    private boolean isUserSeeking = false;
    private String lastLoadedSongId = "";

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.BROADCAST_STATUS.equals(intent.getAction())) {
                boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                String playText = isPlaying ? "暂停" : "播放";
                btnPlayPause.setText(playText);
                btnDetailPlayPause.setText(playText);

                int mode = intent.getIntExtra("mode", MusicService.MODE_LOOP_ALL);
                String modeText = getModeString(mode);
                btnMode.setText(modeText);
                btnDetailMode.setText(modeText);

                boolean isBuffering = intent.getBooleanExtra("isBuffering", false);
                int bufferPercent = intent.getIntExtra("bufferPercent", 0);
                int retryCount = intent.getIntExtra("retryCount", 0);
                int maxRetries = intent.getIntExtra("maxRetries", 3);

                String songId = intent.getStringExtra("songId");
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                String coverArtId = intent.getStringExtra("coverArtId");
                String quality = intent.getStringExtra("quality");

                if (title != null) {
                    if (retryCount > 0) {
                        tvCurrentSong.setText("重试连接中 (" + retryCount + "/" + maxRetries + "): " + title);
                        tvDetailTitle.setText("重试中 (" + retryCount + "/" + maxRetries + ")...");
                    } else if (isPlaying) {
                        if (isBuffering && bufferPercent < 100) {
                            tvCurrentSong.setText(title + " - " + artist + " (缓冲 " + bufferPercent + "%)");
                            tvDetailTitle.setText(title + " (缓冲 " + bufferPercent + "%)");
                        } else {
                            tvCurrentSong.setText(title + " - " + artist);
                            tvDetailTitle.setText(title);
                        }
                    } else if (isBuffering) {
                        tvCurrentSong.setText("正在解析缓冲 (" + bufferPercent + "%): " + title);
                        tvDetailTitle.setText("正在起播 (" + bufferPercent + "%)...");
                    } else {
                        tvCurrentSong.setText(title + " - " + artist);
                        tvDetailTitle.setText(title);
                    }
                    tvDetailArtist.setText(artist);

                    if (quality != null && quality.length() > 0) {
                        tvDetailQuality.setText(quality);
                    }

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(artist, title);
                        refreshQueueList();
                    }

                    updateFavButtonState(songId);
                }

                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);

                if (!isUserSeeking && duration > 0) {
                    seekBar.setMax(duration);
                    seekBar.setProgress(position);
                    detailSeekBar.setMax(duration);
                    detailSeekBar.setProgress(position);
                    String timeStr = formatTime(position) + " / " + formatTime(duration);
                    tvTime.setText(timeStr);
                    tvDetailTime.setText(timeStr);

                    updateLyricPosition(position);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        loadFavSet();

        initViews();
        loadSavedConfig();
        setupListeners();
        setupClickInterceptors();

        // 默认进入首页显示我的歌单，并自动静默同步云端收藏夹
        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

    private void loadFavSet() {
        Set<String> set = prefs.getStringSet("fav_songs_set", new HashSet<String>());
        favSongIds = new HashSet<String>(set);
    }

    private void saveFavSet() {
        prefs.edit().putStringSet("fav_songs_set", favSongIds).commit();
    }

    private boolean isFav(String songId) {
        return songId != null && favSongIds.contains(songId);
    }

    // 与服务端全双工同步收藏状态 (star.view / unstar.view)
    private void serverStarSong(final String songId, final boolean toStar) {
        if (songId == null || songId.length() == 0) return;

        if (toStar) {
            favSongIds.add(songId);
            Toast.makeText(this, "已添加至云端【我的收藏】♥", Toast.LENGTH_SHORT).show();
        } else {
            favSongIds.remove(songId);
            Toast.makeText(this, "已从云端【我的收藏】取消♡", Toast.LENGTH_SHORT).show();
        }
        saveFavSet();
        updateFavButtonState(songId);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String endpoint = toStar ? "star.view" : "unstar.view";
                    requestApi(endpoint + "?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void updateFavButtonState(String currentPlayingSongId) {
        String targetId = currentPlayingSongId;
        if (targetId == null || targetId.length() == 0) {
            ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
            int idx = MusicService.getCurrentIndex();
            if (q != null && idx >= 0 && idx < q.size()) {
                targetId = q.get(idx).id;
            }
        }
        boolean fav = isFav(targetId);
        String symbol = fav ? "♥" : "♡";
        if (btnBottomFav != null) btnBottomFav.setText(symbol);
        if (btnDetailFav != null) btnDetailFav.setText(symbol);
    }

    // 后台静默抓取云端标星歌曲列表，保证红心标记与 Web 端无缝一致
    private void syncServerFavoritesQuietly() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null || !jsonStr.contains("\"song\"")) {
                    jsonStr = requestApi("getStarred.view?" + getAuthParams());
                }
                if (jsonStr == null) return;

                try {
                    JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                    JSONObject starred = root.optJSONObject("starred2");
                    if (starred == null) starred = root.optJSONObject("starred");
                    if (starred != null && starred.has("song")) {
                        favSongIds.clear();
                        Object songObj = starred.get("song");
                        if (songObj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) songObj;
                            for (int i = 0; i < arr.length(); i++) {
                                favSongIds.add(arr.getJSONObject(i).getString("id"));
                            }
                        } else if (songObj instanceof JSONObject) {
                            favSongIds.add(((JSONObject) songObj).getString("id"));
                        }
                        saveFavSet();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateFavButtonState(null);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(statusReceiver, new IntentFilter(MusicService.BROADCAST_STATUS));
        refreshQueueList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (layoutDetailOverlay != null && layoutDetailOverlay.getVisibility() == View.VISIBLE) {
                layoutDetailOverlay.setVisibility(View.GONE);
                return true;
            }
            if (layoutQueuePanel != null && layoutQueuePanel.getVisibility() == View.VISIBLE) {
                layoutQueuePanel.setVisibility(View.GONE);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initViews() {
        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnToggleConfig = (Button) findViewById(R.id.btn_toggle_config);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabSearch = (Button) findViewById(R.id.btn_tab_search);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnExitApp = (Button) findViewById(R.id.btn_exit_app);
        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);

        btnPrev = (Button) findViewById(R.id.btn_prev);
        btnPlayPause = (Button) findViewById(R.id.btn_play_pause);
        btnNext = (Button) findViewById(R.id.btn_next);
        btnMode = (Button) findViewById(R.id.btn_mode);
        btnOpenEq = (Button) findViewById(R.id.btn_open_eq);
        btnToggleQueue = (Button) findViewById(R.id.btn_toggle_queue);
        btnCloseQueue = (Button) findViewById(R.id.btn_close_queue);
        btnOpenDetail = (Button) findViewById(R.id.btn_open_detail);

        layoutConfigPanel = (LinearLayout) findViewById(R.id.layout_config_panel);
        layoutSearchBar = (LinearLayout) findViewById(R.id.layout_search_bar);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);

        tvListTitle = (TextView) findViewById(R.id.tv_list_title);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);

        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        listView = (ListView) findViewById(R.id.list_view);
        lvQueue = (ListView) findViewById(R.id.lv_queue);

        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailExitApp = (Button) findViewById(R.id.btn_detail_exit_app);
        btnDetailFav = (Button) findViewById(R.id.btn_detail_fav);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailPrev = (Button) findViewById(R.id.btn_detail_prev);
        btnDetailPlayPause = (Button) findViewById(R.id.btn_detail_play_pause);
        btnDetailNext = (Button) findViewById(R.id.btn_detail_next);
        btnDetailMode = (Button) findViewById(R.id.btn_detail_mode);
        btnDetailEq = (Button) findViewById(R.id.btn_detail_eq);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);

        ivDetailCover = (ImageView) findViewById(R.id.iv_detail_cover);
        layoutCoverContainer = (LinearLayout) findViewById(R.id.layout_cover_container);
        layoutDetailSeekBox = (LinearLayout) findViewById(R.id.layout_detail_seek_box);
        layoutDetailControls = (LinearLayout) findViewById(R.id.layout_detail_controls);

        tvDetailTitle = (TextView) findViewById(R.id.tv_detail_title);
        tvDetailArtist = (TextView) findViewById(R.id.tv_detail_artist);
        tvDetailQuality = (TextView) findViewById(R.id.tv_detail_quality);
        tvDetailTime = (TextView) findViewById(R.id.tv_detail_time);
        detailSeekBar = (SeekBar) findViewById(R.id.detail_seek_bar);

        layoutDetailLyricsView = (LinearLayout) findViewById(R.id.layout_detail_lyrics_view);
        layoutDetailQueueView = (LinearLayout) findViewById(R.id.layout_detail_queue_view);
        lvDetailQueue = (ListView) findViewById(R.id.lv_detail_queue);

        scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);
        layoutLyricsContainer = (LinearLayout) findViewById(R.id.layout_lyrics_container);

        adapter = new SimpleAdapter(
                this,
                listData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        listView.setAdapter(adapter);

        queueAdapter = new SimpleAdapter(
                this,
                queueData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        lvQueue.setAdapter(queueAdapter);

        detailQueueAdapter = new SimpleAdapter(
                this,
                queueData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        lvDetailQueue.setAdapter(detailQueueAdapter);
    }

    private void setupClickInterceptors() {
        View.OnClickListener consumeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {}
        };
        layoutDetailOverlay.setOnClickListener(consumeListener);
        layoutQueuePanel.setOnClickListener(consumeListener);
        layoutConfigPanel.setOnClickListener(consumeListener);
        layoutBottomPlayer.setOnClickListener(consumeListener);
        if (layoutDetailSeekBox != null) layoutDetailSeekBox.setOnClickListener(consumeListener);
        if (layoutDetailControls != null) layoutDetailControls.setOnClickListener(consumeListener);
    }

    private String getDefaultDownloadPath() {
        try {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (musicDir != null) return musicDir.getAbsolutePath();
        } catch (Throwable ignored) {}
        return "/sdcard/Music";
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:4533"));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("pass", "admin"));
        etCacheSize.setText(prefs.getString("cache_size_mb", "500"));
        etTimeoutSec.setText(prefs.getString("play_timeout_sec", "20"));
        etRetryCount.setText(prefs.getString("play_retry_count", "3"));
        etDownloadPath.setText(prefs.getString("download_path", getDefaultDownloadPath()));
    }

    private void saveConfig() {
        prefs.edit()
                .putString("server", etServer.getText().toString().trim())
                .putString("user", etUsername.getText().toString().trim())
                .putString("pass", etPassword.getText().toString().trim())
                .putString("cache_size_mb", etCacheSize.getText().toString().trim())
                .putString("play_timeout_sec", etTimeoutSec.getText().toString().trim())
                .putString("play_retry_count", etRetryCount.getText().toString().trim())
                .putString("download_path", etDownloadPath.getText().toString().trim())
                .commit();
    }

    private String getModeString(int mode) {
        if (mode == MusicService.MODE_SHUFFLE) return "随机播放";
        if (mode == MusicService.MODE_SINGLE) return "单曲循环";
        return "列表循环";
    }

    private String buildStreamUrl(String songId) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        try {
            String encodedId = URLEncoder.encode(songId, "UTF-8");
            return base + "/rest/stream.view?id=" + encodedId + "&u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8") + "&v=1.12.0&c=RetroSubsonic";
        } catch (Exception e) {
            return base + "/rest/stream.view?id=" + songId + "&u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic";
        }
    }

    private void performAppExit() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("关闭软件")
                .setMessage("确定要退出并彻底关闭播放器吗？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent stopIntent = new Intent(MainActivity.this, MusicService.class);
                            stopIntent.setAction(MusicService.ACTION_STOP);
                            startService(stopIntent);
                        } catch (Exception ignored) {}
                        finish();
                        System.exit(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleDetailQueueView() {
        if (layoutDetailQueueView.getVisibility() == View.VISIBLE) {
            layoutDetailQueueView.setVisibility(View.GONE);
            layoutDetailLyricsView.setVisibility(View.VISIBLE);
            btnDetailQueue.setText("播放列表");
        } else {
            refreshQueueList();
            layoutDetailLyricsView.setVisibility(View.GONE);
            layoutDetailQueueView.setVisibility(View.VISIBLE);
            btnDetailQueue.setText("查看歌词");
        }
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    // 解决 302 错误的核心下载引擎：支持多级跨协议重定向跟随与已有缓存毫秒转存
    private void downloadSongItem(final DisplayEntry entry) {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        final File saveDir = new File(customPath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        final String fileName = sanitizeFileName(entry.title + " - " + entry.artist) + ".mp3";
        final File targetFile = new File(saveDir, fileName);

        Toast.makeText(this, "正在准备下载: " + entry.title, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1. 如果歌曲本地已经有缓冲好的缓存，直接毫秒级极速导出！
                if (CacheManager.isSongCached(MainActivity.this, entry.id)) {
                    File cachedFile = CacheManager.getSongFile(MainActivity.this, entry.id);
                    if (copyFile(cachedFile, targetFile)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "已保存至: " + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                }

                // 2. 本地无完整缓存，启动多级 301/302 重定向跟随下载
                try {
                    String initialUrl = buildStreamUrl(entry.id);
                    boolean ok = downloadWithRedirects(initialUrl, targetFile, 0);
                    if (ok && CacheManager.isValidAudioFile(targetFile)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "下载成功！已保存至:\n" + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        targetFile.delete();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "下载失败: 校验非有效音频", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    final String err = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载异常: " + err, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean copyFile(File src, File dest) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fis != null) fis.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }

    private boolean downloadWithRedirects(String targetUrl, File destFile, int depth) throws Exception {
        if (depth > 6) throw new Exception("重定向层级过多");

        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false); // 手动跟随，彻底解决 HTTP -> HTTPS 跨协议不跳转的 Java 原生缺陷
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.connect();

        int code = conn.getResponseCode();

        // 处理 301 / 302 / 303 / 307 重定向跳转至第三方 CDN
        if (code == 301 || code == 302 || code == 303 || code == 307) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location != null && location.length() > 0) {
                URL redirectUrl = new URL(url, location);
                return downloadWithRedirects(redirectUrl.toString(), destFile, depth + 1);
            }
            throw new Exception("重定向地址为空 (HTTP " + code + ")");
        }

        if (code == 200 || code == 206) {
            InputStream is = conn.getInputStream();
            byte[] preview = new byte[2048];
            int r = is.read(preview);
            if (r <= 0) {
                conn.disconnect();
                throw new Exception("服务器返回了空数据");
            }

            String previewStr = new String(preview, 0, r, "UTF-8").trim();
            // 如果服务端返回了包含下载直链的 JSON 结构，提取出真实 URL 继续追踪
            if (previewStr.startsWith("{") || previewStr.startsWith("[")) {
                StringBuilder sb = new StringBuilder(previewStr);
                byte[] temp = new byte[4096];
                int l;
                while ((l = is.read(temp)) != -1) {
                    sb.append(new String(temp, 0, l, "UTF-8"));
                }
                conn.disconnect();
                JSONObject root = new JSONObject(sb.toString());
                String directUrl = findAudioUrlInJson(root);
                if (directUrl != null) {
                    return downloadWithRedirects(directUrl, destFile, depth + 1);
                }
                throw new Exception("返回的 JSON 中未找到下载链接");
            }

            FileOutputStream fos = new FileOutputStream(destFile);
            fos.write(preview, 0, r);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
            fos.flush();
            fos.close();
            is.close();
            conn.disconnect();
            return true;
        } else {
            conn.disconnect();
            throw new Exception("服务器响应异常 (HTTP " + code + ")");
        }
    }

    private String findAudioUrlInJson(Object json) {
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
                String found = findAudioUrlInJson(obj.opt(k));
                if (found != null) return found;
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                String found = findAudioUrlInJson(arr.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    // 弹出歌曲长按菜单
    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        if (!entry.isSong) return;
        final boolean fav = isFav(entry.id);

        String[] options = new String[]{
                fav ? "★ 已收藏（点此从云端取消）" : "☆ 收藏歌曲 (同步云端)",
                "📁 添加到歌单",
                "🗑 移出歌单/移除",
                "⬇ 下载歌曲到本地"
        };

        new AlertDialog.Builder(this)
                .setTitle(entry.title + " - " + entry.artist)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            serverStarSong(entry.id, !fav);
                        } else if (which == 1) {
                            showAddToPlaylistDialog(entry);
                        } else if (which == 2) {
                            removeFromCurrentView(position);
                        } else if (which == 3) {
                            downloadSongItem(entry);
                        }
                    }
                })
                .show();
    }

    private void showAddToPlaylistDialog(final DisplayEntry entry) {
        final String[] targets = new String[]{"我的收藏 (云端)", "精选歌单", "车载音乐"};
        new AlertDialog.Builder(this)
                .setTitle("选择要加入的歌单")
                .setItems(targets, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (!isFav(entry.id)) serverStarSong(entry.id, true);
                        }
                        Toast.makeText(MainActivity.this, "已加入【" + targets[which] + "】", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void removeFromCurrentView(int position) {
        if (position >= 0 && position < currentItems.size()) {
            currentItems.remove(position);
            listData.remove(position);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "已从当前列表移出", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        btnExitApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        });

        btnDetailExitApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        });

        View.OnClickListener favClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                int idx = MusicService.getCurrentIndex();
                if (q != null && idx >= 0 && idx < q.size()) {
                    String sid = q.get(idx).id;
                    serverStarSong(sid, !isFav(sid));
                } else {
                    Toast.makeText(MainActivity.this, "当前无播放中的歌曲可收藏", Toast.LENGTH_SHORT).show();
                }
            }
        };
        btnBottomFav.setOnClickListener(favClickListener);
        btnDetailFav.setOnClickListener(favClickListener);

        btnDetailDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                int idx = MusicService.getCurrentIndex();
                if (q != null && idx >= 0 && idx < q.size()) {
                    MusicService.SongItem cur = q.get(idx);
                    DisplayEntry dummy = new DisplayEntry(cur.id, cur.title, cur.artist, "", "", cur.quality, true);
                    downloadSongItem(dummy);
                } else {
                    Toast.makeText(MainActivity.this, "当前无播放中的歌曲可下载", Toast.LENGTH_SHORT).show();
                }
            }
        });

        View.OnClickListener toggleQueueClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleDetailQueueView(); }
        };
        ivDetailCover.setOnClickListener(toggleQueueClickListener);
        layoutCoverContainer.setOnClickListener(toggleQueueClickListener);

        btnToggleConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutConfigPanel.getVisibility() == View.VISIBLE) {
                    layoutConfigPanel.setVisibility(View.GONE);
                } else {
                    layoutConfigPanel.setVisibility(View.VISIBLE);
                }
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
                layoutConfigPanel.setVisibility(View.GONE);
                fetchPlaylists();
                syncServerFavoritesQuietly();
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long bytes = CacheManager.getUsedCacheBytes(MainActivity.this);
                double mb = bytes / (1024.0 * 1024.0);
                CacheManager.clearAllCache(MainActivity.this);
                Toast.makeText(MainActivity.this, String.format("已清理缓存，释放 %.1f MB 空间", mb), Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener eqListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { new EqualizerDialog(MainActivity.this).show(); }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

        btnTabPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchBar.setVisibility(View.GONE);
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("我的歌单");
                fetchPlaylists();
            }
        });

        btnTabSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchBar.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("搜索音乐");
                currentItems.clear();
                listData.clear();
                adapter.notifyDataSetChanged();
            }
        });

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { searchSongs(etSearchKeyword.getText().toString().trim()); }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("我的歌单");
                fetchPlaylists();
            }
        });

        btnToggleQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutQueuePanel.getVisibility() == View.VISIBLE) {
                    layoutQueuePanel.setVisibility(View.GONE);
                } else {
                    refreshQueueList();
                    layoutQueuePanel.setVisibility(View.VISIBLE);
                }
            }
        });

        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

        btnOpenDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutDetailOverlay.setVisibility(View.VISIBLE); }
        });

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutDetailOverlay.setVisibility(View.GONE); }
        });

        btnDetailKeepScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isKeepScreenOn = !isKeepScreenOn;
                if (isKeepScreenOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮: 开");
                    Toast.makeText(MainActivity.this, "已开启屏幕常亮", Toast.LENGTH_SHORT).show();
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮: 关");
                    Toast.makeText(MainActivity.this, "已关闭屏幕常亮", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleDetailQueueView(); }
        });

        scrollLyrics.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    isUserTouchingLyrics = true;
                    lyricHandler.removeCallbacksAndMessages(null);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    lyricHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() { isUserTouchingLyrics = false; }
                    }, 3000);
                }
                return false;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= currentItems.size()) return;
                DisplayEntry entry = currentItems.get(position);
                if (!entry.isSong) {
                    fetchPlaylistSongs(entry.id, entry.title);
                } else {
                    ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                    int clickedSongIndex = 0;
                    for (int i = 0; i < currentItems.size(); i++) {
                        DisplayEntry item = currentItems.get(i);
                        if (item.isSong) {
                            if (item.id.equals(entry.id)) {
                                clickedSongIndex = queue.size();
                            }
                            queue.add(new MusicService.SongItem(item.id, item.title, item.artist, buildStreamUrl(item.id), item.coverArt, item.quality));
                        }
                    }
                    MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
                    refreshQueueList();
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentItems.size()) {
                    showSongLongClickMenu(currentItems.get(position), position);
                    return true;
                }
                return false;
            }
        });

        AdapterView.OnItemClickListener queueItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY_INDEX);
                intent.putExtra("target_index", position);
                startService(intent);
            }
        };
        lvQueue.setOnItemClickListener(queueItemClickListener);
        lvDetailQueue.setOnItemClickListener(queueItemClickListener);

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
            }
        };
        btnPlayPause.setOnClickListener(toggleListener);
        btnDetailPlayPause.setOnClickListener(toggleListener);

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
            }
        };
        btnNext.setOnClickListener(nextListener);
        btnDetailNext.setOnClickListener(nextListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
            }
        };
        btnPrev.setOnClickListener(prevListener);
        btnDetailPrev.setOnClickListener(prevListener);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
            }
        };
        btnMode.setOnClickListener(modeListener);
        btnDetailMode.setOnClickListener(modeListener);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    String t = formatTime(progress) + " / " + formatTime(sb.getMax());
                    tvTime.setText(t);
                    tvDetailTime.setText(t);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_SEEK);
                intent.putExtra("position", sb.getProgress());
                startService(intent);
            }
        };
        seekBar.setOnSeekBarChangeListener(seekListener);
        detailSeekBar.setOnSeekBarChangeListener(seekListener);
    }

    private void refreshQueueList() {
        ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
        int currentPlaying = MusicService.getCurrentIndex();
        queueData.clear();

        for (int i = 0; i < list.size(); i++) {
            MusicService.SongItem item = list.get(i);
            Map<String, String> row = new HashMap<String, String>();
            if (i == currentPlaying) {
                row.put("title", ">> " + (i + 1) + ". " + item.title);
            } else {
                row.put("title", (i + 1) + ". " + item.title);
            }
            row.put("subtitle", item.artist);
            queueData.add(row);
        }
        queueAdapter.notifyDataSetChanged();
        detailQueueAdapter.notifyDataSetChanged();
    }

    private void loadCoverArt(final String coverId) {
        if (coverId == null || coverId.length() == 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String base = prefs.getString("server", "");
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                String urlStr = base + "/rest/getCoverArt.view?id=" + coverId + "&size=400&" + getAuthParams();
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() { ivDetailCover.setImageBitmap(bitmap); }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void loadLyrics(final String artist, final String title) {
        lyricRows.clear();
        currentLyricIndex = -1;
        layoutLyricsContainer.removeAllViews();
        TextView loadingTv = new TextView(this);
        loadingTv.setText("歌词加载中...");
        loadingTv.setTextColor(0xFF888888);
        loadingTv.setGravity(Gravity.CENTER);
        layoutLyricsContainer.addView(loadingTv);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String p = "artist=" + URLEncoder.encode(artist, "UTF-8") + "&title=" + URLEncoder.encode(title, "UTF-8");
                    final String jsonStr = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (jsonStr == null) {
                                showSimpleLyric("暂无歌词");
                                return;
                            }
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                if (root.has("lyrics")) {
                                    Object lyricsObj = root.get("lyrics");
                                    String text = "";
                                    if (lyricsObj instanceof JSONObject) {
                                        JSONObject l = (JSONObject) lyricsObj;
                                        text = l.optString("content", l.optString("value", ""));
                                    } else if (lyricsObj instanceof String) {
                                        text = (String) lyricsObj;
                                    }

                                    if (text != null && text.trim().length() > 0) {
                                        buildLyricsView(text);
                                        return;
                                    }
                                }
                                showSimpleLyric("未找到匹配歌词");
                            } catch (Exception e) {
                                showSimpleLyric("歌词解析失败");
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void showSimpleLyric(String msg) {
        layoutLyricsContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(0xFF888888);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        layoutLyricsContainer.addView(tv);
    }

    private void buildLyricsView(String rawText) {
        layoutLyricsContainer.removeAllViews();
        lyricRows.clear();
        currentLyricIndex = -1;

        String[] lines = rawText.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            int closeBracket = line.indexOf(']');
            if (line.startsWith("[") && closeBracket > 1) {
                String timePart = line.substring(1, closeBracket);
                long timeMs = parseTime(timePart);
                if (timeMs >= 0) {
                    String content = line.substring(closeBracket + 1).trim();
                    if (content.length() == 0) content = "···";
                    lyricRows.add(new LyricRow(timeMs, content));
                }
            }
        }

        if (lyricRows.isEmpty()) {
            for (String raw : lines) {
                if (raw.trim().length() == 0) continue;
                TextView tv = new TextView(this);
                tv.setText(raw.trim());
                tv.setTextColor(0xFFCCCCCC);
                tv.setTextSize(15);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, 10, 0, 10);
                layoutLyricsContainer.addView(tv);
            }
            return;
        }

        Collections.sort(lyricRows, new Comparator<LyricRow>() {
            @Override
            public int compare(LyricRow a, LyricRow b) {
                return Long.valueOf(a.timeMs).compareTo(b.timeMs);
            }
        });

        for (LyricRow row : lyricRows) {
            TextView tv = new TextView(this);
            tv.setText(row.text);
            tv.setTextColor(0xFF777777);
            tv.setTextSize(15);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 12, 0, 12);
            row.view = tv;
            layoutLyricsContainer.addView(tv);
        }
    }

    private long parseTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length >= 2) {
                long min = Long.parseLong(parts[0]);
                float sec = Float.parseFloat(parts[1]);
                return (long) (min * 60 * 1000 + sec * 1000);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty() || isUserTouchingLyrics) return;

        int targetIndex = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (currentPosMs >= lyricRows.get(i).timeMs) {
                targetIndex = i;
            } else {
                break;
            }
        }

        if (targetIndex != currentLyricIndex && targetIndex >= 0) {
            if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                LyricRow oldRow = lyricRows.get(currentLyricIndex);
                if (oldRow.view != null) {
                    oldRow.view.setTextColor(0xFF777777);
                    oldRow.view.setTextSize(15);
                    oldRow.view.setTypeface(Typeface.DEFAULT);
                }
            }

            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow.view != null) {
                curRow.view.setTextColor(0xFF00E5FF);
                curRow.view.setTextSize(18);
                curRow.view.setTypeface(Typeface.DEFAULT_BOLD);

                scrollLyrics.post(new Runnable() {
                    @Override
                    public void run() {
                        int scrollY = curRow.view.getTop() - (scrollLyrics.getHeight() / 2) + (curRow.view.getHeight() / 2);
                        if (scrollY < 0) scrollY = 0;
                        scrollLyrics.smoothScrollTo(0, scrollY);
                    }
                });
            }
        }
    }

    private String getAuthParams() {
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        return "u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic&f=json";
    }

    private String requestApi(String pathWithParams) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String fullUrl = base + "/rest/" + pathWithParams;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void fetchPlaylists() {
        tvListTitle.setText("我的歌单");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylists.view?" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentItems.clear();
                        listData.clear();

                        // 顶置“我的收藏”云端同步歌单入口
                        currentItems.add(new DisplayEntry("fav_entry", "我的收藏", "云端同步", "云端标星收藏夹", null, "云端歌单", false));
                        Map<String, String> favRow = new HashMap<String, String>();
                        favRow.put("title", "♥  我的收藏");
                        favRow.put("subtitle", "已同步服务器标星的歌曲 (" + favSongIds.size() + "首)");
                        listData.add(favRow);

                        if (jsonStr != null) {
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject playlistsObj = root.optJSONObject("playlists");
                                if (playlistsObj != null && playlistsObj.has("playlist")) {
                                    Object plObj = playlistsObj.get("playlist");
                                    if (plObj instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) plObj;
                                        for (int i = 0; i < arr.length(); i++) {
                                            addPlaylistRow(arr.getJSONObject(i));
                                        }
                                    } else if (plObj instanceof JSONObject) {
                                        addPlaylistRow((JSONObject) plObj);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void addPlaylistRow(JSONObject p) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        currentItems.add(new DisplayEntry(p.getString("id"), name, "", "歌单", null, "歌单: " + count, false));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "📁  " + name);
        row.put("subtitle", count + " 首歌曲");
        listData.add(row);
    }

    // 打开歌单详情：如果是“我的收藏”，直接从服务器拉取已标星列表
    private void fetchPlaylistSongs(final String playlistId, final String playlistName) {
        if ("fav_entry".equals(playlistId)) {
            fetchServerFavoriteSongs();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylist.view?id=" + playlistId + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) return;
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject playlist = root.getJSONObject("playlist");
                            currentItems.clear();
                            listData.clear();

                            if (playlist.has("entry")) {
                                Object entryObj = playlist.get("entry");
                                if (entryObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) entryObj;
                                    for (int i = 0; i < arr.length(); i++) {
                                        addSongRow(arr.getJSONObject(i));
                                    }
                                } else if (entryObj instanceof JSONObject) {
                                    addSongRow((JSONObject) entryObj);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText("歌单: " + playlistName);
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "加载歌单失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    // 从 Subsonic 云端直接拉取真实的“我的收藏”列表数据
    private void fetchServerFavoriteSongs() {
        btnBack.setVisibility(View.VISIBLE);
        tvListTitle.setText("歌单: 我的收藏 (云端同步)");
        currentItems.clear();
        listData.clear();
        adapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                String jsonStr = requestApi("getStarred2.view?" + getAuthParams());
                if (jsonStr == null || !jsonStr.contains("\"song\"")) {
                    jsonStr = requestApi("getStarred.view?" + getAuthParams());
                }
                final String finalJson = jsonStr;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalJson == null) {
                            Toast.makeText(MainActivity.this, "连接失败，无法获取云端收藏", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            JSONObject root = new JSONObject(finalJson).getJSONObject("subsonic-response");
                            JSONObject starred = root.optJSONObject("starred2");
                            if (starred == null) starred = root.optJSONObject("starred");
                            currentItems.clear();
                            listData.clear();
                            favSongIds.clear();

                            if (starred != null && starred.has("song")) {
                                Object songObj = starred.get("song");
                                if (songObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) songObj;
                                    for (int i = 0; i < arr.length(); i++) {
                                        JSONObject s = arr.getJSONObject(i);
                                        addSongRow(s);
                                        favSongIds.add(s.getString("id"));
                                    }
                                } else if (songObj instanceof JSONObject) {
                                    JSONObject s = (JSONObject) songObj;
                                    addSongRow(s);
                                    favSongIds.add(s.getString("id"));
                                }
                            }
                            saveFavSet();
                            adapter.notifyDataSetChanged();
                            updateFavButtonState(null);
                            if (currentItems.isEmpty()) {
                                Toast.makeText(MainActivity.this, "云端收藏夹为空", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "解析云端收藏列表失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void searchSongs(final String query) {
        if (query.length() == 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    final String jsonStr = requestApi("search3.view?query=" + encoded + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (jsonStr == null) return;
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject result = root.optJSONObject("searchResult3");
                                currentItems.clear();
                                listData.clear();

                                if (result != null && result.has("song")) {
                                    JSONArray songs = result.getJSONArray("song");
                                    for (int i = 0; i < songs.length(); i++) {
                                        addSongRow(songs.getJSONObject(i));
                                    }
                                }
                                adapter.notifyDataSetChanged();
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void addSongRow(JSONObject s) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知艺术家");
        String coverArt = s.optString("coverArt", null);

        int bitRate = s.optInt("bitRate", 0);
        String suffix = s.optString("suffix", "").toUpperCase();
        String quality;
        if (suffix.contains("FLAC") || suffix.contains("WAV") || suffix.contains("APE")) {
            quality = "FLAC 无损";
        } else if (bitRate > 0) {
            quality = bitRate + "K " + (suffix.length() > 0 ? suffix : "MP3");
        } else if (suffix.length() > 0) {
            quality = suffix;
        } else {
            quality = "320K MP3";
        }

        currentItems.add(new DisplayEntry(s.getString("id"), title, artist, subtitleText(artist, quality), coverArt, quality, true));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + "  [" + quality + "]");
        listData.add(row);
    }

    private String subtitleText(String artist, String quality) {
        return artist + " [" + quality + "]";
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
