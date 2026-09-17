#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK_ROOT/platform-tools/adb"

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
HELPER_APK="$ROOT/helper/build/outputs/apk/debug/helper-debug.apk"

if [ ! -f "$APP_APK" ] || [ ! -f "$HELPER_APK" ]; then
  echo "APKs not found. Building first..."
  "$ROOT/bootstrap_and_build.sh"
fi

"$ADB" devices

# Main app first: it defines the signature-level permission used by the helper.
echo "Installing SleepManager..."
"$ADB" install -r "$APP_APK"

echo "Installing compatibility helper..."
"$ADB" install -r "$HELPER_APK"

echo "Launching SleepManager..."
"$ADB" shell am force-stop com.med.sleepmanager
"$ADB" shell am start -n com.med.sleepmanager/.MainActivity
