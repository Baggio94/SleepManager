# SleepManager 0.5.5

SleepManager 0.5.5 includes all 0.5.4 improvements and adds an immediate Main + Helper update check whenever the app enters the foreground.

## What's new

### Update checks when the app opens

With **Automatic update checks** enabled, SleepManager now checks the stable release metadata for both the main app and the installed Helper whenever the app enters the foreground.

- Opening SleepManager or returning to it from the launcher triggers a fresh check.
- Rotation or window resizing does not start duplicate checks.
- The existing daily background check remains enabled.
- If network connectivity is still recovering, SleepManager waits briefly for a validated connection instead of polling or waking the device.
- Concurrent foreground, manual and background checks are deduplicated.
- A failed background check no longer suppresses foreground checks for 24 hours.
- The check only reads release metadata; APKs are downloaded only after you choose **Update**.

### Independent Helper updates

The optional SleepManager Helper now has its own release version and versionCode.

- The Helper is bumped only when the Helper itself changes.
- SleepManager 0.5.4 ships **Helper 1.0.0 / versionCode 1000** unchanged.
- If the Helper is installed and a newer Helper is published, SleepManager can notify and update it independently from the main app.
- If the Helper is not installed, SleepManager offers **Install Helper** instead of showing an update notification.
- If both SleepManager and the Helper need updates, Home and notifications present them together.
- Main-only and Helper-only updates remain independent.

Direct Helper installs and updates use the same security model as the main app. SleepManager verifies the trusted release source, SHA-256, package ID, version metadata and permanent signing certificate before handing the APK to Android's package installer.

### BasicSync compatibility and setup

BasicSync 3.18+ state-aware support is now detected using Android's **versionCode** rather than parsing the visible version name.

This makes the 3.18+ API check robust even if a BasicSync build uses a different version-name format.

Quick setup also shows whether BasicSync is detected and displays its installed version.

### Setup and integration polish

- Enabling Syncthing-Fork now reminds you to enable **Settings → Behaviour → Service control by broadcast** in Syncthing-Fork.
- Quick setup explicitly guides new users to choose their actions, enable SleepManager, then tap **Finish setup**.
- Active action/integration icon circles now use the same primary color as active switches and automatically follow Material You when system colors are enabled.

### Battery statistics consistency

Long-term battery averages and standby estimates already use eligible non-charging sleep sessions of at least **3 hours**.

The remaining UI thresholds and explanatory text now use that same 3-hour source of truth, so the interface and statistics engine are fully consistent.

### Updater return-state fix

After Android's package installer is opened, temporary messages such as **APK verified. Opening Android installer…** are now cleared correctly when returning to SleepManager.

## Main features

SleepManager can manage during sleep:

- Wi-Fi
- Bluetooth
- Syncthing-Fork
- Tailscale
- JamesDSP
- BasicSync

It also includes:

- AYN Thor closed-lid protection and dock controls
- Grace period and custom sleep delay
- Advanced battery / charging / Battery Saver / schedule conditions
- Sleep battery statistics
- Activity log and diagnostics
- Quick Settings tile
- Secure built-in updater

## Compatibility

- Android 9 / API 28 or newer
- Main app package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- BasicSync 3.18+ recommended for state-aware restore
- No root, Shizuku or ADB required for normal use

## Installation

### 1. Install SleepManager

Download and install:

**SleepManager-0.5.5.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

From SleepManager, open:

**About → Updates → Install Helper**

or install the release asset manually:

**SleepManager-Helper-1.0.0.apk**

The Helper has no launcher icon or separate interface.

### 3. Configure SleepManager

Open SleepManager, choose the actions you want, enable SleepManager, then tap **Finish setup**.

For Syncthing-Fork, enable **Settings → Behaviour → Service control by broadcast**.

For BasicSync, enable **Allow remote control**.

## Updating from 0.5.4

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

Official releases keep the same package IDs and permanent signing certificate, so normal updates preserve existing settings.
