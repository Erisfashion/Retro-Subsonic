package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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

    private boolean isDarkTheme = true;
    private ArrayList<String> searchHistoryList = new ArrayList<String>();
    private String targetPlaylistIdToAdd = null;
    private String targetPlaylistNameToAdd = null;

    private FrameLayout layoutRoot;
    private LinearLayout layoutMainView, layoutConfigPanel, layoutQueuePanel, layoutBottomPlayer;
    private LinearLayout layoutSearchPage, layoutSearchHistoryBox, layoutHistoryTags;
    private LinearLayout layoutDetailOverlay;
    private LinearLayout layoutDetailLandscape, layoutDetailPortrait;

    // 详情页横屏控件
    private FrameLayout flVinylDiscLand;
    private ImageView ivVinylCircularCoverLand;
    private TextView tvDetailTitleLand, tvDetailArtistLand, tvDetailQualityLand, tvDetailBufferLand, tvDetailTimeLand;
    private Spinner spinnerDetailBitrateLand;
    private Button btnDetailFavLand, btnLyricDecLand, btnLyricIncLand;
    private ImageView btnDetailModeLand, btnDetailPrevLand, btnDetailPlayPauseLand, btnDetailNextLand, btnDetailEqLand;
    private SeekBar detailSeekBarLand;
    private ScrollView scrollLyricsLand;
    private LinearLayout layoutLyricsContainerLand, layoutDetailLyricsViewLand, layoutDetailQueueViewLand, layoutDetailBottomBlankLand;
    private ListView lvDetailQueueLand;

    // 详情页竖屏控件
    private FrameLayout flVinylDiscPort;
    private ImageView ivVinylCircularCoverPort;
    private TextView tvDetailTitlePort, tvDetailArtistPort, tvDetailQualityPort, tvDetailBufferPort, tvDetailTimePort;
    private Spinner spinnerDetailBitratePort;
    private Button btnDetailFavPort;
    private ImageView btnDetailModePort, btnDetailPrevPort, btnDetailPlayPausePort, btnDetailNextPort, btnDetailEqPort;
    private SeekBar detailSeekBarPort;
    private ScrollView scrollLyricsPort;
    private LinearLayout layoutLyricsContainerPort, layoutDetailBottomBlankPort;

    // 主页控件
    private TextView tvAppTitle, tvAppVersion, tvListTitle, tvCurrentSong, tvTime;
    private ImageView btnThemeToggle, btnSettingsIcon, btnTopSearch, btnExitApp;
    private ImageView ivBottomCover;
    private ImageView btnMode, btnPrev, btnPlayPause, btnNext, btnOpenEq;
    private Button btnBottomFav, btnToggleQueue, btnCloseQueue, btnBack;
    private Button btnTabPlaylists, btnTabRanking;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    private EditText etServer, etUsername, etPassword, etTimeoutSec, etRetryCount, etCacheSize, etDownloadPath;
    private Spinner spinnerConfigBitrate;
    private Button btnConnect, btnClearCache;

    // 搜索页控件
    private Button btnSearchPageBack, btnSearchSubmit, btnFloatingAddPlaylist;
    private ImageView btnClearHistory;
    private Spinner spinnerSearchType;
    private EditText etSearchKeyword;
    private ListView lvSearchResults;
    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private Set<String> checkedSongIds = new HashSet<String>();
    private SearchResultAdapter searchAdapter;

    // 详情页顶栏通用控件
    private Button btnCloseDetail, btnDetailDownload, btnDetailKeepScreen, btnDetailQueue;
    private ImageView btnDetailExitApp;

    // 黑胶旋转调度
    private Handler vinylHandler = new Handler();
    private float currentVinylDegree = 0f;
    private boolean isCurrentSongPlaying = false;
    private Runnable vinylRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCurrentSongPlaying) {
                currentVinylDegree = (currentVinylDegree + 0.6f) % 360f;
                if (flVinylDiscLand != null) flVinylDiscLand.setRotation(currentVinylDegree);
                if (flVinylDiscPort != null) flVinylDiscPort.setRotation(currentVinylDegree);
                vinylHandler.postDelayed(this, 25);
            }
        }
    };

    private boolean isKeepScreenOn = false;
    private int currentLyricIndex = -1;
    private int lyricBaseFontSize = 15;

    private static class LyricRow {
        long timeMs;
        String text;
        TextView viewLand;
        TextView viewPort;
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
    private SimpleAdapter detailQueueAdapterLand;

    private boolean isUserSeeking = false;
    private String lastLoadedSongId = "";
    private boolean isSpinnersInitializing = true;

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
                    setDetailTitle(title);
                    setDetailArtist(artist);

                    String currentBitrate = getSavedBitrate();
                    setDetailQuality(getBitrateDisplay(currentBitrate, quality));

                    if (isBuffering && bufferPercent < 100) {
                        setDetailBuffer("(缓冲 " + bufferPercent + "%)", true);
                    } else if (retryCount > 0) {
                        setDetailBuffer("(重试 " + retryCount + "/" + maxRetries + ")", true);
                    } else {
                        setDetailBuffer("", false);
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
                    if (detailSeekBarLand != null) {
                        detailSeekBarLand.setMax(duration);
                        detailSeekBarLand.setProgress(position);
                    }
                    if (detailSeekBarPort != null) {
                        detailSeekBarPort.setMax(duration);
                        detailSeekBarPort.setProgress(position);
                    }
                    String timeStr = formatTime(position) + " / " + formatTime(duration);
                    tvTime.setText(timeStr);
                    if (tvDetailTimeLand != null) tvDetailTimeLand.setText(timeStr);
                    if (tvDetailTimePort != null) tvDetailTimePort.setText(timeStr);

                    updateLyricPosition(position);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setupCrashHandler();

        super.onCreate(savedInstanceState);
        TLSSocketFactory.install();

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean("is_dark_theme", true);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);

        loadFavSet();
        loadLocalPlaylists();
        loadSearchHistory();

        initViews();
        setupControlIcons();
        setupBitrateSpinners();
        setupSearchTypeSpinner();
        setupThemeColors();
        updateDetailOrientationLayout();
        loadSavedConfig();
        setupListeners();

        restoreLastSessionIfAvailable();
        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDetailOrientationLayout();
    }

    private void updateDetailOrientationLayout() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (layoutDetailLandscape != null) {
            layoutDetailLandscape.setVisibility(isLandscape ? View.VISIBLE : View.GONE);
        }
        if (layoutDetailPortrait != null) {
            layoutDetailPortrait.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        }
    }

    private void setDetailTitle(String t) {
        if (tvDetailTitleLand != null) tvDetailTitleLand.setText(t);
        if (tvDetailTitlePort != null) tvDetailTitlePort.setText(t);
    }

    private void setDetailArtist(String a) {
        if (tvDetailArtistLand != null) tvDetailArtistLand.setText(a);
        if (tvDetailArtistPort != null) tvDetailArtistPort.setText(a);
    }

    private void setDetailQuality(String q) {
        if (tvDetailQualityLand != null) tvDetailQualityLand.setText(q);
        if (tvDetailQualityPort != null) tvDetailQualityPort.setText(q);
    }

    private void setDetailBuffer(String b, boolean visible) {
        if (tvDetailBufferLand != null) {
            tvDetailBufferLand.setText(b);
            tvDetailBufferLand.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (tvDetailBufferPort != null) {
            tvDetailBufferPort.setText(b);
            tvDetailBufferPort.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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
        layoutDetailLandscape = (LinearLayout) findViewById(R.id.layout_detail_landscape);
        layoutDetailPortrait = (LinearLayout) findViewById(R.id.layout_detail_portrait);

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

        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);
        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);

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

        // 详情横屏控件绑定
        flVinylDiscLand = (FrameLayout) findViewById(R.id.fl_vinyl_disc_land);
        ivVinylCircularCoverLand = (ImageView) findViewById(R.id.iv_vinyl_circular_cover_land);
        tvDetailTitleLand = (TextView) findViewById(R.id.tv_detail_title_land);
        tvDetailArtistLand = (TextView) findViewById(R.id.tv_detail_artist_land);
        tvDetailQualityLand = (TextView) findViewById(R.id.tv_detail_quality_land);
        tvDetailBufferLand = (TextView) findViewById(R.id.tv_detail_buffer_land);
        tvDetailTimeLand = (TextView) findViewById(R.id.tv_detail_time_land);
        spinnerDetailBitrateLand = (Spinner) findViewById(R.id.spinner_detail_bitrate_land);
        btnDetailFavLand = (Button) findViewById(R.id.btn_detail_fav_land);
        btnLyricDecLand = (Button) findViewById(R.id.btn_lyric_dec_land);
        btnLyricIncLand = (Button) findViewById(R.id.btn_lyric_inc_land);
        btnDetailModeLand = (ImageView) findViewById(R.id.btn_detail_mode_land);
        btnDetailPrevLand = (ImageView) findViewById(R.id.btn_detail_prev_land);
        btnDetailPlayPauseLand = (ImageView) findViewById(R.id.btn_detail_play_pause_land);
        btnDetailNextLand = (ImageView) findViewById(R.id.btn_detail_next_land);
        btnDetailEqLand = (ImageView) findViewById(R.id.btn_detail_eq_land);
        detailSeekBarLand = (SeekBar) findViewById(R.id.detail_seek_bar_land);
        scrollLyricsLand = (ScrollView) findViewById(R.id.scroll_lyrics_land);
        layoutLyricsContainerLand = (LinearLayout) findViewById(R.id.layout_lyrics_container_land);
        layoutDetailLyricsViewLand = (LinearLayout) findViewById(R.id.layout_detail_lyrics_view_land);
        layoutDetailQueueViewLand = (LinearLayout) findViewById(R.id.layout_detail_queue_view_land);
        layoutDetailBottomBlankLand = (LinearLayout) findViewById(R.id.layout_detail_bottom_blank_land);
        lvDetailQueueLand = (ListView) findViewById(R.id.lv_detail_queue_land);

        // 详情竖屏控件绑定
        flVinylDiscPort = (FrameLayout) findViewById(R.id.fl_vinyl_disc_port);
        ivVinylCircularCoverPort = (ImageView) findViewById(R.id.iv_vinyl_circular_cover_port);
        tvDetailTitlePort = (TextView) findViewById(R.id.tv_detail_title_port);
        tvDetailArtistPort = (TextView) findViewById(R.id.tv_detail_artist_port);
        tvDetailQualityPort = (TextView) findViewById(R.id.tv_detail_quality_port);
        tvDetailBufferPort = (TextView) findViewById(R.id.tv_detail_buffer_port);
        tvDetailTimePort = (TextView) findViewById(R.id.tv_detail_time_port);
        spinnerDetailBitratePort = (Spinner) findViewById(R.id.spinner_detail_bitrate_port);
        btnDetailFavPort = (Button) findViewById(R.id.btn_detail_fav_port);
        btnDetailModePort = (ImageView) findViewById(R.id.btn_detail_mode_port);
        btnDetailPrevPort = (ImageView) findViewById(R.id.btn_detail_prev_port);
        btnDetailPlayPausePort = (ImageView) findViewById(R.id.btn_detail_play_pause_port);
        btnDetailNextPort = (ImageView) findViewById(R.id.btn_detail_next_port);
        btnDetailEqPort = (ImageView) findViewById(R.id.btn_detail_eq_port);
        detailSeekBarPort = (SeekBar) findViewById(R.id.detail_seek_bar_port);
        scrollLyricsPort = (ScrollView) findViewById(R.id.scroll_lyrics_port);
        layoutLyricsContainerPort = (LinearLayout) findViewById(R.id.layout_lyrics_container_port);
        layoutDetailBottomBlankPort = (LinearLayout) findViewById(R.id.layout_detail_bottom_blank_port);

        adapter = new PlaylistsCustomAdapter();
        listView.setAdapter(adapter);

        searchAdapter = new SearchResultAdapter();
        lvSearchResults.setAdapter(searchAdapter);

        queueAdapter = new SimpleAdapter(
                this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2}
        );
        lvQueue.setAdapter(queueAdapter);

        detailQueueAdapterLand = new SimpleAdapter(
                this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2}
        );
        if (lvDetailQueueLand != null) lvDetailQueueLand.setAdapter(detailQueueAdapterLand);
    }

    private void setupThemeColors() {
        if (isDarkTheme) {
            layoutRoot.setBackgroundResource(R.drawable.bg_theme_dark);
            layoutSearchPage.setBackgroundResource(R.drawable.bg_theme_dark);
            layoutDetailOverlay.setBackgroundResource(R.drawable.bg_theme_dark);
            tvAppTitle.setTextColor(0xFFFFFFFF);
            btnThemeToggle.setImageDrawable(MediaIconHelper.createThemeIcon(this, 18, 0xFF00E5FF, true));

            etSearchKeyword.setTextColor(0xFFFFFFFF);
            etSearchKeyword.setHintTextColor(0xFF9CA3AF);
            etSearchKeyword.setBackgroundResource(R.drawable.bg_btn_pill);
        } else {
            layoutRoot.setBackgroundResource(R.drawable.bg_theme_light);
            layoutSearchPage.setBackgroundResource(R.drawable.bg_theme_light);
            layoutDetailOverlay.setBackgroundResource(R.drawable.bg_theme_light);
            tvAppTitle.setTextColor(0xFF1F2937);
            btnThemeToggle.setImageDrawable(MediaIconHelper.createThemeIcon(this, 18, 0xFFFF9800, false));

            etSearchKeyword.setTextColor(0xFF111827);
            etSearchKeyword.setHintTextColor(0xFF6B7280);
            etSearchKeyword.setBackgroundResource(R.drawable.bg_card_frosted_light);
        }
        renderSearchHistoryTags();
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        prefs.edit().putBoolean("is_dark_theme", isDarkTheme).commit();
        setupThemeColors();
        adapter.notifyDataSetChanged();
        searchAdapter.notifyDataSetChanged();
        setupControlIcons();
        Toast.makeText(this, isDarkTheme ? "已切换至深色主题" : "已切换至浅色主题", Toast.LENGTH_SHORT).show();
    }

    private class PlaylistsCustomAdapter extends BaseAdapter {
        @Override public int getCount() {
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

            if (currentSelectedTab == TAB_PLAYLISTS && position == currentItems.size()) {
                TextView addTv = new TextView(MainActivity.this);
                addTv.setText("+  新建歌单 (云同步)");
                addTv.setTextSize(14);
                addTv.setTextColor(isDarkTheme ? 0xFF00E5FF : 0xFF0091EA);
                addTv.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(addTv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showCreatePlaylistDialog(null);
                    }
                });
                return row;
            }

            final DisplayEntry item = currentItems.get(position);

            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView title = new TextView(MainActivity.this);
            title.setText(item.title);
            title.setTextColor(isDarkTheme ? 0xFFFFFFFF : 0xFF111827);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(title);

            TextView sub = new TextView(MainActivity.this);
            sub.setText(item.subtitle);
            sub.setTextColor(isDarkTheme ? 0xFF888C99 : 0xFF6B7280);
            sub.setTextSize(11);
            textCol.addView(sub);
            row.addView(textCol, tLp);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!item.isSong) {
                        if (item.id.startsWith("album_")) {
                            fetchAlbumSongs(item.id.substring(6), item.title);
                        } else if (item.id.startsWith("artist_")) {
                            fetchArtistAlbums(item.id.substring(7), item.title);
                        } else {
                            fetchPlaylistSongs(item.id, item.title);
                        }
                    } else {
                        ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                        int clickedIndex = 0;
                        for (int i = 0; i < currentItems.size(); i++) {
                            DisplayEntry e = currentItems.get(i);
                            if (e.isSong) {
                                if (e.id.equals(item.id)) {
                                    clickedIndex = queue.size();
                                }
                                queue.add(new MusicService.SongItem(
                                        e.id, e.title, e.artist,
                                        buildStreamUrl(e.id), e.coverArt, e.quality
                                ));
                            }
                        }
                        MusicService.setQueue(queue, clickedIndex, MainActivity.this);
                        refreshQueueList();
                    }
                }
            });

            if (item.isSong) {
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showSongLongClickMenu(item, position);
                        return true;
                    }
                });
            }

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

    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        final boolean fav = isFav(entry.id);
        String[] options = new String[]{
                fav ? "★ 已收藏（点此从云端取消）" : "☆ 收藏歌曲 (同步云端)",
                "📁 添加到其他歌单",
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
                            ArrayList<String> sids = new ArrayList<String>();
                            sids.add(entry.id);
                            showChoosePlaylistDialog(sids);
                        } else if (which == 2) {
                            if (position >= 0 && position < currentItems.size()) {
                                currentItems.remove(position);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(MainActivity.this, "已从当前列表移出", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 3) {
                            downloadSongItem(entry);
                        }
                    }
                }).show();
    }

    private void serverStarSong(final String songId, final boolean toStar) {
        if (songId == null || songId.length() == 0) return;
        if (toStar) {
            favSongIds.add(songId);
            Toast.makeText(this, "已添加至云端【我的收藏】♥", Toast.LENGTH_SHORT).show();
        } else {
            favSongIds.remove(songId);
            Toast.makeText(this, "已从【我的收藏】移出♡", Toast.LENGTH_SHORT).show();
        }
        saveFavSet();
        updateFavButtonState(songId);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String endpoint = toStar ? "star.view" : "unstar.view";
                    requestApi(endpoint + "?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void downloadSongItem(final DisplayEntry entry) {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        final File saveDir = new File(customPath);
        if (!saveDir.exists()) saveDir.mkdirs();

        final File targetFile = new File(saveDir, sanitizeFileName(entry.title + " - " + entry.artist) + ".mp3");
        Toast.makeText(this, "正在下载: " + entry.title, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String streamUrl = buildStreamUrl(entry.id);
                    URL url = new URL(streamUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    conn.disconnect();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载成功！保存在:\n" + targetFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String getDefaultDownloadPath() {
        File m = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (m != null) return m.getAbsolutePath();
        return "/sdcard/Music";
    }

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
                            targetPlaylistIdToAdd = pl.id;
                            targetPlaylistNameToAdd = pl.title;
                            openSearchPage("添加歌曲到【" + pl.title + "】");
                        } else if (which == 2) {
                            deleteCloudPlaylist(pl);
                        }
                    }
                }).show();
    }

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

    private void showRenamePlaylistDialog(final DisplayEntry pl) {
        final EditText et = new EditText(this);
        et.setText(pl.title);
        new AlertDialog.Builder(this)
                .setTitle("重命名歌单")
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void ncol(DialogInterface dialog, int which) {} // placeholder
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

            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            TextView title = new TextView(MainActivity.this);
            title.setText(item.title);
            title.setTextColor(isDarkTheme ? 0xFFFFFFFF : 0xFF111827);
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
                    ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                    queue.add(new MusicService.SongItem(item.id, item.title, item.artist, buildStreamUrl(item.id), item.coverArt, item.quality));
                    MusicService.setQueue(queue, 0, MainActivity.this);
                }
            });
            row.addView(textCol, tLp);

            if (item.isSong) {
                final CheckBox cb = new CheckBox(MainActivity.this);
                final boolean isChecked = checkedSongIds.contains(item.id);
                cb.setChecked(isChecked);
                cb.setButtonDrawable(MediaIconHelper.createCheckboxDrawable(MainActivity.this, isChecked, isDarkTheme));
                cb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (cb.isChecked()) {
                            checkedSongIds.add(item.id);
                        } else {
                            checkedSongIds.remove(item.id);
                        }
                        cb.setButtonDrawable(MediaIconHelper.createCheckboxDrawable(MainActivity.this, cb.isChecked(), isDarkTheme));
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
        } else {
            etSearchKeyword.setHint("输入关键词检索...");
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

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSongs(etSearchKeyword.getText().toString().trim());
            }
        });

        btnFloatingAddPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkedSongIds.isEmpty()) return;
                final ArrayList<String> sids = new ArrayList<String>(checkedSongIds);
                if (targetPlaylistIdToAdd != null) {
                    addSongsToCloudPlaylist(targetPlaylistIdToAdd, sids);
                } else {
                    showChoosePlaylistDialog(sids);
                }
            }
        });

        ivBottomCover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutDetailOverlay.setVisibility(View.VISIBLE);
                updateDetailOrientationLayout();
            }
        });

        btnTabPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedTab = TAB_PLAYLISTS;
                btnTabPlaylists.setTextColor(0xFF00E5FF);
                btnTabRanking.setTextColor(0xFFA0A5B5);
                showUserPlaylists();
            }
        });

        btnTabRanking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedTab = TAB_RANKING;
                btnTabRanking.setTextColor(0xFF00E5FF);
                btnTabPlaylists.setTextColor(0xFFA0A5B5);
                showRankingPlaylists();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnBack.setVisibility(View.GONE);
                showCurrentTabContent();
            }
        });

        View.OnClickListener toggleQueueListener = new View.OnClickListener() {
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
        };
        btnToggleQueue.setOnClickListener(toggleQueueListener);
        if (btnDetailQueue != null) btnDetailQueue.setOnClickListener(toggleQueueListener);

        btnCloseQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutQueuePanel.setVisibility(View.GONE);
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
                CacheManager.clearAllCache(MainActivity.this);
                Toast.makeText(MainActivity.this, "已清理缓存", Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        };
        btnExitApp.setOnClickListener(exitListener);
        if (btnDetailExitApp != null) btnDetailExitApp.setOnClickListener(exitListener);

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutDetailOverlay.setVisibility(View.GONE);
            }
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

        View.OnClickListener eqListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new EqualizerDialog(MainActivity.this).show();
            }
        };
        btnOpenEq.setOnClickListener(eqListener);
        if (btnDetailEqLand != null) btnDetailEqLand.setOnClickListener(eqListener);
        if (btnDetailEqPort != null) btnDetailEqPort.setOnClickListener(eqListener);

        View.OnClickListener togglePlayListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
            }
        };
        btnPlayPause.setOnClickListener(togglePlayListener);
        if (btnDetailPlayPauseLand != null) btnDetailPlayPauseLand.setOnClickListener(togglePlayListener);
        if (btnDetailPlayPausePort != null) btnDetailPlayPausePort.setOnClickListener(togglePlayListener);

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
            }
        };
        btnNext.setOnClickListener(nextListener);
        if (btnDetailNextLand != null) btnDetailNextLand.setOnClickListener(nextListener);
        if (btnDetailNextPort != null) btnDetailNextPort.setOnClickListener(nextListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
            }
        };
        btnPrev.setOnClickListener(prevListener);
        if (btnDetailPrevLand != null) btnDetailPrevLand.setOnClickListener(prevListener);
        if (btnDetailPrevPort != null) btnDetailPrevPort.setOnClickListener(prevListener);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
                int nextMode = (MusicService.getCurrentMode() + 1) % 3;
                Toast.makeText(MainActivity.this, getModeString(nextMode), Toast.LENGTH_SHORT).show();
            }
        };
        btnMode.setOnClickListener(modeListener);
        if (btnDetailModeLand != null) btnDetailModeLand.setOnClickListener(modeListener);
        if (btnDetailModePort != null) btnDetailModePort.setOnClickListener(modeListener);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    String t = formatTime(progress) + " / " + formatTime(sb.getMax());
                    tvTime.setText(t);
                    if (tvDetailTimeLand != null) tvDetailTimeLand.setText(t);
                    if (tvDetailTimePort != null) tvDetailTimePort.setText(t);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_SEEK);
                intent.putExtra("position", sb.getProgress());
                startService(intent);
            }
        };
        seekBar.setOnSeekBarChangeListener(seekListener);
        if (detailSeekBarLand != null) detailSeekBarLand.setOnSeekBarChangeListener(seekListener);
        if (detailSeekBarPort != null) detailSeekBarPort.setOnSeekBarChangeListener(seekListener);

        if (layoutDetailBottomBlankLand != null) {
            layoutDetailBottomBlankLand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (layoutDetailQueueViewLand.getVisibility() == View.VISIBLE) {
                        layoutDetailQueueViewLand.setVisibility(View.GONE);
                        layoutDetailLyricsViewLand.setVisibility(View.VISIBLE);
                    } else {
                        refreshQueueList();
                        layoutDetailLyricsViewLand.setVisibility(View.GONE);
                        layoutDetailQueueViewLand.setVisibility(View.VISIBLE);
                        scrollQueueToCenter(lvDetailQueueLand);
                    }
                }
            });
        }

        if (layoutDetailBottomBlankPort != null) {
            layoutDetailBottomBlankPort.setOnClickListener(new View.OnClickListener() {
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
        }

        if (btnLyricDecLand != null) {
            btnLyricDecLand.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { applyLyricFontSize(-2); }
            });
        }
        if (btnLyricIncLand != null) {
            btnLyricIncLand.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { applyLyricFontSize(2); }
            });
        }
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
                            Toast.makeText(MainActivity.this, "已成功添加 " + songIds.size() + " 首歌曲至云端歌单！", Toast.LENGTH_SHORT).show();
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

    private void applyLyricFontSize(int delta) {
        lyricBaseFontSize += delta;
        if (lyricBaseFontSize < 11) lyricBaseFontSize = 11;
        if (lyricBaseFontSize > 26) lyricBaseFontSize = 26;

        prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).commit();

        for (int i = 0; i < lyricRows.size(); i++) {
            LyricRow row = lyricRows.get(i);
            int size = (i == currentLyricIndex) ? (lyricBaseFontSize + 5) : lyricBaseFontSize;
            if (row.viewLand != null) row.viewLand.setTextSize(size);
            if (row.viewPort != null) row.viewPort.setTextSize(size);
        }
        Toast.makeText(this, "歌词字号: " + lyricBaseFontSize + "sp", Toast.LENGTH_SHORT).show();
    }

    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty()) return;
        int targetIndex = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (currentPosMs >= lyricRows.get(i).timeMs) targetIndex = i; else break;
        }

        if (targetIndex != currentLyricIndex && targetIndex >= 0) {
            if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                LyricRow oldRow = lyricRows.get(currentLyricIndex);
                int oldColor = isDarkTheme ? 0xFF777777 : 0xFF9CA3AF;
                if (oldRow.viewLand != null) {
                    oldRow.viewLand.setTextColor(oldColor);
                    oldRow.viewLand.setTextSize(lyricBaseFontSize);
                    oldRow.viewLand.setTypeface(Typeface.DEFAULT);
                }
                if (oldRow.viewPort != null) {
                    oldRow.viewPort.setTextColor(oldColor);
                    oldRow.viewPort.setTextSize(lyricBaseFontSize);
                    oldRow.viewPort.setTypeface(Typeface.DEFAULT);
                }
            }
            currentLyricIndex = targetIndex;
            final LyricRow curRow = lyricRows.get(currentLyricIndex);
            if (curRow != null) {
                if (curRow.viewLand != null) {
                    curRow.viewLand.setTextColor(0xFF00E5FF);
                    curRow.viewLand.setTextSize(lyricBaseFontSize + 5);
                    curRow.viewLand.setTypeface(Typeface.DEFAULT_BOLD);
                }
                if (curRow.viewPort != null) {
                    curRow.viewPort.setTextColor(0xFF00E5FF);
                    curRow.viewPort.setTextSize(lyricBaseFontSize + 5);
                    curRow.viewPort.setTypeface(Typeface.DEFAULT_BOLD);
                }

                if (scrollLyricsLand != null && curRow.viewLand != null) {
                    scrollLyricsLand.post(new Runnable() {
                        @Override
                        public void run() {
                            int scrollY = curRow.viewLand.getTop() - (scrollLyricsLand.getHeight() / 2) + (curRow.viewLand.getHeight() / 2);
                            scrollLyricsLand.smoothScrollTo(0, Math.max(0, scrollY));
                        }
                    });
                }
                if (scrollLyricsPort != null && curRow.viewPort != null) {
                    scrollLyricsPort.post(new Runnable() {
                        @Override
                        public void run() {
                            int scrollY = curRow.viewPort.getTop() - (scrollLyricsPort.getHeight() / 2) + (curRow.viewPort.getHeight() / 2);
                            scrollLyricsPort.smoothScrollTo(0, Math.max(0, scrollY));
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
                        final Bitmap circular = getCircularBitmap(bitmap, 240);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ivBottomCover.setImageBitmap(bitmap);
                                if (ivVinylCircularCoverLand != null) ivVinylCircularCoverLand.setImageBitmap(circular);
                                if (ivVinylCircularCoverPort != null) ivVinylCircularCoverPort.setImageBitmap(circular);
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private Bitmap getCircularBitmap(Bitmap bitmap, int targetSize) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = targetSize / 2f;
        canvas.drawCircle(r, r, r, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        int minEdge = Math.min(srcW, srcH);
        Rect srcRect = new Rect((srcW - minEdge) / 2, (srcH - minEdge) / 2, (srcW + minEdge) / 2, (srcH + minEdge) / 2);
        Rect dstRect = new Rect(0, 0, targetSize, targetSize);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
        return output;
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
            currentItems.add(new DisplayEntry("fav_entry", "我的收藏", "云端同步", "已同步标星 (" + favSongIds.size() + "首)", null, "云端歌单", false));
            currentItems.add(new DisplayEntry("local_featured", "精选歌单", "本地定制", "精选曲库 (" + featuredSongs.size() + "首)", null, "本地歌单", false));
            currentItems.add(new DisplayEntry("local_car", "车载歌单", "本地定制", "出行常备 (" + carSongs.size() + "首)", null, "本地歌单", false));
            currentItems.addAll(rawServerUserPlaylists);
        } else {
            currentItems.addAll(rawServerRankingPlaylists);
        }
        adapter.notifyDataSetChanged();
    }

    private void showUserPlaylists() {
        showCurrentTabContent();
    }

    private void showRankingPlaylists() {
        showCurrentTabContent();
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
        if (detailQueueAdapterLand != null) detailQueueAdapterLand.notifyDataSetChanged();
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int color = isDarkTheme ? 0xFF10141A : 0xFFFFFFFF;
        btnPlayPause.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 20, color) : MediaIconHelper.createPlayIcon(this, 22, color));
        if (btnDetailPlayPauseLand != null) {
            btnDetailPlayPauseLand.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 26, color) : MediaIconHelper.createPlayIcon(this, 28, color));
        }
        if (btnDetailPlayPausePort != null) {
            btnDetailPlayPausePort.setImageDrawable(isPlaying ? MediaIconHelper.createPauseIcon(this, 26, color) : MediaIconHelper.createPlayIcon(this, 28, color));
        }
    }

    private void updateModeIcons(int mode) {
        int color = isDarkTheme ? 0xFF00E5FF : 0xFF0091EA;
        Drawable d = (mode == MusicService.MODE_SHUFFLE) ? MediaIconHelper.createShuffleIcon(this, 18, color)
                : (mode == MusicService.MODE_SINGLE) ? MediaIconHelper.createRepeatOneIcon(this, 18, color)
                : MediaIconHelper.createRepeatIcon(this, 18, color);
        btnMode.setImageDrawable(d);
        if (btnDetailModeLand != null) btnDetailModeLand.setImageDrawable(d);
        if (btnDetailModePort != null) btnDetailModePort.setImageDrawable(d);
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

        if (btnDetailPrevLand != null) btnDetailPrevLand.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, navColor));
        if (btnDetailNextLand != null) btnDetailNextLand.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, navColor));
        if (btnDetailEqLand != null) btnDetailEqLand.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, accent));

        if (btnDetailPrevPort != null) btnDetailPrevPort.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, navColor));
        if (btnDetailNextPort != null) btnDetailNextPort.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, navColor));
        if (btnDetailEqPort != null) btnDetailEqPort.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, accent));
    }

    private String formatTime(int ms) {
        int s = (ms / 1000) % 60;
        int m = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }
}
