package com.home.notifbaca;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

    // Palet tenang: satu aksen, teks gelap di atas kartu putih.
    private static final int ACCENT = 0xFF0B57D0;
    private static final int PAGE_BG = 0xFFF2F4F7;
    private static final int CARD_BG = 0xFFFFFFFF;
    private static final int CARD_STROKE = 0xFFE1E6EB;
    private static final int FIELD_BG = 0xFFF6F8FA;
    private static final int TXT_MAIN = 0xFF1A1C1E;
    private static final int TXT_SUB = 0xFF5A6470;
    private static final int GREEN_OK = 0xFF1A7F37;
    private static final int AMBER_NEED = 0xFFB54708;

    private TextView status;
    private TextView statusSub;
    private View statusDot;
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
        root.setBackgroundColor(PAGE_BG);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        root.addView(header());
        root.addView(buildStatus(), gapTop(12));

        // 01 - suara
        LinearLayout s1 = sectionCard("SUARA");
        s1.addView(caption("Suara yang dipakai buat baca notifikasi"));

        voiceAll = check(new CheckBox(this), "Tampilkan semua bahasa (banyak)");
        voiceAll.setOnCheckedChangeListener((btn, on) -> refreshVoices());
        s1.addView(voiceAll, gapTop(8));

        voiceNames.add("Automatis (default)");
        voiceSp = new Spinner(this);
        voiceSp.setAdapter(new PlainAdapter());
        s1.addView(voiceSp, gapTop(8));

        // Wadah TTS terpisah dari NLS, khusus untuk mengisi daftar suara.
        // Wajib di-init di sini: kalau tidak, refreshVoices() langsung bail
        // dan spinner cuma berisi "Automatis (default)".
        pickerTts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) runOnUiThread(this::refreshVoices);
        });

        Button testVoice = btn("Tes suara terpilih", ACCENT, false);
        testVoice.setOnClickListener(v -> testVoice());
        s1.addView(testVoice, gapTop(10));
        root.addView(s1, gapTop(12));

        // 02 - jadwal
        LinearLayout s2 = sectionCard("JADWAL");
        s2.addView(caption("Umumkan jam dan kirim ringkasan harian"));

        clockCb = check(new CheckBox(this), "Umumkan jam di jadwal berikut");
        clockCb.setChecked("1".equals(sp.getString("clock", "")));
        s2.addView(clockCb, gapTop(8));

        clockEdit = field(new EditText(this), "19:00, 22:00  (jam, pisah koma)");
        clockEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        clockEdit.setText(sp.getString("clocktimes", ""));
        s2.addView(clockEdit, gapTop(6));

        summaryCb = check(new CheckBox(this), "Ringkasan notif tiap hari");
        summaryCb.setChecked("1".equals(sp.getString("summary", "")));
        s2.addView(summaryCb, gapTop(10));

        summaryEdit = field(new EditText(this), "07:00  (jam ringkasan)");
        summaryEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        summaryEdit.setText(sp.getString("summarytime", "07:00"));
        s2.addView(summaryEdit, gapTop(6));
        root.addView(s2, gapTop(12));

        // 03 - filter
        LinearLayout s3 = sectionCard("FILTER");
        s3.addView(caption("App mana yang boleh dan tidak boleh dibacakan"));

        btOnly = check(new CheckBox(this), "Hanya baca saat Bluetooth aktif (TWS)");
        btOnly.setChecked("1".equals(sp.getString("btonly", "")));
        btOnly.setOnCheckedChangeListener((btn, on) ->
                sp.edit().putString("btonly", on ? "1" : "").apply());
        s3.addView(btOnly, gapTop(8));

        muteCb = check(new CheckBox(this), "Jeda sementara (dibisukan)");
        muteCb.setChecked("1".equals(sp.getString("muted", "")));
        muteCb.setOnCheckedChangeListener((btn, on) ->
                sp.edit().putString("muted", on ? "1" : "").apply());
        s3.addView(muteCb, gapTop(2));

        s3.addView(caption("Blokir package (pisah koma)"), gapTop(10));
        block = field(new EditText(this), "com.whatsapp, com.example.app");
        block.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        block.setText(sp.getString("block", ""));
        s3.addView(block, gapTop(6));
        root.addView(s3, gapTop(12));

        // aksi
        LinearLayout s4 = sectionCard("AKSI");
        s4.addView(caption("Perubahan baru berlaku setelah disimpan"));

        Button save = btn("Simpan semua", ACCENT, true);
        save.setOnClickListener(v -> save());
        s4.addView(save, gapTop(10));

        Button grant = btn("Buka akses notifikasi", ACCENT, false);
        grant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        s4.addView(grant, gapTop(10));

        Button reconnect = btn("Sambung ulang layanan", ACCENT, false);
        reconnect.setOnClickListener(v -> reconnect());
        s4.addView(reconnect, gapTop(10));

        Button privacy = btn("Tentang & kebijakan privasi", ACCENT, false);
        privacy.setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class)));
        s4.addView(privacy, gapTop(10));
        root.addView(s4, gapTop(12));

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(PAGE_BG);
        // Tanpa ini EditText pertama langsung dapat fokus dan ScrollView
        // meloncat ke tengah, jadi header kelewat saat app dibuka.
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
    }

    // ---------- potongan tampilan ----------

    private View header() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBg());
        box.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView brand = txt("NotifBaca", TXT_MAIN, 22, Typeface.BOLD);
        box.addView(brand);

        TextView tag = txt("Notifikasi masuk, dibacakan jadi suara", TXT_SUB, 13, Typeface.NORMAL);
        tag.setPadding(0, dp(2), 0, 0);
        box.addView(tag);
        return box;
    }

    private View buildStatus() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        statusDot.setBackground(dot(AMBER_NEED));
        row.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        status = txt("Memuat", TXT_MAIN, 15, Typeface.BOLD);
        status.setPadding(dp(10), 0, 0, 0);
        row.addView(status);
        card.addView(row);

        statusSub = txt("Cek izin akses notifikasi", TXT_SUB, 12, Typeface.NORMAL);
        statusSub.setPadding(0, dp(4), 0, 0);
        card.addView(statusSub);
        return card;
    }

    // Kartu section: putih, border abu tipis, judul biasa.
    private LinearLayout sectionCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(12), dp(14), dp(14));

        TextView t = txt(title, TXT_MAIN, 13, Typeface.BOLD);
        card.addView(t);

        View line = new View(this);
        line.setBackgroundColor(CARD_STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp(1f))));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(2);
        card.addView(line, lp);
        return card;
    }

    private TextView caption(String text) {
        TextView t = txt(text, TXT_SUB, 12, Typeface.NORMAL);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private TextView txt(String text, int color, float spSize, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        t.setTextColor(color);
        t.setTypeface(null, style);
        return t;
    }

    private EditText field(EditText e, String hint) {
        e.setHint(hint);
        e.setHintTextColor(0xFF8A94A0);
        e.setTextColor(TXT_MAIN);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(FIELD_BG);
        g.setCornerRadius(dp(8));
        g.setStroke(Math.max(1, Math.round(dp(1f))), CARD_STROKE);
        e.setBackground(g);
        return e;
    }

    private CheckBox check(CheckBox cb, String text) {
        cb.setText(text);
        cb.setTextColor(TXT_MAIN);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cb.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{ACCENT, 0xFF8A94A0}));
        cb.setPadding(dp(2), dp(8), 0, dp(2));
        return cb;
    }

    private Button btn(String text, int accent, boolean filled) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(filled ? 0xFFFFFFFF : accent);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(filled ? accent : CARD_BG);
        g.setCornerRadius(dp(8));
        g.setStroke(Math.max(1, Math.round(dp(1f))), filled ? accent : CARD_STROKE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), g, null));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        return b;
    }

    // Spinner ikut tema terang standar.
    private class PlainAdapter extends ArrayAdapter<String> {
        PlainAdapter() {
            super(MainActivity.this, android.R.layout.simple_spinner_item, voiceNames);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            TextView t = spinRow(getItem(pos));
            return t;
        }

        @Override
        public View getDropDownView(int pos, View convert, ViewGroup parent) {
            return super.getDropDownView(pos, convert, parent);
        }
    }

    private TextView spinRow(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(TXT_MAIN);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }

    // ---------- helper gambar ----------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private LinearLayout.LayoutParams gapTop(int dip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dip);
        return lp;
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD_BG);
        g.setCornerRadius(dp(12));
        g.setStroke(Math.max(1, Math.round(dp(1f))), CARD_STROKE);
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
        boolean izin = enabled != null && enabled.contains(getPackageName());
        // Izin ada belum tentu layanan jalan (dibunuh battery-saver / putus
        // bind). NLS menulis cap last_hb tiap 15 detik saat masih hidup.
        long lastHb = sp.getLong("last_hb", 0);
        boolean hidup = izin && (System.currentTimeMillis() - lastHb < 30000);
        if (!izin) {
            statusDot.setBackground(dot(AMBER_NEED));
            status.setTextColor(AMBER_NEED);
            status.setText("Menunggu izin");
            statusSub.setText("Buka \"akses notifikasi\" di bawah ini dulu");
        } else if (hidup) {
            statusDot.setBackground(dot(GREEN_OK));
            status.setTextColor(GREEN_OK);
            status.setText("Sistem aktif");
            statusSub.setText("Notifikasi yang masuk dibacakan otomatis");
        } else {
            statusDot.setBackground(dot(AMBER_NEED));
            status.setTextColor(AMBER_NEED);
            status.setText("Izin ok, layanan belum jalan");
            statusSub.setText("Tekan \"Sambung ulang layanan\"; "
                    + "matikan battery-saver/autostart-block buat NotifBaca");
        }
    }

    // Paksa sistem bind ulang listener. Perlu di ROM agresif (Transsion
    // dkk) yang sering memutus service walau izin masih tercatat.
    private void reconnect() {
        try {
            ComponentName cn = new ComponentName(this, NLS.class);
            getPackageManager().setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            getPackageManager().setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            if (Build.VERSION.SDK_INT >= 24) {
                android.service.notification.NotificationListenerService.requestRebind(cn);
            }
            Toast.makeText(this, "Permintaan sambung ulang dikirim", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("NotifBaca", "reconnect gagal", e);
            Toast.makeText(this, "Gagal sambung ulang", Toast.LENGTH_SHORT).show();
        }
        update();
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
