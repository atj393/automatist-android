# Google Play Release Checklist — Automatist v1.0.0

Status key: [x] done | [ ] manual action needed | [~] not applicable

---

## Codebase (handled in repo)

- [x] App icons — launcher icons in all mipmap densities + adaptive icon with foreground/background/monochrome layers (see "App Icon Assets" section in README)
- [x] `strings.xml` created with `app_name`
- [x] `AndroidManifest.xml` uses `@string/app_name` (not hardcoded)
- [x] `versionCode = 1`, `versionName = "1.0.0"` set in `build.gradle.kts`
- [x] Release build: minification + resource shrinking enabled
- [x] ProGuard/R8 rules cover all dependencies (Retrofit, Room, Hilt, Billing, Drive, Serialization)
- [x] Signing config reads from `keystore.properties` (gitignored)
- [x] `network_security_config.xml` enforces HTTPS only
- [x] `data_extraction_rules.xml` + `backup_rules.xml` exclude sensitive DataStore files
- [x] Debug billing toggle (`debugSetPro`) guarded by `BuildConfig.DEBUG` — compiled out by R8
- [x] Notification permission requested contextually (editor + schedule screen), not on launch
- [x] Battery optimization explanation shown in Schedule Status screen with "Fix" button
- [x] About section in Settings shows `BuildConfig.VERSION_NAME`
- [x] Security section in Settings explains data handling

## Signing & Build

- [ ] Generate release keystore (`keytool -genkeypair ...`) if not already done
- [ ] Create `keystore.properties` from `keystore.properties.example`
- [ ] Run `./gradlew bundleRelease` and verify signed AAB is produced
- [ ] Test release build on real device (verify R8 didn't break anything)
- [ ] **Back up keystore + passwords** in a secure location
- [ ] Enroll in Google Play App Signing (recommended)

## Google OAuth — Cloud Sync (Android OAuth Clients)

The app uses Google Play Services Auth (`GoogleSignIn`) for Cloud Sync (Google
Drive backup). It does **not** use Firebase Auth, `google-services.json`, or a
web/server client ID. OAuth client resolution is handled automatically by
Google Play Services based on the installed app's **package name + SHA-1
signing certificate**.

Two Android OAuth clients must be registered in Google Cloud Console
(APIs & Services → Credentials):

| Client | Package Name | SHA-1 Source |
|--------|-------------|--------------|
| Debug | `com.automatist.app.debug` | Default debug keystore (`./gradlew signingReport`) |
| Release / Play | `com.automatist.app` | Release keystore, or Play App Signing certificate |

**Common pitfall:** Debug builds use `applicationIdSuffix = ".debug"`, so the
debug package is `com.automatist.app.debug` — not `com.automatist.app`. Using
the wrong package name causes `DEVELOPER_ERROR` (code 10) at sign-in.

- [ ] Debug Android OAuth client registered with `com.automatist.app.debug` + debug SHA-1
- [ ] Release Android OAuth client registered with `com.automatist.app` + release SHA-1
- [ ] If using Play App Signing: also register the **upload signing** certificate SHA-1 (from Play Console → Setup → App signing) as an additional Android OAuth client for `com.automatist.app`
- [ ] OAuth consent screen configured (app name, support email, authorized domains)
- [ ] Test users added to consent screen if app is in "Testing" publish status
- [ ] Drive API enabled in Google Cloud Console (APIs & Services → Enabled APIs)

## Play Console — Store Listing

- [ ] Upload 512x512 app icon PNG (no alpha) — use `play-store/play_store_icon_512.png` from the repo
- [ ] Upload feature graphic (1024x500)
- [ ] Upload 4-8 phone screenshots (1080x1920 or 1440x2560)
  - Recommended: Dashboard, Workflow Editor, Execution screen, Article Transformer, Templates, Settings, Morning Brief output, Cloud Sync
- [ ] Tablet screenshots (optional but recommended)
- [ ] Enter app name: "Automatist"
- [ ] Enter short description (max 80 chars) — see `play-store-listing-draft.md`
- [ ] Enter full description (max 4000 chars) — see `play-store-listing-draft.md`
- [ ] Select category: Productivity
- [ ] Add tags: AI, Productivity, Summarizer, RSS, Workflows

## Play Console — App Content

- [ ] Privacy policy URL — host the draft from `docs/legal/privacy-policy-draft.md` and enter URL
- [ ] Complete Data Safety form — use answers from `data-safety-draft.md`
- [ ] Complete IARC content rating questionnaire (expected: Everyone)
- [ ] Declare target audience: 18+ (AI API age restrictions)
- [ ] Declare: no ads
- [ ] Declare: not a government app
- [ ] Foreground service declaration — use text from `foreground-service-and-battery-justification.md`
- [ ] Battery optimization justification — use text from same file

## Play Console — Monetization

- [ ] Go to Monetize > In-app products
- [ ] Create product ID: `automatist_pro` (must match `BillingManager.kt` exactly)
- [ ] Set as one-time purchase (not subscription)
- [ ] Set price for all target regions
- [ ] Product name: "Automatist Pro"
- [ ] Product description: "Unlock unlimited custom workflows"
- [ ] **Activate** the product (starts in draft state)

## Play Console — Testing

- [ ] Add license testers in Settings > License testing
- [ ] Upload AAB to **Internal testing** track first
- [ ] Test purchase flow on real device with tester account
- [ ] Test restore purchases
- [ ] Test free-tier limit enforcement (create 1 workflow, attempt 2nd)
- [ ] Review Pre-launch Report (Play Console auto-tests on multiple devices)
- [ ] Graduate to Closed testing (optional)
- [ ] Graduate to Production when confident

## Play Console — Release

- [ ] Upload ProGuard mapping file (`app/build/outputs/mapping/release/mapping.txt`) for crash deobfuscation
- [ ] Select countries/regions for distribution
- [ ] Set managed publishing or immediate publishing preference
- [ ] Submit for review

---

## Post-Launch

- [ ] Monitor Pre-launch Report for crashes/ANRs
- [ ] Monitor Android Vitals dashboard
- [ ] Consider adding crash reporting (Firebase Crashlytics or similar)
- [ ] For each future update: increment `versionCode`, update `versionName`
