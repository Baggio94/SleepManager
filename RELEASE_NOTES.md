# SleepManager 0.7.3

SleepManager 0.7.3 focuses on a more natural **touch-first experience**, clearer sleep controls, and compatibility improvements, while keeping the existing sleep/wake automation intact.

## What's New

- **Touch-first navigation** — removed the custom gamepad key, stick, shoulder-button and focus-handling layer. SleepManager now uses standard Android touch and accessibility behavior.
- **Redesigned Grace Period on Home** — four easy-to-tap choices: **Immediate**, **5s**, **10s**, and **Custom**. Custom durations are **1, 2, 3, 5, 10, 15, 30 minutes, or 1 hour**. The selected duration is displayed clearly; **Cancel** leaves the previous setting unchanged.
- **Simpler battery conditions** — set the Advanced battery threshold with **− / +** buttons, using the existing **20–60%** range in 10% increments.
- **RAOfflineProxy v2.2 compatibility** — when the installed version requires the Android runtime **CONTROL_PROXY** permission, SleepManager can request it through the system permission dialog. Older versions continue to use the existing permission guidance.
- **Read-only Status API v1** — compatible launchers can query whether SleepManager is **enabled** and whether its foreground service is **running**. This API does not change settings or expose private diagnostics. See [Status API documentation](https://github.com/Baggio94/SleepManager/blob/main/docs/status-api.md).
- **Home UI polish** — removed unnecessary hidden UI placeholders, improved list item stability, and refined Grace Period labels and scrolling behavior.

The existing sleep/wake orchestration, radio restoration, sync integrations, and Compatibility Helper behavior remain unchanged. The larger background refresh optimization from the **0.7.4 preview is not part of this release**.

## Compatibility

- **SleepManager:** 0.7.3 (versionCode **567**)
- **Compatibility Helper:** 1.1.7 (versionCode **1121**, unchanged)
- **Android:** 9 / API 28 or newer
- Compatible recent AYN and Retroid handhelds can continue to manage Wi-Fi/Bluetooth directly; other supported devices use the Compatibility Helper as before.
- Existing SleepManager settings are preserved when installing over an earlier version with the same official release signature.
- No root, Shizuku or ADB is required for normal use.

## Install or Update

Download **SleepManager-0.7.3.apk** from the release assets below and install it over your existing SleepManager version, or update from **About → Updates** once the release is published.

Only install **SleepManager-Helper-1.1.7.apk** if your device needs the Compatibility Helper and does not already have the current version. Your existing Helper does not need to be reinstalled for this release.
