# SleepManager 0.6.1-beta2

This beta focuses on **reliability, smarter sync behavior and new device-aware sleep controls**.

Beta2 also hardens the edge cases found during the overlap/contradiction audit: fresh BasicSync state probing before a normal sleep STOP, Charging Separation recovery after a process kill, bounded system-setting restore retries when SleepManager is disabled, and capability-gated closed-lid Power controls.

It includes the fixes and features planned for 0.6.1 so they can now be tested together on real handhelds.

**No root, Shizuku or ADB is required for normal use.**

## What's new

### Better recovery

- Improved recovery when Android kills and restarts the SleepManager foreground service under memory pressure.
- Main ↔ Helper restore handling is now cycle-aware and idempotent, avoiding false **Pending restore needs attention** warnings when a restore already completed before the main process was restarted.
- Diagnostics now include more recovery and device-capability information.

### BasicSync improvements

- If BasicSync is already syncing when the device goes to sleep, SleepManager can let that sync finish before stopping it and turning managed Wi-Fi off.
- A bounded timeout prevents an active sync from keeping the sleep transition open indefinitely.
- BasicSync 3.19 advanced sync now works independently when Syncthing-Fork is also enabled.
- Syncthing-Fork keeps its normal STOP/FOLLOW behavior while BasicSync uses completion-aware sync.

### Battery Saver during sleep

On supported devices, SleepManager can now enable Android Battery Saver after pre-sleep sync/STOP work is finished, then restore the previous state on wake.

If Battery Saver was already enabled before sleep, SleepManager leaves it alone.

The Battery Saver action and Battery Saver sleep condition are mutually exclusive to avoid conflicting behavior.

### Charging Separation with lid closed

On compatible clamshell devices, SleepManager can temporarily disable Charging Separation while the lid is closed so the battery can charge normally, then restore the user's previous setting when the lid opens.

Dock behavior is preserved:

- closing the lid while docked leaves Charging Separation unchanged
- connecting an external display restores the original state
- disconnecting the display while the lid remains closed returns to the closed-lid behavior

This automation follows lid/dock state and is independent from normal sleep conditions.

### Broader clamshell support

The existing Thor `hall_switch` path remains the first choice, while SleepManager can now also detect the standard Linux `SW_LID` capability as a fallback.

This prepares the closed-lid features for other compatible AYN, Retroid and Android clamshell handhelds without hard-coding model names.

### Cleaner UI and background reliability

- **System controls** now groups Wi-Fi, Bluetooth and Battery Saver.
- **Sleep behavior** contains closed-lid, Charging Separation, dock and grace-period options.
- Unsupported Battery Saver / Charging Separation controls stay hidden instead of showing unusable options.
- Added passive checks for Android battery optimization and unused-app restrictions without adding a setup wall of permission prompts.
- Added **Release notes** next to update actions.
- Improved wording and diagnostics.

## Beta notes

This is a preview build. The new Battery Saver, Charging Separation, recovery and generic lid-detection paths need broader real-device testing before the stable 0.6.1 release.

The AYN Thor system controls have already been verified at the system level; this beta will validate the complete in-app control path and state restoration.

## Compatibility

- Android **9 / API 28 or newer**
- BasicSync **3.18+** for normal state-aware sleep/wake
- BasicSync **3.19+** for completion-aware advanced sync
- Syncthing-Fork STOP/FOLLOW remains supported
- Helper **1.1.1**
