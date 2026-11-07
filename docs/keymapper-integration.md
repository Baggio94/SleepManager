# Key Mapper integration (experimental)

Optional, disabled-by-default integration for Key Mapper **4.7.0+**.
Source: https://github.com/keymapperorg/KeyMapper/issues/2279

## Protocol
- Observe `io.github.sds100.keymapper.ACTION_MAPPINGS_STATE_CHANGED`
  with boolean extra `io.github.sds100.keymapper.EXTRA_MAPPINGS_PAUSED`.
- The broadcast is sent when mappings change, Key Mapper starts, or its
  accessibility service starts.
- Send `ACTION_PAUSE_MAPPINGS` and `ACTION_RESUME_MAPPINGS` explicitly to
  `io.github.sds100.keymapper.api.PauseMappingsBroadcastReceiver`.

## Safety
- SleepManager never pauses unknown or already-paused mappings.
- Only when the last observed state was active and a pause request is sent does
  SleepManager create a per-cycle restore token.
- On real wake or manager disable, only an owned pause is restored.
  If Key Mapper reports ACTIVE already, no resume request is made.
- Observations are scoped to the current Android boot count.
- If SleepManager missed the initial state broadcast, open/restart Key Mapper
  to synchronize. SleepManager leaves mappings unchanged meanwhile.
- Broadcast commands have no delivery acknowledgement; pending ownership is
  a record of a request, not guaranteed confirmation of the target's state.
- Does not disable the accessibility service, terminate Key Mapper or change
  stored mappings.

## Manual beta test plan
1. With Key Mapper 4.6.x, the integration toggle must be disabled.
2. With 4.7+, observe active state; opt in, sleep and wake.
3. Manually pause before sleep and confirm mappings stay paused after wake.
4. Test initial state unknown and after a reboot: no blind pause.
5. Disable SleepManager mid-cycle, verify owned mappings are restored.
6. Restart either app mid-cycle; check recovery and state notifications.
7. Verify other connectors and Wi-Fi/Bluetooth/Helper are unchanged.

Keep the PR in Draft until tested with a Key Mapper 4.7 build on a device.
