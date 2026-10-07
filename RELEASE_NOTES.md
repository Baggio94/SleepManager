# SleepManager 0.7.2-rc2

SleepManager 0.7.2-rc2 is the second release candidate for 0.7.2. It keeps the already validated sleep/wake engine while tightening radio-backend migration, integration readiness and handheld controller reliability.

## Radio control and Compatibility Helper

SleepManager now treats radio control as one automatic backend:

- On devices where direct **PServer** Wi-Fi/Bluetooth control is available, SleepManager uses it automatically.
- The Compatibility Helper is only the fallback when direct radio control is unavailable.
- PServer-capable users are not asked to complete Helper-specific setup and Helper updates are not surfaced as required.
- Legacy Helper communication remains supported during the migration window so updating the Main app first does not intentionally break existing setups.

On devices that still require the Helper:

- The Helper battery-optimization exemption is now a real prerequisite for managed Wi-Fi and Bluetooth.
- Wi-Fi additionally requires Android's **Wi-Fi control** access when the platform explicitly reports it as denied.
- Bluetooth does not depend on the Wi-Fi-control special access.
- Existing SleepManager Wi-Fi/Bluetooth preferences are preserved if a prerequisite is temporarily missing. The affected controls are disabled until setup is complete, then become effective again automatically.

## BasicSync readiness

BasicSync readiness now distinguishes two different situations:

- If Android has placed BasicSync in the package **STOPPED** state after a force-stop, SleepManager tells you to open BasicSync instead of incorrectly saying that remote control is disabled.
- If BasicSync is not stopped but still does not answer the bounded `REQUEST_STATE` probes, SleepManager continues to show **Allow remote control required**.

Returning from BasicSync automatically triggers a fresh readiness check. Existing BasicSync sleep/wake, completion-aware sync and periodic-sync behavior is unchanged.

## RAOfflineProxy prerequisite

The RAOfflineProxy setup action now targets the battery-optimization exemption that SleepManager actually checks.

- The UI uses the platform-independent wording **Battery optimization exemption required**.
- **Allow** opens the Android exemption flow when available.
- SleepManager falls back to the system battery-optimization screen, then app details if necessary.
- Readiness is checked again when returning to SleepManager.

## Controller reliability

RC2 includes a focused controller hardening pass without pulling the larger Controller Navigation v2 refactor into 0.7.2.

- **L1 / R1** switch tabs more reliably and no longer clear focus first.
- **Start / Menu** opens or closes the navigation drawer.
- Normal D-pad/left-stick page navigation no longer enters the persistent side rail.
- Up/down navigation gets a small auto-scroll fallback when Compose cannot find the next off-screen focus target.
- **L2 / R2** move by page.
- The right stick scrolls freely without intentionally clearing the current focus.
- Controller **A** produces explicit SleepManager click/haptic feedback when an action is accepted.
- Touch input keeps the existing touch experience and hides the controller highlight until controller input resumes.

The full card registry, per-tab focus memory and horizontal sub-focus for multi-action cards remain planned for 0.7.3.

## Compatibility

- Main app: **0.7.2-rc2 / versionCode 556**
- Compatibility Helper: **1.1.7-rc1 / versionCode 1121** (unchanged in RC2)
- Android **9 / API 28 or newer**
- Existing SleepManager settings are preserved when updating
- Direct PServer control and Helper fallback are selected automatically

## Testing status

The RC1 software matrix already validated core sleep/wake, BasicSync normal and advanced sync, periodic sync, RAOfflineProxy smoke, Tailscale/JamesDSP, process recovery and rapid wake/sleep behavior.

RC2 adds targeted changes around prerequisites and controller input. Before stable 0.7.2, run the targeted RC2 emulator checks followed by the physical release gate on AYN Thor, Retroid Pocket Classic and KONKR Pocket Advance.
