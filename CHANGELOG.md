# Changelog

All notable changes to Automatist are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/).

For detailed history of individual changes, see the Git commit log.

## [Unreleased] — Free & open source

The first free, open-source release. (The `versionName`/`versionCode` for this release are
set at release time and are not bumped in this change.)

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
