# Contributing to Automatist

Thanks for your interest in improving Automatist! Automatist is a free, open-source
Android app licensed under the Apache License 2.0.

## Licensing of contributions (inbound = outbound)

By submitting a contribution (pull request, patch, or otherwise), **you agree that your
contribution is licensed under the same [Apache License 2.0](LICENSE)** that covers the
project. No separate CLA is required.

Please only contribute code you have the right to license this way:

- Do **not** submit employer-owned or confidential code without authorization.
- Do **not** paste code under an incompatible licence (e.g. GPL) or copied from sources
  you cannot relicense under Apache-2.0.
- If a change adds or updates a dependency, update `gradle/libs.versions.toml` /
  `app/build.gradle.kts` **and** [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) in the
  same PR.

## What not to include in a PR

- **No secrets** — API keys, `keystore.properties`, `*.jks`/`*.keystore`, OAuth client
  secrets, service-account JSON, or tokens. These are gitignored; keep it that way.
- **No model binaries** — `.task`, `.litertlm`, `.gguf`, `.safetensors`, or other model
  weights. Model files are downloaded at runtime, never committed.
- **No large binaries** or generated build output (`build/`, `.gradle/`).
- **No brand-impersonating changes** — see [TRADEMARKS.md](TRADEMARKS.md) for fork/brand
  guidance.

## Development setup

- Android Studio (latest stable) + JDK 17.
- Create `local.properties` with your SDK path (this file is gitignored):
  ```
  sdk.dir=/path/to/Android/Sdk
  ```
- Release signing is optional for development; see the README and
  `keystore.properties.example` (never commit real signing credentials).

## Build & test commands

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests
./gradlew :app:lintDebug           # Android lint
./gradlew :app:assembleDebug       # build the debug APK
```

Please run the unit tests and lint before opening a PR. Add tests for behavior changes
where practical (the suite is JVM-only; UI/WorkManager paths are verified on-device).

## Branch & commit expectations

- Branch from `main`; keep PRs focused on a single change.
- Write clear commit messages (a concise imperative subject; body explaining *why*).
- Keep changes minimal and traceable to the PR's purpose; avoid unrelated refactors.
- Match the surrounding code style and conventions.

## Review

Reviews aim to be constructive and respectful — see the
[Code of Conduct](CODE_OF_CONDUCT.md). Maintainer bandwidth is limited and there is no
guaranteed response time.

## Reporting bugs & security issues

- Bugs and feature requests: open a **GitHub Issue** (see the issue templates).
- Security vulnerabilities: **do not** open a public issue — follow [SECURITY.md](SECURITY.md).
