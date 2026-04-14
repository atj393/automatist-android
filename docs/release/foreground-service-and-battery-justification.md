# Foreground Service & Battery Optimization Justification

Draft text for Google Play Console declarations. Copy and adapt as needed.

---

## Foreground Service Type: DATA_SYNC

**Where to declare:** Play Console > Policy > App content > Foreground service permissions

**Service:** `androidx.work.impl.foreground.SystemForegroundService`
**Type:** `dataSync`

### What it does

Automatist uses WorkManager to execute user-configured workflows in the background. A workflow run involves fetching data from multiple sources (RSS feeds, web URLs, weather APIs, route APIs), combining the results, and sending them to an AI provider for text transformation. This pipeline can take 10-30 seconds depending on the number of actions and API response times.

WorkManager promotes the job to a foreground service to prevent the system from killing it mid-execution, which would result in incomplete output and wasted API calls.

### Draft justification text for Play Console

> Automatist uses a foreground service with DATA_SYNC type to reliably execute user-configured workflow pipelines. Each workflow fetches data from multiple sources (RSS feeds, web URLs, weather/route APIs), combines the results, and sends them to an AI provider for text transformation. This multi-step process takes 10-30 seconds and must complete atomically to avoid partial output and wasted API quota. The foreground service is only active during user-initiated or user-scheduled workflow execution, never continuously. Users configure workflows and schedules explicitly in the app.

### Supporting evidence to include

- Screenshots of the workflow editor showing trigger configuration (Manual / Daily / Weekly)
- Screenshot of the live execution screen showing stage-by-stage progress
- Screenshot of the Schedule Status screen showing user-configured schedules

---

## Battery Optimization Exemption: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

**Where to declare:** Play Console > Policy > App content (if flagged during review)

### Why the app requests it

Automatist offers time-sensitive scheduled workflows (e.g., "Morning Commute Brief at 7:00 AM" with weather and route data). Battery optimization can delay WorkManager jobs by minutes to hours, making time-sensitive outputs stale or useless.

The exemption is:
- **Not requested on app launch** — only surfaced in the Schedule Status screen after the user has configured a scheduled workflow
- **Explained in-app** before the system dialog appears: "Your device may delay scheduled runs to save battery. Disabling battery optimization for Automatist improves reliability."
- **Optional** — if the user denies or ignores it, workflows still run but may be delayed. The app continues to function normally.
- **Contextual** — only relevant to users who set up scheduled (Daily/Weekly) workflows

### Draft justification text for Play Console

> Automatist allows users to schedule daily or weekly AI-powered workflows (e.g., a morning briefing combining weather, commute, and news data). These workflows are time-sensitive — a "Morning at 7:00" brief delivered at 10:00 loses most of its value. The app requests battery optimization exemption only from the Schedule Status screen, after the user has configured a scheduled workflow, with a clear explanation of why. If denied, workflows still execute but may be delayed by the system. The app does not request this permission at launch or outside the scheduling context.

### Supporting evidence to include

- Screenshot of the Schedule Status screen showing the battery optimization card with explanation
- Screenshot of a configured Daily trigger in the workflow editor
- Screenshot showing the "Fix" button and its explanation text
