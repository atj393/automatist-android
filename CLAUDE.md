# Automatist

> **Agent note:** [AGENTS.md](AGENTS.md) is an archived, full-length copy of an earlier
> version of this file (including the removed Data Flow and Navigation Routes sections).
> This file is canonical — if the two conflict, follow this one.

## Project Identity

**Type:** Workflow-first AI utility (not chatbot, not agent)

**Purpose:** Transform text content (articles, meeting notes, RSS feeds) into structured outputs (summaries, social posts, briefs) using pluggable AI providers — all on-device, no backend.

**Target Users:** Professionals who consume content and need quick, polished outputs for sharing or review.

**Core Principles:**
- **Output-first** — every workflow produces a concrete, usable artifact
- **Template-driven** — transform types define the output shape, not free-form chat
- **Manual-final-action** — user always reviews before copy/share/save; no auto-posting
- **No scraping / no background automation** — only the Morning Brief worker runs in background; RSS fetches snippets only (title + description), never full articles
- **Privacy-first** — no backend server; all data stays on-device; API keys stored locally

---

## Technical Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Local DB | Room |
| Preferences | DataStore |
| Background | WorkManager |
| HTTP | Retrofit + OkHttp |
| Async | Kotlin Coroutines + Flow |
| Serialization | Gson (Retrofit) + kotlinx.serialization (DataStore) |
| Billing | Google Play Billing Library (v7.0.0) |
| Cloud Sync | Google Drive API (appDataFolder) |
| Auth | Google Play Services Auth |
| On-device AI (system) | Google AI Edge AICore (0.0.1-exp01) — Gemini Nano via Android system service |
| On-device AI (download) | MediaPipe LLM Inference (tasks-genai) — Gemma 3 1B int4 via app download |

**Build:** Gradle KTS, version catalog (`libs.versions.toml`), compile/target SDK 35, min SDK 26, Java 17.

---

## Architecture

**Single-activity** app with Jetpack Compose navigation.

```
┌─────────────────────────────────────────────────┐
│  feature/                                       │
│   article/  meeting/  brief/  dashboard/        │
│   history/  vault/    notes/  workflow/          │
│   upgrade/  sync/                               │
├─────────────────────────────────────────────────┤
│  domain/          (interfaces + models)         │
│   models/  providers/  repositories/  access/   │
│   actions/  engine/  templates/  readiness/     │
│   sync/  workflow/  offline/                    │
├─────────────────────────────────────────────────┤
│  data/            (implementations)             │
│   local/  network/  providers/  repositories/   │
│   access/  billing/  sync/  offline/            │
├─────────────────────────────────────────────────┤
│  platform/        (OS-level concerns)           │
│   automation/  security/  notifications/        │
│   scheduling/  onboarding/                      │
├─────────────────────────────────────────────────┤
│  di/              (Hilt modules)                │
│  ui/              (theme + navigation)          │
└─────────────────────────────────────────────────┘
```

**Pattern:** MVVM — each screen has a `@HiltViewModel` with `StateFlow`; UI observes state and dispatches intents.

**Provider routing:** `TransformProviderRouter` implements `ArticleTransformProvider` and delegates to the active provider (Fake/OpenAI/Anthropic/Gemini/LOCAL_AI) based on profile resolution order.

---

## Workflows

### 1. Article Transformer

- **Input:** Shared or pasted text
- **Transform types:** `SUMMARY` | `THREAD` | `PRO_POST`
- **Actions:** Preview, Copy, Share, Save to history
- **State flow:** Input → Loading → Success / Error

### 2. Meeting Strategist

- **Input:** Title + meeting notes
- **Transform types:** `MEETING_BRIEF` | `STRATEGIC_QUESTIONS`
- **Actions:** Preview, Save, Share

### 3. Morning Brief (automation)

**Config flow:**
1. Trigger: `EVERY_N_HOURS` | `DAILY_AT_HOUR`
2. Feeds: `List<String>` (RSS URLs)
3. Output type: `SUMMARY` | `SOCIAL_POST` | `BULLET_INSIGHTS` | `CUSTOM`
4. Platforms (if SOCIAL): `LINKEDIN` | `X` | `FACEBOOK` | `INSTAGRAM` | `THREADS`
5. Notify: `true` / `false`

**Config model fields:**
```
feedUrls[]
outputType
selectedPlatforms[]
notify
scheduleType
intervalHours?
dailyHour?
maxItems = 5
customInstruction?
```

**Worker pipeline (SynthesizerWorker):**
1. Load config from DataStore
2. Fetch RSS via OkHttp
3. Parse XML (supports RSS + Atom) — title + snippet only
4. Sort by recency
5. Pick top N (<=5)
6. Build payload (limit ~6k–10k chars, truncate per item to 500 chars)
7. Call AI provider via router
8. Generate output
9. Save to history (`workflowType = MORNING_BRIEF`)
10. Notify if enabled

**Limits:**
- No full article fetch — snippet only
- Max ~6k–10k chars total payload
- Truncate per item

**Output rules:**
| Type | Style |
|------|-------|
| SUMMARY | Concise multi-item digest |
| BULLETS | Structured insights |
| SOCIAL | Per-platform distinct outputs (not rewrites) |
| CUSTOM | Follow user instruction |

**Social platform styles:**
| Platform | Tone |
|----------|------|
| LinkedIn | Professional insight |
| X | Short, sharp |
| Facebook | Conversational |
| Instagram | Caption-style |
| Threads | Narrative |

---

## File Structure

```
app/src/main/java/com/automatist/app/
├── MainActivity.kt                  # Launcher, handles share intents
├── ShareEntryActivity.kt            # Receives ACTION_SEND text/plain
├── AutomatistApp.kt                 # @HiltAndroidApp
│
├── domain/
│   ├── access/
│   │   └── ProductAccess.kt        # PlanType, PlanState, ProductAccessRepository interface
│   ├── actions/
│   │   └── WorkflowActionRegistry.kt  # Centralized action metadata, validation, summaries
│   ├── engine/
│   │   ├── DiagnosticException.kt   # Structured error reporting for workflow execution
│   │   ├── ExecutionState.kt        # Sealed interface for execution progress
│   │   ├── TextCompactor.kt         # Text truncation/compaction utilities
│   │   └── WorkflowExecutionEngine.kt # Shared execution pipeline
│   ├── models/
│   │   ├── Models.kt                # ArticleInput, BriefConfig, TransformResult, HistoryItem
│   │   ├── ProviderCatalog.kt       # ProviderModels — known model IDs per provider
│   │   ├── Settings.kt              # AppSettings (activeProvider)
│   │   ├── Types.kt                 # WorkflowType, TransformType, ProviderType, etc.
│   │   ├── WorkflowExportModels.kt  # Portable DTOs for workflow import/export
│   │   └── WorkflowModels.kt        # WorkflowTemplate, WorkflowRun, WorkflowAction, etc.
│   ├── offline/
│   │   ├── OfflineModel.kt          # OfflineModelEntry, OfflineModelCatalog, OfflineRuntimeType
│   │   └── OfflineModelRepository.kt # Interface: status/progress/download/remove
│   ├── providers/
│   │   └── ArticleTransformProvider.kt  # Interface: transform(input, type) → Result
│   ├── readiness/
│   │   └── ReadinessEvaluator.kt    # Dynamic readiness checks for actions/workflows
│   ├── repositories/
│   │   ├── HistoryRepository.kt     # Interface: CRUD for history
│   │   └── WorkflowRepository.kt   # Interface: CRUD for workflows + runs
│   ├── sync/
│   │   ├── CloudBackupModels.kt     # CloudBackupEnvelope serializable model
│   │   └── CloudSyncRepository.kt   # SyncStatus, SyncResult interfaces
│   ├── templates/
│   │   └── BuiltInTemplates.kt      # Code-defined workflow template blueprints
│   └── workflow/
│       └── WorkflowPortabilityManager.kt  # Import, export, duplication of workflows
│
├── data/
│   ├── access/
│   │   ├── BillingProductAccessRepository.kt  # Google Play Billing entitlements
│   │   └── LocalProductAccessRepository.kt    # Local DataStore-backed (dev/testing)
│   ├── billing/
│   │   └── BillingManager.kt        # Google Play Billing v7 integration
│   ├── local/
│   │   ├── AutomatistDatabase.kt    # Room DB (v3, 5 tables)
│   │   ├── HistoryDao.kt            # Room DAO
│   │   ├── HistoryEntity.kt         # Room entity + mapping extensions
│   │   ├── Migrations.kt            # DB migrations v1→v2, v2→v3
│   │   ├── SeedingStateStore.kt     # DataStore: seeding version + first-run flags
│   │   ├── SettingsRepository.kt    # DataStore for AppSettings + BriefConfig
│   │   ├── WorkflowDao.kt           # Room DAO for workflows
│   │   └── WorkflowEntities.kt      # Room entities for workflows + runs
│   ├── network/
│   │   └── RssParser.kt             # RSS/Atom fetcher + parser
│   ├── offline/
│   │   ├── DataStoreOfflineModelRepository.kt  # DataStore-backed offline model state
│   │   ├── MediaPipeInferenceEngine.kt  # MediaPipe LLM Inference wrapper (downloadable models)
│   │   └── ModelDownloadManager.kt  # OkHttp download manager with progress, SHA-256 verification
│   ├── providers/
│   │   ├── TransformProviderRouter.kt   # Routes to active provider
│   │   ├── FakeArticleTransformProvider.kt
│   │   ├── LocalAIArticleTransformProvider.kt  # On-device AI (AICore or MediaPipe)
│   │   ├── LocalPromptBuilder.kt        # Prompt construction for local models
│   │   ├── OpenAIArticleTransformProvider.kt
│   │   ├── AnthropicArticleTransformProvider.kt
│   │   ├── GeminiArticleTransformProvider.kt
│   │   ├── openai/    (OpenAIApi.kt, OpenAIModels.kt)
│   │   ├── anthropic/ (AnthropicApi.kt, AnthropicModels.kt)
│   │   └── gemini/    (GeminiApi.kt, GeminiModels.kt)
│   ├── repositories/
│   │   ├── RoomHistoryRepository.kt # Room implementation of HistoryRepository
│   │   └── RoomWorkflowRepository.kt # Room implementation of WorkflowRepository
│   └── sync/
│       ├── CloudSyncManager.kt      # Google Drive appDataFolder operations
│       └── LocalCloudSyncRepository.kt # DataStore-backed sync status
│
├── feature/
│   ├── article/     (ArticleScreen.kt, ArticleViewModel.kt)
│   ├── meeting/     (MeetingScreen.kt, MeetingViewModel.kt)
│   ├── brief/       (BriefScreen.kt, BriefViewModel.kt)
│   ├── dashboard/   (DashboardScreen.kt, DashboardViewModel.kt)
│   ├── history/     (HistoryScreen.kt, HistoryDetailScreen.kt, HistoryViewModel.kt)
│   ├── vault/       (VaultScreen.kt, VaultViewModel.kt)
│   ├── notes/       (NotesScreen.kt, NotesViewModel.kt)
│   ├── upgrade/     (Upgrade / Pro purchase flow)
│   │   ├── UpgradePrompt.kt        # Non-dismissable modal for free tier limit
│   │   └── UpgradeScreen.kt        # Full upgrade screen with billing + restore
│   ├── sync/        (Cloud sync UI)
│   │   └── CloudSyncScreen.kt      # Google Drive backup management
│   └── workflow/    (Workflow Builder feature)
│       ├── templates/   (WorkflowTemplatesScreen.kt, WorkflowTemplatesViewModel.kt)
│       ├── list/        (WorkflowListScreen.kt, WorkflowListViewModel.kt)
│       ├── editor/      (WorkflowEditorScreen.kt, WorkflowEditorViewModel.kt)
│       ├── run/         (WorkflowRunScreen.kt, WorkflowRunViewModel.kt, WorkflowRunDetailScreen.kt, WorkflowRunDetailViewModel.kt)
│       ├── details/     (WorkflowDetailsScreen.kt, WorkflowDetailsViewModel.kt)
│       ├── history/     (WorkflowHistoryScreen.kt, WorkflowHistoryViewModel.kt)
│       ├── schedule/    (ScheduleStatusScreen.kt, ScheduleStatusViewModel.kt)
│       └── components/  (ActionBlockEditor.kt, ActionBlockList.kt, ActionCatalog.kt, ProfilePicker.kt)
│
├── platform/
│   ├── automation/
│   │   ├── SynthesizerWorker.kt     # WorkManager job for Morning Brief
│   │   └── WorkflowWorker.kt       # WorkManager job for custom workflows
│   ├── notifications/
│   │   └── NotificationHelper.kt   # Centralized notification creation
│   ├── onboarding/
│   │   └── FirstRunSeeder.kt        # Idempotent first-run seeder (profiles + sample workflow)
│   ├── scheduling/
│   │   └── ScheduleManager.kt      # WorkManager schedule orchestration
│   └── security/
│       ├── SecureStorage.kt         # Interface
│       └── KeystoreSecureStorage.kt # DataStore impl (TODO: upgrade to Keystore)
│
├── assets/
│   └── seeded_workflows/
│       └── news_to_social.json      # Bundled "News to Social Posts" workflow for first-run seeding
│
├── di/
│   ├── AccessModule.kt              # ProductAccessRepository binding
│   ├── AppModule.kt                 # Application context
│   ├── DatabaseModule.kt            # Room DB + DAO + repository binding
│   ├── NetworkModule.kt             # OkHttp + 3 Retrofit instances
│   ├── OfflineModule.kt             # OfflineModelRepository binding
│   ├── ProviderModule.kt            # Router → ArticleTransformProvider binding
│   ├── SecurityModule.kt            # SecureStorage binding
│   └── SyncModule.kt                # CloudSyncRepository binding
│
└── ui/
    └── navigation/
        └── AutomatistNavGraph.kt    # All routes
```

---

## Key Components

### AI Provider System

**Interface:** `ArticleTransformProvider.transform(input: ArticleInput, type: TransformType): Result<TransformResult>`

**Router:** `TransformProviderRouter` resolves provider+model from profiles or falls back to legacy `activeProvider`. Resolution order: (1) explicit `profileId` on `ArticleInput`, (2) default profile from `provider_profiles` table, (3) legacy `activeProvider` from AppSettings.

**Provider Profiles:** Stored in Room (`provider_profiles` table). Each profile specifies a provider type, model ID, and display name. One profile can be marked as default. API keys remain centralized in `SecureStorage` keyed by `ProviderType` (shared across profiles using the same provider).

**Profile Routing in Workflows:**
- `WorkflowTemplate.defaultProfileId` — workflow-level default profile
- `WorkflowOutputConfig.outputProfileId` — override for final output generation
- `ArticleInput.profileId` — resolved at execution time, passed to router
- `ArticleInput.modelOverride` — resolved from profile's modelId, passed to concrete provider

**Providers:**
| Provider | Default Model | Auth |
|----------|--------------|------|
| Fake | N/A (mock, 1.5s delay) | None |
| OpenAI | gpt-3.5-turbo (overridable) | Bearer token |
| Anthropic | claude-3-haiku-20240307 (overridable) | x-api-key header |
| Gemini | gemini-1.5-flash | API key query param |
| LOCAL_AI | gemini-nano or gemma-3n-e2b | None (on-device) |

Each cloud provider: fetches key from `SecureStorage` → builds system prompt (or uses `systemPromptOverride`) → calls API → returns `TransformResult`.

LOCAL_AI: dispatches to `AICore` (Gemini Nano, system-managed) or `MediaPipeInferenceEngine` (Gemma 3 1B int4, downloaded). No API key or network needed.

### Local / On-device AI

The `LOCAL_AI` provider (`LocalAIArticleTransformProvider`) runs inference entirely on-device.

**Two runtimes:**

| Runtime | Model | ID | Device Support | Download |
|---------|-------|----|----------------|---------|
| AICORE | Gemini Nano | `gemini-nano` | Android 14+ (API 34), Pixel 8+, Galaxy S24+ | System-managed (no download) |
| DOWNLOADABLE | Gemma 3 1B int4 | `gemma-3n-e2b` | Android 8+ (API 26), ≥3 GB RAM | ~529 MB, app-managed |

**Key facts:**
- Model catalog is in `OfflineModelCatalog` — add future models there without touching routing logic.
- The stable ID `gemma-3n-e2b` must not be renamed — it is stored in user `ProviderProfile` rows and DataStore keys. The display name was corrected to "Gemma 3 1B (int4)" but the ID is frozen.
- The model file `gemma3-1b-it-int4.task` is hosted at `atj393/automatist-models` GitHub Releases, tag `offline-models-v1`. SHA-256: `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`.
- `MediaPipeInferenceEngine` runs on a single dedicated `MediaPipe-Worker` thread (not the shared IO pool) to serialize concurrent inference and avoid multi-GB double allocation.
- Token budget: `MAX_TOTAL_TOKENS = 1536` (input + output combined). `MAX_INPUT_TOKENS = 1280`. Preflight guard rejects oversized prompts before the native call to prevent JNI crashes.
- Context window is capped at 2500 chars in `OfflineModelEntry.contextWindowChars` for the downloadable model (reduced from 4000 to keep prefill latency acceptable).
- Inference timeout: 120 seconds.
- Model is not kept in memory between calls — loaded fresh per call to avoid holding ~2–3 GB RAM in the background.
- `LocalPromptBuilder` constructs the prompt and enforces char-level truncation against `contextWindowChars` before reaching the engine's token guard.

**ProGuard / R8:**
- MediaPipe and AICore classes require explicit keep rules. These are present in `app/proguard-rules.pro`.
- Do not remove or condense them — R8 will obfuscate native JNI class names, causing runtime crashes.

### First-run Seeding

`FirstRunSeeder` (`platform/onboarding/`) runs idempotently on every app launch. `SeedingStateStore` persists the applied version.

**Current version:** 2

**Version history:**
- v1: seed "Demo Mode" (FAKE) profile + "News to Social Posts" workflow from bundled asset `seeded_workflows/news_to_social.json`.
- v2: one-time cleanup of legacy "Article Briefing" workflow left over from pre-v1 app builds (only deletes if name/sourceTemplateId/description match AND `lastRunAtMillis == null`).

**Rules:**
- Never overwrites an existing FAKE profile or an existing default profile.
- After seeding, `reflectSetupProgressFromSeededState()` flips Getting Started checklist flags forward (false→true only) so a new user doesn't see "0 of 4 steps" when they already have a working setup.
- The seeded workflow is imported via `WorkflowPortabilityManager` and arrives disabled — user enables scheduling when ready.

### Readiness System

**`ReadinessEvaluator`** — singleton that dynamically checks if actions/workflows are ready to run by querying `SecureStorage` and `WorkflowRepository`. Returns `ActionReadiness` (per-action) or `WorkflowReadiness` (per-workflow).

**Requirement types:** `SERVICE_KEY` (weather, route APIs), `API_KEY` (AI provider), `PERMISSION` (future: calendar, location), `NONE`.

**Used by:**
- Action Catalog — dynamic badges + "Set Up" CTA navigating to Settings
- Workflow editor — readiness bar at top ("Ready to run" or "X actions need setup" with "Fix in Settings" CTA)
- Template browser — per-template readiness badges ("Ready" or "Setup" chip)

**Legacy auto-migration:** When Settings opens and no profiles exist but legacy activeProvider has a configured API key, a default profile is auto-created.

**Settings deep-linking:** `SettingsSection` enum (PROFILES, PROVIDER_KEYS, SERVICE_KEYS, LEGACY) allows navigation directly to a specific Settings section via `vault?section={key}`.

**Execution preflight:** Before running a workflow, `WorkflowRunViewModel` evaluates readiness and blocks execution with a clear setup-required screen if critical configuration is missing. The blocking screen lists missing requirements and offers a "Open Settings" CTA.

**Template quick-setup:** When using a template that needs setup, a dialog shows what's missing with "Set Up Now" (→ Settings) and "Continue Anyway" options.

**Reactive readiness:** Editor screen refreshes readiness on `ON_RESUME` lifecycle event (e.g., after returning from Settings).

### Settings Structure

**Settings (VaultScreen)** organized into 5 sections:
1. **AI Provider Profiles** — named provider+model configurations (CRUD), one set as default
2. **Provider API Keys** — per-provider AI keys (OpenAI, Anthropic, Gemini)
3. **Service API Keys** — external service keys (OpenWeatherMap, OpenRouteService)
4. **On-device AI** — offline model status, download/remove, device compatibility info
5. **Active Provider (Legacy)** — fallback provider selection for legacy screens

### Transform Types

| Enum | Used By |
|------|---------|
| `SUMMARY` | Article, Brief |
| `THREAD` | Article |
| `PRO_POST` | Article |
| `MEETING_BRIEF` | Meeting |
| `STRATEGIC_QUESTIONS` | Meeting |
| `MORNING_SUMMARY` | Brief worker |
| `CUSTOM_WORKFLOW` | Workflow engine |

### History

Every workflow output can be saved. Stored in Room (`history_items` table) with: id, workflowType, transformType, inputPreview, outputText, providerType, createdAtMillis. Displayed on Dashboard (last 3) and History screen (full list).

### Product Access / Entitlements

**`ProductAccessRepository`** — interface for checking plan state (Free vs Pro).

**`PlanType`:** `FREE` | `PRO`

**`PlanState`:** Contains `planType`, `FREE_WORKFLOW_LIMIT = 1`.

**Implementations:**
- `BillingProductAccessRepository` — production; backed by Google Play Billing ownership state
- `LocalProductAccessRepository` — development/testing; backed by local DataStore

**Hilt binding:** `AccessModule` binds `BillingProductAccessRepository` as the production implementation.

**Free tier limits:** Free users can create 1 workflow. Attempting to create more triggers `UpgradePrompt` modal.

### Google Play Billing

**`BillingManager`** — manages connection to Google Play Billing Library v7.0.0.

**Product ID:** `"automatist_pro"` (one-time purchase, not subscription).

**State flows:**
- `billingStatus: StateFlow<BillingStatus>` — Initializing, Ready, Error
- `purchaseState: StateFlow<PurchaseState>` — Idle, Pending, Success, Error
- `proOwned: StateFlow<Boolean>` — whether Pro has been purchased
- `productDetails: StateFlow<ProductDetails?>` — queried from Google Play

**Features:** Async product query, purchase flow launch, ownership verification, purchase acknowledgement, local caching for startup.

**Integration:** `AutomatistApp.onCreate()` calls `billingManager.queryOwnedPurchases()` on app start.

**Important:** When `queryOwnedPurchases()` finds no purchase for a free user, it emits `PurchaseState.Idle` (not Error). The Error state is reserved for actual billing failures. The UpgradeScreen has separate logic for showing "No previous purchase found" feedback on user-initiated restores.

### Cloud Sync (Google Drive)

**`CloudSyncManager`** — handles backup/restore of workflows to Google Drive `appDataFolder`.

**Flow:** Uses `WorkflowPortabilityManager` to create portable, secret-free workflow DTOs → serializes as `CloudBackupEnvelope` → uploads to Google Drive.

**Auth:** Google Account Credential with OAuth2 (Drive appdata scope).

**Status tracking:** `CloudSyncRepository` stores sync state (email, last backup timestamp, workflow count) in DataStore.

**UI:** `CloudSyncScreen` — sign in, trigger backup, view sync status.

**Hilt binding:** `SyncModule` binds `LocalCloudSyncRepository` as the current implementation.

### Upgrade Flow

**`UpgradePrompt`** — non-dismissable modal dialog shown when free user hits the workflow creation limit.

**`UpgradeScreen`** — full-screen purchase flow with:
- Purchase button (connects to Google Play Billing)
- Restore purchases button (for existing customers) — shows neutral "No previous purchase found" snackbar on user-initiated restore with no purchase (not an error)
- Debug toggle (`BuildConfig.DEBUG` only) for testing Pro state — compiled out by R8 in release

**`UpgradeViewModel`** — manages `planState`, `purchaseState`, `productDetails`, `billingStatus`, `userRestoreInProgress`.

### Secure Storage

API keys stored per-provider in DataStore. Current implementation is plaintext DataStore (TODO: upgrade to Android Keystore / EncryptedSharedPreferences).

---

## Workflow Builder

### Product Model: Template-First

Users can create workflows from curated templates OR from scratch. Templates are starter blueprints, not locked flows.

- **Workflow Templates** — built-in blueprints defined in code (`BuiltInTemplates.kt`), not stored in Room
- **My Workflows** — user-owned instances, fully editable, stored in Room
- **Workflow Runs** — execution history, separate table with FK to user workflow

**Creation flows:**
- **From Template:** Browse Templates → Use Template → fully editable copy created → Save → Run
- **From Scratch:** Start Empty → blank editor → Save → Run

**Template origin tracking:** `sourceTemplateId` tracks which template a workflow was created from (informational only, not restrictive). All user workflows are fully editable regardless of origin.

**Seeded sample:** "News to Social Posts" workflow auto-created for new users as a useful starter (via `FirstRunSeeder`, version 1+).

### Built-In Templates (7)

| Template | Category | Default Trigger | Key Actions |
|----------|----------|-----------------|-------------|
| Morning Commute Brief | Daily Routines | Daily 7:00 | Weather + Route + RSS |
| Morning Brief | News & Content | Daily 8:00 | RSS feed |
| Article Transformer | Communication | Manual | Paste text |
| Meeting Strategist | Communication | Manual | Paste text |
| Content Repurposer | Social Media | Manual | Paste text |
| Competitor Monitor | Research | Daily 9:00 | Multi-feed RSS |
| Research Digest | Research | Manual | Fetch URL |

### 4. Workflow Builder (user-created from templates)

- **Input:** Multi-source actions (fetch URL, paste text, RSS, API, saved notes, previous output)
- **Trigger types:** Manual | Daily schedule | Weekly schedule
- **Processing:** Global instruction + per-action instructions → AI provider
- **Output types:** `BRIEFING` | `SOCIAL_POST` | `BOTH` | `CUSTOM`
- **Actions:** Manual run, scheduled run, copy, share
- **Execution screen:** Stage-by-stage progress, token usage, duration, error handling

**Models:**
- `WorkflowTemplate` — stored in Room with JSON columns + sourceTemplateId + customization rules
- `WorkflowRun` — stored in Room, tracks status, output, token usage, duration
- `WorkflowAction` — id, type, label, sourceData, instruction, order, extraConfig
- `WorkflowTrigger` — sealed interface: Manual, Daily, Weekly, NotificationKeyword (future)

**Action types (11 total):**
- `FETCH_URL` — fetches URL content via OkHttp, strips HTML, truncates to 4000 chars
- `PASTE_TEXT` — user-provided text content
- `FETCH_RSS_FEED` — pulls RSS/Atom feed items via RssParser, supports keyword filter and maxItems config
- `FETCH_API_GET` — GET request to REST API endpoint, supports custom headers, query params, and extraction hints
- `USE_SAVED_NOTE` — references a reusable saved note (stored in `saved_notes` table) or inline text
- `USE_PREVIOUS_OUTPUT` — uses output from another workflow's latest successful run or a specific run
- `FETCH_RSS_MULTI` — pulls and merges items from multiple RSS/Atom feeds with deduplication, keyword filtering, and configurable item limits
- `FETCH_WEATHER` — fetches current weather for a location via OpenWeatherMap API; extracts temperature, humidity, conditions into structured text
- `FETCH_ROUTE_TIME` — fetches travel time and distance between two locations via OpenRouteService API; supports driving, walking, cycling modes with geocoding
- `USE_ACTION_OUTPUT` — uses the output of a specific earlier action within the same workflow run (in-workflow chaining)
- `AI_PROMPT` — runs an intermediate AI call mid-workflow (preprocessing pass); output becomes input to the next stage

**External service API keys** stored in `SecureStorage.saveServiceKey(service, key)` — separate from AI provider keys. Services: `openweathermap`, `openrouteservice`.

**Action system architecture:** `WorkflowActionRegistry` centralizes metadata, validation, and summary generation per action type. Editor composables and executor methods are dispatched per type. Adding a new action type requires changes to: (1) enum, (2) registry entry, (3) executor method, (4) editor composable.

**Action Catalog UX:** The "Add Action" flow uses a full-screen `ModalBottomSheet` (`ActionCatalog.kt`) with category-grouped cards, expandable detail views, search, readiness badges, and "Add to Workflow" buttons. Categories: Daily Life, News & Web, Notes & Text, Data & APIs, Workflow.

**Saved Notes Manager:** Dedicated screen for CRUD operations on reusable notes. Notes are stored in Room (`saved_notes` table) and can be referenced by workflows via `SavedNoteReference` in action `extraConfig`.

**Future action types (extension points exist):**
- USE_FILE, USE_CLIPBOARD, USE_NOTIFICATION, FETCH_API_POST

**Execution engine:** `WorkflowExecutionEngine` — singleton, emits `Flow<ExecutionState>`, shared by both ViewModel (manual runs) and WorkflowWorker (scheduled runs).

**Execution features:**
- Per-action preprocessing: if an action has an `instruction`, the engine runs an AI pass on the raw source content before the main synthesis step.
- Per-action compaction: `TextCompactor` enforces per-source limits to keep the combined prompt within the active provider's context window.
- Output versions: `numberOfOutputs` (1–10) requests multiple generation passes for the final output.
- Stage granularity: `ExecutionState` emits per-action progress so the UI can show which action is running.
- `resetForNewStage()` / `resetTransientState()` called at stage boundaries to clear stale diagnostics.

**Database:** Room v3 with migrations. Tables: `workflow_templates`, `workflow_runs` (FK cascade on template delete), `saved_notes`, `history_items`, `provider_profiles`.

**Per-action config:** Stored in `WorkflowAction.extraConfig` as JSON. Each action type has its own config model: `RssFeedConfig`, `ApiGetConfig`, `SavedNoteReference`, `PreviousOutputConfig`.

---

## DO NOT

- Add new workflow types without explicit request
- Mix enums wrongly (e.g. using article transform types in meeting context)
- Overengineer router layers
- Add hidden automation or background processing beyond the brief worker
- Break the manual-approval model (user must always review before action)
- Auto-post, auto-share, or auto-send anything
- Fetch full articles — snippets only
- Add a backend server (Google Drive sync is the only cloud integration)
- Store API keys in plaintext files or logs
- Include API keys or secrets in workflow export/sync payloads (use WorkflowPortabilityManager)
- Bypass the entitlement system (free tier limits must be enforced via ProductAccessRepository)
- Change the billing product ID (`automatist_pro`) without explicit request
- Rename `GEMMA_3N_E2B_ID = "gemma-3n-e2b"` — this ID is stored in user ProviderProfile rows and DataStore keys; changing it breaks existing installs silently
- Delete or rename the `offline-models-v1` GitHub Release tag — existing installs have this URL hardcoded in `OfflineModelCatalog`; changing it breaks downloads for all users on v1.0.0
- Change `downloadUrl` or `fileSha256` in `OfflineModelCatalog` without also bumping the catalog entry's ID and adding a migration — these values are trust anchors for download integrity
- Bypass the `estimateTokens` / `MAX_INPUT_TOKENS` preflight guard in `MediaPipeInferenceEngine` — oversized prompts cause a native JNI crash (SIGABRT), not a graceful exception
- Make drive-by refactors, reformatting, or unrelated "improvements" — a recurring agent failure in this repo; every changed line must trace back to the current request

---

## Development Guide

### Build
```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew bundleRelease      # produces signed AAB for Play Store
./gradlew assembleRelease    # produces signed APK for sideloading / testing
```

### Test & Lint
```bash
./gradlew test                # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.automatist.app.domain.engine.StageResetTest"  # single class
./gradlew lint                # report: app/build/reports/lint-results-*.html (abortOnError = false)
```

### Command Safety (agents)
- Run without asking: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lint`
- Ask first: `installDebug`, `assembleRelease`, `bundleRelease`, `buildReleaseAndInstall`, any `adb` command

### Adding a New AI Provider
1. Create API interface in `data/providers/{name}/` (`{Name}Api.kt`, `{Name}Models.kt`)
2. Create `{Name}ArticleTransformProvider.kt` implementing `ArticleTransformProvider`
3. Add enum value to `ProviderType` in `domain/models/Types.kt`
4. Add Retrofit instance in `di/NetworkModule.kt`
5. Inject into `TransformProviderRouter` and add routing case
6. Add API key field in `VaultScreen.kt` / `VaultViewModel.kt`

### Adding a New Offline Model
1. Add a new `OfflineModelEntry` to `OfflineModelCatalog.ALL_MODELS` in `domain/offline/OfflineModel.kt`
2. Add the model ID constant to `ProviderModels.LOCAL_AI` in `domain/models/ProviderCatalog.kt`
3. No changes required to `LocalAIArticleTransformProvider`, routing, or the readiness system — the catalog drives dispatch

### Adding a New Transform Type
1. Add enum value to `TransformType` in `domain/models/Types.kt`
2. Add system prompt case in each provider's `getSystemPrompt()` function
3. Add mock response in `FakeArticleTransformProvider`
4. Add UI option in the relevant screen (Article or Meeting)

### ProGuard / R8
Release builds use R8 with `isMinifyEnabled = true`. Keep rules in `app/proguard-rules.pro` cover:
- Retrofit, OkHttp, Gson, Room, Hilt, Billing, Google Drive, kotlinx.serialization
- MediaPipe LLM Inference (`-keep class com.google.mediapipe.** { *; }`)
- Google AI Edge AICore (`-keep class com.google.ai.edge.aicore.** { *; }`)
- AutoValue/JavaPoet annotation processor dontwarn entries (prevent R8 build failure from google-api-client transitive deps)

Do not remove the MediaPipe or AICore keep rules — R8 obfuscating these JNI class names causes runtime crashes, not build errors.

### Release Signing
- Copy `keystore.properties.example` → `keystore.properties` (gitignored)
- Generate keystore: `keytool -genkeypair -v -keystore automatist-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias automatist`
- Fill in `storePassword` and `keyPassword` in `keystore.properties`
- If `keystore.properties` is missing, release builds remain unsigned (debug builds unaffected)

### Build Variants
- **Debug:** `applicationId = "com.automatist.app.debug"` (can coexist with release on same device)
- **Release:** `applicationId = "com.automatist.app"`, minification + resource shrinking enabled

### Worker Scheduling
- Periodic: `PeriodicWorkRequestBuilder` with `ExistingPeriodicWorkPolicy.UPDATE` and `setInitialDelay()` computed from target time-of-day
- One-time: `OneTimeWorkRequestBuilder` for manual "Run Now"
- Unique work names: `SynthesizerWorker_Periodic`, `SynthesizerWorker_OneTime`, `WorkflowWorker_{templateId}`
- Time-of-day scheduling: `computeDelayToNextTime()` calculates milliseconds from now until the next occurrence of the target hour:minute
- Schedule visibility: workflow cards show computed "Next run" time and "Last run" timestamp
- Time input: slider-based picker with 24-hour format, shows computed next run preview in editor
- Platform note: WorkManager may adjust timing by a few minutes for battery optimization
