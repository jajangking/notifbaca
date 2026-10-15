# NotifBaca

Aplikasi Android yang membacakan notifikasi masuk dengan suara (TTS offline).
Package `com.home.notifbaca`, minSdk 26 / targetSdk 33. UI, log, dan commit berbahasa Indonesia.

## Fitur

- Baca notifikasi otomatis via TTS (offline, data lokal saja)
- Pilih suara + tes suara
- Jadwal pengumuman jam + ringkasan harian
- Filter: hanya saat Bluetooth aktif, jeda sementara, blokir package
- Quick Settings tile "Jeda Baca"
- Halaman Tentang & Kebijakan Privasi di dalam app

## Install (pakai)

Download APK dari [Releases](https://github.com/jajangking/notifbaca/releases),
izinkan sumber tak dikenal, lalu beri izin akses notifikasi + POST_NOTIFICATIONS
lewat UI aplikasi.

Catatan: sejak v1.0 APK ditandatangani keystore baru — kalau pernah install
build lama, uninstall dulu sebelum install rilis GitHub.

## Build (kembang)

Tanpa Gradle, manual di Termux (tools: `aapt2`, `d8`, `apksigner`,
`android.jar` dari `~/androidjar/android-13/android.jar`):

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

Isi notifikasi HANYA dibacakan lokal di perangkat, tidak dikirim ke mana pun.
Tidak ada akun, server, iklan, atau pelacakan. Detail di menu
"Tentang & kebijakan privasi" dalam aplikasi.

## Kontak

jajangpostmab@gmail.com
