package com.retro.subsonic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText etServer, etUsername, etPassword, etSearchKeyword;
    private Button btnConnect, btnToggleConfig, btnTabPlaylists, btnTabSearch, btnSearchSubmit, btnBack;
    private Button btnPrev, btnPlayPause, btnNext;
    private LinearLayout layoutConfigPanel, layoutSearchBar;
    private TextView tvListTitle, tvCurrentSong, tvTime;
    private ListView listView;
    private SeekBar seekBar;

    private SharedPreferences prefs;

    private static class DisplayEntry {
        String id;
        String title;
        String subtitle;
        boolean isSong;

        DisplayEntry(String id, String title, String subtitle, boolean isSong) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.isSong = isSong;
        }
    }

    private ArrayList<DisplayEntry> currentItems = new ArrayList<DisplayEntry>();
    private ArrayList<Map<String, String>> listData = new ArrayList<Map<String, String>>();
    private SimpleAdapter adapter;

    private boolean isUserSeeking = false;

    // 接收后台播放状态广播
    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.BROADCAST_STATUS.equals(intent.getAction())) {
                boolean isPlaying = intent.getBooleanExtra("isPlaying", false);
                btnPlayPause.setText(isPlaying ? "⏸ 暂停" : "▶ 播放");

                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                if (title != null) {
                    tvCurrentSong.setText(title + " - " + artist);
                }

                int position = intent.getIntExtra("position", 0);
                int duration = intent.getIntExtra("duration", 0);

                if (!isUserSeeking && duration > 0) {
                    seekBar.setMax(duration);
                    seekBar.setProgress(position);
                    tvTime.setText(formatTime(position) + " / " + formatTime(duration));
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
                                .setTitle("错误提示")
                                .setMessage(ex.toString())
                                .setPositiveButton("确定", null)
                                .show();
                    }
                });
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("subsonic_cfg", MODE_PRIVATE);

        initViews();
        loadSavedConfig();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(statusReceiver, new IntentFilter(MusicService.BROADCAST_STATUS));
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

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnToggleConfig = (Button) findViewById(R.id.btn_toggle_config);
        btnTabPlaylists = (Button) findViewById(R.id.btn_tab_playlists);
        btnTabSearch = (Button) findViewById(R.id.btn_tab_search);
        btnSearchSubmit = (Button) findViewById(R.id.btn_search_submit);
        btnBack = (Button) findViewById(R.id.btn_back);

        btnPrev = (Button) findViewById(R.id.btn_prev);
        btnPlayPause = (Button) findViewById(R.id.btn_play_pause);
        btnNext = (Button) findViewById(R.id.btn_next);

        layoutConfigPanel = (LinearLayout) findViewById(R.id.layout_config_panel);
        layoutSearchBar = (LinearLayout) findViewById(R.id.layout_search_bar);

        tvListTitle = (TextView) findViewById(R.id.tv_list_title);
        tvCurrentSong = (TextView) findViewById(R.id.tv_current_song);
        tvTime = (TextView) findViewById(R.id.tv_time);

        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        listView = (ListView) findViewById(R.id.list_view);

        // 双行展示，标题与歌手/曲目数分离排版
        adapter = new SimpleAdapter(
                this,
                listData,
                android.R.layout.simple_list_item_2,
                new String[]{"title", "subtitle"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        listView.setAdapter(adapter);
    }

    private void loadSavedConfig() {
        etServer.setText(prefs.getString("server", "http://192.168.1.100:4533"));
        etUsername.setText(prefs.getString("user", "admin"));
        etPassword.setText(prefs.getString("pass", "admin"));
    }

    private void saveConfig() {
        prefs.edit()
                .putString("server", etServer.getText().toString().trim())
                .putString("user", etUsername.getText().toString().trim())
                .putString("pass", etPassword.getText().toString().trim())
                .commit();
    }

    private void setupListeners() {
        btnToggleConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layoutConfigPanel.getVisibility() == View.VISIBLE) {
                    layoutConfigPanel.setVisibility(View.GONE);
                    btnToggleConfig.setText("设置服务器 ▼");
                } else {
                    layoutConfigPanel.setVisibility(View.VISIBLE);
                    btnToggleConfig.setText("收起设置 ▲");
                }
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
                layoutConfigPanel.setVisibility(View.GONE);
                btnToggleConfig.setText("设置服务器 ▼");
                fetchPlaylists();
            }
        });

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

        // 列表点击事件
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= currentItems.size()) return;
                DisplayEntry entry = currentItems.get(position);
                if (!entry.isSong) {
                    // 点击歌单：获取歌曲
                    fetchPlaylistSongs(entry.id, entry.title);
                } else {
                    // 点击歌曲：将当前列表中所有歌曲组成连续播放队列，并从该首开始播
                    ArrayList<MusicService.SongItem> queue = new ArrayList<MusicService.SongItem>();
                    int clickedSongIndex = 0;
                    for (int i = 0; i < currentItems.size(); i++) {
                        DisplayEntry item = currentItems.get(i);
                        if (item.isSong) {
                            if (item.id.equals(entry.id)) {
                                clickedSongIndex = queue.size();
                            }
                            queue.add(new MusicService.SongItem(item.id, item.title, item.subtitle, buildStreamUrl(item.id)));
                        }
                    }
                    MusicService.setQueue(queue, clickedSongIndex, MainActivity.this);
                }
            }
        });

        // 播放控制
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_TOGGLE);
                startService(intent);
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_NEXT);
                startService(intent);
            }
        });

        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                intent.setAction(MusicService.ACTION_PREV);
                startService(intent);
            }
        });

        // 进度条拖拽监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    tvTime.setText(formatTime(progress) + " / " + formatTime(sb.getMax()));
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
        });
    }

    private String getAuthParams() {
        String u = prefs.getString("user", "");
        String p = prefs.getString("pass", "");
        return "u=" + URLEncoder.encode(u) + "&p=" + URLEncoder.encode(p) + "&v=1.12.0&c=RetroSubsonic&f=json";
    }

    private String buildStreamUrl(String songId) {
        String base = prefs.getString("server", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/rest/stream.view?id=" + songId + "&" + getAuthParams();
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
                            Toast.makeText(MainActivity.this, "连接失败，请检查设置与网络", Toast.LENGTH_SHORT).show();
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
        currentItems.add(new DisplayEntry(p.getString("id"), name, "歌曲数量: " + count, false));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "📁  " + name);
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
                            Toast.makeText(MainActivity.this, "加载歌单歌曲失败", Toast.LENGTH_SHORT).show();
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
                                if (currentItems.isEmpty()) {
                                    Toast.makeText(MainActivity.this, "无相关结果", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "搜索失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void addSongRow(JSONObject s) throws Exception {
        String title = s.getString("title");
        String artist = s.optString("artist", "未知艺术家");
        currentItems.add(new DisplayEntry(s.getString("id"), title, artist, true));

        Map<String, String> row = new HashMap<String, String>();
        row.put("title", "🎵  " + title);
        row.put("subtitle", artist);
        listData.add(row);
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
