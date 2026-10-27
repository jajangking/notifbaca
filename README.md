# NotifBaca

Aplikasi Android yang membacakan notifikasi masuk dengan suara (TTS).
Package `com.home.notifbaca`, minSdk 26 / targetSdk 33. UI, log, dan commit berbahasa Indonesia.

## Fitur

- Baca notifikasi otomatis via TTS (voice offline atau network, pilih di UI)
- Pilih suara + tes suara
- Jadwal pengumuman jam + ringkasan harian
- Filter: hanya saat Bluetooth aktif (termasuk LE Audio), jeda sementara, blokir package
- Quick Settings tile "Jeda Baca"
- Rebind otomatis: watchdog di layanan + JobScheduler periodik kalau proses dibunuh ROM
- Widget pelindung 2x1 untuk ROM Infinix/Transsion (`Usf_Hiber`) agar layanan tidak dibekukan saat app ditinggalkan, lengkap dengan status aktif
- Mode andal: menahan CPU saat layar mati supaya notifikasi tidak tertunda (bisa dimatikan, baterai lebih boros)
- Tombol izin battery optimization + status izin notifikasi
- Halaman Tentang & Kebijakan Privasi di dalam app

## Install (pakai)

Download APK dari [Releases](https://github.com/jajangking/notifbaca/releases),
izinkan sumber tak dikenal, lalu beri izin akses notifikasi + POST_NOTIFICATIONS
lewat UI aplikasi.

Setelah itu buka **"Izinkan latar belakang (baterai)"** dan, di Infinix/Transsion,
izinkan juga **Autostart** lewat pengaturan aplikasi. Tanpa ini ROM agresif bisa
membunuh layanan saat aplikasi tidak dibuka.

Khusus Infinix/Transsion (XOS), tekan **"Pasang widget pelindung"** di bagian
KEANDALAN lalu tambahkan widget NotifBaca ke layar utama. Layanan `Usf_Hiber`
membekukan seluruh UID aplikasi beberapa detik setelah layar utama ditekan
walaupun sudah foreground service; aplikasi yang punya widget di home screen
dikecualikan. **Jangan hapus widget itu** selama memakai NotifBaca — tanpa widget,
notifikasi berhenti dibaca saat aplikasi ditinggalkan.

**Mode andal** (aktif secara default) menahan CPU saat layar mati supaya callback
notifikasi tidak ikut tertidur. Ini membuat baterai lebih boros; matikan checkbox
di bagian KEANDALAN kalau tidak diperlukan.

Catatan: sejak v1.0 APK ditandatangani keystore baru — kalau pernah install
build lama, uninstall dulu sebelum install rilis GitHub.

## Build (kembang)

Tanpa Gradle, manual di Termux. Cara termudah:

```
ANDROID_JAR=~/androidjar/android-13/android.jar ./build.sh
```

`build.sh` menjalankan `aapt2 compile`, `aapt2 link`, `javac`, `d8`, inject dex,
lalu `apksigner`. Kalau `NB_KS_PASS` / `NB_KEY_PASS` tidak di-set, `apksigner`
akan meminta password keystore secara interaktif.

Setara manualnya:

```
aapt2 compile --dir res -o obj/res.zip
aapt2 link -o base.apk -I ~/androidjar/android-13/android.jar --manifest AndroidManifest.xml obj/res.zip
javac -source 8 -target 8 -classpath ~/androidjar/android-13/android.jar -d obj src/com/home/notifbaca/*.java
mkdir -p dexout
d8 --release --lib ~/androidjar/android-13/android.jar --output dexout obj/com/home/notifbaca/*.class
python3 inject.py
apksigner sign --ks keystore/nb.keystore --ks-key-alias nb --ks-pass pass:<pass> --out notifbaca.apk unsigned.apk
```

`keystore/nb.keystore` rahasia dan di-gitignore — jangan commit, jangan buat
ulang sembarangan (keystore baru = user harus uninstall dulu).

## Privasi

Isi notifikasi diproses lokal di perangkat dan **tidak ditulis ke logcat**.
Tidak ada akun, server, iklan, atau pelacakan; Auto Backup Android dimatikan.

Mode andal menahan CPU saat layar mati (partial wake lock) agar notifikasi tidak
tertunda; ini bisa dimatikan di aplikasi dan hanya memengaruhi baterai.

Pengecualian: kalau memilih voice **network** (butuh internet), mesin TTS bisa
mengirim teks ke penyedia voice. Pilih voice offline kalau ingin 100% lokal.
Detail di menu "Tentang & kebijakan privasi" dalam aplikasi.

## Kontak

jajangpostmab@gmail.com
