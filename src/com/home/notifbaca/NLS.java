package com.home.notifbaca;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NLS extends NotificationListenerService {
    private static final String TAG = "NotifBaca";
    private static final String CHANNEL = "run";
    private static final String PREF = "cfg";
    private static final String KEY_BLOCK = "block";

    private TextToSpeech tts;
    private PowerManager.WakeLock wl;
    private HandlerThread ttsThread;
    private Handler ttsHandler;
    private Handler mainHandler;
    private AudioManager am;
    private AudioAttributes ttsAudio;
    private AudioFocusRequest afr;
    private int hbCount;
    private final Set<String> recent = new HashSet<>();

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
        initChannel();
        startFg();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotifBaca:speak");
        wl.setReferenceCounted(false);
        ttsThread = new HandlerThread("tts");
        ttsThread.start();
        ttsHandler = new Handler(ttsThread.getLooper());
        mainHandler = new Handler(android.os.Looper.getMainLooper());
        startHeartbeat();
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        ttsAudio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ttsAudio)
                .setOnAudioFocusChangeListener(f -> { })
                .build();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault());
                tts.setAudioAttributes(ttsAudio);
            }
        });
    }

    private void startHeartbeat() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "hb " + (++hbCount));
                mainHandler.postDelayed(this, 15000);
            }
        }, 15000);
        mainHandler.postDelayed(new Runnable() {
            private int fails;

            @Override
            public void run() {
                Log.i(TAG, "watch check");
                StatusBarNotification[] all = null;
                try {
                    all = getActiveNotifications();
                } catch (Exception e) {
                    Log.e(TAG, "watch conn mati: " + e);
                }
                boolean alive = false;
                if (all != null) {
                    Log.i(TAG, "watch aktif=" + all.length);
                    for (StatusBarNotification s : all) {
                        if ("com.home.notifbaca".equals(s.getPackageName()) && s.getId() == 1) {
                            alive = true;
                            break;
                        }
                    }
                }
                if (alive) {
                    fails = 0;
                } else {
                    fails++;
                    Log.e(TAG, "watch gagal ke-" + fails + " (butuh 4 beruntun buat kill)");
                }
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
        NotificationChannel c = new NotificationChannel(
                CHANNEL, "Pembaca notifikasi", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
    }

    private void startFg() {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("NotifBaca aktif")
                .setContentText("Notifikasi bakal dibacakan otomatis")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private boolean noise(String pkg) {
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
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onListenerConnected() {
        Log.i(TAG, "listener connected");
        startFg();
    }

    public void onListenerDisconnected() {
        Log.e(TAG, "listener DISCONNECTED");
    }

    public void onNullBinding(android.content.Intent intent) {
        Log.e(TAG, "null binding intent=" + intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "GOT " + sbn.getPackageName() + " ongoing=" + sbn.isOngoing());
        if (sbn.isOngoing()) return;
        String pkg = sbn.getPackageName();
        if (noise(pkg)) return;

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

        String key = pkg + "|" + title + "|" + text;
        if (recent.contains(key)) return;
        if (recent.size() > 300) recent.clear();
        recent.add(key);

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String[] blocked = sp.getString(KEY_BLOCK, "").split(",");
        for (String b : blocked) {
            if (b.trim().equalsIgnoreCase(pkg)) return;
        }

        String app = pkg;
        String[] parts = pkg.split("\\.");
        if (parts.length > 0) app = parts[parts.length - 1];

        SharedPreferences sp2 = getSharedPreferences(PREF, MODE_PRIVATE);
        if ("1".equals(sp2.getString("btonly", "")) && !btActive()) {
            Log.i(TAG, "BT mati, skip " + pkg);
            return;
        }

        String utter = "Notifikasi dari " + app + ". " + title + ". " + text;
        Log.i(TAG, "SPEAKING " + pkg + ": " + utter);

        if (wl != null && !wl.isHeld()) wl.acquire(8000);
        String id = "n" + System.currentTimeMillis();
        try {
            ttsHandler.post(() -> {
                int f = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                if (am != null && afr != null) {
                    f = am.requestAudioFocus(afr);
                }
                tts.speak(utter, TextToSpeech.QUEUE_FLUSH, null, id);
                if (f == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && am != null) {
                    mainHandler.postDelayed(() -> {
                        if (am != null) am.abandonAudioFocusRequest(afr);
                    }, 6000);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "tts gagal", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        if (ttsThread != null) ttsThread.quitSafely();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}