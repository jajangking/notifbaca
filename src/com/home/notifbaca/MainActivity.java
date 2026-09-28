package com.home.notifbaca;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    // Palet cyberpunk: cyan = data, magenta = aksi, lime = siap, amber = perlu izin.
    private static final int NEON_CYAN = 0xFF00E5FF;
    private static final int NEON_MAGENTA = 0xFFFF2BD6;
    private static final int NEON_LIME = 0xFF6BFF3D;
    private static final int NEON_AMBER = 0xFFFFB020;
    private static final int PANEL = 0xFF0B1020;
    private static final int TXT_DIM = 0xFF7688A8;
    private static final int TXT_BRIGHT = 0xFFE9F7FF;

    private TextView status;
    private TextView statusSub;
    private View statusDot;
    private View scanLine;
    private ObjectAnimator pulse;
    private EditText block;
    private CheckBox btOnly;
    private CheckBox clockCb;
    private EditText clockEdit;
    private CheckBox summaryCb;
    private EditText summaryEdit;
    private Spinner voiceSp;
    private CheckBox voiceAll;
    private CheckBox muteCb;
    private TextToSpeech pickerTts;
    private SharedPreferences sp;
    private final java.util.List<String> voiceNames = new java.util.ArrayList<>();
    private final java.util.Map<String, Voice> voiceMap = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(backdrop());
        root.setPadding(dp(18), dp(20), dp(18), dp(28));

        root.addView(header());
        root.addView(buildStatus(), gapTop(14));
        root.addView(scanBar(), gapTop(14));

        // Tiap bagian dibungkus kartu sendiri: latar kartu lebih terang dari
        // halaman dan diberi border neon, jadi batas antar section terlihat
        // jelas tanpa harus menebak dari garis tipis.

        // 01 - suara
        LinearLayout s1 = sectionCard("01", "SUARA");
        s1.addView(caption("Suara yang dipakai buat baca notifikasi"));

        voiceAll = check(new CheckBox(this), "Tampilkan semua bahasa (banyak)");
        voiceAll.setOnCheckedChangeListener((btn, on) -> refreshVoices());
        s1.addView(voiceAll, gapTop(10));

        voiceNames.add("Automatis (default)");
        voiceSp = new Spinner(this);
        voiceSp.setBackground(panel(0xAA00E5FF, 8));
        voiceSp.setPadding(dp(10), dp(4), dp(10), dp(4));
        voiceSp.setAdapter(new NeonAdapter());
        s1.addView(voiceSp, gapTop(8));

        // Wadah TTS terpisah dari NLS, khusus untuk mengisi daftar suara.
        // Wajib di-init di sini: kalau tidak, refreshVoices() langsung bail
        // dan spinner cuma berisi "Automatis (default)".
        pickerTts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) runOnUiThread(this::refreshVoices);
        });

        Button testVoice = btn("▶  TES SUARA TERPILIH", NEON_CYAN, false);
        testVoice.setOnClickListener(v -> testVoice());
        s1.addView(testVoice, gapTop(10));
        root.addView(s1, gapTop(18));

        // 02 - jadwal
        LinearLayout s2 = sectionCard("02", "JADWAL");
        s2.addView(caption("Umumkan jam dan kirim ringkasan harian"));

        clockCb = check(new CheckBox(this), "Umumkan jam di jadwal berikut");
        clockCb.setChecked("1".equals(sp.getString("clock", "")));
        s2.addView(clockCb, gapTop(10));

        clockEdit = field(new EditText(this), "19:00, 22:00  (jam, pisah koma)");
        clockEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        clockEdit.setText(sp.getString("clocktimes", ""));
        s2.addView(clockEdit, gapTop(6));

        summaryCb = check(new CheckBox(this), "Ringkasan notif tiap hari");
        summaryCb.setChecked("1".equals(sp.getString("summary", "")));
        s2.addView(summaryCb, gapTop(12));

        summaryEdit = field(new EditText(this), "07:00  (jam ringkasan)");
        summaryEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        summaryEdit.setText(sp.getString("summarytime", "07:00"));
        s2.addView(summaryEdit, gapTop(6));
        root.addView(s2, gapTop(18));

        // 03 - filter
        LinearLayout s3 = sectionCard("03", "FILTER");
        s3.addView(caption("App mana yang boleh dan tidak boleh dibacakan"));

        btOnly = check(new CheckBox(this), "Hanya baca saat Bluetooth aktif (TWS)");
        btOnly.setChecked("1".equals(sp.getString("btonly", "")));
        btOnly.setOnCheckedChangeListener((btn, on) ->
                sp.edit().putString("btonly", on ? "1" : "").apply());
        s3.addView(btOnly, gapTop(10));

        muteCb = check(new CheckBox(this), "Jeda sementara (dibisukan)");
        muteCb.setChecked("1".equals(sp.getString("muted", "")));
        muteCb.setOnCheckedChangeListener((btn, on) ->
                sp.edit().putString("muted", on ? "1" : "").apply());
        s3.addView(muteCb, gapTop(2));

        s3.addView(caption("Blokir package (pisah koma)"), gapTop(12));
        block = field(new EditText(this), "com.whatsapp, com.example.app");
        block.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        block.setText(sp.getString("block", ""));
        s3.addView(block, gapTop(6));
        root.addView(s3, gapTop(18));

        // aksi
        LinearLayout s4 = sectionCard("04", "AKSI");
        s4.addView(caption("Perubahan baru berlaku setelah disimpan"));

        Button save = btn("◆  SIMPAN SEMUA", NEON_MAGENTA, true);
        save.setOnClickListener(v -> save());
        s4.addView(save, gapTop(12));

        Button grant = btn("⚙  BUKA AKSES NOTIFIKASI", NEON_CYAN, false);
        grant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        s4.addView(grant, gapTop(10));

        Button privacy = btn("i  TENTANG & KEBIJAKAN PRIVASI", NEON_CYAN, false);
        privacy.setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class)));
        s4.addView(privacy, gapTop(10));
        root.addView(s4);

        ScrollView sc = new ScrollView(this);
        sc.setBackground(backdrop());
        // Tanpa ini EditText pertama langsung dapat fokus dan ScrollView
        // meloncat ke tengah, jadi header "NOTIFBACA" kelewat saat app dibuka.
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        sc.addView(root);
        setContentView(sc);
        update();
        maybeShowIntro();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
        startPulse();
    }

    @Override
    protected void onPause() {
        if (pulse != null) pulse.cancel();
        super.onPause();
    }

    // ---------- potongan tampilan ----------

    private View header() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardPanel(NEON_MAGENTA));
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView brand = neon("NOTIFBACA", NEON_CYAN, 30, Typeface.BOLD);
        brand.setShadowLayer(dp(12), 0, 0, 0xFF00E5FF);
        box.addView(brand);

        TextView tag = neon("// notifikasi masuk, jadi suara", NEON_MAGENTA, 11, Typeface.NORMAL);
        tag.setPadding(0, dp(2), 0, 0);
        box.addView(tag);
        return box;
    }

    private View buildStatus() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardPanel());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        statusDot.setBackground(dot(NEON_AMBER));
        row.addView(statusDot, new LinearLayout.LayoutParams(dp(9), dp(9)));

        status = neon("MEMUAT", NEON_LIME, 14, Typeface.BOLD);
        status.setPadding(dp(9), 0, 0, 0);
        row.addView(status);
        card.addView(row);

        statusSub = neon("cek izin akses notifikasi", TXT_DIM, 11, Typeface.NORMAL);
        statusSub.setPadding(0, dp(6), 0, 0);
        card.addView(statusSub);
        return card;
    }

    // Garis "scanner" yang bernapas, biar Kesan hidup.
    private View scanBar() {
        scanLine = new View(this);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x0000E5FF, 0xFF00E5FF, 0xFFFF2BD6, 0x00FF2BD6});
        g.setCornerRadius(dp(1));
        scanLine.setBackground(g);
        return scanLine;
    }

    private void startPulse() {
        if (scanLine == null) return;
        if (pulse != null) pulse.cancel();
        pulse = ObjectAnimator.ofFloat(scanLine, "alpha", 0.2f, 1f, 0.2f);
        pulse.setDuration(2400);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.start();
    }

    // Kartu section: latar jelas lebih terang dari halaman, border neontebal,
    // dan badge nomor di kiri judul. Tiga sinyal sekaligus supaya user langsung
    // tahu di mana satu section berakhir dan yang mana mulai.
    private LinearLayout sectionCard(String no, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardPanel());
        card.setPadding(dp(14), dp(12), dp(14), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = neon(no, 0xFF07101A, 11, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(panel(NEON_CYAN, 5));
        head.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(22)));

        TextView t = neon(title, NEON_CYAN, 13, Typeface.BOLD);
        t.setShadowLayer(dp(7), 0, 0, 0xAA00E5FF);
        t.setPadding(dp(10), 0, 0, 0);
        head.addView(t);
        card.addView(head);

        View line = new View(this);
        GradientDrawable ld = new GradientDrawable();
        ld.setColor(0x7700E5FF);
        line.setBackground(ld);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, hair());
        lp.topMargin = dp(11);
        lp.bottomMargin = dp(2);
        card.addView(line, lp);
        return card;
    }

    // Keterangan kecil pembuka tiap kartu, merah gumsuiin isi kartu.
    private TextView caption(String text) {
        TextView t = neon(text, TXT_DIM, 11, Typeface.NORMAL);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private TextView neon(String text, int color, float spSize, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE, style);
        t.setLetterSpacing(0.05f);
        return t;
    }

    private EditText field(EditText e, String hint) {
        e.setHint(hint);
        e.setHintTextColor(0xFF46587A);
        e.setTextColor(TXT_BRIGHT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        e.setTypeface(Typeface.MONOSPACE);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF070B16);
        g.setCornerRadius(dp(8));
        g.setStroke(hair2(), 0x8800E5FF);
        e.setBackground(g);
        return e;
    }

    private CheckBox check(CheckBox cb, String text) {
        cb.setText(text);
        cb.setTextColor(TXT_BRIGHT);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cb.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{NEON_CYAN, 0xFF39496A}));
        cb.setPadding(dp(2), dp(8), 0, dp(2));
        return cb;
    }

    private Button btn(String text, int stroke, boolean filled) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(filled ? 0xFF0A0410 : stroke);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setLetterSpacing(0.08f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(filled ? stroke : 0xFF080D1A);
        g.setCornerRadius(dp(9));
        g.setStroke(Math.max(1, Math.round(dp(1.3f))), stroke);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), g, null));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        return b;
    }

    // Spinner bawaan tema terang bikin bentrok, jadi barisnya digambar sendiri.
    private class NeonAdapter extends ArrayAdapter<String> {
        NeonAdapter() {
            // Layout bawaan cuma sebagai cadangan; barisnya digambar sendiri
            // di getView/getDropDownView biar ikut tema gelap.
            super(MainActivity.this, android.R.layout.simple_spinner_item, voiceNames);
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            return spinRow(getItem(pos));
        }

        @Override
        public View getDropDownView(int pos, View convert, ViewGroup parent) {
            TextView t = spinRow(getItem(pos));
            t.setBackgroundColor(0xFF080D1A);
            return t;
        }
    }

    private TextView spinRow(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TXT_BRIGHT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        return t;
    }

    // ---------- helper gambar ----------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private int hair() {
        return Math.max(1, Math.round(dp(0.5f)));
    }

    private int hair2() {
        return Math.max(1, Math.round(dp(1f)));
    }

    private LinearLayout.LayoutParams gapTop(int dip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dip);
        return lp;
    }

    private GradientDrawable backdrop() {
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        g.setColors(new int[]{0xFF0B0A22, 0xFF05060E, 0xFF14061E});
        return g;
    }

    private GradientDrawable panel(int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadius(dp(radius));
        g.setStroke(hair2(), stroke);
        return g;
    }

    // Latar kartu: jauh lebih terang dari gradien halaman supaya siluetnya
    // langsung terbaca saat menggulir.
    private GradientDrawable cardPanel() {
        return cardPanel(0xAA00E5FF);
    }

    private GradientDrawable cardPanel(int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF0F1626);
        g.setCornerRadius(dp(14));
        g.setStroke(Math.max(1, Math.round(dp(1.2f))), stroke);
        return g;
    }

    private GradientDrawable dot(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    // ---------- aksi & status ----------

    private void testVoice() {
        if (pickerTts == null) {
            Toast.makeText(this, "TTS belum siap", Toast.LENGTH_SHORT).show();
            return;
        }
        String sel = voiceSp.getSelectedItem() == null
                ? "" : voiceSp.getSelectedItem().toString();
        Voice vv = voiceMap.get(sel);
        if (vv != null) {
            try {
                pickerTts.setVoice(vv);
            } catch (Exception e) {
                Log.w("NotifBaca", "setVoice gagal", e);
            }
        }
        pickerTts.speak("Halo, ini contoh suara yang dipilih.",
                TextToSpeech.QUEUE_FLUSH, null, "nbtest");
    }

    private void save() {
        sp.edit()
                .putString("block", block.getText().toString().trim())
                .putString("btonly", btOnly.isChecked() ? "1" : "")
                .putString("clock", clockCb.isChecked() ? "1" : "")
                .putString("clocktimes", clockEdit.getText().toString().trim())
                .putString("summary", summaryCb.isChecked() ? "1" : "")
                .putString("summarytime", summaryEdit.getText().toString().trim())
                .putString("summaryLast", "")
                .putString("voice", voiceSp.getSelectedItemPosition() == 0
                        ? ""
                        : voiceMap.containsKey(voiceSp.getSelectedItem().toString())
                        ? voiceMap.get(voiceSp.getSelectedItem().toString()).getName() : "")
                .apply();
        Toast.makeText(this, "Disimpan", Toast.LENGTH_SHORT).show();
    }

    private void refreshVoices() {
        if (pickerTts == null) return;
        java.util.Set<Voice> vs = null;
        try {
            vs = pickerTts.getVoices();
        } catch (Exception e) {
            Log.w("NotifBaca", "getVoices gagal", e);
        }
        if (vs == null) return;
        boolean all = voiceAll.isChecked();
        voiceNames.clear();
        voiceNames.add("Automatis (default)");
        voiceMap.clear();
        String savedVoice = sp.getString("voice", "");
        int sel = 0;
        int idx = 1;
        for (Voice v : vs) {
            if (v == null || v.getName() == null) continue;
            java.util.Locale l = v.getLocale();
            boolean idLang = l != null && ("id".equalsIgnoreCase(l.getLanguage())
                    || "in".equalsIgnoreCase(l.getLanguage()));
            if (!all && !idLang) continue;
            java.util.Set<String> feats = v.getFeatures();
            String label = v.getName();
            if (feats != null && feats.contains("genderMale")) label += " (pria)";
            if (feats != null && feats.contains("genderFemale")) label += " (wanita)";
            voiceNames.add(label);
            voiceMap.put(label, v);
            if (savedVoice.equals(v.getName())) sel = idx;
            idx++;
        }
        ((ArrayAdapter) voiceSp.getAdapter()).notifyDataSetChanged();
        voiceSp.setSelection(Math.min(sel, voiceNames.size() - 1));
    }

    @Override
    protected void onDestroy() {
        if (pickerTts != null) {
            pickerTts.stop();
            pickerTts.shutdown();
        }
        super.onDestroy();
    }

    private void update() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        boolean on = enabled != null && enabled.contains(getPackageName());
        statusDot.setBackground(dot(on ? NEON_LIME : NEON_AMBER));
        status.setTextColor(on ? NEON_LIME : NEON_AMBER);
        status.setText(on ? "SISTEM AKTIF" : "MENUNGGU IZIN");
        statusSub.setText(on
                ? "notifikasi yang masuk dibacakan otomatis"
                : "buka \"AKSES NOTIFIKASI\" di bawah ini dulu");
    }

    private void maybeShowIntro() {
        if ("1".equals(sp.getString("intro", ""))) return;
        sp.edit().putString("intro", "1").apply();
        new android.app.AlertDialog.Builder(this)
                .setTitle("Cara kerja NotifBaca")
                .setMessage(
                        "Kita baca notifikasi yang masuk dan membacakannya dengan suara "
                        + "lewat TTS, ideal buat dipakai di jalan lewat TWS.\n\n"
                        + "Supaya berfungsi, aplikasi butuh izin berikut:\n"
                        + "1. Akses notifikasi — biar tahu notif masuk. Isinya HANYA dibacakan "
                        + "lokal, tidak dikirim ke mana pun.\n"
                        + "2. Izin notifikasi sistem (Android 13+) — untuk menjaga layanan "
                        + "tetap aktif.\n\n"
                        + "Pengaturan: akses notifikasi dibuka lewat \"Buka akses notifikasi\" "
                        + "di bawah. Kamu juga bisa blokir aplikasi tertentu, "
                        + "jeda, atau baca hanya saat Bluetooth aktif.")
                .setPositiveButton("Mengerti", (d, w) -> d.dismiss())
                .show();
    }
}
