# Synapse

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

**Build:** Gradle KTS, version catalog (`libs.versions.toml`), compile/target SDK 34, min SDK 26, Java 17.

---

## Architecture

**Single-activity** app with Jetpack Compose navigation.

```
┌─────────────────────────────────────────────┐
│  feature/                                   │
│   article/   meeting/   brief/              │
│   dashboard/  history/   vault/             │
├─────────────────────────────────────────────┤
│  domain/          (interfaces + models)     │
│   models/  providers/  repositories/        │
├─────────────────────────────────────────────┤
│  data/            (implementations)         │
│   local/  network/  providers/  repositories│
├─────────────────────────────────────────────┤
│  platform/        (OS-level concerns)       │
│   automation/  security/                    │
├─────────────────────────────────────────────┤
│  di/              (Hilt modules)            │
│  ui/              (theme + navigation)      │
└─────────────────────────────────────────────┘
```

**Pattern:** MVVM — each screen has a `@HiltViewModel` with `StateFlow`; UI observes state and dispatches intents.

**Provider routing:** `TransformProviderRouter` implements `ArticleTransformProvider` and delegates to the active provider (Fake/OpenAI/Anthropic/Gemini) based on `SettingsRepository.settings`.

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
app/src/main/java/com/synapse/app/
├── MainActivity.kt                  # Launcher, handles share intents
├── ShareEntryActivity.kt            # Receives ACTION_SEND text/plain
├── SynapseApp.kt                    # @HiltAndroidApp
│
├── domain/
│   ├── models/
│   │   ├── Models.kt                # ArticleInput, BriefConfig, TransformResult, HistoryItem
│   │   ├── Types.kt                 # WorkflowType, TransformType, ProviderType, etc.
│   │   └── Settings.kt              # AppSettings (activeProvider)
│   ├── providers/
│   │   └── ArticleTransformProvider.kt  # Interface: transform(input, type) → Result
│   └── repositories/
│       └── HistoryRepository.kt     # Interface: CRUD for history
│
├── data/
│   ├── local/
│   │   ├── HistoryEntity.kt         # Room entity + mapping extensions
│   │   ├── HistoryDao.kt            # Room DAO
│   │   ├── WorkflowEntities.kt      # Room entities for workflows + runs
│   │   ├── WorkflowDao.kt           # Room DAO for workflows
│   │   ├── Migrations.kt            # DB migrations v1→v2, v2→v3
│   │   ├── SynapseDatabase.kt       # Room DB (v3, 4 tables)
│   │   └── SettingsRepository.kt    # DataStore for AppSettings + BriefConfig
│   ├── network/
│   │   └── RssParser.kt             # RSS/Atom fetcher + parser
│   ├── providers/
│   │   ├── TransformProviderRouter.kt   # Routes to active provider
│   │   ├── FakeArticleTransformProvider.kt
│   │   ├── OpenAIArticleTransformProvider.kt
│   │   ├── AnthropicArticleTransformProvider.kt
│   │   ├── GeminiArticleTransformProvider.kt
│   │   ├── openai/    (OpenAIApi.kt, OpenAIModels.kt)
│   │   ├── anthropic/ (AnthropicApi.kt, AnthropicModels.kt)
│   │   └── gemini/    (GeminiApi.kt, GeminiModels.kt)
│   └── repositories/
│       └── RoomHistoryRepository.kt # Room implementation of HistoryRepository
│
├── feature/
│   ├── article/     (ArticleScreen.kt, ArticleViewModel.kt)
│   ├── meeting/     (MeetingScreen.kt, MeetingViewModel.kt)
│   ├── brief/       (BriefScreen.kt, BriefViewModel.kt)
│   ├── dashboard/   (DashboardScreen.kt, DashboardViewModel.kt)
│   ├── history/     (HistoryScreen.kt, HistoryDetailScreen.kt, HistoryViewModel.kt)
│   ├── vault/       (VaultScreen.kt, VaultViewModel.kt)
│   ├── notes/       (NotesScreen.kt, NotesViewModel.kt)
│   └── workflow/    (Workflow Builder feature)
│       ├── list/        (WorkflowListScreen.kt, WorkflowListViewModel.kt)
│       ├── editor/      (WorkflowEditorScreen.kt, WorkflowEditorViewModel.kt)
│       ├── run/         (WorkflowRunScreen.kt, WorkflowRunViewModel.kt, WorkflowRunDetailScreen.kt)
│       └── components/  (ActionBlockEditor.kt, ActionBlockList.kt)
│
├── domain/
│   └── actions/
│       └── WorkflowActionRegistry.kt  # Centralized action metadata, validation, summaries
│
├── domain/
│   └── engine/
│       ├── ExecutionState.kt        # Sealed interface for execution progress
│       └── WorkflowExecutionEngine.kt # Shared execution pipeline
│
├── platform/
│   ├── automation/
│   │   ├── SynthesizerWorker.kt     # WorkManager job for Morning Brief
│   │   └── WorkflowWorker.kt       # WorkManager job for custom workflows
│   └── security/
│       ├── SecureStorage.kt         # Interface
│       └── KeystoreSecureStorage.kt # DataStore impl (TODO: upgrade to Keystore)
│
├── di/
│   ├── AppModule.kt                 # Application context
│   ├── DatabaseModule.kt            # Room DB + DAO + repository binding
│   ├── NetworkModule.kt             # OkHttp + 3 Retrofit instances
│   ├── ProviderModule.kt            # Router → ArticleTransformProvider binding
│   └── SecurityModule.kt            # SecureStorage binding
│
└── ui/
    ├── navigation/
    │   └── SynapseNavGraph.kt       # All routes
    └── theme/
        ├── Color.kt                 # Purple/Pink palette
        ├── Theme.kt                 # Material3 dynamic color
        └── Type.kt                  # Typography
```

---

## Key Components

### AI Provider System

**Interface:** `ArticleTransformProvider.transform(input: ArticleInput, type: TransformType): Result<TransformResult>`

**Router:** `TransformProviderRouter` reads `activeProvider` from settings and delegates. All providers share the same interface.

**Providers:**
| Provider | Model | Auth |
|----------|-------|------|
| Fake | N/A (mock, 1.5s delay) | None |
| OpenAI | gpt-3.5-turbo | Bearer token |
| Anthropic | claude-3-haiku-20240307 | x-api-key header |
| Gemini | gemini-1.5-flash | API key query param |

Each provider: fetches key from `SecureStorage` → builds system prompt (or uses `systemPromptOverride`) → calls API → returns `TransformResult`.

### Transform Types

| Enum | Used By |
|------|---------|
| `SUMMARY` | Article, Brief |
| `THREAD` | Article |
| `PRO_POST` | Article |
| `MEETING_BRIEF` | Meeting |
| `STRATEGIC_QUESTIONS` | Meeting |
| `MORNING_SUMMARY` | Brief worker |

### History

Every workflow output can be saved. Stored in Room (`history_items` table) with: id, workflowType, transformType, inputPreview, outputText, providerType, createdAtMillis. Displayed on Dashboard (last 3) and History screen (full list).

### Secure Storage

API keys stored per-provider in DataStore. Current implementation is plaintext DataStore (TODO: upgrade to Android Keystore / EncryptedSharedPreferences).

### Navigation Routes

`dashboard` → `article_transformer` | `meeting_strategist` | `morning_brief` | `history` → `history_detail/{itemId}` | `vault`

---

## Data Flow

### Article / Meeting Transform
```
User input (text) → ViewModel → TransformProviderRouter
  → reads activeProvider from SettingsRepository
  → delegates to concrete provider
  → provider fetches API key from SecureStorage
  → builds prompt + calls API
  → returns TransformResult
  → ViewModel updates UI state
  → user reviews → copy / share / save to history
```

### Morning Brief
```
WorkManager triggers SynthesizerWorker
  → loads BriefConfig from DataStore
  → RssParser fetches + parses RSS URLs (snippet only)
  → aggregates top 5 items (max ~8k chars)
  → builds systemPromptOverride from config (output type, platforms, custom)
  → calls TransformProviderRouter with MORNING_SUMMARY
  → saves result to Room history
  → fires notification if enabled
```

### Share Intent
```
External app → ACTION_SEND text/plain → ShareEntryActivity
  → launches MainActivity with shared text
  → auto-navigates to article_transformer with text pre-filled
```

### Workflow Builder (custom run)
```
User creates WorkflowTemplate via editor
  → saves to Room (workflow_templates table)
  → manual run: WorkflowRunViewModel → WorkflowExecutionEngine → Flow<ExecutionState>
  → scheduled run: WorkManager → WorkflowWorker → WorkflowExecutionEngine
  → engine executes actions (fetch URL / paste text) in order
  → combines results, builds system prompt from config
  → calls TransformProviderRouter with CUSTOM_WORKFLOW + systemPromptOverride
  → saves WorkflowRun to Room (workflow_runs table)
  → fires notification if enabled
  → user reviews output → copy / share
```

---

## Workflow Builder

### 4. Workflow Builder (user-created)

- **Input:** Multi-source actions (fetch URL, paste text) with per-action instructions
- **Trigger types:** Manual | Daily schedule | Weekly schedule
- **Processing:** Global instruction + per-action instructions → AI provider
- **Output types:** `BRIEFING` | `SOCIAL_POST` | `BOTH` | `CUSTOM`
- **Actions:** Manual run, scheduled run, copy, share
- **Execution screen:** Stage-by-stage progress, token usage, duration, error handling

**Models:**
- `WorkflowTemplate` — stored in Room with JSON columns for trigger/actions/output config
- `WorkflowRun` — stored in Room, tracks status, output, token usage, duration
- `WorkflowAction` — id, type, label, sourceData, instruction, order
- `WorkflowTrigger` — sealed interface: Manual, Daily, Weekly, NotificationKeyword (future)

**Action types:**
- `FETCH_URL` — fetches URL content via OkHttp, strips HTML, truncates to 4000 chars
- `PASTE_TEXT` — user-provided text content
- `FETCH_RSS_FEED` — pulls RSS/Atom feed items via RssParser, supports keyword filter and maxItems config
- `FETCH_API_GET` — GET request to REST API endpoint, supports custom headers, query params, and extraction hints
- `USE_SAVED_NOTE` — references a reusable saved note (stored in `saved_notes` table) or inline text
- `USE_PREVIOUS_OUTPUT` — uses output from another workflow's latest successful run or a specific run
- `FETCH_RSS_MULTI` — pulls and merges items from multiple RSS/Atom feeds with deduplication, keyword filtering, and configurable item limits

**Action system architecture:** `WorkflowActionRegistry` centralizes metadata, validation, and summary generation per action type. Editor composables and executor methods are dispatched per type. Adding a new action type requires changes to: (1) enum, (2) registry entry, (3) executor method, (4) editor composable.

**Saved Notes Manager:** Dedicated screen for CRUD operations on reusable notes. Notes are stored in Room (`saved_notes` table) and can be referenced by workflows via `SavedNoteReference` in action `extraConfig`.

**Future action types (extension points exist):**
- USE_FILE, USE_CLIPBOARD, USE_NOTIFICATION, FETCH_API_POST

**Execution engine:** `WorkflowExecutionEngine` — singleton, emits `Flow<ExecutionState>`, shared by both ViewModel (manual runs) and WorkflowWorker (scheduled runs).

**Database:** Room v3 with migrations. Tables: `workflow_templates`, `workflow_runs` (FK cascade on template delete), `saved_notes`.

**Per-action config:** Stored in `WorkflowAction.extraConfig` as JSON. Each action type has its own config model: `RssFeedConfig`, `ApiGetConfig`, `SavedNoteReference`, `PreviousOutputConfig`.

**Navigation routes:**
`workflow_list` → `workflow_editor?templateId={id}` | `workflow_run/{templateId}` | `workflow_run_detail/{runId}`
`saved_notes` — Saved Notes Manager (CRUD for reusable note content)

---

## DO NOT

- Add new workflow types without explicit request
- Mix enums wrongly (e.g. using article transform types in meeting context)
- Overengineer router layers
- Add hidden automation or background processing beyond the brief worker
- Break the manual-approval model (user must always review before action)
- Auto-post, auto-share, or auto-send anything
- Fetch full articles — snippets only
- Add a backend server or cloud storage
- Store API keys in plaintext files or logs

---

## Development Guide

### Build
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### Adding a New AI Provider
1. Create API interface in `data/providers/{name}/` (`{Name}Api.kt`, `{Name}Models.kt`)
2. Create `{Name}ArticleTransformProvider.kt` implementing `ArticleTransformProvider`
3. Add enum value to `ProviderType` in `domain/models/Types.kt`
4. Add Retrofit instance in `di/NetworkModule.kt`
5. Inject into `TransformProviderRouter` and add routing case
6. Add API key field in `VaultScreen.kt` / `VaultViewModel.kt`

### Adding a New Transform Type
1. Add enum value to `TransformType` in `domain/models/Types.kt`
2. Add system prompt case in each provider's `getSystemPrompt()` function
3. Add mock response in `FakeArticleTransformProvider`
4. Add UI option in the relevant screen (Article or Meeting)

### Worker Scheduling
- Periodic: `PeriodicWorkRequestBuilder` with `ExistingPeriodicWorkPolicy.UPDATE`
- One-time: `OneTimeWorkRequestBuilder` for manual "Run Now"
- Unique work names: `SynthesizerWorker_Periodic`, `SynthesizerWorker_OneTime`
