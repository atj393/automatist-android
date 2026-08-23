# ADR 002 — One provider interface, one router

**Status:** accepted · **Date:** 2026-05

## Context

Automatist supports OpenAI, Anthropic, Gemini, any OpenAI-compatible endpoint, Gemini Nano
through AICore, and offline Gemma through MediaPipe. These differ in authentication (bearer
token, custom header, query parameter, none), in transport (HTTPS vs. JNI), in latency
(hundreds of milliseconds vs. tens of seconds), and in context window by more than an order
of magnitude.

The workflow engine should not know any of that.

## Decision

Every provider implements one interface:

```kotlin
ArticleTransformProvider.transform(input: ArticleInput, type: TransformType): Result<TransformResult>
```

`TransformProviderRouter` implements that same interface and delegates. Resolution order is
fixed and total: explicit `profileId` on the input → the default profile row → the legacy
`activeProvider` setting.

## Alternatives considered

- **Branch per call site.** A `when (providerType)` wherever AI is invoked. Rejected — adding
  a provider becomes a grep-and-pray exercise, and cloud/on-device stop being interchangeable.
- **Per-provider interfaces with an adapter layer.** More faithful to each API's shape, but
  the adapters reintroduce exactly the branching the router removes.
- **Configuration-driven provider definitions.** Attractive until authentication and
  streaming differences have to be expressed as data.

## Consequences

- Adding a provider is a bounded change: an API interface, a provider class, a `ProviderType`
  value, a Retrofit instance, a routing case, and a key field.
- Profiles (provider + model + display name) are a user-facing concept precisely because the
  router made "which model" a first-class parameter rather than a global setting.
- The interface is the lowest common denominator. Provider-specific features that do not fit
  `transform()` — streaming, tool use, multi-turn — are not exposed, and adding them means
  widening the contract for everyone.
- On-device runtimes hide behind the same call, which is why a workflow can fall back from
  cloud to offline without the engine changing.
