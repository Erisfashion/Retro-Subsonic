package com.retro.subsonic;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class EqualizerDialog {

    private Context context;
    private Dialog dialog;

    private static final String[] REVERB_NAMES = new String[]{
            "关闭", "小型房间", "中型房间", "大型房间", "中型礼堂", "音乐大厅", "录音棚"
    };
    private static final short[] REVERB_VALUES = new short[]{
            PresetReverb.PRESET_NONE,
            PresetReverb.PRESET_SMALLROOM,
            PresetReverb.PRESET_MEDIUMROOM,
            PresetReverb.PRESET_LARGEROOM,
            PresetReverb.PRESET_MEDIUMHALL,
            PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE
    };

    public EqualizerDialog(Context context) {
        this.context = context;
        initDialog();
    }

    private void initDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = context.getResources().getDisplayMetrics().density;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        root.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (16 * density));

        // 顶栏：标题与总开关
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("专业音效调节");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(tvTitle, titleLp);

        final Button btnMasterToggle = new Button(context);
        final AudioEffectsManager aem = AudioEffectsManager.getInstance();
        btnMasterToggle.setBackgroundResource(R.drawable.bg_btn_pill_accent);
        btnMasterToggle.setText(aem.isEnabled() ? "音效: 开启" : "音效: 关闭");
        btnMasterToggle.setTextColor(0xFF00E5FF);
        btnMasterToggle.setTextSize(11);
        btnMasterToggle.setPadding((int) (12 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
        btnMasterToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean target = !aem.isEnabled();
                aem.setEnabled(target);
                btnMasterToggle.setText(target ? "音效: 开启" : "音效: 关闭");
                btnMasterToggle.setTextColor(target ? 0xFF00E5FF : 0xFF888888);
                Toast.makeText(context, target ? "音效已开启" : "音效已关闭", Toast.LENGTH_SHORT).show();
            }
        });
        header.addView(btnMasterToggle);
        root.addView(header);

        // 环境音效 (混响) 设置行
        TextView tvReverbLabel = new TextView(context);
        tvReverbLabel.setText("环境空间音效 (混响):");
        tvReverbLabel.setTextColor(0xFF00E5FF);
        tvReverbLabel.setTextSize(13);
        tvReverbLabel.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
        root.addView(tvReverbLabel);

        Spinner spinnerReverb = new Spinner(context);
        spinnerReverb.setBackgroundResource(R.drawable.bg_btn_pill);
        spinnerReverb.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
        spinnerReverb.setAdapter(new SimpleDarkAdapter(REVERB_NAMES));

        short currentPreset = aem.getPresetReverb();
        int selectedIndex = 0;
        for (int i = 0; i < REVERB_VALUES.length; i++) {
            if (REVERB_VALUES[i] == currentPreset) {
                selectedIndex = i;
                break;
            }
        }
        spinnerReverb.setSelection(selectedIndex);
        spinnerReverb.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                short preset = REVERB_VALUES[position];
                aem.setPresetReverb(preset);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinnerReverb);

        // 低音增强调节 (BassBoost)
        TextView tvBass = new TextView(context);
        tvBass.setText("低音增强 (Bass Boost):");
        tvBass.setTextColor(0xFFCCCCCC);
        tvBass.setTextSize(13);
        tvBass.setPadding(0, (int) (14 * density), 0, (int) (4 * density));
        root.addView(tvBass);

        SeekBar sbBass = new SeekBar(context);
        sbBass.setMax(1000);
        sbBass.setProgress(aem.getBassBoostStrength());
        sbBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) aem.setBassBoostStrength((short) progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sbBass);

        // 3D 虚拟环绕 (Virtualizer)
        TextView tvVirt = new TextView(context);
        tvVirt.setText("3D 虚拟现场环绕:");
        tvVirt.setTextColor(0xFFCCCCCC);
        tvVirt.setTextSize(13);
        tvVirt.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        root.addView(tvVirt);

        SeekBar sbVirt = new SeekBar(context);
        sbVirt.setMax(1000);
        sbVirt.setProgress(aem.getVirtualizerStrength());
        sbVirt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) aem.setVirtualizerStrength((short) progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sbVirt);

        // 均衡器各频段 (Equalizer Bands)
        final Equalizer eq = aem.getEqualizer();
        if (eq != null) {
            TextView tvEq = new TextView(context);
            tvEq.setText("频段均衡调节 (10-Band EQ):");
            tvEq.setTextColor(0xFFCCCCCC);
            tvEq.setTextSize(13);
            tvEq.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
            root.addView(tvEq);

            final short minEQ = eq.getBandLevelRange()[0];
            final short maxEQ = eq.getBandLevelRange()[1];
            final int bands = eq.getNumberOfBands();

            for (short i = 0; i < bands; i++) {
                final short band = i;
                int centerFreq = eq.getCenterFreq(band) / 1000;
                String freqStr = centerFreq >= 1000 ? ((centerFreq / 1000) + "kHz") : (centerFreq + "Hz");

                TextView tvBand = new TextView(context);
                tvBand.setText(freqStr);
                tvBand.setTextColor(0xFF888C99);
                tvBand.setTextSize(11);
                tvBand.setPadding(0, (int) (2 * density), 0, 0);
                root.addView(tvBand);

                SeekBar sbBand = new SeekBar(context);
                sbBand.setMax(maxEQ - minEQ);
                sbBand.setProgress(eq.getBandLevel(band) - minEQ);
                sbBand.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) aem.setBandLevel(band, (short) (progress + minEQ));
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
                root.addView(sbBand);
            }
        }

        // 关闭按钮
        Button btnClose = new Button(context);
        btnClose.setText("完成");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(13);
        btnClose.setBackgroundResource(R.drawable.bg_btn_default);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * density));
        closeLp.topMargin = (int) (16 * density);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });
        root.addView(btnClose);

        scrollView.addView(root);
        dialog.setContentView(scrollView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (360 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void show() {
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }

    private class SimpleDarkAdapter extends BaseAdapter {
        private String[] items;
        SimpleDarkAdapter(String[] items) { this.items = items; }
        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int position) { return items[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(context);
            tv.setTextSize(12);
            tv.setTextColor(0xFF00E5FF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(10, 4, 10, 4);
            tv.setText(items[position] + " ▾");
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView : new TextView(context);
            tv.setTextSize(13);
            tv.setTextColor(0xFFE0E0E0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(24, 18, 24, 18);
            tv.setBackgroundColor(0xFF1E222B);
            tv.setText(items[position]);
            return tv;
        }
    }
}
