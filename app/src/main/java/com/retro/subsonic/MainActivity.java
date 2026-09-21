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
import android.graphics.RectF;
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
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import java.util.LinkedHashMap;
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

    private EditText etServer, etUsername, etPassword, etSearchKeyword, etCacheSize;
    private EditText etTimeoutSec, etRetryCount, etDownloadPath;
    private Spinner spinnerConfigBitrate, spinnerDetailBitrate, spinnerSearchType;
    private boolean isSpinnersInitializing = true;

    private Button btnConnect, btnClearCache, btnToggleConfig, btnTabPlaylists, btnTabRanking, btnSearchSubmit, btnBack;
    private ImageView btnOpenEq, btnDetailEq;
    private ImageView btnMode, btnDetailMode;
    private ImageView btnPrev, btnPlayPause, btnNext;
    private ImageView btnDetailPrev, btnDetailPlayPause, btnDetailNext;
    private ImageView btnExitApp, btnDetailExitApp, btnTopSearch;
    private ImageView ivBottomCover;
    private Button btnToggleQueue, btnCloseQueue;
    private Button btnBottomFav, btnDetailFav, btnDetailDownload, btnDetailDlna;
    private Button btnLyricDec, btnLyricInc;
    private LinearLayout layoutConfigPanel, layoutQueuePanel, layoutDetailOverlay, layoutBottomPlayer;
    private TextView tvListTitle, tvCurrentSong, tvTime, tvCacheUsed;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    private LinearLayout layoutSearchOverlay;
    private Button btnSearchPageBack;
    private Button btnToggleSearchHistory;
    private ImageView btnClearSearchHistory;
    private LinearLayout layoutSearchHistoryBox, layoutSearchResultBox;
    private TextView tvSearchResultTitle;
    private CheckBox cbDedupSongs;
    private ListView lvSearchHistory, lvSearchResults;
    private ArrayList<String> searchHistoryList = new ArrayList<String>();
    private ArrayAdapter<String> searchHistoryAdapter;
    private boolean isSearchHistoryCollapsed = false;

    private String targetPlaylistIdForAdd = null;
    private String targetPlaylistNameForAdd = null;

    private ArrayList<DisplayEntry> searchResultsList = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> searchResultsData = new ArrayList<Map<String, String>>();
    private SimpleAdapter searchResultsAdapter;

    private ArrayList<DisplayEntry> rawSearchSongResults = new ArrayList<DisplayEntry>();
    private String lastSearchKeyword = "";

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
    private LinearLayout layoutCoverContainer, layoutDetailSeekBox, layoutDetailControls, layoutDetailBottomBlank;
    private TextView tvDetailTitle, tvDetailArtist, tvDetailQuality, tvDetailBuffer, tvDetailTime;
    private SeekBar detailSeekBar;
    private LinearLayout layoutDetailLyricsView, layoutDetailQueueView;
    private ListView lvDetailQueue;

    private boolean isVinylDisplayMode = true;
    private Bitmap currentRawCoverBitmap;
    private Bitmap currentCircularCoverBitmap;
    private Bitmap currentBottomCoverBitmap;

    private RotateAnimation vinylRotateAnim;
    private boolean isCurrentSongPlaying = false;
    private boolean isKeepScreenOn = false;

    private ScrollView scrollLyrics;
    private LinearLayout layoutLyricsContainer;
    private Handler lyricHandler = new Handler();
    private boolean isUserTouchingLyrics = false;
    private int currentLyricIndex = -1;
    private int lyricBaseFontSize = 15;
    private int lyricTimeOffsetMs = 0;

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
        int bitRateNumeric;

        DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong) {
            this(id, title, artist, subtitle, coverArt, quality, isSong, 0);
        }

        DisplayEntry(String id, String title, String artist, String subtitle, String coverArt, String quality, boolean isSong, int bitRateNumeric) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.subtitle = subtitle;
            this.coverArt = coverArt;
            this.quality = quality;
            this.isSong = isSong;
            this.bitRateNumeric = bitRateNumeric;
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
                String streamUrl = intent.getStringExtra("streamUrl");

                if (title != null) {
                    tvDetailTitle.setText(title);

                    if (retryCount > 0) {
                        tvCurrentSong.setText("重试连接中 (" + retryCount + "/" + maxRetries + "): " + title);
                        tvDetailBuffer.setText("(重试中 " + retryCount + "/" + maxRetries + ")");
                        tvDetailBuffer.setVisibility(View.VISIBLE);
                    } else if (isPlaying) {
                        if (isBuffering && bufferPercent < 100) {
                            tvCurrentSong.setText(title + " - " + artist + " (缓冲 " + bufferPercent + "%)");
                            tvDetailBuffer.setText("(缓冲 " + bufferPercent + "%)");
                            tvDetailBuffer.setVisibility(View.VISIBLE);
                        } else {
                            tvCurrentSong.setText(title + " - " + artist);
                            tvDetailBuffer.setVisibility(View.GONE);
                        }
                    } else if (isBuffering) {
                        tvCurrentSong.setText("正在解析缓冲 (" + bufferPercent + "%): " + title);
                        tvDetailBuffer.setText("(起播中 " + bufferPercent + "%)");
                        tvDetailBuffer.setVisibility(View.VISIBLE);
                    } else {
                        tvCurrentSong.setText(title + " - " + artist);
                        tvDetailBuffer.setVisibility(View.GONE);
                    }

                    tvDetailArtist.setText(artist);
                    String currentBitrate = getSavedBitrate();
                    tvDetailQuality.setText(getBitrateDisplay(currentBitrate, quality));

                    if (songId != null && !songId.equals(lastLoadedSongId)) {
                        lastLoadedSongId = songId;
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(songId, artist, title);
                        refreshQueueList();
                        updateCacheSizeDisplay();

                        if (DlnaManager.isCasting() && streamUrl != null && streamUrl.length() > 0) {
                            startDlnaCastQuietly(DlnaManager.getCurrentDevice(), streamUrl, title, artist, 0);
                        }
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

                    int effectivePos = position + lyricTimeOffsetMs;
                    if (DlnaManager.isCasting()) {
                        effectivePos -= 2500;
                    }
                    updateLyricPosition(effectivePos);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        TLSSocketFactory.install();

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);
        lyricBaseFontSize = prefs.getInt("lyric_font_size", 15);
        isVinylDisplayMode = prefs.getBoolean("is_vinyl_display_mode", true);

        loadFavSet();
        loadLocalPlaylists();
        loadSearchHistory();

        initViews();
        setupControlIcons();
        setupBitrateSpinners();
        setupSearchTypeSpinner();
        setupVinylAnimation();
        updateCoverDisplayMode();
        loadSavedConfig();
        setupListeners();
        setupClickInterceptors();
        updateCacheSizeDisplay();

        restoreLastSessionIfAvailable();

        fetchPlaylists();
        syncServerFavoritesQuietly();
    }

    private void updateCacheSizeDisplay() {
        if (tvCacheUsed == null) return;
        long bytes = CacheManager.getUsedCacheBytes(this);
        double mb = bytes / (1024.0 * 1024.0);
        tvCacheUsed.setText(String.format("(已缓存 %.1f MB)", mb));
    }

    private void loadSearchHistory() {
        searchHistoryList.clear();
        String json = prefs.getString("search_history_json", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                searchHistoryList.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
    }

    private void saveSearchHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (String kw : searchHistoryList) {
                arr.put(kw);
            }
            prefs.edit().putString("search_history_json", arr.toString()).commit();
        } catch (Exception ignored) {}
    }

    private void addSearchHistory(String kw) {
        if (kw == null || kw.trim().length() == 0) return;
        String clean = kw.trim();
        searchHistoryList.remove(clean);
        searchHistoryList.add(0, clean);
        if (searchHistoryList.size() > 20) {
            searchHistoryList.remove(searchHistoryList.size() - 1);
        }
        saveSearchHistory();
        if (searchHistoryAdapter != null) searchHistoryAdapter.notifyDataSetChanged();
    }

    private void removeSingleSearchHistory(final String kw) {
        new AlertDialog.Builder(this)
                .setTitle("删除搜索历史")
                .setMessage("确定删除关键词 \"" + kw + "\" 吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        searchHistoryList.remove(kw);
                        saveSearchHistory();
                        if (searchHistoryAdapter != null) searchHistoryAdapter.notifyDataSetChanged();
                        if (searchHistoryList.isEmpty()) {
                            layoutSearchHistoryBox.setVisibility(View.GONE);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptClearSearchHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清空历史记录")
                .setMessage("确定要清空全部搜索历史记录吗？")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        searchHistoryList.clear();
                        saveSearchHistory();
                        if (searchHistoryAdapter != null) searchHistoryAdapter.notifyDataSetChanged();
                        layoutSearchHistoryBox.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "搜索历史已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

    private String getSavedBitrate() {
        return prefs.getString("default_bitrate", "auto");
    }

    private int getBitrateIndex(String val) {
        for (int i = 0; i < BITRATE_VALUES.length; i++) {
            if (BITRATE_VALUES[i].equalsIgnoreCase(val)) {
                return i;
            }
        }
        return 0;
    }

    private String getBitrateDisplay(String val, String originalQuality) {
        if ("auto".equalsIgnoreCase(val)) {
            if (originalQuality != null && originalQuality.length() > 0) {
                return originalQuality;
            }
            return "原曲音质";
        }
        if ("128".equalsIgnoreCase(val)) return "128K MP3";
        if ("192".equalsIgnoreCase(val)) return "192K MP3";
        if ("320".equalsIgnoreCase(val)) return "320K MP3";
        if ("flac".equalsIgnoreCase(val)) return "FLAC 无损";
        return (originalQuality != null && originalQuality.length() > 0) ? originalQuality : "原曲音质";
    }

    private class BitrateSpinnerAdapter extends BaseAdapter {
        private String[] items;

        BitrateSpinnerAdapter(String[] items) {
            this.items = items;
        }

        @Override
        public int getCount() { return items.length; }

        @Override
        public Object getItem(int position) { return items[position]; }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(6, 2, 6, 2);
            tv.setTextColor(0xFF00E5FF);
            tv.setText(items[position] + " ▾");
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(MainActivity.this);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(24, 18, 24, 18);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setTextColor(0xFFE0E0E0);
            tv.setText(items[position]);
            return tv;
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
                if (isSpinnersInitializing) {
                    return;
                }
                String newBitrate = BITRATE_VALUES[position];
                String oldBitrate = getSavedBitrate();

                if (!newBitrate.equals(oldBitrate)) {
                    prefs.edit().putString("default_bitrate", newBitrate).commit();

                    if (parent == spinnerConfigBitrate) {
                        spinnerDetailBitrate.setSelection(position);
                    } else {
                        spinnerConfigBitrate.setSelection(position);
                    }

                    ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
                    int curIdx = MusicService.getCurrentIndex();
                    String origQuality = "";
                    if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
                        origQuality = queue.get(curIdx).quality;
                    }

                    tvDetailQuality.setText(getBitrateDisplay(newBitrate, origQuality));
                    onBitrateChanged(newBitrate, BITRATE_LABELS[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerConfigBitrate.setOnItemSelectedListener(listener);
        spinnerDetailBitrate.setOnItemSelectedListener(listener);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                isSpinnersInitializing = false;
            }
        }, 500);
    }

    private void setupSearchTypeSpinner() {
        BitrateSpinnerAdapter typeAdapter = new BitrateSpinnerAdapter(SEARCH_TYPES);
        spinnerSearchType.setAdapter(typeAdapter);
        spinnerSearchType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    etSearchKeyword.setHint("输入歌曲名检索...");
                    if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.VISIBLE);
                } else if (position == 1) {
                    etSearchKeyword.setHint("输入歌手名检索...");
                    if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.GONE);
                } else if (position == 2) {
                    etSearchKeyword.setHint("输入专辑名检索...");
                    if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.GONE);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void onBitrateChanged(String newBitrate, String label) {
        Toast.makeText(this, "播放码率已设为: " + label, Toast.LENGTH_SHORT).show();

        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();

        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem currentSong = queue.get(curIdx);

            File cachedFile = CacheManager.getSongFile(MainActivity.this, currentSong.id);
            if (cachedFile.exists()) {
                cachedFile.delete();
            }

            currentSong.streamUrl = buildStreamUrl(currentSong.id, newBitrate);
            currentSong.quality = getBitrateDisplay(newBitrate, currentSong.quality);

            Intent intent = new Intent(MainActivity.this, MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY_INDEX);
            intent.putExtra("target_index", curIdx);
            startService(intent);
        }
    }

    private void setupControlIcons() {
        int darkIconColor = 0xFF10141A;
        int lightIconColor = 0xFFE2E8F0;
        int redIconColor = 0xFFFF6B6B;
        int cyanIconColor = 0xFF00E5FF;

        btnPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 18, lightIconColor));
        btnNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 18, lightIconColor));
        btnPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 22, darkIconColor));

        btnDetailPrev.setImageDrawable(MediaIconHelper.createPreviousIcon(this, 22, lightIconColor));
        btnDetailNext.setImageDrawable(MediaIconHelper.createNextIcon(this, 22, lightIconColor));
        btnDetailPlayPause.setImageDrawable(MediaIconHelper.createPlayIcon(this, 28, darkIconColor));

        btnTopSearch.setImageDrawable(MediaIconHelper.createSearchIcon(this, 20, cyanIconColor));

        btnExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));
        btnDetailExitApp.setImageDrawable(MediaIconHelper.createPowerIcon(this, 18, redIconColor));

        btnOpenEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 18, cyanIconColor));
        btnDetailEq.setImageDrawable(MediaIconHelper.createEqualizerIcon(this, 20, cyanIconColor));

        btnClearSearchHistory.setImageDrawable(MediaIconHelper.createTrashIcon(this, 18, 0xFFA0A5B5));

        updateModeIcons(MusicService.getCurrentMode());
    }

    private void updateModeIcons(int mode) {
        int iconColor = 0xFF00E5FF;
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
            if (layoutSearchOverlay != null && layoutSearchOverlay.getVisibility() == View.VISIBLE) {
                layoutSearchOverlay.setVisibility(View.GONE);
                targetPlaylistIdForAdd = null;
                targetPlaylistNameForAdd = null;
                return true;
            }
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

        spinnerConfigBitrate = (Spinner) findViewById(R.id.spinner_config_bitrate);
        spinnerDetailBitrate = (Spinner) findViewById(R.id.spinner_detail_bitrate);
        spinnerSearchType = (Spinner) findViewById(R.id.spinner_search_type);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnToggleConfig = (Button) findViewById(R.id.btn_toggle_config);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabRanking = (Button) findViewById(R.id.btn_tab_ranking);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnBottomFav = (Button) findViewById(R.id.btn_bottom_fav);
        tvCacheUsed = (TextView) findViewById(R.id.tv_cache_used);

        btnExitApp = (ImageView) findViewById(R.id.btn_exit_app);
        btnDetailExitApp = (ImageView) findViewById(R.id.btn_detail_exit_app);
        btnTopSearch = (ImageView) findViewById(R.id.btn_top_search);

        ivBottomCover = (ImageView) findViewById(R.id.iv_bottom_cover);

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

        layoutConfigPanel = (LinearLayout) findViewById(R.id.layout_config_panel);
        layoutQueuePanel = (LinearLayout) findViewById(R.id.layout_queue_panel);
        layoutDetailOverlay = (LinearLayout) findViewById(R.id.layout_detail_overlay);
        layoutBottomPlayer = (LinearLayout) findViewById(R.id.layout_bottom_player);

        layoutSearchOverlay = (LinearLayout) findViewById(R.id.layout_search_overlay);
        btnSearchPageBack = (Button) findViewById(R.id.btn_search_page_back);
        btnToggleSearchHistory = (Button) findViewById(R.id.btn_toggle_search_history);
        btnClearSearchHistory = (ImageView) findViewById(R.id.btn_clear_search_history);
        layoutSearchHistoryBox = (LinearLayout) findViewById(R.id.layout_search_history_box);
        layoutSearchResultBox = (LinearLayout) findViewById(R.id.layout_search_result_box);
        tvSearchResultTitle = (TextView) findViewById(R.id.tv_search_result_title);
        cbDedupSongs = (CheckBox) findViewById(R.id.cb_dedup_songs);
        lvSearchHistory = (ListView) findViewById(R.id.lv_search_history);
        lvSearchResults = (ListView) findViewById(R.id.lv_search_results);

        searchHistoryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, searchHistoryList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(0xFFCBD5E1);
                tv.setTextSize(13);
                tv.setPadding(16, 12, 16, 12);
                return tv;
            }
        };
        lvSearchHistory.setAdapter(searchHistoryAdapter);

        searchResultsAdapter = new SimpleAdapter(
                this,
                searchResultsData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        lvSearchResults.setAdapter(searchResultsAdapter);

        tvListTitle = (TextView) findViewById(R.id.tv_list_title);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);

        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        listView = (ListView) findViewById(R.id.list_view);
        lvQueue = (ListView) findViewById(R.id.lv_queue);

        btnCloseDetail = (Button) findViewById(R.id.btn_close_detail);
        btnDetailFav = (Button) findViewById(R.id.btn_detail_fav);
        btnDetailDownload = (Button) findViewById(R.id.btn_detail_download);
        btnDetailDlna = (Button) findViewById(R.id.btn_detail_dlna);
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
        tvDetailBuffer = (TextView) findViewById(R.id.tv_detail_buffer);
        tvDetailTime = (TextView) findViewById(R.id.tv_detail_time);
        detailSeekBar = (SeekBar) findViewById(R.id.detail_seek_bar);

        layoutDetailLyricsView = (LinearLayout) findViewById(R.id.layout_detail_lyrics_view);
        layoutDetailQueueView = (LinearLayout) findViewById(R.id.layout_detail_queue_view);
        lvDetailQueue = (ListView) findViewById(R.id.lv_detail_queue);

        scrollLyrics = (ScrollView) findViewById(R.id.scroll_lyrics);
        layoutLyricsContainer = (LinearLayout) findViewById(R.id.layout_lyrics_container);

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
        layoutSearchOverlay.setOnClickListener(consumeListener);
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
        etTimeoutSec.setText(prefs.getString("play_timeout_sec", "30"));
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
        return buildStreamUrl(songId, getSavedBitrate());
    }

    private String buildStreamUrl(String songId, String bitrate) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");

        String bitrateParam = "";
        if ("128".equalsIgnoreCase(bitrate)) {
            bitrateParam = "&maxBitRate=128";
        } else if ("192".equalsIgnoreCase(bitrate)) {
            bitrateParam = "&maxBitRate=192";
        } else if ("320".equalsIgnoreCase(bitrate)) {
            bitrateParam = "&maxBitRate=320";
        } else if ("flac".equalsIgnoreCase(bitrate)) {
            bitrateParam = "&format=flac";
        } else {
            bitrateParam = "";
        }

        try {
            String encodedId = URLEncoder.encode(songId, "UTF-8");
            return base + "/rest/stream.view?id=" + encodedId + "&u=" + URLEncoder.encode(u, "UTF-8") + "&p=" + URLEncoder.encode(p, "UTF-8") + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
        } catch (Exception e) {
            return base + "/rest/stream.view?id=" + songId + "&u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic" + bitrateParam;
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

        ArrayList<String> optList = new ArrayList<String>();
        if (targetPlaylistIdForAdd != null && targetPlaylistNameForAdd != null) {
            optList.add("★ 加入指定歌单: " + targetPlaylistNameForAdd);
        }
        optList.add(fav ? "★ 已收藏（点此从云端取消）" : "☆ 收藏歌曲 (同步云端)");
        optList.add("📁 添加到歌单 (云端/本地)");
        optList.add("🗑 移出当前列表");
        optList.add("⬇ 下载歌曲到本地");

        final String[] options = optList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(entry.title + " - " + entry.artist)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String opt = options[which];
                        if (opt.startsWith("★ 加入指定歌单")) {
                            addSongToServerPlaylist(targetPlaylistIdForAdd, entry.id, targetPlaylistNameForAdd);
                        } else if (opt.contains("收藏歌曲") || opt.contains("已收藏")) {
                            serverStarSong(entry.id, !fav);
                        } else if (opt.contains("添加到歌单")) {
                            showAddToPlaylistDialog(entry);
                        } else if (opt.contains("移出当前列表")) {
                            removeFromCurrentView(position, entry);
                        } else if (opt.contains("下载歌曲")) {
                            downloadSongItem(entry);
                        }
                    }
                })
                .show();
    }

    private void showAddToPlaylistDialog(final DisplayEntry entry) {
        final ArrayList<String> names = new ArrayList<String>();
        final ArrayList<String> ids = new ArrayList<String>();

        names.add("♥ 我的收藏 (云端同步)");
        ids.add("ACTION_FAV");

        names.add("⭐ 精选歌单 (本地定制)");
        ids.add("LOCAL_FEATURED");

        names.add("🚗 车载歌单 (本地定制)");
        ids.add("LOCAL_CAR");

        for (DisplayEntry pl : rawServerUserPlaylists) {
            names.add("📁 " + pl.title + " (云端歌单)");
            ids.add(pl.id);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要加入的歌单")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String targetId = ids.get(which);
                        if ("ACTION_FAV".equals(targetId)) {
                            if (!isFav(entry.id)) serverStarSong(entry.id, true);
                        } else if ("LOCAL_FEATURED".equals(targetId)) {
                            addSongToLocalList(featuredSongs, entry, "精选歌单");
                        } else if ("LOCAL_CAR".equals(targetId)) {
                            addSongToLocalList(carSongs, entry, "车载歌单");
                        } else {
                            addSongToServerPlaylist(targetId, entry.id, names.get(which));
                        }
                    }
                })
                .show();
    }

    private void addSongToServerPlaylist(final String playlistId, final String songId, final String playlistDisplayName) {
        Toast.makeText(this, "正在同步添加至云端歌单...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8")
                            + "&songIdToAdd=" + URLEncoder.encode(songId, "UTF-8");
                    String res = requestApi("updatePlaylist.view?" + param + "&" + getAuthParams());
                    if (res != null && !res.contains("\"status\":\"failed\"")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "已成功添加至 " + playlistDisplayName, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "同步云端歌单失败，请检查服务器权限", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "网络连接异常，添加失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showPlaylistLongClickMenu(final DisplayEntry playlistEntry) {
        if ("fav_entry".equals(playlistEntry.id)
                || "local_featured".equals(playlistEntry.id)
                || "local_car".equals(playlistEntry.id)
                || "action_create_playlist".equals(playlistEntry.id)) {
            return;
        }

        String[] options = new String[]{"➕ 添加歌曲 (前往搜索并关联)", "✏ 重命名歌单", "🗑 删除歌单"};

        new AlertDialog.Builder(this)
                .setTitle("管理歌单: " + playlistEntry.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            targetPlaylistIdForAdd = playlistEntry.id;
                            targetPlaylistNameForAdd = playlistEntry.title;
                            layoutSearchOverlay.setVisibility(View.VISIBLE);
                            if (searchHistoryList.isEmpty() || isSearchHistoryCollapsed) {
                                layoutSearchHistoryBox.setVisibility(View.GONE);
                            } else {
                                layoutSearchHistoryBox.setVisibility(View.VISIBLE);
                            }
                            etSearchKeyword.setHint("搜索歌曲以加入【" + playlistEntry.title + "】...");
                            etSearchKeyword.requestFocus();
                            Toast.makeText(MainActivity.this, "已锁定歌单【" + playlistEntry.title + "】，长按搜索结果即可直接加入！", Toast.LENGTH_LONG).show();
                        } else if (which == 1) {
                            promptRenamePlaylist(playlistEntry);
                        } else if (which == 2) {
                            promptDeletePlaylist(playlistEntry);
                        }
                    }
                })
                .show();
    }

    private void promptCreatePlaylist() {
        final EditText input = new EditText(this);
        input.setHint("输入新歌单名称...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777777);

        new AlertDialog.Builder(this)
                .setTitle("新建云端歌单")
                .setView(input)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = input.getText().toString().trim();
                        if (name.length() == 0) {
                            Toast.makeText(MainActivity.this, "歌单名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        createNewServerPlaylist(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNewServerPlaylist(final String playlistName) {
        Toast.makeText(this, "正在同步创建云端歌单...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "name=" + URLEncoder.encode(playlistName, "UTF-8");
                    String res = requestApi("createPlaylist.view?" + param + "&" + getAuthParams());
                    if (res != null && !res.contains("\"status\":\"failed\"")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "歌单【" + playlistName + "】创建成功！", Toast.LENGTH_SHORT).show();
                                fetchPlaylists();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "创建失败，请确认服务器操作权限", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "网络连接异常，创建失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void promptRenamePlaylist(final DisplayEntry playlistEntry) {
        final EditText input = new EditText(this);
        input.setText(playlistEntry.title);
        input.setTextColor(0xFFFFFFFF);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("重命名歌单")
                .setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String newName = input.getText().toString().trim();
                        if (newName.length() == 0 || newName.equals(playlistEntry.title)) return;
                        renameServerPlaylist(playlistEntry.id, newName);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameServerPlaylist(final String playlistId, final String newName) {
        Toast.makeText(this, "正在同步更新歌单名...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "playlistId=" + URLEncoder.encode(playlistId, "UTF-8")
                            + "&name=" + URLEncoder.encode(newName, "UTF-8");
                    String res = requestApi("updatePlaylist.view?" + param + "&" + getAuthParams());
                    if (res != null && !res.contains("\"status\":\"failed\"")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "歌单名称已更新为【" + newName + "】", Toast.LENGTH_SHORT).show();
                                fetchPlaylists();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "重命名失败，请检查服务器权限", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "网络异常，重命名失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void promptDeletePlaylist(final DisplayEntry playlistEntry) {
        new AlertDialog.Builder(this)
                .setTitle("删除歌单")
                .setMessage("确定要删除云端歌单【" + playlistEntry.title + "】吗？该操作不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteServerPlaylist(playlistEntry.id, playlistEntry.title);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteServerPlaylist(final String playlistId, final String playlistName) {
        Toast.makeText(this, "正在删除云端歌单...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String param = "id=" + URLEncoder.encode(playlistId, "UTF-8");
                    String res = requestApi("deletePlaylist.view?" + param + "&" + getAuthParams());
                    if (res != null && !res.contains("\"status\":\"failed\"")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "歌单【" + playlistName + "】已删除", Toast.LENGTH_SHORT).show();
                                fetchPlaylists();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "删除失败，请检查服务器权限", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "网络异常，删除失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
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

    private void removeFromCurrentView(final int position, final DisplayEntry entry) {
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
            removeRowFromUi(position);
            return;
        }

        if ("车载歌单".equals(tvListTitle.getText().toString())) {
            for (int i = 0; i < carSongs.size(); i++) {
                if (carSongs.get(i).id.equals(entry.id)) {
                    carSongs.remove(i);
                    break;
                }
            }
            saveLocalPlaylists();
            removeRowFromUi(position);
            return;
        }

        if (currentActivePlaylistId != null && !currentActivePlaylistId.startsWith("local_")) {
            final int songIndexToRemove = position;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String param = "playlistId=" + URLEncoder.encode(currentActivePlaylistId, "UTF-8")
                                + "&songIndexToRemove=" + songIndexToRemove;
                        String res = requestApi("updatePlaylist.view?" + param + "&" + getAuthParams());
                        if (res != null && !res.contains("\"status\":\"failed\"")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    removeRowFromUi(position);
                                    Toast.makeText(MainActivity.this, "已同步从云端歌单移除", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "云端移除失败，请确认服务器操作权限", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "网络异常，移除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
            return;
        }

        removeRowFromUi(position);
    }

    private void removeRowFromUi(int position) {
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
                row.view.setTextSize(i == currentLyricIndex ? (lyricBaseFontSize + 5) : lyricBaseFontSize);
            }
        }
        Toast.makeText(this, "歌词字号: " + lyricBaseFontSize + "sp", Toast.LENGTH_SHORT).show();
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
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        int minEdge = Math.min(srcW, srcH);
        Rect srcRect = new Rect((srcW - minEdge) / 2, (srcH - minEdge) / 2, (srcW + minEdge) / 2, (srcH + minEdge) / 2);
        Rect dstRect = new Rect(0, 0, targetSize, targetSize);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);

        return output;
    }

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int targetSize, float cornerRadiusPx) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        if (targetSize <= 0) targetSize = 190;

        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rectF = new RectF(0, 0, targetSize, targetSize);
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        int minEdge = Math.min(srcW, srcH);
        Rect srcRect = new Rect((srcW - minEdge) / 2, (srcH - minEdge) / 2, (srcW + minEdge) / 2, (srcH + minEdge) / 2);
        Rect dstRect = new Rect(0, 0, targetSize, targetSize);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);

        return output;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private void startDlnaCast(DlnaManager.Device targetDev) {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem current = queue.get(curIdx);
            int currentPos = seekBar != null ? seekBar.getProgress() : 0;

            DlnaManager.playUrl(targetDev, current.streamUrl, current.title, current.artist, currentPos, new DlnaManager.PositionCallback() {
                @Override
                public void onPositionInfo(int positionMs, int durationMs) {
                    if (DlnaManager.isCasting() && durationMs > 0) {
                        int effectivePos = positionMs - 2500;
                        if (effectivePos < 0) effectivePos = 0;
                        updateLyricPosition(effectivePos);
                    }
                }
            });

            Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
            muteIntent.setAction(MusicService.ACTION_SET_MUTE);
            muteIntent.putExtra("is_muted", true);
            startService(muteIntent);

            if (btnDetailDlna != null) {
                btnDetailDlna.setText("⛶ 投播中: " + (targetDev.name.length() > 5 ? targetDev.name.substring(0, 5) + ".." : targetDev.name));
                btnDetailDlna.setTextColor(0xFFFF4081);
            }
            Toast.makeText(this, "已投播至 " + targetDev.name + "，平板已静音，歌词与进度同步校准！", Toast.LENGTH_LONG).show();
        }
    }

    private void startDlnaCastQuietly(DlnaManager.Device targetDev, String streamUrl, String title, String artist, int positionMs) {
        DlnaManager.playUrl(targetDev, streamUrl, title, artist, positionMs, new DlnaManager.PositionCallback() {
            @Override
            public void onPositionInfo(int positionMs, int durationMs) {
                if (DlnaManager.isCasting() && durationMs > 0) {
                    int effectivePos = positionMs - 2500;
                    if (effectivePos < 0) effectivePos = 0;
                    updateLyricPosition(effectivePos);
                }
            }
        });
        Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
        muteIntent.setAction(MusicService.ACTION_SET_MUTE);
        muteIntent.putExtra("is_muted", true);
        startService(muteIntent);
    }
}
