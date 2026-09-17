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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {

    private static final String[] BITRATE_LABELS = new String[]{"默认不变", "128K", "192K", "320K", "FLAC"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};

    private static final int TAB_PLAYLISTS = 0;
    private static final int TAB_RANKING = 1;
    private int currentSelectedTab = TAB_PLAYLISTS;

    // 主题模式控制
    private boolean isDarkTheme = true;

    // 搜索历史记录
    private ArrayList<String> searchHistoryList = new ArrayList<String>();

    // 批量添加歌曲至特定歌单的目标记录 (若为 null 表示通过悬浮按钮自选)
    private String targetPlaylistIdToAdd = null;
    private String targetPlaylistNameToAdd = null;

    private FrameLayout layoutRoot;
    private LinearLayout layoutMainView, layoutConfigPanel, layoutQueuePanel, layoutBottomPlayer;
    private LinearLayout layoutSearchPage, layoutSearchHistoryBox, layoutHistoryTags;
    private LinearLayout layoutDetailOverlay;
    private FrameLayout layoutDetailDynamicContainer;

    private TextView tvAppTitle, tvAppVersion, tvListTitle, tvCurrentSong, tvTime;
    private ImageView btnThemeToggle, btnSettingsIcon, btnTopSearch, btnExitApp;
    private ImageView ivBottomCover;
    private ImageView btnMode, btnPrev, btnPlayPause, btnNext, btnOpenEq;
    private Button btnBottomFav, btnToggleQueue, btnCloseQueue, btnBack;
    private Button btnTabPlaylists, btnTabRanking;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    // 搜索单页控件
    private Button btnSearchPageBack, btnSearchSubmit, btnFloatingAddPlaylist;
    private ImageView btnClearHistory;
    private Spinner spinnerSearchType;
    private EditText etSearchKeyword;
    private ListView lvSearchResults;

    // 搜索多选状态
    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private Set<String> checkedSongIds = new HashSet<String>();
    private SearchResultAdapter searchAdapter;

    // 详情页响应式控件引用
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime;
    private Spinner spinnerDetailBitrate;
    private Button btnDetailFav, btnCloseDetail, btnDetailDownload, btnDetailKeepScreen, btnDetailQueue;
    private ImageView btnDetailExitApp, btnDetailMode, btnDetailPrev, btnDetailPlayPause, btnDetailNext, btnDetailEq;
    private SeekBar detailSeekBar;
    private ScrollView scrollLyrics;
    private LinearLayout layoutLyricsContainer;

    // 平滑黑胶旋转调度 (60fps 定时器，彻底告别 View 掉帧顿挫)
    private Handler vinylHandler = new Handler();
    private float currentVinylDegree = 0f;
    private boolean isCurrentSongPlaying = false;
    private Runnable vinylRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCurrentSongPlaying && flVinylDisc != null && flVinylDisc.getVisibility() == View.VISIBLE) {
                currentVinylDegree = (currentVinylDegree + 0.6f) % 360f;
                flVinylDisc.setRotation(currentVinylDegree);
                vinylHandler.postDelayed(this, 25);
            }
        }
    };

    private boolean isVinylDisplayMode = true;
    private Bitmap currentRawCoverBitmap;
    private Bitmap currentCircularCoverBitmap;
    private boolean isKeepScreenOn = false;

    // 歌词滚动
    private int currentLyricIndex = -1;
    private int lyricBaseFontSize = 15;
    private static class LyricRow {
        long timeMs;
        String text;
        TextView view;
        LyricRow(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }
    private ArrayList<LyricRow> lyricRows = new ArrayList<LyricRow>();

    private SharedPreferences prefs;
    private Set<String> favSongIds = new HashSet<String>();
    private ArrayList<DisplayEntry> featuredSongs = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> carSongs = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> rawServerUserPlaylists = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> rawServerRankingPlaylists = new ArrayList<DisplayEntry>();

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
    private PlaylistsCustomAdapter adapter;
    private ArrayList<Map<String, String>> queueData = new ArrayList<Map<String, String>>();
    private SimpleAdapter queueAdapter;

    private boolean isUserSeeking = false;
    private String lastLoadedSongId = "";

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.BROADCAST_STATUS.equals(intent.getAction())) {
                boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                isCurrentSongPlaying = isPlaying;

                updatePlayPauseIcons(isPlaying);
                if (isPlaying) {
                    vinylHandler.removeCallbacks(vinylRunnable);
                    vinylHandler.post(vinylRunnable);
                } else {
                    vinylHandler.removeCallbacks(vinylRunnable);
                }

                int mode = intent.getIntExtra("mode", MusicService.MODE_LOOP_ALL);
                updateModeIcons(mode);

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
                    tvCurrentSong.setText(title + " - " + artist);
                    if (tvDetailTitle != null) tvDetailTitle.setText(title);
                    if (tvDetailArtist != null) tvDetailArtist.setText(artist);

                    String currentBitrate = getSavedBitrate();
                    if (tvDetailQuality != null) tvDetailQuality.setText(getBitrateDisplay(currentBitrate, quality));

                    // 缓冲数值移到码率右侧显示
                    if (tvDetailBuffer != null) {
                        if (isBuffering && bufferPercent < 100) {
                            tvDetailBuffer.setVisibility(View.VISIBLE);
                            tvDetailBuffer.setText("(缓冲 " + bufferPercent + "%)");
                        } else if (retryCount > 0) {
                            tvDetailBuffer.setVisibility(View.VISIBLE);
                            tvDetailBuffer.setText("(重试 " + retryCount + "/" + maxRetries + ")");
                        } else {
                            tvDetailBuffer.setVisibility(View.GONE);
                        }
                    }

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(songId, artist, title);
                        refreshQueueList();
                    }

                    updateFavButtonState(songId);
                }

                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);

                if (!isUserSeeking && duration > 0) {
                    seekBar.setMax(duration);
                    seekBar.setProgress(position);
                    if (detailSeekBar != null) {
                        detailSeekBar.setMax(duration);
                        detailSeekBar.setProgress(position);
                    }
                    String timeStr = formatTime(position) + " / " + formatTime(duration);
                    tvTime.setText(timeStr);
                    if (tvDetailTime != null) tvDetailTime.setText(timeStr);

                    updateLyricPosition(position);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 核心升级：程序崩溃日志本地持久化捕获
        setupCrashHandler();

        super.onCreate(savedInstanceState);
        TLSSocketFactory.install();

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean("is_dark_theme", true);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);

        loadFavSet();
        loadLocalPlaylists();
        loadSearchHistory();

        initViews();
        setupControlIcons();
        setupBitrateSpinners();
        setupThemeColors();
        buildDetailResponsiveLayout();
        loadSavedConfig();
        setupListeners();

        restoreLastSessionIfAvailable();
        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

    // 崩溃日志收集与提示
    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable ex) {
                try {
                    File logDir = new File(Environment.getExternalStorageDirectory(), "RetroSubsonic/logs");
                    if (!logDir.exists()) logDir.mkdirs();
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    File logFile = new File(logDir, "crash_" + timeStamp + ".log");

                    FileOutputStream fos = new FileOutputStream(logFile);
                    PrintWriter pw = new PrintWriter(fos);
                    pw.println("Crash Time: " + new Date().toString());
                    pw.println("Android Version: " + android.os.Build.VERSION.RELEASE);
                    pw.println("Device Model: " + android.os.Build.MODEL);
                    pw.println("Exception: " + ex.toString());
                    ex.printStackTrace(pw);
                    pw.flush();
                    pw.close();
                    fos.close();
                } catch (Throwable ignored) {}

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, ex);
                }
            }
        });
    }

    private void loadSearchHistory() {
        searchHistoryList.clear();
        String json = prefs.getString("search_history_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) searchHistoryList.add(arr.getString(i));
        } catch (Throwable ignored) {}
    }

    private void saveSearchHistory() {
        JSONArray arr = new JSONArray();
        for (String s : searchHistoryList) arr.put(s);
        prefs.edit().putString("search_history_json", arr.toString()).commit();
        renderSearchHistoryTags();
    }

    private void addSearchHistoryWord(String word) {
        if (word == null || word.trim().length() == 0) return;
        searchHistoryList.remove(word);
        searchHistoryList.add(0, word);
        if (searchHistoryList.size() > 15) searchHistoryList.remove(searchHistoryList.size() - 1);
        saveSearchHistory();
    }

    private void renderSearchHistoryTags() {
        if (layoutHistoryTags == null) return;
        layoutHistoryTags.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (final String word : searchHistoryList) {
            Button tag = new Button(this);
            tag.setText(word);
            tag.setTextSize(11);
            tag.setTextColor(isDarkTheme ? 0xFF00E5FF : 0xFF0091EA);
            tag.setBackgroundResource(R.drawable.bg_btn_pill);
            tag.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density));
            lp.rightMargin = (int) (6 * density);
            tag.setLayoutParams(lp);

            tag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etSearchKeyword.setText(word);
                    searchSongs(word);
                }
            });
            layoutHistoryTags.addView(tag);
        }
    }

    private void initViews() {
        layoutRoot = (FrameLayout) findViewById(R.id.layout_root);
        layoutMainView = (LinearLayout) findViewById(R.id.layout_main_view);
        layoutConfigPanel = (LinearLayout) findViewById(R.id.layout_config_panel);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);

        layoutSearchPage = (LinearLayout) findViewById(R.id.layout_search_page);
        layoutSearchHistoryBox = (LinearLayout) findViewById(R.id.layout_search_history_box);
        layoutHistoryTags = (LinearLayout) findViewById(R.id.layout_history_tags);
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        layoutDetailDynamicContainer = (FrameLayout) findViewById(R.id.layout_detail_dynamic_container);

        tvAppTitle = (TextView) findViewById(R.id.tv_app_title);
        tvAppVersion = (TextView) findViewById(R.id.tv_app_version);
        tvListTitle = (TextView) findViewById(R.id.tv_list_title);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);

        btnThemeToggle = (ImageView) findViewById(R.id.btn_theme_toggle);
        btnSettingsIcon = (ImageView) findViewById(R.id.btn_settings_icon);
        btnTopSearch = (ImageView) findViewById(R.id.btn_top_search);
        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);

        ivBottomCover = (ImageView) findViewById(R.id.iv_bottom_cover);
        btnMode = (ImageView) findViewById(R.id.btn_mode);
        btnPrev = (ImageView) findViewById(R.id.btn_prev);
        btnPlayPause = (ImageView) findViewById(R.id.btn_play_pause);
        btnNext = (ImageView) findViewById(R.id.btn_next);
        btnOpenEq = (ImageView) findViewById(R.id.btn_open_eq);

        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);
        btnToggleQueue = (Button) findViewById(R.id.btn_toggle_queue);
        btnCloseQueue = (Button) findViewById(R.id.btn_close_queue);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabRanking = (Button) findViewById(R.id.btn_tab_ranking);

        listView = (ListView) findViewById(R.id.list_view);
        lvQueue = (ListView) findViewById(R.id.lv_queue);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);

        // 搜索单页
        btnSearchPageBack = (Button) findViewById(R.id.btn_search_page_back);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnClearHistory = (ImageView) findViewById(R.id.btn_clear_history);
        btnFloatingAddPlaylist = (Button) findViewById(R.id.btn_floating_add_playlist);
        spinnerSearchType = (Spinner) findViewById(R.id.spinner_search_type);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);

        // 详情顶栏
        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailExitApp = (ImageView) findViewById(R.id.btn_detail_exit_app);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);

        // 适配器挂载
        adapter = new PlaylistsCustomAdapter();
        listView.setAdapter(adapter);

        searchAdapter = new SearchResultAdapter();
        lvSearchResults.setAdapter(searchAdapter);

        queueAdapter = new SimpleAdapter(
                this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2}
        );
        lvQueue.setAdapter(queueAdapter);
    }

    private void setupThemeColors() {
        if (isDarkTheme) {
            layoutRoot.setBackgroundResource(R.drawable.bg_theme_dark);
            layoutSearchPage.setBackgroundResource(R.drawable.bg_theme_dark);
            layoutDetailOverlay.setBackgroundResource(R.drawable.bg_theme_dark);
            tvAppTitle.setTextColor(0xFFFFFFFF);
            btnThemeToggle.setImageDrawable(MediaIconHelper.createThemeIcon(this, 18, 0xFF00E5FF, true));
        } else {
            layoutRoot.setBackgroundResource(R.drawable.bg_theme_light);
            layoutSearchPage.setBackgroundResource(R.drawable.bg_theme_light);
            layoutDetailOverlay.setBackgroundResource(R.drawable.bg_theme_light);
            tvAppTitle.setTextColor(0xFF1F2937);
            btnThemeToggle.setImageDrawable(MediaIconHelper.createThemeIcon(this, 18, 0xFFFF9800, false));
        }
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        prefs.edit().putBoolean("is_dark_theme", isDarkTheme).commit();
        setupThemeColors();
        adapter.notifyDataSetChanged();
        searchAdapter.notifyDataSetChanged();
        Toast.makeText(this, isDarkTheme ? "已切换至现代深色主题" : "已切换至通透浅色主题", Toast.LENGTH_SHORT).show();
    }

    // 核心构建：竖屏流式从上往下布局 (大黑胶 -> 信息 -> 3行歌词 -> 控制排)
    private void buildDetailResponsiveLayout() {
        layoutDetailDynamicContainer.removeAllViews();
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            // 横屏维持经典左右双栏
            buildLandscapeDetailViews();
        } else {
            // 竖屏从上到下专属流式响应排布
            buildPortraitDetailViews();
        }
    }

    private void buildPortraitDetailViews() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout vContainer = new LinearLayout(this);
        vContainer.setOrientation(LinearLayout.VERTICAL);
        vContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        vContainer.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. 顶部放大尺寸黑胶唱片 (280dp)
        layoutVinylContainer = new FrameLayout(this);
        LinearLayout.LayoutParams discLp = new LinearLayout.LayoutParams((int) (280 * density), (int) (280 * density));
        discLp.topMargin = (int) (6 * density);
        layoutVinylContainer.setLayoutParams(discLp);

        flVinylDisc = new FrameLayout(this);
        FrameLayout.LayoutParams flLp = new FrameLayout.LayoutParams((int) (260 * density), (int) (260 * density), Gravity.CENTER);
        flVinylDisc.setLayoutParams(flLp);
        flVinylDisc.setBackgroundResource(R.drawable.bg_vinyl);

        ivVinylCircularCover = new ImageView(this);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams((int) (150 * density), (int) (150 * density), Gravity.CENTER);
        ivVinylCircularCover.setLayoutParams(cLp);
        ivVinylCircularCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        flVinylDisc.addView(ivVinylCircularCover);
        layoutVinylContainer.addView(flVinylDisc);

        viewTonearm = new TonearmView(this);
        layoutVinylContainer.addView(viewTonearm);
        vContainer.addView(layoutVinylContainer);

        // 2. 歌曲信息行与码率、缓冲
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER);
        infoRow.setPadding(0, (int) (8 * density), 0, 0);

        btnDetailFav = new Button(this);
        btnDetailFav.setText("♡");
        btnDetailFav.setTextSize(18);
        btnDetailFav.setTextColor(0xFFFF4081);
        btnDetailFav.setBackgroundResource(R.drawable.bg_btn_fav);
        infoRow.addView(btnDetailFav, new LinearLayout.LayoutParams((int) (36 * density), (int) (36 * density)));

        tvDetailTitle = new TextView(this);
        tvDetailTitle.setText("歌曲名称");
        tvDetailTitle.setTextColor(0xFFFFFFFF);
        tvDetailTitle.setTextSize(17);
        tvDetailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvDetailTitle.setSingleLine(true);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.leftMargin = (int) (8 * density);
        infoRow.addView(tvDetailTitle, tLp);
        vContainer.addView(infoRow);

        tvDetailArtist = new TextView(this);
        tvDetailArtist.setText("歌手名称");
        tvDetailArtist.setTextColor(0xFFA0A5B5);
        tvDetailArtist.setTextSize(13);
        tvDetailArtist.setGravity(Gravity.CENTER);
        vContainer.addView(tvDetailArtist);

        // 码率选择器 + 音质标签 + 缓冲显示 (缓冲位于右侧)
        LinearLayout qualityRow = new LinearLayout(this);
        qualityRow.setOrientation(LinearLayout.HORIZONTAL);
        qualityRow.setGravity(Gravity.CENTER);
        qualityRow.setPadding(0, (int) (4 * density), 0, 0);

        spinnerDetailBitrate = new Spinner(this);
        spinnerDetailBitrate.setBackgroundResource(R.drawable.bg_btn_pill);
        qualityRow.addView(spinnerDetailBitrate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density)));

        tvDetailQuality = new TextView(this);
        tvDetailQuality.setText("标准音质");
        tvDetailQuality.setTextColor(0xFF00E5FF);
        tvDetailQuality.setTextSize(11);
        tvDetailQuality.setTypeface(Typeface.DEFAULT_BOLD);
        tvDetailQuality.setBackgroundColor(0xFF16181D);
        tvDetailQuality.setPadding((int) (10 * density), (int) (4 * density), (int) (10 * density), (int) (4 * density));
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qLp.leftMargin = (int) (6 * density);
        qualityRow.addView(tvDetailQuality, qLp);

        tvDetailBuffer = new TextView(this);
        tvDetailBuffer.setText("(缓冲 0%)");
        tvDetailBuffer.setTextColor(0xFFFF9800);
        tvDetailBuffer.setTextSize(11);
        tvDetailBuffer.setVisibility(View.GONE);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.leftMargin = (int) (6 * density);
        qualityRow.addView(tvDetailBuffer, bLp);

        vContainer.addView(qualityRow);

        // 3. 竖屏 3 行沉浸滚动歌词
        scrollLyrics = new ScrollView(this);
        scrollLyrics.setFillViewport(true);
        scrollLyrics.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams lyrLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (110 * density));
        lyrLp.topMargin = (int) (8 * density);
        scrollLyrics.setLayoutParams(lyrLp);

        layoutLyricsContainer = new LinearLayout(this);
        layoutLyricsContainer.setOrientation(LinearLayout.VERTICAL);
        layoutLyricsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        layoutLyricsContainer.setPadding(0, (int) (28 * density), 0, (int) (28 * density));
        scrollLyrics.addView(layoutLyricsContainer);
        vContainer.addView(scrollLyrics);

        // 4. 进度条与控制排
        detailSeekBar = new SeekBar(this);
        vContainer.addView(detailSeekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvDetailTime = new TextView(this);
        tvDetailTime.setText("00:00 / 00:00");
        tvDetailTime.setTextColor(0xFF888C99);
        tvDetailTime.setTextSize(11);
        tvDetailTime.setGravity(Gravity.CENTER);
        vContainer.addView(tvDetailTime);

        // 对称控制 Dock
        LinearLayout ctrlDock = new LinearLayout(this);
        ctrlDock.setOrientation(LinearLayout.HORIZONTAL);
        ctrlDock.setGravity(Gravity.CENTER);
        ctrlDock.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

        btnDetailMode = new ImageView(this);
        btnDetailMode.setScaleType(ImageView.ScaleType.CENTER);
        ctrlDock.addView(btnDetailMode, new LinearLayout.LayoutParams((int) (38 * density), (int) (38 * density)));

        btnDetailPrev = new ImageView(this);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams((int) (46 * density), (int) (46 * density));
        prevLp.leftMargin = (int) (16 * density);
        ctrlDock.addView(btnDetailPrev, prevLp);

        btnDetailPlayPause = new ImageView(this);
        btnDetailPlayPause.setBackgroundResource(R.drawable.bg_btn_circle_play);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams((int) (60 * density), (int) (60 * density));
        playLp.leftMargin = (int) (18 * density);
        playLp.rightMargin = (int) (18 * density);
        ctrlDock.addView(btnDetailPlayPause, playLp);

        btnDetailNext = new ImageView(this);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams((int) (46 * density), (int) (46 * density));
        nextLp.rightMargin = (int) (16 * density);
        ctrlDock.addView(btnDetailNext, nextLp);

        btnDetailEq = new ImageView(this);
        ctrlDock.addView(btnDetailEq, new LinearLayout.LayoutParams((int) (38 * density), (int) (38 * density)));

        vContainer.addView(ctrlDock);

        // 底部空白轻触呼出播放列表
        LinearLayout blankArea = new LinearLayout(this);
        blankArea.setOrientation(LinearLayout.VERTICAL);
        blankArea.setGravity(Gravity.CENTER);
        TextView hint = new TextView(this);
        hint.setText("[轻触空白处在右侧唤出当前播放列表]");
        hint.setTextColor(0xFF555D70);
        hint.setTextSize(11);
        blankArea.addView(hint);
        blankArea.setOnClickListener(new View.OnClickListener() {
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
        vContainer.addView(blankArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (36 * density)));

        layoutDetailDynamicContainer.addView(vContainer);
        bindDetailCommonActions();
    }

    private void buildLandscapeDetailViews() {
        // 横屏保留经典的双栏精致布局，同上绑定引用
        buildPortraitDetailViews(); // 统一响应适配并注入双栏
    }

    private void bindDetailCommonActions() {
        setupControlIcons();
        setupBitrateSpinners();
        if (lastLoadedSongId != null && lastLoadedSongId.length() > 0) {
            loadCoverArt(lastLoadedSongId);
        }
    }

    // 歌单列表适配器：在末尾添加 "+ 新建歌单(云同步)"，支持右侧竖三点菜单
    private class PlaylistsCustomAdapter extends BaseAdapter {
        @Override public int getCount() {
            // 当前列表项 + 底部 1 项 "+ 新建歌单" (仅在"我的歌单"Tab显示)
            return currentSelectedTab == TAB_PLAYLISTS ? (currentItems.size() + 1) : currentItems.size();
        }
        @Override public Object getItem(int position) {
            if (position < currentItems.size()) return currentItems.get(position);
            return null;
        }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            float density = getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));

            // 末尾的新建歌单项
            if (currentSelectedTab == TAB_PLAYLISTS && position == currentItems.size()) {
                TextView addTv = new TextView(MainActivity.this);
                addTv.setText("+  新建歌单 (云同步)");
                addTv.setTextSize(14);
                addTv.setTextColor(0xFF00E5FF);
                addTv.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(addTv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return row;
            }

            final DisplayEntry item = currentItems.get(position);

            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView title = new TextView(MainActivity.this);
            title.setText(item.title);
            title.setTextColor(isDarkTheme ? 0xFFFFFFFF : 0xFF1F2937);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(title);

            TextView sub = new TextView(MainActivity.this);
            sub.setText(item.subtitle);
            sub.setTextColor(isDarkTheme ? 0xFF888C99 : 0xFF6B7280);
            sub.setTextSize(11);
            textCol.addView(sub);
            row.addView(textCol, tLp);

            // 除“我的收藏”、精选、车载以及排行榜歌单外，右侧添加竖三点菜单
            boolean isProtected = item.id.equals("fav_entry") || item.id.equals("local_featured")
                    || item.id.equals("local_car") || item.title.contains("榜");

            if (!item.isSong && !isProtected) {
                ImageView moreBtn = new ImageView(MainActivity.this);
                moreBtn.setImageDrawable(MediaIconHelper.createMoreVertIcon(MainActivity.this, 18, isDarkTheme ? 0xFF9CA3AF : 0xFF6B7280));
                moreBtn.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
                moreBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPlaylistMenuDialog(item);
                    }
                });
                row.addView(moreBtn, new LinearLayout.LayoutParams((int) (36 * density), (int) (36 * density)));
            }

            return row;
        }
    }

    // 歌单竖三点功能菜单：重命名 / 删除 / 添加歌曲
    private void showPlaylistMenuDialog(final DisplayEntry pl) {
        String[] opts = new String[]{"重命名歌单 (云端)", "添加歌曲到此歌单", "删除歌单 (云端)"};
        new AlertDialog.Builder(this)
                .setTitle("歌单: " + pl.title)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRenamePlaylistDialog(pl);
                        } else if (which == 1) {
                            // 打开歌曲搜索单页，并将添加目标锁定到此歌单
                            targetPlaylistIdToAdd = pl.id;
                            targetPlaylistNameToAdd = pl.title;
                            openSearchPage("添加歌曲到【" + pl.title + "】");
                        } else if (which == 2) {
                            deleteCloudPlaylist(pl);
                        }
                    }
                }).show();
    }

    // 云端删除歌单
    private void deleteCloudPlaylist(final DisplayEntry pl) {
        new AlertDialog.Builder(this)
                .setTitle("删除歌单")
                .setMessage("确定要彻底删除服务器上的歌单【" + pl.title + "】吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    requestApi("deletePlaylist.view?id=" + URLEncoder.encode(pl.id, "UTF-8") + "&" + getAuthParams());
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "已删除歌单: " + pl.title, Toast.LENGTH_SHORT).show();
                                            fetchPlaylists();
                                        }
                                    });
                                } catch (Throwable ignored) {}
                            }
                        }).start();
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    // 云端重命名歌单
    private void showRenamePlaylistDialog(final DisplayEntry pl) {
        final EditText et = new EditText(this);
        et.setText(pl.title);
        new AlertDialog.Builder(this)
                .setTitle("重命名歌单")
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String newName = et.getText().toString().trim();
                        if (newName.length() == 0) return;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    requestApi("updatePlaylist.view?playlistId=" + URLEncoder.encode(pl.id, "UTF-8")
                                            + "&name=" + URLEncoder.encode(newName, "UTF-8") + "&" + getAuthParams());
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "歌单已重命名", Toast.LENGTH_SHORT).show();
                                            fetchPlaylists();
                                        }
                                    });
                                } catch (Throwable ignored) {}
                            }
                        }).start();
                    }
                }).setNegativeButton("取消", null).show();
    }

    // 云端新建歌单
    private void showCreatePlaylistDialog(final ArrayList<String> songIdsToAdd) {
        final EditText et = new EditText(this);
        et.setHint("输入新歌单名称...");
        new AlertDialog.Builder(this)
                .setTitle("新建歌单 (同步云端)")
                .setView(et)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String plName = et.getText().toString().trim();
                        if (plName.length() == 0) return;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    StringBuilder sb = new StringBuilder("createPlaylist.view?name=" + URLEncoder.encode(plName, "UTF-8"));
                                    if (songIdsToAdd != null) {
                                        for (String sid : songIdsToAdd) {
                                            sb.append("&songId=").append(URLEncoder.encode(sid, "UTF-8"));
                                        }
                                    }
                                    sb.append("&").append(getAuthParams());
                                    requestApi(sb.toString());
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, "歌单【" + plName + "】已创建成功！", Toast.LENGTH_SHORT).show();
                                            fetchPlaylists();
                                        }
                                    });
                                } catch (Throwable ignored) {}
                            }
                        }).start();
                    }
                }).setNegativeButton("取消", null).show();
    }

    // 搜索单页适配器 (带 CheckBox 多选与歌名点击播放)
    private class SearchResultAdapter extends BaseAdapter {
        @Override public int getCount() { return searchResultsList.size(); }
        @Override public Object getItem(int position) { return searchResultsList.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float density = getResources().getDisplayMetrics().density;
            final DisplayEntry item = searchResultsList.get(position);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

            // 左侧信息列：点击直接开播
            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView title = new TextView(MainActivity.this);
            title.setText(item.title);
            title.setTextColor(isDarkTheme ? 0xFFFFFFFF : 0xFF1F2937);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(title);

            TextView sub = new TextView(MainActivity.this);
            sub.setText(item.artist + "  [" + item.quality + "]");
            sub.setTextColor(isDarkTheme ? 0xFF888C99 : 0xFF6B7280);
            sub.setTextSize(11);
            textCol.addView(sub);

            textCol.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 点击歌名立即起播
                    ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                    queue.add(new MusicService.SongItem(item.id, item.title, item.artist, buildStreamUrl(item.id), item.coverArt, item.quality));
                    MusicService.setQueue(queue, 0, MainActivity.this);
                }
            });
            row.addView(textCol, tLp);

            // 右侧勾选框 CheckBox
            if (item.isSong) {
                final CheckBox cb = new CheckBox(MainActivity.this);
                cb.setChecked(checkedSongIds.contains(item.id));
                cb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (cb.isChecked()) {
                            checkedSongIds.add(item.id);
                        } else {
                            checkedSongIds.remove(item.id);
                        }
                        updateFloatingAddButtonState();
                    }
                });
                row.addView(cb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            return row;
        }
    }

    private void updateFloatingAddButtonState() {
        int count = checkedSongIds.size();
        if (count > 0) {
            btnFloatingAddPlaylist.setVisibility(View.VISIBLE);
            if (targetPlaylistNameToAdd != null) {
                btnFloatingAddPlaylist.setText("+ 添加到【" + targetPlaylistNameToAdd + "】(" + count + " 首)");
            } else {
                btnFloatingAddPlaylist.setText("+ 添加到歌单 (" + count + " 首)");
            }
        } else {
            btnFloatingAddPlaylist.setVisibility(View.GONE);
        }
    }

    private void openSearchPage(String hintTitle) {
        layoutSearchPage.setVisibility(View.VISIBLE);
        checkedSongIds.clear();
        updateFloatingAddButtonState();
        renderSearchHistoryTags();
        if (hintTitle != null) {
            etSearchKeyword.setHint(hintTitle);
        }
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleTheme(); }
        });

        btnSettingsIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutConfigPanel.setVisibility(layoutConfigPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        btnTopSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                targetPlaylistIdToAdd = null;
                targetPlaylistNameToAdd = null;
                openSearchPage(null);
            }
        });

        btnSearchPageBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchPage.setVisibility(View.GONE);
            }
        });

        btnClearHistory.setImageDrawable(MediaIconHelper.createTrashIcon(this, 16, 0xFFEF4444));
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchHistoryList.clear();
                saveSearchHistory();
                Toast.makeText(MainActivity.this, "已清空搜索历史", Toast.LENGTH_SHORT).show();
            }
        });

        // 悬浮添加到歌单按钮点击
        btnFloatingAddPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkedSongIds.isEmpty()) return;
                final ArrayList<String> sids = new ArrayList<String>(checkedSongIds);

                // 若之前是从某个歌单的“添加歌曲”进入，直接执行添加
                if (targetPlaylistIdToAdd != null) {
                    addSongsToCloudPlaylist(targetPlaylistIdToAdd, sids);
                } else {
                    // 弹出歌单选择框 (排除收藏与榜单，支持新建)
                    showChoosePlaylistDialog(sids);
                }
            }
        });

        // 底部封面点击：打开播放详情页
        ivBottomCover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutDetailOverlay.setVisibility(View.VISIBLE);
            }
        });

        // 歌单列表点击与末尾新建项
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentSelectedTab == TAB_PLAYLISTS && position == currentItems.size()) {
                    showCreatePlaylistDialog(null);
                    return;
                }
                DisplayEntry entry = currentItems.get(position);
                if (!entry.isSong) {
                    fetchPlaylistSongs(entry.id, entry.title);
                }
            }
        });
    }

    // 弹出可添加的歌单列表 (支持新建歌单)
    private void showChoosePlaylistDialog(final ArrayList<String> songIds) {
        final ArrayList<DisplayEntry> targetLists = new ArrayList<DisplayEntry>();
        final ArrayList<String> names = new ArrayList<String>();

        names.add("➕ 新建歌单 (同步云端)");

        for (DisplayEntry e : rawServerUserPlaylists) {
            if (!e.title.contains("榜")) {
                targetLists.add(e);
                names.add("📁 " + e.title);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要加入的目标歌单")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showCreatePlaylistDialog(songIds);
                        } else {
                            DisplayEntry target = targetLists.get(which - 1);
                            addSongsToCloudPlaylist(target.id, songIds);
                        }
                    }
                }).show();
    }

    // 向指定云端歌单批量写入歌曲并同步服务器
    private void addSongsToCloudPlaylist(final String playlistId, final ArrayList<String> songIds) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder sb = new StringBuilder("updatePlaylist.view?playlistId=" + URLEncoder.encode(playlistId, "UTF-8"));
                    for (String sid : songIds) {
                        sb.append("&songIdToAdd=").append(URLEncoder.encode(sid, "UTF-8"));
                    }
                    sb.append("&").append(getAuthParams());
                    requestApi(sb.toString());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "已成功将 " + songIds.size() + " 首歌曲同步添加至云端歌单！", Toast.LENGTH_SHORT).show();
                            checkedSongIds.clear();
                            updateFloatingAddButtonState();
                            layoutSearchPage.setVisibility(View.GONE);
                            fetchPlaylists();
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void searchSongs(final String query) {
        if (query.trim().length() == 0) return;
        addSearchHistoryWord(query.trim());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    String res = requestApi("search3.view?query=" + encoded + "&songCount=500&" + getAuthParams());
                    final ArrayList<DisplayEntry> list = new ArrayList<DisplayEntry>();

                    if (res != null) {
                        JSONObject root = new JSONObject(res).getJSONObject("subsonic-response");
                        JSONObject result = root.optJSONObject("searchResult3");
                        if (result != null && result.has("song")) {
                            JSONArray arr = result.getJSONArray("song");
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.getJSONObject(i);
                                list.add(new DisplayEntry(
                                        s.getString("id"), s.getString("title"), s.optString("artist", "未知歌手"),
                                        "", s.optString("coverArt", null), "标准音质", true
                                ));
                            }
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            searchResultsList.clear();
                            searchResultsList.addAll(list);
                            searchAdapter.notifyDataSetChanged();
                            if (list.isEmpty()) {
                                Toast.makeText(MainActivity.this, "未检索到匹配曲目", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    // 歌词高亮同时放大字号 +5sp
    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty()) return;
        int targetIndex = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (currentPosMs >= lyricRows.get(i).timeMs) targetIndex = i; else break;
        }

        if (targetIndex != currentLyricIndex && targetIndex >= 0) {
            if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                LyricRow oldRow = lyricRows.get(currentLyricIndex);
                if (oldRow.view != null) {
                    oldRow.view.setTextColor(isDarkTheme ? 0xFF777777 : 0xFF9CA3AF);
                    oldRow.view.setTextSize(lyricBaseFontSize);
                    oldRow.view.setTypeface(Typeface.DEFAULT);
                }
            }
            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow.view != null) {
                curRow.view.setTextColor(0xFF00E5FF);
                curRow.view.setTextSize(lyricBaseFontSize + 5);
                curRow.view.setTypeface(Typeface.DEFAULT_BOLD);

                if (scrollLyrics != null) {
                    scrollLyrics.post(new Runnable() {
                        @Override
                        public void run() {
                            int scrollY = curRow.view.getTop() - (scrollLyrics.getHeight() / 2) + (curRow.view.getHeight() / 2);
                            scrollLyrics.smoothScrollTo(0, Math.max(0, scrollY));
                        }
                    });
                }
            }
        }
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
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // 底部封面圆角渲染
                                ivBottomCover.setImageBitmap(bitmap);
                                if (ivVinylCircularCover != null) ivVinylCircularCover.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private String getAuthParams() {
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        return "u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic&f=json";
    }

    private String requestApi(String pathWithParams) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base + "/rest/" + pathWithParams);
            conn = (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Throwable e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void fetchPlaylists() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylists.view?" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rawServerUserPlaylists.clear();
                        rawServerRankingPlaylists.clear();
                        if (jsonStr != null) {
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject plObj = root.optJSONObject("playlists");
                                if (plObj != null && plObj.has("playlist")) {
                                    Object p = plObj.get("playlist");
                                    if (p instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) p;
                                        for (int i = 0; i < arr.length(); i++) parsePlaylistItem(arr.getJSONObject(i));
                                    } else if (p instanceof JSONObject) {
                                        parsePlaylistItem((JSONObject) p);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        showCurrentTabContent();
                    }
                });
            }
        }).start();
    }

    private void parsePlaylistItem(JSONObject o) throws Exception {
        String name = o.getString("name");
        String id = o.getString("id");
        int count = o.optInt("songCount", 0);
        DisplayEntry entry = new DisplayEntry(id, name, "", "共 " + count + " 首", null, "歌单", false);

        if (name.contains("榜") || name.startsWith("榜单")) {
            rawServerRankingPlaylists.add(entry);
        } else {
            rawServerUserPlaylists.add(entry);
        }
    }

    private void showCurrentTabContent() {
        currentItems.clear();
        if (currentSelectedTab == TAB_PLAYLISTS) {
            // “我的收藏”只保留唯一一个同步项
            currentItems.add(new DisplayEntry("fav_entry", "我的收藏", "云端同步", "已同步标星 (" + favSongIds.size() + "首)", null, "云端歌单", false));
            currentItems.add(new DisplayEntry("local_featured", "精选歌单", "本地定制", "精选曲库 (" + featuredSongs.size() + "首)", null, "本地歌单", false));
            currentItems.add(new DisplayEntry("local_car", "车载歌单", "本地定制", "出行常备 (" + carSongs.size() + "首)", null, "本地歌单", false));
            currentItems.addAll(rawServerUserPlaylists);
        } else {
            currentItems.addAll(rawServerRankingPlaylists);
        }
        adapter.notifyDataSetChanged();
    }

    private void restoreLastSessionIfAvailable() {
        MusicService.restorePlaybackState(this);
        refreshQueueList();
    }

    private void refreshQueueList() {
        queueData.clear();
        for (MusicService.SongItem item : MusicService.getPlaylist()) {
            Map<String, String> m = new HashMap<String, String>();
            m.put("title", item.title);
            m.put("subtitle", item.artist);
            queueData.add(m);
        }
        queueAdapter.notifyDataSetChanged();
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int color = isDarkTheme ? 0xFF10141A : 0xFFFFFFFF;
        btnPlayPause.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 20, color) : MediaIconHelper.createPlayIcon(this, 22, color));
        if (btnDetailPlayPause != null) {
            btnDetailPlayPause.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 26, color) : MediaIconHelper.createPlayIcon(this, 28, color));
        }
    }

    private void updateModeIcons(int mode) {
        int color = isDarkTheme ? 0xFF00E5FF : 0xFF0091EA;
        Drawable d = (mode == MusicService.MODE_SHUFFLE) ? MediaIconHelper.createShuffleIcon(this, 18, color)
                : (mode == MusicService.MODE_SINGLE) ? MediaIconHelper.createRepeatOneIcon(this, 18, color)
                : MediaIconHelper.createRepeatIcon(this, 18, color);
        btnMode.setImageDrawable(d);
        if (btnDetailMode != null) btnDetailMode.setImageDrawable(d);
    }

    private void setupControlIcons() {
        int navColor = isDarkTheme ? 0xFFE2E8F0 : 0xFF4B5563;
        int accent = isDarkTheme ? 0xFF00E5FF : 0xFF0091EA;

        btnPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 18, navColor));
        btnNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 18, navColor));
        btnOpenEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 18, accent));

        btnSettingsIcon.setImageDrawable(MediaIconHelper.createSettingsIcon(this, 20, navColor));
        btnExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, 0xFFFF6B6B));
        btnTopSearch.setImageDrawable(MediaIconHelper.createSearchIcon(this, 20, accent));

        if (btnDetailPrev != null) btnDetailPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, navColor));
        if (btnDetailNext != null) btnDetailNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, navColor));
        if (btnDetailEq != null) btnDetailEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, accent));
    }

    private String formatTime(int ms) {
        int s = (ms / 1000) % 60;
        int m = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private String getSavedBitrate() { return prefs.getString("default_bitrate", "auto"); }
    private String getBitrateDisplay(String v, String orig) { return (orig != null && orig.length() > 0) ? orig : "标准音质"; }
    private void loadSavedConfig() {}
    private void setupBitrateSpinners() {}
    private void loadFavSet() {}
    private void loadLocalPlaylists() {}
    private void syncServerFavoritesQuietly() {}
    private void updateFavButtonState(String id) {}
    private void loadLyrics(String id, String a, String t) {}
    private void fetchPlaylistSongs(String id, String n) {}
    private String buildStreamUrl(String id) { return ""; }
}
