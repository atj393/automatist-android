# ADR 001 — No backend

**Status:** accepted · **Date:** 2026-05

## Context

Automatist sends user text to AI providers on a schedule. The obvious architecture is a thin
server: it holds provider keys, proxies requests, stores history, and enables sync across
devices. Nearly every comparable product is built that way.

That server would also hold every article, meeting note, and RSS digest its users ever
processed, plus the credentials to bill against. For a free, single-developer, open-source
project it is simultaneously the most attractive thing to attack, the only recurring cost,
and the reason a subscription would eventually be necessary.

## Decision

There is no Automatist server. The app talks directly to whichever provider the user
configured, with the user's own key, from the user's own device. All state lives in Room and
DataStore on the device.

## Alternatives considered

- **Thin proxy for keys.** Removes the "paste an API key" step, at the cost of holding
  everyone's traffic and my credentials. Rejected — it converts a privacy property into a
  liability and forces metering.
- **Optional sync server.** Same breach surface, only opt-in. Rejected in favour of Google
  Drive `appDataFolder`, where the user's own storage holds the backup and I never see it.
- **Local-only with no export.** Simplest, but loses everything when a phone is replaced.

## Consequences

- Users must supply their own API key, or run entirely offline. This is a real onboarding
  cost and the most common source of setup confusion; the readiness system exists largely to
  soften it.
- No cross-device sync without Drive, and no server-side migration path — schema changes ship
  as Room migrations to clients (currently at v17).
- No hosting bill, no breach surface, and no structural pressure toward a paywall. The app
  can credibly stay free because it does not cost anything to run.
- Backups are secret-free by construction: `WorkflowPortabilityManager` strips keys before
  anything is serialised.
