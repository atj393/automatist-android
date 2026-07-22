<p align="center">
  <img src="docs/assets/readme/automatist-logo.png" width="112" alt="Automatist logo">
</p>

<h1 align="center">Automatist</h1>

<p align="center">
  <strong>Mobile-first AI automation for Android.</strong><br>
  Useful AI workflows from the phone you already carry — no server, no homelab, no always-on laptop.
</p>

<p align="center">
  Free • Open source • No ads • No subscriptions • No feature paywalls
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License: Apache-2.0"></a>
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/status-active%20development-brightgreen" alt="Active development">
</p>

---

## Project status

- **Source:** open source under **Apache-2.0**, in **active development**.
- **Google Play:** [Available on Google Play Store](https://play.google.com/store/apps/details?id=com.automatist.app) — or build from source (see [Build from source](#build-from-source)).
- **On-device AI:** availability depends on your device — see [Cloud, on-device & offline AI](#cloud-on-device--offline-ai).

## Why Automatist

Automatist turns the Android phone you already own into a small runner for recurring AI tasks — no cloud account to create, no server to host, no subscription to pay.

- **Automate recurring AI tasks** — summaries, briefs, social posts, and digests — on a schedule.
- **Combine sources** — articles, RSS feeds, URLs, REST APIs, saved notes, weather, and routes — then hand the result to AI.
- **No backend** — everything runs on your device; there is no Automatist server that receives your content.
- **Your AI, your keys** — bring your own cloud-provider keys, or run fully on-device with no key and no network.
- **You stay in control** — every workflow produces a concrete artifact you review before copying, sharing, or saving. Nothing is auto-posted.

## What you can build

Pick a template or start from an empty canvas, chain reusable actions, add AI instructions, then run it manually or on a schedule.

- **Morning Brief** — summarize your RSS feeds into a daily digest.
- **Article Transformer** — turn any article or shared text into a summary, thread, or professional post.
- **Meeting Strategist** — turn meeting notes into a brief with action items or strategic questions.
- **News to Social Posts** — fetch news and generate platform-tailored posts.
- **Morning Commute Brief** — combine weather, travel time, and headlines before you leave.
- **Custom** — e.g. a stock or flight tracker assembled from the REST-API action, or anything you build from the action catalog.

> Some actions require your own keys (AI providers; weather/route services), and some AI options require a compatible device or a one-time model download. Automatist does not bundle or pay for thi[...]

## Core capabilities

- **Workflow builder** with 11 action types: fetch URL, RSS, multi-RSS, REST API (GET), saved notes, previous-run output, another action's output, weather, route time, mid-workflow AI pass, and pa[...]
- **Templates + blank canvas**, plus secret-free workflow import/export.
- **Scheduling** — manual, daily, weekly, or interval — via Android WorkManager.
- **Provider profiles** — named provider + model configurations, assignable per workflow.
- **Transparent run logs** — stage-by-stage progress, estimated token usage, duration, and error details.
- **Optional Google Drive sync** — back up workflow definitions to your own private Drive app folder.

## Screenshots

<p align="center">
  <img src="docs/assets/readme/screenshot-templates.png" width="220" alt="Workflow templates">
  <img src="docs/assets/readme/screenshot-editor.png" width="220" alt="Workflow editor">
  <img src="docs/assets/readme/screenshot-workflow-details.png" width="220" alt="Workflow details and schedule">
  <img src="docs/assets/readme/screenshot-run-results.png" width="220" alt="Run results and log">
</p>

## Cloud, on-device & offline AI

| Mode | Providers / models | Where your prompt goes |
|---|---|---|
| **Cloud** | OpenAI, Anthropic, Google Gemini, or any user-configured OpenAI-compatible endpoint | Sent **directly** to the provider you choose, using **your** API key |
| **On-device (system)** | Gemini Nano via Android AICore | Stays on the device; requires a supported device / Android version |
| **Offline (downloadable)** | Compatible MediaPipe `.task` models — built-in Gemma 3 1B (int4), or a model you add | Stays on the device after a one-time download |

- Downloaded models are integrity-checked with **SHA-256**. For on-device and offline inference, **prompts never leave the device**; the model host only ever receives a file-download request.
- **`.gguf` and `.safetensors` are not supported** by the MediaPipe runtime, and not every model on Hugging Face is compatible — only MediaPipe `.task` language models. See [docs/local-model-imp[...]
- On-device availability and performance vary by device; nothing is guaranteed.

## Privacy & data ownership

- **No Automatist backend, no analytics, no tracking, no ads, no billing.**
- Cloud AI: your text goes **directly** to the provider you select, authenticated with your own key.
- On-device / offline AI: prompts stay on the device.
- External workflow requests (URLs, RSS, REST APIs, weather, routes) go only to the services **you** configure.
- Google Drive sync is **optional** and uses your private `appDataFolder`; the developer cannot access it.
- API keys are stored **locally** on the device.

See the [privacy policy draft](docs/legal/privacy-policy-draft.md) — *pending publication at `automatist.cloud/privacy`.*

## Quick start

For everyday use:

1. **[Get it on Google Play](https://play.google.com/store/apps/details?id=com.automatist.app)** or **build & install** from source (see below).
2. In **Settings**, choose **on-device AI** (no key needed) or add a **cloud provider** API key.
3. Open **Templates → Use Template**, or start from an empty workflow.
4. Add actions and an output instruction.
5. **Run** it once, or set a **schedule**.
6. Review the result and the run log.

## Build from source

```bash
git clone https://github.com/atj393/automatist-android.git
cd automatist-android
```

**Requirements:** Android Studio (latest stable), JDK 17, Android SDK (compileSdk/targetSdk 35, minSdk 26).

Create a `local.properties` file (gitignored) pointing at your SDK:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

Build, test, and lint:

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:testDebugUnitTest    # run unit tests
./gradlew :app:lintDebug            # run Android lint
./gradlew :app:assembleRelease      # release APK (requires your own keystore.properties)
```

Release signing reads `keystore.properties` (gitignored) — see `keystore.properties.example`. **Never commit signing credentials, keystores, or API keys.**

### Forking

Automatist is Apache-2.0, so forks are welcome. If you publish a fork:

- Use a different **`applicationId`** (not `com.automatist.app`).
- Configure your **own Google OAuth client** (your package name + signing-cert SHA-1) and enable the Drive API if you want cloud sync — see the [release checklist](docs/release/google-play-rele[...]
- Use your **own signing key** and **branding** — see [TRADEMARKS.md](TRADEMARKS.md).

## Supported providers & model formats

- **Cloud:** OpenAI, Anthropic, Google Gemini, and any OpenAI-compatible endpoint you configure (with your own base URL + key).
- **On-device:** Gemini Nano (Android AICore) on supported devices.
- **Offline:** MediaPipe `.task` LLMs — the built-in Gemma 3 1B (int4), or a compatible model you add via URL or a JSON manifest (HTTPS-only, SHA-256-verified). `.gguf` / `.safetensors` are **n[...]

## Scheduling & Android limitations

Schedules run through **WorkManager**. Android **Doze** and aggressive **OEM battery management** can **delay** background runs — sometimes by minutes or more. Automatist does **not** promise e[...]

## Architecture

- **Single-activity** Jetpack Compose app; **MVVM** with Hilt.
- **Room** (database v17) for history, workflows, notes, and provider profiles; **DataStore** for settings and keys.
- **WorkManager** for scheduled/background execution; **Retrofit + OkHttp** for network calls (HTTPS enforced).
- A pluggable **provider router** dispatches to the active cloud or on-device provider.

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md) · [SUPPORT.md](SUPPORT.md) · [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) · [CHANGELOG.md](CHANGELOG.md)
- [LICENSE](LICENSE) · [NOTICE](NOTICE) · [TRADEMARKS.md](TRADEMARKS.md) · [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- [Local model import](docs/local-model-import.md) · [Privacy policy (draft)](docs/legal/privacy-policy-draft.md)

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, build/test commands, and PR expectations (no secrets or model binaries in PRs), and the [Code of Conduct](CODE_OF_COND[...]

## Security

Please report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not open a public issue for security problems.

## Support

Automatist is free and open source. Use **GitHub Issues** for bugs and feature requests on [atj393/automatist-android](https://github.com/atj393/automatist-android/issues); for private matters se[...]

## License & branding

- **First-party source code:** [Apache License 2.0](LICENSE) — Copyright © 2026 Alexis Johnson (see [NOTICE](NOTICE)).
- **Third-party dependencies:** their own upstream licences — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **AI model weights** (e.g. Gemma, downloaded at runtime): their upstream model terms — the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and [Prohibited Use Policy](https://ai.googl[...]
- **Branding** — the Automatist name, logo, launcher icon, and store artwork are **not** granted by the code licence. See [TRADEMARKS.md](TRADEMARKS.md).

"Android", "Google Play", "Gemma", and "Gemini" are trademarks of Google LLC. Automatist is an independent project and is not affiliated with, endorsed by, or sponsored by Google LLC.
