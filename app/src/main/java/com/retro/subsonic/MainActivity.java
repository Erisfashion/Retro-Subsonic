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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {

    private static final String[] BITRATE_LABELS = new String[]{"默认不变", "128K", "192K", "320K", "FLAC"};
    private static final String[] BITRATE_VALUES = new String[]{"auto", "128", "192", "320", "flac"};
    private static final String[] SEARCH_TYPES = new String[]{"歌曲", "歌手", "专辑"};

    private static final int TAB_PLAYLISTS = 0;
    private static final int TAB_RANKING = 1;
    private int currentSelectedTab = TAB_PLAYLISTS;

    private boolean isLightTheme = false;

    private FrameLayout layoutRootFrame;
    private TextView tvAppLogo, tvAppVersion;
    private ImageView btnToggleTheme, btnOpenSettings, btnTopSearch, btnExitApp, btnDetailExitApp;

    private EditText etServer, etUsername, etPassword, etCacheSize;
    private EditText etTimeoutSec, etRetryCount, etDownloadPath;
    private Spinner spinnerConfigBitrate, spinnerDetailBitrate;
    private boolean isSpinnersInitializing = true;

    private Button btnConnect, btnClearCache, btnTabPlaylists, btnTabRanking, btnBack;
    private ImageView btnOpenEq, btnDetailEq;
    private ImageView btnMode, btnDetailMode;
    private ImageView btnPrev, btnPlayPause, btnNext;
    private ImageView btnDetailPrev, btnDetailPlayPause, btnDetailNext;
    private Button btnToggleQueue, btnCloseQueue, btnCloseDetailQueue;
    private FrameLayout flMiniCoverContainer;
    private ImageView ivMiniCover;
    private Button btnBottomFav, btnDetailFav, btnDetailDownload;
    private Button btnLyricDec, btnLyricInc;
    private LinearLayout layoutConfigPanel, layoutQueuePanel, layoutDetailOverlay, layoutBottomPlayer;
    private TextView tvListTitle, tvCurrentSong, tvTime;
    private ListView listView, lvQueue, lvDetailQueue;
    private SeekBar seekBar;

    // 搜索全屏页组件
    private LinearLayout layoutSearchPageOverlay, layoutHistoryTagsFlow;
    private Button btnSearchBack, btnSearchSubmit, btnFloatingAddToPlaylist;
    private Spinner spinnerSearchType;
    private EditText etSearchKeyword;
    private ImageView btnClearSearchHistory;
    private ListView lvSearchResults;
    private ArrayList<String> searchHistoryList = new ArrayList<String>();
    private Set<String> selectedSearchSongIds = new HashSet<String>();
    private ArrayList<DisplayEntry> currentSearchResults = new ArrayList<DisplayEntry>();
    private SearchResultAdapter searchAdapter;

    // 响应式详情页组件
    private LinearLayout layoutDetailResponsiveContent, layoutLandscapeRightPanel, layoutDetailQueuePanel;
    private LinearLayout layoutPortraitThreeLinesLyrics;
    private TextView tvLyricPrevLine, tvLyricCurrentLine, tvLyricNextLine;
    private TextView tvDetailBufferStatus;

    // 下拉刷新组件
    private LinearLayout refreshHeaderView;
    private ProgressBar refreshProgressBar;
    private TextView refreshTextView;
    private int refreshHeaderHeight = 0;
    private boolean isRefreshing = false;
    private boolean isPulling = false;
    private float touchStartY = 0;

    private String currentActivePlaylistId = null;
    private String currentActivePlaylistName = null;

    private ArrayList<DisplayEntry> rawServerUserPlaylists = new ArrayList<DisplayEntry>();
    private ArrayList<DisplayEntry> rawServerRankingPlaylists = new ArrayList<DisplayEntry>();

    private Button btnCloseDetail;
    private Button btnDetailKeepScreen, btnDetailQueue;
    private FrameLayout layoutVinylContainer, flVinylDisc;
    private ImageView ivVinylCircularCover, ivSquareCover;
    private TonearmView viewTonearm;
    private LinearLayout layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailTime;
    private SeekBar detailSeekBar;

    private boolean isVinylDisplayMode = true;
    private Bitmap currentRawCoverBitmap;
    private Bitmap currentCircularCoverBitmap;

    private RotateAnimation vinylRotateAnim;
    private boolean isCurrentSongPlaying = false;
    private boolean isKeepScreenOn = false;

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

    public static class DisplayEntry {
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
    private CustomPlaylistAdapter playlistAdapter;

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

                updatePlayPauseIcons(isPlaying);
                updateVinylAnimationState();

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
                    if (retryCount > 0) {
                        tvCurrentSong.setText("重试中 (" + retryCount + "/" + maxRetries + "): " + title);
                        tvDetailTitle.setText(title);
                        tvDetailBufferStatus.setText("(重试 " + retryCount + "/" + maxRetries + ")");
                        tvDetailBufferStatus.setVisibility(View.VISIBLE);
                    } else if (isPlaying) {
                        if (isBuffering && bufferPercent < 100) {
                            tvCurrentSong.setText(title + " - " + artist + " (" + bufferPercent + "%)");
                            tvDetailTitle.setText(title);
                            tvDetailBufferStatus.setText("(" + bufferPercent + "%)");
                            tvDetailBufferStatus.setVisibility(View.VISIBLE);
                        } else {
                            tvCurrentSong.setText(title + " - " + artist);
                            tvDetailTitle.setText(title);
                            tvDetailBufferStatus.setVisibility(View.GONE);
                        }
                    } else if (isBuffering) {
                        tvCurrentSong.setText("缓冲中 (" + bufferPercent + "%): " + title);
                        tvDetailTitle.setText(title);
                        tvDetailBufferStatus.setText("(" + bufferPercent + "%)");
                        tvDetailBufferStatus.setVisibility(View.VISIBLE);
                    } else {
                        tvCurrentSong.setText(title + " - " + artist);
                        tvDetailTitle.setText(title);
                        tvDetailBufferStatus.setVisibility(View.GONE);
                    }
                    tvDetailArtist.setText(artist);

                    String currentBitrate = getSavedBitrate();
                    tvDetailQuality.setText(getBitrateDisplay(currentBitrate, quality));

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
        TLSSocketFactory.install();

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        isLightTheme = prefs.getBoolean("is_light_theme", false);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);

        loadFavSet();
        loadLocalPlaylists();
        loadSearchHistory();

        initViews();
        applyCurrentTheme(false);
        setupControlIcons();
        setupBitrateSpinners();
        setupSearchTypeSpinner();
        setupVinylAnimation();
        updateCoverDisplayMode();
        loadSavedConfig();
        setupListeners();
        setupClickInterceptors();

        restoreLastSessionIfAvailable();
        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

    private void applyCurrentTheme(boolean notifyUser) {
        int primaryText = isLightTheme ? 0xFF1C202B : 0xFFFFFFFF;
        int subText = isLightTheme ? 0xFF60687B : 0xFFA0A5B5;

        layoutRootFrame.setBackgroundResource(isLightTheme ? R.drawable.bg_main_gradient_light : R.drawable.bg_main_gradient);
        layoutDetailOverlay.setBackgroundResource(isLightTheme ? R.drawable.bg_main_gradient_light : R.drawable.bg_main_gradient);
        layoutSearchPageOverlay.setBackgroundResource(isLightTheme ? R.drawable.bg_main_gradient_light : R.drawable.bg_main_gradient);

        tvAppLogo.setTextColor(primaryText);
        tvAppVersion.setTextColor(subText);
        tvListTitle.setTextColor(subText);
        tvCurrentSong.setTextColor(primaryText);

        setupControlIcons();
        if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
        if (searchAdapter != null) searchAdapter.notifyDataSetChanged();

        if (notifyUser) {
            Toast.makeText(this, isLightTheme ? "已切换至浅色晶钻主题" : "已切换至深色曜石主题", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTheme() {
        isLightTheme = !isLightTheme;
        prefs.edit().putBoolean("is_light_theme", isLightTheme).commit();
        applyCurrentTheme(true);
    }

    private void initViews() {
        layoutRootFrame = (FrameLayout) findViewById(R.id.layout_root_frame);
        tvAppLogo = (TextView) findViewById(R.id.tv_app_logo);
        tvAppVersion = (TextView) findViewById(R.id.tv_app_version);

        btnToggleTheme = (ImageView) findViewById(R.id.btn_toggle_theme);
        btnOpenSettings = (ImageView) findViewById(R.id.btn_open_settings);
        btnTopSearch = (ImageView) findViewById(R.id.btn_top_search);
        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);
        btnDetailExitApp = (ImageView) findViewById(R.id.btn_detail_exit_app);

        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);
        etDownloadPath = (EditText) findViewById(R.id.et_download_path);

        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        spinnerDetailBitrate = (Spinner) findViewById(R.id.spinner_detail_bitrate);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabRanking = (Button) findViewById(R.id.btn_tab_ranking);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);

        btnOpenEq = (ImageView) findViewById(R.id.btn_open_eq);
        btnDetailEq = (ImageView) findViewById(R.id.btn_detail_eq);
        btnMode = (ImageView) findViewById(R.id.btn_mode);
        btnDetailMode = (ImageView) findViewById(R.id.btn_detail_mode);

        btnPrev = (ImageView) findViewById(R.id.btn_prev);
        btnPlayPause = (ImageView) findViewById(R.id.btn_play_pause);
        btnNext = (ImageView) findViewById(R.id.btn_next);
        btnDetailPrev = (ImageView) findViewById(R.id.btn_detail_prev);
        btnDetailPlayPause = (ImageView) findViewById(R.id.btn_detail_play_pause);
        btnDetailNext = (ImageView) findViewById(R.id.btn_detail_next);

        btnToggleQueue = (Button) findViewById(R.id.btn_toggle_queue);
        btnCloseQueue = (Button) findViewById(R.id.btn_close_queue);
        btnCloseDetailQueue = (Button) findViewById(R.id.btn_close_detail_queue);

        flMiniCoverContainer = (FrameLayout) findViewById(R.id.fl_mini_cover_container);
        ivMiniCover = (ImageView) findViewById(R.id.iv_mini_cover);

        layoutConfigPanel = (LinearLayout) findViewById(R.id.layout_config_panel);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);

        tvListTitle = (TextView) findViewById(R.id.tv_list_title);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);

        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        listView = (ListView) findViewById(R.id.list_view);
        lvQueue = (ListView) findViewById(R.id.lv_queue);

        // 全新搜索独立页组件
        layoutSearchPageOverlay = (LinearLayout) findViewById(R.id.layout_search_page_overlay);
        layoutHistoryTagsFlow = (LinearLayout) findViewById(R.id.layout_history_tags_flow);
        btnSearchBack = (Button) findViewById(R.id.btn_search_back);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnFloatingAddToPlaylist = (Button) findViewById(R.id.btn_floating_add_to_playlist);
        spinnerSearchType = (Spinner) findViewById(R.id.spinner_search_type);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        btnClearSearchHistory = (ImageView) findViewById(R.id.btn_clear_search_history);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);

        // 详情页
        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailFav = (Button) findViewById(R.id.btn_detail_fav);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);

        btnLyricDec = (Button) findViewById(R.id.btn_lyric_dec);
        btnLyricInc = (Button) findViewById(R.id.btn_lyric_inc);

        layoutDetailResponsiveContent = (LinearLayout) findViewById(R.id.layout_detail_responsive_content);
        layoutLandscapeRightPanel = (LinearLayout) findViewById(R.id.layout_landscape_right_panel);
        layoutDetailQueuePanel = (LinearLayout) findViewById(R.id.layout_detail_queue_panel);
        lvDetailQueue = (ListView) findViewById(R.id.lv_detail_queue);

        layoutPortraitThreeLinesLyrics = (LinearLayout) findViewById(R.id.layout_portrait_three_lines_lyrics);
        tvLyricPrevLine = (TextView) findViewById(R.id.tv_lyric_prev_line);
        tvLyricCurrentLine = (TextView) findViewById(R.id.tv_lyric_current_line);
        tvLyricNextLine = (TextView) findViewById(R.id.tv_lyric_next_line);

        layoutVinylContainer = (FrameLayout) findViewById(R.id.layout_vinyl_container);
        flVinylDisc = (FrameLayout) findViewById(R.id.fl_vinyl_disc);
        ivVinylCircularCover = (ImageView) findViewById(R.id.iv_vinyl_circular_cover);
        viewTonearm = (TonearmView) findViewById(R.id.view_tonearm);
        ivSquareCover = (ImageView) findViewById(R.id.iv_square_cover);

        layoutCoverContainer = (LinearLayout) findViewById(R.id.layout_cover_container);
        layoutDetailSeekBox = (LinearLayout) findViewById(R.id.layout_detail_seek_box);
        layoutDetailControls = (LinearLayout) findViewById(R.id.layout_detail_controls);

        tvDetailTitle = (TextView) findViewById(R.id.tv_detail_title);
        tvDetailArtist = (TextView) findViewById(R.id.tv_detail_artist);
        tvDetailQuality = (TextView) findViewById(R.id.tv_detail_quality);
        tvDetailBufferStatus = (TextView) findViewById(R.id.tv_detail_buffer_status);
        tvDetailTime = (TextView) findViewById(R.id.tv_detail_time);
        detailSeekBar = (SeekBar) findViewById(R.id.detail_seek_bar);

        scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);
        layoutLyricsContainer = (LinearLayout) findViewById(R.id.layout_lyrics_container);

        setupPullToRefresh();
        applyResponsiveOrientation(getResources().getConfiguration().orientation);

        playlistAdapter = new CustomPlaylistAdapter();
        listView.setAdapter(playlistAdapter);

        searchAdapter = new SearchResultAdapter();
        lvSearchResults.setAdapter(searchAdapter);

        queueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvQueue.setAdapter(queueAdapter);

        detailQueueAdapter = new SimpleAdapter(this, queueData, android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"}, new int[]{android.R.id.text1, android.R.id.text2});
        lvDetailQueue.setAdapter(detailQueueAdapter);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsiveOrientation(newConfig.orientation);
    }

    private void applyResponsiveOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutDetailResponsiveContent.setOrientation(LinearLayout.VERTICAL);
            layoutLandscapeRightPanel.setVisibility(View.GONE);
            layoutPortraitThreeLinesLyrics.setVisibility(View.VISIBLE);
        } else {
            layoutDetailResponsiveContent.setOrientation(LinearLayout.HORIZONTAL);
            layoutLandscapeRightPanel.setVisibility(View.VISIBLE);
            layoutPortraitThreeLinesLyrics.setVisibility(View.GONE);
        }
    }

    private void setupVinylAnimation() {
        flVinylDisc.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        vinylRotateAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotateAnim.setDuration(16000);
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

    private void loadSearchHistory() {
        searchHistoryList.clear();
        String json = prefs.getString("search_history_arr", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                searchHistoryList.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
    }

    private void saveSearchHistory(String query) {
        if (query == null || query.trim().length() == 0) return;
        query = query.trim();
        searchHistoryList.remove(query);
        searchHistoryList.add(0, query);
        if (searchHistoryList.size() > 15) {
            searchHistoryList.remove(searchHistoryList.size() - 1);
        }
        JSONArray arr = new JSONArray(searchHistoryList);
        prefs.edit().putString("search_history_arr", arr.toString()).commit();
        renderSearchHistoryTags();
    }

    private void clearSearchHistory() {
        searchHistoryList.clear();
        prefs.edit().putString("search_history_arr", "[]").commit();
        renderSearchHistoryTags();
        Toast.makeText(this, "搜索历史已清空", Toast.LENGTH_SHORT).show();
    }

    private void renderSearchHistoryTags() {
        layoutHistoryTagsFlow.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int txtColor = isLightTheme ? 0xFF007ACC : 0xFF00E5FF;
        int bgRes = isLightTheme ? R.drawable.bg_pill_light : R.drawable.bg_btn_pill;

        for (final String keyword : searchHistoryList) {
            Button btn = new Button(this);
            btn.setText(keyword);
            btn.setTextSize(11);
            btn.setTextColor(txtColor);
            btn.setBackgroundResource(bgRes);
            btn.setPadding((int) (10 * density), (int) (2 * density), (int) (10 * density), (int) (2 * density));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (28 * density));
            lp.rightMargin = (int) (6 * density);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etSearchKeyword.setText(keyword);
                    performSearch(keyword);
                }
            });
            layoutHistoryTagsFlow.addView(btn);
        }
    }

    private class CustomPlaylistAdapter extends BaseAdapter {
        @Override public int getCount() { return currentItems.size(); }
        @Override public Object getItem(int pos) { return currentItems.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(final int pos, View convertView, ViewGroup parent) {
            ViewHolder h;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_playlist_row, parent, false);
                h = new ViewHolder();
                h.tvTitle = (TextView) convertView.findViewById(R.id.tv_item_title);
                h.tvSubtitle = (TextView) convertView.findViewById(R.id.tv_item_subtitle);
                h.btnMoreVert = (ImageView) convertView.findViewById(R.id.btn_item_more_vert);
                convertView.setTag(h);
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            final DisplayEntry item = currentItems.get(pos);
            h.tvTitle.setText(item.title);
            h.tvSubtitle.setText(item.subtitle);

            h.tvTitle.setTextColor(isLightTheme ? 0xFF1C202B : 0xFFFFFFFF);
            h.tvSubtitle.setTextColor(isLightTheme ? 0xFF60687B : 0xFF888C99);

            boolean canEdit = !item.isSong && !item.id.equals("fav_entry") && !item.id.equals("local_featured")
                    && !item.id.equals("local_car") && !item.title.contains("榜");

            if (canEdit) {
                h.btnMoreVert.setVisibility(View.VISIBLE);
                h.btnMoreVert.setImageDrawable(MediaIconHelper.createMoreVertIcon(MainActivity.this, 18, isLightTheme ? 0xFF60687B : 0xFFA0A5B5));
                h.btnMoreVert.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPlaylistActionDialog(item);
                    }
                });
            } else {
                h.btnMoreVert.setVisibility(View.GONE);
            }

            return convertView;
        }

        class ViewHolder {
            TextView tvTitle, tvSubtitle;
            ImageView btnMoreVert;
        }
    }

    private class SearchResultAdapter extends BaseAdapter {
        @Override public int getCount() { return currentSearchResults.size(); }
        @Override public Object getItem(int pos) { return currentSearchResults.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(final int pos, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_playlist_row, parent, false);
            }

            TextView tvTitle = (TextView) v.findViewById(R.id.tv_item_title);
            TextView tvSub = (TextView) v.findViewById(R.id.tv_item_subtitle);
            CheckBox cb = (CheckBox) v.findViewById(R.id.cb_song_select);
            ImageView btnMore = (ImageView) v.findViewById(R.id.btn_item_more_vert);

            btnMore.setVisibility(View.GONE);
            final DisplayEntry item = currentSearchResults.get(pos);

            tvTitle.setText(item.title);
            tvSub.setText(item.subtitle);
            tvTitle.setTextColor(isLightTheme ? 0xFF1C202B : 0xFFFFFFFF);
            tvSub.setTextColor(isLightTheme ? 0xFF60687B : 0xFF888C99);

            if (item.isSong) {
                cb.setVisibility(View.VISIBLE);
                cb.setOnCheckedChangeListener(null);
                cb.setChecked(selectedSearchSongIds.contains(item.id));
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            selectedSearchSongIds.add(item.id);
                        } else {
                            selectedSearchSongIds.remove(item.id);
                        }
                        updateFloatingAddButton();
                    }
                });
            } else {
                cb.setVisibility(View.GONE);
            }

            return v;
        }
    }

    private void updateFloatingAddButton() {
        int count = selectedSearchSongIds.size();
        if (count > 0) {
            btnFloatingAddToPlaylist.setText("➕ 添加已选歌曲到歌单 (" + count + ")");
            btnFloatingAddToPlaylist.setVisibility(View.VISIBLE);
        } else {
            btnFloatingAddToPlaylist.setVisibility(View.GONE);
        }
    }

    private void showPlaylistActionDialog(final DisplayEntry playlistEntry) {
        String[] options = new String[]{"✏️ 重命名歌单", "🗑️ 删除歌单", "➕ 添加歌曲到该歌单"};
        new AlertDialog.Builder(this)
                .setTitle("管理歌单: " + playlistEntry.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRenamePlaylistDialog(playlistEntry);
                        } else if (which == 1) {
                            confirmDeletePlaylist(playlistEntry);
                        } else if (which == 2) {
                            openSearchForSpecificPlaylist(playlistEntry);
                        }
                    }
                }).show();
    }

    private void showRenamePlaylistDialog(final DisplayEntry entry) {
        final EditText et = new EditText(this);
        et.setText(entry.title);
        et.setSelection(entry.title.length());

        new AlertDialog.Builder(this)
                .setTitle("重命名歌单")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newName = et.getText().toString().trim();
                        if (newName.length() > 0) {
                            updateServerPlaylistName(entry.id, newName);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeletePlaylist(final DisplayEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要从服务器彻底删除歌单【" + entry.title + "】吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteServerPlaylist(entry.id);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openSearchForSpecificPlaylist(DisplayEntry entry) {
        currentActivePlaylistId = entry.id;
        currentActivePlaylistName = entry.title;
        layoutSearchPageOverlay.setVisibility(View.VISIBLE);
        renderSearchHistoryTags();
    }

    private void updateServerPlaylistName(final String playlistId, final String newName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String p = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8") + "&name=" + URLEncoder.encode(newName, "UTF-8");
                    requestApi("updatePlaylist.view?" + p + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "歌单已重命名", Toast.LENGTH_SHORT).show();
                            fetchPlaylists();
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void deleteServerPlaylist(final String playlistId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    requestApi("deletePlaylist.view?id=" + URLEncoder.encode(playlistId, "UTF-8") + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "歌单已删除", Toast.LENGTH_SHORT).show();
                            fetchPlaylists();
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void addSongsToServerPlaylist(final String playlistId, final Set<String> songIds, final String plName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder sb = new StringBuilder("playlistId=").append(URLEncoder.encode(playlistId, "UTF-8"));
                    for (String sid : songIds) {
                        sb.append("&songIdToAdd=").append(URLEncoder.encode(sid, "UTF-8"));
                    }
                    requestApi("updatePlaylist.view?" + sb.toString() + "&" + getAuthParams());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "成功添加 " + songIds.size() + " 首歌曲至【" + plName + "】", Toast.LENGTH_SHORT).show();
                            selectedSearchSongIds.clear();
                            updateFloatingAddButton();
                            searchAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void showSelectPlaylistToAddDialog() {
        final ArrayList<DisplayEntry> targets = new ArrayList<DisplayEntry>();
        final ArrayList<String> names = new ArrayList<String>();

        for (DisplayEntry e : rawServerUserPlaylists) {
            targets.add(e);
            names.add(e.title);
        }

        if (targets.isEmpty()) {
            Toast.makeText(this, "暂无可操作的自建歌单", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要加入的自建歌单")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DisplayEntry target = targets.get(which);
                        addSongsToServerPlaylist(target.id, selectedSearchSongIds, target.title);
                    }
                }).show();
    }

    private void performSearch(final String query) {
        if (query.trim().length() == 0) return;
        saveSearchHistory(query);

        final int searchTypePos = spinnerSearchType.getSelectedItemPosition();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    String queryParams;
                    if (searchTypePos == 1) {
                        queryParams = "search3.view?query=" + encoded + "&artistCount=100&albumCount=0&songCount=0&" + getAuthParams();
                    } else if (searchTypePos == 2) {
                        queryParams = "search3.view?query=" + encoded + "&albumCount=100&artistCount=0&songCount=0&" + getAuthParams();
                    } else {
                        queryParams = "search3.view?query=" + encoded + "&songCount=500&artistCount=0&albumCount=0&" + getAuthParams();
                    }

                    final String jsonStr = requestApi(queryParams);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (jsonStr == null) return;
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject result = root.optJSONObject("searchResult3");
                                currentSearchResults.clear();
                                selectedSearchSongIds.clear();
                                updateFloatingAddButton();

                                if (result != null) {
                                    if (searchTypePos == 1 && result.has("artist")) {
                                        Object artObj = result.get("artist");
                                        if (artObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) artObj;
                                            for (int i = 0; i < arr.length(); i++) addArtistResult(arr.getJSONObject(i));
                                        } else if (artObj instanceof JSONObject) {
                                            addArtistResult((JSONObject) artObj);
                                        }
                                    } else if (searchTypePos == 2 && result.has("album")) {
                                        Object albObj = result.get("album");
                                        if (albObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) albObj;
                                            for (int i = 0; i < arr.length(); i++) addAlbumResult(arr.getJSONObject(i));
                                        } else if (albObj instanceof JSONObject) {
                                            addAlbumResult((JSONObject) albObj);
                                        }
                                    } else if (result.has("song")) {
                                        Object sObj = result.get("song");
                                        if (sObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) sObj;
                                            for (int i = 0; i < arr.length(); i++) addSongResult(arr.getJSONObject(i));
                                        } else if (sObj instanceof JSONObject) {
                                            addSongResult((JSONObject) sObj);
                                        }
                                    }
                                }
                                searchAdapter.notifyDataSetChanged();
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void addArtistResult(JSONObject a) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        int albumCount = a.optInt("albumCount", 0);
        currentSearchResults.add(new DisplayEntry("artist_" + id, name, "歌手", albumCount > 0 ? (albumCount + " 张专辑") : "歌手详情", null, "歌手", false));
    }

    private void addAlbumResult(JSONObject a) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        String artist = a.optString("artist", "未知艺术家");
        int songCount = a.optInt("songCount", 0);
        String coverArt = a.optString("coverArt", null);
        currentSearchResults.add(new DisplayEntry("album_" + id, name, artist, artist + (songCount > 0 ? (" • " + songCount + " 首") : ""), coverArt, "专辑", false));
    }

    private void addSongResult(JSONObject s) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知艺术家");
        String coverArt = s.optString("coverArt", null);
        int bitRate = s.optInt("bitRate", 0);
        String suffix = s.optString("suffix", "").toUpperCase();
        String quality = (bitRate > 0) ? (bitRate + "K " + suffix) : "320K MP3";

        currentSearchResults.add(new DisplayEntry(s.getString("id"), title, artist, artist + " [" + quality + "]", coverArt, quality, true));
    }

    private void setupListeners() {
        btnToggleTheme.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTheme(); }
        });

        btnOpenSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutConfigPanel.setVisibility(layoutConfigPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        btnExitApp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { performAppExit(); }
        });
        btnDetailExitApp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { performAppExit(); }
        });

        btnTopSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchPageOverlay.setVisibility(View.VISIBLE);
                renderSearchHistoryTags();
            }
        });

        btnSearchBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchPageOverlay.setVisibility(View.GONE);
            }
        });

        btnClearSearchHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearSearchHistory(); }
        });

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch(etSearchKeyword.getText().toString());
            }
        });

        btnFloatingAddToPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentActivePlaylistId != null && !currentActivePlaylistId.startsWith("fav_")
                        && !currentActivePlaylistId.startsWith("local_")) {
                    addSongsToServerPlaylist(currentActivePlaylistId, selectedSearchSongIds, currentActivePlaylistName);
                } else {
                    showSelectPlaylistToAddDialog();
                }
            }
        });

        flMiniCoverContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutDetailOverlay.setVisibility(View.VISIBLE);
            }
        });

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailOverlay.setVisibility(View.GONE); }
        });

        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshQueueList();
                layoutDetailQueuePanel.setVisibility(layoutDetailQueuePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                scrollQueueToCenter(lvDetailQueue);
            }
        });

        btnCloseDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { layoutDetailQueuePanel.setVisibility(View.GONE); }
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
                currentSelectedTab = TAB_PLAYLISTS;
                btnTabPlaylists.setTextColor(0xFF00E5FF);
                btnTabRanking.setTextColor(isLightTheme ? 0xFF60687B : 0xFFA0A5B5);
                btnBack.setVisibility(View.GONE);
                showUserPlaylists();
            }
        });

        btnTabRanking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedTab = TAB_RANKING;
                btnTabRanking.setTextColor(0xFF00E5FF);
                btnTabPlaylists.setTextColor(isLightTheme ? 0xFF60687B : 0xFFA0A5B5);
                btnBack.setVisibility(View.GONE);
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

        btnLyricDec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyLyricFontSize(-2); }
        });
        btnLyricInc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyLyricFontSize(2); }
        });

        View.OnClickListener favClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
                int idx = MusicService.getCurrentIndex();
                if (q != null && idx >= 0 && idx < q.size()) {
                    String sid = q.get(idx).id;
                    serverStarSong(sid, !isFav(sid));
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
                    downloadSongItem(new DisplayEntry(cur.id, cur.title, cur.artist, "", "", cur.quality, true));
                }
            }
        });

        View.OnClickListener coverToggleListener = new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCoverDisplayMode(); }
        };
        layoutVinylContainer.setOnClickListener(coverToggleListener);
        ivVinylCircularCover.setOnClickListener(coverToggleListener);
        viewTonearm.setOnClickListener(coverToggleListener);
        ivSquareCover.setOnClickListener(coverToggleListener);

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
            @Override public void onClick(View v) { layoutQueuePanel.setVisibility(View.GONE); }
        });

        btnDetailKeepScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isKeepScreenOn = !isKeepScreenOn;
                if (isKeepScreenOn) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮: 开");
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    btnDetailKeepScreen.setText("常亮: 关");
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int pos = position - listView.getHeaderViewsCount();
                if (pos < 0 || pos >= currentItems.size()) return;

                DisplayEntry entry = currentItems.get(pos);
                if (!entry.isSong) {
                    if (entry.id.startsWith("album_")) {
                        fetchAlbumSongs(entry.id.substring(6), entry.title);
                    } else if (entry.id.startsWith("artist_")) {
                        fetchArtistAlbums(entry.id.substring(7), entry.title);
                    } else {
                        fetchPlaylistSongs(entry.id, entry.title);
                    }
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

        lvSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= currentSearchResults.size()) return;
                DisplayEntry entry = currentSearchResults.get(position);
                if (entry.isSong) {
                    ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                    queue.add(new MusicService.SongItem(entry.id, entry.title, entry.artist, buildStreamUrl(entry.id), entry.coverArt, entry.quality));
                    MusicService.setQueue(queue, 0, MainActivity.this);
                    refreshQueueList();
                }
            }
        });

        AdapterView.OnItemClickListener queueClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY_INDEX);
                intent.putExtra("target_index", position);
                startService(intent);
            }
        };
        lvQueue.setOnItemClickListener(queueClickListener);
        lvDetailQueue.setOnItemClickListener(queueClickListener);

        View.OnTouchListener touchFeedbackListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.68f);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1.0f);
                }
                return false;
            }
        };

        btnPrev.setOnTouchListener(touchFeedbackListener);
        btnPlayPause.setOnTouchListener(touchFeedbackListener);
        btnNext.setOnTouchListener(touchFeedbackListener);
        btnDetailPrev.setOnTouchListener(touchFeedbackListener);
        btnDetailPlayPause.setOnTouchListener(touchFeedbackListener);
        btnDetailNext.setOnTouchListener(touchFeedbackListener);
        btnMode.setOnTouchListener(touchFeedbackListener);
        btnDetailMode.setOnTouchListener(touchFeedbackListener);
        btnOpenEq.setOnTouchListener(touchFeedbackListener);
        btnDetailEq.setOnTouchListener(touchFeedbackListener);
        btnExitApp.setOnTouchListener(touchFeedbackListener);
        btnDetailExitApp.setOnTouchListener(touchFeedbackListener);
        btnTopSearch.setOnTouchListener(touchFeedbackListener);

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
            }
        };
        btnPlayPause.setOnClickListener(toggleListener);
        btnDetailPlayPause.setOnClickListener(toggleListener);

        View.OnClickListener nextListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_NEXT));
            }
        };
        btnNext.setOnClickListener(nextListener);
        btnDetailNext.setOnClickListener(nextListener);

        View.OnClickListener prevListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_PREV));
            }
        };
        btnPrev.setOnClickListener(prevListener);
        btnDetailPrev.setOnClickListener(prevListener);

        View.OnClickListener modeListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_CYCLE_MODE));
                int nextMode = (MusicService.getCurrentMode() + 1) % 3;
                Toast.makeText(MainActivity.this, getModeString(nextMode), Toast.LENGTH_SHORT).show();
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
            @Override public void onStopTrackingTouch(SeekBar sb) {
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

    private void setupControlIcons() {
        int darkIconColor = 0xFF10141A;
        int lightIconColor = isLightTheme ? 0xFF4A5568 : 0xFFE2E8F0;
        int redIconColor = 0xFFFF6B6B;
        int cyanIconColor = isLightTheme ? 0xFF007ACC : 0xFF00E5FF;

        btnPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 18, lightIconColor));
        btnNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 18, lightIconColor));
        btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, darkIconColor));

        btnDetailPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, lightIconColor));
        btnDetailNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, lightIconColor));
        btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, darkIconColor));

        btnTopSearch.setImageDrawable(MediaIconHelper.createSearchIcon(this, 20, cyanIconColor));
        btnOpenSettings.setImageDrawable(MediaIconHelper.createSettingsIcon(this, 20, cyanIconColor));
        btnToggleTheme.setImageDrawable(MediaIconHelper.createThemeIcon(this, 20, cyanIconColor, isLightTheme));

        btnExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));
        btnDetailExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));

        btnOpenEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 18, cyanIconColor));
        btnDetailEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, cyanIconColor));
        btnClearSearchHistory.setImageDrawable(MediaIconHelper.createTrashIcon(this, 18, isLightTheme ? 0xFF60687B : 0xFFA0A5B5));

        updateModeIcons(MusicService.getCurrentMode());
    }

    private void updateModeIcons(int mode) {
        int iconColor = isLightTheme ? 0xFF007ACC : 0xFF00E5FF;
        if (mode == MusicService.MODE_SHUFFLE) {
            if (btnMode != null) btnMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 18, iconColor));
            if (btnDetailMode != null) btnDetailMode.setImageDrawable(MediaIconHelper.createShuffleIcon(this, 20, iconColor));
        } else if (mode == MusicService.MODE_SINGLE) {
            if (btnMode != null) btnMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 18, iconColor));
            if (btnDetailMode != null) btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatOneIcon(this, 20, iconColor));
        } else {
            if (btnMode != null) btnMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 18, iconColor));
            if (btnDetailMode != null) btnDetailMode.setImageDrawable(MediaIconHelper.createRepeatIcon(this, 20, iconColor));
        }
    }

    private void updatePlayPauseIcons(boolean isPlaying) {
        int darkIconColor = 0xFF10141A;
        if (isPlaying) {
            btnPlayPause.setImageDrawable(MediaIconHelper.createPauseIcon(this, 20, darkIconColor));
            btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPauseIcon(this, 26, darkIconColor));
        } else {
            btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, darkIconColor));
            btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, darkIconColor));
        }
    }

    private void updateLyricPosition(int currentPosMs) {
        if (lyricRows.isEmpty()) return;

        int targetIndex = -1;
        for (int i = 0; i < lyricRows.size(); i++) {
            if (currentPosMs >= lyricRows.get(i).timeMs) {
                targetIndex = i;
            } else {
                break;
            }
        }

        if (targetIndex >= 0 && targetIndex < lyricRows.size()) {
            tvLyricPrevLine.setText(targetIndex > 0 ? lyricRows.get(targetIndex - 1).text : "");
            tvLyricCurrentLine.setText(lyricRows.get(targetIndex).text);
            tvLyricNextLine.setText(targetIndex < lyricRows.size() - 1 ? lyricRows.get(targetIndex + 1).text : "");

            if (targetIndex != currentLyricIndex) {
                if (currentLyricIndex >= 0 && currentLyricIndex < lyricRows.size()) {
                    LyricRow oldRow = lyricRows.get(currentLyricIndex);
                    if (oldRow.view != null) {
                        oldRow.view.setTextColor(isLightTheme ? 0xFF60687B : 0xFF777777);
                        oldRow.view.setTextSize(lyricBaseFontSize);
                        oldRow.view.setTypeface(Typeface.DEFAULT);
                    }
                }

                currentLyricIndex = targetIndex;
                final LyricRow curRow = lyricRows.get(currentLyricIndex);
                if (curRow.view != null) {
                    curRow.view.setTextColor(isLightTheme ? 0xFF007ACC : 0xFF00E5FF);
                    curRow.view.setTextSize(lyricBaseFontSize + 5);
                    curRow.view.setTypeface(Typeface.DEFAULT_BOLD);

                    if (!isUserTouchingLyrics) {
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
    }

    private void restoreLastSessionIfAvailable() {
        if (MusicService.getPlaylist().isEmpty()) {
            boolean restored = MusicService.restorePlaybackState(this);
            if (restored) {
                refreshQueueList();
                int curIdx = MusicService.getCurrentIndex();
                ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
                if (curIdx >= 0 && curIdx < list.size()) {
                    MusicService.SongItem song = list.get(curIdx);
                    lastLoadedSongId = song.id;

                    tvCurrentSong.setText(song.title + " - " + song.artist);
                    tvDetailTitle.setText(song.title);
                    tvDetailArtist.setText(song.artist);
                    tvDetailQuality.setText(getBitrateDisplay(getSavedBitrate(), song.quality));

                    SharedPreferences sp = getSharedPreferences("retro_playback_state", MODE_PRIVATE);
                    int pos = sp.getInt("saved_position", 0);
                    int dur = sp.getInt("saved_duration", 0);

                    if (dur > 0) {
                        seekBar.setMax(dur);
                        seekBar.setProgress(pos);
                        detailSeekBar.setMax(dur);
                        detailSeekBar.setProgress(pos);
                        String tStr = formatTime(pos) + " / " + formatTime(dur);
                        tvTime.setText(tStr);
                        tvDetailTime.setText(tStr);
                    }

                    loadCoverArt(song.coverArtId != null ? song.coverArtId : song.id);
                    loadLyrics(song.id, song.artist, song.title);
                    updateFavButtonState(song.id);
                    updatePlayPauseIcons(false);
                    updateModeIcons(MusicService.getCurrentMode());
                }
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

            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
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
                                JSONObject playlistsObj = root.optJSONObject("playlists");
                                if (playlistsObj != null && playlistsObj.has("playlist")) {
                                    Object plObj = playlistsObj.get("playlist");
                                    if (plObj instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) plObj;
                                        for (int i = 0; i < arr.length(); i++) {
                                            categorizePlaylistItem(arr.getJSONObject(i));
                                        }
                                    } else if (plObj instanceof JSONObject) {
                                        categorizePlaylistItem((JSONObject) plObj);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        showCurrentTabContent();
                        stopRefreshing();
                    }
                });
            }
        }).start();
    }

    private void categorizePlaylistItem(JSONObject p) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        String id = p.getString("id");

        boolean isRanking = name.contains("榜单") || name.startsWith("榜") || name.endsWith("榜");
        DisplayEntry entry = new DisplayEntry(id, name, "", count + " 首歌曲", null, isRanking ? "官方排行榜" : "自建歌单", false);

        if (isRanking) {
            rawServerRankingPlaylists.add(entry);
        } else {
            rawServerUserPlaylists.add(entry);
        }
    }

    private void showCurrentTabContent() {
        if (currentSelectedTab == TAB_PLAYLISTS) {
            showUserPlaylists();
        } else {
            showRankingPlaylists();
        }
    }

    private void showUserPlaylists() {
        currentActivePlaylistId = null;
        currentActivePlaylistName = null;
        tvListTitle.setText("我的歌单");
        currentItems.clear();

        currentItems.add(new DisplayEntry("fav_entry", "♥  我的收藏", "云端同步", "已同步服务器标星 (" + favSongIds.size() + "首)", null, "云端歌单", false));
        currentItems.add(new DisplayEntry("local_featured", "⭐  精选歌单", "本地定制", "本地定制精选 (" + featuredSongs.size() + "首)", null, "本地歌单", false));
        currentItems.add(new DisplayEntry("local_car", "🚗  车载歌单", "本地定制", "出行必听车载曲库 (" + carSongs.size() + "首)", null, "本地歌单", false));

        for (DisplayEntry e : rawServerUserPlaylists) {
            currentItems.add(e);
        }

        playlistAdapter.notifyDataSetChanged();
    }

    private void showRankingPlaylists() {
        currentActivePlaylistId = null;
        currentActivePlaylistName = null;
        tvListTitle.setText("排行榜");
        currentItems.clear();

        for (DisplayEntry e : rawServerRankingPlaylists) {
            currentItems.add(e);
        }

        if (rawServerRankingPlaylists.isEmpty()) {
            currentItems.add(new DisplayEntry("empty", "暂无排行榜歌单", "", "连接的服务器暂未同步榜单数据", null, "", false));
        }

        playlistAdapter.notifyDataSetChanged();
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
            for (DisplayEntry e : featuredSongs) currentItems.add(e);
            playlistAdapter.notifyDataSetChanged();
            return;
        }

        if ("local_car".equals(playlistId)) {
            btnBack.setVisibility(View.VISIBLE);
            tvListTitle.setText("车载歌单");
            currentItems.clear();
            for (DisplayEntry e : carSongs) currentItems.add(e);
            playlistAdapter.notifyDataSetChanged();
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

                            if (playlist.has("entry")) {
                                Object entryObj = playlist.get("entry");
                                if (entryObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) entryObj;
                                    for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i));
                                } else if (entryObj instanceof JSONObject) {
                                    addSongRow((JSONObject) entryObj);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText(playlistName);
                            playlistAdapter.notifyDataSetChanged();
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

    private void fetchAlbumSongs(final String albumId, final String albumName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getAlbum.view?id=" + albumId + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) return;
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject album = root.getJSONObject("album");
                            currentItems.clear();

                            if (album.has("song")) {
                                Object songObj = album.get("song");
                                if (songObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) songObj;
                                    for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i));
                                } else if (songObj instanceof JSONObject) {
                                    addSongRow((JSONObject) songObj);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText("专辑: " + albumName);
                            playlistAdapter.notifyDataSetChanged();
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void fetchArtistAlbums(final String artistId, final String artistName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getArtist.view?id=" + artistId + "&" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) return;
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject artistObj = root.getJSONObject("artist");
                            currentItems.clear();

                            if (artistObj.has("album")) {
                                Object albObj = artistObj.get("album");
                                if (albObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) albObj;
                                    for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i));
                                } else if (albObj instanceof JSONObject) {
                                    addAlbumRow((JSONObject) albObj);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText("歌手: " + artistName);
                            playlistAdapter.notifyDataSetChanged();
                        } catch (Exception ignored) {}
                    }
                });
            }
        }).start();
    }

    private void addAlbumRow(JSONObject a) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        String artist = a.optString("artist", "未知艺术家");
        int songCount = a.optInt("songCount", 0);
        String coverArt = a.optString("coverArt", null);
        currentItems.add(new DisplayEntry("album_" + id, name, artist, artist + (songCount > 0 ? (" • " + songCount + " 首") : ""), coverArt, "专辑", false));
    }

    private void addSongRow(JSONObject s) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知艺术家");
        String coverArt = s.optString("coverArt", null);
        int bitRate = s.optInt("bitRate", 0);
        String suffix = s.optString("suffix", "").toUpperCase();
        String quality = (bitRate > 0) ? (bitRate + "K " + suffix) : "320K MP3";

        currentItems.add(new DisplayEntry(s.getString("id"), title, artist, artist + "  [" + quality + "]", coverArt, quality, true));
    }

    private void fetchServerFavoriteSongs() {
        btnBack.setVisibility(View.VISIBLE);
        tvListTitle.setText("歌单: 我的收藏 (云端同步)");
        currentItems.clear();
        playlistAdapter.notifyDataSetChanged();

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
                            stopRefreshing();
                            return;
                        }
                        try {
                            JSONObject root = new JSONObject(finalJson).getJSONObject("subsonic-response");
                            JSONObject starred = root.optJSONObject("starred2");
                            if (starred == null) starred = root.optJSONObject("starred");
                            currentItems.clear();
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
                            playlistAdapter.notifyDataSetChanged();
                            updateFavButtonState(null);
                        } catch (Exception ignored) {
                        } finally {
                            stopRefreshing();
                        }
                    }
                });
            }
        }).start();
    }

    private void loadCoverArt(final String coverId) {
        if (coverId == null || coverId.length() == 0) {
            ivVinylCircularCover.setImageResource(android.R.drawable.ic_menu_report_image);
            ivSquareCover.setImageResource(android.R.drawable.ic_menu_report_image);
            ivMiniCover.setImageResource(android.R.drawable.ic_menu_report_image);
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

                    if (conn instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                    }

                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                    byte[] imageBytes = baos.toByteArray();
                    baos.close();
                    is.close();
                    conn.disconnect();

                    if (imageBytes != null && imageBytes.length > 0) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);

                        opts.inSampleSize = calculateInSampleSize(opts, 300, 300);
                        opts.inJustDecodeBounds = false;
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;

                        final Bitmap safeDecodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);
                        if (safeDecodedBitmap != null) {
                            final Bitmap safeCircularBitmap = getCircularBitmap(safeDecodedBitmap, 240);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (currentRawCoverBitmap != null && !currentRawCoverBitmap.isRecycled()) currentRawCoverBitmap.recycle();
                                    if (currentCircularCoverBitmap != null && !currentCircularCoverBitmap.isRecycled()) currentCircularCoverBitmap.recycle();

                                    currentRawCoverBitmap = safeDecodedBitmap;
                                    currentCircularCoverBitmap = safeCircularBitmap;

                                    ivVinylCircularCover.setImageBitmap(currentCircularCoverBitmap);
                                    ivSquareCover.setImageBitmap(currentRawCoverBitmap);
                                    ivMiniCover.setImageBitmap(currentRawCoverBitmap);
                                }
                            });
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
        lyricRows.clear();
        currentLyricIndex = -1;
        layoutLyricsContainer.removeAllViews();
        tvLyricPrevLine.setText("");
        tvLyricCurrentLine.setText("歌词加载中...");
        tvLyricNextLine.setText("");

        new Thread(new Runnable() {
            @Override
            public void run() {
                String lyricsText = null;

                if (songId != null && songId.length() > 0) {
                    try {
                        String res = requestApi("getLyricsBySongId.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}

                    if (lyricsText == null) {
                        try {
                            String res = requestApi("getLyrics.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                            lyricsText = parseLyricsFromJson(res);
                        } catch (Throwable ignored) {}
                    }
                }

                if (lyricsText == null && title != null && title.length() > 0) {
                    try {
                        String p = "artist=" + URLEncoder.encode(artist != null ? artist : "", "UTF-8")
                                + "&title=" + URLEncoder.encode(title, "UTF-8");
                        String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}
                }

                final String finalLyrics = lyricsText;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalLyrics != null && finalLyrics.trim().length() > 0) {
                            buildLyricsView(finalLyrics);
                        } else {
                            tvLyricCurrentLine.setText("未找到匹配歌词");
                        }
                    }
                });
            }
        }).start();
    }

    private String parseLyricsFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.length() == 0) return null;
        try {
            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
            if (root.has("lyricsList")) {
                JSONObject list = root.optJSONObject("lyricsList");
                if (list != null && list.has("structuredLyrics")) {
                    Object slObj = list.get("structuredLyrics");
                    JSONObject targetSL = (slObj instanceof JSONArray) ? ((JSONArray) slObj).optJSONObject(0) : (JSONObject) slObj;
                    if (targetSL != null && targetSL.has("line")) {
                        Object lineObj = targetSL.get("line");
                        StringBuilder lrcBuilder = new StringBuilder();
                        if (lineObj instanceof JSONArray) {
                            JSONArray lArr = (JSONArray) lineObj;
                            for (int i = 0; i < lArr.length(); i++) appendStructuredLrcLine(lrcBuilder, lArr.getJSONObject(i));
                        } else if (lineObj instanceof JSONObject) {
                            appendStructuredLrcLine(lrcBuilder, (JSONObject) lineObj);
                        }
                        if (lrcBuilder.length() > 0) return lrcBuilder.toString();
                    }
                }
            }

            if (root.has("lyrics")) {
                Object lyricsObj = root.get("lyrics");
                if (lyricsObj instanceof JSONObject) {
                    return ((JSONObject) lyricsObj).optString("content", "");
                } else if (lyricsObj instanceof String) {
                    return (String) lyricsObj;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void appendStructuredLrcLine(StringBuilder sb, JSONObject l) {
        long startMs = l.optLong("start", 0);
        String val = l.optString("value", "");
        int sec = (int) ((startMs / 1000) % 60);
        int min = (int) ((startMs / (1000 * 60)) % 60);
        int cs = (int) ((startMs % 1000) / 10);
        sb.append(String.format("[%02d:%02d.%02d]", min, sec, cs)).append(val).append("\n");
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
                long timeMs = parseTime(line.substring(1, closeBracket));
                if (timeMs >= 0) {
                    String content = line.substring(closeBracket + 1).trim();
                    if (content.length() == 0) content = "···";
                    lyricRows.add(new LyricRow(timeMs, content));
                }
            }
        }

        Collections.sort(lyricRows, new Comparator<LyricRow>() {
            @Override public int compare(LyricRow a, LyricRow b) {
                return Long.valueOf(a.timeMs).compareTo(b.timeMs);
            }
        });

        int lyricColor = isLightTheme ? 0xFF60687B : 0xFF777777;
        for (LyricRow row : lyricRows) {
            TextView tv = new TextView(this);
            tv.setText(row.text);
            tv.setTextColor(lyricColor);
            tv.setTextSize(lyricBaseFontSize);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 12, 0, 12);
            row.view = tv;
            layoutLyricsContainer.addView(tv);
        }

        if (!lyricRows.isEmpty()) {
            tvLyricCurrentLine.setText(lyricRows.get(0).text);
            tvLyricNextLine.setText(lyricRows.size() > 1 ? lyricRows.get(1).text : "");
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

    private void applyLyricFontSize(int delta) {
        lyricBaseFontSize += delta;
        if (lyricBaseFontSize < 11) lyricBaseFontSize = 11;
        if (lyricBaseFontSize > 26) lyricBaseFontSize = 26;
        prefs.edit().putInt("lyric_font_size", lyricBaseFontSize).commit();

        for (int i = 0; i < lyricRows.size(); i++) {
            LyricRow row = lyricRows.get(i);
            if (row.view != null) {
                row.view.setTextSize(i == currentLyricIndex ? (lyricBaseFontSize + 5) : lyricBaseFontSize);
            }
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap, int targetSize) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        if (targetSize <= 0) targetSize = 240;

        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        float r = targetSize / 2f;
        canvas.drawCircle(r, r, r, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int minEdge = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Rect srcRect = new Rect((bitmap.getWidth() - minEdge) / 2, (bitmap.getHeight() - minEdge) / 2,
                (bitmap.getWidth() + minEdge) / 2, (bitmap.getHeight() + minEdge) / 2);
        Rect dstRect = new Rect(0, 0, targetSize, targetSize);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);

        return output;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
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
                        if (touchStartY == 0) touchStartY = event.getY();
                        float deltaY = event.getY() - touchStartY;
                        if (deltaY > 15 && isListViewAtTop()) {
                            isPulling = true;
                            int paddingTop = (int) (-refreshHeaderHeight + (deltaY * 0.45f));
                            refreshHeaderView.setPadding(0, paddingTop, 0, 0);
                            if (paddingTop >= 0) {
                                refreshTextView.setText("释放立即刷新");
                                refreshProgressBar.setVisibility(View.VISIBLE);
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

        if (btnBack.getVisibility() == View.VISIBLE && currentActivePlaylistId != null) {
            fetchPlaylistSongs(currentActivePlaylistId, currentActivePlaylistName);
        } else {
            fetchPlaylists();
            syncServerFavoritesQuietly();
        }
    }

    private void stopRefreshing() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
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

    private void setupBitrateSpinners() {
        BitrateSpinnerAdapter adapterConfig = new BitrateSpinnerAdapter(BITRATE_LABELS);
        BitrateSpinnerAdapter adapterDetail = new BitrateSpinnerAdapter(BITRATE_LABELS);

        spinnerConfigBitrate.setAdapter(adapterConfig);
        spinnerDetailBitrate.setAdapter(adapterDetail);

        int initialIndex = getBitrateIndex(getSavedBitrate());
        spinnerConfigBitrate.setSelection(initialIndex);
        spinnerDetailBitrate.setSelection(initialIndex);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSpinnersInitializing) return;
                String newBitrate = BITRATE_VALUES[position];
                String oldBitrate = getSavedBitrate();

                if (!newBitrate.equals(oldBitrate)) {
                    prefs.edit().putString("default_bitrate", newBitrate).commit();
                    if (parent == spinnerConfigBitrate) spinnerDetailBitrate.setSelection(position);
                    else spinnerConfigBitrate.setSelection(position);

                    tvDetailQuality.setText(getBitrateDisplay(newBitrate, ""));
                    onBitrateChanged(newBitrate, BITRATE_LABELS[position]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerConfigBitrate.setOnItemSelectedListener(listener);
        spinnerDetailBitrate.setOnItemSelectedListener(listener);

        new Handler().postDelayed(new Runnable() {
            @Override public void run() { isSpinnersInitializing = false; }
        }, 500);
    }

    private void setupSearchTypeSpinner() {
        BitrateSpinnerAdapter typeAdapter = new BitrateSpinnerAdapter(SEARCH_TYPES);
        spinnerSearchType.setAdapter(typeAdapter);
    }

    private void onBitrateChanged(String newBitrate, String label) {
        Toast.makeText(this, "播放码率已设为: " + label, Toast.LENGTH_SHORT).show();
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();

        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem currentSong = queue.get(curIdx);
            File cachedFile = CacheManager.getSongFile(MainActivity.this, currentSong.id);
            if (cachedFile.exists()) cachedFile.delete();

            currentSong.streamUrl = buildStreamUrl(currentSong.id, newBitrate);
            currentSong.quality = getBitrateDisplay(newBitrate, currentSong.quality);

            Intent intent = new Intent(MainActivity.this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY_INDEX);
            intent.putExtra("target_index", curIdx);
            startService(intent);
        }
    }

    private class BitrateSpinnerAdapter extends BaseAdapter {
        private String[] items;
        BitrateSpinnerAdapter(String[] items) { this.items = items; }
        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int pos) { return items[pos]; }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(6, 2, 6, 2);
            tv.setTextColor(isLightTheme ? 0xFF007ACC : 0xFF00E5FF);
            tv.setText(items[pos] + " ▾");
            return tv;
        }

        @Override
        public View getDropDownView(int pos, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(24, 18, 24, 18);
            tv.setBackgroundColor(isLightTheme ? 0xFFEBF1FA : 0xFF1E222B);
            tv.setTextColor(isLightTheme ? 0xFF1C202B : 0xFFE0E0E0);
            tv.setText(items[pos]);
            return tv;
        }
    }

    private void refreshQueueList() {
        ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
        int currentPlaying = MusicService.getCurrentIndex();
        queueData.clear();

        for (int i = 0; i < list.size(); i++) {
            MusicService.SongItem item = list.get(i);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", (i == currentPlaying ? ">> " : "") + (i + 1) + ". " + item.title);
            row.put("subtitle", item.artist);
            queueData.add(row);
        }
        queueAdapter.notifyDataSetChanged();
        detailQueueAdapter.notifyDataSetChanged();
    }

    private void scrollQueueToCenter(final ListView lv) {
        final int idx = MusicService.getCurrentIndex();
        if (idx >= 0 && lv != null) {
            lv.post(new Runnable() {
                @Override public void run() {
                    lv.setSelectionFromTop(idx, Math.max(0, lv.getHeight() / 2 - 35));
                }
            });
        }
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

    private String getDefaultDownloadPath() {
        try {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (musicDir != null) return musicDir.getAbsolutePath();
        } catch (Throwable ignored) {}
        return "/sdcard/Music";
    }

    private String getSavedBitrate() { return prefs.getString("default_bitrate", "auto"); }

    private int getBitrateIndex(String val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i].equalsIgnoreCase(val)) return i;
        }
        return 0;
    }

    private String getBitrateDisplay(String val, String originalQuality) {
        if ("auto".equalsIgnoreCase(val)) return (originalQuality != null && originalQuality.length() > 0) ? originalQuality : "原曲音质";
        if ("128".equalsIgnoreCase(val)) return "128K MP3";
        if ("192".equalsIgnoreCase(val)) return "192K MP3";
        if ("320".equalsIgnoreCase(val)) return "320K MP3";
        if ("flac".equalsIgnoreCase(val)) return "FLAC 无损";
        return (originalQuality != null && originalQuality.length() > 0) ? originalQuality : "原曲音质";
    }

    private String getModeString(int mode) {
        if (mode == MusicService.MODE_SHUFFLE) return "随机播放";
        if (mode == MusicService.MODE_SINGLE) return "单曲循环";
        return "列表循环";
    }

    private String buildStreamUrl(String songId) { return buildStreamUrl(songId, getSavedBitrate()); }

    private String buildStreamUrl(String songId, String bitrate) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");

        String bitrateParam = "";
        if ("128".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=128";
        else if ("192".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=192";
        else if ("320".equalsIgnoreCase(bitrate)) bitrateParam = "&maxBitRate=320";
        else if ("flac".equalsIgnoreCase(bitrate)) bitrateParam = "&format=flac";

        try {
            return base + "/rest/stream.view?id=" + URLEncoder.encode(songId, "UTF-8")
                    + "&u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8")
                    + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
        } catch (Exception e) {
            return base + "/rest/stream.view?id=" + songId + "&u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
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
    }

    private void loadFavSet() {
        Set<String> set = prefs.getStringSet("fav_songs_set", new HashSet<String>());
        favSongIds = new HashSet<String>(set);
    }

    private void saveFavSet() { prefs.edit().putStringSet("fav_songs_set", favSongIds).commit(); }

    private boolean isFav(String songId) { return songId != null && favSongIds.contains(songId); }

    private void updateFavButtonState(String currentPlayingSongId) {
        String targetId = currentPlayingSongId;
        if (targetId == null || targetId.length() == 0) {
            ArrayList<MusicService.SongItem> q = MusicService.getPlaylist();
            int idx = MusicService.getCurrentIndex();
            if (q != null && idx >= 0 && idx < q.size()) targetId = q.get(idx).id;
        }
        boolean fav = isFav(targetId);
        String symbol = fav ? "♥" : "♡";
        if (btnBottomFav != null) btnBottomFav.setText(symbol);
        if (btnDetailFav != null) btnDetailFav.setText(symbol);
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
                        playlistAdapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
        }
        saveFavSet();
        updateFavButtonState(songId);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String endpoint = toStar ? "star.view" : "unstar.view";
                    requestApi(endpoint + "?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void syncServerFavoritesQuietly() {
        new Thread(new Runnable() {
            @Override public void run() {
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
                            for (int i = 0; i < arr.length(); i++) favSongIds.add(arr.getJSONObject(i).getString("id"));
                        } else if (songObj instanceof JSONObject) {
                            favSongIds.add(((JSONObject) songObj).getString("id"));
                        }
                        saveFavSet();
                        runOnUiThread(new Runnable() {
                            @Override public void run() { updateFavButtonState(null); }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void loadLocalPlaylists() {
        featuredSongs.clear();
        carSongs.clear();
        try {
            JSONArray fArr = new JSONArray(prefs.getString("local_playlist_featured", "[]"));
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.getJSONObject(i);
                featuredSongs.add(new DisplayEntry(o.getString("id"), o.getString("title"), o.optString("artist", "未知歌手"), "", o.optString("coverArt", null), o.optString("quality", "标准音质"), true));
            }

            JSONArray cArr = new JSONArray(prefs.getString("local_playlist_car", "[]"));
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

    private void showSongLongClickMenu(final DisplayEntry entry, final int position) {
        if (!entry.isSong) return;
        final boolean fav = isFav(entry.id);

        String[] options = new String[]{
                fav ? "★ 已收藏（点此从云端取消）" : "☆ 收藏歌曲 (同步云端)",
                "📁 添加到歌单 (精选/车载/自建歌单)",
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
        final ArrayList<String> names = new ArrayList<String>();
        names.add("♥ 我的收藏 (云端)");
        names.add("⭐ 精选歌单 (本地)");
        names.add("🚗 车载歌单 (本地)");

        for (DisplayEntry e : rawServerUserPlaylists) {
            names.add("📁 " + e.title + " (云端)");
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要加入的歌单")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (!isFav(entry.id)) serverStarSong(entry.id, true);
                        } else if (which == 1) {
                            addSongToLocalList(featuredSongs, entry, "精选歌单");
                        } else if (which == 2) {
                            addSongToLocalList(carSongs, entry, "车载歌单");
                        } else {
                            DisplayEntry targetPl = rawServerUserPlaylists.get(which - 3);
                            addSongsToServerPlaylist(targetPl.id, Collections.singleton(entry.id), targetPl.title);
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
        } else if (currentActivePlaylistId != null && !currentActivePlaylistId.startsWith("local_")) {
            // 云端歌单移除
            removeSongFromServerPlaylist(currentActivePlaylistId, position);
        }

        if (position >= 0 && position < currentItems.size()) {
            currentItems.remove(position);
            playlistAdapter.notifyDataSetChanged();
            Toast.makeText(this, "已从当前列表移出", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeSongFromServerPlaylist(final String playlistId, final int songIndex) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String p = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8") + "&songIndexToRemove=" + songIndex;
                    requestApi("updatePlaylist.view?" + p + "&" + getAuthParams());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void setupClickInterceptors() {
        View.OnClickListener consume = new View.OnClickListener() { @Override public void onClick(View v) {} };
        layoutDetailOverlay.setOnClickListener(consume);
        layoutQueuePanel.setOnClickListener(consume);
        layoutConfigPanel.setOnClickListener(consume);
        layoutSearchPageOverlay.setOnClickListener(consume);
        layoutDetailQueuePanel.setOnClickListener(consume);
    }

    private void downloadSongItem(final DisplayEntry entry) {
        String customPath = prefs.getString("download_path", getDefaultDownloadPath());
        final File saveDir = new File(customPath);
        if (!saveDir.exists()) saveDir.mkdirs();

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

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private boolean copyFile(File src, File dest) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len);
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
                while ((l = is.read(temp)) != -1) sb.append(new String(temp, 0, l, "UTF-8"));
                conn.disconnect();
                JSONObject root = new JSONObject(sb.toString());
                String directUrl = findAudioUrlInJson(root);
                if (directUrl != null) return downloadWithRedirects(directUrl, destFile, depth + 1);
                throw new Exception("返回的 JSON 中未找到下载链接");
            }

            FileOutputStream fos = new FileOutputStream(destFile);
            fos.write(preview, 0, r);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
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
                    if (strVal.startsWith("http://") || strVal.startsWith("https://")) return strVal;
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

    private void performAppExit() {
        new AlertDialog.Builder(this)
                .setTitle("关闭软件")
                .setMessage("确定要退出并彻底关闭播放器吗？")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            stopService(new Intent(MainActivity.this, MusicService.class).setAction(MusicService.ACTION_STOP));
                        } catch (Exception ignored) {}
                        finish();
                        System.exit(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
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
            if (layoutSearchPageOverlay != null && layoutSearchPageOverlay.getVisibility() == View.VISIBLE) {
                layoutSearchPageOverlay.setVisibility(View.GONE);
                return true;
            }
            if (layoutDetailOverlay != null && layoutDetailOverlay.getVisibility() == View.VISIBLE) {
                layoutDetailOverlay.setVisibility(View.GONE);
                return true;
            }
            if (layoutDetailQueuePanel != null && layoutDetailQueuePanel.getVisibility() == View.VISIBLE) {
                layoutDetailQueuePanel.setVisibility(View.GONE);
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
        }
        return super.onKeyDown(keyCode, event);
    }
}
