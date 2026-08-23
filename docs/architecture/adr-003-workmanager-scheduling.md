# ADR 003 — WorkManager for scheduling, with honest guarantees

**Status:** accepted · **Date:** 2026-05

## Context

The core promise is "recurring AI tasks from your phone" — a morning brief at 07:00, a
competitor digest every four hours. On a server this is a cron line. On Android it is a
negotiation with Doze, App Standby buckets, and OEM battery managers that are considerably
more aggressive than stock AOSP.

## Decision

Schedule through WorkManager. There are two mechanisms, because the two features want
different things:

**Custom workflows — a chain of self-rescheduling one-shots.** `ScheduleManager` enqueues a
`OneTimeWorkRequestBuilder` with `setInitialDelay()` computed to the next occurrence, under a
unique work name with `ExistingWorkPolicy.REPLACE`. After a scheduled run completes,
`WorkflowWorker` calls `rescheduleNext()` to enqueue the following one.

A periodic request cannot express "every day at 07:00" — it expresses "every 24 hours from
whenever this was registered", which drifts and cannot follow a changed target time without
being torn down. Recomputing the delay per run is what makes an actual time-of-day schedule
possible.

The cost is that the chain can break: if the process is killed between "run finished" and
"next one enqueued", nothing re-arms it. `reconcile()` covers that by checking on app start
and after reboot whether each non-manual template still has pending work, and re-enqueueing
if not.

Scheduled runs also carry a `NetworkType.CONNECTED` constraint. An overnight run that fires
with no connectivity collapses an RSS action into an empty fetch and reports "feed returned
no content", which is a misleading way to say "no internet at 3 a.m.". Manual runs
deliberately carry no such constraint, so a user tap never silently queues.

**Morning Brief — a genuine periodic request.** `BriefViewModel` uses
`PeriodicWorkRequestBuilder` with `ExistingPeriodicWorkPolicy.UPDATE` under the unique name
`SynthesizerWorker_Periodic`. It does not set a target-time initial delay, so its interval is
relative to registration. `SynthesizerWorker_OneTime` handles "Run now".

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
- Survives reboots and process death — but for custom workflows that is `reconcile()` doing
  the work, not WorkManager alone, because a broken one-shot chain has nothing to resume.
- Users on heavily customised OEM builds may need to exempt the app from battery
  optimisation; no amount of correct code substitutes for that.
