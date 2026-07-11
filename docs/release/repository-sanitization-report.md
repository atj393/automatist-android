# Repository Sanitization Audit Report

> Secrets and personal data are **masked** in this report (e.g. `sk-12…cdef`,
> `R3CR…PCD`, `C:\Users\<user>\…`). No raw secret values appear here.

> **OUTCOME (rewrite executed, owner-approved).** A full-history rewrite was performed locally
> with `git filter-branch` after a verified backup bundle. It stripped `.claude/`, `app/build/`,
> `.gradle/`, and `local.properties` from all history; normalized the owner's two personal Gmail
> addresses to `atj393@users.noreply.github.com`; removed the 4 AI-tool `Co-Authored-By: Claude`
> trailers; rewrote the tag; deleted `refs/original/*`; and gc'd (~40.9 MiB → ~1.3 MiB). Verified
> clean afterward. **Not pushed** — every commit hash changed; force-pushing to `origin` is the
> owner's step. No credential secrets ever existed → no rotation. See
> `open-source-publication-checklist.md` for the mapping and remaining owner actions.

## 1. Audit date
2026-07-11.

## 2. Refs and history scope scanned
All reachable refs via `--all`: local branches (`chore/public-repository-sanitization`,
`chore/open-source-readiness-phase-5`, `docs/free-app-policy-phase-4`,
`feat/free-access-phase-1..3`, `feat/custom-local-models`, `feature/local-model`,
`chore/play-store-release-hardening`, `main`), remote-tracking `origin/*`, tag
`offline-models-v1`, and **`refs/original/*`** (backups from a prior `git filter-branch`).
145 commits total. No stashes; single worktree.

## 3. Tools and commands used (read-only)
`git status/branch/log/show-ref/tag/stash/worktree`, `git rev-list --all`,
`git log --all -G/-S`, `git grep`, `git shortlog -sne --all`, `git cat-file --batch-check`,
`git count-objects -vH`, `git fsck` review. `gitleaks` and `git-filter-repo` are **not
installed** (native git used instead).

## 4. Current-tree secret findings
**No credential secrets in the current tree.** No API keys, tokens, private keys, or
keystore material; no tracked `local.properties`/`*.jks`/`*.keystore`/`google-services.json`/
service-account/OAuth JSON/`.env`. The only "secret-shaped" hits were placeholder comments in
`app/build.gradle.kts` (`// storePassword=...`). **Finding:** `.claude/settings.json` and
`.claude/settings.local.json` are tracked and contain local machine data (not credentials) —
see §6.

## 5. Historical secret findings
**No credential secrets in reachable history.** `-G` scans for `sk-`, `sk-ant-`, `AIza…`,
`ghp_`, `github_pat_`, `-----BEGIN … PRIVATE KEY-----`, `xox…`, `AKIA…`, `hf_…` each returned
**0 commits**. → **No credential rotation required.** (Hygiene/personal-data items in history
are in §6–§8, §10.)

## 6. Personal-information findings
- **Author/committer emails (metadata):** two personal Gmail addresses used by the sole owner
  across 145 commits — `alexi…393@gmail.com` (×112) and `alexi…jr@gmail.com` (×33), plus
  `GitHub <noreply@github.com>` on one merge. Personal emails should be normalized to a
  confirmed public GitHub no-reply for `atj393` (metadata; requires history rewrite + owner
  confirmation of the exact no-reply address — do not invent one).
- **`.claude/settings.local.json`** (tracked + in history): a **device serial** `R3CR…PCD`
  (×3, in `adb.serial` args), local username paths `/c/Users/johnson/…`, absolute path
  `C:/Alexis/Test/mobile-app`, and Gradle-dist hash paths. Device identifier + local paths.
- **`.claude/settings.json`** (tracked + in history): local path `//c/Users/johnson/.claude/…`.
- **Historical `local.properties`** (removed from current tree; still in reachable history):
  `sdk.dir=C:\Users\<user>\AppData\Local\Android\Sdk` — a local Windows **username path, not a
  secret**.
- **`docs/release/open-source-publication-checklist.md`** currently prints the same
  `C:\Users\<user>\…` SDK path as an audit description (minor; mask on cleanup).
- **`Alexis Johnson`** appears as the copyright/publisher identity — this is the **approved
  public identity** (allowlist); keep.

## 7. Employer/company-reference findings
**None.** `optimal-systems`/`optimalsystems`/`enaio`/`yuuvis`/`blue.?bird` = **0** in the
current tree and **0** across all history (`-G`, case-insensitive). "confidential" appears only
in legitimate contribution-rule text and a TLS comment. No employer emails, repos, products, or
customer names.

## 8. Author/committer inventory
| Identity | Role | Commits |
|---|---|---|
| Alexis Johnson `<alexi…393@gmail.com>` | author + committer | 112 |
| Alexis Johnson `<alexi…jr@gmail.com>` | author + committer | 33 |
| GitHub `<noreply@github.com>` | committer (1 merge) | 1 |

All human authorship is the **sole owner** (two personal-email identities of the same person).
**No third-party human contributor exists** — nothing to preserve for others; nothing to erase.
GPG: 144 unsigned, 1 unverifiable (the GitHub merge). Configured identity:
`Alexis Johnson <alexi…393@gmail.com>`.

## 9. Co-authored-by / trailer inventory
- **`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`** on **4 commits**:
  `03b07c8`, `a44dc87`, `aad1cb3`, `c79e01d`. This is **AI-tool attribution**, not a human
  copyright contribution → propose removal (only after history-rewrite approval).
- No `Signed-off-by`, `Reviewed-by`, `Assisted-by`, `Generated-by`, or "Generated with"
  trailers. ("Claude" also appears as the filename `CLAUDE.md` in some subjects — not a trailer.)

## 10. Large/binary-file inventory (history)
Build artifacts were committed early (`first commit` + a "regenerate build artifacts" chore) and
remain reachable in history / `refs/original`:
- `app/build/intermediates/dex/debug/mergeExtDexDebug/classes.dex` — ~44 MB
- `app/build/intermediates/apk/debug/app-debug.apk` — ~17.5 MB
- `classes2.dex` ~12 MB, `classes3.dex` ~1.3 MB, plus many `app/build/**` intermediates
- `.gradle/8.13/executionHistory/executionHistory.bin` ~1.75 MB, kapt caches ~3.7 MB
These account for the ~40.9 MiB of loose objects. **No model weights** (`.task`/`.gguf`/
`.safetensors`/`.litertlm`) are tracked or in history. Current-tree largest file:
`icons/New folder/Automatish Icon.png` (~331 KB, a brand asset). Build artifacts are **not** in
the current tree (gitignored); they are history-only.

## 11. Website-source audit (`C:\Alexis\Test\automatist-site`, read-only)
Static Cloudflare site (HTML/CSS/JS + assets + `wrangler.jsonc`). **No secrets, no analytics IDs
(no `G-`/`UA-`/`GTM-`), no company references, no personal data.** Only email is
`support@automatist.cloud`. `wrangler.jsonc` holds no secrets (name/compat-date/observability/
assets dir) — deploy config, **do not copy**. Approved public copy: title *"Automatist ·
Mobile-first AI automation for Android"*; summary *"Useful AI workflows from the phone you
already carry. No server, no homelab, no always-on laptop…"*; feature headings (AI workflow
builder, AI profiles, Scheduled without a scheduler, Transparent run logs, Templates & blank
canvas, Optional Google Drive sync, On-device AI — no keys/no cloud). Content classes: A safe
copy; B/C safe branding/screenshots (per TRADEMARKS.md); D public legal/support URLs; F
`wrangler.jsonc`/deploy config (do not copy). No stale Pro/billing text observed.

## 12. Current-tree corrections required
1. Untrack and gitignore `.claude/settings.json` and `.claude/settings.local.json` (local paths
   + device serial; personal tool config, not product source).
2. Mask the `C:\Users\<user>\…` path in `open-source-publication-checklist.md`.
3. (Optional) tidy the `your-org` planning-note references and the `icons/New folder/` path.

## 13. History corrections required
Yes — see the decision gate. Strip from all reachable history: `app/build/**`, `.gradle/**`
(build artifacts/bloat), `.claude/**` (device serial + local paths), `local.properties`;
remove the 4 AI-tool `Co-Authored-By: Claude` trailers; normalize the two personal owner emails
to a confirmed `atj393` GitHub no-reply; drop `refs/original/*`.

## 14. Credential-rotation requirements
**None.** No credential was ever committed (current tree or history).

## 15. Publication recommendation
**Do not publish yet.** A history rewrite is required for a clean public history (personal
emails, device serial/local paths, AI trailers, build-artifact bloat). **Critical caveat:** the
branches are **already present on `origin`** (`github.com/atj393/automatist-android`) — the
listed items may already be on GitHub. None are credentials, but a rewrite on an already-pushed
repo needs a coordinated force-push (or a fresh sanitized repo) and explicit approval.

## 16. Remaining blockers
- History-rewrite approval (exact phrase) — see the gate.
- Owner must confirm the exact GitHub no-reply email for `atj393` (do not invent).
- `git-filter-repo` is not installed (needs installing, or use `filter-branch`).
- Confirm current GitHub repo visibility (public vs private) — affects whether force-push vs a
  fresh repo is the right remediation.

## 17. Secret-masking statement
All potential secrets and personal identifiers in this report are masked or referenced by
path/pattern only. No raw secret values are included. (In practice, no credential secrets were
found in the tree or history.)
