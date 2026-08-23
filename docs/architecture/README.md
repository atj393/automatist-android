# Architecture decision records

Short records of the decisions that shaped Automatist, kept so the reasoning survives after
the discussion is forgotten. Each one states the context, the decision, what else was on the
table, and what it cost.

| ADR | Decision |
|---|---|
| [001](adr-001-no-backend.md) | No backend — the app is the whole system |
| [002](adr-002-provider-router.md) | One provider interface, one router |
| [003](adr-003-workmanager-scheduling.md) | WorkManager for scheduling, with honest guarantees |
| [004](adr-004-offline-token-budget.md) | Preflight token budget for on-device inference |

A high-level diagram of how these fit together is in the [main README](../../README.md#architecture).
