# SleepManager

SleepManager is an Android sleep and wake manager for gaming handhelds, phones and tablets designed to reduce unnecessary standby battery drain by managing Wi-Fi, Bluetooth, Syncthing-Fork, BasicSync, Tailscale and other background integrations while the device sleeps.

It restores only the state it actually changed when the device wakes, with extra closed-lid protection and dock controls for the **AYN Thor**.

**No root, Shizuku or ADB is required for normal use.**

## Why SleepManager?

Android gaming handhelds can stay asleep for hours or days between sessions. SleepManager helps reduce unnecessary standby activity by temporarily disabling selected radios and services during sleep, then restoring only the state it changed when the device wakes.

It is designed for devices such as **Retroid**, **Odin**, **AYN Thor** and other Android handhelds, while also working on Android phones and tablets.

## Features

### Sleep actions

SleepManager can manage these actions when the screen turns off:

- **Wi-Fi** — turn it off during sleep and restore it only if SleepManager changed it.
- **Bluetooth** — same state-aware behavior as Wi-Fi.
- **Syncthing-Fork** — send STOP during sleep and FOLLOW again on wake after Helper-managed radio restoration; Syncthing-Fork then handles network reconnection itself.
- **Tailscale** — disconnect during sleep and reconnect only when SleepManager verified that it disconnected it.
- **JamesDSP** — apply OFF during sleep and ON again after wake.
- **BasicSync** — with BasicSync 3.18+, observe the current mode/run state while awake, stop it only when active, then restore the exact previous mode on wake.

### Sleep rules

- Immediate, 5-second or 10-second grace period
- Custom delay of 1, 5, 10 or 30 minutes
- Optional battery threshold
- Optional `Not charging` condition
- Optional Android Battery Saver condition
- Optional schedule / time window
- Enabled conditions use **AND logic**

### Advanced sync

SleepManager 0.6 adds two optional completion-aware sync modes:

- **Periodic sync while sleeping** — periodically bring back Helper-managed Wi-Fi when needed, wait for a usable network, sync, stop the client, then return to the previous sleep state.
- **Sync then stop on sleep & wake** — sync before sleep and after wake, then stop the client again once synchronization has completed.

These modes require a sync client that exposes reliable synchronization state. **BasicSync 3.19+** is currently supported.

### Battery statistics

SleepManager records sleep-session information such as:

- battery change
- sleep duration
- drain per hour
- measured mAh when Android exposes a charge counter
- deep-sleep percentage
- 7-day drain averages
- estimated standby time
- best / worst measured drain
- a more precise current battery percentage when Android exposes charge-counter and full-charge data
- learned full-charge capacity when available, with design capacity and level-based fallbacks

To keep long-term estimates meaningful, only **eligible sleep sessions of at least 3 hours** are used for battery statistics and standby estimates.

Shorter sleeps are still shown in **Last sleep** and remain part of the recent session history. Sessions containing charging are excluded from drain statistics.

### Activity log and diagnostics

The Activity page shows recent sleep/wake actions and provides a copyable diagnostic report.

On Android 11+, diagnostics also include recent Android process-exit history, including system exit reasons such as low-memory kills, crashes, ANRs and user/system-requested stops, plus sampled process memory information when Android provides it.

Wi-Fi diagnostics distinguish between:

- Wi-Fi already in the requested state
- a successful Wi-Fi change
- a Wi-Fi toggle attempt that failed
- failure while **Airplane mode is enabled**

This makes it easier to tell the difference between “nothing needed to change” and “SleepManager tried, but Android did not allow the change”.

### Built-in updater

SleepManager can check both the main app and the optional Helper for stable updates from **About → Updates**.

With **Automatic update checks** enabled, SleepManager also refreshes Main + Helper release metadata whenever the app enters the foreground, while keeping the existing daily background check. Foreground checks are deduplicated and wait briefly for validated connectivity without polling or holding a wake lock.

If the Helper is not installed, SleepManager can download and install the signed Helper directly from the app. If it is already installed, SleepManager offers a Helper update only when a newer Helper version is published.

For a direct install or update, SleepManager verifies:

- SHA-256
- package name
- version metadata
- the permanent SleepManager signing certificate

The verified APK is then handed to Android's official package installer.

### Appearance

SleepManager uses its own fixed light/dark palette by default.

On Android 12 / API 31 or newer, **Use system colors** can be enabled to use Material You dynamic colors instead. Active integration states such as **Running**, **Starting** and **Connected** use the same highlighted status treatment throughout the app.

## AYN Thor

SleepManager includes optional Thor-specific behavior when the Thor lid sensor is detected.

### Closed-lid protection

If the Thor wakes unexpectedly while the lid is still closed, SleepManager returns it to sleep instead of running the normal wake sequence.

During a blocked closed-lid wake, SleepManager does not prematurely restore managed radios or integrations.

Android Device Admin permission is required only for the `lockNow()` action used by this protection.

### Dock-safe behavior

Closed-lid protection is aware of an attached external display.

When an external display is active and the Thor lid is closed, SleepManager treats this as intentional docked use and does **not** force the device back to sleep.

Two optional controls are available:

- **Sleep when external display disconnects** — if enabled, disconnecting the external display while the lid is still closed starts a normal SleepManager sleep cycle. If disabled, the Thor keeps AYN's default awake behavior.
- **Power button sleeps with lid closed** — when the Thor is awake with the lid closed, pressing Power can start a normal sleep cycle, whether still docked or after the external display has been disconnected. Turning this option off keeps AYN's default behavior.

SleepManager never wakes an already sleeping Thor to implement these options.

## Installation

### 1. Install SleepManager

Install the latest:

`SleepManager-<version>.apk`

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

Open SleepManager and use **Install Helper**. SleepManager downloads, verifies and hands the signed Helper APK to Android's package installer.

You can also install `SleepManager-Helper-<version>.apk` manually from the GitHub release if needed.

The Helper has no launcher icon or separate UI.

### 3. Configure SleepManager

Open SleepManager, choose what should be managed during sleep, configure any optional rules, then enable SleepManager.

## Optional integrations

### Syncthing-Fork

SleepManager detects supported Syncthing-Fork builds automatically.

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable Syncthing inside SleepManager.

Sleep/wake STOP/FOLLOW control is supported. The new completion-aware maintenance modes remain unavailable for Syncthing-Fork until it exposes a supported synchronization-completion API.

### Tailscale

Install and sign in to the official Tailscale Android app, then enable Tailscale inside SleepManager.

### BasicSync

SleepManager uses BasicSync's official Android remote-control and state broadcasts.

In BasicSync, enable **Allow remote control**.

With **BasicSync 3.18 or newer**, SleepManager observes BasicSync's mode and run state while the SleepManager service is active. This preserves the state from before screen-off instead of trying to discover it after BasicSync may already have reacted to sleep.

With **BasicSync 3.19 or newer**, SleepManager can also use BasicSync's official folder/device counters for completion-aware maintenance. SleepManager treats errors, blocked states and incomplete observations conservatively, and requires a stable completed state before stopping BasicSync.

The Integrations page also shows the latest observed BasicSync state, such as **Auto mode · Running** or **Manual mode · Stopped**.

Sleep behavior is state-aware:

- **AUTO mode + active** → STOP during sleep → restore **AUTO mode** on wake
- **Manual mode + started** → STOP during sleep → restore **started manual mode** on wake
- **Manual mode + stopped** → leave BasicSync untouched
- Already inactive/transitional states are left untouched when there is nothing useful to stop

If no reliable pre-sleep state has been observed yet, SleepManager leaves BasicSync unchanged rather than guessing. If **Allow remote control** is disabled, BasicSync ignores the remote-control/state requests and SleepManager likewise leaves it untouched.

Older BasicSync versions remain supported with the legacy policy:

**STOP during sleep → AUTO mode after wake**

### JamesDSP

SleepManager supports the commonly used O2P JamesDSP Manager package and the standard RootlessJamesDSP package when available.

JamesDSP does not expose a reliable public state-query API to normal third-party apps. For that reason, enabling this integration is an explicit policy:

**OFF during sleep → ON after wake**

If you prefer to keep JamesDSP manually disabled while awake, leave its SleepManager integration disabled.

## How sleep / wake restoration works

SleepManager tries to restore only state that it actually changed.

For example:

- if Wi-Fi was already OFF before sleep, SleepManager leaves it OFF on wake
- if Wi-Fi was ON and SleepManager successfully turned it OFF, it is restored
- Tailscale waits for usable connectivity before restoration; Syncthing-Fork FOLLOW is sent after managed radio restoration so Syncthing can handle its own reconnect timing
- pending restore state is stored so a process/service restart does not silently lose track of it

This state-aware model is used to avoid forcing unrelated user state.

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Optional Helper target SDK: **28**
- Main package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- Extra AYN Thor controls appear only when the Thor lid sensor is detected

The Helper intentionally targets API 28 because modern Android target-SDK restrictions prevent a normal current-target app from directly toggling Wi-Fi/Bluetooth in the way SleepManager needs.

Main ↔ Helper communication is protected by a signature-level permission, and official APKs use the same permanent signing certificate.

## Updates and app identity

Official releases keep the same Android package IDs and signing identity so they can be installed as normal updates without losing settings.

Install the main APK first, then update/install the Helper if you use Wi-Fi or Bluetooth management.

Android may ask once for:

- notification permission
- **Install unknown apps** permission for direct APK updates initiated from SleepManager

## Troubleshooting

### Wi-Fi or Bluetooth does not change

Make sure the **SleepManager Helper** is installed and matches the official SleepManager signing identity.

If Wi-Fi remains unchanged, check the Activity log. SleepManager reports failed toggle attempts separately and includes the current Airplane-mode state in diagnostics.

### Syncthing does not pause or resume

Make sure:

- a supported Syncthing-Fork build is detected
- **Service Control by Broadcast** is enabled
- the correct Syncthing target is selected if several supported builds are installed

### Tailscale does not reconnect

Open Tailscale and confirm it is signed in and able to connect normally. SleepManager only restores Tailscale when it verified that SleepManager itself disconnected it.

### JamesDSP does not return to a manually disabled state

When JamesDSP management is enabled, SleepManager intentionally applies **OFF on sleep / ON on wake** because JamesDSP does not expose a readable public power state.

### Thor closed-lid protection cannot be enabled

The Thor-specific controls are shown only when SleepManager detects the Thor Hall sensor.

Closed-lid protection also requires Android Device Admin permission for the return-to-sleep action.

## Build from source

Requirements:

- JDK 17+
- Android SDK 36
- Android build-tools 36.0.0

On macOS:

```bash
chmod +x bootstrap_and_build.sh
./bootstrap_and_build.sh
```

Generated debug APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
helper/build/outputs/apk/debug/helper-debug.apk
```

## Releases

See:

- [CHANGELOG.md](CHANGELOG.md) — version history
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — notes for the current release
- [GitHub Releases](https://github.com/Baggio94/SleepManager/releases) — official APK downloads

