# SleepManager

SleepManager is a lightweight Android app for handhelds, phones, and tablets that manages selected actions when the screen turns off and restores them when the device wakes.

Set it once, choose what should sleep, and let it run in the background.

## What it can do

- **Wi-Fi** — turn it off during sleep and restore it only if SleepManager changed it.
- **Bluetooth** — same behavior as Wi-Fi.
- **Syncthing-Fork** — pause it during sleep and resume it after the network is ready again.
- **Tailscale** — disconnects Tailscale during sleep if it is connected, then reconnects it on wake.
- **AYN Thor closed-lid protection** — protects against unwanted wake-ups while the lid is still closed.
- **Grace period** — Immediate, 5 seconds, 10 seconds, or a longer custom delay.
- **Advanced conditions** — run sleep actions only when your enabled conditions are all true.
- **Activity log** — see recent sleep/wake actions and copy a diagnostic log when needed.
- **Quick Settings tile** — quickly enable or disable SleepManager.

SleepManager does not require root, Shizuku or ADB on the device.

## AYN Thor closed-lid protection

This is an optional feature made specifically for the **AYN Thor**.

Thor owners have repeatedly reported cases where the device wakes or stays awake while the lid is closed. AYN has already addressed parts of this behavior in firmware—for example, OTA 1.0.0.293 added options to disable the power button while closed and to prevent wake on charging-cable plug-in—but community reports of closed-lid wake/sleep issues have continued.

SleepManager does not modify the Thor firmware. Instead, it watches the Thor lid sensor.

**If the wake bug happens while the lid is still closed, SleepManager puts the Thor back to sleep.**

During that false wake it also avoids the normal wake sequence, so it does not restore Wi-Fi/Bluetooth or resume integrations until the lid is actually opened.

This matters because an unnoticed wake inside a case or bag can lead to unnecessary battery drain and heat.

Community / firmware references:

- [AYN Thor OTA changelog archive](https://github.com/ChimeraGaming/AYN-OTA-Changelogs/blob/main/Thor.md)
- [r/AynThor — Auto Wake Issues](https://www.reddit.com/r/AynThor/comments/1r3qxei/auto_wake_issues/)
- [r/AynThor — Screen not turning off when closing device](https://www.reddit.com/r/AynThor/comments/1s7gy0h/screen_not_turning_off_when_closing_device/)
- [r/AynThor — Thor Keeps Waking Up](https://www.reddit.com/r/AynThor/comments/1w93d8l/thor_keeps_waking_up/)

Enabling this feature asks for Android Device Admin permission. SleepManager uses it only to return the Thor to sleep when this protection is active.

## Installation

Install:

1. **SleepManager**
2. **SleepManager Helper** if you want SleepManager to control Wi-Fi or Bluetooth

Then open SleepManager, choose the actions you want, and enable it.

The Helper has no launcher icon or separate UI.

### Syncthing-Fork

SleepManager detects supported Syncthing-Fork builds automatically.

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

SleepManager also shows the current Syncthing state in the app when it can verify it.

### Tailscale

Install the official Tailscale Android app and sign in normally.

SleepManager shows the current Tailscale connection state. If Tailscale is connected when the device sleeps, SleepManager can disconnect it and later reconnect it only when that change was verified.

## Sleep behavior

The default setup is simple:

- **Grace period:** Immediate
- **Custom delay:** Off
- **Advanced conditions:** Off

When the screen turns off, SleepManager:

1. waits for the selected grace/custom delay
2. checks any enabled Advanced conditions
3. applies only the selected sleep actions
4. remembers what it actually changed

When the device wakes, SleepManager restores only those changes.

If Wi-Fi, Bluetooth, Syncthing or Tailscale was already in the desired sleep state, SleepManager does not force a different state on wake.

## Advanced conditions

Advanced conditions use **AND logic**.

If several conditions are enabled, every one of them must be true before sleep actions run.

Current options include:

- battery below a selected percentage
- device is not charging
- Android Battery Saver is ON or OFF
- schedule / time window
- custom sleep delay: 1, 5, 10 or 30 minutes

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Optional compatibility Helper for Wi-Fi/Bluetooth
- Extra AYN Thor protection when the Thor lid sensor is detected

## Updates

Official builds keep the same package IDs and signing certificate so they can be installed as normal updates:

- Main app: `com.med.sleepmanager`
- Helper: `com.med.sleepmanager.helper`

Install the main app first, then the Helper.

## Troubleshooting

### Wi-Fi or Bluetooth does not change

Make sure the **SleepManager Helper** is installed.

### Syncthing does not pause or resume

Make sure:

- Syncthing-Fork is detected in SleepManager
- **Service Control by Broadcast** is enabled in Syncthing-Fork
- the correct Syncthing target is selected if several builds are installed

### Tailscale does not reconnect

Open Tailscale and make sure it is signed in and able to connect normally. SleepManager only restores Tailscale when it verified that SleepManager itself disconnected it.

### Thor protection cannot be enabled

The option is shown only when SleepManager detects the AYN Thor lid sensor. Android Device Admin permission is required for the return-to-sleep action.

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

See [CHANGELOG.md](CHANGELOG.md) for the full history and [RELEASE_NOTES.md](RELEASE_NOTES.md) for the current release notes.
