package com.home.notifbaca;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
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
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(ctx, RebindJobService.class))
                    .setPeriodic(PERIOD_MS)
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
        return true;
    }
}
