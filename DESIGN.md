# SleepManager product / UI rules

- One main screen.
- Material 3.
- Dynamic Color where available.
- Follow system light / dark mode automatically.
- Keep technical implementation details away from normal users.
- Every setting explains what happens on wake.
- Restore only what SleepManager changed.
- No root / Shizuku controls in v1.
- Airplane Mode remains disabled until a safe no-ADB method is validated.
- Wi-Fi / Bluetooth are visually unavailable when the helper is absent.
- Syncthing is visually unavailable when no compatible build is detected.
- Future Airplane Mode logic should automatically disable redundant Wi-Fi / Bluetooth options
  when the device's Airplane Mode behavior already handles those radios.
