package com.home.notifbaca;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private EditText block;
    private CheckBox btOnly;
    private CheckBox clockCb;
    private EditText clockEdit;
    private CheckBox summaryCb;
    private EditText summaryEdit;
    private Spinner voiceSp;
    private CheckBox voiceAll;
    private CheckBox muteCb;
    private CheckBox replyCb;
    private TextView replyStatus;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);

        status = new TextView(this);
        status.setTextSize(18);
        root.addView(status);

        TextView lbl = new TextView(this);
        lbl.setText("Blokir package (pisah koma):");
        root.addView(lbl);

        block = new EditText(this);
        block.setHint("misal: com.whatsapp, com.example.app");
        block.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(block);

        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        block.setText(sp.getString("block", ""));

        btOnly = new CheckBox(this);
        btOnly.setText("Hanya baca saat Bluetooth aktif (TWS)");
        btOnly.setChecked("1".equals(sp.getString("btonly", "")));
        btOnly.setOnCheckedChangeListener((btn, on) -> {
            sp.edit().putString("btonly", on ? "1" : "").apply();
        });
        root.addView(btOnly);

        muteCb = new CheckBox(this);
        muteCb.setText("Jeda sementara (dibisukan)");
        muteCb.setChecked("1".equals(sp.getString("muted", "")));
        muteCb.setOnCheckedChangeListener((btn, on) -> {
            sp.edit().putString("muted", on ? "1" : "").apply();
        });
        root.addView(muteCb);

        clockCb = new CheckBox(this);
        clockCb.setText("Umumkan jam di jadwal berikut");
        clockCb.setChecked("1".equals(sp.getString("clock", "")));
        root.addView(clockCb);

        clockEdit = new EditText(this);
        clockEdit.setHint("Jam (HH:mm, pisah koma), mis. 19:00, 22:00");
        clockEdit.setText(sp.getString("clocktimes", ""));
        root.addView(clockEdit);

        summaryCb = new CheckBox(this);
        summaryCb.setText("Ringkasan notif tiap hari");
        summaryCb.setChecked("1".equals(sp.getString("summary", "")));
        root.addView(summaryCb);

        summaryEdit = new EditText(this);
        summaryEdit.setHint("Jam ringkasan (HH:mm), mis. 07:00");
        summaryEdit.setText(sp.getString("summarytime", "07:00"));
        root.addView(summaryEdit);

        TextView lblVoice = new TextView(this);
        lblVoice.setText("Suara TTS:");
        root.addView(lblVoice);

        voiceAll = new CheckBox(this);
        voiceAll.setText("Tampilkan semua bahasa (banyak)");
        voiceAll.setChecked(false);
        voiceAll.setOnCheckedChangeListener((btn, on) -> refreshVoices());
        root.addView(voiceAll);

        voiceNames.add("Automatis (default)");
        voiceSp = new Spinner(this);
        ArrayAdapter<String> va = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, voiceNames);
        va.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSp.setAdapter(va);
        root.addView(voiceSp);

        Button testVoice = new Button(this);
        testVoice.setText("Tes suara terpilih");
        testVoice.setOnClickListener(v -> {
            if (pickerTts == null) {
                Toast.makeText(this, "TTS belum siap", Toast.LENGTH_SHORT).show();
                return;
            }
            String sel = voiceSp.getSelectedItem() == null ? ""
                    : voiceSp.getSelectedItem().toString();
            Voice vv = voiceMap.get(sel);
            if (vv != null) {
                try {
                    pickerTts.setVoice(vv);
                } catch (Exception e) {
                    Log.w("NotifBaca", "setVoice gagal", e);
                }
            }
            pickerTts.speak("Halo, ini contoh suara yang dipilih.", TextToSpeech.QUEUE_FLUSH, null, "nbtest");
        });
        root.addView(testVoice);

        pickerTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                runOnUiThread(this::refreshVoices);
            }
        });

        Button save = new Button(this);
        save.setText("Simpan semua");
        save.setOnClickListener(v -> {
            sp.edit()
                    .putString("block", block.getText().toString().trim())
                    .putString("btonly", btOnly.isChecked() ? "1" : "")
                    .putString("clock", clockCb.isChecked() ? "1" : "")
                    .putString("clocktimes", clockEdit.getText().toString().trim())
                    .putString("summary", summaryCb.isChecked() ? "1" : "")
                    .putString("summarytime", summaryEdit.getText().toString().trim())
                    .putString("summaryLast", "")
                    .putString("voice", voiceSp.getSelectedItemPosition() == 0 ? ""
                            : voiceMap.containsKey(voiceSp.getSelectedItem().toString())
                            ? voiceMap.get(voiceSp.getSelectedItem().toString()).getName() : "")
                    .apply();
            Toast.makeText(this, "Disimpan", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button grant = new Button(this);
        grant.setText("Buka pengaturan akses notifikasi");
        grant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(grant);

        replyCb = new CheckBox(this);
        replyCb.setText("Balas balik via suara (EKSPERIMENTAL)");
        replyCb.setChecked("1".equals(sp.getString("reply", "")));
        replyCb.setOnCheckedChangeListener((b, on) -> {
            sp.edit().putString("reply", on ? "1" : "").apply();
            if (on) {
                updateReplyStatus();
                if (Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission("android.permission.RECORD_AUDIO")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 2);
                }
            } else {
                replyStatus.setText("");
            }
        });
        root.addView(replyCb);

        Button accBtn = new Button(this);
        accBtn.setText("Aktifkan / cek aksesibilitas (untuk balas)");
        accBtn.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accBtn);

        replyStatus = new TextView(this);
        replyStatus.setTextSize(14);
        root.addView(replyStatus);

        setContentView(root);
        update();
        updateReplyStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
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
                getContentResolver(), "enabled_notification_listeners") == null ? "" :
                Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean on = enabled.contains(getPackageName());
        status.setText(on
                ? "Status: AKTIF — notifikasi bakal dibacakan"
                : "Status: BELUM aktif — izinkan akses notifikasi dulu");
        updateReplyStatus();
    }

    private void updateReplyStatus() {
        if (replyStatus == null) return;
        boolean rep = "1".equals(sp.getString("reply", ""));
        if (!rep) {
            replyStatus.setText("");
            return;
        }
        replyStatus.setText(ReplyAccessibilityService.isOnline()
                ? "Balas suara SIAP (aksesibilitas aktif)."
                : "Balas suara: aktifkan NotifBaca di Setelan Aksesibilitas.");
    }
}