# Trademarks & Branding

The Automatist first-party source code is licensed under the Apache License,
Version 2.0 (see [LICENSE](LICENSE)). **That licence covers source code — it does
not grant any right to use the Automatist name, logo, or brand assets** in a way
that could suggest affiliation with or endorsement by the Automatist project.

This is consistent with Section 6 ("Trademarks") of the Apache License 2.0, which
does not grant trademark rights.

## What the Apache-2.0 licence does grant

- The right to use, modify, and redistribute the **source code** (including the
  normal Kotlin/Compose UI source), subject to the Apache-2.0 terms.
- Normal UI source that happens to contain the product name (strings, class names,
  package names) is part of the source code and is covered by Apache-2.0. It is
  **not** excluded merely because it mentions "Automatist".

## Brand assets NOT granted under Apache-2.0

The following are Automatist brand assets. They are provided in the repository for
building the official app, but the Apache-2.0 licence does **not** grant permission
to reuse them to represent your own build or to imply endorsement:

- The **"Automatist" name** and the app logo/wordmark.
- Launcher icon artwork:
  - `app/src/main/res/drawable/ic_launcher_foreground.png`
  - `app/src/main/res/drawable/ic_launcher_background.png`
  - `app/src/main/res/drawable/ic_launcher_monochrome.png`
  - `app/src/main/res/mipmap-*/ic_launcher.png` (all densities)
  - the adaptive-icon definitions in `app/src/main/res/mipmap-anydpi-v26/` insofar
    as they compose the above artwork
- Icon source/master files in `icons/`
- Store artwork: `play-store/play_store_icon_512.png`
- Any screenshots, feature graphics, promotional graphics, or website marketing
  assets bearing the Automatist identity (wherever they live).

(These marks are used to identify the official project. No claim of trademark
**registration** is made here; unregistered rights may still apply under
applicable law.)

## Guidance for forks and redistributions

Forks are welcome under Apache-2.0. If you publicly distribute a modified build,
please:

- Use the source under the Apache-2.0 terms (retain `LICENSE`/`NOTICE`, state your
  changes — see Apache-2.0 §4).
- **Rename your app** and use a **different application ID** (not `com.automatist.app`),
  a **different launcher icon and logo**, your **own signing key**, and your **own
  store listing / identity**, unless you have written permission to use the
  Automatist brand.
- Do **not** present your fork as the official Automatist release, and do not use the
  Automatist name/logo in a way that implies endorsement by or affiliation with the
  Automatist project.

The official Automatist release published on Google Play is built and signed only by
the project owner; the official signing key is never published.

## Third-party marks

"Google Play", "Android", "Gemma", and "Gemini" are trademarks of Google LLC.
Automatist is an independent project and is not affiliated with, endorsed by, or
sponsored by Google LLC. Model weights (e.g. Gemma) remain under their upstream terms
(see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)).
