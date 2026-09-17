package com.retro.subsonic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.retro.subsonic.model.Song;
import com.retro.subsonic.service.MusicService;

public class MainActivity extends AppCompatActivity implements MusicService.OnPlaybackChangeListener {

    private MusicService musicService;
    private boolean isBound = false;

    // 底部迷你播放器组件
    private View bottomPlayerLayout;
    private ImageView ivBottomCover;
    private TextView tvBottomTitle, tvBottomArtist;
    private ImageButton btnBottomPlay, btnBottomNext;

    // 播放详情页组件
    private View playerDetailLayout;
    private ImageButton btnDetailClose, btnDetailPlay, btnDetailPrev, btnDetailNext;
    private TextView tvDetailTitle, tvDetailArtist, tvLyrics, tvCurrentTime, tvTotalTime;
    private SeekBar sbProgress;
    private ImageView ivRecordDisc;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            musicService.setOnPlaybackChangeListener(MainActivity.this);
            isBound = true;

            // 首次绑定立即拉取当前状态同步界面
            if (musicService.getCurrentSong() != null) {
                onTrackChanged(musicService.getCurrentSong());
                onPlaybackStateChanged(musicService.isPlaying());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        bindMusicService();
    }

    private void initViews() {
        // 底部播放器
        bottomPlayerLayout = findViewById(R.id.bottom_player_layout);
        ivBottomCover = findViewById(R.id.iv_bottom_cover);
        tvBottomTitle = findViewById(R.id.tv_bottom_title);
        tvBottomArtist = findViewById(R.id.tv_bottom_artist);
        btnBottomPlay = findViewById(R.id.btn_bottom_play);
        btnBottomNext = findViewById(R.id.btn_bottom_next);

        // 详情播放页
        playerDetailLayout = findViewById(R.id.player_detail_layout);
        btnDetailClose = findViewById(R.id.btn_detail_close);
        btnDetailPlay = findViewById(R.id.btn_detail_play);
        btnDetailPrev = findViewById(R.id.btn_detail_prev);
        btnDetailNext = findViewById(R.id.btn_detail_next);
        tvDetailTitle = findViewById(R.id.tv_detail_title);
        tvDetailArtist = findViewById(R.id.tv_detail_artist);
        tvLyrics = findViewById(R.id.tv_lyrics);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        sbProgress = findViewById(R.id.sb_progress);
        ivRecordDisc = findViewById(R.id.iv_record_disc);

        // 点击底部播放器展开详情页
        bottomPlayerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPlayerDetail();
            }
        });

        // 底部播放控制
        btnBottomPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) {
                    musicService.togglePlayPause();
                }
            }
        });

        btnBottomNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) {
                    musicService.next();
                }
            }
        });

        // 详情页控制
        btnDetailClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayerDetail();
            }
        });

        btnDetailPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) {
                    musicService.togglePlayPause();
                }
            }
        });

        btnDetailPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) {
                    musicService.previous();
                }
            }
        });

        btnDetailNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && musicService != null) {
                    musicService.next();
                }
            }
        });
    }

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void openPlayerDetail() {
        if (playerDetailLayout != null) {
            playerDetailLayout.setVisibility(View.VISIBLE);
        }
    }

    public void closePlayerDetail() {
        if (playerDetailLayout != null) {
            playerDetailLayout.setVisibility(View.GONE);
        }
    }

    // 核心：拦截返回键逻辑
    @Override
    public void onBackPressed() {
        // 1. 如果播放详情页展开，优先折叠收起
        if (playerDetailLayout != null && playerDetailLayout.getVisibility() == View.VISIBLE) {
            closePlayerDetail();
            return;
        }

        // 2. 如果 Fragment 栈内有历史页面，回退到上一级 Fragment
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }

        // 3. 正常退出或进入后台
        super.onBackPressed();
    }

    // --- MusicService.OnPlaybackChangeListener 回调 ---

    @Override
    public void onTrackChanged(Song song) {
        if (song == null) {
            bottomPlayerLayout.setVisibility(View.GONE);
            return;
        }

        bottomPlayerLayout.setVisibility(View.VISIBLE);

        // 刷新底部栏信息
        tvBottomTitle.setText(song.getTitle());
        tvBottomArtist.setText(song.getArtist());

        // 刷新详情页信息
        tvDetailTitle.setText(song.getTitle());
        tvDetailArtist.setText(song.getArtist());

        // 加载歌词
        if (song.getLyrics() != null && !song.getLyrics().isEmpty()) {
            tvLyrics.setText(song.getLyrics());
        } else {
            tvLyrics.setText("暂无歌词");
        }

        // 加载封面（兼容老版本，此处使用你的图片加载器，如 Glide/Picasso 或本地网络图片解码）
        if (musicService != null) {
            musicService.loadCover(song, ivBottomCover);
            musicService.loadCover(song, ivRecordDisc);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        btnBottomPlay.setImageResource(iconRes);
        btnDetailPlay.setImageResource(iconRes);
    }

    @Override
    public void onProgressUpdate(int currentPosition, int duration) {
        if (sbProgress != null) {
            sbProgress.setMax(duration);
            sbProgress.setProgress(currentPosition);
        }
        if (tvCurrentTime != null) {
            tvCurrentTime.setText(formatTime(currentPosition));
        }
        if (tvTotalTime != null) {
            tvTotalTime.setText(formatTime(duration));
        }
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            if (musicService != null) {
                musicService.setOnPlaybackChangeListener(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}
