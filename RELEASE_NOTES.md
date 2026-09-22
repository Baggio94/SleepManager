# SleepManager 0.5.3

SleepManager 0.5.3 adds state-aware BasicSync support, live integration status, more precise battery reporting and optional Material You colors.

## What's new

### BasicSync integration

SleepManager now supports **BasicSync** through its official Android remote-control API.

In BasicSync, enable:

**Allow remote control**

With **BasicSync 3.18 or newer**, SleepManager observes BasicSync's current mode and run state while the SleepManager service is active. This lets it preserve the state from before screen-off and restore it correctly after wake.

- **Auto mode + active** → STOP during sleep → restore **Auto mode** on wake
- **Manual mode + started** → STOP during sleep → restore **started manual mode** on wake
- **Manual mode + stopped** → left untouched
- Inactive or transitional states are left untouched when there is nothing useful to stop

The Integrations page also shows the latest observed BasicSync state, such as:

- **Auto mode · Running**
- **Manual mode · Running**
- **Manual mode · Stopped**
- **Paused / Starting / Stopping** when reported by BasicSync

If SleepManager has not yet observed a reliable pre-sleep state, it leaves BasicSync unchanged rather than guessing.

Older BasicSync versions remain supported with the legacy behavior:

**STOP during sleep → AUTO mode after wake**

### More precise battery information

When Android exposes the relevant battery counters, SleepManager can now show a more precise current battery percentage in Stats instead of being limited to the rounded Android level.

Capacity estimation now prefers the battery's learned **full-charge capacity** when available, then falls back to design capacity or the existing level-based estimate.

This does not change the existing sleep-session rules: long-term drain and standby estimates still use eligible non-charging sessions of at least **3 hours**.

### Appearance and UI polish

SleepManager now includes **Use system colors**.

- The existing SleepManager light/dark palette remains the default.
- On Android 12 / API 31 or newer, enabling **Use system colors** applies Material You dynamic colors.
- Fixed light and dark palettes were refined for closer visual consistency.
- Active integration states such as **Running**, **Starting** and **Connected** now use the same highlighted status color across integrations.
- Syncthing and BasicSync use distinct integration icons.

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

**SleepManager-0.5.3.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

Install:

**SleepManager-Helper-0.5.3.apk**

The Helper has no launcher icon or separate interface.

### 3. Configure SleepManager

Open SleepManager, choose what should be managed during sleep, configure any optional rules, then enable SleepManager.

For BasicSync, enable **Allow remote control** inside BasicSync before enabling the integration in SleepManager.

## Updating from 0.5.2

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

The built-in updater verifies the downloaded APK before handing it to Android's official package installer.

Official releases keep the same package IDs and permanent signing certificate, so normal updates preserve existing settings.
