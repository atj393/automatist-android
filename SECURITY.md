# Security Policy

## Supported versions

Automatist is a single actively-developed Android app. Security fixes target the
**latest released version** on Google Play and the current `main` branch. Older versions
are not maintained separately.

## Reporting a vulnerability

**Please do not report security vulnerabilities in public GitHub Issues.**

Use one of these private channels:

- GitHub's **private vulnerability reporting** ("Report a vulnerability" under the
  repository's *Security* tab), or
- email **support@automatist.cloud**.

Please include enough detail to reproduce (affected version, device/Android version,
and steps). Do **not** include real secrets, API keys, tokens, or other people's personal
data in your report.

## What to expect

- This is a small open-source project maintained on a best-effort basis. There is
  **no guaranteed response time** and no bug-bounty program.
- Valid reports will be investigated and addressed in a reasonable timeframe; please allow
  time for a fix before any public disclosure (coordinated disclosure is appreciated).

## Scope notes

- Automatist has **no developer-operated backend**. Cloud AI, weather/route, and Drive
  requests go directly from the user's device to third parties the user configures, using
  the user's own credentials.
- API keys are stored on-device (currently plaintext DataStore; a move to the Android
  Keystore is planned) and are excluded from backup and from workflow exports.
- Reports about third-party services (OpenAI, Anthropic, Google, model hosts, etc.) should
  go to those providers; this policy covers the Automatist app itself.
