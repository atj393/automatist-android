# Privacy Policy — Automatist

**Last updated:** 2026-07-11 <!-- set to the actual publish date before going live -->

> **Note:** This is a draft for review. It is not legal advice. Have it reviewed by a legal
> professional before publishing. Remove this note before publishing.

---

## Introduction

Automatist ("the app") is a workflow-based AI text-transformation utility for Android. This
policy explains what data the app handles, where it goes, and your choices. Automatist is
**free** — there are no ads, no subscriptions, no in-app purchases, and no feature paywalls.

## Developer Information

- **Developer:** Alexis Johnson
- **Contact:** support@automatist.cloud

> Confirm the developer/legal identity and a monitored contact address are correct before
> publishing.

## No Automatist backend

Automatist does **not** operate a developer-run application server. The app has no analytics,
telemetry, crash-reporting, advertising, or tracking SDK, and there is no Automatist server that
receives your content. Data leaves your device only in the specific, user-initiated cases
described below, and in each case it goes to a third party **you** choose (an AI provider, Google
Drive, or an external source you configure) — never to Automatist.

`automatist.cloud` is used only as the address of static support and legal pages (see Contact); it
is not a data-collection endpoint.

---

## Data stored on your device

All core app data is stored locally using Android's Room database (`automatist.db`) and DataStore:

- **Workflow definitions** — templates, actions, triggers, instructions, and the URLs/feeds you enter
- **Workflow run history** — output text, timestamps, token usage, and (redacted) error messages
- **Transform history** — saved summaries, briefs, and other AI-generated outputs
- **Saved notes** — reusable text you create
- **App settings** — provider selection, Morning Brief configuration, onboarding flags
- **API keys and service keys** — stored locally in DataStore

This data is not transmitted anywhere except as described below.

## Data processed entirely on-device

If you select the on-device provider (**LOCAL_AI** — Gemini Nano, the downloadable Gemma model, or
a compatible model you add yourself), your text is processed entirely on your device. **No workflow
content is sent to any server for local inference.** Gemini Nano runs via the Android system
service (AICore); its behaviour depends on your device and that system service. Downloadable models
run from app-private storage with no network access after they are downloaded.

## Cloud AI providers (optional)

When you run a transform or workflow using a **cloud** provider, the app sends the text you provide
(your input and a system prompt describing the transformation, plus the model id) **directly** to
the provider you selected, authenticated with the API key you supplied:

- **OpenAI** (`api.openai.com`) — [privacy policy](https://openai.com/privacy)
- **Anthropic** (`api.anthropic.com`) — [privacy policy](https://www.anthropic.com/privacy)
- **Google Gemini** (`generativelanguage.googleapis.com`) — [privacy policy](https://policies.google.com/privacy)
- **A custom, OpenAI-compatible endpoint you configure** — if you create a profile with your own
  base URL (for example a self-hosted or alternative provider), the same content is sent to **the
  host you entered**, under that host's terms. Review the terms and privacy policy of any endpoint
  you configure.

**What is sent:** your input text and the system prompt. No device identifiers, and no API keys for
other providers, are included. **When:** only when you explicitly start a transform, or when a
workflow you configured runs on its schedule. **Why it is optional:** the on-device provider above
performs the same operations without sending any content off the device.

The provider processes your data under its own terms and retention practices; Automatist does not
control or receive that processing.

## API keys and service keys

You supply your own keys. They are stored locally on your device in DataStore and are sent only to
the matching provider/service as the authentication for your own requests. They are excluded from
Android backup and device transfer, and are stripped from workflow exports and cloud backups.

**Known limitation:** keys are currently stored in DataStore **without encryption at rest**. A
future update is planned to move them to the Android Keystore / EncryptedSharedPreferences.

## External model downloads (optional)

You may download a built-in model or add a compatible MediaPipe `.task` model by entering its HTTPS
download URL, SHA-256 checksum, expected size, and license link. The model is downloaded only after
you tap **Download**.

- The **model host** (for the built-in model, GitHub Releases; for a model you add, the host you
  chose) receives the normal file-download request and associated network information such as your
  IP address, under that host's own privacy policy.
- The host does **not** receive your workflow text or API keys merely because you download a model.
- You are responsible for reviewing the third-party model's license.
- Checksum verification protects download **integrity**; it does not guarantee a model's quality,
  performance, or license compliance.

The downloaded file is stored in app-private storage and is removed when you remove it in the app or
uninstall Automatist.

## Google Drive sync (optional)

If you opt in to cloud backup:

- You sign in with your Google Account and grant the **Drive app-data scope**
  (`DRIVE_APPDATA`) only.
- The app accesses your **Google Account email**, which it stores locally to show the connected
  account. No other Google profile data is stored.
- Only **workflow definitions** are uploaded — API keys and other secrets are stripped before
  upload. The backup is written to Drive's **`appDataFolder`**, a private, app-scoped folder that
  is not visible in your normal Drive files.
- Because `appDataFolder` is tied to your own Google Account and there is no Automatist backend,
  **the developer cannot access your Drive backup.**
- **Disconnecting** cloud sync clears the app's **local** sync status (connected flag, stored
  email, last-backup info). It does **not** delete the backup file already stored in your Drive and
  does not sign you out of Google. To remove a backup from Drive, use your Google Account's
  app-data controls (Google Account → Data & privacy → Third-party apps/services). The app does not
  provide an in-app "delete cloud backup" button.

## External workflow sources (optional)

Workflows you configure may connect to external services. Each receives only the normal network
request needed to return the result you asked for:

- **URLs / RSS feeds / REST APIs** you enter — fetched directly from the host you specify
- **OpenWeatherMap** (`api.openweathermap.org`) — the location text you enter, plus your service key
- **OpenRouteService** (`api.openrouteservice.org`) — the origin/destination you enter, plus your
  service key
- **Model hosts** — as described under model downloads

Location and route inputs are **text you type**; the app does not read device GPS or sensor
location (it holds no location permission).

## Support and feedback

The developer receives information only when **you** choose to send it — for example by emailing
`feedback@automatist.cloud` or opening a support page. A support email may optionally include the
app version, basic device info, a run id, or an error message, but only the fields you include, and
only if you send it. Nothing is transmitted automatically.

## Security

- All network communication uses HTTPS; cleartext HTTP is disabled app-wide (Android network
  security configuration).
- Release builds do not log request/response bodies, prompts, or keys; error messages are redacted
  of keys/tokens before they are shown or stored.
- API keys are excluded from Android backup and device transfer, and stripped from exports/backups.
- See the encryption-at-rest limitation noted above.

## Data We Do NOT Collect

- No analytics or telemetry
- No crash reporting (no Firebase, Sentry, or similar SDK)
- No advertising identifiers
- No device fingerprinting
- No device location (weather/route inputs are typed by you, not read from sensors)
- No contacts or phone data
- No payment or purchase data (the app has no billing integration)

## Your Choices and Data Deletion

- **AI provider:** you choose which provider (if any) processes your text, and can switch to the
  on-device provider or stop at any time.
- **Cloud sync:** entirely optional; you can disconnect at any time (see the Drive section for what
  disconnect does and how to remove a Drive backup).
- **Notifications / battery optimization:** optional; the app works if you decline them (scheduled
  runs may be delayed).
- **Local deletion:** delete individual history items, workflows, saved notes, and downloaded
  models in the app; clear everything via Android Settings → Apps → Automatist → Storage → Clear
  data; or uninstall to remove all local data.

## Children's Privacy

Automatist is intended for adults (the app's Play target audience is 18+, and third-party AI
providers commonly require adult users). It is not directed at children, and the developer does not
knowingly collect personal information from children.

## Changes to This Policy

We may update this policy from time to time; the "Last updated" date reflects the latest revision.

## Contact

Questions about this policy: **support@automatist.cloud**
