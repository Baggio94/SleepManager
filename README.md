# SleepManager

**Smart sleep automation for Android.**

SleepManager automatically puts selected Android services into a lower-power state when the screen turns off, then restores them when the device wakes.

> Current version: **0.2.12**

## Features

- **Wi-Fi** — turns it off during sleep and restores its previous state on wake.
- **Bluetooth** — turns it off during sleep and restores its previous state on wake.
- **Syncthing-Fork** — pauses on sleep and resumes after connectivity restoration on wake.
- **Previous-state restoration** — if Wi-Fi or Bluetooth was already off before sleep, SleepManager leaves it off after wake.
- **Reboot persistence** — restarts the automation after boot when SleepManager was enabled.
- **Live status** — shows current radio state, active behavior and the last sleep/wake activity.

## Installation

1. Open the [latest SleepManager release](https://github.com/Baggio94/SleepManager/releases/latest).
2. Download:
   - `SleepManager-0.2.12-debug.apk`
   - `SleepManager-Helper-0.2.12-debug.apk`
3. Install **SleepManager first**.
4. Install the **Helper APK** if you want SleepManager to control Wi-Fi and/or Bluetooth.
5. Open SleepManager and choose the actions you want it to manage.

The Helper has no launcher icon or UI. It is only used by SleepManager to control Wi-Fi and Bluetooth without root, Shizuku or ADB.

If you only want the Syncthing-Fork integration, the Helper is not required.

## Setup

### Wi-Fi and Bluetooth

Enable the Wi-Fi and/or Bluetooth switches inside SleepManager.

The switch means **SleepManager manages this setting while the device sleeps**. The `Current: ON/OFF` label shows the actual current Android state.

SleepManager remembers the state before sleep:

- Wi-Fi ON before sleep → turns OFF → restores ON after wake.
- Wi-Fi already OFF → remains OFF.
- Bluetooth ON before sleep → turns OFF → restores ON after wake.
- Bluetooth already OFF → remains OFF.

### Syncthing-Fork

SleepManager supports these Syncthing-Fork package variants:

- `com.github.catfriend1.syncthingfork`
- `com.github.catfriend1.syncthingfork.debug`
- `com.github.catfriend1.syncthingandroid`
- `com.github.catfriend1.syncthingandroid.debug`

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

Then return to SleepManager:

1. Select Syncthing-Fork as the target if prompted.
2. Enable the Syncthing-Fork switch.
3. Enable SleepManager.

## How it works

When the screen turns **OFF**:

1. SleepManager sends `STOP` to Syncthing-Fork if enabled.
2. It turns off the selected radios.

When the screen turns **ON**:

1. SleepManager restores Wi-Fi and Bluetooth to their previous states.
2. If connectivity is being managed, it waits **2.5 seconds**.
3. It sends `FOLLOW` to Syncthing-Fork so normal Syncthing run conditions resume.

If neither Wi-Fi nor Bluetooth is managed, the current version uses a shorter **0.8 second** wake delay before `FOLLOW`.

A future version will replace the fixed delay with real network-readiness detection.

## Using SleepManager

Once your options are configured:

1. Tap **Enable SleepManager**.
2. Check **Current behavior** to confirm what will happen on sleep and wake.
3. Tap **Finish setup** if you want to close the setup screen.

SleepManager continues running in the background after the setup screen is closed.

The **Last activity** section shows what happened during the most recent sleep/wake cycle, for example:

```text
Wake
Wi-Fi · Restored to previous state
Bluetooth · Unchanged
Syncthing · Resumed
```

## Airplane mode

Airplane mode is visible in the UI but intentionally disabled for now.

It will only be enabled if a safe no-ADB method is found.

## Compatibility helper

Wi-Fi and Bluetooth control is handled by a small companion Helper APK.

The Helper:

- has no launcher activity
- has no UI
- requires no root
- requires no Shizuku
- requires no ADB
- accepts commands only from SleepManager through a signature-protected permission

The main app targets **Android 16 / API 36**. The compatibility helper targets API 28 to retain access to the older public Wi-Fi/Bluetooth toggle APIs on supported devices.

## Build from source

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

SleepManager is under active development. Current priorities include real network-readiness detection before Syncthing resumes, onboarding improvements and continued UI polish.

No license has been selected yet.
