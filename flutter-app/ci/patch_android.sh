#!/usr/bin/env bash
# 用 flutter create 生成的 android/ 脚手架打我们的补丁：
# 1) AndroidManifest 插入 INTERNET 权限 + AdMob APPLICATION_ID meta-data
# 2) 替换启动图标为我们的（紫粉渐变 + 金币播放 + monochrome）
# 用法: patch_android.sh <flutter项目根>
set -euo pipefail

GEN="$1"
RES="$GEN/android/app/src/main/res"
MANIFEST="$GEN/android/app/src/main/AndroidManifest.xml"
OVERLAY="$(cd "$(dirname "$0")" && pwd)/android-overlay/res"

ADMOB_APP_ID="${ADMOB_APP_ID:-ca-app-pub-3940256099942544~3347511713}"

# --- manifest：补 INTERNET（release 模板默认没有）+ AdMob App ID ---
if ! grep -q "android.permission.INTERNET" "$MANIFEST"; then
  sed -i 's#<application#<uses-permission android:name="android.permission.INTERNET" />\n    <application#' "$MANIFEST"
fi
if ! grep -q "com.google.android.gms.ads.APPLICATION_ID" "$MANIFEST"; then
  sed -i "s#</application>#    <meta-data android:name=\"com.google.android.gms.ads.APPLICATION_ID\" android:value=\"$ADMOB_APP_ID\" />\n    </application>#" "$MANIFEST"
fi

# --- 启动图标覆盖 ---
mkdir -p "$RES"
cp -f "$OVERLAY/drawable/ic_launcher_background.xml" "$RES/drawable/" 2>/dev/null || \
  { mkdir -p "$RES/drawable"; cp -f "$OVERLAY/drawable/ic_launcher_background.xml" "$RES/drawable/"; }
cp -f "$OVERLAY/drawable/ic_launcher_foreground.xml" "$RES/drawable/"
cp -f "$OVERLAY/drawable/ic_launcher_monochrome.xml" "$RES/drawable/"
mkdir -p "$RES/mipmap-anydpi-v26"
cp -f "$OVERLAY/mipmap-anydpi-v26/ic_launcher.xml" "$RES/mipmap-anydpi-v26/"
cp -f "$OVERLAY/mipmap-anydpi-v26/ic_launcher_round.xml" "$RES/mipmap-anydpi-v26/"

echo "[patch] manifest + launcher icons done"
grep -c "INTERNET" "$MANIFEST" > /dev/null && echo "[patch] INTERNET ok"
grep -c "APPLICATION_ID" "$MANIFEST" > /dev/null && echo "[patch] AdMob id ok"