# Third-Party Notices

Automatist is proprietary software (see [LICENSE.md](LICENSE.md)). It
uses the following third-party components. Each component remains the
property of its respective copyright holder and is governed by its own
license. Nothing in Automatist's proprietary license alters the terms
of any third-party license listed here.

Where a third-party license requires that its terms accompany
redistribution, that requirement is satisfied by the links and summaries
below; copies of the full license texts are also available at the URLs
provided.

---

## 1. Android / AndroidX / Jetpack (Apache License 2.0)

Copyright © The Android Open Source Project.

- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.lifecycle:lifecycle-viewmodel-compose
- androidx.activity:activity-compose
- androidx.compose.ui:ui, ui-graphics, ui-tooling-preview, ui-test-junit4, ui-test-manifest
- androidx.compose.material3:material3
- androidx.compose.material:material-icons-extended
- androidx.compose:compose-bom
- androidx.navigation:navigation-compose
- androidx.room:room-runtime, room-ktx, room-compiler
- androidx.work:work-runtime-ktx
- androidx.datastore:datastore-preferences, datastore
- androidx.hilt:hilt-navigation-compose, hilt-compiler
- androidx.test.ext:junit
- androidx.test.espresso:espresso-core

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 2. Kotlin and Kotlin Coroutines (Apache License 2.0)

Copyright © JetBrains s.r.o. and Kotlin contributors.

- org.jetbrains.kotlin:kotlin-stdlib
- org.jetbrains.kotlinx:kotlinx-coroutines-core
- org.jetbrains.kotlinx:kotlinx-coroutines-android
- org.jetbrains.kotlinx:kotlinx-coroutines-play-services
- org.jetbrains.kotlinx:kotlinx-serialization-json

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 3. Dagger and Hilt (Apache License 2.0)

Copyright © Google LLC and Dagger contributors.

- com.google.dagger:hilt-android
- com.google.dagger:hilt-compiler

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 4. Square networking libraries (Apache License 2.0)

Copyright © Square, Inc.

- com.squareup.retrofit2:retrofit
- com.squareup.retrofit2:converter-gson
- com.squareup.okhttp3:okhttp
- com.squareup.okhttp3:logging-interceptor

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 5. Gson (Apache License 2.0)

Copyright © Google LLC.

- com.google.code.gson:gson

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 6. Google Play Billing Library

Copyright © Google LLC.

- com.android.billingclient:billing
- com.android.billingclient:billing-ktx

License: Google Play Billing Library Terms of Service —
https://developer.android.com/google/play/billing/play-billing-library-license

Use of the library also requires compliance with the Google Play
Developer Program Policies.

## 7. Google Play Services / Google Sign-In

Copyright © Google LLC.

- com.google.android.gms:play-services-auth

License: Android Software Development Kit License Agreement —
https://developer.android.com/studio/terms

## 8. Google Drive REST API Client

Copyright © Google LLC.

- com.google.api-client:google-api-client-android
- com.google.apis:google-api-services-drive
- com.google.http-client:google-http-client-gson

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

Use of the Drive API also requires compliance with the
[Google APIs Terms of Service](https://developers.google.com/terms).

## 9. Google AI Edge AICore (client)

Copyright © Google LLC.

- com.google.ai.edge.aicore:aicore

License: Google AI Edge / AICore client license terms (see upstream
artifact metadata and the AICore documentation). Inference is executed
by the Android system service; model weights (Gemini Nano) are provided
by the system and are not distributed by Automatist. Use is governed
by Google's terms applicable to AICore and Gemini Nano.

## 10. MediaPipe LLM Inference

Copyright © Google LLC.

- com.google.mediapipe:tasks-genai
- com.google.mediapipe:tasks-core

License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

## 11. Gemma model weights (runtime-downloaded, not redistributed in source)

Copyright © Google LLC.

- gemma3-1b-it-int4 (and any other Gemma model Automatist may
  optionally download at runtime)

License: **Gemma Terms of Use and Gemma Prohibited Use Policy** apply.
Automatist acts as an installer that fetches the model file over
HTTPS; it does not grant any rights to the model weights beyond those
granted by the upstream Gemma license.

- Gemma Terms of Use: https://ai.google.dev/gemma/terms
- Gemma Prohibited Use Policy:
  https://ai.google.dev/gemma/prohibited_use_policy

Users who download Gemma model weights through Automatist are bound by
the Gemma Terms of Use and Prohibited Use Policy. Automatist does not
claim ownership of, or a sublicensable right in, the Gemma weights.

## 12. AndroidX / JUnit test libraries (Apache License 2.0 / EPL 1.0)

- junit:junit — Eclipse Public License 1.0 —
  https://www.eclipse.org/legal/epl-v10.html
- androidx.test.ext:junit, androidx.test.espresso:espresso-core —
  Apache License 2.0

## 13. Cloud AI provider APIs (runtime-only, no bundled code)

Automatist calls the following third-party HTTP APIs when the user
configures them. No SDK code from these providers is bundled in the
Automatist APK; only the user's own text, the system prompt, and the
user-supplied API key are transmitted.

- OpenAI — https://openai.com/policies
- Anthropic — https://www.anthropic.com/legal
- Google AI / Gemini API — https://ai.google.dev/terms

---

## Updating this file

When a dependency is added, upgraded, or removed in
`gradle/libs.versions.toml` or `app/build.gradle.kts`, update this file
within the same change set. License summaries here are required by
several of the upstream licenses and must remain accurate.
