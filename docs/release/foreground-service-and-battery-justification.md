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

## Battery Optimization — NO restricted permission requested

**Status:** As of the Play Store hardening pass, Automatist **does not** declare the
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and **does not** invoke the direct
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` allow-dialog.

### Why it was removed

Google Play restricts that permission and the direct exemption dialog to apps whose
**core function is adversely affected** by Doze / App Standby (alarms, calendars,
real-time messaging, etc.). Automatist's scheduled workflows still run when the app is
battery-optimized — they may simply be delayed — so the app does **not** meet that bar.
Declaring the permission would create an unnecessary policy-review risk for no functional
gain.

### What the app does instead (no permission needed)

- The Schedule Status screen still detects whether the app is battery-optimized via the
  read-only `PowerManager.isIgnoringBatteryOptimizations()` (no permission required) and
  shows an explanatory card.
- The "Fix" button deep-links the user to the **system battery-optimization settings list**
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), where the user can find Automatist and
  choose "Don't optimize." Any app may open this screen; it requires no permission and no
  Play declaration.
- If the user does nothing, scheduled workflows still execute (possibly delayed). The app
  functions normally.

### Play Console action

- **None required for battery optimization.** Because the restricted permission is not
  declared, there is no battery-exemption declaration to fill in. If a reviewer asks, the
  honest answer is: the app does not request battery-optimization exemption; it only links
  the user to the standard system settings screen.
