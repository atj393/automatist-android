# Google Play Release Checklist — Automatist v1.1.2

Status key: [x] done in repo | [ ] manual action needed | [~] not applicable

> **Version:** `versionName = "1.1.2"`, `versionCode = 9` (see `app/build.gradle.kts`).
> Every Play upload must use a `versionCode` strictly **greater** than the last one
> already uploaded to that track. Bump `versionCode` (and `versionName` for user-visible
> releases) before each upload.
>
> **Release log:**
> - `1.0.0` / `versionCode 6` — free & open-source build; active in Internal + Closed testing.
> - `1.1.0` / `versionCode 7` — prepared 2026-07-13; already uploaded to a testing track and
>   must not be reused (adds MediaPipe `.task` model import + battery guidance). Release
>   notes: `docs/release/play-release-notes-1.1.0.txt`.
> - `1.1.1` / `versionCode 8` — targets Android 16 (API level 36); built and verified locally,
>   never uploaded; superseded by `9` below. Release notes:
>   `docs/release/play-release-notes-1.1.1.txt`.
> - `1.1.2` / `versionCode 9` — **this is the build to upload.** Same Android 16 targeting as
>   `8`, plus fixes for two of the three Play Console pre-launch warnings raised against the
>   `versionCode 7` upload (deprecated edge-to-edge status-bar API, optimized resource
>   shrinking). Release notes: `docs/release/play-release-notes-1.1.2.txt`. See "Android 16
>   (API 36) targeting" section below for the compatibility audit, and "Play Console
>   pre-launch warnings (versionCode 7)" for the warning-by-warning disposition.

---

## Billing-free release — required verification (this release)

This is the first release after Google Play Billing and the Pro entitlement were removed.
Verify all of the following before promoting to production:

1. [ ] Verify the uploaded AAB has **no BillingClient** (`apkanalyzer dex packages` shows no
   `com.android.billingclient`; `dependencyInsight --dependency billingclient --configuration
   releaseRuntimeClasspath` finds nothing)
2. [ ] Verify the merged release manifest has **no `com.android.vending.BILLING`** permission
3. [ ] Store listing set to **no in-app purchases** (and Free)
4. [ ] Review/deactivate the legacy `automatist_pro` product only at the safe point (see sequence)
5. [ ] Confirm Data Safety answers match the billing-free binary (see `data-safety-draft.md`)
6. [ ] Confirm the privacy policy contains no billing/purchase/refund/restore language
7. [ ] Complete the AI-generated-content policy review/declaration
8. [ ] Complete the foreground-service (`dataSync`) declaration
9. [ ] Confirm the privacy-policy URL works publicly (`https://automatist.cloud/privacy`)
10. [ ] Confirm the support email (`feedback@automatist.cloud`) is monitored
11. [ ] Confirm the app contains **no sponsor / donation / payment link**
12. [ ] Test upgrade from `versionCode = 6` (previous billing build) → data intact, all features free
13. [ ] Confirm the old `product_access` data is inert and harmless (no code reads it; no crash)
14. [ ] Confirm fresh installs and upgraded users have **identical** feature access
15. [ ] Confirm content rating (IARC) and target audience are set
16. [ ] Confirm the model-download and cloud-provider disclosures are present and accurate

### Safe Play Console sequence

1. Finish and test the billing-free build.
2. Upload it to the intended testing track and confirm the new artifact is active and functioning.
3. Update the store listing, Data Safety, and policy declarations to match the billing-free binary.
4. Handle the old `automatist_pro` product (deactivate) **only** once it can no longer affect the
   current production build or existing users — i.e. after the billing-free build is the live
   production artifact.

> Do not perform Play Console actions from the codebase; this checklist documents them for the
> release owner.

---

## Android 16 (API 36) targeting — compatibility audit (this release)

`compileSdk`/`targetSdk` raised 35 → 36. Reviewed against the official Android 16 behavior
changes; only items with a real, verified issue got a code change.

- [x] **Edge-to-edge** — `MainActivity` already uses `enableEdgeToEdge()`; all `Scaffold`s
  consume inset padding; dialogs/bottom sheets use default Material3 inset handling. No
  `windowOptOutEdgeToEdgeEnforcement` opt-out existed to remove. No code change needed.
- [x] **Predictive back** — no `BackHandler`/custom back-press interception exists anywhere in
  the app, and no `enableOnBackInvokedCallback` opt-out is set. Android 16 turns predictive
  back on by default at targetSdk 36; nothing in the app fights it. No code change needed.
  (Full shrink/preview transition animation between screens needs a newer Navigation Compose
  than this app currently pins — cosmetic only, not attempted this release.)
- [x] **Large screens / adaptive layout** — no orientation/aspect-ratio/resizability
  restrictions existed to remove. Found and fixed two real content-clipping risks that
  Android 16's large-screen enforcement would expose in split-screen/small-height windows:
  `ArticleScreen.kt`'s result view and all of `CloudSyncScreen.kt` had no scroll container
  (sibling `MeetingScreen.kt` already used `verticalScroll` for the equivalent content).
- [x] **Scheduling / WorkManager** — `WorkflowWorker` already promotes to a `dataSync`
  foreground service. `SynthesizerWorker` (Morning Brief) did not, despite doing the same
  class of long-running network + on-device-AI work; fixed to match `WorkflowWorker`'s
  pattern. No `BOOT_COMPLETED` receiver exists; the single reconciliation path
  (`AutomatistApp.onCreate`) has no duplicate-scheduling risk.
- [x] **Manifest / permissions** — both activities already declare explicit `exported`
  values; no `BILLING` permission; `FOREGROUND_SERVICE_DATA_SYNC` already documented and
  justified. No code change needed.
- [ ] **Native library 16 KB page-size alignment** (MediaPipe, AICore `.so` libraries) — NOT
  VERIFIED from source alone. Confirm on the actual built AAB/APK once available (e.g.
  `unzip -l` the native libs and check alignment, or `apkanalyzer`).
- [ ] **Physical-device / emulator validation** (edge-to-edge, predictive back, rotation,
  scheduling while locked, notification permission, on-device model inference) — NOT
  VERIFIED. Requires an Android 16 (API 36) emulator or device; see the manual smoke-test
  section below.

---

## Play Console pre-launch warnings (`versionCode 7`)

Three automated suggestions came back against the `versionCode 7` (targetSdk 35) upload.
Disposition for `versionCode 9`:

- [x] **"Edge-to-edge may not display for all users"** — fixed. `Theme.kt` was setting
  `window.statusBarColor` on every recomposition, fighting `enableEdgeToEdge()`'s transparent
  system bars. Removed.
- [x] **"App uses deprecated APIs for edge-to-edge" (`Window.setStatusBarColor`)** — fixed by
  the same change above.
- [x] **"Optimised resource shrinking isn't enabled"** — fixed. Added
  `android.r8.optimizedResourceShrinking=true` to `gradle.properties` (official AGP 8.12+
  opt-in; this project is on AGP 8.13.2).
- [ ] **"Upgrade AGP to 9.0+"** — deliberately not done. Real major-version migration (built-in
  Kotlin support, KMP plugin changes, `applicationVariants` API removal); AGP 8.13.2 already
  compiles cleanly against compileSdk 36. Track as a separate follow-up, not a release blocker.

---

## Codebase (handled in repo)

- [x] App icons — launcher icons in all mipmap densities + adaptive icon (foreground/background/monochrome)
- [x] `strings.xml` with `app_name`; manifest uses `@string/app_name`
- [x] `versionCode = 9`, `versionName = "1.1.2"` in `build.gradle.kts`
- [x] Release build: `isMinifyEnabled = true` + `isShrinkResources = true` (R8)
- [x] ProGuard/R8 rules cover all deps (Retrofit, Gson, Room, Hilt, Drive, Auth, OkHttp, Coroutines, WorkManager, Compose, kotlinx.serialization, MediaPipe LLM Inference, Google AI Edge AICore) — billing keep rules removed with the billing dependency
- [x] R8 strips `Log.v`/`Log.d` from release (`-assumenosideeffects`); surviving `Log.i/w/e` logs carry only metadata (no API keys, no workflow text, no raw provider responses); OkHttp BODY/HEADERS logging is `BuildConfig.DEBUG`-only
- [x] Signing config reads from `keystore.properties` (gitignored; never committed)
- [x] `network_security_config.xml` enforces HTTPS only (`cleartextTrafficPermitted="false"`)
- [x] `data_extraction_rules.xml` + `backup_rules.xml` exclude `secure_prefs` (API/service keys, now `EncryptedSharedPreferences`) and `cloud_sync` (Drive session metadata); the legacy `product_access` file is also excluded (inert — billing removed, no longer written or read); file names and domains verified against the real storage locations (`sharedpref` for `secure_prefs.xml`, `file` + `datastore/...` for the DataStore-backed files)
- [x] Notification permission requested contextually (editor + schedule screen), not on launch
- [x] **Battery optimization: the restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and the direct allow-dialog were removed.** The Schedule Status screen shows non-alarming guidance ("Scheduled workflows may be delayed by Android battery optimization … set Automatist to Unrestricted for the most reliable runs") and an **"Open battery settings"** button that deep-links to the normal system battery settings (no permission, graceful fallback). The workflow editor's scheduling note carries the same honest framing. See `foreground-service-and-battery-justification.md`.
- [x] Custom local model import is HTTPS-only, `.task`-only, SHA-256-verified, size-bounded, app-private storage; manifest import shows a review + license-acknowledgement screen before any download
- [x] About section in Settings shows `BuildConfig.VERSION_NAME`; Security/Support section explains data handling

## Signing & build

- [ ] Generate release keystore (`keytool -genkeypair ...`) if not already done
- [ ] Create `keystore.properties` from `keystore.properties.example`
- [ ] **Back up keystore + passwords** in a secure location (loss = cannot update the app)
- [ ] Enroll in Google Play App Signing (recommended)
- [ ] `./gradlew :app:bundleRelease` → signed AAB at `app/build/outputs/bundle/release/app-release.aab`
- [ ] Verify the release build runs on a real device (R8 did not break anything) — see device smoke test below

## Play Console — create the release / update an existing app

- [ ] First release: create the app in Play Console (default language, app/game, free/paid)
- [ ] Update: open the target track → **Create new release**
- [ ] Upload the **AAB** (not APK) — Play generates per-device APKs and serves 64-bit (arm64) automatically; the MediaPipe native libs include arm64-v8a
- [ ] Confirm the uploaded artifact: correct `versionCode`/`versionName`, `applicationId = com.automatist.app` (NOT the `.debug` suffix), target API 36
- [ ] Add release notes
- [ ] Upload the R8 mapping file (`app/build/outputs/mapping/release/mapping.txt`) for crash deobfuscation

## Google OAuth — Cloud Sync (Android OAuth Clients)

Uses Google Play Services Auth (`GoogleSignIn`) for Drive backup. No Firebase, no
`google-services.json`, no web/server client ID. OAuth resolves from **package name +
SHA-1 signing cert**.

- [ ] Debug Android OAuth client registered for `com.automatist.app.debug` + debug SHA-1
- [ ] Release Android OAuth client registered for `com.automatist.app` + release SHA-1
- [ ] If using Play App Signing: also register the **Play app-signing** cert SHA-1 for `com.automatist.app`
- [ ] OAuth consent screen configured (app name, support email, authorized domains)
- [ ] Drive API enabled in Google Cloud Console
- [ ] Common pitfall: wrong package name → `DEVELOPER_ERROR` (code 10) at sign-in

## Play Console — store listing

- [ ] 512×512 icon PNG (no alpha) — `play-store/play_store_icon_512.png`
- [ ] Feature graphic (1024×500)
- [ ] 4–8 phone screenshots; tablet optional
- [ ] App name: "Automatist"; short + full description from `play-store-listing-draft.md`
- [ ] Category: Productivity

## Play Console — App content (policy)

- [ ] **Privacy policy URL** — host the policy (draft in `docs/legal/privacy-policy-draft.md`) and enter the live URL. The app links to `https://automatist.cloud/privacy` (see `SupportConfig.kt`); confirm that page is **published and reachable** before submitting.
- [ ] **Data Safety form** — complete using `data-safety-draft.md`. Confirm it matches real behavior:
  - Data shared with third-party AI providers (OpenAI/Anthropic/Gemini) only on user action
  - Optional Google Drive backup (workflow definitions only; no secrets/API keys)
  - Optional download from a user-chosen model host (request metadata only; no workflow text)
  - On-device LOCAL_AI processes text without sending it anywhere
  - Data encrypted in transit (HTTPS); API keys stored app-private and excluded from backup
  - User can delete data (history/workflows/notes/models in app; clear data / uninstall; Drive backup removable)
- [ ] **Foreground service** declaration — `FOREGROUND_SERVICE_DATA_SYNC` for scheduled workflow execution; use text from `foreground-service-and-battery-justification.md`
- [ ] **Battery optimization** — nothing to declare; the restricted permission is not used (see same doc)
- [ ] **Content rating** (IARC questionnaire) — expected Everyone/PEGI 3; note the app produces AI-generated text from user/RSS input
- [ ] **Target audience & content** — declare 18+ (third-party AI provider terms commonly require adult users)
- [ ] **Ads** — declare: no ads
- [ ] **Government app** — declare: no
- [ ] **AI-generated content** — review under the current Play policy **"AI-Generated Content"** (`support.google.com/googleplay/android-developer/answer/14094294`). Automatist transforms user-provided text via cloud AI providers and on-device models and appears to fall under the policy's **productivity-tool** scope (it enhances existing content and hosts no user-to-user AI content); still ensure outputs cannot facilitate prohibited content and follow "Best Practices to Safeguard AI-Generated Content." Confirm the exact declaration in Play Console.
- [ ] **Payments / in-app products** — declare **no in-app purchases** (Play policy "Payments"); the app has no billing integration. Nothing to test.
- [ ] **Account deletion** — `[~]` N/A: the app does **not** create an app account (no developer backend). Google sign-in is only used to access the user's own Drive. Data-deletion paths: "clear app data / uninstall" for on-device data, and remove the Drive backup via the user's Google account (the app does not delete the Drive backup automatically — see privacy policy).

## App access (for review)

- [ ] `[~]` No login and no paywall gate app functionality — every feature is free and available to all users. Core features (article transform, workflows, on-device + cloud AI with the user's own keys) are usable without an account. Provide reviewer notes:
  - To exercise cloud AI, a reviewer must supply their own provider API key in Settings (the app ships none)
  - On-device models require a one-time download (Wi-Fi recommended)

## Play Console — Monetization

- [~] **No monetization.** The app has no in-app products, no subscriptions, and no ads. Set the
  app to **Free** with **no in-app purchases**. There is no billing integration to test.
- [ ] Handle the legacy `automatist_pro` Play product only at the safe point (see the release
  sequence below). The current build never references or queries it, so it cannot affect the
  billing-free binary or existing users; deactivate it only once the billing-free build is the
  active production artifact.

## Play Console — Testing track

- [ ] Upload AAB to **Internal testing** first
- [ ] Confirm every feature is available with no purchase prompt and no Pro/Free UI
- [ ] Confirm creating multiple workflows and enabling several at once works (no activation limit)
- [ ] Review the Pre-launch Report (auto-tests on many devices) for crashes/ANRs
- [ ] Graduate Internal → Closed → Production when confident

---

## Custom local model disclosure (store listing + review notes)

The app lets advanced users add a compatible MediaPipe `.task` model from an external
HTTPS source (manual entry or a JSON manifest). Disclose honestly:

- Only MediaPipe-compatible `.task` language models are accepted; no `.gguf`/`.safetensors`/`.bin`/APK/script/plug-in, and no arbitrary code is downloaded or executed
- Downloads are HTTPS-only, SHA-256-verified, size-bounded, and stored app-private
- The model file is fetched only after explicit user review + license acknowledgement
- The built-in Gemma model is hosted by the developer (GitHub Releases); Gemini Nano is system-managed by Android (AICore)
- Automatist verifies integrity (checksum) but cannot guarantee a third-party model's quality, performance, or license compliance — stated in-app and in `docs/local-model-import.md`

## Offline model hosting (do not break existing installs)

- **Built-in Gemma:** `atj393/automatist-models`, tag `offline-models-v1`, `gemma3-1b-it-int4.task` (~529 MB), SHA-256 `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`
- [ ] Verify the release tag + file are publicly downloadable before shipping
- [ ] Do **not** delete/rename the tag or change the URL/checksum after release — installs have it hardcoded

---

## Manual physical-device smoke test (REQUIRED before production)

Run on at least one real Android device (ideally one AICore-capable, e.g. Pixel 8+/Galaxy
S24+, and one ordinary device) using a **release** build (`assembleRelease` APK or the AAB
via internal testing). Record pass/fail for each.

1. [ ] **Fresh install** — first launch seeds Demo profile + sample workflow; no crash; dashboard renders
2. [ ] **Existing-user upgrade** — if upgrade test data is available, install over a prior version; Room migrates (DB v17) with no data loss; workflows/history intact
3. [ ] **Built-in Gemma** — Settings → On-device AI → download Gemma; verify progress + checksum success; create a LOCAL_AI profile; run an article summary; output appears
4. [ ] **Gemini Nano unsupported device** — on a non-AICore / pre-Android-14 device, "Check availability" returns a clear UNSUPPORTED state (no crash); on a supported device, it reports available
5. [ ] **Qwen external manual import** — add the independent Qwen2.5-0.5B `.task` via the manual form (URL + SHA-256 + size + license); validation accepts it; download + checksum succeed; run a workflow using it
6. [ ] **Qwen manifest import** — import the same model via a manifest URL; review screen shows name/source/license/size; confirm + license acknowledgement; download + run succeed
7. [ ] **Checksum failure handling** — point a custom source at a `.task` whose declared SHA-256 is wrong; download fails cleanly, no model installed, friendly error, app stable
8. [ ] **Remove file → remove source → re-download** — remove the downloaded file (source stays "Not installed"); remove the custom source entirely (any profile using it shows setup-required, not a crash); re-add + re-download works
9. [ ] **Cloud AI profile workflow** — add a real provider API key (OpenAI/Anthropic/Gemini); create a profile; run a workflow end to end; output + token usage shown; key never logged
10. [ ] **Google Drive sync** (if configured) — sign in; back up workflows; sign out; restore; verify no secrets in the backup envelope
11. [ ] **No purchase path** — confirm there is no Upgrade screen, no Pro/Free badge, and no purchase or restore control anywhere in the UI; no path can open a Google Play purchase flow
12. [ ] **Airplane-mode after download** — download a local model, enable airplane mode, run a LOCAL_AI workflow → succeeds fully offline; a cloud-provider workflow fails with a clear "no network" message (no crash)

Also confirm during the pass:
- [ ] No sensitive data in `adb logcat` for a release build (no API keys, prompt/workflow text, or raw responses)
- [ ] Scheduled (Daily/Weekly) workflow fires; the foreground notification appears during execution
- [ ] Rotation / app-restart mid-workflow does not crash or lose committed results

### Battery-optimization guidance (UX) test

1. [ ] Enable a scheduled workflow (Daily/Weekly/Interval) in the workflow editor; the scheduling note explains delays may occur and to set Unrestricted for reliability
2. [ ] Open **Schedule Status**
3. [ ] Confirm the battery guidance card appears (non-alarming wording; mentions Unrestricted/Don't optimize) and the always-shown info card explains delays are expected Android behaviour
4. [ ] Tap **Open battery settings**
5. [ ] Confirm the normal Android battery settings screen opens (battery-optimization list, or app battery details on the fallback path) — **no** direct allow/exemption dialog, no crash
6. [ ] Return to Automatist; the page still works; the snackbar guidance ("…Don't optimize" / "…Unrestricted") is shown
7. [ ] Run a short **Interval** schedule with the phone locked
8. [ ] Confirm the scheduled run still fires, or is honestly delayed (the app does not claim exact timing); no "scheduling broken" messaging
9. [ ] Confirm `adb shell dumpsys package com.automatist.app | grep -i battery` (or the merged manifest) shows **no** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission

---

## Post-launch

- [ ] Monitor Pre-launch Report, Android Vitals (crashes/ANRs)
- [ ] Consider adding crash reporting (e.g., Firebase Crashlytics) — currently none integrated
- [ ] Each update: increment `versionCode`, update `versionName`, re-upload mapping file
