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
                + "Data yang diakses — diproses lokal di perangkat:\n"
                + "• Isi notifikasi: dibacakan lewat speaker/Bluetooth dan tidak ditulis ke log.\n"
                + "• Status Bluetooth: hanya dipakai untuk opsi \"hanya baca saat BT aktif\" "
                + "(tidak membaca kontak atau riwayat panggilan).\n"
                + "• Riwayat ringkasan: disimpan sementara di memori proses untuk fitur ringkasan "
                + "harian; hilang saat proses/layanan berhenti.\n\n"
                + "Catatan suara network:\n"
                + "• Voice offline diproses di perangkat. Kalau memilih voice network (butuh "
                + "internet), mesin TTS bisa mengirim teks ke penyedia voice. Pilih voice "
                + "offline kalau ingin 100% lokal.\n\n"
                + "Izin yang digunakan:\n"
                + "• Akses notifikasi (NotificationListenerService): inti fitur membaca notifikasi. "
                + "Konten tidak pernah dikirim oleh NotifBaca.\n"
                + "• POST_NOTIFICATIONS (Android 13+): untuk menampilkan status layanan foreground.\n"
                + "• Pengecualian optimasi baterai: hanya untuk menjaga layanan tetap hidup saat "
                + "aplikasi tidak dibuka; tidak mengakses data.\n"
                + "• Mode andal: menahan CPU saat layar mati agar notifikasi tidak tertunda; "
                + "bisa dimatikan dan membuat baterai lebih boros.\n"
                + "• Quick Settings tile \"Jeda Baca\": sekadar toggle jeda, tanpa data tambahan.\n"
                + "NotifBaca tidak memakai mikrofon, lokasi, kontak, kamera, telepon, atau izin "
                + "penyimpanan.\n\n"
                + "Keamanan:\n"
                + "Tidak ada akun, server, pengumpulan, atau pelacakan. Auto Backup Android "
                + "dimatikan, jadi preferensi tidak diunggah ke cloud. Semua pengaturan disimpan "
                + "di perangkat. Menghapus aplikasi menghapus data lokal.\n\n"
                + "Kontak pengembang:\n"
                + "jajangpostmab@gmail.com");
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