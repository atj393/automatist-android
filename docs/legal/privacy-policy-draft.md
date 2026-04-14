# Privacy Policy — Automatist

**Last updated:** [INSERT DATE BEFORE PUBLISHING]

> **Note:** This is a first draft for review. It is not legal advice. Have it reviewed by a legal professional before publishing. Remove this note before publishing.

---

## Introduction

Automatist ("the app") is a workflow-based AI text transformation utility for Android. This privacy policy explains what data the app collects, how it is used, and your choices.

## Developer Information

- **Developer:** [INSERT DEVELOPER NAME OR ENTITY]
- **Contact:** [INSERT CONTACT EMAIL]

---

## Data We Collect

### Data stored on your device

All core app data is stored locally on your device using Android's Room database and DataStore:

- **Workflow definitions** — templates, actions, triggers, and instructions you create
- **Workflow run history** — output text, execution timestamps, token usage, error messages
- **Transform history** — saved article summaries, meeting briefs, and other AI-generated outputs
- **Saved notes** — reusable text content you create for use in workflows
- **App settings** — AI provider selection, display preferences
- **API keys** — stored locally in DataStore on your device

This data is not transmitted to any server except as described below.

### Data sent to third-party AI providers

When you run a workflow or transform content, the text you provide is sent to the AI provider you selected:

- **OpenAI** (api.openai.com) — governed by [OpenAI's privacy policy](https://openai.com/privacy)
- **Anthropic** (api.anthropic.com) — governed by [Anthropic's privacy policy](https://www.anthropic.com/privacy)
- **Google Gemini** (generativelanguage.googleapis.com) — governed by [Google's privacy policy](https://policies.google.com/privacy)

**What is sent:** Your input text and a system prompt describing the desired transformation. No device identifiers, personal information, or API keys for other providers are included in these requests.

**When it is sent:** Only when you explicitly initiate a transform or when a workflow you configured runs on its schedule.

### Data sent to external service APIs

Some workflow actions fetch data from external services:

- **OpenWeatherMap** (api.openweathermap.org) — location name sent for weather data
- **OpenRouteService** (api.openrouteservice.org) — origin and destination sent for route/travel time data
- **RSS feed sources** — your configured feed URLs are fetched directly

These services receive only the query data needed for their function (location, URL). No personal information is sent.

### Data sent to Google (optional features)

- **Google Drive Cloud Sync** — If you opt in to cloud backup, workflow definitions (without API keys or secrets) are uploaded to your Google Drive private app folder (`appDataFolder`). This requires signing in with your Google Account. Your Google Account email is used for authentication only and is not stored permanently by the app.
- **Google Play Billing** — If you purchase Automatist Pro, the transaction is handled by Google Play. The app does not collect or store payment information.

---

## Data We Do NOT Collect

- No analytics or telemetry
- No crash reporting (no Firebase, Sentry, or similar SDK)
- No advertising identifiers
- No device fingerprinting
- No location tracking (location names for weather/route actions are entered by the user, not read from device sensors)
- No contact or phone data
- No usage tracking beyond what is stored locally on your device

---

## Data Sharing

We do not sell, rent, or share your data with any party except:

1. **AI providers** — text content sent for transformation, as described above, only when you initiate it
2. **Google Drive** — workflow definitions only, if you opt in to cloud sync
3. **External service APIs** — query data only, for weather and route actions you configure

---

## Data Security

- All network communication uses HTTPS (enforced via Android network security configuration)
- API keys are stored locally on your device
- Workflow exports and cloud backups automatically strip API keys and sensitive fields
- Android backup and data transfer exclude API key storage

**Known limitation:** API keys are currently stored in Android DataStore without encryption. We plan to migrate to Android Keystore or EncryptedSharedPreferences in a future update.

---

## Your Choices

- **AI provider:** You choose which provider processes your text. You can change providers or stop using them at any time.
- **Cloud sync:** Entirely optional. You can sign out or stop syncing at any time. Your Google Drive data can be deleted from Drive.
- **Notifications:** Optional. You can deny the notification permission and the app continues to work — you just won't receive alerts for scheduled workflow results.
- **Battery optimization:** Optional. Denying the exemption means scheduled workflows may be slightly delayed by the system.
- **Data deletion:** You can delete individual history items, workflows, and saved notes within the app. You can clear all app data via Android Settings > Apps > Automatist > Clear Data. Uninstalling the app removes all local data.

---

## Children's Privacy

Automatist is not directed at children under 13. We do not knowingly collect personal information from children. The app relies on third-party AI APIs that may have their own age restrictions.

---

## Changes to This Policy

We may update this privacy policy from time to time. The "Last updated" date at the top will reflect the most recent revision. Continued use of the app after changes constitutes acceptance.

---

## Contact

If you have questions about this privacy policy, contact us at:

**[INSERT CONTACT EMAIL]**
