# Third-Party Notices

Automatist's own source code is licensed under the Apache License, Version 2.0
(see [LICENSE](LICENSE) and [NOTICE](NOTICE)). Automatist also **uses** the
third-party components listed below. Each remains the property of its respective
copyright holder and is governed by its own licence; the Apache-2.0 licence on
Automatist's source does **not** alter or relicense any of them.

Where a third-party licence requires that its terms accompany redistribution, that
requirement is met by the attributions and upstream links below; full licence texts
are available at the URLs provided. This list focuses on **direct** dependencies and
notable transitive components; it is **not** an exhaustive inventory of every
transitive dependency. Resolve the full tree with
`./gradlew :app:dependencies` if a complete list is required.

---

## Runtime dependencies

### Android / AndroidX / Jetpack — Apache-2.0
Copyright © The Android Open Source Project. https://www.apache.org/licenses/LICENSE-2.0

- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx (+ transitive lifecycle-* / viewmodel-compose)
- androidx.activity:activity-compose
- androidx.compose (BOM), compose.ui:ui / ui-graphics / ui-tooling-preview, compose.material3:material3, compose.material:material-icons-extended
- androidx.navigation:navigation-compose
- androidx.room:room-runtime, room-ktx, room-compiler
- androidx.work:work-runtime-ktx
- androidx.datastore:datastore-preferences
- androidx.hilt:hilt-navigation-compose
- (transitive) androidx.sqlite, androidx.startup, androidx.collection, androidx.core, androidx.fragment, androidx.loader, androidx.annotation, androidx.arch.core

### Kotlin & Kotlin libraries — Apache-2.0
Copyright © JetBrains s.r.o. and Kotlin contributors. https://www.apache.org/licenses/LICENSE-2.0

- org.jetbrains.kotlin:kotlin-stdlib (+ -jdk8 / -common)
- org.jetbrains.kotlinx:kotlinx-coroutines-android / -core (+ transitive -guava, -reactive)
- org.jetbrains.kotlinx:kotlinx-serialization-json

### Dagger / Hilt — Apache-2.0
Copyright © Google LLC and Dagger contributors. https://www.apache.org/licenses/LICENSE-2.0

- com.google.dagger:hilt-android, hilt-compiler

### Square networking — Apache-2.0
Copyright © Square, Inc. https://www.apache.org/licenses/LICENSE-2.0

- com.squareup.retrofit2:retrofit, converter-gson
- com.squareup.okhttp3:okhttp, logging-interceptor
- com.squareup.okio:okio

### Gson — Apache-2.0
Copyright © Google LLC. https://www.apache.org/licenses/LICENSE-2.0

- com.google.code.gson:gson

### Google Play Services / Google Sign-In
Copyright © Google LLC.

- com.google.android.gms:play-services-auth (+ transitive play-services-base / -basement / -tasks / -fido / -auth-*)

Licensed under the Android Software Development Kit License Agreement —
https://developer.android.com/studio/terms

### Google Drive REST API client & Google API/HTTP/OAuth client — Apache-2.0
Copyright © Google LLC. https://www.apache.org/licenses/LICENSE-2.0

- com.google.apis:google-api-services-drive
- com.google.api-client:google-api-client-android (+ google-api-client)
- com.google.oauth-client:google-oauth-client
- com.google.http-client:google-http-client, google-http-client-gson, google-http-client-apache-v2
- com.google.auth:google-auth-library-credentials, google-auth-library-oauth2-http
- commons-codec:commons-codec

Use of the Drive API also requires compliance with the
[Google APIs Terms of Service](https://developers.google.com/terms).

### Google AI Edge AICore (client) — Google terms
Copyright © Google LLC.

- com.google.ai.edge.aicore:aicore

Inference is executed by the Android system service; the Gemini Nano model weights are
provided by the system and are not distributed by Automatist. Use is governed by
Google's terms for AICore / Gemini Nano.

### MediaPipe LLM Inference — Apache-2.0
Copyright © Google LLC. https://www.apache.org/licenses/LICENSE-2.0

- com.google.mediapipe:tasks-genai (+ transitive tasks-core)

### Notable transitive components (attention licences)
- com.google.protobuf:protobuf-javalite — **BSD-3-Clause** (Google). https://github.com/protocolbuffers/protobuf/blob/main/LICENSE
- org.reactivestreams:reactive-streams — **MIT-0 / CC0** (public-domain-like).
- com.google.guava:guava, io.grpc:grpc-context, io.opencensus:* — Apache-2.0.
- com.google.code.findbugs:jsr305, com.google.errorprone:error_prone_annotations, org.checkerframework:checker-qual, com.google.j2objc:j2objc-annotations, com.google.auto.value:auto-value(-annotations) — permissive (Apache-2.0 / BSD-style) annotation libraries.

---

## Build / test-only dependencies (not shipped in the APK/AAB)

- junit:junit — **Eclipse Public License 1.0** — https://www.eclipse.org/legal/epl-v10.html
- org.jetbrains.kotlinx:kotlinx-coroutines-test — Apache-2.0
- androidx.compose.ui:ui-tooling — Apache-2.0 (debug builds only)

These are test/debug tooling and are not distributed in the release binary.

---

## Cloud AI provider APIs (runtime calls only — no bundled SDK code)

Automatist calls these third-party HTTP APIs only when the user configures a cloud
profile. No provider SDK code is bundled; only the user's text, the system prompt, and
the user-supplied API key are transmitted.

- OpenAI — https://openai.com/policies
- Anthropic — https://www.anthropic.com/legal
- Google AI / Gemini API — https://ai.google.dev/terms
- A user-configured OpenAI-compatible endpoint — governed by that host's own terms.

---

## AI model weights (runtime-downloaded — NOT redistributed in this repository, NOT Apache-2.0)

Model weights are never part of the Automatist source distribution and are **not**
covered by the Apache-2.0 licence on the source. They remain under their upstream
model terms:

- **Gemma (e.g. `gemma3-1b-it-int4`)** — © Google LLC, governed by the
  [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and the
  [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy).
  Automatist only fetches the file over HTTPS; it grants no rights to the weights
  beyond the upstream Gemma licence.
- **Gemini Nano (via AICore)** — provided by the Android system service; not
  distributed by Automatist; governed by Google's AICore / Gemini Nano terms.
- **Qwen (e.g. Qwen2.5-0.5B) and other user-added models** — Automatist does **not**
  bundle these. Qwen appears in this repository **only as a test fixture / example**
  of a compatible external MediaPipe `.task` model a user may choose to import; it is
  not shipped with the app. Any model a user adds is downloaded from a host the user
  chooses and remains under that model's own upstream licence, which the user is
  responsible for reviewing.

Automatist does not claim ownership of, or a sublicensable right in, any third-party
model weights.

---

## Fonts

No fonts are bundled in this repository's source. If Inter or Instrument Serif are used
in website/marketing assets, they remain under the SIL Open Font License 1.1 (© their
respective project authors) — those assets are governed separately, not by this repo's
source licence.

---

## Updating this file

When a dependency is added, upgraded, or removed in `gradle/libs.versions.toml` or
`app/build.gradle.kts`, update this file in the same change set. This inventory is a
good-faith summary and is not a legal guarantee of completeness.
