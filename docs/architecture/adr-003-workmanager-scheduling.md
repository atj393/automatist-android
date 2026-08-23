# ADR 003 — WorkManager for scheduling, with honest guarantees

**Status:** accepted · **Date:** 2026-05

## Context

The core promise is "recurring AI tasks from your phone" — a morning brief at 07:00, a
competitor digest every four hours. On a server this is a cron line. On Android it is a
negotiation with Doze, App Standby buckets, and OEM battery managers that are considerably
more aggressive than stock AOSP.

## Decision

Schedule through WorkManager: `PeriodicWorkRequestBuilder` with
`ExistingPeriodicWorkPolicy.UPDATE` and an initial delay computed to the next target
time-of-day, plus `OneTimeWorkRequestBuilder` for "Run now". Unique work names per workflow
keep re-scheduling idempotent.

Then say plainly, in the app and the README, that runs can be delayed.

## Alternatives considered

- **`AlarmManager` with exact alarms.** Genuinely precise, and requires
  `SCHEDULE_EXACT_ALARM` — a permission Google restricts to alarms and calendars. A digest
  generator does not qualify, and should not pretend to.
- **Foreground service.** Reliable and dishonest: a persistent notification and a wakelock for
  something that runs for thirty seconds a day.
- **Push-triggered execution.** Requires the backend that ADR 001 rejected.

## Consequences

- Runs are approximate. A daily 07:00 workflow may fire at 07:00, at 07:40, or when the
  device next leaves Doze. This is a product property, not a bug, and is documented as one.
- The UI shows computed "next run" and actual "last run" so drift is visible rather than
  mysterious.
- Survives reboots and process death without special handling.
- Users on heavily customised OEM builds may need to exempt the app from battery
  optimisation; no amount of correct code substitutes for that.
