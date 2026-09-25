# SleepManager 0.6.0

SleepManager 0.6 focuses on **smarter syncing, more precise battery tracking and better reliability**.

The biggest addition is completion-aware sync automation with BasicSync 3.19+: SleepManager can now sync at controlled times during sleep, before sleep or after wake, then stop the sync client again when the work is finished.

It also improves Syncthing-Fork wake behavior, battery measurements, diagnostics and AYN Thor reliability.

**No root, Shizuku or ADB is required for normal use.**

## At a glance

- **Periodic sync while sleeping** with supported completion-aware providers.
- **Sync then stop on sleep & wake** for short, controlled sync windows.
- **BasicSync 3.19 completion support** using its official synchronization counters.
- **Faster Syncthing-Fork wake restore** without waiting up to 15 seconds for Android internet validation.
- **More precise sleep battery measurements** when Android exposes charge-counter data.
- **Better diagnostics** for unexpected app/process stops on Android 11+.
- **AYN Thor reliability improvements** around closed-lid false wakes and maintenance syncs.
- **Helper 1.1** with safe temporary Wi-Fi control for maintenance.

## Smarter sync automation

### Periodic sync while sleeping

With a supported completion-aware sync provider, SleepManager can perform a maintenance sync during a long sleep session.

When the scheduled maintenance time arrives, SleepManager can:

1. temporarily restore Helper-managed Wi-Fi if needed
2. wait for usable connectivity
3. start the sync client
4. wait for synchronization to finish
5. stop the client
6. return Wi-Fi to the sleep state

The next attempt uses a one-shot **24-hour alarm**. SleepManager does not run permanent maintenance polling in the background.

### Sync then stop on sleep & wake

This second mode is designed for people who want their sync client stopped most of the time.

SleepManager can:

- sync before sleep, then stop the client before continuing the normal sleep actions
- sync after a real wake, then stop the client again once synchronization is complete

### BasicSync 3.19

BasicSync 3.19 provides the synchronization state needed for these new workflows.

SleepManager waits conservatively:

- active scanning, syncing, cleaning, starting or pending work is still considered busy
- blocked, errored or incomplete state is not treated as success
- completion must remain stable before SleepManager accepts it
- STOP is confirmed before managed sleep Wi-Fi is removed

SleepManager also preserves BasicSync ownership correctly. If BasicSync was in Auto mode, started Manual mode or already stopped Manual mode, SleepManager restores only the state it actually took ownership of.

If you manually change BasicSync while SleepManager owns a stopped state, SleepManager relinquishes that ownership instead of overwriting your newer choice.

BasicSync 3.18+ remains supported for the normal state-aware sleep/wake integration.

## Faster Syncthing-Fork wake behavior

Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported.

Previously, FOLLOW could be delayed while SleepManager waited for Android to report a fully validated internet connection.

In 0.6, once Helper-managed radio restoration has completed, SleepManager sends **FOLLOW immediately** and lets Syncthing-Fork handle its own network reconnection.

This removes the unnecessary wake delay seen on devices where Android internet validation takes several seconds.

The new completion-aware maintenance modes still require a supported synchronization-completion API, so they are currently available with BasicSync 3.19+ rather than Syncthing-Fork.

## More precise sleep battery statistics

When Android exposes a usable charge counter and battery-capacity value, SleepManager can now keep a more precise battery change for each sleep session.

Improvements include:

- **Last sleep** can show two decimal places for measured battery change
- 7-day drain uses the more precise measured value when available
- Best/Worst drain and standby estimates prefer measured precision
- measured mAh remains available
- recent historical sessions with measured mAh can be re-evaluated using the current capacity estimate
- ambiguous older 0%-change sessions are no longer treated as genuine zero-drain samples

Short sessions are still shown in **Last sleep**, but only eligible non-charging sessions of at least **3 hours** contribute to long-term averages and standby estimates.

Sessions that include charging remain excluded from drain averages.

## Better diagnostics

On Android 11+, **Activity → Copy diagnostics** can now include recent Android process-exit information when the system provides it.

This can help distinguish between cases such as:

- low-memory kill
- crash
- ANR
- user-requested stop
- package update
- other Android/system exit reasons

The report can also include process importance and sampled PSS/RSS memory information when available.

This is especially useful when SleepManager appears to have stopped unexpectedly on a low-memory or aggressively managed device.

## AYN Thor improvements

0.6 keeps Thor closed-lid protection compatible with the new sync workflows.

- Closed-lid false wakes do not interrupt an active pre-sleep or maintenance sync.
- A periodic maintenance trigger during a suppressed false wake is deferred instead of being treated as a real wake.
- Existing dock-aware closed-lid protection remains intact.
- Existing **Sleep when external display disconnects** and **Power button sleeps with lid closed** options remain available.

## Battery-conscious implementation

The new sync automation is designed to stay bounded and event-driven:

- no permanent maintenance polling
- network readiness uses Android callbacks
- synchronization polling runs only while a maintenance session is active
- partial wake locks are held only during active bounded transitions
- no periodic maintenance alarm is armed unless the selected provider supports reliable completion state

Existing battery, charging, Battery Saver and schedule conditions also apply to maintenance syncs.

## Compatibility

- Android **9 / API 28 or newer**
- Main app: **0.6.0 / versionCode 531**
- Helper: **1.1.0 / versionCode 1100**
- BasicSync **3.18+** for state-aware normal sleep/wake behavior
- BasicSync **3.19+** for completion-aware sync automation
- Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported
- AYN Thor-specific options appear only when the Thor lid sensor is detected

## Installation

### 1. Install SleepManager

Download and install:

**SleepManager-0.6.0.apk**

### 2. Install the Helper if you want Wi-Fi / Bluetooth control

Inside SleepManager, open:

**About → Updates → Install Helper**

or install the release asset manually:

**SleepManager-Helper-1.1.0.apk**

The Helper has no launcher icon or separate interface.

### 3. Configure SleepManager

Open SleepManager, choose the actions and optional rules you want, enable SleepManager, then tap **Finish setup**.

### Optional integration setup

**Syncthing-Fork**

Enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable Syncthing-Fork in SleepManager.

**BasicSync**

Enable:

**Allow remote control**

BasicSync 3.19+ is required for the new completion-aware sync modes.

**AYN Thor closed-lid protection**

Enable the Thor protection option in SleepManager and approve the one-time Android Device Admin permission when prompted.

## Updating from an earlier version

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

Official releases keep the same package IDs and permanent signing certificate, so normal updates preserve existing settings.

**No root, Shizuku or ADB is required on the device.**
