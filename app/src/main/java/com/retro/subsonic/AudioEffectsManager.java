package com.retro.subsonic;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

public class AudioEffectsManager {

    private static AudioEffectsManager instance;

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private PresetReverb presetReverb;
    private int currentSessionId = 0;

    private AudioEffectsManager() {}

    public static synchronized AudioEffectsManager getInstance() {
        if (instance == null) {
            instance = new AudioEffectsManager();
        }
        return instance;
    }

    // 每次切歌生成新的 SessionId 时，强制将保存的所有参数推送到新 Session
    public synchronized void attachSession(int sessionId, Context context) {
        if (sessionId <= 0) return;
        
        detach();
        currentSessionId = sessionId;

        SharedPreferences sp = context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE);
        boolean eqUserEnabled = sp.getBoolean("user_eq_active", false);

        // 1. 挂载均衡器并强制还原各频段增益
        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(eqUserEnabled);
            short bands = equalizer.getNumberOfBands();
            for (short b = 0; b < bands; b++) {
                int level = sp.getInt("band_" + b, 0);
                equalizer.setBandLevel(b, (short) level);
            }
        } catch (Throwable t) {
            equalizer = null;
        }

        // 2. 挂载重低音增强并还原增益
        try {
            bassBoost = new BassBoost(0, sessionId);
            boolean bassEnabled = sp.getBoolean("bass_enabled", false);
            bassBoost.setEnabled(bassEnabled && eqUserEnabled);
            int strength = sp.getInt("bass_strength", 0);
            bassBoost.setStrength((short) strength);
        } catch (Throwable t) {
            bassBoost = null;
        }

        // 3. 挂载 3D 立体声环绕并还原深度
        try {
            virtualizer = new Virtualizer(0, sessionId);
            boolean virtEnabled = sp.getBoolean("virt_enabled", false);
            virtualizer.setEnabled(virtEnabled && eqUserEnabled);
            int strength = sp.getInt("virt_strength", 0);
            virtualizer.setStrength((short) strength);
        } catch (Throwable t) {
            virtualizer = null;
        }

        // 4. 挂载环境混响并还原预设 (Session 0 全局辅助混音总线)
        try {
            presetReverb = new PresetReverb(0, 0);
            short revPreset = (short) sp.getInt("reverb_preset", PresetReverb.PRESET_NONE);
            if (revPreset != PresetReverb.PRESET_NONE && eqUserEnabled) {
                presetReverb.setEnabled(true);
                presetReverb.setPreset(revPreset);
            } else {
                presetReverb.setEnabled(false);
            }
        } catch (Throwable t) {
            presetReverb = null;
        }
    }

    public Equalizer getEqualizer() {
        return equalizer;
    }

    public void setBandLevel(short band, short level, Context context) {
        context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("user_eq_active", true)
                .putInt("band_" + band, level)
                .commit();

        if (equalizer != null) {
            try {
                equalizer.setEnabled(true);
                equalizer.setBandLevel(band, level);
            } catch (Throwable ignored) {}
        }
    }

    public void setBassBoost(boolean enabled, int strength, Context context) {
        context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("user_eq_active", true)
                .putBoolean("bass_enabled", enabled)
                .putInt("bass_strength", strength)
                .commit();

        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enabled);
                bassBoost.setStrength((short) strength);
            } catch (Throwable ignored) {}
        }
    }

    public boolean isBassBoostEnabled(Context context) {
        return context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .getBoolean("bass_enabled", false);
    }

    public int getBassStrength(Context context) {
        return context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .getInt("bass_strength", 0);
    }

    public void setVirtualizer(boolean enabled, int strength, Context context) {
        context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("user_eq_active", true)
                .putBoolean("virt_enabled", enabled)
                .putInt("virt_strength", strength)
                .commit();

        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enabled);
                virtualizer.setStrength((short) strength);
            } catch (Throwable ignored) {}
        }
    }

    public boolean isVirtualizerEnabled(Context context) {
        return context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .getBoolean("virt_enabled", false);
    }

    public int getVirtualizerStrength(Context context) {
        return context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .getInt("virt_strength", 0);
    }

    public void setReverbPreset(short preset, Context context) {
        context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("user_eq_active", true)
                .putInt("reverb_preset", preset)
                .commit();

        if (presetReverb != null) {
            try {
                if (preset == PresetReverb.PRESET_NONE) {
                    presetReverb.setEnabled(false);
                } else {
                    presetReverb.setEnabled(true);
                    presetReverb.setPreset(preset);
                }
            } catch (Throwable ignored) {}
        }
    }

    public short getReverbPreset(Context context) {
        return (short) context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .getInt("reverb_preset", PresetReverb.PRESET_NONE);
    }

    public synchronized void detach() {
        try { if (equalizer != null) equalizer.release(); } catch (Throwable ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Throwable ignored) {}
        try { if (virtualizer != null) virtualizer.release(); } catch (Throwable ignored) {}
        try { if (presetReverb != null) presetReverb.release(); } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
        virtualizer = null;
        presetReverb = null;
        currentSessionId = 0;
    }
}
