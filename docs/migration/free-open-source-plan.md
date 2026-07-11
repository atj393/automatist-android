# Automatist Free and Open-Source Migration

## Target product

- Free for all users
- No ads
- No subscriptions
- No one-time unlock
- No feature paywalls
- No payment or sponsorship link inside the Android app
- Optional external support through:
  - GitHub README
  - GitHub Sponsors
  - project website
  - external release announcements

## Phase 0 findings (audit)

Established before any code change:

- Room database version **17**; database file name **`automatist.db`**.
- Release application ID **`com.automatist.app`** (debug: `com.automatist.app.debug`).
- Billing / entitlement is **not stored in Room** — no entity, column, or migration references it.
- Pro resolution comes from **Play Billing ownership** (`BillingManager.proOwned`, in-memory)
  **OR** the isolated **`product_access` / `pro_unlocked`** DataStore flag.
- The **only real feature gate** was the number of **active** (enabled) workflows — free users
  were capped at **one active workflow at a time**. Workflow *creation* was already unrestricted.
- Scheduling, cloud sync, local AI, custom models, templates, output versions, and retry were
  already available without Pro.
- The `product_access` DataStore is a dedicated, isolated file and can remain harmlessly.
- **No Room migration and no DataStore migration are required** to make the product free — a
  missing `pro_unlocked` already resolves to FREE.

## Phase 1 status (this change) — "make all features free"

What changed:

- `PlanState` now grants **`UNLIMITED_ACTIVE_WORKFLOWS = Int.MAX_VALUE`** to *every* plan
  (both FREE and PRO). The misleading `FREE_ACTIVE_WORKFLOW_LIMIT = 1` constant was removed.
- Both `ProductAccessRepository` implementations map ownership to plan **identity only**:
  - never purchased → `PlanState(PlanType.FREE)` (truthfully FREE, full access)
  - existing purchase / local override → `PlanState(PlanType.PRO)` (retained temporarily)
  - both plans receive unlimited active workflows.
- The workflow editor no longer saves a new workflow as paused because another is active;
  new workflows are enabled by default, routed through the centralized `canActivateWorkflow`
  policy (which now always permits activation).
- The workflow-details enable toggle no longer triggers the activation-limit dialog during
  normal use (`checkActivationBlocked()` always returns `null`).

What intentionally did **not** change in Phase 1:

- Old **BillingClient** code remains compiled. `BillingManager`, the `automatist_pro`
  product ID, purchase acknowledgement, restore flow, billing ProGuard rules, `UpgradeScreen`,
  upgrade navigation, `product_access` DataStore, and `AutomatistApp` billing initialization
  are all untouched.
- Existing purchase identity is retained temporarily (surfaced as the "Pro" label).
- **Billing no longer controls access** — it only sets the FREE/PRO label.

Release caveat:

- **The intermediate Phase 1 build must not be published as a standalone production release.**
  A user could still open the Upgrade screen and a purchase flow that no longer grants any
  additional features (everything is already free). Billing runtime and Upgrade UI are removed
  in later phases before a public/free release.

Persistent-data guarantees (verified — see Phase 7 below):

- `AutomatistDatabase` version stays **17**; database file stays `automatist.db`.
- No Room entity changed; no migration added.
- No DataStore name or key changed; `product_access` untouched.
- `applicationId` stays `com.automatist.app`.

## Phase 2 completed

- Upgrade screen removed
- Upgrade navigation removed
- paid-plan badges removed
- purchase and restore controls removed from the UI
- old activation-limit dialog removed
- switch-active workaround removed
- every feature remains available to all users
- billing runtime is still temporarily compiled but has no user-facing entry
- this intermediate build is still not the final release because BillingClient
  and billing declarations remain until Phase 3

What was removed (files):

- `feature/upgrade/UpgradeScreen.kt` and `feature/upgrade/UpgradePrompt.kt` (deleted;
  the `feature/upgrade` package is now empty).
- `Routes.UPGRADE`, the Upgrade composable destination, and all three
  `onNavigateToUpgrade` wirings in `AutomatistNavGraph.kt`.
- The Dashboard "Free"/"Pro" `AssistChip` and `DashboardViewModel.planState`
  (the badge was its only consumer).
- The `WorkflowDetailsViewModel` activation gate: `checkActivationBlocked()`,
  `switchActiveToThis()`, the `planState` field, and the blocking return of
  `toggleEnabled()` (now returns `Unit`); the details screen's
  `ActivationLimitDialog` and `activationBlockedByName` state.
- The dead `onNavigateToUpgrade` parameter on `WorkflowListScreen`.

What was intentionally retained for Phase 3 (billing runtime, no UI entry):

- `BillingManager` (incl. `launchPurchaseFlow`, `queryOwnedPurchases`, restore),
  `BillingClient` dependency, `automatist_pro`, `BillingProductAccessRepository`,
  `LocalProductAccessRepository`, `AccessModule`, the `ProductAccess` domain,
  `AutomatistApp` billing bootstrap, `product_access` DataStore, and the billing
  ProGuard rules. `WorkflowEditorViewModel.save()` still reads the internal plan
  identity to decide default enablement (unrestricted); this is not user-facing.

### Phase 3 checklist

- remove BillingManager
- remove billing dependency
- remove product ID
- remove entitlement repositories
- simplify/remove ProductAccess domain
- remove AccessModule binding
- remove billing app bootstrap
- remove billing ProGuard rules
- verify merged manifest no longer contains BILLING
- verify AAB has no BillingClient classes
- leave old product_access data inert without migration
- update billing-related tests

## Manual smoke-test checklist (device)

Scheduling registration/cancellation is WorkManager-backed and not JVM-unit-tested;
verify on-device:

1. Fresh install
2. Dashboard shows no Pro/Free status
3. Settings contains no Upgrade or restore-purchase action
4. Create three workflows
5. Enable all three
6. Confirm none is automatically paused
7. Disable one workflow
8. Confirm only that workflow is disabled
9. Re-enable it
10. Confirm scheduling remains registered
11. Open every main app screen and verify no paid UI remains
12. Verify no path can start a purchase

## Future phases

### Phase 2 — remove Upgrade UI ✅ (done — see "Phase 2 completed" above)
- Removed `UpgradeScreen`, `UpgradePrompt` (both dialogs), the dashboard Pro/Free chip,
  the `Routes.UPGRADE` route and all `onNavigateToUpgrade` wiring, Pro badges, and paid
  marketing strings.

### Phase 3 — remove billing runtime
- Remove `BillingManager`, the `com.android.billingclient:billing-ktx` dependency,
  `BillingProductAccessRepository` / `LocalProductAccessRepository`, Play product references,
  the restore flow, and the billing ProGuard keep rules. Collapse or delete the
  `ProductAccessRepository`/`AccessModule` layer once nothing reads a plan.

### Phase 4 — legal & external materials
- Update legal documents, Play Store materials, privacy policy, website, and support pages
  for a free product (remove IAP/pricing/restore references; set "In-App Purchases: No").

### Phase 5 — licensing
- Replace the proprietary source licence with the approved open-source licence.
- Preserve separate upstream licences for model weights (e.g. Gemma terms — weights are
  downloaded at runtime, never redistributed in-repo).
- Add GitHub Sponsors support **outside** the Android app (e.g. `.github/FUNDING.yml`).

### Phase 6 — release validation
- Complete upgrade testing (install-over-existing at a higher `versionCode`, same
  `applicationId`, same `automatist.db`) and release validation.

## Notes / known follow-ups

- `UpgradePrompt.kt` still contains the "one active workflow at a time" dialog strings. The
  dialog is now unreachable during normal use (access checks never block); the strings are
  removed with the Upgrade UI in Phase 2.
- `LocalProductAccessRepository` is unbound dead code that shares the `product_access` store
  delegate with the production repo; it is removed alongside billing in Phase 3.
