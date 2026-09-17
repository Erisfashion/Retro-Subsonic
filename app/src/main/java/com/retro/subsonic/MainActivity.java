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

    private void setupClickInterceptors() {
        View.OnClickListener consume = new View.OnClickListener() { @Override public void onClick(View v) {} };
        layoutDetailOverlay.setOnClickListener(consume);
        layoutQueuePanel.setOnClickListener(consume);
        layoutConfigPanel.setOnClickListener(consume);
        layoutSearchPageOverlay.setOnClickListener(consume);
        layoutDetailQueuePanel.setOnClickListener(consume);
    }

    private void downloadSongItem(final DisplayEntry entry) {
        Toast.makeText(this, "正在准备下载: " + entry.title, Toast.LENGTH_SHORT).show();
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
