# SleepManager Development Notes

This directory contains only documentation that is useful beside the code for the **current development release**.

## Current development cycle

The active development line is **SleepManager 0.7.0** on branch `dev/0.7.0`.

- [0.7.0 development status](./0.7.0.md) — implemented behavior, current release blockers and definition of done.
- [0.7.0 validation plan](./0.7.0-validation.md) — CI, emulator, real E2E and physical AYN Thor validation checklist.

Future feature planning, integration roadmaps, internal investigations and historical implementation plans are maintained outside the public repository.

## Release safety

Public stable remains **0.6.1** until the 0.7 validation gate is complete and the candidate is explicitly accepted.

Physical AYN Thor testing must preserve app data and Device Admin state. Do not uninstall SleepManager or clear app data during upgrade/rollback validation.
