#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="${ADB:-$SDK_ROOT/platform-tools/adb}"
PKG="com.med.sleepmanager"
PREFS_PATH="shared_prefs/sleep_manager.xml"

if [ ! -x "$ADB" ]; then
  echo "adb not found at: $ADB"
  echo "Set ANDROID_SDK_ROOT or ADB first."
  exit 1
fi

show_pref() {
  echo
  echo "=== LAST ACTIVITY ==="
  "$ADB" shell run-as "$PKG" cat "$PREFS_PATH" 2>/dev/null \
    | sed 's/></>\n</g' \
    | grep -E 'last_event|last_event_time' || true
}

show_logs() {
  echo
  echo "=== RECENT LOGS ==="
  "$ADB" logcat -d -t 250 \
    | grep -E 'SleepManager|SleepManagerHelper|AppConfigReceiver' || true
}

wifi_status() {
  local first
  first=$("$ADB" shell cmd wifi status 2>/dev/null | head -n 1 | tr -d '\r')
  echo "Wi-Fi: ${first:-unknown}"
}

bt_status() {
  local state
  state=$("$ADB" shell settings get global bluetooth_on 2>/dev/null | tr -d '\r')
  case "$state" in
    1) echo "Bluetooth: enabled" ;;
    0) echo "Bluetooth: disabled" ;;
    *) echo "Bluetooth: unknown ($state)" ;;
  esac
}

screen_cycle() {
  echo
  echo "===== BEFORE ====="
  wifi_status
  bt_status

  echo
  echo ">>> Forcing screen to SLEEP"
  "$ADB" shell input keyevent 223
  sleep 3

  echo
  echo "===== DURING SLEEP ====="
  wifi_status
  bt_status
  show_pref

  echo
  echo ">>> Forcing screen to WAKE"
  "$ADB" shell input keyevent 224
  # SleepManager can intentionally delay Syncthing FOLLOW by up to 2.5 s
  # after connectivity restoration, so wait long enough for Last activity.
  sleep 5

  echo
  echo "===== AFTER WAKE ====="
  wifi_status
  bt_status
  show_pref
}

case "${1:-cycle}" in
  cycle)
    "$ADB" logcat -c
    screen_cycle
    show_logs
    ;;
  prefs)
    show_pref
    ;;
  logs)
    show_logs
    ;;
  wifi-on)
    "$ADB" shell svc wifi enable
    sleep 2
    wifi_status
    ;;
  wifi-off)
    "$ADB" shell svc wifi disable
    sleep 2
    wifi_status
    ;;
  help|-h|--help)
    cat <<EOF
Usage: ./scripts/test_last_activity.sh [command]

Commands:
  cycle    Deterministic SLEEP/WAKE cycle + Last activity + logs (default)
  prefs    Show saved Last activity
  logs     Show recent SleepManager-related logs
  wifi-on  Enable Wi-Fi, then print status
  wifi-off Disable Wi-Fi, then print status
EOF
    ;;
  *)
    echo "Unknown command: $1"
    echo "Use ./scripts/test_last_activity.sh --help"
    exit 1
    ;;
esac
