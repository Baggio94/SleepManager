# SleepManager 0.7.0

SleepManager 0.7 is mainly a **reliability and cleanup release**.

A large part of the app has been refactored internally to make the sleep/wake logic cleaner, easier to maintain and safer to change in the future.

There are also several important bug fixes around false wakes, Wi-Fi/Bluetooth restore, Battery Saver and clamshell behavior.

### Major internal refactor

A lot of the sleep/wake code has been reorganized and split into smaller, more focused parts.

This should make SleepManager easier to maintain.

### Battery Saver improvements

Battery Saver handling is now aware of external power.

If the device goes to sleep while plugged in, SleepManager no longer tries to enable Battery Saver unnecessarily.

If power is removed while the device is still asleep, Battery Saver can still be enabled and the original state is restored on wake.

### Bug fixes

- Fixed **#29** — some devices could report an unrealistic battery capacity. SleepManager now detects clearly invalid battery values and uses a safer fallback for the displayed stats.
- Improved handling for **#30** on AYN Thor — when SELinux prevents access to the lid sensor, SleepManager now shows a clear warning instead of silently hiding the clamshell options.
- Improved closed-lid false-wake handling so the original sleep cycle and pending restores are preserved until a real wake.
- Improved Wi-Fi and Bluetooth restoration around delayed or stale Helper responses.

### Diagnostics 2.0

Diagnostics have been expanded to keep more useful information across multiple sleep/wake cycles.

Advanced diagnostics are **off by default** so normal SleepManager operation stays lightweight.

They can be enabled from the Activity page when more detailed troubleshooting information is needed.

## SleepManager Helper 1.1.2

The Helper also receives an update in this release.

- More reliable Wi-Fi and Bluetooth restore.
- New cycle-aware communication with SleepManager.
- Better protection against delayed or mismatched restore requests.

The Helper is only required if you use Wi-Fi or Bluetooth management.

## Compatibility

- Android **9 / API 28 or newer**
- SleepManager **0.7.0**
- SleepManager Helper **1.1.2**
- Existing settings are preserved when updating
- No root, Shizuku or ADB is required for normal use

## Installation

1. Download and install **SleepManager 0.7.0** from the release assets below.
2. If you use Wi-Fi or Bluetooth management, install **SleepManager Helper 1.1.2** as well.
3. If you are updating from an older version, simply install the new APK over the existing app.
4. Your existing SleepManager settings will be preserved.

You can also update directly from **About → Updates** inside SleepManager.

After updating, open SleepManager once and check **About → Updates** to make sure both SleepManager and the Helper are up to date.
