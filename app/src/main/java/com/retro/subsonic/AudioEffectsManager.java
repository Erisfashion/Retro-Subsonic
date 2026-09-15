package com.retro.subsonic;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

import java.lang.ref.WeakReference;

public class AudioEffectsManager {

    private static AudioEffectsManager instance;

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private PresetReverb presetReverb;

    private WeakReference<MediaPlayer> mediaPlayerRef;
    private WeakReference<Context> contextRef;
    private int currentSessionId = 0;

    private boolean isEnabled = true;
    private short savedReverbPreset = PresetReverb.PRESET_NONE;
    private short savedBassStrength = 0;
    private short savedVirtualizerStrength = 0;
    private short savedEqualizerPreset = -1;

    public static synchronized AudioEffectsManager getInstance() {
        if (instance == null) {
            instance = new AudioEffectsManager();
        }
        return instance;
    }

    private AudioEffectsManager() {}

    public synchronized void attachMediaPlayer(MediaPlayer mp, Context context) {
        this.contextRef = new WeakReference<Context>(context);
        this.mediaPlayerRef = new WeakReference<MediaPlayer>(mp);
        if (mp == null) return;

        int sessionId = mp.getAudioSessionId();
        loadSavedConfig(context);
        initEffects(sessionId);
        applyReverbToPlayer();
    }

    public synchronized void attachSession(int sessionId, Context context) {
        this.contextRef = new WeakReference<Context>(context);
        loadSavedConfig(context);
        initEffects(sessionId);
        applyReverbToPlayer();
    }

    private void loadSavedConfig(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences("retro_audio_effects", Context.MODE_PRIVATE);
        isEnabled = sp.getBoolean("effects_enabled", true);
        savedReverbPreset = (short) sp.getInt("reverb_preset", PresetReverb.PRESET_NONE);
        savedBassStrength = (short) sp.getInt("bass_strength", 0);
        savedVirtualizerStrength = (short) sp.getInt("virtualizer_strength", 0);
        savedEqualizerPreset = (short) sp.getInt("equalizer_preset", -1);
    }

    private void initEffects(int sessionId) {
        currentSessionId = sessionId;

        // 1. 初始化均衡器
        try {
            if (equalizer != null) {
                try { equalizer.release(); } catch (Throwable ignored) {}
            }
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(isEnabled);
            if (savedEqualizerPreset >= 0 && savedEqualizerPreset < equalizer.getNumberOfPresets()) {
                equalizer.usePreset(savedEqualizerPreset);
            }
        } catch (Throwable t) {
            equalizer = null;
        }

        // 2. 初始化低音增强
        try {
            if (bassBoost != null) {
                try { bassBoost.release(); } catch (Throwable ignored) {}
            }
            bassBoost = new BassBoost(0, sessionId);
            if (bassBoost.getStrengthSupported()) {
                bassBoost.setStrength(savedBassStrength);
                bassBoost.setEnabled(isEnabled && savedBassStrength > 0);
            }
        } catch (Throwable t) {
            bassBoost = null;
        }

        // 3. 初始化 3D 虚拟现场
        try {
            if (virtualizer != null) {
                try { virtualizer.release(); } catch (Throwable ignored) {}
            }
            virtualizer = new Virtualizer(0, sessionId);
            if (virtualizer.getStrengthSupported()) {
                virtualizer.setStrength(savedVirtualizerStrength);
                virtualizer.setEnabled(isEnabled && savedVirtualizerStrength > 0);
            }
        } catch (Throwable t) {
            virtualizer = null;
        }

        // 4. 核心修复：环境音效 (PresetReverb) 属于全局辅助混合效果，必须绑定 Session 0
        try {
            if (presetReverb != null) {
                try { presetReverb.release(); } catch (Throwable ignored) {}
            }
            presetReverb = new PresetReverb(0, 0);
            presetReverb.setPreset(savedReverbPreset);
            presetReverb.setEnabled(isEnabled && savedReverbPreset != PresetReverb.PRESET_NONE);
        } catch (Throwable t) {
            try {
                // 部分定制 ROM 容错降级
                presetReverb = new PresetReverb(0, sessionId);
                presetReverb.setPreset(savedReverbPreset);
                presetReverb.setEnabled(isEnabled && savedReverbPreset != PresetReverb.PRESET_NONE);
            } catch (Throwable ignored) {
                presetReverb = null;
            }
        }
    }

    // 关键：将辅助混响挂载到 MediaPlayer 并将发送音量开至 1.0f
    public synchronized void applyReverbToPlayer() {
        if (mediaPlayerRef == null) return;
        MediaPlayer mp = mediaPlayerRef.get();
        if (mp == null) return;

        try {
            if (presetReverb != null) {
                mp.attachAuxEffect(presetReverb.getId());
            }
            float sendLevel = (isEnabled && savedReverbPreset != PresetReverb.PRESET_NONE) ? 1.0f : 0.0f;
            mp.setAuxEffectSendLevel(sendLevel);
        } catch (Throwable ignored) {}
    }

    public synchronized void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        saveSetting("effects_enabled", enabled);

        if (equalizer != null) {
            try { equalizer.setEnabled(enabled); } catch (Throwable ignored) {}
        }
        if (bassBoost != null) {
            try { bassBoost.setEnabled(enabled && savedBassStrength > 0); } catch (Throwable ignored) {}
        }
        if (virtualizer != null) {
            try { virtualizer.setEnabled(enabled && savedVirtualizerStrength > 0); } catch (Throwable ignored) {}
        }
        if (presetReverb != null) {
            try { presetReverb.setEnabled(enabled && savedReverbPreset != PresetReverb.PRESET_NONE); } catch (Throwable ignored) {}
        }
        applyReverbToPlayer();
    }

    public synchronized boolean isEnabled() { return isEnabled; }

    public synchronized void setPresetReverb(short preset) {
        this.savedReverbPreset = preset;
        saveSetting("reverb_preset", (int) preset);

        if (presetReverb != null) {
            try {
                presetReverb.setPreset(preset);
                presetReverb.setEnabled(isEnabled && preset != PresetReverb.PRESET_NONE);
            } catch (Throwable ignored) {}
        }
        applyReverbToPlayer();
    }

    public synchronized short getPresetReverb() { return savedReverbPreset; }

    public synchronized void setBassBoostStrength(short strength) {
        this.savedBassStrength = strength;
        saveSetting("bass_strength", (int) strength);

        if (bassBoost != null && bassBoost.getStrengthSupported()) {
            try {
                bassBoost.setStrength(strength);
                bassBoost.setEnabled(isEnabled && strength > 0);
            } catch (Throwable ignored) {}
        }
    }

    public synchronized short getBassBoostStrength() { return savedBassStrength; }

    public synchronized void setVirtualizerStrength(short strength) {
        this.savedVirtualizerStrength = strength;
        saveSetting("virtualizer_strength", (int) strength);

        if (virtualizer != null && virtualizer.getStrengthSupported()) {
            try {
                virtualizer.setStrength(strength);
                virtualizer.setEnabled(isEnabled && strength > 0);
            } catch (Throwable ignored) {}
        }
    }

    public synchronized short getVirtualizerStrength() { return savedVirtualizerStrength; }

    public synchronized Equalizer getEqualizer() { return equalizer; }

    public synchronized void setEqualizerPreset(short preset) {
        this.savedEqualizerPreset = preset;
        saveSetting("equalizer_preset", (int) preset);

        if (equalizer != null && preset >= 0 && preset < equalizer.getNumberOfPresets()) {
            try {
                equalizer.usePreset(preset);
            } catch (Throwable ignored) {}
        }
    }

    public synchronized short getEqualizerPreset() { return savedEqualizerPreset; }

    public synchronized void setBandLevel(short band, short level) {
        if (equalizer != null) {
            try {
                equalizer.setBandLevel(band, level);
                savedEqualizerPreset = -1;
                saveSetting("equalizer_preset", -1);
            } catch (Throwable ignored) {}
        }
    }

    private void saveSetting(String key, int value) {
        if (contextRef != null && contextRef.get() != null) {
            contextRef.get().getSharedPreferences("retro_audio_effects", Context.MODE_PRIVATE)
                    .edit().putInt(key, value).commit();
        }
    }

    private void saveSetting(String key, boolean value) {
        if (contextRef != null && contextRef.get() != null) {
            contextRef.get().getSharedPreferences("retro_audio_effects", Context.MODE_PRIVATE)
                    .edit().putBoolean(key, value).commit();
        }
    }

    public synchronized void detach() {
        if (mediaPlayerRef != null) {
            MediaPlayer mp = mediaPlayerRef.get();
            if (mp != null) {
                try { mp.setAuxEffectSendLevel(0.0f); } catch (Throwable ignored) {}
            }
            mediaPlayerRef.clear();
        }
        if (equalizer != null) {
            try { equalizer.release(); } catch (Throwable ignored) {}
            equalizer = null;
        }
        if (bassBoost != null) {
            try { bassBoost.release(); } catch (Throwable ignored) {}
            bassBoost = null;
        }
        if (virtualizer != null) {
            try { virtualizer.release(); } catch (Throwable ignored) {}
            virtualizer = null;
        }
        if (presetReverb != null) {
            try { presetReverb.release(); } catch (Throwable ignored) {}
            presetReverb = null;
        }
    }
}
