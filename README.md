# SleepManager

SleepManager is a lightweight Android app for handhelds, phones, and tablets that manages selected actions when the screen turns off and restores them when the device wakes.

Set it once, choose what should sleep, and let it run in the background.

## What it can do

- **Wi-Fi** — turn it off during sleep and restore it only if SleepManager changed it.
- **Bluetooth** — same behavior as Wi-Fi.
- **Syncthing-Fork** — pause it during sleep and resume it after the network is ready again.
- **Tailscale** — disconnects Tailscale during sleep if it is connected, then reconnects it on wake.
- **JamesDSP** — sends JamesDSP power OFF during sleep and ON again on wake when this integration is enabled.
- **AYN Thor closed-lid protection** — protects against unwanted wake-ups while the lid is still closed.
- **Grace period** — Immediate, 5 seconds, 10 seconds, or a longer custom delay.
- **Advanced conditions** — run sleep actions only when your enabled conditions are all true.
- **Activity log** — see recent sleep/wake actions and copy a diagnostic log when needed.
- **Battery statistics** — track sleep drain, duration, 7-day averages and measured mAh when the device exposes a charge counter.
- **Quick Settings tile** — quickly enable or disable SleepManager.

SleepManager does not require root, Shizuku or ADB on the device.

## AYN Thor closed-lid protection

This is an optional feature made specifically for the **AYN Thor**.

Thor owners have repeatedly reported cases where the device wakes or stays awake while the lid is closed. AYN has already addressed parts of this behavior in firmware—for example, OTA 1.0.0.293 added options to disable the power button while closed and to prevent wake on charging-cable plug-in—but community reports of closed-lid wake/sleep issues have continued.

SleepManager does not modify the Thor firmware. Instead, it watches the Thor lid sensor.

**If the wake bug happens while the lid is still closed, SleepManager puts the Thor back to sleep.**

During that false wake it also avoids the normal wake sequence, so it does not restore Wi-Fi/Bluetooth or resume integrations until the lid is actually opened.

This matters because an unnoticed wake inside a case or bag can lead to unnecessary battery drain and heat.

Community / firmware references:

- [AYN Thor OTA changelog archive](https://github.com/ChimeraGaming/AYN-OTA-Changelogs/blob/main/Thor.md)
- [r/AynThor — Auto Wake Issues](https://www.reddit.com/r/AynThor/comments/1r3qxei/auto_wake_issues/)
- [r/AynThor — Screen not turning off when closing device](https://www.reddit.com/r/AynThor/comments/1s7gy0h/screen_not_turning_off_when_closing_device/)
- [r/AynThor — Thor Keeps Waking Up](https://www.reddit.com/r/AynThor/comments/1w93d8l/thor_keeps_waking_up/)

Enabling this feature asks for Android Device Admin permission. SleepManager uses it only to return the Thor to sleep when this protection is active.

## Installation

Install:

1. **SleepManager**
2. **SleepManager Helper** if you want SleepManager to control Wi-Fi or Bluetooth

Then open SleepManager, choose the actions you want, and enable it.

The Helper has no launcher icon or separate UI.

### Syncthing-Fork

SleepManager detects supported Syncthing-Fork builds automatically.

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

SleepManager also shows the current Syncthing state in the app when it can verify it.

### Tailscale

Install the official Tailscale Android app and sign in normally.

SleepManager shows the current Tailscale connection state. If Tailscale is connected when the device sleeps, SleepManager can disconnect it and later reconnect it only when that change was verified.

### JamesDSP

SleepManager supports the JamesDSP Manager package used by O2P Tweaks and the standard RootlessJamesDSP package when available.

When the JamesDSP integration is enabled, SleepManager sends JamesDSP **OFF** on sleep and **ON** on wake.

JamesDSP exposes a public power-control broadcast but no public state-query API for normal Android apps. Because SleepManager cannot reliably read whether JamesDSP was already OFF before sleep, this integration acts as an explicit policy: **OFF while asleep, ON while awake**. If you prefer to keep JamesDSP manually disabled while awake, leave the SleepManager JamesDSP integration disabled.

## Sleep behavior

The default setup is simple:

- **Grace period:** Immediate
- **Custom delay:** Off
- **Advanced conditions:** Off

When the screen turns off, SleepManager:

1. waits for the selected grace/custom delay
2. checks any enabled Advanced conditions
3. applies only the selected sleep actions
4. remembers what it actually changed

When the device wakes, SleepManager restores only those changes.

Wi-Fi, Bluetooth and Tailscale restoration is state-aware: SleepManager avoids restoring a state it did not verify that it changed. Syncthing-Fork is verified when possible and falls back to its compatible STOP/FOLLOW behavior when state cannot be confirmed.

JamesDSP is the exception: because its public integration exposes power control but no readable power state, enabling JamesDSP management means **OFF during sleep and ON after wake**.

## Advanced conditions

Advanced conditions use **AND logic**.

If several conditions are enabled, every one of them must be true before sleep actions run.

Current options include:

- battery below a selected percentage
- device is not charging
- Android Battery Saver is ON or OFF
- schedule / time window
- custom sleep delay: 1, 5, 10 or 30 minutes

## Compatibility

- Android **9 / API 28 or newer**
- Main app target SDK: **36**
- Optional compatibility Helper for Wi-Fi/Bluetooth
- Extra AYN Thor protection when the Thor lid sensor is detected

## Updates

Official builds keep the same package IDs and signing certificate so they can be installed as normal updates:

- Main app: `com.med.sleepmanager`
- Helper: `com.med.sleepmanager.helper`

Install the main app first, then the Helper.

## Troubleshooting

### Wi-Fi or Bluetooth does not change

Make sure the **SleepManager Helper** is installed.

### Syncthing does not pause or resume

Make sure:

- Syncthing-Fork is detected in SleepManager
- **Service Control by Broadcast** is enabled in Syncthing-Fork
- the correct Syncthing target is selected if several builds are installed

### Tailscale does not reconnect

Open Tailscale and make sure it is signed in and able to connect normally. SleepManager only restores Tailscale when it verified that SleepManager itself disconnected it.

### JamesDSP does not return to the state I expected

When JamesDSP management is enabled, SleepManager intentionally applies **OFF on sleep / ON on wake**. JamesDSP does not expose a readable public power state, so SleepManager cannot preserve a pre-existing manual OFF state.

### Thor protection cannot be enabled

The option is shown only when SleepManager detects the AYN Thor lid sensor. Android Device Admin permission is required for the return-to-sleep action.

## Build from source

Requirements:

- JDK 17+
- Android SDK 36
- Android build-tools 36.0.0

On macOS:

```bash
chmod +x bootstrap_and_build.sh
./bootstrap_and_build.sh
```

Generated debug APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
helper/build/outputs/apk/debug/helper-debug.apk
```

## Releases

See [CHANGELOG.md](CHANGELOG.md) for the full history and [RELEASE_NOTES.md](RELEASE_NOTES.md) for the current release notes.

## Technical details

The sections above are intentionally kept simple. This section goes deeper into how SleepManager actually works internally.

### Core sleep / wake flow

SleepManager runs a foreground service that listens for Android screen state changes.

When Android sends `ACTION_SCREEN_OFF`, SleepManager does not blindly toggle everything. It starts a sleep transaction:

1. wait for the configured Grace period / Custom delay
2. evaluate all enabled Advanced conditions
3. create a persistent sleep-cycle record
4. pause/disconnect enabled integrations
5. apply Wi-Fi / Bluetooth sleep actions
6. remember which changes still need to be restored

When Android sends `ACTION_SCREEN_ON`, SleepManager first decides whether this is a real wake. On an AYN Thor with closed-lid protection enabled, a wake while the lid is still closed is intercepted before normal restoration begins.

For a real wake, SleepManager restores Wi-Fi / Bluetooth first when needed, waits for usable network connectivity, then restores network-dependent integrations such as Syncthing-Fork and Tailscale.

Sleep-cycle state is stored persistently by `SleepCycleStore`. A transaction is not considered complete until the Helper and every changed connector have either been restored or explicitly no longer require restoration. This lets pending restore state survive a service/process restart instead of losing track of what SleepManager changed.

### Wi-Fi and Bluetooth

Modern Android versions no longer allow a normal target-SDK application to directly toggle Wi-Fi and Bluetooth in the way SleepManager needs.

For that reason, radio control is isolated in the optional **SleepManager Helper**:

- Main app package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- Main app target SDK: **36**
- Helper target SDK: **28**

The Helper uses the legacy Android radio APIs:

- `WifiManager.setWifiEnabled(...)`
- `BluetoothAdapter.enable()` / `disable()`

Communication between the Main app and Helper is protected by the custom signature-level permission:

`com.med.sleepmanager.permission.CONTROL_HELPER`

Both APKs are signed with the same permanent certificate, so another unrelated app cannot simply impersonate the Helper protocol.

The Helper reads the current radio state before changing anything. For each sleep cycle it stores:

- whether Wi-Fi / Bluetooth were selected for management
- their previous state
- whether SleepManager actually changed them

Example: if Wi-Fi was already OFF before sleep, SleepManager leaves it OFF and does not turn it ON on wake. If Wi-Fi was ON and SleepManager successfully turned it OFF, it is restored on wake.

This is why radio restoration is state-aware rather than a simple "OFF on sleep / ON on wake" toggle.

### Grace period and Custom delay

The Grace period exists so short screen-off events do not immediately trigger the full sleep routine.

Available short delays are:

- Immediate
- 5 seconds
- 10 seconds

For delays up to 10 seconds, SleepManager uses a main-thread `Handler` and a short partial wake lock so the delay can finish reliably during the screen-off transition.

Custom delays of **1, 5, 10 or 30 minutes** use `AlarmManager`. When Android allows exact alarms, SleepManager schedules the delay with `setExactAndAllowWhileIdle()`. If exact-alarm access is unavailable, it falls back to `setAndAllowWhileIdle()`, which Android may defer.

If the device genuinely wakes before the delay expires, the pending sleep delay is cancelled.

On an AYN Thor, a closed-lid false wake is handled before this cancellation logic. That means a false wake while the lid is still CLOSED does **not** cancel the original Grace / Custom delay.

Advanced conditions are evaluated when the delay actually expires, not when the screen first turns off. The decision therefore uses the battery, charging, Battery Saver and schedule state that exists at the moment the sleep actions are about to run.

### Advanced conditions

Enabled Advanced conditions use **AND logic**: every enabled condition must pass.

#### Battery threshold

Battery percentage is read first from the sticky `ACTION_BATTERY_CHANGED` broadcast. If that value is unavailable, SleepManager falls back to `BatteryManager.BATTERY_PROPERTY_CAPACITY`.

The condition passes only when the current percentage is **below** the configured threshold.

#### Not charging

Charging state also comes from `ACTION_BATTERY_CHANGED`.

Both `BATTERY_STATUS_CHARGING` and `BATTERY_STATUS_FULL` are treated as charging states, so sleep actions are skipped when **Not charging** is enabled and the device is plugged in / full.

#### Battery Saver

Android Battery Saver state is read through `PowerManager.isPowerSaveMode`.

SleepManager can require Battery Saver to be either ON or OFF before applying the sleep actions.

#### Schedule

The schedule is evaluated using the device's local time in minutes since midnight.

Normal windows such as `23:00 → 23:30` are handled directly, while cross-midnight windows such as `23:00 → 07:00` are treated as one continuous overnight range.

If start and end are identical, the schedule is treated as unrestricted.

### Syncthing-Fork integration

SleepManager supports the current, debug/root and legacy Syncthing-Fork package variants.

Syncthing-Fork must have:

**Settings → Behaviour → Service Control by Broadcast**

enabled.

SleepManager controls the selected Syncthing target with the broadcasts exposed by Syncthing-Fork:

- sleep: `<package>.action.STOP`
- wake: `<package>.action.FOLLOW`

When STOP is successfully sent, SleepManager stores the exact Syncthing package name as the restore token. This matters if several supported Syncthing builds are installed: the wake action is sent back to the same target that was paused.

There is an important technical distinction here: Syncthing-Fork does not expose a reliable public query that lets SleepManager prove whether the service was already paused before the STOP broadcast. A successfully sent STOP is therefore recorded as a pending Syncthing change rather than pretending that the pre-sleep state is known with certainty.

For the status shown in the UI, SleepManager separately probes Syncthing's default local GUI health endpoint:

`127.0.0.1:8384/rest/noauth/health`

Both HTTP and local HTTPS are supported, with a short 500 ms health-check timeout.

If Wi-Fi or Bluetooth is also being disabled, SleepManager gives Syncthing **1 second** after STOP before radio sleep is applied. This gives Syncthing time to process the stop request before connectivity disappears.

On wake, network-dependent restoration is delayed until Android reports a default network with both:

- `NET_CAPABILITY_INTERNET`
- `NET_CAPABILITY_VALIDATED`

The network-ready wait has a **15 second** timeout. After that gate completes, SleepManager sends FOLLOW. If the restore cannot be sent successfully, the Syncthing restore remains pending rather than being silently marked complete.

### Tailscale integration

SleepManager targets the official Android package:

`com.tailscale.ipn`

Control uses Tailscale's Android broadcasts:

- `com.tailscale.ipn.DISCONNECT_VPN`
- `com.tailscale.ipn.CONNECT_VPN`

SleepManager does not treat the presence of an arbitrary Android VPN as proof that Tailscale is connected. It looks for a VPN transport whose interface owns a Tailscale address:

- IPv4: `100.64.0.0/10`
- IPv6: `fd7a:115c:a1e0::/48`

If Tailscale is already disconnected when sleep starts, nothing is scheduled for restoration.

If it is connected, SleepManager sends DISCONNECT and then verifies the result every **500 ms**, for up to **8 checks**. Only after the Tailscale VPN is actually observed as disconnected is the transaction converted into a restoreable Tailscale change.

If disconnection cannot be verified, SleepManager clears that pending change and will **not** reconnect Tailscale on wake. This prevents SleepManager from enabling a VPN it cannot prove it disabled.

On wake, Tailscale follows the same validated-network gate used by Syncthing. SleepManager sends CONNECT, then checks the VPN state every **500 ms** for up to **12 attempts**. A second CONNECT request is sent on attempt 4 if the VPN is still not back.

The restore transaction is cleared only when reconnection is verified. If reconnection never verifies, the restore remains pending for diagnostics/recovery instead of being reported as successful.

### JamesDSP integration

SleepManager supports the exported JamesDSP power receiver:

`me.timschneeberger.rootlessjamesdsp.SET_POWER_STATE`

with the boolean extra:

`rootlessjamesdsp.enabled`

Sleep sends `false` and wake sends `true` to the exact JamesDSP package selected for that sleep cycle.

The commonly used O2P JamesDSP Manager package is `james.dsp`; the standard RootlessJamesDSP package is also detected when installed.

JamesDSP does not expose a public query API that lets a normal third-party app reliably read its current power state. SleepManager therefore deliberately does not display a guessed Running / Stopped state. Enabling this integration is an explicit awake/sleep policy rather than a state-preserving toggle.

No root, Shizuku, ADB or notification-listener permission is required for this control path.

### AYN Thor closed-lid protection

The Thor protection does not modify AYN firmware and does not guess lid state from screen state.

SleepManager searches Linux input devices under:

`/sys/class/input/event*/device/name`

for the device named:

`hall_switch`

It then reads the corresponding `/dev/input/eventX` device directly and parses Linux `input_event` messages.

The relevant event is:

- type `0x05` — `EV_SW`
- code `0x00` — `SW_LID`
- value `1` — lid CLOSED
- value `0` — lid OPEN

When CLOSED is reported, SleepManager starts a **1.5 second close guard**. If the device is still interactive with the lid closed, Device Admin's `DevicePolicyManager.lockNow()` is used to return it to sleep.

More importantly, if `ACTION_SCREEN_ON` arrives while the lid state is still CLOSED, SleepManager stops the normal wake path immediately:

- Grace / Custom delay is not cancelled
- Wi-Fi / Bluetooth are not restored
- Syncthing is not resumed
- Tailscale is not reconnected

A closed-lid wake is rechecked after **500 ms** and, if the Thor is still interactive and closed, it is locked again.

There is also a **900 ms lock cooldown**. This avoids repeatedly firing `lockNow()` during the same transition while still scheduling another verification, because some Thor wake/sleep sequences can briefly bounce back to an interactive state.

Once the lid reports OPEN, the closed-lid guards are cancelled and the next real screen wake can follow the normal restoration path.

Android Device Admin permission is used only for the `lockNow()` action required by this feature.

### Restore ordering and network recovery

The wake order is intentionally asymmetric with the sleep order.

When Wi-Fi was turned off for sleep, network-dependent apps cannot be restored first. SleepManager therefore restores the Helper-managed radios, waits for Android to report validated connectivity, and only then resumes pending network connectors.

The network gate uses `ConnectivityManager.registerDefaultNetworkCallback()` and completes when the active/default network has validated Internet access. It is cancellable, so another screen-off event or a Thor false wake can stop an in-progress wake restoration.

Pending connector changes are stored independently. A failed Syncthing or Tailscale restore therefore does not erase the whole sleep transaction.

### Persistent transaction model

A sleep cycle has a unique timestamp-based cycle ID and persists information such as:

- whether the Helper was expected
- whether the Helper sleep request was sent
- whether Helper restoration completed
- which radios were managed
- which connector changes are still pending
- connector-specific restore tokens

Critical transaction updates in the Main app use synchronous SharedPreferences commits so the state is written before the flow moves on.

If the foreground service is recreated while a sleep transaction already exists, SleepManager reuses the stored transaction instead of starting a duplicate cycle.

Disabling SleepManager also goes through the restore path: it first attempts to restore anything that is still owned by the active sleep transaction, then stops the automation service.

This transaction model is the reason SleepManager can be conservative about wake restoration: it tries to restore what it knows it changed, preserve unresolved work when restoration fails, and avoid forcing unrelated user state.

