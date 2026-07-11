# Automatist

A workflow-first AI utility for Android that transforms text content into structured, shareable outputs — entirely on-device with no backend.

Automatist is not a chatbot. It takes articles, meeting notes, and RSS feeds as input and produces summaries, social media posts, professional briefs, and strategic insights using pluggable AI providers.

**Automatist is free** — no ads, no subscriptions, no in-app purchases, and no feature paywalls. Every feature is available to everyone.

> **Proprietary software.** Automatist's source in this repository is currently **All Rights Reserved** and is not (yet) open source. It is published for transparency, security review, and personal evaluation only. See [LICENSE.md](LICENSE.md), [EULA.md](EULA.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Do not copy, fork, redistribute, or reuse this code without prior written permission.

---

## Features

### Article Transformer
Paste or share text from any app and transform it into:
- **Summary** — concise bulleted digest
- **Thread Draft** — Twitter/X-style multi-post thread
- **Professional Post** — LinkedIn-ready insight post

### Meeting Strategist
Input meeting title and notes to generate:
- **Meeting Brief** — executive summary with action items
- **Strategic Questions** — follow-up questions for deeper discussion

### Morning Brief (Automated)
Configure RSS feeds and get AI-generated digests on a schedule:
- Set trigger interval (every N hours or daily at a specific hour)
- Choose output format: summary, bullet insights, social post, or custom
- Target specific social platforms (LinkedIn, X, Facebook, Instagram, Threads) with platform-appropriate tone
- Background processing via WorkManager with optional notifications

### Workflow Builder (Custom Workflows)
Create reusable custom workflow templates with multiple data sources:
- **Template library** — 7 built-in templates (Morning Commute Brief, Competitor Monitor, Research Digest, etc.)
- **Multi-step builder** — guided sections for basics, trigger, actions, instructions, and output
- **11 action types** — Fetch URL, Paste Text, RSS Feed, Multi-RSS, API GET, Saved Notes, Previous Output, Weather, Route Time, Action Output, AI Prompt
- **Action Catalog** — full-screen categorized browser with search, readiness badges, and detail views
- **Flexible triggers** — manual, daily schedule, or weekly schedule
- **Output options** — briefing, social post, both, or custom format
- **Live execution screen** — stage-by-stage progress, action status, token usage, and duration
- **Run history** — all runs persisted with full output, token stats, and error details
- **Workflow portability** — import, export, and duplicate workflows (secret-free portable format)
- **Background scheduling** — via WorkManager for reliable scheduled execution
- **Schedule dashboard** — view next run times, last run status, and manage all schedules

### Cloud Sync (Google Drive)
- **Backup workflows** to Google Drive `appDataFolder` (private, app-scoped storage)
- **Restore workflows** from backup on any device
- **Secret-free exports** — API keys are never included in sync payloads
- **Sync status tracking** — last backup time, workflow count, connected account

### Additional Features
- **History** — all outputs saved locally with full search and detail view
- **Vault (Settings)** — AI provider profiles, API keys, service keys, legacy provider fallback
- **Saved Notes** — reusable note content that workflows can reference
- **Readiness System** — dynamic checks for action/workflow prerequisites with setup CTAs
- **Share Intent** — receive text from any app via Android share sheet
- **Multi-Provider** — switch between OpenAI, Anthropic, Gemini, a fully on-device LOCAL_AI mode (Gemini Nano or Gemma 3 1B int4), or a local demo mode
- **Provider Profiles** — named provider+model configurations, one set as default
- **Token Usage Tracking** — real token counts from OpenAI, Anthropic, and Gemini APIs

---

## Screenshots

<!-- Add screenshots here -->

---

## Architecture

```
feature/          UI layer (Compose screens + ViewModels)
domain/           Interfaces, models, enums (no dependencies)
data/             Implementations (Room, Retrofit, DataStore, providers)
platform/         OS concerns (WorkManager, secure storage)
di/               Hilt modules
ui/               Theme + navigation graph
```

- **Single-activity** with Jetpack Compose navigation
- **MVVM** — each screen backed by a `@HiltViewModel` with `StateFlow`
- **Provider router** — strategy pattern routes AI calls to the active provider/profile
- **Repository pattern** — Room-backed history and workflows with Flow-based reactivity
- **Readiness system** — dynamic prerequisite checks for actions and workflows

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Background | WorkManager |
| HTTP | Retrofit + OkHttp |
| Async | Coroutines + Flow |
| Serialization | Gson (Retrofit) + kotlinx.serialization (DataStore) |
| Cloud Sync | Google Drive API (appDataFolder) |
| Auth | Google Play Services Auth |
| On-device AI (system) | Google AI Edge AICore 0.0.1-exp01 (Gemini Nano) |
| On-device AI (download) | MediaPipe LLM Inference / tasks-genai (Gemma 3 1B int4) |

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15) | **Java:** 17

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/automatist.git
   cd automatist
   ```

2. Open in Android Studio and sync Gradle.

3. (Optional) Set up release signing:
   ```bash
   cp keystore.properties.example keystore.properties
   # Generate a keystore and fill in passwords — see keystore.properties.example for details
   ```

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

5. Configure an AI provider in **Settings**:
   - Create a provider profile (OpenAI, Anthropic, or Gemini) with your API key
   - Or use **Fake (Local Demo)** to explore without an API key
   - Optionally add service API keys (OpenWeatherMap, OpenRouteService) for weather/route actions

### API Keys

Automatist requires an API key for whichever AI provider you choose:

| Provider | Get a key at |
|----------|-------------|
| OpenAI | https://platform.openai.com/api-keys |
| Anthropic | https://console.anthropic.com/ |
| Gemini | https://aistudio.google.com/apikey |

Some workflow actions require additional service API keys:

| Service | Used By | Get a key at |
|---------|---------|-------------|
| OpenWeatherMap | Weather action | https://openweathermap.org/api |
| OpenRouteService | Route Time action | https://openrouteservice.org/ |

All keys are stored on-device only. No data leaves the device except direct API calls to the respective provider/service.

---

## Project Structure

```
app/src/main/java/com/automatist/app/
├── MainActivity.kt              # Launcher + share intent handler
├── ShareEntryActivity.kt        # Receives shared text from other apps
├── AutomatistApp.kt             # Application class (@HiltAndroidApp)
│
├── domain/                      # Business logic (pure Kotlin)
│   ├── actions/                 # Workflow action registry + metadata
│   ├── engine/                  # Workflow execution engine + state
│   ├── models/                  # Data classes + enums
│   ├── providers/               # ArticleTransformProvider interface
│   ├── readiness/               # Dynamic readiness evaluator
│   ├── repositories/            # History + Workflow repository interfaces
│   ├── sync/                    # Cloud sync models + interfaces
│   ├── templates/               # Built-in workflow templates
│   └── workflow/                # Workflow portability (import/export)
│
├── data/                        # Implementation layer
│   ├── local/                   # Room DB, DAO, entity, settings
│   ├── network/                 # RSS parser
│   ├── providers/               # AI provider implementations + router
│   ├── repositories/            # Room-backed history + workflow repos
│   └── sync/                    # Google Drive sync manager + local repo
│
├── feature/                     # Screens (Compose + ViewModel)
│   ├── article/                 # Article Transformer
│   ├── meeting/                 # Meeting Strategist
│   ├── brief/                   # Morning Brief config + results
│   ├── dashboard/               # Home screen
│   ├── history/                 # History list + detail
│   ├── notes/                   # Saved Notes manager
│   ├── vault/                   # Settings (profiles, API keys, service keys)
│   ├── sync/                    # Cloud sync (Google Drive backup)
│   └── workflow/                # Workflow builder, editor, run, templates,
│                                #   details, history, schedule, components
│
├── platform/
│   ├── automation/              # WorkManager workers (Brief + Workflow)
│   ├── notifications/           # Notification helper
│   ├── scheduling/              # Schedule manager
│   └── security/                # Secure API key storage
│
├── di/                          # Hilt modules
└── ui/                          # Navigation graph + theme
```

---

## Supported AI Providers

| Provider | Default Model | Notes |
|----------|--------------|-------|
| Fake | — | Local mock responses, no API key needed |
| OpenAI | gpt-3.5-turbo (overridable) | Bearer token auth |
| Anthropic | claude-3-haiku-20240307 (overridable) | x-api-key header |
| Gemini | gemini-1.5-flash | API key query param |
| LOCAL_AI | gemini-nano / gemma-3n-e2b | Fully on-device, no API key or internet needed |

All providers implement the same `ArticleTransformProvider` interface. Create named **provider profiles** (provider + model combinations) in Settings and assign them as defaults or per-workflow overrides.

---

## How It Works

### Content Transformation
```
User input → ViewModel → TransformProviderRouter → Active AI Provider → TransformResult → UI preview → User action (copy/share/save)
```

### Morning Brief Automation
```
WorkManager trigger → Load config → Fetch RSS (snippets only) → Parse top 5 items → Build prompt → AI provider → Save to history → Notify
```

### Share Intent
```
External app → Share text → ShareEntryActivity → Article Transformer (pre-filled)
```

### Cloud Sync
```
Sign in with Google → CloudSyncManager → Export workflows (secret-free) → Upload to Drive appDataFolder → Track sync status locally
```

---

## Design Principles

- **User always reviews before acting** — no auto-posting, auto-sharing, or auto-sending
- **Snippets only** — RSS parsing extracts titles and descriptions, never full articles
- **On-device first** — no backend server, no analytics; Google Drive sync is optional and user-initiated
- **Template-driven outputs** — transform types define output shape, not free-form generation
- **Secret-free portability** — workflow exports and cloud backups never include API keys
- **Idempotent workers** — Brief and Workflow runs are safe to retry

---

## Extending the codebase (internal)

> Automatist is proprietary (see [LICENSE.md](LICENSE.md)). These notes are
> for the project maintainer's internal reference and for reviewers with
> written permission. External pull requests and forks are not accepted.
> If you have an idea or a security report, email **support@automatist.cloud**.

### Adding a New AI Provider
1. Create API interface + models in `data/providers/{name}/`
2. Implement `ArticleTransformProvider` in `data/providers/`
3. Add `ProviderType` enum value in `domain/models/Types.kt`
4. Add Retrofit instance in `di/NetworkModule.kt`
5. Wire into `TransformProviderRouter`
6. Add key management UI in `feature/vault/`

### Adding a New Transform Type
1. Add enum value to `TransformType` in `domain/models/Types.kt`
2. Add system prompt in each provider's prompt builder
3. Add mock response in `FakeArticleTransformProvider`
4. Add UI option in the relevant feature screen

---

## Privacy

- All data stored locally on-device (Room database + DataStore)
- API keys never leave the device except in direct provider API calls
- Cloud sync (Google Drive) is optional and user-initiated — uses private `appDataFolder` scope
- Workflow backups are secret-free — API keys are stripped before sync/export
- No telemetry, analytics, or tracking
- No backend server
- RSS feeds fetched directly from source — no proxy

---

## App Icon Assets

The Automatist launcher icon uses Android adaptive icon layers (API 26+) with monochrome themed icon support (API 33+), plus legacy pre-composed bitmaps for older launchers.

### Background

The original icon artwork filled ~76% of the adaptive icon canvas, which caused the three circular nodes of the symbol to be cropped by launcher masking on Samsung One UI and other OEMs that apply aggressive circular or squircle masks. The fix was to reduce the artwork scale to 45% fill with 89px padding per side on the 432px adaptive canvas, keeping the design identical but ensuring full visibility under all launcher mask shapes including parallax-shifted states.

### Final approved sizing

| Parameter | Value |
|-----------|-------|
| Adaptive canvas | 432 x 432 px |
| Artwork fill | 45% of canvas width |
| Scaled artwork size | 254 x 254 px |
| Padding per side | 89 px |

### Android resource locations

| File | Location |
|------|----------|
| Foreground layer | `app/src/main/res/drawable/ic_launcher_foreground.png` |
| Background layer | `app/src/main/res/drawable/ic_launcher_background.png` |
| Monochrome layer | `app/src/main/res/drawable/ic_launcher_monochrome.png` |
| Adaptive XML | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` |
| Adaptive XML (round) | `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` |
| Legacy mdpi (48x48) | `app/src/main/res/mipmap-mdpi/ic_launcher.png` |
| Legacy hdpi (72x72) | `app/src/main/res/mipmap-hdpi/ic_launcher.png` |
| Legacy xhdpi (96x96) | `app/src/main/res/mipmap-xhdpi/ic_launcher.png` |
| Legacy xxhdpi (144x144) | `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` |
| Legacy xxxhdpi (192x192) | `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` |
| Play Store icon (512x512) | `play-store/play_store_icon_512.png` |

The Play Store icon is not an Android runtime resource. Upload it manually to Google Play Console during release.

### Source files

The master source image and all generated exports are kept in `icons/` at the project root for reference. The Android project resources in `res/` are the copies that ship with the app.

### Future changes

Do not regenerate or resize icons without testing on a real device with an aggressive launcher mask (e.g., Samsung One UI circular mask). The 45% fill ratio was chosen specifically to prevent cropping across Samsung, Pixel, and stock Android launchers. If the icon design changes, regenerate all assets from the new master and re-verify on-device before merging.

---

## License

Automatist is proprietary software. Copyright © 2026 Alexis Johnson. All Rights Reserved.

- [LICENSE.md](LICENSE.md) — proprietary source-code license (viewing and evaluation only; no redistribution or reuse)
- [EULA.md](EULA.md) — end-user licence agreement for the installed app
- [NOTICE.md](NOTICE.md) — short third-party attribution notice
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — detailed inventory of third-party components and their licenses
- Hosted legal pages: [Terms](https://automatist.cloud/terms.html) · [Privacy](https://automatist.cloud/privacy.html)

Third-party dependencies (AndroidX, Kotlin, OkHttp, Retrofit, Hilt, MediaPipe, Google Drive API, AICore, etc.) remain governed by their own licenses, as listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The Gemma model weights that the app can optionally download at runtime remain governed by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy); Automatist does not claim ownership of those weights.

For commercial licensing, partnership, or reuse enquiries, contact **support@automatist.cloud**.

---

## Trademarks

"Automatist" and the Automatist logo are trademarks of Alexis Johnson. "Android", "Google Play", "Gemma", and "Gemini" are trademarks of Google LLC. Automatist is not affiliated with, endorsed by, or sponsored by Google LLC.
