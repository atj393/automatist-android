# Automatist — Third-Party Notices (Short)

Automatist ("the Software") is proprietary software of Alexis Johnson.
See [LICENSE.md](LICENSE.md) for the terms that apply to the original
Automatist source code, assets, and documentation.

The Software includes or depends on components provided by third parties
under their own licenses. Those components remain the property of their
respective owners and are not covered by Automatist's proprietary
license. The principal notices required by those components are
reproduced below. A detailed inventory is provided in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## Apache License 2.0 components

Automatist uses a number of libraries licensed under the Apache License,
Version 2.0. Copies of their NOTICE files (where provided upstream) are
reproduced in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The full
text of Apache License 2.0 is available at:

https://www.apache.org/licenses/LICENSE-2.0

Representative components:

- Kotlin Standard Library and Coroutines — © JetBrains s.r.o. and Kotlin
  contributors
- AndroidX / Jetpack libraries (Compose, Material3, Lifecycle, Room,
  WorkManager, DataStore, Navigation, Activity, Hilt Android,
  Hilt Compiler, Hilt Navigation Compose) — © The Android Open Source
  Project
- Dagger / Hilt — © Google LLC and Dagger contributors
- OkHttp — © Square, Inc.
- Retrofit — © Square, Inc.
- Gson — © Google LLC
- kotlinx.serialization — © JetBrains s.r.o. and Kotlin contributors
- Google API Client for Java / Google HTTP Client — © Google LLC
- Google Drive REST API client — © Google LLC
- Google Play Billing Library — © Google LLC (subject also to the
  Google Play Billing Library Terms)
- Google Play Services Auth — © Google LLC (subject also to the
  Android SDK License)
- Google AI Edge AICore (client library) — © Google LLC (subject also
  to the Google AI Edge / AICore terms)
- MediaPipe Tasks GenAI and MediaPipe Tasks Core — © Google LLC

## Third-party AI models (runtime-downloaded)

Automatist can optionally download and run third-party language models
on-device. These model files are **not** distributed with this
repository's source code and are **not** covered by the Automatist
proprietary license.

- **Gemma 3 1B (int4) and related Gemma derivatives** —
  © Google LLC. Distributed and used under the Gemma Terms of Use and
  the Gemma Prohibited Use Policy. Your use of these model weights is
  governed by those terms in addition to the Automatist EULA.
  - Gemma Terms of Use: https://ai.google.dev/gemma/terms
  - Gemma Prohibited Use Policy: https://ai.google.dev/gemma/prohibited_use_policy

- **Gemini Nano (via Google AI Edge / AICore)** — provided by the
  Android system service. Not distributed by the Automatist project.
  Use is governed by Google's terms applicable to AICore / Gemini Nano.

Automatist does not claim ownership of Gemma, Gemini Nano, or any other
third-party model weights. The original Gemma terms, prohibited use
policy, and notices continue to apply to any Gemma weights downloaded
through Automatist.

## Fonts

If redistributed with Automatist marketing or web assets:

- **Inter** — © The Inter Project Authors, licensed under the SIL Open
  Font License 1.1.
- **Instrument Serif** — © The Instrument Serif Project Authors,
  licensed under the SIL Open Font License 1.1.

## Trademarks

"Google Play", "Android", "Gemma", "Gemini", and related marks are
trademarks of Google LLC. Automatist is not affiliated with, endorsed
by, or sponsored by Google LLC.
