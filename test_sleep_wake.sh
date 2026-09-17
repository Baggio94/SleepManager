#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK_ROOT/platform-tools/adb"

"$ADB" logcat -c

echo "Initial Wi-Fi state:"
"$ADB" shell cmd wifi status | head -n 1 || true

echo
echo "Forcing screen to SLEEP..."
"$ADB" shell input keyevent 223
sleep 2

echo
echo "=== WIFI WHILE SCREEN OFF ==="
"$ADB" shell cmd wifi status | head -n 3 || true

sleep 3

echo
echo "Forcing screen to WAKE..."
"$ADB" shell input keyevent 224
sleep 3

echo
echo "=== WIFI AFTER WAKE ==="
"$ADB" shell cmd wifi status | head -n 3 || true

echo
echo "=== SLEEPMANAGER LOGS ==="
"$ADB" logcat -d -t 500 | grep -E "SleepManager|SleepManagerHelper|AppConfigReceiver" || true
