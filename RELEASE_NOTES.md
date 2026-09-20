# SleepManager 0.5.1 RC1

SleepManager 0.5.1 focuses on safer background operation, a built-in updater, improved battery statistics, and polish discovered during real-device testing on the AYN Thor.

## Highlights

### Built-in updater

- Checks the latest stable GitHub release automatically, at most once every 24 hours.
- Supports manual update checks from **About → Updates**.
- Shows an Android notification when a newer stable version is available.
- Tapping an update notification opens SleepManager directly on the Updates section.
- Downloads the release APK directly inside SleepManager when trusted update metadata is available.
- Verifies the downloaded APK before installation:
  - SHA-256 digest
  - package name `com.med.sleepmanager`
  - release version name
  - a strictly newer Android version code
  - the permanent SleepManager signing certificate
- Opens Android's official package installer after verification.
- Uses Android's one-time **Install unknown apps** permission for SleepManager; no root, Shizuku or ADB is required.
- Stable releases now publish an `update.json` manifest alongside the APKs.
- Falls back to the GitHub release page when direct-install metadata is unavailable.

The complete download → verification → Android installer flow was validated with a separately signed prerelease before RC1.

### Safer background behavior and external navigation

- SleepManager is hidden from Android Recents again so it cannot be accidentally swiped away on devices such as the AYN Thor, where that action can terminate the foreground service.
- App-initiated navigation is now distinguished from a genuine Home/leave action.
- Android permission screens, App info, GitHub links and update flows no longer cause SleepManager to remove its own task.
- The foreground service still restarts after package replacement when SleepManager was enabled.

### Notification polish

- Replaced the generic Android alarm status-bar icon with a dedicated SleepManager small-notification icon.
- The new icon uses a monochrome transparent vector designed for Android status-bar notification rendering.
- Update notifications and the persistent foreground-service notification use the same SleepManager visual identity.

### Battery statistics refinements

- The battery gauge now cycles through useful sleep statistics when tapped.
- Available views include battery capacity, estimated standby, sleep drain per hour and deep-sleep percentage.
- Battery percentage alignment and responsive presentation were refined across handheld and tablet layouts.

### Update notification controls

- Added notification permission handling on Android 13+.
- Update-notification state is shown in **About → Updates**.
- Developer builds retain a detection-only simulated-update control; it is not present in this RC build.

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Compatibility Helper target SDK: **28**
- Same package IDs and permanent signing certificate as previous official builds
- RC1 version code: **521**
- The final 0.5.1 stable build must use a version code higher than 521 so RC testers can update normally

## Installation / update

1. Install **SleepManager 0.5.1-rc1** over an existing official signed build.
2. Install **SleepManager Helper 0.5.1-rc1** only if you use Wi-Fi or Bluetooth management and want matching version metadata.
3. Existing settings are preserved when updating an official signed build.
4. Android may ask once for notification permission and, when using the built-in updater, permission for SleepManager to install unknown apps.
5. For Syncthing-Fork, keep **Settings → Behaviour → Service Control by Broadcast** enabled.
6. For AYN Thor protection, keep the one-time Device Admin permission enabled.

No root, Shizuku or ADB is required for normal use.
