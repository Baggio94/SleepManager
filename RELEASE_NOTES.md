# SleepManager 0.5.1

SleepManager 0.5.1 brings a secure built-in updater, better background reliability on Android handhelds, new battery insights, and several important improvements introduced with 0.5.0.

## What's new in 0.5.1

### Built-in secure updater
SleepManager can now check for new stable releases directly from the app.

When an update is available, SleepManager can:
- notify you
- download the APK directly
- verify its SHA-256
- verify the package, version and official SleepManager signing certificate
- open Android's official installer

No root, Shizuku or ADB is required.

### Better background reliability
- SleepManager is hidden from Android Recents to avoid accidental service termination on devices such as the AYN Thor.
- Permission screens, App info, GitHub links and update screens now work without closing the app's background task.
- The service restarts automatically after reboot or app update when SleepManager is enabled.

### Improved battery stats
The battery gauge can now cycle through:
- battery capacity
- estimated standby time
- sleep drain per hour
- deep-sleep percentage

### New notification icon
The generic Android alarm icon has been replaced with a dedicated SleepManager status-bar icon.

## Also included from 0.5.0

### JamesDSP support
SleepManager can automatically turn JamesDSP **OFF during sleep** and **ON again after wake**.

### Sleep battery tracking
SleepManager records:
- battery drain during sleep
- sleep duration
- drain per hour
- 7-day averages
- measured mAh when supported
- deep-sleep percentage
- standby estimates

Charging sessions are excluded from drain averages.

### AYN Thor closed-lid protection
SleepManager monitors the Thor Hall sensor directly.

If the Thor wakes while the lid is still closed, SleepManager sends it back to sleep and prevents the normal wake sequence from restoring Wi-Fi, Bluetooth or other integrations too early.

### More reliable sleep / wake handling
- More durable sleep transactions
- Better recovery after process/service restarts
- Improved Syncthing-Fork STOP/FOLLOW handling
- Better responsive layouts for handheld and tablet screens

## Main features

SleepManager can manage during sleep:
- Wi-Fi
- Bluetooth
- Syncthing-Fork
- Tailscale
- JamesDSP

It also includes:
- AYN Thor closed-lid protection
- Grace period and custom sleep delay
- Advanced battery / charging / Battery Saver / schedule conditions
- Sleep battery statistics
- Activity log and diagnostics
- Quick Settings tile

## Compatibility

- Android 9 / API 28 or newer
- Main app package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- No root, Shizuku or ADB required for normal use

---

## Installation

### 1. Install SleepManager
Download and install:

**SleepManager-0.5.1.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control
Install:

**SleepManager-Helper-0.5.1.apk**

The Helper has no launcher icon and no separate interface. It is only used by SleepManager to control Wi-Fi and Bluetooth.

### 3. Open SleepManager
Choose what you want SleepManager to manage when the device sleeps, then enable SleepManager.

### 4. Optional integrations

**Syncthing-Fork**

In Syncthing-Fork enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable Syncthing inside SleepManager.

**Tailscale**

Install and sign in to the official Tailscale Android app, then enable Tailscale inside SleepManager.

**JamesDSP**

Install a supported JamesDSP build, then enable JamesDSP inside SleepManager.

**AYN Thor closed-lid protection**

Enable **AYN Thor closed-lid protection** in SleepManager and approve the Android Device Admin permission when prompted.

### 5. Finish setup
Once your options are configured and SleepManager is enabled, tap **Finish setup**.

SleepManager will then continue running in the background and automatically handle your selected sleep / wake actions.
