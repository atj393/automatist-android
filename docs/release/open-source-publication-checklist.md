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
10. [x] **History-rewrite / new-repo decision** — history kept + sanitized (rewrite executed
    with owner approval); the sanitized history was pushed to `origin` via explicit per-ref leases
    (see "Public repository verification" below). Old commit history is no longer reachable.
11. [x] **README build instructions tested** — the documented build was run on a fresh clone of
    the public remote: `assembleDebug`, `assembleRelease`, `bundleRelease`, and
    `testDebugUnitTest` — **BUILD SUCCESSFUL** (13m). The release artifact built **unsigned**
    (`app-release-unsigned.apk`) because no signing keystore is present in a public clone — the
    expected, documented behaviour for contributors.
12. [x] **CONTRIBUTING / SECURITY / CODE_OF_CONDUCT / SUPPORT / CHANGELOG present** — plus
    `.github` issue templates + PR template.
13. [x] **Actual repository URL confirmed** — `https://github.com/atj393/automatist-android`
    (README/docs updated; the old `your-org` placeholder is gone).
14. [x] **GitHub Issues enabled** — confirmed enabled on the public repo.
15. [x] **Branch protection configured** — `main` protected: PR required (0 required approvals
    so the solo maintainer is not locked out), force-push blocked, deletion blocked, conversation
    resolution required, no status-check requirement (no CI exists yet), `enforce_admins=false`
    so the owner retains recovery access.
16. [x] **Dependabot / security settings reviewed** — Dependabot alerts + automated security
    updates enabled; secret scanning + push protection enabled (free on public repos); private
    vulnerability reporting enabled (SECURITY.md points at it).
17. [ ] **Sponsors configured only when active** — Sponsors is NOT active for `atj393`; no
    `.github/FUNDING.yml` was added. Add it only after Sponsors is enabled (see migration plan).
18. [x] **Website updated after publication** — free/open-source copy + billing-free legal pages
    (privacy/terms/support) deployed to https://automatist.cloud and verified live.
19. [ ] **Play listing updated with real repository URL** — per `docs/release/play-store-listing-draft.md`.
20. [ ] **Tagged open-source release created only after validation** — do not tag before builds +
    device smoke tests pass.
21. [x] **Official release signing key remains private** — `automatist-release.jks` /
    `keystore.properties` are gitignored and were never tracked or copied into the fresh clone;
    the clone's release build produced an **unsigned** artifact, confirming the signing key is
    absent from the public repo.
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

## Public repository verification — EXECUTED 2026-07-11

The sanitized history was pushed to `origin` and the repository was made public. All steps used
explicit, per-ref force-with-lease pushes (never `push --force --all`) and no history rewrite was
re-run.

**Remote replacement (controlled, lease-based):**
- `main`: lease `6d1af1e…` → `b93d628c368e8371fc30533beb4dc461fcc1649e` (sanitized HEAD).
- tag `offline-models-v1`: lease `46004147…` → `5004f4a05dad008c2f1922982197008c3d54567d`.
- Pre-replacement remote refs were recorded out-of-repo before pushing.
- 5 obsolete migration branches were deleted from the remote after confirming their content is
  fully contained in `main`.

**Fresh-clone audit (clone of the public remote, separate directory):**
- No old/unsanitized commit SHA reachable; single author identity
  `Alexis Johnson <atj393@users.noreply.github.com>`; no `.claude/`, `app/build/`, `.gradle/`,
  `local.properties`, keystores, model weights, personal Gmail, device serial, or employer
  references present.
- Documented build ran green (see item 11); release artifact unsigned (see item 21).

**Publication + hardening:**
- Repository visibility set to **public**; unauthenticated access, README, and LICENSE confirmed
  reachable (HTTP 200).
- Advertised public refs: `HEAD`/`refs/heads/main` = `b93d628…`, `refs/tags/offline-models-v1` =
  `5004f4a…`, plus read-only `refs/pull/1|2/head` (GitHub-managed PR refs). No old SHA advertised.
- Security: Issues enabled; secret scanning + push protection; Dependabot alerts + automated
  security updates; private vulnerability reporting; `main` branch protection (see item 15).

**Residual (owner decision, low severity):** GitHub retains read-only `refs/pull/1` (`a63811a…`)
and `refs/pull/2` (`3c9da81…`) from pre-sanitization pull requests. These are not clonable via
`git clone`, contain no credentials, and carry only the pre-sanitization Gmail author metadata
(an owner-approved public identity) plus old build-artifact/device-serial diffs. To remove them,
close/delete the corresponding pull requests in the GitHub UI; otherwise they can be accepted as
benign historical PR refs.
