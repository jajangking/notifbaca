package com.home.notifbaca;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private EditText block;
    private CheckBox btOnly;

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

        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        block.setText(sp.getString("block", ""));

        btOnly = new CheckBox(this);
        btOnly.setText("Hanya baca saat Bluetooth aktif (TWS)");
        btOnly.setChecked("1".equals(sp.getString("btonly", "")));
        btOnly.setOnCheckedChangeListener((btn, on) -> {
            sp.edit().putString("btonly", on ? "1" : "").apply();
        });
        root.addView(btOnly);

        Button save = new Button(this);
        save.setText("Simpan semua");
        save.setOnClickListener(v -> {
            sp.edit()
                    .putString("block", block.getText().toString().trim())
                    .putString("btonly", btOnly.isChecked() ? "1" : "")
                    .apply();
            Toast.makeText(this, "Disimpan", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button grant = new Button(this);
        grant.setText("Buka pengaturan akses notifikasi");
        grant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(grant);

        setContentView(root);
        update();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    private void update() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners") == null ? "" :
                Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean on = enabled.contains(getPackageName());
        status.setText(on
                ? "Status: AKTIF — notifikasi bakal dibacakan"
                : "Status: BELUM aktif — izinkan akses notifikasi dulu");
    }
}