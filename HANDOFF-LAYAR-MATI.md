# Handoff: Bug Layar Mati — NotifBaca v1.0.4

**Tanggal:** 2026-10-27  
**Perangkat uji:** Infinix X6855, Android 16 (SDK 36)  
**Aplikasi:** `com.home.notifbaca`, UID `10562`  
**Versi terpasang:** v1.0.4 (`versionCode=5`, `versionName=1.0.4`), update menimpa v1.0.3  
**Repo:** `/root/notifbaca`, HEAD `8b3a559`  
**Status:** **Skenario B (layar mati) GAGAL.** Notifikasi tidak dibacakan saat layar mati; suara baru muncul beruntun setelah layar dinyalakan.

> Dokumen ini adalah laporan + handoff. **Jangan menganggap ini instruksi untuk langsung memperbaiki.** Baca juga `AGENTS.md` dan `HANDOFF-TERMUX.md`. Jangan mengubah keystore, versi, atau invariant arsitektur tanpa keputusan pengguna.

---

## 1. Fakta dari pengguna

Pengguna menguji sendiri skenario layar mati:

> "masih bug layar mati gak ada suara, trus di hidupin baru ada suara bermunculan"

Artinya:
- Layar dikunci / mati.
- Notifikasi masuk, tetapi **tidak ada suara**.
- Setelah layar dinyalakan, beberapa suara TTS **muncul beruntun** (tertunda), bukan dibacakan saat notifikasi masuk.

---

## 2. Bukti waktu dan log

Waktu di bawah adalah waktu perangkat (`13:xx`). Pengujian otomatis dimulai sekitar **13:07**, layar dinyalakan pengguna sekitar **13:19:24**.

### 2.1 Log aplikasi menunjukkan proses tertahan

- `13:06:30` → `hb 88`
- `13:20:39` → `hb 96`

Hanya **8 heartbeat** dalam ±14 menit. Normalnya ±56 heartbeat (15 detik sekali). Log aplikasi untuk rentang **13:07–13:19 kosong** dari buffer yang diambil setelah bangun.

Ini konsisten dengan proses aplikasi tertahan/freeze, bukan sekadar TTS lambat.

### 2.2 USF/Transsion membekukan UID 10562, lalu unfreeze saat LCD menyala

```text
10-27 13:19:24.635  I Usf_Hiber/importantScene: widget: packageName=com.home.notifbaca
10-27 13:19:24.635  I Usf_Hiber/appStateMachine: uid:10562, pkg=com.home.notifbaca  transsion F to R because of LCD on ,importantcase is : widget
10-27 13:19:24.635  I Usf_Hiber/appStateMachine: uid:10562 exit FrozenState
10-27 13:19:24.646  I Usf_Hiber/stateManager: unfreeze uid: 10562 com.home.notifbaca pids:[21228]  reason:LcdOn   cpninfo: costTime=11ms ,last freeze 89s ago
```

Poin penting:
- Sebelum LCD menyala, UID 10562 berada di **FrozenState**.
- Unfreeze terjadi karena **LcdOn**.
- `importantcase is : widget` menunjukkan widget pelindung **dikenali** oleh USF, tetapi aplikasi tetap sempat dibekukan.
- `last freeze 89s ago` adalah freeze terakhir yang tercatat; bisa ada siklus freeze sebelumnya yang sudah keluar dari buffer.

### 2.3 TTS baru mulai setelah unfreeze

`NotifBaca:speak` baru diambil setelah LCD menyala:

```text
10-27 13:19:24.796  I Hiber/proxyWakeLock: add uid: 10562 wakelock: ... NotifBaca:speak ...
10-27 13:19:24.800  I Hiber/proxyWakeLock: acquireWakeLockInternal: ... tag="NotifBaca:speak" ... uid=10562 pid=21228
10-27 13:19:34.804  I tts_speak_success: [com.google.android.tts,10562,21228,64,...]
10-27 13:19:42.349  I tts_speak_success: [com.google.android.tts,10562,21228,48,...]
10-27 13:19:49.650  I tts_speak_success: [com.google.android.tts,10562,21228,73,...]
10-27 13:19:58.904  I tts_speak_success: [com.google.android.tts,10562,21228,108,...]
10-27 13:19:58.907  I Hiber/proxyWakeLock: ... REL NotifBaca:speak
```

Empat `tts_speak_success` berurutan setelah layar menyala cocok dengan laporan “suara bermunculan”. TTS tidak mulai saat layar mati.

### 2.4 Wake lock CPU tidak terlihat

Saat diperiksa setelah bangun, daftar wake lock hanya menunjukkan:

```text
10-27 12:38:41.864 - 10562 (com.home.notifbaca) - ACQ *launch* (partial)
10-27 13:19:24.800 - 10562 (com.home.notifbaca) - ACQ NotifBaca:speak (partial)
10-27 13:19:58.907 - 10562 (com.home.notifbaca) - REL NotifBaca:speak
```

Tidak terlihat `NotifBaca:cpu` di daftar tersebut. Ini perlu diverifikasi ulang: apakah `holdcpu` benar-benar aktif, dan apakah `updateCpuWakeLock()` sempat berjalan saat layar mati sebelum proses dibekukan.

### 2.5 Widget pelindung

- UI sebelumnya menunjukkan **“Widget pelindung: terpasang”**.
- `dumpsys appwidget` menunjukkan:
  - widget id `409`
  - host `com.transsion.XOSLauncher`
  - provider `com.home.notifbaca/com.home.notifbaca.Widget`
- Log USF juga mencatat `importantScene: widget: packageName=com.home.notifbaca`.

Jadi widget **ada dan dikenali**, tetapi freeze tetap terjadi saat layar mati.

---

## 3. Catatan penting: uji otomatis skenario B tidak valid

Percobaan otomatis memakai ADB gagal karena **perintah `adb shell` menggantung saat layar mati**:

- Script mulai ±13:07, mengirim `KEYCODE_POWER`, lalu menunggu.
- `adb shell dumpsys power` baru kembali sekitar **13:19:45**, setelah pengguna menyalakan layar.
- Script **tidak sempat mengirim notifikasi uji**; notifikasi yang memicu suara beruntun berasal dari notifikasi nyata yang tertunda.
- Script sudah dihentikan; `scenarioB.log` kosong dan **bukan bukti** untuk skenario B.

**Jangan mengulang pengujian dengan cara ini.** Saat layar mati di perangkat ini, koneksi ADB/`adb shell` tidak bisa diandalkan. Untuk uji berikutnya:
- kirim notifikasi dari perangkat lain, atau
- siapkan pemicu yang berjalan di perangkat **sebelum** layar dimatikan, lalu ambil log setelah layar dinyalakan.

---

## 4. Diagnosis awal

**Penyebab dominan:** USF/Transsion memasukkan UID 10562 ke `FrozenState` saat layar mati, meskipun widget pelindung terpasang. Aplikasi tidak memproses notifikasi/TTS selama freeze. Saat LCD menyala, USF meng-unfreeze UID; notifikasi yang tertunda baru diproses dan TTS berbunyi beruntun.

**Bukan bukti probe watchdog rusak:** pada pengujian v1.0.4 sebelumnya (layar menyala), `watch check` → `watch probe ok` berulang tanpa pola gagal. Jangan memindahkan probe ke main thread atau membuang probe.

**Hipotesis yang perlu diuji, bukan diasumsikan benar:**
1. USF mengabaikan exemption widget saat layar mati (widget hanya dianggap important saat layar menyala/home aktif).
2. `NotifBaca:cpu` tidak pernah di-acquire sebelum freeze, sehingga tidak ada wake lock yang menahan proses. `updateCpuWakeLock()` hanya acquire saat `!isInteractive && sConnected && holdcpu`, dan itu bisa kalah cepat dengan freeze.
3. Preferensi `holdcpu` mungkin tidak aktif atau `sConnected` belum true saat layar mati.
4. Freeze terjadi pada aplikasi, bukan hanya engine TTS. Bukti: `NotifBaca:speak` dan `tts_speak_success` baru ada setelah LCD on.
5. Widget mungkin berada di halaman home yang tidak dianggap aktif oleh USF, meskipun provider terdaftar.

---

## 5. Langkah berikutnya untuk AI

1. **Baca** `AGENTS.md`, `HANDOFF-TERMUX.md`, dan dokumen ini. Jangan mengubah kode dulu.
2. **Verifikasi preferensi**: pastikan checkbox “Tahan proses saat layar mati” aktif; cek apakah `NotifBaca:cpu` pernah muncul di `dumpsys power` saat layar mati.
3. **Reproduksi layar mati** tanpa ADB berulang saat layar mati:
   - kunci layar 5–10 menit,
   - kirim notifikasi dari perangkat lain,
   - nyalakan layar,
   - ambil `adb logcat -d -b all` dan cari `Usf_Hiber`, `10562`, `NotifBaca`, `tts_speak`.
4. **Cek pengaturan Infinix/Transsion**:
   - Autostart,
   - battery unrestricted (sudah whitelist),
   - “Lock app” / kunci di recents,
   - pastikan widget berada di halaman home utama, bukan halaman tersembunyi.
5. **Uji hipotesis wake lock**: apakah acquire `NotifBaca:cpu` sebelum layar mati (atau receiver `SCREEN_OFF` yang lebih awal) mencegah freeze? Ukur dampak baterai; jangan acquire tanpa timeout.
6. **Jangan** menyimpulkan skenario C–F dari dokumen ini. Hanya B yang diuji; C–F belum.
7. Jika mengubah kode:
   - pertahankan `GAIN_TRANSIENT`,
   - FGS id=1/channel `run`,
   - watchdog grace 120 detik,
   - wake lock selalu dengan timeout,
   - jangan menulis isi notifikasi ke log,
   - jangan uninstall aplikasi; keystore harus tetap sama.

---

## 6. Kesimpulan singkat

| Item | Hasil |
|---|---|
| Skenario A (layar nyala) | Lulus pada putaran sebelumnya |
| Skenario B (layar mati) | **GAGAL** — tidak ada suara saat layar mati, TTS tertunda sampai layar menyala |
| Probe watchdog v1.0.4 | Tidak menunjukkan pola gagal saat layar menyala |
| Widget pelindung | Terpasang dan dikenali USF, tetapi freeze tetap terjadi |
| `cgroup.freeze` saat layar mati | Tidak tersampel karena ADB menggantung; log USF menunjukkan `FrozenState` |
| Skenario C–F | Tidak diuji |

**Inti handoff:** fokus berikutnya adalah **freeze USF saat layar mati**, bukan TTS atau probe watchdog. Jangan memperbaiki dengan membuang probe, memindahkannya ke main thread, atau menghapus widget.
