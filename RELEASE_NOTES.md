# SleepManager 0.5.1

SleepManager 0.5.1 is a cumulative release that includes the major improvements introduced in 0.5.0 plus the new built-in updater, safer background behavior on handhelds such as the AYN Thor, notification polish, and additional battery-stat refinements.

## Highlights

### Built-in secure updater

- SleepManager can now check GitHub for a newer stable release automatically, at most once every 24 hours.
- A manual **Check for updates** action is available in **About → Updates**.
- Android notifications can alert you when a newer stable version is available.
- Tapping an update notification opens SleepManager directly on the Updates section.
- When trusted update metadata is available, SleepManager downloads the new APK directly inside the app.
- Before Android is allowed to install it, SleepManager verifies:
  - the published SHA-256 digest
  - package name `com.med.sleepmanager`
  - the expected release version name
  - a strictly newer Android `versionCode`
  - the permanent SleepManager release signing certificate
- After verification, SleepManager opens Android's official package installer.
- Android may ask once for **Install unknown apps** permission for SleepManager. No root, Shizuku or ADB is required.
- Stable releases now publish an `update.json` manifest alongside the APKs.
- If direct-install metadata is unavailable, SleepManager falls back to the corresponding GitHub release page.

The full download → SHA-256 verification → package/version verification → signature verification → Android installer flow was validated with a separately signed prerelease before 0.5.1.

### Safer background behavior and external navigation

- SleepManager is hidden from Android Recents so it cannot be accidentally swiped away on devices such as the AYN Thor, where removing the task can also terminate the foreground service.
- App-initiated navigation is now distinguished from a genuine user leave/Home action.
- Android permission screens, App info, GitHub links and update flows no longer cause SleepManager to remove its own task.
- The foreground service still restarts after package replacement when SleepManager was enabled.
- Boot recovery remains enabled through Android's `BOOT_COMPLETED` flow.

### Dedicated notification icon

- Replaced the generic Android alarm status-bar icon with a dedicated SleepManager small-notification icon.
- The new icon is a monochrome transparent vector designed for Android status-bar rendering.
- The persistent foreground-service notification and update notifications now share the same SleepManager visual identity.

### Battery statistics refinements

- The Home battery gauge can now cycle through additional sleep statistics when tapped.
- Available views include:
  - current battery capacity
  - estimated standby
  - sleep drain per hour
  - deep-sleep percentage / collecting state
- Battery-percentage alignment and responsive presentation were refined.

## Major features introduced in 0.5.0 and included in 0.5.1

Because 0.5.0 was released only shortly before 0.5.1, the main 0.5.0 additions are included here as well.

### JamesDSP sleep / wake integration

- Detects the JamesDSP Manager package used by O2P Tweaks and the standard RootlessJamesDSP package when available.
- Sends JamesDSP **OFF** when sleep actions run.
- Sends JamesDSP **ON** again on a real wake.
- Uses JamesDSP's exported power-control receiver; no root, Shizuku or ADB is required.
- JamesDSP does not expose a public power-state query to normal apps, so SleepManager deliberately does not show a guessed Running / Stopped state.
- Enabling the integration is therefore an explicit **OFF while asleep / ON while awake** policy.

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

## Existing core features

SleepManager also continues to provide:

- state-aware Wi-Fi sleep/wake management through the optional Helper
- state-aware Bluetooth sleep/wake management through the optional Helper
- Syncthing-Fork STOP/FOLLOW integration with network-ready restore
- Tailscale disconnect/reconnect with verification
- AYN Thor Hall-sensor closed-lid protection
- Grace period and Custom delay
- Advanced battery, charging, Battery Saver and schedule conditions
- persistent sleep/wake transactions
- Activity log and diagnostic copy
- Quick Settings tile

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Compatibility Helper target SDK: **28**
- Main app package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- Same permanent signing certificate as previous official builds
- Stable 0.5.1 version code: **522**
- Existing settings are preserved when updating an official signed build

## Installation / update

1. Install **SleepManager 0.5.1** over your existing official build.
2. Install **SleepManager Helper 0.5.1** if you use Wi-Fi or Bluetooth management and want the matching Helper build.
3. Existing settings are preserved during an official signed update.
4. For Syncthing-Fork, keep **Settings → Behaviour → Service Control by Broadcast** enabled.
5. For AYN Thor protection, keep the one-time Device Admin permission enabled.
6. Android 13+ may ask for notification permission if you enable update notifications.
7. The first direct in-app APK update may also require Android's one-time **Install unknown apps** permission for SleepManager.

No root, Shizuku or ADB is required for normal use.
