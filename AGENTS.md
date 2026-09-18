# AGENTS.md

NotifBaca — aplikasi Android (package `com.home.notifbaca`, minSdk 26 / targetSdk 33) yang membaca notifikasi keras via TextToSpeech. UI, log, dan commit message semua berbahasa Indonesia.

## Build: tanpa Gradle, manual di Termux

Repo ini TIDAK punya Gradle/project SDK Android. Build-mu manual, urutan wajib sebagai berikut (tools = paket Termux `aapt2`, `d8`, `apksigner`; `android.jar` dari `~/androidjar/android-13/android.jar`):

```
aapt2 compile --dir res -o obj/res.zip
aapt2 link -o base.apk -I ~/androidjar/android-13/android.jar --manifest AndroidManifest.xml obj/res.zip
javac -source 8 -target 8 -classpath ~/androidjar/android-13/android.jar -d obj src/com/home/notifbaca/*.java
d8 --release --lib ~/androidjar/android-13/android.jar --output dexout obj/com/home/notifbaca/*.class
python inject.py          # salin base.apk -> unsigned.apk + sisip dexout/classes.dex
apksigner sign --ks keystore/nb.keystore --ks-key-alias <alias> --ks-pass pass:<pass> --out notifbaca.apk unsigned.apk
```

- `inject.py` hanya menyisipkan `dexout/classes.dex` ke salinan `base.apk` → `unsigned.apk`.
- `apksigner sign` dengan `--out` menghasilkan byproduct `<out>.idsig` — itu normal, bisa diabaikan.
- Sumber yang diedit: `AndroidManifest.xml`, `src/com/home/notifbaca/*.java`, `res/` (ikon mipmap). Semua `.apk`, `*.idsig`, `dexout/`, `obj/`, `base.apk` adalah artefak build-gitignored — regenerate, jangan diedit/commit.
- Kalau `obj/` dihapus total, buat dulu `mkdir -p obj` sebelum `aapt2 compile --dir res -o obj/res.zip`.

## Keystore (kritis)

- `keystore/nb.keystore` adalah RAHASIA dan di-gitignore ("jangan pernah commit"). Jangan commit, jangan buat ulang.
- APK ditandatangani v2/v3. Untuk update OTA/install-over wajib pakai keystore SAMA — keystore baru = harus uninstall dulu di device. Simpan perintah sign yang sudah pernah dipakai.

## Arsitektur & gotcha

- `MainActivity` → UI hubung-aktifkan NotificationListenerService, blokir package (SharedPreferences `cfg`/`block`, pisah koma), toggle "hanya baca saat BT aktif" (`cfg`/`btonly`).
- `NLS` → NotificationListenerService. Filter noise statis (`NOISE` + awalan `com.transsion.`/`android.`), dedupe pakai `recent`, baca via TTS — voice dari pref `cfg`/`voice` (dipilih di UI, bisa network voice Google), fallback `Locale.getDefault()`.
- Watchdog di `NLS` sengaja membunuh proses sendiri setelah 4 kali gagal berturut-turut mengecek notif aktif — ini self-heal biar sistem rebind listener. JANGAN "perbaiki" sebagai bug.
- Foreground service id=1 dipakai watchdog sebagai penanda hidup — jangan ganti id/channel (`CHANNEL="run"`) kalau tidak menyesuaikan watchdog.
- Logcat: `adb logcat -s NotifBaca` (TAG `NotifBaca`). Heartbeat tiap 15s, watch-check tiap 12s.
- Di device: butuh grant akses notifikasi (NLS) + izin POST_NOTIFICATIONS (SDK 33+); keduanya lewat UI MainActivity.