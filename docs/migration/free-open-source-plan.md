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

## Future phases

### Phase 2 — remove Upgrade UI
- Remove `UpgradeScreen`, `UpgradePrompt` (both dialogs), the dashboard Pro/Free chip,
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
