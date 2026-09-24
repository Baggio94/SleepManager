# SleepManager 0.6.0

SleepManager 0.6.0 adds completion-aware sync maintenance while keeping the normal sleep/wake behavior state-aware and battery-conscious.

## What's new

### Periodic sync while sleeping

When supported by the selected sync client, SleepManager can perform a maintenance sync during a long sleep session:

1. Temporarily restore Helper-managed Wi-Fi when needed.
2. Wait for validated connectivity.
3. Start the sync client.
4. Wait for synchronization to complete.
5. Stop the client.
6. Return Wi-Fi to the previous sleep state.

The next maintenance attempt is scheduled with a one-shot 24-hour alarm. The alarm is cancelled on a real wake and re-armed only when the feature is still eligible.

### Sync then stop on sleep & wake

A second optional mode keeps a completion-aware sync client stopped outside short synchronization windows.

Before sleep, SleepManager can sync first, stop the client, then continue the normal sleep actions. After a real wake, networking is restored first, the client syncs, and SleepManager stops it again after completion.

### BasicSync 3.19 support

BasicSync 3.19 exposes official folder/device synchronization counters in addition to its existing mode and run-state API.

SleepManager uses those counters conservatively:

- active scanning/syncing/cleaning/starting or pending device work means synchronization is still busy
- blocked/error/incomplete observations are treated as unknown, never as success
- an idle result must remain stable before SleepManager accepts completion

BasicSync 3.18+ remains supported for normal state-aware sleep/wake control.

### Helper 1.1

The optional Helper adds temporary Wi-Fi control for periodic sleep maintenance.

Temporary Wi-Fi changes are allowed only while the Helper still owns the Wi-Fi change from the active sleep cycle, so a late cleanup cannot turn Wi-Fi off after a normal wake.

### AYN Thor

Closed-lid false wakes no longer interrupt an in-progress pre-sleep or periodic maintenance sync. If a periodic alarm lands during a suppressed closed-lid wake, SleepManager defers the attempt instead of treating it as a normal wake.

## Battery behavior

The new maintenance path is bounded and event-driven:

- no permanent maintenance polling
- network readiness uses Android callbacks
- synchronization polling runs only while a maintenance session is active
- partial wake locks are held only during an active bounded maintenance session
- no periodic alarm is armed unless the selected provider supports reliable completion state

## Current limitation

Completion-aware maintenance currently works with **BasicSync 3.19+**.

Syncthing-Fork STOP/FOLLOW sleep/wake integration remains supported, but the new maintenance modes stay unavailable for Syncthing-Fork until a supported synchronization-completion API exists.

## Development status

0.6.0 is still under development. Emulator validation covers the BasicSync 3.19 broadcast contract, pre-sleep/wake maintenance flow and periodic orchestration. Final hardware validation is still required before the stable release.
