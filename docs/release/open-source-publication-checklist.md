# Open-Source Publication Checklist

Gate before making `atj393/automatist-android` public. `[x]` = done in-repo with evidence;
`[ ]` = manual action by the owner (GitHub/Play/website — cannot be done from the codebase);
`[~]` = decision required.

1. [x] **Ownership / employer approval confirmed** — the project owner confirmed sole ownership
   and that no employer approval is required (recorded in the Phase 5 relicensing).
2. [x] **Licence boundary reviewed** — source = Apache-2.0; deps = upstream; model weights =
   upstream; branding = reserved. See `NOTICE`, `THIRD_PARTY_NOTICES.md`, `TRADEMARKS.md`.
3. [x] **Apache-2.0 `LICENSE` present** — canonical, unmodified text at repo root.
4. [x] **`NOTICE` accurate** — Automatist, © 2026 Alexis Johnson, model-weight & branding boundary.
5. [x] **`TRADEMARKS.md` present** — branding excluded from Apache-2.0 + fork guidance.
6. [x] **Third-party notices reviewed** — `THIRD_PARTY_NOTICES.md` rebuilt from the resolved
   dependency tree; Google Play Billing removed; runtime vs test split; Gemma/Qwen documented.
7. [x] **Model weights absent from repo** — no `.task`/`.gguf`/`.safetensors`/`.litertlm` tracked
   or in Git history; `models/` and model extensions are gitignored.
8. [x] **Keystores & secrets absent** — no `.jks`, real `keystore.properties`, `google-services.json`,
   service-account/OAuth JSON, or key material tracked; secret-pattern scan clean.
9. [x] **Git history reviewed** — only `local.properties` was ever committed; it contains just an
   SDK path with a local Windows username (no secret). No keys/keystores/models in history.
10. [~] **History-rewrite / new-repo decision** — see "History publication options" below.
    Recommendation: **Option 1 (keep history)** is acceptable; choose Option 3 if pristine history
    is desired. **Not executed** — owner's decision.
11. [ ] **README build instructions tested** — run the documented debug build/tests on a clean
    checkout before publishing.
12. [x] **CONTRIBUTING / SECURITY / CODE_OF_CONDUCT / SUPPORT / CHANGELOG present** — plus
    `.github` issue templates + PR template.
13. [x] **Actual repository URL confirmed** — `https://github.com/atj393/automatist-android`
    (README/docs updated; the old `your-org` placeholder is gone).
14. [ ] **GitHub Issues enabled** — enable in repo settings before publishing.
15. [ ] **Branch protection configured** — protect `main` (require PR/review as desired).
16. [ ] **Dependabot / security settings reviewed** — enable Dependabot alerts + private
    vulnerability reporting (SECURITY.md points at it).
17. [ ] **Sponsors configured only when active** — Sponsors is NOT active for `atj393`; no
    `.github/FUNDING.yml` was added. Add it only after Sponsors is enabled (see migration plan).
18. [ ] **Website updated after publication** — per `docs/migration/website-free-open-source-migration.md`.
19. [ ] **Play listing updated with real repository URL** — per `docs/release/play-store-listing-draft.md`.
20. [ ] **Tagged open-source release created only after validation** — do not tag before builds +
    device smoke tests pass.
21. [ ] **Official release signing key remains private** — `automatist-release.jks` /
    `keystore.properties` stay untracked and out of the public repo (verified gitignored).
22. [ ] **Physical-device smoke tests passed** — per `docs/release/google-play-release-checklist.md`.

## History rewrite (item 10) — EXECUTED with owner approval

A full-history rewrite was approved and performed locally with `git filter-branch` over all refs
(`git-filter-repo` was unavailable). A verified backup bundle was created first
(`../automatist-android-before-sanitization.bundle`). The rewrite:

- **Stripped from all history:** `.claude/` (local tool config: local paths + a device serial),
  `app/build/` and `.gradle/` (committed build artifacts / bloat), and `local.properties`
  (a local Windows SDK path, e.g. `C:\Users\<user>\…\Android\Sdk` — a username path, **not a
  secret**).
- **Normalized author/committer emails** — the owner's two personal Gmail addresses →
  `atj393@users.noreply.github.com` (name preserved: Alexis Johnson).
- **Removed** the 4 AI-tool `Co-Authored-By: Claude` trailers.
- **Rewrote the tag** `offline-models-v1`; deleted `refs/original/*`; ran `gc --prune=now`
  (repo ~40.9 MiB → ~1.3 MiB).

**Verified after rewrite:** no personal Gmail, no device serial, no `.claude`/`app/build`/
`.gradle`/`local.properties` in any reachable ref; author identity is only
`Alexis Johnson <atj393@users.noreply.github.com>` (+ the `GitHub <noreply@github.com>` merge);
HEAD source tree unchanged except the removed `.claude/` files. No credential secrets ever
existed, so no rotation was needed.

**Remaining (owner actions — not performed here):**

- Every commit hash changed. **Force-push** the rewritten refs + tag to `origin` (strategy A).
  The local remote-tracking cache was cleared during sanitization, so use plain `git push --force`
  (a `--force-with-lease` has no baseline) and **do not `git fetch` before pushing** — fetching
  first would re-pull the old history into the local clone. After the private repo shows only the
  clean history, a later fetch is safe. (Not done here — pushing is the owner's step.)
- If a numeric-ID GitHub no-reply (`<id>+atj393@users.noreply.github.com`) is preferred for
  commit-attribution linkage, re-run the email step with that address before force-pushing.
- Optionally delete intermediate migration branches before making the repo public.

The pre-rewrite state is fully recoverable from the backup bundle
(`git clone ../automatist-android-before-sanitization.bundle`).
