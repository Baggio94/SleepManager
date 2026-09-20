# SleepManager 0.5.0

SleepManager 0.5.0 adds sleep battery statistics, JamesDSP integration, stronger recovery logic and a more polished responsive interface.

## Highlights

### JamesDSP sleep / wake integration

- Detects the JamesDSP Manager package used by O2P Tweaks and the standard RootlessJamesDSP package when available.
- Sends JamesDSP **OFF** when sleep actions run.
- Sends JamesDSP **ON** again on a real wake.
- Uses JamesDSP's exported power-control receiver; no root, Shizuku or ADB is required.
- JamesDSP does not expose a public power-state query to normal apps, so SleepManager does not show a guessed Running / Stopped state.
- Because the previous JamesDSP state cannot be read reliably, enabling this integration is an explicit **OFF while asleep / ON while awake** policy.

### Sleep battery dashboard and statistics

- Shows the current battery level near the top of Home.
- Tracks the last completed sleep session, duration and battery percentage used.
- Shows drain per hour and a rolling 7-day average.
- Uses Android's charge counter when available to show measured mAh usage.
- Excludes sleep sessions that included charging from drain averages.
- AYN Thor closed-lid false wakes remain part of the same sleep session instead of ending the measurement.

### Stronger AYN Thor recovery

- Queries the current Hall-switch state when closed-lid protection starts.
- Persists the last known lid state as a fallback across service recovery.
- Closed-lid false wakes continue to avoid the normal wake restore sequence.
- Grace period / Custom delay are not cancelled by a closed-lid false wake.

### More resilient sleep / wake transactions

- Service startup validates the start request before applying screen state.
- Main ↔ Helper sleep/restore requests are more idempotent and durable.
- Critical transaction state is persisted synchronously.
- Pending restore failures are surfaced on Home and can be explicitly forgotten when recovery is no longer possible.
- Disabling SleepManager first attempts to restore changes still owned by the active transaction.

### Better Syncthing-Fork verification

- When the pre-sleep running state can be confirmed, SleepManager verifies that Syncthing actually stopped after the STOP grace period.
- If STOP cannot be confirmed, SleepManager does not pretend that the stop succeeded.
- Compatible STOP/FOLLOW behavior remains available when Syncthing state cannot be queried.

### Responsive UI and polish

- Added dedicated layouts and regression checks for compact portrait, landscape and wider handheld/tablet screens.
- Home, Advanced, Stats, Activity log and About keep independent scroll positions.
- The sleep/wake test dialog scrolls correctly on short landscape displays.
- Integration state labels use sentence case and setting text spacing was refined.
- Navigation alignment and behavior-card hierarchy were polished for handheld screens.

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Compatibility Helper target SDK: **28**
- Same package IDs and permanent signing certificate as previous stable releases
- Version code **501**, so devices running the 0.5.0 beta can update normally to this stable build

## Installation / update

1. Install **SleepManager 0.5.0**.
2. Install **SleepManager Helper 0.5.0** if you use Wi-Fi or Bluetooth management.
3. Existing settings are preserved when updating an official signed build.
4. For Syncthing-Fork, keep **Settings → Behaviour → Service Control by Broadcast** enabled.
5. For AYN Thor protection, keep the one-time Device Admin permission enabled.

No root, Shizuku or ADB is required on the device.
