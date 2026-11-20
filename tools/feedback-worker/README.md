# The Dream Table feedback Worker

A Cloudflare Worker that receives feedback from the in-game form and writes one
Markdown note per submission into the vault (`davisrr-18/rede-neural`), in
`02-projetos/The Dream Table/feedback/`, through the GitHub contents API.

The note is marked as untrusted: the player text is quoted inside a callout and
the note has `status: rascunho`. Nothing in the text is ever executed or followed.

## Status

- Verified locally: `npm run typecheck` and `npm test` (56 tests), from this folder.
- `cf deploy --dry-run` no longer picks the Vite build. It now stops because no
  build tool is declared (`No Cloudflare dev server is installed`). The fix is to
  declare Wrangler (step 1 of Setup). That step is not done yet in this folder.
- Not verified: `cf deploy`, the live GitHub API, the Workers Rate Limiting binding
  in production, and the `cf` secret upload.

## Request contract

`POST` to any path with `Content-Type: application/json`:

```json
{ "text": "...", "category": "bug", "modVersion": "1.5.0", "loader": "fabric", "mcVersion": "1.21.1" }
```

| Field | Rule |
|---|---|
| `text` | string, 10 to 2000 characters after trimming |
| `category` | `bug`, `idea` or `other` |
| `modVersion`, `loader`, `mcVersion` | 1 to 32 characters, matching `^[A-Za-z0-9._+-]+$` |

Extra fields are ignored and never written. Responses:

| Status | Meaning |
|---|---|
| 200 `{"ok":true}` | Note written |
| 400 `{"error":...}` | Invalid JSON, content type or fields |
| 405 | Any method other than POST |
| 413 | Body larger than 8192 bytes (checked on `Content-Length` and on the bytes read) |
| 429 | Per-IP limit or daily cap reached |
| 502 | GitHub write failed (no GitHub details are returned) |

## Limits

| Limit | Value | Where |
|---|---|---|
| Per IP (`CF-Connecting-IP`) | 3 requests per 60 seconds | Workers Rate Limiting binding `FEEDBACK_RATE_LIMITER` (per Cloudflare location, not exact) |
| Global daily cap | 200 valid submissions per UTC day | `DAILY_CAP` in `src/index.ts`, counter in KV. Read-then-write, so simultaneous requests can exceed it by a few |
| Body size | 8192 bytes | `MAX_BODY_BYTES` in `src/index.ts` |

Logs never contain the player text, the IP address or the token.

## Layout

- `package.json`, `tsconfig.json`: the Worker. Dependencies: `cf`, `typescript`,
  `@cloudflare/workers-types`, and `wrangler` once declared (step 1).
- `src/index.ts`: request handler (default export `fetch`), limits, retry on 422.
- `src/note.ts`: validation, text sanitization, note template, path and id.
- `src/github.ts`: the single GitHub `PUT` per attempt.
- `cloudflare.config.ts`: Worker config, bindings, repository and branch (not secrets).
- `test/`: its own `package.json` (`vitest`, `vite`), `tsconfig.json`, `vitest.config.ts`
  and `.npmrc`. It is kept apart so `vite` is never listed in the Worker's root
  `package.json`, which would make `cf` choose the Vite build. The tests import
  `../src` and fake GitHub, KV and the rate limiter.

Vite is not in the root `package.json` and is not hoisted into the root
`node_modules`.

`test/.npmrc` sets `legacy-peer-deps=true` because npm 10 fails on `vitest` peers
(`Cannot read properties of null (reading 'edgesOut')`). The failure reproduces
without the file, so the file stays.

## Setup (run once)

Requirements: Node.js 22.18 or later and npm. Run every npm command below in this
folder, `tools/feedback-worker`.

### 1. Install dependencies and declare Wrangler

Install the Worker and the test tooling. Then declare Wrangler, the build tool
`cf` needs, at the exact version used by the tests:

Linux and macOS:

```sh
cd tools/feedback-worker
npm install
(cd test && npm install)
npm install --save-dev --save-exact --legacy-peer-deps wrangler@4.163.0
```

Windows (PowerShell):

```powershell
Set-Location tools\feedback-worker
npm install
Set-Location test
npm install
Set-Location ..
npm install --save-dev --save-exact --legacy-peer-deps wrangler@4.163.0
```

`cf` requires wrangler 4.163.0 or newer. `--legacy-peer-deps` is needed because that
wrangler lists a newer optional `@cloudflare/workers-types` peer than the pinned one.
The KV namespace already exists and its id is in `cloudflare.config.ts` (step 3 is done).

### 2. Install the Cloudflare CLI and sign in

Same on all systems:

```sh
npm install --global cf
cf auth login
cf auth whoami
```

`cf auth login` prints a link and a code. Add `--no-browser` on a remote machine.

### 3. Create the KV namespace

Same on all systems:

```sh
cf kv namespaces create --title the-dream-table-feedback
```

Copy the namespace `id` from the output into `cloudflare.config.ts`, replacing
`REPLACE_WITH_KV_NAMESPACE_ID`.

The rate limit namespace is `1001` in `cloudflare.config.ts`. Namespace ids must be
unique in the account; change it if another Rate Limiting binding already uses it.

### 4. Create the GitHub token

1. On GitHub, open Settings, then Developer settings, then Personal access tokens,
   then Fine-grained tokens, then Generate new token.
2. Resource owner: `davisrr-18`.
3. Repository access: Only select repositories, then `davisrr-18/rede-neural` only.
4. Permissions, Repository permissions: Contents, Read and write. Nothing else.
5. Expiration: a short period (for example 30 days). Rotate it when it expires.

### 5. Set the token as a secret and deploy

A Worker that does not exist yet cannot get secrets in advance, and `cf deploy`
stops without `GITHUB_TOKEN`: the first deploy must use `cf deploy --secrets-file`,
which uploads the secret with the Worker version. Create a JSON file **outside the repository**, deploy with
it, and delete the file right after.

Linux and macOS:

```sh
printf '{"GITHUB_TOKEN": "%s"}\n' 'PASTE_THE_TOKEN_HERE' > "$HOME/feedback-secret.json"
cf deploy --secrets-file "$HOME/feedback-secret.json"
rm "$HOME/feedback-secret.json"
```

Windows (PowerShell):

```powershell
Set-Content -Path "$HOME\feedback-secret.json" -Value '{"GITHUB_TOKEN": "PASTE_THE_TOKEN_HERE"}'
cf deploy --secrets-file "$HOME\feedback-secret.json"
Remove-Item "$HOME\feedback-secret.json"
```

Alternative, **not verified**: `cf workers secrets update GITHUB_TOKEN --worker the-dream-table-feedback --text <token>`.
It exists in `cf --help`, but its effect on an existing Worker was not tested, and
`--text` puts the token in the shell history. Prefer the path above.

Before the first deploy, check the build without uploading:

```sh
cf deploy --dry-run
```

### 6. Smoke test

This writes a real note into the vault. Delete it afterwards.

Linux and macOS (replace the URL with the one `cf deploy` printed):

```sh
curl -s -X POST "https://the-dream-table-feedback.YOUR-SUBDOMAIN.workers.dev/" \
  -H "Content-Type: application/json" \
  -d '{"text":"Teste de fumaca do formulario","category":"other","modVersion":"1.5.0","loader":"fabric","mcVersion":"1.21.1"}'
```

Windows (PowerShell):

```powershell
$body = '{"text":"Teste de fumaca do formulario","category":"other","modVersion":"1.5.0","loader":"fabric","mcVersion":"1.21.1"}'
Invoke-RestMethod -Method Post -Uri "https://the-dream-table-feedback.YOUR-SUBDOMAIN.workers.dev/" -ContentType "application/json" -Body $body
```

Expected: `{"ok":true}`, and a new file in `02-projetos/The Dream Table/feedback/`.

### 7. Revoke or rotate the token

- Revoke: GitHub, Settings, Developer settings, Fine-grained tokens, then Delete
  the `the-dream-table-feedback` token. The Worker can no longer write notes.
- Rotate: create a new token (step 4), then repeat step 5 with the new value.

## Development

From `tools/feedback-worker`:

```sh
npm run typecheck
npm test
```

Both run locally and do not contact Cloudflare or GitHub. `npm test` runs the
tests in `test/`, so step 1 must be done first.
