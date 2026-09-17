#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$SDK_ROOT"

need() { command -v "$1" >/dev/null 2>&1; }

if ! need brew; then
  echo "Homebrew is required."
  exit 1
fi

if ! need java; then
  brew install openjdk@21
  export PATH="$(brew --prefix openjdk@21)/bin:$PATH"
fi

if ! need sdkmanager; then
  brew install --cask android-commandlinetools
fi

if ! need gradle; then
  brew install gradle
fi

mkdir -p "$SDK_ROOT"

yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

cd "$ROOT"

if [ ! -x "./gradlew" ]; then
  gradle wrapper --gradle-version 9.6.0
fi

./gradlew :app:assembleDebug :helper:assembleDebug

echo
echo "Build complete:"
echo "  $ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo "  $ROOT/helper/build/outputs/apk/debug/helper-debug.apk"
