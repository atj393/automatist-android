# Website & External-Content Migration Plan (free + eventual open-source)

**Scope:** planning only. Do **not** edit the website repository from the Android repo. This
document inventories what the `automatist.cloud` site (and other external content) must change to
match the free, billing-free app, and what is deferred until the source is actually relicensed and
published (a later phase). URLs referenced by the app today live in
`app/src/main/java/com/automatist/app/platform/support/SupportConfig.kt` and `EULA.md` / `README.md`.

**Status now:** the Android app is **free** (no ads, no subscriptions, no in-app purchases, no
paywalls) and contains **no** Google Play Billing. The source is still **proprietary** — do **not**
publish open-source claims on the site yet.

**Guardrails**
- No sponsorship / donation / payment link is added **inside** the Android app (Play-distributed) —
  ever. Support/sponsorship live only outside the app (website, GitHub).
- Do not add a real GitHub Sponsors URL until Sponsors is configured — use a placeholder.
- Do not claim open source until the repository is relicensed and made public.

---

## Page-by-page inventory

For each page: **(1)** content to remove · **(2)** replacement free-product wording · **(3)**
content deferred until the app is truly open source · **(4)** future GitHub Sponsors placement.

### Homepage
1. Remove any "Free vs Pro", pricing, or "Upgrade to Pro" hero/CTA.
2. Position as "Free, private, on-device AI workflows for Android. Every feature included."
3. Deferred: "Open source" badge / "View on GitHub" hero once public.
4. Sponsors: optional footer "Support the project" → GitHub Sponsors (placeholder), never a payment CTA.

### Pricing
1. Remove the entire pricing/tier table and any price figures.
2. Replace with a short "Automatist is free — no subscriptions, no in-app purchases, no ads." Consider redirecting `/pricing` → homepage or a "Free" section.
3. Deferred: none.
4. Sponsors: may add an "optional, unlocks nothing" support note here later (not a price).

### Pro / Upgrade pages
1. Remove `/pro` and `/upgrade` pages (or their content).
2. Replace with a redirect to the homepage/features; if kept, state all former "Pro" features are now free.
3. Deferred: none.
4. Sponsors: none here (avoid implying sponsorship replaces "Pro").

### FAQ
1. Remove "How do I upgrade?", "What does Pro include?", "How do I restore purchases?", refund questions.
2. Add: "Is Automatist free?" → yes, fully; "Are there ads/subscriptions?" → no; "How is my data handled?" → link privacy policy; "How do I get support?" → support channels.
3. Deferred: "Is it open source?", "Can I contribute?" answers.
4. Sponsors: an optional "How can I support development?" entry pointing to GitHub Sponsors (placeholder).

### Support (`/support`, `/support?priority=true`)
1. Remove "priority support" as a paid benefit and the `?priority=true` tier. Everyone gets the same support.
2. Replace with a single support page (contact + how to report issues); email `support@automatist.cloud` and in-app feedback `feedback@automatist.cloud`.
3. Deferred: GitHub Issues/Discussions links once the repo is public.
4. Sponsors: may note support is community/best-effort; sponsorship does **not** buy priority.

### Privacy (`/privacy`, `/privacy.html`)
1. Remove Google Play Billing / purchase-data / refund / restore language.
2. Publish the rewritten policy from `docs/legal/privacy-policy-draft.md` (billing-free; accurate cloud-AI, Drive, and model-download disclosures). Ensure `https://automatist.cloud/privacy` resolves (the app links to it).
3. Deferred: none (policy is independent of licence).
4. Sponsors: none.

### Terms (`/terms`, `/terms.html`)
1. Remove in-app-purchase / paid-entitlement / restore / refund clauses.
2. Publish terms consistent with the updated `EULA.md` (free product; still proprietary for now).
3. Deferred: reconcile with the open-source licence when the source is relicensed (Phase 5).
4. Sponsors: none.

### Download
1. Remove any "free vs paid" framing.
2. Keep the Google Play link; state the app is free.
3. Deferred: link to GitHub Releases / source once public.
4. Sponsors: none.

### Feature comparison
1. Remove the Free-vs-Pro comparison table entirely.
2. Replace with a single feature list (all features available to everyone).
3. Deferred: none.
4. Sponsors: none.

### Screenshots
1. Remove screenshots showing the Upgrade screen, Pro/Free badge, price, or restore button.
2. Recapture from the billing-free build (no paid UI).
3. Deferred: none.
4. Sponsors: none.

### Metadata / SEO / structured data
1. Remove price/offer metadata: `og:`/Twitter tags mentioning price or "Pro"; JSON-LD
   `SoftwareApplication.offers`/`Product` price fields; any "paid app"/IAP structured data.
2. Set structured data to a free application (e.g. `offers.price` = `0` / `PriceSpecification` 0, or omit offers) and update titles/descriptions to the free positioning.
3. Deferred: `codeRepository` / open-source metadata until public.
4. Sponsors: none in structured data.

### Release posts / changelog
1. Remove or annotate posts announcing "Pro", pricing, or purchase features as current.
2. Add a post: "Automatist is now free — every feature included." (Public wording only; do not describe internal migration phases.)
3. Deferred: an "open source" announcement post until relicensed/public.
4. Sponsors: a future "support development via GitHub Sponsors" note (placeholder) once configured.

### Email addresses
1. No change required to remove billing, but ensure addresses are monitored: `support@automatist.cloud` (support/legal), `feedback@automatist.cloud` (in-app feedback).
2. Keep as the contact points referenced by the app, privacy policy, and EULA.
3. Deferred: none.
4. Sponsors: none.

### Play Store links
1. Ensure the Play listing links reflect a free app with no in-app purchases (see `play-store-listing-draft.md`).
2. Keep the store link; update any "buy"/"upgrade" wording.
3. Deferred: none.
4. Sponsors: none.

### GitHub links
1. Fix the placeholder clone URL (`github.com/your-org/automatist`) once the real repo/org is decided.
2. Add repo links only when the repository is public.
3. Deferred: "View source", "Contribute", stars/forks — until public and relicensed.
4. Sponsors: the GitHub Sponsors button lives on the GitHub repo (via `.github/FUNDING.yml`, added in a later phase) and/or the website — **placeholder** `https://github.com/sponsors/<ACCOUNT-TBD>` until configured.

---

## Support & sponsorship model (record; do not implement yet)

Automatist's optional support channels will live **outside** the Play-distributed Android app:

- GitHub repository README (once public)
- GitHub Sponsors (URL **TBD** — do not use a fake link)
- the project website (`automatist.cloud`)
- GitHub Releases / external release announcements

Rules:
- Sponsorship is **optional**.
- Sponsorship **unlocks no features** — the app is fully free for everyone.
- Sponsorship provides **no service priority** (no "priority support" tier).
- Automatist remains free.
- **No sponsorship, donation, coffee, or payment link appears inside the Android app** that ships
  on Google Play.

Do not configure GitHub Sponsors or add `.github/FUNDING.yml` in this phase.

---

## Cross-references

- App-side support/legal URLs: `platform/support/SupportConfig.kt` (+ hard-coded website link in
  `feature/vault/VaultScreen.kt`).
- Privacy policy source: `docs/legal/privacy-policy-draft.md`.
- Terms/EULA source: `EULA.md` (hosted terms at `automatist.cloud/terms.html`).
- Play listing / Data Safety: `docs/release/play-store-listing-draft.md`,
  `docs/release/data-safety-draft.md`.
