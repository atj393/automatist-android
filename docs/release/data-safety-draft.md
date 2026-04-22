# Data Safety Form — Draft Answers for Play Console

Use these answers when filling out the Data Safety section in Google Play Console.
Based on actual app behavior as of v1.0.0. Review before submitting.

---

## Overview Questions

**Does your app collect or share any of the required user data types?**
Yes

**Is all of the user data collected by your app encrypted in transit?**
Yes — HTTPS enforced via `network_security_config.xml` (`cleartextTrafficPermitted="false"`)

**Do you provide a way for users to request that their data is deleted?**
Yes — users can delete individual history items, delete workflows, clear all app data via Android settings, or uninstall the app. Cloud sync data can be removed from Google Drive.

---

## Data Types

### Personal info
- **Name**: Not collected
- **Email address**: Collected only if user signs in with Google for Cloud Sync. Used for Drive authentication only, not stored beyond the sync session credential. Shared with: Google Drive API.
- **Phone number**: Not collected
- **Address**: Not collected

### Financial info
- **User payment info**: Not collected by the app. Purchases handled entirely through Google Play Billing.
- **Purchase history**: Google Play manages this. The app checks purchase status via Billing Library.

### App activity
- **App interactions**: Not collected or shared externally
- **In-app search history**: Not collected
- **Other user-generated content**: Collected locally only (workflow definitions, history items, saved notes). Shared with AI provider APIs when user initiates a transform. Shared with Google Drive only if user opts into Cloud Sync (workflow definitions only, no secrets).

### App info and performance
- **Crash logs**: Not collected (no crash reporting SDK currently integrated)
- **Diagnostics**: Not collected
- **Other app performance data**: Not collected

### Device or other IDs
- **Device or other IDs**: Not collected

---

## Data Sharing

**Does your app share user data with third parties?**
Yes

### What data is shared and with whom?

| Data Type | Shared With | Purpose |
|-----------|-------------|---------|
| User-entered text content | User's selected AI provider (OpenAI, Anthropic, or Google Gemini) | Core app functionality — text transformation |
| Workflow definitions (no secrets) | Google Drive (optional, user-initiated) | Cloud backup |
| Google Account email | Google Drive API (optional) | Authentication for cloud sync |

### Notes for each sharing partner:

**AI Providers (OpenAI / Anthropic / Google Gemini)**
- Text is sent only when the user explicitly initiates a transform or workflow run
- The user chooses which provider to use
- Only the text content and system prompt are sent — no device IDs, no user identity
- Each provider has its own privacy policy and data handling practices

**LOCAL_AI (On-device)**
- If the user selects LOCAL_AI (Gemini Nano or Gemma 3 1B), all processing occurs on-device
- No text content leaves the device for AI processing
- Gemini Nano is managed by Android system services (AICore)
- Gemma 3 1B is downloaded once (~529 MB) and stored in app-internal storage

**Google Drive**
- Optional feature — user must explicitly sign in and initiate backup
- Uses `appDataFolder` scope (private, app-scoped storage invisible to user in Drive UI)
- Only workflow definitions are synced — API keys and secrets are excluded
- User can disconnect at any time

**Google Play Billing**
- Standard Google Play purchase flow for Pro upgrade
- App does not handle payment information directly

---

## Data Collection

**Is any of the collected data processed ephemerally?**
- User text sent to AI providers: processed in-memory for the API call, not persisted on server by the app (provider's retention policies apply)
- Google account credential for Drive: held in memory during sync operation

**Is data collection required or optional?**
- AI provider text sharing: Required for core functionality (transforms won't work without it)
- Google Drive sync: Optional
- Purchase data: Optional (for Pro upgrade only)

---

## Data Handling Practices

| Practice | Answer |
|----------|--------|
| Data encrypted in transit | Yes (HTTPS enforced) |
| Data encrypted at rest | Partially — Room database and DataStore are not encrypted. API keys are in DataStore (TODO: migrate to EncryptedSharedPreferences). |
| Users can request data deletion | Yes |
| Data is transferred using a secure connection | Yes |

---

## Assumptions & Notes

- This draft is based on app behavior, not legal review. Have it reviewed before submission.
- If crash reporting (e.g., Firebase Crashlytics) is added later, the Data Safety form must be updated to declare diagnostics/crash log collection.
- AI provider data retention varies by provider. Users should review each provider's privacy policy. Consider adding links in the app.
- The `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission does not involve data collection but may require a separate declaration in Play Console (see foreground service justification doc).
