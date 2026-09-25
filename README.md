# SleepManager

SleepManager helps Android handhelds, phones and tablets use less battery while they sleep.

It can temporarily disable selected radios and background services when the screen turns off, then restore only the state it actually changed when the device wakes. It also includes sleep battery statistics, sync automation and dedicated closed-lid / dock features for the **AYN Thor**.

**No root, Shizuku or ADB is required for normal use.**

## At a glance

SleepManager can:

- turn **Wi-Fi** and **Bluetooth** off during sleep and restore them safely on wake
- pause and resume **Syncthing-Fork**
- manage **BasicSync**, **Tailscale** and **JamesDSP**
- run optional **sync-before-sleep**, **sync-after-wake** and **periodic sleep sync** workflows with supported providers
- track sleep drain, measured mAh, deep sleep and standby estimates
- protect the **AYN Thor** from closed-lid false wakes
- handle Thor dock disconnects and power-button sleep behavior
- keep recent activity and diagnostics for troubleshooting
- check and install signed stable updates from inside the app

SleepManager is designed primarily for Android gaming handhelds such as **AYN Thor**, **AYN Odin** and **Retroid** devices, but it also works on regular Android phones and tablets.

## Quick start

1. Install the latest **SleepManager** APK.
2. If you want SleepManager to control Wi-Fi or Bluetooth, install the optional **SleepManager Helper**.
3. Open SleepManager and choose what should happen when the device sleeps.
4. Enable SleepManager, then tap **Finish setup**.

That is enough for the basic sleep / wake automation.

Optional integrations such as Syncthing-Fork and BasicSync need one setting enabled inside those apps; see [Integrations](#integrations).

## How SleepManager behaves

The main rule is simple:

> **Restore only what SleepManager changed.**

Examples:

- Wi-Fi was already OFF before sleep → it stays OFF after wake.
- Wi-Fi was ON and SleepManager turned it OFF → it is restored on wake.
- Tailscale is restored only if SleepManager verified that it disconnected it.
- BasicSync 3.18+ restores the previous Auto / Manual state instead of forcing a new one.
- Pending restore state is saved so a process or service restart does not silently forget it.

JamesDSP is the main exception: it does not expose a reliable public power-state query, so enabling its integration explicitly means **OFF during sleep → ON after wake**.

## Sleep actions

### Wi-Fi and Bluetooth

With the optional Helper installed, SleepManager can turn Wi-Fi and Bluetooth off while the device sleeps and restore only the radios it changed.

The Helper has no launcher icon or separate interface. It runs only when SleepManager asks it to perform a supported action.

### Syncthing-Fork

SleepManager can send Syncthing-Fork:

- **STOP** before managed sleep networking is removed
- **FOLLOW** after wake and radio restoration

On wake, FOLLOW is sent after managed radio restoration without waiting for Android's internet-validation delay; Syncthing-Fork handles its own reconnection from there.

### BasicSync

SleepManager uses BasicSync's official Android remote-control and state broadcasts.

With **BasicSync 3.18+**, SleepManager can preserve its previous run mode across sleep:

- **Auto mode + active** → stop for sleep → restore Auto mode
- **Manual mode + started** → stop for sleep → restore started Manual mode
- **Manual mode + stopped** → leave it untouched

With **BasicSync 3.19+**, SleepManager can also read official folder/device synchronization counters and use the completion-aware sync features described below.

### Tailscale

SleepManager can disconnect the official Tailscale Android app during sleep and reconnect it only when SleepManager verified that it performed the disconnect.

### JamesDSP

Supported JamesDSP builds can be powered off for sleep and powered on again after wake.

Because JamesDSP does not provide a readable public power state, this integration is an explicit **OFF asleep / ON awake** policy.

## Sleep rules

Sleep actions can be limited with optional conditions:

- grace period: Immediate, 5 seconds or 10 seconds
- custom delay: 1, 5, 10 or 30 minutes
- battery threshold
- **Not charging**
- Android Battery Saver state
- schedule / time window

When several conditions are enabled, they use **AND logic**: all enabled conditions must match.

## Advanced sync

SleepManager 0.6 adds two optional completion-aware workflows.

### Periodic sync while sleeping

During a long sleep session, SleepManager can periodically:

1. temporarily restore Helper-managed Wi-Fi when needed
2. wait for usable connectivity
3. start the supported sync client
4. wait until synchronization is complete
5. stop the client
6. return Wi-Fi to the sleep state

The next maintenance attempt uses a one-shot 24-hour alarm. There is no permanent maintenance polling.

### Sync then stop on sleep & wake

SleepManager can also sync immediately before sleep and after a real wake, then stop the client again once synchronization has completed.

This is useful when you want a sync client available only for short, controlled synchronization windows.

### Current provider support

Completion-aware sync currently requires **BasicSync 3.19+**.

Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported, but the advanced completion-aware workflows stay unavailable until Syncthing-Fork exposes a supported synchronization-completion API.

## Battery statistics

SleepManager records sleep-session information including:

- battery change
- sleep duration
- drain per hour
- measured mAh when Android exposes a usable charge counter
- deep-sleep percentage
- 7-day drain average
- best / worst measured drain
- estimated standby time
- more precise current battery percentage when the required Android battery data is available
- learned full-charge capacity when available, with design/fallback estimates otherwise

### Short vs long sessions

**Last sleep** can show short sessions immediately.

For long-term averages and standby estimates, SleepManager only uses eligible, non-charging sleep sessions of at least **3 hours**. Shorter sessions remain visible but do not distort the long-term statistics.

Sessions that include charging are excluded from drain averages.

## Activity and diagnostics

The Activity page keeps recent SleepManager events and can generate a copyable diagnostic report.

Diagnostics include configuration and restore state, plus details that help distinguish between:

- a radio already being in the requested state
- a successful change
- a failed change
- Wi-Fi failure while Airplane mode is enabled

On **Android 11+**, diagnostics also include recent Android process-exit information when available. This can help identify low-memory kills, crashes, ANRs, user-requested stops and other reasons Android ended the SleepManager process.

## AYN Thor

SleepManager includes extra controls when the Thor lid sensor is detected.

### Closed-lid protection

If the Thor wakes unexpectedly while the lid is still closed, SleepManager can immediately return it to sleep instead of running the normal wake sequence.

During a blocked false wake, SleepManager does not prematurely restore radios or integrations.

This feature uses Android **Device Admin** only for the one-time permission required by the return-to-sleep action.

### Dock-safe behavior

Closed-lid protection understands an active external display.

When the Thor is intentionally docked with the lid closed, SleepManager does not treat that as a false wake.

Two optional Thor controls are available:

- **Sleep when external display disconnects** — disconnecting the external display while the lid is closed can start a normal SleepManager sleep cycle.
- **Power button sleeps with lid closed** — pressing Power while the Thor is awake with the lid closed can start a normal sleep cycle, including while docked.

SleepManager does not wake an already sleeping Thor just to implement these options.

## Integrations

### Syncthing-Fork setup

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable Syncthing-Fork in SleepManager.

### BasicSync setup

In BasicSync, enable:

**Allow remote control**

BasicSync 3.18+ is recommended for state-aware normal sleep/wake behavior.

BasicSync 3.19+ is required for **Periodic sync while sleeping** and **Sync then stop on sleep & wake**.

### Tailscale setup

Install and sign in to the official Tailscale Android app, then enable Tailscale in SleepManager.

### JamesDSP setup

Install a supported JamesDSP build, then enable JamesDSP in SleepManager if you want the explicit OFF-during-sleep / ON-after-wake behavior.

## Installation

### 1. Install SleepManager

Download the latest stable APK from [GitHub Releases](https://github.com/Baggio94/SleepManager/releases):

`SleepManager-<version>.apk`

Install it normally through Android.

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

Inside SleepManager, open:

**About → Updates → Install Helper**

SleepManager downloads, verifies and hands the signed Helper APK to Android's package installer.

You can also install the matching release asset manually:

`SleepManager-Helper-<version>.apk`

The Helper is optional unless you want Wi-Fi / Bluetooth control.

### 3. Configure your actions

Open SleepManager, choose the actions and optional rules you want, enable SleepManager, then tap **Finish setup**.

### 4. Configure optional integrations

If you use Syncthing-Fork or BasicSync, enable the required setting shown in the [Integrations](#integrations) section above.

### Permissions you may see

Depending on which features you use, Android may ask for:

- notification permission
- **Install unknown apps** permission when using the built-in updater
- **Device Admin** only if you enable AYN Thor closed-lid protection

**No root, Shizuku or ADB is required on the device.**

## Updates

SleepManager can check the main app and optional Helper for stable updates from:

**About → Updates**

With **Automatic update checks** enabled, it checks when the app enters the foreground and also keeps the daily background check.

Before offering a direct APK install, SleepManager verifies:

- SHA-256
- package name
- version metadata
- the permanent SleepManager signing certificate

Official releases keep the same package IDs and signing identity, so normal updates preserve existing settings.

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Optional Helper target SDK: **28**
- Main package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- AYN Thor-specific controls appear only when the Thor lid sensor is detected

The Helper intentionally targets API 28 because newer Android target-SDK restrictions prevent a normal current-target app from directly toggling Wi-Fi/Bluetooth in the way SleepManager needs.

Main ↔ Helper communication is protected by a signature-level permission, and official APKs use the same permanent signing certificate.

## Troubleshooting

### Wi-Fi or Bluetooth does not change

Make sure the **SleepManager Helper** is installed.

If Wi-Fi still does not change, check **Activity** and copy the diagnostics. SleepManager reports attempted actions, success/failure and Airplane-mode state when relevant.

### Syncthing-Fork does not pause or resume

Check that:

- a supported Syncthing-Fork build is detected
- **Settings → Behaviour → Service Control by Broadcast** is enabled in Syncthing-Fork
- the correct Syncthing target is selected if several supported builds are installed

### BasicSync does not respond

Make sure **Allow remote control** is enabled in BasicSync.

For the advanced completion-aware sync workflows, use **BasicSync 3.19+**.

### Tailscale does not reconnect

Open Tailscale and confirm that it is signed in and can connect normally. SleepManager restores it only when SleepManager verified that it performed the sleep disconnect.

### JamesDSP turns on after wake

That is the intended behavior when JamesDSP management is enabled. Because JamesDSP does not expose a reliable readable power state, the integration is explicitly **OFF during sleep → ON after wake**.

### AYN Thor closed-lid protection cannot be enabled

The Thor options appear only when SleepManager detects the Hall/lid sensor.

Closed-lid protection also needs the Android Device Admin permission used for the return-to-sleep action.

### SleepManager unexpectedly stops

On Android 11+, open **Activity → Copy diagnostics** after reopening SleepManager. The process-exit section can show why Android ended the previous process when the system provides that information.

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

## Release information

- [Latest GitHub release](https://github.com/Baggio94/SleepManager/releases/latest)
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — current release explained in detail
- [CHANGELOG.md](CHANGELOG.md) — version history
