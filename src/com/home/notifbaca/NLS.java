package com.home.notifbaca;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NLS extends NotificationListenerService {
    private static final String TAG = "NotifBaca";
    private static final String CHANNEL = "run";
    private static final String PREF = "cfg";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_CLOCK = "clock";
    private static final String KEY_CLOCKTIMES = "clocktimes";
    private static final String KEY_SUMMARY = "summary";
    private static final String KEY_SUMMARYTIME = "summarytime";
    private static final String KEY_SUMMARYLAST = "summaryLast";
    private static final String KEY_SPOKEN = "spoken";
    private static final String KEY_LAST_HB = "last_hb";
    // Rescan saat rebind cuma buat notif segar, bukan basi berjam-jam.
    private static final long CATCHUP_MAX_AGE = 120000;
    private static final int CATCHUP_MAX_COUNT = 3;

    // Dipakai RebindJobService dan UI (satu proses) untuk membedakan
    // "layanan masih hidup" dari "proses baru hidup karena job, tapi NLS
    // belum ter-bind". sConnected khusus status listener.
    private static volatile boolean sAlive;
    private static volatile boolean sConnected;

    static boolean isAlive() {
        return sAlive;
    }

    static boolean isConnected() {
        return sConnected;
    }

    private TextToSpeech tts;
    private PowerManager.WakeLock wl;
    private PowerManager powerManager;
    private PowerManager.WakeLock cpuWl;
    private BroadcastReceiver screenReceiver;
    private boolean holdCpu;
    private HandlerThread ttsThread;
    private Handler ttsHandler;
    private Handler mainHandler;
    private AudioManager am;
    private AudioAttributes ttsAudio;
    private AudioFocusRequest afr;
    private int hbCount;
    private volatile boolean destroyed;
    private long lastClockMin = -1;
    private static final int MAX_SPEAK_QUEUE = 10;
    private static final int TTS_INIT_MAX = 6;
    private static final long TTS_RETRY_MS = 15000;
    private final java.util.concurrent.ConcurrentLinkedQueue<String> speakQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile boolean ttsReady;
    private volatile boolean ttsSpeaking;
    private int ttsInitAttempts;
    private int ttsInitGeneration;
    private int fgAttempts;
    private final Set<String> recent = new HashSet<>();
    private final java.util.List<String> summaryLog = new java.util.ArrayList<>();

    private static final Set<String> NOISE = new HashSet<>();

    static {
        NOISE.add("android");
        NOISE.add("com.android.systemui");
        NOISE.add("com.android.settings");
        NOISE.add("com.android.vending");
        NOISE.add("com.google.android.gms");
        NOISE.add("com.google.android.inputmethod.latin");
        NOISE.add("com.google.android.googlequicksearchbox");
        NOISE.add("com.termux");
        NOISE.add("com.termux.api");
        NOISE.add("com.termux.gui");
        NOISE.add("com.facemoji.lite.transsion");
    }

@Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        sAlive = true;
        sConnected = false;
        loadSpoken();
        initChannel();
        startFg();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        powerManager = pm;
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotifBaca:speak");
        wl.setReferenceCounted(false);
        ttsThread = new HandlerThread("tts");
        ttsThread.start();
        ttsHandler = new Handler(ttsThread.getLooper());
        mainHandler = new Handler(android.os.Looper.getMainLooper());
        // Opsional (default aktif): tahan CPU saat layar mati supaya callback
        // notifikasi tidak ikut tertidur. Tanpa ini proses memang hidup, tapi
        // handler baru jalan lagi saat layar/app dibuka — persis gejala
        // "layanan mati kalau tidak dibuka". Bisa dimatikan di UI.
        holdCpu = "1".equals(getSharedPreferences(PREF, MODE_PRIVATE)
                .getString("holdcpu", "1"));
        cpuWl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotifBaca:cpu");
        cpuWl.setReferenceCounted(false);
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateCpuWakeLock();
            }
        };
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenReceiver, f);
        } catch (Exception e) {
            Log.w(TAG, "register receiver layar gagal", e);
        }
        updateCpuWakeLock();
        startHeartbeat();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        // USAGE_MEDIA: TTS main di stream media, jadi keras lemahnya ikut
        // volume perangkat apa adanya. App tidak lagi menaikkan atau menurunkan
        // volume sendiri — slider volume di UI sudah dihapus.
        ttsAudio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        // GAIN_TRANSIENT, bukan MAY_DUCK. Duck cuma permintaan: app musik boleh
        // mengabaikannya, jadi suara TTS jadi beradu dengan musik. TRANSIENT
        // memberi tahu app musik fokusnya hilang sementara, dan kebanyakan
        // app musik menghentikan atau menurunkan volumenya sendiri.
        afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ttsAudio)
                .setOnAudioFocusChangeListener(f -> Log.i(TAG, "focus berubah: " + f))
                .build();
        initTts();
        startMinuteTicker();
        RebindJobService.schedule(this);
    }

    // TTS bisa gagal init kalau engine belum siap setelah boot. Dulu kegagalan
    // ini didiamkan: ttsReady tetap false dan semua notifikasi menumpuk hilang.
    private void initTts() {
        if (destroyed || ttsInitAttempts >= TTS_INIT_MAX) return;
        final int gen = ++ttsInitGeneration;
        ttsInitAttempts++;
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "shutdown TTS lama gagal", e);
            }
            tts = null;
        }
        try {
            tts = new TextToSpeech(this, status -> {
                if (destroyed || gen != ttsInitGeneration) return;
                if (status != TextToSpeech.SUCCESS) {
                    ttsReady = false;
                    Log.e(TAG, "init TTS gagal status=" + status
                            + " (percobaan " + ttsInitAttempts + "/" + TTS_INIT_MAX + ")");
                    mainHandler.postDelayed(this::initTts, TTS_RETRY_MS);
                    return;
                }
                try {
                    configureTts();
                    ttsInitAttempts = 0;
                    ttsReady = true;
                    drainQueue();
                } catch (Exception e) {
                    ttsReady = false;
                    Log.e(TAG, "konfigurasi TTS gagal", e);
                    mainHandler.postDelayed(this::initTts, TTS_RETRY_MS);
                }
            });
        } catch (Exception e) {
            ttsReady = false;
            Log.e(TAG, "TextToSpeech gagal dibuat", e);
            mainHandler.postDelayed(this::initTts, TTS_RETRY_MS);
        }
    }

    private void configureTts() {
        String saved = getSharedPreferences(PREF, MODE_PRIVATE).getString("voice", "");
        boolean voiceFound = false;
        if (saved != null && !saved.isEmpty()) {
            try {
                java.util.Set<Voice> vs = tts.getVoices();
                if (vs != null) {
                    for (Voice vo : vs) {
                        if (vo != null && saved.equals(vo.getName())) {
                            try {
                                tts.setVoice(vo);
                                voiceFound = true;
                            } catch (Exception e) {
                                Log.e(TAG, "setVoice gagal, fallback default", e);
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "getVoices gagal", e);
            }
        }
        // Kalau voice tersimpan sudah tidak ada (engine berubah), jangan diam
        // memakai voice bawaan yang mungkin berbahasa lain.
        if (!voiceFound) {
            int r = tts.setLanguage(Locale.getDefault());
            if (r == TextToSpeech.LANG_MISSING_DATA
                    || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "bahasa default tidak didukung TTS");
            }
        }
        tts.setAudioAttributes(ttsAudio);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                speakDone();
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                speakDone();
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                speakDone();
            }
        });
    }

    private void startMinuteTicker() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                try {
                    tickMinute();
                } catch (Exception e) {
                    Log.e(TAG, "tick gagal", e);
                }
                mainHandler.postDelayed(this, 60000);
            }
        }, 60000);
    }

    private void tickMinute() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boolean onBT = !"1".equals(sp.getString("btonly", "")) || btActive();
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        Log.i(TAG, "tick " + String.format(Locale.US, "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));

        if (!destroyed && "1".equals(sp.getString(KEY_CLOCK, "")) && onBT) {
            String times = sp.getString(KEY_CLOCKTIMES, "");
            String hh = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.HOUR_OF_DAY));
            String mm = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MINUTE));
            String nowStr = hh + ":" + mm;
            if (times != null) {
                for (String t : times.split(",")) {
                    String tt = t.trim().replace('.', ':');
                    if (tt.matches("\\d{2}:\\d{2}") && tt.equals(nowStr)) {
                        long em = now / 60000;
                        if (em != lastClockMin) {
                            lastClockMin = em;
                            int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
                            int m = cal.get(java.util.Calendar.MINUTE);
                            speakSimple("Sekarang pukul " + h + " " + (m < 10 ? "0" : "") + m + ".");
                        }
                        break;
                    }
                }
            }
        }

        if (!destroyed && "1".equals(sp.getString(KEY_SUMMARY, ""))) {
            String target = sp.getString(KEY_SUMMARYTIME, "07:00");
            if (target != null) target = target.trim().replace('.', ':');
            if (target == null || !target.matches("\\d{2}:\\d{2}")) target = "07:00";
            String hh = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.HOUR_OF_DAY));
            String mm = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MINUTE));
            if (target.equals(hh + ":" + mm)) {
                String today = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
                        .format(new java.util.Date());
                if (!today.equals(sp.getString(KEY_SUMMARYLAST, ""))) {
                    sp.edit().putString(KEY_SUMMARYLAST, today).apply();
                    if (onBT) speakSummary();
                }
            }
        }
    }

    private void speakSummary() {
        final java.util.List<String> copy;
        synchronized (summaryLog) {
            copy = new java.util.ArrayList<>(summaryLog);
            summaryLog.clear();
        }
        if (copy.isEmpty()) return;
        StringBuilder sb = new StringBuilder("Ringkasan notifikasi Anda. ");
        for (String s : copy) sb.append(s).append(" ");
        speakSimple(sb.toString().trim());
    }

    private void updateCpuWakeLock() {
        if (destroyed || cpuWl == null) return;
        boolean want = holdCpu && sConnected && powerManager != null
                && !powerManager.isInteractive();
        try {
            if (want && !cpuWl.isHeld()) {
                cpuWl.acquire();
                Log.i(TAG, "cpu_wl on");
            } else if (!want && cpuWl.isHeld()) {
                cpuWl.release();
                Log.i(TAG, "cpu_wl off");
            }
        } catch (Exception e) {
            Log.w(TAG, "atur cpu_wl gagal", e);
        }
    }

    private void writeHeartbeat() {
        // Cap waktu hidup buat UI: bedakan "izin ada" vs "layanan jalan".
        try {
            getSharedPreferences(PREF, MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_HB, System.currentTimeMillis()).apply();
        } catch (Exception e) {
            Log.w(TAG, "tulis last_hb gagal", e);
        }
    }

    private void startHeartbeat() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                Log.i(TAG, "hb " + (++hbCount));
                writeHeartbeat();
                // Baca ulang preferensi: user bisa mematikan mode andal tanpa
                // restart layanan.
                holdCpu = "1".equals(getSharedPreferences(PREF, MODE_PRIVATE)
                        .getString("holdcpu", "1"));
                updateCpuWakeLock();
                mainHandler.postDelayed(this, 15000);
            }
        }, 15000);
        mainHandler.postDelayed(new Runnable() {
            private int fails;

            @Override
            public void run() {
                if (destroyed) return;
                Log.i(TAG, "watch check");
                // Cukup cek binder listener masih hidup. Dulu kode ini juga
                // mewajibkan notifikasi FGS id=1 terlihat; padahal notifikasi
                // itu bisa disembunyikan sistem (POST_NOTIFICATIONS ditolak /
                // channel MIN), sehingga watchdog salah membunuh proses terus-
                // menerus dan layanan tampak mati sendiri saat app ditutup.
                boolean connected = false;
                try {
                    StatusBarNotification[] all = getActiveNotifications();
                    if (all != null) {
                        connected = true;
                        Log.i(TAG, "watch ok, aktif=" + all.length);
                    } else {
                        Log.e(TAG, "watch: getActiveNotifications null");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "watch conn mati: " + e);
                }
                if (connected) {
                    sConnected = true;
                    fails = 0;
                } else {
                    sConnected = false;
                    fails++;
                    Log.e(TAG, "watch gagal ke-" + fails + " (butuh 4 beruntun buat kill)");
                }
                updateCpuWakeLock();
                if (fails >= 4) {
                    Log.e(TAG, "watch conn drop 4x, kill proses biar sistem rebind");
                    Process.killProcess(Process.myPid());
                    return;
                }
                mainHandler.postDelayed(this, 12000);
            }
        }, 12000);
    }

    private void initChannel() {
        // LOW, bukan MIN: channel MIN bisa disembunyikan ROM dan membuat
        // status layanan tidak terlihat. Tidak ada suara karena importance LOW.
        NotificationChannel c = new NotificationChannel(
                CHANNEL, "Pembaca notifikasi", NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
    }

    private void startFg() {
        if (fgAttempts >= 5) {
            Log.e(TAG, "startForeground gagal 5x, menyerah sampai rebind berikutnya");
            return;
        }
        fgAttempts++;
        try {
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setContentTitle("NotifBaca aktif")
                    .setContentText("Notifikasi bakal dibacakan otomatis")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(1, n);
            fgAttempts = 0;
        } catch (Exception e) {
            // Jangan biarkan service mati hanya karena FGS ditolak sistem.
            // Listener tetap bisa jalan sebagai bound service; coba lagi nanti.
            Log.e(TAG, "startForeground gagal (percobaan " + fgAttempts + ")", e);
            if (fgAttempts < 5) mainHandler.postDelayed(this::startFg, 15000);
        }
    }

    private boolean noise(String pkg) {
        // Notifikasi milik NotifBaca sendiri ("NotifBaca aktif") selalu noise
        // selama app hidup, jadi dibuang di sini dan bukan di NOISE yang bisa
        // diubah user dari UI.
        if ("com.home.notifbaca".equals(pkg)) return true;
        if (NOISE.contains(pkg)) return true;
        if (pkg.startsWith("com.transsion.") || pkg.startsWith("android.")) return true;
        return false;
    }

    private boolean btActive() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || t == AudioDeviceInfo.TYPE_HEARING_AID) {
                return true;
            }
            // TWS modern (LE Audio) bisa muncul sebagai BLE headset/speaker,
            // bukan A2DP/SCO. Tanpa ini opsi "hanya saat BT aktif" salah
            // menganggap Bluetooth mati.
            if (Build.VERSION.SDK_INT >= 31
                    && (t == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= 33
                    && t == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                return true;
            }
        }
        return false;
    }

    // Key stabil anti-koma: teks notif bisa mengandung koma, jadi simpan
    // hash hex, bukan teks mentah yang di-split koma.
    private static String stableKey(String raw) {
        return Integer.toHexString(raw == null ? 0 : raw.hashCode());
    }

    private void loadSpoken() {
        String s = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SPOKEN, "");
        if (s == null) return;
        for (String k : s.split(",")) {
            String kk = k.trim();
            if (!kk.isEmpty()) recent.add(kk);
        }
    }

    private void markSpoken(String key) {
        String s = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SPOKEN, "");
        java.util.List<String> spoken = new java.util.ArrayList<>();
        if (s != null) {
            for (String k : s.split(",")) {
                if (!k.trim().isEmpty()) spoken.add(k.trim());
            }
        }
        spoken.add(key);
        while (spoken.size() > 60) spoken.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String k : spoken) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k);
        }
        getSharedPreferences(PREF, MODE_PRIVATE)
                .edit().putString(KEY_SPOKEN, sb.toString()).apply();
    }

    @Override
    public void onListenerConnected() {
        Log.i(TAG, "listener connected");
        sConnected = true;
        writeHeartbeat();
        updateCpuWakeLock();
        fgAttempts = 0;
        startFg();
        RebindJobService.schedule(this);
        loadSpoken();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) {
                Log.i(TAG, "rescan aktif " + all.length);
                // Cuma kejar yang segar: notif basi dilewati di consider(),
                // dan dibatasi biar tidak borongan suara aneh saat buka app.
                for (StatusBarNotification sbn : all) {
                    if (speakQueue.size() >= CATCHUP_MAX_COUNT) {
                        Log.i(TAG, "rescan stop, budget habis");
                        break;
                    }
                    consider(sbn, true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "rescan gagal", e);
        }
    }

    public void onListenerDisconnected() {
        Log.e(TAG, "listener DISCONNECTED");
        sConnected = false;
        updateCpuWakeLock();
        RebindJobService.schedule(this);
        // Self-heal: minta sistem bind ulang. Tanpa ini service diam
        // selamanya sampai user buka app (badge izin tetap hijau).
        try {
            requestRebind(new ComponentName(this, NLS.class));
            Log.i(TAG, "requestRebind dikirim");
        } catch (Exception e) {
            Log.e(TAG, "requestRebind gagal", e);
        }
        // Jaring kedua: coba lagi 5 detik kemudian kalau masih putus.
        try {
            mainHandler.postDelayed(() -> {
                if (destroyed) return;
                try {
                    requestRebind(new ComponentName(NLS.this, NLS.class));
                    Log.i(TAG, "requestRebind ulang dikirim");
                } catch (Exception e) {
                    Log.e(TAG, "requestRebind ulang gagal", e);
                }
            }, 5000);
        } catch (Exception e) {
            Log.e(TAG, "jadwal rebind gagal", e);
        }
    }

    public void onNullBinding(android.content.Intent intent) {
        Log.e(TAG, "null binding intent=" + intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (destroyed || sbn == null) return;
        try {
            consider(sbn, false);
        } catch (Exception e) {
            // Satu notifikasi aneh tidak boleh menjatuhkan seluruh listener.
            Log.e(TAG, "proses notifikasi gagal", e);
        }
    }

    private void consider(StatusBarNotification sbn, boolean catchUp) {
        if (destroyed) return;
        String pkg = sbn.getPackageName();
        // Log ditulis setelah filter, bukan sebelum. Dulu baris "GOT" ditulis
        // lebih dulu sehingga notif yang ongoing/noise tetap terlihat seolah
        // dibaca, padahal dilewati.
        if (sbn.isOngoing()) {
            Log.i(TAG, "SKIP ongoing " + pkg);
            return;
        }
        if (noise(pkg)) {
            Log.i(TAG, "SKIP noise " + pkg);
            return;
        }
        // Saat catchUp (rebind / buka app), lewati notif basi biar tidak
        // borongan suara aneh. Notif live (catchUp=false) selalu dibaca.
        if (catchUp) {
            long age = System.currentTimeMillis() - sbn.getPostTime();
            if (age > CATCHUP_MAX_AGE) {
                Log.i(TAG, "SKIP basi " + pkg + " umur=" + (age / 1000) + "s");
                return;
            }
        }
        Log.i(TAG, (catchUp ? "KETINGGALAN " : "GOT ") + pkg);

        String title = "";
        String text = "";
        Bundle ex = sbn.getNotification().extras;
        if (ex != null) {
            CharSequence ct = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence tx = ex.getCharSequence(Notification.EXTRA_TEXT);
            if (ct != null) title = ct.toString();
            if (tx != null) text = tx.toString();
        }
        if (title.isEmpty() && text.isEmpty()) return;

        // Cek blokir sebelum menandai "sudah dibaca". Dulu notifikasi dari
        // package yang diblokir tetap dimasukkan ke recent/markSpoken.
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String[] blocked = sp.getString(KEY_BLOCK, "").split(",");
        for (String b : blocked) {
            if (b.trim().equalsIgnoreCase(pkg)) {
                Log.i(TAG, "SKIP blokir " + pkg);
                return;
            }
        }

        String key = stableKey(pkg + "|" + title + "|" + text);
        if (recent.contains(key)) return;
        if (recent.size() > 300) recent.clear();
        recent.add(key);
        markSpoken(key);

        String app = pkg;
        String[] parts = pkg.split("\\.");
        if (parts.length > 0) app = parts[parts.length - 1];

        synchronized (summaryLog) {
            summaryLog.add(app + ": " + title + ". " + text);
            while (summaryLog.size() > 60) summaryLog.remove(0);
        }

        if ("1".equals(sp.getString("btonly", "")) && !btActive()) {
            Log.i(TAG, "BT mati, skip " + pkg);
            return;
        }

        String utter = "Notifikasi dari " + app + ". " + title + ". " + text;

        speakSimple(utter);
    }

    private void speakSimple(String text) {
        if (destroyed || text == null || text.isEmpty()) return;
        if ("1".equals(getSharedPreferences(PREF, MODE_PRIVATE).getString("muted", ""))) {
            // Jangan pernah menulis isi notifikasi ke logcat.
            Log.i(TAG, "muted, skip len=" + text.length());
            return;
        }
        Log.i(TAG, "SPEAKING len=" + text.length());
        enqueue(text);
        if (ttsReady) drainQueue();
    }

    private void enqueue(String text) {
        speakQueue.add(text);
        while (speakQueue.size() > MAX_SPEAK_QUEUE) speakQueue.poll();
    }

    private void drainQueue() {
        if (destroyed || !ttsReady || ttsSpeaking || tts == null || ttsHandler == null) return;
        final String text = speakQueue.poll();
        if (text == null) return;
        ttsSpeaking = true;
        // Tahan CPU sampai antrean selesai, bukan cuma 10 detik: notifikasi
        // panjang bisa melewati 10 detik dan device sempat tidur di tengah baca.
        if (wl != null && !wl.isHeld()) {
            try {
                wl.acquire(10 * 60 * 1000L);
            } catch (Exception e) {
                Log.w(TAG, "wakelock gagal", e);
            }
        }
        try {
            ttsHandler.post(() -> {
                if (destroyed) return;
                if (am != null && afr != null) {
                    int f = am.requestAudioFocus(afr);
                    Log.i(TAG, "request focus = " + f);
                    // Jaring pengaman: kalau onDone tidak pernah datang, focus
                    // tetap dilepas setelah 60 detik supaya musik tidak diam
                    // terus. Timeout ini diperbarui tiap antrean baru.
                    mainHandler.removeCallbacks(focusFallback);
                    mainHandler.postDelayed(focusFallback, 60000);
                }
                try {
                    int r = tts.speak(text, TextToSpeech.QUEUE_ADD, null,
                            "n" + System.currentTimeMillis());
                    if (r == TextToSpeech.ERROR) {
                        Log.e(TAG, "tts.speak return ERROR");
                        speakDone();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "tts gagal", e);
                    speakDone();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "tts gagal", e);
            ttsSpeaking = false;
            releaseWakeLock();
        }
    }

    // Fokus audio sengaja ditahan sampai seluruh antrean selesai dibacakan,
    // bukan dilepas dengan timer tetap. Kalau dilepas terlalu awal, musik
    // kembali ke nada penuh padahal NotifBaca masih bicara.
    private final Runnable focusFallback = () -> {
        if (destroyed) return;
        Log.w(TAG, "onDone tidak datang, lepas focus paksa + reset antrean");
        // Dulu cuma releaseFocus(): kalau onDone/onError tidak pernah datang
        // (network voice mati, engine sibuk saat Doze), ttsSpeaking macet
        // true selamanya dan semua notif berikutnya diam sampai restart.
        ttsSpeaking = false;
        releaseFocus();
        releaseWakeLock();
        drainQueue();
    };

    private void releaseFocus() {
        if (mainHandler != null) mainHandler.removeCallbacks(focusFallback);
        if (am == null || afr == null) return;
        try {
            am.abandonAudioFocusRequest(afr);
        } catch (Exception e) {
            Log.w(TAG, "abandon focus gagal", e);
        }
    }

    private void releaseWakeLock() {
        if (wl == null || !wl.isHeld()) return;
        try {
            wl.release();
        } catch (Exception e) {
            Log.w(TAG, "release wakelock gagal", e);
        }
    }

    private void speakDone() {
        if (destroyed || mainHandler == null) return;
        mainHandler.post(() -> {
            if (destroyed) return;
            ttsSpeaking = false;
            // Kalau antrean sudah kosong, musik boleh lanjut. Kalau masih ada,
            // focus ditahan supaya tidak ada jeda di antara dua notifikasi.
            if (speakQueue.isEmpty()) {
                releaseFocus();
                releaseWakeLock();
            }
            drainQueue();
        });
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        sAlive = false;
        sConnected = false;
        destroyed = true;
        ttsReady = false;
        ttsInitGeneration++;
        // Hentikan heartbeat/watch/tick. Tanpa ini last_hb terus diperbarui
        // walau service sudah mati, dan UI salah bilang "Sistem aktif".
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                Log.w(TAG, "unregister receiver layar gagal", e);
            }
            screenReceiver = null;
        }
        releaseFocus();
        releaseWakeLock();
        if (cpuWl != null && cpuWl.isHeld()) {
            try {
                cpuWl.release();
            } catch (Exception e) {
                Log.w(TAG, "release cpu_wl gagal", e);
            }
        }
        cpuWl = null;
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception e) {
            Log.w(TAG, "stopForeground gagal", e);
        }
        if (ttsThread != null) ttsThread.quitSafely();
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "shutdown TTS gagal", e);
            }
            tts = null;
        }
        super.onDestroy();
    }
}