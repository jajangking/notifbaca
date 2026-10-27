#!/usr/bin/env bash
# Build manual NotifBaca (tanpa Gradle). Jalankan dari mana saja.
set -euo pipefail
cd "$(dirname "$0")"

AJ="${ANDROID_JAR:-$HOME/androidjar/android-13/android.jar}"
KS="${KEYSTORE:-keystore/nb.keystore}"
ALIAS="${KS_ALIAS:-nb}"
OUT="${OUT_APK:-notifbaca.apk}"

if [ ! -f "$AJ" ]; then
    echo "android.jar tidak ditemukan: $AJ" >&2
    echo "Set ANDROID_JAR=/path/ke/android.jar" >&2
    exit 1
fi
if [ ! -f "$KS" ]; then
    echo "keystore tidak ditemukan: $KS" >&2
    echo "Set KEYSTORE=/path/ke/nb.keystore (jangan commit keystore)" >&2
    exit 1
fi

rm -rf obj dexout base.apk unsigned.apk
mkdir -p obj dexout

echo "[1/6] aapt2 compile"
aapt2 compile --dir res -o obj/res.zip

echo "[2/6] aapt2 link"
aapt2 link -o base.apk -I "$AJ" --manifest AndroidManifest.xml \
    --java obj obj/res.zip

echo "[3/6] javac"
javac -source 8 -target 8 -Xlint:-options -classpath "$AJ" \
    -d obj obj/com/home/notifbaca/R.java src/com/home/notifbaca/*.java

echo "[4/6] d8"
d8 --release --lib "$AJ" --output dexout obj/com/home/notifbaca/*.class

echo "[5/6] inject dex"
python3 inject.py

echo "[6/6] apksigner"
SIGN_ARGS=(--ks "$KS" --ks-key-alias "$ALIAS" --out "$OUT")
if [ -n "${NB_KS_PASS:-}" ]; then
    SIGN_ARGS+=(--ks-pass "env:NB_KS_PASS")
fi
if [ -n "${NB_KEY_PASS:-}" ]; then
    SIGN_ARGS+=(--key-pass "env:NB_KEY_PASS")
fi
apksigner sign "${SIGN_ARGS[@]}" unsigned.apk
apksigner verify --print-certs "$OUT"

echo
echo "Selesai: $OUT"
aapt2 dump badging "$OUT" | grep -E "^package:|^sdkVersion|^targetSdkVersion" || true
