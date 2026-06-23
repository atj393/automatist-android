# Adding local (on-device) models to Automatist

Automatist can run **compatible MediaPipe `.task` language models** fully on your device —
no internet or API key is needed once a model is installed. This document explains which
models work, the two ways to add one, and how a model publisher can package and publish a
model that Automatist can import.

> Automatist verifies a model's SHA-256 checksum, but **cannot guarantee** a third-party
> model's quality, on-device performance, or that your use complies with its license.

---

## 1. Supported and unsupported formats

**Supported**

- MediaPipe LLM Inference **`.task`** bundles (the format loaded by `LlmInference.setModelPath`).

**Not supported in this version**

- `.gguf` (llama.cpp), `.safetensors`, `.bin`, raw `.tflite`
- APKs, native libraries (`.so`), shell/Python scripts, or any executable plug-in
- `.litertlm` (the newer LiteRT-LM format) — not loadable by the bundled MediaPipe runtime

Automatist will **not** convert `.gguf` or `.safetensors` files into `.task`. Conversion must
be done outside the app with Google's MediaPipe/AI-Edge tooling before hosting.

> **Hugging Face is a discovery/hosting platform, not proof of compatibility.** Most models on
> Hugging Face are *not* in `.task` format. A model is only usable here if it is an actual
> MediaPipe LLM `.task` bundle that downloads over direct HTTPS without a login or token.

---

## 2. Built-in / verified models vs. advanced import

**Built-in models (easiest).** Automatist ships with curated on-device options
(Gemini Nano via Android AICore, and a downloadable Gemma model). These need no URLs or
checksums — just tap *Download* / *Check availability* in **Settings → On-device AI**. Prefer
these unless you specifically need a different model.

There is no remote "model store" in this version; only models that actually exist are shown.

**Advanced import (for everyone else).** If you need a different compatible model, you can:

1. **Import a model manifest** *(preferred)* — paste one HTTPS URL that points to a small JSON
   file describing the model. Automatist fetches and validates it, then shows a review screen
   before anything is downloaded.
2. **Enter model details manually** *(fallback)* — type the name, `.task` URL, SHA-256, size,
   and license URL yourself.

Both paths run the **same** security checks and the same secure download flow.

---

## 3. Manifest schema

The manifest is a small, versioned JSON document hosted at an HTTPS URL.

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `schemaVersion` | int | yes | Must be `1` (the only version this app supports). |
| `format` | string | yes | Must be exactly `"mediapipe-llm-task"`. |
| `name` | string | yes | 1–80 characters. Display name only — never used for storage. |
| `version` | string | no | Free-form publisher version label (shown on review). |
| `modelUrl` | string | yes | HTTPS, port 443, no credentials, path ends in `.task`. |
| `sha256` | string | yes | Exactly 64 hexadecimal characters. |
| `fileSizeBytes` | long | yes | Positive; must map to roughly 1–3072 MB. |
| `licenseUrl` | string | yes | HTTPS license page. |
| `sourceUrl` | string | yes | HTTPS model card / source page. |
| `minimumRamMb` | int | no | Advisory hint. Clamped to a safe range (0–16384). |
| `contextWindowChars` | int | no | Advisory hint. Clamped to 500–4000 (default 2500). |

Notes:

- The manifest document itself must be small (a strict size limit is enforced when fetching).
- `minimumRamMb` / `contextWindowChars` are **hints**: Automatist applies safe defaults and
  bounds and never trusts them verbatim.
- The manifest's `name`/`version` are **never** used to name files on disk. Automatist always
  generates its own safe internal identifier and file name.
- Importing a manifest never downloads the model automatically — the user must review and
  confirm first.

---

## 4. Manifest example (copy-paste)

```json
{
  "schemaVersion": 1,
  "format": "mediapipe-llm-task",
  "name": "Example Local Model",
  "version": "1.0.0",
  "modelUrl": "https://publisher.example/models/example-model.task",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "fileSizeBytes": 734003200,
  "licenseUrl": "https://publisher.example/license",
  "sourceUrl": "https://publisher.example/model-card",
  "minimumRamMb": 4096,
  "contextWindowChars": 2500
}
```

---

## 5. Step-by-step for a model publisher

1. **Prepare a compatible MediaPipe `.task` package outside the app.** Start from a model that
   Google's MediaPipe LLM Inference supports, and produce a `.task` bundle using the official
   MediaPipe / AI-Edge conversion tooling. Automatist does not convert models; ship a real
   `.task` file. (Verify it loads with the MediaPipe LLM Inference API before publishing.)
2. **Host it over HTTPS.** Put the `.task` file on a server or CDN that serves it over direct
   HTTPS (port 443) with **no login, token, or cookie** required. Redirects must stay HTTPS.
   The URL path must end in `.task`.
3. **Calculate the SHA-256 checksum** of the exact file you host:
   - macOS/Linux: `shasum -a 256 example-model.task`
   - Windows (PowerShell): `Get-FileHash example-model.task -Algorithm SHA256`
   Publish the lowercase 64-character hex digest. If you re-export the model, the checksum
   changes — update the manifest.
4. **Provide a public license/source page.** Host an HTTPS license page (`licenseUrl`) and a
   model card / source page (`sourceUrl`) so users can review terms before importing.
5. **Publish the manifest.** Host the JSON from §4 at a stable HTTPS URL and share that URL.
   Users paste it into **Settings → On-device AI → Import model manifest**.

---

## 6. Automatist does not convert models

Automatist will **not** convert `.gguf`, `.safetensors`, `.bin`, or any other format into a
MediaPipe `.task` bundle. Conversion is the publisher's responsibility and must be done with
the appropriate MediaPipe / AI-Edge tools before hosting.

---

## 7. Hugging Face is a source, not a guarantee

Hosting a file on Hugging Face (or anywhere else) does not make it compatible. Automatist
accepts a model only if it is a genuine MediaPipe LLM `.task` bundle available via direct,
unauthenticated HTTPS, with a valid checksum and a public license/source page. Many popular
Hugging Face repositories ship `.gguf`/`.safetensors` files or are gated behind a login — those
cannot be used here.
