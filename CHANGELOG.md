# Changelog

Release notes are organized by version and focus on user-visible behavior first.

## 0.4.0-dev1 — 2026-09-18 — Preview

### Highlights

- New modern UI with permanent side navigation, clearer states, Activity log and expanded About page.
- Advanced sleep rules: grace period, custom delay, battery, charging, Battery Saver and schedule conditions.
- Tailscale integration with state-aware disconnect/reconnect.
- Syncthing-Fork current state detection and network-ready restore.
- Persistent sleep/wake transactions that restore only what SleepManager actually changed.
- Quick Settings tile.
- Standard Android haptic and click feedback throughout the UI.
- AYN Thor closed-lid protection remains active: if the Thor wakes while the lid is still closed, SleepManager puts it back to sleep instead of running the normal wake sequence.

### Notes

- Fresh installs default to **Immediate** grace period, **Custom delay OFF**, sleep actions OFF and Advanced conditions OFF.
- Advanced conditions use **AND logic**.
- This is a development preview. Final regression testing on emulator and AYN Thor is still in progress before stable 0.4.0.

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for the user-facing release summary.

---

## 0.3.2 — 2026-09-18

### Highlights

- More reliable Syncthing shutdown before managed radios are disabled.
- AYN Thor closed-lid false wakes remain blocked without restoring connectivity.
- Wi-Fi and Bluetooth restore only the state SleepManager actually changed.
- Complete Android launcher icon support, including Android 13+ themed icons and legacy fallbacks.
- Stable build identity and signing guidance for seamless APK updates.
- README and release notes reorganized for clarity.

### Fixed

#### Syncthing sleep sequence

SleepManager now sends Syncthing `STOP` before disabling managed radios.

When Syncthing `STOP` is sent and Wi-Fi or Bluetooth also needs to be disabled:

1. `STOP` is sent immediately.
2. SleepManager waits **1 second**.
3. The Helper applies the Wi-Fi/Bluetooth sleep action.

A temporary one-shot partial wake lock keeps the CPU alive during this short transition and has a safety timeout. It is released as soon as the Helper completes the sleep action, when the transition is cancelled by a real wake, or when the service stops.

This prevents the network from being removed so quickly that Syncthing cannot complete its remote disconnect cleanly.

#### AYN Thor false wakes

If Android reports `SCREEN_ON` while the Thor Hall sensor still reports `SW_LID = CLOSED`:

- Wi-Fi/Bluetooth are not restored.
- Syncthing `FOLLOW` is not scheduled or sent.
- SleepManager calls `lockNow()`.
- The resulting duplicate `SCREEN_OFF` does not run the sleep actions a second time.

Repeated closed-lid false wakes remain protected.

### Connectivity behavior

Wi-Fi and Bluetooth remain state-aware.

SleepManager records whether each radio was already on and whether SleepManager actually changed it. On wake, it restores only the radios it changed.

Validated cases include:

- Wi-Fi ON / Bluetooth ON -> both are restored.
- Wi-Fi ON / Bluetooth OFF -> only Wi-Fi is restored.
- Wi-Fi OFF / Bluetooth ON -> only Bluetooth is restored.
- Radios that were already off are never treated as app-managed changes.

### Syncthing wake behavior

On a normal wake, managed connectivity is restored first and Syncthing `FOLLOW` is currently scheduled **2.5 seconds** later.

### Launcher icon

The 0.3.2 icon setup includes:

- adaptive icon foreground/background
- Android 13+ monochrome/themed icon
- round icon support
- legacy `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` launcher fallbacks
- separate store icon source

### Build and update consistency

The project keeps stable Android application IDs:

- `com.med.sleepmanager`
- `com.med.sleepmanager.helper`

Version metadata is centralized so the main app and Helper cannot drift accidentally.

The build documentation now makes the Android update requirements explicit:

- same application ID
- same signing certificate
- compatible/increasing `versionCode`

The main app and Helper must use the same signer because the Helper control permission is signature-protected.

### Documentation

- README reorganized into features, installation, setup, architecture, update identity, troubleshooting, and roadmap.
- Syncthing requirements are easier to find.
- AYN Thor behavior is documented separately.
- Build signing and update compatibility are documented explicitly.

### Known limitation

Syncthing wake currently uses a fixed **2.5-second** delay before `FOLLOW`.

A future release may replace this with a network-ready check so `FOLLOW` is sent when connectivity is actually usable rather than after a fixed timer.

---

## 0.3.1

### Highlights

- Event-driven foreground sleep/wake service.
- Wi-Fi and Bluetooth sleep/wake management through the compatibility Helper.
- Syncthing-Fork `STOP` / `FOLLOW` integration.
- AYN Thor Hall-sensor closed-lid protection.
- Duplicate sleep-event protection.
- State-aware radio restoration.
- Device Admin used only for Thor `lockNow()` protection.

### Notes

0.3.1 is the baseline from which the 0.3.2 reliability and polish work was developed.
