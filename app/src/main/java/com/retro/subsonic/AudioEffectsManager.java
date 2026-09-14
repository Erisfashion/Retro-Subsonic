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

    // 仅在用户手动启用音效时安全挂载，杜绝 mediaserver 崩溃
    public synchronized void attachSession(int sessionId, Context context) {
        if (sessionId <= 0) return;
        if (sessionId == currentSessionId && equalizer != null) return;

        detach();
        currentSessionId = sessionId;

        SharedPreferences sp = context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE);
        boolean eqUserEnabled = sp.getBoolean("user_eq_active", false);
        if (!eqUserEnabled) return; // 默认不启用，保证原声直通最稳

        // 1. 均衡器
        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(true);
            short bands = equalizer.getNumberOfBands();
            for (short b = 0; b < bands; b++) {
                int level = sp.getInt("band_" + b, 0);
                equalizer.setBandLevel(b, (short) level);
            }
        } catch (Throwable t) {
            equalizer = null;
        }

        // 2. 重低音增强
        try {
            bassBoost = new BassBoost(0, sessionId);
            boolean bassEnabled = sp.getBoolean("bass_enabled", false);
            bassBoost.setEnabled(bassEnabled);
            int strength = sp.getInt("bass_strength", 0);
            bassBoost.setStrength((short) strength);
        } catch (Throwable t) {
            bassBoost = null;
        }

        // 3. 3D 立体声环绕
        try {
            virtualizer = new Virtualizer(0, sessionId);
            boolean virtEnabled = sp.getBoolean("virt_enabled", false);
            virtualizer.setEnabled(virtEnabled);
            int strength = sp.getInt("virt_strength", 0);
            virtualizer.setStrength((short) strength);
        } catch (Throwable t) {
            virtualizer = null;
        }

        // 4. 环境混响 (必须挂在 Session 0 全局混音上，绝不能挂在 MediaPlayer Session 上)
        try {
            presetReverb = new PresetReverb(0, 0);
            short revPreset = (short) sp.getInt("reverb_preset", PresetReverb.PRESET_NONE);
            if (revPreset != PresetReverb.PRESET_NONE) {
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
        if (equalizer != null) {
            try {
                equalizer.setBandLevel(band, level);
                context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                        .edit().putInt("band_" + band, level).commit();
            } catch (Throwable ignored) {}
        }
    }

    public void setBassBoost(boolean enabled, int strength, Context context) {
        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enabled);
                bassBoost.setStrength((short) strength);
                context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                        .edit().putBoolean("bass_enabled", enabled)
                        .putInt("bass_strength", strength).commit();
            } catch (Throwable ignored) {}
        }
    }

    public boolean isBassBoostEnabled() {
        return bassBoost != null && bassBoost.getEnabled();
    }

    public int getBassStrength() {
        return bassBoost != null ? bassBoost.getRoundedStrength() : 0;
    }

    public void setVirtualizer(boolean enabled, int strength, Context context) {
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enabled);
                virtualizer.setStrength((short) strength);
                context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                        .edit().putBoolean("virt_enabled", enabled)
                        .putInt("virt_strength", strength).commit();
            } catch (Throwable ignored) {}
        }
    }

    public boolean isVirtualizerEnabled() {
        return virtualizer != null && virtualizer.getEnabled();
    }

    public int getVirtualizerStrength() {
        return virtualizer != null ? virtualizer.getRoundedStrength() : 0;
    }

    public void setReverbPreset(short preset, Context context) {
        if (presetReverb != null) {
            try {
                if (preset == PresetReverb.PRESET_NONE) {
                    presetReverb.setEnabled(false);
                } else {
                    presetReverb.setEnabled(true);
                    presetReverb.setPreset(preset);
                }
                context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                        .edit().putInt("reverb_preset", preset).commit();
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
