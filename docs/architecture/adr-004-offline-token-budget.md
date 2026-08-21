# ADR 004 — Preflight token budget for on-device inference

**Status:** accepted · **Date:** 2026-06

## Context

Offline inference runs Gemma 3 1B (int4) through MediaPipe LLM Inference, which is a JNI
wrapper over a native runtime. Handing that runtime a prompt longer than the model's context
does not raise a Kotlin exception. It aborts the process with SIGABRT.

This is a categorically different failure from an HTTP 400. There is nothing to catch, no
`Result.failure` to return, and no way to retry — the app is simply gone, mid-workflow,
with no diagnostics beyond a native crash log.

Workflow inputs are user-controlled and unbounded: an RSS action can merge a dozen feeds.

## Decision

Enforce the budget before the native call, in two independent layers.

1. **Character-level truncation.** `LocalPromptBuilder` truncates each source against the
   catalogue's `contextWindowChars` (2500 for the downloadable model) while assembling the
   prompt.
2. **Token-level preflight.** `MediaPipeInferenceEngine` estimates tokens and rejects the call
   outright if the estimate exceeds `MAX_INPUT_TOKENS`.

```
MAX_TOTAL_TOKENS            = 1536
MIN_OUTPUT_RESERVE_TOKENS   =  256
MAX_INPUT_TOKENS            = 1280   // total − reserve
```

The reserve exists because input and output share one budget: a prompt that fits exactly
leaves no room to answer.

## Alternatives considered

- **Catch and retry smaller.** Not available. The failure is a native abort.
- **Token-level guard only.** One estimator between user input and an unrecoverable crash is
  too few. The estimate is approximate (~3 chars/token), so the character cap keeps inputs far
  enough from the ceiling that estimator error cannot cross it.
- **Trust the model's own context handling.** Empirically, it does not have any.

## Consequences

- Long inputs are visibly truncated offline. Fidelity is traded for the process staying alive,
  and the offline model's context is small enough that the trade is unavoidable.
- The estimator is deliberately conservative; some prompts that would have fit are rejected.
- The guard must not be bypassed or "optimised" — it is the only thing between user input and
  a native abort, which is why it is called out in `CLAUDE.md` as a hard rule.
- Cloud providers use the same builder but far larger windows, so truncation is rarely
  observable there.
