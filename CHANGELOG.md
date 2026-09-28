# Changelog

All notable changes to Automatist are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/).

For detailed history of individual changes, see the Git commit log.

## [1.1.3] — 2026-09-29

`versionName = "1.1.3"`, `versionCode = 10`. Ships a security fix that landed after the
1.1.2 version bump and was not previously released or logged here.

### Fixed
- `KeystoreSecureStorage` was backed by a plain (unencrypted) DataStore Preferences file
  despite its name. It now uses `EncryptedSharedPreferences` with an Android Keystore
  AES256-GCM master key, so API/service keys are actually encrypted at rest.
- `backup_rules.xml` and `data_extraction_rules.xml` excluded sensitive files under the
  "sharedpref" domain, but DataStore Preferences files live under `filesDir/datastore/`
  (the "file" domain), so the exclusions never matched anything. Domains corrected so the
  exclusions take effect.

## [1.1.2] — 2026-07-22

`versionName = "1.1.2"`, `versionCode = 9`. Supersedes `versionCode 8`, which was built and
verified locally but never uploaded. Same Android 16 targeting as 1.1.1, plus fixes for two
of the three Play Console pre-launch warnings raised against the earlier `versionCode 7`
(targetSdk 35) upload.

### Fixed
- `Theme.kt` set `window.statusBarColor` on every recomposition — deprecated on API 35+ and
  in direct conflict with `enableEdgeToEdge()` (edge-to-edge expects transparent system bars,
  not a manually painted status bar color). Removed; `isAppearanceLightStatusBars` (icon
  color, not deprecated) is unchanged. Addresses both the "deprecated edge-to-edge API" and
  "edge-to-edge may not display for all users" pre-launch warnings.
- Enabled `android.r8.optimizedResourceShrinking=true` (`gradle.properties`), per the
  official AGP 8.12+ opt-in (this project is on AGP 8.13.2); requires `isShrinkResources =
  true`, already set. Addresses the "optimised resource shrinking isn't enabled" warning.

### Not changed
- Play Console also suggested upgrading the Android Gradle Plugin to 9.0+. Not done here —
  it is a major-version migration (built-in Kotlin support, KMP plugin changes, API removals)
  out of scope for this release; AGP 8.13.2 already compiles cleanly against compileSdk 36.

## [1.1.1] — 2026-07-22

`versionName = "1.1.1"`, `versionCode = 8`. Targets Android 16 (API level 36); supersedes
`versionCode 7`, which was already uploaded to a Play testing track and cannot be reused.

### Changed
- `compileSdk`/`targetSdk` raised from 35 to 36 (Android 16). `minSdk` (26), `applicationId`
  (`com.automatist.app`), and the Room database (`automatist.db`, v17) are unchanged.
- `ArticleScreen`'s result view and `CloudSyncScreen` now scroll their content
  (`verticalScroll`), matching the pattern already used elsewhere (e.g. `MeetingScreen`).
  Android 16 removes orientation/aspect-ratio/resizability restrictions on large screens,
  which would otherwise expose these two screens' non-scrolling layouts as content-clipping
  bugs on tablets, foldables, or split-screen windows.
- `SynthesizerWorker` (Morning Brief) now promotes itself to a `dataSync` foreground service
  during RSS fetch + AI transform, matching the protection `WorkflowWorker` already had. This
  worker can run on-device MediaPipe inference for up to ~120s and had no protection from
  Android 15/16's stricter background-execution limits.

### Notes
- Edge-to-edge, predictive back, and manifest/permission surfaces were audited against
  Android 16 behavior changes and found already compatible — no code changes required there.
- No user data is affected: Room stays at v17, no DataStore keys changed, no migration
  required.

## [1.1.0] — 2026-07-13

`versionName = "1.1.0"`, `versionCode = 7`. The free, open-source release, prepared for
Google Play closed testing (supersedes the `1.0.0` / `versionCode 6` testing build).

### Added
- Import compatible on-device **MediaPipe `.task`** models via an app-managed manifest, in
  addition to the bundled downloadable model. (`.gguf`/`.safetensors` are not supported;
  compatibility depends on the model — not every third-party model will work.)
- Clearer battery-optimization guidance for scheduled workflows on the Schedule Status
  screen, to improve on-time execution. (The system may still adjust scheduled timing.)

### Changed
- Automatist is now **free** — every feature is available to all users. There are no ads,
  no subscriptions, no in-app purchases, and no feature paywalls.
- First-party source code is now licensed under the **Apache License 2.0** (previously
  proprietary). Third-party dependencies keep their upstream licences; AI model weights
  remain under their upstream model terms; branding is covered by `TRADEMARKS.md`.

### Removed
- Google Play Billing and the paid "Pro" tier: the Billing dependency, `BillingManager`,
  purchase/restore/acknowledge logic, the Upgrade screen and route, the Pro/Free badge,
  the activation-limit dialog, and the product-access/entitlement layer.
- The one-active-workflow limit for free users (multiple workflows can be enabled freely).

### Notes
- No user data is affected: existing workflows, history, notes, profiles, schedules, API
  keys, and downloaded models are preserved. The Room database (`automatist.db`, v17) and
  `applicationId` (`com.automatist.app`) are unchanged; no migration is required.
- A legacy `product_access` preference file may remain inert on upgraded devices; it is no
  longer read.

## History before the open-source release

Automatist was previously distributed as a proprietary app that used Google Play Billing
for an optional one-time "Pro" upgrade. That functionality has been fully removed. Earlier
change detail lives in the Git history of this repository.
