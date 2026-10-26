package com.home.notifbaca;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;

// Jaring pengaman terakhir kalau proses NotifBaca dibunuh ROM saat app tidak
// dibuka. Watchdog di dalam NLS ikut mati bersama proses, jadi tidak bisa
// menolong. Job ini dijadwalkan ulang oleh sistem dan hanya minta rebind
// kalau heartbeat sudah basi; kalau layanan masih hidup, tidak melakukan apa-apa.
public class RebindJobService extends JobService {
    private static final String TAG = "NotifBaca";
    private static final int JOB_ID = 4201;
    private static final long STALE_MS = 3 * 60 * 1000L; // 3 menit
    private static final long PERIOD_MS = 15 * 60 * 1000L; // minimum JobScheduler

    static void schedule(Context ctx) {
        if (ctx == null) return;
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            // Jangan jadwalkan ulang kalau job yang sama sudah antre:
            // schedule() dengan ID sama MENGGANTI job dan mengulang periode
            // 15 menit dari nol. schedule() dipanggil dari onCreate,
            // onListenerConnected, onListenerDisconnected, MainActivity, dan
            // BootReceiver - saat layanan flapping, job pengaman jadi tidak
            // pernah sempat jalan karena timernya selalu di-reset.
            if (js.getPendingJob(JOB_ID) != null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(ctx, RebindJobService.class))
                    // Flex 5 menit: sistem boleh menjalankan job di akhir
                    // periode saat perangkat kebetulan bangun, tidak harus
                    // menunggu tepat 15 menit (penting di Android 16 yang
                    // menerapkan kuota runtime job per standby bucket).
                    .setPeriodic(PERIOD_MS, 5 * 60 * 1000L)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            int r = js.schedule(job);
            if (r != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "jadwal rebind ditolak: " + r);
            }
        } catch (Exception e) {
            Log.w(TAG, "jadwal rebind gagal", e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        try {
            // Tanpa akses notifikasi, requestRebind tidak akan pernah
            // berhasil; jangan bangunkan proses sia-sia tiap 15 menit.
            String enabled = Settings.Secure.getString(
                    getContentResolver(), "enabled_notification_listeners");
            if (enabled == null || !enabled.contains(getPackageName())) {
                Log.i(TAG, "job rebind: akses notifikasi belum diberi, lewati");
                return false;
            }
            boolean alive = NLS.isAlive();
            boolean connected = NLS.isConnected();
            long last = getSharedPreferences("cfg", MODE_PRIVATE).getLong("last_hb", 0);
            long age = System.currentTimeMillis() - last;
            // Jangan hanya percaya last_hb: setelah proses mati, cap terakhir
            // masih bisa < 3 menit, padahal NLS sudah tidak ada. sAlive hanya
            // true kalau service benar-benar hidup di proses ini.
            if (!alive || !connected || last <= 0 || age > STALE_MS || age < 0) {
                Log.w(TAG, "NLS tidak hidup/terhubung (alive=" + alive
                        + ", connected=" + connected + ", umur hb="
                        + (age / 1000) + "s), minta rebind");
                NotificationListenerService.requestRebind(
                        new ComponentName(this, NLS.class));
            } else {
                Log.i(TAG, "job rebind: layanan masih hidup");
            }
        } catch (Exception e) {
            Log.w(TAG, "job rebind gagal", e);
        }
        // Kerja selesai sinkron; job periodik tetap terjadwal oleh sistem.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Catat alasan berhenti (API 31+) buat diagnosis: di Android 16 job
        // bisa dihentikan karena kuota standby bucket habis
        // (STOP_REASON_QUOTA), dan itu terlihat persis seperti "app mati".
        if (Build.VERSION.SDK_INT >= 31 && params != null) {
            Log.w(TAG, "job rebind dihentikan sistem, stopReason=" + params.getStopReason());
        }
        return true;
    }
}
