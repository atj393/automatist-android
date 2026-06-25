# Google Play Release Checklist — Automatist v1.0.0

Status key: [x] done in repo | [ ] manual action needed | [~] not applicable

> **Version:** `versionName = "1.0.0"`, `versionCode = 6` (see `app/build.gradle.kts`).
> Every Play upload must use a `versionCode` strictly **greater** than the last one
> already uploaded to that track. Bump `versionCode` (and `versionName` for user-visible
> releases) before each upload.

---

## Codebase (handled in repo)

- [x] App icons — launcher icons in all mipmap densities + adaptive icon (foreground/background/monochrome)
- [x] `strings.xml` with `app_name`; manifest uses `@string/app_name`
- [x] `versionCode = 6`, `versionName = "1.0.0"` in `build.gradle.kts`
- [x] Release build: `isMinifyEnabled = true` + `isShrinkResources = true` (R8)
- [x] ProGuard/R8 rules cover all deps (Retrofit, Gson, Room, Hilt, Billing, Drive, Auth, OkHttp, Coroutines, WorkManager, Compose, kotlinx.serialization, MediaPipe LLM Inference, Google AI Edge AICore)
- [x] R8 strips `Log.v`/`Log.d` from release (`-assumenosideeffects`); surviving `Log.i/w/e` logs carry only metadata (no API keys, no workflow text, no raw provider responses); OkHttp BODY/HEADERS logging is `BuildConfig.DEBUG`-only
- [x] Signing config reads from `keystore.properties` (gitignored; never committed)
- [x] `network_security_config.xml` enforces HTTPS only (`cleartextTrafficPermitted="false"`)
- [x] `data_extraction_rules.xml` + `backup_rules.xml` exclude the three secret DataStores (`secure_prefs_stub`, `product_access`, `cloud_sync`); file names verified to match the real DataStore names
- [x] Debug Pro override (`debugSetPro` / local `pro_unlocked`) guarded by `BuildConfig.DEBUG`; never set in release, so release entitlement = Play ownership only
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
- [ ] Confirm the uploaded artifact: correct `versionCode`/`versionName`, `applicationId = com.automatist.app` (NOT the `.debug` suffix), target API 35
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
- [ ] **AI-generated content** — declare per current Play "AI-generated content" policy (the app transforms text via AI providers and on-device models)
- [ ] **Account deletion** — `[~]` N/A: the app does **not** create an app account. Google sign-in is only used to access the user's own Drive. Document this if asked; offer "clear app data / uninstall" and "remove Drive backup" as the data-deletion paths.

## App access (for review)

- [ ] `[~]` No login/paywall gates app functionality. Core features (article transform, workflows, on-device + cloud AI with the user's own keys) are usable without an account. Pro is an optional one-time IAP. Provide reviewer notes:
  - To exercise cloud AI, a reviewer must supply their own provider API key in Settings (the app ships none)
  - On-device models require a one-time download (Wi-Fi recommended)

## Play Console — Monetization

- [ ] Create in-app product ID `automatist_pro` (must match `BillingManager.kt` exactly)
- [ ] One-time purchase (not subscription); set prices; name "Automatist Pro"; description "Unlock unlimited custom workflows"
- [ ] **Activate** the product (starts in draft)
- [ ] Verify restore-purchases works (UpgradeScreen "Restore"); free user with no purchase sees a neutral "No previous purchase found", not an error

## Play Console — Testing track

- [ ] Add license testers (Settings → License testing)
- [ ] Upload AAB to **Internal testing** first
- [ ] Test purchase + restore on a real device with a tester account (Play test track)
- [ ] Test free-tier limit (create 1 workflow, attempt a 2nd → UpgradePrompt)
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
11. [ ] **Pro purchase/restore** — on the Play internal-test track with a license tester: purchase `automatist_pro` → Pro unlocked → workflow limit removed; reinstall → restore re-grants Pro; no-purchase restore shows the neutral message
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
