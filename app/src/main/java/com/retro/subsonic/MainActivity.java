package com.retro.subsonic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText etServer, etUsername, etPassword, etSearchKeyword, etCacheSize;
    private EditText etTimeoutSec, etRetryCount;
    private Button btnConnect, btnClearCache, btnToggleConfig, btnTabPlaylists, btnTabSearch, btnSearchSubmit, btnBack;
    private Button btnPrev, btnPlayPause, btnNext, btnMode, btnToggleQueue, btnCloseQueue, btnOpenDetail, btnOpenEq;
    private LinearLayout layoutConfigPanel, layoutSearchBar, layoutQueuePanel, layoutDetailOverlay, layoutBottomPlayer;
    private TextView tvListTitle, tvCurrentSong, tvTime;
    private ListView listView, lvQueue;
    private SeekBar seekBar;

    // 详情页组件
    private Button btnCloseDetail, btnDetailPrev, btnDetailPlayPause, btnDetailNext, btnDetailMode, btnDetailEq;
    private Button btnDetailKeepScreen, btnDetailQueue;
    private ImageView ivDetailCover;
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

    private static class DisplayEntry {
        String id;
        String title;
        String subtitle;
        String coverArt;
        String quality;
        boolean isSong;

        DisplayEntry(String id, String title, String subtitle, String coverArt, String quality, boolean isSong) {
            this.id = id;
            this.title = title;
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
                    } else if (isBuffering) {
                        tvCurrentSong.setText("正在获取音频 (" + bufferPercent + "%): " + title);
                        tvDetailTitle.setText("解析缓冲中 (" + bufferPercent + "%)...");
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

        initViews();
        loadSavedConfig();
        setupListeners();
        setupClickInterceptors();
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

    private void initViews() {
        etServer = (EditText) findViewById(R.id.et_server);
        etUsername = (EditText) findViewById(R.id.et_username);
        etPassword = (EditText) findViewById(R.id.et_password);
        etSearchKeyword = (EditText) findViewById(R.id.et_search_keyword);
        etCacheSize = (EditText) findViewById(R.id.et_cache_size);
        etTimeoutSec = (EditText) findViewById(R.id.et_timeout_sec);
        etRetryCount = (EditText) findViewById(R.id.et_retry_count);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnToggleConfig = (Button) findViewById(R.id.btn_toggle_config);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabSearch = (Button) findViewById(R.id.btn_tab_search);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnBack = (Button) findViewById(R.id.btn_back);

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
        btnDetailPrev = (Button) findViewById(R.id.btn_detail_prev);
        btnDetailPlayPause = (Button) findViewById(R.id.btn_detail_play_pause);
        btnDetailNext = (Button) findViewById(R.id.btn_detail_next);
        btnDetailMode = (Button) findViewById(R.id.btn_detail_mode);
        btnDetailEq = (Button) findViewById(R.id.btn_detail_eq);
        btnDetailKeepScreen = (Button) findViewById(R.id.btn_detail_keep_screen);
        btnDetailQueue = (Button) findViewById(R.id.btn_detail_queue);

        ivDetailCover = (ImageView) findViewById(R.id.iv_detail_cover);
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
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:4533"));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("pass", "admin"));
        etCacheSize.setText(prefs.getString("cache_size_mb", "500"));
        etTimeoutSec.setText(prefs.getString("play_timeout_sec", "20"));
        etRetryCount.setText(prefs.getString("play_retry_count", "3"));
    }

    private void saveConfig() {
        prefs.edit()
                .putString("server", etServer.getText().toString().trim())
                .putString("user", etUsername.getText().toString().trim())
                .putString("pass", etPassword.getText().toString().trim())
                .putString("cache_size_mb", etCacheSize.getText().toString().trim())
                .putString("play_timeout_sec", etTimeoutSec.getText().toString().trim())
                .putString("play_retry_count", etRetryCount.getText().toString().trim())
                .commit();
    }

    private String getModeString(int mode) {
        if (mode == MusicService.MODE_SHUFFLE) return "随机播放";
        if (mode == MusicService.MODE_SINGLE) return "单曲循环";
        return "列表循环";
    }

    // 关键修正：stream.view 严禁传入 f=json，并且强制追加 format=mp3 进行转码保证兼容
    private String buildStreamUrl(String songId) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        return base + "/rest/stream.view?id=" + songId + "&u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic&format=mp3&estimateContentLength=true";
    }

    private void setupListeners() {
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
            public void onClick(View v) {
                new EqualizerDialog(MainActivity.this).show();
            }
        };
        btnOpenEq.setOnClickListener(eqListener);
        btnDetailEq.setOnClickListener(eqListener);

        btnTabPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchBar.setVisibility(View.GONE);
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("歌单列表");
                fetchPlaylists();
            }
        });

        btnTabSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSearchBar.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("搜索结果");
                currentItems.clear();
                listData.clear();
                adapter.notifyDataSetChanged();
            }
        });

        btnSearchSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSongs(etSearchKeyword.getText().toString().trim());
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnBack.setVisibility(View.GONE);
                tvListTitle.setText("歌单列表");
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
            public void onClick(View v) {
                layoutQueuePanel.setVisibility(View.GONE);
            }
        });

        btnOpenDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutDetailOverlay.setVisibility(View.VISIBLE);
            }
        });

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

        btnDetailQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                        public void run() {
                            isUserTouchingLyrics = false;
                        }
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
                            queue.add(new MusicService.SongItem(item.id, item.title, item.subtitle, buildStreamUrl(item.id), item.coverArt, item.quality));
                        }
                    }
                    MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
                    refreshQueueList();
                }
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

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
            }

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
                            public void run() {
                                ivDetailCover.setImageBitmap(bitmap);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String jsonStr = requestApi("getPlaylists.view?" + getAuthParams());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (jsonStr == null) {
                            Toast.makeText(MainActivity.this, "连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            JSONObject root = new JSONObject(jsonStr).getJSONObject("subsonic-response");
                            JSONObject playlistsObj = root.optJSONObject("playlists");
                            currentItems.clear();
                            listData.clear();

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
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void addPlaylistRow(JSONObject p) throws Exception {
        String name = p.getString("name");
        int count = p.optInt("songCount", 0);
        currentItems.add(new DisplayEntry(p.getString("id"), name, "歌曲: " + count, null, "", false));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "[歌单] " + name);
        row.put("subtitle", count + " 首歌曲");
        listData.add(row);
    }

    private void fetchPlaylistSongs(final String playlistId, final String playlistName) {
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

        currentItems.add(new DisplayEntry(s.getString("id"), title, artist, coverArt, quality, true));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", title);
        row.put("subtitle", artist + "  [" + quality + "]");
        listData.add(row);
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
