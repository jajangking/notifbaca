package com.home.notifbaca;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("NotifBaca — Tentang & Kebijakan Privasi");
        title.setTextSize(19);
        root.addView(title);

        TextView body = new TextView(this);
        body.setTextSize(14);
        body.setLineSpacing(4, 1);
        body.setPadding(0, 24, 0, 0);
        body.setText(
                "Apa yang dilakukan NotifBaca?\n"
                + "Membacakan isi notifikasi (nama aplikasi, judul, dan teks) dengan suara "
                + "lewat Text-to-Speech (TTS) saat tiba, ideal saat berkendara sambil mendengar "
                + "lewat Bluetooth (TWS).\n\n"
                + "Data yang diakses — semua lokal di perangkat, tidak ada yang dikirim keluar:\n"
                + "• Isi notifikasi: dibacakan keras lewat speaker/Bluetooth.\n"
                + "• Status Bluetooth: hanya dipakai untuk opsi \"hanya baca saat BT aktif\" "
                + "(tidak membaca kontak atau riwayat panggilan).\n"
                + "• Riwayat ringkasan: catatan aplikasi yang dibacakan tersimpan lokal di "
                + "penyimpanan aplikasi untuk fitur ringkasan harian (maksimal beberapa lusin "
                + "entri terakhir).\n\n"
                + "Izin yang digunakan:\n"
                + "• Akses notifikasi (NotificationListenerService): inti fitur membaca notifikasi. "
                + "Konten tidak pernah keluar dari perangkat Anda.\n"
                + "• POST_NOTIFICATIONS (Android 13+): untuk menjalankan layanan foreground "
                + "(watchdog agar pembacaan tetap aktif) dan menampilkan status aplikasi.\n"
                + "• Quick Settings tile \"Jeda Baca\": sekadar toggle jeda, tanpa data tambahan.\n"
                + "NotifBaca tidak memakai mikrofon, lokasi, kontak, kamera, telepon, atau izin "
                + "penyimpanan.\n\n"
                + "Keamanan:\n"
                + "Tidak ada akun, tidak ada server, tidak ada pengumpulan atau pelacakan. Semua "
                + "pengaturan disimpan di preferensi lokal perangkat. Menghapus aplikasi menghapus "
                + "semua data lokal.\n\n"
                + "Kontak pengembang:\n"
                + "[isi alamat email Anda di sini]");
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        LinearLayout.LayoutParams svP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        LinearLayout.LayoutParams svL = new LinearLayout.LayoutParams(svP);
        sv.setLayoutParams(svL);
        root.addView(sv);

        Button back = new Button(this);
        back.setText("Kembali");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(back, bp);

        setContentView(root);
    }
}