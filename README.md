# Synapse

A workflow-first AI utility for Android that transforms text content into structured, shareable outputs — entirely on-device with no backend.

Synapse is not a chatbot. It takes articles, meeting notes, and RSS feeds as input and produces summaries, social media posts, professional briefs, and strategic insights using pluggable AI providers.

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

### Additional Features
- **History** — all outputs saved locally with full search and detail view
- **Vault** — manage AI provider selection and API keys
- **Share Intent** — receive text from any app via Android share sheet
- **Multi-Provider** — switch between OpenAI, Anthropic, Gemini, or a local demo mode

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
- **Provider router** — strategy pattern routes AI calls to the active provider
- **Repository pattern** — Room-backed history with Flow-based reactivity

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

**Min SDK:** 26 (Android 8.0) | **Target SDK:** 34 (Android 14) | **Java:** 17

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/synapse.git
   cd synapse
   ```

2. Open in Android Studio and sync Gradle.

3. Build and run:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

4. Configure an AI provider in the **Vault** screen:
   - Select a provider (OpenAI, Anthropic, or Gemini)
   - Enter your API key
   - Or use **Fake (Local Demo)** to explore without an API key

### API Keys

Synapse requires an API key for whichever provider you choose:

| Provider | Get a key at |
|----------|-------------|
| OpenAI | https://platform.openai.com/api-keys |
| Anthropic | https://console.anthropic.com/ |
| Gemini | https://aistudio.google.com/apikey |

Keys are stored on-device only. No data leaves the device except direct API calls to your selected provider.

---

## Project Structure

```
app/src/main/java/com/synapse/app/
├── MainActivity.kt              # Launcher + share intent handler
├── ShareEntryActivity.kt        # Receives shared text from other apps
├── SynapseApp.kt                # Application class (@HiltAndroidApp)
│
├── domain/                      # Business logic (pure Kotlin)
│   ├── models/                  # Data classes + enums
│   ├── providers/               # ArticleTransformProvider interface
│   └── repositories/            # HistoryRepository interface
│
├── data/                        # Implementation layer
│   ├── local/                   # Room DB, DAO, entity, settings
│   ├── network/                 # RSS parser
│   ├── providers/               # AI provider implementations + router
│   └── repositories/            # Room-backed history repository
│
├── feature/                     # Screens (Compose + ViewModel)
│   ├── article/                 # Article Transformer
│   ├── meeting/                 # Meeting Strategist
│   ├── brief/                   # Morning Brief config + results
│   ├── dashboard/               # Home screen
│   ├── history/                 # History list + detail
│   └── vault/                   # Provider & API key management
│
├── platform/
│   ├── automation/              # SynthesizerWorker (Morning Brief)
│   └── security/                # Secure API key storage
│
├── di/                          # Hilt modules
└── ui/                          # Navigation graph + theme
```

---

## Supported AI Providers

| Provider | Model | Notes |
|----------|-------|-------|
| Fake | — | Local mock responses, no API key needed |
| OpenAI | gpt-3.5-turbo | Bearer token auth |
| Anthropic | claude-3-haiku | x-api-key header |
| Gemini | gemini-1.5-flash | API key query param |

All providers implement the same `ArticleTransformProvider` interface. Switch between them at runtime from the Vault screen.

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

---

## Design Principles

- **User always reviews before acting** — no auto-posting, auto-sharing, or auto-sending
- **Snippets only** — RSS parsing extracts titles and descriptions, never full articles
- **On-device everything** — no backend server, no cloud storage, no analytics
- **Template-driven outputs** — transform types define output shape, not free-form generation
- **Idempotent worker** — Morning Brief runs are safe to retry

---

## Contributing

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
- No telemetry, analytics, or tracking
- No backend server
- RSS feeds fetched directly from source — no proxy

---

## License

<!-- Add your license here -->
