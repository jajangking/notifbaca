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
- Branch: `arena/e481637c-notifbaca`
- Commit perbaikan: `e9ec3e7` ("Perbaiki layanan sering mati di Android 16")
- Hasil build yang benar: `versionCode 4`, `versionName 1.0.3`
- Status verifikasi sejauh ini: lolos compile melawan `android.jar`
  API 33 (identik dengan kode lama). **Belum pernah diuji di perangkat**
  — itu tugasmu di sini.

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
git log --oneline -2  # harus terlihat e9ec3e7 di paling atas (atau lebih baru)
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
aapt2 dump badging notifbaca.apk | grep -E "^package:"   # versionCode='4' versionName='1.0.3'
aapt2 dump xmltree notifbaca.apk --file AndroidManifest.xml | grep -i foreground
```

Yang harus terlihat: `foregroundServiceType` bernilai mediaPlayback dan
izin `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Kalau `versionCode` masih 3,
berarti branch/kode yang ter-build salah — berhenti dan cek Langkah 0.

## Langkah 4 — Install menimpa versi lama

Pilih yang tersedia di perangkat (install menimpa; **jangan uninstall
dulu** supaya izin akses notifikasi & pengaturan tidak hilang):

```bash
# Opsi A: buka installer dari Termux
termux-open notifbaca.apk

# Opsi B: salin ke Download lalu buka dari file manager
cp notifbaca.apk /sdcard/Download/NotifBaca-1.0.3.apk

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

## Langkah 6 — Skenario pengujian

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
logcat -s NotifBaca | grep -E "masa tenggang|requestRebind|watch gagal|cpu_wl|startForeground gagal|job rebind|TTS belum siap|SPEAKING|listener"
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
| `FATAL EXCEPTION` / `Unable to create service` berulang | **Gagal** — kumpulkan stack trace, lapor pengguna |
| `watch conn drop 4x` tak lama setelah `onCreate`/boot | **Gagal** — watchdog masih membunuh terlalu dini, lapor pengguna |

## Langkah 8 — Cek sistem (opsional, kalau adb tersedia)

```bash
adb shell dumpsys activity services com.home.notifbaca   # NLS ter-bind & isForeground
adb shell cmd notification list                          # NotifBaca termasuk listener yang diizinkan
adb shell dumpsys deviceidle whitelist | grep notifbaca  # dikecualikan dari optimasi baterai
adb shell am get-standby-bucket com.home.notifbaca       # 10=active ... 45=restricted (hindari 45)
adb shell dumpsys jobscheduler | grep -A5 -B5 notifbaca  # job 4201 terjadwal periodik
```

## Kriteria selesai

**Berhasil** kalau: skenario A–D lulus, E tidak menunjukkan layanan mati
atau boros baterai tidak wajar, dan tidak ada pola log "Gagal" di atas.

**Rollback** kalau layanan justru tidak jalan sama sekali: install lagi
APK rilis v1.0.2 dari GitHub Releases (menimpa), atau build ulang dari
`main` (`git checkout main` lalu Langkah 2–4). Jangan uninstall kecuali
pengguna yang memutuskan.

## Format laporan ke pengguna

Akhiri tugas dengan laporan ringkas berisi:

1. Perangkat & versi Android (`getprop ro.product.model`,
   `getprop ro.build.version.release`, `getprop ro.build.version.sdk`).
2. APK terpasang: versionCode/versionName dari Langkah 3.
3. Hasil tiap skenario A–E: lulus/gagal + 5–15 baris log pendukung
   (tanpa isi notifikasi!).
4. Pola log penting yang ditemukan dari tabel Langkah 7 (terutama
   `stopReason` dan exception `startForeground`, kalau ada).
5. Hasil Langkah 8 kalau dijalankan (standby bucket, status FGS).
6. Kesimpulan: layanan bertahan di Android 16 atau masih mati, dan
   dalam kondisi apa.

Jangan upload APK hasil build atau membuat rilis GitHub — itu keputusan
pengguna setelah membaca laporanmu.
