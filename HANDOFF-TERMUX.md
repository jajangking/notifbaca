# Handoff: Uji Perbaikan "Layanan Sering Mati di Android 16" di Termux

Dokumen ini buat **AI agent yang jalan di Termux pada perangkat pengguna**.
Tugasmu: build APK dari branch perbaikan, install menimpa versi lama,
uji di perangkat, lalu laporkan hasilnya ke pengguna.

> **Wajib baca `AGENTS.md` lebih dulu.** Dokumen ini hanya pelengkap
> langkah eksekusi; aturan arsitektur, build, dan keystore di `AGENTS.md`
> tetap berlaku penuh dan tidak boleh dilanggar.

## Konteks singkat

- Bug: di Android 16 layanan NotifBaca sering mati sendiri sehingga
  notifikasi masuk tidak dibacakan.
- Akar masalah yang ditemukan (sudah diperbaiki di kode):
  1. `NLS.onCreate()` memanggil `startFg()` sebelum `mainHandler` dibuat —
     kalau `startForeground()` ditolak sistem (umum di Android 12–16 saat
     listener di-bind dari background), blok `catch` melempar NPE,
     `onCreate` crash, proses mati, sistem bind ulang, crash lagi.
  2. `startForeground()` tanpa tipe FGS di manifest (wajib sejak
     targetSdk 34). Sekarang `mediaPlayback` + izin pasangannya.
  3. Watchdog membunuh proses 48 detik setelah `onCreate`, sebelum
     binding sistem sempat terbentuk (boot/Doze/freezer). Sekarang ada
     masa tenggang 120 detik + `requestRebind()` sebelum kill.
  4. Wake lock CPU `acquire()` tanpa timeout → app kena standby bucket
     *restricted*. Sekarang timeout 10 menit, diperbarui heartbeat.
  5. `RebindJobService.schedule()` selalu me-reset timer job periodik
     sehingga job pengaman tidak pernah jalan. Sekarang cek
     `getPendingJob()` dulu + flex 5 menit + log `stopReason`.
  6. Init TTS yang sudah menyerah (6× gagal) tidak pernah dicoba lagi.
     Sekarang dicoba lagi saat rebind / notif masuk (maks 1×/menit).
  7. **Freezer ROM Transsion/Infinix**: `com.transsion.usf` (`Usf_Hiber`)
     membekukan **seluruh UID** aplikasi ±5–10 detik setelah Home ditekan,
     walaupun `NLS` sudah foreground service, battery-optimization sudah
     dikecualikan, dan listener tetap ter-bind. Cek
     `/sys/fs/cgroup/apps/uid_<uid>/cgroup.freeze` (1 = beku). Solusinya:
     widget pelindung di home screen; USF memberi `importantReason=widget`
     dan tidak membekukan UID. Widget **wajib tetap ada**.
  8. **TTS binder di main thread**: `new TextToSpeech()`,
     `configureTts()`/`getVoices()`, dan `getActiveNotifications()` bisa
     menggantung saat engine/system sibuk. Sekarang inisialisasi TTS jalan
     di thread `ttsHandler`, daftar suara di thread `ui-voices`, dan rescan
     notifikasi di thread `nls-rescan`; main thread tetap bisa heartbeat
     dan menerima callback notifikasi.
  9. **Probe watchdog async** (v1.0.4): perbaikan #8 sempat membuang probe
     binder watchdog sama sekali (`connected = sConnected`), sehingga
     watchdog buta terhadap binder yang mati diam-diam tanpa
     `onListenerDisconnected()`. Sekarang probe `getActiveNotifications()`
     dikembalikan tapi jalan di thread `nls-watch` (`launchWatchProbe()`),
     hasil dinilai di main thread (`onWatchProbeResult()`). Probe yang
     belum balik dalam 12 detik dihitung gagal. Jangan pindahkan probe ke
     main thread, jangan buang probe lagi.
- Branch: `arena/e481637c-notifbaca`
- Commit perbaikan awal: `e9ec3e7` ("Perbaiki layanan sering mati di Android 16")
- Hasil build yang benar **saat ini**: `versionCode 5`, `versionName 1.0.4`
- Status verifikasi: v1.0.3 sudah diuji di **Infinix X6855, Android 16
  (SDK 36)** dengan layar tetap menyala. Hasil: di background 45–90 detik
  heartbeat terus jalan, `cgroup.freeze=0`, notifikasi uji masuk dan
  `SPEAKING`. Widget pelindung sudah dipasang di layar utama perangkat uji.
  v1.0.4 (probe watchdog async) **belum diuji di perangkat** — itu tugas
  verifikasi putaran ini, lihat bagian "Tugas verifikasi saat ini".
  Skenario layar mati, reboot, diusir dari recents, dan soak baterai
  1–2 jam **dilewati dulu** atas instruksi pengguna.

## Tugas verifikasi saat ini (v1.0.4) — kerjakan ini

Pengguna meminta verifikasi langsung untuk perubahan v1.0.4 saja.
Ruang lingkup putaran ini:

1. Jalankan **Langkah 0–5** seperti biasa (ambil branch terbaru, build,
   pastikan `versionCode='5' versionName='1.0.4'`, install menimpa,
   widget pelindung tetap terpasang, status "Sistem aktif").
2. Jalankan **hanya skenario A** (notif dasar, layar nyala) dari
   Langkah 6, plus pantau log watchdog baru selama ±5 menit dengan app
   di background (layar tetap menyala, jangan buka app) — lihat
   Langkah 7. Yang wajib terlihat berulang: `watch check` diikuti
   `watch probe ok, aktif=...`, heartbeat `hb` tetap jalan, dan
   `SPEAKING` untuk notif uji. Yang **tidak boleh** muncul:
   `watch: probe sebelumnya belum selesai`, `watch gagal ke-...`,
   `watch conn drop 4x`, atau `FATAL EXCEPTION`.
3. **Skenario B, C, D, E, dan F DILEWATI pada putaran ini.** Jangan
   jalankan, dan jangan simpulkan apa pun soal perilaku layar mati /
   reboot / recents / soak / freezer dari putaran ini.
4. Laporkan dengan format di bagian "Format laporan ke pengguna",
   dengan penyesuaian: skenario cukup A + hasil pantau probe watchdog.
   Tambahkan satu baris eksplisit: "Skenario B–E+F dilewati sesuai
   instruksi pengguna."
5. Kalau probe watchdog bermasalah (muncul pola terlarang di poin 2),
   **berhenti dan laporkan** dengan 10–20 baris log terkait. Jangan
   memperbaiki sendiri dengan membuang probe atau memindahkannya ke
   main thread.

## Aturan keras (jangan dilanggar)

- `keystore/nb.keystore` **rahasia**: jangan commit, jangan buat ulang,
  jangan tampilkan/copy ke mana pun. Keystore baru = pengguna harus
  uninstall dulu. Kalau file tidak ada, **berhenti dan tanya pengguna**.
- Password keystore: jangan tulis ke file, jangan echo ke log/laporan.
  Minta pengguna mengetiknya sendiri di sesi yang sama (lihat Langkah 2).
- **Jangan pernah menulis isi notifikasi ke logcat/laporan.** Cukup
  package, panjang teks, dan status — ikuti pola log yang sudah ada.
- Jangan mengubah invariant di `AGENTS.md` (fokus audio `GAIN_TRANSIENT`,
  FGS id=1 & channel `run`, masa tenggang watchdog, wake lock bertimeout,
  `allowBackup="false"`, dsb).
- **Widget pelindung wajib ada di home screen** di Infinix/Transsion.
  Jangan menghapus `Widget.java`, receiver `APPWIDGET_UPDATE`, atau widget
  yang sudah dipasang. Tanpa widget, `Usf_Hiber` membekukan UID dan
  notifikasi berhenti dibaca.
- Jangan naikkan `targetSdkVersion` dalam tugas ini.
- Semua artefak build (`.apk`, `obj/`, `dexout/`, `base.apk`,
  `unsigned.apk`, `*.idsig`) adalah gitignored — jangan commit.
- Kalau ada langkah gagal dan tidak jelas sebabnya: berhenti, kumpulkan
  log secukupnya, laporkan ke pengguna. Jangan menebak-nebak perbaikan
  besar sendiri.

## Langkah 0 — Ambil kode

```bash
cd ~/notifbaca        # sesuaikan dengan lokasi repo di perangkat
git fetch origin
git checkout arena/e481637c-notifbaca
git pull origin arena/e481637c-notifbaca
git log --oneline -3  # commit perbaikan terbaru, minimal setelah e9ec3e7
```

Kalau repo belum ada: `git clone https://github.com/jajangking/notifbaca.git`
lalu checkout branch di atas.

## Langkah 1 — Cek prasyarat

```bash
command -v aapt2 d8 apksigner javac python3
ls -lh ~/androidjar/android-13/android.jar
ls keystore/nb.keystore   # cukup cek ada; JANGAN baca/isinya ditampilkan
```

Kalau `android.jar` ada di lokasi lain, sesuaikan `ANDROID_JAR=` di
Langkah 2. Kalau keystore tidak ada → berhenti, tanya pengguna.

## Langkah 2 — Build

Password keystore diisi pengguna sendiri (tidak tampil di layar):

```bash
read -s -p "Password keystore: " NB_KS_PASS; echo; export NB_KS_PASS
ANDROID_JAR=~/androidjar/android-13/android.jar ./build.sh
```

Kalau pengguna lebih suka interaktif, jalankan `./build.sh` tanpa
`NB_KS_PASS` dan biarkan `apksigner` yang menanyakan.

Build sukses ditandai baris `Selesai: notifbaca.apk` dan output
`aapt2 dump badging` di akhir `build.sh`.

## Langkah 3 — Verifikasi APK sebelum install

```bash
apksigner verify --print-certs notifbaca.apk        # sertifikat = keystore lama
aapt2 dump badging notifbaca.apk | grep -E "^package:"   # versionCode='5' versionName='1.0.4'
aapt2 dump xmltree notifbaca.apk --file AndroidManifest.xml | grep -i foreground
```

Yang harus terlihat: `foregroundServiceType` bernilai mediaPlayback dan
izin `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Kalau `versionCode` masih 4
atau lebih lama, berarti branch/kode yang ter-build salah — berhenti
dan cek Langkah 0.

## Langkah 4 — Install menimpa versi lama

Pilih yang tersedia di perangkat (install menimpa; **jangan uninstall
dulu** supaya izin akses notifikasi & pengaturan tidak hilang):

```bash
# Opsi A: buka installer dari Termux
termux-open notifbaca.apk

# Opsi B: salin ke Download lalu buka dari file manager
cp notifbaca.apk /sdcard/Download/NotifBaca-1.0.4.apk

# Opsi C: kalau adb (wireless debugging/Shizuku/pairing) tersedia
adb install -r notifbaca.apk
```

Kalau installer menolak dengan alasan tanda tangan berbeda, artinya
keystore yang dipakai salah — berhenti, jangan uninstall, lapor pengguna.

## Langkah 5 — Cek izin setelah install

Buka aplikasi NotifBaca, pastikan:

1. Status menuju **"Sistem aktif"** (titik hijau), bukan "Menunggu izin"
   atau "Izin ok, layanan belum jalan".
2. Akses notifikasi masih aktif (tombol "Buka akses notifikasi").
3. Tekan **"Izinkan latar belakang (baterai)"** → sudah dikecualikan
   dari optimasi baterai. Ini juga syarat resmi agar FGS boleh start
   dari background.
4. Di Infinix/Transsion: **Autostart** untuk NotifBaca aktif
   (lewat "Info aplikasi / autostart").
5. Checkbox KEANDALAN "Tahan proses saat layar mati" tetap aktif,
   dan "Jeda sementara (dibisukan)" **tidak** aktif.
6. Tekan **"Pasang widget pelindung"** di bagian KEANDALAN, lalu
   **"Tambahkan ke Layar Utama"**. Pastikan status berubah menjadi
   **"Widget pelindung: terpasang"**. Ini wajib di Infinix/Transsion;
   tanpa widget, `Usf_Hiber` membekukan seluruh UID saat app ditinggalkan.

## Langkah 6 — Skenario pengujian

Sebelum mulai, pastikan widget pelindung sudah terpasang di layar utama
dan status di app menunjukkan **"Widget pelindung: terpasang"**. Di
Infinix/Transsion, cek juga freezer setelah app ditinggalkan:

```bash
# ganti 10562 dengan UID dari: adb shell dumpsys package com.home.notifbaca | grep userId=
adb shell cat /sys/fs/cgroup/apps/uid_10562/cgroup.freeze   # harus 0
```

Jalankan berurutan, catat hasil tiap skenario (lulus/gagal + log terkait):

- **A. Notif dasar (layar nyala):** kirim pesan uji (mis. WhatsApp ke
  nomor sendiri dari perangkat lain, atau notif tes) → notif dibacakan.
- **B. Layar mati:** kunci layar, tunggu 5–10 menit tanpa membuka app,
  kirim notif uji → tetap dibacakan, tidak menunggu app dibuka.
- **C. Diusir dari recents:** swipe NotifBaca dari recents, kunci layar,
  kirim notif uji → tetap dibacakan (`stopWithTask=false`).
- **D. Reboot:** restart HP, **jangan buka app sama sekali**, tunggu
  ±3 menit setelah unlock pertama, kirim notif uji → dibacakan.
  Ini skenario yang dulu paling sering kena crash-loop & watchdog.
- **E. Tahan lama:** biarkan 1–2 jam (idealnya semalaman) tanpa membuka
  app, kirim notif uji berkala → tetap dibacakan; cek pemakaian baterai
  NotifBaca di Pengaturan tidak melonjak tidak wajar.
- **F. Freezer ROM (khusus Infinix/Transsion):** pastikan widget masih
  ada, tekan Home, tunggu 60 detik tanpa membuka app, lalu cek
  `cgroup.freeze` = 0 dan log `hb`/`watch ok` masih muncul. Kalau
  `cgroup.freeze` = 1 atau muncul `Usf_Hiber/stateManager: freeze uid`,
  widget hilang/tidak terpasang — pasang ulang sebelum menyimpulkan
  layanan mati.

## Langkah 7 — Pantau logcat

Di Termux, `logcat` aplikasi biasa hanya melihat log UID sendiri. Kalau
perintah di bawah kosong, pakai salah satu: adb wireless debugging dari
perangkat lain/PC, atau beri izin sekali via adb
(`adb shell pm grant com.termux android.permission.READ_LOGS`), atau
Shizuku. Jangan mengutak-atik cara lain yang butuh root.

```bash
logcat -c
logcat -s NotifBaca
# Saring khusus tanda perbaikan bekerja / gagal:
logcat -s NotifBaca | grep -E "watch|masa tenggang|requestRebind|cpu_wl|startForeground gagal|job rebind|TTS belum siap|SPEAKING|listener|hb|GOT|Usf_Hiber"
```

| Pola log | Arti |
|---|---|
| `onCreate` → `listener connected` → `SPEAKING len=...` | Normal, notif dibacakan |
| `watch: belum terhubung, masa tenggang bind (...s)` lalu `listener connected` | Perbaikan #3 bekerja: binding telat tapi tidak dibunuh |
| `watch: requestRebind dikirim` lalu `listener connected` | Self-heal lunak bekerja |
| `startForeground gagal` sesekali lalu layanan tetap jalan | Masih wajar (listener jalan sebagai bound service); catat exception-nya di laporan |
| `cpu_wl on` / `cpu_wl diperbarui` / `cpu_wl off` | Perbaikan #4 bekerja |
| `job rebind dihentikan sistem, stopReason=...` | Job kena kuota Android 16; catat nilainya di laporan |
| `TTS belum siap, init ulang ...` lalu `SPEAKING` | Perbaikan #6 bekerja |
| `GOT <package> len=...` lalu `SPEAKING len=...` | Notifikasi diterima dan dibacakan; catat package + panjang saja |
| `hb` tetap muncul tiap ±15 detik saat background | Proses tidak beku dan main thread sehat |
| `watch check` diikuti `watch probe ok, aktif=...` tiap ±12 detik | Probe watchdog async (v1.0.4) bekerja: binder hidup dan tidak menggantung main thread |
| `watch: probe sebelumnya belum selesai, dianggap gagal` | Binder `getActiveNotifications()` menggantung >12 detik; watchdog menghitungnya gagal — catat dan laporkan, jangan buang probe |
| `watch probe: getActiveNotifications null` / `watch probe conn mati: ...` | Probe mendeteksi binder mati; harusnya diikuti `watch: requestRebind dikirim` lalu `listener connected` |
| `Usf_Hiber/stateManager: freeze uid: <uid> ...` | **Gagal di ROM Transsion** — widget pelindung hilang/tidak terpasang; pasang ulang widget, jangan simpulkan layanan mati |
| `cgroup.freeze` bernilai `1` saat background | UID dibekukan `Usf_Hiber`; cek widget pelindung dulu |
| `FATAL EXCEPTION` / `Unable to create service` berulang | **Gagal** — kumpulkan stack trace, lapor pengguna |
| `watch conn drop 4x` tak lama setelah `onCreate`/boot | **Gagal** — watchdog masih membunuh terlalu dini, lapor pengguna |

## Langkah 8 — Cek sistem (opsional, kalau adb tersedia)

```bash
adb shell dumpsys activity services com.home.notifbaca   # NLS ter-bind & isForeground
adb shell cmd notification list                          # NotifBaca termasuk listener yang diizinkan
adb shell dumpsys deviceidle whitelist | grep notifbaca  # dikecualikan dari optimasi baterai
adb shell am get-standby-bucket com.home.notifbaca       # 10=active ... 45=restricted (hindari 45)
adb shell dumpsys jobscheduler | grep -A5 -B5 notifbaca  # job 4201 terjadwal periodik
adb shell dumpsys appwidget | grep -i notifbaca         # widget pelindung terpasang
adb shell cat /sys/fs/cgroup/apps/uid_$(adb shell dumpsys package com.home.notifbaca | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1)/cgroup.freeze  # 0 = tidak beku
```

## Kriteria selesai

**Putaran ini (v1.0.4):** berhasil kalau APK `versionCode 5` terpasang
menimpa, skenario A lulus, dan selama ±5 menit background (layar nyala)
`watch probe ok` muncul berulang tanpa pola "Gagal" di atas. Skenario
B–E+F dilewati sesuai instruksi pengguna dan tidak memengaruhi
kesimpulan putaran ini.

**Umum (putaran penuh, nanti):** berhasil kalau skenario A–D lulus, E
tidak menunjukkan layanan mati atau boros baterai tidak wajar, F
menunjukkan `cgroup.freeze=0` selama widget terpasang, dan tidak ada
pola log "Gagal" di atas. Kalau F gagal karena widget hilang, itu bukan
bug layanan — pasang ulang widget lalu ulangi F sebelum menyimpulkan.

**Rollback** kalau layanan justru tidak jalan sama sekali: install lagi
APK rilis v1.0.2 dari GitHub Releases (menimpa), atau build ulang dari
`main` (`git checkout main` lalu Langkah 2–4). Jangan uninstall kecuali
pengguna yang memutuskan.

## Format laporan ke pengguna

Akhiri tugas dengan laporan ringkas berisi:

1. Perangkat & versi Android (`getprop ro.product.model`,
   `getprop ro.build.version.release`, `getprop ro.build.version.sdk`).
2. APK terpasang: versionCode/versionName dari Langkah 3 (putaran ini
   harus `5` / `1.0.4`).
3. Hasil skenario yang dijalankan: lulus/gagal + 5–15 baris log
   pendukung (tanpa isi notifikasi!). Putaran ini cukup skenario A +
   hasil pantau probe watchdog (`watch probe ok` dsb.), dan tulis
   eksplisit bahwa skenario B–E+F dilewati sesuai instruksi pengguna.
4. Status widget pelindung (terpasang/tidak) dan nilai `cgroup.freeze`
   saat app di background.
5. Pola log penting yang ditemukan dari tabel Langkah 7 (terutama
   `stopReason`, exception `startForeground`, dan `Usf_Hiber` kalau ada).
6. Hasil Langkah 8 kalau dijalankan (standby bucket, status FGS).
7. Kesimpulan: layanan bertahan di Android 16 atau masih mati, dan
   dalam kondisi apa.

Jangan upload APK hasil build atau membuat rilis GitHub — itu keputusan
pengguna setelah membaca laporanmu.
