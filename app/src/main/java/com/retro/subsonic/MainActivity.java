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
    private Button btnLyricDec, btnLyricInc, btnLyricDelay, btnLyricAdvance;
    private TextView tvLyricOffsetStatus;
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
    private long manualLyricOffsetMs = 0;
    private String currentLoadedRawLyrics = null;

    // 时间防抖：记录上次确认有效的合法播放秒数，拦截偶发突跳到结尾的假帧
    private int lastValidProgressMs = 0;

    private Handler dlnaSyncHandler = new Handler();
    private Runnable dlnaSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (DlnaManager.isCasting() && isCurrentSongPlaying && !isUserSeeking) {
                DlnaManager.getPositionInfo(new DlnaManager.PositionCallback() {
                    @Override
                    public void onPositionReceived(int positionMs, int durationMs) {
                        if (positionMs >= 0 && !isUserSeeking) {
                            int totalDur = durationMs > 0 ? durationMs : (seekBar != null ? seekBar.getMax() : 0);

                            // 防抖过滤：投播音箱在缓冲时偶尔返回等于总时长的假帧，拦截之
                            if (totalDur > 0 && positionMs >= totalDur - 1000 && lastValidProgressMs < totalDur * 0.85) {
                                return;
                            }

                            // 过滤无拖动时突跳向前超过 15 秒的异常尖刺
                            if (lastValidProgressMs > 0 && (positionMs - lastValidProgressMs) > 15000) {
                                return;
                            }

                            lastValidProgressMs = positionMs;

                            if (durationMs > 0) {
                                seekBar.setMax(durationMs);
                                detailSeekBar.setMax(durationMs);
                                String timeStr = formatTime(positionMs) + " / " + formatTime(durationMs);
                                tvTime.setText(timeStr);
                                tvDetailTime.setText(timeStr);
                            }
                            seekBar.setProgress(positionMs);
                            detailSeekBar.setProgress(positionMs);
                            updateLyricPosition(positionMs);
                        }
                    }
                });
                dlnaSyncHandler.postDelayed(this, 1000);
            }
        }
    };

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
                        lastValidProgressMs = 0;
                        manualLyricOffsetMs = 0;
                        updateLyricOffsetStatusView();
                        loadCoverArt(coverArtId != null ? coverArtId : songId);
                        loadLyrics(songId, artist, title);
                        refreshQueueList();
                        updateCacheSizeDisplay();

                        if (DlnaManager.isCasting() && streamUrl != null && streamUrl.length() > 0) {
                            DlnaManager.playUrl(DlnaManager.getCurrentDevice(), streamUrl, title, artist, 0);
                        }
                    }

                    updateFavButtonState(songId);
                }

                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);

                if (!DlnaManager.isCasting() && !isUserSeeking && duration > 0) {
                    // 本地播放时间防抖：若曲目还在中前段，过滤掉底层偶发返回的等于总时长的突跳帧
                    if (position >= duration - 1000 && lastValidProgressMs < duration * 0.85) {
                        return;
                    }
                    if (lastValidProgressMs > 0 && (position - lastValidProgressMs) > 15000) {
                        return;
                    }

                    lastValidProgressMs = position;

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

        // 完整绑定歌词字号与偏置微调控件
        btnLyricDec = (Button) findViewById(R.id.btn_lyric_dec);
        btnLyricInc = (Button) findViewById(R.id.btn_lyric_inc);
        btnLyricDelay = (Button) findViewById(R.id.btn_lyric_delay);
        btnLyricAdvance = (Button) findViewById(R.id.btn_lyric_advance);
        tvLyricOffsetStatus = (TextView) findViewById(R.id.tv_lyric_offset_status);

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

    private void adjustLyricOffset(long deltaMs) {
        manualLyricOffsetMs += deltaMs;
        updateLyricOffsetStatusView();
        if (currentLoadedRawLyrics != null && currentLoadedRawLyrics.length() > 0) {
            buildLyricsView(currentLoadedRawLyrics);
            int curPos = detailSeekBar != null ? detailSeekBar.getProgress() : 0;
            updateLyricPosition(curPos);
        }
        Toast.makeText(this, "歌词偏置: " + (manualLyricOffsetMs >= 0 ? "+" : "") + (manualLyricOffsetMs / 1000.0) + "s", Toast.LENGTH_SHORT).show();
    }

    private void updateLyricOffsetStatusView() {
        if (tvLyricOffsetStatus != null) {
            double sec = manualLyricOffsetMs / 1000.0;
            tvLyricOffsetStatus.setText(String.format("%s%.1fs", (sec > 0 ? "+" : ""), sec));
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

    private void setupListeners() {
        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { performAppExit(); }
        };
        btnExitApp.setOnClickListener(exitListener);
        btnDetailExitApp.setOnClickListener(exitListener);

        btnTopSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                targetPlaylistIdForAdd = null;
                targetPlaylistNameForAdd = null;
                etSearchKeyword.setHint("输入歌曲名检索...");
                layoutSearchOverlay.setVisibility(View.VISIBLE);
                if (searchHistoryList.isEmpty()) {
                    layoutSearchHistoryBox.setVisibility(View.GONE);
                } else {
                    layoutSearchHistoryBox.setVisibility(View.VISIBLE);
                    isSearchHistoryCollapsed = false;
                    lvSearchHistory.setVisibility(View.VISIBLE);
                    btnToggleSearchHistory.setText("收起 ▴");
                }
                etSearchKeyword.requestFocus();
            }
        });

        btnSearchPageBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchOverlay.setVisibility(View.GONE);
                targetPlaylistIdForAdd = null;
                targetPlaylistNameForAdd = null;
            }
        });

        btnToggleSearchHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSearchHistoryCollapsed = !isSearchHistoryCollapsed;
                if (isSearchHistoryCollapsed) {
                    lvSearchHistory.setVisibility(View.GONE);
                    btnToggleSearchHistory.setText("展开 ▾");
                } else {
                    lvSearchHistory.setVisibility(View.VISIBLE);
                    btnToggleSearchHistory.setText("收起 ▴");
                }
            }
        });

        btnClearSearchHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptClearSearchHistory();
            }
        });

        lvSearchHistory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchHistoryList.size()) {
                    String kw = searchHistoryList.get(position);
                    etSearchKeyword.setText(kw);
                    searchSongs(kw);
                }
            }
        });

        lvSearchHistory.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchHistoryList.size()) {
                    removeSingleSearchHistory(searchHistoryList.get(position));
                    return true;
                }
                return false;
            }
        });

        cbDedupSongs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int searchTypePos = spinnerSearchType != null ? spinnerSearchType.getSelectedItemPosition() : 0;
                if (searchTypePos == 0 && !rawSearchSongResults.isEmpty()) {
                    applySongDeduplication(isChecked);
                }
            }
        });

        btnTabPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedTab = TAB_PLAYLISTS;
                btnTabPlaylists.setTextColor(0xFF00E5FF);
                btnTabRanking.setTextColor(0xFFA0A5B5);
                btnBack.setVisibility(View.GONE);
                showUserPlaylists();
            }
        });

        btnTabRanking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedTab = TAB_RANKING;
                btnTabRanking.setTextColor(0xFF00E5FF);
                btnTabPlaylists.setTextColor(0xFFA0A5B5);
                btnBack.setVisibility(View.GONE);
                showRankingPlaylists();
            }
        });

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String kw = etSearchKeyword.getText().toString().trim();
                if (kw.length() > 0) {
                    addSearchHistory(kw);
                }
                searchSongs(kw);
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
            @Override
            public void onClick(View v) { applyLyricFontSize(-2); }
        });

        btnLyricInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { applyLyricFontSize(2); }
        });

        btnLyricDelay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjustLyricOffset(-500); }
        });

        btnLyricAdvance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { adjustLyricOffset(500); }
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

        if (btnDetailDlna != null) {
            btnDetailDlna.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DlnaManager.isCasting()) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("DLNA 投播管理")
                                .setMessage("当前正在投播至: " + DlnaManager.getCurrentDevice().name + "\n\n是否断开投播并恢复平板扬声器？")
                                .setPositiveButton("断开投播", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        DlnaManager.disconnect();
                                        dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
                                        btnDetailDlna.setText("⛶ 投播");
                                        btnDetailDlna.setTextColor(0xFF00E5FF);

                                        Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
                                        muteIntent.setAction(MusicService.ACTION_SET_MUTE);
                                        muteIntent.putExtra("is_muted", false);
                                        startService(muteIntent);

                                        Toast.makeText(MainActivity.this, "已断开投播，恢复本地发声", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        Toast.makeText(MainActivity.this, "正在扫描局域网 DLNA 音响/电视设备...", Toast.LENGTH_SHORT).show();
                        final ArrayList<DlnaManager.Device> foundDevices = new ArrayList<DlnaManager.Device>();
                        final ArrayList<String> deviceNames = new ArrayList<String>();

                        final ArrayAdapter<String> devAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, deviceNames);

                        final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("选择投播设备 (局域网 DLNA)")
                                .setAdapter(devAdapter, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        if (which >= 0 && which < foundDevices.size()) {
                                            DlnaManager.Device targetDev = foundDevices.get(which);
                                            startDlnaCast(targetDev);
                                        }
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();

                        DlnaManager.searchDevices(MainActivity.this, new DlnaManager.DiscoveryCallback() {
                            @Override
                            public void onDeviceFound(DlnaManager.Device device) {
                                for (DlnaManager.Device d : foundDevices) {
                                    if (d.location.equals(device.location)) return;
                                }
                                foundDevices.add(device);
                                deviceNames.add("🔊 " + device.name);
                                devAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            });
        }

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
                    updateCacheSizeDisplay();
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
                updateCacheSizeDisplay();
                Toast.makeText(MainActivity.this, String.format("已清理缓存，释放 %.1f MB 空间", mb), Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener eqListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { new EqualizerDialog(MainActivity.this).show(); }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

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

        View.OnClickListener openDetailListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) { layoutDetailOverlay.setVisibility(View.VISIBLE); }
        };
        ivBottomCover.setOnClickListener(openDetailListener);

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
                if ("action_create_playlist".equals(entry.id)) {
                    promptCreatePlaylist();
                    return;
                }

                if (!entry.isSong) {
                    if (entry.id.startsWith("album_")) {
                        fetchAlbumSongs(entry.id.substring(6), entry.title);
                    } else if (entry.id.startsWith("artist_")) {
                        fetchArtistAlbums(entry.id.substring(7), entry.title);
                    } else {
                        fetchPlaylistSongs(entry.id, entry.title);
                    }
                } else {
                    playSongInList(currentItems, entry);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int pos = position - listView.getHeaderViewsCount();
                if (pos >= 0 && pos < currentItems.size()) {
                    DisplayEntry entry = currentItems.get(pos);
                    if (entry.isSong) {
                        showSongLongClickMenu(entry, pos);
                    } else {
                        showPlaylistLongClickMenu(entry);
                    }
                    return true;
                }
                return false;
            }
        });

        lvSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= searchResultsList.size()) return;
                DisplayEntry entry = searchResultsList.get(position);
                if (!entry.isSong) {
                    layoutSearchOverlay.setVisibility(View.GONE);
                    if (entry.id.startsWith("album_")) {
                        fetchAlbumSongs(entry.id.substring(6), entry.title);
                    } else if (entry.id.startsWith("artist_")) {
                        fetchArtistAlbums(entry.id.substring(7), entry.title);
                    }
                } else {
                    playSongInList(searchResultsList, entry);
                }
            }
        });

        lvSearchResults.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < searchResultsList.size()) {
                    showSongLongClickMenu(searchResultsList.get(position), position);
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
        ivBottomCover.setOnTouchListener(touchFeedbackListener);
        btnExitApp.setOnTouchListener(touchFeedbackListener);
        btnDetailExitApp.setOnTouchListener(touchFeedbackListener);
        btnTopSearch.setOnTouchListener(touchFeedbackListener);
        btnClearSearchHistory.setOnTouchListener(touchFeedbackListener);

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DlnaManager.isCasting()) {
                    if (isCurrentSongPlaying) {
                        DlnaManager.pause();
                    } else {
                        DlnaManager.resume();
                    }
                }
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

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                lastValidProgressMs = sb.getProgress();
                if (DlnaManager.isCasting()) {
                    DlnaManager.seek(sb.getProgress());
                }
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_SEEK);
                intent.putExtra("position", sb.getProgress());
                startService(intent);
            }
        };
        seekBar.setOnSeekBarChangeListener(seekListener);
        detailSeekBar.setOnSeekBarChangeListener(seekListener);
    }

    private void startDlnaCast(DlnaManager.Device targetDev) {
        ArrayList<MusicService.SongItem> queue = MusicService.getPlaylist();
        int curIdx = MusicService.getCurrentIndex();
        if (queue != null && curIdx >= 0 && curIdx < queue.size()) {
            MusicService.SongItem current = queue.get(curIdx);
            int currentPos = seekBar != null ? seekBar.getProgress() : 0;

            DlnaManager.playUrl(targetDev, current.streamUrl, current.title, current.artist, currentPos);

            Intent muteIntent = new Intent(MainActivity.this, MusicService.class);
            muteIntent.setAction(MusicService.ACTION_SET_MUTE);
            muteIntent.putExtra("is_muted", true);
            startService(muteIntent);

            if (btnDetailDlna != null) {
                btnDetailDlna.setText("⛶ 投播中: " + (targetDev.name.length() > 5 ? targetDev.name.substring(0, 5) + ".." : targetDev.name));
                btnDetailDlna.setTextColor(0xFFFF4081);
            }

            dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
            dlnaSyncHandler.postDelayed(dlnaSyncRunnable, 1000);

            Toast.makeText(this, "已投播至 " + targetDev.name + "，平板已自动静音！", Toast.LENGTH_LONG).show();
        }
    }

    private void playSongInList(ArrayList<DisplayEntry> list, DisplayEntry entry) {
        ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
        int clickedSongIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            DisplayEntry item = list.get(i);
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

    private void refreshQueueList() {
        ArrayList<MusicService.SongItem> list = MusicService.getPlaylist();
        int currentPlaying = MusicService.getCurrentIndex();
        queueData.clear();

        for (int i = 0; i < list.size(); i++) {
            MusicService.SongItem item = list.get(i);
            Map<String, String> row = new HashMap<String, String>();
            if (i == currentPlaying) {
                row.put("title", "▶  " + (i + 1) + ". " + item.title);
            } else {
                row.put("title", "    " + (i + 1) + ". " + item.title);
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
            ivBottomCover.setImageResource(R.drawable.ic_launcher);
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
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
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
                            float density = getResources().getDisplayMetrics().density;
                            final Bitmap safeBottomRoundedBitmap = getRoundedCornerBitmap(safeDecodedBitmap, (int) (95 * density), 10 * density);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (currentRawCoverBitmap != null && !currentRawCoverBitmap.isRecycled()) {
                                        currentRawCoverBitmap.recycle();
                                    }
                                    if (currentCircularCoverBitmap != null && !currentCircularCoverBitmap.isRecycled()) {
                                        currentCircularCoverBitmap.recycle();
                                    }
                                    if (currentBottomCoverBitmap != null && !currentBottomCoverBitmap.isRecycled()) {
                                        currentBottomCoverBitmap.recycle();
                                    }

                                    currentRawCoverBitmap = safeDecodedBitmap;
                                    currentCircularCoverBitmap = safeCircularBitmap;
                                    currentBottomCoverBitmap = safeBottomRoundedBitmap;

                                    ivVinylCircularCover.setImageBitmap(currentCircularCoverBitmap);
                                    ivSquareCover.setImageBitmap(currentRawCoverBitmap);
                                    ivBottomCover.setImageBitmap(currentBottomCoverBitmap);
                                }
                            });
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private boolean isValidLyrics(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.length() == 0) return false;
        String lower = trimmed.toLowerCase();
        if (lower.contains("lyrics not found")
                || lower.contains("not found in library")
                || lower.contains("getlyricsbysongid")
                || lower.contains("get lyricsbysongid")
                || lower.contains("valid song id")
                || lower.contains("no lyrics available")
                || lower.contains("no lyrics found")
                || lower.contains("error:")
                || lower.contains("subsonic-response")) {
            return false;
        }
        return true;
    }

    private void loadLyrics(final String songId, final String artist, final String title) {
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
                String lyricsText = null;

                if (songId != null && songId.length() > 0) {
                    try {
                        String res = requestApi("getLyricsBySongId.view?id=" + URLEncoder.encode(songId, "UTF-8") + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}
                }

                if (lyricsText == null && title != null && title.length() > 0) {
                    try {
                        String p = "artist=" + URLEncoder.encode(artist != null ? artist : "", "UTF-8")
                                + "&title=" + URLEncoder.encode(title, "UTF-8");
                        String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                        lyricsText = parseLyricsFromJson(res);
                    } catch (Throwable ignored) {}
                }

                if (lyricsText == null && title != null) {
                    String cleanTitle = title.replaceAll("\\([^)]*\\)", "")
                            .replaceAll("\\[[^\\]]*\\]", "")
                            .replaceAll("（[^）]*）", "")
                            .trim();

                    if (cleanTitle.length() > 0 && !cleanTitle.equals(title)) {
                        try {
                            String p = "artist=" + URLEncoder.encode(artist != null ? artist : "", "UTF-8")
                                    + "&title=" + URLEncoder.encode(cleanTitle, "UTF-8");
                            String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                            lyricsText = parseLyricsFromJson(res);
                        } catch (Throwable ignored) {}
                    }
                }

                if (lyricsText == null && title != null) {
                    String cleanTitle = title.replaceAll("\\([^)]*\\)", "")
                            .replaceAll("\\[[^\\]]*\\]", "")
                            .replaceAll("（[^）]*）", "")
                            .trim();

                    if (cleanTitle.length() > 0) {
                        try {
                            String p = "title=" + URLEncoder.encode(cleanTitle, "UTF-8");
                            String res = requestApi("getLyrics.view?" + p + "&" + getAuthParams());
                            lyricsText = parseLyricsFromJson(res);
                        } catch (Throwable ignored) {}
                    }
                }

                final String finalLyrics = lyricsText;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentLoadedRawLyrics = finalLyrics;
                        if (finalLyrics != null && finalLyrics.trim().length() > 0) {
                            buildLyricsView(finalLyrics);
                        } else {
                            showSimpleLyric("未找到匹配歌词");
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

            if (root.has("status") && !"ok".equalsIgnoreCase(root.getString("status"))) {
                return null;
            }

            if (root.has("lyricsList")) {
                JSONObject list = root.optJSONObject("lyricsList");
                if (list != null && list.has("structuredLyrics")) {
                    Object slObj = list.get("structuredLyrics");
                    JSONObject targetSL = null;
                    if (slObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) slObj;
                        if (arr.length() > 0) targetSL = arr.getJSONObject(0);
                    } else if (slObj instanceof JSONObject) {
                        targetSL = (JSONObject) slObj;
                    }

                    if (targetSL != null && targetSL.has("line")) {
                        Object lineObj = targetSL.get("line");
                        StringBuilder lrcBuilder = new StringBuilder();
                        if (lineObj instanceof JSONArray) {
                            JSONArray lArr = (JSONArray) lineObj;
                            for (int i = 0; i < lArr.length(); i++) {
                                JSONObject l = lArr.getJSONObject(i);
                                appendStructuredLrcLine(lrcBuilder, l);
                            }
                        } else if (lineObj instanceof JSONObject) {
                            appendStructuredLrcLine(lrcBuilder, (JSONObject) lineObj);
                        }
                        String candidate = lrcBuilder.toString();
                        if (isValidLyrics(candidate)) {
                            return candidate;
                        }
                    }
                }
            }

            if (root.has("lyrics")) {
                Object lyricsObj = root.get("lyrics");
                String candidate = null;
                if (lyricsObj instanceof JSONObject) {
                    JSONObject l = (JSONObject) lyricsObj;
                    candidate = l.optString("content", l.optString("value", ""));
                } else if (lyricsObj instanceof String) {
                    candidate = (String) lyricsObj;
                }
                if (isValidLyrics(candidate)) {
                    return candidate;
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

        long headerOffsetMs = 0;
        String[] lines = rawText.split("\n");

        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;

            if (line.toLowerCase().startsWith("[offset:") && line.endsWith("]")) {
                try {
                    String offsetVal = line.substring(8, line.length() - 1).trim();
                    headerOffsetMs = Long.parseLong(offsetVal);
                } catch (Exception ignored) {}
                continue;
            }

            int closeBracket = line.indexOf(']');
            if (line.startsWith("[") && closeBracket > 1) {
                String timePart = line.substring(1, closeBracket);
                long timeMs = parseTime(timePart);
                if (timeMs >= 0) {
                    long finalCalculatedTime = timeMs + headerOffsetMs + manualLyricOffsetMs;
                    if (finalCalculatedTime < 0) finalCalculatedTime = 0;
                    String content = line.substring(closeBracket + 1).trim();
                    if (content.length() == 0) content = "···";
                    lyricRows.add(new LyricRow(finalCalculatedTime, content));
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
                curRow.view.setTextSize(lyricBaseFontSize + 5);
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

            if (conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
            }

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

        String nLower = name.trim().toLowerCase();
        if ("我的收藏".equals(name) || "starred".equals(nLower) || "favorites".equals(nLower) || "favourite".equals(nLower)) {
            return;
        }

        boolean isRanking = name.contains("榜单") || name.startsWith("榜") || name.endsWith("榜");
        DisplayEntry entry = new DisplayEntry(id, name, "", isRanking ? "排行榜" : "歌单", null, isRanking ? "排行榜歌单" : "歌单: " + count, false);

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

        for (DisplayEntry e : rawServerUserPlaylists) {
            currentItems.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "📁  " + e.title);
            row.put("subtitle", e.quality + " (长按管理)");
            listData.add(row);
        }

        currentItems.add(new DisplayEntry("action_create_playlist", "+ 新建歌单 (云端同步)", "", "点击创建全新的云端歌单并同步服务器", null, "操作", false));
        Map<String, String> createRow = new HashMap<String, String>();
        createRow.put("title", "➕  新建歌单 (云端同步)");
        createRow.put("subtitle", "点击创建全新的云端歌单并同步服务器");
        listData.add(createRow);

        adapter.notifyDataSetChanged();
    }

    private void showRankingPlaylists() {
        currentActivePlaylistId = null;
        currentActivePlaylistName = null;
        tvListTitle.setText("排行榜");
        currentItems.clear();
        listData.clear();

        for (DisplayEntry e : rawServerRankingPlaylists) {
            currentItems.add(e);
            Map<String, String> row = new HashMap<String, String>();
            row.put("title", "🏆  " + e.title);
            row.put("subtitle", "官方排行榜");
            listData.add(row);
        }

        if (rawServerRankingPlaylists.isEmpty()) {
            Map<String, String> emptyRow = new HashMap<String, String>();
            emptyRow.put("title", "暂无排行榜歌单");
            emptyRow.put("subtitle", "连接的服务器暂未同步榜单数据");
            listData.add(emptyRow);
        }

        adapter.notifyDataSetChanged();
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
                                        addSongRow(arr.getJSONObject(i), currentItems, listData);
                                    }
                                } else if (entryObj instanceof JSONObject) {
                                    addSongRow((JSONObject) entryObj, currentItems, listData);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText(playlistName);
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
                            listData.clear();

                            if (album.has("song")) {
                                Object songObj = album.get("song");
                                if (songObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) songObj;
                                    for (int i = 0; i < arr.length(); i++) {
                                        addSongRow(arr.getJSONObject(i), currentItems, listData);
                                    }
                                } else if (songObj instanceof JSONObject) {
                                    addSongRow((JSONObject) songObj, currentItems, listData);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText("专辑: " + albumName);
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "加载专辑失败", Toast.LENGTH_SHORT).show();
                        }
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
                            listData.clear();

                            if (artistObj.has("album")) {
                                Object albObj = artistObj.get("album");
                                if (albObj instanceof JSONArray) {
                                    JSONArray arr = (JSONArray) albObj;
                                    for (int i = 0; i < arr.length(); i++) {
                                        addAlbumRow(arr.getJSONObject(i), currentItems, listData);
                                    }
                                } else if (albObj instanceof JSONObject) {
                                    addAlbumRow((JSONObject) albObj, currentItems, listData);
                                }
                            }
                            btnBack.setVisibility(View.VISIBLE);
                            tvListTitle.setText("歌手: " + artistName);
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "加载歌手详情失败", Toast.LENGTH_SHORT).show();
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
                                        addSongRow(s, currentItems, listData);
                                        favSongIds.add(s.getString("id"));
                                    }
                                } else if (songObj instanceof JSONObject) {
                                    JSONObject s = (JSONObject) songObj;
                                    addSongRow(s, currentItems, listData);
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
        if (query == null || query.trim().length() == 0) return;
        lastSearchKeyword = query.trim();

        final int searchTypePos = spinnerSearchType != null ? spinnerSearchType.getSelectedItemPosition() : 0;

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
                            if (jsonStr == null) {
                                Toast.makeText(MainActivity.this, "搜索连接超时", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            try {
                                JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                                JSONObject result = root.optJSONObject("searchResult3");
                                searchResultsList.clear();
                                searchResultsData.clear();
                                rawSearchSongResults.clear();

                                if (result != null) {
                                    if (searchTypePos == 1 && result.has("artist")) {
                                        Object artObj = result.get("artist");
                                        if (artObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) artObj;
                                            for (int i = 0; i < arr.length(); i++) addArtistRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                                        } else if (artObj instanceof JSONObject) {
                                            addArtistRow((JSONObject) artObj, searchResultsList, searchResultsData);
                                        }
                                        tvSearchResultTitle.setText("搜索歌手: " + query + " (" + searchResultsList.size() + " 位)");
                                        if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.GONE);
                                    } else if (searchTypePos == 2 && result.has("album")) {
                                        Object albObj = result.get("album");
                                        if (albObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) albObj;
                                            for (int i = 0; i < arr.length(); i++) addAlbumRow(arr.getJSONObject(i), searchResultsList, searchResultsData);
                                        } else if (albObj instanceof JSONObject) {
                                            addAlbumRow((JSONObject) albObj, searchResultsList, searchResultsData);
                                        }
                                        tvSearchResultTitle.setText("搜索专辑: " + query + " (" + searchResultsList.size() + " 张)");
                                        if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.GONE);
                                    } else if (result.has("song")) {
                                        if (cbDedupSongs != null) cbDedupSongs.setVisibility(View.VISIBLE);
                                        Object sObj = result.get("song");
                                        ArrayList<Map<String, String>> dummyData = new ArrayList<Map<String, String>>();
                                        if (sObj instanceof JSONArray) {
                                            JSONArray arr = (JSONArray) sObj;
                                            for (int i = 0; i < arr.length(); i++) addSongRow(arr.getJSONObject(i), rawSearchSongResults, dummyData);
                                        } else if (sObj instanceof JSONObject) {
                                            addSongRow((JSONObject) sObj, rawSearchSongResults, dummyData);
                                        }

                                        boolean needDedup = cbDedupSongs != null && cbDedupSongs.isChecked();
                                        applySongDeduplication(needDedup);
                                    }
                                }

                                if (!searchHistoryList.isEmpty()) {
                                    layoutSearchHistoryBox.setVisibility(View.VISIBLE);
                                    isSearchHistoryCollapsed = true;
                                    lvSearchHistory.setVisibility(View.GONE);
                                    btnToggleSearchHistory.setText("展开 ▾");
                                } else {
                                    layoutSearchHistoryBox.setVisibility(View.GONE);
                                }

                                layoutSearchResultBox.setVisibility(View.VISIBLE);
                                searchResultsAdapter.notifyDataSetChanged();
                            } catch (Exception ignored) {}
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void applySongDeduplication(boolean dedup) {
        searchResultsList.clear();
        searchResultsData.clear();

        if (!dedup) {
            for (DisplayEntry e : rawSearchSongResults) {
                searchResultsList.add(e);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", e.title);
                row.put("subtitle", e.artist + "  [" + e.quality + "]");
                searchResultsData.add(row);
            }
            tvSearchResultTitle.setText("搜索歌曲: " + lastSearchKeyword + " (" + searchResultsList.size() + " 首)");
        } else {
            LinkedHashMap<String, DisplayEntry> bestSongsMap = new LinkedHashMap<String, DisplayEntry>();

            for (DisplayEntry song : rawSearchSongResults) {
                String key = song.title.trim().toLowerCase();
                if (!bestSongsMap.containsKey(key)) {
                    bestSongsMap.put(key, song);
                } else {
                    DisplayEntry exist = bestSongsMap.get(key);
                    if (song.bitRateNumeric > exist.bitRateNumeric) {
                        bestSongsMap.put(key, song);
                    }
                }
            }

            for (DisplayEntry bestSong : bestSongsMap.values()) {
                searchResultsList.add(bestSong);
                Map<String, String> row = new HashMap<String, String>();
                row.put("title", bestSong.title);
                row.put("subtitle", bestSong.artist + "  [" + bestSong.quality + "]");
                searchResultsData.add(row);
            }

            if (rawSearchSongResults.size() != searchResultsList.size()) {
                tvSearchResultTitle.setText("搜索歌曲: " + lastSearchKeyword + " (去重后 " + searchResultsList.size() + " 首 / 原始 " + rawSearchSongResults.size() + " 首)");
            } else {
                tvSearchResultTitle.setText("搜索歌曲: " + lastSearchKeyword + " (" + searchResultsList.size() + " 首)");
            }
        }
        searchResultsAdapter.notifyDataSetChanged();
    }

    private void addArtistRow(JSONObject a, ArrayList<DisplayEntry> targetList, ArrayList<Map<String, String>> targetData) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        int albumCount = a.optInt("albumCount", 0);
        targetList.add(new DisplayEntry("artist_" + id, name, "歌手", albumCount > 0 ? (albumCount + " 张专辑") : "点击查看专辑与歌曲", null, "歌手", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "👤  " + name);
        row.put("subtitle", albumCount > 0 ? (albumCount + " 张专辑") : "歌手详情");
        targetData.add(row);
    }

    private void addAlbumRow(JSONObject a, ArrayList<DisplayEntry> targetList, ArrayList<Map<String, String>> targetData) throws Exception {
        String id = a.getString("id");
        String name = a.getString("name");
        String artist = a.optString("artist", "未知艺术家");
        int songCount = a.optInt("songCount", 0);
        String coverArt = a.optString("coverArt", null);
        targetList.add(new DisplayEntry("album_" + id, name, artist, artist + (songCount > 0 ? (" • " + songCount + " 首") : ""), coverArt, "专辑", false));
        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "💿  " + name);
        row.put("subtitle", artist + (songCount > 0 ? ("  [" + songCount + " 首歌曲]") : ""));
        targetData.add(row);
    }

    private void addSongRow(JSONObject s, ArrayList<DisplayEntry> targetList, ArrayList<Map<String, String>> targetData) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知艺术家");
        String coverArt = s.optString("coverArt", null);

        int bitRate = s.optInt("bitRate", 0);
        String suffix = s.optString("suffix", "").toUpperCase();
        String quality;
        int bitRateScore = bitRate;

        if (suffix.contains("FLAC") || suffix.contains("WAV") || suffix.contains("APE") || suffix.contains("DSD")) {
            quality = "FLAC 无损";
            bitRateScore = 10000 + bitRate;
        } else if (bitRate > 0) {
            quality = bitRate + "K " + (suffix.length() > 0 ? suffix : "MP3");
            bitRateScore = bitRate;
        } else if (suffix.length() > 0) {
            quality = suffix;
            bitRateScore = 256;
        } else {
            quality = "320K MP3";
            bitRateScore = 320;
        }

        targetList.add(new DisplayEntry(s.getString("id"), title, artist, subtitleText(artist, quality), coverArt, quality, true, bitRateScore));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + "  [" + quality + "]");
        targetData.add(row);
    }

    private String subtitleText(String artist, String quality) {
        return artist + " [" + quality + "]";
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dlnaSyncHandler.removeCallbacks(dlnaSyncRunnable);
        if (currentRawCoverBitmap != null && !currentRawCoverBitmap.isRecycled()) {
            currentRawCoverBitmap.recycle();
            currentRawCoverBitmap = null;
        }
        if (currentCircularCoverBitmap != null && !currentCircularCoverBitmap.isRecycled()) {
            currentCircularCoverBitmap.recycle();
            currentCircularCoverBitmap = null;
        }
        if (currentBottomCoverBitmap != null && !currentBottomCoverBitmap.isRecycled()) {
            currentBottomCoverBitmap.recycle();
            currentBottomCoverBitmap = null;
        }
    }
}
