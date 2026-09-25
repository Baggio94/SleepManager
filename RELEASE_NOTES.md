# SleepManager 0.6.0

SleepManager 0.6.0 adds completion-aware synchronization maintenance, more precise sleep battery measurements, stronger diagnostics and several reliability improvements while keeping the normal sleep/wake path state-aware and battery-conscious.

## What's new

### Periodic sync while sleeping

With a completion-aware provider, SleepManager can periodically perform a maintenance sync during a long sleep session:

1. Temporarily restore Helper-managed Wi-Fi when needed.
2. Wait for validated connectivity.
3. Start the sync client.
4. Wait for synchronization to complete.
5. Stop the client.
6. Return Wi-Fi to the previous sleep state.

Maintenance uses a one-shot 24-hour alarm rather than permanent background polling. The alarm is cancelled on a real wake and is armed only when the selected provider supports reliable completion state.

### Sync then stop on sleep & wake

This optional mode keeps a completion-aware sync client stopped outside short synchronization windows.

Before sleep, SleepManager can sync first, wait for stable completion, stop the client, then continue the normal sleep actions. After a real wake, networking is restored, the client syncs again, and SleepManager stops it after completion.

### BasicSync 3.19 completion support

BasicSync 3.19 exposes official folder/device synchronization counters in addition to its existing mode and run-state API.

SleepManager maps those counters conservatively:

- scanning, syncing, cleaning, starting or pending device work remains busy
- blocked, errored or incomplete observations remain unknown
- an idle result must stay stable before completion is accepted
- STOP is confirmed before managed sleep Wi-Fi is switched off
- SleepManager preserves the original BasicSync mode and relinquishes ownership if the user changes BasicSync manually

BasicSync 3.18+ remains supported for normal state-aware sleep/wake control.

### Helper 1.1

The optional Helper adds temporary Wi-Fi control for maintenance sessions without overwriting the saved wake-restore state.

Temporary Wi-Fi changes are accepted only while the Helper still owns the Wi-Fi change from the active sleep cycle, preventing a late cleanup from turning Wi-Fi off after a normal wake.

### Syncthing-Fork wake reliability

Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported.

On wake, after Helper-managed radio restoration completes, SleepManager now sends FOLLOW immediately instead of waiting up to 15 seconds for Android internet validation. Syncthing-Fork handles its own network reconnection from that point. The validated-network gate remains available for integrations that still require it.

### AYN Thor

- Closed-lid false wakes no longer interrupt an in-progress pre-sleep or periodic maintenance sync.
- If a periodic alarm lands during a suppressed closed-lid wake, SleepManager defers the attempt instead of treating it as a real wake.
- Existing dock-aware closed-lid protection and power-button/external-display options remain state-aware.

## Battery behavior

The new maintenance path stays bounded and event-driven:

- no permanent maintenance polling
- network readiness uses Android callbacks
- synchronization polling runs only during an active bounded maintenance session
- partial wake locks are held only during active bounded transitions/maintenance
- managed sync clients are stopped before SleepManager turns managed Wi-Fi off
- existing battery, charging, Battery Saver and schedule conditions also gate maintenance syncs

## More precise sleep battery statistics

SleepManager now keeps a precise session battery change when Android exposes a usable charge counter and battery-capacity value.

- **Last sleep** shows two decimal places when the session has a measured precise value.
- 7-day drain, Best/Worst drain and standby estimates prefer precise measured drain instead of Android's coarse integer battery level.
- Sessions shorter than 3 hours remain visible in Last sleep/history but do not contribute to long-term statistics.
- Sessions containing charging remain excluded from drain averages.
- Legacy sessions that only report a 0% integer change are no longer treated as real 0% drain samples.
- Existing recent sessions with measured mAh can be re-evaluated using the current capacity estimate without clearing history.

When precise battery data is unavailable, SleepManager keeps the honest integer Android fallback instead of displaying fake decimal precision.

## Process-exit diagnostics

On Android 11+, copyable diagnostics now include recent Android process-exit history.

The report includes the system exit reason, exit status, process importance and sampled PSS/RSS memory values when available. This helps distinguish low-memory kills, crashes, ANRs and user/system-requested stops when investigating unexpected service deaths.

## Compatibility

- Completion-aware maintenance currently works with **BasicSync 3.19+**.
- BasicSync 3.18+ remains supported for normal state-aware sleep/wake control.
- Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported, but completion-aware maintenance stays unavailable until Syncthing-Fork exposes a supported synchronization-completion API.
- Helper version: **1.1.0 / versionCode 1100**.
- Main app: **0.6.0 / versionCode 531**.
- **No root, Shizuku or ADB required for normal use.**

## Validation

0.6.0 was validated on emulator and real AYN Thor hardware, including:

- BasicSync pre-sleep and wake completion-aware synchronization
- BasicSync ownership/state restoration
- periodic sleep synchronization with real transfer behavior
- Syncthing-Fork STOP before managed Wi-Fi sleep and immediate FOLLOW after wake radio restoration
- AYN Thor closed-lid sleep/wake behavior
- precise short-session battery measurement and deep-sleep reporting
- foreground-service recovery after process termination


## Installation

### 1. Install SleepManager

Download and install:

**SleepManager-0.6.0.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

From SleepManager, open:

**About → Updates → Install Helper**

or install the release asset manually:

**SleepManager-Helper-1.1.0.apk**

The Helper has no launcher icon or separate interface.

### 3. Configure SleepManager

Open SleepManager, choose the actions you want, enable SleepManager, then tap **Finish setup**.

For **Syncthing-Fork**, enable **Settings → Behaviour → Service Control by Broadcast**.

For **BasicSync**, enable **Allow remote control**. BasicSync 3.19+ is required for the new completion-aware sync modes.

For **AYN Thor closed-lid protection**, enable the option and approve the one-time Android Device Admin permission when prompted.

## Updating from an earlier version

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

Official releases keep the same package IDs and permanent signing certificate, so normal updates preserve existing settings.

**No root, Shizuku or ADB is required on the device.**
