# SleepManager 0.4.1

SleepManager 0.4.1 focuses on a simpler UI, smarter sleep rules and more reliable app integrations.

## Highlights

- **New modern UI**
  - Permanent side navigation rail for Home, Advanced, Activity log and About.
  - Cleaner setup flow and more readable current states.
  - Compact Current behavior summary.
  - Expanded About page with app details and support links.

- **Advanced sleep rules**
  - Grace period: Immediate, 5s, 10s or Custom.
  - Custom delay: 1, 5, 10 or 30 minutes.
  - Battery level condition.
  - Not charging condition.
  - Android Battery Saver condition.
  - Schedule / time window.
  - Enabled conditions are combined with AND logic.

- **Tailscale integration**
  - Detects the official Tailscale app and current connection state.
  - Disconnects Tailscale for sleep only when appropriate.
  - Reconnects it only when SleepManager verified that it disconnected it.
  - Waits for usable network connectivity before restoring.

- **Improved Syncthing-Fork integration**
  - Live RUNNING / STOPPED / UNKNOWN state in the UI.
  - Network-ready restore instead of relying only on a fixed delay.
  - Supports HTTP and local HTTPS health checks.
  - Keeps the existing STOP / FOLLOW broadcast workflow.

- **Safer sleep/wake transactions**
  - SleepManager remembers only what it changed.
  - Pending restore state survives process restarts.
  - Failed restores stay pending instead of being marked successful.

- **AYN Thor closed-lid protection**
  - Thor owners have widely reported wake/sleep problems while the lid is closed.
  - If the Thor wakes while its lid sensor still says CLOSED, SleepManager puts it back to sleep.
  - Normal wake restoration is suppressed until the lid is really opened.
  - This helps avoid unnoticed battery drain and heat if a closed Thor wakes in a case or bag.

- **Activity log and diagnostics**
  - Recent sleep/wake events are available from Activity log.
  - Copy log includes current configuration and transaction state.

- **Quick Settings tile**
  - Quickly enable or disable SleepManager from Android Quick Settings.

- **Haptic and sound feedback**
  - Buttons, options and navigation now use Android's standard haptic/click feedback.
  - Feedback follows the device's own system settings.

## Defaults

On a fresh install:

- Grace period: **Immediate**
- Custom delay: **Off**
- Sleep actions: **Off**
- Advanced conditions: **Off**

## Installation

1. Install **SleepManager**.
2. Install **SleepManager Helper** if you want Wi-Fi or Bluetooth control.
3. Open SleepManager and choose the actions you want.
4. For Syncthing-Fork, enable **Settings → Behaviour → Service Control by Broadcast**.
5. For AYN Thor protection, enable the option and grant the one-time Device Admin permission.
6. Enable **SleepManager**, then tap **Finish setup**.

No root, Shizuku or ADB is required on the device.

