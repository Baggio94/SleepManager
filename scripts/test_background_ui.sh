#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK_ROOT/platform-tools/adb"

echo "SleepManager process / service:"
"$ADB" shell dumpsys activity services com.med.sleepmanager | grep -E "SleepManagerService|foreground" || true

echo
echo "Recent task entries containing SleepManager (normally none because excludeFromRecents=true):"
"$ADB" shell dumpsys activity recents | grep -i -C 2 "com.med.sleepmanager" || true

echo
echo "SleepManager package process:"
"$ADB" shell pidof com.med.sleepmanager || true
