#!/bin/bash
# 离线构建脚本（不依赖 Gradle / 网络）
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
# SDK / JDK 位置：优先用环境变量（CI 里由 setup-android / setup-java 提供），否则回退到本机默认路径
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
BT="${BT_DIR:-$SDK/build-tools/34.0.0}"
AJAR="${AJAR:-$SDK/platforms/android-34/android.jar}"
if [ -z "$JAVA_HOME" ]; then
  if [ -d /opt/homebrew/opt/openjdk@17 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  elif [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  fi
fi
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"

AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"

OUT="$ROOT/build"
APP_VER="1.1.2"
REL_KS="$ROOT/keys/padkeys-release.jks"
if [ -f "$REL_KS" ]; then
  APK_NAME="PadKeys-v${APP_VER}.apk"
else
  APK_NAME="padkeys-$(date +%Y%m%d-%H%M).apk"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> 1/6 编译资源"
"$AAPT2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "==> 2/6 链接资源 + 生成 R.java"
"$AAPT2" link -o "$OUT/base.apk" \
  -I "$AJAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  "$OUT/res.zip"

echo "==> 3/6 编译 Java"
SRC_LIST="$OUT/sources.txt"
find "$ROOT/src" "$OUT/gen" -name "*.java" > "$SRC_LIST"
CP="$AJAR"
if [ -d "$ROOT/libs" ]; then
  for j in "$ROOT"/libs/*.jar; do CP="$CP:$j"; done
fi
"$JAVAC" -encoding UTF-8 -source 11 -target 11 -nowarn \
  -classpath "$CP" -d "$OUT/classes" @"$SRC_LIST"

echo "==> 4/6 转 dex"
( cd "$OUT/classes" && jar cf "$OUT/classes.jar" . )
D8_IN="$OUT/classes.jar"
if [ -d "$ROOT/libs" ]; then
  for j in "$ROOT"/libs/*.jar; do D8_IN="$D8_IN $j"; done
fi
"$D8" --release --min-api 28 --lib "$AJAR" --output "$OUT/dex" $D8_IN

echo "==> 5/6 打包"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes.dex )
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "==> 6/6 签名"
if [ -f "$REL_KS" ]; then
  echo "   使用正式签名 keys/padkeys-release.jks"
  "$APKSIGNER" sign --ks "$REL_KS" --ks-pass pass:padkeys2026 --key-pass pass:padkeys2026 \
    --ks-key-alias padkeys --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
  "$APKSIGNER" verify --print-certs "$OUT/$APK_NAME" | head -4
  echo
  echo "APK(正式版): $OUT/$APK_NAME"
  ls -lh "$OUT/$APK_NAME"
  exit 0
fi
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 \
    -validity 10000 >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
"$APKSIGNER" verify --print-certs "$OUT/$APK_NAME" | head -5

echo
echo "APK: $OUT/$APK_NAME"
ls -lh "$OUT/$APK_NAME"
