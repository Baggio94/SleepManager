# SleepManager

**Smart sleep automation for Android.**

SleepManager automatically puts selected Android services into a lower-power state when the screen turns off, then restores them when the device wakes.

> Current version: **0.2.12** — early preview

## What it does

- **Wi-Fi** — turns it off during sleep and restores its previous state on wake.
- **Bluetooth** — turns it off during sleep and restores its previous state on wake.
- **Syncthing-Fork** — pauses on sleep and resumes after connectivity restoration on wake.
- **Reboot persistence** — restarts the background automation after boot when enabled.
- **Live status UI** — shows current radio state, active behavior and last activity.

SleepManager only restores a radio when it changed that radio itself. If Wi-Fi or Bluetooth was already off before sleep, it stays off after wake.

## Syncthing-Fork

Supported package variants:

- `com.github.catfriend1.syncthingfork`
- `com.github.catfriend1.syncthingfork.debug`
- `com.github.catfriend1.syncthingandroid`
- `com.github.catfriend1.syncthingandroid.debug`

Sleep behavior:

- Screen OFF → Syncthing `STOP`
- Screen ON → restore connectivity → short delay → Syncthing `FOLLOW`

When Wi-Fi or Bluetooth is managed, the current preview waits **2.5 seconds** before sending `FOLLOW`. A future version will replace this fixed delay with real network-readiness detection.

## Compatibility helper

Wi-Fi and Bluetooth control is handled by a small companion helper APK included in this repository.

The helper:

- has no launcher activity
- has no UI
- requires no root
- requires no Shizuku
- accepts commands only from SleepManager through a signature-protected permission

The main app targets **Android 16 / API 36**. The compatibility helper targets API 28 to retain access to the older public Wi-Fi/Bluetooth toggle APIs on supported devices.

## Airplane mode

Airplane mode is visible in the UI but intentionally disabled for now. It will only be enabled if a safe no-ADB approach is found.

## Build

The included build script is currently designed for macOS with Homebrew:

```bash
chmod +x bootstrap_and_build.sh install_emulator.sh
./bootstrap_and_build.sh
```

Build outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
helper/build/outputs/apk/debug/helper-debug.apk
```

To install both APKs on a connected Android device or emulator:

```bash
./install_emulator.sh
```

## Quick test

```bash
./test_sleep_wake.sh
./test_last_activity.sh cycle
```

The test scripts use deterministic Android `SLEEP` and `WAKEUP` key events and print recent SleepManager activity.

## Project status

SleepManager is still an early preview. Current priorities include replacing the fixed Syncthing wake delay with real network detection, improving onboarding and continuing UI polish.

No license has been selected yet.
