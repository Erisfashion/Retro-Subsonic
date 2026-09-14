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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
    private Button btnLyricDec, btnLyricInc;
    private LinearLayout layoutConfigPanel, layoutSearchBar, layoutQueuePanel, layoutDetailOverlay, layoutBottomPlayer;
    private TextView tvListTitle, tvCurrentSong, tvTime;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    // 原生轻量级下拉刷新组件
    private LinearLayout refreshHeaderView;
    private ProgressBar refreshProgressBar;
    private TextView refreshTextView;
    private int refreshHeaderHeight = 0;
    private boolean isRefreshing = false;
    private boolean isPulling = false;
    private float touchStartY = 0;

    // 当前页面状态记录
    private String currentActivePlaylistId = null;
    private String currentActivePlaylistName = null;

    // 详情页组件
    private Button btnCloseDetail, btnDetailPrev, btnDetailPlayPause, btnDetailNext, btnDetailMode, btnDetailEq;
    private Button btnDetailKeepScreen, btnDetailQueue;
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private LinearLayout layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls, layoutDetailBottomBlank;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailTime;
    private SeekBar detailSeekBar;
    private LinearLayout layoutDetailLyricsView, layoutDetailQueueView;
    private ListView lvDetailQueue;

    // 封面模式：黑胶唱机模式 vs 静态方形模式
    private boolean isVinylDisplayMode = true;
    private Bitmap currentRawCoverBitmap;

    // 黑胶旋转动画控制
    private RotateAnimation vinylRotateAnim;
    private boolean isCurrentSongPlaying = false;

    private boolean isKeepScreenOn = false;

    // 歌词滚动与字号相关
    private ScrollView scrollLyrics;
    private LinearLayout layoutLyricsContainer;
    private Handler lyricHandler = new Handler();
    private boolean isUserTouchingLyrics = false;
    private int currentLyricIndex = -1;
    private int lyricBaseFontSize = 15;

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
    private ArrayList<DisplayEntry> featuredSongs = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> carSongs = new ArrayList<DisplayEntry>();

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
                isCurrentSongPlaying = isPlaying;
                String playText = isPlaying ? "暂停" : "播放";
                btnPlayPause.setText(playText);
                btnDetailPlayPause.setText(playText);

                updateVinylAnimationState();

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
        // 全局崩溃兜底，避免系统直接显示“已停止运行”对话框
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable ex) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("程序遇到错误")
                                .setMessage(ex.toString() + "\n" + (ex.getCause() != null ? ex.getCause().toString() : ""))
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                })
                                .show();
                    }
                });
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);

        loadFavSet();
        loadLocalPlaylists();

        // 核心时序修复：先初始化视图并完成 HeaderView 挂载，最后再执行 setAdapter
        initViews();
        setupVinylAnimation();
        updateCoverDisplayMode();
        loadSavedConfig();
        setupListeners();
        setupClickInterceptors();

        // 默认进入首页显示我的歌单
        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

    private void setupPullToRefresh() {
        refreshHeaderView = new LinearLayout(this);
        refreshHeaderView.setOrientation(LinearLayout.HORIZONTAL);
        refreshHeaderView.setGravity(Gravity.CENTER);

        float density = getResources().getDisplayMetrics().density;
        refreshHeaderHeight = (int) (48 * density);

        refreshProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        refreshProgressBar.setVisibility(View.GONE);
        refreshHeaderView.addView(refreshProgressBar);

        refreshTextView = new TextView(this);
        refreshTextView.setText("下拉刷新列表");
        refreshTextView.setTextColor(0xFF888C99);
        refreshTextView.setTextSize(12);
        refreshTextView.setPadding((int) (8 * density), 0, 0, 0);
        refreshHeaderView.addView(refreshTextView);

        // 严格遵循 Android 4.2.2 规范：addHeaderView 必须在 setAdapter 之前执行
        listView.addHeaderView(refreshHeaderView, null, false);
        hideRefreshHeader();

        listView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isRefreshing) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (touchStartY == 0) {
                            touchStartY = event.getY();
                        }
                        float deltaY = event.getY() - touchStartY;
                        if (deltaY > 15 && isListViewAtTop()) {
                            isPulling = true;
                            int paddingTop = (int) (-refreshHeaderHeight + (deltaY * 0.45f));
                            refreshHeaderView.setPadding(0, paddingTop, 0, 0);

                            if (paddingTop >= 0) {
                                refreshTextView.setText("释放立即刷新");
                                refreshProgressBar.setVisibility(View.VISIBLE);
                            } else {
                                refreshTextView.setText("下拉刷新列表");
                                refreshProgressBar.setVisibility(View.GONE);
                            }
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isPulling) {
                            isPulling = false;
                            touchStartY = 0;
                            if (refreshHeaderView.getPaddingTop() >= 0) {
                                startRefreshing();
                            } else {
                                hideRefreshHeader();
                            }
                        }
                        break;
                }
                return false;
            }
        });
    }

    private boolean isListViewAtTop() {
        if (listView.getChildCount() == 0) return true;
        if (listView.getFirstVisiblePosition() == 0) {
            View first = listView.getChildAt(0);
            return first != null && first.getTop() >= 0;
        }
        return false;
    }

    private void startRefreshing() {
        isRefreshing = true;
        refreshHeaderView.setPadding(0, 16, 0, 16);
        refreshProgressBar.setVisibility(View.VISIBLE);
        refreshTextView.setText("正在刷新中...");

        String title = tvListTitle.getText().toString();
        if (title.contains("我的收藏")) {
            fetchServerFavoriteSongs();
        } else if (title.contains("精选歌单")) {
            loadLocalPlaylists();
            fetchPlaylistSongs("local_featured", "精选歌单");
            stopRefreshing();
        } else if (title.contains("车载歌单")) {
            loadLocalPlaylists();
            fetchPlaylistSongs("local_car", "车载歌单");
            stopRefreshing();
        } else if (layoutSearchBar.getVisibility() == View.VISIBLE) {
            searchSongs(etSearchKeyword.getText().toString().trim());
        } else if (btnBack.getVisibility() == View.VISIBLE && currentActivePlaylistId != null) {
            fetchPlaylistSongs(currentActivePlaylistId, currentActivePlaylistName);
        } else {
            fetchPlaylists();
            syncServerFavoritesQuietly();
        }
    }

    private void stopRefreshing() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isRefreshing = false;
                isPulling = false;
                touchStartY = 0;
                hideRefreshHeader();
            }
        });
    }

    private void hideRefreshHeader() {
        if (refreshHeaderView != null) {
            refreshHeaderView.setPadding(0, -refreshHeaderHeight, 0, 0);
            refreshProgressBar.setVisibility(View.GONE);
            refreshTextView.setText("下拉刷新列表");
        }
    }

    private void setupVinylAnimation() {
        vinylRotateAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnim.setDuration(12000);
        vinylRotateAnim.setRepeatCount(Animation.INFINITE);
        vinylRotateAnim.setInterpolator(new LinearInterpolator());
    }

    private void updateVinylAnimationState() {
        if (flVinylDisc == null) return;
        if (isVinylDisplayMode && isCurrentSongPlaying) {
            if (flVinylDisc.getAnimation() == null) {
                flVinylDisc.startAnimation(vinylRotateAnim);
            }
        } else {
            flVinylDisc.clearAnimation();
        }
    }

    private void updateCoverDisplayMode() {
        if (isVinylDisplayMode) {
            layoutVinylContainer.setVisibility(View.VISIBLE);
            ivSquareCover.setVisibility(View.GONE);
            updateVinylAnimationState();
        } else {
            layoutVinylContainer.setVisibility(View.GONE);
            if (flVinylDisc != null) flVinylDisc.clearAnimation();
            ivSquareCover.setVisibility(View.VISIBLE);
        }
    }

    private void toggleCoverDisplayMode() {
        isVinylDisplayMode = !isVinylDisplayMode;
        prefs.edit().putBoolean("is_vinyl_display_mode", isVinylDisplayMode).commit();
        updateCoverDisplayMode();
        Toast.makeText(this, isVinylDisplayMode ? "已切换为黑胶唱机模式" : "已切换为方形封面模式", Toast.LENGTH_SHORT).show();
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

    private void loadLocalPlaylists() {
        featuredSongs.clear();
        carSongs.clear();
        try {
            String fStr = prefs.getString("local_playlist_featured", "[]");
            JSONArray fArr = new JSONArray(fStr);
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知歌手"), "", o.optString("coverArt", null), o.optString("quality", "标准音质"), true));
            }

            String cStr = prefs.getString("local_playlist_car", "[]");
            JSONArray cArr = new JSONArray(cStr);
            for (int i = 0; i < cArr.length(); i++) {
                JSONObject o = cArr.getJSONObject(i);
                carSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知歌手"), "", o.optString("coverArt", null), o.optString("quality", "标准音质"), true));
            }
        } catch (Exception ignored) {}
    }

    private void saveLocalPlaylists() {
        try {
            JSONArray fArr = new JSONArray();
            for (DisplayEntry e : featuredSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("title", e.title);
                o.put("artist", e.artist);
                o.put("coverArt", e.coverArt);
                o.put("quality", e.quality);
                fArr.put(o);
            }
            prefs.edit().putString("local_playlist_featured", fArr.toString()).commit();

            JSONArray cArr = new JSONArray();
            for (DisplayEntry e : carSongs) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("title", e.title);
                o.put("artist", e.artist);
                o.put("coverArt", e.coverArt);
                o.put("quality", e.quality);
                cArr.put(o);
            }
            prefs.edit().putString("local_playlist_car", cArr.toString()).commit();
        } catch (Exception ignored) {}
    }

    private void serverStarSong(final String songId, final boolean toStar) {
        if (songId == null || songId.length() == 0) return;

        if (toStar) {
            favSongIds.add(songId);
            Toast.makeText(this, "已添加至云端【我的收藏】♥", Toast.LENGTH_SHORT).show();
        } else {
            favSongIds.remove(songId);
            Toast.makeText(this, "已从【我的收藏】移出♡", Toast.LENGTH_SHORT).show();

            if (tvListTitle.getText().toString().contains("我的收藏")) {
                for (int i = 0; i < currentItems.size(); i++) {
                    if (songId.equals(currentItems.get(i).id)) {
                        currentItems.remove(i);
                        listData.remove(i);
                        adapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
        }
        saveFavSet();
        updateFavButtonState(songId);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String endpoint = toStar ? "star.view" : "unstar.view";
                    String res = requestApi(endpoint + "?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                    if (res != null && res.contains("\"status\":\"failed\"")) {
                        try {
                            JSONObject r = new JSONObject(res).getJSONObject("subsonic-response");
                            final String msg = r.getJSONObject("error").getString("message");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "云端收藏同步异常: " + msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception ignored) {}
                    }
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
                            public void run() { updateFavButtonState(null); }
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
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
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
            if (btnBack != null && btnBack.getVisibility() == View.VISIBLE) {
                btnBack.performClick();
                return true;
            }
            if (layoutSearchBar != null && layoutSearchBar.getVisibility() == View.VISIBLE) {
                btnTabPlaylists.performClick();
                return true;
            }
            return super.onKeyDown(keyCode, event);
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

        btnLyricDec = (Button) findViewById(R.id.btn_lyric_dec);
        btnLyricInc = (Button) findViewById(R.id.btn_lyric_inc);

        layoutVinylContainer = (FrameLayout) findViewById(R.id.layout_vinyl_container);
        flVinylDisc = (FrameLayout) findViewById(R.id.fl_vinyl_disc);
        ivVinylCircularCover = (ImageView) findViewById(R.id.iv_vinyl_circular_cover);
        viewTonearm = (TonearmView) findViewById(R.id.view_tonearm);
        ivSquareCover = (ImageView) findViewById(R.id.iv_square_cover);

        layoutCoverContainer = (LinearLayout) findViewById(R.id.layout_cover_container);
        layoutDetailSeekBox = (LinearLayout) findViewById(R.id.layout_detail_seek_box);
        layoutDetailControls = (LinearLayout) findViewById(R.id.layout_detail_controls);
        layoutDetailBottomBlank = (LinearLayout) findViewById(R.id.layout_detail_bottom_blank);

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

        // 核心修复点：必须先调用 setupPullToRefresh 完成 addHeaderView，严禁在 addHeaderView 前 setAdapter
        setupPullToRefresh();

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

    private void scrollQueueToCenter(final ListView lv) {
        final int idx = MusicService.getCurrentIndex();
        if (idx >= 0 && lv != null) {
            lv.post(new Runnable() {
                @Override
                public void run() {
                    int h = lv.getHeight();
                    lv.setSelectionFromTop(idx, Math.max(0, h / 2 - 35));
                }
            });
        }
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
            scrollQueueToCenter(lvDetailQueue);
        }
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

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
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.2.2; zh-cn) AppleWebKit/534.30");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.connect();

        int code = conn.getResponseCode();

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

    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        if (!entry.isSong) return;
        final boolean fav = isFav(entry.id);

        String[] options = new String[]{
                fav ? "★ 已收藏（点此从云端取消）" : "☆ 收藏歌曲 (同步云端)",
                "📁 添加到歌单 (精选/车载)",
                "🗑 移出当前列表",
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
                            removeFromCurrentView(position, entry);
                        } else if (which == 3) {
                            downloadSongItem(entry);
                        }
                    }
                })
                .show();
    }

    private void showAddToPlaylistDialog(final DisplayEntry entry) {
        final String[] targets = new String[]{"♥ 我的收藏 (云端)", "⭐ 精选歌单 (本地)", "🚗 车载歌单 (本地)"};
        new AlertDialog.Builder(this)
                .setTitle("选择要加入的歌单")
                .setItems(targets, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (!isFav(entry.id)) serverStarSong(entry.id, true);
                        } else if (which == 1) {
                            addSongToLocalList(featuredSongs, entry, "精选歌单");
                        } else if (which == 2) {
                            addSongToLocalList(carSongs, entry, "车载歌单");
                        }
                    }
                })
                .show();
    }

    private void addSongToLocalList(ArrayList<DisplayEntry> targetList, DisplayEntry song, String listName) {
        for (DisplayEntry e : targetList) {
            if (e.id.equals(song.id)) {
                Toast.makeText(this, "该歌曲已在【" + listName + "】中", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        targetList.add(song);
        saveLocalPlaylists();
        Toast.makeText(this, "已加入【" + listName + "】", Toast.LENGTH_SHORT).show();
    }

    private void removeFromCurrentView(int position, DisplayEntry entry) {
        if (tvListTitle.getText().toString().contains("我的收藏")) {
            serverStarSong(entry.id, false);
            return;
        }

        if ("精选歌单".equals(tvListTitle.getText().toString())) {
            for (int i = 0; i < featuredSongs.size(); i++) {
                if (featuredSongs.get(i).id.equals(entry.id)) {
                    featuredSongs.remove(i);
                    break;
                }
            }
            saveLocalPlaylists();
        } else if ("车载歌单".equals(tvListTitle.getText().toString())) {
            for (int i = 0; i < carSongs.size(); i++) {
                if (carSongs.get(i).id.equals(entry.id)) {
                    carSongs.remove(i);
                    break;
                }
            }
            saveLocalPlaylists();
        }

        if (position >= 0 && position < currentItems.size()) {
            currentItems.remove(position);
            listData.remove(position);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "已从当前列表移出", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyLyricFontSize(int delta) {
        lyricBaseFontSize += delta;
        if (lyricBaseFontSize < 11) lyricBaseFontSize = 11;
        if (lyricBaseFontSize > 26) lyricBaseFontSize = 26;

        prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).commit();

        for (int i = 0; i < lyricRows.size(); i++) {
            LyricRow row = lyricRows.get(i);
            if (row.view != null) {
                row.view.setTextSize(i == currentLyricIndex ? (lyricBaseFontSize + 3) : lyricBaseFontSize);
            }
        }
        Toast.makeText(this, "歌词字号: " + lyricBaseFontSize + "sp", Toast.LENGTH_SHORT).show();
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        Rect srcRect = new Rect((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2);
        Rect dstRect = new Rect(0, 0, size, size);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);

        return output;
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

        btnLyricDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { applyLyricFontSize(-2); }
        });

        btnLyricInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { applyLyricFontSize(2); }
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

        View.OnClickListener coverToggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCoverDisplayMode();
            }
        };
        layoutVinylContainer.setOnClickListener(coverToggleListener);
        ivVinylCircularCover.setOnClickListener(coverToggleListener);
        viewTonearm.setOnClickListener(coverToggleListener);
        ivSquareCover.setOnClickListener(coverToggleListener);

        layoutDetailBottomBlank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDetailQueueView();
            }
        });

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
                    scrollQueueToCenter(lvQueue);
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
                int pos = position - listView.getHeaderViewsCount();
                if (pos < 0 || pos >= currentItems.size()) return;

                DisplayEntry entry = currentItems.get(pos);
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
                int pos = position - listView.getHeaderViewsCount();
                if (pos >= 0 && pos < currentItems.size()) {
                    showSongLongClickMenu(currentItems.get(pos), pos);
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
        if (coverId == null || coverId.length() == 0) {
            ivVinylCircularCover.setImageResource(android.R.drawable.ic_menu_report_image);
            ivSquareCover.setImageResource(android.R.drawable.ic_menu_report_image);
            return;
        }
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
                        currentRawCoverBitmap = bitmap;
                        final Bitmap circularBitmap = getCircularBitmap(bitmap);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ivVinylCircularCover.setImageBitmap(circularBitmap);
                                ivSquareCover.setImageBitmap(currentRawCoverBitmap);
                            }
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
        tv.setTextSize(lyricBaseFontSize);
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
                tv.setTextSize(lyricBaseFontSize);
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
            tv.setTextSize(lyricBaseFontSize);
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
                    oldRow.view.setTextSize(lyricBaseFontSize);
                    oldRow.view.setTypeface(Typeface.DEFAULT);
                }
            }

            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow.view != null) {
                curRow.view.setTextColor(0xFF00E5FF);
                curRow.view.setTextSize(lyricBaseFontSize + 3);
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
        currentActivePlaylistId = null;
        currentActivePlaylistName = null;
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

                        currentItems.add(new DisplayEntry("fav_entry", "我的收藏", "云端同步", "已同步服务器标星 (" + favSongIds.size() + "首)", null, "云端歌单", false));
                        Map<String, String> favRow = new HashMap<String, String>();
                        favRow.put("title", "♥  我的收藏");
                        favRow.put("subtitle", "已同步服务器标星 (" + favSongIds.size() + "首)");
                        listData.add(favRow);

                        currentItems.add(new DisplayEntry("local_featured", "精选歌单", "本地定制", "本地定制精选 (" + featuredSongs.size() + "首)", null, "本地歌单", false));
                        Map<String, String> featRow = new HashMap<String, String>();
                        featRow.put("title", "⭐  精选歌单");
                        featRow.put("subtitle", "本地定制精选 (" + featuredSongs.size() + "首)");
                        listData.add(featRow);

                        currentItems.add(new DisplayEntry("local_car", "车载歌单", "本地定制", "出行必听车载曲库 (" + carSongs.size() + "首)", null, "本地歌单", false));
                        Map<String, String> carRow = new HashMap<String, String>();
                        carRow.put("title", "🚗  车载歌单");
                        carRow.put("subtitle", "出行必听车载曲库 (" + carSongs.size() + "首)");
                        listData.add(carRow);

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
                        stopRefreshing();
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

    private void fetchPlaylistSongs(final String playlistId, final String playlistName) {
        currentActivePlaylistId = playlistId;
        currentActivePlaylistName = playlistName;

        if ("fav_entry".equals(playlistId)) {
            fetchServerFavoriteSongs();
            return;
        }

        if ("local_featured".equals(playlistId)) {
            btnBack.setVisibility(View.VISIBLE);
            tvListTitle.setText("精选歌单");
            currentItems.clear();
            listData.clear();
            for (DisplayEntry e : featuredSongs) {
                currentItems.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + "  [" + e.quality + "]");
                listData.add(row);
            }
            adapter.notifyDataSetChanged();
            stopRefreshing();
            return;
        }

        if ("local_car".equals(playlistId)) {
            btnBack.setVisibility(View.VISIBLE);
            tvListTitle.setText("车载歌单");
            currentItems.clear();
            listData.clear();
            for (DisplayEntry e : carSongs) {
                currentItems.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + "  [" + e.quality + "]");
                listData.add(row);
            }
            adapter.notifyDataSetChanged();
            stopRefreshing();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylist.view?id=" + playlistId + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) {
                            stopRefreshing();
                            return;
                        }
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
                        } finally {
                            stopRefreshing();
                        }
                    }
                });
            }
        }).start();
    }

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
                            stopRefreshing();
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
                        } finally {
                            stopRefreshing();
                        }
                    }
                });
            }
        }).start();
    }

    private void searchSongs(final String query) {
        if (query.length() == 0) {
            stopRefreshing();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    final String jsonStr = requestApi("search3.view?query=" + encoded + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (jsonStr == null) {
                                stopRefreshing();
                                return;
                            }
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
                            } catch (Exception ignored) {
                            } finally {
                                stopRefreshing();
                            }
                        }
                    });
                } catch (Exception ignored) {
                    stopRefreshing();
                }
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
