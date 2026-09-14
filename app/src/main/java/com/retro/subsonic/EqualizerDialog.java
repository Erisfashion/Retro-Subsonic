package com.retro.subsonic;

import android.app.Dialog;
import android.content.Context;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

public class EqualizerDialog {

    private Context context;
    private Dialog dialog;
    private AudioEffectsManager aem;

    private CheckBox cbVirt, cbBass;
    private SeekBar sbVirt, sbBass;
    private TextView tvVirtVal, tvBassVal;
    private RadioGroup rgReverb;

    private LinearLayout layoutEqBands;
    private ArrayList<SeekBar> bandSeekBars = new ArrayList<SeekBar>();
    private ArrayList<TextView> bandValTexts = new ArrayList<TextView>();

    public EqualizerDialog(Context context) {
        this.context = context;
        this.aem = AudioEffectsManager.getInstance();
    }

    public void show() {
        context.getSharedPreferences("subsonic_eq_cfg", Context.MODE_PRIVATE)
                .edit().putBoolean("user_eq_active", true).commit();

        dialog = new Dialog(context, android.R.style.Theme_Holo_Dialog_NoActionBar);
        dialog.setContentView(R.layout.dialog_equalizer);
        dialog.setCanceledOnTouchOutside(true);

        initViews();
        setupReverb();
        setupVirtAndBass();
        setupEqualizer();

        dialog.show();
    }

    private void initViews() {
        Button btnClose = (Button) dialog.findViewById(R.id.btn_eq_close);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        cbVirt = (CheckBox) dialog.findViewById(R.id.cb_virt);
        sbVirt = (SeekBar) dialog.findViewById(R.id.sb_virt);
        tvVirtVal = (TextView) dialog.findViewById(R.id.tv_virt_val);

        cbBass = (CheckBox) dialog.findViewById(R.id.cb_bass);
        sbBass = (SeekBar) dialog.findViewById(R.id.sb_bass);
        tvBassVal = (TextView) dialog.findViewById(R.id.tv_bass_val);

        rgReverb = (RadioGroup) dialog.findViewById(R.id.rg_reverb);
        layoutEqBands = (LinearLayout) dialog.findViewById(R.id.layout_eq_bands);
    }

    private void setupReverb() {
        short curRev = aem.getReverbPreset(context);
        if (curRev == PresetReverb.PRESET_SMALLROOM) {
            ((RadioButton) dialog.findViewById(R.id.rb_rev_room)).setChecked(true);
        } else if (curRev == PresetReverb.PRESET_MEDIUMHALL) {
            ((RadioButton) dialog.findViewById(R.id.rb_rev_hall)).setChecked(true);
        } else if (curRev == PresetReverb.PRESET_LARGEHALL) {
            ((RadioButton) dialog.findViewById(R.id.rb_rev_large)).setChecked(true);
        } else if (curRev == PresetReverb.PRESET_PLATE) {
            ((RadioButton) dialog.findViewById(R.id.rb_rev_plate)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_rev_none)).setChecked(true);
        }

        rgReverb.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                short p = PresetReverb.PRESET_NONE;
                if (checkedId == R.id.rb_rev_room) p = PresetReverb.PRESET_SMALLROOM;
                else if (checkedId == R.id.rb_rev_hall) p = PresetReverb.PRESET_MEDIUMHALL;
                else if (checkedId == R.id.rb_rev_large) p = PresetReverb.PRESET_LARGEHALL;
                else if (checkedId == R.id.rb_rev_plate) p = PresetReverb.PRESET_PLATE;
                aem.setReverbPreset(p, context);
            }
        });
    }

    private void setupVirtAndBass() {
        cbVirt.setChecked(aem.isVirtualizerEnabled(context));
        int virtStrength = aem.getVirtualizerStrength(context) / 10;
        sbVirt.setProgress(virtStrength);
        tvVirtVal.setText(virtStrength + "%");

        cbVirt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                aem.setVirtualizer(isChecked, sbVirt.getProgress() * 10, context);
            }
        });

        sbVirt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVirtVal.setText(progress + "%");
                if (fromUser) {
                    aem.setVirtualizer(cbVirt.isChecked(), progress * 10, context);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cbBass.setChecked(aem.isBassBoostEnabled(context));
        int bassStrength = aem.getBassStrength(context) / 10;
        sbBass.setProgress(bassStrength);
        tvBassVal.setText(bassStrength + "%");

        cbBass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                aem.setBassBoost(isChecked, sbBass.getProgress() * 10, context);
            }
        });

        sbBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBassVal.setText(progress + "%");
                if (fromUser) {
                    aem.setBassBoost(cbBass.isChecked(), progress * 10, context);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupEqualizer() {
        Equalizer eq = aem.getEqualizer();
        layoutEqBands.removeAllViews();
        bandSeekBars.clear();
        bandValTexts.clear();

        if (eq == null) {
            TextView tv = new TextView(context);
            tv.setText("请在歌曲播放中调节音效 (原声直通)");
            tv.setTextColor(0xFF888888);
            layoutEqBands.addView(tv);
            return;
        }

        short numBands = eq.getNumberOfBands();
        final short minLevel = eq.getBandLevelRange()[0];
        final short maxLevel = eq.getBandLevelRange()[1];
        final int range = maxLevel - minLevel;

        for (short i = 0; i < numBands; i++) {
            final short bandIndex = i;
            int freq = eq.getCenterFreq(bandIndex) / 1000;
            String freqStr = freq < 1000 ? freq + "Hz" : (freq / 1000) + "kHz";

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 4, 0, 4);

            TextView tvFreq = new TextView(context);
            tvFreq.setText(freqStr);
            tvFreq.setTextColor(0xFFCCCCCC);
            tvFreq.setTextSize(12);
            tvFreq.setWidth(110);

            SeekBar sb = new SeekBar(context);
            sb.setMax(range);
            short curLevel = eq.getBandLevel(bandIndex);
            sb.setProgress(curLevel - minLevel);

            final TextView tvVal = new TextView(context);
            int db = curLevel / 100;
            tvVal.setText((db > 0 ? "+" + db : "" + db) + "dB");
            tvVal.setTextColor(0xFF00E5FF);
            tvVal.setTextSize(12);
            tvVal.setWidth(90);
            tvVal.setGravity(Gravity.RIGHT);

            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    short level = (short) (minLevel + progress);
                    int dbVal = level / 100;
                    tvVal.setText((dbVal > 0 ? "+" + dbVal : "" + dbVal) + "dB");
                    if (fromUser) {
                        aem.setBandLevel(bandIndex, level, context);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            LinearLayout.LayoutParams lpSb = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(tvFreq);
            row.addView(sb, lpSb);
            row.addView(tvVal);

            layoutEqBands.addView(row);
            bandSeekBars.add(sb);
            bandValTexts.add(tvVal);
        }

        Button btnReset = (Button) dialog.findViewById(R.id.btn_eq_reset);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPresetCurve(new int[]{0, 0, 0, 0, 0});
            }
        });

        dialog.findViewById(R.id.btn_preset_pop).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{2, 1, -1, 2, 3}); }
        });
        dialog.findViewById(R.id.btn_preset_rock).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{4, 2, -1, 2, 4}); }
        });
        dialog.findViewById(R.id.btn_preset_dance).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{5, 3, 0, 2, 1}); }
        });
        dialog.findViewById(R.id.btn_preset_classic).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{3, 2, -1, 2, 3}); }
        });
        dialog.findViewById(R.id.btn_preset_vocal).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{-2, 1, 4, 2, -1}); }
        });
        dialog.findViewById(R.id.btn_preset_bass).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{6, 4, 1, -1, -2}); }
        });
        dialog.findViewById(R.id.btn_preset_flat).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyPresetCurve(new int[]{0, 0, 0, 0, 0}); }
        });
    }

    private void applyPresetCurve(int[] dbs) {
        Equalizer eq = aem.getEqualizer();
        if (eq == null) return;
        short minLevel = eq.getBandLevelRange()[0];
        short maxLevel = eq.getBandLevelRange()[1];

        for (int i = 0; i < bandSeekBars.size() && i < dbs.length; i++) {
            short level = (short) (dbs[i] * 100);
            if (level < minLevel) level = minLevel;
            if (level > maxLevel) level = maxLevel;

            aem.setBandLevel((short) i, level, context);
            bandSeekBars.get(i).setProgress(level - minLevel);
            int db = level / 100;
            bandValTexts.get(i).setText((db > 0 ? "+" + db : "" + db) + "dB");
        }
    }
}
