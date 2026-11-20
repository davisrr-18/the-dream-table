# The Dream Table feedback Worker

A Cloudflare Worker that receives feedback from the in-game form and writes one
Markdown note per submission into the vault (`davisrr-18/rede-neural`), in
`02-projetos/The Dream Table/feedback/`, through the GitHub contents API.

The note is marked as untrusted: the player text is quoted inside a callout and
the note has `status: rascunho`. Nothing in the text is ever executed or followed.

## Status

- Verified locally: `npm run typecheck` and `npm test` from this folder (81 tests).
  The tests fake GitHub, KV and the rate limiter. They do not contact Cloudflare or GitHub.
- Declared: `wrangler` 4.163.0 (in `package.json`) and the KV namespace id (in
  `cloudflare.config.ts`). The last `cf deploy --dry-run` was before Wrangler was
  declared, so it has not been run since. Run it once before the first deploy.
- Not verified: `cf deploy`, the live GitHub API, the Workers Rate Limiting binding
  in production, the `cf` secret upload, and how the notes render in Obsidian.

## Request contract

`POST` to any path with `Content-Type: application/json`:

```json
{ "text": "...", "category": "bug", "modVersion": "1.5.0", "loader": "fabric", "mcVersion": "1.21.1" }
```

| Field | Rule |
|---|---|
| `text` | string, 10 to 2000 characters after trimming (before cleaning). After removing invisible characters it must still have 10 characters, or the request is refused |
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

## Text handling

The player text is cleaned and escaped before it is written:

- CR and LF are normalized; control characters (except line breaks) and zero-width
  characters are removed; runs of three or more line breaks become two.
- Backslashes are doubled first. Then `<`, `[`, `]` and `%` are escaped, and so is
  `#` at the start of a tag, and `---` or a code fence at the start of a line
  (also when indented). Every line is then prefixed with `> `.

So links, embeds, tags, HTML, comments and callouts in the player text show as plain
text in Obsidian, and the text stays inside the quote.

## Limits

| Limit | Value | Where |
|---|---|---|
| Per IP (`CF-Connecting-IP`) | 3 requests per 60 seconds | Workers Rate Limiting binding `FEEDBACK_RATE_LIMITER` (per Cloudflare location, not exact) |
| Global daily cap | 200 accepted notes per UTC day | `DAILY_CAP` in `src/index.ts`, counter in KV. Counted only after GitHub accepts the note. Read-then-write, so simultaneous requests can exceed it by a few |
| Body size | 8192 bytes | `MAX_BODY_BYTES` in `src/index.ts` |

Logs never contain the player text, the IP address, the token or the GitHub response
body. A GitHub failure logs one line: `github-write-failed status=<n>` (`status=0`
means no HTTP response).

## Layout

- `package.json`, `tsconfig.json`: the Worker. Dependencies: `cf`, `typescript`,
  `@cloudflare/workers-types` and `wrangler` (the build tool `cf` uses).
- `src/index.ts`: request handler (default export `fetch`), limits, retry on 422.
- `src/note.ts`: validation, cleaning and escaping, note template, path and id.
- `src/github.ts`: the single GitHub `PUT` per attempt.
- `cloudflare.config.ts`: Worker config, bindings, repository and branch (not secrets).
- `test/`: its own `package.json` (`vitest`, `vite`), `tsconfig.json`, `vitest.config.ts`
  and `.npmrc`. It is kept apart so `vite` is never listed in the Worker's root
  `package.json`, which would make `cf` choose the Vite build. The tests import
  `../src`.

`test/.npmrc` sets `legacy-peer-deps=true` because npm 10 fails on `vitest` peers
(`Cannot read properties of null (reading 'edgesOut')`). The failure reproduces
without the file, so the file stays.

## Setup (run once)

Requirements: Node.js 22.18 or later and npm. Run every npm command below in this
folder, `tools/feedback-worker`.

### 1. Install dependencies

This installs the Worker's tools, including `wrangler`, and the test tooling.

Linux and macOS:

```sh
cd tools/feedback-worker
npm install
(cd test && npm install)
```

Windows (PowerShell):

```powershell
Set-Location tools\feedback-worker
npm install
Set-Location test
npm install
Set-Location ..
```

### 2. Install the Cloudflare CLI and sign in

Same on all systems:

```sh
npm install --global cf
cf auth login
cf auth whoami
```

`cf auth login` prints a link and a code. Add `--no-browser` on a remote machine.

### 3. KV namespace

The namespace id is already in `cloudflare.config.ts`. Only if you create the Worker
in another account, create a new namespace and put its `id` there:

```sh
cf kv namespaces create --title the-dream-table-feedback
```

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

Use the `cf` documented path: `cf deploy --secrets-file`, which uploads the secret
with the Worker version. The token is typed without echo, so it does not reach the
shell history or the screen. The file goes **outside the repository** and is
deleted right after the deploy.

Linux and macOS:

```sh
read -rs -p "GitHub token: " GITHUB_TOKEN && echo
printf '{"GITHUB_TOKEN": "%s"}\n' "$GITHUB_TOKEN" > "$HOME/feedback-secret.json"
chmod 600 "$HOME/feedback-secret.json"
unset GITHUB_TOKEN
cf deploy --secrets-file "$HOME/feedback-secret.json"
rm "$HOME/feedback-secret.json"
```

Windows (PowerShell):

```powershell
$secure = Read-Host "GitHub token" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $token = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  $json = '{"GITHUB_TOKEN": "' + $token + '"}'
  [System.IO.File]::WriteAllText("$HOME\feedback-secret.json", $json)
} finally {
  [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  Remove-Variable token, json -ErrorAction SilentlyContinue
}
cf deploy --secrets-file "$HOME\feedback-secret.json"
Remove-Item "$HOME\feedback-secret.json"
```

Before the first deploy, check the build without uploading (not re-run since the last
change):

```sh
cf deploy --dry-run
```

Alternative, **not verified**: `cf workers secrets update GITHUB_TOKEN --worker the-dream-table-feedback --text <token>`.
It exists in `cf --help`, but its effect on an existing Worker was not tested. Passing
`--text` puts the token on the command line, so prefer the path above.

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

## Known risks

- **The token reaches the whole repository.** A fine-grained token limited to Contents
  cannot restrict paths, so anyone who obtains it can write any file in
  `davisrr-18/rede-neural`. Accepted by the owner. Mitigation: a short expiry, rotation,
  and revocation (step 7).
- **The daily cap can be used up by an attacker.** Each IP gets 3 requests per minute,
  but an attacker with many IPs can reach the 200 accepted notes for the day. The
  Worker then answers 429 to players until 00:00 UTC. Accepted for now; raising the cap
  or adding a per-day IP rule would be the fixes.
- **The player text is untrusted.** The note marks it as data and the text is escaped,
  but anything that reads the vault must still treat the text as data.

## Development

From `tools/feedback-worker`:

```sh
npm run typecheck
npm test
```

Both run locally and do not contact Cloudflare or GitHub. `npm test` runs the tests in
`test/`, so step 1 must be done first.
