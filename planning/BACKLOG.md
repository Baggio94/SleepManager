# SleepManager Future Backlog

This file tracks work that is intentionally **not allowed into the current 0.7 reliability candidate** until the validation gate in [0.7.0.md](./0.7.0.md) passes and the user explicitly accepts the candidate.

The goal is to keep future work visible without mixing it into the release-hardening phase.

---

# 1. Integration roadmap

## 1.1 Key Mapper

Upstream:
- keymapperorg/KeyMapper issue #2279
- Status: open
- Existing external actions already include pause/resume/toggle mappings.
- Upstream developer agreed that `ACTION_MAPPINGS_STATE_CHANGED` is the right general state API.
- Upstream plans to emit the state broadcast when the Key Mapper service starts.
- Accessibility-service availability can be checked by SleepManager using Android APIs; Key Mapper does not need to expose that separately.

### Desired SleepManager behavior

- [ ] Detect supported Key Mapper version/API.
- [ ] Register a manifest receiver for mapping-state changes if the final upstream contract requires it.
- [ ] Cache the last authoritative mappings state.
- [ ] Before sleep:
  - if mappings already paused -> leave untouched
  - if mappings active -> pause and record ownership
  - if state unknown -> do not guess
- [ ] On real wake:
  - resume only if SleepManager paused mappings
- [ ] False wake must not resume mappings.
- [ ] Service/process restart must preserve ownership.
- [ ] Show accessibility-service status separately from mappings state.
- [ ] Add diagnostics:
  - installed/version
  - API availability
  - accessibility service enabled
  - last known mappings state
  - action sent
  - restore ownership/result
- [ ] Add tests for active, already paused, unknown, duplicate state broadcasts and process restart.

### Important constraint

Do not infer an initial mapping state by sending toggle commands. Wait for the documented upstream state contract or use a safe explicit state request if upstream later adds one.

---

## 1.2 RAOfflineProxy

Upstream:
- misantronic/RAOfflineProxy issue #191: completed
- implementation referenced by upstream as PR #195
- automation contract documented upstream in `docs/automation-api.md`
- intended to ship with the next RAOfflineProxy version after the issue discussion

### Upstream API contract

SleepManager should use the existing ContentProvider:

- authority: `com.raofflineproxy.config`
- permission: `com.raofflineproxy.permission.CONTROL_PROXY`
- `contentResolver.call(..., "status", ...)`
- `contentResolver.call(..., "start", ...)`
- `contentResolver.call(..., "stop", ...)`
- calls must be off the main thread
- provider notifies state changes through `notifyChange`
- background notification delivery may be delayed by roughly 10 seconds
- queue states include:
  - `idle`
  - `caching`
  - `waiting`
  - `blocked`
- upstream said to treat `blocked` like `idle`
- start/stop are idempotent
- start/stop do not change the user's autostart setting
- Android 12+ background start requires RAOfflineProxy battery usage to be **Unrestricted**
- if background FGS start is blocked, API returns `foreground_service_not_allowed` and rolls back

### Desired SleepManager behavior

- [ ] Detect supported RAOfflineProxy version/API.
- [ ] Require/show **Unrestricted battery usage** before enabling the integration.
- [ ] Query `status` before taking ownership.
- [ ] Observe provider changes instead of polling continuously.
- [ ] During sleep transition:
  - if queue is caching/waiting or cache work remains, keep RAOfflineProxy and Wi-Fi available
  - once caching is finished/queue is safe, send `stop`
  - wait until `running=false`
  - only then allow managed Wi-Fi OFF
- [ ] If already stopped, leave untouched.
- [ ] Record ownership only when SleepManager changed the proxy state.
- [ ] On real wake, send `start` only when SleepManager owns the stop.
- [ ] If `foreground_service_not_allowed`, show an actionable battery-usage hint and preserve pending restore state.
- [ ] False wake must not start the proxy.
- [ ] Process restart must preserve pending ownership.
- [ ] Diagnostics must include:
  - running
  - shouldBeRunning
  - queue state/count/caching metadata exposed by API
  - last command/result
  - ownership/restore result
- [ ] Add bounded timeout/failure policy; never hold Wi-Fi indefinitely.

---

## 1.3 Obtainium

Upstream:
- SleepManager public issue #25: open
- ImranR98/Obtainium issue #3355: open
- Current request proposes a temporary pause/resume mechanism for background WorkManager update work without changing user settings.

### Desired contract

Preferred upstream API shape:

- temporary pause background work
- resume background work
- current temporary-pause/background-work state
- queued work must respect the temporary pause
- active download/update/install work should ideally finish safely rather than be killed
- resuming should return to Obtainium's normal schedule rather than forcing an immediate update
- user's configured interval/settings must not be modified

### SleepManager plan

- [ ] Wait for an official supported upstream API.
- [ ] Do not edit Obtainium's persistent user configuration as a workaround.
- [ ] Once API exists:
  - query initial state
  - pause only if necessary
  - record ownership
  - resume only if SleepManager paused it
  - preserve state through false wakes/process restart
- [ ] Add diagnostics and focused tests.

---

## 1.4 Syncthing-Fork state API

Upstream:
- researchxxl/syncthing-android PR #395: open / currently blocked in review
- maintainer asked for a smaller, clearly motivated change and questioned code placement
- SleepManager currently remains on the existing STOP/FOLLOW + health-probe behavior
- issue #396 about a definitive global sync-completion state was closed because upstream cannot reliably know absolute completion across all remote states

### Immediate rule

- [ ] Do not press Update branch or keep changing PR #395 unless the maintainer specifically asks for changes.
- [ ] Do not make the current SleepManager release depend on PR #395.
- [ ] Keep legacy STOP/FOLLOW behavior working.

### Desired state API if upstream accepts it

Minimum useful contract:
- current service mode
- current run state
- state-change broadcast
- request-current-state action
- enough information to confirm START/STOP result

Then SleepManager can:
- preserve the user's exact prior mode/state
- stop only when needed
- confirm STOP before removing Wi-Fi
- restore only what it changed
- remove the current health-probe ambiguity

### Completion-aware sync

A definitive `SYNCED` flag is not currently guaranteed upstream.

If future upstream APIs expose raw folder/device counters:
- [ ] treat them as observations, not absolute truth
- [ ] use a stability window + timeout policy in SleepManager
- [ ] preserve UNKNOWN state
- [ ] never block sleep indefinitely

Until then:
- BasicSync remains the supported completion-aware provider.
- Syncthing-Fork remains normal STOP/FOLLOW only.

---

# 2. Existing integration improvements

## BasicSync

- [ ] Keep 3.18+ state-aware normal sleep/wake behavior regression-tested.
- [ ] Keep 3.19+ completion-aware advanced sync regression-tested.
- [ ] Verify AUTO/RUNNING preservation.
- [ ] Verify MANUAL/RUNNING preservation.
- [ ] Verify MANUAL/STOPPED is left untouched.
- [ ] Verify active sync is allowed to finish before STOP in normal mode.
- [ ] Verify Sync then stop before sleep / after wake.
- [ ] Verify periodic sync.
- [ ] Verify timeouts leave a diagnosable state.
- [ ] Verify false wake never triggers wake-sync.

## Tailscale

- [ ] Keep disconnect/reconnect ownership state-aware.
- [ ] Verify delayed disconnect confirmation.
- [ ] Verify process restart while restore pending.
- [ ] Add richer diagnostics if the public app API permits it.

## JamesDSP

- [ ] Keep explicit OFF/ON behavior documented as less state-aware than other connectors.
- [ ] Re-check whether newer supported builds expose a reliable state API before adding assumptions.
- [ ] Never turn it ON on wake unless SleepManager actually turned it OFF for the sleep transaction.

## Compatibility Helper

- [ ] Keep Wi-Fi/Bluetooth ownership fully cycle-aware.
- [ ] Preserve signature permission.
- [ ] Keep old Helper upgrade compatibility for a defined transition period.
- [ ] Eventually remove legacy uncorrelated-result acceptance only after old Helper versions are no longer supported.

---

# 3. Onboarding / Quick Setup redesign

Blocked until 0.7 reliability validation passes.

## Goals

- [ ] Keep onboarding short.
- [ ] Auto-detect installed integrations.
- [ ] Ask for capabilities only when a chosen feature needs them.
- [ ] Avoid repeated setup spam.
- [ ] Explain why each required setting exists.
- [ ] Keep an always-accessible Quick Setup / repair flow after onboarding.

## Contextual setup items

### Core
- SleepManager foreground/background reliability
- Battery Optimization
- Unused App Restrictions

### Wi-Fi / Bluetooth
- Helper installed/current
- Helper signer/version compatibility

### Closed-lid protection
- lid sensor available
- Device Admin active
- Force SELinux limitation/warning on Thor when relevant

### Syncthing-Fork
- installed target
- Service Control by Broadcast requirement

### BasicSync
- installed/version
- Allow remote control
- completion API support/version

### RAOfflineProxy
- supported version/API
- Unrestricted battery usage

### Key Mapper
- supported state API
- accessibility service status

### Obtainium
- only once an official temporary-pause API exists

## Final setup summary

- [ ] Show exactly what will happen on screen OFF.
- [ ] Show exactly what will be restored on real wake.
- [ ] Mention that false wakes do not trigger normal wake restoration.
- [ ] Surface missing prerequisites without forcing unrelated permissions.

---

# 4. Future features / quality-of-life

These are ideas, not committed release scope.

## Diagnostics / support workflow

- [ ] One-tap diagnostics suitable for GitHub issue templates.
- [ ] Optional "Copy short summary" and "Copy full diagnostics".
- [ ] Clearly flag suspicious firmware values.
- [ ] Include capability reasons instead of only supported/not-supported booleans.
- [ ] Consider a diagnostic export file only if clipboard blocks become too large.

## Battery / sleep analytics

- [ ] Per-session false-wake count.
- [ ] Per-session deep-sleep duration + percentage.
- [ ] Separate powered vs unplugged sessions.
- [ ] Better long-term trend presentation if enough data exists.
- [ ] Avoid over-interpreting short sessions or charge-limited firmware data.

## Compatibility database

Potential future lightweight device notes:
- known lid-sensor path/detection method
- privileged control support
- known firmware caveats
- no remote telemetry required

Do not turn this into brittle model-specific branching unless a generic capability check is impossible.

---

# 5. Explicit non-goals / constraints

## Mobile data

Public issue #6 is closed.

Android does not expose a reliable normal-third-party-app mobile-data toggle; it generally requires privileged `MODIFY_PHONE_STATE`/carrier privileges.

SleepManager should continue to avoid adding:
- root requirement
- Shizuku requirement
- ADB requirement

Revisit only if Android exposes a safe normal-app API.

## Third-party app internals

Do not:
- force-stop apps as a substitute for a supported integration API
- mutate another app's persistent settings to simulate a temporary sleep state
- assume undocumented internal services will stay stable

## Battery/background efficiency

Do not add:
- permanent polling
- unbounded wake locks
- unbounded network waits
- periodic jobs when an event-driven callback/broadcast is available

---

# 6. Backlog entry requirements

Before adding any future integration/feature to a release branch, document:

1. user-visible purpose
2. upstream API/capability
3. initial-state query
4. ownership model
5. sleep action
6. confirmation behavior
7. wake restore
8. false-wake behavior
9. process-restart behavior
10. timeout/failure behavior
11. diagnostics
12. unit/integration/physical tests
13. battery/runtime cost
14. compatibility/version gating
