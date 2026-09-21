# SleepManager 0.5.2

SleepManager 0.5.2 adds better AYN Thor dock behavior, more reliable battery statistics, and clearer Wi-Fi diagnostics.

## What's new

### Better AYN Thor dock support

Closed-lid protection is now dock-aware.

- Closing the Thor while an external display is active no longer triggers a false sleep.
- **Sleep when external display disconnects** can put the Thor to sleep automatically if the external display is unplugged while the lid is still closed.
- **Power button sleeps with lid closed** lets the Power button start a normal sleep cycle while the Thor is awake with the lid closed.
- Turning these optional controls off keeps AYN's default behavior.
- SleepManager never wakes an already sleeping Thor to implement these options.

### More reliable battery statistics

Short sleeps can distort hourly drain estimates, so SleepManager now uses only eligible sleep sessions of **3 hours or longer** for:

- 7-day drain average
- charge drain
- deep-sleep average
- measured sleep total
- best / worst drain
- standby estimates

Shorter sleeps are still visible in **Last sleep** and history.

Sessions containing charging remain excluded from drain statistics.

### Clearer Wi-Fi failure diagnostics

SleepManager can now distinguish between Wi-Fi already being in the requested state and a Wi-Fi toggle that was actually attempted but failed.

The Activity log and copied diagnostics now include:

- attempted action
- success / failure
- Airplane-mode state

When relevant, the Activity log can show:

**Wi-Fi toggle failed · Airplane mode is enabled**

## Main features

SleepManager can manage during sleep:

- Wi-Fi
- Bluetooth
- Syncthing-Fork
- Tailscale
- JamesDSP

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
- No root, Shizuku or ADB required for normal use

## Installation

### 1. Install SleepManager

Download and install:

**SleepManager-0.5.2.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

Install:

**SleepManager-Helper-0.5.2.apk**

The Helper has no launcher icon or separate interface.

### 3. Configure SleepManager

Open SleepManager, choose what should be managed during sleep, configure any optional rules, then enable SleepManager.

## Updating from 0.5.1

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

The built-in updater verifies the downloaded APK before handing it to Android's official installer.

Official releases keep the same package IDs and permanent signing certificate, so normal updates preserve existing settings.
