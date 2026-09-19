# SleepManager 0.5.0-beta1

This beta focuses on battery visibility and resilience around sleep/wake recovery.

## Highlights

- **Sleep battery dashboard**
  - Shows the current battery level near the top of Home.
  - Tracks the last completed sleep session, its duration and battery percentage used.
  - Shows sleep drain per hour and a rolling 7-day average.
  - Uses Android's charge counter when the device exposes it, so SleepManager can also show measured mAh used.
  - Sleep sessions that include charging are excluded from the drain average.
  - AYN Thor closed-lid false wakes stay inside the same sleep session instead of ending the measurement.

- **Stronger AYN Thor closed-lid recovery**
  - SleepManager now queries the current Hall-switch state when protection starts instead of relying only on future lid events.
  - The last known lid state is also persisted as a safe fallback when the service restarts while the screen is already off.
  - Closed-lid false wakes still do not cancel Grace period / Custom delay and still do not run the normal wake restore sequence.

- **Safer service startup**
  - The service no longer applies the current screen state from `onCreate()`.
  - Startup actions are validated first in `onStartCommand()`, avoiding a theoretical new sleep cycle during a disable/restore launch.

- **More resilient Main ↔ Helper protocol**
  - The Helper now returns a result for duplicate sleep requests and restore requests with no active Helper cycle.
  - Critical Helper transaction state uses synchronous persistence.
  - Restore mismatches are surfaced instead of silently waiting forever.

- **Pending restore recovery**
  - When a restore cannot complete, Home can show a clear pending-restore warning.
  - A manual **Forget pending restore** action is available for transactions that can no longer be recovered.

- **Improved Syncthing-Fork verification**
  - When SleepManager can confirm Syncthing is running before STOP, it checks again after the STOP grace period.
  - If Syncthing is still running, SleepManager does not pretend that STOP succeeded.
  - When Syncthing state cannot be verified, the existing compatible STOP/FOLLOW behavior is preserved.

- **UI / maintenance improvements**
  - Compatibility Helper setup now includes a direct **Get Helper** action.
  - Switches have clearer accessibility semantics.
  - Visible status polling is reduced from every 1 second to every 3 seconds.
  - First automated regression tests cover the Thor closed-lid + Grace period wake path.

## Beta notes

This is a preview build for real-device testing before a future stable 0.5.x release.

The main application ID, Helper application ID and signing certificate remain unchanged, so this beta can update an existing SleepManager installation while preserving its settings.

## Installation

1. Install **SleepManager**.
2. Install **SleepManager Helper** if you want Wi-Fi or Bluetooth control.
3. Open SleepManager and choose the actions you want.
4. For Syncthing-Fork, enable **Settings → Behaviour → Service Control by Broadcast**.
5. For AYN Thor protection, enable the option and grant the one-time Device Admin permission.
6. Enable **SleepManager**, then tap **Finish setup**.

No root, Shizuku or ADB is required on the device.
