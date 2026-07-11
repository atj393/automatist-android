# Data Safety Form — Draft Answers for Play Console

Rebuilt from the actual data-flow audit of the current **billing-free** build. Not legal advice —
review before submitting. Google's Data Safety definitions used here:

- **Collect** = the app transmits user data **off the device**. Data processed **only on-device**
  and not sent off device does **not** need to be disclosed. **Ephemeral** processing (held in
  memory, kept no longer than needed to service the request) is also exempt.
- **Share** = transfer of collected data to a third party. **User-initiated transfers** to a third
  party, made on prominent in-app disclosure and user action, may be exempt from "sharing"
  disclosure — but are assessed conservatively below.

Key facts that drive the answers (see "Evidence" at the end):

- **There is no Automatist backend and no analytics/telemetry/ads/crash SDK.** The developer
  collects nothing. Every off-device transmission is user-initiated and goes to a third party the
  **user** chooses (an AI provider, the user's own Google Drive, or an external source configured
  in a workflow).
- **Purchase history / payment information: NOT collected.** Google Play Billing has been removed;
  the app has no billing integration and makes no purchase queries. An inert legacy
  `product_access` preference file may remain on upgraded devices, but **no code reads it** — it
  must **not** be declared as purchase-history/payment collection.
- **On-device (LOCAL_AI) processing sends nothing off device** and is not disclosed as collection.

---

## Overview Questions

**Does your app collect or share any of the required user data types?**
Yes — via user-initiated transmission to third parties the user selects (AI provider, the user's
own Google Drive, external workflow sources). The developer operates no server and collects nothing.

**Is all of the user data collected by your app encrypted in transit?**
Yes — HTTPS enforced app-wide via `network_security_config.xml`
(`cleartextTrafficPermitted="false"`).

**Do you provide a way for users to request that their data is deleted?**
Yes — delete history items / workflows / notes / downloaded models in-app; clear all data via
Android Settings; or uninstall. A Google Drive backup is removed via the user's Google Account
app-data controls (the app clears only its local sync status on disconnect — see notes).

---

## Data types — declarations

### Personal info
- **Name / phone / address:** Not collected.
- **Email address:** Obtained from Google Sign-In **only if** the user opts into Google Drive
  backup; stored locally to display the connected account. **Optional.** Purpose: **App
  functionality** (authenticate access to the user's own Drive). Not sent to any Automatist server.
  *Uncertainty:* whether Google Sign-In email must be declared as "collected"/"shared with Google"
  is a Play Console judgment — declare conservatively as collected/optional; confirm in Console.

### Financial info
- **Payment info:** **Not collected.** No billing integration.
- **Purchase history:** **Not collected.** No billing integration; the inert legacy
  `product_access` file is never read (do not declare).

### App activity
- **App interactions / in-app search history:** Not collected.
- **Other user-generated content** (input text, prompts, workflow content, saved notes, run
  outputs): Stored **locally**. Transmitted off-device **only** when the user runs a **cloud**
  provider or an external-source action, or opts into Drive backup (see Sharing). **Optional** (the
  on-device provider performs the same work with no transmission). Processed **ephemerally** for
  the API call (not persisted off-device by the developer; the chosen provider's retention applies).

### Files and docs
- Not applicable — the app accepts **text** only (typed, pasted, or shared via the Android
  Sharesheet). It does not import or upload user files/documents.

### App info and performance
- **Crash logs / diagnostics / other performance data:** **Not collected.** No crash-reporting or
  analytics SDK is integrated; all logging is local logcat only, and release builds log no bodies
  or keys.

### Device or other IDs
- **Not collected.** No advertising ID, no device identifiers. (External hosts you contact receive
  your IP address as an inherent part of any network request; the app does not itself collect or
  transmit a device identifier.)

---

## Data sharing (third parties the user selects)

The app shares data only through **user-initiated** transfers to third parties the user chooses.
Assessed conservatively:

| Data | Recipient | Trigger | Purpose | Notes |
|------|-----------|---------|---------|-------|
| Input text + system prompt | The user-selected AI provider: OpenAI, Anthropic, Google Gemini, **or a custom OpenAI-compatible host the user configures** | User starts a transform, or a user-configured schedule runs | App functionality (text transformation) | User chooses the provider and supplies the key; destination is not a fixed set. May qualify as a user-initiated transfer; disclose to be safe. |
| Location/route input text | OpenWeatherMap / OpenRouteService | User runs a weather/route action | App functionality | Text the user typed; not device location. |
| Content of user-entered URLs/RSS/API requests | The host the user entered | Workflow/brief run | App functionality | Destination chosen by the user. |
| Workflow definitions (secrets stripped) | The user's own Google Drive (`appDataFolder`) | User taps Backup | App functionality (cloud backup) | Private to the user's Google account; developer cannot access. |
| Google account email | Google (sign-in) | User connects Drive | App functionality (auth) | See Personal info. |
| Model-download request | GitHub Releases (built-in) or a user-chosen HTTPS host | User taps Download | App functionality | Inbound file download; host receives an ordinary request (incl. IP), **no** prompt/content/keys. |

### Notes per recipient

**AI providers (incl. custom endpoints)** — text is sent only on explicit user action or a
user-configured schedule; the user selects the provider and supplies the key; only text + system
prompt + model id are sent (no device IDs, no other providers' keys). Each provider's own privacy
policy governs its handling and retention.

**On-device (LOCAL_AI)** — Gemini Nano (AICore) or a downloadable/compatible model: **all inference
is on-device; no content is sent off device.** Not disclosed as collection/sharing. The only
network the local feature uses is the one-time model **download**, which sends no user content.

**Google Drive** — optional; `DRIVE_APPDATA` scope; only workflow definitions (secrets stripped);
stored in the user's private app-data folder; developer has no access. Disconnect clears local
status only; it does not delete the Drive backup (removed via the user's Google Account).

**Model hosts** — receive only a file-download / manifest request (and inherent network metadata
such as IP). SHA-256 verified for integrity; users review third-party model licenses.

---

## Data collection — required vs optional, ephemeral

- **AI text transmission (cloud):** Optional — required only if the user chooses a cloud provider;
  the on-device provider needs no transmission. Processed ephemerally for the request.
- **Google Drive backup / email:** Optional.
- **Weather/route/URL/RSS/API actions:** Optional — only if the user builds and runs them.
- **Model download:** Optional.
- Nothing is collected by the developer, and nothing is collected automatically at rest by a
  developer server (there is none).

---

## Data handling practices

| Practice | Answer |
|----------|--------|
| Data encrypted in transit | Yes (HTTPS enforced app-wide) |
| Data encrypted at rest | Partially — Room DB and DataStore are not encrypted. API/service keys are in DataStore (plaintext; planned migration to Keystore/EncryptedSharedPreferences). |
| Users can request data deletion | Yes (in-app deletes; clear data; uninstall; Drive backup via Google Account) |
| Data transferred over a secure connection | Yes |

---

## Evidence used for each answer

- **No backend / no tracking SDKs:** dependency set in `gradle/libs.versions.toml` +
  `app/build.gradle.kts` (only AndroidX/Kotlin/Hilt/Room/DataStore/WorkManager/Retrofit/OkHttp/
  Drive+Auth/AICore/MediaPipe; no Firebase/Crashlytics/GA/Sentry/AdMob). `AutomatistApp.onCreate`
  makes no analytics/billing call.
- **No billing / purchase / payment:** `data/billing/` and `domain/access/` deleted;
  `BillingRuntimeRemovedTest`; no `com.android.billingclient` on `releaseRuntimeClasspath`; merged
  manifest has no `BILLING` permission. Legacy `product_access` is excluded from backup and read by
  no code.
- **Cloud AI transmission:** `data/providers/{OpenAI,Anthropic,Gemini}ArticleTransformProvider.kt`,
  `OpenAICompatibleProvider.kt` (user-defined base URL), `di/NetworkModule.kt` (hosts), key from
  `platform/security/KeystoreSecureStorage.kt`.
- **On-device inference sends nothing:** `data/providers/LocalAIArticleTransformProvider.kt`,
  `data/offline/MediaPipeInferenceEngine.kt` (no network on the inference path; only prompt-length
  logging).
- **Model downloads:** `data/offline/ModelDownloadManager.kt`, `domain/offline/OfflineModel.kt`
  (SHA-256 pinned), `OfflineModelRegistry.kt` / `ManifestFetcher.kt` (HTTPS-only, size-bounded).
- **Workflow external sources:** `domain/engine/WorkflowExecutionEngine.kt` (FETCH_URL / RSS /
  API_GET / WEATHER / ROUTE_TIME), `data/network/RssParser.kt`, service keys via `SecureStorage`.
- **Google Drive + email:** `data/sync/CloudSyncManager.kt` (`DRIVE_APPDATA`, `appDataFolder`),
  `feature/sync/CloudSyncScreen.kt` (`requestEmail`), `data/sync/LocalCloudSyncRepository.kt`
  (local `cloud_sync` store; disconnect clears local only).
- **Keys / storage:** `KeystoreSecureStorage.kt` (`secure_prefs_stub`, plaintext),
  `res/xml/backup_rules.xml` + `data_extraction_rules.xml` (exclude `secure_prefs_stub`,
  `cloud_sync`, legacy `product_access`).
- **Logging redaction:** `di/NetworkModule.kt` (body logging `BuildConfig.DEBUG`-only),
  `domain/engine/DiagnosticException.kt` (`ErrorRedactor`), `proguard-rules.pro` (strips
  `Log.v/Log.d` in release).
- **Encryption in transit:** `res/xml/network_security_config.xml`
  (`cleartextTrafficPermitted="false"`).

---

## Assumptions & items to confirm in Play Console

- Google Sign-In email declaration (collected/shared) is a Console judgment — confirm.
- Whether user-initiated transfers to a user-selected AI provider are declarable under the
  "user-initiated transfer" exemption or must be listed as sharing — disclose conservatively and
  confirm; do **not** minimize because the developer does not operate the provider.
- AI provider data retention varies by provider (users should review each provider's policy).
- Encryption-at-rest for keys is a known limitation (plaintext DataStore) — reflected above.
- If a crash-reporting/analytics SDK is ever added, this form must be updated.
