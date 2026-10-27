# AGENTS.md

NotifBaca — aplikasi Android (package `com.home.notifbaca`, minSdk 26 / targetSdk 33) yang membaca notifikasi keras via TextToSpeech. UI, log, dan commit message semua berbahasa Indonesia.

## Build: tanpa Gradle, manual di Termux

Cara termudah: `ANDROID_JAR=~/androidjar/android-13/android.jar ./build.sh`
(atau set `NB_KS_PASS` / biarkan `apksigner` menanyakan password).

Repo ini TIDAK punya Gradle/project SDK Android. Urutan manualnya (tools = paket Termux `aapt2`, `d8`, `apksigner`; `android.jar` dari `~/androidjar/android-13/android.jar`):

```
aapt2 compile --dir res -o obj/res.zip
aapt2 link -o base.apk -I ~/androidjar/android-13/android.jar --manifest AndroidManifest.xml --java obj obj/res.zip
javac -source 8 -target 8 -classpath ~/androidjar/android-13/android.jar -d obj obj/com/home/notifbaca/R.java src/com/home/notifbaca/*.java
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
- Fokus audio pakai `AUDIOFOCUS_GAIN_TRANSIENT`, **jangan** `GAIN_TRANSIENT_MAY_DUCK`. Duck cuma permintaan ("boleh nge-lower") dan hampir selalu diabaikan app musik (YouTube dsb) sehingga suara TTS beradu dengan musik. `GAIN_TRANSIENT` bikin app musik benar-benar berhenti/lower dan otomatis lanjut lagi setelah focus dilepas. Focus dipegang sampai **seluruh antrean** selesai dibaca (dilepas di `speakDone()` kalau `speakQueue` kosong), bukan timer tetap — dulu dilepas paksa setelah 6 detik sehingga musik kembali nada penuh padahal notifikasi masih dibaca. `focusFallback` (60 detik) hanya jaring pengaman kalau `onDone` tidak pernah datang.
- TTS main di stream media (`USAGE_MEDIA`, `AudioManager.STREAM_MUSIC`) supaya ikut volume perangkat apa adanya. App **tidak** lagi menaikkan/menurunkan volume sendiri — slider volume + pref `cfg`/`volume` + `applyVolume()`/`restoreVolume()` sudah dihapus. Kalau mau volume khusus NotifBaca, harus lewat mixer/equalizer atau Aturan Volume Android 13+, bukan `setStreamVolume`.
- Log `AS.AudioService: AudioHardening background playback would be muted for com.google.android.tts, level: full` itu **peringatan saja** (bg-audio hardening Android 17, `enforced=false`), bukan mute sungguhan — TTS tetap kedengaran. Jangan panik dan jangan `cmd audio set-hardening 0`.
- Watchdog di `NLS` sengaja membunuh proses sendiri setelah 4 kali `getActiveNotifications()` **null/throw** berturut-turut — ini self-heal biar sistem rebind listener. Jangan memakai keberadaan notifikasi FGS id=1 sebagai penanda hidup: notifikasi itu bisa disembunyikan sistem (POST_NOTIFICATIONS ditolak / channel MIN) dan dulu menyebabkan kill-loop. Watchdog punya **masa tenggang 120 detik** (`WATCH_GRACE_MS`) setelah `onCreate` selama listener belum pernah terhubung, dan mengirim `requestRebind` pada kegagalan ke-2 sebelum kill — tanpa masa tenggang, proses bunuh diri 48 detik setelah dibuat saat binding sistem telat (boot/Doze/freezer Android 14–16), sebelum sempat terhubung sama sekali. Jangan hapus masa tenggang ini. Probe `getActiveNotifications()` **wajib async** di thread `nls-watch` (`launchWatchProbe()` → hasil dinilai di `onWatchProbeResult()` di main thread): memanggilnya di main thread bisa menggantung dan memblokir semua callback, tapi membuang probe dan hanya percaya `sConnected` membuat watchdog buta terhadap binder yang mati diam-diam tanpa `onListenerDisconnected()`. Probe yang belum balik dalam 12 detik dihitung gagal.
- `RebindJobService` adalah jaring pengaman di luar proses: JobScheduler periodik 15 menit (flex 5 menit), persisted, minta `requestRebind` kalau `NLS.isAlive()/isConnected()` false atau `last_hb` lebih dari 3 menit. Dijadwalkan dari `NLS`, `MainActivity`, `BootReceiver` (BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED). `schedule()` **wajib** cek `getPendingJob(JOB_ID)` dulu: `JobScheduler.schedule()` dengan ID sama mengganti job dan me-reset timer periode, jadi memanggilnya berulang membuat job tidak pernah jalan. Di Android 16 job juga kena kuota runtime per standby bucket — `onStopJob` mencatat `getStopReason()` buat diagnosis.
- **Mode andal**: `NLS` memegang `PARTIAL_WAKE_LOCK` (`NotifBaca:cpu`) selama layar mati + listener tersambung + pref `cfg`/`holdcpu` = `1` (default aktif). Tanpa wake lock, proses tetap hidup sebagai FGS tapi handler/callback bisa tertidur saat layar mati (gejala "layanan mati kalau app tidak dibuka"). Release saat layar nyala, listener putus, atau `onDestroy`; user bisa mematikannya di section KEANDALAN. Wake lock **selalu** di-`acquire(CPU_WL_TIMEOUT_MS)` (10 menit, diperbarui heartbeat) — jangan `acquire()` tanpa timeout: lock yang bocor (mis. broadcast `SCREEN_ON` terantre saat app cached) menguras baterai semalaman dan membuat Android menaruh app di standby bucket *restricted*, yang justru membuat proses makin sering dibunuh.
- Foreground service id=1 dan `CHANNEL="run"` jangan diubah tanpa menyesuaikan watchdog/kanal. `startForeground()` **hanya** dipanggil dari `onListenerConnected()` (lewat `startFg()`), **jangan** dari `onCreate()`: di `onCreate` binding sistem belum ada sehingga promosi bisa ditolak (`ForegroundServiceStartNotAllowedException`, Android 12+), dan `mainHandler` wajib dibuat sebelum kode apa pun yang bisa menyentuhnya di blok catch (dulu NPE di catch `startFg` membuat `onCreate` crash berulang di Android 16). Tipe FGS `mediaPlayback` dideklarasikan di manifest + izin `FOREGROUND_SERVICE_MEDIA_PLAYBACK` dan diteruskan eksplisit ke `startForeground()` — wajib sejak targetSdk 34, dan tidak kena batas 6 jam seperti `dataSync`. Kalau nanti targetSdk/`android.jar` naik ke 34+, pertimbangkan `specialUse`.
- **Jangan pernah menulis isi notifikasi ke logcat.** Log hanya package, panjang teks, dan status. Ini aplikasi privasi.
- **Widget pelindung wajib di ROM Infinix/Transsion (XOS)**: layanan `com.transsion.usf` (`Usf_Hiber`) membekukan **seluruh UID** aplikasi ±5–10 detik setelah layar utama ditekan, walaupun `NLS` sudah foreground service dan battery-optimization sudah dikecualikan. Cek dengan `cat /sys/fs/cgroup/apps/uid_<uid>/cgroup.freeze` (1 = beku). USF mengecualikan aplikasi yang punya widget di home screen (`importantReason=widget`), makanya ada `Widget.java` + receiver `APPWIDGET_UPDATE` + tombol "Pasang widget pelindung" (`AppWidgetManager.requestPinAppWidget`). Jangan hapus widget/receiver ini; tanpa widget, notifikasi berhenti dibaca saat app ditinggalkan.
- **TTS tidak boleh diinisialisasi di main thread**: `new TextToSpeech()`, `configureTts()` (`getVoices`/`setVoice`), dan `MainActivity.refreshVoices()` adalah binder ke engine TTS. Kalau engine lambat/mati, panggilan menggantung dan membekukan seluruh proses (heartbeat, watchdog, callback notifikasi ikut mati). Semua dijalankan di `ttsHandler`/thread `ui-voices`, bukan main thread.
- `allowBackup="false"` dan Auto Backup dimatikan; jangan diubah tanpa alasan kuat.
- `onDestroy` wajib menghentikan heartbeat/tick (`removeCallbacksAndMessages`) supaya UI tidak salah bilang "Sistem aktif" setelah layanan mati.
- Logcat: `adb logcat -s NotifBaca` (TAG `NotifBaca`). Heartbeat tiap 15s, watch-check tiap 12s.
- Di device: butuh grant akses notifikasi (NLS) + izin POST_NOTIFICATIONS (SDK 33+); keduanya lewat UI MainActivity.