# SleepManager Planning

Private development planning for [SleepManager](https://github.com/Baggio94/SleepManager).

## Current development cycle

The active development line is **SleepManager 0.7.0** on branch `dev/0.7.0`.

Use these files as the current sources of truth:

- [SleepManager 0.7.0 roadmap](./0.7.0.md) — current code status, bugs, architecture work, diagnostics and release blockers.
- [SleepManager 0.7.0 validation plan](./0.7.0-validation.md) — CI, emulator, real E2E and physical AYN Thor validation checklist.
- [Future backlog and integrations](./BACKLOG.md) — work intentionally deferred until the 0.7 validation gate is accepted.

The old 0.6.2 planning files are retained only as historical implementation records:

- [0.6.2 roadmap](./0.6.2.md)
- [0.6.2 implementation plan](./0.6.2-implementation-plan.md)

No new work should be planned against the 0.6.2 files.

## Release safety rule

The public/stable repository remains on **0.6.1** until the complete 0.7 validation gate passes and the user explicitly accepts the candidate.

The physical AYN Thor may be used for 0.7 validation, but **must be returned to the stable 0.6.1 build after the test session**. Never uninstall SleepManager or clear app data for testing.

## Architecture rules

New work should continue the architecture introduced during the 0.6.2 refactor:

- `SleepManagerService` stays an orchestrator rather than a business-logic container.
- Pure decisions belong in focused/testable Policy classes.
- Persistent/runtime ownership belongs in Stores.
- Android, binder and privileged actions belong in Controllers.
- UI reads prepared ViewModel/UiState instead of performing business calculations.
- Integration state changes must be ownership-aware and idempotent.
- Prefer small isolated commits with focused tests.
- Preserve stable 0.6.1 behavior unless a change is intentional and documented.

## Purpose

This repository is for internal planning, architecture notes and future feature work that should not be exposed in the public SleepManager issue tracker until it is ready.

Public bugs, user feature requests and community discussions can remain in the main SleepManager repository.
