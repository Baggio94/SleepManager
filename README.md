# SleepManager

SleepManager is a lightweight Android utility for handheld devices that automates selected actions when the screen turns off and restores them when the device wakes.

The project is designed to stay predictable and lightweight:

- event-driven rather than polling-driven
- no root
- no Shizuku
- no ADB requirement on the device
- no permanent wake lock
- restore only the state SleepManager actually changed

## Features

| Feature | Sleep | Wake |
| --- | --- | --- |
| Wi-Fi | Turns Wi-Fi off when enabled | Restores it only if SleepManager turned it off |
| Bluetooth | Turns Bluetooth off when enabled | Restores it only if SleepManager turned it off |
| Syncthing-Fork | Sends `STOP`, then gives it a short grace period before managed radios are disabled | Sends `FOLLOW` after the normal wake flow |
| AYN Thor closed-lid protection | Keeps protection armed while the lid is closed | Suppresses false wakes and calls `lockNow()` instead of restoring normal wake actions |

## Components

### SleepManager

Main application:

`com.med.sleepmanager`

The main app contains the UI, foreground service, sleep/wake sequencing, Syncthing integration, and AYN Thor closed-lid protection.

### SleepManager Helper

Compatibility helper:

`com.med.sleepmanager.helper`

The Helper has no launcher icon and no user interface. It exists only to control Wi-Fi and Bluetooth through the older Android APIs required by this project.

The main app must be installed before the Helper because the two apps communicate through a signature-level permission.

## Compatibility

- Minimum Android version: **Android 9 / API 28**
- SleepManager target SDK: **36**
- Helper target SDK: **28**

`targetSdk` is not the minimum Android version.

## Installation

Install the APKs in this order:

1. **SleepManager**
2. **SleepManager Helper**

Then open SleepManager, choose the actions you want it to manage, and enable SleepManager.

If Wi-Fi or Bluetooth management is enabled, the Helper must be installed.

## Setup

### Wi-Fi and Bluetooth

SleepManager keeps track of whether each managed radio was already on before sleep and whether SleepManager actually changed it.

That means:

- Wi-Fi already off before sleep stays off after wake.
- Bluetooth already off before sleep stays off after wake.
- A radio is restored only when SleepManager itself turned it off.

This avoids unexpectedly changing the user's previous connectivity state.

### Syncthing-Fork

Supported package names:

- `com.github.catfriend1.syncthingfork`
- `com.github.catfriend1.syncthingfork.debug`
- `com.github.catfriend1.syncthingandroid`
- `com.github.catfriend1.syncthingandroid.debug`

In Syncthing-Fork, enable:

`Settings -> Behaviour -> Service Control by Broadcast`

When the device sleeps, SleepManager sends `STOP` first. In 0.3.2, if Wi-Fi or Bluetooth also needs to be disabled, SleepManager waits **1 second** before applying the radio sleep action. A short, one-shot partial wake lock keeps the CPU alive only for that transition.

On a normal wake, SleepManager restores the managed radio state first and currently schedules Syncthing `FOLLOW` **2.5 seconds** later.

### AYN Thor closed-lid protection

This feature was developed and validated on the AYN Thor.

The Thor exposes a Linux Hall switch (`hall_switch / SW_LID`). SleepManager discovers the corresponding input device dynamically instead of relying on a fixed `/dev/input/eventX` path.

When the lid is closed, protection remains armed. If Android wakes while the lid is still closed, SleepManager:

1. does not restore Wi-Fi or Bluetooth
2. does not resume Syncthing
3. calls `DevicePolicyManager.lockNow()`
4. ignores the duplicate sleep event that follows

Enabling this feature requires Android Device Admin access because `lockNow()` is the only reason SleepManager uses Device Admin.

Disabling AYN Thor closed-lid protection removes that Device Admin access.

## Sleep and wake sequence

### Normal sleep

```text
SCREEN_OFF
  -> Syncthing STOP
  -> 1 s grace period when managed radios also need to sleep
  -> Wi-Fi / Bluetooth sleep action
  -> remember only the states SleepManager actually changed
```

### Closed-lid false wake on AYN Thor

```text
SCREEN_ON while SW_LID = CLOSED
  -> restore nothing
  -> do not send Syncthing FOLLOW
  -> lockNow()
  -> duplicate SCREEN_OFF is ignored
```

### Normal wake

```text
SW_LID = OPEN / normal SCREEN_ON
  -> restore only app-managed radio changes
  -> schedule Syncthing FOLLOW
```

## Architecture and battery impact

SleepManager uses an event-driven foreground service.

It does **not** permanently poll the screen state. The main sleep/wake flow reacts to Android screen events, while the Thor Hall sensor is read directly through the Linux input device.

The app does not hold a permanent wake lock. The only wake lock in 0.3.2 is a temporary one-shot partial wake lock used during the 1-second Syncthing STOP grace period, with a safety timeout.

The UI queries current radio and integration state only while the Activity is visible.

## Build identity and updates

Android updates depend on more than the package name.

SleepManager intentionally keeps these application IDs stable:

- Main app: `com.med.sleepmanager`
- Helper: `com.med.sleepmanager.helper`

For an APK to update an already installed build without uninstalling it, Android also requires the **same signing certificate** and a compatible `versionCode`.

The main app and Helper must always be signed with the same project key because their communication permission is protected with `signature`.

Project version and application IDs are centralized in `gradle.properties` so both modules stay aligned.

### Local stable signing

Copy:

```bash
cp signing.properties.example signing.properties
```

Then edit `signing.properties` with the path, alias, and credentials for your project signing key.

`signing.properties`, `*.jks`, and `*.keystore` are ignored by Git and must never be committed.

When stable signing is configured, both the app and Helper use the same signer for local debug/release builds.

### GitHub Actions signing

Official CI artifacts should use the same signing key through GitHub Actions Secrets:

- `SLEEPMANAGER_KEYSTORE_B64`
- `SLEEPMANAGER_KEYSTORE_PASSWORD`
- `SLEEPMANAGER_KEY_ALIAS`
- `SLEEPMANAGER_KEY_PASSWORD`

The keystore itself must remain private and must never be committed to the repository.

The workflow verifies the two package IDs and checks that the app and Helper were signed with the same certificate before publishing artifacts.

## Build from source

Requirements:

- JDK 17+
- Android SDK 36
- Android build-tools 36.0.0
- Gradle 9.6.0, or use the included bootstrap script

On macOS:

```bash
chmod +x bootstrap_and_build.sh
./bootstrap_and_build.sh
```

Debug APKs are generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
helper/build/outputs/apk/debug/helper-debug.apk
```

Install the main app before the Helper:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r helper/build/outputs/apk/debug/helper-debug.apk
```

## Launcher icon

SleepManager 0.3.2 uses an Android launcher icon set with:

- adaptive foreground/background layers
- Android 13+ monochrome/themed icon support
- round launcher support
- legacy density fallbacks for older launchers
- a separate 512x512 store icon source

The manifest uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

## Troubleshooting

### Syncthing does not stop or resume

Check that:

- a supported Syncthing-Fork package is installed
- `Service Control by Broadcast` is enabled
- the correct detected Syncthing build is selected in SleepManager

### Wi-Fi or Bluetooth does not change

Check that the SleepManager Helper is installed and that the main app was installed first.

### APK refuses to update

If Android reports a signature mismatch, the new APK was signed with a different certificate. Matching package IDs alone are not enough.

Use one stable signing key for every build you intend to install as an update.

### AYN Thor protection cannot be enabled

SleepManager must detect the Thor Hall sensor and Device Admin access must be granted.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## Roadmap

Possible future improvements include:

- network-ready detection before Syncthing `FOLLOW` instead of a fixed wake delay
- media pause/resume
- Quick Settings tile
- configurable sleep delays
- charging/battery conditions
- diagnostics/history
- additional integrations

## Current status

**0.3.2** is a consolidation release focused on sleep reliability, Syncthing shutdown timing, launcher icon completeness, build/update consistency, and clearer documentation.
